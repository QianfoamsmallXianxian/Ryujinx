# Análise estrutural: Kenji-NX, NEOV2 e Gezinjinx

Data: 18/09/2026
Dispositivo de referência: AYN Thor, Adreno 740 (FD740, a7xx_gen2), driver Turnip PurpleVK 26.1
Foco: performance (stutter e lentidão). Compatibilidade de jogos fica para uma fase posterior.

## 1. Bases comparadas

| Código | Referência | Situação |
|---|---|---|
| Kenji-NX stock | `libryujinx_bionic` @ e7cb0632 | Upstream Android |
| NEOV2 (Kenji-NX-Current) | `neo-v2-fase1` @ 4c5abb58 + árvore de trabalho não commitada (build F3A) | Stock + MUA3 V29 + slack + telemetria + VulkanSubmissionWorker |
| Gezinjinx (Ryujinx Neo) | `origin/baseline` 748f0608 (Kenji 2.1.0-pr.2) → 9cdac144 (313 commits) + árvore A9–A17 | Reescritas de submissão, sincronização, GPFIFO, MME, NCE e GAL |

Observações sobre o upstream:

- `libryujinx_bionic` tem 279 commits próprios e está 347 commits atrás de `master`.
- Os PRs do Forgejo do Kenji-NX são apenas Renovate. Os PRs de código real estão em `projects/Ryubing`.

## 2. Problemas estruturais do Kenji-NX (upstream)

### 2.1 GPU e Vulkan (maior peso no Adreno, que é TBDR)

- **Texturas sem format list (UBWC perdido)**
  - `TextureStorage.cs` (linhas 101 e 117–131) sempre cria imagens com `MUTABLE_FORMAT` e `EXTENDED_USAGE`, sem `VkImageFormatListCreateInfo`.
  - No Turnip (`tu_image.cc`, linhas 604–657), uma imagem mutável sem format list perde UBWC.
  - O FD740 (a7xx_gen2) não tem `ubwc_all_formats_compatible`. Por isso `format_list_has_swaps(NULL)` retorna verdadeiro e a imagem também é forçada para layout linear.
  - Resultado provável: texturas e render targets lineares e sem compressão, o que eleva muito o consumo de banda.
  - Status: dedução a partir do código do Mesa e do Ryujinx. Falta confirmação no aparelho.
- **Render passes sempre com Load/Store e layout General**
  - `RenderPassHolder.cs` (linhas 65–74): toda troca de render pass carrega e grava os attachments inteiros na memória.
  - Clears são feitos com `CmdClearAttachments`, não com `LoadOp.Clear`.
  - Há 19 pontos de `EndRenderPass`, o que multiplica essas cargas e gravações.
- **Barreiras removidas em feedback loop**
  - `BarrierBatch.cs` (linhas 333–354) descarta barreiras em casos de feedback loop no TBDR.
  - É um risco de hazard e um candidato a bugs gráficos.
- **Modelo de pipelines**
  - Apenas 7 a 9 dynamic states (`PipelineState.cs`, linhas 579–599). Não há graphics pipeline library.
  - O suporte a extended dynamic state foi revertido no upstream (5da6c490).
  - `VkPipelineCache` não é persistido em disco (`PipelineBase.cs`, linhas 107–112).
  - Há um `_pipelineCreateLock` global (`PipelineState.cs`, linhas 13, 373 e 646), vindo do commit 6840ae0c das correções para Adreno 8xx, e ele vale para todos os aparelhos.
- **Workaround do Turnip (8ccf83d9)**
  - `IsSignaledLazy` faz o CommandBufferPool liberar command buffers somente quando os 16 estão em uso.
  - O StagingBuffer de 32 MB fica sempre cheio e força flushes.

### 2.2 Threads do renderer e pacing

- **ThreadedRenderer**
  - `Thread.Sleep(1)` quando a fila está cheia.
  - Round-trips síncronos em `InvokeCommand`.
  - Busy-spin em `Interrupt`.
  - Falta o PR #174 (`AddTableRef` atômico).
- **SurfaceFlinger**
  - Vsync do guest dirigido por timer, com spin de 2 ms por quadro e sem relação com o vsync do painel.

### 2.3 Kernel, memória e CPU

- **Kernel**
  - CriticalSection global.
  - Preempção a cada 10 ms.
  - Fallback com spin no KTimeManager.
- **Commit 6840ae0c (aplicado a todos os aparelhos)**
  - Slack de 12 GB em `KernelInit.cs`.
  - Timeout de syncpoint elevado de 1 s para 30 s, com `Thread.Sleep(100)`.
  - `NvHostEvent FailingCountMax` 10.
  - Alterações no `AutoDeleteCache`.
- **NCE**
  - SIGSEGV + mprotect a cada escrita em página rastreada (4 KB).
  - Interrupções via `pthread_kill`.
- **MemoryOwner**
  - Lock global no pool, com lista O(n).
  - Retém arrays de até 4 vezes o tamanho pedido.

### 2.4 Camada Android

- **`frameEnded` a cada present via JNI**
  - Cria `QuickSettings`.
  - Chama `setTurboMode(true)`, que chega em `adrenotools_set_turbo`.
  - Chama `force60HzRefreshRate`, que faz `sendBroadcast`.
- **Outros pontos**
  - Performance Mode com padrão inconsistente: `true` em QuickSettings e `false` em SettingsViewModel.
  - `inputUpdate` via JNA a cada 1 ms.
  - Chamadas JNA de giroscópio na thread principal (ANR de 13/09).
- **Build**
  - Os flavors por dispositivo mudam apenas a camada C++.
  - O core .NET AOT fica fixo em `IlcInstructionSet=armv8.2-a`.

### 2.5 PRs do Ryubing

| PR | Situação na bionic |
|---|---|
| #174 (race no `AddTableRef`) | Ausente |
| #92 / #105 (tempo de vida de vertex buffer) | Revertido em a8a23b6b. Permanece o comportamento indefinido de buffer destruído antes do submit |
| #182 (guarda para coordenadas NaN) | Ausente |
| #111 | Nunca foi mergeado |
| #178 | Aberto no upstream, mas já aplicado via cherry-pick |
| #15 | Verificado e descartado |

## 3. NEOV2 (Kenji-NX-Current)

### 3.1 O que mudou em relação ao stock

- MUA3 V29.
- Slack configurável (0, 4, 8 ou 12 GiB; padrão 12), aplicado em 4 pontos.
- Telemetria `[NEOV2-BASELINE]` e arquivos de telemetria de submissão.
- Transplante do `VulkanSubmissionWorker` a partir do Gezinjinx, com dois workers e batching desligado. O arquivo difere do original em apenas 14 linhas.
- `FenceHolder` passou a esperar `_submission.Wait()` antes das fences e mantém `_alwaysWaits` para o Turnip.
- `CommandBufferPool` usa `submissionReady && entry.Fence.IsSignaledLazy()`.

### 3.2 Bug do transplante (build F3A)

- `Window.PresentOne` chama `SwapchainApi.QueuePresent` direto na fila (linha 689), fora do worker.
- `BufferManager.cs:224` chama `QueueBindSparse` direto na fila, fora do worker.
- Consequência: o present pode esperar o semáforo binário `renderFinished` antes de o worker ter enviado o command buffer que o sinaliza. Isso viola a especificação do Vulkan.
- É o candidato mais forte ao `DeviceLost` do MP4: `RYUJINX-NEO-SUBMISSION-FIRST-FAULT operation=QueueSubmit timeline=0 sequence=44551`.
- No Gezinjinx, os dois caminhos já passam pelo worker:
  - `Window.cs:785` usa `QueueSubmissionWorker.Present`.
  - `BufferManager.cs:208` usa `QueueSubmissionWorker.BindSparse`.

### 3.3 O que não mudou

- `TextureStorage`, `RenderPassHolder`, `PipelineState`, `PipelineBase`, `BarrierBatch` e `StagingBuffer` estão idênticos ao stock.
- `MainActivity.kt`, `GameHost.kt` e `PerformanceManager.kt` estão idênticos ao stock.
- A correção de `windowHandle` do A17 não foi portada.

## 4. Gezinjinx (Ryujinx Neo)

### 4.1 O que foi reescrito

- **Submissão e sincronização**
  - Worker próprio, com `QueueSubmit`, `Present` e `BindSparse` na mesma fila, em ordem.
  - A conclusão dos command buffers é checada por timeline semaphore (`FenceHolder.IsSignaled` usa `IsTimelineReached`).
- **ThreadedRenderer**
  - Espera por evento no lugar de `Thread.Sleep(1)`, com proteção contra wakeup perdido (`_producerWaiting`).
  - Fila em potência de 2 (8192), com máscara no lugar de módulo.
  - Telemetria condicionada a `DetailedTelemetry`.
- **Neo Renderer (GAL/Multithreading/Neo)**
  - Cerca de 3.000 linhas.
  - Fica desligado por padrão (`NeoRendererBuildConfiguration.Enabled = false`). Só é ativado com `-p:RyujinxNeoRendererDefault=true`, no workflow `neo-baseline`.
- **GPFIFO e MME**
  - `MethodDecoder`, `MethodRun` e `WriteRun` com laço especializado.
  - Novas estruturas de dispatch e submissão.
  - `MacroAot` e `MacroProgramAnalyzer`.
- **NCE**
  - Correções de contexto (FPSTATE, PSTATE e ctr_el0) em `NcePatcher` e nos contextos.
- **Pipelines**
  - Compilação em voo único por `PipelineUid` (`ShaderCollection.GetOrCompileGraphicsPipeline`).
  - Tempo de vida de `ShaderCollection` controlado por `Auto<>`.
- **Swapchain**
  - Tratamento de `SuboptimalKhr` como aquisição válida.
  - Gate de present separado da disponibilidade da superfície.

### 4.2 Itens resolvidos no código (ainda não medidos)

1. **Workaround `IsSignaledLazy` do Turnip**
   - Substituído por timeline semaphore. O command buffer é liberado assim que a GPU termina.
2. **`Thread.Sleep(1)` no ThreadedRenderer**
   - Substituído por espera por evento. A thread do jogo acorda assim que há espaço na fila.

### 4.3 Itens parcialmente resolvidos

- **Criação de pipelines**
  - Passou a ser em voo único, mas o `_pipelineCreateLock` global continua no caminho de criação.
- **Ordem do present**
  - Está correta no Gezinjinx. É exatamente a parte que faltou no NEOV2.

### 4.4 Custos novos introduzidos

- `_program.Get(Cbs)` em todo draw e dispatch (`PipelineBase.cs`): contagem de referência extra no hot path.
- O acquire do swapchain espera na CPU a fence do consumidor anterior daquele semáforo (`consumer.Wait()` em `Window.cs`).
- Cerca de 4.000 linhas de telemetria em Common e GPFifo. A maior parte é protegida por flag, mas é preciso auditar o que roda sempre.

### 4.5 O que não mudou

- `TextureStorage`, `RenderPassHolder`, `BarrierBatch`, `StagingBuffer`, `TextureView` e `FramebufferParams`.
- `SurfaceFlinger`, `KCriticalSection`, `MemoryOwner`, `MemoryTracking` e `NceMemoryBlock`.
- `MainActivity.kt` e `GameHost.kt` (o `frameEnded` continua igual).
- O PR #174 continua ausente.

## 5. Checklist estrutural comparado

| Problema | Stock | NEOV2 | Gezinjinx |
|---|---|---|---|
| Texturas sem format list (sem UBWC, provável layout linear) | Presente | Presente | Presente |
| Render pass Load/Store + General, clear via `CmdClearAttachments` | Presente | Presente | Presente |
| `BarrierBatch` removendo barreiras em feedback loop | Presente | Presente | Presente |
| 7–9 dynamic states, sem GPL, `VkPipelineCache` não persistido | Presente | Presente | Presente |
| `_pipelineCreateLock` global | Presente | Presente | Presente (com voo único) |
| StagingBuffer de 32 MB sempre cheio | Presente | Presente | Presente |
| Workaround `IsSignaledLazy` do Turnip | Presente | Presente | **Resolvido** |
| `Thread.Sleep(1)` no ThreadedRenderer | Presente | Presente | **Resolvido** |
| Present e BindSparse fora da ordem do worker | Não se aplica | **Bug** | Correto |
| Vsync do SurfaceFlinger por timer com spin de 2 ms | Presente | Presente | Presente |
| `frameEnded` via JNI a cada present | Presente | Presente | Presente |
| CriticalSection global, MemoryOwner, rastreamento NCE por página | Presente | Presente | Presente |
| PR #174 (`AddTableRef` atômico) | Ausente | Ausente | Ausente |

## 6. Conclusão

- **O que as reescritas cobriram:** as duas linhas melhoraram o caminho que leva o trabalho até a GPU (submissão, sincronização, frontend GPFIFO/MME).
- **O que ficou de fora:** nenhuma das duas alterou o custo do próprio trabalho na GPU.
- **Por que o ganho foi pequeno:** no Adreno, que é TBDR, o gargalo típico é banda de memória. Texturas sem UBWC, render passes que carregam e gravam tudo a cada troca e pipelines pouco dinâmicos continuam iguais ao stock. Essa é a explicação mais provável para as reescritas não terem trazido ganho perceptível.

## 7. Plano de commits proposto (NEOV2)

Regras:

- Uma mudança por APK.
- Um commit por etapa.
- Comparação contra o stock e gate de regressão.
- Escopo limitado ao hot path do Vulkan e à telemetria.

| Ordem | Commit | Tipo | Origem |
|---|---|---|---|
| 1 | `Present` e `BindSparse` pelo `VulkanSubmissionWorker` | Correção (DeviceLost) | Portar do Gezinjinx |
| 2 | Timeline semaphore no lugar de `IsSignaledLazy` | Performance (CPU e sincronização) | Portar do Gezinjinx |
| 3 | Espera por evento no lugar de `Thread.Sleep(1)` no ThreadedRenderer | Performance (CPU) | Portar do Gezinjinx |
| 4 | `VkImageFormatListCreateInfo` em `TextureStorage` (UBWC) | Performance (banda de GPU) | Novo |
| 5 | Render passes com `LoadOp.Clear` / `DontCare` quando possível | Performance (banda de GPU) | Novo |
| 6 | Persistência do `VkPipelineCache` | Performance (stutter de compilação) | Novo |
| 7 | `frameEnded` sem trabalho JNI por quadro | Performance (CPU no Android) | Novo |

Cada item só avança depois de o anterior ser medido contra o stock.
