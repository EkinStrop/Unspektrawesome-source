package com.unspektrawesome.spektra

import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class SpektraRenderDiagnostics(
    val success: Boolean,
    val width: Int,
    val height: Int,
    val process: Int,
    val renderOutput: Int,
    val grainModel: Int,
    val wallMs: Double,
    val serializedWaitMs: Double,
    val cpuSetupMs: Double,
    val sourceCopyMs: Double,
    val commandEncodingMs: Double,
    val commandBufferMs: Double,
    val gpuCommandBufferMs: Double,
    val outputCopyMs: Double,
    val staticAllocationBytes: Long,
    val scratchAllocationBytes: Long,
    val sharedScratchAllocationBytes: Long,
    val privateScratchAllocationBytes: Long,
    val transientCachedBytes: Long,
    val transientBudgetBytes: Long,
    val uploadBytes: Long,
    val passCount: Int,
    val sharedBackend: Boolean,
    val externalBackend: Boolean,
    val privateScratchEnabled: Boolean,
    val renderSerialized: Boolean,
    val tiledRendering: Boolean,
    val tileCount: Int,
    val tileWidth: Int,
    val tileHeight: Int,
    val tileOverlap: Int,
    val halationPath: Boolean,
    val cameraDiffusionPath: Boolean,
    val printDiffusionPath: Boolean,
    val dirPath: Boolean,
    val productionGrainPath: Boolean,
    val grainSynthesisPath: Boolean,
    val finalPostProcessPath: Boolean
) {
    companion object {
        fun fromJson(json: String): SpektraRenderDiagnostics {
            val value = JSONObject(json)
            return SpektraRenderDiagnostics(
                success = value.getBoolean("success"),
                width = value.getInt("width"),
                height = value.getInt("height"),
                process = value.getInt("process"),
                renderOutput = value.getInt("renderOutput"),
                grainModel = value.getInt("grainModel"),
                wallMs = value.getDouble("wallMs"),
                serializedWaitMs = value.getDouble("serializedWaitMs"),
                cpuSetupMs = value.getDouble("cpuSetupMs"),
                sourceCopyMs = value.getDouble("sourceCopyMs"),
                commandEncodingMs = value.getDouble("commandEncodingMs"),
                commandBufferMs = value.getDouble("commandBufferMs"),
                gpuCommandBufferMs = value.getDouble("gpuCommandBufferMs"),
                outputCopyMs = value.getDouble("outputCopyMs"),
                staticAllocationBytes = value.getLong("staticAllocationBytes"),
                scratchAllocationBytes = value.getLong("scratchAllocationBytes"),
                sharedScratchAllocationBytes = value.getLong("sharedScratchAllocationBytes"),
                privateScratchAllocationBytes = value.getLong("privateScratchAllocationBytes"),
                transientCachedBytes = value.getLong("transientCachedBytes"),
                transientBudgetBytes = value.getLong("transientBudgetBytes"),
                uploadBytes = value.getLong("uploadBytes"),
                passCount = value.getInt("passCount"),
                sharedBackend = value.getBoolean("sharedBackend"),
                externalBackend = value.getBoolean("externalBackend"),
                privateScratchEnabled = value.getBoolean("privateScratchEnabled"),
                renderSerialized = value.getBoolean("renderSerialized"),
                tiledRendering = value.getBoolean("tiledRendering"),
                tileCount = value.getInt("tileCount"),
                tileWidth = value.getInt("tileWidth"),
                tileHeight = value.getInt("tileHeight"),
                tileOverlap = value.getInt("tileOverlap"),
                halationPath = value.getBoolean("halationPath"),
                cameraDiffusionPath = value.getBoolean("cameraDiffusionPath"),
                printDiffusionPath = value.getBoolean("printDiffusionPath"),
                dirPath = value.getBoolean("dirPath"),
                productionGrainPath = value.getBoolean("productionGrainPath"),
                grainSynthesisPath = value.getBoolean("grainSynthesisPath"),
                finalPostProcessPath = value.getBoolean("finalPostProcessPath")
            )
        }
    }
}

data class SpektraFilmParams(
    val enabled: Boolean = false,
    val process: Int = 0,
    val renderOutput: Int = 0,
    val rgbToRawMethod: Int = 2,
    val inputColorSpace: Int = 15,
    val outputColorSpace: Int = 25,
    val outputRole: Int = 0,
    val hdrPreset: Int = 0,
    val hdrTransfer: Int = 0,
    val hdrReferenceWhiteNits: Float = 203f,
    val hdrPeakNits: Float = 1000f,
    val hdrExposureEv: Float = 0f,
    val hdrToneMapping: Int = 1,
    val colorAdaptation: Boolean = false,
    val film: Int = 2,
    val paper: Int = 3,
    val printTiming: Int = 0,
    val cameraUvFilterEnabled: Boolean = false,
    val cameraUvCutNm: Float = 410f,
    val cameraIrFilterEnabled: Boolean = false,
    val cameraIrCutNm: Float = 675f,
    val filmExposureEv: Float = 0f,
    val autoExposure: Boolean = false,
    val autoExposureMethod: Int = 0,
    val printExposureEv: Float = 0f,
    val filmPushPullMode: Int = 0,
    val filmPushPullStops: Float = 0f,
    val printPushPullStops: Float = 0f,
    val negativeBleachBypassAmount: Float = 0f,
    val negativeLeucoCyanCoupling: Float = 1f,
    val printBleachBypassAmount: Float = 0f,
    val filmGamma: Float = 1f,
    val printGamma: Float = 1f,
    val printShadowShape: Float = 0f,
    val printHighlightShape: Float = 0f,
    val filterC: Float = 0f,
    val filterMShift: Float = 0f,
    val filterYShift: Float = 0f,
    val enlargerScale: Float = 1f,
    val enlargerOffsetXPercent: Float = 0f,
    val enlargerOffsetYPercent: Float = 0f,
    val preflashExposure: Float = 0f,
    val preflashMFilterShift: Float = 0f,
    val preflashYFilterShift: Float = 0f,
    val printerLightsR: Float = 0f,
    val printerLightsG: Float = 0f,
    val printerLightsB: Float = 0f,
    val printerLightsGang: Boolean = false,
    val printerLightCalibration: Boolean = true,
    val dirCouplersAmount: Float = 0f,
    val dirCouplersDiffusionUm: Float = 20f,
    val dirCouplersDiffusionTailUm: Float = 200f,
    val dirCouplersDiffusionTailWeight: Float = 0.06f,
    val dirCouplersInhibitionSameLayer: Float = 1f,
    val dirCouplersInhibitionInterlayer: Float = 1f,
    val dirCouplersGammaSameLayerR: Float = 0.336f,
    val dirCouplersGammaSameLayerG: Float = 0.319f,
    val dirCouplersGammaSameLayerB: Float = 0.273f,
    val dirCouplersGammaRToG: Float = 0.353f,
    val dirCouplersGammaRToB: Float = 0.302f,
    val dirCouplersGammaGToR: Float = 0.154f,
    val dirCouplersGammaGToB: Float = 0.353f,
    val dirCouplersGammaBToR: Float = 0.168f,
    val dirCouplersGammaBToG: Float = 0.226f,
    val grainEnabled: Boolean = true,
    val grainModel: Int = 0,
    val filmFormat: Int = 4,
    val grainAmount: Float = 1f,
    val grainSaturation: Float = 1f,
    val grainSublayersEnabled: Boolean = true,
    val grainSubLayerCount: Int = 1,
    val grainParticleAreaUm2: Float = 0.1f,
    val grainParticleScaleR: Float = 1.2f,
    val grainParticleScaleG: Float = 1f,
    val grainParticleScaleB: Float = 2.5f,
    val grainParticleScaleLayer0: Float = 6f,
    val grainParticleScaleLayer1: Float = 1f,
    val grainParticleScaleLayer2: Float = 0.4f,
    val grainDensityMinR: Float = 0.04f,
    val grainDensityMinG: Float = 0.05f,
    val grainDensityMinB: Float = 0.06f,
    val grainUniformityR: Float = 0.99f,
    val grainUniformityG: Float = 0.97f,
    val grainUniformityB: Float = 0.98f,
    val grainFinalBlurUm: Float = 11.8f,
    val grainBlurDyeCloudsUm: Float = 1f,
    val grainMicroStructureScale: Float = 0.2f,
    val grainMicroStructureSigmaNm: Float = 30f,
    val grainSeed: Int = 1,
    val grainAnimate: Boolean = false,
    val grainSynthesisSize: Float = 1f,
    val grainSynthesisAmount: Float = 1f,
    val grainSynthesisSharpness: Float = 1f,
    val grainSynthesisQuality: Float = 1f,
    val grainSynthesisSamples: Int = 128,
    val grainSynthesisMeanRadiusUm: Float = 0.25f,
    val grainSynthesisRadiusStdDevRatio: Float = 0f,
    val grainSynthesisObservationSigmaUm: Float = 1f,
    val grainSynthesisCellSizeRatio: Float = 1f,
    val grainSynthesisMaxRadiusQuantile: Float = 0.999f,
    val grainSynthesisCoverageEpsilon: Float = 0.0001f,
    val grainSynthesisMaxGrainsPerCell: Int = 32,
    val grainSynthesisRadiusScaleR: Float = 1.2f,
    val grainSynthesisRadiusScaleG: Float = 1f,
    val grainSynthesisRadiusScaleB: Float = 2.5f,
    val grainSynthesisLayerScale0: Float = 6f,
    val grainSynthesisLayerScale1: Float = 1f,
    val grainSynthesisLayerScale2: Float = 0.4f,
    val grainSynthesisLayered: Boolean = true,
    val halationEnabled: Boolean = false,
    val scatterAmount: Float = 1f,
    val scatterScale: Float = 1f,
    val halationAmount: Float = 1f,
    val halationScale: Float = 1f,
    val halationStrengthR: Float = 0.05f,
    val halationStrengthG: Float = 0.015f,
    val halationStrengthB: Float = 0f,
    val halationFirstSigmaUmR: Float = 65f,
    val halationFirstSigmaUmG: Float = 65f,
    val halationFirstSigmaUmB: Float = 65f,
    val halationBoostEv: Float = 0f,
    val halationBoostRange: Float = 0.3f,
    val halationProtectEv: Float = 4f,
    val cameraDiffusionEnabled: Boolean = false,
    val cameraDiffusionFamily: Int = 1,
    val cameraDiffusionStrength: Float = 0.5f,
    val cameraDiffusionSpatialScale: Float = 1f,
    val cameraDiffusionHaloWarmth: Float = 0f,
    val cameraDiffusionCoreIntensity: Float = 1f,
    val cameraDiffusionCoreSize: Float = 1f,
    val cameraDiffusionHaloIntensity: Float = 1f,
    val cameraDiffusionHaloSize: Float = 1f,
    val cameraDiffusionBloomIntensity: Float = 1f,
    val cameraDiffusionBloomSize: Float = 1f,
    val printDiffusionEnabled: Boolean = false,
    val printDiffusionFamily: Int = 1,
    val printDiffusionStrength: Float = 0.5f,
    val printDiffusionSpatialScale: Float = 1f,
    val printDiffusionHaloWarmth: Float = 0f,
    val printDiffusionCoreIntensity: Float = 1f,
    val printDiffusionCoreSize: Float = 1f,
    val printDiffusionHaloIntensity: Float = 1f,
    val printDiffusionHaloSize: Float = 1f,
    val printDiffusionBloomIntensity: Float = 1f,
    val printDiffusionBloomSize: Float = 1f,
    val scannerEnabled: Boolean = false,
    val scannerWhiteCorrection: Boolean = false,
    val scannerBlackCorrection: Boolean = false,
    val scannerWhiteLevel: Float = 0.98f,
    val scannerBlackLevel: Float = 0.01f,
    val glarePercent: Float = 0.03f,
    val glareRoughness: Float = 0.7f,
    val glareBlur: Float = 0.5f,
    val scannerMtf50LpMm: Float = 60f,
    val scannerUnsharpRadiusUm: Float = 5f,
    val scannerUnsharpAmount: Float = 0.7f,
    val scanNegativeInvert: Boolean = false,
    val colorAdaptationInputCompression: Boolean = true,
    val colorAdaptationCurveSmoothing: Boolean = true,
    val colorAdaptationOutputLightnessCompression: Boolean = true,
    val colorAdaptationOutputChromaCompression: Boolean = true,
    val gpuRenderTiling: Int = 0
) {
    fun toByteBuffer(): ByteBuffer = writeTo(
        ByteBuffer.allocateDirect(STRUCT_SIZE).order(ByteOrder.nativeOrder()),
    )

    fun writeTo(target: ByteBuffer): ByteBuffer {
        require(target.isDirect && target.capacity() >= STRUCT_SIZE) {
            "SpektraFilm parameter target must be a direct buffer of at least $STRUCT_SIZE bytes"
        }
        val b = target.order(ByteOrder.nativeOrder()).apply { clear() }
        fun bool(v: Boolean) = b.putInt(if (v) 1 else 0)
        bool(enabled); b.putInt(process); b.putInt(renderOutput); b.putInt(rgbToRawMethod)
        b.putInt(inputColorSpace); b.putInt(outputColorSpace); b.putInt(outputRole)
        b.putInt(hdrPreset); b.putInt(hdrTransfer); b.putFloat(hdrReferenceWhiteNits)
        b.putFloat(hdrPeakNits); b.putFloat(hdrExposureEv); b.putInt(hdrToneMapping)
        bool(colorAdaptation); b.putInt(film); b.putInt(paper); b.putInt(printTiming)
        bool(cameraUvFilterEnabled); b.putFloat(cameraUvCutNm); bool(cameraIrFilterEnabled); b.putFloat(cameraIrCutNm)
        b.putFloat(filmExposureEv); bool(autoExposure); b.putInt(autoExposureMethod); b.putFloat(printExposureEv)
        b.putInt(filmPushPullMode); b.putFloat(filmPushPullStops); b.putFloat(printPushPullStops)
        b.putFloat(negativeBleachBypassAmount); b.putFloat(negativeLeucoCyanCoupling); b.putFloat(printBleachBypassAmount)
        b.putFloat(filmGamma); b.putFloat(printGamma); b.putFloat(printShadowShape); b.putFloat(printHighlightShape)
        b.putFloat(filterC); b.putFloat(filterMShift); b.putFloat(filterYShift); b.putFloat(enlargerScale)
        b.putFloat(enlargerOffsetXPercent); b.putFloat(enlargerOffsetYPercent); b.putFloat(preflashExposure)
        b.putFloat(preflashMFilterShift); b.putFloat(preflashYFilterShift); b.putFloat(printerLightsR)
        b.putFloat(printerLightsG); b.putFloat(printerLightsB); bool(printerLightsGang); bool(printerLightCalibration)
        b.putFloat(dirCouplersAmount); b.putFloat(dirCouplersDiffusionUm); b.putFloat(dirCouplersDiffusionTailUm)
        b.putFloat(dirCouplersDiffusionTailWeight); b.putFloat(dirCouplersInhibitionSameLayer); b.putFloat(dirCouplersInhibitionInterlayer)
        b.putFloat(dirCouplersGammaSameLayerR); b.putFloat(dirCouplersGammaSameLayerG); b.putFloat(dirCouplersGammaSameLayerB)
        b.putFloat(dirCouplersGammaRToG); b.putFloat(dirCouplersGammaRToB); b.putFloat(dirCouplersGammaGToR)
        b.putFloat(dirCouplersGammaGToB); b.putFloat(dirCouplersGammaBToR); b.putFloat(dirCouplersGammaBToG)
        bool(grainEnabled); b.putInt(grainModel); b.putInt(filmFormat); b.putFloat(grainAmount); b.putFloat(grainSaturation)
        bool(grainSublayersEnabled); b.putInt(grainSubLayerCount); b.putFloat(grainParticleAreaUm2)
        b.putFloat(grainParticleScaleR); b.putFloat(grainParticleScaleG); b.putFloat(grainParticleScaleB)
        b.putFloat(grainParticleScaleLayer0); b.putFloat(grainParticleScaleLayer1); b.putFloat(grainParticleScaleLayer2)
        b.putFloat(grainDensityMinR); b.putFloat(grainDensityMinG); b.putFloat(grainDensityMinB)
        b.putFloat(grainUniformityR); b.putFloat(grainUniformityG); b.putFloat(grainUniformityB)
        b.putFloat(grainFinalBlurUm); b.putFloat(grainBlurDyeCloudsUm); b.putFloat(grainMicroStructureScale)
        b.putFloat(grainMicroStructureSigmaNm); b.putInt(grainSeed); bool(grainAnimate)
        b.putFloat(grainSynthesisSize); b.putFloat(grainSynthesisAmount); b.putFloat(grainSynthesisSharpness)
        b.putFloat(grainSynthesisQuality); b.putInt(grainSynthesisSamples); b.putFloat(grainSynthesisMeanRadiusUm)
        b.putFloat(grainSynthesisRadiusStdDevRatio); b.putFloat(grainSynthesisObservationSigmaUm); b.putFloat(grainSynthesisCellSizeRatio)
        b.putFloat(grainSynthesisMaxRadiusQuantile); b.putFloat(grainSynthesisCoverageEpsilon); b.putInt(grainSynthesisMaxGrainsPerCell)
        b.putFloat(grainSynthesisRadiusScaleR); b.putFloat(grainSynthesisRadiusScaleG); b.putFloat(grainSynthesisRadiusScaleB)
        b.putFloat(grainSynthesisLayerScale0); b.putFloat(grainSynthesisLayerScale1); b.putFloat(grainSynthesisLayerScale2)
        bool(grainSynthesisLayered); bool(halationEnabled); b.putFloat(scatterAmount); b.putFloat(scatterScale)
        b.putFloat(halationAmount); b.putFloat(halationScale); b.putFloat(halationStrengthR); b.putFloat(halationStrengthG)
        b.putFloat(halationStrengthB); b.putFloat(halationFirstSigmaUmR); b.putFloat(halationFirstSigmaUmG)
        b.putFloat(halationFirstSigmaUmB); b.putFloat(halationBoostEv); b.putFloat(halationBoostRange); b.putFloat(halationProtectEv)
        bool(cameraDiffusionEnabled); b.putInt(cameraDiffusionFamily); b.putFloat(cameraDiffusionStrength)
        b.putFloat(cameraDiffusionSpatialScale); b.putFloat(cameraDiffusionHaloWarmth); b.putFloat(cameraDiffusionCoreIntensity)
        b.putFloat(cameraDiffusionCoreSize); b.putFloat(cameraDiffusionHaloIntensity); b.putFloat(cameraDiffusionHaloSize)
        b.putFloat(cameraDiffusionBloomIntensity); b.putFloat(cameraDiffusionBloomSize)
        bool(printDiffusionEnabled); b.putInt(printDiffusionFamily); b.putFloat(printDiffusionStrength)
        b.putFloat(printDiffusionSpatialScale); b.putFloat(printDiffusionHaloWarmth); b.putFloat(printDiffusionCoreIntensity)
        b.putFloat(printDiffusionCoreSize); b.putFloat(printDiffusionHaloIntensity); b.putFloat(printDiffusionHaloSize)
        b.putFloat(printDiffusionBloomIntensity); b.putFloat(printDiffusionBloomSize)
        bool(scannerEnabled); bool(scannerWhiteCorrection); bool(scannerBlackCorrection); b.putFloat(scannerWhiteLevel)
        b.putFloat(scannerBlackLevel); b.putFloat(glarePercent); b.putFloat(glareRoughness); b.putFloat(glareBlur)
        b.putFloat(scannerMtf50LpMm); b.putFloat(scannerUnsharpRadiusUm); b.putFloat(scannerUnsharpAmount)
        bool(scanNegativeInvert); bool(colorAdaptationInputCompression); bool(colorAdaptationCurveSmoothing)
        bool(colorAdaptationOutputLightnessCompression); bool(colorAdaptationOutputChromaCompression)
        b.putInt(gpuRenderTiling)
        b.flip()
        return b
    }

    fun toJsonObject(): JSONObject = JSONObject().also { o ->
        o.put("enabled", enabled)
        o.put("process", process)
        o.put("scanNegativeInvert", scanNegativeInvert)
        o.put("colorAdaptation", colorAdaptation)
        o.put("colorAdaptationInputCompression", colorAdaptationInputCompression)
        o.put("colorAdaptationCurveSmoothing", colorAdaptationCurveSmoothing)
        o.put("colorAdaptationOutputLightnessCompression", colorAdaptationOutputLightnessCompression)
        o.put("colorAdaptationOutputChromaCompression", colorAdaptationOutputChromaCompression)
        o.put("renderOutput", renderOutput)
        o.put("rgbToRawMethod", rgbToRawMethod)
        o.put("inputColorSpace", inputColorSpace)
        o.put("outputColorSpace", outputColorSpace)
        o.put("outputRole", outputRole)
        o.put("film", film)
        o.put("paper", paper)
        o.put("filmExposureEv", filmExposureEv.toDouble())
        o.put("printExposureEv", printExposureEv.toDouble())
        o.put("filmPushPullStops", filmPushPullStops.toDouble())
        o.put("printPushPullStops", printPushPullStops.toDouble())
        o.put("filmGamma", filmGamma.toDouble())
        o.put("printGamma", printGamma.toDouble())
        o.put("filterC", filterC.toDouble())
        o.put("filterMShift", filterMShift.toDouble())
        o.put("filterYShift", filterYShift.toDouble())
        o.put("grainEnabled", grainEnabled)
        o.put("grainModel", grainModel)
        o.put("grainAmount", grainAmount.toDouble())
        o.put("halationEnabled", halationEnabled)
        o.put("halationAmount", halationAmount.toDouble())
        o.put("cameraDiffusionEnabled", cameraDiffusionEnabled)
        o.put("cameraDiffusionStrength", cameraDiffusionStrength.toDouble())
        o.put("printDiffusionEnabled", printDiffusionEnabled)
        o.put("printDiffusionStrength", printDiffusionStrength.toDouble())
        o.put("scannerEnabled", scannerEnabled)
        o.put("glarePercent", glarePercent.toDouble())
        o.put("scannerUnsharpAmount", scannerUnsharpAmount.toDouble())
        o.put("scannerBlackLevel", scannerBlackLevel.toDouble())
        o.put("glareRoughness", glareRoughness.toDouble())
        o.put("glareBlur", glareBlur.toDouble())
        o.put("scannerMtf50LpMm", scannerMtf50LpMm.toDouble())
        o.put("scannerUnsharpRadiusUm", scannerUnsharpRadiusUm.toDouble())
        o.put("gpuRenderTiling", gpuRenderTiling)
        o.put("grainSynthesisRadiusScaleR", grainSynthesisRadiusScaleR.toDouble())
        o.put("grainSynthesisRadiusScaleG", grainSynthesisRadiusScaleG.toDouble())
        o.put("grainSynthesisRadiusScaleB", grainSynthesisRadiusScaleB.toDouble())
        o.put("grainSynthesisLayerScale0", grainSynthesisLayerScale0.toDouble())
        o.put("grainSynthesisLayerScale1", grainSynthesisLayerScale1.toDouble())
        o.put("grainSynthesisLayerScale2", grainSynthesisLayerScale2.toDouble())
        o.put("grainSynthesisLayered", grainSynthesisLayered)
        o.put("scatterAmount", scatterAmount.toDouble())
        o.put("scatterScale", scatterScale.toDouble())
        o.put("halationScale", halationScale.toDouble())
        o.put("halationStrengthR", halationStrengthR.toDouble())
        o.put("halationStrengthG", halationStrengthG.toDouble())
        o.put("halationStrengthB", halationStrengthB.toDouble())
        o.put("halationFirstSigmaUmR", halationFirstSigmaUmR.toDouble())
        o.put("halationFirstSigmaUmG", halationFirstSigmaUmG.toDouble())
        o.put("halationFirstSigmaUmB", halationFirstSigmaUmB.toDouble())
        o.put("halationBoostEv", halationBoostEv.toDouble())
        o.put("halationBoostRange", halationBoostRange.toDouble())
        o.put("halationProtectEv", halationProtectEv.toDouble())
        o.put("cameraDiffusionFamily", cameraDiffusionFamily)
        o.put("cameraDiffusionSpatialScale", cameraDiffusionSpatialScale.toDouble())
        o.put("cameraDiffusionHaloWarmth", cameraDiffusionHaloWarmth.toDouble())
        o.put("cameraDiffusionCoreIntensity", cameraDiffusionCoreIntensity.toDouble())
        o.put("cameraDiffusionCoreSize", cameraDiffusionCoreSize.toDouble())
        o.put("cameraDiffusionHaloIntensity", cameraDiffusionHaloIntensity.toDouble())
        o.put("cameraDiffusionHaloSize", cameraDiffusionHaloSize.toDouble())
        o.put("cameraDiffusionBloomIntensity", cameraDiffusionBloomIntensity.toDouble())
        o.put("cameraDiffusionBloomSize", cameraDiffusionBloomSize.toDouble())
        o.put("printDiffusionFamily", printDiffusionFamily)
        o.put("printDiffusionSpatialScale", printDiffusionSpatialScale.toDouble())
        o.put("printDiffusionHaloWarmth", printDiffusionHaloWarmth.toDouble())
        o.put("printDiffusionCoreIntensity", printDiffusionCoreIntensity.toDouble())
        o.put("printDiffusionCoreSize", printDiffusionCoreSize.toDouble())
        o.put("printDiffusionHaloIntensity", printDiffusionHaloIntensity.toDouble())
        o.put("printDiffusionHaloSize", printDiffusionHaloSize.toDouble())
        o.put("printDiffusionBloomIntensity", printDiffusionBloomIntensity.toDouble())
        o.put("printDiffusionBloomSize", printDiffusionBloomSize.toDouble())
        o.put("scannerWhiteCorrection", scannerWhiteCorrection)
        o.put("scannerBlackCorrection", scannerBlackCorrection)
        o.put("scannerWhiteLevel", scannerWhiteLevel.toDouble())
        o.put("dirCouplersGammaRToB", dirCouplersGammaRToB.toDouble())
        o.put("dirCouplersGammaGToR", dirCouplersGammaGToR.toDouble())
        o.put("dirCouplersGammaGToB", dirCouplersGammaGToB.toDouble())
        o.put("dirCouplersGammaBToR", dirCouplersGammaBToR.toDouble())
        o.put("dirCouplersGammaBToG", dirCouplersGammaBToG.toDouble())
        o.put("filmFormat", filmFormat)
        o.put("grainSaturation", grainSaturation.toDouble())
        o.put("grainSublayersEnabled", grainSublayersEnabled)
        o.put("grainSubLayerCount", grainSubLayerCount)
        o.put("grainParticleAreaUm2", grainParticleAreaUm2.toDouble())
        o.put("grainParticleScaleR", grainParticleScaleR.toDouble())
        o.put("grainParticleScaleG", grainParticleScaleG.toDouble())
        o.put("grainParticleScaleB", grainParticleScaleB.toDouble())
        o.put("grainParticleScaleLayer0", grainParticleScaleLayer0.toDouble())
        o.put("grainParticleScaleLayer1", grainParticleScaleLayer1.toDouble())
        o.put("grainParticleScaleLayer2", grainParticleScaleLayer2.toDouble())
        o.put("grainDensityMinR", grainDensityMinR.toDouble())
        o.put("grainDensityMinG", grainDensityMinG.toDouble())
        o.put("grainDensityMinB", grainDensityMinB.toDouble())
        o.put("grainUniformityR", grainUniformityR.toDouble())
        o.put("grainUniformityG", grainUniformityG.toDouble())
        o.put("grainUniformityB", grainUniformityB.toDouble())
        o.put("grainFinalBlurUm", grainFinalBlurUm.toDouble())
        o.put("grainBlurDyeCloudsUm", grainBlurDyeCloudsUm.toDouble())
        o.put("grainMicroStructureScale", grainMicroStructureScale.toDouble())
        o.put("grainMicroStructureSigmaNm", grainMicroStructureSigmaNm.toDouble())
        o.put("grainSeed", grainSeed)
        o.put("grainAnimate", grainAnimate)
        o.put("grainSynthesisSize", grainSynthesisSize.toDouble())
        o.put("grainSynthesisAmount", grainSynthesisAmount.toDouble())
        o.put("grainSynthesisSharpness", grainSynthesisSharpness.toDouble())
        o.put("grainSynthesisQuality", grainSynthesisQuality.toDouble())
        o.put("grainSynthesisSamples", grainSynthesisSamples)
        o.put("grainSynthesisMeanRadiusUm", grainSynthesisMeanRadiusUm.toDouble())
        o.put("grainSynthesisRadiusStdDevRatio", grainSynthesisRadiusStdDevRatio.toDouble())
        o.put("grainSynthesisObservationSigmaUm", grainSynthesisObservationSigmaUm.toDouble())
        o.put("grainSynthesisCellSizeRatio", grainSynthesisCellSizeRatio.toDouble())
        o.put("grainSynthesisMaxRadiusQuantile", grainSynthesisMaxRadiusQuantile.toDouble())
        o.put("grainSynthesisCoverageEpsilon", grainSynthesisCoverageEpsilon.toDouble())
        o.put("grainSynthesisMaxGrainsPerCell", grainSynthesisMaxGrainsPerCell)
        o.put("hdrPreset", hdrPreset)
        o.put("hdrTransfer", hdrTransfer)
        o.put("hdrReferenceWhiteNits", hdrReferenceWhiteNits.toDouble())
        o.put("hdrPeakNits", hdrPeakNits.toDouble())
        o.put("hdrExposureEv", hdrExposureEv.toDouble())
        o.put("hdrToneMapping", hdrToneMapping)
        o.put("printTiming", printTiming)
        o.put("cameraUvFilterEnabled", cameraUvFilterEnabled)
        o.put("cameraUvCutNm", cameraUvCutNm.toDouble())
        o.put("cameraIrFilterEnabled", cameraIrFilterEnabled)
        o.put("cameraIrCutNm", cameraIrCutNm.toDouble())
        o.put("autoExposure", autoExposure)
        o.put("autoExposureMethod", autoExposureMethod)
        o.put("filmPushPullMode", filmPushPullMode)
        o.put("negativeBleachBypassAmount", negativeBleachBypassAmount.toDouble())
        o.put("negativeLeucoCyanCoupling", negativeLeucoCyanCoupling.toDouble())
        o.put("printBleachBypassAmount", printBleachBypassAmount.toDouble())
        o.put("printShadowShape", printShadowShape.toDouble())
        o.put("printHighlightShape", printHighlightShape.toDouble())
        o.put("enlargerScale", enlargerScale.toDouble())
        o.put("enlargerOffsetXPercent", enlargerOffsetXPercent.toDouble())
        o.put("enlargerOffsetYPercent", enlargerOffsetYPercent.toDouble())
        o.put("preflashExposure", preflashExposure.toDouble())
        o.put("preflashMFilterShift", preflashMFilterShift.toDouble())
        o.put("preflashYFilterShift", preflashYFilterShift.toDouble())
        o.put("printerLightsR", printerLightsR.toDouble())
        o.put("printerLightsG", printerLightsG.toDouble())
        o.put("printerLightsB", printerLightsB.toDouble())
        o.put("printerLightsGang", printerLightsGang)
        o.put("printerLightCalibration", printerLightCalibration)
        o.put("dirCouplersAmount", dirCouplersAmount.toDouble())
        o.put("dirCouplersDiffusionUm", dirCouplersDiffusionUm.toDouble())
        o.put("dirCouplersDiffusionTailUm", dirCouplersDiffusionTailUm.toDouble())
        o.put("dirCouplersDiffusionTailWeight", dirCouplersDiffusionTailWeight.toDouble())
        o.put("dirCouplersInhibitionSameLayer", dirCouplersInhibitionSameLayer.toDouble())
        o.put("dirCouplersInhibitionInterlayer", dirCouplersInhibitionInterlayer.toDouble())
        o.put("dirCouplersGammaSameLayerR", dirCouplersGammaSameLayerR.toDouble())
        o.put("dirCouplersGammaSameLayerG", dirCouplersGammaSameLayerG.toDouble())
        o.put("dirCouplersGammaSameLayerB", dirCouplersGammaSameLayerB.toDouble())
        o.put("dirCouplersGammaRToG", dirCouplersGammaRToG.toDouble())
    }

    companion object {
        const val STRUCT_SIZE = 648
        val FILM_LABELS = listOf(
            "Kodak Ektar 100", "Kodak Portra 160", "Kodak Portra 400", "Kodak Portra 800",
            "Kodak Portra 800 +1", "Kodak Portra 800 +2", "Kodak Gold 200", "Kodak Ultramax 400",
            "Kodak Vision3 50D", "Kodak Vision3 250D", "Kodak Verita 200D", "Kodak Vision3 200T",
            "Kodak Vision3 500T", "Fujifilm Pro 400H", "Fujifilm C200", "Fujifilm X-TRA 400",
            "Kodak Ektachrome 100", "Kodak Kodachrome 64", "Fujifilm Velvia 100", "Fujifilm Provia 100F"
        )
        val PAPER_LABELS = listOf(
            "Kodak Endura Premier", "Kodak Ultra Endura", "Kodak Ektacolor Edge", "Kodak Supra Endura",
            "Kodak Portra Endura", "Fujifilm Crystal Archive Type II", "Kodak 2383", "Kodak 2393"
        )
        fun fromJson(obj: JSONObject?): SpektraFilmParams {
            if (obj == null) return SpektraFilmParams()
            val d = SpektraFilmParams()
            return d.copy(
                enabled = obj.optBoolean("enabled", d.enabled),
                process = obj.optInt("process", d.process),
                scanNegativeInvert = obj.optBoolean("scanNegativeInvert", d.scanNegativeInvert),
                colorAdaptation = obj.optBoolean("colorAdaptation", d.colorAdaptation),
                colorAdaptationInputCompression = obj.optBoolean("colorAdaptationInputCompression", d.colorAdaptationInputCompression),
                colorAdaptationCurveSmoothing = obj.optBoolean("colorAdaptationCurveSmoothing", d.colorAdaptationCurveSmoothing),
                colorAdaptationOutputLightnessCompression = obj.optBoolean("colorAdaptationOutputLightnessCompression", d.colorAdaptationOutputLightnessCompression),
                colorAdaptationOutputChromaCompression = obj.optBoolean("colorAdaptationOutputChromaCompression", d.colorAdaptationOutputChromaCompression),
                renderOutput = obj.optInt("renderOutput", d.renderOutput),
                rgbToRawMethod = obj.optInt("rgbToRawMethod", d.rgbToRawMethod),
                inputColorSpace = obj.optInt("inputColorSpace", d.inputColorSpace),
                outputColorSpace = obj.optInt("outputColorSpace", d.outputColorSpace),
                outputRole = obj.optInt("outputRole", d.outputRole),
                film = obj.optInt("film", d.film),
                paper = obj.optInt("paper", d.paper),
                filmExposureEv = obj.optDouble("filmExposureEv", d.filmExposureEv.toDouble()).toFloat(),
                printExposureEv = obj.optDouble("printExposureEv", d.printExposureEv.toDouble()).toFloat(),
                filmPushPullStops = obj.optDouble("filmPushPullStops", d.filmPushPullStops.toDouble()).toFloat(),
                printPushPullStops = obj.optDouble("printPushPullStops", d.printPushPullStops.toDouble()).toFloat(),
                filmGamma = obj.optDouble("filmGamma", d.filmGamma.toDouble()).toFloat(),
                printGamma = obj.optDouble("printGamma", d.printGamma.toDouble()).toFloat(),
                filterC = obj.optDouble("filterC", d.filterC.toDouble()).toFloat(),
                filterMShift = obj.optDouble("filterMShift", d.filterMShift.toDouble()).toFloat(),
                filterYShift = obj.optDouble("filterYShift", d.filterYShift.toDouble()).toFloat(),
                grainEnabled = obj.optBoolean("grainEnabled", d.grainEnabled),
                grainModel = obj.optInt("grainModel", d.grainModel),
                grainAmount = obj.optDouble("grainAmount", d.grainAmount.toDouble()).toFloat(),
                halationEnabled = obj.optBoolean("halationEnabled", d.halationEnabled),
                halationAmount = obj.optDouble("halationAmount", d.halationAmount.toDouble()).toFloat(),
                cameraDiffusionEnabled = obj.optBoolean("cameraDiffusionEnabled", d.cameraDiffusionEnabled),
                cameraDiffusionStrength = obj.optDouble("cameraDiffusionStrength", d.cameraDiffusionStrength.toDouble()).toFloat(),
                printDiffusionEnabled = obj.optBoolean("printDiffusionEnabled", d.printDiffusionEnabled),
                printDiffusionStrength = obj.optDouble("printDiffusionStrength", d.printDiffusionStrength.toDouble()).toFloat(),
                scannerEnabled = obj.optBoolean("scannerEnabled", d.scannerEnabled),
                glarePercent = obj.optDouble("glarePercent", d.glarePercent.toDouble()).toFloat(),
                scannerUnsharpAmount = obj.optDouble("scannerUnsharpAmount", d.scannerUnsharpAmount.toDouble()).toFloat(),
                hdrPreset = obj.optInt("hdrPreset", d.hdrPreset),
                hdrTransfer = obj.optInt("hdrTransfer", d.hdrTransfer),
                hdrReferenceWhiteNits = obj.optDouble("hdrReferenceWhiteNits", d.hdrReferenceWhiteNits.toDouble()).toFloat(),
                hdrPeakNits = obj.optDouble("hdrPeakNits", d.hdrPeakNits.toDouble()).toFloat(),
                hdrExposureEv = obj.optDouble("hdrExposureEv", d.hdrExposureEv.toDouble()).toFloat(),
                hdrToneMapping = obj.optInt("hdrToneMapping", d.hdrToneMapping),
                printTiming = obj.optInt("printTiming", d.printTiming),
                cameraUvFilterEnabled = obj.optBoolean("cameraUvFilterEnabled", d.cameraUvFilterEnabled),
                cameraUvCutNm = obj.optDouble("cameraUvCutNm", d.cameraUvCutNm.toDouble()).toFloat(),
                cameraIrFilterEnabled = obj.optBoolean("cameraIrFilterEnabled", d.cameraIrFilterEnabled),
                cameraIrCutNm = obj.optDouble("cameraIrCutNm", d.cameraIrCutNm.toDouble()).toFloat(),
                autoExposure = obj.optBoolean("autoExposure", d.autoExposure),
                autoExposureMethod = obj.optInt("autoExposureMethod", d.autoExposureMethod),
                filmPushPullMode = obj.optInt("filmPushPullMode", d.filmPushPullMode),
                negativeBleachBypassAmount = obj.optDouble("negativeBleachBypassAmount", d.negativeBleachBypassAmount.toDouble()).toFloat(),
                negativeLeucoCyanCoupling = obj.optDouble("negativeLeucoCyanCoupling", d.negativeLeucoCyanCoupling.toDouble()).toFloat(),
                printBleachBypassAmount = obj.optDouble("printBleachBypassAmount", d.printBleachBypassAmount.toDouble()).toFloat(),
                printShadowShape = obj.optDouble("printShadowShape", d.printShadowShape.toDouble()).toFloat(),
                printHighlightShape = obj.optDouble("printHighlightShape", d.printHighlightShape.toDouble()).toFloat(),
                enlargerScale = obj.optDouble("enlargerScale", d.enlargerScale.toDouble()).toFloat(),
                enlargerOffsetXPercent = obj.optDouble("enlargerOffsetXPercent", d.enlargerOffsetXPercent.toDouble()).toFloat(),
                enlargerOffsetYPercent = obj.optDouble("enlargerOffsetYPercent", d.enlargerOffsetYPercent.toDouble()).toFloat(),
                preflashExposure = obj.optDouble("preflashExposure", d.preflashExposure.toDouble()).toFloat(),
                preflashMFilterShift = obj.optDouble("preflashMFilterShift", d.preflashMFilterShift.toDouble()).toFloat(),
                preflashYFilterShift = obj.optDouble("preflashYFilterShift", d.preflashYFilterShift.toDouble()).toFloat(),
                printerLightsR = obj.optDouble("printerLightsR", d.printerLightsR.toDouble()).toFloat(),
                printerLightsG = obj.optDouble("printerLightsG", d.printerLightsG.toDouble()).toFloat(),
                printerLightsB = obj.optDouble("printerLightsB", d.printerLightsB.toDouble()).toFloat(),
                printerLightsGang = obj.optBoolean("printerLightsGang", d.printerLightsGang),
                printerLightCalibration = obj.optBoolean("printerLightCalibration", d.printerLightCalibration),
                dirCouplersAmount = obj.optDouble("dirCouplersAmount", d.dirCouplersAmount.toDouble()).toFloat(),
                dirCouplersDiffusionUm = obj.optDouble("dirCouplersDiffusionUm", d.dirCouplersDiffusionUm.toDouble()).toFloat(),
                dirCouplersDiffusionTailUm = obj.optDouble("dirCouplersDiffusionTailUm", d.dirCouplersDiffusionTailUm.toDouble()).toFloat(),
                dirCouplersDiffusionTailWeight = obj.optDouble("dirCouplersDiffusionTailWeight", d.dirCouplersDiffusionTailWeight.toDouble()).toFloat(),
                dirCouplersInhibitionSameLayer = obj.optDouble("dirCouplersInhibitionSameLayer", d.dirCouplersInhibitionSameLayer.toDouble()).toFloat(),
                dirCouplersInhibitionInterlayer = obj.optDouble("dirCouplersInhibitionInterlayer", d.dirCouplersInhibitionInterlayer.toDouble()).toFloat(),
                dirCouplersGammaSameLayerR = obj.optDouble("dirCouplersGammaSameLayerR", d.dirCouplersGammaSameLayerR.toDouble()).toFloat(),
                dirCouplersGammaSameLayerG = obj.optDouble("dirCouplersGammaSameLayerG", d.dirCouplersGammaSameLayerG.toDouble()).toFloat(),
                dirCouplersGammaSameLayerB = obj.optDouble("dirCouplersGammaSameLayerB", d.dirCouplersGammaSameLayerB.toDouble()).toFloat(),
                dirCouplersGammaRToG = obj.optDouble("dirCouplersGammaRToG", d.dirCouplersGammaRToG.toDouble()).toFloat(),
                dirCouplersGammaRToB = obj.optDouble("dirCouplersGammaRToB", d.dirCouplersGammaRToB.toDouble()).toFloat(),
                dirCouplersGammaGToR = obj.optDouble("dirCouplersGammaGToR", d.dirCouplersGammaGToR.toDouble()).toFloat(),
                dirCouplersGammaGToB = obj.optDouble("dirCouplersGammaGToB", d.dirCouplersGammaGToB.toDouble()).toFloat(),
                dirCouplersGammaBToR = obj.optDouble("dirCouplersGammaBToR", d.dirCouplersGammaBToR.toDouble()).toFloat(),
                dirCouplersGammaBToG = obj.optDouble("dirCouplersGammaBToG", d.dirCouplersGammaBToG.toDouble()).toFloat(),
                filmFormat = obj.optInt("filmFormat", d.filmFormat),
                grainSaturation = obj.optDouble("grainSaturation", d.grainSaturation.toDouble()).toFloat(),
                grainSublayersEnabled = obj.optBoolean("grainSublayersEnabled", d.grainSublayersEnabled),
                grainSubLayerCount = obj.optInt("grainSubLayerCount", d.grainSubLayerCount),
                grainParticleAreaUm2 = obj.optDouble("grainParticleAreaUm2", d.grainParticleAreaUm2.toDouble()).toFloat(),
                grainParticleScaleR = obj.optDouble("grainParticleScaleR", d.grainParticleScaleR.toDouble()).toFloat(),
                grainParticleScaleG = obj.optDouble("grainParticleScaleG", d.grainParticleScaleG.toDouble()).toFloat(),
                grainParticleScaleB = obj.optDouble("grainParticleScaleB", d.grainParticleScaleB.toDouble()).toFloat(),
                grainParticleScaleLayer0 = obj.optDouble("grainParticleScaleLayer0", d.grainParticleScaleLayer0.toDouble()).toFloat(),
                grainParticleScaleLayer1 = obj.optDouble("grainParticleScaleLayer1", d.grainParticleScaleLayer1.toDouble()).toFloat(),
                grainParticleScaleLayer2 = obj.optDouble("grainParticleScaleLayer2", d.grainParticleScaleLayer2.toDouble()).toFloat(),
                grainDensityMinR = obj.optDouble("grainDensityMinR", d.grainDensityMinR.toDouble()).toFloat(),
                grainDensityMinG = obj.optDouble("grainDensityMinG", d.grainDensityMinG.toDouble()).toFloat(),
                grainDensityMinB = obj.optDouble("grainDensityMinB", d.grainDensityMinB.toDouble()).toFloat(),
                grainUniformityR = obj.optDouble("grainUniformityR", d.grainUniformityR.toDouble()).toFloat(),
                grainUniformityG = obj.optDouble("grainUniformityG", d.grainUniformityG.toDouble()).toFloat(),
                grainUniformityB = obj.optDouble("grainUniformityB", d.grainUniformityB.toDouble()).toFloat(),
                grainFinalBlurUm = obj.optDouble("grainFinalBlurUm", d.grainFinalBlurUm.toDouble()).toFloat(),
                grainBlurDyeCloudsUm = obj.optDouble("grainBlurDyeCloudsUm", d.grainBlurDyeCloudsUm.toDouble()).toFloat(),
                grainMicroStructureScale = obj.optDouble("grainMicroStructureScale", d.grainMicroStructureScale.toDouble()).toFloat(),
                grainMicroStructureSigmaNm = obj.optDouble("grainMicroStructureSigmaNm", d.grainMicroStructureSigmaNm.toDouble()).toFloat(),
                grainSeed = obj.optInt("grainSeed", d.grainSeed),
                grainAnimate = obj.optBoolean("grainAnimate", d.grainAnimate),
                grainSynthesisSize = obj.optDouble("grainSynthesisSize", d.grainSynthesisSize.toDouble()).toFloat(),
                grainSynthesisAmount = obj.optDouble("grainSynthesisAmount", d.grainSynthesisAmount.toDouble()).toFloat(),
                grainSynthesisSharpness = obj.optDouble("grainSynthesisSharpness", d.grainSynthesisSharpness.toDouble()).toFloat(),
                grainSynthesisQuality = obj.optDouble("grainSynthesisQuality", d.grainSynthesisQuality.toDouble()).toFloat(),
                grainSynthesisSamples = obj.optInt("grainSynthesisSamples", d.grainSynthesisSamples),
                grainSynthesisMeanRadiusUm = obj.optDouble("grainSynthesisMeanRadiusUm", d.grainSynthesisMeanRadiusUm.toDouble()).toFloat(),
                grainSynthesisRadiusStdDevRatio = obj.optDouble("grainSynthesisRadiusStdDevRatio", d.grainSynthesisRadiusStdDevRatio.toDouble()).toFloat(),
                grainSynthesisObservationSigmaUm = obj.optDouble("grainSynthesisObservationSigmaUm", d.grainSynthesisObservationSigmaUm.toDouble()).toFloat(),
                grainSynthesisCellSizeRatio = obj.optDouble("grainSynthesisCellSizeRatio", d.grainSynthesisCellSizeRatio.toDouble()).toFloat(),
                grainSynthesisMaxRadiusQuantile = obj.optDouble("grainSynthesisMaxRadiusQuantile", d.grainSynthesisMaxRadiusQuantile.toDouble()).toFloat(),
                grainSynthesisCoverageEpsilon = obj.optDouble("grainSynthesisCoverageEpsilon", d.grainSynthesisCoverageEpsilon.toDouble()).toFloat(),
                grainSynthesisMaxGrainsPerCell = obj.optInt("grainSynthesisMaxGrainsPerCell", d.grainSynthesisMaxGrainsPerCell),
                grainSynthesisRadiusScaleR = obj.optDouble("grainSynthesisRadiusScaleR", d.grainSynthesisRadiusScaleR.toDouble()).toFloat(),
                grainSynthesisRadiusScaleG = obj.optDouble("grainSynthesisRadiusScaleG", d.grainSynthesisRadiusScaleG.toDouble()).toFloat(),
                grainSynthesisRadiusScaleB = obj.optDouble("grainSynthesisRadiusScaleB", d.grainSynthesisRadiusScaleB.toDouble()).toFloat(),
                grainSynthesisLayerScale0 = obj.optDouble("grainSynthesisLayerScale0", d.grainSynthesisLayerScale0.toDouble()).toFloat(),
                grainSynthesisLayerScale1 = obj.optDouble("grainSynthesisLayerScale1", d.grainSynthesisLayerScale1.toDouble()).toFloat(),
                grainSynthesisLayerScale2 = obj.optDouble("grainSynthesisLayerScale2", d.grainSynthesisLayerScale2.toDouble()).toFloat(),
                grainSynthesisLayered = obj.optBoolean("grainSynthesisLayered", d.grainSynthesisLayered),
                scatterAmount = obj.optDouble("scatterAmount", d.scatterAmount.toDouble()).toFloat(),
                scatterScale = obj.optDouble("scatterScale", d.scatterScale.toDouble()).toFloat(),
                halationScale = obj.optDouble("halationScale", d.halationScale.toDouble()).toFloat(),
                halationStrengthR = obj.optDouble("halationStrengthR", d.halationStrengthR.toDouble()).toFloat(),
                halationStrengthG = obj.optDouble("halationStrengthG", d.halationStrengthG.toDouble()).toFloat(),
                halationStrengthB = obj.optDouble("halationStrengthB", d.halationStrengthB.toDouble()).toFloat(),
                halationFirstSigmaUmR = obj.optDouble("halationFirstSigmaUmR", d.halationFirstSigmaUmR.toDouble()).toFloat(),
                halationFirstSigmaUmG = obj.optDouble("halationFirstSigmaUmG", d.halationFirstSigmaUmG.toDouble()).toFloat(),
                halationFirstSigmaUmB = obj.optDouble("halationFirstSigmaUmB", d.halationFirstSigmaUmB.toDouble()).toFloat(),
                halationBoostEv = obj.optDouble("halationBoostEv", d.halationBoostEv.toDouble()).toFloat(),
                halationBoostRange = obj.optDouble("halationBoostRange", d.halationBoostRange.toDouble()).toFloat(),
                halationProtectEv = obj.optDouble("halationProtectEv", d.halationProtectEv.toDouble()).toFloat(),
                cameraDiffusionFamily = obj.optInt("cameraDiffusionFamily", d.cameraDiffusionFamily),
                cameraDiffusionSpatialScale = obj.optDouble("cameraDiffusionSpatialScale", d.cameraDiffusionSpatialScale.toDouble()).toFloat(),
                cameraDiffusionHaloWarmth = obj.optDouble("cameraDiffusionHaloWarmth", d.cameraDiffusionHaloWarmth.toDouble()).toFloat(),
                cameraDiffusionCoreIntensity = obj.optDouble("cameraDiffusionCoreIntensity", d.cameraDiffusionCoreIntensity.toDouble()).toFloat(),
                cameraDiffusionCoreSize = obj.optDouble("cameraDiffusionCoreSize", d.cameraDiffusionCoreSize.toDouble()).toFloat(),
                cameraDiffusionHaloIntensity = obj.optDouble("cameraDiffusionHaloIntensity", d.cameraDiffusionHaloIntensity.toDouble()).toFloat(),
                cameraDiffusionHaloSize = obj.optDouble("cameraDiffusionHaloSize", d.cameraDiffusionHaloSize.toDouble()).toFloat(),
                cameraDiffusionBloomIntensity = obj.optDouble("cameraDiffusionBloomIntensity", d.cameraDiffusionBloomIntensity.toDouble()).toFloat(),
                cameraDiffusionBloomSize = obj.optDouble("cameraDiffusionBloomSize", d.cameraDiffusionBloomSize.toDouble()).toFloat(),
                printDiffusionFamily = obj.optInt("printDiffusionFamily", d.printDiffusionFamily),
                printDiffusionSpatialScale = obj.optDouble("printDiffusionSpatialScale", d.printDiffusionSpatialScale.toDouble()).toFloat(),
                printDiffusionHaloWarmth = obj.optDouble("printDiffusionHaloWarmth", d.printDiffusionHaloWarmth.toDouble()).toFloat(),
                printDiffusionCoreIntensity = obj.optDouble("printDiffusionCoreIntensity", d.printDiffusionCoreIntensity.toDouble()).toFloat(),
                printDiffusionCoreSize = obj.optDouble("printDiffusionCoreSize", d.printDiffusionCoreSize.toDouble()).toFloat(),
                printDiffusionHaloIntensity = obj.optDouble("printDiffusionHaloIntensity", d.printDiffusionHaloIntensity.toDouble()).toFloat(),
                printDiffusionHaloSize = obj.optDouble("printDiffusionHaloSize", d.printDiffusionHaloSize.toDouble()).toFloat(),
                printDiffusionBloomIntensity = obj.optDouble("printDiffusionBloomIntensity", d.printDiffusionBloomIntensity.toDouble()).toFloat(),
                printDiffusionBloomSize = obj.optDouble("printDiffusionBloomSize", d.printDiffusionBloomSize.toDouble()).toFloat(),
                scannerWhiteCorrection = obj.optBoolean("scannerWhiteCorrection", d.scannerWhiteCorrection),
                scannerBlackCorrection = obj.optBoolean("scannerBlackCorrection", d.scannerBlackCorrection),
                scannerWhiteLevel = obj.optDouble("scannerWhiteLevel", d.scannerWhiteLevel.toDouble()).toFloat(),
                scannerBlackLevel = obj.optDouble("scannerBlackLevel", d.scannerBlackLevel.toDouble()).toFloat(),
                glareRoughness = obj.optDouble("glareRoughness", d.glareRoughness.toDouble()).toFloat(),
                glareBlur = obj.optDouble("glareBlur", d.glareBlur.toDouble()).toFloat(),
                scannerMtf50LpMm = obj.optDouble("scannerMtf50LpMm", d.scannerMtf50LpMm.toDouble()).toFloat(),
                scannerUnsharpRadiusUm = obj.optDouble("scannerUnsharpRadiusUm", d.scannerUnsharpRadiusUm.toDouble()).toFloat(),
                gpuRenderTiling = obj.optInt("gpuRenderTiling", d.gpuRenderTiling),
            )
        }
    }
}

object SpektraFilmRenderer {
    init {
        System.loadLibrary("unspektrawesome_vulkan")
    }

    fun isAvailable(): Boolean = nativeSpektraIsAvailable()

    fun renderRgba16f(
        source: ByteBuffer,
        destination: ByteBuffer,
        width: Int,
        height: Int,
        params: SpektraFilmParams,
        timeSeconds: Double = 0.0
    ): Boolean {
        if (!source.isDirect || !destination.isDirect) return false
        return nativeSpektraRenderRgba16f(source, destination, width, height, params.toByteBuffer(), timeSeconds)
    }

    fun release() = nativeSpektraRelease()
    fun destroy() = nativeSpektraDestroy()

    fun lastDiagnostics(): SpektraRenderDiagnostics? {
        val json = nativeSpektraLastDiagnosticsJson() ?: return null
        return runCatching { SpektraRenderDiagnostics.fromJson(json) }.getOrNull()
    }

    private external fun nativeSpektraIsAvailable(): Boolean
    private external fun nativeSpektraRenderRgba16f(
        source: ByteBuffer,
        destination: ByteBuffer,
        width: Int,
        height: Int,
        params: ByteBuffer,
        timeSeconds: Double
    ): Boolean
    private external fun nativeSpektraLastDiagnosticsJson(): String?
    private external fun nativeSpektraRelease()
    private external fun nativeSpektraDestroy()
}
