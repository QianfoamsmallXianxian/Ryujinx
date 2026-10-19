using Silk.NET.Vulkan;
using System;
using System.Collections.Generic;
using VkBuffer = Silk.NET.Vulkan.Buffer;

namespace Ryujinx.Graphics.Vulkan.FrameGen
{
    // NEOV2-FRAMEGEN1: cadeia de passes compute do LSFG (mipmaps, alpha, beta, gamma, delta, generate).
    // Referencia de comportamento: Eden (lsfg_*.cpp). Reescrita com UM slot de geracao (timestamp 0,5),
    // porque o Neo gera no maximo um quadro intermediario por quadro real.

    internal sealed unsafe class LsfgImage : IDisposable
    {
        private readonly Vk _api;
        private readonly Device _device;
        private readonly MemoryAllocation _allocation;

        public readonly Image Image;
        public readonly ImageView View;
        public readonly Extent2D Extent;
        public ImageLayout Layout = ImageLayout.Undefined;

        public LsfgImage(VulkanRenderer gd, Device device, uint width, uint height, Format format)
        {
            _api = gd.Api;
            _device = device;
            Extent = new Extent2D(Math.Max(1, width), Math.Max(1, height));

            ImageCreateInfo ci = new()
            {
                SType = StructureType.ImageCreateInfo,
                ImageType = ImageType.Type2D,
                Format = format,
                Extent = new Extent3D(Extent.Width, Extent.Height, 1),
                MipLevels = 1,
                ArrayLayers = 1,
                Samples = SampleCountFlags.Count1Bit,
                Tiling = ImageTiling.Optimal,
                Usage = ImageUsageFlags.TransferSrcBit | ImageUsageFlags.TransferDstBit |
                        ImageUsageFlags.StorageBit | ImageUsageFlags.SampledBit,
                SharingMode = SharingMode.Exclusive,
                InitialLayout = ImageLayout.Undefined,
            };

            _api.CreateImage(device, in ci, null, out Image).ThrowOnError();
            _api.GetImageMemoryRequirements(device, Image, out MemoryRequirements req);
            _allocation = gd.MemoryAllocator.AllocateDeviceMemory(req, MemoryPropertyFlags.DeviceLocalBit);
            if (_allocation.Memory.Handle == 0)
            {
                _api.DestroyImage(device, Image, null);
                throw new InvalidOperationException("LSFG: sem memoria para imagem");
            }

            _api.BindImageMemory(device, Image, _allocation.Memory, _allocation.Offset).ThrowOnError();

            ImageViewCreateInfo vi = new()
            {
                SType = StructureType.ImageViewCreateInfo,
                Image = Image,
                ViewType = ImageViewType.Type2D,
                Format = format,
                SubresourceRange = new ImageSubresourceRange(ImageAspectFlags.ColorBit, 0, 1, 0, 1),
            };
            _api.CreateImageView(device, in vi, null, out View).ThrowOnError();
        }

        public void Dispose()
        {
            _api.DestroyImageView(_device, View, null);
            _api.DestroyImage(_device, Image, null);
            _allocation.Dispose();
        }
    }

    internal sealed unsafe class LsfgBarriers
    {
        private readonly Vk _api;
        private readonly CommandBuffer _cb;
        private readonly List<ImageMemoryBarrier> _list = new();

        public LsfgBarriers(Vk api, CommandBuffer cb)
        {
            _api = api;
            _cb = cb;
        }

        private LsfgBarriers Push(LsfgImage image, AccessFlags src, AccessFlags dst)
        {
            if (image == null)
            {
                return this;
            }

            _list.Add(new ImageMemoryBarrier
            {
                SType = StructureType.ImageMemoryBarrier,
                SrcAccessMask = src,
                DstAccessMask = dst,
                OldLayout = image.Layout,
                NewLayout = ImageLayout.General,
                SrcQueueFamilyIndex = Vk.QueueFamilyIgnored,
                DstQueueFamilyIndex = Vk.QueueFamilyIgnored,
                Image = image.Image,
                SubresourceRange = new ImageSubresourceRange(ImageAspectFlags.ColorBit, 0, 1, 0, 1),
            });
            image.Layout = ImageLayout.General;
            return this;
        }

        public LsfgBarriers WriteToRead(LsfgImage image) => Push(image, AccessFlags.ShaderWriteBit, AccessFlags.ShaderReadBit);

        public LsfgBarriers ReadToWrite(LsfgImage image) => Push(image, AccessFlags.ShaderReadBit, AccessFlags.ShaderWriteBit);

        public LsfgBarriers WriteToRead(LsfgImage[] images)
        {
            foreach (LsfgImage i in images)
            {
                WriteToRead(i);
            }

            return this;
        }

        public LsfgBarriers ReadToWrite(LsfgImage[] images)
        {
            foreach (LsfgImage i in images)
            {
                ReadToWrite(i);
            }

            return this;
        }

        public void Build()
        {
            if (_list.Count == 0)
            {
                return;
            }

            ImageMemoryBarrier[] arr = _list.ToArray();
            fixed (ImageMemoryBarrier* p = arr)
            {
                _api.CmdPipelineBarrier(_cb, PipelineStageFlags.ComputeShaderBit, PipelineStageFlags.ComputeShaderBit,
                    0, 0, null, 0, null, (uint)arr.Length, p);
            }

            _list.Clear();
        }
    }

    internal sealed unsafe class LsfgPass : IDisposable
    {
        private readonly Vk _api;
        private readonly Device _device;
        public readonly DescriptorSetLayout SetLayout;
        private readonly PipelineLayout _layout;
        private readonly Pipeline _pipeline;

        // Ordem fixa em todos os passes: UBO, samplers, imagens amostradas, imagens de storage.
        public LsfgPass(Vk api, Device device, uint[] spirv, int ubo, int samplers, int sampled, int storage)
        {
            _api = api;
            _device = device;

            List<DescriptorSetLayoutBinding> bindings = new();
            uint b = 0;
            void Add(int n, DescriptorType t)
            {
                for (int i = 0; i < n; i++)
                {
                    bindings.Add(new DescriptorSetLayoutBinding
                    {
                        Binding = b++,
                        DescriptorType = t,
                        DescriptorCount = 1,
                        StageFlags = ShaderStageFlags.ComputeBit,
                    });
                }
            }

            Add(ubo, DescriptorType.UniformBuffer);
            Add(samplers, DescriptorType.Sampler);
            Add(sampled, DescriptorType.SampledImage);
            Add(storage, DescriptorType.StorageImage);

            DescriptorSetLayoutBinding[] arr = bindings.ToArray();
            fixed (DescriptorSetLayoutBinding* pb = arr)
            {
                DescriptorSetLayoutCreateInfo dci = new()
                {
                    SType = StructureType.DescriptorSetLayoutCreateInfo,
                    BindingCount = (uint)arr.Length,
                    PBindings = pb,
                };
                _api.CreateDescriptorSetLayout(device, in dci, null, out SetLayout).ThrowOnError();
            }

            DescriptorSetLayout setLayout = SetLayout;
            PipelineLayoutCreateInfo pli = new()
            {
                SType = StructureType.PipelineLayoutCreateInfo,
                SetLayoutCount = 1,
                PSetLayouts = &setLayout,
            };
            _api.CreatePipelineLayout(device, in pli, null, out _layout).ThrowOnError();

            ShaderModule module;
            fixed (uint* pc = spirv)
            {
                ShaderModuleCreateInfo smi = new()
                {
                    SType = StructureType.ShaderModuleCreateInfo,
                    CodeSize = (nuint)(spirv.Length * 4),
                    PCode = pc,
                };
                _api.CreateShaderModule(device, in smi, null, out module).ThrowOnError();
            }

            try
            {
                byte* name = stackalloc byte[] { (byte)'m', (byte)'a', (byte)'i', (byte)'n', 0 };
                ComputePipelineCreateInfo pi = new()
                {
                    SType = StructureType.ComputePipelineCreateInfo,
                    Layout = _layout,
                    Stage = new PipelineShaderStageCreateInfo
                    {
                        SType = StructureType.PipelineShaderStageCreateInfo,
                        Stage = ShaderStageFlags.ComputeBit,
                        Module = module,
                        PName = name,
                    },
                };
                _api.CreateComputePipelines(device, default, 1, in pi, null, out _pipeline).ThrowOnError();
            }
            finally
            {
                _api.DestroyShaderModule(device, module, null);
            }
        }

        public void BindPipeline(CommandBuffer cb) => _api.CmdBindPipeline(cb, PipelineBindPoint.Compute, _pipeline);

        public void BindSet(CommandBuffer cb, DescriptorSet set) =>
            _api.CmdBindDescriptorSets(cb, PipelineBindPoint.Compute, _layout, 0, 1, in set, 0, null);

        public void Bind(CommandBuffer cb, DescriptorSet set)
        {
            BindPipeline(cb);
            BindSet(cb, set);
        }

        public void Dispose()
        {
            _api.DestroyPipeline(_device, _pipeline, null);
            _api.DestroyPipelineLayout(_device, _layout, null);
            _api.DestroyDescriptorSetLayout(_device, SetLayout, null);
        }
    }

    internal sealed unsafe class LsfgChain : IDisposable
    {
        private const int Levels = 7;
        private const int History = 3;
        private const int DeltaFirstLevel = 4;
        private const int DeltaInstances = 3;
        private const float Timestamp = 0.5f;
        internal const int Slots = 7;        // numero impar inclui o ponto exato entre os quadros
        internal const int OutputRing = 3;   // texturas de saida simultaneas
        internal const int MidpointSlot = Slots / 2;

        internal static float SlotFraction(int slot) => (slot + 0.5f) / Slots;
        internal static int SlotFor(double fraction) => Math.Clamp((int)(fraction * Slots), 0, Slots - 1);

        private readonly VulkanRenderer _gd;
        private readonly Vk _api;
        private readonly Device _device;

        private readonly List<IDisposable> _owned = new();
        private readonly List<(VkBuffer Buffer, MemoryAllocation Allocation)> _buffers = new();
        private readonly Dictionary<ulong, VkBuffer> _constants = new();
        private readonly Dictionary<ulong, Sampler> _samplers = new();
        private DescriptorPool _pool;

        private readonly float _flowScale;
        private readonly LsfgImage _dummy;
        private readonly LsfgImage[] _frames = new LsfgImage[2];
        private bool _dummyReady;

        // mipmaps
        private readonly LsfgPass _mipPass;
        private readonly LsfgImage[] _mipOut = new LsfgImage[Levels];
        private readonly DescriptorSet[] _mipSets = new DescriptorSet[2];
        private readonly Extent2D _flowExtent;

        // alpha
        private readonly LsfgPass[] _alphaPass = new LsfgPass[4];
        private sealed class Alpha
        {
            public LsfgImage Temp1, Temp2;
            public LsfgImage[] Temp3 = new LsfgImage[2];
            public LsfgImage[][] Out = new LsfgImage[History][];
            public DescriptorSet[] Sets = new DescriptorSet[3];
            public DescriptorSet[] Last = new DescriptorSet[History];
        }

        private readonly Alpha[] _alpha = new Alpha[Levels];

        // beta
        private readonly LsfgPass[] _betaPass = new LsfgPass[5];
        private readonly LsfgImage[] _betaTemp1 = new LsfgImage[2];
        private readonly LsfgImage[] _betaTemp2 = new LsfgImage[2];
        private readonly LsfgImage[] _betaOut = new LsfgImage[6];
        private readonly DescriptorSet[] _betaFirst = new DescriptorSet[History];
        private readonly DescriptorSet[] _betaSets = new DescriptorSet[4];

        // gamma
        private readonly LsfgPass[] _gammaPass = new LsfgPass[5];
        private sealed class Gamma
        {
            public LsfgImage[] Temp1 = new LsfgImage[3];
            public LsfgImage[] Temp2 = new LsfgImage[2];
            public LsfgImage Out;
            public LsfgImage Previous;
            public LsfgImage Flow;
            public LsfgImage[][] Inputs;
            public DescriptorSet[][] First = new DescriptorSet[Slots][];
            public DescriptorSet[][] Sets = new DescriptorSet[Slots][];
        }

        private readonly Gamma[] _gamma = new Gamma[Levels];

        // delta
        private readonly LsfgPass[] _deltaPass = new LsfgPass[10];
        private sealed class Delta
        {
            public LsfgImage[] Temp1 = new LsfgImage[3];
            public LsfgImage[] Temp2 = new LsfgImage[2];
            public LsfgImage Out1, Out2;
            public LsfgImage PreviousGamma, Previous1, Previous2, Flow;
            public LsfgImage[][] Inputs;
            public DescriptorSet[][] First = new DescriptorSet[Slots][];
            public DescriptorSet[][] Sixth = new DescriptorSet[Slots][];
            public DescriptorSet[][] Sets = new DescriptorSet[Slots][];
        }

        private readonly Delta[] _delta = new Delta[DeltaInstances];

        // generate
        private readonly LsfgPass _genPass;
        private readonly DescriptorSet[][][] _genSets = new DescriptorSet[Slots][][]; // [slot][saida][paridade]
        private readonly LsfgImage _motion, _detail1, _detail2;

        public Extent2D InputExtent => _frames[0].Extent;

        internal Image Frame(int i) => _frames[i].Image;

        // ---- diagnostico: timestamps de GPU por etapa (so em quadros de sondagem) ----
        private QueryPool _queryPool;
        private int _queryNext;
        private bool _probing;

        internal unsafe void BeginProbe(CommandBuffer cb)
        {
            if (_queryPool.Handle == 0)
            {
                QueryPoolCreateInfo qi = new()
                {
                    SType = StructureType.QueryPoolCreateInfo,
                    QueryType = QueryType.Timestamp,
                    QueryCount = 16,
                };
                _api.CreateQueryPool(_device, in qi, null, out _queryPool).ThrowOnError();
            }

            _api.CmdResetQueryPool(cb, _queryPool, 0, 16);
            _queryNext = 0;
            _probing = true;
            Mark(cb);
        }

        private unsafe void Mark(CommandBuffer cb)
        {
            if (_probing && _queryNext < 16)
            {
                _api.CmdWriteTimestamp(cb, PipelineStageFlags.BottomOfPipeBit, _queryPool, (uint)_queryNext++);
            }
        }

        internal unsafe ulong[] EndProbe()
        {
            _probing = false;
            ulong[] result = new ulong[_queryNext];
            if (_queryNext > 0)
            {
                fixed (ulong* p = result)
                {
                    _api.GetQueryPoolResults(_device, _queryPool, 0, (uint)_queryNext, (nuint)(8 * _queryNext), p, 8,
                        QueryResultFlags.Result64Bit | QueryResultFlags.ResultWaitBit);
                }
            }

            return result;
        }

        public LsfgChain(VulkanRenderer gd, Device device, Dictionary<uint, uint[]> modules,
            uint width, uint height, float flowScale, ImageView[] outputViews)
        {
            _gd = gd;
            _api = gd.Api;
            _device = device;
            _flowScale = flowScale;

            try
            {
                CreatePool();

                _dummy = Own(new LsfgImage(gd, device, 1, 1, Format.R8G8B8A8Unorm));
                for (int i = 0; i < 2; i++)
                {
                    _frames[i] = Own(new LsfgImage(gd, device, width, height, Format.R8G8B8A8Unorm));
                }

                Sampler sampler = GetSampler(SamplerAddressMode.ClampToBorder, false);
                Sampler border = GetSampler(SamplerAddressMode.ClampToBorder, true);
                Sampler edge = GetSampler(SamplerAddressMode.ClampToEdge, false);

                // ---- mipmaps ----
                _mipPass = OwnP(new LsfgPass(_api, device, modules[LosslessDll.MipmapsId], 1, 1, 1, Levels));
                _flowExtent = new Extent2D(
                    Math.Max(1u, (uint)(width * flowScale)),
                    Math.Max(1u, (uint)(height * flowScale)));
                for (int i = 0; i < Levels; i++)
                {
                    _mipOut[i] = Own(new LsfgImage(gd, device, _flowExtent.Width >> i, _flowExtent.Height >> i, Format.R8Unorm));
                }

                AllocateSets(_mipPass, _mipSets);
                for (int i = 0; i < 2; i++)
                {
                    new Writer(this).Ubo(Constants(0f, false, false)).Sampler(sampler).Sampled(_frames[i]).Storage(_mipOut).Build(_mipSets[i]);
                }

                // ---- alpha ----
                _alphaPass[0] = OwnP(new LsfgPass(_api, device, modules[LosslessDll.Alpha[0]], 0, 1, 1, 1));
                _alphaPass[1] = OwnP(new LsfgPass(_api, device, modules[LosslessDll.Alpha[1]], 0, 1, 1, 1));
                _alphaPass[2] = OwnP(new LsfgPass(_api, device, modules[LosslessDll.Alpha[2]], 0, 1, 1, 2));
                _alphaPass[3] = OwnP(new LsfgPass(_api, device, modules[LosslessDll.Alpha[3]], 0, 1, 2, 2));
                for (int l = 0; l < Levels; l++)
                {
                    Alpha a = _alpha[l] = new Alpha();
                    uint hw = (_mipOut[l].Extent.Width + 1) >> 1, hh = (_mipOut[l].Extent.Height + 1) >> 1;
                    uint qw = (hw + 1) >> 1, qh = (hh + 1) >> 1;
                    a.Temp1 = Own(new LsfgImage(gd, device, hw, hh, Format.R8G8B8A8Unorm));
                    a.Temp2 = Own(new LsfgImage(gd, device, hw, hh, Format.R8G8B8A8Unorm));
                    for (int i = 0; i < 2; i++)
                    {
                        a.Temp3[i] = Own(new LsfgImage(gd, device, qw, qh, Format.R8G8B8A8Unorm));
                    }

                    for (int h = 0; h < History; h++)
                    {
                        a.Out[h] = new LsfgImage[2];
                        for (int i = 0; i < 2; i++)
                        {
                            a.Out[h][i] = Own(new LsfgImage(gd, device, qw, qh, Format.R8G8B8A8Unorm));
                        }
                    }

                    for (int i = 0; i < 3; i++)
                    {
                        a.Sets[i] = AllocateSet(_alphaPass[i]);
                    }

                    for (int h = 0; h < History; h++)
                    {
                        a.Last[h] = AllocateSet(_alphaPass[3]);
                    }

                    new Writer(this).Sampler(sampler).Sampled(_mipOut[l]).Storage(a.Temp1).Build(a.Sets[0]);
                    new Writer(this).Sampler(sampler).Sampled(a.Temp1).Storage(a.Temp2).Build(a.Sets[1]);
                    new Writer(this).Sampler(sampler).Sampled(a.Temp2).Storage(a.Temp3).Build(a.Sets[2]);
                    for (int h = 0; h < History; h++)
                    {
                        new Writer(this).Sampler(sampler).Sampled(a.Temp3).Storage(a.Out[h]).Build(a.Last[h]);
                    }
                }

                // ---- beta ----
                _betaPass[0] = OwnP(new LsfgPass(_api, device, modules[LosslessDll.Beta[0]], 0, 1, 6, 2));
                for (int i = 1; i < 4; i++)
                {
                    _betaPass[i] = OwnP(new LsfgPass(_api, device, modules[LosslessDll.Beta[i]], 0, 1, 2, 2));
                }

                _betaPass[4] = OwnP(new LsfgPass(_api, device, modules[LosslessDll.Beta[4]], 1, 1, 2, 6));
                LsfgImage[][] betaIn = _alpha[0].Out;
                Extent2D be = betaIn[0][0].Extent;
                for (int i = 0; i < 2; i++)
                {
                    _betaTemp1[i] = Own(new LsfgImage(gd, device, be.Width, be.Height, Format.R8G8B8A8Unorm));
                    _betaTemp2[i] = Own(new LsfgImage(gd, device, be.Width, be.Height, Format.R8G8B8A8Unorm));
                }

                for (int i = 0; i < 6; i++)
                {
                    _betaOut[i] = Own(new LsfgImage(gd, device, be.Width >> i, be.Height >> i, Format.R8Unorm));
                }

                for (int h = 0; h < History; h++)
                {
                    _betaFirst[h] = AllocateSet(_betaPass[0]);
                }

                _betaSets[0] = AllocateSet(_betaPass[1]);
                _betaSets[1] = AllocateSet(_betaPass[2]);
                _betaSets[2] = AllocateSet(_betaPass[3]);
                _betaSets[3] = AllocateSet(_betaPass[4]);
                for (int h = 0; h < History; h++)
                {
                    new Writer(this).Sampler(border)
                        .Sampled(betaIn[(h + 1) % History]).Sampled(betaIn[(h + 2) % History]).Sampled(betaIn[h % History])
                        .Storage(_betaTemp1).Build(_betaFirst[h]);
                }

                new Writer(this).Sampler(sampler).Sampled(_betaTemp1).Storage(_betaTemp2).Build(_betaSets[0]);
                new Writer(this).Sampler(sampler).Sampled(_betaTemp2).Storage(_betaTemp1).Build(_betaSets[1]);
                new Writer(this).Sampler(sampler).Sampled(_betaTemp1).Storage(_betaTemp2).Build(_betaSets[2]);
                new Writer(this).Ubo(Constants(Timestamp, false, false)).Sampler(sampler).Sampled(_betaTemp2).Storage(_betaOut).Build(_betaSets[3]);

                // ---- gamma / delta ----
                _gammaPass[0] = OwnP(new LsfgPass(_api, device, modules[LosslessDll.Gamma[0]], 1, 2, 5, 3));
                _gammaPass[1] = OwnP(new LsfgPass(_api, device, modules[LosslessDll.Gamma[1]], 0, 1, 3, 2));
                _gammaPass[2] = OwnP(new LsfgPass(_api, device, modules[LosslessDll.Gamma[2]], 0, 1, 2, 2));
                _gammaPass[3] = OwnP(new LsfgPass(_api, device, modules[LosslessDll.Gamma[3]], 0, 1, 2, 2));
                _gammaPass[4] = OwnP(new LsfgPass(_api, device, modules[LosslessDll.Gamma[4]], 1, 2, 4, 1));

                _deltaPass[0] = OwnP(new LsfgPass(_api, device, modules[LosslessDll.Delta[0]], 1, 2, 5, 3));
                _deltaPass[1] = OwnP(new LsfgPass(_api, device, modules[LosslessDll.Delta[1]], 0, 1, 3, 2));
                _deltaPass[2] = OwnP(new LsfgPass(_api, device, modules[LosslessDll.Delta[2]], 0, 1, 2, 2));
                _deltaPass[3] = OwnP(new LsfgPass(_api, device, modules[LosslessDll.Delta[3]], 0, 1, 2, 2));
                _deltaPass[4] = OwnP(new LsfgPass(_api, device, modules[LosslessDll.Delta[4]], 1, 2, 4, 1));
                _deltaPass[5] = OwnP(new LsfgPass(_api, device, modules[LosslessDll.Delta[5]], 1, 2, 6, 1));
                for (int i = 6; i < 9; i++)
                {
                    _deltaPass[i] = OwnP(new LsfgPass(_api, device, modules[LosslessDll.Delta[i]], 0, 1, 1, 1));
                }

                _deltaPass[9] = OwnP(new LsfgPass(_api, device, modules[LosslessDll.Delta[9]], 1, 2, 2, 1));

                for (int i = 0; i < Levels; i++)
                {
                    int level = Levels - 1 - i;
                    Gamma g = _gamma[i] = new Gamma
                    {
                        Inputs = _alpha[level].Out,
                        Flow = _betaOut[Math.Min(level, 5)],
                        Previous = i == 0 ? null : _gamma[i - 1].Out,
                    };
                    Extent2D e = g.Inputs[0][0].Extent;
                    for (int k = 0; k < 3; k++) g.Temp1[k] = Own(new LsfgImage(gd, device, e.Width, e.Height, Format.R8G8B8A8Unorm));
                    for (int k = 0; k < 2; k++) g.Temp2[k] = Own(new LsfgImage(gd, device, e.Width, e.Height, Format.R8G8B8A8Unorm));
                    g.Out = Own(new LsfgImage(gd, device, e.Width, e.Height, Format.R16G16B16A16Sfloat));

                    for (int s = 0; s < Slots; s++)
                    {
                        DescriptorSet[] first = g.First[s] = new DescriptorSet[History];
                        DescriptorSet[] sets = g.Sets[s] = new DescriptorSet[4];
                        for (int h = 0; h < History; h++) first[h] = AllocateSet(_gammaPass[0]);
                        for (int k = 0; k < 4; k++) sets[k] = AllocateSet(_gammaPass[k + 1]);

                        VkBuffer ubo = Constants(SlotFraction(s), g.Previous == null, false);
                        for (int h = 0; h < History; h++)
                        {
                            new Writer(this).Ubo(ubo).Sampler(border).Sampler(edge)
                                .Sampled(g.Inputs[(h + 2) % History]).Sampled(g.Inputs[h % History]).Sampled(g.Previous)
                                .Storage(g.Temp1).Build(first[h]);
                        }

                        new Writer(this).Sampler(sampler).Sampled(g.Temp1).Storage(g.Temp2).Build(sets[0]);
                        new Writer(this).Sampler(sampler).Sampled(g.Temp2).Storage(g.Temp1[0]).Storage(g.Temp1[1]).Build(sets[1]);
                        new Writer(this).Sampler(sampler).Sampled(g.Temp1[0]).Sampled(g.Temp1[1]).Storage(g.Temp2).Build(sets[2]);
                        new Writer(this).Ubo(ubo).Sampler(sampler).Sampler(edge)
                            .Sampled(g.Temp2).Sampled(g.Previous).Sampled(g.Flow).Storage(g.Out).Build(sets[3]);
                    }

                    if (i < DeltaFirstLevel)
                    {
                        continue;
                    }

                    int idx = i - DeltaFirstLevel;
                    Delta d = _delta[idx] = new Delta
                    {
                        Inputs = _alpha[level].Out,
                        Flow = _betaOut[level],
                        PreviousGamma = i == DeltaFirstLevel ? null : _gamma[i - 1].Out,
                        Previous1 = idx == 0 ? null : _delta[idx - 1].Out1,
                        Previous2 = idx == 0 ? null : _delta[idx - 1].Out2,
                    };
                    for (int k = 0; k < 3; k++) d.Temp1[k] = Own(new LsfgImage(gd, device, e.Width, e.Height, Format.R8G8B8A8Unorm));
                    for (int k = 0; k < 2; k++) d.Temp2[k] = Own(new LsfgImage(gd, device, e.Width, e.Height, Format.R8G8B8A8Unorm));
                    d.Out1 = Own(new LsfgImage(gd, device, e.Width, e.Height, Format.R16G16B16A16Sfloat));
                    d.Out2 = Own(new LsfgImage(gd, device, e.Width, e.Height, Format.R16G16B16A16Sfloat));

                    int[] layouts = { 1, 2, 3, 4, 6, 7, 8, 9 };
                    for (int s = 0; s < Slots; s++)
                    {
                        DescriptorSet[] first = d.First[s] = new DescriptorSet[History];
                        DescriptorSet[] sixth = d.Sixth[s] = new DescriptorSet[History];
                        DescriptorSet[] sets = d.Sets[s] = new DescriptorSet[8];
                        for (int h = 0; h < History; h++)
                        {
                            first[h] = AllocateSet(_deltaPass[0]);
                            sixth[h] = AllocateSet(_deltaPass[5]);
                        }

                        for (int k = 0; k < 8; k++) sets[k] = AllocateSet(_deltaPass[layouts[k]]);

                        VkBuffer dubo = Constants(SlotFraction(s), false, d.PreviousGamma == null);
                        for (int h = 0; h < History; h++)
                        {
                            new Writer(this).Ubo(dubo).Sampler(border).Sampler(edge)
                                .Sampled(d.Inputs[(h + 2) % History]).Sampled(d.Inputs[h % History]).Sampled(d.PreviousGamma)
                                .Storage(d.Temp1).Build(first[h]);
                            new Writer(this).Ubo(dubo).Sampler(border).Sampler(edge)
                                .Sampled(d.Inputs[(h + 2) % History]).Sampled(d.Inputs[h % History]).Sampled(d.PreviousGamma).Sampled(d.Previous1)
                                .Storage(d.Temp2[0]).Build(sixth[h]);
                        }

                        new Writer(this).Sampler(sampler).Sampled(d.Temp1).Storage(d.Temp2).Build(sets[0]);
                        new Writer(this).Sampler(sampler).Sampled(d.Temp2).Storage(d.Temp1[0]).Storage(d.Temp1[1]).Build(sets[1]);
                        new Writer(this).Sampler(sampler).Sampled(d.Temp1[0]).Sampled(d.Temp1[1]).Storage(d.Temp2).Build(sets[2]);
                        new Writer(this).Ubo(dubo).Sampler(sampler).Sampler(edge)
                            .Sampled(d.Temp2).Sampled(d.PreviousGamma).Sampled(d.Flow).Storage(d.Out1).Build(sets[3]);
                        new Writer(this).Sampler(sampler).Sampled(d.Temp2[0]).Storage(d.Temp1[0]).Build(sets[4]);
                        new Writer(this).Sampler(sampler).Sampled(d.Temp1[0]).Storage(d.Temp2[0]).Build(sets[5]);
                        new Writer(this).Sampler(sampler).Sampled(d.Temp2[0]).Storage(d.Temp1[0]).Build(sets[6]);
                        new Writer(this).Ubo(dubo).Sampler(sampler).Sampler(edge)
                            .Sampled(d.Temp1[0]).Sampled(d.Previous2).Storage(d.Out2).Build(sets[7]);
                    }
                }

                // ---- generate ----
                _genPass = OwnP(new LsfgPass(_api, device, modules[LosslessDll.GenerateId], 1, 2, 5, 1));
                _motion = _gamma[Levels - 1].Out;
                _detail1 = _delta[DeltaInstances - 1].Out1;
                _detail2 = _delta[DeltaInstances - 1].Out2;
                for (int s = 0; s < Slots; s++)
                {
                    VkBuffer gubo = Constants(SlotFraction(s), false, false);
                    _genSets[s] = new DescriptorSet[outputViews.Length][];
                    for (int o = 0; o < outputViews.Length; o++)
                    {
                        _genSets[s][o] = new DescriptorSet[2];
                        for (int i = 0; i < 2; i++)
                        {
                            _genSets[s][o][i] = AllocateSet(_genPass);
                            new Writer(this).Ubo(gubo).Sampler(sampler).Sampler(edge)
                                .Sampled(_frames[1 - i]).Sampled(_frames[i]).Sampled(_motion).Sampled(_detail1).Sampled(_detail2)
                                .StorageView(outputViews[o]).Build(_genSets[s][o][i]);
                        }
                    }
                }
            }
            catch
            {
                Dispose();
                throw;
            }
        }

        private T Own<T>(T obj) where T : IDisposable
        {
            _owned.Add(obj);
            return obj;
        }

        private LsfgPass OwnP(LsfgPass p) => Own(p);

        // ------------------------------------------------------------------ recursos auxiliares

        private void CreatePool()
        {
            DescriptorPoolSize[] sizes =
            {
                new(DescriptorType.UniformBuffer, 2048),
                new(DescriptorType.Sampler, 4096),
                new(DescriptorType.SampledImage, 12288),
                new(DescriptorType.StorageImage, 6144),
            };
            fixed (DescriptorPoolSize* p = sizes)
            {
                DescriptorPoolCreateInfo ci = new()
                {
                    SType = StructureType.DescriptorPoolCreateInfo,
                    MaxSets = 1024,
                    PoolSizeCount = (uint)sizes.Length,
                    PPoolSizes = p,
                };
                _api.CreateDescriptorPool(_device, in ci, null, out _pool).ThrowOnError();
            }
        }

        private DescriptorSet AllocateSet(LsfgPass pass)
        {
            DescriptorSetLayout layout = pass.SetLayout;
            DescriptorSetAllocateInfo ai = new()
            {
                SType = StructureType.DescriptorSetAllocateInfo,
                DescriptorPool = _pool,
                DescriptorSetCount = 1,
                PSetLayouts = &layout,
            };
            _api.AllocateDescriptorSets(_device, in ai, out DescriptorSet set).ThrowOnError();
            return set;
        }

        private void AllocateSets(LsfgPass pass, DescriptorSet[] dest)
        {
            for (int i = 0; i < dest.Length; i++)
            {
                dest[i] = AllocateSet(pass);
            }
        }

        private Sampler GetSampler(SamplerAddressMode mode, bool whiteBorder)
        {
            ulong key = ((ulong)mode) | (whiteBorder ? 0x100UL : 0);
            if (_samplers.TryGetValue(key, out Sampler s))
            {
                return s;
            }

            SamplerCreateInfo ci = new()
            {
                SType = StructureType.SamplerCreateInfo,
                MagFilter = Filter.Linear,
                MinFilter = Filter.Linear,
                MipmapMode = SamplerMipmapMode.Linear,
                AddressModeU = mode,
                AddressModeV = mode,
                AddressModeW = mode,
                MaxLod = 1000f,
                BorderColor = whiteBorder ? BorderColor.FloatOpaqueWhite : BorderColor.FloatTransparentBlack,
            };
            _api.CreateSampler(_device, in ci, null, out s).ThrowOnError();
            _samplers[key] = s;
            return s;
        }

        // Estrutura de 48 bytes lida pelos shaders do LSFG.
        private VkBuffer Constants(float timestamp, bool firstIter, bool firstIterS)
        {
            ulong key = (ulong)BitConverter.SingleToUInt32Bits(timestamp) | (firstIter ? 1UL << 32 : 0) | (firstIterS ? 1UL << 33 : 0);
            if (_constants.TryGetValue(key, out VkBuffer cached))
            {
                return cached;
            }

            BufferCreateInfo bi = new()
            {
                SType = StructureType.BufferCreateInfo,
                Size = 48,
                Usage = BufferUsageFlags.UniformBufferBit,
                SharingMode = SharingMode.Exclusive,
            };
            _api.CreateBuffer(_device, in bi, null, out VkBuffer buffer).ThrowOnError();
            _api.GetBufferMemoryRequirements(_device, buffer, out MemoryRequirements req);
            MemoryAllocation alloc = _gd.MemoryAllocator.AllocateDeviceMemory(
                req, MemoryPropertyFlags.HostVisibleBit | MemoryPropertyFlags.HostCoherentBit, true);
            if (alloc.Memory.Handle == 0 || alloc.HostPointer == 0)
            {
                _api.DestroyBuffer(_device, buffer, null);
                throw new InvalidOperationException("LSFG: sem memoria para UBO");
            }

            _api.BindBufferMemory(_device, buffer, alloc.Memory, alloc.Offset).ThrowOnError();

            uint* p = (uint*)alloc.HostPointer;
            for (int i = 0; i < 12; i++) p[i] = 0;
            p[2] = firstIter ? 1u : 0u;                                    // first_iter
            p[3] = firstIterS ? 1u : 0u;                                   // first_iter_s
            ((float*)p)[6] = 1.0f / _flowScale;                            // resolution_inv_scale
            ((float*)p)[7] = timestamp;                                    // timestamp
            ((float*)p)[8] = 0.5f;                                         // ui_threshold

            _buffers.Add((buffer, alloc));
            _constants[key] = buffer;
            return buffer;
        }

        private sealed class Writer
        {
            private readonly LsfgChain _c;
            private readonly List<(DescriptorType Type, int Index, bool IsBuffer)> _entries = new();
            private readonly List<DescriptorImageInfo> _images = new();
            private readonly List<DescriptorBufferInfo> _buffers = new();

            public Writer(LsfgChain c) => _c = c;

            public Writer Ubo(VkBuffer b)
            {
                _entries.Add((DescriptorType.UniformBuffer, _buffers.Count, true));
                _buffers.Add(new DescriptorBufferInfo(b, 0, 48));
                return this;
            }

            public Writer Sampler(Sampler s)
            {
                _entries.Add((DescriptorType.Sampler, _images.Count, false));
                _images.Add(new DescriptorImageInfo { Sampler = s });
                return this;
            }

            private Writer Img(DescriptorType t, ImageView v)
            {
                _entries.Add((t, _images.Count, false));
                _images.Add(new DescriptorImageInfo { ImageView = v, ImageLayout = ImageLayout.General });
                return this;
            }

            // Imagem nula vira uma imagem 1x1 valida (a flag first_iter impede a leitura).
            public Writer Sampled(LsfgImage i) => Img(DescriptorType.SampledImage, (i ?? _c._dummy).View);

            public Writer Sampled(LsfgImage[] a)
            {
                foreach (LsfgImage i in a) Sampled(i);
                return this;
            }

            public Writer Storage(LsfgImage i) => Img(DescriptorType.StorageImage, i.View);

            public Writer Storage(LsfgImage[] a)
            {
                foreach (LsfgImage i in a) Storage(i);
                return this;
            }

            public Writer StorageView(ImageView v) => Img(DescriptorType.StorageImage, v);

            public unsafe void Build(DescriptorSet set)
            {
                DescriptorImageInfo[] imgs = _images.ToArray();
                DescriptorBufferInfo[] bufs = _buffers.ToArray();
                WriteDescriptorSet[] writes = new WriteDescriptorSet[_entries.Count];
                fixed (DescriptorImageInfo* pi = imgs)
                fixed (DescriptorBufferInfo* pb = bufs)
                {
                    for (int i = 0; i < writes.Length; i++)
                    {
                        (DescriptorType type, int index, bool isBuffer) = _entries[i];
                        writes[i] = new WriteDescriptorSet
                        {
                            SType = StructureType.WriteDescriptorSet,
                            DstSet = set,
                            DstBinding = (uint)i,
                            DescriptorCount = 1,
                            DescriptorType = type,
                            PImageInfo = isBuffer ? null : pi + index,
                            PBufferInfo = isBuffer ? pb + index : null,
                        };
                    }

                    fixed (WriteDescriptorSet* pw = writes)
                    {
                        _c._api.UpdateDescriptorSets(_c._device, (uint)writes.Length, pw, 0, null);
                    }
                }
            }
        }

        // ------------------------------------------------------------------ despacho

        private static uint Groups(uint size, int shift) => (size + (1u << shift) - 1) >> shift;

        private void Dispatch(CommandBuffer cb, uint x, uint y) => _api.CmdDispatch(cb, x, y, 1);

        private LsfgBarriers B(CommandBuffer cb) => new(_api, cb);

        /// <summary>Copia o quadro real para o historico e roda mipmaps, alpha e beta (uma vez por quadro real).</summary>
        public void DispatchShared(CommandBuffer cb, Image source, uint srcWidth, uint srcHeight, uint srcLevel, uint srcLayer, ulong count)
        {
            if (!_dummyReady)
            {
                B(cb).ReadToWrite(_dummy).Build();
                _dummyReady = true;
            }

            CopyInput(cb, source, srcWidth, srcHeight, srcLevel, srcLayer, _frames[count % 2]);
            Mark(cb);

            // mipmaps
            B(cb).WriteToRead(_frames[count % 2]).ReadToWrite(_mipOut).Build();
            _mipPass.Bind(cb, _mipSets[count % 2]);
            Dispatch(cb, Groups(_flowExtent.Width, 6), Groups(_flowExtent.Height, 6));
            Mark(cb);

            // alpha: 4 estagios, todos os niveis por estagio
            for (int stage = 0; stage < 4; stage++)
            {
                LsfgBarriers bar = B(cb);
                for (int l = 0; l < Levels; l++)
                {
                    Alpha a = _alpha[l];
                    switch (stage)
                    {
                        case 0: bar.WriteToRead(_mipOut[l]).ReadToWrite(a.Temp1); break;
                        case 1: bar.WriteToRead(a.Temp1).ReadToWrite(a.Temp2); break;
                        case 2: bar.WriteToRead(a.Temp2).ReadToWrite(a.Temp3); break;
                        default: bar.WriteToRead(a.Temp3).ReadToWrite(a.Out[count % History]); break;
                    }
                }

                bar.Build();

                _alphaPass[stage].BindPipeline(cb);
                foreach (Alpha a in _alpha)
                {
                    Extent2D e = stage < 2 ? a.Temp1.Extent : a.Temp3[0].Extent;
                    DescriptorSet set = stage < 3 ? a.Sets[stage] : a.Last[count % History];
                    _alphaPass[stage].BindSet(cb, set);
                    Dispatch(cb, Groups(e.Width, 3), Groups(e.Height, 3));
                }
            }

            // beta
            Mark(cb);
            Extent2D be = _betaTemp1[0].Extent;
            uint gx = Groups(be.Width, 3), gy = Groups(be.Height, 3);
            LsfgBarriers bb = B(cb);
            foreach (LsfgImage[] slot in _alpha[0].Out)
            {
                bb.WriteToRead(slot);
            }

            bb.ReadToWrite(_betaTemp1).Build();
            _betaPass[0].Bind(cb, _betaFirst[count % History]);
            Dispatch(cb, gx, gy);

            B(cb).WriteToRead(_betaTemp1).ReadToWrite(_betaTemp2).Build();
            _betaPass[1].Bind(cb, _betaSets[0]);
            Dispatch(cb, gx, gy);

            B(cb).WriteToRead(_betaTemp2).ReadToWrite(_betaTemp1).Build();
            _betaPass[2].Bind(cb, _betaSets[1]);
            Dispatch(cb, gx, gy);

            B(cb).WriteToRead(_betaTemp1).ReadToWrite(_betaTemp2).Build();
            _betaPass[3].Bind(cb, _betaSets[2]);
            Dispatch(cb, gx, gy);

            B(cb).WriteToRead(_betaTemp2).ReadToWrite(_betaOut).Build();
            _betaPass[4].Bind(cb, _betaSets[3]);
            Dispatch(cb, Groups(be.Width, 5), Groups(be.Height, 5));
            Mark(cb);
        }

        private void CopyInput(CommandBuffer cb, Image source, uint w, uint h, uint srcLevel, uint srcLayer, LsfgImage dest)
        {
            ImageSubresourceRange sourceRange = new(ImageAspectFlags.ColorBit, srcLevel, 1, srcLayer, 1);
            ImageSubresourceRange destRange = new(ImageAspectFlags.ColorBit, 0, 1, 0, 1);

            ImageMemoryBarrier* before = stackalloc ImageMemoryBarrier[2];
            before[0] = new ImageMemoryBarrier
            {
                SType = StructureType.ImageMemoryBarrier,
                SrcAccessMask = AccessFlags.MemoryWriteBit,
                DstAccessMask = AccessFlags.TransferReadBit,
                OldLayout = ImageLayout.General,
                NewLayout = ImageLayout.General,
                SrcQueueFamilyIndex = Vk.QueueFamilyIgnored,
                DstQueueFamilyIndex = Vk.QueueFamilyIgnored,
                Image = source,
                SubresourceRange = sourceRange,
            };
            before[1] = new ImageMemoryBarrier
            {
                SType = StructureType.ImageMemoryBarrier,
                SrcAccessMask = AccessFlags.ShaderReadBit,
                DstAccessMask = AccessFlags.TransferWriteBit,
                OldLayout = dest.Layout,
                NewLayout = ImageLayout.TransferDstOptimal,
                SrcQueueFamilyIndex = Vk.QueueFamilyIgnored,
                DstQueueFamilyIndex = Vk.QueueFamilyIgnored,
                Image = dest.Image,
                SubresourceRange = destRange,
            };
            _api.CmdPipelineBarrier(cb, PipelineStageFlags.AllCommandsBit, PipelineStageFlags.TransferBit,
                0, 0, null, 0, null, 2, before);

            ImageBlit.SrcOffsetsBuffer srcOffsets = new();
            ImageBlit.DstOffsetsBuffer dstOffsets = new();
            srcOffsets.Element0 = new Offset3D(0, 0, 0);
            srcOffsets.Element1 = new Offset3D((int)w, (int)h, 1);
            dstOffsets.Element0 = new Offset3D(0, 0, 0);
            dstOffsets.Element1 = new Offset3D((int)dest.Extent.Width, (int)dest.Extent.Height, 1);
            ImageBlit blit = new()
            {
                SrcSubresource = new ImageSubresourceLayers(ImageAspectFlags.ColorBit, srcLevel, srcLayer, 1),
                SrcOffsets = srcOffsets,
                DstSubresource = new ImageSubresourceLayers(ImageAspectFlags.ColorBit, 0, 0, 1),
                DstOffsets = dstOffsets,
            };
            _api.CmdBlitImage(cb, source, ImageLayout.General, dest.Image, ImageLayout.TransferDstOptimal, 1, in blit, Filter.Linear);

            ImageMemoryBarrier* after = stackalloc ImageMemoryBarrier[2];
            after[0] = new ImageMemoryBarrier
            {
                SType = StructureType.ImageMemoryBarrier,
                SrcAccessMask = AccessFlags.TransferReadBit,
                DstAccessMask = AccessFlags.ColorAttachmentWriteBit | AccessFlags.ShaderReadBit | AccessFlags.ShaderWriteBit,
                OldLayout = ImageLayout.General,
                NewLayout = ImageLayout.General,
                SrcQueueFamilyIndex = Vk.QueueFamilyIgnored,
                DstQueueFamilyIndex = Vk.QueueFamilyIgnored,
                Image = source,
                SubresourceRange = sourceRange,
            };
            after[1] = new ImageMemoryBarrier
            {
                SType = StructureType.ImageMemoryBarrier,
                SrcAccessMask = AccessFlags.TransferWriteBit,
                DstAccessMask = AccessFlags.ShaderReadBit,
                OldLayout = ImageLayout.TransferDstOptimal,
                NewLayout = ImageLayout.General,
                SrcQueueFamilyIndex = Vk.QueueFamilyIgnored,
                DstQueueFamilyIndex = Vk.QueueFamilyIgnored,
                Image = dest.Image,
                SubresourceRange = destRange,
            };
            _api.CmdPipelineBarrier(cb, PipelineStageFlags.TransferBit,
                PipelineStageFlags.AllCommandsBit & ~PipelineStageFlags.TransferBit,
                0, 0, null, 0, null, 2, after);

            dest.Layout = ImageLayout.General;
        }

        /// <summary>Gera o quadro intermediario (timestamp 0,5) em <paramref name="target"/>.</summary>
        public void DispatchGeneration(CommandBuffer cb, ulong count, int slot, int output, Image target, uint width, uint height)
        {
            for (int i = 0; i < Levels; i++)
            {
                DispatchGamma(cb, _gamma[i], count, slot);
                if (i >= DeltaFirstLevel)
                {
                    DispatchDelta(cb, _delta[i - DeltaFirstLevel], count, slot);
                }
            }

            Mark(cb);
            LsfgBarriers bar = B(cb);
            bar.WriteToRead(_frames[0]).WriteToRead(_frames[1]).WriteToRead(_motion).WriteToRead(_detail1).WriteToRead(_detail2);
            bar.Build();

            ImageMemoryBarrier discard = new()
            {
                SType = StructureType.ImageMemoryBarrier,
                SrcAccessMask = 0,
                DstAccessMask = AccessFlags.ShaderWriteBit,
                OldLayout = ImageLayout.Undefined,
                NewLayout = ImageLayout.General,
                SrcQueueFamilyIndex = Vk.QueueFamilyIgnored,
                DstQueueFamilyIndex = Vk.QueueFamilyIgnored,
                Image = target,
                SubresourceRange = new ImageSubresourceRange(ImageAspectFlags.ColorBit, 0, 1, 0, 1),
            };
            _api.CmdPipelineBarrier(cb, PipelineStageFlags.AllCommandsBit, PipelineStageFlags.ComputeShaderBit,
                0, 0, null, 0, null, 1, &discard);

            _genPass.Bind(cb, _genSets[slot][output][count % 2]);
            Dispatch(cb, Groups(width, 4), Groups(height, 4));

            ImageMemoryBarrier done = discard;
            done.SrcAccessMask = AccessFlags.ShaderWriteBit;
            done.DstAccessMask = AccessFlags.ShaderReadBit | AccessFlags.TransferReadBit | AccessFlags.ColorAttachmentReadBit;
            done.OldLayout = ImageLayout.General;
            _api.CmdPipelineBarrier(cb, PipelineStageFlags.ComputeShaderBit,
                PipelineStageFlags.FragmentShaderBit | PipelineStageFlags.ComputeShaderBit | PipelineStageFlags.TransferBit |
                PipelineStageFlags.ColorAttachmentOutputBit,
                0, 0, null, 0, null, 1, &done);
            Mark(cb);
        }

        private void DispatchGamma(CommandBuffer cb, Gamma g, ulong count, int slot)
        {
            Extent2D e = g.Temp1[0].Extent;
            uint gx = Groups(e.Width, 3), gy = Groups(e.Height, 3);
            int hist = (int)(count % History), prev = (int)((count + 2) % History);

            LsfgBarriers b0 = B(cb);
            b0.WriteToRead(g.Inputs[prev]).WriteToRead(g.Inputs[hist]).WriteToRead(g.Previous).ReadToWrite(g.Temp1).Build();
            _gammaPass[0].Bind(cb, g.First[slot][hist]);
            Dispatch(cb, gx, gy);

            B(cb).WriteToRead(g.Temp1).ReadToWrite(g.Temp2).Build();
            _gammaPass[1].Bind(cb, g.Sets[slot][0]);
            Dispatch(cb, gx, gy);

            B(cb).WriteToRead(g.Temp2).ReadToWrite(g.Temp1[0]).ReadToWrite(g.Temp1[1]).Build();
            _gammaPass[2].Bind(cb, g.Sets[slot][1]);
            Dispatch(cb, gx, gy);

            B(cb).WriteToRead(g.Temp1[0]).WriteToRead(g.Temp1[1]).ReadToWrite(g.Temp2).Build();
            _gammaPass[3].Bind(cb, g.Sets[slot][2]);
            Dispatch(cb, gx, gy);

            B(cb).WriteToRead(g.Temp2).WriteToRead(g.Previous).WriteToRead(g.Flow).ReadToWrite(g.Out).Build();
            _gammaPass[4].Bind(cb, g.Sets[slot][3]);
            Dispatch(cb, gx, gy);
        }

        private void DispatchDelta(CommandBuffer cb, Delta d, ulong count, int slot)
        {
            Extent2D e = d.Temp1[0].Extent;
            uint gx = Groups(e.Width, 3), gy = Groups(e.Height, 3);
            int hist = (int)(count % History), prev = (int)((count + 2) % History);

            B(cb).WriteToRead(d.Inputs[prev]).WriteToRead(d.Inputs[hist]).WriteToRead(d.PreviousGamma).ReadToWrite(d.Temp1).Build();
            _deltaPass[0].Bind(cb, d.First[slot][hist]);
            Dispatch(cb, gx, gy);

            B(cb).WriteToRead(d.Temp1).ReadToWrite(d.Temp2).Build();
            _deltaPass[1].Bind(cb, d.Sets[slot][0]);
            Dispatch(cb, gx, gy);

            B(cb).WriteToRead(d.Temp2).ReadToWrite(d.Temp1).Build();
            _deltaPass[2].Bind(cb, d.Sets[slot][1]);
            Dispatch(cb, gx, gy);

            B(cb).WriteToRead(d.Temp1).ReadToWrite(d.Temp2).Build();
            _deltaPass[3].Bind(cb, d.Sets[slot][2]);
            Dispatch(cb, gx, gy);

            B(cb).WriteToRead(d.Temp2).WriteToRead(d.PreviousGamma).WriteToRead(d.Flow).ReadToWrite(d.Out1).Build();
            _deltaPass[4].Bind(cb, d.Sets[slot][3]);
            Dispatch(cb, gx, gy);

            B(cb).WriteToRead(d.Inputs[prev]).WriteToRead(d.Inputs[hist]).WriteToRead(d.PreviousGamma).WriteToRead(d.Previous1)
                .ReadToWrite(d.Temp2).Build();
            _deltaPass[5].Bind(cb, d.Sixth[slot][hist]);
            Dispatch(cb, gx, gy);

            B(cb).WriteToRead(d.Temp2).ReadToWrite(d.Temp1[0]).ReadToWrite(d.Temp1[1]).Build();
            _deltaPass[6].Bind(cb, d.Sets[slot][4]);
            Dispatch(cb, gx, gy);

            B(cb).WriteToRead(d.Temp1[0]).WriteToRead(d.Temp1[1]).ReadToWrite(d.Temp2).Build();
            _deltaPass[7].Bind(cb, d.Sets[slot][5]);
            Dispatch(cb, gx, gy);

            B(cb).WriteToRead(d.Temp2).ReadToWrite(d.Temp1[0]).ReadToWrite(d.Temp1[1]).Build();
            _deltaPass[8].Bind(cb, d.Sets[slot][6]);
            Dispatch(cb, gx, gy);

            B(cb).WriteToRead(d.Temp1[0]).WriteToRead(d.Temp1[1]).WriteToRead(d.Previous2).ReadToWrite(d.Out2).Build();
            _deltaPass[9].Bind(cb, d.Sets[slot][7]);
            Dispatch(cb, gx, gy);
        }

        public void Dispose()
        {
            _api.DeviceWaitIdle(_device);

            foreach (IDisposable o in _owned)
            {
                o.Dispose();
            }

            _owned.Clear();

            foreach ((VkBuffer buffer, MemoryAllocation alloc) in _buffers)
            {
                _api.DestroyBuffer(_device, buffer, null);
                alloc.Dispose();
            }

            _buffers.Clear();

            foreach (Sampler s in _samplers.Values)
            {
                _api.DestroySampler(_device, s, null);
            }

            _samplers.Clear();

            if (_queryPool.Handle != 0)
            {
                _api.DestroyQueryPool(_device, _queryPool, null);
                _queryPool = default;
            }

            if (_pool.Handle != 0)
            {
                _api.DestroyDescriptorPool(_device, _pool, null);
                _pool = default;
            }
        }
    }
}
