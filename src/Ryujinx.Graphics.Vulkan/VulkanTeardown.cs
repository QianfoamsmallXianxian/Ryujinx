using System;
using System.Runtime.ExceptionServices;

namespace Ryujinx.Graphics.Vulkan
{
    static class VulkanTeardown
    {
        public static void Run(ref ExceptionDispatchInfo firstError, Action action)
        {
            try
            {
                action();
            }
            catch (Exception exception)
            {
                firstError ??= ExceptionDispatchInfo.Capture(exception);
            }
        }
    }
}
