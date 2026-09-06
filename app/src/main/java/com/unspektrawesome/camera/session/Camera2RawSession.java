package com.unspektrawesome.camera.session;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.hardware.HardwareBuffer;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.LensShadingMap;
import android.hardware.camera2.params.RggbChannelVector;
import android.hardware.camera2.params.SessionConfiguration;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Range;
import android.util.Rational;
import android.util.Log;

import com.unspektrawesome.camera.CameraRoute;
import com.unspektrawesome.camera.IntRange;
import com.unspektrawesome.camera.RawFormat;
import com.unspektrawesome.camera.RawOutput;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.EnumSet;
import java.nio.ByteBuffer;
import java.util.concurrent.Executor;

/** Single-route RAW-only Camera2 session with borrowed GPU and packed plane buffers. */
public final class Camera2RawSession implements AutoCloseable {
    private static final String TAG = "UnspekRawSession";
    private static final int MAX_IMAGES = 3;
    private static final int METADATA_CAPACITY = 24;
    private static final long STILL_CAPTURE_TIMEOUT_MS = 15_000L;

    public enum State {
        IDLE,
        OPENING,
        CONFIGURING,
        STREAMING,
        STILL_CONFIGURING,
        STILL_CAPTURING,
        STILL_CAPTURED,
        STILL_FAILED,
        PREVIEW_RECONFIGURING,
        CLOSING,
        CLOSED,
        UNSUPPORTED,
        ERROR
    }

    public enum ErrorCode {
        API_UNSUPPORTED,
        CAMERA_ACCESS,
        CAMERA_DISCONNECTED,
        CAMERA_DEVICE,
        SESSION_CONFIGURATION,
        REQUEST,
        CAPTURE_FAILURE,
        BUFFER,
        CONSUMER
    }

    public static final class SessionError {
        public final ErrorCode code;
        public final String message;
        public final boolean fatal;
        public final Throwable cause;

        SessionError(ErrorCode code, String message, boolean fatal, Throwable cause) {
            this.code = Objects.requireNonNull(code);
            this.message = Objects.requireNonNull(message);
            this.fatal = fatal;
            this.cause = cause;
        }
    }

    private final CameraManager cameraManager;
    private final TimestampMetadataMatcher<RawCaptureMetadata> metadata =
            new TimestampMetadataMatcher<>(METADATA_CAPACITY);
    private final NewestFrameSlot<Image> pendingImage = new NewestFrameSlot<>(Image::close);

    private volatile State state = State.IDLE;
    private HandlerThread cameraThread;
    private Handler cameraHandler;
    private ImageReader imageReader;
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private CameraCaptureSession closingSession;
    private Runnable afterSessionClosed;
    private CameraRoute route;
    private RawOutput previewOutput;
    private RawOutput output;
    private IntRange aeFpsRange;
    private RawFrameConsumer frameConsumer;
    private RawSessionListener listener;
    private String physicalCameraId;
    private boolean missingWhiteBalanceLogged;
    private boolean requestLensShadingMap;
    private RawStillCaptureCallback stillCaptureCallback;
    private boolean stillFrameDelivered;
    private Runnable stillCaptureTimeout;

    public Camera2RawSession(CameraManager cameraManager) {
        this.cameraManager = Objects.requireNonNull(cameraManager);
    }

    public State state() { return state; }
    public long droppedPendingFrames() { return pendingImage.dropped(); }
    public long metadataEvictions() { return metadata.evictions(); }

    public void captureStill(List<RawOutput> availableOutputs,
                             RawStillCaptureCallback callback) {
        captureStill(availableOutputs, EnumSet.allOf(RawFormat.class), callback);
    }

    public synchronized void captureStill(List<RawOutput> availableOutputs,
                                          Set<RawFormat> importableFormats,
                                          RawStillCaptureCallback callback) {
        RawOutput selected = StillCapturePlanner.select(
                state, availableOutputs, importableFormats);
        Objects.requireNonNull(callback);
        if (selected.maximumResolutionMode && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            throw new IllegalArgumentException(
                    "Maximum-resolution still capture requires Android 12 (API 31) or newer");
        }
        stillCaptureCallback = callback;
        stillFrameDelivered = false;
        transition(State.STILL_CONFIGURING);
        Handler handler = cameraHandler;
        if (handler == null || !handler.post(() -> beginStillReconfiguration(selected))) {
            fail(new SessionError(ErrorCode.REQUEST,
                    "Camera thread closed before still capture could start", true, null));
        }
    }

    public synchronized void resumePreview() {
        if (!StillCapturePlanner.canResume(state)) {
            throw new IllegalStateException("Preview can resume only after still capture completion");
        }
        transition(State.PREVIEW_RECONFIGURING);
        Handler handler = cameraHandler;
        if (handler == null || !handler.post(this::beginPreviewReconfiguration)) {
            fail(new SessionError(ErrorCode.REQUEST,
                    "Camera thread closed before preview could resume", true, null));
        }
    }

    @SuppressLint("MissingPermission")
    public synchronized void start(CameraRoute route, RawOutput output, IntRange aeFpsRange,
                                   RawFrameConsumer consumer, RawSessionListener listener) {
        if (state != State.IDLE) throw new IllegalStateException("Session has already been started");
        this.output = Objects.requireNonNull(output);
        this.previewOutput = output;
        this.route = Objects.requireNonNull(route);
        this.aeFpsRange = Objects.requireNonNull(aeFpsRange);
        this.frameConsumer = Objects.requireNonNull(consumer);
        this.listener = Objects.requireNonNull(listener);
        this.physicalCameraId = route.physicalCameraId;
        this.missingWhiteBalanceLogged = false;

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            unsupported("GPU-usage ImageReader requires Android 10 (API 29) or newer");
            return;
        }
        if (output.maximumResolutionMode && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            unsupported("Maximum-resolution sensor mode requires Android 12 (API 31) or newer");
            return;
        }
        requestLensShadingMap = supportsLensShadingMap(route);

        cameraThread = new HandlerThread("UnspektrawesomeRawCamera");
        cameraThread.start();
        cameraHandler = new Handler(cameraThread.getLooper());
        try {
            imageReader = Api29.createGpuRawReader(output);
            imageReader.setOnImageAvailableListener(this::onPreviewImageAvailable, cameraHandler);
            transition(State.OPENING);
            cameraManager.openCamera(route.openCameraId, deviceCallback(route), cameraHandler);
        } catch (CameraAccessException | SecurityException | IllegalArgumentException error) {
            fail(new SessionError(ErrorCode.CAMERA_ACCESS,
                    "Unable to open RAW camera route " + route.routeId, true, error));
        } catch (RuntimeException error) {
            fail(new SessionError(ErrorCode.BUFFER,
                    "Unable to allocate the GPU RAW ImageReader", true, error));
        }
    }

    private CameraDevice.StateCallback deviceCallback(CameraRoute route) {
        return new CameraDevice.StateCallback() {
            @Override
            public void onOpened(CameraDevice device) {
                if (state == State.CLOSING || state == State.CLOSED || state == State.ERROR) {
                    device.close();
                    return;
                }
                cameraDevice = device;
                configureSession(SessionMode.PREVIEW);
            }

            @Override
            public void onDisconnected(CameraDevice device) {
                device.close();
                fail(new SessionError(ErrorCode.CAMERA_DISCONNECTED,
                        "Camera disconnected while streaming RAW", true, null));
            }

            @Override
            public void onError(CameraDevice device, int error) {
                device.close();
                fail(new SessionError(ErrorCode.CAMERA_DEVICE,
                        "Camera device error " + error, true, null));
            }
        };
    }

    private void configureSession(SessionMode mode) {
        if (mode == SessionMode.PREVIEW && state == State.OPENING) transition(State.CONFIGURING);
        try {
            OutputConfiguration configuration = new OutputConfiguration(imageReader.getSurface());
            if (route.physicalCameraId != null) {
                configuration.setPhysicalCameraId(route.physicalCameraId);
            }
            SessionConfiguration sessionConfiguration = new SessionConfiguration(
                    SessionConfiguration.SESSION_REGULAR,
                    Collections.singletonList(configuration), handlerExecutor(), sessionCallback(mode));
            cameraDevice.createCaptureSession(sessionConfiguration);
        } catch (CameraAccessException | IllegalArgumentException | IllegalStateException error) {
            handleConfigurationFailure(mode, new SessionError(ErrorCode.SESSION_CONFIGURATION,
                    "Unable to configure the RAW-only capture session", mode == SessionMode.PREVIEW,
                    error));
        }
    }

    private CameraCaptureSession.StateCallback sessionCallback(SessionMode mode) {
        return new CameraCaptureSession.StateCallback() {
            @Override
            public void onConfigured(CameraCaptureSession session) {
                if (state == State.CLOSING || state == State.CLOSED || state == State.ERROR) {
                    session.close();
                    return;
                }
                captureSession = session;
                if (mode == SessionMode.PREVIEW) {
                    startRepeating(session);
                } else {
                    issueStillCapture(session);
                }
            }

            @Override
            public void onConfigureFailed(CameraCaptureSession session) {
                session.close();
                handleConfigurationFailure(mode,
                        new SessionError(ErrorCode.SESSION_CONFIGURATION,
                                "Camera rejected the RAW-only output configuration",
                                mode == SessionMode.PREVIEW, null));
            }

            @Override
            public void onClosed(CameraCaptureSession session) {
                onSessionClosed(session);
            }
        };
    }

    private void startRepeating(CameraCaptureSession session) {
        try {
            CaptureRequest.Builder request = cameraDevice.createCaptureRequest(
                    CameraDevice.TEMPLATE_PREVIEW);
            request.addTarget(imageReader.getSurface());
            request.set(CaptureRequest.CONTROL_AWB_MODE,
                    CameraMetadata.CONTROL_AWB_MODE_AUTO);
            request.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                    new Range<>(aeFpsRange.lower, aeFpsRange.upper));
            if (requestLensShadingMap) {
                request.set(CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE,
                        CameraMetadata.STATISTICS_LENS_SHADING_MAP_MODE_ON);
            }
            if (output.maximumResolutionMode && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                request.set(CaptureRequest.SENSOR_PIXEL_MODE,
                        CaptureRequest.SENSOR_PIXEL_MODE_MAXIMUM_RESOLUTION);
            }
            session.setRepeatingRequest(request.build(), captureCallback, cameraHandler);
            boolean resumed = state == State.PREVIEW_RECONFIGURING;
            transition(State.STREAMING);
            if (resumed) {
                RawStillCaptureCallback callback = stillCaptureCallback;
                stillCaptureCallback = null;
                if (callback != null) {
                    try { callback.onPreviewResumed(); }
                    catch (RuntimeException error) {
                        report(new SessionError(ErrorCode.CONSUMER,
                                "Preview-resumed callback failed", false, error));
                    }
                }
            }
        } catch (CameraAccessException | IllegalArgumentException | IllegalStateException error) {
            fail(new SessionError(ErrorCode.REQUEST,
                    "Unable to start the RAW repeating request", true, error));
        }
    }

    private void issueStillCapture(CameraCaptureSession session) {
        try {
            CaptureRequest.Builder request = cameraDevice.createCaptureRequest(
                    CameraDevice.TEMPLATE_STILL_CAPTURE);
            request.addTarget(imageReader.getSurface());
            request.set(CaptureRequest.CONTROL_AWB_MODE,
                    CameraMetadata.CONTROL_AWB_MODE_AUTO);
            if (requestLensShadingMap) {
                request.set(CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE,
                        CameraMetadata.STATISTICS_LENS_SHADING_MAP_MODE_ON);
            }
            if (output.maximumResolutionMode && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                request.set(CaptureRequest.SENSOR_PIXEL_MODE,
                        CaptureRequest.SENSOR_PIXEL_MODE_MAXIMUM_RESOLUTION);
            }
            transition(State.STILL_CAPTURING);
            session.capture(request.build(), stillCaptureResultCallback, cameraHandler);
            scheduleStillCaptureTimeout();
        } catch (CameraAccessException | IllegalArgumentException | IllegalStateException error) {
            stillFailure(new SessionError(ErrorCode.REQUEST,
                    "Unable to issue the full-resolution RAW still request", false, error));
        }
    }

    private void beginStillReconfiguration(RawOutput captureOutput) {
        replaceSession(() -> {
            replaceReader(captureOutput, this::onStillImageAvailable);
            if (state == State.STILL_CONFIGURING) configureSession(SessionMode.STILL);
        });
    }

    private void beginPreviewReconfiguration() {
        replaceSession(() -> {
            replaceReader(previewOutput, this::onPreviewImageAvailable);
            if (state == State.PREVIEW_RECONFIGURING) configureSession(SessionMode.PREVIEW);
        });
    }

    private void replaceSession(Runnable afterClose) {
        pendingImage.clear();
        metadata.clear();
        CameraCaptureSession session = captureSession;
        if (session == null) {
            afterClose.run();
            return;
        }
        afterSessionClosed = afterClose;
        closingSession = session;
        try { session.stopRepeating(); }
        catch (CameraAccessException | IllegalStateException ignored) { }
        try { session.abortCaptures(); }
        catch (CameraAccessException | IllegalStateException ignored) { }
        session.close();
    }

    private void onSessionClosed(CameraCaptureSession session) {
        if (captureSession == session) captureSession = null;
        if (closingSession != session) return;
        closingSession = null;
        Runnable continuation = afterSessionClosed;
        afterSessionClosed = null;
        if (continuation != null && state != State.CLOSING && state != State.CLOSED
                && state != State.ERROR) {
            continuation.run();
        }
    }

    private void replaceReader(RawOutput nextOutput,
                               ImageReader.OnImageAvailableListener imageListener) {
        ImageReader previous = imageReader;
        imageReader = null;
        if (previous != null) {
            previous.setOnImageAvailableListener(null, null);
            previous.close();
        }
        try {
            output = nextOutput;
            imageReader = Api29.createGpuRawReader(nextOutput);
            imageReader.setOnImageAvailableListener(imageListener, cameraHandler);
        } catch (RuntimeException error) {
            if (state == State.STILL_CONFIGURING) {
                stillFailure(new SessionError(ErrorCode.BUFFER,
                        "Unable to allocate the full-resolution GPU RAW ImageReader",
                        false, error));
            } else {
                fail(new SessionError(ErrorCode.BUFFER,
                        "Unable to restore the preview GPU RAW ImageReader", true, error));
            }
        }
    }

    private void handleConfigurationFailure(SessionMode mode, SessionError error) {
        if (mode == SessionMode.STILL) stillFailure(error); else fail(error);
    }

    private void stillFailure(SessionError error) {
        State current = state;
        if (current == State.CLOSING || current == State.CLOSED || current == State.ERROR) return;
        cancelStillCaptureTimeout();
        report(error);
        transition(State.STILL_FAILED);
        RawStillCaptureCallback callback = stillCaptureCallback;
        if (callback != null) {
            try { callback.onStillCaptureError(error); }
            catch (RuntimeException callbackError) {
                report(new SessionError(ErrorCode.CONSUMER,
                        "Still-capture error callback failed", false, callbackError));
            }
        }
    }

    private final CameraCaptureSession.CaptureCallback captureCallback =
            new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request,
                                               TotalCaptureResult result) {
                    if (state != State.STREAMING) return;
                    RawCaptureMetadata captured = captureMetadata(result, physicalCameraId);
                    if (captured == null) return;
                    metadata.put(captured.sensorTimestampNs, captured);
                    NewestFrameSlot.Entry<Image> image =
                            pendingImage.takeIfTimestamp(captured.sensorTimestampNs);
                    if (image != null) {
                        RawCaptureMetadata exact = metadata.take(captured.sensorTimestampNs);
                        deliverPreview(image.value, exact);
                    }
                }

                @Override
                public void onCaptureFailed(CameraCaptureSession session, CaptureRequest request,
                                            CaptureFailure failure) {
                    if (state != State.STREAMING) return;
                    report(new SessionError(ErrorCode.CAPTURE_FAILURE,
                            "RAW capture failed for frame " + failure.getFrameNumber(), false, null));
                }
            };

    private final CameraCaptureSession.CaptureCallback stillCaptureResultCallback =
            new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request,
                                               TotalCaptureResult result) {
                    if (state != State.STILL_CAPTURING || stillFrameDelivered) return;
                    RawCaptureMetadata captured = captureMetadata(result, physicalCameraId);
                    if (captured == null) {
                        stillFailure(new SessionError(ErrorCode.CAPTURE_FAILURE,
                                "RAW still result has no sensor timestamp", false, null));
                        return;
                    }
                    metadata.put(captured.sensorTimestampNs, captured);
                    NewestFrameSlot.Entry<Image> image =
                            pendingImage.takeIfTimestamp(captured.sensorTimestampNs);
                    if (image != null) {
                        RawCaptureMetadata exact = metadata.take(captured.sensorTimestampNs);
                        deliverStill(image.value, exact);
                    }
                }

                @Override
                public void onCaptureFailed(CameraCaptureSession session, CaptureRequest request,
                                            CaptureFailure failure) {
                    if (state != State.STILL_CAPTURING || stillFrameDelivered) return;
                    stillFailure(new SessionError(ErrorCode.CAPTURE_FAILURE,
                            "Full-resolution RAW capture failed for frame "
                                    + failure.getFrameNumber(), false, null));
                }
            };

    private void onPreviewImageAvailable(ImageReader reader) {
        onImageAvailable(reader, false);
    }

    private void onStillImageAvailable(ImageReader reader) {
        onImageAvailable(reader, true);
    }

    private void onImageAvailable(ImageReader reader, boolean stillImage) {
        Image image;
        try {
            image = reader.acquireLatestImage();
        } catch (IllegalStateException error) {
            fail(new SessionError(ErrorCode.BUFFER,
                    "RAW ImageReader could not acquire the newest image", true, error));
            return;
        }
        if (image == null) return;
        if ((!stillImage && state != State.STREAMING)
                || (stillImage && state != State.STILL_CAPTURING)) {
            image.close();
            return;
        }
        long timestamp = image.getTimestamp();
        pendingImage.offer(timestamp, image);
        RawCaptureMetadata exact = metadata.take(timestamp);
        if (exact != null) {
            NewestFrameSlot.Entry<Image> matched = pendingImage.takeIfTimestamp(timestamp);
            if (matched != null) {
                if (stillImage) deliverStill(matched.value, exact);
                else deliverPreview(matched.value, exact);
            }
        }
    }

    private void deliverPreview(Image image, RawCaptureMetadata captured) {
        try (Image ownedImage = image) {
            HardwareBuffer hardwareBuffer = ownedImage.getHardwareBuffer();
            if (hardwareBuffer == null) {
                fail(new SessionError(ErrorCode.BUFFER,
                        "RAW image has no HardwareBuffer", true, null));
                return;
            }
            try (HardwareBuffer borrowedBuffer = hardwareBuffer) {
                Image.Plane[] planes = ownedImage.getPlanes();
                if (planes.length != 1 || planes[0].getRowStride() <= 0) {
                    fail(new SessionError(ErrorCode.BUFFER,
                            "RAW image has no valid row-stride metadata", true, null));
                    return;
                }
                ByteBuffer packedRawBytes = output.format == RawFormat.RAW_SENSOR
                        ? null
                        : PackedRawPlaneBuffer.borrow(
                                planes[0].getBuffer(), planes[0].getRowStride(),
                                ownedImage.getHeight());
                frameConsumer.onRawFrame(new RawHardwareFrame(
                        borrowedBuffer, packedRawBytes, captured, output,
                        planes[0].getRowStride()));
            }
        } catch (RuntimeException error) {
            fail(new SessionError(ErrorCode.CONSUMER,
                    "RAW frame consumer failed during synchronous buffer access", true, error));
        }
    }

    private void deliverStill(Image image, RawCaptureMetadata captured) {
        if (stillFrameDelivered) {
            image.close();
            return;
        }
        stillFrameDelivered = true;
        cancelStillCaptureTimeout();
        try (Image ownedImage = image) {
            HardwareBuffer hardwareBuffer = ownedImage.getHardwareBuffer();
            if (hardwareBuffer == null) {
                stillFailure(new SessionError(ErrorCode.BUFFER,
                        "Full-resolution RAW image has no HardwareBuffer", false, null));
                return;
            }
            try (HardwareBuffer borrowedBuffer = hardwareBuffer) {
                Image.Plane[] planes = ownedImage.getPlanes();
                if (planes.length != 1 || planes[0].getRowStride() <= 0) {
                    stillFailure(new SessionError(ErrorCode.BUFFER,
                            "Full-resolution RAW image has no valid row-stride metadata",
                            false, null));
                    return;
                }
                RawStillCaptureCallback callback = stillCaptureCallback;
                if (callback == null) {
                    stillFailure(new SessionError(ErrorCode.CONSUMER,
                            "Still-capture callback is unavailable", false, null));
                    return;
                }
                ByteBuffer packedRawBytes = output.format == RawFormat.RAW_SENSOR
                        ? null
                        : PackedRawPlaneBuffer.borrow(
                                planes[0].getBuffer(), planes[0].getRowStride(),
                                ownedImage.getHeight());
                transition(State.STILL_CAPTURED);
                callback.onStillFrame(new RawHardwareFrame(
                        borrowedBuffer, packedRawBytes, captured, output,
                        planes[0].getRowStride()));
            }
        } catch (RuntimeException error) {
            SessionError callbackError = new SessionError(ErrorCode.CONSUMER,
                    "Still-capture consumer failed during synchronous buffer access",
                    false, error);
            if (state == State.PREVIEW_RECONFIGURING || state == State.STREAMING) {
                report(callbackError);
            } else {
                stillFailure(callbackError);
            }
        }
    }

    private RawCaptureMetadata captureMetadata(TotalCaptureResult result,
                                               String physicalCameraId) {
        CaptureResult sensorResult = result;
        if (physicalCameraId != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Map<String, TotalCaptureResult> physicalResults =
                    Api31.physicalCameraResults(result);
            TotalCaptureResult physical = physicalResults.get(physicalCameraId);
            if (physical != null) sensorResult = physical;
        }
        Long timestamp = captureValue(sensorResult, result, CaptureResult.SENSOR_TIMESTAMP);
        if (timestamp == null || timestamp <= 0) return null;
        Rational[] neutral = captureValue(
                sensorResult, result, CaptureResult.SENSOR_NEUTRAL_COLOR_POINT);
        float[] neutralValues = null;
        if (neutral != null && neutral.length == 3) {
            float[] candidate = new float[3];
            boolean valid = true;
            for (int index = 0; index < 3; index++) {
                candidate[index] = neutral[index].getDenominator() == 0 ? 0f
                        : neutral[index].getNumerator() / (float) neutral[index].getDenominator();
                valid &= Float.isFinite(candidate[index]) && candidate[index] > 0f;
            }
            if (valid) neutralValues = candidate;
        }
        if (neutralValues == null) {
            RggbChannelVector gains = captureValue(
                    sensorResult, result, CaptureResult.COLOR_CORRECTION_GAINS);
            if (gains != null) {
                float green = (gains.getGreenEven() + gains.getGreenOdd()) * 0.5f;
                if (gains.getRed() > 0f && green > 0f && gains.getBlue() > 0f) {
                    neutralValues = new float[]{
                            1f / gains.getRed(), 1f / green, 1f / gains.getBlue()};
                }
            }
        }
        if (neutralValues == null && !missingWhiteBalanceLogged) {
            missingWhiteBalanceLogged = true;
            Log.w(TAG, "RAW white balance missing for route " + route.routeId
                    + "; logical keys=" + whiteBalanceKeyNames(result)
                    + "; physical keys=" + whiteBalanceKeyNames(sensorResult)
                    + "; AWB state=" + captureValue(sensorResult, result,
                    CaptureResult.CONTROL_AWB_STATE));
        }
        LensShadingData lensShading = lensShadingData(
                captureValue(sensorResult, result,
                        CaptureResult.STATISTICS_LENS_SHADING_CORRECTION_MAP));
        return new RawCaptureMetadata(timestamp, result.getFrameNumber(),
                captureValue(sensorResult, result, CaptureResult.SENSOR_EXPOSURE_TIME),
                captureValue(sensorResult, result, CaptureResult.SENSOR_FRAME_DURATION),
                captureValue(sensorResult, result, CaptureResult.SENSOR_SENSITIVITY),
                captureValue(sensorResult, result, CaptureResult.LENS_FOCUS_DISTANCE),
                captureValue(sensorResult, result, CaptureResult.SENSOR_DYNAMIC_BLACK_LEVEL),
                captureValue(sensorResult, result, CaptureResult.SENSOR_DYNAMIC_WHITE_LEVEL),
                neutralValues,
                lensShading == null ? 0 : lensShading.width,
                lensShading == null ? 0 : lensShading.height,
                lensShading == null ? null : lensShading.gains);
    }

    private static <T> T captureValue(CaptureResult preferred,
                                      TotalCaptureResult fallback,
                                      CaptureResult.Key<T> key) {
        T value = preferred.get(key);
        return value != null ? value : fallback.get(key);
    }

    private static String whiteBalanceKeyNames(CaptureResult result) {
        StringBuilder names = new StringBuilder("[");
        for (CaptureResult.Key<?> key : result.getKeys()) {
            String name = key.getName();
            String lower = name.toLowerCase(java.util.Locale.ROOT);
            if (!lower.contains("awb") && !lower.contains("neutral")
                    && !lower.contains("colorcorrection") && !lower.contains("gain")) {
                continue;
            }
            if (names.length() > 1) names.append(", ");
            names.append(name);
        }
        return names.append(']').toString();
    }

    private static LensShadingData lensShadingData(LensShadingMap map) {
        if (map == null) return null;
        int width = map.getColumnCount();
        int height = map.getRowCount();
        long expected = (long) width * height * 4;
        if (width <= 0 || height <= 0 || expected != map.getGainFactorCount()
                || expected > Integer.MAX_VALUE) return null;
        float[] gains = new float[(int) expected];
        try { map.copyGainFactors(gains, 0); }
        catch (RuntimeException ignored) { return null; }
        for (float gain : gains) {
            if (!Float.isFinite(gain) || gain <= 0f) return null;
        }
        return new LensShadingData(width, height, gains);
    }

    private boolean supportsLensShadingMap(CameraRoute route) {
        int[] routeModes = lensShadingMapModes(characteristics(route.characteristicsCameraId));
        int[] openCameraModes = route.characteristicsCameraId.equals(route.openCameraId)
                ? null
                : lensShadingMapModes(characteristics(route.openCameraId));
        return shouldRequestLensShadingMap(routeModes, openCameraModes);
    }

    static boolean shouldRequestLensShadingMap(int[] routeModes, int[] openCameraModes) {
        return supportsLensShadingMap(routeModes) || supportsLensShadingMap(openCameraModes);
    }

    private static int[] lensShadingMapModes(CameraCharacteristics characteristics) {
        return characteristics == null ? null : characteristics.get(
                CameraCharacteristics.STATISTICS_INFO_AVAILABLE_LENS_SHADING_MAP_MODES);
    }

    private static boolean supportsLensShadingMap(int[] modes) {
        if (modes == null) return false;
        for (int mode : modes) {
            if (mode == CameraMetadata.STATISTICS_LENS_SHADING_MAP_MODE_ON) return true;
        }
        return false;
    }

    private CameraCharacteristics characteristics(String cameraId) {
        try { return cameraManager.getCameraCharacteristics(cameraId); }
        catch (CameraAccessException | IllegalArgumentException | SecurityException ignored) {
            return null;
        }
    }

    private Executor handlerExecutor() {
        return command -> {
            Handler handler = cameraHandler;
            if (handler == null || !handler.post(command)) {
                throw new IllegalStateException("Camera callback thread is closed");
            }
        };
    }

    private void scheduleStillCaptureTimeout() {
        cancelStillCaptureTimeout();
        Runnable timeout = () -> {
            if (state == State.STILL_CAPTURING && !stillFrameDelivered) {
                stillFailure(new SessionError(ErrorCode.CAPTURE_FAILURE,
                        "Timed out waiting for the full-resolution RAW image and metadata",
                        false, null));
            }
        };
        stillCaptureTimeout = timeout;
        cameraHandler.postDelayed(timeout, STILL_CAPTURE_TIMEOUT_MS);
    }

    private void cancelStillCaptureTimeout() {
        Runnable timeout = stillCaptureTimeout;
        stillCaptureTimeout = null;
        Handler handler = cameraHandler;
        if (timeout != null && handler != null) handler.removeCallbacks(timeout);
    }

    private void unsupported(String message) {
        transition(State.UNSUPPORTED);
        report(new SessionError(ErrorCode.API_UNSUPPORTED, message, true, null));
    }

    private void fail(SessionError error) {
        State current = state;
        if (current == State.CLOSING || current == State.CLOSED || current == State.ERROR) return;
        report(error);
        transition(State.ERROR);
        cleanup(State.ERROR);
    }

    private void report(SessionError error) {
        RawSessionListener current = listener;
        if (current == null) return;
        try { current.onError(error); }
        catch (RuntimeException ignored) { }
    }

    private void transition(State next) {
        state = next;
        RawSessionListener current = listener;
        if (current == null) return;
        try { current.onStateChanged(next); }
        catch (RuntimeException ignored) { }
    }

    @Override
    public synchronized void close() {
        if (state == State.CLOSED || state == State.CLOSING) return;
        if (state == State.IDLE || state == State.UNSUPPORTED) {
            transition(State.CLOSED);
            return;
        }
        transition(State.CLOSING);
        Handler handler = cameraHandler;
        if (handler != null && Looper.myLooper() != handler.getLooper() && handler.post(
                () -> cleanup(State.CLOSED))) {
            return;
        }
        cleanup(State.CLOSED);
    }

    private void cleanup(State terminalState) {
        cancelStillCaptureTimeout();
        afterSessionClosed = null;
        closingSession = null;
        CameraCaptureSession session = captureSession;
        captureSession = null;
        if (session != null) {
            try { session.stopRepeating(); }
            catch (CameraAccessException | IllegalStateException ignored) { }
            session.close();
        }
        CameraDevice device = cameraDevice;
        cameraDevice = null;
        if (device != null) device.close();
        pendingImage.clear();
        metadata.clear();
        ImageReader reader = imageReader;
        imageReader = null;
        if (reader != null) {
            reader.setOnImageAvailableListener(null, null);
            reader.close();
        }
        HandlerThread thread = cameraThread;
        cameraThread = null;
        cameraHandler = null;
        if (thread != null) thread.quitSafely();
        stillCaptureCallback = null;
        transition(terminalState);
    }

    private static final class Api29 {
        @TargetApi(Build.VERSION_CODES.Q)
        static ImageReader createGpuRawReader(RawOutput output) {
            long usage = output.format == RawFormat.RAW_SENSOR
                    ? HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE
                    : HardwareBuffer.USAGE_CPU_READ_OFTEN;
            return ImageReader.newInstance(output.size.width, output.size.height,
                    output.format.imageFormat, MAX_IMAGES, usage);
        }
    }

    private static final class Api31 {
        @TargetApi(Build.VERSION_CODES.S)
        static Map<String, TotalCaptureResult> physicalCameraResults(
                TotalCaptureResult result) {
            return result.getPhysicalCameraTotalResults();
        }
    }

    private static final class LensShadingData {
        final int width;
        final int height;
        final float[] gains;

        LensShadingData(int width, int height, float[] gains) {
            this.width = width;
            this.height = height;
            this.gains = gains;
        }
    }

    private enum SessionMode { PREVIEW, STILL }
}
