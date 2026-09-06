#define VK_USE_PLATFORM_ANDROID_KHR 1

#include <jni.h>

#include <android/hardware_buffer.h>
#include <android/hardware_buffer_jni.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <vulkan/vulkan.h>

#include <algorithm>
#include <array>
#include <chrono>
#include <cmath>
#include <cstddef>
#include <cstdint>
#include <cstdlib>
#include <cstring>
#include <limits>
#include <memory>
#include <mutex>
#include <stdexcept>
#include <string>
#include <vector>

#include "present_fragment_spv.h"
#include "present_vertex_spv.h"
#include "preview_bilinear_compute_spv.h"
#include "preview_bilinear_raw_image_compute_spv.h"
#include "capture_color_compute_spv.h"
#include "rcd_green_compute_spv.h"
#include "rcd_green_raw_image_compute_spv.h"
#include "rcd_guide_compute_spv.h"
#include "rcd_guide_raw_image_compute_spv.h"
#include "rcd_median_compute_spv.h"
#include "rcd_median_raw_image_compute_spv.h"
#include "rcd_output_compute_spv.h"
#include "rcd_output_raw_image_compute_spv.h"
#include "SpektraVulkanRenderer.h"

extern "C" bool SpektraFilmRenderVulkanImagesWithRendererNative(
    spektrafilm::VulkanRenderer* renderer,
    VkImage sourceImage,
    VkImage destinationImage,
    uint32_t width,
    uint32_t height,
    const void* paramsData,
    size_t paramsSize,
    double timeSeconds,
    const char** errorMessage);

namespace {

constexpr uint32_t kFramesInFlight = 2;
constexpr VkDeviceSize kRawParametersSize = 64;
constexpr VkDeviceSize kRawColorParametersSize = 144;
constexpr VkDeviceSize kGeometryParametersSize = 64;
constexpr VkDeviceSize kSpektraFilmParametersSize = 648;

enum class RawImportKind {
    None,
    Buffer,
    Image,
};

struct RawParameters {
    int32_t dimensions[4];
    int32_t layoutInfo[4];
    float blackLevel[4];
    float whiteLevel[4];
};

static_assert(sizeof(RawParameters) == kRawParametersSize);

struct GeometryParameters {
    float sourceCrop[4];
    float activeArray[4];
    int32_t transform[4];
    float target[4];
};

static_assert(sizeof(GeometryParameters) == kGeometryParametersSize);

void require(VkResult result, const char* operation)
{
    if (result != VK_SUCCESS)
        throw std::runtime_error(std::string(operation) + " failed (" +
                                 std::to_string(result) + ")");
}

bool containsExtension(
    const std::vector<VkExtensionProperties>& extensions,
    const char* name)
{
    return std::any_of(
        extensions.begin(), extensions.end(),
        [name](const VkExtensionProperties& extension) {
            return std::strcmp(extension.extensionName, name) == 0;
        });
}

VkShaderModule makeShader(
    VkDevice device,
    const uint32_t* code,
    size_t wordCount)
{
    VkShaderModuleCreateInfo info{VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO};
    info.codeSize = wordCount * sizeof(uint32_t);
    info.pCode = code;
    VkShaderModule module = VK_NULL_HANDLE;
    require(vkCreateShaderModule(device, &info, nullptr, &module),
            "vkCreateShaderModule");
    return module;
}

class VulkanRenderer {
public:
    VulkanRenderer()
    {
        createInstance();
    }

    ~VulkanRenderer()
    {
        clearSurface();
        if (instance_ != VK_NULL_HANDLE)
            vkDestroyInstance(instance_, nullptr);
    }

    bool setSurface(JNIEnv* environment, jobject surfaceObject)
    {
        std::lock_guard<std::mutex> operationLock(operationMutex_);
        clearSurface();
        if (surfaceObject == nullptr) {
            setDiagnostic("Surface detached");
            return true;
        }

        window_ = ANativeWindow_fromSurface(environment, surfaceObject);
        if (window_ == nullptr) {
            setDiagnostic("Surface does not provide an ANativeWindow");
            return false;
        }

        try {
            VkAndroidSurfaceCreateInfoKHR surfaceInfo{
                VK_STRUCTURE_TYPE_ANDROID_SURFACE_CREATE_INFO_KHR};
            surfaceInfo.window = window_;
            require(vkCreateAndroidSurfaceKHR(
                        instance_, &surfaceInfo, nullptr, &surface_),
                    "vkCreateAndroidSurfaceKHR");
            selectDeviceAndQueue();
            createDeviceResources();
            createSwapchain();
            setDiagnostic("Vulkan RAW preview surface ready");
            return true;
        } catch (const std::exception& error) {
            setDiagnostic(error.what());
            clearSurface();
            return false;
        }
    }

    bool render(
        JNIEnv* environment,
        jobject hardwareBufferObject,
        jobject packedRawBytesObject,
        jobject rawParametersObject,
        jobject rawColorParametersObject,
        jobject geometryParametersObject,
        jobject spektraFilmParametersObject,
        jobject lensShadingObject,
        int32_t processingWidth,
        int32_t processingHeight)
    {
        std::lock_guard<std::mutex> operationLock(operationMutex_);
        Frame* activeFrame = nullptr;
        bool submitted = false;
        bool imageAcquired = false;
        try {
            if (device_ == VK_NULL_HANDLE || swapchain_ == VK_NULL_HANDLE)
                throw std::runtime_error("No Vulkan surface is attached");
            if (hardwareBufferObject == nullptr)
                throw std::runtime_error("RAW HardwareBuffer is null");
            if (processingWidth <= 0 || processingHeight <= 0)
                throw std::runtime_error("Processing dimensions must be positive");

            void* rawAddress = environment->GetDirectBufferAddress(rawParametersObject);
            jlong rawCapacity = environment->GetDirectBufferCapacity(rawParametersObject);
            void* spektraAddress =
                environment->GetDirectBufferAddress(rawColorParametersObject);
            jlong spektraCapacity =
                environment->GetDirectBufferCapacity(rawColorParametersObject);
            void* geometryAddress =
                environment->GetDirectBufferAddress(geometryParametersObject);
            jlong geometryCapacity =
                environment->GetDirectBufferCapacity(geometryParametersObject);
            void* spektraFilmAddress =
                environment->GetDirectBufferAddress(spektraFilmParametersObject);
            jlong spektraFilmCapacity =
                environment->GetDirectBufferCapacity(spektraFilmParametersObject);
            if (rawAddress == nullptr || rawCapacity <
                    static_cast<jlong>(kRawParametersSize)) {
                throw std::runtime_error(
                    "RAW parameters must be a direct ByteBuffer of at least 64 bytes");
            }
            if (spektraAddress == nullptr || spektraCapacity <
                    static_cast<jlong>(kRawColorParametersSize)) {
                throw std::runtime_error(
                    "RAW color parameters must be a direct ByteBuffer of at least 144 bytes");
            }
            if (geometryAddress == nullptr || geometryCapacity <
                    static_cast<jlong>(kGeometryParametersSize)) {
                throw std::runtime_error(
                    "Geometry parameters must be a direct ByteBuffer of at least 64 bytes");
            }
            if (spektraFilmAddress == nullptr || spektraFilmCapacity <
                    static_cast<jlong>(kSpektraFilmParametersSize)) {
                throw std::runtime_error(
                    "SpektraFilm parameters must be a direct ByteBuffer of at least 648 bytes");
            }

            std::array<std::byte, kRawColorParametersSize> spektraParameters{};
            std::memcpy(spektraParameters.data(), spektraAddress,
                        spektraParameters.size());
            std::array<std::byte, kSpektraFilmParametersSize> spektraFilmParameters{};
            std::memcpy(spektraFilmParameters.data(), spektraFilmAddress,
                        spektraFilmParameters.size());
            int32_t spektraFilmEnabled = 0;
            std::memcpy(&spektraFilmEnabled, spektraFilmParameters.data(),
                        sizeof(spektraFilmEnabled));
            float shadingWidthValue = 0.0f;
            float shadingHeightValue = 0.0f;
            std::memcpy(&shadingWidthValue, spektraParameters.data() + 136,
                        sizeof(float));
            std::memcpy(&shadingHeightValue, spektraParameters.data() + 140,
                        sizeof(float));
            uint32_t shadingWidth = checkedMapDimension(
                shadingWidthValue, "Lens shading width");
            uint32_t shadingHeight = checkedMapDimension(
                shadingHeightValue, "Lens shading height");
            if ((shadingWidth == 0) != (shadingHeight == 0))
                throw std::runtime_error(
                    "Lens shading width and height must both be zero or positive");
            uint64_t shadingTexels = static_cast<uint64_t>(shadingWidth) *
                                     static_cast<uint64_t>(shadingHeight);
            if (shadingTexels > 1024u * 1024u)
                throw std::runtime_error("Lens shading map is unreasonably large");
            VkDeviceSize shadingBytes = shadingTexels == 0
                ? sizeof(float) * 4
                : shadingTexels * sizeof(float) * 4;
            void* shadingAddress = lensShadingObject == nullptr
                ? nullptr
                : environment->GetDirectBufferAddress(lensShadingObject);
            jlong shadingCapacity = lensShadingObject == nullptr
                ? -1
                : environment->GetDirectBufferCapacity(lensShadingObject);
            if (shadingTexels > 0 &&
                (shadingAddress == nullptr || shadingCapacity <
                    static_cast<jlong>(shadingBytes))) {
                throw std::runtime_error(
                    "Lens shading map must be a direct ByteBuffer containing width times height interleaved R, G-even, G-odd, B float gains");
            }

            RawParameters rawParameters{};
            std::memcpy(&rawParameters, rawAddress, sizeof(rawParameters));
            rawParameters.dimensions[2] = processingWidth;
            rawParameters.dimensions[3] = processingHeight;
            validateRawParameters(rawParameters);

            Frame& frame = frames_[frameIndex_];
            frame.useSpektraOutput = spektraFilmEnabled != 0;
            activeFrame = &frame;
            require(vkWaitForFences(device_, 1, &frame.fence, VK_TRUE,
                                    std::numeric_limits<uint64_t>::max()),
                    "vkWaitForFences");
            releaseImportedBuffer(frame);

            if (processingWidth_ != static_cast<uint32_t>(processingWidth) ||
                processingHeight_ != static_cast<uint32_t>(processingHeight)) {
                require(vkDeviceWaitIdle(device_), "vkDeviceWaitIdle");
                createProcessingImages(
                    static_cast<uint32_t>(processingWidth),
                    static_cast<uint32_t>(processingHeight));
            }

            ensureLensShadingBuffer(frame, shadingBytes);
            if (shadingTexels > 0) {
                std::memcpy(frame.lensShadingMapped, shadingAddress,
                            static_cast<size_t>(shadingBytes));
            } else {
                const std::array<float, 4> identity{1.0f, 1.0f, 1.0f, 1.0f};
                std::memcpy(frame.lensShadingMapped, identity.data(),
                            sizeof(identity));
            }

            importRawBuffer(
                environment, hardwareBufferObject, packedRawBytesObject,
                rawParameters, frame);
            std::memcpy(frame.rawMapped, &rawParameters, sizeof(rawParameters));
            std::memcpy(frame.spektraMapped, spektraParameters.data(),
                        spektraParameters.size());
            std::memcpy(frame.geometryMapped, geometryAddress,
                        static_cast<size_t>(kGeometryParametersSize));
            updateFrameDescriptors(frame);

            uint32_t imageIndex = 0;
            VkResult acquire = vkAcquireNextImageKHR(
                device_, swapchain_, std::numeric_limits<uint64_t>::max(),
                frame.imageAvailable, VK_NULL_HANDLE, &imageIndex);
            if (acquire == VK_ERROR_OUT_OF_DATE_KHR) {
                releaseImportedBuffer(frame);
                recreateSwapchain();
                setDiagnostic("Swapchain recreated after acquire");
                return false;
            }
            if (acquire != VK_SUCCESS && acquire != VK_SUBOPTIMAL_KHR)
                require(acquire, "vkAcquireNextImageKHR");
            imageAcquired = true;

            require(vkResetCommandBuffer(frame.commandBuffer, 0),
                    "vkResetCommandBuffer");
            recordRawCommands(frame);

            VkSubmitInfo rawSubmit{VK_STRUCTURE_TYPE_SUBMIT_INFO};
            rawSubmit.commandBufferCount = 1;
            rawSubmit.pCommandBuffers = &frame.commandBuffer;
            require(vkResetFences(device_, 1, &frame.fence),
                    "vkResetFences for RAW processing");
            VkResult rawSubmitResult;
            {
                std::lock_guard<std::recursive_mutex> queueLock(queueMutex_);
                rawSubmitResult = vkQueueSubmit(
                    queue_, 1, &rawSubmit, frame.fence);
            }
            if (rawSubmitResult != VK_SUCCESS) {
                restoreSignaledFence(frame);
                require(rawSubmitResult, "vkQueueSubmit for RAW processing");
            }
            require(vkWaitForFences(device_, 1, &frame.fence, VK_TRUE,
                                    std::numeric_limits<uint64_t>::max()),
                    "vkWaitForFences for RAW processing");

            VkImage outputImage = frame.processingImage;
            if (frame.useSpektraOutput) {
                const auto now = std::chrono::steady_clock::now().time_since_epoch();
                const double timeSeconds =
                    std::chrono::duration<double>(now).count();
                const char* spektraError = nullptr;
                if (!SpektraFilmRenderVulkanImagesWithRendererNative(
                        spektraRenderer_.get(), frame.processingImage,
                        frame.spektraImage, processingWidth_, processingHeight_,
                        spektraFilmParameters.data(),
                        spektraFilmParameters.size(), timeSeconds,
                        &spektraError)) {
                    throw std::runtime_error(
                        std::string("SpektraFilm preview failed: ") +
                        (spektraError ? spektraError : "unknown native error"));
                }
                frame.spektraInitialized = true;
                outputImage = frame.spektraImage;
            }

            require(vkResetCommandBuffer(frame.commandBuffer, 0),
                    "vkResetCommandBuffer for presentation");
            recordPresentCommands(frame, imageIndex, outputImage);

            VkPipelineStageFlags waitStage =
                VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
            VkSubmitInfo submit{VK_STRUCTURE_TYPE_SUBMIT_INFO};
            submit.waitSemaphoreCount = 1;
            submit.pWaitSemaphores = &frame.imageAvailable;
            submit.pWaitDstStageMask = &waitStage;
            submit.commandBufferCount = 1;
            submit.pCommandBuffers = &frame.commandBuffer;
            submit.signalSemaphoreCount = 1;
            submit.pSignalSemaphores = &frame.renderFinished;
            require(vkResetFences(device_, 1, &frame.fence), "vkResetFences");
            VkResult submitResult;
            {
                std::lock_guard<std::recursive_mutex> queueLock(queueMutex_);
                submitResult = vkQueueSubmit(queue_, 1, &submit, frame.fence);
            }
            if (submitResult != VK_SUCCESS) {
                vkDestroyFence(device_, frame.fence, nullptr);
                VkFenceCreateInfo fenceInfo{VK_STRUCTURE_TYPE_FENCE_CREATE_INFO};
                fenceInfo.flags = VK_FENCE_CREATE_SIGNALED_BIT;
                require(vkCreateFence(device_, &fenceInfo, nullptr, &frame.fence),
                        "vkCreateFence after failed submit");
                require(submitResult, "vkQueueSubmit");
            }
            submitted = true;

            VkPresentInfoKHR present{VK_STRUCTURE_TYPE_PRESENT_INFO_KHR};
            present.waitSemaphoreCount = 1;
            present.pWaitSemaphores = &frame.renderFinished;
            present.swapchainCount = 1;
            present.pSwapchains = &swapchain_;
            present.pImageIndices = &imageIndex;
            VkResult presented;
            {
                std::lock_guard<std::recursive_mutex> queueLock(queueMutex_);
                presented = vkQueuePresentKHR(queue_, &present);
            }
            if (presented == VK_ERROR_OUT_OF_DATE_KHR ||
                presented == VK_SUBOPTIMAL_KHR || acquire == VK_SUBOPTIMAL_KHR) {
                recreateSwapchain();
            } else if (presented != VK_SUCCESS) {
                require(presented, "vkQueuePresentKHR");
            }

            frameIndex_ = (frameIndex_ + 1) % kFramesInFlight;
            setDiagnostic(
                rawParameters.layoutInfo[1] == 0
                    ? "Frame rendered from zero-copy RAW_SENSOR HardwareBuffer"
                    : "Frame rendered from packed RAW Vulkan upload buffer");
            return true;
        } catch (const std::exception& error) {
            if (activeFrame != nullptr && !submitted) {
                releaseImportedBuffer(*activeFrame);
                activeFrame->processingInitialized = false;
            }
            if (imageAcquired && !submitted) {
                try {
                    recreateSwapchain();
                } catch (...) {
                }
            }
            setDiagnostic(error.what());
            return false;
        }
    }

    bool captureRcd(
        JNIEnv* environment,
        jobject hardwareBufferObject,
        jobject packedRawBytesObject,
        jobject rawParametersObject,
        jobject rawColorParametersObject,
        jobject geometryParametersObject,
        jobject spektraFilmParametersObject,
        jobject lensShadingObject,
        jobject outputObject,
        double spektraTimeSeconds)
    {
        std::lock_guard<std::mutex> operationLock(operationMutex_);
        bool rawSubmitted = false;
        try {
            if (device_ == VK_NULL_HANDLE)
                throw std::runtime_error("Vulkan renderer is not initialized");
            if (hardwareBufferObject == nullptr)
                throw std::runtime_error("RAW HardwareBuffer is null");

            void* rawAddress = environment->GetDirectBufferAddress(rawParametersObject);
            jlong rawCapacity = environment->GetDirectBufferCapacity(rawParametersObject);
            void* spektraAddress =
                environment->GetDirectBufferAddress(rawColorParametersObject);
            jlong spektraCapacity =
                environment->GetDirectBufferCapacity(rawColorParametersObject);
            void* geometryAddress =
                environment->GetDirectBufferAddress(geometryParametersObject);
            jlong geometryCapacity =
                environment->GetDirectBufferCapacity(geometryParametersObject);
            void* spektraFilmAddress =
                environment->GetDirectBufferAddress(spektraFilmParametersObject);
            jlong spektraFilmCapacity =
                environment->GetDirectBufferCapacity(spektraFilmParametersObject);
            void* outputAddress = environment->GetDirectBufferAddress(outputObject);
            jlong outputCapacity = environment->GetDirectBufferCapacity(outputObject);
            if (rawAddress == nullptr || rawCapacity <
                    static_cast<jlong>(kRawParametersSize)) {
                throw std::runtime_error(
                    "RAW parameters must be a direct ByteBuffer of at least 64 bytes");
            }
            if (spektraAddress == nullptr || spektraCapacity <
                    static_cast<jlong>(kRawColorParametersSize)) {
                throw std::runtime_error(
                    "RAW color parameters must be a direct ByteBuffer of at least 144 bytes");
            }
            if (geometryAddress == nullptr || geometryCapacity <
                    static_cast<jlong>(kGeometryParametersSize)) {
                throw std::runtime_error(
                    "Geometry parameters must be a direct ByteBuffer of at least 64 bytes");
            }
            if (spektraFilmAddress == nullptr || spektraFilmCapacity <
                    static_cast<jlong>(kSpektraFilmParametersSize)) {
                throw std::runtime_error(
                    "SpektraFilm parameters must be a direct ByteBuffer of at least 648 bytes");
            }
            if (outputAddress == nullptr)
                throw std::runtime_error("Capture output must be a direct ByteBuffer");

            RawParameters rawParameters{};
            std::memcpy(&rawParameters, rawAddress, sizeof(rawParameters));
            validateRawParameters(rawParameters);
            GeometryParameters geometryParameters{};
            std::memcpy(
                &geometryParameters, geometryAddress,
                sizeof(geometryParameters));
            if (geometryParameters.transform[0] < 0 ||
                geometryParameters.transform[0] > 3 ||
                geometryParameters.transform[2] <= 0 ||
                geometryParameters.transform[3] <= 0) {
                throw std::runtime_error(
                    "Capture geometry has an invalid transform or output size");
            }
            for (float value : geometryParameters.sourceCrop) {
                if (!std::isfinite(value))
                    throw std::runtime_error(
                        "Capture source crop must be finite");
            }
            if (geometryParameters.sourceCrop[0] < 0.0f ||
                geometryParameters.sourceCrop[1] < 0.0f ||
                geometryParameters.sourceCrop[2] <= 0.0f ||
                geometryParameters.sourceCrop[3] <= 0.0f ||
                geometryParameters.sourceCrop[0] +
                        geometryParameters.sourceCrop[2] > 1.0001f ||
                geometryParameters.sourceCrop[1] +
                        geometryParameters.sourceCrop[3] > 1.0001f) {
                throw std::runtime_error(
                    "Capture source crop must stay within normalized RAW bounds");
            }
            uint32_t rawWidth = static_cast<uint32_t>(rawParameters.dimensions[0]);
            uint32_t rawHeight = static_cast<uint32_t>(rawParameters.dimensions[1]);
            uint32_t outputWidth =
                static_cast<uint32_t>(geometryParameters.transform[2]);
            uint32_t outputHeight =
                static_cast<uint32_t>(geometryParameters.transform[3]);
            rawParameters.dimensions[2] = static_cast<int32_t>(outputWidth);
            rawParameters.dimensions[3] = static_cast<int32_t>(outputHeight);
            uint64_t outputBytes64 = static_cast<uint64_t>(outputWidth) *
                                     static_cast<uint64_t>(outputHeight) * 4u;
            if (outputBytes64 > static_cast<uint64_t>(
                    std::numeric_limits<jlong>::max()) ||
                outputCapacity < static_cast<jlong>(outputBytes64)) {
                throw std::runtime_error(
                    "Capture output ByteBuffer is smaller than oriented width times height times four bytes");
            }

            std::array<std::byte, kRawColorParametersSize> spektraParameters{};
            std::memcpy(spektraParameters.data(), spektraAddress,
                        spektraParameters.size());
            std::array<std::byte, kSpektraFilmParametersSize> spektraFilmParameters{};
            std::memcpy(spektraFilmParameters.data(), spektraFilmAddress,
                        spektraFilmParameters.size());
            int32_t spektraFilmEnabled = 0;
            std::memcpy(&spektraFilmEnabled, spektraFilmParameters.data(),
                        sizeof(spektraFilmEnabled));
            float shadingWidthValue = 0.0f;
            float shadingHeightValue = 0.0f;
            std::memcpy(&shadingWidthValue, spektraParameters.data() + 136,
                        sizeof(float));
            std::memcpy(&shadingHeightValue, spektraParameters.data() + 140,
                        sizeof(float));
            uint32_t shadingWidth = checkedMapDimension(
                shadingWidthValue, "Lens shading width");
            uint32_t shadingHeight = checkedMapDimension(
                shadingHeightValue, "Lens shading height");
            if ((shadingWidth == 0) != (shadingHeight == 0))
                throw std::runtime_error(
                    "Lens shading width and height must both be zero or positive");
            uint64_t shadingTexels = static_cast<uint64_t>(shadingWidth) *
                                     static_cast<uint64_t>(shadingHeight);
            if (shadingTexels > 1024u * 1024u)
                throw std::runtime_error("Lens shading map is unreasonably large");
            VkDeviceSize shadingBytes = shadingTexels == 0
                ? sizeof(float) * 4
                : shadingTexels * sizeof(float) * 4;
            void* shadingAddress = lensShadingObject == nullptr
                ? nullptr
                : environment->GetDirectBufferAddress(lensShadingObject);
            jlong shadingCapacity = lensShadingObject == nullptr
                ? -1
                : environment->GetDirectBufferCapacity(lensShadingObject);
            if (shadingTexels > 0 &&
                (shadingAddress == nullptr || shadingCapacity <
                    static_cast<jlong>(shadingBytes))) {
                throw std::runtime_error(
                    "Lens shading map must contain width times height interleaved R, G-even, G-odd, B float gains");
            }

            Frame& frame = captureFrame_;
            frame.useSpektraOutput = spektraFilmEnabled != 0;
            require(vkWaitForFences(device_, 1, &frame.fence, VK_TRUE,
                                    std::numeric_limits<uint64_t>::max()),
                    "vkWaitForFences for capture");
            releaseImportedBuffer(frame);
            ensureCaptureResources(outputWidth, outputHeight);
            ensureRcdResources(rawWidth, rawHeight);
            ensureRcdPipelines(
                rawWidth, rawHeight, rawParameters.layoutInfo[2]);
            ensureLensShadingBuffer(frame, shadingBytes);
            if (shadingTexels > 0) {
                std::memcpy(frame.lensShadingMapped, shadingAddress,
                            static_cast<size_t>(shadingBytes));
            } else {
                const std::array<float, 4> identity{1.0f, 1.0f, 1.0f, 1.0f};
                std::memcpy(frame.lensShadingMapped, identity.data(),
                            sizeof(identity));
            }
            importRawBuffer(
                environment, hardwareBufferObject, packedRawBytesObject,
                rawParameters, frame);
            std::memcpy(frame.rawMapped, &rawParameters, sizeof(rawParameters));
            std::memcpy(frame.spektraMapped, spektraParameters.data(),
                        spektraParameters.size());
            std::memcpy(frame.geometryMapped, &geometryParameters,
                        sizeof(geometryParameters));
            updateCaptureDescriptor(frame);
            updateRcdDescriptors(frame);

            require(vkResetCommandBuffer(frame.commandBuffer, 0),
                    "vkResetCommandBuffer for capture");
            recordCaptureCommands(frame);
            VkSubmitInfo submit{VK_STRUCTURE_TYPE_SUBMIT_INFO};
            submit.commandBufferCount = 1;
            submit.pCommandBuffers = &frame.commandBuffer;
            require(vkResetFences(device_, 1, &frame.fence),
                    "vkResetFences for capture");
            VkResult submitResult;
            {
                std::lock_guard<std::recursive_mutex> queueLock(queueMutex_);
                submitResult = vkQueueSubmit(queue_, 1, &submit, frame.fence);
            }
            if (submitResult != VK_SUCCESS) {
                restoreSignaledFence(frame);
                require(submitResult, "vkQueueSubmit for capture");
            }
            rawSubmitted = true;
            require(vkWaitForFences(device_, 1, &frame.fence, VK_TRUE,
                                    std::numeric_limits<uint64_t>::max()),
                    "vkWaitForFences for capture RAW processing");
            releaseImportedBuffer(frame);

            VkImage outputImage = captureImage_;
            if (frame.useSpektraOutput) {
                const char* spektraError = nullptr;
                if (!SpektraFilmRenderVulkanImagesWithRendererNative(
                        spektraRenderer_.get(), captureImage_,
                        captureSpektraImage_, outputWidth, outputHeight,
                        spektraFilmParameters.data(),
                        spektraFilmParameters.size(), spektraTimeSeconds,
                        &spektraError)) {
                    throw std::runtime_error(
                        std::string("SpektraFilm capture failed: ") +
                        (spektraError ? spektraError : "unknown native error"));
                }
                outputImage = captureSpektraImage_;
            }

            require(vkResetCommandBuffer(frame.commandBuffer, 0),
                    "vkResetCommandBuffer for capture readback");
            recordCaptureReadbackCommands(frame, outputImage);
            require(vkResetFences(device_, 1, &frame.fence),
                    "vkResetFences for capture readback");
            {
                std::lock_guard<std::recursive_mutex> queueLock(queueMutex_);
                submitResult = vkQueueSubmit(queue_, 1, &submit, frame.fence);
            }
            if (submitResult != VK_SUCCESS) {
                restoreSignaledFence(frame);
                require(submitResult, "vkQueueSubmit for capture readback");
            }
            require(vkWaitForFences(device_, 1, &frame.fence, VK_TRUE,
                                    std::numeric_limits<uint64_t>::max()),
                    "vkWaitForFences for capture readback");

            std::memcpy(outputAddress, captureStagingMapped_,
                        static_cast<size_t>(outputBytes64));
            setDiagnostic(
                frame.useSpektraOutput
                    ? "Full-resolution RCD capture processed by SpektraFilm"
                    : "Full-resolution linear RCD capture completed");
            return true;
        } catch (const std::exception& error) {
            releaseImportedBuffer(captureFrame_);
            if (!rawSubmitted) {
                captureInitialized_ = false;
                captureSpektraLayoutInitialized_ = false;
                rcdLayoutsInitialized_ = false;
            }
            setDiagnostic(error.what());
            return false;
        }
    }

    std::string diagnostic() const
    {
        std::lock_guard<std::mutex> lock(diagnosticMutex_);
        return diagnostic_;
    }

private:
    struct Frame {
        VkCommandBuffer commandBuffer = VK_NULL_HANDLE;
        VkFence fence = VK_NULL_HANDLE;
        VkSemaphore imageAvailable = VK_NULL_HANDLE;
        VkSemaphore renderFinished = VK_NULL_HANDLE;
        VkBuffer rawUniform = VK_NULL_HANDLE;
        VkDeviceMemory rawUniformMemory = VK_NULL_HANDLE;
        void* rawMapped = nullptr;
        VkBuffer spektraUniform = VK_NULL_HANDLE;
        VkDeviceMemory spektraUniformMemory = VK_NULL_HANDLE;
        void* spektraMapped = nullptr;
        VkBuffer geometryUniform = VK_NULL_HANDLE;
        VkDeviceMemory geometryUniformMemory = VK_NULL_HANDLE;
        void* geometryMapped = nullptr;
        VkBuffer lensShadingBuffer = VK_NULL_HANDLE;
        VkDeviceMemory lensShadingMemory = VK_NULL_HANDLE;
        void* lensShadingMapped = nullptr;
        VkDeviceSize lensShadingCapacity = 0;
        VkDescriptorSet computeDescriptor = VK_NULL_HANDLE;
        VkDescriptorSet presentDescriptor = VK_NULL_HANDLE;
        VkImage processingImage = VK_NULL_HANDLE;
        VkDeviceMemory processingMemory = VK_NULL_HANDLE;
        VkImageView processingView = VK_NULL_HANDLE;
        bool processingInitialized = false;
        VkImage spektraImage = VK_NULL_HANDLE;
        VkDeviceMemory spektraMemory = VK_NULL_HANDLE;
        VkImageView spektraView = VK_NULL_HANDLE;
        bool spektraLayoutInitialized = false;
        bool spektraInitialized = false;
        bool useSpektraOutput = false;
        RawImportKind rawImportKind = RawImportKind::None;
        VkBuffer packedRawBuffer = VK_NULL_HANDLE;
        VkDeviceMemory packedRawMemory = VK_NULL_HANDLE;
        void* packedRawMapped = nullptr;
        VkDeviceSize packedRawCapacity = 0;
        VkImage importedImage = VK_NULL_HANDLE;
        VkImageView importedImageView = VK_NULL_HANDLE;
        VkDeviceMemory importedMemory = VK_NULL_HANDLE;
        AHardwareBuffer* retainedHardwareBuffer = nullptr;
    };

    enum RcdStage : size_t {
        RcdGuide = 0,
        RcdGreen = 1,
        RcdMedian = 2,
        RcdOutput = 3,
        RcdStageCount = 4,
    };

    enum RcdInput : size_t {
        RcdBufferInput = 0,
        RcdImageInput = 1,
        RcdInputCount = 2,
    };

    void setDiagnostic(const std::string& value)
    {
        std::lock_guard<std::mutex> lock(diagnosticMutex_);
        diagnostic_ = value;
    }

    void restoreSignaledFence(Frame& frame)
    {
        if (frame.fence != VK_NULL_HANDLE)
            vkDestroyFence(device_, frame.fence, nullptr);
        frame.fence = VK_NULL_HANDLE;
        VkFenceCreateInfo fenceInfo{VK_STRUCTURE_TYPE_FENCE_CREATE_INFO};
        fenceInfo.flags = VK_FENCE_CREATE_SIGNALED_BIT;
        require(vkCreateFence(device_, &fenceInfo, nullptr, &frame.fence),
                "vkCreateFence after failed submit");
    }

    static uint32_t checkedMapDimension(float value, const char* name)
    {
        if (!std::isfinite(value) || value < 0.0f || value > 65535.0f ||
            std::abs(value - std::round(value)) > 0.001f) {
            throw std::runtime_error(std::string(name) +
                                     " must be a nonnegative integer");
        }
        return static_cast<uint32_t>(std::round(value));
    }

    void createInstance()
    {
        uint32_t version = VK_API_VERSION_1_0;
        auto enumerateVersion = reinterpret_cast<PFN_vkEnumerateInstanceVersion>(
            vkGetInstanceProcAddr(VK_NULL_HANDLE, "vkEnumerateInstanceVersion"));
        if (enumerateVersion == nullptr ||
            enumerateVersion(&version) != VK_SUCCESS ||
            version < VK_API_VERSION_1_1) {
            throw std::runtime_error("Vulkan 1.1 loader is required");
        }

        uint32_t extensionCount = 0;
        require(vkEnumerateInstanceExtensionProperties(
                    nullptr, &extensionCount, nullptr),
                "vkEnumerateInstanceExtensionProperties");
        std::vector<VkExtensionProperties> extensions(extensionCount);
        require(vkEnumerateInstanceExtensionProperties(
                    nullptr, &extensionCount, extensions.data()),
                "vkEnumerateInstanceExtensionProperties");
        if (!containsExtension(extensions, VK_KHR_SURFACE_EXTENSION_NAME) ||
            !containsExtension(extensions, VK_KHR_ANDROID_SURFACE_EXTENSION_NAME)) {
            throw std::runtime_error("Android Vulkan surface extensions are unavailable");
        }

        VkApplicationInfo application{VK_STRUCTURE_TYPE_APPLICATION_INFO};
        application.pApplicationName = "Unspektrawesome";
        application.applicationVersion = 1;
        application.pEngineName = "Unspektrawesome";
        application.engineVersion = 1;
        application.apiVersion = VK_API_VERSION_1_1;
        std::array<const char*, 2> names{
            VK_KHR_SURFACE_EXTENSION_NAME,
            VK_KHR_ANDROID_SURFACE_EXTENSION_NAME};
        VkInstanceCreateInfo info{VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO};
        info.pApplicationInfo = &application;
        info.enabledExtensionCount = static_cast<uint32_t>(names.size());
        info.ppEnabledExtensionNames = names.data();
        require(vkCreateInstance(&info, nullptr, &instance_), "vkCreateInstance");
    }

    void selectDeviceAndQueue()
    {
        uint32_t deviceCount = 0;
        require(vkEnumeratePhysicalDevices(instance_, &deviceCount, nullptr),
                "vkEnumeratePhysicalDevices");
        std::vector<VkPhysicalDevice> devices(deviceCount);
        require(vkEnumeratePhysicalDevices(
                    instance_, &deviceCount, devices.data()),
                "vkEnumeratePhysicalDevices");

        for (VkPhysicalDevice candidate : devices) {
            VkPhysicalDeviceProperties properties{};
            vkGetPhysicalDeviceProperties(candidate, &properties);
            if (properties.apiVersion < VK_API_VERSION_1_1)
                continue;

            uint32_t extensionCount = 0;
            if (vkEnumerateDeviceExtensionProperties(
                    candidate, nullptr, &extensionCount, nullptr) != VK_SUCCESS)
                continue;
            std::vector<VkExtensionProperties> extensions(extensionCount);
            if (vkEnumerateDeviceExtensionProperties(
                    candidate, nullptr, &extensionCount,
                    extensions.data()) != VK_SUCCESS)
                continue;
            if (!containsExtension(extensions, VK_KHR_SWAPCHAIN_EXTENSION_NAME) ||
                !containsExtension(
                    extensions,
                    VK_ANDROID_EXTERNAL_MEMORY_ANDROID_HARDWARE_BUFFER_EXTENSION_NAME) ||
                !containsExtension(
                    extensions, VK_EXT_QUEUE_FAMILY_FOREIGN_EXTENSION_NAME) ||
                !containsExtension(
                    extensions, VK_KHR_SHADER_FLOAT16_INT8_EXTENSION_NAME))
                continue;

            VkPhysicalDeviceShaderFloat16Int8Features float16Features{
                VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SHADER_FLOAT16_INT8_FEATURES};
            VkPhysicalDeviceFeatures2 features{
                VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_FEATURES_2};
            features.pNext = &float16Features;
            vkGetPhysicalDeviceFeatures2(candidate, &features);
            if (float16Features.shaderFloat16 != VK_TRUE)
                continue;

            uint32_t familyCount = 0;
            vkGetPhysicalDeviceQueueFamilyProperties(
                candidate, &familyCount, nullptr);
            std::vector<VkQueueFamilyProperties> families(familyCount);
            vkGetPhysicalDeviceQueueFamilyProperties(
                candidate, &familyCount, families.data());
            for (uint32_t index = 0; index < familyCount; ++index) {
                VkBool32 presentSupported = VK_FALSE;
                if (vkGetPhysicalDeviceSurfaceSupportKHR(
                        candidate, index, surface_, &presentSupported) != VK_SUCCESS)
                    continue;
                VkQueueFlags required =
                    VK_QUEUE_GRAPHICS_BIT | VK_QUEUE_COMPUTE_BIT;
                if ((families[index].queueFlags & required) == required &&
                    presentSupported == VK_TRUE) {
                    physicalDevice_ = candidate;
                    queueFamily_ = index;
                    return;
                }
            }
        }
        throw std::runtime_error(
            "No Vulkan 1.1 device supports compute, shaderFloat16 RCD, presentation, swapchain, Android HardwareBuffer import, and foreign queue ownership");
    }

    void createDeviceResources()
    {
        float priority = 1.0f;
        VkDeviceQueueCreateInfo queueInfo{
            VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO};
        queueInfo.queueFamilyIndex = queueFamily_;
        queueInfo.queueCount = 1;
        queueInfo.pQueuePriorities = &priority;
        std::array<const char*, 4> extensions{
            VK_KHR_SWAPCHAIN_EXTENSION_NAME,
            VK_ANDROID_EXTERNAL_MEMORY_ANDROID_HARDWARE_BUFFER_EXTENSION_NAME,
            VK_EXT_QUEUE_FAMILY_FOREIGN_EXTENSION_NAME,
            VK_KHR_SHADER_FLOAT16_INT8_EXTENSION_NAME};
        VkPhysicalDeviceShaderFloat16Int8Features float16Features{
            VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SHADER_FLOAT16_INT8_FEATURES};
        float16Features.shaderFloat16 = VK_TRUE;
        VkDeviceCreateInfo info{VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO};
        info.pNext = &float16Features;
        info.queueCreateInfoCount = 1;
        info.pQueueCreateInfos = &queueInfo;
        info.enabledExtensionCount = static_cast<uint32_t>(extensions.size());
        info.ppEnabledExtensionNames = extensions.data();
        require(vkCreateDevice(physicalDevice_, &info, nullptr, &device_),
                "vkCreateDevice");
        vkGetDeviceQueue(device_, queueFamily_, 0, &queue_);
        getAhbProperties_ =
            reinterpret_cast<PFN_vkGetAndroidHardwareBufferPropertiesANDROID>(
                vkGetDeviceProcAddr(
                    device_, "vkGetAndroidHardwareBufferPropertiesANDROID"));
        if (getAhbProperties_ == nullptr)
            throw std::runtime_error(
                "Android HardwareBuffer import function is unavailable");

        VkCommandPoolCreateInfo poolInfo{
            VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO};
        poolInfo.flags = VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT;
        poolInfo.queueFamilyIndex = queueFamily_;
        require(vkCreateCommandPool(device_, &poolInfo, nullptr, &commandPool_),
                "vkCreateCommandPool");
        createDescriptorResources();
        createComputePipeline();
        createFrames();
        spektrafilm::ExternalVulkanContext spektraContext{};
        spektraContext.instance = instance_;
        spektraContext.physicalDevice = physicalDevice_;
        spektraContext.device = device_;
        spektraContext.computeQueue = queue_;
        spektraContext.computeQueueFamily = queueFamily_;
        spektraContext.queueMutex = &queueMutex_;
        spektraRenderer_ =
            std::make_unique<spektrafilm::VulkanRenderer>(spektraContext);
        if (!spektraRenderer_->isAvailable()) {
            throw std::runtime_error(
                "Spektra shared Vulkan renderer is unavailable: " +
                spektraRenderer_->lastError());
        }
    }

    uint32_t memoryType(uint32_t bits, VkMemoryPropertyFlags flags) const
    {
        VkPhysicalDeviceMemoryProperties properties{};
        vkGetPhysicalDeviceMemoryProperties(physicalDevice_, &properties);
        for (uint32_t index = 0; index < properties.memoryTypeCount; ++index) {
            if ((bits & (1u << index)) != 0 &&
                (properties.memoryTypes[index].propertyFlags & flags) == flags)
                return index;
        }
        throw std::runtime_error("No compatible Vulkan memory type");
    }

    void makeBuffer(
        VkDeviceSize size,
        VkBufferUsageFlags usage,
        VkMemoryPropertyFlags memoryFlags,
        VkBuffer& buffer,
        VkDeviceMemory& memory,
        void** mapped)
    {
        VkBufferCreateInfo bufferInfo{VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO};
        bufferInfo.size = size;
        bufferInfo.usage = usage;
        bufferInfo.sharingMode = VK_SHARING_MODE_EXCLUSIVE;
        require(vkCreateBuffer(device_, &bufferInfo, nullptr, &buffer),
                "vkCreateBuffer");
        VkMemoryRequirements requirements{};
        vkGetBufferMemoryRequirements(device_, buffer, &requirements);
        VkMemoryAllocateInfo allocation{VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO};
        allocation.allocationSize = requirements.size;
        allocation.memoryTypeIndex = memoryType(
            requirements.memoryTypeBits, memoryFlags);
        require(vkAllocateMemory(device_, &allocation, nullptr, &memory),
                "vkAllocateMemory");
        require(vkBindBufferMemory(device_, buffer, memory, 0),
                "vkBindBufferMemory");
        if (mapped != nullptr) {
            require(vkMapMemory(device_, memory, 0, size, 0, mapped),
                    "vkMapMemory");
        }
    }

    void ensureLensShadingBuffer(Frame& frame, VkDeviceSize size)
    {
        if (frame.lensShadingBuffer != VK_NULL_HANDLE &&
            frame.lensShadingCapacity == size)
            return;
        if (frame.lensShadingMapped != nullptr)
            vkUnmapMemory(device_, frame.lensShadingMemory);
        if (frame.lensShadingBuffer != VK_NULL_HANDLE)
            vkDestroyBuffer(device_, frame.lensShadingBuffer, nullptr);
        if (frame.lensShadingMemory != VK_NULL_HANDLE)
            vkFreeMemory(device_, frame.lensShadingMemory, nullptr);
        frame.lensShadingBuffer = VK_NULL_HANDLE;
        frame.lensShadingMemory = VK_NULL_HANDLE;
        frame.lensShadingMapped = nullptr;
        frame.lensShadingCapacity = 0;
        makeBuffer(size, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT,
                   VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT |
                       VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
                   frame.lensShadingBuffer, frame.lensShadingMemory,
                   &frame.lensShadingMapped);
        frame.lensShadingCapacity = size;
    }

    void ensurePackedRawBuffer(Frame& frame, VkDeviceSize size)
    {
        if (frame.packedRawBuffer != VK_NULL_HANDLE &&
            frame.packedRawCapacity >= size)
            return;
        if (frame.packedRawMapped != nullptr)
            vkUnmapMemory(device_, frame.packedRawMemory);
        if (frame.packedRawBuffer != VK_NULL_HANDLE)
            vkDestroyBuffer(device_, frame.packedRawBuffer, nullptr);
        if (frame.packedRawMemory != VK_NULL_HANDLE)
            vkFreeMemory(device_, frame.packedRawMemory, nullptr);
        frame.packedRawBuffer = VK_NULL_HANDLE;
        frame.packedRawMemory = VK_NULL_HANDLE;
        frame.packedRawMapped = nullptr;
        frame.packedRawCapacity = 0;
        makeBuffer(
            size, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT,
            VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT,
            frame.packedRawBuffer, frame.packedRawMemory,
            &frame.packedRawMapped);
        frame.packedRawCapacity = size;
    }

    void destroyPackedRawBuffer(Frame& frame)
    {
        if (frame.packedRawMapped != nullptr)
            vkUnmapMemory(device_, frame.packedRawMemory);
        if (frame.packedRawBuffer != VK_NULL_HANDLE)
            vkDestroyBuffer(device_, frame.packedRawBuffer, nullptr);
        if (frame.packedRawMemory != VK_NULL_HANDLE)
            vkFreeMemory(device_, frame.packedRawMemory, nullptr);
        frame.packedRawBuffer = VK_NULL_HANDLE;
        frame.packedRawMemory = VK_NULL_HANDLE;
        frame.packedRawMapped = nullptr;
        frame.packedRawCapacity = 0;
    }

    void createDescriptorResources()
    {
        std::array<VkDescriptorSetLayoutBinding, 8> computeBindings{};
        computeBindings[0] = {0, VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, 1,
                              VK_SHADER_STAGE_COMPUTE_BIT, nullptr};
        computeBindings[1] = {1, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, 1,
                              VK_SHADER_STAGE_COMPUTE_BIT, nullptr};
        computeBindings[2] = {2, VK_DESCRIPTOR_TYPE_STORAGE_IMAGE, 1,
                              VK_SHADER_STAGE_COMPUTE_BIT, nullptr};
        computeBindings[3] = {3, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, 1,
                              VK_SHADER_STAGE_COMPUTE_BIT, nullptr};
        computeBindings[4] = {4, VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, 1,
                              VK_SHADER_STAGE_COMPUTE_BIT, nullptr};
        computeBindings[5] = {5, VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, 1,
                              VK_SHADER_STAGE_COMPUTE_BIT, nullptr};
        computeBindings[6] = {6, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, 1,
                              VK_SHADER_STAGE_COMPUTE_BIT, nullptr};
        computeBindings[7] = {7, VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, 1,
                              VK_SHADER_STAGE_COMPUTE_BIT, nullptr};
        VkDescriptorSetLayoutCreateInfo computeInfo{
            VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO};
        computeInfo.bindingCount = static_cast<uint32_t>(computeBindings.size());
        computeInfo.pBindings = computeBindings.data();
        require(vkCreateDescriptorSetLayout(
                    device_, &computeInfo, nullptr, &computeDescriptorLayout_),
                "vkCreateDescriptorSetLayout");

        VkDescriptorSetLayoutBinding presentBinding{
            0, VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, 1,
            VK_SHADER_STAGE_FRAGMENT_BIT, nullptr};
        VkDescriptorSetLayoutCreateInfo presentInfo{
            VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO};
        presentInfo.bindingCount = 1;
        presentInfo.pBindings = &presentBinding;
        require(vkCreateDescriptorSetLayout(
                    device_, &presentInfo, nullptr, &presentDescriptorLayout_),
                "vkCreateDescriptorSetLayout");

        std::array<VkDescriptorPoolSize, 4> sizes{{
            {VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, (kFramesInFlight + 1) * 2},
            {VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, (kFramesInFlight + 1) * 3},
            {VK_DESCRIPTOR_TYPE_STORAGE_IMAGE, kFramesInFlight + 1},
            {VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
             (kFramesInFlight + 1) * 2 + kFramesInFlight}}};
        VkDescriptorPoolCreateInfo poolInfo{
            VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO};
        poolInfo.maxSets = kFramesInFlight * 2 + 1;
        poolInfo.poolSizeCount = static_cast<uint32_t>(sizes.size());
        poolInfo.pPoolSizes = sizes.data();
        require(vkCreateDescriptorPool(
                    device_, &poolInfo, nullptr, &descriptorPool_),
                "vkCreateDescriptorPool");

        VkSamplerCreateInfo samplerInfo{VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO};
        samplerInfo.magFilter = VK_FILTER_LINEAR;
        samplerInfo.minFilter = VK_FILTER_LINEAR;
        samplerInfo.mipmapMode = VK_SAMPLER_MIPMAP_MODE_NEAREST;
        samplerInfo.addressModeU = VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
        samplerInfo.addressModeV = VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
        samplerInfo.addressModeW = VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
        samplerInfo.maxLod = 0.0f;
        require(vkCreateSampler(device_, &samplerInfo, nullptr, &sampler_),
                "vkCreateSampler");

        samplerInfo.magFilter = VK_FILTER_NEAREST;
        samplerInfo.minFilter = VK_FILTER_NEAREST;
        require(vkCreateSampler(
                    device_, &samplerInfo, nullptr, &rawIntegerSampler_),
                "vkCreateSampler for integer RAW image");

        std::array<VkDescriptorSetLayoutBinding, 7> rcdBindings{{
            {0, VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, 1,
             VK_SHADER_STAGE_COMPUTE_BIT, nullptr},
            {1, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, 1,
             VK_SHADER_STAGE_COMPUTE_BIT, nullptr},
            {2, VK_DESCRIPTOR_TYPE_STORAGE_IMAGE, 1,
             VK_SHADER_STAGE_COMPUTE_BIT, nullptr},
            {5, VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, 1,
             VK_SHADER_STAGE_COMPUTE_BIT, nullptr},
            {6, VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, 1,
             VK_SHADER_STAGE_COMPUTE_BIT, nullptr},
            {7, VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, 1,
             VK_SHADER_STAGE_COMPUTE_BIT, nullptr},
            {8, VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, 1,
             VK_SHADER_STAGE_COMPUTE_BIT, nullptr},
        }};
        VkDescriptorSetLayoutCreateInfo rcdLayoutInfo{
            VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO};
        rcdLayoutInfo.bindingCount =
            static_cast<uint32_t>(rcdBindings.size());
        rcdLayoutInfo.pBindings = rcdBindings.data();
        require(vkCreateDescriptorSetLayout(
                    device_, &rcdLayoutInfo, nullptr,
                    &rcdDescriptorLayout_),
                "vkCreateDescriptorSetLayout for staged RCD");

        std::array<VkDescriptorPoolSize, 4> rcdPoolSizes{{
            {VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, RcdStageCount},
            {VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, RcdStageCount},
            {VK_DESCRIPTOR_TYPE_STORAGE_IMAGE, RcdStageCount},
            {VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
             RcdStageCount * 4},
        }};
        VkDescriptorPoolCreateInfo rcdPoolInfo{
            VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO};
        rcdPoolInfo.maxSets = RcdStageCount;
        rcdPoolInfo.poolSizeCount =
            static_cast<uint32_t>(rcdPoolSizes.size());
        rcdPoolInfo.pPoolSizes = rcdPoolSizes.data();
        require(vkCreateDescriptorPool(
                    device_, &rcdPoolInfo, nullptr, &rcdDescriptorPool_),
                "vkCreateDescriptorPool for staged RCD");

        std::array<VkDescriptorSetLayout, RcdStageCount> rcdLayouts{};
        rcdLayouts.fill(rcdDescriptorLayout_);
        VkDescriptorSetAllocateInfo rcdAllocateInfo{
            VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO};
        rcdAllocateInfo.descriptorPool = rcdDescriptorPool_;
        rcdAllocateInfo.descriptorSetCount = RcdStageCount;
        rcdAllocateInfo.pSetLayouts = rcdLayouts.data();
        require(vkAllocateDescriptorSets(
                    device_, &rcdAllocateInfo, rcdDescriptorSets_.data()),
                "vkAllocateDescriptorSets for staged RCD");

        VkPipelineLayoutCreateInfo rcdPipelineLayoutInfo{
            VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO};
        rcdPipelineLayoutInfo.setLayoutCount = 1;
        rcdPipelineLayoutInfo.pSetLayouts = &rcdDescriptorLayout_;
        require(vkCreatePipelineLayout(
                    device_, &rcdPipelineLayoutInfo, nullptr,
                    &rcdPipelineLayout_),
                "vkCreatePipelineLayout for staged RCD");
    }

    void createComputePipeline()
    {
        VkPipelineLayoutCreateInfo layoutInfo{
            VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO};
        layoutInfo.setLayoutCount = 1;
        layoutInfo.pSetLayouts = &computeDescriptorLayout_;
        VkPushConstantRange captureTransform{
            VK_SHADER_STAGE_COMPUTE_BIT, 0, sizeof(int32_t) * 2};
        layoutInfo.pushConstantRangeCount = 1;
        layoutInfo.pPushConstantRanges = &captureTransform;
        require(vkCreatePipelineLayout(
                    device_, &layoutInfo, nullptr, &computePipelineLayout_),
                "vkCreatePipelineLayout");
        VkShaderModule shader = makeShader(
            device_, kPreviewBilinearSpirv.data(),
            kPreviewBilinearSpirv.size());
        VkPipelineShaderStageCreateInfo stage{
            VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO};
        stage.stage = VK_SHADER_STAGE_COMPUTE_BIT;
        stage.module = shader;
        stage.pName = "main";
        VkComputePipelineCreateInfo pipelineInfo{
            VK_STRUCTURE_TYPE_COMPUTE_PIPELINE_CREATE_INFO};
        pipelineInfo.stage = stage;
        pipelineInfo.layout = computePipelineLayout_;
        VkResult result = vkCreateComputePipelines(
            device_, VK_NULL_HANDLE, 1, &pipelineInfo, nullptr,
            &computePipeline_);
        vkDestroyShaderModule(device_, shader, nullptr);
        require(result, "vkCreateComputePipelines");

        shader = makeShader(
            device_, kPreviewBilinearImageSpirv.data(),
            kPreviewBilinearImageSpirv.size());
        stage.module = shader;
        pipelineInfo.stage = stage;
        result = vkCreateComputePipelines(
            device_, VK_NULL_HANDLE, 1, &pipelineInfo, nullptr,
            &computeImagePipeline_);
        vkDestroyShaderModule(device_, shader, nullptr);
        require(result, "vkCreateComputePipelines for RAW image preview");

        shader = makeShader(
            device_, kCaptureColorSpirv.data(), kCaptureColorSpirv.size());
        stage.module = shader;
        pipelineInfo.stage = stage;
        result = vkCreateComputePipelines(
            device_, VK_NULL_HANDLE, 1, &pipelineInfo, nullptr,
            &captureColorPipeline_);
        vkDestroyShaderModule(device_, shader, nullptr);
        require(result, "vkCreateComputePipelines for capture color");

    }

    struct RcdWorkgroup {
        uint32_t x;
        uint32_t y;
    };

    RcdWorkgroup chooseRcdWorkgroup() const
    {
        VkPhysicalDeviceProperties properties{};
        vkGetPhysicalDeviceProperties(physicalDevice_, &properties);
        const bool supports16x16 =
            properties.limits.maxComputeWorkGroupInvocations >= 256 &&
            properties.limits.maxComputeWorkGroupSize[0] >= 16 &&
            properties.limits.maxComputeWorkGroupSize[1] >= 16 &&
            properties.limits.maxComputeSharedMemorySize >= 8 * 1024;
        if (!supports16x16)
            return {8, 8};

        const char* adreno = std::strstr(properties.deviceName, "Adreno");
        if (adreno != nullptr) {
            while (*adreno != '\0' && (*adreno < '0' || *adreno > '9'))
                ++adreno;
            const int gpu = *adreno == '\0' ? 0 : std::atoi(adreno);
            if (gpu > 0 && gpu < 700)
                return {16, 8};
        }
        return {16, 16};
    }

    VkPipeline createRcdPipeline(
        const uint32_t* code,
        size_t wordCount,
        uint32_t width,
        uint32_t height,
        int32_t cfaPattern,
        RcdWorkgroup workgroup)
    {
        std::array<std::array<int32_t, 2>, 4> redOffsets{{
            {{0, 0}},
            {{1, 0}},
            {{0, 1}},
            {{1, 1}},
        }};
        if (cfaPattern < 0 || cfaPattern >=
                static_cast<int32_t>(redOffsets.size())) {
            throw std::runtime_error("RCD requires a standard Bayer CFA");
        }

        struct SpecializationData {
            int32_t redOffsetX;
            int32_t redOffsetY;
            int32_t width;
            int32_t height;
            uint32_t localSizeX;
            uint32_t localSizeY;
        } data{
            redOffsets[static_cast<size_t>(cfaPattern)][0],
            redOffsets[static_cast<size_t>(cfaPattern)][1],
            static_cast<int32_t>(width),
            static_cast<int32_t>(height),
            workgroup.x,
            workgroup.y,
        };
        std::array<VkSpecializationMapEntry, 6> entries{{
            {7, offsetof(SpecializationData, redOffsetX), sizeof(int32_t)},
            {14, offsetof(SpecializationData, redOffsetY), sizeof(int32_t)},
            {8, offsetof(SpecializationData, width), sizeof(int32_t)},
            {9, offsetof(SpecializationData, height), sizeof(int32_t)},
            {12, offsetof(SpecializationData, localSizeX), sizeof(uint32_t)},
            {13, offsetof(SpecializationData, localSizeY), sizeof(uint32_t)},
        }};
        VkSpecializationInfo specialization{};
        specialization.mapEntryCount =
            static_cast<uint32_t>(entries.size());
        specialization.pMapEntries = entries.data();
        specialization.dataSize = sizeof(data);
        specialization.pData = &data;

        VkShaderModule shader = makeShader(device_, code, wordCount);
        VkPipelineShaderStageCreateInfo stage{
            VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO};
        stage.stage = VK_SHADER_STAGE_COMPUTE_BIT;
        stage.module = shader;
        stage.pName = "main";
        stage.pSpecializationInfo = &specialization;
        VkComputePipelineCreateInfo pipelineInfo{
            VK_STRUCTURE_TYPE_COMPUTE_PIPELINE_CREATE_INFO};
        pipelineInfo.stage = stage;
        pipelineInfo.layout = rcdPipelineLayout_;
        VkPipeline pipeline = VK_NULL_HANDLE;
        VkResult result = vkCreateComputePipelines(
            device_, VK_NULL_HANDLE, 1, &pipelineInfo, nullptr, &pipeline);
        vkDestroyShaderModule(device_, shader, nullptr);
        require(result, "vkCreateComputePipelines for staged RCD");
        return pipeline;
    }

    void destroyRcdPipelines()
    {
        for (auto& inputPipelines : rcdPipelines_) {
            for (VkPipeline& pipeline : inputPipelines) {
                if (pipeline != VK_NULL_HANDLE)
                    vkDestroyPipeline(device_, pipeline, nullptr);
                pipeline = VK_NULL_HANDLE;
            }
        }
        rcdPipelineWidth_ = 0;
        rcdPipelineHeight_ = 0;
        rcdPipelinePattern_ = -1;
    }

    void ensureRcdPipelines(
        uint32_t width,
        uint32_t height,
        int32_t cfaPattern)
    {
        if (rcdPipelineWidth_ == width && rcdPipelineHeight_ == height &&
            rcdPipelinePattern_ == cfaPattern)
            return;
        destroyRcdPipelines();

        struct SpirvView {
            const uint32_t* data;
            size_t size;
        };
        const std::array<std::array<SpirvView, RcdStageCount>, RcdInputCount>
            shaders{{
                {{
                    {kRcdGuideBufferSpirv.data(), kRcdGuideBufferSpirv.size()},
                    {kRcdGreenBufferSpirv.data(), kRcdGreenBufferSpirv.size()},
                    {kRcdMedianBufferSpirv.data(), kRcdMedianBufferSpirv.size()},
                    {kRcdOutputBufferSpirv.data(), kRcdOutputBufferSpirv.size()},
                }},
                {{
                    {kRcdGuideImageSpirv.data(), kRcdGuideImageSpirv.size()},
                    {kRcdGreenImageSpirv.data(), kRcdGreenImageSpirv.size()},
                    {kRcdMedianImageSpirv.data(), kRcdMedianImageSpirv.size()},
                    {kRcdOutputImageSpirv.data(), kRcdOutputImageSpirv.size()},
                }},
            }};
        const RcdWorkgroup workgroup = chooseRcdWorkgroup();
        try {
            for (size_t input = 0; input < RcdInputCount; ++input) {
                for (size_t stage = 0; stage < RcdStageCount; ++stage) {
                    rcdPipelines_[input][stage] = createRcdPipeline(
                        shaders[input][stage].data,
                        shaders[input][stage].size,
                        width, height, cfaPattern, workgroup);
                }
            }
        } catch (...) {
            destroyRcdPipelines();
            throw;
        }
        rcdWorkgroup_ = workgroup;
        rcdPipelineWidth_ = width;
        rcdPipelineHeight_ = height;
        rcdPipelinePattern_ = cfaPattern;
    }

    void createFrames()
    {
        std::array<VkCommandBuffer, kFramesInFlight + 1> commandBuffers{};
        VkCommandBufferAllocateInfo commandInfo{
            VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO};
        commandInfo.commandPool = commandPool_;
        commandInfo.level = VK_COMMAND_BUFFER_LEVEL_PRIMARY;
        commandInfo.commandBufferCount = kFramesInFlight + 1;
        require(vkAllocateCommandBuffers(
                    device_, &commandInfo, commandBuffers.data()),
                "vkAllocateCommandBuffers");

        std::array<VkDescriptorSetLayout, kFramesInFlight + 1> computeLayouts{};
        computeLayouts.fill(computeDescriptorLayout_);
        std::array<VkDescriptorSet, kFramesInFlight + 1> computeSets{};
        VkDescriptorSetAllocateInfo computeInfo{
            VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO};
        computeInfo.descriptorPool = descriptorPool_;
        computeInfo.descriptorSetCount = kFramesInFlight + 1;
        computeInfo.pSetLayouts = computeLayouts.data();
        require(vkAllocateDescriptorSets(
                    device_, &computeInfo, computeSets.data()),
                "vkAllocateDescriptorSets");

        std::array<VkDescriptorSetLayout, kFramesInFlight> presentLayouts{};
        presentLayouts.fill(presentDescriptorLayout_);
        std::array<VkDescriptorSet, kFramesInFlight> presentSets{};
        VkDescriptorSetAllocateInfo presentInfo{
            VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO};
        presentInfo.descriptorPool = descriptorPool_;
        presentInfo.descriptorSetCount = kFramesInFlight;
        presentInfo.pSetLayouts = presentLayouts.data();
        require(vkAllocateDescriptorSets(
                    device_, &presentInfo, presentSets.data()),
                "vkAllocateDescriptorSets");

        for (uint32_t index = 0; index < kFramesInFlight; ++index) {
            Frame& frame = frames_[index];
            frame.commandBuffer = commandBuffers[index];
            frame.computeDescriptor = computeSets[index];
            frame.presentDescriptor = presentSets[index];
            VkFenceCreateInfo fenceInfo{VK_STRUCTURE_TYPE_FENCE_CREATE_INFO};
            fenceInfo.flags = VK_FENCE_CREATE_SIGNALED_BIT;
            require(vkCreateFence(device_, &fenceInfo, nullptr, &frame.fence),
                    "vkCreateFence");
            VkSemaphoreCreateInfo semaphoreInfo{
                VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO};
            require(vkCreateSemaphore(
                        device_, &semaphoreInfo, nullptr, &frame.imageAvailable),
                    "vkCreateSemaphore");
            require(vkCreateSemaphore(
                        device_, &semaphoreInfo, nullptr, &frame.renderFinished),
                    "vkCreateSemaphore");
            makeBuffer(kRawParametersSize, VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
                       VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT |
                           VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
                       frame.rawUniform, frame.rawUniformMemory,
                       &frame.rawMapped);
            makeBuffer(kRawColorParametersSize, VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
                       VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT |
                           VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
                       frame.spektraUniform, frame.spektraUniformMemory,
                       &frame.spektraMapped);
            makeBuffer(kGeometryParametersSize, VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
                       VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT |
                           VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
                       frame.geometryUniform, frame.geometryUniformMemory,
                       &frame.geometryMapped);
        }

        captureFrame_.commandBuffer = commandBuffers[kFramesInFlight];
        captureFrame_.computeDescriptor = computeSets[kFramesInFlight];
        VkFenceCreateInfo captureFenceInfo{VK_STRUCTURE_TYPE_FENCE_CREATE_INFO};
        captureFenceInfo.flags = VK_FENCE_CREATE_SIGNALED_BIT;
        require(vkCreateFence(
                    device_, &captureFenceInfo, nullptr, &captureFrame_.fence),
                "vkCreateFence for capture");
        makeBuffer(kRawParametersSize, VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
                   VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT |
                       VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
                   captureFrame_.rawUniform, captureFrame_.rawUniformMemory,
                   &captureFrame_.rawMapped);
        makeBuffer(kRawColorParametersSize, VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
                   VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT |
                       VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
                   captureFrame_.spektraUniform,
                   captureFrame_.spektraUniformMemory,
                   &captureFrame_.spektraMapped);
        makeBuffer(kGeometryParametersSize, VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
                   VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT |
                       VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
                   captureFrame_.geometryUniform,
                   captureFrame_.geometryUniformMemory,
                   &captureFrame_.geometryMapped);
    }

    void validateRawParameters(const RawParameters& parameters) const
    {
        if (parameters.dimensions[0] <= 0 || parameters.dimensions[1] <= 0)
            throw std::runtime_error("RAW dimensions must be positive");
        if (parameters.layoutInfo[0] <= 0)
            throw std::runtime_error("RAW row stride must be positive");
        if (parameters.layoutInfo[1] < 0 || parameters.layoutInfo[1] > 2)
            throw std::runtime_error("RAW format must be RAW_SENSOR, RAW10, or RAW12");
        if (parameters.layoutInfo[2] < 0 || parameters.layoutInfo[2] > 3)
            throw std::runtime_error("CFA must be RGGB, GRBG, GBRG, or BGGR");
        int64_t minimumStride = 0;
        if (parameters.layoutInfo[1] == 1)
            minimumStride = ((static_cast<int64_t>(parameters.dimensions[0]) + 3) / 4) * 5;
        else if (parameters.layoutInfo[1] == 2)
            minimumStride = ((static_cast<int64_t>(parameters.dimensions[0]) + 1) / 2) * 3;
        else
            minimumStride = static_cast<int64_t>(parameters.dimensions[0]) * 2;
        if (parameters.layoutInfo[0] < minimumStride)
            throw std::runtime_error("RAW row stride is too small for its width and format");
    }

    void importRawBuffer(
        JNIEnv* environment,
        jobject object,
        jobject packedRawBytesObject,
        const RawParameters& parameters,
        Frame& frame)
    {
        if (parameters.layoutInfo[1] != 0) {
            void* packedAddress = packedRawBytesObject == nullptr
                ? nullptr
                : environment->GetDirectBufferAddress(packedRawBytesObject);
            jlong packedCapacity = packedRawBytesObject == nullptr
                ? -1
                : environment->GetDirectBufferCapacity(packedRawBytesObject);
            uint64_t requiredBytes =
                static_cast<uint64_t>(parameters.layoutInfo[0]) *
                static_cast<uint64_t>(parameters.dimensions[1]);
            if (requiredBytes == 0 || requiredBytes >
                    static_cast<uint64_t>(std::numeric_limits<jlong>::max()) ||
                packedAddress == nullptr ||
                packedCapacity < static_cast<jlong>(requiredBytes)) {
                throw std::runtime_error(
                    "Packed RAW plane must be a direct ByteBuffer containing rowStride times height bytes");
            }
            uint64_t alignedBytes = (requiredBytes + 3u) & ~uint64_t{3u};
            VkPhysicalDeviceProperties deviceProperties{};
            vkGetPhysicalDeviceProperties(physicalDevice_, &deviceProperties);
            if (alignedBytes > deviceProperties.limits.maxStorageBufferRange)
                throw std::runtime_error(
                    "Packed RAW plane exceeds the Vulkan storage buffer range");

            ensurePackedRawBuffer(
                frame, static_cast<VkDeviceSize>(alignedBytes));
            std::memcpy(
                frame.packedRawMapped, packedAddress,
                static_cast<size_t>(requiredBytes));
            if (alignedBytes != requiredBytes) {
                std::memset(
                    static_cast<std::byte*>(frame.packedRawMapped) + requiredBytes,
                    0, static_cast<size_t>(alignedBytes - requiredBytes));
            }
            VkMappedMemoryRange flush{
                VK_STRUCTURE_TYPE_MAPPED_MEMORY_RANGE};
            flush.memory = frame.packedRawMemory;
            flush.offset = 0;
            flush.size = VK_WHOLE_SIZE;
            require(vkFlushMappedMemoryRanges(device_, 1, &flush),
                    "vkFlushMappedMemoryRanges for packed RAW upload");
            frame.rawImportKind = RawImportKind::Buffer;
            return;
        }

        if (packedRawBytesObject != nullptr)
            throw std::runtime_error(
                "RAW_SENSOR must use its R16_UINT HardwareBuffer image");
        AHardwareBuffer* hardwareBuffer =
            AHardwareBuffer_fromHardwareBuffer(environment, object);
        if (hardwareBuffer == nullptr)
            throw std::runtime_error("Unable to access RAW HardwareBuffer");
        AHardwareBuffer_Desc description{};
        AHardwareBuffer_describe(hardwareBuffer, &description);
        VkAndroidHardwareBufferFormatPropertiesANDROID formatProperties{
            VK_STRUCTURE_TYPE_ANDROID_HARDWARE_BUFFER_FORMAT_PROPERTIES_ANDROID};
        VkAndroidHardwareBufferPropertiesANDROID properties{
            VK_STRUCTURE_TYPE_ANDROID_HARDWARE_BUFFER_PROPERTIES_ANDROID};
        properties.pNext = &formatProperties;
        require(getAhbProperties_(device_, hardwareBuffer, &properties),
                "vkGetAndroidHardwareBufferPropertiesANDROID");

        if (description.format != AHARDWAREBUFFER_FORMAT_BLOB) {
            if (description.height <= 1 || description.layers != 1 ||
                description.width !=
                    static_cast<uint32_t>(parameters.dimensions[0]) ||
                description.height !=
                    static_cast<uint32_t>(parameters.dimensions[1])) {
                throw std::runtime_error(
                    "RAW HardwareBuffer image dimensions do not match Camera2 metadata: format=" +
                    std::to_string(description.format) + " width=" +
                    std::to_string(description.width) + " height=" +
                    std::to_string(description.height) + " layers=" +
                    std::to_string(description.layers));
            }
            if (formatProperties.format == VK_FORMAT_UNDEFINED ||
                formatProperties.format != VK_FORMAT_R16_UINT) {
                throw std::runtime_error(
                    "RAW_SENSOR HardwareBuffer does not expose VK_FORMAT_R16_UINT: ahbFormat=" +
                    std::to_string(description.format) + " vkFormat=" +
                    std::to_string(formatProperties.format) + " externalFormat=" +
                    std::to_string(formatProperties.externalFormat));
            }
            VkFormatFeatureFlags requiredFeatures =
                VK_FORMAT_FEATURE_SAMPLED_IMAGE_BIT;
            if ((formatProperties.formatFeatures & requiredFeatures) !=
                requiredFeatures) {
                throw std::runtime_error(
                    "RAW_SENSOR R16_UINT HardwareBuffer lacks sampled Vulkan image support: formatFeatures=" +
                    std::to_string(formatProperties.formatFeatures));
            }

            VkExternalMemoryImageCreateInfo externalCreate{
                VK_STRUCTURE_TYPE_EXTERNAL_MEMORY_IMAGE_CREATE_INFO};
            externalCreate.handleTypes =
                VK_EXTERNAL_MEMORY_HANDLE_TYPE_ANDROID_HARDWARE_BUFFER_BIT_ANDROID;
            VkImageCreateInfo imageInfo{VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO};
            imageInfo.pNext = &externalCreate;
            imageInfo.imageType = VK_IMAGE_TYPE_2D;
            imageInfo.format = VK_FORMAT_R16_UINT;
            imageInfo.extent = {description.width, description.height, 1};
            imageInfo.mipLevels = 1;
            imageInfo.arrayLayers = 1;
            imageInfo.samples = VK_SAMPLE_COUNT_1_BIT;
            imageInfo.tiling = VK_IMAGE_TILING_OPTIMAL;
            imageInfo.usage = VK_IMAGE_USAGE_SAMPLED_BIT;
            imageInfo.sharingMode = VK_SHARING_MODE_EXCLUSIVE;
            imageInfo.initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;
            require(vkCreateImage(
                        device_, &imageInfo, nullptr, &frame.importedImage),
                    "vkCreateImage for RAW_SENSOR HardwareBuffer");

            VkMemoryRequirements requirements{};
            vkGetImageMemoryRequirements(
                device_, frame.importedImage, &requirements);
            uint32_t typeBits =
                requirements.memoryTypeBits & properties.memoryTypeBits;
            if (typeBits == 0) {
                throw std::runtime_error(
                    "RAW_SENSOR HardwareBuffer image has no compatible Vulkan memory type");
            }

            VkMemoryDedicatedAllocateInfo dedicated{
                VK_STRUCTURE_TYPE_MEMORY_DEDICATED_ALLOCATE_INFO};
            dedicated.image = frame.importedImage;
            VkImportAndroidHardwareBufferInfoANDROID importInfo{
                VK_STRUCTURE_TYPE_IMPORT_ANDROID_HARDWARE_BUFFER_INFO_ANDROID};
            importInfo.pNext = &dedicated;
            importInfo.buffer = hardwareBuffer;
            VkMemoryAllocateInfo allocation{
                VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO};
            allocation.pNext = &importInfo;
            allocation.allocationSize = properties.allocationSize;
            allocation.memoryTypeIndex = memoryType(typeBits, 0);
            require(vkAllocateMemory(
                        device_, &allocation, nullptr, &frame.importedMemory),
                    "vkAllocateMemory for RAW_SENSOR HardwareBuffer image");
            require(vkBindImageMemory(
                        device_, frame.importedImage, frame.importedMemory, 0),
                    "vkBindImageMemory for RAW_SENSOR HardwareBuffer");
            frame.importedImageView = createImageView(
                frame.importedImage, VK_FORMAT_R16_UINT);
            AHardwareBuffer_acquire(hardwareBuffer);
            frame.retainedHardwareBuffer = hardwareBuffer;
            frame.rawImportKind = RawImportKind::Image;
            return;
        }

        throw std::runtime_error(
            "RAW_SENSOR HardwareBuffer is not a two-dimensional R16_UINT image");
    }

    void releaseImportedBuffer(Frame& frame)
    {
        if (frame.importedImageView != VK_NULL_HANDLE) {
            vkDestroyImageView(device_, frame.importedImageView, nullptr);
            frame.importedImageView = VK_NULL_HANDLE;
        }
        if (frame.importedImage != VK_NULL_HANDLE) {
            vkDestroyImage(device_, frame.importedImage, nullptr);
            frame.importedImage = VK_NULL_HANDLE;
        }
        if (frame.importedMemory != VK_NULL_HANDLE) {
            vkFreeMemory(device_, frame.importedMemory, nullptr);
            frame.importedMemory = VK_NULL_HANDLE;
        }
        if (frame.retainedHardwareBuffer != nullptr) {
            AHardwareBuffer_release(frame.retainedHardwareBuffer);
            frame.retainedHardwareBuffer = nullptr;
        }
        frame.rawImportKind = RawImportKind::None;
    }

    void createProcessingImages(uint32_t width, uint32_t height)
    {
        destroyProcessingImages();
        processingWidth_ = width;
        processingHeight_ = height;
        for (Frame& frame : frames_) {
            VkImageCreateInfo imageInfo{VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO};
            imageInfo.imageType = VK_IMAGE_TYPE_2D;
            imageInfo.format = VK_FORMAT_R16G16B16A16_SFLOAT;
            imageInfo.extent = {width, height, 1};
            imageInfo.mipLevels = 1;
            imageInfo.arrayLayers = 1;
            imageInfo.samples = VK_SAMPLE_COUNT_1_BIT;
            imageInfo.tiling = VK_IMAGE_TILING_OPTIMAL;
            imageInfo.usage =
                VK_IMAGE_USAGE_STORAGE_BIT | VK_IMAGE_USAGE_SAMPLED_BIT;
            imageInfo.sharingMode = VK_SHARING_MODE_EXCLUSIVE;
            imageInfo.initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;
            require(vkCreateImage(
                        device_, &imageInfo, nullptr, &frame.processingImage),
                    "vkCreateImage");
            VkMemoryRequirements requirements{};
            vkGetImageMemoryRequirements(
                device_, frame.processingImage, &requirements);
            VkMemoryAllocateInfo allocation{
                VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO};
            allocation.allocationSize = requirements.size;
            allocation.memoryTypeIndex = memoryType(
                requirements.memoryTypeBits,
                VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
            require(vkAllocateMemory(
                        device_, &allocation, nullptr, &frame.processingMemory),
                    "vkAllocateMemory");
            require(vkBindImageMemory(
                        device_, frame.processingImage,
                        frame.processingMemory, 0),
                    "vkBindImageMemory");
            frame.processingView = createImageView(
                frame.processingImage, VK_FORMAT_R16G16B16A16_SFLOAT);
            require(vkCreateImage(
                        device_, &imageInfo, nullptr, &frame.spektraImage),
                    "vkCreateImage for SpektraFilm output");
            vkGetImageMemoryRequirements(
                device_, frame.spektraImage, &requirements);
            allocation.allocationSize = requirements.size;
            allocation.memoryTypeIndex = memoryType(
                requirements.memoryTypeBits,
                VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
            require(vkAllocateMemory(
                        device_, &allocation, nullptr, &frame.spektraMemory),
                    "vkAllocateMemory for SpektraFilm output");
            require(vkBindImageMemory(
                        device_, frame.spektraImage, frame.spektraMemory, 0),
                    "vkBindImageMemory for SpektraFilm output");
            frame.spektraView = createImageView(
                frame.spektraImage, VK_FORMAT_R16G16B16A16_SFLOAT);
            frame.processingInitialized = false;
            frame.spektraLayoutInitialized = false;
            frame.spektraInitialized = false;
        }
    }

    void ensureCaptureResources(uint32_t width, uint32_t height)
    {
        if (captureImage_ != VK_NULL_HANDLE && captureWidth_ == width &&
            captureHeight_ == height)
            return;
        destroyCaptureResources();

        VkPhysicalDeviceProperties deviceProperties{};
        vkGetPhysicalDeviceProperties(physicalDevice_, &deviceProperties);
        if (width > deviceProperties.limits.maxImageDimension2D ||
            height > deviceProperties.limits.maxImageDimension2D) {
            throw std::runtime_error(
                "Full-resolution capture exceeds the Vulkan image dimension limit");
        }
        VkFormatProperties formatProperties{};
        vkGetPhysicalDeviceFormatProperties(
            physicalDevice_, VK_FORMAT_R16G16B16A16_SFLOAT, &formatProperties);
        VkFormatFeatureFlags required = VK_FORMAT_FEATURE_STORAGE_IMAGE_BIT |
                                        VK_FORMAT_FEATURE_TRANSFER_SRC_BIT |
                                        VK_FORMAT_FEATURE_BLIT_SRC_BIT;
        if ((formatProperties.optimalTilingFeatures & required) != required) {
            throw std::runtime_error(
                "Device cannot store and read back an RGBA16F capture image");
        }
        VkFormatProperties readbackFormatProperties{};
        vkGetPhysicalDeviceFormatProperties(
            physicalDevice_, VK_FORMAT_R8G8B8A8_UNORM,
            &readbackFormatProperties);
        const VkFormatFeatureFlags readbackRequired =
            VK_FORMAT_FEATURE_BLIT_DST_BIT |
            VK_FORMAT_FEATURE_TRANSFER_SRC_BIT;
        if ((readbackFormatProperties.optimalTilingFeatures &
             readbackRequired) != readbackRequired) {
            throw std::runtime_error(
                "Device cannot convert RGBA16F capture output to RGBA8");
        }

        VkImageCreateInfo imageInfo{VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO};
        imageInfo.imageType = VK_IMAGE_TYPE_2D;
        imageInfo.format = VK_FORMAT_R16G16B16A16_SFLOAT;
        imageInfo.extent = {width, height, 1};
        imageInfo.mipLevels = 1;
        imageInfo.arrayLayers = 1;
        imageInfo.samples = VK_SAMPLE_COUNT_1_BIT;
        imageInfo.tiling = VK_IMAGE_TILING_OPTIMAL;
        imageInfo.usage =
            VK_IMAGE_USAGE_STORAGE_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT;
        imageInfo.sharingMode = VK_SHARING_MODE_EXCLUSIVE;
        imageInfo.initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;
        require(vkCreateImage(device_, &imageInfo, nullptr, &captureImage_),
                "vkCreateImage for RCD capture");
        VkMemoryRequirements requirements{};
        vkGetImageMemoryRequirements(device_, captureImage_, &requirements);
        VkMemoryAllocateInfo allocation{VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO};
        allocation.allocationSize = requirements.size;
        allocation.memoryTypeIndex = memoryType(
            requirements.memoryTypeBits, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
        require(vkAllocateMemory(
                    device_, &allocation, nullptr, &captureMemory_),
                "vkAllocateMemory for RCD capture");
        require(vkBindImageMemory(device_, captureImage_, captureMemory_, 0),
                "vkBindImageMemory for RCD capture");
        captureView_ = createImageView(
            captureImage_, VK_FORMAT_R16G16B16A16_SFLOAT);
        require(vkCreateImage(
                    device_, &imageInfo, nullptr, &captureSpektraImage_),
                "vkCreateImage for SpektraFilm capture output");
        vkGetImageMemoryRequirements(
            device_, captureSpektraImage_, &requirements);
        allocation.allocationSize = requirements.size;
        allocation.memoryTypeIndex = memoryType(
            requirements.memoryTypeBits, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
        require(vkAllocateMemory(
                    device_, &allocation, nullptr, &captureSpektraMemory_),
                "vkAllocateMemory for SpektraFilm capture output");
        require(vkBindImageMemory(
                    device_, captureSpektraImage_, captureSpektraMemory_, 0),
                "vkBindImageMemory for SpektraFilm capture output");
        captureSpektraView_ = createImageView(
            captureSpektraImage_, VK_FORMAT_R16G16B16A16_SFLOAT);

        VkImageCreateInfo readbackImageInfo = imageInfo;
        readbackImageInfo.format = VK_FORMAT_R8G8B8A8_UNORM;
        readbackImageInfo.usage =
            VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT;
        require(vkCreateImage(
                    device_, &readbackImageInfo, nullptr,
                    &captureReadbackImage_),
                "vkCreateImage for capture RGBA8 conversion");
        vkGetImageMemoryRequirements(
            device_, captureReadbackImage_, &requirements);
        allocation.allocationSize = requirements.size;
        allocation.memoryTypeIndex = memoryType(
            requirements.memoryTypeBits, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
        require(vkAllocateMemory(
                    device_, &allocation, nullptr, &captureReadbackMemory_),
                "vkAllocateMemory for capture RGBA8 conversion");
        require(vkBindImageMemory(
                    device_, captureReadbackImage_, captureReadbackMemory_, 0),
                "vkBindImageMemory for capture RGBA8 conversion");

        VkDeviceSize stagingSize = static_cast<VkDeviceSize>(width) *
                                   static_cast<VkDeviceSize>(height) * 4;
        makeBuffer(stagingSize, VK_BUFFER_USAGE_TRANSFER_DST_BIT,
                   VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT |
                       VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
                   captureStaging_, captureStagingMemory_,
                   &captureStagingMapped_);
        captureWidth_ = width;
        captureHeight_ = height;
        captureInitialized_ = false;
        captureSpektraLayoutInitialized_ = false;
        captureReadbackInitialized_ = false;
    }

    void destroyCaptureResources()
    {
        if (captureStagingMapped_ != nullptr)
            vkUnmapMemory(device_, captureStagingMemory_);
        if (captureStaging_ != VK_NULL_HANDLE)
            vkDestroyBuffer(device_, captureStaging_, nullptr);
        if (captureStagingMemory_ != VK_NULL_HANDLE)
            vkFreeMemory(device_, captureStagingMemory_, nullptr);
        if (captureView_ != VK_NULL_HANDLE)
            vkDestroyImageView(device_, captureView_, nullptr);
        if (captureSpektraView_ != VK_NULL_HANDLE)
            vkDestroyImageView(device_, captureSpektraView_, nullptr);
        if (captureImage_ != VK_NULL_HANDLE)
            vkDestroyImage(device_, captureImage_, nullptr);
        if (captureSpektraImage_ != VK_NULL_HANDLE)
            vkDestroyImage(device_, captureSpektraImage_, nullptr);
        if (captureReadbackImage_ != VK_NULL_HANDLE)
            vkDestroyImage(device_, captureReadbackImage_, nullptr);
        if (captureMemory_ != VK_NULL_HANDLE)
            vkFreeMemory(device_, captureMemory_, nullptr);
        if (captureSpektraMemory_ != VK_NULL_HANDLE)
            vkFreeMemory(device_, captureSpektraMemory_, nullptr);
        if (captureReadbackMemory_ != VK_NULL_HANDLE)
            vkFreeMemory(device_, captureReadbackMemory_, nullptr);
        captureStagingMapped_ = nullptr;
        captureStaging_ = VK_NULL_HANDLE;
        captureStagingMemory_ = VK_NULL_HANDLE;
        captureView_ = VK_NULL_HANDLE;
        captureImage_ = VK_NULL_HANDLE;
        captureMemory_ = VK_NULL_HANDLE;
        captureSpektraView_ = VK_NULL_HANDLE;
        captureSpektraImage_ = VK_NULL_HANDLE;
        captureSpektraMemory_ = VK_NULL_HANDLE;
        captureReadbackImage_ = VK_NULL_HANDLE;
        captureReadbackMemory_ = VK_NULL_HANDLE;
        captureWidth_ = 0;
        captureHeight_ = 0;
        captureInitialized_ = false;
        captureSpektraLayoutInitialized_ = false;
        captureReadbackInitialized_ = false;
    }

    VkImageView createImageView(VkImage image, VkFormat format) const
    {
        VkImageViewCreateInfo viewInfo{
            VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO};
        viewInfo.image = image;
        viewInfo.viewType = VK_IMAGE_VIEW_TYPE_2D;
        viewInfo.format = format;
        viewInfo.subresourceRange.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
        viewInfo.subresourceRange.levelCount = 1;
        viewInfo.subresourceRange.layerCount = 1;
        VkImageView view = VK_NULL_HANDLE;
        require(vkCreateImageView(device_, &viewInfo, nullptr, &view),
                "vkCreateImageView");
        return view;
    }

    void createRcdImage(
        uint32_t width,
        uint32_t height,
        VkFormat format,
        VkImage& image,
        VkDeviceMemory& memory,
        VkImageView& view)
    {
        VkImageCreateInfo imageInfo{VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO};
        imageInfo.imageType = VK_IMAGE_TYPE_2D;
        imageInfo.format = format;
        imageInfo.extent = {width, height, 1};
        imageInfo.mipLevels = 1;
        imageInfo.arrayLayers = 1;
        imageInfo.samples = VK_SAMPLE_COUNT_1_BIT;
        imageInfo.tiling = VK_IMAGE_TILING_OPTIMAL;
        imageInfo.usage =
            VK_IMAGE_USAGE_STORAGE_BIT | VK_IMAGE_USAGE_SAMPLED_BIT;
        imageInfo.sharingMode = VK_SHARING_MODE_EXCLUSIVE;
        imageInfo.initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;
        require(vkCreateImage(device_, &imageInfo, nullptr, &image),
                "vkCreateImage for staged RCD");
        VkMemoryRequirements requirements{};
        vkGetImageMemoryRequirements(device_, image, &requirements);
        VkMemoryAllocateInfo allocation{VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO};
        allocation.allocationSize = requirements.size;
        allocation.memoryTypeIndex = memoryType(
            requirements.memoryTypeBits, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
        require(vkAllocateMemory(device_, &allocation, nullptr, &memory),
                "vkAllocateMemory for staged RCD");
        require(vkBindImageMemory(device_, image, memory, 0),
                "vkBindImageMemory for staged RCD");
        view = createImageView(image, format);
    }

    void ensureRcdResources(uint32_t width, uint32_t height)
    {
        if (rcdWidth_ == width && rcdHeight_ == height &&
            rcdDemosaicImage_ != VK_NULL_HANDLE)
            return;
        destroyRcdResources();

        const uint32_t blockWidth = (width + 1) / 2;
        const uint32_t blockHeight = (height + 1) / 2;
        for (VkFormat format : {
                 VK_FORMAT_R16G16_SFLOAT,
                 VK_FORMAT_R16G16B16A16_SFLOAT}) {
            VkFormatProperties properties{};
            vkGetPhysicalDeviceFormatProperties(
                physicalDevice_, format, &properties);
            const VkFormatFeatureFlags required =
                VK_FORMAT_FEATURE_STORAGE_IMAGE_BIT |
                VK_FORMAT_FEATURE_SAMPLED_IMAGE_BIT;
            if ((properties.optimalTilingFeatures & required) != required) {
                throw std::runtime_error(
                    "Staged RCD requires sampled and storage fp16 images");
            }
        }

        try {
            createRcdImage(
                blockWidth, blockHeight, VK_FORMAT_R16G16B16A16_SFLOAT,
                rcdGuideImage_, rcdGuideMemory_, rcdGuideView_);
            createRcdImage(
                blockWidth, blockHeight, VK_FORMAT_R16G16_SFLOAT,
                rcdGreenImage_, rcdGreenMemory_, rcdGreenView_);
            createRcdImage(
                blockWidth, blockHeight, VK_FORMAT_R16G16_SFLOAT,
                rcdRatioImage_, rcdRatioMemory_, rcdRatioView_);
            createRcdImage(
                width, height, VK_FORMAT_R16G16B16A16_SFLOAT,
                rcdDemosaicImage_, rcdDemosaicMemory_, rcdDemosaicView_);
        } catch (...) {
            destroyRcdResources();
            throw;
        }
        rcdWidth_ = width;
        rcdHeight_ = height;
        rcdLayoutsInitialized_ = false;
    }

    void destroyRcdResources()
    {
        for (VkImageView view : {
                 rcdGuideView_, rcdGreenView_, rcdRatioView_,
                 rcdDemosaicView_}) {
            if (view != VK_NULL_HANDLE)
                vkDestroyImageView(device_, view, nullptr);
        }
        for (VkImage image : {
                 rcdGuideImage_, rcdGreenImage_, rcdRatioImage_,
                 rcdDemosaicImage_}) {
            if (image != VK_NULL_HANDLE)
                vkDestroyImage(device_, image, nullptr);
        }
        for (VkDeviceMemory memory : {
                 rcdGuideMemory_, rcdGreenMemory_, rcdRatioMemory_,
                 rcdDemosaicMemory_}) {
            if (memory != VK_NULL_HANDLE)
                vkFreeMemory(device_, memory, nullptr);
        }
        rcdGuideImage_ = VK_NULL_HANDLE;
        rcdGuideMemory_ = VK_NULL_HANDLE;
        rcdGuideView_ = VK_NULL_HANDLE;
        rcdGreenImage_ = VK_NULL_HANDLE;
        rcdGreenMemory_ = VK_NULL_HANDLE;
        rcdGreenView_ = VK_NULL_HANDLE;
        rcdRatioImage_ = VK_NULL_HANDLE;
        rcdRatioMemory_ = VK_NULL_HANDLE;
        rcdRatioView_ = VK_NULL_HANDLE;
        rcdDemosaicImage_ = VK_NULL_HANDLE;
        rcdDemosaicMemory_ = VK_NULL_HANDLE;
        rcdDemosaicView_ = VK_NULL_HANDLE;
        rcdWidth_ = 0;
        rcdHeight_ = 0;
        rcdLayoutsInitialized_ = false;
    }

    void updateRcdDescriptorSet(
        Frame& frame,
        RcdStage stage,
        VkImageView output,
        VkImageView guide,
        VkImageView green,
        VkImageView ratio)
    {
        VkDescriptorBufferInfo rawBuffer{
            frame.packedRawBuffer, 0, VK_WHOLE_SIZE};
        VkDescriptorImageInfo rawImage{
            rawIntegerSampler_, frame.importedImageView,
            VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL};
        VkDescriptorBufferInfo rawUniform{
            frame.rawUniform, 0, kRawParametersSize};
        VkDescriptorImageInfo outputImage{
            VK_NULL_HANDLE, output, VK_IMAGE_LAYOUT_GENERAL};
        VkDescriptorImageInfo guideImage{
            rawIntegerSampler_, guide, VK_IMAGE_LAYOUT_GENERAL};
        VkDescriptorImageInfo greenImage{
            rawIntegerSampler_, green, VK_IMAGE_LAYOUT_GENERAL};
        VkDescriptorImageInfo ratioImage{
            rawIntegerSampler_, ratio, VK_IMAGE_LAYOUT_GENERAL};

        std::array<VkWriteDescriptorSet, 6> writes{};
        uint32_t writeCount = 0;
        auto& rawWrite = writes[writeCount++];
        rawWrite.sType = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET;
        rawWrite.dstSet = rcdDescriptorSets_[stage];
        rawWrite.dstBinding =
            frame.rawImportKind == RawImportKind::Image ? 5 : 0;
        rawWrite.descriptorCount = 1;
        rawWrite.descriptorType =
            frame.rawImportKind == RawImportKind::Image
                ? VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER
                : VK_DESCRIPTOR_TYPE_STORAGE_BUFFER;
        if (frame.rawImportKind == RawImportKind::Image)
            rawWrite.pImageInfo = &rawImage;
        else
            rawWrite.pBufferInfo = &rawBuffer;

        auto& uniformWrite = writes[writeCount++];
        uniformWrite.sType = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET;
        uniformWrite.dstSet = rcdDescriptorSets_[stage];
        uniformWrite.dstBinding = 1;
        uniformWrite.descriptorCount = 1;
        uniformWrite.descriptorType = VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER;
        uniformWrite.pBufferInfo = &rawUniform;

        auto& outputWrite = writes[writeCount++];
        outputWrite.sType = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET;
        outputWrite.dstSet = rcdDescriptorSets_[stage];
        outputWrite.dstBinding = 2;
        outputWrite.descriptorCount = 1;
        outputWrite.descriptorType = VK_DESCRIPTOR_TYPE_STORAGE_IMAGE;
        outputWrite.pImageInfo = &outputImage;

        auto addSampled = [&](uint32_t binding, VkDescriptorImageInfo* info) {
            auto& write = writes[writeCount++];
            write.sType = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET;
            write.dstSet = rcdDescriptorSets_[stage];
            write.dstBinding = binding;
            write.descriptorCount = 1;
            write.descriptorType = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
            write.pImageInfo = info;
        };
        if (guide != VK_NULL_HANDLE)
            addSampled(6, &guideImage);
        if (green != VK_NULL_HANDLE)
            addSampled(7, &greenImage);
        if (ratio != VK_NULL_HANDLE)
            addSampled(8, &ratioImage);
        vkUpdateDescriptorSets(device_, writeCount, writes.data(), 0, nullptr);
    }

    void updateRcdDescriptors(Frame& frame)
    {
        updateRcdDescriptorSet(
            frame, RcdGuide, rcdGuideView_,
            VK_NULL_HANDLE, VK_NULL_HANDLE, VK_NULL_HANDLE);
        updateRcdDescriptorSet(
            frame, RcdGreen, rcdGreenView_,
            rcdGuideView_, VK_NULL_HANDLE, VK_NULL_HANDLE);
        updateRcdDescriptorSet(
            frame, RcdMedian, rcdRatioView_,
            VK_NULL_HANDLE, rcdGreenView_, VK_NULL_HANDLE);
        updateRcdDescriptorSet(
            frame, RcdOutput, rcdDemosaicView_,
            VK_NULL_HANDLE, rcdGreenView_, rcdRatioView_);
    }

    void updateFrameDescriptors(Frame& frame)
    {
        VkDescriptorBufferInfo rawBuffer{
            frame.packedRawBuffer, 0, VK_WHOLE_SIZE};
        VkDescriptorImageInfo rawImage{
            rawIntegerSampler_, frame.importedImageView,
            VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL};
        VkDescriptorBufferInfo rawUniform{
            frame.rawUniform, 0, kRawParametersSize};
        VkDescriptorImageInfo storageImage{
            VK_NULL_HANDLE, frame.processingView, VK_IMAGE_LAYOUT_GENERAL};
        VkDescriptorBufferInfo spektraUniform{
            frame.spektraUniform, 0, kRawColorParametersSize};
        VkDescriptorBufferInfo lensShading{
            frame.lensShadingBuffer, 0, frame.lensShadingCapacity};
        VkDescriptorBufferInfo geometryUniform{
            frame.geometryUniform, 0, kGeometryParametersSize};
        std::array<VkWriteDescriptorSet, 6> computeWrites{};
        for (auto& write : computeWrites)
            write.sType = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET;
        computeWrites[0].dstSet = frame.computeDescriptor;
        computeWrites[0].dstBinding =
            frame.rawImportKind == RawImportKind::Image ? 5 : 0;
        computeWrites[0].descriptorCount = 1;
        computeWrites[0].descriptorType =
            frame.rawImportKind == RawImportKind::Image
                ? VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER
                : VK_DESCRIPTOR_TYPE_STORAGE_BUFFER;
        if (frame.rawImportKind == RawImportKind::Image)
            computeWrites[0].pImageInfo = &rawImage;
        else
            computeWrites[0].pBufferInfo = &rawBuffer;
        computeWrites[1].dstSet = frame.computeDescriptor;
        computeWrites[1].dstBinding = 1;
        computeWrites[1].descriptorCount = 1;
        computeWrites[1].descriptorType = VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER;
        computeWrites[1].pBufferInfo = &rawUniform;
        computeWrites[2].dstSet = frame.computeDescriptor;
        computeWrites[2].dstBinding = 2;
        computeWrites[2].descriptorCount = 1;
        computeWrites[2].descriptorType = VK_DESCRIPTOR_TYPE_STORAGE_IMAGE;
        computeWrites[2].pImageInfo = &storageImage;
        computeWrites[3].dstSet = frame.computeDescriptor;
        computeWrites[3].dstBinding = 3;
        computeWrites[3].descriptorCount = 1;
        computeWrites[3].descriptorType = VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER;
        computeWrites[3].pBufferInfo = &spektraUniform;
        computeWrites[4].dstSet = frame.computeDescriptor;
        computeWrites[4].dstBinding = 4;
        computeWrites[4].descriptorCount = 1;
        computeWrites[4].descriptorType = VK_DESCRIPTOR_TYPE_STORAGE_BUFFER;
        computeWrites[4].pBufferInfo = &lensShading;
        computeWrites[5].dstSet = frame.computeDescriptor;
        computeWrites[5].dstBinding = 6;
        computeWrites[5].descriptorCount = 1;
        computeWrites[5].descriptorType = VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER;
        computeWrites[5].pBufferInfo = &geometryUniform;
        vkUpdateDescriptorSets(
            device_, static_cast<uint32_t>(computeWrites.size()),
            computeWrites.data(), 0, nullptr);

        VkDescriptorImageInfo sampledImage{
            sampler_, frame.useSpektraOutput
                ? frame.spektraView : frame.processingView,
            VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL};
        VkWriteDescriptorSet presentWrite{
            VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET};
        presentWrite.dstSet = frame.presentDescriptor;
        presentWrite.dstBinding = 0;
        presentWrite.descriptorCount = 1;
        presentWrite.descriptorType =
            VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
        presentWrite.pImageInfo = &sampledImage;
        vkUpdateDescriptorSets(device_, 1, &presentWrite, 0, nullptr);
    }

    void updateCaptureDescriptor(Frame& frame)
    {
        VkDescriptorBufferInfo rawBuffer{
            frame.packedRawBuffer, 0, VK_WHOLE_SIZE};
        VkDescriptorImageInfo rawImage{
            rawIntegerSampler_, frame.importedImageView,
            VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL};
        VkDescriptorBufferInfo rawUniform{
            frame.rawUniform, 0, kRawParametersSize};
        VkDescriptorImageInfo outputImage{
            VK_NULL_HANDLE, captureView_, VK_IMAGE_LAYOUT_GENERAL};
        VkDescriptorBufferInfo spektraUniform{
            frame.spektraUniform, 0, kRawColorParametersSize};
        VkDescriptorBufferInfo lensShading{
            frame.lensShadingBuffer, 0, frame.lensShadingCapacity};
        VkDescriptorBufferInfo geometryUniform{
            frame.geometryUniform, 0, kGeometryParametersSize};
        VkDescriptorImageInfo rcdInput{
            rawIntegerSampler_, rcdDemosaicView_, VK_IMAGE_LAYOUT_GENERAL};
        std::array<VkWriteDescriptorSet, 7> writes{};
        for (auto& write : writes)
            write.sType = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET;
        writes[0].dstSet = frame.computeDescriptor;
        writes[0].dstBinding =
            frame.rawImportKind == RawImportKind::Image ? 5 : 0;
        writes[0].descriptorCount = 1;
        writes[0].descriptorType =
            frame.rawImportKind == RawImportKind::Image
                ? VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER
                : VK_DESCRIPTOR_TYPE_STORAGE_BUFFER;
        if (frame.rawImportKind == RawImportKind::Image)
            writes[0].pImageInfo = &rawImage;
        else
            writes[0].pBufferInfo = &rawBuffer;
        writes[1].dstSet = frame.computeDescriptor;
        writes[1].dstBinding = 1;
        writes[1].descriptorCount = 1;
        writes[1].descriptorType = VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER;
        writes[1].pBufferInfo = &rawUniform;
        writes[2].dstSet = frame.computeDescriptor;
        writes[2].dstBinding = 2;
        writes[2].descriptorCount = 1;
        writes[2].descriptorType = VK_DESCRIPTOR_TYPE_STORAGE_IMAGE;
        writes[2].pImageInfo = &outputImage;
        writes[3].dstSet = frame.computeDescriptor;
        writes[3].dstBinding = 3;
        writes[3].descriptorCount = 1;
        writes[3].descriptorType = VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER;
        writes[3].pBufferInfo = &spektraUniform;
        writes[4].dstSet = frame.computeDescriptor;
        writes[4].dstBinding = 4;
        writes[4].descriptorCount = 1;
        writes[4].descriptorType = VK_DESCRIPTOR_TYPE_STORAGE_BUFFER;
        writes[4].pBufferInfo = &lensShading;
        writes[5].dstSet = frame.computeDescriptor;
        writes[5].dstBinding = 6;
        writes[5].descriptorCount = 1;
        writes[5].descriptorType = VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER;
        writes[5].pBufferInfo = &geometryUniform;
        writes[6].dstSet = frame.computeDescriptor;
        writes[6].dstBinding = 7;
        writes[6].descriptorCount = 1;
        writes[6].descriptorType =
            VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
        writes[6].pImageInfo = &rcdInput;
        vkUpdateDescriptorSets(
            device_, static_cast<uint32_t>(writes.size()),
            writes.data(), 0, nullptr);
    }

    void createSwapchain()
    {
        VkSurfaceCapabilitiesKHR capabilities{};
        require(vkGetPhysicalDeviceSurfaceCapabilitiesKHR(
                    physicalDevice_, surface_, &capabilities),
                "vkGetPhysicalDeviceSurfaceCapabilitiesKHR");
        uint32_t formatCount = 0;
        require(vkGetPhysicalDeviceSurfaceFormatsKHR(
                    physicalDevice_, surface_, &formatCount, nullptr),
                "vkGetPhysicalDeviceSurfaceFormatsKHR");
        if (formatCount == 0)
            throw std::runtime_error("Surface exposes no Vulkan formats");
        std::vector<VkSurfaceFormatKHR> formats(formatCount);
        require(vkGetPhysicalDeviceSurfaceFormatsKHR(
                    physicalDevice_, surface_, &formatCount, formats.data()),
                "vkGetPhysicalDeviceSurfaceFormatsKHR");
        VkSurfaceFormatKHR selected = formats.front();
        for (const VkSurfaceFormatKHR& format : formats) {
            if ((format.format == VK_FORMAT_B8G8R8A8_UNORM ||
                 format.format == VK_FORMAT_R8G8B8A8_UNORM) &&
                format.colorSpace == VK_COLOR_SPACE_SRGB_NONLINEAR_KHR) {
                selected = format;
                break;
            }
        }
        swapchainFormat_ = selected.format;
        swapchainExtent_ = capabilities.currentExtent;
        if (swapchainExtent_.width == std::numeric_limits<uint32_t>::max()) {
            uint32_t width = static_cast<uint32_t>(
                std::max(ANativeWindow_getWidth(window_), 1));
            uint32_t height = static_cast<uint32_t>(
                std::max(ANativeWindow_getHeight(window_), 1));
            swapchainExtent_.width = std::clamp(
                width, capabilities.minImageExtent.width,
                capabilities.maxImageExtent.width);
            swapchainExtent_.height = std::clamp(
                height, capabilities.minImageExtent.height,
                capabilities.maxImageExtent.height);
        }

        uint32_t imageCount = std::max(
            capabilities.minImageCount, kFramesInFlight);
        if (capabilities.maxImageCount > 0)
            imageCount = std::min(imageCount, capabilities.maxImageCount);
        VkSwapchainCreateInfoKHR info{
            VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR};
        info.surface = surface_;
        info.minImageCount = imageCount;
        info.imageFormat = selected.format;
        info.imageColorSpace = selected.colorSpace;
        info.imageExtent = swapchainExtent_;
        info.imageArrayLayers = 1;
        info.imageUsage = VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT;
        info.imageSharingMode = VK_SHARING_MODE_EXCLUSIVE;
        info.preTransform = capabilities.currentTransform;
        info.compositeAlpha = chooseCompositeAlpha(capabilities);
        info.presentMode = VK_PRESENT_MODE_FIFO_KHR;
        info.clipped = VK_TRUE;
        require(vkCreateSwapchainKHR(device_, &info, nullptr, &swapchain_),
                "vkCreateSwapchainKHR");

        uint32_t actualCount = 0;
        require(vkGetSwapchainImagesKHR(
                    device_, swapchain_, &actualCount, nullptr),
                "vkGetSwapchainImagesKHR");
        swapchainImages_.resize(actualCount);
        require(vkGetSwapchainImagesKHR(
                    device_, swapchain_, &actualCount,
                    swapchainImages_.data()),
                "vkGetSwapchainImagesKHR");
        for (VkImage image : swapchainImages_)
            swapchainViews_.push_back(createImageView(image, swapchainFormat_));
        createPresentationPipeline();
        for (VkImageView view : swapchainViews_) {
            VkFramebufferCreateInfo framebufferInfo{
                VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO};
            framebufferInfo.renderPass = renderPass_;
            framebufferInfo.attachmentCount = 1;
            framebufferInfo.pAttachments = &view;
            framebufferInfo.width = swapchainExtent_.width;
            framebufferInfo.height = swapchainExtent_.height;
            framebufferInfo.layers = 1;
            VkFramebuffer framebuffer = VK_NULL_HANDLE;
            require(vkCreateFramebuffer(
                        device_, &framebufferInfo, nullptr, &framebuffer),
                    "vkCreateFramebuffer");
            framebuffers_.push_back(framebuffer);
        }
    }

    static VkCompositeAlphaFlagBitsKHR chooseCompositeAlpha(
        const VkSurfaceCapabilitiesKHR& capabilities)
    {
        constexpr std::array<VkCompositeAlphaFlagBitsKHR, 4> choices{{
            VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR,
            VK_COMPOSITE_ALPHA_PRE_MULTIPLIED_BIT_KHR,
            VK_COMPOSITE_ALPHA_POST_MULTIPLIED_BIT_KHR,
            VK_COMPOSITE_ALPHA_INHERIT_BIT_KHR}};
        for (VkCompositeAlphaFlagBitsKHR choice : choices) {
            if ((capabilities.supportedCompositeAlpha & choice) != 0)
                return choice;
        }
        return VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR;
    }

    void createPresentationPipeline()
    {
        VkAttachmentDescription attachment{};
        attachment.format = swapchainFormat_;
        attachment.samples = VK_SAMPLE_COUNT_1_BIT;
        attachment.loadOp = VK_ATTACHMENT_LOAD_OP_CLEAR;
        attachment.storeOp = VK_ATTACHMENT_STORE_OP_STORE;
        attachment.initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;
        attachment.finalLayout = VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
        VkAttachmentReference reference{0,
            VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL};
        VkSubpassDescription subpass{};
        subpass.pipelineBindPoint = VK_PIPELINE_BIND_POINT_GRAPHICS;
        subpass.colorAttachmentCount = 1;
        subpass.pColorAttachments = &reference;
        VkSubpassDependency dependency{};
        dependency.srcSubpass = VK_SUBPASS_EXTERNAL;
        dependency.dstSubpass = 0;
        dependency.srcStageMask = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
        dependency.dstStageMask = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
        dependency.dstAccessMask = VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
        VkRenderPassCreateInfo renderInfo{
            VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO};
        renderInfo.attachmentCount = 1;
        renderInfo.pAttachments = &attachment;
        renderInfo.subpassCount = 1;
        renderInfo.pSubpasses = &subpass;
        renderInfo.dependencyCount = 1;
        renderInfo.pDependencies = &dependency;
        require(vkCreateRenderPass(
                    device_, &renderInfo, nullptr, &renderPass_),
                "vkCreateRenderPass");

        VkPushConstantRange pushRange{
            VK_SHADER_STAGE_FRAGMENT_BIT, 0, sizeof(float) * 3};
        VkPipelineLayoutCreateInfo layoutInfo{
            VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO};
        layoutInfo.setLayoutCount = 1;
        layoutInfo.pSetLayouts = &presentDescriptorLayout_;
        layoutInfo.pushConstantRangeCount = 1;
        layoutInfo.pPushConstantRanges = &pushRange;
        require(vkCreatePipelineLayout(
                    device_, &layoutInfo, nullptr, &presentPipelineLayout_),
                "vkCreatePipelineLayout");

        VkShaderModule vertex = makeShader(
            device_, kPresentVertexSpirv.data(), kPresentVertexSpirv.size());
        VkShaderModule fragment = makeShader(
            device_, kPresentFragmentSpirv.data(), kPresentFragmentSpirv.size());
        std::array<VkPipelineShaderStageCreateInfo, 2> stages{};
        stages[0].sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
        stages[0].stage = VK_SHADER_STAGE_VERTEX_BIT;
        stages[0].module = vertex;
        stages[0].pName = "main";
        stages[1].sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
        stages[1].stage = VK_SHADER_STAGE_FRAGMENT_BIT;
        stages[1].module = fragment;
        stages[1].pName = "main";
        VkPipelineVertexInputStateCreateInfo vertexInput{
            VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO};
        VkPipelineInputAssemblyStateCreateInfo assembly{
            VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO};
        assembly.topology = VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST;
        VkPipelineViewportStateCreateInfo viewport{
            VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO};
        viewport.viewportCount = 1;
        viewport.scissorCount = 1;
        VkPipelineRasterizationStateCreateInfo raster{
            VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO};
        raster.polygonMode = VK_POLYGON_MODE_FILL;
        raster.cullMode = VK_CULL_MODE_NONE;
        raster.frontFace = VK_FRONT_FACE_COUNTER_CLOCKWISE;
        raster.lineWidth = 1.0f;
        VkPipelineMultisampleStateCreateInfo multisample{
            VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO};
        multisample.rasterizationSamples = VK_SAMPLE_COUNT_1_BIT;
        VkPipelineColorBlendAttachmentState blendAttachment{};
        blendAttachment.colorWriteMask =
            VK_COLOR_COMPONENT_R_BIT | VK_COLOR_COMPONENT_G_BIT |
            VK_COLOR_COMPONENT_B_BIT | VK_COLOR_COMPONENT_A_BIT;
        VkPipelineColorBlendStateCreateInfo blend{
            VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO};
        blend.attachmentCount = 1;
        blend.pAttachments = &blendAttachment;
        std::array<VkDynamicState, 2> dynamicStates{
            VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR};
        VkPipelineDynamicStateCreateInfo dynamic{
            VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO};
        dynamic.dynamicStateCount = static_cast<uint32_t>(dynamicStates.size());
        dynamic.pDynamicStates = dynamicStates.data();
        VkGraphicsPipelineCreateInfo pipelineInfo{
            VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO};
        pipelineInfo.stageCount = static_cast<uint32_t>(stages.size());
        pipelineInfo.pStages = stages.data();
        pipelineInfo.pVertexInputState = &vertexInput;
        pipelineInfo.pInputAssemblyState = &assembly;
        pipelineInfo.pViewportState = &viewport;
        pipelineInfo.pRasterizationState = &raster;
        pipelineInfo.pMultisampleState = &multisample;
        pipelineInfo.pColorBlendState = &blend;
        pipelineInfo.pDynamicState = &dynamic;
        pipelineInfo.layout = presentPipelineLayout_;
        pipelineInfo.renderPass = renderPass_;
        VkResult result = vkCreateGraphicsPipelines(
            device_, VK_NULL_HANDLE, 1, &pipelineInfo, nullptr,
            &presentPipeline_);
        vkDestroyShaderModule(device_, fragment, nullptr);
        vkDestroyShaderModule(device_, vertex, nullptr);
        require(result, "vkCreateGraphicsPipelines");
    }

    void acquireRawForCompute(VkCommandBuffer commandBuffer, const Frame& frame)
    {
        if (frame.rawImportKind == RawImportKind::Image) {
            VkImageMemoryBarrier barrier{
                VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER};
            barrier.srcAccessMask = 0;
            barrier.dstAccessMask = VK_ACCESS_SHADER_READ_BIT;
            barrier.oldLayout = VK_IMAGE_LAYOUT_GENERAL;
            barrier.newLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
            barrier.srcQueueFamilyIndex = VK_QUEUE_FAMILY_FOREIGN_EXT;
            barrier.dstQueueFamilyIndex = queueFamily_;
            barrier.image = frame.importedImage;
            barrier.subresourceRange.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
            barrier.subresourceRange.levelCount = 1;
            barrier.subresourceRange.layerCount = 1;
            vkCmdPipelineBarrier(
                commandBuffer, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,
                VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, 0, 0, nullptr,
                0, nullptr, 1, &barrier);
            return;
        }

        VkBufferMemoryBarrier barrier{
            VK_STRUCTURE_TYPE_BUFFER_MEMORY_BARRIER};
        barrier.srcAccessMask = VK_ACCESS_HOST_WRITE_BIT;
        barrier.dstAccessMask = VK_ACCESS_SHADER_READ_BIT;
        barrier.srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
        barrier.dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
        barrier.buffer = frame.packedRawBuffer;
        barrier.offset = 0;
        barrier.size = VK_WHOLE_SIZE;
        vkCmdPipelineBarrier(
            commandBuffer, VK_PIPELINE_STAGE_HOST_BIT,
            VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, 0, 0, nullptr,
            1, &barrier, 0, nullptr);
    }

    void releaseRawFromCompute(VkCommandBuffer commandBuffer, const Frame& frame)
    {
        if (frame.rawImportKind == RawImportKind::Image) {
            VkImageMemoryBarrier barrier{
                VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER};
            barrier.srcAccessMask = VK_ACCESS_SHADER_READ_BIT;
            barrier.dstAccessMask = 0;
            barrier.oldLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
            barrier.newLayout = VK_IMAGE_LAYOUT_GENERAL;
            barrier.srcQueueFamilyIndex = queueFamily_;
            barrier.dstQueueFamilyIndex = VK_QUEUE_FAMILY_FOREIGN_EXT;
            barrier.image = frame.importedImage;
            barrier.subresourceRange.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
            barrier.subresourceRange.levelCount = 1;
            barrier.subresourceRange.layerCount = 1;
            vkCmdPipelineBarrier(
                commandBuffer, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
                VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT, 0, 0, nullptr,
                0, nullptr, 1, &barrier);
            return;
        }

        return;
    }

    void recordRcdStages(VkCommandBuffer commandBuffer, Frame& frame)
    {
        std::array<VkImageMemoryBarrier, RcdStageCount> initialize{};
        std::array<VkImage, RcdStageCount> images{
            rcdGuideImage_, rcdGreenImage_, rcdRatioImage_,
            rcdDemosaicImage_};
        for (size_t index = 0; index < initialize.size(); ++index) {
            auto& barrier = initialize[index];
            barrier.sType = VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER;
            barrier.srcAccessMask = rcdLayoutsInitialized_
                ? VK_ACCESS_SHADER_READ_BIT | VK_ACCESS_SHADER_WRITE_BIT : 0;
            barrier.dstAccessMask = VK_ACCESS_SHADER_WRITE_BIT;
            barrier.oldLayout = rcdLayoutsInitialized_
                ? VK_IMAGE_LAYOUT_GENERAL : VK_IMAGE_LAYOUT_UNDEFINED;
            barrier.newLayout = VK_IMAGE_LAYOUT_GENERAL;
            barrier.srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
            barrier.dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
            barrier.image = images[index];
            barrier.subresourceRange = {
                VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1};
        }
        vkCmdPipelineBarrier(
            commandBuffer,
            rcdLayoutsInitialized_ ? VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT
                                   : VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,
            VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, 0,
            0, nullptr, 0, nullptr,
            static_cast<uint32_t>(initialize.size()), initialize.data());
        rcdLayoutsInitialized_ = true;

        const size_t input = frame.rawImportKind == RawImportKind::Image
            ? RcdImageInput : RcdBufferInput;
        const uint32_t blockWidth = (rcdWidth_ + 1) / 2;
        const uint32_t blockHeight = (rcdHeight_ + 1) / 2;
        for (size_t stage = 0; stage < RcdStageCount; ++stage) {
            vkCmdBindPipeline(
                commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE,
                rcdPipelines_[input][stage]);
            vkCmdBindDescriptorSets(
                commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE,
                rcdPipelineLayout_, 0, 1, &rcdDescriptorSets_[stage],
                0, nullptr);
            vkCmdDispatch(
                commandBuffer,
                (blockWidth + rcdWorkgroup_.x - 1) / rcdWorkgroup_.x,
                (blockHeight + rcdWorkgroup_.y - 1) / rcdWorkgroup_.y,
                1);

            VkImageMemoryBarrier ready{
                VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER};
            ready.srcAccessMask = VK_ACCESS_SHADER_WRITE_BIT;
            ready.dstAccessMask = VK_ACCESS_SHADER_READ_BIT;
            ready.oldLayout = VK_IMAGE_LAYOUT_GENERAL;
            ready.newLayout = VK_IMAGE_LAYOUT_GENERAL;
            ready.srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
            ready.dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
            ready.image = images[stage];
            ready.subresourceRange = {
                VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1};
            vkCmdPipelineBarrier(
                commandBuffer, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
                VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, 0,
                0, nullptr, 0, nullptr, 1, &ready);
        }
    }

    void recordCaptureCommands(Frame& frame)
    {
        VkCommandBufferBeginInfo begin{
            VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO};
        begin.flags = VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;
        require(vkBeginCommandBuffer(frame.commandBuffer, &begin),
                "vkBeginCommandBuffer for capture");

        acquireRawForCompute(frame.commandBuffer, frame);

        VkImageMemoryBarrier toGeneral{
            VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER};
        toGeneral.srcAccessMask = captureInitialized_
            ? VK_ACCESS_SHADER_READ_BIT : 0;
        toGeneral.dstAccessMask = VK_ACCESS_SHADER_WRITE_BIT;
        toGeneral.oldLayout = captureInitialized_
            ? VK_IMAGE_LAYOUT_GENERAL
            : VK_IMAGE_LAYOUT_UNDEFINED;
        toGeneral.newLayout = VK_IMAGE_LAYOUT_GENERAL;
        toGeneral.srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
        toGeneral.dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
        toGeneral.image = captureImage_;
        toGeneral.subresourceRange.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
        toGeneral.subresourceRange.levelCount = 1;
        toGeneral.subresourceRange.layerCount = 1;
        vkCmdPipelineBarrier(
            frame.commandBuffer,
            captureInitialized_ ? VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT
                                : VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,
            VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, 0, 0, nullptr, 0, nullptr,
            1, &toGeneral);

        recordRcdStages(frame.commandBuffer, frame);

        vkCmdBindPipeline(
            frame.commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE,
            captureColorPipeline_);
        vkCmdBindDescriptorSets(
            frame.commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE,
            computePipelineLayout_, 0, 1, &frame.computeDescriptor,
            0, nullptr);
        vkCmdDispatch(
            frame.commandBuffer,
            (captureWidth_ + 7) / 8,
            (captureHeight_ + 7) / 8,
            1);

        VkImageMemoryBarrier sourceReady{
            VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER};
        sourceReady.srcAccessMask = VK_ACCESS_SHADER_WRITE_BIT;
        sourceReady.dstAccessMask = VK_ACCESS_SHADER_READ_BIT;
        sourceReady.oldLayout = VK_IMAGE_LAYOUT_GENERAL;
        sourceReady.newLayout = VK_IMAGE_LAYOUT_GENERAL;
        sourceReady.srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
        sourceReady.dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
        sourceReady.image = captureImage_;
        sourceReady.subresourceRange = toGeneral.subresourceRange;
        vkCmdPipelineBarrier(
            frame.commandBuffer, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
            VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, 0, 0, nullptr, 0, nullptr,
            1, &sourceReady);

        if (frame.useSpektraOutput && !captureSpektraLayoutInitialized_) {
            VkImageMemoryBarrier initializeSpektra{
                VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER};
            initializeSpektra.srcAccessMask = 0;
            initializeSpektra.dstAccessMask = VK_ACCESS_SHADER_WRITE_BIT;
            initializeSpektra.oldLayout = VK_IMAGE_LAYOUT_UNDEFINED;
            initializeSpektra.newLayout = VK_IMAGE_LAYOUT_GENERAL;
            initializeSpektra.srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
            initializeSpektra.dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
            initializeSpektra.image = captureSpektraImage_;
            initializeSpektra.subresourceRange = toGeneral.subresourceRange;
            vkCmdPipelineBarrier(
                frame.commandBuffer, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,
                VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, 0, 0, nullptr,
                0, nullptr, 1, &initializeSpektra);
            captureSpektraLayoutInitialized_ = true;
        }

        releaseRawFromCompute(frame.commandBuffer, frame);

        require(vkEndCommandBuffer(frame.commandBuffer),
                "vkEndCommandBuffer for capture RAW processing");
        captureInitialized_ = true;
    }

    void recordCaptureReadbackCommands(Frame& frame, VkImage outputImage)
    {
        VkCommandBufferBeginInfo begin{
            VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO};
        begin.flags = VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;
        require(vkBeginCommandBuffer(frame.commandBuffer, &begin),
                "vkBeginCommandBuffer for capture readback");

        VkImageMemoryBarrier toTransfer{
            VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER};
        toTransfer.srcAccessMask = VK_ACCESS_SHADER_WRITE_BIT |
                                   VK_ACCESS_SHADER_READ_BIT;
        toTransfer.dstAccessMask = VK_ACCESS_TRANSFER_READ_BIT;
        toTransfer.oldLayout = VK_IMAGE_LAYOUT_GENERAL;
        toTransfer.newLayout = VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL;
        toTransfer.srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
        toTransfer.dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
        toTransfer.image = outputImage;
        toTransfer.subresourceRange = {
            VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1};
        vkCmdPipelineBarrier(
            frame.commandBuffer, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
            VK_PIPELINE_STAGE_TRANSFER_BIT, 0, 0, nullptr, 0, nullptr,
            1, &toTransfer);

        VkImageMemoryBarrier readbackToDestination{
            VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER};
        readbackToDestination.srcAccessMask = captureReadbackInitialized_
            ? VK_ACCESS_TRANSFER_READ_BIT : 0;
        readbackToDestination.dstAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT;
        readbackToDestination.oldLayout = captureReadbackInitialized_
            ? VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL
            : VK_IMAGE_LAYOUT_UNDEFINED;
        readbackToDestination.newLayout = VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
        readbackToDestination.srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
        readbackToDestination.dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
        readbackToDestination.image = captureReadbackImage_;
        readbackToDestination.subresourceRange = toTransfer.subresourceRange;
        vkCmdPipelineBarrier(
            frame.commandBuffer,
            captureReadbackInitialized_ ? VK_PIPELINE_STAGE_TRANSFER_BIT
                                        : VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,
            VK_PIPELINE_STAGE_TRANSFER_BIT, 0, 0, nullptr, 0, nullptr,
            1, &readbackToDestination);

        VkImageBlit conversion{};
        conversion.srcSubresource.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
        conversion.srcSubresource.layerCount = 1;
        conversion.srcOffsets[1] = {
            static_cast<int32_t>(captureWidth_),
            static_cast<int32_t>(captureHeight_), 1};
        conversion.dstSubresource.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
        conversion.dstSubresource.layerCount = 1;
        conversion.dstOffsets[1] = conversion.srcOffsets[1];
        vkCmdBlitImage(
            frame.commandBuffer,
            outputImage, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
            captureReadbackImage_, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
            1, &conversion, VK_FILTER_NEAREST);

        VkImageMemoryBarrier readbackToSource{
            VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER};
        readbackToSource.srcAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT;
        readbackToSource.dstAccessMask = VK_ACCESS_TRANSFER_READ_BIT;
        readbackToSource.oldLayout = VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
        readbackToSource.newLayout = VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL;
        readbackToSource.srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
        readbackToSource.dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
        readbackToSource.image = captureReadbackImage_;
        readbackToSource.subresourceRange = toTransfer.subresourceRange;
        vkCmdPipelineBarrier(
            frame.commandBuffer, VK_PIPELINE_STAGE_TRANSFER_BIT,
            VK_PIPELINE_STAGE_TRANSFER_BIT, 0, 0, nullptr, 0, nullptr,
            1, &readbackToSource);

        VkBufferMemoryBarrier stagingForTransfer{
            VK_STRUCTURE_TYPE_BUFFER_MEMORY_BARRIER};
        stagingForTransfer.srcAccessMask = VK_ACCESS_HOST_READ_BIT;
        stagingForTransfer.dstAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT;
        stagingForTransfer.srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
        stagingForTransfer.dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
        stagingForTransfer.buffer = captureStaging_;
        stagingForTransfer.offset = 0;
        stagingForTransfer.size = VK_WHOLE_SIZE;
        vkCmdPipelineBarrier(
            frame.commandBuffer, VK_PIPELINE_STAGE_HOST_BIT,
            VK_PIPELINE_STAGE_TRANSFER_BIT, 0, 0, nullptr,
            1, &stagingForTransfer, 0, nullptr);

        VkBufferImageCopy copy{};
        copy.bufferOffset = 0;
        copy.imageSubresource.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
        copy.imageSubresource.mipLevel = 0;
        copy.imageSubresource.baseArrayLayer = 0;
        copy.imageSubresource.layerCount = 1;
        copy.imageExtent = {captureWidth_, captureHeight_, 1};
        vkCmdCopyImageToBuffer(
            frame.commandBuffer, captureReadbackImage_,
            VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, captureStaging_, 1, &copy);
        captureReadbackInitialized_ = true;

        VkImageMemoryBarrier backToGeneral{
            VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER};
        backToGeneral.srcAccessMask = VK_ACCESS_TRANSFER_READ_BIT;
        backToGeneral.dstAccessMask = VK_ACCESS_SHADER_READ_BIT |
                                      VK_ACCESS_SHADER_WRITE_BIT;
        backToGeneral.oldLayout = VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL;
        backToGeneral.newLayout = VK_IMAGE_LAYOUT_GENERAL;
        backToGeneral.srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
        backToGeneral.dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
        backToGeneral.image = outputImage;
        backToGeneral.subresourceRange = toTransfer.subresourceRange;
        vkCmdPipelineBarrier(
            frame.commandBuffer, VK_PIPELINE_STAGE_TRANSFER_BIT,
            VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, 0, 0, nullptr, 0, nullptr,
            1, &backToGeneral);

        VkBufferMemoryBarrier stagingForHost{
            VK_STRUCTURE_TYPE_BUFFER_MEMORY_BARRIER};
        stagingForHost.srcAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT;
        stagingForHost.dstAccessMask = VK_ACCESS_HOST_READ_BIT;
        stagingForHost.srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
        stagingForHost.dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
        stagingForHost.buffer = captureStaging_;
        stagingForHost.offset = 0;
        stagingForHost.size = VK_WHOLE_SIZE;
        vkCmdPipelineBarrier(
            frame.commandBuffer, VK_PIPELINE_STAGE_TRANSFER_BIT,
            VK_PIPELINE_STAGE_HOST_BIT, 0, 0, nullptr,
            1, &stagingForHost, 0, nullptr);
        require(vkEndCommandBuffer(frame.commandBuffer),
                "vkEndCommandBuffer for capture readback");
    }

    void recordRawCommands(Frame& frame)
    {
        VkCommandBufferBeginInfo begin{
            VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO};
        begin.flags = VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;
        require(vkBeginCommandBuffer(frame.commandBuffer, &begin),
                "vkBeginCommandBuffer");
        acquireRawForCompute(frame.commandBuffer, frame);
        VkImageMemoryBarrier toGeneral{
            VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER};
        toGeneral.srcAccessMask = frame.processingInitialized
            ? VK_ACCESS_SHADER_READ_BIT : 0;
        toGeneral.dstAccessMask = VK_ACCESS_SHADER_WRITE_BIT;
        toGeneral.oldLayout = frame.processingInitialized
            ? VK_IMAGE_LAYOUT_GENERAL
            : VK_IMAGE_LAYOUT_UNDEFINED;
        toGeneral.newLayout = VK_IMAGE_LAYOUT_GENERAL;
        toGeneral.srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
        toGeneral.dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
        toGeneral.image = frame.processingImage;
        toGeneral.subresourceRange.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
        toGeneral.subresourceRange.levelCount = 1;
        toGeneral.subresourceRange.layerCount = 1;
        vkCmdPipelineBarrier(
            frame.commandBuffer,
            frame.processingInitialized
                ? VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT
                : VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,
            VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, 0, 0, nullptr, 0, nullptr,
            1, &toGeneral);
        vkCmdBindPipeline(
            frame.commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE,
            frame.rawImportKind == RawImportKind::Image
                ? computeImagePipeline_ : computePipeline_);
        vkCmdBindDescriptorSets(
            frame.commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE,
            computePipelineLayout_, 0, 1, &frame.computeDescriptor, 0, nullptr);
        vkCmdDispatch(frame.commandBuffer,
                      (processingWidth_ + 7) / 8,
                      (processingHeight_ + 7) / 8, 1);

        VkImageMemoryBarrier sourceReady{
            VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER};
        sourceReady.srcAccessMask = VK_ACCESS_SHADER_WRITE_BIT;
        sourceReady.dstAccessMask = VK_ACCESS_SHADER_READ_BIT;
        sourceReady.oldLayout = VK_IMAGE_LAYOUT_GENERAL;
        sourceReady.newLayout = VK_IMAGE_LAYOUT_GENERAL;
        sourceReady.srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
        sourceReady.dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
        sourceReady.image = frame.processingImage;
        sourceReady.subresourceRange = toGeneral.subresourceRange;
        vkCmdPipelineBarrier(
            frame.commandBuffer, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
            VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, 0, 0, nullptr, 0, nullptr,
            1, &sourceReady);

        if (frame.useSpektraOutput && !frame.spektraLayoutInitialized) {
            VkImageMemoryBarrier initializeSpektra{
                VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER};
            initializeSpektra.srcAccessMask = 0;
            initializeSpektra.dstAccessMask = VK_ACCESS_SHADER_WRITE_BIT;
            initializeSpektra.oldLayout = VK_IMAGE_LAYOUT_UNDEFINED;
            initializeSpektra.newLayout = VK_IMAGE_LAYOUT_GENERAL;
            initializeSpektra.srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
            initializeSpektra.dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
            initializeSpektra.image = frame.spektraImage;
            initializeSpektra.subresourceRange = toGeneral.subresourceRange;
            vkCmdPipelineBarrier(
                frame.commandBuffer, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,
                VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, 0, 0, nullptr,
                0, nullptr, 1, &initializeSpektra);
            frame.spektraLayoutInitialized = true;
        }

        releaseRawFromCompute(frame.commandBuffer, frame);

        require(vkEndCommandBuffer(frame.commandBuffer),
                "vkEndCommandBuffer for RAW processing");
        frame.processingInitialized = true;
    }

    void recordPresentCommands(
        Frame& frame,
        uint32_t imageIndex,
        VkImage outputImage)
    {
        VkCommandBufferBeginInfo begin{
            VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO};
        begin.flags = VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;
        require(vkBeginCommandBuffer(frame.commandBuffer, &begin),
                "vkBeginCommandBuffer for presentation");

        VkImageMemoryBarrier toSample{
            VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER};
        toSample.srcAccessMask = VK_ACCESS_SHADER_WRITE_BIT |
                                 VK_ACCESS_SHADER_READ_BIT;
        toSample.dstAccessMask = VK_ACCESS_SHADER_READ_BIT;
        toSample.oldLayout = VK_IMAGE_LAYOUT_GENERAL;
        toSample.newLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
        toSample.srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
        toSample.dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
        toSample.image = outputImage;
        toSample.subresourceRange = {
            VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1};
        vkCmdPipelineBarrier(
            frame.commandBuffer, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
            VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT, 0, 0, nullptr, 0, nullptr,
            1, &toSample);

        VkClearValue clear{};
        clear.color.float32[3] = 1.0f;
        VkRenderPassBeginInfo renderBegin{
            VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO};
        renderBegin.renderPass = renderPass_;
        renderBegin.framebuffer = framebuffers_.at(imageIndex);
        renderBegin.renderArea.extent = swapchainExtent_;
        renderBegin.clearValueCount = 1;
        renderBegin.pClearValues = &clear;
        vkCmdBeginRenderPass(
            frame.commandBuffer, &renderBegin, VK_SUBPASS_CONTENTS_INLINE);
        VkViewport viewport{0.0f, 0.0f,
            static_cast<float>(swapchainExtent_.width),
            static_cast<float>(swapchainExtent_.height), 0.0f, 1.0f};
        VkRect2D scissor{{0, 0}, swapchainExtent_};
        vkCmdSetViewport(frame.commandBuffer, 0, 1, &viewport);
        vkCmdSetScissor(frame.commandBuffer, 0, 1, &scissor);
        vkCmdBindPipeline(frame.commandBuffer,
                          VK_PIPELINE_BIND_POINT_GRAPHICS, presentPipeline_);
        vkCmdBindDescriptorSets(
            frame.commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS,
            presentPipelineLayout_, 0, 1, &frame.presentDescriptor, 0, nullptr);
        std::array<float, 3> aspects{
            static_cast<float>(processingWidth_) /
                static_cast<float>(processingHeight_),
            static_cast<float>(swapchainExtent_.width) /
                static_cast<float>(swapchainExtent_.height),
            swapchainFormat_ == VK_FORMAT_B8G8R8A8_SRGB ||
                    swapchainFormat_ == VK_FORMAT_R8G8B8A8_SRGB
                ? 1.0f : 0.0f};
        vkCmdPushConstants(
            frame.commandBuffer, presentPipelineLayout_,
            VK_SHADER_STAGE_FRAGMENT_BIT, 0, sizeof(aspects), aspects.data());
        vkCmdDraw(frame.commandBuffer, 3, 1, 0, 0);
        vkCmdEndRenderPass(frame.commandBuffer);

        VkImageMemoryBarrier backToGeneral{
            VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER};
        backToGeneral.srcAccessMask = VK_ACCESS_SHADER_READ_BIT;
        backToGeneral.dstAccessMask = VK_ACCESS_SHADER_READ_BIT |
                                      VK_ACCESS_SHADER_WRITE_BIT;
        backToGeneral.oldLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
        backToGeneral.newLayout = VK_IMAGE_LAYOUT_GENERAL;
        backToGeneral.srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
        backToGeneral.dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
        backToGeneral.image = outputImage;
        backToGeneral.subresourceRange = toSample.subresourceRange;
        vkCmdPipelineBarrier(
            frame.commandBuffer, VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
            VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, 0, 0, nullptr, 0, nullptr,
            1, &backToGeneral);
        require(vkEndCommandBuffer(frame.commandBuffer),
                "vkEndCommandBuffer for presentation");
    }

    void recreateSwapchain()
    {
        require(vkDeviceWaitIdle(device_), "vkDeviceWaitIdle");
        destroySwapchain();
        createSwapchain();
    }

    void destroySwapchain()
    {
        for (VkFramebuffer framebuffer : framebuffers_)
            vkDestroyFramebuffer(device_, framebuffer, nullptr);
        framebuffers_.clear();
        if (presentPipeline_ != VK_NULL_HANDLE)
            vkDestroyPipeline(device_, presentPipeline_, nullptr);
        presentPipeline_ = VK_NULL_HANDLE;
        if (presentPipelineLayout_ != VK_NULL_HANDLE)
            vkDestroyPipelineLayout(device_, presentPipelineLayout_, nullptr);
        presentPipelineLayout_ = VK_NULL_HANDLE;
        if (renderPass_ != VK_NULL_HANDLE)
            vkDestroyRenderPass(device_, renderPass_, nullptr);
        renderPass_ = VK_NULL_HANDLE;
        for (VkImageView view : swapchainViews_)
            vkDestroyImageView(device_, view, nullptr);
        swapchainViews_.clear();
        swapchainImages_.clear();
        if (swapchain_ != VK_NULL_HANDLE)
            vkDestroySwapchainKHR(device_, swapchain_, nullptr);
        swapchain_ = VK_NULL_HANDLE;
    }

    void destroyProcessingImages()
    {
        for (Frame& frame : frames_) {
            if (frame.spektraView != VK_NULL_HANDLE)
                vkDestroyImageView(device_, frame.spektraView, nullptr);
            if (frame.spektraImage != VK_NULL_HANDLE)
                vkDestroyImage(device_, frame.spektraImage, nullptr);
            if (frame.spektraMemory != VK_NULL_HANDLE)
                vkFreeMemory(device_, frame.spektraMemory, nullptr);
            if (frame.processingView != VK_NULL_HANDLE)
                vkDestroyImageView(device_, frame.processingView, nullptr);
            if (frame.processingImage != VK_NULL_HANDLE)
                vkDestroyImage(device_, frame.processingImage, nullptr);
            if (frame.processingMemory != VK_NULL_HANDLE)
                vkFreeMemory(device_, frame.processingMemory, nullptr);
            frame.processingView = VK_NULL_HANDLE;
            frame.processingImage = VK_NULL_HANDLE;
            frame.processingMemory = VK_NULL_HANDLE;
            frame.processingInitialized = false;
            frame.spektraView = VK_NULL_HANDLE;
            frame.spektraImage = VK_NULL_HANDLE;
            frame.spektraMemory = VK_NULL_HANDLE;
            frame.spektraLayoutInitialized = false;
            frame.spektraInitialized = false;
            frame.useSpektraOutput = false;
        }
        processingWidth_ = 0;
        processingHeight_ = 0;
    }

    void destroyDevice()
    {
        if (device_ == VK_NULL_HANDLE)
            return;
        vkDeviceWaitIdle(device_);
        spektraRenderer_.reset();
        for (Frame& frame : frames_)
            releaseImportedBuffer(frame);
        releaseImportedBuffer(captureFrame_);
        destroyProcessingImages();
        destroyRcdResources();
        destroyCaptureResources();
        destroySwapchain();
        for (Frame& frame : frames_) {
            destroyPackedRawBuffer(frame);
            if (frame.rawMapped != nullptr)
                vkUnmapMemory(device_, frame.rawUniformMemory);
            if (frame.spektraMapped != nullptr)
                vkUnmapMemory(device_, frame.spektraUniformMemory);
            if (frame.geometryMapped != nullptr)
                vkUnmapMemory(device_, frame.geometryUniformMemory);
            if (frame.rawUniform != VK_NULL_HANDLE)
                vkDestroyBuffer(device_, frame.rawUniform, nullptr);
            if (frame.rawUniformMemory != VK_NULL_HANDLE)
                vkFreeMemory(device_, frame.rawUniformMemory, nullptr);
            if (frame.spektraUniform != VK_NULL_HANDLE)
                vkDestroyBuffer(device_, frame.spektraUniform, nullptr);
            if (frame.spektraUniformMemory != VK_NULL_HANDLE)
                vkFreeMemory(device_, frame.spektraUniformMemory, nullptr);
            if (frame.geometryUniform != VK_NULL_HANDLE)
                vkDestroyBuffer(device_, frame.geometryUniform, nullptr);
            if (frame.geometryUniformMemory != VK_NULL_HANDLE)
                vkFreeMemory(device_, frame.geometryUniformMemory, nullptr);
            if (frame.lensShadingMapped != nullptr)
                vkUnmapMemory(device_, frame.lensShadingMemory);
            if (frame.lensShadingBuffer != VK_NULL_HANDLE)
                vkDestroyBuffer(device_, frame.lensShadingBuffer, nullptr);
            if (frame.lensShadingMemory != VK_NULL_HANDLE)
                vkFreeMemory(device_, frame.lensShadingMemory, nullptr);
            if (frame.imageAvailable != VK_NULL_HANDLE)
                vkDestroySemaphore(device_, frame.imageAvailable, nullptr);
            if (frame.renderFinished != VK_NULL_HANDLE)
                vkDestroySemaphore(device_, frame.renderFinished, nullptr);
            if (frame.fence != VK_NULL_HANDLE)
                vkDestroyFence(device_, frame.fence, nullptr);
            frame = {};
        }
        destroyPackedRawBuffer(captureFrame_);
        if (captureFrame_.rawMapped != nullptr)
            vkUnmapMemory(device_, captureFrame_.rawUniformMemory);
        if (captureFrame_.spektraMapped != nullptr)
            vkUnmapMemory(device_, captureFrame_.spektraUniformMemory);
        if (captureFrame_.geometryMapped != nullptr)
            vkUnmapMemory(device_, captureFrame_.geometryUniformMemory);
        if (captureFrame_.lensShadingMapped != nullptr)
            vkUnmapMemory(device_, captureFrame_.lensShadingMemory);
        if (captureFrame_.rawUniform != VK_NULL_HANDLE)
            vkDestroyBuffer(device_, captureFrame_.rawUniform, nullptr);
        if (captureFrame_.rawUniformMemory != VK_NULL_HANDLE)
            vkFreeMemory(device_, captureFrame_.rawUniformMemory, nullptr);
        if (captureFrame_.spektraUniform != VK_NULL_HANDLE)
            vkDestroyBuffer(device_, captureFrame_.spektraUniform, nullptr);
        if (captureFrame_.spektraUniformMemory != VK_NULL_HANDLE)
            vkFreeMemory(device_, captureFrame_.spektraUniformMemory, nullptr);
        if (captureFrame_.geometryUniform != VK_NULL_HANDLE)
            vkDestroyBuffer(device_, captureFrame_.geometryUniform, nullptr);
        if (captureFrame_.geometryUniformMemory != VK_NULL_HANDLE)
            vkFreeMemory(device_, captureFrame_.geometryUniformMemory, nullptr);
        if (captureFrame_.lensShadingBuffer != VK_NULL_HANDLE)
            vkDestroyBuffer(device_, captureFrame_.lensShadingBuffer, nullptr);
        if (captureFrame_.lensShadingMemory != VK_NULL_HANDLE)
            vkFreeMemory(device_, captureFrame_.lensShadingMemory, nullptr);
        if (captureFrame_.fence != VK_NULL_HANDLE)
            vkDestroyFence(device_, captureFrame_.fence, nullptr);
        captureFrame_ = {};
        if (rawIntegerSampler_ != VK_NULL_HANDLE)
            vkDestroySampler(device_, rawIntegerSampler_, nullptr);
        if (sampler_ != VK_NULL_HANDLE)
            vkDestroySampler(device_, sampler_, nullptr);
        destroyRcdPipelines();
        if (rcdPipelineLayout_ != VK_NULL_HANDLE)
            vkDestroyPipelineLayout(device_, rcdPipelineLayout_, nullptr);
        if (rcdDescriptorPool_ != VK_NULL_HANDLE)
            vkDestroyDescriptorPool(device_, rcdDescriptorPool_, nullptr);
        if (rcdDescriptorLayout_ != VK_NULL_HANDLE)
            vkDestroyDescriptorSetLayout(
                device_, rcdDescriptorLayout_, nullptr);
        if (computeImagePipeline_ != VK_NULL_HANDLE)
            vkDestroyPipeline(device_, computeImagePipeline_, nullptr);
        if (captureColorPipeline_ != VK_NULL_HANDLE)
            vkDestroyPipeline(device_, captureColorPipeline_, nullptr);
        if (computePipeline_ != VK_NULL_HANDLE)
            vkDestroyPipeline(device_, computePipeline_, nullptr);
        if (computePipelineLayout_ != VK_NULL_HANDLE)
            vkDestroyPipelineLayout(device_, computePipelineLayout_, nullptr);
        if (descriptorPool_ != VK_NULL_HANDLE)
            vkDestroyDescriptorPool(device_, descriptorPool_, nullptr);
        if (presentDescriptorLayout_ != VK_NULL_HANDLE)
            vkDestroyDescriptorSetLayout(
                device_, presentDescriptorLayout_, nullptr);
        if (computeDescriptorLayout_ != VK_NULL_HANDLE)
            vkDestroyDescriptorSetLayout(
                device_, computeDescriptorLayout_, nullptr);
        if (commandPool_ != VK_NULL_HANDLE)
            vkDestroyCommandPool(device_, commandPool_, nullptr);
        vkDestroyDevice(device_, nullptr);
        device_ = VK_NULL_HANDLE;
        queue_ = VK_NULL_HANDLE;
        physicalDevice_ = VK_NULL_HANDLE;
        frameIndex_ = 0;
        rawIntegerSampler_ = VK_NULL_HANDLE;
        sampler_ = VK_NULL_HANDLE;
        computeImagePipeline_ = VK_NULL_HANDLE;
        captureColorPipeline_ = VK_NULL_HANDLE;
        computePipeline_ = VK_NULL_HANDLE;
        rcdPipelineLayout_ = VK_NULL_HANDLE;
        rcdDescriptorPool_ = VK_NULL_HANDLE;
        rcdDescriptorLayout_ = VK_NULL_HANDLE;
        rcdDescriptorSets_.fill(VK_NULL_HANDLE);
        computePipelineLayout_ = VK_NULL_HANDLE;
        descriptorPool_ = VK_NULL_HANDLE;
        presentDescriptorLayout_ = VK_NULL_HANDLE;
        computeDescriptorLayout_ = VK_NULL_HANDLE;
        commandPool_ = VK_NULL_HANDLE;
        getAhbProperties_ = nullptr;
    }

    void clearSurface()
    {
        destroyDevice();
        if (surface_ != VK_NULL_HANDLE) {
            vkDestroySurfaceKHR(instance_, surface_, nullptr);
            surface_ = VK_NULL_HANDLE;
        }
        if (window_ != nullptr) {
            ANativeWindow_release(window_);
            window_ = nullptr;
        }
    }

    VkInstance instance_ = VK_NULL_HANDLE;
    VkSurfaceKHR surface_ = VK_NULL_HANDLE;
    ANativeWindow* window_ = nullptr;
    VkPhysicalDevice physicalDevice_ = VK_NULL_HANDLE;
    VkDevice device_ = VK_NULL_HANDLE;
    VkQueue queue_ = VK_NULL_HANDLE;
    uint32_t queueFamily_ = 0;
    PFN_vkGetAndroidHardwareBufferPropertiesANDROID getAhbProperties_ = nullptr;
    VkCommandPool commandPool_ = VK_NULL_HANDLE;
    VkDescriptorSetLayout computeDescriptorLayout_ = VK_NULL_HANDLE;
    VkDescriptorSetLayout presentDescriptorLayout_ = VK_NULL_HANDLE;
    VkDescriptorPool descriptorPool_ = VK_NULL_HANDLE;
    VkPipelineLayout computePipelineLayout_ = VK_NULL_HANDLE;
    VkPipeline computePipeline_ = VK_NULL_HANDLE;
    VkPipeline computeImagePipeline_ = VK_NULL_HANDLE;
    VkPipeline captureColorPipeline_ = VK_NULL_HANDLE;
    VkDescriptorSetLayout rcdDescriptorLayout_ = VK_NULL_HANDLE;
    VkDescriptorPool rcdDescriptorPool_ = VK_NULL_HANDLE;
    VkPipelineLayout rcdPipelineLayout_ = VK_NULL_HANDLE;
    std::array<VkDescriptorSet, RcdStageCount> rcdDescriptorSets_{};
    std::array<std::array<VkPipeline, RcdStageCount>, RcdInputCount>
        rcdPipelines_{};
    RcdWorkgroup rcdWorkgroup_{8, 8};
    uint32_t rcdPipelineWidth_ = 0;
    uint32_t rcdPipelineHeight_ = 0;
    int32_t rcdPipelinePattern_ = -1;
    VkSampler sampler_ = VK_NULL_HANDLE;
    VkSampler rawIntegerSampler_ = VK_NULL_HANDLE;
    std::array<Frame, kFramesInFlight> frames_{};
    Frame captureFrame_{};
    uint32_t frameIndex_ = 0;
    uint32_t processingWidth_ = 0;
    uint32_t processingHeight_ = 0;
    VkImage captureImage_ = VK_NULL_HANDLE;
    VkDeviceMemory captureMemory_ = VK_NULL_HANDLE;
    VkImageView captureView_ = VK_NULL_HANDLE;
    VkImage captureSpektraImage_ = VK_NULL_HANDLE;
    VkDeviceMemory captureSpektraMemory_ = VK_NULL_HANDLE;
    VkImageView captureSpektraView_ = VK_NULL_HANDLE;
    VkImage rcdGuideImage_ = VK_NULL_HANDLE;
    VkDeviceMemory rcdGuideMemory_ = VK_NULL_HANDLE;
    VkImageView rcdGuideView_ = VK_NULL_HANDLE;
    VkImage rcdGreenImage_ = VK_NULL_HANDLE;
    VkDeviceMemory rcdGreenMemory_ = VK_NULL_HANDLE;
    VkImageView rcdGreenView_ = VK_NULL_HANDLE;
    VkImage rcdRatioImage_ = VK_NULL_HANDLE;
    VkDeviceMemory rcdRatioMemory_ = VK_NULL_HANDLE;
    VkImageView rcdRatioView_ = VK_NULL_HANDLE;
    VkImage rcdDemosaicImage_ = VK_NULL_HANDLE;
    VkDeviceMemory rcdDemosaicMemory_ = VK_NULL_HANDLE;
    VkImageView rcdDemosaicView_ = VK_NULL_HANDLE;
    uint32_t rcdWidth_ = 0;
    uint32_t rcdHeight_ = 0;
    bool rcdLayoutsInitialized_ = false;
    VkImage captureReadbackImage_ = VK_NULL_HANDLE;
    VkDeviceMemory captureReadbackMemory_ = VK_NULL_HANDLE;
    VkBuffer captureStaging_ = VK_NULL_HANDLE;
    VkDeviceMemory captureStagingMemory_ = VK_NULL_HANDLE;
    void* captureStagingMapped_ = nullptr;
    uint32_t captureWidth_ = 0;
    uint32_t captureHeight_ = 0;
    bool captureInitialized_ = false;
    bool captureSpektraLayoutInitialized_ = false;
    bool captureReadbackInitialized_ = false;
    VkSwapchainKHR swapchain_ = VK_NULL_HANDLE;
    VkFormat swapchainFormat_ = VK_FORMAT_UNDEFINED;
    VkExtent2D swapchainExtent_{};
    std::vector<VkImage> swapchainImages_;
    std::vector<VkImageView> swapchainViews_;
    VkRenderPass renderPass_ = VK_NULL_HANDLE;
    VkPipelineLayout presentPipelineLayout_ = VK_NULL_HANDLE;
    VkPipeline presentPipeline_ = VK_NULL_HANDLE;
    std::vector<VkFramebuffer> framebuffers_;
    mutable std::mutex diagnosticMutex_;
    std::mutex operationMutex_;
    std::recursive_mutex queueMutex_;
    std::unique_ptr<spektrafilm::VulkanRenderer> spektraRenderer_;
    std::string diagnostic_ = "Renderer created without a surface";
};

VulkanRenderer* renderer(jlong handle)
{
    return reinterpret_cast<VulkanRenderer*>(static_cast<intptr_t>(handle));
}

void throwRuntimeException(JNIEnv* environment, const char* message)
{
    jclass exceptionClass = environment->FindClass("java/lang/RuntimeException");
    if (exceptionClass != nullptr)
        environment->ThrowNew(exceptionClass, message);
}

} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_unspektrawesome_vulkan_VulkanRenderer_nativeCreate(
    JNIEnv* environment,
    jobject)
{
    try {
        return static_cast<jlong>(
            reinterpret_cast<intptr_t>(new VulkanRenderer()));
    } catch (const std::exception& error) {
        throwRuntimeException(environment, error.what());
        return 0;
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_unspektrawesome_vulkan_VulkanRenderer_nativeSetSurface(
    JNIEnv* environment,
    jobject,
    jlong handle,
    jobject surface)
{
    VulkanRenderer* instance = renderer(handle);
    return instance != nullptr && instance->setSurface(environment, surface)
        ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_unspektrawesome_vulkan_VulkanRenderer_nativeRender(
    JNIEnv* environment,
    jobject,
    jlong handle,
    jobject hardwareBuffer,
    jobject packedRawBytes,
    jobject rawParameters,
    jobject rawColorParameters,
    jobject geometryParameters,
    jobject spektraFilmParameters,
    jobject lensShadingMap,
    jint processingWidth,
    jint processingHeight)
{
    VulkanRenderer* instance = renderer(handle);
    return instance != nullptr && instance->render(
        environment, hardwareBuffer, packedRawBytes, rawParameters,
        rawColorParameters,
        geometryParameters,
        spektraFilmParameters,
        lensShadingMap,
        processingWidth, processingHeight) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_unspektrawesome_vulkan_VulkanRenderer_nativeCaptureRcd(
    JNIEnv* environment,
    jobject,
    jlong handle,
    jobject hardwareBuffer,
    jobject packedRawBytes,
    jobject rawParameters,
    jobject rawColorParameters,
    jobject geometryParameters,
    jobject spektraFilmParameters,
    jobject lensShadingMap,
    jobject rgbaOutput,
    jdouble spektraTimeSeconds)
{
    VulkanRenderer* instance = renderer(handle);
    return instance != nullptr && instance->captureRcd(
        environment, hardwareBuffer, packedRawBytes, rawParameters,
        rawColorParameters,
        geometryParameters,
        spektraFilmParameters,
        lensShadingMap, rgbaOutput,
        spektraTimeSeconds) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_unspektrawesome_vulkan_VulkanRenderer_nativeGetDiagnosticString(
    JNIEnv* environment,
    jobject,
    jlong handle)
{
    VulkanRenderer* instance = renderer(handle);
    std::string value = instance != nullptr
        ? instance->diagnostic() : "Renderer is released";
    return environment->NewStringUTF(value.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_unspektrawesome_vulkan_VulkanRenderer_nativeRelease(
    JNIEnv*,
    jobject,
    jlong handle)
{
    delete renderer(handle);
}
