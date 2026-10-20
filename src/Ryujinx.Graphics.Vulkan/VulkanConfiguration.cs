namespace Ryujinx.Graphics.Vulkan
{
    static class VulkanConfiguration
    {
        public const bool UseFastBufferUpdates = true;
        public const bool UseUnsafeBlit = true;
        public const bool UsePushDescriptors = true;

        public const bool ForceD24S8Unsupported = true;
        public const bool ForceRGB16IntFloatUnsupported = false;
    }
}
