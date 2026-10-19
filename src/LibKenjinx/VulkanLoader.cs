using Ryujinx.Common.Logging;
using Silk.NET.Core.Contexts;
using Silk.NET.Vulkan;
using System;
using System.Runtime.InteropServices;

namespace LibKenjinx
{
    public class VulkanLoader : IDisposable
    {
        private delegate nint GetInstanceProcAddress(nint instance, nint name);
        private delegate nint GetDeviceProcAddress(nint device, nint name);

        private nint _loadedLibrary = nint.Zero;
        private GetInstanceProcAddress _getInstanceProcAddr;
        private GetDeviceProcAddress _getDeviceProcAddr;

        public void Dispose()
        {
            if (_loadedLibrary != nint.Zero)
            {
                NativeLibrary.Free(_loadedLibrary);
                _loadedLibrary = nint.Zero;
            }
        }

        public VulkanLoader(nint driver)
        {
            _loadedLibrary = driver;

            if (_loadedLibrary != nint.Zero)
            {
                IntPtr instanceGetProc = NativeLibrary.GetExport(_loadedLibrary, "vkGetInstanceProcAddr");
                IntPtr deviceProc = NativeLibrary.GetExport(_loadedLibrary, "vkGetDeviceProcAddr");

                _getInstanceProcAddr = Marshal.GetDelegateForFunctionPointer<GetInstanceProcAddress>(instanceGetProc);
                _getDeviceProcAddr = Marshal.GetDelegateForFunctionPointer<GetDeviceProcAddress>(deviceProc);
            }
        }

        public unsafe Vk GetApi()
        {

            if (_loadedLibrary == nint.Zero)
            {
                return Vk.GetApi();
            }
            MultiNativeContext? ctx = new(new INativeContext[1]);
            Vk ret = new Vk(ctx);
            ctx.Contexts[0] = new LamdaNativeContext
            (
                x =>
                {
                    IntPtr xPtr = Marshal.StringToHGlobalAnsi(x);
                    byte* xp = (byte*)xPtr;
                    try
                    {
                        nint ptr;
                        ptr = _getInstanceProcAddr(ret.CurrentInstance.GetValueOrDefault().Handle, xPtr);

                        if (ptr == 0)
                        {
                            ptr = _getInstanceProcAddr(nint.Zero, xPtr);

                            if (ptr == 0)
                            {
                                IntPtr currentDevice = ret.CurrentDevice.GetValueOrDefault().Handle;
                                if (currentDevice != nint.Zero)
                                {
                                    ptr = _getDeviceProcAddr(currentDevice, xPtr);
                                }

                                if (ptr == 0)
                                {
                                    Logger.Warning?.Print(LogClass.Gpu, $"Failed to get function pointer: {x}");
                                }

                            }
                        }

                        return ptr;
                    }
                    finally
                    {
                        Marshal.FreeHGlobal(xPtr);
                    }
                }
            );
            return ret;
        }
    }
}
