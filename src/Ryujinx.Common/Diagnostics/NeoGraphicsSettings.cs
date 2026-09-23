namespace Ryujinx.Common.Diagnostics
{
    public enum DrawFlushMode
    {
        /// <summary>Current behavior: Turnip keeps the 4 draw flushes per frame guard, other drivers stay uncapped.</summary>
        Default = 0,

        /// <summary>At most one draw flush per frame, on every driver. All other filters stay in place.</summary>
        Reduced = 1,

        /// <summary>ShouldFlushDraw never forces a flush. Attachment/query/sync/barrier flushes stay active.</summary>
        Disabled = 2,
    }

    public enum BufferReadbackMode
    {
        /// <summary>Current behavior: the CPU waits for the GPU sync before the buffer data is read back.</summary>
        Default = 0,

        /// <summary>The coherency wait is skipped. The readback, the range bookkeeping and every other sync stay in place.</summary>
        Relaxed = 1,
    }

    /// <summary>
    /// Strict host sync experiment mode.
    /// </summary>
    public enum StrictSyncMode
    {
        Default = 0,
        Immediate = 1,
    }

    /// <summary>
    /// Effective (global + per game) experimental graphics settings resolved before the renderer starts.
    /// </summary>
    public static class NeoGraphicsSettings
    {
        /// <summary>Value the renderer uses.</summary>
        public static DrawFlushMode DrawFlushMode { get; set; } = DrawFlushMode.Default;

        /// <summary>Global value, kept only for the startup log line.</summary>
        public static DrawFlushMode DrawFlushGlobalMode { get; set; } = DrawFlushMode.Default;

        /// <summary>Per game override as selected by the user, or null when "use global" is selected. Log only.</summary>
        public static DrawFlushMode? DrawFlushGameOverride { get; set; }

        /// <summary>Value the GPU buffer cache uses.</summary>
        public static BufferReadbackMode BufferReadbackMode { get; set; } = BufferReadbackMode.Default;

        /// <summary>Global value, kept only for the startup log line.</summary>
        public static BufferReadbackMode BufferReadbackGlobalMode { get; set; } = BufferReadbackMode.Default;

        /// <summary>Per game override as selected by the user, or null when "use global" is selected. Log only.</summary>
        public static BufferReadbackMode? BufferReadbackGameOverride { get; set; }

        /// <summary>Value the Vulkan sync manager uses.</summary>
        public static StrictSyncMode StrictSyncMode { get; set; } = StrictSyncMode.Default;

        /// <summary>Global value, kept only for the startup log line.</summary>
        public static StrictSyncMode StrictSyncGlobalMode { get; set; } = StrictSyncMode.Default;

        /// <summary>Per game override as selected by the user, or null when "use global" is selected. Log only.</summary>
        public static StrictSyncMode? StrictSyncGameOverride { get; set; }
    }
}