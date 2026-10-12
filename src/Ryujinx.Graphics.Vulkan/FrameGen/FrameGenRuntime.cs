using Ryujinx.Common.Diagnostics;
using Ryujinx.Common.Logging;
using Ryujinx.Graphics.GAL;
using Silk.NET.Vulkan;
using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Threading;

namespace Ryujinx.Graphics.Vulkan.FrameGen
{
    internal readonly struct FrameGenOutput
    {
        public readonly TextureView Texture;
        public readonly long DueTicks;

        public FrameGenOutput(TextureView texture, long dueTicks)
        {
            Texture = texture;
            DueTicks = dueTicks;
        }
    }

    // NEOV2-FRAMEGEN1: o quadro real sempre e apresentado. O credito distribui no maximo
    // um intermediario por quadro real, sem ultrapassar o teto pedido pelo jogo.
    internal sealed class FrameGenRuntime : IDisposable
    {
        private const double PanelRate = 60.0;
        private const int StableFramesRequired = 30;   // intervalo de troca estavel antes de ligar
        private const int WarmFramesRequired = 3;      // historico de 3 posicoes preenchido
        private const double MaxFrameSeconds = 0.25;   // reinicia apos uma pausa longa
        private const double Smoothing = 0.25;
        private const double MaxMultiplier = 2.0;      // um gerado, no maximo, por quadro real

        private static long _presentedTotal;

        public static void CountPresented() => Interlocked.Increment(ref _presentedTotal);

        private readonly VulkanRenderer _gd;
        private readonly Silk.NET.Vulkan.Device _device;

        private readonly bool _enabled;
        private bool _failed;
        private Dictionary<uint, uint[]> _modules;
        private LsfgChain _chain;
        private readonly TextureView[] _ring = new TextureView[LsfgChain.OutputRing];
        private int _ringNext;
        private readonly FenceHolder[] _ringUseFences = new FenceHolder[LsfgChain.OutputRing];
        private uint _width, _height;

        private long _lastFrameTicks;
        private double _smoothed;
        private int _lastSwap = int.MinValue;
        private int _stable;
        private int _warm;
        private ulong _count;
        private bool _engaged;
        private double _extraCredit;

        private long _statsTicks;
        private long _statsReal, _statsGen, _statsPresentedBase;
        private long _rateTicks;
        private long _rateBase;

        public bool Active => _enabled && !_failed;

        public FrameGenRuntime(VulkanRenderer gd, Silk.NET.Vulkan.Device device)
        {
            _gd = gd;
            _device = device;

            string path = NeoGraphicsSettings.FrameGenDllPath;
            bool wanted = NeoGraphicsSettings.FrameGenEnabled;
            LosslessStatus status = LosslessStatus.NotInstalled;

            if (wanted)
            {
                try
                {
                    status = LosslessDll.Load(path, out _modules);
                }
                catch (Exception ex)
                {
                    Logger.Warning?.Print(LogClass.Gpu, $"[NEOV2-FRAMEGEN1] falha ao ler a DLL: {ex.Message}");
                    status = LosslessStatus.UnreadableFile;
                }
            }

            _enabled = wanted && status == LosslessStatus.Ok;
            Logger.Notice.Print(LogClass.Gpu,
                $"[NEOV2-FRAMEGEN1] {(_enabled ? "ligado" : "desligado")}, dll={(!wanted ? "nao usada" : status == LosslessStatus.Ok ? "ok" : "erro: " + LosslessDll.Describe(status))}");

            _statsTicks = Stopwatch.GetTimestamp();
            _rateTicks = _statsTicks;
            _rateBase = Interlocked.Read(ref _presentedTotal);
            _statsPresentedBase = _rateBase;
        }

        // Contador de FPS do app: com o framegen ligado mostra a taxa apresentada (reais + gerados).
        private void UpdatePresentedRate()
        {
            long now = Stopwatch.GetTimestamp();
            double seconds = (now - _rateTicks) / (double)Stopwatch.Frequency;
            if (seconds < 1.0)
            {
                return;
            }

            long presented = Interlocked.Read(ref _presentedTotal);
            NeoGraphicsSettings.FrameGenPresentedFps = (presented - _rateBase) / seconds;
            _rateBase = presented;
            _rateTicks = now;
        }

        private void Fail(string reason)
        {
            NeoGraphicsSettings.FrameGenPresentedFps = 0;
            if (_failed)
            {
                return;
            }

            _failed = true;
            Logger.Warning?.Print(LogClass.Gpu, $"[NEOV2-FRAMEGEN1] desligado nesta sessao: {reason}");
            DisposeChain();
        }

        private void DisposeChain()
        {
            try
            {
                _chain?.Dispose();
                for (int i = 0; i < _ring.Length; i++)
                {
                    _ringUseFences[i]?.Put();
                    _ringUseFences[i] = null;
                    _ring[i]?.Dispose();
                    _ring[i] = null;
                }
            }
            catch (Exception ex)
            {
                Logger.Warning?.Print(LogClass.Gpu, $"[NEOV2-FRAMEGEN1] erro ao liberar a cadeia: {ex.Message}");
            }

            _chain = null;
        }

        // A apresentacao le a textura gerada; ela so pode ser reescrita apos o fence do blit.
        public void TrackOutputUse(TextureView output, FenceHolder fence)
        {
            if (output == null)
            {
                return;
            }

            for (int i = 0; i < _ring.Length; i++)
            {
                if (ReferenceEquals(_ring[i], output) && fence.TryAddReference())
                {
                    _ringUseFences[i]?.Put();
                    _ringUseFences[i] = fence;
                    return;
                }
            }
        }

        private void WaitForOutputReuse(int ring)
        {
            FenceHolder fence = _ringUseFences[ring];
            if (fence != null)
            {
                try
                {
                    if (!fence.IsSignaledLazy())
                    {
                        fence.Wait();
                    }
                }
                finally
                {
                    _ringUseFences[ring] = null;
                    fence.Put();
                }
            }
        }

        /// <summary>
        /// Chamado a cada quadro real. Devolve true quando ha um intermediario para apresentar
        /// antes do quadro real. O quadro real deve ser apresentado em todos os casos.
        /// </summary>
        public bool OnRealFrame(TextureView src, List<FrameGenOutput> outputs)
        {
            outputs.Clear();
            if (!Active)
            {
                return false;
            }

            UpdatePresentedRate();

            try
            {
                return Process(src, outputs);
            }
            catch (Exception ex)
            {
                outputs.Clear();
                Fail(ex.Message);
                return false;
            }
            finally
            {
                LogStats();
            }
        }

        private void ResetState()
        {
            _warm = 0;
            _smoothed = 0;
            _engaged = false;
            _extraCredit = 0;
        }

        private bool Process(TextureView src, List<FrameGenOutput> outputs)
        {
            _statsReal++;

            long now = Stopwatch.GetTimestamp();
            double dt = _lastFrameTicks == 0 ? 0 : (now - _lastFrameTicks) / (double)Stopwatch.Frequency;
            _lastFrameTicks = now;

            // Fonte confiavel do teto: o intervalo de troca pedido pelo proprio jogo.
            int swap = NeoGraphicsSettings.GuestSwapInterval;
            _stable = swap == _lastSwap ? _stable + 1 : 0;
            _lastSwap = swap;

            bool swapEligible = swap is 0 or 1 or 2 && _stable >= StableFramesRequired;
            TextureCreateInfo info = src.Info;
            bool formatOk = info.Samples <= 1 && info.Target == Target.Texture2D && !info.Format.IsDepthOrStencil() &&
                !info.IsCompressed && src.Valid && src.VkFormat == src.Storage.VkFormat &&
                (src.Storage.UsageFlags & ImageUsageFlags.TransferSrcBit) != 0 &&
                _gd.FormatCapabilities.OptimalFormatSupports(FormatFeatureFlags.BlitSrcBit | FormatFeatureFlags.SampledImageFilterLinearBit, info.Format) &&
                _gd.FormatCapabilities.OptimalFormatSupports(FormatFeatureFlags.BlitDstBit, Ryujinx.Graphics.GAL.Format.R8G8B8A8Unorm);

            if (!swapEligible || !formatOk || dt <= 0 || dt > MaxFrameSeconds)
            {
                ResetState();
                return false;
            }

            _smoothed = _smoothed > 0 ? _smoothed + (dt - _smoothed) * Smoothing : dt;

            double ceiling = PanelRate / Math.Max(1, swap);
            double realRate = 1.0 / _smoothed;
            double outRate = Math.Min(ceiling, MaxMultiplier * realRate);

            // O teto vem do swap interval do jogo, nao de valores de FPS usados como exemplos.
            _engaged = outRate > realRate;
            if (!_engaged)
            {
                _warm = 0;
                _extraCredit = 0;
                return false;
            }

            EnsureChain(src);

            // Credito fracionario: com 20 reais e teto 30, gera em ciclos alternados.
            // Com 15 reais e teto 30, gera em todo ciclo; nunca gera mais de um.
            bool generate = false;
            int ring = 0;
            if (_warm + 1 >= WarmFramesRequired)
            {
                _extraCredit += Math.Clamp(outRate / realRate - 1.0, 0.0, 1.0);
                if (_extraCredit >= 1.0 - 1e-6)
                {
                    _extraCredit = Math.Max(0.0, _extraCredit - 1.0);
                    ring = _ringNext++ % LsfgChain.OutputRing;
                    WaitForOutputReuse(ring);
                    generate = true;
                }
            }

            CommandBufferScoped cbs = _gd.CommandBufferPool.Rent();
            try
            {
                CommandBuffer cb = cbs.CommandBuffer;
                Image srcImage = src.GetImage().Get(cbs).Value;
                _chain.DispatchShared(cb, srcImage, (uint)src.Width, (uint)src.Height, (uint)src.FirstLevel, (uint)src.FirstLayer, _count);

                if (generate)
                {
                    Image outImage = _ring[ring].GetImage().Get(cbs).Value;
                    _chain.DispatchGeneration(cb, _count, LsfgChain.MidpointSlot, ring, outImage, _width, _height);
                    outputs.Add(new FrameGenOutput(_ring[ring], 0));
                }
            }
            finally
            {
                _gd.CommandBufferPool.Return(cbs, null, null, null);
            }

            _count++;
            _warm++;
            _statsGen += outputs.Count;

            return generate;
        }

        private void EnsureChain(TextureView src)
        {
            uint w = (uint)src.Width, h = (uint)src.Height;
            if (_chain != null && _width == w && _height == h)
            {
                return;
            }

            DisposeChain();

            Logger.Notice.Print(LogClass.Gpu,
                $"[NEOV2-FRAMEGEN1] fonte: {w}x{h}, formato={src.VkFormat}, mip={src.FirstLevel}, camada={src.FirstLayer}");

            _width = w;
            _height = h;

            ImageView[] views = new ImageView[_ring.Length];
            CommandBufferScoped cbs = _gd.CommandBufferPool.Rent();
            try
            {
                for (int i = 0; i < _ring.Length; i++)
                {
                    TextureCreateInfo oi = new(
                        (int)w, (int)h, 1, 1, 1, 1, 1, 4,
                        Ryujinx.Graphics.GAL.Format.R8G8B8A8Unorm, DepthStencilMode.Depth, Target.Texture2D,
                        SwizzleComponent.Red, SwizzleComponent.Green, SwizzleComponent.Blue, SwizzleComponent.Alpha);

                    _ring[i] = _gd.CreateTextureView(oi);
                    views[i] = _ring[i].GetIdentityImageView().Get(cbs).Value;
                }
            }
            finally
            {
                _gd.CommandBufferPool.Return(cbs, null, null, null);
            }

            // Mapa de fluxo em resolucao reduzida (custo na GPU do Adreno): 25% acima de 1400 de largura, 50% abaixo.
            float flowScale = w > 1400 ? 0.25f : 0.5f;
            _chain = new LsfgChain(_gd, _device, _modules, w, h, flowScale, views);

            ResetState();
            _count = 0;
            _ringNext = 0;
        }

        private void LogStats()
        {
            long now = Stopwatch.GetTimestamp();
            double seconds = (now - _statsTicks) / (double)Stopwatch.Frequency;
            if (seconds < 5.0)
            {
                return;
            }

            long presented = Interlocked.Read(ref _presentedTotal);
            Logger.Notice.Print(LogClass.Gpu,
                $"[NEOV2-FRAMEGEN1] 5s: reais={_statsReal / seconds:F1}/s gerados={_statsGen / seconds:F1}/s apresentados={(presented - _statsPresentedBase) / seconds:F1}/s swap={_lastSwap}");

            _statsTicks = now;
            _statsReal = 0;
            _statsGen = 0;
            _statsPresentedBase = presented;
        }

        public void Dispose()
        {
            NeoGraphicsSettings.FrameGenPresentedFps = 0;
            DisposeChain();
        }
    }
}
