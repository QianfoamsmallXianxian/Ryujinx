using ARMeilleure.Memory;
using Ryujinx.Common.Logging;
using Ryujinx.Common;
using Ryujinx.Memory;
using Ryujinx.Memory.Range;
using Ryujinx.Memory.Tracking;
using System;
using System.Collections.Generic;
using System.Linq;
using System.Runtime.CompilerServices;

namespace Ryujinx.Cpu.Nce
{
    /// <summary>
    /// Represents a CPU memory manager which maps guest virtual memory directly onto a host virtual region.
    /// </summary>
    public sealed class MemoryManagerNative : VirtualMemoryManagerRefCountedBase, IMemoryManager, IVirtualMemoryManagerTracked, IWritableBlock
    {
        private readonly MemoryBlock _addressSpace;

        private readonly MemoryBlock _backingMemory;
        private readonly PageTable<ulong> _pageTable;

        private readonly MemoryEhMeilleure _memoryEh;

        private readonly ManagedPageFlags _pages;
        private readonly NceMemoryProtectionMap _protections;

        /// <inheritdoc/>
        public bool UsesPrivateAllocations => false;

        public nint PageTablePointer => nint.Zero;

        public ulong ReservedSize => (ulong)_addressSpace.Pointer.ToInt64();

        // Actual native VA reservation, including code patch islands allocated by the loader.
        internal ulong GuestAddressStart => ReservedSize;
        internal ulong GuestAddressEnd => checked(ReservedSize + _addressSpace.Size);

        public MemoryManagerType Type => MemoryManagerType.HostMappedUnsafe;
        public bool HasGuestPermissionComposition => _protections != null;

        // The initial mapping contract must not erase a remapped region's tracking demand.
        public void SetMappedPermission(ulong address, ulong size, MemoryPermission permission)
        {
            _protections.SetGuest(address, size, permission, restoreTracking: true);
        }

        public MemoryTracking Tracking { get; }

        public event Action<ulong, ulong> UnmapEvent;

        public int AddressSpaceBits { get; }
        protected override ulong AddressSpaceSize { get; }

        /// <summary>
        /// Creates a new instance of the host mapped memory manager.
        /// </summary>
        /// <param name="addressSpace">Address space memory block</param>
        /// <param name="backingMemory">Physical backing memory where virtual memory will be mapped to</param>
        /// <param name="addressSpaceSize">Size of the address space</param>
        /// <param name="invalidAccessHandler">Optional function to handle invalid memory accesses</param>
        public MemoryManagerNative(
            MemoryBlock addressSpace,
            MemoryBlock backingMemory,
            ulong addressSpaceSize,
            InvalidAccessHandler invalidAccessHandler = null)
        {
            _backingMemory = backingMemory;
            _pageTable = new PageTable<ulong>();
            AddressSpaceSize = addressSpaceSize;

            ulong asSize = PageSize;
            int asBits = PageBits;

            while (asSize < addressSpaceSize)
            {
                asSize <<= 1;
                asBits++;
            }

            AddressSpaceBits = asBits;

            _pages = new ManagedPageFlags(asBits);

            _addressSpace = addressSpace;

            if (PlatformInfo.IsBionic)
                _protections = new NceMemoryProtectionMap(GuestAddressStart, _addressSpace.Size,
                    MemoryBlock.GetPageSize(), ApplyNativeProtection);

            Tracking = new MemoryTracking(this, PageSize, invalidAccessHandler);
            _memoryEh = new MemoryEhMeilleure(addressSpaceSize, Tracking,
                _protections != null ? HandleGuestTrackingFault : null);
        }

        /// <inheritdoc/>
        public void Map(ulong va, ulong pa, ulong size, MemoryMapFlags flags)
        {
            AssertValidAddressAndSize(va, size);

            if (_protections != null)
            {
                _protections.Map(va, size, () =>
                {
                    _addressSpace.MapView(_backingMemory, pa, AddressToOffset(va), size);
                    _pages.AddMapping(va, size);
                    PtMap(va, pa, size);
                });
                // Do not hold the protection lock while entering tracking callbacks.
                Tracking.Map(va, size);
                return;
            }
            _addressSpace.MapView(_backingMemory, pa, AddressToOffset(va), size);
            _pages.AddMapping(va, size);
            PtMap(va, pa, size);

            Tracking.Map(va, size);
        }

        private void PtMap(ulong va, ulong pa, ulong size)
        {
            while (size != 0)
            {
                _pageTable.Map(va, pa);

                va += PageSize;
                pa += PageSize;
                size -= PageSize;
            }
        }

        /// <inheritdoc/>
        public void Unmap(ulong va, ulong size)
        {
            AssertValidAddressAndSize(va, size);

            UnmapEvent?.Invoke(va, size);
            Tracking.Unmap(va, size);

            if (_protections != null)
            {
                _protections.Unmap(va, size, () =>
                {
                    _pages.RemoveMapping(va, size);
                    PtUnmap(va, size);
                    _addressSpace.UnmapView(_backingMemory, AddressToOffset(va), size);
                });
                return;
            }
            _pages.RemoveMapping(va, size);
            PtUnmap(va, size);
            _addressSpace.UnmapView(_backingMemory, AddressToOffset(va), size);
        }

        private void PtUnmap(ulong va, ulong size)
        {
            while (size != 0)
            {
                _pageTable.Unmap(va);

                va += PageSize;
                size -= PageSize;
            }
        }

        /// <inheritdoc/>
        public void Reprotect(ulong va, ulong size, MemoryPermission protection)
        {
            if (_protections != null)
            {
                bool restored = _protections.SetGuest(va, size, protection, TrackingProtectionRestoreEnabled);
                if (restored && !_protectionRestoreLogged)
                {
                    _protectionRestoreLogged = true;
                    Logger.Info?.Print(LogClass.Cpu, $"[NEOV2-NCE-PROT1] Tracking protection restored after a guest permission change at 0x{va:X} (0x{size:X} bytes)");
                }
                return;
            }
            _addressSpace.Reprotect(AddressToOffset(va), size, protection);

            // NEOV2 NCE-PROT1: on NCE the guest permission is applied to the same host pages the memory tracking
            // protects. Put the tracking protection back, so GPU tracked pages keep faulting on CPU access.
            if (TrackingProtectionRestoreEnabled && Tracking.RestoreGuestProtection(va, size) && !_protectionRestoreLogged)
            {
                _protectionRestoreLogged = true;
                Logger.Info?.Print(LogClass.Cpu, $"[NEOV2-NCE-PROT1] Tracking protection restored after a guest permission change at 0x{va:X} (0x{size:X} bytes)");
            }
        }

        private static bool _protectionRestoreLogged;

        /// <summary>
        /// NEOV2 NCE-PROT1: per game switch for restoring tracking protection after guest permission changes.
        /// Off by default. Set by the frontend before the game is loaded.
        /// </summary>
        public static bool TrackingProtectionRestoreEnabled { get; set; }

        public ref T GetRef<T>(ulong va) where T : unmanaged
        {
            if (!IsContiguous(va, Unsafe.SizeOf<T>()))
            {
                ThrowMemoryNotContiguous();
            }

            SignalMemoryTracking(va, (ulong)Unsafe.SizeOf<T>(), true);

            return ref _backingMemory.GetRef<T>(GetPhysicalAddressChecked(va));
        }

        [MethodImpl(MethodImplOptions.AggressiveInlining)]
        public override bool IsMapped(ulong va)
        {
            return ValidateAddress(va) && _pages.IsMapped(va);
        }

        /// <inheritdoc/>
        public bool IsRangeMapped(ulong va, ulong size)
        {
            AssertValidAddressAndSize(va, size);

            return _pages.IsRangeMapped(va, size);
        }

        /// <inheritdoc/>
        public IEnumerable<HostMemoryRange> GetHostRegions(ulong va, ulong size)
        {
            if (size == 0)
            {
                return Enumerable.Empty<HostMemoryRange>();
            }

            List<MemoryRange> guestRegions = GetPhysicalRegionsImpl(va, size);
            if (guestRegions == null)
            {
                return null;
            }

            HostMemoryRange[] regions = new HostMemoryRange[guestRegions.Count];

            for (int i = 0; i < regions.Length; i++)
            {
                MemoryRange guestRegion = guestRegions[i];
                nint pointer = _backingMemory.GetPointer(guestRegion.Address, guestRegion.Size);
                regions[i] = new HostMemoryRange((nuint)(ulong)pointer, guestRegion.Size);
            }

            return regions;
        }

        /// <inheritdoc/>
        public IEnumerable<MemoryRange> GetPhysicalRegions(ulong va, ulong size)
        {
            if (size == 0)
            {
                return Enumerable.Empty<MemoryRange>();
            }

            return GetPhysicalRegionsImpl(va, size);
        }

        private List<MemoryRange> GetPhysicalRegionsImpl(ulong va, ulong size)
        {
            if (!ValidateAddress(va) || !ValidateAddressAndSize(va, size))
            {
                return null;
            }

            int pages = GetPagesCount(va, (uint)size, out va);

            List<MemoryRange> regions = new();

            ulong regionStart = GetPhysicalAddressInternal(va);
            ulong regionSize = PageSize;

            for (int page = 0; page < pages - 1; page++)
            {
                if (!ValidateAddress(va + PageSize))
                {
                    return null;
                }

                ulong newPa = GetPhysicalAddressInternal(va + PageSize);

                if (GetPhysicalAddressInternal(va) + PageSize != newPa)
                {
                    regions.Add(new MemoryRange(regionStart, regionSize));
                    regionStart = newPa;
                    regionSize = 0;
                }

                va += PageSize;
                regionSize += PageSize;
            }

            regions.Add(new MemoryRange(regionStart, regionSize));

            return regions;
        }

        private ulong GetPhysicalAddressChecked(ulong va)
        {
            if (!IsMapped(va))
            {
                ThrowInvalidMemoryRegionException($"Not mapped: va=0x{va:X16}");
            }

            return GetPhysicalAddressInternal(va);
        }

        private ulong GetPhysicalAddressInternal(ulong va)
        {
            return _pageTable.Read(va) + (va & PageMask);
        }

        /// <inheritdoc/>
        /// <remarks>
        /// This function also validates that the given range is both valid and mapped, and will throw if it is not.
        /// </remarks>
        public override void SignalMemoryTracking(ulong va, ulong size, bool write, bool precise = false, int? exemptId = null)
        {
            AssertValidAddressAndSize(va, size);

            if (precise)
            {
                Tracking.VirtualMemoryEvent(va, size, write, precise: true, exemptId);
                return;
            }

            _pages.SignalMemoryTracking(Tracking, va, size, write, exemptId);
        }

        /// <inheritdoc/>
        public void TrackingReprotect(ulong va, ulong size, MemoryPermission protection, bool guest)
        {
            if (guest)
            {
                if (_protections != null) _protections.SetTracking(va, size, protection);
                else _addressSpace.Reprotect(AddressToOffset(va), size, protection, false);
            }
            else
            {
                _pages.TrackingReprotect(va, size, protection);
            }
        }

        /// <inheritdoc/>
        public RegionHandle BeginTracking(ulong address, ulong size, int id, RegionFlags flags)
        {
            return Tracking.BeginTracking(address, size, id, flags);
        }

        /// <inheritdoc/>
        public MultiRegionHandle BeginGranularTracking(ulong address, ulong size, IEnumerable<IRegionHandle> handles, ulong granularity, int id, RegionFlags flags)
        {
            return Tracking.BeginGranularTracking(address, size, handles, granularity, id, flags);
        }

        /// <inheritdoc/>
        public SmartMultiRegionHandle BeginSmartGranularTracking(ulong address, ulong size, ulong granularity, int id)
        {
            return Tracking.BeginSmartGranularTracking(address, size, granularity, id);
        }

        private void ApplyNativeProtection(ulong address, ulong size, MemoryPermission permission)
        {
            _addressSpace.Reprotect(AddressToOffset(address), size, permission);
        }

        // Only a genuine data-tracking fault may mark GPU resources dirty.
        // Kernel frame classification (including prefetch) stays in the approved R10 path.
        private ulong HandleGuestTrackingFault(ulong address, ulong size, bool write)
        {
            if (!_protections.IsGuestAccessAllowed(address, size, write))
            {
                return 0;
            }
            ulong pageMask = (ulong)PageSize - 1;
            ulong aligned = address & ~pageMask;
            ulong end = checked((address + size + pageMask) & ~pageMask);
            try
            {
                if (!Tracking.VirtualMemoryEvent(aligned, end - aligned, write))
                {
                    return 0;
                }

                // Tracking releases its lock before returning. Another thread may have
                // rearmed R/None already; that still permits a retry of a valid guest access.
                // Only a revoked guest contract/unmap makes this an unhandled fault.
                var access = _protections.GetAccessState(address, size, write);
                if (!access.GuestAllowed)
                {
                    return 0;
                }
                return address;
            }
            catch (Exception exception) when (exception is Ryujinx.Memory.MemoryProtectionException or InvalidMemoryRegionException or ArgumentException)
            {
                // A failed mprotect cannot be reported as a handled fault or retried forever.
                return 0;
            }
        }

        private ulong AddressToOffset(ulong address)
        {
            if (address < ReservedSize)
            {
                throw new ArgumentException($"Invalid address 0x{address:x16}");
            }

            return address - ReservedSize;
        }

        /// <summary>
        /// Disposes of resources used by the memory manager.
        /// </summary>
        protected override void Destroy()
        {
            _addressSpace.Dispose();
            _memoryEh.Dispose();
        }

        protected override Memory<byte> GetPhysicalAddressMemory(nuint pa, int size)
            => _backingMemory.GetMemory(pa, size);

        protected override Span<byte> GetPhysicalAddressSpan(nuint pa, int size)
            => _backingMemory.GetSpan(pa, size);

        protected override nuint TranslateVirtualAddressChecked(ulong va)
            => (nuint)GetPhysicalAddressChecked(va);

        protected override nuint TranslateVirtualAddressUnchecked(ulong va)
            => (nuint)GetPhysicalAddressInternal(va);
    }
}
