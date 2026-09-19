using Ryujinx.Common;
using Ryujinx.Common.Memory;
using Silk.NET.Vulkan;
using System;
using System.Diagnostics;

namespace Ryujinx.Graphics.Vulkan
{
    /// <summary>
    /// Holder for multiple host GPU fences.
    /// </summary>
    class MultiFenceHolder
    {
        public static readonly ObjectPool<FenceHolder[]> FencePool = new(() => new FenceHolder[CommandBufferPool.MaxCommandBuffers]);
        
        private const int BufferUsageTrackingGranularity = 4096;

        public FenceHolder[] Fences { get; }
        private readonly BufferUsageBitmap _bufferUsageBitmap;

        /// <summary>
        /// Creates a new instance of the multiple fence holder.
        /// </summary>
        public MultiFenceHolder()
        {
            Fences = FencePool.Allocate();
        }

        /// <summary>
        /// Creates a new instance of the multiple fence holder, with a given buffer size in mind.
        /// </summary>
        /// <param name="size">Size of the buffer</param>
        public MultiFenceHolder(int size)
        {
            Fences = FencePool.Allocate();
            _bufferUsageBitmap = new BufferUsageBitmap(size, BufferUsageTrackingGranularity);
        }

        /// <summary>
        /// Adds read/write buffer usage information to the uses list.
        /// </summary>
        /// <param name="cbIndex">Index of the command buffer where the buffer is used</param>
        /// <param name="offset">Offset of the buffer being used</param>
        /// <param name="size">Size of the buffer region being used, in bytes</param>
        /// <param name="write">Whether the access is a write or not</param>
        public void AddBufferUse(int cbIndex, int offset, int size, bool write)
        {
            _bufferUsageBitmap.Add(cbIndex, offset, size, false);

            if (write)
            {
                _bufferUsageBitmap.Add(cbIndex, offset, size, true);
            }
        }

        /// <summary>
        /// Removes all buffer usage information for a given command buffer.
        /// </summary>
        /// <param name="cbIndex">Index of the command buffer where the buffer is used</param>
        public void RemoveBufferUses(int cbIndex)
        {
            _bufferUsageBitmap?.Clear(cbIndex);
        }

        /// <summary>
        /// Checks if a given range of a buffer is being used by a command buffer still being processed by the GPU.
        /// </summary>
        /// <param name="cbIndex">Index of the command buffer where the buffer is used</param>
        /// <param name="offset">Offset of the buffer being used</param>
        /// <param name="size">Size of the buffer region being used, in bytes</param>
        /// <returns>True if in use, false otherwise</returns>
        public bool IsBufferRangeInUse(int cbIndex, int offset, int size)
        {
            return _bufferUsageBitmap.OverlapsWith(cbIndex, offset, size);
        }

        /// <summary>
        /// Checks if a given range of a buffer is being used by any command buffer still being processed by the GPU.
        /// </summary>
        /// <param name="offset">Offset of the buffer being used</param>
        /// <param name="size">Size of the buffer region being used, in bytes</param>
        /// <param name="write">True if only write usages should count</param>
        /// <returns>True if in use, false otherwise</returns>
        public bool IsBufferRangeInUse(int offset, int size, bool write)
        {
            return _bufferUsageBitmap.OverlapsWith(offset, size, write);
        }

        /// <summary>
        /// Adds a fence to the holder.
        /// </summary>
        /// <param name="cbIndex">Command buffer index of the command buffer that owns the fence</param>
        /// <param name="fence">Fence to be added</param>
        /// <returns>True if the command buffer's previous fence value was null</returns>
        public bool AddFence(int cbIndex, FenceHolder fence)
        {
            ref FenceHolder fenceRef = ref Fences[cbIndex];

            if (fenceRef == null)
            {
                fenceRef = fence;
                return true;
            }

            return false;
        }

        /// <summary>
        /// Removes a fence from the holder.
        /// </summary>
        /// <param name="cbIndex">Command buffer index of the command buffer that owns the fence</param>
        public void RemoveFence(int cbIndex)
        {
            Fences[cbIndex] = null;
        }

        /// <summary>
        /// Determines if a fence referenced on the given command buffer.
        /// </summary>
        /// <param name="cbIndex">Index of the command buffer to check if it's used</param>
        /// <returns>True if referenced, false otherwise</returns>
        public bool HasFence(int cbIndex)
        {
            return Fences[cbIndex] != null;
        }

        /// <summary>
        /// Wait until all the fences on the holder are signaled.
        /// </summary>
        /// <param name="api">Vulkan API instance</param>
        /// <param name="device">GPU device that the fences belongs to</param>
        public void WaitForFences(Vk api, Device device)
        {
            WaitForFencesImpl(api, device, 0, 0, false, 0UL);
        }

        /// <summary>
        /// Wait until all the fences on the holder with buffer uses overlapping the specified range are signaled.
        /// </summary>
        /// <param name="api">Vulkan API instance</param>
        /// <param name="device">GPU device that the fences belongs to</param>
        /// <param name="offset">Start offset of the buffer range</param>
        /// <param name="size">Size of the buffer range in bytes</param>
        public void WaitForFences(Vk api, Device device, int offset, int size)
        {
            WaitForFencesImpl(api, device, offset, size, false, 0UL);
        }

        /// <summary>
        /// Wait until all the fences on the holder are signaled, or the timeout expires.
        /// </summary>
        /// <param name="api">Vulkan API instance</param>
        /// <param name="device">GPU device that the fences belongs to</param>
        /// <param name="timeout">Timeout in nanoseconds</param>
        /// <returns>True if all fences were signaled, false otherwise</returns>
        public bool WaitForFences(Vk api, Device device, ulong timeout)
        {
            return WaitForFencesImpl(api, device, 0, 0, true, timeout);
        }

        /// <summary>
        /// Wait until all the fences on the holder with buffer uses overlapping the specified range are signaled.
        /// </summary>
        /// <param name="api">Vulkan API instance</param>
        /// <param name="device">GPU device that the fences belongs to</param>
        /// <param name="offset">Start offset of the buffer range</param>
        /// <param name="size">Size of the buffer range in bytes</param>
        /// <param name="hasTimeout">Indicates if <paramref name="timeout"/> should be used</param>
        /// <param name="timeout">Timeout in nanoseconds</param>
        /// <returns>True if all fences were signaled before the timeout expired, false otherwise</returns>
        private bool WaitForFencesImpl(Vk api, Device device, int offset, int size, bool hasTimeout, ulong timeout)
        {
            using SpanOwner<FenceHolder> fenceHoldersOwner = SpanOwner<FenceHolder>.Rent(CommandBufferPool.MaxCommandBuffers);
            using SpanOwner<FenceHolder> timelineHoldersOwner = SpanOwner<FenceHolder>.Rent(CommandBufferPool.MaxCommandBuffers);
            using SpanOwner<FenceHolder> timelineWaitHoldersOwner = SpanOwner<FenceHolder>.Rent(CommandBufferPool.MaxCommandBuffers);
            using SpanOwner<FenceHolder> pendingTimelineHoldersOwner = SpanOwner<FenceHolder>.Rent(CommandBufferPool.MaxCommandBuffers);
            Span<FenceHolder> fenceHolders = fenceHoldersOwner.Span;
            Span<FenceHolder> timelineHolders = timelineHoldersOwner.Span;
            Span<FenceHolder> timelineWaitHolders = timelineWaitHoldersOwner.Span;
            Span<FenceHolder> pendingTimelineHolders = pendingTimelineHoldersOwner.Span;

            int count = size != 0 ? GetOverlappingFences(fenceHolders, offset, size) : GetFences(fenceHolders);

            // NEOV2 Etapa 2 (portado do Gezinjinx): uma consulta sem espera nao pode
            // bloquear aguardando o worker enviar o command buffer.
            if (hasTimeout && timeout == 0)
            {
                for (int i = 0; i < count; i++)
                {
                    if (!fenceHolders[i].Submission.IsCompleted)
                    {
                        return false;
                    }
                }
            }

            Span<Fence> fences = stackalloc Fence[count];
            int fenceCount = 0;
            int timelineCount = 0;

            for (int i = 0; i < count; i++)
            {
                FenceHolder holder = fenceHolders[i];

                if (holder.IsTimeline)
                {
                    if (holder.TryAddReference())
                    {
                        timelineHolders[timelineCount++] = holder;
                    }
                }
                else if (holder.TryGet(out Fence fence))
                {
                    fences[fenceCount] = fence;
                    fenceHolders[fenceCount] = holder;
                    fenceCount++;
                }
            }

            // Os valores do timeline sao monotonicos na ordem FIFO de cada VulkanSubmissionWorker.
            // Esperar o maior valor de um worker prova que todos os menores ja terminaram.
            // Valor zero indica que o holder ainda nao chegou ao Submit e recebe espera individual.
            int timelineWaitCount = 0;
            int pendingTimelineCount = 0;
            int knownCompleteCount = 0;

            for (int i = 0; i < timelineCount; i++)
            {
                FenceHolder holder = timelineHolders[i];
                ulong timelineValue = holder.TimelineValue;

                if (timelineValue == 0)
                {
                    pendingTimelineHolders[pendingTimelineCount++] = holder;
                    continue;
                }

                if (holder.Submission.IsCompleted &&
                    TimelineCompletionGenerationCache.IsKnownComplete(holder.SubmissionWorker, timelineValue))
                {
                    holder.Submission.ThrowIfFaulted();
                    holder.SubmissionWorker.ThrowIfFaulted();
                    knownCompleteCount++;
                    continue;
                }

                int existingIndex = -1;

                for (int j = 0; j < timelineWaitCount; j++)
                {
                    if (ReferenceEquals(timelineWaitHolders[j].SubmissionWorker, holder.SubmissionWorker))
                    {
                        existingIndex = j;
                        break;
                    }
                }

                if (existingIndex < 0)
                {
                    timelineWaitHolders[timelineWaitCount++] = holder;
                }
                else if (timelineValue > timelineWaitHolders[existingIndex].TimelineValue)
                {
                    timelineWaitHolders[existingIndex] = holder;
                }
            }

            TimelineWaitCoalescingTelemetry.Record(timelineCount, timelineWaitCount + pendingTimelineCount, pendingTimelineCount, knownCompleteCount);

            bool signaled = true;
            long waitStart = hasTimeout ? Stopwatch.GetTimestamp() : 0;

            ulong RemainingTimeout()
            {
                if (!hasTimeout)
                {
                    return ulong.MaxValue;
                }

                double elapsedNanoseconds =
                    (Stopwatch.GetTimestamp() - waitStart) * (1_000_000_000d / Stopwatch.Frequency);

                if (elapsedNanoseconds >= timeout)
                {
                    return 0;
                }

                return timeout - (ulong)elapsedNanoseconds;
            }

            try
            {
                if (hasTimeout)
                {
                    for (int i = 0; i < timelineWaitCount; i++)
                    {
                        FenceHolder holder = timelineWaitHolders[i];

                        if (!holder.Wait(RemainingTimeout()))
                        {
                            signaled = false;
                            break;
                        }

                        TimelineCompletionGenerationCache.MarkCompleted(holder.SubmissionWorker, holder.TimelineValue);
                    }

                    if (signaled)
                    {
                        for (int i = 0; i < pendingTimelineCount; i++)
                        {
                            FenceHolder holder = pendingTimelineHolders[i];

                            if (!holder.Wait(RemainingTimeout()))
                            {
                                signaled = false;
                                break;
                            }

                            TimelineCompletionGenerationCache.MarkCompleted(holder.SubmissionWorker, holder.TimelineValue);
                        }
                    }

                    if (signaled && fenceCount != 0)
                    {
                        signaled = FenceHelper.AllSignaled(api, device, fences[..fenceCount], RemainingTimeout());
                    }
                }
                else
                {
                    for (int i = 0; i < timelineWaitCount; i++)
                    {
                        FenceHolder holder = timelineWaitHolders[i];
                        holder.Wait();
                        TimelineCompletionGenerationCache.MarkCompleted(holder.SubmissionWorker, holder.TimelineValue);
                    }

                    for (int i = 0; i < pendingTimelineCount; i++)
                    {
                        FenceHolder holder = pendingTimelineHolders[i];
                        holder.Wait();
                        TimelineCompletionGenerationCache.MarkCompleted(holder.SubmissionWorker, holder.TimelineValue);
                    }

                    if (fenceCount != 0)
                    {
                        FenceHelper.WaitAllIndefinitely(api, device, fences[..fenceCount]);
                    }
                }
            }
            finally
            {
                for (int i = 0; i < timelineCount; i++)
                {
                    timelineHolders[i].Put();
                }

                for (int i = 0; i < fenceCount; i++)
                {
                    fenceHolders[i].PutLock();
                }
            }

            return signaled;
        }

        /// <summary>
        /// Gets fences to wait for.
        /// </summary>
        /// <param name="storage">Span to store fences in</param>
        /// <returns>Number of fences placed in storage</returns>
        private int GetFences(Span<FenceHolder> storage)
        {
            int count = 0;

            for (int i = 0; i < Fences.Length; i++)
            {
                FenceHolder fence = Fences[i];

                if (fence != null)
                {
                    storage[count++] = fence;
                }
            }

            return count;
        }

        /// <summary>
        /// Gets fences to wait for use of a given buffer region.
        /// </summary>
        /// <param name="storage">Span to store overlapping fences in</param>
        /// <param name="offset">Offset of the range</param>
        /// <param name="size">Size of the range in bytes</param>
        /// <returns>Number of fences for the specified region placed in storage</returns>
        private int GetOverlappingFences(Span<FenceHolder> storage, int offset, int size)
        {
            int count = 0;

            for (int i = 0; i < Fences.Length; i++)
            {
                FenceHolder fence = Fences[i];

                if (fence != null && _bufferUsageBitmap.OverlapsWith(i, offset, size))
                {
                    storage[count++] = fence;
                }
            }

            return count;
        }
    }
}
