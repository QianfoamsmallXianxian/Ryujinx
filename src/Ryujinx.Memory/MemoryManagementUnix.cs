using Ryujinx.Common;
using Ryujinx.Common.Logging;
using System;
using System.Collections.Concurrent;
using System.Diagnostics.CodeAnalysis;
using System.Runtime.InteropServices;
using System.Runtime.Versioning;
using static Ryujinx.Memory.MemoryManagerUnixHelper;

namespace Ryujinx.Memory
{
    [SupportedOSPlatform("linux")]
    [SupportedOSPlatform("macos")]
    [SupportedOSPlatform("android")]
    [SuppressMessage("Interoperability", "CA1416:Validate platform compatibility")]
    static class MemoryManagementUnix
    {
        private static readonly ConcurrentDictionary<nint, ulong> _allocations = new();

        public static nint Allocate(ulong size, bool forJit)
        {
            return AllocateInternal(size, MmapProts.PROT_READ | MmapProts.PROT_WRITE, forJit);
        }

        public static nint Reserve(ulong size, bool forJit)
        {
            return AllocateInternal(size, MmapProts.PROT_NONE, forJit);
        }

        /// <summary>
        /// NEOV2 NCE-LOWBASE1: reserves a range at exactly the given address, if that range is free.
        /// The address is only a hint to the kernel (no MAP_FIXED), so existing mappings are never replaced.
        /// </summary>
        /// <param name="address">Desired start address</param>
        /// <param name="size">Size of the range</param>
        /// <returns>The address, or zero if the range could not be reserved there</returns>
        public static nint ReserveAt(nint address, ulong size)
        {
            MmapFlags flags = MmapFlags.MAP_ANONYMOUS | MmapFlags.MAP_PRIVATE | MmapFlags.MAP_NORESERVE;

            nint ptr = Mmap(address, size, MmapProts.PROT_NONE, flags, -1, 0);

            if (ptr == MAP_FAILED)
            {
                return nint.Zero;
            }

            if (ptr != address || !_allocations.TryAdd(ptr, size))
            {
                munmap(ptr, size);

                return nint.Zero;
            }

            return ptr;
        }

        private static nint AllocateInternal(ulong size, MmapProts prot, bool forJit, bool shared = false)
        {
            MmapFlags flags = MmapFlags.MAP_ANONYMOUS;

            if (shared)
            {
                flags |= MmapFlags.MAP_SHARED | MmapFlags.MAP_UNLOCKED;
            }
            else
            {
                flags |= MmapFlags.MAP_PRIVATE;
            }

            if (prot == MmapProts.PROT_NONE)
            {
                flags |= MmapFlags.MAP_NORESERVE;
            }

            if (OperatingSystem.IsMacOSVersionAtLeast(10, 14) && forJit)
            {
                flags |= MmapFlags.MAP_JIT_DARWIN;

                if (prot == (MmapProts.PROT_READ | MmapProts.PROT_WRITE))
                {
                    prot |= MmapProts.PROT_EXEC;
                }
            }

            nint ptr = Mmap(nint.Zero, size, prot, flags, -1, 0);

            if (ptr == MAP_FAILED)
            {
                throw new SystemException(Marshal.GetLastPInvokeErrorMessage());
            }

            if (!_allocations.TryAdd(ptr, size))
            {
                // This should be impossible, kernel shouldn't return an already mapped address.
                throw new InvalidOperationException();
            }

            return ptr;
        }

        public static void Commit(nint address, ulong size, bool forJit)
        {
            MmapProts prot = MmapProts.PROT_READ | MmapProts.PROT_WRITE;

            if (OperatingSystem.IsMacOSVersionAtLeast(10, 14) && forJit)
            {
                prot |= MmapProts.PROT_EXEC;
            }

            if (mprotect(address, size, prot) != 0)
            {
                throw new SystemException(Marshal.GetLastPInvokeErrorMessage());
            }
        }

        public static void Decommit(nint address, ulong size)
        {
            // Must be writable for madvise to work properly.
            if (mprotect(address, size, MmapProts.PROT_READ | MmapProts.PROT_WRITE) != 0)
            {
                throw new SystemException(Marshal.GetLastPInvokeErrorMessage());
            }

            if (madvise(address, size, MADV_REMOVE) != 0
                || mprotect(address, size, MmapProts.PROT_NONE) != 0)
            {
                throw new SystemException(Marshal.GetLastPInvokeErrorMessage());
            }
        }

        public static bool Reprotect(nint address, ulong size, MemoryPermission permission)
        {
            return mprotect(address, size, GetProtection(permission)) == 0;
        }

        private static MmapProts GetProtection(MemoryPermission permission)
        {
            return permission switch
            {
                MemoryPermission.None => MmapProts.PROT_NONE,
                MemoryPermission.Read => MmapProts.PROT_READ,
                MemoryPermission.ReadAndWrite => MmapProts.PROT_READ | MmapProts.PROT_WRITE,
                MemoryPermission.ReadAndExecute => MmapProts.PROT_READ | MmapProts.PROT_EXEC,
                MemoryPermission.ReadWriteExecute => MmapProts.PROT_READ | MmapProts.PROT_WRITE | MmapProts.PROT_EXEC,
                MemoryPermission.Execute => MmapProts.PROT_EXEC,
                _ => throw new MemoryProtectionException(permission),
            };
        }

        public static bool Free(nint address)
        {
            if (_allocations.TryRemove(address, out ulong size))
            {
                return munmap(address, size) == 0;
            }

            return false;
        }

        public static bool Unmap(nint address, ulong size)
        {
            return munmap(address, size) == 0;
        }

        public unsafe static nint CreateSharedMemory(ulong size, bool reserve)
        {
            int fd;

            if (OperatingSystem.IsMacOS())
            {
                byte[] memName = "Ryujinx-XXXXXX"u8.ToArray();

                fixed (byte* pMemName = memName)
                {
                    fd = shm_open((nint)pMemName, 0x2 | 0x200 | 0x800 | 0x400, 384); // O_RDWR | O_CREAT | O_EXCL | O_TRUNC, 0600
                    if (fd == -1)
                    {
                        throw new SystemException(Marshal.GetLastPInvokeErrorMessage());
                    }

                    if (shm_unlink((nint)pMemName) != 0)
                    {
                        throw new SystemException(Marshal.GetLastPInvokeErrorMessage());
                    }
                }
            }
            else if (PlatformInfo.IsBionic)
            {
                // NEOV2 NCE-MEMFD1: prefer memfd over ashmem. ashmem_mmap swaps vma->vm_file for its
                // internal shmem file, so the kernel never merges a new MAP_FIXED view with the adjacent
                // view at mmap time. Every guest Map then costs one VMA forever, and games that call
                // svcMapPhysicalMemory tens of thousands of times (UE4, e.g. Dragon Quest) exhaust
                // vm.max_map_count (65530). Views of a memfd keep vm_file and merge when VA and offset
                // are contiguous. ART also uses memfd for its executable JIT views in app processes.
                // The app runs as linux-bionic, where OperatingSystem.IsAndroid() is false: PlatformInfo.IsBionic already holds here.
                if (RuntimeInformation.ProcessArchitecture == Architecture.Arm64)
                {
                    byte[] memfdName = "Ryujinx-memfd"u8.ToArray();

                    fixed (byte* pMemfdName = memfdName)
                    {
                        // Guest code is executed from these views, so ask for an executable memfd explicitly.
                        // Kernels without MFD_EXEC reject the flag with EINVAL: retry without it. A kernel that
                        // forbids executable memfds (vm.memfd_noexec=2) returns EACCES and we keep ashmem.
                        fd = (int)syscall_memfd_create(SYS_memfd_create_arm64, (nint)pMemfdName, MFD_CLOEXEC | MFD_EXEC);

                        if (fd < 0 && Marshal.GetLastPInvokeError() == 22) // EINVAL
                        {
                            fd = (int)syscall_memfd_create(SYS_memfd_create_arm64, (nint)pMemfdName, MFD_CLOEXEC);
                        }
                    }

                    if (fd >= 0)
                    {
                        if (ftruncate(fd, (nint)size) == 0)
                        {
                            LogBackingOnce($"[NEOV2-NCE-MEMFD1] Backing memory: memfd (size=0x{size:X})");

                            return (nint)fd;
                        }

                        int truncateError = Marshal.GetLastPInvokeError();
                        close(fd);
                        LogBackingOnce($"[NEOV2-NCE-MEMFD1] memfd ftruncate failed (errno={truncateError}); falling back to ashmem");
                    }
                    else
                    {
                        LogBackingOnce($"[NEOV2-NCE-MEMFD1] memfd_create failed (errno={Marshal.GetLastPInvokeError()}); falling back to ashmem");
                    }
                }

                byte[] memName = "Ryujinx-XXXXXX"u8.ToArray();

                Logger.Debug?.Print(LogClass.Cpu, $"Creating Android SharedMemory of size:{size}");

                fixed (byte* pMemName = memName)
                {
                    fd = ASharedMemory_create((nint)pMemName, (nuint)size);
                    if (fd <= 0)
                    {
                        throw new OutOfMemoryException();
                    }
                }

                LogBackingOnce($"[NEOV2-NCE-MEMFD1] Backing memory: ashmem (size=0x{size:X})");

                // ASharedMemory_create handle ftruncate for us.
                return (nint)fd;
            }
            else
            {
                byte[] fileName = "/dev/shm/Ryujinx-XXXXXX"u8.ToArray();

                fixed (byte* pFileName = fileName)
                {
                    fd = mkstemp((nint)pFileName);
                    if (fd == -1 || unlink((nint)pFileName) != 0)
                    {
                        throw new SystemException(Marshal.GetLastPInvokeErrorMessage());
                    }
                }
            }

            if (ftruncate(fd, (nint)size) != 0)
            {
                throw new SystemException(Marshal.GetLastPInvokeErrorMessage());
            }

            return fd;
        }

        public static void DestroySharedMemory(nint handle)
        {
            close(handle.ToInt32());
        }

        public static nint MapSharedMemory(nint handle, ulong size)
        {
            return Mmap(nint.Zero, size, MmapProts.PROT_READ | MmapProts.PROT_WRITE, MmapFlags.MAP_SHARED, handle.ToInt32(), 0);
        }

        public static void UnmapSharedMemory(nint address, ulong size)
        {
            munmap(address, size);
        }

        public static void MapView(nint sharedMemory, ulong srcOffset, nint location, ulong size)
        {
            nint result = Mmap(location, size, MmapProts.PROT_READ | MmapProts.PROT_WRITE, MmapFlags.MAP_FIXED | MmapFlags.MAP_SHARED, sharedMemory.ToInt32(), (long)srcOffset);

            // NEOV2 NCE-MEMFD1: a failed view used to be ignored. The page then stayed as the PROT_NONE
            // reservation while the NCE protection map believed it was RW, and every guest access became
            // an endless "handled" tracking fault. Fail loudly instead.
            if (result == MAP_FAILED)
            {
                ThrowMappingFailure("MapView", location, size);
            }
        }

        public static void UnmapView(nint location, ulong size)
        {
            nint result = Mmap(location, size, MmapProts.PROT_NONE, MmapFlags.MAP_FIXED | MmapFlags.MAP_PRIVATE | MmapFlags.MAP_ANONYMOUS | MmapFlags.MAP_NORESERVE, -1, 0);

            if (result == MAP_FAILED)
            {
                ThrowMappingFailure("UnmapView", location, size);
            }
        }

        private static void ThrowMappingFailure(string operation, nint location, ulong size)
        {
            int errno = Marshal.GetLastPInvokeError();
            string message = $"[NEOV2-NCE-MEMFD1] {operation} failed at 0x{(ulong)location:X} size=0x{size:X} errno={errno} ({Marshal.GetLastPInvokeErrorMessage()})";

            Logger.Error?.Print(LogClass.Cpu, message);

            throw new SystemException(message);
        }

        private static int _backingLogged;

        private static void LogBackingOnce(string message)
        {
            if (System.Threading.Interlocked.Exchange(ref _backingLogged, 1) == 0)
            {
                Logger.Notice.Print(LogClass.Cpu, message);
            }
        }
    }
}
