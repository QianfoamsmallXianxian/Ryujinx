# Ryujinx Neo — Relatório técnico completo do projeto

- **Projeto:** Ryujinx Neo, fork Android derivado do Kenji-NX
- **Repositório:** [Sunshinin11/Ryujinx_Neo](https://github.com/Sunshinin11/Ryujinx_Neo)
- **Branch analisada:** `neo-v2-nce2` no GitHub e no checkout `Kenji-NX-Current`
- **Último código publicado:** [`a24f5d1e7`](https://github.com/Sunshinin11/Ryujinx_Neo/commit/a24f5d1e792f3b8718686359f502def5e401e810) (27/09/2026)
- **Current local analisado:** cinco commits criados em 29/09/2026 para as mudanças funcionais de 28/09, ainda não enviados ao GitHub
- **Data desta revisão:** 29/09/2026
- **Versão Android no código:** `0.0.1`, `versionCode 20108` ([build.gradle](https://github.com/Sunshinin11/Ryujinx_Neo/blob/neo-v2-nce2/src/KenjinxAndroid/app/build.gradle))
- **Dispositivo histórico de referência:** AYN Thor, Snapdragon 8 Gen 2, Adreno 740
- **Plataforma em foco:** Android arm64-v8a / Linux Bionic, Vulkan em Turnip/Adreno

> As seções 5–10 e as métricas por jogo preservam o histórico até 20/09. As seções 11–17 distinguem o código publicado na branch GitHub do estado funcional observado no checkout **Kenji-NX-Current** em 28/09. Os cinco commits de código criados localmente em 29/09 ainda não foram enviados ao GitHub. Os registros de build da data confirmam compilação Android; os testes ocorreram no Experimental antes da promoção e não foram repetidos nesta revisão.

---

## 1. Resumo executivo

O Ryujinx Neo é um fork Android do Kenji-NX com trabalho próprio no backend Vulkan, NCE, compatibilidade gráfica e interface. A linha **Neo Current** está em `neo-v2-nce2`; o código Android declara versão `0.0.1` (`versionCode 20108`). O [changelog](../CHANGELOG.md) lista as mudanças por período, incluindo o trabalho funcional de 28/09 encontrado no Current local.

As frentes implementadas na branch incluem:

1. **GPU/Vulkan:** worker de submissão, ajustes de sincronização, cache de pipelines e texturas, caminho VTG via Compute, offset exato de texture buffer no Turnip, upload 3D assíncrono e chaves de compatibilidade.
2. **CPU/NCE:** arquitetura NCE Neo 2, proteção opcional de rastreamento da GPU após mudanças de permissão e conversão seletiva de acessos exclusivos.
3. **Android:** identidade Ryujinx Neo, dois layouts da biblioteca, perfis por Title ID, gerenciador de drivers Vulkan e controles globais/por jogo.
4. **Diagnóstico:** categorias de log configuráveis, com nove chaves desligadas por padrão na linha Current e controles Guest/Debug expostos no checkout local.

Os benchmarks e relatos anteriores a 23/09 continuam úteis como contexto, mas não representam uma medição do snapshot analisado. Em especial, a tela preta de *Marvel Ultimate Alliance 3* no NCE era uma observação anterior às mudanças [NCE-PROT1](https://github.com/Sunshinin11/Ryujinx_Neo/commit/b614253ae) e [TEXBUF-ALIGN1](https://github.com/Sunshinin11/Ryujinx_Neo/commit/f0a4f81be). Os commits relatam correções para esse caminho; esta revisão não executou o jogo para confirmar o resultado final.

Em 28/09, o Current local recebeu nova página de configurações por jogo, interface responsiva, correções de seleção de DLC/atualizações, modo sem internet por jogo, ajustes NCE/memória e envio seletivo de regiões de texturas 2D grandes. Essas mudanças foram validadas no Experimental antes da promoção ao Current. Os builds Android `MainlineArmv8Release` terminaram com sucesso às 21:09 e 21:36; esta revisão não repetiu os testes do Experimental.

---

## 2. Identidade, origem e escopo

### 2.1 Origem

O fork parte da linha Android/Bionic do Kenji-NX, que por sua vez deriva do ecossistema Ryujinx/Ryubing. O trabalho do Ryujinx Neo não substitui os créditos nem as licenças upstream.

| Elemento | Papel no projeto |
|---|---|
| Ryujinx | Base arquitetural original do emulador |
| Ryubing | Continuidade comunitária e fonte de correções estudadas |
| Kenji-NX | Base Android/Bionic do fork atual |
| Gezinjinx/Ryujinx Neo anterior | Fonte de ideias e implementações experimentais de submissão, NCE e GPU |
| Eden/Strato/ChimeraNX | Referências comparativas de comportamento, NCE e interface; não fazem parte do repositório |
| Ryujinx Neo atual | Fork mantido no repositório `Sunshinin11/Ryujinx_Neo` |

### 2.2 Objetivo

Criar uma versão Android do Ryujinx que seja:

- eficiente em SoCs Snapdragon/Adreno;
- estável em sessões reais de jogo;
- configurável por título;
- adequada a dispositivos portáteis como Thor, Odin 2 e Odin 3;
- visualmente própria e simples de usar;
- transparente sobre mudanças experimentais e riscos de regressão.

### 2.3 Fora do escopo atual

- distribuição de jogos, firmware ou chaves;
- inclusão de APKs no repositório;
- mistura de código de outros forks sem seleção e atribuição;
- otimizações universais sem validação mínima;
- uso do JIT como padrão enquanto o custo térmico permanecer alto.

---

## 3. Repositório, branches e pontos de recuperação

### 3.1 Repositório verificado

A branch padrão consultada no GitHub é `neo-v2-nce2`. No checkout local **Kenji-NX-Current**, cinco commits criados em 29/09 sobre `a24f5d1e7` registram os 18 arquivos de código alterados em 28/09. O HEAD local está em `8bd3d0369`; esses commits ainda não foram enviados ao GitHub. Este relatório distingue esse estado local dos commits publicados; o Experimental é o ambiente de validação prévia das mudanças promovidas ao Current, conforme a seção 5.4.

### 3.2 Linhas e tags históricas

| Referência | Papel registrado |
|---|---|
| `libryujinx_bionic` | Linha Android/Bionic de referência |
| `neo-v2-baseline` | Baseline experimental NEO V2 |
| `neo-v2-fase1` | Etapas históricas de desempenho Vulkan/GPU |
| `neo-v2-nce2` | Linha Current documentada aqui |
| `neo-v2-mua3-stable-pre-nce2-20260919` | Snapshot anterior ao NCE Neo 2 (`f8d0b8e06`) |
| `neo-v2-nce2-r3-pre-ui-20260919` | NCE2 R3 e Loop Safety antes da reforma visual (`7cb21885e`) |

As referências antigas são pontos de comparação do histórico. O estado publicado corresponde ao snapshot GitHub indicado no início; o estado funcional local de 28/09 é detalhado na seção 12.

---

## 4. Arquitetura resumida

```text
Interface Android / Jetpack Compose
        |
        | preferências globais e por Title ID
        v
KenjinxNative.kt / JNI / LibKenjinx
        |
        +--> HLE, memória e serviços do Switch
        +--> CPU: NCE ou JIT
        +--> GPU guest / GAL
                    |
                    v
              Backend Vulkan
                    |
                    +--> shaders SPIR-V
                    +--> pipelines e cache
                    +--> command buffers
                    +--> worker de submissão
                    +--> Turnip / Adreno 740
```

### 4.1 Componentes principais

| Área | Diretórios/arquivos centrais | Responsabilidade |
|---|---|---|
| Aplicativo Android | `src/KenjinxAndroid/app` | Biblioteca, configurações, controles, ciclo do app e JNI |
| Ponte nativa | `src/LibKenjinx` | Criação da sessão e transporte das opções Android para o core |
| CPU | `src/Ryujinx.Cpu` | NCE, JIT, sinais, contexto e relógio |
| GPU guest | `src/Ryujinx.Graphics.Gpu` | Estado da GPU emulada, cache e shaders |
| Tradutor de shader | `src/Ryujinx.Graphics.Shader` | Tradução e geração SPIR-V |
| Backend Vulkan | `src/Ryujinx.Graphics.Vulkan` | Imagens, pipelines, barreiras, render passes e submissão |
| HLE | `src/Ryujinx.HLE` | Kernel e serviços do sistema emulado |
| Código comum | `src/Ryujinx.Common` | Memória, temporização, logging e utilidades |

---

## 5. Método de desenvolvimento e regras operacionais

O projeto adotou um fluxo rígido depois que builds experimentais demais passaram a consumir tempo sem produzir informação suficiente.

### 5.1 Fluxo esperado

1. Analisar código, comparação upstream e logs existentes.
2. Definir uma hipótese que possa ser confirmada ou rejeitada.
3. Fazer uma mudança real acompanhada da telemetria estritamente necessária.
4. Entregar o código para compilação externa.
5. Testar no mesmo aparelho, driver, modo e trecho do jogo.
6. Guardar log e registrar o resultado, inclusive quando negativo.
7. Manter, ajustar ou reverter.

### 5.2 Regras consolidadas

- O Codex **não compila APKs** deste projeto.
- A compilação é feita externamente pelo Antigravity.
- Builds exclusivamente de telemetria são proibidas.
- APKs não devem ser versionados no Git.
- O repositório deve conter apenas o fork trabalhado, sem cópias completas de Ryubing, Gezinjinx, Eden ou outros projetos.
- Mudanças de NCE, Vulkan, shaders e interface devem ser separadas sempre que possível.
- Antes de uma rodada arriscada, criar branch/tag de recuperação.
- Resultado negativo é informação e deve ser documentado.

### 5.3 Variante de build

A variante atual confirmada em `src/KenjinxAndroid/app/build.gradle` e nos logs `android_build_current_doom1_fix.log` e `android_build_current_doom1b.log` é `assembleMainlineArmv8Release` (`MainlineArmv8Release`).

A variante `assembleMainlineArmv82Release` fica registrada como histórica. O comando de build não é executado durante alterações feitas pelo Codex; fica sob responsabilidade do compilador externo designado pelo usuário.

### 5.4 Fluxo Experimental → Current

- Toda mudança é implementada e testada no checkout Experimental.
- A promoção ao Current só ocorre após aprovação e ausência de regressões.
- O Current recebe apenas código já validado no Experimental.
- Esta documentação analisa o checkout Current; os registros de teste do Experimental não fazem parte desta revisão.

---

## 6. Fase de desempenho Vulkan — etapas históricas

Esta seção registra as etapas 1 a 23. Ela descreve o histórico da fase, não afirma que cada arquivo permaneceu idêntico depois das limpezas posteriores.

### 6.1 Etapas 1 a 13 — caminho gráfico

| Etapa | Mudança | Resultado histórico | Decisão na época |
|---|---|---|---|
| 1 | `Present` e `BindSparse` pelo worker | Corrigiu a ordenação do caminho de submissão | Mantida |
| 2 | Timeline semaphores | Reduziu dependência do modelo anterior de fences | Mantida |
| 3 | Espera por evento no `ThreadedRenderer` | Removeu `Thread.Sleep(1)` do caminho estudado | Mantida |
| 4B | Listas de formatos de textura | MK11: 239 para 98 ms; TOTK: 410 para 111 ms nos piores intervalos medidos | Mantida |
| 5 | Clears usando `LoadOp.Clear` quando possível | Menos leitura inútil antes de apagar attachment | Mantida |
| 6 | Cache persistente de pipelines | Carregamento observado de 16,9 s para 0,7 s | Mantida |
| 7 | Trabalho Android por quadro reduzido | Menos JNI e menos operações repetidas por apresentação | Mantida |
| 8 | Criação paralela de pipelines | Compilação historicamente cerca de 44% mais rápida | Mantida, mas sempre sob observação |
| 9 | Fila de referências sem trava | Redução esperada de contenção | Mantida |
| 10 | Preservação de barreiras em feedback loop | Corrigiu o bug visual observado no MK11 | Mantida |
| 11 | Staging buffer de 32 para 128 MB | Neutro nos testes; aumenta pressão de memória | Mantida na fase, mas não é vitória comprovada |
| 12 | Espera de VSync sem busy-spin prolongado | FPS neutro; expectativa de menor custo de CPU | Mantida |
| 13 | Listas compatíveis com a classe UBWC da Adreno | Menos stutter e forte melhora do MK11 | Mantida |

### 6.2 Etapas 14 a 19 — submissão e flush

| Etapa | Mudança | Resultado histórico | Decisão na época |
|---|---|---|---|
| 14 | Batching de submissões Vulkan | Melhor resultado isolado do TOTK na fase: 17,4 FPS no cenário medido | Mantida |
| 15 | Auto-flush de 1 para 3 ms | TOTK caiu para 14,8 FPS | Revertida |
| 16 | Origem dos flushes | Permitiu identificar os pontos que forçavam submissão | Mantida como diagnóstico associado ao sistema real |
| 17 | Sync estrito esperando flush natural | Link's Awakening subiu de 40,3 para 43,8 FPS no teste | Mantida |
| 18 | Cache de texturas maior | Eliminou quedas para 11 FPS em carregamentos do TOTK | Mantida |
| 19 | Remoção de flush duplicado após present | Reduziu submissões; FPS neutro | Mantida na época |

### 6.3 Etapas 20 a 23 — CPU, memória e pipelines

| Etapa | Mudança | Resultado histórico | Estado/risco |
|---|---|---|---|
| 20 | Revisão da `CriticalSection` | Nenhuma mudança aplicada | Análise encerrada |
| 21 | `MemoryOwner` usando `ArrayPool<T>.Shared` | Melhor teste do LA: 45,3 FPS | Mantida |
| 22 | Leitura nativa do contador do host | FPS neutro; houve um crash plausivelmente relacionado em um teste | Exige cautela |
| 23 | Mais estados dinâmicos no Turnip | Resultado inicial modesto; risco de erro gráfico silencioso | Requer validação visual ampla |

### 6.4 Leitura conservadora dos ganhos

**Muito provável:**

- redução do tempo de carregamento do cache de pipelines;
- correção visual do MK11;
- redução de leituras/recarregamentos de textura;
- redução de stutter pelas listas de formato e compatibilidade UBWC.

**Provável:**

- melhora forte do MK11;
- melhora de estabilidade e fluidez do Link's Awakening em determinados trechos;
- redução de overhead de submissão com batching.

**Modesto ou não comprovado:**

- ganho final do TOTK;
- vantagem isolada das etapas neutras em FPS;
- ausência de regressões em todos os jogos.

O relatório original usou muitas execuções únicas. Temperatura, clocks, estado de cache e processos em segundo plano podem alterar bastante os números em um portátil Android. Por isso, os resultados acima são evidências de engenharia, não benchmarks científicos definitivos.

---

## 7. Memory Slack e gerenciamento de memória

O Memory Slack amplia o espaço de endereçamento reservado pelo emulador. A interface foi simplificada para dois estados úteis:

| Estado | Valor enviado ao core |
|---|---:|
| Desativado | 0 GiB adicionais |
| Ativado | 12 GiB adicionais |

O valor passa da interface Android por `KenjinxNative.kt`, JNI e `LibKenjinx`, chegando a `HLEConfiguration` e aos limites de memória do processo emulado.

No estado atual:

- existe opção global;
- existe override por jogo;
- preferências antigas são migradas;
- a interface usa switch em vez de quatro opções grandes;
- o tamanho de DRAM, que é uma configuração diferente, usa um seletor compacto.

Memory Slack não cria RAM física e não deve ser descrito como aumento real de memória do dispositivo.

---

## 8. Investigação do Marvel Ultimate Alliance 3

> Esta seção registra a investigação anterior às correções de 23/09. Para o estado da branch Current, consulte as seções 12 e 13. O experimento `[MUA3-SYNC]` e as hipóteses descartadas continuam apenas como histórico.

### 8.1 Sintomas históricos

- corrupção/explosão de vértices em determinadas versões;
- `VK_ERROR_DEVICE_LOST` na cena inicial de combate;
- tela preta no NCE;
- o jogo aparece no JIT, embora com desempenho muito baixo em determinadas builds de proteção;
- comportamento diferente entre JIT e NCE mesmo com o mesmo driver Vulkan.

Title ID usado na investigação: `010060700AC50000`.

### 8.2 Correção V29

O caminho V29 força o geometry shader conhecido em `0x100719F30` para conversão Vertex+Geometry via compute. No código atual, a ativação pode ser reconhecida pelo log:

```text
[NEOV2-V29] Forcing geometry=0x100719F30 to Vertex+Geometry as Compute
```

Esse tratamento é importante para evitar a corrupção de vértices observada. Entretanto, testes posteriores mostraram que simplesmente pular os draws associados a esse geometry shader não eliminava o `DeviceLost`. Portanto:

- V29 tem valor visual/funcional;
- V29 não foi comprovada como causa nem como correção do hang de GPU original;
- o problema de tela preta no NCE é uma linha separada de investigação.

### 8.3 Hipóteses testadas e descartadas para o DeviceLost

| Hipótese | Teste | Resultado |
|---|---|---|
| O geometry shader `0x100719F30` trava a GPU | Mais de 31 mil draws associados foram pulados | `DeviceLost` continuou |
| Vertex buffer resolvido no momento errado | Correção inspirada nos PRs #92/#105 | Não resolveu |
| Criação paralela de pipelines trava o driver | Etapa 8 desativada no teste | Não resolveu |
| Acesso fora dos limites do buffer | `robustBufferAccess` ativado em Qualcomm | Não resolveu |
| Fence específica do Eden | Teste histórico no fork anterior | Não resolveu |
| Barreira global compute→draw | Experimento `[MUA3-SYNC]` | Não resolveu |

As mudanças experimentais MUA3B, MUA3C, MUA3D, telemetria específica e `[MUA3-SYNC]` foram removidas na limpeza que produziu o snapshot estável anterior ao NCE2.

### 8.4 AHB bus error

O erro AHB apareceu continuamente muito antes do timeout de GPU. O teste com outro jogo estável produziu a mesma assinatura com frequência aproximadamente 3,4 vezes maior. Conclusão atual:

- o AHB error não é um bom gatilho para o crash do MUA3;
- ele provavelmente é ruído/uma particularidade de acesso a registradores do driver;
- não deve continuar como linha principal da investigação do `DeviceLost` sem nova evidência.

### 8.5 Hipótese de trabalho que sobrou

O timeout tinha aparência de um trabalho da GPU que não termina, sem page fault, IOMMU fault ou SMMU fault no intervalo analisado. Possibilidades:

- shader com loop/espera que não converge;
- draw ou dispatch indireto com parâmetros ruins;
- diferença de estado produzida pelo NCE antes do trabalho gráfico;
- erro de tradução/ordem de memória que só aparece no NCE.

### 8.6 Relação com Shader Loop Safety

A proteção contra loops infinitos permitiu atravessar o ponto problemático em uma build, mas o resultado ficou perto de 2 FPS e outros jogos, como Mario + Rabbids Kingdom Battle, apresentaram bugs gráficos. Isso mostrou que a proteção:

- pode evitar hangs reais;
- não é segura como regra global obrigatória;
- precisa ser selecionável globalmente e por jogo;
- deve usar cache de shader separado para não misturar binários protegidos e normais.

---

## 9. Shader Loop Safety selecionável

### 9.1 Funcionamento

Quando ativada, a geração SPIR-V adiciona um contador aos loops de shader e interrompe a execução após 8.192 iterações. O estado é propagado por:

```text
Android settings
  -> KenjinxNative/JNI
  -> LibKenjinx.Graphics
  -> GraphicsConfig
  -> GpuAccessor/HostCapabilities
  -> TranslatorContext
  -> SpirvGenerator
```

### 9.2 Estado atual

- desativada por padrão;
- configurável globalmente;
- configurável por jogo;
- loga `[NEOV2-LOOP-GUARD] enabled; limit=8192` quando ativa;
- caches separados:
  - `shader` com proteção;
  - `shader-no-loop-guard` sem proteção;
- versão de codegen atualizada para impedir reutilização indevida de shaders antigos.

### 9.3 Motivo da chave

O limite artificial muda a semântica do shader. Ele pode impedir um loop realmente infinito, mas também pode cortar um loop longo e legítimo, produzindo texturas, iluminação ou geometria incorretas. Por isso, a escolha por jogo é a solução correta no estágio atual.

---

## 10. NCE Neo 2

> As subseções 10.1–10.4 descrevem a implantação inicial. As comparações de 10.5 são históricas; a branch recebeu correções adicionais depois daqueles testes.

### 10.1 Motivação

O NCE deveria executar código ARM64 do jogo quase nativamente no ARM64 do Android, reduzindo o custo do JIT. Na prática observada, o NCE original e as primeiras revisões do Neo 2 não entregaram essa vantagem e ainda apresentaram problemas de compatibilidade.

### 10.2 Introdução do NCE Neo 2

Commit principal: `f24d6f706` — `nce: introduce Neo 2 execution and signal architecture`.

Principais áreas alteradas:

- `NceCpuContext.cs`;
- `NceExecutionContext.cs`;
- `NceNativeContext.cs`;
- `NceNativeInterface.cs`;
- `NcePatcher.cs`;
- `NceThreadContextIndex.cs`;
- `NativeSignalHandler.cs`;
- `UnixSignalHandlerRegistration.cs`;
- assembler ARM64.

Objetivos técnicos:

- melhorar preservação e restauração de contexto;
- associar falhas ao contexto correto da thread;
- tornar a manipulação de sinais mais previsível;
- usar pilha alternativa para sinais;
- compactar o snapshot usado no caminho de falha;
- reduzir operações perigosas durante manutenção de cache.

### 10.3 Correções posteriores

| Commit | Correção |
|---|---|
| `5243dc5af` | Removeu definição duplicada de `_alternateStackMemory`, que impedia compilação |
| `cd86aa8f9` | Evitou manutenção de cache NCE antes do mapeamento de código |
| `d29a1ebf5` | Adicionou proteção R3 de flush e integrou Shader Loop Safety selecionável |
| `7cb21885e` | Corrigiu o escopo da capability usada na geração da proteção de loop |

O marcador atual esperado é:

```text
[NEOV2-NCE2-R3] backend active; boundedContextIndex=True; altSignalStack=128KiB; compactFaultSnapshot=True
```

### 10.4 Falhas de compilação já corrigidas

1. Campo `_alternateStackMemory` definido duas vezes em `NceExecutionContext`.
2. Referência a `parameters` fora do escopo correto em `SpirvGenerator`.

Esses erros foram problemas de código/integração, não resultados de runtime.

### 10.5 Observações históricas e mudanças posteriores

Nos testes anteriores a 23/09, *MUA3* mostrava menu e depois tela preta no NCE enquanto o JIT renderizava; *Metroid Prime 4* apresentava stutter no NCE, e *Pokémon Legends: Z-A* avançava melhor no JIT. No mesmo conjunto de testes, *TOTK*, *Link's Awakening* e *MK11* tiveram desempenho parecido nos dois modos. O teste com Turnip T30 não isolou o driver como causa única. Esses resultados pertencem às builds e trechos testados naquela fase.

Depois disso, a branch recebeu [NCE-PROT1](https://github.com/Sunshinin11/Ryujinx_Neo/commit/b614253ae), opção **desligada por padrão** que reaplica rastreamento de memória da GPU após mudanças de permissão em NCE, e [TEXBUF-ALIGN1](https://github.com/Sunshinin11/Ryujinx_Neo/commit/f0a4f81be), que mantém o offset exato da view de texture buffer no Turnip quando o driver anuncia alinhamento de um texel. O primeiro commit relata correção da tela preta de *MUA3*; o segundo relata eliminação de `DeviceLost` na entrada do gameplay. A chave de proteção NCE é seletiva porque pode trazer regressões em outros títulos, incluindo *Metroid Prime 4*.

A conversão `LDXR/STXR` para `LDAXR/STLXR` passou a usar [filtro de instruções](https://github.com/Sunshinin11/Ryujinx_Neo/commit/2a1c968f06); o commit relata correção do boot de *Mario + Rabbids Sparks of Hope*. As mudanças foram validadas no Experimental antes da promoção ao Current; esta revisão não repetiu os testes.

### 10.6 Hipóteses prioritárias para o NCE

1. **Manutenção de cache de instruções/dados:** flush ou invalidação excessiva pode explicar stutter; falta de invalidação correta pode explicar código incorreto.
2. **Proteção de páginas e sinais:** `mprotect`, SIGSEGV e rastreamento de escrita podem provocar pausas muito frequentes.
3. **Estado de contexto:** registradores, FP/SIMD, flags, TLS ou contador podem divergir do JIT.
4. **Ordem de memória:** o NCE pode expor uma corrida que o JIT serializa acidentalmente.
5. **Patcher/trampolines:** blocos específicos podem ser patchados ou retomados de forma incorreta.
6. **Pacing:** parte do stutter pode vir da interação entre threads NCE, GPU e SurfaceFlinger, não do custo bruto da instrução.

### 10.7 Direção recomendada

- usar o JIT como oráculo de comportamento, não como padrão final;
- comparar NCE e JIT no mesmo trecho, cache e driver;
- medir eventos já existentes em uma build que também contenha uma correção real;
- começar pelo custo e frequência de cache maintenance, page protection e signal recovery;
- comparar o NCE atual com implementações conhecidas do Eden/Strato sem copiar mudanças em massa;
- manter cada tentativa reversível e protegida por tag.

---

## 11. Interface Android e identidade visual

### 11.1 Configurações por jogo

Commit-base da funcionalidade: `762b4af08` — `android: redesign NEO library and add per-game profiles`.

Foi criado `GameSettingsStore.kt`, indexado por Title ID. O sistema oferece:

- perfil global;
- perfil personalizado por jogo;
- fallback seguro para títulos sem perfil;
- proteção para IDs inválidos/homebrew;
- carregamento tanto pela biblioteca quanto por atalho/intenção;
- atualização contextual das configurações durante a sessão.

Opções abrangidas incluem, entre outras:

- NCE/JIT;
- PPTC;
- Memory Slack;
- Shader Loop Safety;
- cache de shaders;
- recompressão de textura;
- Macro HLE;
- resolução e anisotrópico;
- VSync;
- controle virtual, preset e estatísticas.

### 11.2 Persistência durante o jogo

- o controle virtual respeita o estado salvo;
- a sobreposição de estatísticas respeita o estado salvo;
- essas opções não devem reaparecer ou desaparecer a cada abertura sem ação do usuário;
- o menu permanente inferior foi substituído por gesto a partir da borda esquerda;
- a gaveta rápida permite alterar controle, estatísticas, VSync, movimento, Amiibo e abrir as configurações do jogo.

### 11.3 Biblioteca e dois layouts

**Modo NEO:**

- cartuchos inspirados na mídia física do Nintendo Switch;
- sete colunas no alvo paisagem do Thor/Odin;
- capas encaixadas no recorte do cartucho;
- busca em formato pílula;
- sem nome abaixo dos cartuchos;
- efeito de pressão/foco com escala, elevação, sombra e contorno;
- botão para trocar de layout no canto inferior direito.

**Modo Switch:**

- capas horizontais/quadradas em carrossel;
- hora, Wi-Fi e bateria no canto superior direito;
- barra inferior com atalhos circulares coloridos para CPU, gráficos, controles, dados e sistema;
- botão para voltar ao modo NEO no canto inferior direito.

A escolha do layout é persistida em `neoHomeLayout` e a transição usa animação suave.

### 11.4 Configurações redesenhadas

- fundo claro e identidade branca/cinza/grafite;
- menu principal por categorias;
- barra lateral persistente nas páginas internas;
- conteúdo em painel branco;
- apenas seta de voltar, sem botão redundante “Fechar”;
- switches para decisões binárias;
- seletores compactos para listas longas, como tamanho de DRAM, idioma e layout de controle;
- descrições revisadas para explicar o efeito real sem prometer ganhos;
- traduções adicionais para português;
- popup de jogo mais horizontal, em duas colunas;
- seleção indicada por contraste, borda e fundo, não apenas por cor suave.

### 11.5 Identidade do aplicativo

- nome exibido: **Ryujinx Neo**;
- logo escura usada nas superfícies claras;
- ícone de notificação próprio;
- ícone adaptativo com área segura e fundo claro para launcher, abertura e tela de recentes;
- ícone antigo do Kenji-NX/Ryujinx removido das ligações principais do manifesto.

### 11.6 Controles

A opção de inverter A/B e X/Y continua disponível, mas agora:

- vem desativada por padrão;
- possui migração única para corrigir instalações antigas;
- continua configurável quando o usuário realmente quiser o layout alternativo.

### 11.7 Estado da reforma visual

A reforma visual de base está commitada em [`54b072361`](https://github.com/Sunshinin11/Ryujinx_Neo/commit/54b072361) e recebeu [polimento posterior](https://github.com/Sunshinin11/Ryujinx_Neo/commit/df38536b2). Em 28/09, o Current local acrescentou página própria de configurações por jogo, grade responsiva e card de ações adaptado a telas estreitas. Os builds desse dia foram concluídos. Validado no Experimental antes da promoção ao Current; esta revisão não repetiu os testes.

---

## 12. Estado do Current em 28/09/2026

### 12.1 Código publicado na branch GitHub

Na branch GitHub, a última alteração de código publicada é `a24f5d1e7`. Estão publicados NCE Neo 2, NCE-PROT1 opcional, Shader Loop Safety selecionável, VTG via Compute, upload 3D assíncrono, compatibilidade gráfica 2D por jogo, perfis por Title ID, dois layouts de biblioteca, gerenciador de drivers e opções de sincronização/GPU descritas no [changelog](../CHANGELOG.md).

### 12.2 Mudanças funcionais do Current local em 28/09

O diff entre `a24f5d1e7` e o HEAD local `8bd3d0369` abrange 18 arquivos de código, alterados em 28/09 e organizados nos cinco commits locais abaixo em 29/09. Eles ainda não estão na branch GitHub. Os registros `android_build_current_doom1_fix.log` (21:09) e `android_build_current_doom1b.log` (21:36) terminam em **BUILD SUCCESSFUL** para `MainlineArmv8Release`; foram gerados APKs locais dessa linha. Validado no Experimental antes da promoção ao Current; esta revisão não repetiu os testes.

| Área | Implementação observada no Current local |
|---|---|
| Configurações por jogo | `GameSettingsDialog.kt` foi reorganizado como `GameSettingsPage`, com seções de sistema/CPU, gráficos, controles/interface, compatibilidade e sincronização. `MainView.kt` registra a rota `gameSettings`; `HomeViews.kt` abre a página pelo menu do título. Voltar sai sem salvar; há ações de salvar e restaurar. |
| Layout responsivo | `HomeViews.kt` usa três ou quatro colunas em retrato conforme a largura e sete em paisagem, além de reduzir margens e cabeçalho em telas estreitas. `GameActionsDialog.kt` usa uma coluna no celular e tenta a capa embutida quando não há imagem salva. |
| DLC e atualizações | `DlcViews.kt` e `TitleUpdateViews.kt` deixam de recarregar o estado salvo em cada recomposição; `DlcViewModel.kt` preserva alterações de ativação pendentes ao reconstruir a lista. |
| Modo sem internet por jogo | `GameSettingsStore.kt` guarda uma opção desligada por padrão. `MainViewModel.kt` a aplica no início do jogo e a limpa no fluxo geral; Kotlin/JNI repassam o valor para `NeoGraphicsSettings`. `IGeneralService` responde que nenhum pedido de internet foi aceito quando ligada. Isso não bloqueia toda a rede do Android. |
| Segurança NCE | `NceExecutionContext.cs` faz `RequestInterrupt` e `Dispose` usarem a mesma serialização para todos os títulos, em vez de condicioná-la à opção de compatibilidade 2D. |
| Memória | `KSystemControl.cs` altera de 3173 para 3285 MiB o retorno para os arranjos 4 GiB, 4 GiB SystemDev e 6 GiB AppletDev. |
| Texturas 2D grandes | `TextureGroup.cs` implementa rastreamento por regiões em texturas block-linear elegíveis a partir de 4 MiB, inclusive camadas/mipmaps. Escritas localizadas podem provocar envio só das regiões alteradas; há fallback para envio integral e verificação rotativa por hash de mudanças não notificadas. |
| Logs | `SettingViews.kt` expõe as chaves Guest e Debug na interface, com descrição do custo de Debug. |

Os commits locais são `653256590` (texturas 2D grandes), `181375e3c` (limite de memória), `b3ef70fc6` (sincronização NCE), `fbcfa896a` (NIFM por jogo) e `8bd3d0369` (interface Android, DLC, atualizações e logs). Os hashes ainda não têm links no GitHub porque não houve push.

### 12.3 Evidência e limites

- Os dois logs citados registram builds Android concluídos. Validado no Experimental antes da promoção ao Current; esta revisão não repetiu os testes.
- O texto da opção “Modo sem internet” cita *DOOM 2016* como motivação. A opção altera apenas a resposta do NIFM e não bloqueia toda a rede. Validado no Experimental antes da promoção ao Current; esta revisão não repetiu os testes.
- O envio de texturas foi validado no Experimental antes da promoção ao Current; esta revisão não repetiu os testes. Imagem, memória, tempo de quadro e fallback permanecem itens de monitoramento para futuras promoções.
- A branch remota continua sem os cinco commits locais do código de 28/09. Os nomes dos arquivos acima identificam o checkout Current analisado, não links para uma implementação já publicada.
- `[MUA3-SYNC]`, `mua3-diag.txt`, robust buffer access forçado e pulo de draws do geometry shader continuam apenas como histórico de experimentos removidos.

---

## 13. Resultados por jogo e estado da evidência

As métricas abaixo pertencem aos testes históricos da consolidação de 20/09. Os commits posteriores e o código local de 28/09 introduzem correções ou opções validadas no Experimental antes da promoção ao Current; esta revisão não repetiu os testes.

| Jogo | Observação histórica | Mudança posterior relacionada | Evidência nesta revisão |
|---|---|---|---|
| *Mortal Kombat 11* | Feedback loop corrigido, stutter reduzido e 60 FPS em modo portátil em um teste | Otimizações Vulkan mantidas no Current | Validado no Experimental antes da promoção ao Current; esta revisão não repetiu os testes. |
| *Tears of the Kingdom* | Melhor observação isolada de 17,4 FPS no trecho pesado; engasgo periódico de ~450 ms | Ajustes de sincronização e GPU | Validado no Experimental antes da promoção ao Current; esta revisão não repetiu os testes. |
| *Link's Awakening* | Melhor observação histórica de 45,3 FPS no trecho testado; um crash isolado | Ajustes posteriores de Vulkan/NCE | Validado no Experimental antes da promoção ao Current; esta revisão não repetiu os testes. |
| *Metroid Prime 4* | Stutter no NCE e problema de textura também visto em JIT | Proteção NCE seletiva e upload 3D assíncrono | Validado no Experimental antes da promoção ao Current; esta revisão não repetiu os testes. |
| *Marvel Ultimate Alliance 3* | Tela preta no NCE, renderização em JIT; Loop Safety atravessou um ponto com ~2 FPS | NCE-PROT1 e TEXBUF-ALIGN1 relatam correções para caminhos específicos | Validado no Experimental antes da promoção ao Current; esta revisão não repetiu os testes. |
| *Pokémon Legends: Z-A* | JIT avançou melhor que NCE no teste antigo | Mudanças posteriores de NCE | Validado no Experimental antes da promoção ao Current; esta revisão não repetiu os testes. |
| *Mario + Rabbids Kingdom Battle* | Loop Safety global causou bugs gráficos; commit posterior relata crashes aleatórios com otimizações de GPU | Loop Safety selecionável e chave “Otimizações de GPU” por jogo | Validado no Experimental antes da promoção ao Current; esta revisão não repetiu os testes. |
| *Mario + Rabbids Sparks of Hope* | Sem medição anterior registrada aqui | Filtro de acessos exclusivos NCE relatado como correção de boot | Validado no Experimental antes da promoção ao Current; esta revisão não repetiu os testes. |
| *MARVEL Cosmic Invasion* | Investigação de boot, tela preta e caminhos gráficos 2D | Compatibilidade gráfica 2D em `a24f5d1e7`; serialização NCE ampliada localmente em 28/09 | Validado no Experimental antes da promoção ao Current; esta revisão não repetiu os testes. |
| *DOOM 2016* | Espera de rede antes do gameplay motivou a opção local | “Modo sem internet” por jogo na Current local | Validado no Experimental antes da promoção ao Current; esta revisão não repetiu os testes. |

O envio seletivo de texturas 2D grandes foi validado no Experimental antes da promoção ao Current; a matriz de regressão para camadas, mipmaps e pequenas regiões deve ser mantida em futuras promoções. Os resultados de desempenho não devem ser generalizados para outros jogos, drivers ou aparelhos.

---

## 14. Telemetria e logs

### 14.1 Estado dos logs

O Current publicado desliga nove chaves de log por padrão e migra preferências antigas ([commit](https://github.com/Sunshinin11/Ryujinx_Neo/commit/bfeab8f9c)). A interface local de 28/09 acrescenta controles visíveis para **Logs do jogo (Guest)** e **Logs de Debug**. Debug avisa sobre custo de diagnóstico. Ativar a chave não garante uma mensagem específica: o caminho precisa executar e a categoria correspondente deve estar habilitada.

Os marcadores `[NEOV2-NCE2-R3]`, `[NEOV2-V29]`, `[NEOV2-LOOP-GUARD]` e `[NEOV2-MEMLIMIT]` aparecem no histórico técnico. Sua ausência em uma sessão só é conclusiva quando o caminho executado e a categoria de log são conhecidos.

### 14.2 Marcadores históricos/removidos

- `[MUA3-SYNC]`;
- `[MUA3-TRACE]`;
- modos de `mua3-diag.txt`;
- telemetria dedicada de command buffers MUA3.

### 14.3 Interpretação responsável

- ausência de mensagem não prova ausência de execução, a menos que o marcador esteja no caminho obrigatório;
- `DeviceLost` é o resultado final, não necessariamente a origem;
- um log sem page fault não elimina erro lógico de shader;
- comparar logs exige mesmo driver, modo CPU, configuração e trecho do jogo;
- linhas suprimidas pelo kernel podem esconder o início de uma sequência de erros.

---

## 15. Metodologia de benchmark recomendada

Para transformar o histórico em evidência de release:

1. Aquecer o aparelho e estabilizar a temperatura.
2. Usar exatamente a mesma rota/save e duração.
3. Rodar pelo menos três vezes cada configuração.
4. Separar cache frio e cache quente.
5. Registrar mediana de FPS, 1% low, frametime P95 e P99.
6. Registrar temperatura e clocks quando possível.
7. Manter driver e modo portátil/dock fixos.
8. Comparar imagens quando houver mudanças em barreiras, pipelines, formatos ou shaders.
9. Testar fechamento e reabertura, não apenas gameplay.
10. Registrar versão do APK/commit junto do log.

### Matriz mínima sugerida

| Jogo | Motivo |
|---|---|
| TOTK | CPU/GPU pesado, flushes e stutter periódico |
| Link's Awakening | Sensível a pacing e estabilidade |
| MK11 | Feedback loop, UBWC e desempenho gráfico |
| MP4 | Stutter NCE e problema de textura |
| MUA3 | Tela preta NCE, V29 e loops de shader |
| Pokémon Z-A | Compatibilidade NCE |
| Mario + Rabbids KB | Regressão de Loop Safety |

---

## 16. Riscos técnicos conhecidos

| Risco | Consequência | Mitigação atual |
|---|---|---|
| NCE incorreto | Tela preta, stutter ou falha de jogo | Comparação direta com JIT e tags de segurança |
| Loop Safety global | Bugs gráficos | Desativada por padrão e configurável por jogo |
| Dynamic states | Erros discretos de face, profundidade ou stencil | Comparação visual em vários jogos |
| Staging buffer grande | Pressão de memória | Observar dispositivos com menos RAM |
| Cache de textura grande | Maior retenção de memória | Monitorar OOM e troca de jogos |
| Batching agressivo | Dificulta diagnóstico por timeline e pode alterar ordem | Desativar/segmentar em diagnósticos específicos |
| Alterações simultâneas | Impossível atribuir resultado | Uma hipótese principal por rodada |
| Ícone/tema em cache pelo Android | Aparência antiga mesmo após correção | Reinstalação limpa durante validação visual |
| Configuração antiga persistida | Resultado diferente do esperado | Migrações explícitas e log das opções efetivas |
| Envio seletivo de texturas 2D grandes | Imagem incorreta ou custo extra de rastreamento/hash | Comparar imagem, memória, FPS e fallback com envio integral |
| Resposta NIFM por jogo | Jogo reagir de forma inesperada à ausência de internet aceita | Manter a opção desligada por padrão e testar ligada/desligada por título |
| Serialização NCE ampliada | Contenção ou regressão no encerramento/interrupção | Testar sessões longas e abertura/fechamento repetidos |
| Limite de memória 3285 MiB | Menos memória disponível para sistema/applets emulados nos arranjos 4 GiB, 4 GiB SystemDev e 6 GiB AppletDev | Validado no Experimental; monitorar applets e títulos que dependem do pool de sistema |

---

## 17. Próximos passos recomendados

### Prioridade 1: publicar o Current local

1. Integrar os cinco commits locais à branch GitHub `neo-v2-nce2`, conciliando antes os commits de documentação publicados remotamente.
2. Criar tag de recuperação sobre `a24f5d1e7` antes do push.
3. Após o push, atualizar os cabeçalhos de ambos os documentos para identificar o novo commit de código publicado.

### Prioridades 2 e 3: manutenção da matriz de regressão

Nas futuras promoções, manter os cenários já usados para *MUA3* (NCE-PROT1 e TEXBUF-ALIGN1), *Metroid Prime 4* (NCE/JIT e upload 3D assíncrono), *Mario + Rabbids Kingdom Battle* (otimizações de GPU), *Mario + Rabbids Sparks of Hope* (filtro NCE) e *MARVEL Cosmic Invasion* (compatibilidade gráfica 2D). Registrar também as verificações de interface, DLC/atualizações, resposta NIFM, serialização NCE, texturas 2D grandes e limite de memória.

Preservar a metodologia da seção 15: mesmo save e driver, cache frio/quente separado e pelo menos três execuções por cenário. Os resultados históricos continuam limitados aos jogos, aparelhos e drivers em que foram obtidos.

---

## 18. Arquivos-chave para trabalhos futuros

### NCE

- `src/Ryujinx.Cpu/Nce/NceCpuContext.cs`
- `src/Ryujinx.Cpu/Nce/NceExecutionContext.cs`
- `src/Ryujinx.Cpu/Nce/NceNativeContext.cs`
- `src/Ryujinx.Cpu/Nce/NceNativeInterface.cs`
- `src/Ryujinx.Cpu/Nce/NcePatcher.cs`
- `src/Ryujinx.Cpu/Nce/NceThreadContextIndex.cs`
- `src/Ryujinx.Cpu/Signal/NativeSignalHandler.cs`
- `src/Ryujinx.Cpu/Signal/UnixSignalHandlerRegistration.cs`

### Shader Loop Safety e MUA3

- `src/Ryujinx.Graphics.Gpu/GraphicsConfig.cs`
- `src/Ryujinx.Graphics.Gpu/Shader/ShaderCache.cs`
- `src/Ryujinx.Graphics.Gpu/Shader/GpuAccessorBase.cs`
- `src/Ryujinx.Graphics.Gpu/Shader/DiskCache/DiskCacheHostStorage.cs`
- `src/Ryujinx.Graphics.Shader/IGpuAccessor.cs`
- `src/Ryujinx.Graphics.Shader/Translation/HostCapabilities.cs`
- `src/Ryujinx.Graphics.Shader/Translation/TranslatorContext.cs`
- `src/Ryujinx.Graphics.Shader/CodeGen/Spirv/SpirvGenerator.cs`

### Vulkan e performance

- `src/Ryujinx.Graphics.Vulkan/VulkanSubmissionWorker.cs`
- `src/Ryujinx.Graphics.Vulkan/TextureStorage.cs`
- `src/Ryujinx.Graphics.Vulkan/RenderPassHolder.cs`
- `src/Ryujinx.Graphics.Vulkan/BarrierBatch.cs`
- `src/Ryujinx.Graphics.Vulkan/PipelineState.cs`
- `src/Ryujinx.Graphics.Vulkan/PipelineBase.cs`
- `src/Ryujinx.Graphics.Vulkan/AutoFlushCounter.cs`
- `src/Ryujinx.Graphics.Vulkan/Window.cs`
- `src/Ryujinx.Graphics.Gpu/Image/AutoDeleteCache.cs`
- `src/Ryujinx.Graphics.Gpu/Image/TextureGroup.cs` (envio seletivo de texturas 2D da Current local)
- `src/Ryujinx.Common/Memory/MemoryOwner.cs`

### Android e configurações

- `src/KenjinxAndroid/app/src/main/java/org/kenjinx/android/viewmodels/QuickSettings.kt`
- `src/KenjinxAndroid/app/src/main/java/org/kenjinx/android/viewmodels/SettingsViewModel.kt`
- `src/KenjinxAndroid/app/src/main/java/org/kenjinx/android/viewmodels/GameSettingsStore.kt`
- `src/KenjinxAndroid/app/src/main/java/org/kenjinx/android/viewmodels/MainViewModel.kt`
- `src/KenjinxAndroid/app/src/main/java/org/kenjinx/android/views/HomeViews.kt`
- `src/KenjinxAndroid/app/src/main/java/org/kenjinx/android/views/SettingViews.kt`
- `src/KenjinxAndroid/app/src/main/java/org/kenjinx/android/views/GameSettingsDialog.kt`
- `src/KenjinxAndroid/app/src/main/java/org/kenjinx/android/views/GameActionsDialog.kt`
- `src/KenjinxAndroid/app/src/main/java/org/kenjinx/android/views/GameViews.kt`
- `src/LibKenjinx/Android/JniExportedMethods.cs` (opção NIFM da Current local)
- `src/Ryujinx.HLE/HOS/Services/Nifm/StaticService/IGeneralService.cs` (resposta NIFM da Current local)

---

## 19. Histórico condensado de commits do fork

| Commit | Descrição |
|---|---|
| `4c5abb58f` | Port da correção MUA3 V29 geometry-as-compute |
| `e564bb060` | Consolidação inicial das otimizações Vulkan e Memory Slack |
| `f8d0b8e06` | Snapshot do MUA3 anterior ao NCE Neo 2 |
| `f24d6f706` | Arquitetura NCE Neo 2 |
| `d29a1ebf5` / `7cb21885e` | NCE2 R3 e Loop Safety selecionável, depois corrigido |
| `762b4af08` / `54b072361` | Perfis por jogo e reforma visual |
| `df38536b2` / `1d34c02d8` | Identidade Neo e gerenciador de drivers |
| `d517b9276` | Promoção para Neo Current 0.0.1 |
| `b614253ae` / `f0a4f81be` | NCE-PROT1 e offset exato de texture buffer |
| `b9baca871` / `69fd95c78` | Migração do Experimental e upload 3D assíncrono |
| `2a1c968f0` | Filtro de exclusivas NCE e chave de otimizações de GPU |
| `a24f5d1e7` | Compatibilidade gráfica 2D por jogo; último commit de código publicado antes das mudanças de 28/09 |
| `653256590` | Texturas 2D grandes; commit local criado em 29/09, ainda não enviado |
| `181375e3c` | Limite de memória 3285 MiB; commit local criado em 29/09, ainda não enviado |
| `b3ef70fc6` | Serialização NCE; commit local criado em 29/09, ainda não enviado |
| `fbcfa896a` | Modo sem internet por jogo; commit local criado em 29/09, ainda não enviado |
| `8bd3d0369` | Interface Android, DLC, atualizações e logs; commit local criado em 29/09, ainda não enviado |

---

## 20. Checklist antes de qualquer nova build de desempenho

- [ ] Árvore visual congelada em commit/tag.
- [ ] Commit exato anotado.
- [ ] Nenhuma telemetria sem hipótese e sem mudança real.
- [ ] Driver, jogo, save, modo e configuração definidos.
- [ ] NCE/JIT claramente indicado no log.
- [ ] Loop Safety claramente indicado no log.
- [ ] Memory Slack e DRAM efetivos conhecidos.
- [ ] Cache frio/quente identificado.
- [ ] Resultado esperado definido antes do teste.
- [ ] Critério de manter/reverter definido.
- [ ] APK e artefatos fora do Git.

---

## 21. Glossário

| Termo | Significado neste projeto |
|---|---|
| NCE | Execução nativa de código ARM64 guest no host ARM64 |
| JIT | Tradução dinâmica de código durante a execução |
| PPTC | Cache persistente de código traduzido |
| UBWC | Compressão de largura de banda usada por GPUs Adreno |
| TBDR | Renderização em blocos típica de GPUs móveis |
| Timeline semaphore | Primitiva Vulkan que representa progresso crescente da GPU |
| Batching | Agrupamento de vários envios em uma chamada ao driver |
| Flush | Forçar envio/sincronização do trabalho pendente |
| DeviceLost | Estado Vulkan em que o dispositivo/driver deixa de aceitar o trabalho atual |
| V29 | Tratamento específico do geometry shader conhecido do MUA3 |
| Loop Safety | Limite artificial de iterações em loops de shader |
| Memory Slack | Folga adicional no espaço de endereçamento emulado |
| Title ID | Identificador único do título Switch usado nos perfis por jogo |

---

## 22. Conclusão

O Ryujinx Neo Current não parou nas alterações de 27/09. Em 28/09, o checkout Current recebeu mudanças funcionais em interface Android, DLC/atualizações, NIFM por jogo, NCE, limite de memória, logs e envio seletivo de texturas 2D grandes. Os builds Android das 21:09 e 21:36 terminaram com sucesso. O código foi organizado em cinco commits locais em 29/09, ainda ausentes da branch GitHub, cuja última alteração de código publicada é `a24f5d1e7`.

O próximo passo do fluxo é integrar e publicar os cinco commits locais do Current, que reúnem os 18 arquivos de código de 28/09, após conciliar os commits remotos de documentação e criar uma tag de recuperação sobre `a24f5d1e7`. Depois do push, os cabeçalhos dos dois documentos devem apontar para o novo commit de código publicado. A matriz de regressão permanece como rotina de futuras promoções; esta revisão não repetiu os testes do Experimental.
