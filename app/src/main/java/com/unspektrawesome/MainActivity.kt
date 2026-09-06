package com.unspektrawesome

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.unspektrawesome.camera.CameraCatalog
import com.unspektrawesome.camera.CameraDescriptor
import com.unspektrawesome.settings.CameraPreferences
import com.unspektrawesome.settings.CameraPreferencesStore
import com.unspektrawesome.settings.LensButtonMapping
import com.unspektrawesome.preview.RawPreviewPhase
import com.unspektrawesome.preview.RawCapturePhase
import com.unspektrawesome.preview.RawVulkanPreviewController
import com.unspektrawesome.preview.VulkanRawPreviewView
import com.unspektrawesome.spektra.SharedPreferencesSpektraStatePersistence
import com.unspektrawesome.spektra.SpektraStateRepository
import com.unspektrawesome.ui.camera.CameraScreen
import com.unspektrawesome.ui.camera.UnspektrawesomeTheme
import com.unspektrawesome.vulkan.VulkanBridge
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val spektraRepository by lazy {
        SpektraStateRepository(
            SharedPreferencesSpektraStatePersistence(
                getSharedPreferences("spektra", MODE_PRIVATE),
            ),
        )
    }
    private val cameraPreferencesStore by lazy { CameraPreferencesStore(this) }
    private val previewController by lazy {
        RawVulkanPreviewController(this, spektraRepository)
    }

    private var cameras by mutableStateOf<List<CameraDescriptor>>(emptyList())
    private var cameraPreferences by mutableStateOf(CameraPreferences())
    private var selectedLensId by mutableStateOf<String?>(null)
    private var status by mutableStateOf("Checking Vulkan")
    private var vulkanDiagnostics by mutableStateOf("Vulkan probe has not run")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        cameraPreferences = cameraPreferencesStore.load()
        selectedLensId = cameraPreferences.defaultLensId
        probeVulkan()
        setContent {
            val previewStatus by previewController.status.collectAsState()
            val captureStatus by previewController.captureStatus.collectAsState()
            val rawImportCapabilities by previewController.rawImportCapabilities.collectAsState()
            val selectedCamera = cameras.firstOrNull { it.route.routeId == selectedLensId }
            val previewView = remember { VulkanRawPreviewView(this@MainActivity) }
            LaunchedEffect(selectedCamera, cameraPreferences, hasCameraPermission()) {
                previewController.configure(
                    selectedCamera.takeIf { hasCameraPermission() },
                    cameraPreferences,
                )
            }
            DisposableEffect(previewView) {
                previewView.bind(previewController, this@MainActivity)
                onDispose { previewView.unbind() }
            }
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { granted ->
                if (granted) discoverCameras() else status = "Camera permission denied"
            }
            UnspektrawesomeTheme {
                CameraScreen(
                    cameras = cameras,
                    selectedLensId = selectedLensId,
                    preferences = cameraPreferences,
                    status = when {
                        captureStatus.phase != RawCapturePhase.IDLE -> captureStatus.message
                        selectedCamera != null && previewStatus.phase != RawPreviewPhase.IDLE ->
                            previewStatus.message
                        else -> status
                    },
                    vulkanDiagnostics = "$vulkanDiagnostics\n${previewStatus.rendererDiagnostic}",
                    rawImportCapabilities = rawImportCapabilities,
                    spektraRepository = spektraRepository,
                    hasCameraPermission = hasCameraPermission(),
                    onRequestCameraPermission = {
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    },
                    onLensSelected = ::selectLens,
                    onPreferencesChanged = ::saveCameraPreferences,
                    onShutter = { previewController.captureStill() },
                    onOpenGallery = { openGallery(captureStatus.lastUri) },
                    viewfinder = {
                        AndroidView(
                            factory = { previewView },
                            modifier = Modifier.fillMaxSize(),
                        )
                    },
                )
            }
        }
        if (hasCameraPermission()) discoverCameras()
    }

    override fun onDestroy() {
        previewController.close()
        worker.shutdownNow()
        super.onDestroy()
    }

    private fun hasCameraPermission(): Boolean = ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.CAMERA,
    ) == PackageManager.PERMISSION_GRANTED

    private fun probeVulkan() {
        worker.execute {
            val result = runCatching { VulkanBridge.probe() }
                .getOrElse { "Unsupported: ${it.message ?: it.javaClass.simpleName}" }
            runOnUiThread {
                vulkanDiagnostics = result
                status = if (result.contains("unsupported", ignoreCase = true)) {
                    "Vulkan unavailable"
                } else {
                    "Vulkan ready, waiting for RAW buffer"
                }
            }
        }
    }

    private fun discoverCameras() {
        status = "Discovering RAW cameras"
        worker.execute {
            val result = runCatching { CameraCatalog(this).discover() }
            runOnUiThread {
                result.onSuccess { discovered ->
                    cameras = discovered
                    val enabled = enabledCameras(discovered, cameraPreferences)
                    val preferred = selectedLensId?.let { id ->
                        enabled.firstOrNull { it.route.routeId == id }
                    }
                    selectedLensId = (preferred ?: enabled.firstOrNull())?.route?.routeId
                    status = when {
                        discovered.isEmpty() -> "No cameras discovered"
                        enabled.none(CameraDescriptor::isUsable) -> "No usable RAW camera"
                        else -> "RAW cameras ready"
                    }
                }.onFailure {
                    status = "Camera discovery failed: ${it.message ?: it.javaClass.simpleName}"
                }
            }
        }
    }

    private fun selectLens(routeId: String) {
        selectedLensId = routeId
        saveCameraPreferences(cameraPreferences.copy(defaultLensId = routeId))
    }

    private fun saveCameraPreferences(value: CameraPreferences) {
        val enabled = enabledCameras(cameras, value)
        if (selectedLensId !in enabled.map { it.route.routeId }) {
            selectedLensId = enabled.firstOrNull()?.route?.routeId
        }
        val normalized = value.copy(defaultLensId = selectedLensId)
        cameraPreferences = normalized
        cameraPreferencesStore.save(normalized)
    }

    private fun openGallery(lastCapture: Uri?) {
        val intent = if (lastCapture == null) {
            Intent(Intent.ACTION_VIEW, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        } else {
            Intent(Intent.ACTION_VIEW).setDataAndType(lastCapture, "image/jpeg")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { startActivity(intent) }.onFailure {
            status = "No gallery app available"
        }
    }

    private fun enabledCameras(
        values: List<CameraDescriptor>,
        preferences: CameraPreferences,
    ): List<CameraDescriptor> = LensButtonMapping.resolvedCameras(values, preferences)
}
