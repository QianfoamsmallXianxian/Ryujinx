using System;
using System.Collections.Generic;
using System.Reflection;
using System.Threading;
using NUnit.Framework;
using Ryujinx.Common.Configuration;
using Ryujinx.Common.Diagnostics;
using Ryujinx.Graphics.GAL;
using Ryujinx.Graphics.GAL.Multithreading;
using Ryujinx.Graphics.Shader;

namespace Ryujinx.Tests.Graphics
{
    [TestFixture, NonParallelizable]
    public class HugareThreadedRendererTests
    {
        private static void Require(bool value, string message)
        {
            if (!value)
                throw new Exception(message);
        }

        [TestCase(true, true)]
        [TestCase(true, false)]
        [TestCase(false, true)]
        [TestCase(false, false)]
        public void QueuePreservesCommandsAndHandlesInterrupts(bool hugare, bool hysteresis)
        {
            bool previousHysteresis = NeoGraphicsSettings.QueueHysteresisEnabled;
            bool previousHugare = NeoGraphicsSettings.HugareOptimizationsEnabled;
            try
            {
                NeoGraphicsSettings.QueueHysteresisEnabled = hysteresis;
                NeoGraphicsSettings.HugareOptimizationsEnabled = hugare;
                var backend = new HugareFakeRenderer();
                var threaded = new ThreadedRenderer(backend);
                Exception failure = null;
                int interrupts = 0;
                var worker = new Thread(() => threaded.RunLoop(() =>
                {
                    try
                    {
                        IPipeline pipeline = threaded.Pipeline;
                        pipeline.SetBlendState(0, default);
                        pipeline.SetBlendState(0, default);
                        pipeline.SetBlendState(default(AdvancedBlendDescriptor));
                        pipeline.SetBlendState(0, default);

                        var attribs = new VertexAttribDescriptor[2];
                        pipeline.SetVertexAttribs(attribs);
                        pipeline.SetVertexAttribs(attribs);
                        attribs[1] = attribs[1] with { Offset = 16 };
                        pipeline.SetVertexAttribs(attribs);
                        var oversized = new VertexAttribDescriptor[33];
                        pipeline.SetVertexAttribs(oversized);
                        pipeline.SetVertexAttribs(oversized);
                        oversized[32] = oversized[32] with { Offset = 32 };
                        pipeline.SetVertexAttribs(oversized);

                        for (int i = 0; i < 40000; i++)
                        {
                            var face = (FrontFace)(i & 1);
                            pipeline.SetFrontFace(face);
                            pipeline.SetFrontFace(face);
                            if (i >= 30000 && i % 1000 == 0)
                            {
                                threaded.GetCapabilities();
                                backend.InterruptAction(() => interrupts++);
                            }
                        }
                        threaded.GetCapabilities();
                        threaded.FlushThreadedCommands();
                    }
                    catch (Exception ex) { failure = ex; }
                    finally
                    {
                        backend.InterruptAction(() => typeof(ThreadedRenderer).GetField("_running", BindingFlags.NonPublic | BindingFlags.Instance).SetValue(threaded, false));
                    }
                }));
                worker.IsBackground = true;
                worker.Start();
                Require(worker.Join(25000), "Render loop did not stop");
                if (failure != null)
                    throw failure;
                Require(backend.Recording.FrontFaces.Count == (hugare ? 40000 : 80000), "Commands lost or duplicates escaped cache");
                for (int i = 0; i < backend.Recording.FrontFaces.Count; i++)
                    Require(backend.Recording.FrontFaces[i] == (FrontFace)((hugare ? i : i / 2) & 1), "Command order changed");
                Require(backend.Recording.BlendCalls == (hugare ? 2 : 3) && backend.Recording.AdvancedBlendCalls == 1, "Advanced blending failed to invalidate cache");
                Require(backend.Recording.AttribCalls == (hugare ? 5 : 6) && backend.Recording.LastAttribs[32].Offset == 32, "Vertex attribute update was skipped");
                Require(backend.Invokes == 11 && interrupts == 10, "Synchronous command or interruption lost");
                int depth = (int)typeof(ThreadedRenderer).GetField("_commandCount", BindingFlags.NonPublic | BindingFlags.Instance).GetValue(threaded);
                Require(depth == 0, "Queue did not drain");
                threaded.Dispose();
                TestContext.WriteLine($"PASS hysteresis={hysteresis}: 40000 ordered commands, dedup, blend invalidation, attribute tails, 11 invokes, 10 interrupts, drain/shutdown");
            }
            finally { NeoGraphicsSettings.QueueHysteresisEnabled = previousHysteresis;
                NeoGraphicsSettings.HugareOptimizationsEnabled = previousHugare; }
        }
    }
    sealed class HugareFakePipeline : IPipeline
    {
        public List<FrontFace> FrontFaces = new(); public int BlendCalls, AdvancedBlendCalls, AttribCalls; public VertexAttribDescriptor[] LastAttribs;
        public void Barrier() { }
        public void BeginTransformFeedback(PrimitiveTopology topology) { }
        public void ClearBuffer(BufferHandle destination, int offset, int size, uint value) { }
        public void ClearRenderTargetColor(int index, int layer, int layerCount, uint componentMask, ColorF color) { }
        public void ClearRenderTargetDepthStencil(
                    int layer,
                    int layerCount,
                    float depthValue,
                    bool depthMask,
                    int stencilValue,
                    int stencilMask)
        { }
        public void CommandBufferBarrier() { }
        public void CopyBuffer(BufferHandle source, BufferHandle destination, int srcOffset, int dstOffset, int size) { }
        public void DispatchCompute(int groupsX, int groupsY, int groupsZ) { }
        public void Draw(int vertexCount, int instanceCount, int firstVertex, int firstInstance) { }
        public void DrawIndexed(
                    int indexCount,
                    int instanceCount,
                    int firstIndex,
                    int firstVertex,
                    int firstInstance)
        { }
        public void DrawIndexedIndirect(BufferRange indirectBuffer) { }
        public void DrawIndexedIndirectCount(BufferRange indirectBuffer, BufferRange parameterBuffer, int maxDrawCount, int stride) { }
        public void DrawIndirect(BufferRange indirectBuffer) { }
        public void DrawIndirectCount(BufferRange indirectBuffer, BufferRange parameterBuffer, int maxDrawCount, int stride) { }
        public void DrawTexture(ITexture texture, ISampler sampler, Extents2DF srcRegion, Extents2DF dstRegion) { }
        public void EndTransformFeedback() { }
        public void SetAlphaTest(bool enable, float reference, CompareOp op) { }
        public void SetBlendState(AdvancedBlendDescriptor blend) { AdvancedBlendCalls++; }
        public void SetBlendState(int index, BlendDescriptor blend) { BlendCalls++; }
        public void SetDepthBias(PolygonModeMask enables, float factor, float units, float clamp) { }
        public void SetDepthClamp(bool clamp) { }
        public void SetDepthMode(DepthMode mode) { }
        public void SetDepthTest(DepthTestDescriptor depthTest) { }
        public void SetFaceCulling(bool enable, Face face) { }
        public void SetFrontFace(FrontFace frontFace) { FrontFaces.Add(frontFace); Thread.SpinWait(2000); }
        public void SetIndexBuffer(BufferRange buffer, IndexType type) { }
        public void SetImage(ShaderStage stage, int binding, ITexture texture) { }
        public void SetImageArray(ShaderStage stage, int binding, IImageArray array) { }
        public void SetImageArraySeparate(ShaderStage stage, int setIndex, IImageArray array) { }
        public void SetLineParameters(float width, bool smooth) { }
        public void SetLogicOpState(bool enable, LogicalOp op) { }
        public void SetMultisampleState(MultisampleDescriptor multisample) { }
        public void SetPatchParameters(int vertices, ReadOnlySpan<float> defaultOuterLevel, ReadOnlySpan<float> defaultInnerLevel) { }
        public void SetPointParameters(float size, bool isProgramPointSize, bool enablePointSprite, Origin origin) { }
        public void SetPolygonMode(PolygonMode frontMode, PolygonMode backMode) { }
        public void SetPrimitiveRestart(bool enable, int index) { }
        public void SetPrimitiveTopology(PrimitiveTopology topology) { }
        public void SetProgram(IProgram program) { }
        public void SetRasterizerDiscard(bool discard) { }
        public void SetRenderTargetColorMasks(ReadOnlySpan<uint> componentMask) { }
        public void SetRenderTargets(Span<ITexture> colors, ITexture depthStencil) { }
        public void SetScissors(ReadOnlySpan<Rectangle<int>> regions) { }
        public void SetStencilTest(StencilTestDescriptor stencilTest) { }
        public void SetStorageBuffers(ReadOnlySpan<BufferAssignment> buffers) { }
        public void SetTextureAndSampler(ShaderStage stage, int binding, ITexture texture, ISampler sampler) { }
        public void SetTextureArray(ShaderStage stage, int binding, ITextureArray array) { }
        public void SetTextureArraySeparate(ShaderStage stage, int setIndex, ITextureArray array) { }
        public void SetTransformFeedbackBuffers(ReadOnlySpan<BufferRange> buffers) { }
        public void SetUniformBuffers(ReadOnlySpan<BufferAssignment> buffers) { }
        public void SetUserClipDistance(int index, bool enableClip) { }
        public void SetVertexAttribs(ReadOnlySpan<VertexAttribDescriptor> vertexAttribs) { AttribCalls++; LastAttribs = vertexAttribs.ToArray(); }
        public void SetVertexBuffers(ReadOnlySpan<VertexBufferDescriptor> vertexBuffers) { }
        public void SetViewports(ReadOnlySpan<Viewport> viewports) { }
        public void TextureBarrier() { }
        public void TextureBarrierTiled() { }
        public bool TryHostConditionalRendering(ICounterEvent value, ulong compare, bool isEqual) { return false; }
        public bool TryHostConditionalRendering(ICounterEvent value, ICounterEvent compare, bool isEqual) { return false; }
        public void EndHostConditionalRendering() { }
    }
    sealed class HugareFakeRenderer : IRenderer
    {
        public event EventHandler<ScreenCaptureImageInfo> ScreenCaptured; public HugareFakePipeline Recording = new(); public IPipeline Pipeline => Recording; public IWindow Window => null; public bool PreferThreading => true; public uint ProgramCount => 0; public Action<Action> InterruptAction; public int Invokes; public void Dispose() { }
        public void BackgroundContextAction(Action action, bool alwaysBackground = false) { }
        public BufferHandle CreateBuffer(int size, BufferAccess access = BufferAccess.Default) { return default; }
        public BufferHandle CreateBuffer(nint pointer, int size) { return default; }
        public BufferHandle CreateBufferSparse(ReadOnlySpan<BufferRange> storageBuffers) { return default; }
        public IImageArray CreateImageArray(int size, bool isBuffer) { return default; }
        public IProgram CreateProgram(ShaderSource[] shaders, ShaderInfo info) { return default; }
        public ISampler CreateSampler(SamplerCreateInfo info) { return default; }
        public ITexture CreateTexture(TextureCreateInfo info) { return default; }
        public ITextureArray CreateTextureArray(int size, bool isBuffer) { return default; }
        public bool PrepareHostMapping(nint address, ulong size) { return default; }
        public void CreateSync(ulong id, bool strict) { }
        public void DeleteBuffer(BufferHandle buffer) { }
        public PinnedSpan<byte> GetBufferData(BufferHandle buffer, int offset, int size) { return default; }
        public Capabilities GetCapabilities() { Invokes++; return default; }
        public ulong GetCurrentSync() { return default; }
        public HardwareInfo GetHardwareInfo() { return default; }
        public IProgram LoadProgramBinary(byte[] programBinary, bool hasFragmentShader, ShaderInfo info) { return default; }
        public void SetBufferData(BufferHandle buffer, int offset, ReadOnlySpan<byte> data) { }
        public void UpdateCounters() { }
        public void PreFrame() { }
        public ICounterEvent ReportCounter(CounterType type, EventHandler<ulong> resultHandler, float divisor, bool hostReserved) { return default; }
        public void ResetCounter(CounterType type) { }
        public void WaitSync(ulong id) { }
        public void Initialize(GraphicsDebugLevel logLevel) { }
        public void SetInterruptAction(Action<Action> interruptAction) { InterruptAction = interruptAction; }
        public void Screenshot() { }
    }
}
