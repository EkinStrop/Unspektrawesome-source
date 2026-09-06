#pragma once

#include <memory>
#include <mutex>
#include <string>
#include <vulkan/vulkan.h>

#include "SpektraRenderer.h"

namespace spektrafilm {

using VulkanPassDiagnostics = RendererPassDiagnostics;
using VulkanRenderDiagnostics = RendererDiagnostics;

struct ExternalVulkanContext {
  VkInstance instance = VK_NULL_HANDLE;
  VkPhysicalDevice physicalDevice = VK_NULL_HANDLE;
  VkDevice device = VK_NULL_HANDLE;
  VkQueue computeQueue = VK_NULL_HANDLE;
  uint32_t computeQueueFamily = 0;
  std::recursive_mutex *queueMutex = nullptr;

  bool isValid() const {
    return instance != VK_NULL_HANDLE &&
      physicalDevice != VK_NULL_HANDLE &&
      device != VK_NULL_HANDLE &&
      computeQueue != VK_NULL_HANDLE &&
      queueMutex != nullptr;
  }
};

class VulkanRenderer final : public Renderer {
public:
  VulkanRenderer();
  explicit VulkanRenderer(const ExternalVulkanContext &externalContext);
  ~VulkanRenderer() override;

  VulkanRenderer(const VulkanRenderer &) = delete;
  VulkanRenderer &operator=(const VulkanRenderer &) = delete;

  bool isAvailable() const override;
  const VulkanRenderDiagnostics &lastDiagnostics() const override;
  const std::string &lastError() const override;
  void releaseTransientResources() override;

  bool renderVulkanImages(
    VkImage sourceImage,
    VkImage destinationImage,
    uint32_t width,
    uint32_t height,
    const RenderParams &params,
    double time
  );

  bool render(
    const ImageView &source,
    const MutableImageView &destination,
    const RenderWindow &window,
    const RenderParams &params,
    double time
  ) override;

private:
  struct Impl;
  ExternalVulkanContext externalContext_{};
  bool hasExternalContext_ = false;
  std::unique_ptr<Impl> impl_;
  VulkanRenderDiagnostics lastRenderDiagnostics_;
  std::string lastRenderError_;
};

} // namespace spektrafilm
