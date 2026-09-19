using Silk.NET.Vulkan;
using System;
using System.Collections.Generic;
using System.Linq;

namespace Ryujinx.Graphics.Vulkan
{
    internal class RenderPassHolder
    {
        private readonly struct FramebufferCacheKey : IRefEquatable<FramebufferCacheKey>
        {
            private readonly uint _width;
            private readonly uint _height;
            private readonly uint _layers;

            public FramebufferCacheKey(uint width, uint height, uint layers)
            {
                _width = width;
                _height = height;
                _layers = layers;
            }

            public override int GetHashCode()
            {
                return HashCode.Combine(_width, _height, _layers);
            }

            public bool Equals(ref FramebufferCacheKey other)
            {
                return other._width == _width && other._height == _height && other._layers == _layers;
            }
        }

        private readonly record struct ForcedFence(TextureStorage Texture, PipelineStageFlags StageFlags);

        private readonly TextureView[] _textures;
        private readonly Auto<DisposableRenderPass> _renderPass;
        private readonly HashTableSlim<FramebufferCacheKey, Auto<DisposableFramebuffer>> _framebuffers;
        private readonly RenderPassCacheKey _key;
        private readonly List<ForcedFence> _forcedFences;

        // NEOV2 Etapa 5: bits do clearMask usados para criar variantes do render pass com LoadOp.Clear.
        // Bits 0..7: attachments de cor (indice do attachment). Bit 30: aspecto depth. Bit 31: aspecto stencil.
        public const uint DepthClearBit = 1u << 30;
        public const uint StencilClearBit = 1u << 31;

        private const int MaxAttachments = Constants.MaxRenderTargets + 1;

        private readonly bool _hasFramebuffer;
        private readonly int _attachmentsCount;
        private readonly int _colorAttachmentsCount;
        private readonly int _maxColorAttachmentIndex;
        private readonly bool _hasDepthStencil;
        private readonly Silk.NET.Vulkan.Format[] _attachmentFormats;
        private readonly SampleCountFlags[] _attachmentSamples;
        private readonly int[] _attachmentBindIndices;

        private Dictionary<uint, Auto<DisposableRenderPass>> _clearVariants;
        private bool _disposed;

        public bool IsDisposed => _disposed;

        public unsafe RenderPassHolder(VulkanRenderer gd, Device device, RenderPassCacheKey key, FramebufferParams fb)
        {
            // Create render pass using framebuffer params.

            _hasFramebuffer = fb != null;

            if (_hasFramebuffer && fb.AttachmentsCount != 0)
            {
                _attachmentsCount = fb.AttachmentsCount;
                _attachmentFormats = new Silk.NET.Vulkan.Format[_attachmentsCount];
                _attachmentSamples = new SampleCountFlags[_attachmentsCount];

                for (int i = 0; i < _attachmentsCount; i++)
                {
                    _attachmentFormats[i] = fb.AttachmentFormats[i];
                    _attachmentSamples[i] = TextureStorage.ConvertToSampleCountFlags(gd.Capabilities.SupportedSampleCounts, fb.AttachmentSamples[i]);
                }

                int colorAttachmentsCount = fb.ColorAttachmentsCount;

                if (colorAttachmentsCount > MaxAttachments - 1)
                {
                    colorAttachmentsCount = MaxAttachments - 1;
                }

                _colorAttachmentsCount = colorAttachmentsCount;
                _maxColorAttachmentIndex = fb.MaxColorAttachmentIndex;
                _attachmentBindIndices = new int[colorAttachmentsCount];

                for (int i = 0; i < colorAttachmentsCount; i++)
                {
                    _attachmentBindIndices[i] = fb.AttachmentIndices[i];
                }

                _hasDepthStencil = fb.HasDepthStencil;
            }

            _renderPass = BuildRenderPass(gd, device, 0);

            _framebuffers = new HashTableSlim<FramebufferCacheKey, Auto<DisposableFramebuffer>>();

            // Register this render pass with all render target views.

            if (_hasFramebuffer)
            {
                TextureView[] textures = fb.GetAttachmentViews();

                foreach (TextureView texture in textures)
                {
                    texture.AddRenderPass(key, this);
                }

                _textures = textures;
            }

            _key = key;

            _forcedFences = [];
        }

        private unsafe Auto<DisposableRenderPass> BuildRenderPass(VulkanRenderer gd, Device device, uint clearMask)
        {
            AttachmentDescription[] attachmentDescs = null;

            SubpassDescription subpass = new()
            {
                PipelineBindPoint = PipelineBindPoint.Graphics,
            };

            AttachmentReference* attachmentReferences = stackalloc AttachmentReference[MaxAttachments];

            if (_hasFramebuffer && _attachmentsCount != 0)
            {
                attachmentDescs = new AttachmentDescription[_attachmentsCount];

                int dsIndex = _attachmentsCount - 1;

                for (int i = 0; i < _attachmentsCount; i++)
                {
                    AttachmentLoadOp loadOp = AttachmentLoadOp.Load;
                    AttachmentLoadOp stencilLoadOp = AttachmentLoadOp.Load;

                    if (_hasDepthStencil && i == dsIndex)
                    {
                        if ((clearMask & DepthClearBit) != 0)
                        {
                            loadOp = AttachmentLoadOp.Clear;
                        }

                        if ((clearMask & StencilClearBit) != 0)
                        {
                            stencilLoadOp = AttachmentLoadOp.Clear;
                        }
                    }
                    else if (i < 30 && (clearMask & (1u << i)) != 0)
                    {
                        loadOp = AttachmentLoadOp.Clear;
                    }

                    attachmentDescs[i] = new AttachmentDescription(
                        0,
                        _attachmentFormats[i],
                        _attachmentSamples[i],
                        loadOp,
                        AttachmentStoreOp.Store,
                        stencilLoadOp,
                        AttachmentStoreOp.Store,
                        ImageLayout.General,
                        ImageLayout.General);
                }

                if (_colorAttachmentsCount != 0)
                {
                    int maxAttachmentIndex = _maxColorAttachmentIndex;
                    subpass.ColorAttachmentCount = (uint)maxAttachmentIndex + 1;
                    subpass.PColorAttachments = &attachmentReferences[0];

                    // Fill with VK_ATTACHMENT_UNUSED to cover any gaps.
                    for (int i = 0; i <= maxAttachmentIndex; i++)
                    {
                        subpass.PColorAttachments[i] = new AttachmentReference(Vk.AttachmentUnused, ImageLayout.Undefined);
                    }

                    for (int i = 0; i < _colorAttachmentsCount; i++)
                    {
                        int bindIndex = _attachmentBindIndices[i];

                        subpass.PColorAttachments[bindIndex] = new AttachmentReference((uint)i, ImageLayout.General);
                    }
                }

                if (_hasDepthStencil)
                {
                    subpass.PDepthStencilAttachment = &attachmentReferences[MaxAttachments - 1];
                    *subpass.PDepthStencilAttachment = new AttachmentReference((uint)dsIndex, ImageLayout.General);
                }
            }

            SubpassDependency subpassDependency = PipelineConverter.CreateSubpassDependency(gd);

            fixed (AttachmentDescription* pAttachmentDescs = attachmentDescs)
            {
                RenderPassCreateInfo renderPassCreateInfo = new()
                {
                    SType = StructureType.RenderPassCreateInfo,
                    PAttachments = pAttachmentDescs,
                    AttachmentCount = attachmentDescs != null ? (uint)attachmentDescs.Length : 0,
                    PSubpasses = &subpass,
                    SubpassCount = 1,
                    PDependencies = &subpassDependency,
                    DependencyCount = 1,
                };

                gd.Api.CreateRenderPass(device, in renderPassCreateInfo, null, out RenderPass renderPass).ThrowOnError();

                return new Auto<DisposableRenderPass>(new DisposableRenderPass(gd.Api, device, renderPass));
            }
        }

        /// <summary>
        /// NEOV2 Etapa 5: variante do render pass em que os attachments do clearMask usam LoadOp.Clear.
        /// E compativel com o render pass base (so muda o load op), entao usa os mesmos framebuffers e pipelines.
        /// </summary>
        public Auto<DisposableRenderPass> GetRenderPassWithClears(VulkanRenderer gd, Device device, uint clearMask)
        {
            if (clearMask == 0)
            {
                return _renderPass;
            }

            _clearVariants ??= new Dictionary<uint, Auto<DisposableRenderPass>>();

            if (!_clearVariants.TryGetValue(clearMask, out Auto<DisposableRenderPass> variant))
            {
                variant = BuildRenderPass(gd, device, clearMask);
                _clearVariants.Add(clearMask, variant);
            }

            return variant;
        }

        public Auto<DisposableFramebuffer> GetFramebuffer(VulkanRenderer gd, CommandBufferScoped cbs, FramebufferParams fb)
        {
            FramebufferCacheKey key = new(fb.Width, fb.Height, fb.Layers);

            if (!_framebuffers.TryGetValue(ref key, out Auto<DisposableFramebuffer> result))
            {
                result = fb.Create(gd.Api, cbs, _renderPass);

                _framebuffers.Add(ref key, result);
            }

            return result;
        }

        public Auto<DisposableRenderPass> GetRenderPass()
        {
            return _renderPass;
        }

        public void AddForcedFence(TextureStorage storage, PipelineStageFlags stageFlags)
        {
            if (!_forcedFences.Any(fence => fence.Texture == storage))
            {
                _forcedFences.Add(new ForcedFence(storage, stageFlags));
            }
        }

        public void InsertForcedFences(CommandBufferScoped cbs)
        {
            if (_forcedFences.Count > 0)
            {
                for (int i = 0; i < _forcedFences.Count; i++)
                {
                    if (_forcedFences[i].Texture.Disposed)
                    {
                        _forcedFences.RemoveAt(i--);
                        continue;
                    }
                    
                    _forcedFences[i].Texture.QueueWriteToReadBarrier(cbs, AccessFlags.ShaderReadBit, _forcedFences[i].StageFlags);
                }
            }
        }

        public bool ContainsAttachment(TextureStorage storage)
        {
            return _textures.Any(view => view.Storage == storage);
        }

        public void Dispose()
        {
            // Dispose all framebuffers.

            foreach (Auto<DisposableFramebuffer> fb in _framebuffers.Values)
            {
                fb.Dispose();
            }

            // Notify all texture views that this render pass has been disposed.

            foreach (TextureView texture in _textures)
            {
                texture.RemoveRenderPass(_key);
            }

            // Dispose render pass.

            _renderPass.Dispose();

            if (_clearVariants != null)
            {
                foreach (Auto<DisposableRenderPass> variant in _clearVariants.Values)
                {
                    variant.Dispose();
                }

                _clearVariants.Clear();
            }

            _disposed = true;
        }
    }
}
