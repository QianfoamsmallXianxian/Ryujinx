using System;

namespace Ryujinx.Graphics.Vulkan
{
    internal static class TimelineBatchEncoding
    {
        internal const int MaxItems = 16;
        internal const int MaxSemaphores = 128;
        internal static bool CanAppend(bool timeline, bool isSubmit, int count, int semaphoreCount) =>
            timeline && isSubmit && count < MaxItems && semaphoreCount <= MaxSemaphores;

        // Submit() appends this queue's timeline semaphore after the binary signals.
        // Zero is valid for ignored binary values, never for the timeline signal.
        internal static void WriteValues(Span<ulong> waits, Span<ulong> signals, ulong value)
        {
            if (value == 0 || signals.IsEmpty)
            {
                throw new ArgumentException("A timeline submit requires a nonzero signal.");
            }
            waits.Clear();
            signals.Clear();
            signals[^1] = value;
        }
    }
}
