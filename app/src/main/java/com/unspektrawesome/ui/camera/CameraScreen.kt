package com.unspektrawesome.ui.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unspektrawesome.camera.CameraDescriptor
import com.unspektrawesome.camera.LensFacing
import com.unspektrawesome.camera.RawOutput
import com.unspektrawesome.preview.RawImportCapability
import com.unspektrawesome.preview.RawImportCapabilityState
import com.unspektrawesome.preview.RawImportStreamKey
import com.unspektrawesome.settings.CameraPreferences
import com.unspektrawesome.settings.ConfiguredSensorProfile
import com.unspektrawesome.settings.LensButtonMapping
import com.unspektrawesome.settings.RawPreviewQuality
import com.unspektrawesome.settings.SensorProfileCatalog
import com.unspektrawesome.spektra.SpektraFilmParams
import com.unspektrawesome.spektra.SpektraState
import com.unspektrawesome.spektra.SpektraStateRepository
import java.util.Locale

private val Accent = Color(0xFFDDFE52)
private val Glass = Color(0xE8121413)
private val Muted = Color(0xFFADB3AC)

@Composable
fun CameraScreen(
    cameras: List<CameraDescriptor>,
    selectedLensId: String?,
    preferences: CameraPreferences,
    status: String,
    vulkanDiagnostics: String,
    rawImportCapabilities: Map<RawImportStreamKey, RawImportCapability>,
    spektraRepository: SpektraStateRepository,
    hasCameraPermission: Boolean,
    onRequestCameraPermission: () -> Unit,
    onLensSelected: (String) -> Unit,
    onPreferencesChanged: (CameraPreferences) -> Unit,
    onShutter: () -> Unit,
    onOpenGallery: () -> Unit,
    viewfinder: @Composable BoxScope.() -> Unit,
) {
    var spektraVisible by rememberSaveable { mutableStateOf(false) }
    var settingsVisible by rememberSaveable { mutableStateOf(false) }
    val spektra by spektraRepository.state.collectAsState()
    val filmStock = SpektraFilmParams.FILM_LABELS.getOrNull(spektra.params.film) ?: "SpektraFilm"
    val enabledCameras = remember(cameras, preferences) {
        LensButtonMapping.resolvedCameras(cameras, preferences)
    }

    if (settingsVisible) {
        CameraSettingsScreen(
            cameras = cameras,
            preferences = preferences,
            diagnostics = vulkanDiagnostics,
            rawImportCapabilities = rawImportCapabilities,
            onChange = onPreferencesChanged,
            onLensSelected = onLensSelected,
            onDismiss = { settingsVisible = false },
        )
        return
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Box(Modifier.fillMaxSize(), content = viewfinder)
        ViewfinderScrims()
        CompactTopControls(
            status = status,
            quality = preferences.previewQuality,
            onSettings = { settingsVisible = true },
        )

        if (!hasCameraPermission) {
            PermissionPrompt(onRequestCameraPermission)
        }

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (spektraVisible) {
                SpektraAdjustmentTray(
                    state = spektra,
                    repository = spektraRepository,
                    onClose = { spektraVisible = false },
                )
            }
            LensSelector(enabledCameras, selectedLensId, onLensSelected)
            SpektraEntryRow(
                active = spektraVisible,
                filmStock = filmStock,
                onClick = { spektraVisible = !spektraVisible },
            )
            CaptureBar(
                activeProfile = filmStock,
                onGallery = onOpenGallery,
                onShutter = onShutter,
            )
        }
    }

}

@Composable
private fun BoxScope.ViewfinderScrims() {
    Box(
        Modifier.align(Alignment.TopCenter).fillMaxWidth().height(150.dp).background(
            Brush.verticalGradient(listOf(Color(0xC9000000), Color.Transparent)),
        ),
    )
    Box(
        Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(260.dp).background(
            Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000))),
        ),
    )
}

@Composable
private fun BoxScope.CompactTopControls(
    status: String,
    quality: RawPreviewQuality,
    onSettings: () -> Unit,
) {
    Row(
        Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "UNSPEKTRAWESOME",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                letterSpacing = 1.2.sp,
            )
            Text(status, color = Muted, fontSize = 10.sp, maxLines = 1)
        }
        StatusChip("${quality.label} RAW")
        TextButton(onClick = onSettings, modifier = Modifier.padding(start = 2.dp)) {
            Text("SET", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun StatusChip(text: String) {
    Surface(color = Color(0x66181B19), shape = RoundedCornerShape(50)) {
        Text(text, color = Accent, fontSize = 9.sp, modifier = Modifier.padding(8.dp, 5.dp))
    }
}

@Composable
private fun BoxScope.PermissionPrompt(onRequest: () -> Unit) {
    Surface(
        modifier = Modifier.align(Alignment.Center).padding(28.dp),
        color = Glass,
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            Modifier.padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Camera access is required for the RAW viewfinder", color = Color.White)
            Button(onClick = onRequest, colors = ButtonDefaults.buttonColors(containerColor = Accent)) {
                Text("Allow camera", color = Color.Black)
            }
        }
    }
}

@Composable
private fun LensSelector(
    cameras: List<CameraDescriptor>,
    selectedLensId: String?,
    onSelect: (String) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        cameras.forEach { camera ->
            val selected = camera.route.routeId == selectedLensId
            val label = lensButtonLabel(camera)
            Text(
                label,
                color = if (selected) Color.Black else Color.White,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 3.dp, vertical = 5.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (selected) Accent else Color(0x55191B1A))
                    .clickable { onSelect(camera.route.routeId) }
                    .padding(horizontal = 13.dp, vertical = 7.dp),
            )
        }
    }
}

@Composable
private fun SpektraEntryRow(active: Boolean, filmStock: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Row(
            modifier = Modifier.clip(RoundedCornerShape(50))
                .background(if (active) Accent else Color(0xB3191B1A))
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Text(
                "SPEKTRA",
                color = if (active) Color.Black else Accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.7.sp,
            )
            Text(
                filmStock,
                color = if (active) Color(0xFF35382F) else Color.White,
                fontSize = 10.sp,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun CaptureBar(activeProfile: String, onGallery: () -> Unit, onShutter: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onGallery, modifier = Modifier.width(92.dp)) {
            Text("GALLERY", color = Color.White, fontSize = 10.sp)
        }
        Box(
            Modifier.size(70.dp).semantics {
                contentDescription = "Take photo"
                role = Role.Button
            }.border(2.dp, Color.White, CircleShape).padding(5.dp)
                .clip(CircleShape).background(Color.White).clickable(onClick = onShutter),
        )
        Text(
            activeProfile.uppercase(Locale.ROOT),
            color = Accent,
            fontSize = 9.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.width(92.dp),
        )
    }
}

private enum class CameraSettingsTab(val label: String) {
    GENERAL("General"),
    LENS_PROFILES("Lens Profiles"),
    SYSTEM("System"),
}

@Composable
private fun CameraSettingsScreen(
    cameras: List<CameraDescriptor>,
    preferences: CameraPreferences,
    diagnostics: String,
    rawImportCapabilities: Map<RawImportStreamKey, RawImportCapability>,
    onChange: (CameraPreferences) -> Unit,
    onLensSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedTab by rememberSaveable { mutableStateOf(CameraSettingsTab.LENS_PROFILES) }
    val profiles = remember(cameras, preferences) {
        SensorProfileCatalog.resolve(cameras, preferences)
    }
    Column(Modifier.fillMaxSize().background(Color(0xFF0C0E0D))) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onDismiss) {
                Text("BACK", color = Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text("CAMERA SETUP", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(
                    "${profiles.size} ${if (profiles.size == 1) "sensor" else "sensors"} detected",
                    color = Muted,
                    fontSize = 10.sp,
                )
            }
            TextButton(onClick = onDismiss) {
                Text("DONE", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            CameraSettingsTab.entries.forEach { tab ->
                val selected = tab == selectedTab
                Text(
                    tab.label.uppercase(Locale.ROOT),
                    color = if (selected) Color.Black else Muted,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(50))
                        .background(if (selected) Accent else Color(0xFF202321))
                        .clickable { selectedTab = tab }
                        .padding(vertical = 10.dp),
                )
            }
        }
        when (selectedTab) {
            CameraSettingsTab.GENERAL -> GeneralCameraSettings(preferences, onChange)
            CameraSettingsTab.LENS_PROFILES -> LensProfileSettings(
                profiles,
                preferences,
                rawImportCapabilities,
                onChange,
                onLensSelected,
            )
            CameraSettingsTab.SYSTEM -> SystemCameraSettings(diagnostics)
        }
    }
}

@Composable
private fun GeneralCameraSettings(
    preferences: CameraPreferences,
    onChange: (CameraPreferences) -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 18.dp),
    ) {
        SettingsSectionHeader("VIEWFINDER", "GPU processing resolution is independent from sensor raster.")
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp),
        ) {
            RawPreviewQuality.entries.forEach { quality ->
                SettingsChoice(
                    label = "${quality.label}  ${(quality.scale * 100).toInt()}%",
                    selected = preferences.previewQuality == quality,
                ) {
                    onChange(preferences.copy(previewQuality = quality))
                }
            }
        }
    }
}

@Composable
private fun LensProfileSettings(
    profiles: List<ConfiguredSensorProfile>,
    preferences: CameraPreferences,
    rawImportCapabilities: Map<RawImportStreamKey, RawImportCapability>,
    onChange: (CameraPreferences) -> Unit,
    onLensSelected: (String) -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SettingsSectionHeader(
            "LENS PROFILES",
            "One profile per physical sensor. Choose the Camera2 path and RAW raster used by its viewfinder button.",
        )
        if (profiles.isEmpty()) {
            Text("No usable RAW sensors were discovered.", color = Color(0xFFFF8A80), fontSize = 11.sp)
        }
        profiles.forEachIndexed { index, configured ->
            SensorProfileCard(
                buttonIndex = index,
                configured = configured,
                allProfiles = profiles,
                preferences = preferences,
                rawImportCapabilities = rawImportCapabilities,
                onChange = onChange,
                onLensSelected = onLensSelected,
            )
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun SensorProfileCard(
    buttonIndex: Int,
    configured: ConfiguredSensorProfile,
    allProfiles: List<ConfiguredSensorProfile>,
    preferences: CameraPreferences,
    rawImportCapabilities: Map<RawImportStreamKey, RawImportCapability>,
    onChange: (CameraPreferences) -> Unit,
    onLensSelected: (String) -> Unit,
) {
    val profile = configured.profile
    val camera = configured.selectedPath
    var pathExpanded by rememberSaveable(profile.id.value) { mutableStateOf(false) }
    var outputExpanded by rememberSaveable("raw-${profile.id.value}") { mutableStateOf(false) }
    val outputs = remember(camera) { camera.rawOutputs.filterNot { it.maximumResolutionMode } }
    val selectedOutput = configured.selectedRegularRawOutput
        ?: outputs.takeIf(List<RawOutput>::isNotEmpty)?.let(::chooseDefaultPreviewOutput)

    Surface(
        color = Color(0xFF171A18),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = Accent, shape = RoundedCornerShape(9.dp)) {
                    Text(
                        "${buttonIndex + 1}",
                        color = Color.Black,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text(sensorProfileTitle(camera), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(sensorProfileDetail(camera), color = Muted, fontSize = 9.sp, maxLines = 2)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Switch(
                        checked = configured.enabled,
                        onCheckedChange = { enabled ->
                            onChange(
                                SensorProfileCatalog.setEnabled(
                                    materializeProfilePreferences(preferences, allProfiles),
                                    configured.profile,
                                    enabled,
                                ),
                            )
                        },
                    )
                    Text("BUTTON", color = Muted, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    RadioButton(
                        selected = configured.isDefault,
                        enabled = configured.enabled,
                        onClick = {
                            val updated = SensorProfileCatalog.setEnabled(
                                materializeProfilePreferences(preferences, allProfiles),
                                configured.profile,
                                true,
                            )
                            onChange(SensorProfileCatalog.setDefault(updated, configured.profile))
                            onLensSelected(camera.route.routeId)
                        },
                    )
                    Text("DEFAULT", color = Muted, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(12.dp))
            CompactDropdownField(
                label = "CAMERA ID / PATH",
                value = cameraRouteLabel(camera),
                detail = cameraRouteDetail(camera),
                expanded = pathExpanded,
                onExpand = { pathExpanded = true },
                onDismiss = { pathExpanded = false },
            ) {
                profile.paths.forEach { candidate ->
                    val selected = candidate.route.routeId == camera.route.routeId
                    DropdownMenuItem(
                        modifier = Modifier.background(
                            if (selected) Color(0xFF30352B) else Color.Transparent,
                        ),
                        colors = MenuDefaults.itemColors(
                            textColor = if (selected) Accent else Color.White,
                        ),
                        text = {
                            Column {
                                Text(
                                    cameraRouteLabel(candidate),
                                    color = if (selected) Accent else Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                )
                                Text(cameraRouteDetail(candidate), color = Muted, fontSize = 9.sp)
                            }
                        },
                        onClick = {
                            onChange(
                                SensorProfileCatalog.selectPath(
                                    materializeProfilePreferences(preferences, allProfiles),
                                    configured.profile,
                                    candidate.route.routeId,
                                ),
                            )
                            if (configured.isDefault) onLensSelected(candidate.route.routeId)
                            pathExpanded = false
                        },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            CompactDropdownField(
                label = "RAW STREAM",
                value = selectedOutput?.let(::rawOutputLabel) ?: "No regular RAW output",
                detail = selectedOutput?.let { outputCapabilityDetail(camera, it, rawImportCapabilities) },
                expanded = outputExpanded,
                enabled = outputs.isNotEmpty(),
                onExpand = { outputExpanded = true },
                onDismiss = { outputExpanded = false },
            ) {
                outputs.sortedWith(
                    compareByDescending<RawOutput> { it.size.area() }.thenBy { it.format.name },
                ).forEach { output ->
                    val capability = rawImportCapabilities[
                        RawImportStreamKey.from(camera.route.routeId, output)
                    ]
                    val enabled = capability?.state != RawImportCapabilityState.INCOMPATIBLE
                    val selected = output == selectedOutput
                    DropdownMenuItem(
                        enabled = enabled,
                        modifier = Modifier.background(
                            if (selected) Color(0xFF30352B) else Color.Transparent,
                        ),
                        colors = MenuDefaults.itemColors(
                            textColor = if (selected) Accent else Color.White,
                            disabledTextColor = Color(0xFFFF8A80),
                        ),
                        text = {
                            Column {
                                Text(
                                    rawOutputLabel(output),
                                    color = when {
                                        !enabled -> Color(0xFFFF8A80)
                                        selected -> Accent
                                        else -> Color.White
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                )
                                Text(
                                    outputCapabilityDetail(camera, output, rawImportCapabilities),
                                    color = if (enabled) Muted else Color(0xFFFF8A80),
                                    fontSize = 9.sp,
                                )
                            }
                        },
                        onClick = {
                            onChange(
                                SensorProfileCatalog.selectRawOutput(
                                    materializeProfilePreferences(preferences, allProfiles),
                                    configured.profile,
                                    output,
                                ),
                            )
                            outputExpanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactDropdownField(
    label: String,
    value: String,
    detail: String?,
    expanded: Boolean,
    enabled: Boolean = true,
    onExpand: () -> Unit,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column {
        Text(label, color = Muted, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
        BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp))
                    .background(Color(0xFF272B28)).clickable(enabled = enabled, onClick = onExpand)
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(value, color = if (enabled) Color.White else Muted, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                    detail?.let { Text(it, color = Muted, fontSize = 8.sp, maxLines = 1) }
                }
                Text("▾", color = Accent, fontSize = 13.sp)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = onDismiss,
                modifier = Modifier.width(maxWidth),
                shape = RoundedCornerShape(14.dp),
                containerColor = Color(0xFF202421),
                tonalElevation = 0.dp,
                shadowElevation = 10.dp,
            ) {
                content()
            }
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String, detail: String) {
    Text(title, color = Accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
    Text(detail, color = Muted, fontSize = 9.sp, modifier = Modifier.padding(top = 3.dp))
}

@Composable
private fun SystemCameraSettings(diagnostics: String) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 18.dp),
    ) {
        SettingsSectionHeader("VULKAN", "Renderer and actual HardwareBuffer import diagnostics.")
        Surface(
            color = Color(0xFF171A18),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        ) {
            Text(diagnostics, color = Color(0xFFCED5CE), fontSize = 9.sp, modifier = Modifier.padding(13.dp))
        }
    }
}

@Composable
private fun SettingsChoice(
    label: String,
    selected: Boolean,
    fillWidth: Boolean = false,
    enabled: Boolean = true,
    detail: String? = null,
    onClick: () -> Unit,
) {
    val modifier = Modifier.padding(end = 6.dp, top = 4.dp)
        .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
        .clip(RoundedCornerShape(10.dp))
        .background(
            when {
                !enabled -> Color(0xFF241D1D)
                selected -> Accent
                else -> Color(0xFF282B29)
            },
        )
        .clickable(enabled = enabled, onClick = onClick)
        .padding(horizontal = 11.dp, vertical = 8.dp)
    Column(modifier) {
        Text(
            label,
            color = when {
                !enabled -> Color(0xFFFF8A80)
                selected -> Color.Black
                else -> Color.White
            },
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
        detail?.let {
            Text(
                it,
                color = when {
                    !enabled -> Color(0xFFFF8A80)
                    selected -> Color(0xFF35382F)
                    else -> Color.Gray
                },
                fontSize = 8.sp,
            )
        }
    }
}

private fun materializeProfilePreferences(
    preferences: CameraPreferences,
    profiles: List<ConfiguredSensorProfile>,
): CameraPreferences {
    val routes = profiles.map { it.selectedPath.route.routeId }
    return preferences.copy(
        lensButtonRouteIds = if (preferences.lensButtonMappingConfigured) {
            preferences.lensButtonRouteIds
        } else {
            routes
        },
        lensButtonMappingConfigured = true,
        enabledLensIds = if (preferences.lensSelectionConfigured) {
            preferences.enabledLensIds
        } else {
            profiles.filter { it.enabled }.mapTo(linkedSetOf()) { it.selectedPath.route.routeId }
        },
        lensSelectionConfigured = true,
    )
}

private fun chooseDefaultPreviewOutput(outputs: List<RawOutput>): RawOutput = outputs
    .filter { it.maximumFps() >= 29.75 }
    .maxByOrNull { it.size.area() }
    ?: outputs.maxBy { it.size.area() }

private fun cameraRouteLabel(camera: CameraDescriptor): String = buildString {
    append("ID ${camera.route.routeId}")
    when {
        camera.route.physicalCameraId != null -> append("  physical ${camera.route.physicalCameraId}")
        camera.route.openCameraId != camera.route.routeId -> append("  open ${camera.route.openCameraId}")
        else -> append("  direct")
    }
}

private fun cameraRouteDetail(camera: CameraDescriptor): String = buildString {
    append(camera.route.kind.name.lowercase().replace('_', ' '))
    append(" route ${camera.route.routeId}, opens ${camera.route.openCameraId}")
    camera.route.physicalCameraId?.let { append(", physical $it") }
}

private fun sensorProfileTitle(camera: CameraDescriptor): String = buildString {
    append(lensButtonLabel(camera))
    camera.equivalentFocalLengthMm?.let {
        append(String.format(Locale.ROOT, "  %.0f mm eq", it))
    }
}

private fun lensButtonLabel(camera: CameraDescriptor): String = when (camera.facing) {
    LensFacing.FRONT -> "Front"
    LensFacing.EXTERNAL -> "External"
    else -> camera.relativeZoom?.let(::formatZoom) ?: camera.lensRole.label
}

private fun sensorProfileDetail(camera: CameraDescriptor): String = buildString {
    append(camera.facing.name.lowercase().replaceFirstChar(Char::uppercase))
    camera.focalLengthMm?.let {
        append(String.format(Locale.ROOT, " · %.2f mm", it))
    }
    if (camera.sensorWidthMm != null && camera.sensorHeightMm != null) {
        append(String.format(Locale.ROOT, " · %.2f x %.2f mm sensor", camera.sensorWidthMm, camera.sensorHeightMm))
    }
}

private fun rawOutputLabel(output: RawOutput): String = String.format(
    Locale.ROOT,
    "%d x %d (%s, %.2f)",
    output.size.width,
    output.size.height,
    output.format.name,
    output.size.width.toFloat() / output.size.height,
)

private fun outputCapabilityDetail(
    camera: CameraDescriptor,
    output: RawOutput,
    capabilities: Map<RawImportStreamKey, RawImportCapability>,
): String {
    val capability = capabilities[RawImportStreamKey.from(camera.route.routeId, output)]
    return when (capability?.state) {
        RawImportCapabilityState.COMPATIBLE -> "Vulkan zero-copy verified"
        RawImportCapabilityState.INCOMPATIBLE -> "Unavailable: ${capability.reason}"
        null -> if (output.minimumFrameDurationNs > 0) {
            String.format(Locale.ROOT, "Up to %.1f fps · verified when selected", output.maximumFps())
        } else {
            "Verified with an actual Camera2 frame when selected"
        }
    }
}

private fun formatZoom(value: Float): String = if (value == value.toInt().toFloat()) {
    "${value.toInt()}x"
} else {
    String.format(Locale.ROOT, "%.1fx", value)
}

private fun formatValue(value: Float, suffix: String?): String {
    val number = String.format(Locale.ROOT, "%+.2f", value)
    return if (suffix == null) number else "$number $suffix"
}

@Composable
fun UnspektrawesomeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = androidx.compose.material3.darkColorScheme(
            primary = Accent,
            onPrimary = Color.Black,
            surface = Color(0xFF111311),
            background = Color.Black,
        ),
        content = content,
    )
}
