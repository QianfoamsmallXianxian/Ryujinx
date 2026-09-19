# Ryujinx Neo (Kenji-NX Fork)

## VisÃ£o Geral
Este repositÃ³rio Ã© o fork **Ryujinx Neo**, baseado na ramificaÃ§Ã£o Android do **Kenji-NX**. O objetivo deste fork Ã© aprimorar a estabilidade, vazÃ£o de submissÃ£o e eficiÃªncia de execuÃ§Ã£o da emulaÃ§Ã£o no ambiente Android mÃ³vel, com foco prioritÃ¡rio em arquiteturas TBDR (GPUs Adreno/Turnip).

## Alvo e Arquitetura
- **Plataforma alvo:** Android (Linux Bionic / arm64-v8a)
- **Backend grÃ¡fico:** Vulkan com suporte a timeline semaphores e otimizaÃ§Ãµes direcionadas a TBDR (Adreno 7xx / Turnip)
- **Branch atual:** `neo-v2-fase1` (NEOV2 - Fase 1 Experimental)

## Melhorias Implementadas na Branch NEOV2
1. **Vulkan Submission Worker Dedicado:**
   - Desacoplamento da fila de submissÃ£o Vulkan do thread grÃ¡fico principal via fila FIFO thread-safe (`VulkanSubmissionWorker`).
   - SincronizaÃ§Ã£o e apresentaÃ§Ã£o ordenadas sem bloquear o loop de emulaÃ§Ã£o prematuramente.
2. **Timeline Semaphores e Wait Coalescing:**
   - ReduÃ§Ã£o de overhead de chamadas de espera repetitivas via cache de geraÃ§Ã£o de conclusÃ£o (`TimelineCompletionGenerationCache`).
3. **OtimizaÃ§Ãµes para TBDR (Adreno):**
   - Suporte a limpezas de render pass adiadas (`Deferred Clears`) aproveitando `LoadOp.Clear` nativo, reduzindo passagens de load/store desnecessÃ¡rias.
   - AplicaÃ§Ã£o de `ExtendedDynamicState` para reduÃ§Ã£o de recompilaÃ§Ã£o de pipelines grÃ¡ficos.
   - PersistÃªncia controlada de cache de pipelines.
4. **Memory Slack ConfigurÃ¡vel:**
   - Adicionada opÃ§Ã£o configurÃ¡vel na interface Android e repassada ao HLE (`MemorySlack`), permitindo folga dinÃ¢mica de memÃ³ria no espaÃ§o de endereÃ§amento da aplicaÃ§Ã£o (4 GiB, 8 GiB, 12 GiB).
5. **Telemetria DiagnÃ³stica de GPU:**
   - InstrumentaÃ§Ã£o leve para anÃ¡lise de trÃ¡fego de buffers, formatos de textura, espera de sincronizaÃ§Ã£o e geraÃ§Ã£o de timeline semaphores.

## AlteraÃ§Ãµes e Experimentos do Marvel Ultimate Alliance 3 (MUA3)
- **Telemetria Seletiva por Title ID:**
  - DiagnÃ³stico e rastreamento de pipeline e shaders restritos estritamente ao Title ID do jogo.
- **Experimento de SincronizaÃ§Ã£o `[MUA3-SYNC]`:**
  - **Estado:** Estritamente Experimental.
  - **DescriÃ§Ã£o:** InserÃ§Ã£o de barreiras de sincronizaÃ§Ã£o globais (compute-to-scene memory barriers) antes de operaÃ§Ãµes de draw para assegurar transiÃ§Ãµes de memÃ³ria de compute shaders.
  - **Resultado Observado:** O teste confirmou que as barreiras sÃ£o acionadas e registradas corretamente nos pontos crÃ­ticos, contudo **nÃ£o foi suficiente para mitigar ou resolver o erro de `DeviceLost`** especÃ­fico no driver grÃ¡fico. O cÃ³digo permanece como registro de pesquisa e investigaÃ§Ã£o de sincronizaÃ§Ã£o grÃ¡fica.

## InstruÃ§Ãµes de CompilaÃ§Ã£o
A compilaÃ§Ã£o do APK Android deve ser realizada atravÃ©s do Gradle Wrapper no diretÃ³rio do projeto Android:

```powershell
Set-Location -LiteralPath "src\KenjinxAndroid"
.\gradlew.bat assembleMainlineArmv82Release
```

O arquivo APK final Ã© gerado em:
`src/KenjinxAndroid/app/build/outputs/apk/mainlineArmv82/release/`

## CrÃ©ditos e Upstream
- **Ryujinx Project:** Criado originalmente por **gdkchan** e equipe de desenvolvedores Ryujinx.
- **Kenji-NX:** Projeto de compatibilidade e portabilidade Android continuado pela comunidade Kenji-NX / Ryubing.
- Este projeto respeita e preserva integralmente as licenÃ§as originais (MIT License), crÃ©ditos upstream e atribuiÃ§Ãµes de direitos autorais contidas no arquivo [LICENSE.txt](../LICENSE.txt).
