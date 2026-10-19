using Ryujinx.Common.Diagnostics;
using Ryujinx.Common.Logging;
using System.Diagnostics;
using System.Threading;

namespace Ryujinx.Graphics.Vulkan
{
    internal enum LogicalSyncWaitOutcome
    {
        Watermark,
        Cleaned,
        Signalled,
        ActualSignalled,
        TimedOut,
        Missing,
    }

    internal static class LogicalSyncWaitTelemetry
    {
        private static readonly long SampleIntervalTicks = Stopwatch.Frequency * 5;
        private static long _nextSampleTimestamp = Stopwatch.GetTimestamp() + SampleIntervalTicks;
        private static long _requested;
        private static long _watermark;
        private static long _cleaned;
        private static long _signalled;
        private static long _actualSignalled;
        private static long _timedOut;
        private static long _missing;
        private static long _flushInterrupts;

        public static void Record(LogicalSyncWaitOutcome outcome, bool flushInterrupt = false)
        {
            if (!GezinjinxPerformanceMode.DetailedTelemetry)
            {
                return;
            }

            Interlocked.Increment(ref _requested);

            switch (outcome)
            {
                case LogicalSyncWaitOutcome.Watermark:
                    Interlocked.Increment(ref _watermark);
                    break;
                case LogicalSyncWaitOutcome.Cleaned:
                    Interlocked.Increment(ref _cleaned);
                    break;
                case LogicalSyncWaitOutcome.Signalled:
                    Interlocked.Increment(ref _signalled);
                    break;
                case LogicalSyncWaitOutcome.ActualSignalled:
                    Interlocked.Increment(ref _actualSignalled);
                    break;
                case LogicalSyncWaitOutcome.TimedOut:
                    Interlocked.Increment(ref _timedOut);
                    break;
                case LogicalSyncWaitOutcome.Missing:
                    Interlocked.Increment(ref _missing);
                    break;
            }

            if (flushInterrupt)
            {
                Interlocked.Increment(ref _flushInterrupts);
            }

            TryLog();
        }

        private static void TryLog()
        {
            long now = Stopwatch.GetTimestamp();
            long next = Volatile.Read(ref _nextSampleTimestamp);

            if (now < next ||
                Interlocked.CompareExchange(ref _nextSampleTimestamp, now + SampleIntervalTicks, next) != next)
            {
                return;
            }

            long requested = Interlocked.Exchange(ref _requested, 0);
            long watermark = Interlocked.Exchange(ref _watermark, 0);
            long cleaned = Interlocked.Exchange(ref _cleaned, 0);
            long signalled = Interlocked.Exchange(ref _signalled, 0);
            long actualSignalled = Interlocked.Exchange(ref _actualSignalled, 0);
            long timedOut = Interlocked.Exchange(ref _timedOut, 0);
            long missing = Interlocked.Exchange(ref _missing, 0);
            long flushInterrupts = Interlocked.Exchange(ref _flushInterrupts, 0);
            long reused = watermark + cleaned + signalled;
            long actual = actualSignalled + timedOut;

            Logger.Info?.Print(
                LogClass.Gpu,
                $"[GEZINJINX-V0.0.9-R6] Logical sync reuse: requested={requested} actual={actual} reused={reused} watermark={watermark} signalled={signalled} cleaned={cleaned} timeouts={timedOut} missing={missing} flushInterrupts={flushInterrupts}");
        }
    }
}
