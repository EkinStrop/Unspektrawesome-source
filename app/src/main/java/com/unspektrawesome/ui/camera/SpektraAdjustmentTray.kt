package com.unspektrawesome.ui.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unspektrawesome.spektra.SpektraFilmParams
import com.unspektrawesome.spektra.SpektraState
import com.unspektrawesome.spektra.SpektraStateRepository
import java.util.Locale
import kotlin.math.roundToInt

private val TrayAccent = Color(0xFFDDFE52)
private val TrayGlass = Color(0xF0141615)
private val TrayMuted = Color(0xFFADB3AC)

private enum class SpektraPage(val label: String) {
    FILM("Film"),
    PRINT("Print"),
    GRAIN("Grain"),
    LOOK("Look"),
    OPTICS("Optics"),
    OUTPUT("Output"),
    ADVANCED("Advanced"),
}

private val processLabels = listOf("Print simulation", "Scan negative", "Process negative")
private val renderOutputLabels = listOf(
    "Final preview", "Film density CMY", "Film density CMY + grain",
    "Film log raw", "Print log raw", "Print density CMY",
)
private val rgbToRawLabels = listOf("Hanatos 2025", "Mallett 2019", "Hanatos 2026")
private val colorSpaceLabels = listOf(
    "ARRI LogC4", "ARRI LogC3 EI800", "BMDFilm WideGamut Gen5",
    "DaVinci Intermediate WideGamut", "RED Log3G10 REDWideGamutRGB",
    "Sony S-Log3 S-Gamut3", "Sony S-Log3 S-Gamut3.Cine",
    "Canon Log2 CinemaGamut D55", "Canon Log3 CinemaGamut D55",
    "Panasonic V-Log V-Gamut", "ACES2065-1", "ACEScg", "ACEScct", "ACEScc",
    "Linear Rec.2020", "Linear Rec.709", "Linear P3-D65", "sRGB", "Display P3",
    "ProPhoto RGB", "Adobe RGB (1998)", "DCI-P3", "P3-D65 Gamma 2.2",
    "P3-D65 Gamma 2.6", "Rec.709 Gamma 2.2", "Rec.709 Gamma 2.4",
)
private val outputRoleLabels = listOf("Display Out SDR", "Display Out HDR", "Scene Handoff")
private val hdrPresetLabels = listOf("PQ 1000", "PQ 4000", "HLG 1000", "Custom")
private val hdrTransferLabels = listOf("Rec.2100 ST2084 (PQ)", "Rec.2100 HLG")
private val hdrToneMappingLabels = listOf("Soft Rolloff", "Hard Clip")
private val autoExposureLabels = listOf("Center weighted", "Median")
private val pushPullLabels = listOf("Standard", "Experimental")
private val printTimingLabels = listOf("Filtered enlarger", "APD printer density")
private val grainModelLabels = listOf("Preview", "Production", "Grain Synthesis")
private val filmFormatLabels = listOf(
    "Standard 8", "Super 8", "Standard 16", "Super 16",
    "Standard 35", "Super 35", "Standard 65", "IMAX 70",
)
private val diffusionFamilyLabels = listOf(
    "Glimmerglass", "Black Pro-Mist", "Pro-Mist", "CineBloom",
)

@Composable
internal fun SpektraAdjustmentTray(
    state: SpektraState,
    repository: SpektraStateRepository,
    onClose: () -> Unit,
) {
    var selectedPage by rememberSaveable { mutableStateOf(SpektraPage.FILM) }
    val params = state.params
    val update: (SpektraFilmParams) -> Unit = { next -> repository.updateParams { next } }

    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        color = TrayGlass,
        shape = RoundedCornerShape(22.dp),
        tonalElevation = 10.dp,
    ) {
        Column(Modifier.fillMaxWidth().height(372.dp).padding(top = 7.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("SpektraFilm", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(
                        SpektraFilmParams.FILM_LABELS.getOrNull(params.film) ?: "Film processing",
                        color = TrayMuted,
                        fontSize = 10.sp,
                    )
                }
                Switch(checked = params.enabled, onCheckedChange = { update(params.copy(enabled = it)) })
                if (repository.canUndo()) {
                    TextButton(onClick = { repository.undo() }) { Text("Undo", color = TrayAccent) }
                }
                TextButton(onClick = { repository.reset() }) { Text("Reset", color = Color.White) }
                TextButton(onClick = onClose) { Text("Close", color = TrayMuted) }
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                SpektraPage.entries.forEach { page ->
                    FilterChip(
                        selected = selectedPage == page,
                        onClick = { selectedPage = page },
                        label = { Text(page.label, maxLines = 1, fontSize = 10.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = TrayAccent,
                            selectedLabelColor = Color.Black,
                        ),
                    )
                }
            }
            Column(
                Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                when (selectedPage) {
                    SpektraPage.FILM -> FilmPage(params, update)
                    SpektraPage.PRINT -> PrintPage(params, update)
                    SpektraPage.GRAIN -> GrainPage(params, update)
                    SpektraPage.LOOK -> LookPage(params, update)
                    SpektraPage.OPTICS -> OpticsPage(params, update)
                    SpektraPage.OUTPUT -> OutputPage(params, update)
                    SpektraPage.ADVANCED -> AdvancedPage(params, update)
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun FilmPage(p: SpektraFilmParams, update: (SpektraFilmParams) -> Unit) {
    Section("FILM")
    OptionRow("RGB to Raw", p.rgbToRawMethod, rgbToRawLabels) { update(p.copy(rgbToRawMethod = it)) }
    OptionRow("Stock", p.film, SpektraFilmParams.FILM_LABELS) { update(p.copy(film = it)) }
    OptionRow("Film Format", p.filmFormat, filmFormatLabels) { update(p.copy(filmFormat = it)) }
    FloatRow("Exposure EV", p.filmExposureEv, -8f..8f, 0f, ::formatEv) { update(p.copy(filmExposureEv = it)) }
    SwitchRow("Auto Exposure", p.autoExposure) { update(p.copy(autoExposure = it)) }
    OptionRow("Auto Exposure Meter", p.autoExposureMethod, autoExposureLabels) { update(p.copy(autoExposureMethod = it)) }
    OptionRow("Push / Pull Mode", p.filmPushPullMode, pushPullLabels) { update(p.copy(filmPushPullMode = it)) }
    FloatRow("Film Push / Pull Stops", p.filmPushPullStops, -2f..2f, 0f, ::formatEv) { update(p.copy(filmPushPullStops = it)) }
    FloatRow("Negative Bleach Bypass", p.negativeBleachBypassAmount, 0f..1f) { update(p.copy(negativeBleachBypassAmount = it)) }
    FloatRow("Leuco-Cyan Coupling", p.negativeLeucoCyanCoupling, 0f..2f, 1f) { update(p.copy(negativeLeucoCyanCoupling = it)) }
    FloatRow("Gamma", p.filmGamma, 0.1f..2f, 1f) { update(p.copy(filmGamma = it)) }
}

@Composable
private fun PrintPage(p: SpektraFilmParams, update: (SpektraFilmParams) -> Unit) {
    Section("PRINT")
    OptionRow("Paper", p.paper, SpektraFilmParams.PAPER_LABELS) { update(p.copy(paper = it)) }
    OptionRow("Print Timing", p.printTiming, printTimingLabels) { update(p.copy(printTiming = it)) }
    FloatRow("Exposure EV", p.printExposureEv, -5f..5f, 0f, ::formatEv) { update(p.copy(printExposureEv = it)) }
    FloatRow("Print Push / Pull Stops", p.printPushPullStops, -2f..2f, 0f, ::formatEv) { update(p.copy(printPushPullStops = it)) }
    FloatRow("Print Bleach Bypass", p.printBleachBypassAmount, 0f..1f) { update(p.copy(printBleachBypassAmount = it)) }
    FloatRow("Gamma", p.printGamma, 0.1f..2f, 1f) { update(p.copy(printGamma = it)) }
    FloatRow("Shadow Shape", p.printShadowShape, -1f..1f) { update(p.copy(printShadowShape = it)) }
    FloatRow("Highlight Shape", p.printHighlightShape, -1f..1f) { update(p.copy(printHighlightShape = it)) }
    FloatRow("C Filter", p.filterC, 0f..120f, formatter = ::formatOne) { update(p.copy(filterC = it)) }
    FloatRow("M Filter Shift", p.filterMShift, -60f..60f, formatter = ::formatOne) { update(p.copy(filterMShift = it)) }
    FloatRow("Y Filter Shift", p.filterYShift, -60f..60f, formatter = ::formatOne) { update(p.copy(filterYShift = it)) }
    FloatRow("Preflash Exposure", p.preflashExposure, 0f..1f) { update(p.copy(preflashExposure = it)) }
    FloatRow("Preflash M Filter Shift", p.preflashMFilterShift, -60f..60f, formatter = ::formatOne) { update(p.copy(preflashMFilterShift = it)) }
    FloatRow("Preflash Y Filter Shift", p.preflashYFilterShift, -60f..60f, formatter = ::formatOne) { update(p.copy(preflashYFilterShift = it)) }
    SwitchRow("Gang Printer Points", p.printerLightsGang) { update(p.copy(printerLightsGang = it)) }
    FloatRow("Printer Point R", p.printerLightsR, -24f..24f, formatter = ::formatOne) { update(p.copy(printerLightsR = it)) }
    FloatRow("Printer Point G", p.printerLightsG, -24f..24f, formatter = ::formatOne) { update(p.copy(printerLightsG = it)) }
    FloatRow("Printer Point B", p.printerLightsB, -24f..24f, formatter = ::formatOne) { update(p.copy(printerLightsB = it)) }
    SwitchRow("Printer Point Calibration", p.printerLightCalibration) { update(p.copy(printerLightCalibration = it)) }
}

@Composable
private fun GrainPage(p: SpektraFilmParams, update: (SpektraFilmParams) -> Unit) {
    Section("GRAIN")
    SwitchRow("Enabled", p.grainEnabled) { update(p.copy(grainEnabled = it)) }
    OptionRow("Model", p.grainModel, grainModelLabels) { update(p.copy(grainModel = it)) }
    FloatRow("Amount", p.grainAmount, 0f..2f, 1f) { update(p.copy(grainAmount = it)) }
    FloatRow("Saturation", p.grainSaturation, 0f..1f, 1f) { update(p.copy(grainSaturation = it)) }
    FloatRow("Particle Area um2", p.grainParticleAreaUm2, 0.01f..5f, 0.1f) { update(p.copy(grainParticleAreaUm2 = it)) }
    SwitchRow("Sublayers", p.grainSublayersEnabled) { update(p.copy(grainSublayersEnabled = it)) }
    IntRow("Sub Layer Count", p.grainSubLayerCount, 1..8, 1) { update(p.copy(grainSubLayerCount = it)) }
    FloatRow("Particle Scale R", p.grainParticleScaleR, 0f..8f, 1.2f) { update(p.copy(grainParticleScaleR = it)) }
    FloatRow("Particle Scale G", p.grainParticleScaleG, 0f..8f, 1f) { update(p.copy(grainParticleScaleG = it)) }
    FloatRow("Particle Scale B", p.grainParticleScaleB, 0f..8f, 2.5f) { update(p.copy(grainParticleScaleB = it)) }
    FloatRow("Layer Scale 0", p.grainParticleScaleLayer0, 0f..8f, 6f) { update(p.copy(grainParticleScaleLayer0 = it)) }
    FloatRow("Layer Scale 1", p.grainParticleScaleLayer1, 0f..8f, 1f) { update(p.copy(grainParticleScaleLayer1 = it)) }
    FloatRow("Layer Scale 2", p.grainParticleScaleLayer2, 0f..8f, 0.4f) { update(p.copy(grainParticleScaleLayer2 = it)) }
    FloatRow("Density Min R", p.grainDensityMinR, 0f..1f, 0.04f) { update(p.copy(grainDensityMinR = it)) }
    FloatRow("Density Min G", p.grainDensityMinG, 0f..1f, 0.05f) { update(p.copy(grainDensityMinG = it)) }
    FloatRow("Density Min B", p.grainDensityMinB, 0f..1f, 0.06f) { update(p.copy(grainDensityMinB = it)) }
    FloatRow("Uniformity R", p.grainUniformityR, 0f..1f, 0.99f) { update(p.copy(grainUniformityR = it)) }
    FloatRow("Uniformity G", p.grainUniformityG, 0f..1f, 0.97f) { update(p.copy(grainUniformityG = it)) }
    FloatRow("Uniformity B", p.grainUniformityB, 0f..1f, 0.98f) { update(p.copy(grainUniformityB = it)) }
    FloatRow("Final Grain Blur", p.grainFinalBlurUm, 0f..25f, 7.17f, ::formatTwo) { update(p.copy(grainFinalBlurUm = it)) }
    FloatRow("Dye Cloud Blur um", p.grainBlurDyeCloudsUm, 0f..10f, 1f) { update(p.copy(grainBlurDyeCloudsUm = it)) }
    FloatRow("Micro Structure Scale", p.grainMicroStructureScale, 0f..100f, 0.2f) { update(p.copy(grainMicroStructureScale = it)) }
    FloatRow("Micro Structure Sigma nm", p.grainMicroStructureSigmaNm, 0f..100f, 30f, ::formatOne) { update(p.copy(grainMicroStructureSigmaNm = it)) }
    IntRow("Seed", p.grainSeed, 0..1_000_000, 1) { update(p.copy(grainSeed = it)) }
    SwitchRow("Animate", p.grainAnimate) { update(p.copy(grainAnimate = it)) }
    FloatRow("Synthesis Size", p.grainSynthesisSize, 0.25f..4f, 1f) { update(p.copy(grainSynthesisSize = it)) }
    FloatRow("Synthesis Amount", p.grainSynthesisAmount, 0f..3f, 1f) { update(p.copy(grainSynthesisAmount = it)) }
    FloatRow("Synthesis Sharpness", p.grainSynthesisSharpness, 0.25f..4f, 1f) { update(p.copy(grainSynthesisSharpness = it)) }
    FloatRow("Synthesis Quality", p.grainSynthesisQuality, 0.25f..4f, 1f) { update(p.copy(grainSynthesisQuality = it)) }
    Section("GRAIN SYNTHESIS")
    IntRow("Samples", p.grainSynthesisSamples, 1..2048, 128) { update(p.copy(grainSynthesisSamples = it)) }
    FloatRow("Mean Radius um", p.grainSynthesisMeanRadiusUm, 0.05f..10f, 0.25f) { update(p.copy(grainSynthesisMeanRadiusUm = it)) }
    FloatRow("Radius StdDev Ratio", p.grainSynthesisRadiusStdDevRatio, 0f..1f) { update(p.copy(grainSynthesisRadiusStdDevRatio = it)) }
    FloatRow("Observation Aperture Sigma um", p.grainSynthesisObservationSigmaUm, 0f..20f, 1f) { update(p.copy(grainSynthesisObservationSigmaUm = it)) }
    FloatRow("Cell Size Ratio", p.grainSynthesisCellSizeRatio, 0.25f..2f, 1f) { update(p.copy(grainSynthesisCellSizeRatio = it)) }
    FloatRow("Max Radius Quantile", p.grainSynthesisMaxRadiusQuantile, 0.95f..0.9999f, 0.999f, ::formatFour) { update(p.copy(grainSynthesisMaxRadiusQuantile = it)) }
    FloatRow("Coverage Epsilon", p.grainSynthesisCoverageEpsilon, 0.000001f..0.01f, 0.0001f, ::formatSix) { update(p.copy(grainSynthesisCoverageEpsilon = it)) }
    IntRow("Max Grains Per Cell", p.grainSynthesisMaxGrainsPerCell, 1..128, 32) { update(p.copy(grainSynthesisMaxGrainsPerCell = it)) }
    FloatRow("Radius Scale R", p.grainSynthesisRadiusScaleR, 0f..8f, 1.2f) { update(p.copy(grainSynthesisRadiusScaleR = it)) }
    FloatRow("Radius Scale G", p.grainSynthesisRadiusScaleG, 0f..8f, 1f) { update(p.copy(grainSynthesisRadiusScaleG = it)) }
    FloatRow("Radius Scale B", p.grainSynthesisRadiusScaleB, 0f..8f, 2.5f) { update(p.copy(grainSynthesisRadiusScaleB = it)) }
    FloatRow("Layer Scale 0", p.grainSynthesisLayerScale0, 0f..8f, 6f) { update(p.copy(grainSynthesisLayerScale0 = it)) }
    FloatRow("Layer Scale 1", p.grainSynthesisLayerScale1, 0f..8f, 1f) { update(p.copy(grainSynthesisLayerScale1 = it)) }
    FloatRow("Layer Scale 2", p.grainSynthesisLayerScale2, 0f..8f, 0.4f) { update(p.copy(grainSynthesisLayerScale2 = it)) }
    SwitchRow("Layered", p.grainSynthesisLayered) { update(p.copy(grainSynthesisLayered = it)) }
}

@Composable
private fun LookPage(p: SpektraFilmParams, update: (SpektraFilmParams) -> Unit) {
    Section("HALATION")
    SwitchRow("Enabled", p.halationEnabled) { update(p.copy(halationEnabled = it)) }
    FloatRow("Scatter Amount", p.scatterAmount, 0f..2f, 1f) { update(p.copy(scatterAmount = it)) }
    FloatRow("Scatter Scale", p.scatterScale, 0f..4f, 1f) { update(p.copy(scatterScale = it)) }
    FloatRow("Amount", p.halationAmount, 0f..4f, 1f) { update(p.copy(halationAmount = it)) }
    FloatRow("Scale", p.halationScale, 0f..4f, 1f) { update(p.copy(halationScale = it)) }
    FloatRow("Boost EV", p.halationBoostEv, 0f..20f, formatter = ::formatEv) { update(p.copy(halationBoostEv = it)) }
    FloatRow("Boost Range", p.halationBoostRange, 0f..1f, 0.3f) { update(p.copy(halationBoostRange = it)) }
    FloatRow("Protect EV", p.halationProtectEv, 0f..10f, 4f, ::formatEv) { update(p.copy(halationProtectEv = it)) }
    FloatRow("Strength R", p.halationStrengthR, 0f..1f, 0.05f) { update(p.copy(halationStrengthR = it)) }
    FloatRow("Strength G", p.halationStrengthG, 0f..1f, 0.015f) { update(p.copy(halationStrengthG = it)) }
    FloatRow("Strength B", p.halationStrengthB, 0f..1f) { update(p.copy(halationStrengthB = it)) }
    FloatRow("First Sigma R um", p.halationFirstSigmaUmR, 0f..200f, 65f, ::formatOne) { update(p.copy(halationFirstSigmaUmR = it)) }
    FloatRow("First Sigma G um", p.halationFirstSigmaUmG, 0f..200f, 65f, ::formatOne) { update(p.copy(halationFirstSigmaUmG = it)) }
    FloatRow("First Sigma B um", p.halationFirstSigmaUmB, 0f..200f, 65f, ::formatOne) { update(p.copy(halationFirstSigmaUmB = it)) }
    Section("DIFFUSION")
    SwitchRow("Camera Enabled", p.cameraDiffusionEnabled) { update(p.copy(cameraDiffusionEnabled = it)) }
    OptionRow("Camera Family", p.cameraDiffusionFamily, diffusionFamilyLabels) { update(p.copy(cameraDiffusionFamily = it)) }
    FloatRow("Camera Strength", p.cameraDiffusionStrength, 0f..2f, 0.5f) { update(p.copy(cameraDiffusionStrength = it)) }
    FloatRow("Camera Spatial Scale", p.cameraDiffusionSpatialScale, 0f..4f, 1f) { update(p.copy(cameraDiffusionSpatialScale = it)) }
    FloatRow("Camera Halo Warmth", p.cameraDiffusionHaloWarmth, -1.5f..1.5f) { update(p.copy(cameraDiffusionHaloWarmth = it)) }
    FloatRow("Camera Core Intensity", p.cameraDiffusionCoreIntensity, 0f..4f, 1f) { update(p.copy(cameraDiffusionCoreIntensity = it)) }
    FloatRow("Camera Core Size", p.cameraDiffusionCoreSize, 0.1f..4f, 1f) { update(p.copy(cameraDiffusionCoreSize = it)) }
    FloatRow("Camera Halo Intensity", p.cameraDiffusionHaloIntensity, 0f..4f, 1f) { update(p.copy(cameraDiffusionHaloIntensity = it)) }
    FloatRow("Camera Halo Size", p.cameraDiffusionHaloSize, 0.1f..4f, 1f) { update(p.copy(cameraDiffusionHaloSize = it)) }
    FloatRow("Camera Bloom Intensity", p.cameraDiffusionBloomIntensity, 0f..4f, 1f) { update(p.copy(cameraDiffusionBloomIntensity = it)) }
    FloatRow("Camera Bloom Size", p.cameraDiffusionBloomSize, 0.1f..4f, 1f) { update(p.copy(cameraDiffusionBloomSize = it)) }
    SwitchRow("Print Enabled", p.printDiffusionEnabled) { update(p.copy(printDiffusionEnabled = it)) }
    OptionRow("Print Family", p.printDiffusionFamily, diffusionFamilyLabels) { update(p.copy(printDiffusionFamily = it)) }
    FloatRow("Print Strength", p.printDiffusionStrength, 0f..2f, 0.5f) { update(p.copy(printDiffusionStrength = it)) }
    FloatRow("Print Spatial Scale", p.printDiffusionSpatialScale, 0f..4f, 1f) { update(p.copy(printDiffusionSpatialScale = it)) }
    FloatRow("Print Halo Warmth", p.printDiffusionHaloWarmth, -1.5f..1.5f) { update(p.copy(printDiffusionHaloWarmth = it)) }
    FloatRow("Print Core Intensity", p.printDiffusionCoreIntensity, 0f..4f, 1f) { update(p.copy(printDiffusionCoreIntensity = it)) }
    FloatRow("Print Core Size", p.printDiffusionCoreSize, 0.1f..4f, 1f) { update(p.copy(printDiffusionCoreSize = it)) }
    FloatRow("Print Halo Intensity", p.printDiffusionHaloIntensity, 0f..4f, 1f) { update(p.copy(printDiffusionHaloIntensity = it)) }
    FloatRow("Print Halo Size", p.printDiffusionHaloSize, 0.1f..4f, 1f) { update(p.copy(printDiffusionHaloSize = it)) }
    FloatRow("Print Bloom Intensity", p.printDiffusionBloomIntensity, 0f..4f, 1f) { update(p.copy(printDiffusionBloomIntensity = it)) }
    FloatRow("Print Bloom Size", p.printDiffusionBloomSize, 0.1f..4f, 1f) { update(p.copy(printDiffusionBloomSize = it)) }
    Section("SCANNER")
    SwitchRow("Enabled", p.scannerEnabled) { update(p.copy(scannerEnabled = it)) }
    SwitchRow("White Correction", p.scannerWhiteCorrection) { update(p.copy(scannerWhiteCorrection = it)) }
    SwitchRow("Black Correction", p.scannerBlackCorrection) { update(p.copy(scannerBlackCorrection = it)) }
    FloatRow("White Level", p.scannerWhiteLevel, 0f..1f, 0.98f) { update(p.copy(scannerWhiteLevel = it)) }
    FloatRow("Black Level", p.scannerBlackLevel, 0f..1f, 0.01f) { update(p.copy(scannerBlackLevel = it)) }
    FloatRow("Glare Percent", p.glarePercent, 0f..0.2f, 0.03f) { update(p.copy(glarePercent = it)) }
    FloatRow("Glare Roughness", p.glareRoughness, 0f..4f, 0.7f) { update(p.copy(glareRoughness = it)) }
    FloatRow("Glare Blur", p.glareBlur, 0f..32f, 0.5f) { update(p.copy(glareBlur = it)) }
    FloatRow("MTF50 lp/mm", p.scannerMtf50LpMm, 0f..300f, 60f, ::formatOne) { update(p.copy(scannerMtf50LpMm = it)) }
    FloatRow("Unsharp Radius um", p.scannerUnsharpRadiusUm, 0f..100f, 5f, ::formatOne) { update(p.copy(scannerUnsharpRadiusUm = it)) }
    FloatRow("Unsharp Amount", p.scannerUnsharpAmount, 0f..4f, 0.7f) { update(p.copy(scannerUnsharpAmount = it)) }
}

@Composable
private fun OpticsPage(p: SpektraFilmParams, update: (SpektraFilmParams) -> Unit) {
    Section("FILTERING")
    SwitchRow("Filter UV", p.cameraUvFilterEnabled) { update(p.copy(cameraUvFilterEnabled = it)) }
    FloatRow("UV Cut nm", p.cameraUvCutNm, 380f..450f, 410f, ::formatZero) { update(p.copy(cameraUvCutNm = it)) }
    SwitchRow("Filter IR", p.cameraIrFilterEnabled) { update(p.copy(cameraIrFilterEnabled = it)) }
    FloatRow("IR Cut nm", p.cameraIrCutNm, 600f..780f, 675f, ::formatZero) { update(p.copy(cameraIrCutNm = it)) }
    Section("FILM PLANE")
    FloatRow("Scale", p.enlargerScale, 1f..32f, 1f) { update(p.copy(enlargerScale = it)) }
    FloatRow("Offset X %", p.enlargerOffsetXPercent, -100f..100f, formatter = ::formatOne) { update(p.copy(enlargerOffsetXPercent = it)) }
    FloatRow("Offset Y %", p.enlargerOffsetYPercent, -100f..100f, formatter = ::formatOne) { update(p.copy(enlargerOffsetYPercent = it)) }
}

@Composable
private fun OutputPage(p: SpektraFilmParams, update: (SpektraFilmParams) -> Unit) {
    Section("COLOR")
    OptionRow("Mode", p.process, processLabels) { update(p.copy(process = it)) }
    if (p.process == 1) SwitchRow("Invert Negative", p.scanNegativeInvert) { update(p.copy(scanNegativeInvert = it)) }
    OptionRow("Render Output", p.renderOutput, renderOutputLabels) { update(p.copy(renderOutput = it)) }
    OptionRow("Input Color Space", p.inputColorSpace, colorSpaceLabels) { update(p.copy(inputColorSpace = it)) }
    OptionRow("Output Color Space", p.outputColorSpace, colorSpaceLabels) { update(p.copy(outputColorSpace = it)) }
    OptionRow("Output Role", p.outputRole, outputRoleLabels) { update(p.copy(outputRole = it)) }
    OptionRow("HDR Preset", p.hdrPreset, hdrPresetLabels) { update(p.copy(hdrPreset = it)) }
    OptionRow("HDR Transfer", p.hdrTransfer, hdrTransferLabels) { update(p.copy(hdrTransfer = it)) }
    OptionRow("HDR Tone Mapping", p.hdrToneMapping, hdrToneMappingLabels) { update(p.copy(hdrToneMapping = it)) }
    SwitchRow("Color Adaptation", p.colorAdaptation) { update(p.copy(colorAdaptation = it)) }
    if (p.colorAdaptation) {
        SwitchRow("Input Compression", p.colorAdaptationInputCompression) { update(p.copy(colorAdaptationInputCompression = it)) }
        SwitchRow("Curve Smoothing", p.colorAdaptationCurveSmoothing) { update(p.copy(colorAdaptationCurveSmoothing = it)) }
        SwitchRow("Output Lightness Compression", p.colorAdaptationOutputLightnessCompression) { update(p.copy(colorAdaptationOutputLightnessCompression = it)) }
        SwitchRow("Output Chroma Compression", p.colorAdaptationOutputChromaCompression) { update(p.copy(colorAdaptationOutputChromaCompression = it)) }
    }
    FloatRow("Reference White Nits", p.hdrReferenceWhiteNits, 48f..1000f, 203f, ::formatZero) { update(p.copy(hdrReferenceWhiteNits = it)) }
    FloatRow("Peak Nits", p.hdrPeakNits, 100f..10000f, 1000f, ::formatZero) { update(p.copy(hdrPeakNits = it)) }
    FloatRow("HDR Exposure EV", p.hdrExposureEv, -8f..8f, formatter = ::formatEv) { update(p.copy(hdrExposureEv = it)) }
}

@Composable
private fun AdvancedPage(p: SpektraFilmParams, update: (SpektraFilmParams) -> Unit) {
    Section("DIR COUPLERS")
    FloatRow("Amount", p.dirCouplersAmount, 0f..2f) { update(p.copy(dirCouplersAmount = it)) }
    FloatRow("Diffusion um", p.dirCouplersDiffusionUm, 0f..100f, 20f, ::formatOne) { update(p.copy(dirCouplersDiffusionUm = it)) }
    FloatRow("Tail um", p.dirCouplersDiffusionTailUm, 0f..1000f, 200f, ::formatOne) { update(p.copy(dirCouplersDiffusionTailUm = it)) }
    FloatRow("Tail Weight", p.dirCouplersDiffusionTailWeight, 0f..1f, 0.06f) { update(p.copy(dirCouplersDiffusionTailWeight = it)) }
    FloatRow("Same-Layer Inhibition", p.dirCouplersInhibitionSameLayer, 0f..2f, 1f) { update(p.copy(dirCouplersInhibitionSameLayer = it)) }
    FloatRow("Interlayer Inhibition", p.dirCouplersInhibitionInterlayer, 0f..2f, 1f) { update(p.copy(dirCouplersInhibitionInterlayer = it)) }
    FloatRow("Same-Layer Gamma R", p.dirCouplersGammaSameLayerR, 0f..1f, 0.336f) { update(p.copy(dirCouplersGammaSameLayerR = it)) }
    FloatRow("Same-Layer Gamma G", p.dirCouplersGammaSameLayerG, 0f..1f, 0.319f) { update(p.copy(dirCouplersGammaSameLayerG = it)) }
    FloatRow("Same-Layer Gamma B", p.dirCouplersGammaSameLayerB, 0f..1f, 0.273f) { update(p.copy(dirCouplersGammaSameLayerB = it)) }
    FloatRow("R -> G Gamma", p.dirCouplersGammaRToG, 0f..1f, 0.353f) { update(p.copy(dirCouplersGammaRToG = it)) }
    FloatRow("R -> B Gamma", p.dirCouplersGammaRToB, 0f..1f, 0.302f) { update(p.copy(dirCouplersGammaRToB = it)) }
    FloatRow("G -> R Gamma", p.dirCouplersGammaGToR, 0f..1f, 0.154f) { update(p.copy(dirCouplersGammaGToR = it)) }
    FloatRow("G -> B Gamma", p.dirCouplersGammaGToB, 0f..1f, 0.353f) { update(p.copy(dirCouplersGammaGToB = it)) }
    FloatRow("B -> R Gamma", p.dirCouplersGammaBToR, 0f..1f, 0.168f) { update(p.copy(dirCouplersGammaBToR = it)) }
    FloatRow("B -> G Gamma", p.dirCouplersGammaBToG, 0f..1f, 0.226f) { update(p.copy(dirCouplersGammaBToG = it)) }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        color = TrayAccent,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, fontSize = 11.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun OptionRow(label: String, value: Int, labels: List<String>, onChange: (Int) -> Unit) {
    var expanded by rememberSaveable(label) { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, fontSize = 11.sp, modifier = Modifier.width(124.dp))
        Box(Modifier.weight(1f)) {
            Text(
                labels.getOrNull(value) ?: "Unknown ($value)",
                color = TrayAccent,
                fontSize = 10.sp,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth().background(Color(0xFF282B29), RoundedCornerShape(9.dp))
                    .clickable { expanded = true }.padding(horizontal = 10.dp, vertical = 9.dp),
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                labels.forEachIndexed { index, option ->
                    DropdownMenuItem(
                        text = { Text(option, fontSize = 11.sp) },
                        onClick = { onChange(index); expanded = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun FloatRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    default: Float = 0f,
    formatter: (Float) -> String = ::formatThree,
    onChange: (Float) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, fontSize = 10.sp, modifier = Modifier.width(124.dp))
        Slider(value = value.coerceIn(range), onValueChange = onChange, valueRange = range, modifier = Modifier.weight(1f))
        Text(
            formatter(value),
            color = TrayMuted,
            fontSize = 9.sp,
            textAlign = TextAlign.End,
            modifier = Modifier.width(57.dp).clickable { onChange(default) }.padding(vertical = 8.dp),
        )
    }
}

@Composable
private fun IntRow(label: String, value: Int, range: IntRange, default: Int, onChange: (Int) -> Unit) {
    FloatRow(label, value.toFloat(), range.first.toFloat()..range.last.toFloat(), default.toFloat(), ::formatZero) {
        onChange(it.roundToInt().coerceIn(range))
    }
}

private fun formatEv(value: Float) = String.format(Locale.ROOT, if (value >= 0f) "+%.2f" else "%.2f", value)
private fun formatZero(value: Float) = String.format(Locale.ROOT, "%.0f", value)
private fun formatOne(value: Float) = String.format(Locale.ROOT, "%.1f", value)
private fun formatTwo(value: Float) = String.format(Locale.ROOT, "%.2f", value)
private fun formatThree(value: Float) = String.format(Locale.ROOT, "%.3f", value)
private fun formatFour(value: Float) = String.format(Locale.ROOT, "%.4f", value)
private fun formatSix(value: Float) = String.format(Locale.ROOT, "%.6f", value)
