using System.Runtime.CompilerServices;
using System.Threading;

namespace Ryujinx.Graphics.Vulkan
{
    /// <summary>
    /// Shares timeline completion knowledge across resource/range waits.
    /// Once a value has been proven complete for a submission worker, every lower value on
    /// the same monotonic queue timeline is complete too and does not need another Vulkan wait.
    /// </summary>
    internal static class TimelineCompletionGenerationCache
    {
        private sealed class CompletionState
        {
            public long CompletedValue;
        }

        private static readonly ConditionalWeakTable<VulkanSubmissionWorker, CompletionState> _states = new();

        public static bool IsKnownComplete(VulkanSubmissionWorker worker, ulong value)
        {
            if (value == 0 || value > (ulong)long.MaxValue)
            {
                return false;
            }

            CompletionState state = _states.GetValue(worker, static _ => new CompletionState());
            return value <= (ulong)Volatile.Read(ref state.CompletedValue);
        }

        public static void MarkCompleted(VulkanSubmissionWorker worker, ulong value)
        {
            if (value == 0 || value > (ulong)long.MaxValue)
            {
                return;
            }

            CompletionState state = _states.GetValue(worker, static _ => new CompletionState());
            long candidate = (long)value;
            long current = Volatile.Read(ref state.CompletedValue);

            while (candidate > current)
            {
                long observed = Interlocked.CompareExchange(ref state.CompletedValue, candidate, current);

                if (observed == current)
                {
                    return;
                }

                current = observed;
            }
        }
    }
}
