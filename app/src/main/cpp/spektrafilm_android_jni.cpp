#include <jni.h>
#include <android/log.h>

#include <algorithm>
#include <chrono>
#include <cstdint>
#include <cstring>
#include <iomanip>
#include <limits>
#include <memory>
#include <mutex>
#include <sstream>
#include <string>

#include "spektrafilm/src/SpektraVulkanRenderer.h"

#define LOG_TAG "SpektraFilmJNI"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

namespace {

struct SpektraAndroidParams {
    int32_t enabled;
    int32_t process;
    int32_t renderOutput;
    int32_t rgbToRawMethod;
    int32_t inputColorSpace;
    int32_t outputColorSpace;
    int32_t outputRole;
    int32_t hdrPreset;
    int32_t hdrTransfer;
    float hdrReferenceWhiteNits;
    float hdrPeakNits;
    float hdrExposureEv;
    int32_t hdrToneMapping;
    int32_t colorAdaptation;
    int32_t film;
    int32_t paper;
    int32_t printTiming;
    int32_t cameraUvFilterEnabled;
    float cameraUvCutNm;
    int32_t cameraIrFilterEnabled;
    float cameraIrCutNm;
    float filmExposureEv;
    int32_t autoExposure;
    int32_t autoExposureMethod;
    float printExposureEv;
    int32_t filmPushPullMode;
    float filmPushPullStops;
    float printPushPullStops;
    float negativeBleachBypassAmount;
    float negativeLeucoCyanCoupling;
    float printBleachBypassAmount;
    float filmGamma;
    float printGamma;
    float printShadowShape;
    float printHighlightShape;
    float filterC;
    float filterMShift;
    float filterYShift;
    float enlargerScale;
    float enlargerOffsetXPercent;
    float enlargerOffsetYPercent;
    float preflashExposure;
    float preflashMFilterShift;
    float preflashYFilterShift;
    float printerLightsR;
    float printerLightsG;
    float printerLightsB;
    int32_t printerLightsGang;
    int32_t printerLightCalibration;
    float dirCouplersAmount;
    float dirCouplersDiffusionUm;
    float dirCouplersDiffusionTailUm;
    float dirCouplersDiffusionTailWeight;
    float dirCouplersInhibitionSameLayer;
    float dirCouplersInhibitionInterlayer;
    float dirCouplersGammaSameLayerR;
    float dirCouplersGammaSameLayerG;
    float dirCouplersGammaSameLayerB;
    float dirCouplersGammaRToG;
    float dirCouplersGammaRToB;
    float dirCouplersGammaGToR;
    float dirCouplersGammaGToB;
    float dirCouplersGammaBToR;
    float dirCouplersGammaBToG;
    int32_t grainEnabled;
    int32_t grainModel;
    int32_t filmFormat;
    float grainAmount;
    float grainSaturation;
    int32_t grainSublayersEnabled;
    int32_t grainSubLayerCount;
    float grainParticleAreaUm2;
    float grainParticleScaleR;
    float grainParticleScaleG;
    float grainParticleScaleB;
    float grainParticleScaleLayer0;
    float grainParticleScaleLayer1;
    float grainParticleScaleLayer2;
    float grainDensityMinR;
    float grainDensityMinG;
    float grainDensityMinB;
    float grainUniformityR;
    float grainUniformityG;
    float grainUniformityB;
    float grainFinalBlurUm;
    float grainBlurDyeCloudsUm;
    float grainMicroStructureScale;
    float grainMicroStructureSigmaNm;
    uint32_t grainSeed;
    int32_t grainAnimate;
    float grainSynthesisSize;
    float grainSynthesisAmount;
    float grainSynthesisSharpness;
    float grainSynthesisQuality;
    int32_t grainSynthesisSamples;
    float grainSynthesisMeanRadiusUm;
    float grainSynthesisRadiusStdDevRatio;
    float grainSynthesisObservationSigmaUm;
    float grainSynthesisCellSizeRatio;
    float grainSynthesisMaxRadiusQuantile;
    float grainSynthesisCoverageEpsilon;
    int32_t grainSynthesisMaxGrainsPerCell;
    float grainSynthesisRadiusScaleR;
    float grainSynthesisRadiusScaleG;
    float grainSynthesisRadiusScaleB;
    float grainSynthesisLayerScale0;
    float grainSynthesisLayerScale1;
    float grainSynthesisLayerScale2;
    int32_t grainSynthesisLayered;
    int32_t halationEnabled;
    float scatterAmount;
    float scatterScale;
    float halationAmount;
    float halationScale;
    float halationStrengthR;
    float halationStrengthG;
    float halationStrengthB;
    float halationFirstSigmaUmR;
    float halationFirstSigmaUmG;
    float halationFirstSigmaUmB;
    float halationBoostEv;
    float halationBoostRange;
    float halationProtectEv;
    int32_t cameraDiffusionEnabled;
    int32_t cameraDiffusionFamily;
    float cameraDiffusionStrength;
    float cameraDiffusionSpatialScale;
    float cameraDiffusionHaloWarmth;
    float cameraDiffusionCoreIntensity;
    float cameraDiffusionCoreSize;
    float cameraDiffusionHaloIntensity;
    float cameraDiffusionHaloSize;
    float cameraDiffusionBloomIntensity;
    float cameraDiffusionBloomSize;
    int32_t printDiffusionEnabled;
    int32_t printDiffusionFamily;
    float printDiffusionStrength;
    float printDiffusionSpatialScale;
    float printDiffusionHaloWarmth;
    float printDiffusionCoreIntensity;
    float printDiffusionCoreSize;
    float printDiffusionHaloIntensity;
    float printDiffusionHaloSize;
    float printDiffusionBloomIntensity;
    float printDiffusionBloomSize;
    int32_t scannerEnabled;
    int32_t scannerWhiteCorrection;
    int32_t scannerBlackCorrection;
    float scannerWhiteLevel;
    float scannerBlackLevel;
    float glarePercent;
    float glareRoughness;
    float glareBlur;
    float scannerMtf50LpMm;
    float scannerUnsharpRadiusUm;
    float scannerUnsharpAmount;
    int32_t scanNegativeInvert;
    int32_t colorAdaptationInputCompression;
    int32_t colorAdaptationCurveSmoothing;
    int32_t colorAdaptationOutputLightnessCompression;
    int32_t colorAdaptationOutputChromaCompression;
    int32_t gpuRenderTiling;
};

static_assert(sizeof(SpektraAndroidParams) == 648, "Unexpected SpektraAndroidParams layout");

std::mutex gSpektraMutex;
std::unique_ptr<spektrafilm::VulkanRenderer> gSpektraRenderer;
std::string gSpektraInitializationError;

bool ensureSpektraRendererLocked() {
    if (gSpektraRenderer) {
        return true;
    }

    gSpektraRenderer = std::make_unique<spektrafilm::VulkanRenderer>();
    if (!gSpektraRenderer || !gSpektraRenderer->isAvailable()) {
        gSpektraInitializationError = gSpektraRenderer
            ? gSpektraRenderer->lastError()
            : "Unable to create the Spektra Vulkan renderer.";
        gSpektraRenderer.reset();
        return false;
    }
    gSpektraInitializationError.clear();
    return true;
}

using SpektraClock = std::chrono::steady_clock;

double elapsedMs(SpektraClock::time_point start, SpektraClock::time_point end) {
    return std::chrono::duration<double, std::milli>(end - start).count();
}

struct SpektraAndroidDiagnostics {
    bool valid = false;
    bool success = false;
    int32_t width = 0;
    int32_t height = 0;
    int32_t process = 0;
    int32_t renderOutput = 0;
    int32_t grainModel = 0;
    double wallMs = 0.0;
    double serializedWaitMs = 0.0;
    double cpuSetupMs = 0.0;
    double sourceCopyMs = 0.0;
    double commandEncodingMs = 0.0;
    double commandBufferMs = 0.0;
    double gpuCommandBufferMs = 0.0;
    double outputCopyMs = 0.0;
    uint64_t staticAllocationBytes = 0;
    uint64_t scratchAllocationBytes = 0;
    uint64_t sharedScratchAllocationBytes = 0;
    uint64_t privateScratchAllocationBytes = 0;
    uint64_t transientCachedBytes = 0;
    uint64_t transientBudgetBytes = 0;
    uint64_t uploadBytes = 0;
    uint32_t passCount = 0;
    bool sharedBackend = false;
    bool externalBackend = false;
    bool privateScratchEnabled = false;
    bool renderSerialized = false;
    bool tiledRendering = false;
    uint32_t tileCount = 0;
    uint32_t tileWidth = 0;
    uint32_t tileHeight = 0;
    uint32_t tileOverlap = 0;
    bool halationPath = false;
    bool cameraDiffusionPath = false;
    bool printDiffusionPath = false;
    bool dirPath = false;
    bool productionGrainPath = false;
    bool grainSynthesisPath = false;
    bool finalPostProcessPath = false;
};

SpektraAndroidDiagnostics gLastSpektraDiagnostics;

SpektraAndroidDiagnostics makeDiagnostics(
    const spektrafilm::RendererDiagnostics& native,
    const SpektraAndroidParams& params,
    int width,
    int height,
    bool success,
    double wallMs,
    double serializedWaitMs) {
    SpektraAndroidDiagnostics result{};
    result.valid = true;
    result.success = success;
    result.width = width;
    result.height = height;
    result.process = params.process;
    result.renderOutput = params.renderOutput;
    result.grainModel = params.grainModel;
    result.wallMs = wallMs;
    result.serializedWaitMs = serializedWaitMs;
    result.cpuSetupMs = native.cpuSetupMs;
    result.sourceCopyMs = native.sourceCopyMs;
    result.commandEncodingMs = native.commandEncodingMs;
    result.commandBufferMs = native.commandBufferMs;
    result.gpuCommandBufferMs = native.gpuCommandBufferMs;
    result.outputCopyMs = native.outputCopyMs;
    result.staticAllocationBytes = native.staticAllocationBytes;
    result.scratchAllocationBytes = native.scratchAllocationBytes;
    result.sharedScratchAllocationBytes = native.sharedScratchAllocationBytes;
    result.privateScratchAllocationBytes = native.privateScratchAllocationBytes;
    result.transientCachedBytes = native.transientCachedBytes;
    result.transientBudgetBytes = native.transientBudgetBytes;
    result.uploadBytes = native.uploadBytes;
    result.passCount = native.passCount;
    result.sharedBackend = native.sharedBackend;
    result.externalBackend = native.externalBackend;
    result.privateScratchEnabled = native.privateScratchEnabled;
    result.renderSerialized = native.renderSerialized;
    result.tiledRendering = native.tiledRendering;
    result.tileCount = native.tileCount;
    result.tileWidth = native.tileWidth;
    result.tileHeight = native.tileHeight;
    result.tileOverlap = native.tileOverlap;
    result.halationPath = native.halationPath;
    result.cameraDiffusionPath = native.cameraDiffusionPath;
    result.printDiffusionPath = native.printDiffusionPath;
    result.dirPath = native.dirPath;
    result.productionGrainPath = native.productionGrainPath;
    result.grainSynthesisPath = native.grainSynthesisPath;
    result.finalPostProcessPath = native.finalPostProcessPath;
    return result;
}

std::string diagnosticsJson(const SpektraAndroidDiagnostics& d) {
    if (!d.valid) {
        return {};
    }
    std::ostringstream out;
    out << std::fixed << std::setprecision(3)
        << "{"
        << "\"success\":" << (d.success ? "true" : "false")
        << ",\"width\":" << d.width
        << ",\"height\":" << d.height
        << ",\"process\":" << d.process
        << ",\"renderOutput\":" << d.renderOutput
        << ",\"grainModel\":" << d.grainModel
        << ",\"wallMs\":" << d.wallMs
        << ",\"serializedWaitMs\":" << d.serializedWaitMs
        << ",\"cpuSetupMs\":" << d.cpuSetupMs
        << ",\"sourceCopyMs\":" << d.sourceCopyMs
        << ",\"commandEncodingMs\":" << d.commandEncodingMs
        << ",\"commandBufferMs\":" << d.commandBufferMs
        << ",\"gpuCommandBufferMs\":" << d.gpuCommandBufferMs
        << ",\"outputCopyMs\":" << d.outputCopyMs
        << ",\"staticAllocationBytes\":" << d.staticAllocationBytes
        << ",\"scratchAllocationBytes\":" << d.scratchAllocationBytes
        << ",\"sharedScratchAllocationBytes\":" << d.sharedScratchAllocationBytes
        << ",\"privateScratchAllocationBytes\":" << d.privateScratchAllocationBytes
        << ",\"transientCachedBytes\":" << d.transientCachedBytes
        << ",\"transientBudgetBytes\":" << d.transientBudgetBytes
        << ",\"uploadBytes\":" << d.uploadBytes
        << ",\"passCount\":" << d.passCount
        << ",\"sharedBackend\":" << (d.sharedBackend ? "true" : "false")
        << ",\"externalBackend\":" << (d.externalBackend ? "true" : "false")
        << ",\"privateScratchEnabled\":" << (d.privateScratchEnabled ? "true" : "false")
        << ",\"renderSerialized\":" << (d.renderSerialized ? "true" : "false")
        << ",\"tiledRendering\":" << (d.tiledRendering ? "true" : "false")
        << ",\"tileCount\":" << d.tileCount
        << ",\"tileWidth\":" << d.tileWidth
        << ",\"tileHeight\":" << d.tileHeight
        << ",\"tileOverlap\":" << d.tileOverlap
        << ",\"halationPath\":" << (d.halationPath ? "true" : "false")
        << ",\"cameraDiffusionPath\":" << (d.cameraDiffusionPath ? "true" : "false")
        << ",\"printDiffusionPath\":" << (d.printDiffusionPath ? "true" : "false")
        << ",\"dirPath\":" << (d.dirPath ? "true" : "false")
        << ",\"productionGrainPath\":" << (d.productionGrainPath ? "true" : "false")
        << ",\"grainSynthesisPath\":" << (d.grainSynthesisPath ? "true" : "false")
        << ",\"finalPostProcessPath\":" << (d.finalPostProcessPath ? "true" : "false")
        << "}";
    return out.str();
}

#if !defined(NDEBUG)
struct SpektraBenchmarkAccumulator {
    bool configured = false;
    int32_t width = 0;
    int32_t height = 0;
    int32_t process = 0;
    int32_t grainModel = 0;
    uint32_t featureMask = 0;
    uint32_t warmupCount = 0;
    uint32_t sampleCount = 0;
    double wallSumMs = 0.0;
    double waitSumMs = 0.0;
    double commandSumMs = 0.0;
    double sourceCopySumMs = 0.0;
    double outputCopySumMs = 0.0;
    double wallMinMs = std::numeric_limits<double>::max();
    double wallMaxMs = 0.0;
};

SpektraBenchmarkAccumulator gSpektraBenchmark;

uint32_t benchmarkFeatureMask(const SpektraAndroidDiagnostics& d) {
    return (d.halationPath ? 1u << 0u : 0u) |
        (d.cameraDiffusionPath ? 1u << 1u : 0u) |
        (d.printDiffusionPath ? 1u << 2u : 0u) |
        (d.dirPath ? 1u << 3u : 0u) |
        (d.productionGrainPath ? 1u << 4u : 0u) |
        (d.grainSynthesisPath ? 1u << 5u : 0u) |
        (d.finalPostProcessPath ? 1u << 6u : 0u) |
        (d.tiledRendering ? 1u << 7u : 0u);
}

void resetBenchmarkSamples(SpektraBenchmarkAccumulator& benchmark) {
    benchmark.sampleCount = 0;
    benchmark.wallSumMs = 0.0;
    benchmark.waitSumMs = 0.0;
    benchmark.commandSumMs = 0.0;
    benchmark.sourceCopySumMs = 0.0;
    benchmark.outputCopySumMs = 0.0;
    benchmark.wallMinMs = std::numeric_limits<double>::max();
    benchmark.wallMaxMs = 0.0;
}

void recordDebugBenchmark(const SpektraAndroidDiagnostics& d) {
    if (!d.valid || !d.success) {
        return;
    }
    const uint32_t featureMask = benchmarkFeatureMask(d);
    const bool sameConfiguration =
        gSpektraBenchmark.configured &&
        gSpektraBenchmark.width == d.width &&
        gSpektraBenchmark.height == d.height &&
        gSpektraBenchmark.process == d.process &&
        gSpektraBenchmark.grainModel == d.grainModel &&
        gSpektraBenchmark.featureMask == featureMask;
    if (!sameConfiguration) {
        gSpektraBenchmark = {};
        gSpektraBenchmark.configured = true;
        gSpektraBenchmark.width = d.width;
        gSpektraBenchmark.height = d.height;
        gSpektraBenchmark.process = d.process;
        gSpektraBenchmark.grainModel = d.grainModel;
        gSpektraBenchmark.featureMask = featureMask;
        LOGI(
            "SpektraPerf cold backend=%s size=%dx%d process=%d grain=%d features=0x%x "
            "wall=%.3fms wait=%.3fms command=%.3fms copyIn=%.3fms copyOut=%.3fms "
            "passes=%u cached=%.1fMiB budget=%.1fMiB newScratch=%.1fMiB "
            "newStatic=%.1fMiB upload=%.1fMiB",
            d.externalBackend ? "main" : "standalone",
            d.width,
            d.height,
            d.process,
            d.grainModel,
            featureMask,
            d.wallMs,
            d.serializedWaitMs,
            d.commandBufferMs,
            d.sourceCopyMs,
            d.outputCopyMs,
            d.passCount,
            static_cast<double>(d.transientCachedBytes) / (1024.0 * 1024.0),
            static_cast<double>(d.transientBudgetBytes) / (1024.0 * 1024.0),
            static_cast<double>(d.scratchAllocationBytes) / (1024.0 * 1024.0),
            static_cast<double>(d.staticAllocationBytes) / (1024.0 * 1024.0),
            static_cast<double>(d.uploadBytes) / (1024.0 * 1024.0));
    }

    if (gSpektraBenchmark.warmupCount < 3u) {
        ++gSpektraBenchmark.warmupCount;
        return;
    }

    ++gSpektraBenchmark.sampleCount;
    gSpektraBenchmark.wallSumMs += d.wallMs;
    gSpektraBenchmark.waitSumMs += d.serializedWaitMs;
    gSpektraBenchmark.commandSumMs += d.commandBufferMs;
    gSpektraBenchmark.sourceCopySumMs += d.sourceCopyMs;
    gSpektraBenchmark.outputCopySumMs += d.outputCopyMs;
    gSpektraBenchmark.wallMinMs = std::min(gSpektraBenchmark.wallMinMs, d.wallMs);
    gSpektraBenchmark.wallMaxMs = std::max(gSpektraBenchmark.wallMaxMs, d.wallMs);

    constexpr uint32_t kSummarySampleCount = 30u;
    if (gSpektraBenchmark.sampleCount < kSummarySampleCount) {
        return;
    }
    const double samples = static_cast<double>(gSpektraBenchmark.sampleCount);
    LOGI(
        "SpektraPerf summary backend=%s samples=%u size=%dx%d process=%d grain=%d features=0x%x "
        "wallAvg=%.3fms wallMin=%.3fms wallMax=%.3fms waitAvg=%.3fms "
        "commandAvg=%.3fms copyInAvg=%.3fms copyOutAvg=%.3fms passes=%u "
        "cached=%.1fMiB budget=%.1fMiB newScratch=%.1fMiB newStatic=%.1fMiB "
        "upload=%.1fMiB",
        d.externalBackend ? "main" : "standalone",
        gSpektraBenchmark.sampleCount,
        d.width,
        d.height,
        d.process,
        d.grainModel,
        featureMask,
        gSpektraBenchmark.wallSumMs / samples,
        gSpektraBenchmark.wallMinMs,
        gSpektraBenchmark.wallMaxMs,
        gSpektraBenchmark.waitSumMs / samples,
        gSpektraBenchmark.commandSumMs / samples,
        gSpektraBenchmark.sourceCopySumMs / samples,
        gSpektraBenchmark.outputCopySumMs / samples,
        d.passCount,
        static_cast<double>(d.transientCachedBytes) / (1024.0 * 1024.0),
        static_cast<double>(d.transientBudgetBytes) / (1024.0 * 1024.0),
        static_cast<double>(d.scratchAllocationBytes) / (1024.0 * 1024.0),
        static_cast<double>(d.staticAllocationBytes) / (1024.0 * 1024.0),
        static_cast<double>(d.uploadBytes) / (1024.0 * 1024.0));
    resetBenchmarkSamples(gSpektraBenchmark);
}
#endif

template <typename E>
E enumValue(int32_t value) {
    return static_cast<E>(value);
}

spektrafilm::RenderParams convertParams(const SpektraAndroidParams& in) {
    spektrafilm::RenderParams p{};
    p.process = enumValue<spektrafilm::ProcessMode>(in.process);
    p.scanNegativeInvert = in.scanNegativeInvert != 0;
    p.renderOutput = enumValue<spektrafilm::RenderOutputMode>(in.renderOutput);
    p.rgbToRawMethod = enumValue<spektrafilm::RgbToRawMethod>(in.rgbToRawMethod);
    p.inputColorSpace = enumValue<spektrafilm::ColorSpace>(in.inputColorSpace);
    p.outputColorSpace = enumValue<spektrafilm::ColorSpace>(in.outputColorSpace);
    p.outputRole = enumValue<spektrafilm::OutputRole>(in.outputRole);
    p.hdrPreset = enumValue<spektrafilm::HdrPreset>(in.hdrPreset);
    p.hdrTransfer = enumValue<spektrafilm::HdrTransfer>(in.hdrTransfer);
    p.hdrReferenceWhiteNits = in.hdrReferenceWhiteNits;
    p.hdrPeakNits = in.hdrPeakNits;
    p.hdrExposureEv = in.hdrExposureEv;
    p.hdrToneMapping = enumValue<spektrafilm::HdrToneMapping>(in.hdrToneMapping);
    p.colorAdaptation = in.colorAdaptation != 0;
    p.colorAdaptationInputCompression = in.colorAdaptationInputCompression != 0;
    p.colorAdaptationCurveSmoothing = in.colorAdaptationCurveSmoothing != 0;
    p.colorAdaptationOutputLightnessCompression = in.colorAdaptationOutputLightnessCompression != 0;
    p.colorAdaptationOutputChromaCompression = in.colorAdaptationOutputChromaCompression != 0;
    p.gpuRenderTiling =
        enumValue<spektrafilm::GpuRenderTilingMode>(in.gpuRenderTiling);
    p.film = in.film;
    p.paper = in.paper;
    p.printTiming = enumValue<spektrafilm::PrintTimingMode>(in.printTiming);
    p.cameraUvFilterEnabled = in.cameraUvFilterEnabled != 0;
    p.cameraUvCutNm = in.cameraUvCutNm;
    p.cameraIrFilterEnabled = in.cameraIrFilterEnabled != 0;
    p.cameraIrCutNm = in.cameraIrCutNm;
    p.filmExposureEv = in.filmExposureEv;
    p.autoExposure = in.autoExposure != 0;
    p.autoExposureMethod = enumValue<spektrafilm::AutoExposureMethod>(in.autoExposureMethod);
    p.printExposureEv = in.printExposureEv;
    p.filmPushPullMode = enumValue<spektrafilm::PushPullMode>(in.filmPushPullMode);
    p.filmPushPullStops = in.filmPushPullStops;
    p.printPushPullStops = in.printPushPullStops;
    p.negativeBleachBypassAmount = in.negativeBleachBypassAmount;
    p.negativeLeucoCyanCoupling = in.negativeLeucoCyanCoupling;
    p.printBleachBypassAmount = in.printBleachBypassAmount;
    p.filmGamma = in.filmGamma;
    p.printGamma = in.printGamma;
    p.printShadowShape = in.printShadowShape;
    p.printHighlightShape = in.printHighlightShape;
    p.filterC = in.filterC;
    p.filterMShift = in.filterMShift;
    p.filterYShift = in.filterYShift;
    p.enlargerScale = in.enlargerScale;
    p.enlargerOffsetXPercent = in.enlargerOffsetXPercent;
    p.enlargerOffsetYPercent = in.enlargerOffsetYPercent;
    p.preflashExposure = in.preflashExposure;
    p.preflashMFilterShift = in.preflashMFilterShift;
    p.preflashYFilterShift = in.preflashYFilterShift;
    p.printerLightsR = in.printerLightsR;
    p.printerLightsG = in.printerLightsG;
    p.printerLightsB = in.printerLightsB;
    p.printerLightsGang = in.printerLightsGang != 0;
    p.printerLightCalibration = in.printerLightCalibration != 0;
    p.dirCouplersAmount = in.dirCouplersAmount;
    p.dirCouplersDiffusionUm = in.dirCouplersDiffusionUm;
    p.dirCouplersDiffusionTailUm = in.dirCouplersDiffusionTailUm;
    p.dirCouplersDiffusionTailWeight = in.dirCouplersDiffusionTailWeight;
    p.dirCouplersInhibitionSameLayer = in.dirCouplersInhibitionSameLayer;
    p.dirCouplersInhibitionInterlayer = in.dirCouplersInhibitionInterlayer;
    p.dirCouplersGammaSameLayerR = in.dirCouplersGammaSameLayerR;
    p.dirCouplersGammaSameLayerG = in.dirCouplersGammaSameLayerG;
    p.dirCouplersGammaSameLayerB = in.dirCouplersGammaSameLayerB;
    p.dirCouplersGammaRToG = in.dirCouplersGammaRToG;
    p.dirCouplersGammaRToB = in.dirCouplersGammaRToB;
    p.dirCouplersGammaGToR = in.dirCouplersGammaGToR;
    p.dirCouplersGammaGToB = in.dirCouplersGammaGToB;
    p.dirCouplersGammaBToR = in.dirCouplersGammaBToR;
    p.dirCouplersGammaBToG = in.dirCouplersGammaBToG;
    p.grainEnabled = in.grainEnabled != 0;
    p.grainModel = enumValue<spektrafilm::GrainModel>(in.grainModel);
    p.filmFormat = enumValue<spektrafilm::FilmFormat>(in.filmFormat);
    p.grainAmount = in.grainAmount;
    p.grainSaturation = in.grainSaturation;
    p.grainSublayersEnabled = in.grainSublayersEnabled != 0;
    p.grainSubLayerCount = in.grainSubLayerCount;
    p.grainParticleAreaUm2 = in.grainParticleAreaUm2;
    p.grainParticleScaleR = in.grainParticleScaleR;
    p.grainParticleScaleG = in.grainParticleScaleG;
    p.grainParticleScaleB = in.grainParticleScaleB;
    p.grainParticleScaleLayer0 = in.grainParticleScaleLayer0;
    p.grainParticleScaleLayer1 = in.grainParticleScaleLayer1;
    p.grainParticleScaleLayer2 = in.grainParticleScaleLayer2;
    p.grainDensityMinR = in.grainDensityMinR;
    p.grainDensityMinG = in.grainDensityMinG;
    p.grainDensityMinB = in.grainDensityMinB;
    p.grainUniformityR = in.grainUniformityR;
    p.grainUniformityG = in.grainUniformityG;
    p.grainUniformityB = in.grainUniformityB;
    p.grainFinalBlurUm = in.grainFinalBlurUm;
    p.grainBlurDyeCloudsUm = in.grainBlurDyeCloudsUm;
    p.grainMicroStructureScale = in.grainMicroStructureScale;
    p.grainMicroStructureSigmaNm = in.grainMicroStructureSigmaNm;
    p.grainSeed = in.grainSeed;
    p.grainAnimate = in.grainAnimate != 0;
    p.grainSynthesisSize = in.grainSynthesisSize;
    p.grainSynthesisAmount = in.grainSynthesisAmount;
    p.grainSynthesisSharpness = in.grainSynthesisSharpness;
    p.grainSynthesisQuality = in.grainSynthesisQuality;
    p.grainSynthesisSamples = in.grainSynthesisSamples;
    p.grainSynthesisMeanRadiusUm = in.grainSynthesisMeanRadiusUm;
    p.grainSynthesisRadiusStdDevRatio = in.grainSynthesisRadiusStdDevRatio;
    p.grainSynthesisObservationSigmaUm = in.grainSynthesisObservationSigmaUm;
    p.grainSynthesisCellSizeRatio = in.grainSynthesisCellSizeRatio;
    p.grainSynthesisMaxRadiusQuantile = in.grainSynthesisMaxRadiusQuantile;
    p.grainSynthesisCoverageEpsilon = in.grainSynthesisCoverageEpsilon;
    p.grainSynthesisMaxGrainsPerCell = in.grainSynthesisMaxGrainsPerCell;
    p.grainSynthesisRadiusScaleR = in.grainSynthesisRadiusScaleR;
    p.grainSynthesisRadiusScaleG = in.grainSynthesisRadiusScaleG;
    p.grainSynthesisRadiusScaleB = in.grainSynthesisRadiusScaleB;
    p.grainSynthesisLayerScale0 = in.grainSynthesisLayerScale0;
    p.grainSynthesisLayerScale1 = in.grainSynthesisLayerScale1;
    p.grainSynthesisLayerScale2 = in.grainSynthesisLayerScale2;
    p.grainSynthesisLayered = in.grainSynthesisLayered != 0;
    p.halationEnabled = in.halationEnabled != 0;
    p.scatterAmount = in.scatterAmount;
    p.scatterScale = in.scatterScale;
    p.halationAmount = in.halationAmount;
    p.halationScale = in.halationScale;
    p.halationStrengthR = in.halationStrengthR;
    p.halationStrengthG = in.halationStrengthG;
    p.halationStrengthB = in.halationStrengthB;
    p.halationFirstSigmaUmR = in.halationFirstSigmaUmR;
    p.halationFirstSigmaUmG = in.halationFirstSigmaUmG;
    p.halationFirstSigmaUmB = in.halationFirstSigmaUmB;
    p.halationBoostEv = in.halationBoostEv;
    p.halationBoostRange = in.halationBoostRange;
    p.halationProtectEv = in.halationProtectEv;
    p.cameraDiffusionEnabled = in.cameraDiffusionEnabled != 0;
    p.cameraDiffusionFamily = enumValue<spektrafilm::DiffusionFilterFamily>(in.cameraDiffusionFamily);
    p.cameraDiffusionStrength = in.cameraDiffusionStrength;
    p.cameraDiffusionSpatialScale = in.cameraDiffusionSpatialScale;
    p.cameraDiffusionHaloWarmth = in.cameraDiffusionHaloWarmth;
    p.cameraDiffusionCoreIntensity = in.cameraDiffusionCoreIntensity;
    p.cameraDiffusionCoreSize = in.cameraDiffusionCoreSize;
    p.cameraDiffusionHaloIntensity = in.cameraDiffusionHaloIntensity;
    p.cameraDiffusionHaloSize = in.cameraDiffusionHaloSize;
    p.cameraDiffusionBloomIntensity = in.cameraDiffusionBloomIntensity;
    p.cameraDiffusionBloomSize = in.cameraDiffusionBloomSize;
    p.printDiffusionEnabled = in.printDiffusionEnabled != 0;
    p.printDiffusionFamily = enumValue<spektrafilm::DiffusionFilterFamily>(in.printDiffusionFamily);
    p.printDiffusionStrength = in.printDiffusionStrength;
    p.printDiffusionSpatialScale = in.printDiffusionSpatialScale;
    p.printDiffusionHaloWarmth = in.printDiffusionHaloWarmth;
    p.printDiffusionCoreIntensity = in.printDiffusionCoreIntensity;
    p.printDiffusionCoreSize = in.printDiffusionCoreSize;
    p.printDiffusionHaloIntensity = in.printDiffusionHaloIntensity;
    p.printDiffusionHaloSize = in.printDiffusionHaloSize;
    p.printDiffusionBloomIntensity = in.printDiffusionBloomIntensity;
    p.printDiffusionBloomSize = in.printDiffusionBloomSize;
    p.scannerEnabled = in.scannerEnabled != 0;
    p.scannerWhiteCorrection = in.scannerWhiteCorrection != 0;
    p.scannerBlackCorrection = in.scannerBlackCorrection != 0;
    p.scannerWhiteLevel = in.scannerWhiteLevel;
    p.scannerBlackLevel = in.scannerBlackLevel;
    p.glarePercent = in.glarePercent;
    p.glareRoughness = in.glareRoughness;
    p.glareBlur = in.glareBlur;
    p.scannerMtf50LpMm = in.scannerMtf50LpMm;
    p.scannerUnsharpRadiusUm = in.scannerUnsharpRadiusUm;
    p.scannerUnsharpAmount = in.scannerUnsharpAmount;
    return p;
}

bool readParams(JNIEnv* env, jobject paramsBuffer, SpektraAndroidParams& out) {
    auto* ptr = static_cast<const uint8_t*>(env->GetDirectBufferAddress(paramsBuffer));
    const jlong capacity = env->GetDirectBufferCapacity(paramsBuffer);
    if (!ptr || capacity < static_cast<jlong>(sizeof(SpektraAndroidParams))) {
        LOGE("Invalid Spektra params buffer: capacity=%lld required=%zu",
             static_cast<long long>(capacity), sizeof(SpektraAndroidParams));
        return false;
    }
    std::memcpy(&out, ptr, sizeof(SpektraAndroidParams));
    return true;
}

} // namespace

extern "C" {

size_t SpektraFilmAndroidParamsSize() {
    return sizeof(SpektraAndroidParams);
}

bool SpektraFilmRenderVulkanImagesWithRendererNative(
    spektrafilm::VulkanRenderer* renderer,
    VkImage sourceImage,
    VkImage destinationImage,
    uint32_t width,
    uint32_t height,
    const void* paramsData,
    size_t paramsSize,
    double timeSeconds,
    const char** errorMessage) {
    if (errorMessage) {
        *errorMessage = nullptr;
    }
    if (!renderer || sourceImage == VK_NULL_HANDLE ||
        destinationImage == VK_NULL_HANDLE || width == 0u || height == 0u ||
        !paramsData || paramsSize < sizeof(SpektraAndroidParams)) {
        if (errorMessage) {
            *errorMessage = "Invalid shared-device Spektra Vulkan render arguments.";
        }
        return false;
    }

    SpektraAndroidParams androidParams{};
    std::memcpy(&androidParams, paramsData, sizeof(androidParams));
    if (!androidParams.enabled) {
        if (errorMessage) {
            *errorMessage = "Shared-device Spektra rendering was requested while disabled.";
        }
        return false;
    }

    const spektrafilm::RenderParams params = convertParams(androidParams);
    const bool ok = renderer->renderVulkanImages(
        sourceImage, destinationImage, width, height, params, timeSeconds);
    if (!ok && errorMessage) {
        *errorMessage = renderer->lastError().c_str();
    }
    return ok;
}

bool SpektraFilmRenderRgba16fNative(
    const void* src,
    void* dst,
    int width,
    int height,
    const void* paramsData,
    size_t paramsSize,
    double timeSeconds,
    const char** errorMessage) {
    if (errorMessage) {
        *errorMessage = nullptr;
    }
    const int64_t requiredBytes = static_cast<int64_t>(width) * height * 4 * sizeof(uint16_t);
    if (!src || !dst || width <= 0 || height <= 0 || requiredBytes <= 0 ||
        !paramsData || paramsSize < sizeof(SpektraAndroidParams)) {
        if (errorMessage) *errorMessage = "Invalid Spektra native render arguments.";
        return false;
    }

    SpektraAndroidParams androidParams{};
    std::memcpy(&androidParams, paramsData, sizeof(SpektraAndroidParams));
    if (!androidParams.enabled) {
        if (src != dst) {
            std::memcpy(dst, src, static_cast<size_t>(requiredBytes));
        }
        return true;
    }

    const SpektraClock::time_point callStart = SpektraClock::now();
    std::unique_lock<std::mutex> lock(gSpektraMutex);
    const SpektraClock::time_point lockAcquired = SpektraClock::now();
    const double serializedWaitMs = elapsedMs(callStart, lockAcquired);
    if (!ensureSpektraRendererLocked()) {
        if (errorMessage) {
            *errorMessage = gSpektraInitializationError.c_str();
        }
        const SpektraClock::time_point callEnd = SpektraClock::now();
        gLastSpektraDiagnostics = makeDiagnostics(
            {},
            androidParams,
            width,
            height,
            false,
            elapsedMs(callStart, callEnd),
            serializedWaitMs);
        return false;
    }
    if (!gSpektraRenderer->isAvailable()) {
        if (errorMessage) *errorMessage = gSpektraRenderer->lastError().c_str();
        const SpektraClock::time_point callEnd = SpektraClock::now();
        gLastSpektraDiagnostics = makeDiagnostics(
            gSpektraRenderer->lastDiagnostics(),
            androidParams,
            width,
            height,
            false,
            elapsedMs(callStart, callEnd),
            serializedWaitMs);
        return false;
    }

    spektrafilm::ImageView source{};
    source.data = src;
    source.width = width;
    source.height = height;
    source.rowBytes = width * 4 * static_cast<int32_t>(sizeof(uint16_t));
    source.components = 4;
    source.bytesPerComponent = sizeof(uint16_t);

    spektrafilm::MutableImageView destination{};
    destination.data = dst;
    destination.width = width;
    destination.height = height;
    destination.rowBytes = width * 4 * static_cast<int32_t>(sizeof(uint16_t));
    destination.components = 4;
    destination.bytesPerComponent = sizeof(uint16_t);

    spektrafilm::RenderWindow window{};
    window.x2 = width;
    window.y2 = height;

    const spektrafilm::RenderParams params = convertParams(androidParams);
    const bool ok = gSpektraRenderer->render(source, destination, window, params, timeSeconds);
    const SpektraClock::time_point callEnd = SpektraClock::now();
    gLastSpektraDiagnostics = makeDiagnostics(
        gSpektraRenderer->lastDiagnostics(),
        androidParams,
        width,
        height,
        ok,
        elapsedMs(callStart, callEnd),
        serializedWaitMs);
#if !defined(NDEBUG)
    recordDebugBenchmark(gLastSpektraDiagnostics);
#endif
    if (!ok && errorMessage) {
        *errorMessage = gSpektraRenderer->lastError().c_str();
    }
    return ok;
}

bool SpektraFilmRenderVulkanImagesNative(
    VkImage sourceImage,
    VkImage destinationImage,
    uint32_t width,
    uint32_t height,
    const void* paramsData,
    size_t paramsSize,
    double timeSeconds,
    const char** errorMessage) {
    if (errorMessage) {
        *errorMessage = nullptr;
    }
    if (sourceImage == VK_NULL_HANDLE || destinationImage == VK_NULL_HANDLE ||
        width == 0u || height == 0u ||
        !paramsData || paramsSize < sizeof(SpektraAndroidParams)) {
        if (errorMessage) {
            *errorMessage = "Invalid Spektra Vulkan image render arguments.";
        }
        return false;
    }

    SpektraAndroidParams androidParams{};
    std::memcpy(&androidParams, paramsData, sizeof(SpektraAndroidParams));
    if (!androidParams.enabled) {
        if (errorMessage) {
            *errorMessage = "Spektra Vulkan image rendering was requested while disabled.";
        }
        return false;
    }

    const SpektraClock::time_point callStart = SpektraClock::now();
    std::unique_lock<std::mutex> lock(gSpektraMutex);
    const SpektraClock::time_point lockAcquired = SpektraClock::now();
    const double serializedWaitMs = elapsedMs(callStart, lockAcquired);
    if (!ensureSpektraRendererLocked()) {
        if (errorMessage) {
            *errorMessage = gSpektraInitializationError.c_str();
        }
        return false;
    }

    const spektrafilm::RenderParams params = convertParams(androidParams);
    const bool ok = gSpektraRenderer->renderVulkanImages(
        sourceImage,
        destinationImage,
        width,
        height,
        params,
        timeSeconds);
    const SpektraClock::time_point callEnd = SpektraClock::now();
    gLastSpektraDiagnostics = makeDiagnostics(
        gSpektraRenderer->lastDiagnostics(),
        androidParams,
        static_cast<int>(width),
        static_cast<int>(height),
        ok,
        elapsedMs(callStart, callEnd),
        serializedWaitMs);
#if !defined(NDEBUG)
    recordDebugBenchmark(gLastSpektraDiagnostics);
#endif
    if (!ok && errorMessage) {
        *errorMessage = gSpektraRenderer->lastError().c_str();
    }
    return ok;
}

JNIEXPORT jboolean JNICALL
Java_com_unspektrawesome_spektra_SpektraFilmRenderer_nativeSpektraIsAvailable(
    JNIEnv*, jobject) {
    std::lock_guard<std::mutex> lock(gSpektraMutex);
    if (!ensureSpektraRendererLocked()) {
        return JNI_FALSE;
    }
    return gSpektraRenderer->isAvailable() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_unspektrawesome_spektra_SpektraFilmRenderer_nativeSpektraRenderRgba16f(
    JNIEnv* env, jobject,
    jobject sourceBuffer,
    jobject destinationBuffer,
    jint width,
    jint height,
    jobject paramsBuffer,
    jdouble timeSeconds) {
    if (width <= 0 || height <= 0) {
        LOGE("Invalid Spektra render dimensions %dx%d", width, height);
        return JNI_FALSE;
    }

    auto* src = env->GetDirectBufferAddress(sourceBuffer);
    auto* dst = env->GetDirectBufferAddress(destinationBuffer);
    const jlong srcCapacity = env->GetDirectBufferCapacity(sourceBuffer);
    const jlong dstCapacity = env->GetDirectBufferCapacity(destinationBuffer);
    const int64_t requiredBytes = static_cast<int64_t>(width) * height * 4 * sizeof(uint16_t);
    if (!src || !dst || srcCapacity < requiredBytes || dstCapacity < requiredBytes) {
        LOGE("Invalid Spektra RGBA16F buffers: src=%lld dst=%lld required=%lld",
             static_cast<long long>(srcCapacity),
             static_cast<long long>(dstCapacity),
             static_cast<long long>(requiredBytes));
        return JNI_FALSE;
    }

    SpektraAndroidParams androidParams{};
    if (!readParams(env, paramsBuffer, androidParams)) {
        return JNI_FALSE;
    }
    const char* errorMessage = nullptr;
    const bool ok = SpektraFilmRenderRgba16fNative(
        src,
        dst,
        width,
        height,
        &androidParams,
        sizeof(androidParams),
        timeSeconds,
        &errorMessage);
    if (!ok) {
        LOGE("Spektra render failed: %s", errorMessage ? errorMessage : "unknown error");
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

JNIEXPORT jstring JNICALL
Java_com_unspektrawesome_spektra_SpektraFilmRenderer_nativeSpektraLastDiagnosticsJson(
    JNIEnv* env, jobject) {
    std::lock_guard<std::mutex> lock(gSpektraMutex);
    const std::string json = diagnosticsJson(gLastSpektraDiagnostics);
    return json.empty() ? nullptr : env->NewStringUTF(json.c_str());
}

JNIEXPORT void JNICALL
Java_com_unspektrawesome_spektra_SpektraFilmRenderer_nativeSpektraRelease(
    JNIEnv*, jobject) {
    std::lock_guard<std::mutex> lock(gSpektraMutex);
    if (gSpektraRenderer) {
        gSpektraRenderer->releaseTransientResources();
    }
}

JNIEXPORT void JNICALL
Java_com_unspektrawesome_spektra_SpektraFilmRenderer_nativeSpektraDestroy(
    JNIEnv*, jobject) {
    std::lock_guard<std::mutex> lock(gSpektraMutex);
    gSpektraRenderer.reset();
    gSpektraInitializationError.clear();
    gLastSpektraDiagnostics = {};
#if !defined(NDEBUG)
    gSpektraBenchmark = {};
#endif
}

}
