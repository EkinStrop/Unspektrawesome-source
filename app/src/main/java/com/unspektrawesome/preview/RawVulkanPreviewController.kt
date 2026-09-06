package com.unspektrawesome.preview

import android.content.Context
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.SystemClock
import android.view.Surface
import android.view.WindowManager
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.unspektrawesome.camera.CameraDescriptor
import com.unspektrawesome.camera.RawFormat
import com.unspektrawesome.camera.RawPreviewPolicy
import com.unspektrawesome.camera.Size2d
import com.unspektrawesome.camera.session.Camera2RawSession
import com.unspektrawesome.camera.session.RawHardwareFrame
import com.unspektrawesome.camera.session.RawStillCaptureCallback
import com.unspektrawesome.capture.CaptureGeometry
import com.unspektrawesome.capture.CaptureMetadata
import com.unspektrawesome.capture.FrameGeometrySnapshot
import com.unspektrawesome.capture.JpegMediaStoreWriter
import com.unspektrawesome.capture.ProcessedImage
import com.unspektrawesome.settings.CameraPreferences
import com.unspektrawesome.spektra.SpektraCaptureSnapshot
import com.unspektrawesome.spektra.SpektraFilmParams
import com.unspektrawesome.spektra.SpektraState
import com.unspektrawesome.spektra.SpektraStateRepository
import com.unspektrawesome.vulkan.VulkanRenderer
import com.unspektrawesome.vulkan.params.FrameSeedSnapshot
import com.unspektrawesome.vulkan.params.GeometryShaderParameters
import com.unspektrawesome.vulkan.params.LensShadingMapParameters
import com.unspektrawesome.vulkan.params.RawShaderParameters
import com.unspektrawesome.vulkan.params.RawColorShaderParameters
import com.unspektrawesome.vulkan.params.VulkanCaptureSnapshot
import com.unspektrawesome.vulkan.params.WhiteBalanceGains
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors

enum class RawPreviewPhase {
    IDLE, PAUSED, WAITING_FOR_SURFACE, OPENING, STREAMING, ERROR, CLOSED,
}

data class RawPreviewStatus(
    val phase: RawPreviewPhase = RawPreviewPhase.IDLE,
    val message: String = "RAW preview is idle",
    val rendererDiagnostic: String = "Renderer has not attached a surface",
    val renderedFrames: Long = 0,
    val skippedFrames: Long = 0,
    val droppedCameraFrames: Long = 0,
    val spektraRevision: Long? = null,
)

enum class RawCapturePhase {
    IDLE, CAPTURING, SAVING, SUCCEEDED, ERROR,
}

data class RawCaptureStatus(
    val phase: RawCapturePhase = RawCapturePhase.IDLE,
    val message: String = "Ready to capture",
    val revisionId: Long? = null,
    val lastUri: Uri? = null,
)

/** Owns Camera2 RAW streaming and synchronous borrowed-buffer Vulkan rendering. */
class RawVulkanPreviewController(
    context: Context,
    private val spektraRepository: SpektraStateRepository,
) : DefaultLifecycleObserver, AutoCloseable {
    private val lock = Any()
    private val cameraManager = context.applicationContext
        .getSystemService(CameraManager::class.java)
    private val renderer = VulkanRenderer()
    private val jpegWriter = JpegMediaStoreWriter(context.applicationContext.contentResolver)
    private val captureWriter = Executors.newSingleThreadExecutor { task ->
        Thread(task, "UnspektrawesomeJpegWriter")
    }
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val mutableStatus = MutableStateFlow(RawPreviewStatus())
    private val mutableCaptureStatus = MutableStateFlow(RawCaptureStatus())
    private val mutableRawImportCapabilities =
        MutableStateFlow<Map<RawImportStreamKey, RawImportCapability>>(emptyMap())

    private var configuredCamera: CameraDescriptor? = null
    private var configuredPreferences = CameraPreferences()
    private var active: ActiveSession? = null
    private var attachedSurface: Surface? = null
    private var viewportSize: Size2d? = null
    private var resumed = false
    private var closed = false
    private var blockedConfiguration: ConfigurationKey? = null
    private var latestWhiteBalance: WhiteBalanceGains? = null
    private var latestLensShadingMap: LensShadingMapParameters? = null
    private var pendingCapture: PendingCapture? = null
    private var renderedFrames = 0L
    private var skippedFrames = 0L
    private val rawParameterBuffer = directBuffer(RawShaderParameters.SIZE_BYTES)
    private val rawColorParameterBuffer = directBuffer(RawColorShaderParameters.SIZE_BYTES)
    private val geometryParameterBuffer = directBuffer(GeometryShaderParameters.SIZE_BYTES)
    private val previewSpektraParameterBuffer = directBuffer(SpektraFilmParams.STRUCT_SIZE)
    private var previewSpektraRevision = Long.MIN_VALUE
    private var lensShadingBuffer: ByteBuffer? = null
    private var lastSuccessPublishAtMs = 0L

    val status: StateFlow<RawPreviewStatus> = mutableStatus.asStateFlow()
    val captureStatus: StateFlow<RawCaptureStatus> = mutableCaptureStatus.asStateFlow()
    val rawImportCapabilities: StateFlow<Map<RawImportStreamKey, RawImportCapability>> =
        mutableRawImportCapabilities.asStateFlow()

    fun captureStill(): Boolean = synchronized(lock) {
        if (closed || !resumed) {
            publishCaptureErrorLocked("RAW preview is not active")
            return@synchronized false
        }
        if (pendingCapture != null || mutableCaptureStatus.value.phase in BUSY_CAPTURE_PHASES) {
            return@synchronized false
        }
        val owner = active
        if (owner == null || owner.session.state() != Camera2RawSession.State.STREAMING) {
            publishCaptureErrorLocked("RAW preview is not ready for capture")
            return@synchronized false
        }
        val request = runCatching {
            val snapshot = spektraRepository.captureSnapshot()
            val transform = CaptureGeometry.transform(
                owner.camera.sensorMetadata.orientationDegrees,
                displayRotationDegrees(),
                owner.camera.facing,
            )
            val captureOutput = RawPreviewPolicy.selectCaptureOutput(
                owner.camera.rawOutputs,
                VulkanRawImportPolicy.candidateFormats,
            )
            val geometry = FrameGeometrySnapshot.create(
                owner.camera.sensorMetadata,
                captureOutput,
                transform,
                requireNotNull(viewportSize) { "Viewfinder dimensions are unavailable" },
            )
            PendingCapture(
                owner,
                snapshot,
                captureOutput,
                geometry,
                requireNotNull(latestLensShadingMap) {
                    "No valid lens-shading map has arrived for the selected lens"
                },
                snapshot.params.toByteBuffer(),
                System.nanoTime() * NANOSECONDS_TO_SECONDS,
                System.currentTimeMillis(),
            )
        }.getOrElse { error ->
            publishCaptureErrorLocked(
                "Full-resolution RAW capture is unavailable: ${error.message ?: error.javaClass.simpleName}",
            )
            return@synchronized false
        }
        pendingCapture = request
        mutableCaptureStatus.value = RawCaptureStatus(
            phase = RawCapturePhase.CAPTURING,
            message = "Capturing full-resolution RAW",
            revisionId = request.snapshot.revisionId,
            lastUri = mutableCaptureStatus.value.lastUri,
        )
        runCatching {
            owner.session.captureStill(
                owner.camera.rawOutputs,
                VulkanRawImportPolicy.candidateFormats,
                stillCallback(request),
            )
        }.onFailure { error ->
            pendingCapture = null
            publishCaptureErrorLocked(
                "Full-resolution RAW request failed: ${error.message ?: error.javaClass.simpleName}",
                request.snapshot.revisionId,
            )
        }.isSuccess
    }

    fun configure(camera: CameraDescriptor?, preferences: CameraPreferences) = synchronized(lock) {
        check(!closed) { "Preview controller is closed" }
        val changed = configuredCamera?.route?.routeId != camera?.route?.routeId ||
            configuredPreferences.previewQuality != preferences.previewQuality ||
            configuredPreferences.rawPreviewStreams != preferences.rawPreviewStreams
        configuredCamera = camera
        configuredPreferences = preferences
        if (changed) {
            blockedConfiguration = null
            latestWhiteBalance = null
            latestLensShadingMap = null
        }
        reconcileLocked()
    }

    fun attachSurface(surface: Surface) = synchronized(lock) {
        check(!closed) { "Preview controller is closed" }
        if (!surface.isValid) {
            publishLocked(RawPreviewPhase.WAITING_FOR_SURFACE, "Viewfinder surface is not valid")
            return@synchronized
        }
        if (attachedSurface === surface) return@synchronized
        stopActiveLocked()
        attachedSurface = surface
        blockedConfiguration = null
        if (!renderer.setSurface(surface)) {
            attachedSurface = null
            publishLocked(RawPreviewPhase.ERROR, "Vulkan rejected the viewfinder surface")
            return@synchronized
        }
        reconcileLocked()
    }

    fun updateViewport(width: Int, height: Int) = synchronized(lock) {
        if (closed || width <= 0 || height <= 0) return@synchronized
        viewportSize = Size2d(width, height)
    }

    fun detachSurface(surface: Surface? = null) = synchronized(lock) {
        if (closed || (surface != null && attachedSurface !== surface)) return@synchronized
        stopActiveLocked()
        attachedSurface = null
        renderer.setSurface(null)
        publishLocked(RawPreviewPhase.WAITING_FOR_SURFACE, "Waiting for viewfinder surface")
    }

    override fun onResume(owner: LifecycleOwner) = synchronized(lock) {
        if (closed) return@synchronized
        resumed = true
        blockedConfiguration = null
        reconcileLocked()
    }

    override fun onPause(owner: LifecycleOwner) = synchronized(lock) {
        if (closed) return@synchronized
        resumed = false
        stopActiveLocked()
        publishLocked(RawPreviewPhase.PAUSED, "RAW preview is paused")
    }

    override fun onDestroy(owner: LifecycleOwner) {
        close()
    }

    override fun close() = synchronized(lock) {
        if (closed) return@synchronized
        closed = true
        resumed = false
        active?.session?.close()
        active = null
        attachedSurface = null
        pendingCapture = null
        renderer.close()
        captureWriter.shutdownNow()
        publishLocked(RawPreviewPhase.CLOSED, "RAW preview is closed")
    }

    private fun stillCallback(request: PendingCapture) = object : RawStillCaptureCallback {
        override fun onStillFrame(frame: RawHardwareFrame) {
            processStillFrame(request, frame)
        }

        override fun onPreviewResumed() = Unit

        override fun onStillCaptureError(error: Camera2RawSession.SessionError) = synchronized(lock) {
            if (pendingCapture !== request) return@synchronized
            pendingCapture = null
            publishCaptureErrorLocked(
                "${error.code}: ${error.message}",
                request.snapshot.revisionId,
            )
            resumePreviewAfterStillLocked(request.owner)
        }
    }

    private fun processStillFrame(request: PendingCapture, frame: RawHardwareFrame) = synchronized(lock) {
        if (closed || pendingCapture !== request || active !== request.owner) {
            pendingCapture = null
            return@synchronized
        }
        val processed = runCatching {
            runCatching { WhiteBalanceGains.fromRawCaptureMetadata(frame.metadata) }
                .getOrNull()?.also { latestWhiteBalance = it }
            check(frame.output == request.captureOutput) {
                "Still RAW output does not match the shutter geometry snapshot"
            }
            val whiteBalance = WhiteBalanceGains.fromRawCaptureMetadata(frame.metadata)
                .also { latestWhiteBalance = it }
            val captureLensShading = LensShadingMapParameters.preferCaptureMetadata(
                frame.metadata,
                request.lensShadingMap,
            ).also { latestLensShadingMap = it }
            val captureSnapshot = VulkanCaptureSnapshot.from(request.snapshot, frame.metadata)
            val raw = RawShaderParameters.forCapture(
                frame.output,
                request.owner.camera.sensorMetadata,
                frame.metadata,
                frame.rowStrideBytes,
                captureSnapshot,
            )
            val rawColor = RawColorShaderParameters.fromCaptureSnapshot(
                captureSnapshot,
                request.owner.camera.sensorMetadata,
                whiteBalance,
                captureLensShading,
            )
            val outputSize = request.geometry.outputSize
            val rgba = directBuffer(CaptureGeometry.rgbaBufferSize(outputSize))
            val rendered = renderer.captureRcd(
                frame.hardwareBuffer,
                frame.packedRawBytes,
                raw.writeTo(rawParameterBuffer),
                rawColor.writeTo(rawColorParameterBuffer),
                GeometryShaderParameters.from(request.geometry).writeTo(geometryParameterBuffer),
                request.spektraParameters,
                lensShadingByteBuffer(captureLensShading),
                rgba,
                request.spektraTimeSeconds,
            )
            check(rendered) { renderer.diagnostics() }
            ProcessedImage(outputSize.width, outputSize.height, rgba)
        }

        resumePreviewAfterStillLocked(request.owner)
        processed.onSuccess { image ->
            mutableCaptureStatus.value = RawCaptureStatus(
                phase = RawCapturePhase.SAVING,
                message = "Saving full-resolution RCD capture",
                revisionId = request.snapshot.revisionId,
                lastUri = mutableCaptureStatus.value.lastUri,
            )
            saveCapture(request, frame, image)
        }.onFailure { error ->
            pendingCapture = null
            publishCaptureErrorLocked(
                "Vulkan RCD capture failed: ${error.message ?: error.javaClass.simpleName}",
                request.snapshot.revisionId,
            )
        }
    }

    private fun saveCapture(
        request: PendingCapture,
        frame: RawHardwareFrame,
        image: ProcessedImage,
    ) {
        val metadata = CaptureMetadata(
            sensorTimestampNs = frame.metadata.sensorTimestampNs,
            exposureTimeNs = frame.metadata.exposureTimeNs,
            sensitivityIso = frame.metadata.sensitivityIso,
            focalLengthMm = request.owner.camera.focalLengthMm,
            lensModel = request.owner.camera.displayName(),
        )
        captureWriter.execute {
            val result = runCatching {
                jpegWriter.write(
                    image,
                    metadata,
                    capturedAtMillis = request.capturedAtMillis,
                )
            }
            synchronized(lock) {
                if (pendingCapture !== request) return@synchronized
                pendingCapture = null
                result.onSuccess { uri ->
                    mutableCaptureStatus.value = RawCaptureStatus(
                        phase = RawCapturePhase.SUCCEEDED,
                        message = "Saved full-resolution RCD capture",
                        revisionId = request.snapshot.revisionId,
                        lastUri = uri,
                    )
                }.onFailure { error ->
                    publishCaptureErrorLocked(
                        "JPEG save failed: ${error.message ?: error.javaClass.simpleName}",
                        request.snapshot.revisionId,
                    )
                }
            }
        }
    }

    private fun resumePreviewAfterStillLocked(owner: ActiveSession) {
        if (closed || active !== owner) return
        runCatching { owner.session.resumePreview() }.onFailure { error ->
            publishCaptureErrorLocked(
                "RAW preview resume failed: ${error.message ?: error.javaClass.simpleName}",
                pendingCapture?.snapshot?.revisionId,
            )
        }
    }

    private fun publishCaptureErrorLocked(message: String, revisionId: Long? = null) {
        mutableCaptureStatus.value = RawCaptureStatus(
            phase = RawCapturePhase.ERROR,
            message = message,
            revisionId = revisionId,
            lastUri = mutableCaptureStatus.value.lastUri,
        )
    }

    @Suppress("DEPRECATION")
    private fun displayRotationDegrees(): Int = when (windowManager.defaultDisplay.rotation) {
        Surface.ROTATION_0 -> 0
        Surface.ROTATION_90 -> 90
        Surface.ROTATION_180 -> 180
        Surface.ROTATION_270 -> 270
        else -> 0
    }

    private fun reconcileLocked() {
        if (closed) return
        val camera = configuredCamera
        val surface = attachedSurface
        if (!resumed) {
            stopActiveLocked()
            publishLocked(RawPreviewPhase.PAUSED, "RAW preview is paused")
            return
        }
        if (surface == null || !surface.isValid) {
            stopActiveLocked()
            publishLocked(RawPreviewPhase.WAITING_FOR_SURFACE, "Waiting for viewfinder surface")
            return
        }
        if (camera == null) {
            stopActiveLocked()
            publishLocked(RawPreviewPhase.IDLE, "Select a RAW camera")
            return
        }

        val plan = runCatching {
            RawPreviewPlanSelector.select(
                camera,
                configuredPreferences,
                importCapabilities = mutableRawImportCapabilities.value,
            )
        }.getOrElse { error ->
            stopActiveLocked()
            publishLocked(
                RawPreviewPhase.ERROR,
                "RAW preview selection failed: ${error.message ?: error.javaClass.simpleName}",
            )
            return
        }
        val key = ConfigurationKey(camera.route.routeId, plan)
        if (blockedConfiguration == key) return
        val current = active
        if (current != null) {
            if (current.key == key && current.session.state() !in TERMINAL_SESSION_STATES) return
            stopActiveLocked()
            return
        }
        startLocked(camera, plan, key)
    }

    private fun startLocked(
        camera: CameraDescriptor,
        plan: RawPreviewPolicy.Plan,
        key: ConfigurationKey,
    ) {
        val session = Camera2RawSession(cameraManager)
        val owner = ActiveSession(session, camera, plan, key)
        active = owner
        publishLocked(
            RawPreviewPhase.OPENING,
            "Opening ${camera.displayName()} at ${plan.processingSize}",
        )
        session.start(
            camera.route,
            plan.acquisitionOutput,
            plan.aeTargetRange,
            { frame -> renderFrame(owner, frame) },
            object : com.unspektrawesome.camera.session.RawSessionListener {
                override fun onStateChanged(state: Camera2RawSession.State) {
                    handleSessionState(owner, state)
                }

                override fun onError(error: Camera2RawSession.SessionError) {
                    handleSessionError(owner, error)
                }
            },
        )
    }

    private fun stopActiveLocked() {
        val current = active ?: return
        if (pendingCapture?.owner === current &&
            mutableCaptureStatus.value.phase == RawCapturePhase.CAPTURING
        ) {
            val revision = pendingCapture?.snapshot?.revisionId
            pendingCapture = null
            publishCaptureErrorLocked("RAW capture was canceled", revision)
        }
        current.session.close()
        if (current.session.state() in TERMINAL_SESSION_STATES) active = null
    }

    private fun handleSessionState(owner: ActiveSession, state: Camera2RawSession.State) =
        synchronized(lock) {
            if (closed || active !== owner) return@synchronized
            when (state) {
                Camera2RawSession.State.OPENING, Camera2RawSession.State.CONFIGURING ->
                    publishLocked(RawPreviewPhase.OPENING, "Configuring RAW-only Camera2 session")
                Camera2RawSession.State.STREAMING ->
                    publishLocked(RawPreviewPhase.STREAMING, "RAW Vulkan preview streaming")
                Camera2RawSession.State.CLOSED -> {
                    active = null
                    reconcileLocked()
                }
                Camera2RawSession.State.UNSUPPORTED, Camera2RawSession.State.ERROR -> {
                    if (pendingCapture?.owner === owner) {
                        val revision = pendingCapture?.snapshot?.revisionId
                        pendingCapture = null
                        publishCaptureErrorLocked("RAW session ended during capture", revision)
                    }
                    active = null
                    blockedConfiguration = owner.key
                }
                else -> Unit
            }
        }

    private fun handleSessionError(
        owner: ActiveSession,
        error: Camera2RawSession.SessionError,
    ) = synchronized(lock) {
        if (closed || active !== owner) return@synchronized
        if (error.fatal) {
            blockedConfiguration = owner.key
            if (error.code == Camera2RawSession.ErrorCode.BUFFER) {
                val reason = buildString {
                    append("${error.code}: ${error.message}")
                    error.cause?.message?.takeIf(String::isNotBlank)?.let {
                        append(": $it")
                    }
                }
                recordRawImportCapability(
                    owner,
                    RawImportCapability(RawImportCapabilityState.INCOMPATIBLE, reason),
                )
            }
        }
        publishLocked(
            RawPreviewPhase.ERROR,
            "${error.code}: ${error.message}",
            owner,
        )
    }

    private fun renderFrame(owner: ActiveSession, frame: RawHardwareFrame) = synchronized(lock) {
        if (closed || !resumed || attachedSurface == null || active !== owner) {
            skippedFrames++
            return@synchronized
        }
        runCatching {
            runCatching { WhiteBalanceGains.fromRawCaptureMetadata(frame.metadata) }
                .getOrNull()?.let { latestWhiteBalance = it }
            runCatching { LensShadingMapParameters.fromRawCaptureMetadataOrNull(frame.metadata) }
                .getOrNull()?.let { latestLensShadingMap = it }

            val whiteBalance = latestWhiteBalance ?: run {
                skippedFrames++
                publishLocked(
                    RawPreviewPhase.OPENING,
                    "Waiting for valid RAW white-balance metadata: " +
                        (frame.metadata.neutralColorPoint()?.contentToString() ?: "missing"),
                    owner,
                )
                return@synchronized
            }
            val state = spektraRepository.captureSnapshot()
            val capture = VulkanCaptureSnapshot.from(state, frame.metadata)
            val geometry = FrameGeometrySnapshot.create(
                owner.camera.sensorMetadata,
                frame.output,
                CaptureGeometry.transform(
                    owner.camera.sensorMetadata.orientationDegrees,
                    displayRotationDegrees(),
                    owner.camera.facing,
                ),
                requireNotNull(viewportSize) { "Viewfinder dimensions are unavailable" },
                owner.plan.processingSize,
            )
            val raw = RawShaderParameters.forPreview(
                frame.output,
                owner.camera.sensorMetadata,
                frame.metadata,
                frame.rowStrideBytes,
                geometry.outputSize,
                FrameSeedSnapshot.fromFrameNumber(frame.metadata.frameNumber),
            )
            val rawColor = RawColorShaderParameters.fromCaptureSnapshot(
                capture,
                owner.camera.sensorMetadata,
                whiteBalance,
                latestLensShadingMap,
            )
            val rendered = renderer.render(
                frame.hardwareBuffer,
                frame.packedRawBytes,
                raw.writeTo(rawParameterBuffer),
                rawColor.writeTo(rawColorParameterBuffer),
                GeometryShaderParameters.from(geometry).writeTo(geometryParameterBuffer),
                previewSpektraParameters(state.state),
                lensShadingByteBuffer(),
                geometry.outputSize.width,
                geometry.outputSize.height,
            )
            if (rendered) {
                recordRawImportCapability(
                    owner,
                    RawImportCapability(
                        RawImportCapabilityState.COMPATIBLE,
                        when (frame.output.format) {
                            RawFormat.RAW_SENSOR ->
                                "Actual Camera2 RAW_SENSOR HardwareBuffer imported as a Vulkan R16_UINT image"
                            RawFormat.RAW10, RawFormat.RAW12 ->
                                "Actual Camera2 ${frame.output.format} plane uploaded to a Vulkan storage buffer"
                        },
                    ),
                )
                renderedFrames++
                val now = SystemClock.elapsedRealtime()
                val previous = mutableStatus.value
                if (previous.phase != RawPreviewPhase.STREAMING ||
                    previous.spektraRevision != state.revisionId ||
                    now - lastSuccessPublishAtMs >= STATUS_PUBLISH_INTERVAL_MS
                ) {
                    lastSuccessPublishAtMs = now
                    publishLocked(
                        RawPreviewPhase.STREAMING,
                        "RAW Vulkan preview streaming",
                        owner,
                        state.revisionId,
                    )
                }
            } else {
                skippedFrames++
                val diagnostic = renderer.diagnostics()
                val importRejection =
                    RawImportDiagnosticClassifier.importRejectionReason(diagnostic)
                if (importRejection != null) {
                    recordRawImportCapability(
                        owner,
                        RawImportCapability(
                            RawImportCapabilityState.INCOMPATIBLE,
                            importRejection,
                        ),
                    )
                    blockedConfiguration = owner.key
                    publishLocked(
                        RawPreviewPhase.ERROR,
                        "${frame.output.format} ${frame.output.size.width} x " +
                            "${frame.output.size.height} cannot use Vulkan zero-copy import: " +
                            importRejection,
                        owner,
                    )
                    owner.session.close()
                } else {
                    publishLocked(
                        RawPreviewPhase.ERROR,
                        "Vulkan RAW error: $diagnostic",
                        owner,
                    )
                }
            }
        }.onFailure { error ->
            skippedFrames++
            publishLocked(
                RawPreviewPhase.ERROR,
                "RAW frame render failed: ${error.message ?: error.javaClass.simpleName}",
                owner,
            )
        }
    }

    private fun lensShadingByteBuffer(
        map: LensShadingMapParameters? = latestLensShadingMap,
    ): ByteBuffer? {
        map ?: return null
        val current = lensShadingBuffer
        val target = if (current == null || current.capacity() < map.sizeBytes) {
            directBuffer(map.sizeBytes).also { lensShadingBuffer = it }
        } else {
            current
        }
        return map.writeTo(target)
    }

    private fun previewSpektraParameters(state: SpektraState): ByteBuffer {
        if (previewSpektraRevision != state.revisionId) {
            state.params.writeTo(previewSpektraParameterBuffer)
            previewSpektraRevision = state.revisionId
        } else {
            previewSpektraParameterBuffer.position(0)
            previewSpektraParameterBuffer.limit(SpektraFilmParams.STRUCT_SIZE)
        }
        return previewSpektraParameterBuffer
    }

    private fun recordRawImportCapability(
        owner: ActiveSession,
        capability: RawImportCapability,
    ) {
        val key = RawImportStreamKey.from(
            owner.camera.route.routeId,
            owner.plan.acquisitionOutput,
        )
        if (mutableRawImportCapabilities.value[key] == capability) return
        mutableRawImportCapabilities.value =
            mutableRawImportCapabilities.value + (key to capability)
    }

    private fun publishLocked(
        phase: RawPreviewPhase,
        message: String,
        owner: ActiveSession? = active,
        revision: Long? = mutableStatus.value.spektraRevision,
    ) {
        mutableStatus.value = RawPreviewStatus(
            phase = phase,
            message = message,
            rendererDiagnostic = renderer.diagnostics(),
            renderedFrames = renderedFrames,
            skippedFrames = skippedFrames,
            droppedCameraFrames = owner?.session?.droppedPendingFrames() ?: 0L,
            spektraRevision = revision,
        )
    }

    private data class ConfigurationKey(
        val routeId: String,
        val acquisitionOutput: com.unspektrawesome.camera.RawOutput,
        val processingWidth: Int,
        val processingHeight: Int,
        val framesPerSecond: Int,
    ) {
        constructor(routeId: String, plan: RawPreviewPolicy.Plan) : this(
            routeId,
            plan.acquisitionOutput,
            plan.processingSize.width,
            plan.processingSize.height,
            plan.framesPerSecond,
        )
    }

    private data class ActiveSession(
        val session: Camera2RawSession,
        val camera: CameraDescriptor,
        val plan: RawPreviewPolicy.Plan,
        val key: ConfigurationKey,
    )

    private data class PendingCapture(
        val owner: ActiveSession,
        val snapshot: SpektraCaptureSnapshot,
        val captureOutput: com.unspektrawesome.camera.RawOutput,
        val geometry: FrameGeometrySnapshot,
        val lensShadingMap: LensShadingMapParameters,
        val spektraParameters: ByteBuffer,
        val spektraTimeSeconds: Double,
        val capturedAtMillis: Long,
    )

    private companion object {
        const val STATUS_PUBLISH_INTERVAL_MS = 1_000L
        const val NANOSECONDS_TO_SECONDS = 1e-9

        val BUSY_CAPTURE_PHASES = setOf(RawCapturePhase.CAPTURING, RawCapturePhase.SAVING)

        fun directBuffer(size: Int): ByteBuffer = ByteBuffer.allocateDirect(size)
            .order(ByteOrder.nativeOrder())

        val TERMINAL_SESSION_STATES = setOf(
            Camera2RawSession.State.CLOSED,
            Camera2RawSession.State.UNSUPPORTED,
            Camera2RawSession.State.ERROR,
        )
    }
}
