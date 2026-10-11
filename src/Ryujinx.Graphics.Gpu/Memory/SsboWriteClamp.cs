using System;

namespace Ryujinx.Graphics.Gpu.Memory
{
    // NEOV2-SSBOCLAMP: MARVEL Ultimate Alliance 3 (010060700AC50000) flicker and corruption fix.
    //
    // The geometry shader 0x100719F30 (a passthrough geometry shader that the VTG runs as compute) binds its storage buffer 1
    // with the whole size of the driver descriptor, an 8 MB window that slides by about 0x2C28 bytes on every draw, but it writes
    // only about 0x2C28 bytes per draw. Marking the whole 8 MB window as modified by the GPU also covers pages that the guest CPU
    // rewrites every frame (dynamic vertex data in 1 MB blocks). For those pages the CPU write is excluded from the guest to host
    // upload (BufferModifiedRangeList.ExcludeModifiedRegions) and the GPU reads old vertices.
    //
    // The limit below only affects the GPU write tracking (SignalModified). The binding sent to the shader keeps the full size,
    // and storage buffer 0 (the 96 KB counters that the CPU reads) stays fully tracked.
    static class SsboWriteClamp
    {
        private const string TitleId = "010060700AC50000";
        private const ulong GeometryShaderOffset = 0x719F30;
        private const ulong ShaderBase = 0x100000000UL;

        /// <summary>
        /// Maximum number of bytes tracked as written by the GPU for the clamped binding: 64 KB, about six times the
        /// ~0x2C28 bytes that the geometry shader writes per draw.
        /// </summary>
        public const ulong WriteTrackingLimit = 0x10000;

        /// <summary>True while the draw being committed uses the target geometry shader (GPU thread only).</summary>
        public static bool TargetDraw;

        /// <summary>
        /// Updates <see cref="TargetDraw"/> for the draw about to be committed.
        /// </summary>
        /// <param name="usesGeometryAsCompute">True if the draw runs a geometry shader as compute</param>
        /// <param name="shaderBaseAddress">Shader base address of the draw</param>
        /// <param name="geometryOffset">Offset of the geometry shader from the shader base address</param>
        public static void Update(bool usesGeometryAsCompute, ulong shaderBaseAddress, uint geometryOffset)
        {
            TargetDraw = usesGeometryAsCompute &&
                shaderBaseAddress == ShaderBase &&
                geometryOffset == GeometryShaderOffset &&
                string.Equals(GraphicsConfig.TitleId, TitleId, StringComparison.OrdinalIgnoreCase);
        }
    }
}
