# Ryujinx Neo — Relatório técnico completo do projeto

**Projeto:** Ryujinx Neo, fork Android derivado do Kenji-NX  
**Repositório de trabalho:** `C:\Desenvolvimento\Kenji-NX-Current`  
**Repositório público do fork:** `https://github.com/Sunshinin11/Ryujinx_Neo`  
**Branch atual:** `neo-v2-nce2`  
**Commit-base atual:** `54b07236143be555f45a2a0dfbff6574829f9b10`  
**Data desta consolidação:** 20/09/2026  
**Dispositivo principal de referência:** AYN Thor, Snapdragon 8 Gen 2, Adreno 740  
**Plataforma:** Android arm64-v8a / Linux Bionic  
**Backend gráfico principal:** Vulkan, com foco em Turnip/Adreno  

> Este documento separa deliberadamente três coisas: o que está no código atual, o que foi observado em testes e o que ainda é hipótese. Experimentos removidos continuam registrados como histórico, mas não são descritos como recursos ativos.

---

## 1. Resumo executivo

O Ryujinx Neo nasceu como uma evolução experimental do Kenji-NX para Android, inicialmente focada em desempenho Vulkan no AYN Thor. O projeto avançou em quatro frentes principais:

1. **Caminho Vulkan e GPU:** worker de submissão, timeline semaphores, batching, listas de formatos compatíveis com UBWC, cache persistente de pipelines, melhorias em render pass, barreiras, cache de texturas e redução de trabalho por quadro.
2. **CPU e NCE:** criação do NCE Neo 2, reestruturação de contexto/sinais, pilha alternativa de sinal, índice de contexto por thread e proteções adicionais de cache e flush.
3. **Compatibilidade de shaders:** correção V29 para o MUA3 e proteção selecionável contra loops infinitos em shaders, com caches separados para o modo protegido e o modo normal.
4. **Experiência Android:** configurações por jogo, persistência do controle virtual e das estatísticas, dois layouts de biblioteca, identidade visual própria, menus reorganizados e interface pensada para Thor/Odin.

Os maiores ganhos históricos medidos vieram das listas de formatos de textura, da compatibilidade com UBWC, do cache persistente de pipelines, do agrupamento de submissões, do cache maior de texturas e da substituição do pool próprio de memória pelo `ArrayPool<T>.Shared`.

O principal problema técnico em aberto é o **NCE**. Nos testes atuais, ele não demonstrou a vantagem esperada sobre o JIT e apresentou regressões específicas:

- MUA3 renderiza no JIT, mas permanece preto no NCE;
- MP4 apresenta stuttering no NCE e fica suave no JIT;
- Pokémon Legends: Z-A conseguiu avançar no JIT, enquanto o NCE apresentou problemas;
- TOTK, Link's Awakening e MK11 tiveram desempenho semelhante entre NCE e JIT nos testes realizados.

O JIT, porém, aquece muito o Thor. Portanto, ele é uma referência de comportamento e desempenho, não uma solução aceitável como padrão. Fazer o NCE funcionar corretamente continua sendo o trabalho mais importante para considerar o emulador pronto.

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

### 3.1 Remotes

| Remote | Endereço | Uso |
|---|---|---|
| `origin` | `https://git.ryujinx.app/projects/Kenji-NX.git` | Upstream Kenji-NX |
| `sunshinin11` | `https://github.com/Sunshinin11/Ryujinx_Neo.git` | Fork Ryujinx Neo |

### 3.2 Branches locais relevantes

| Branch | Finalidade |
|---|---|
| `libryujinx_bionic` | Linha Android/Bionic de referência |
| `neo-v2-baseline` | Baseline experimental do NEO V2 |
| `neo-v2-fase1` | Etapas de desempenho Vulkan/GPU |
| `neo-v2-nce2` | Linha atual, com NCE Neo 2, Loop Safety selecionável e interface nova |
| `preserve-batching-db668399` | Ponto de estudo relacionado ao batching |

### 3.3 Tags de segurança

| Tag | Commit | Significado |
|---|---|---|
| `neo-v2-mua3-stable-pre-nce2-20260919` | `f8d0b8e06` | Estado valioso do MUA3 antes da introdução do NCE Neo 2 |
| `neo-v2-nce2-r3-pre-ui-20260919` | `7cb21885e` | NCE2 R3 e Loop Safety corrigidos antes da reforma visual |

Esses pontos existem para impedir que uma rodada experimental destrua uma build funcional ou torne difícil separar problemas de interface, NCE e GPU.

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

### 5.3 Variante usada historicamente

`assembleMainlineArmv82Release`

O comando de build não é executado durante alterações feitas pelo Codex; fica sob responsabilidade do compilador externo designado pelo usuário.

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

### 10.5 Evidências atuais de que o NCE ainda está quebrado ou incompleto

| Jogo | JIT | NCE | Interpretação atual |
|---|---|---|---|
| MUA3 | Renderiza; proteção extrema chegou a permitir avanço com desempenho muito baixo | Menu aparece, jogo fica preto | Forte candidato a erro de estado/execução específico do NCE |
| Metroid Prime 4 | Sem stuttering perceptível no teste | Stuttering recorrente | NCE introduz pausas que o JIT não apresenta |
| Pokémon Legends: Z-A | Conseguiu rodar/avançar | Histórico de falha ou comportamento incompleto | Outro sinal de incompatibilidade NCE |
| TOTK | Desempenho semelhante ao NCE | Desempenho semelhante ao JIT | NCE não demonstra a vantagem esperada |
| Link's Awakening | Desempenho semelhante ao NCE | Desempenho semelhante ao JIT | Mesmo padrão |
| MK11 | Desempenho semelhante ao NCE | Desempenho semelhante ao JIT | Mesmo padrão |

O teste com Turnip T30 não alterou a conclusão: o problema principal permaneceu ligado ao caminho NCE, não foi explicado apenas pelo driver.

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

As alterações visuais mais recentes estão na árvore de trabalho e ainda não fazem parte do commit-base `54b072361`. Elas foram revisadas estaticamente, mas ainda aguardam a primeira compilação externa. Nenhum APK foi gerado durante essa revisão.

---

## 12. Estado atual exato do código

### 12.1 Funcionalidades presentes no commit-base/branch

- otimizações gerais NEO V2;
- snapshot limpo do MUA3 anterior ao NCE2;
- V29;
- NCE Neo 2 e correções R3;
- Shader Loop Safety selecionável;
- caches de shader isolados;
- Memory Slack 0/12 GiB;
- configurações por jogo;
- primeira grande reforma visual.

### 12.2 Alterações não commitadas atuais

As mudanças pendentes estão concentradas na interface Android:

- manifesto, nome e ícones;
- `DocumentProvider` e notificação;
- paleta e tema;
- persistência de opções;
- diálogos de ações e configuração por jogo;
- biblioteca NEO/Switch;
- navegação direta para categorias;
- reorganização das configurações;
- logo escura;
- recursos adaptativos do launcher.

O diretório `Imagens/` contém ativos de origem e permanece não rastreado. O diretório `.kotlin/` também não é parte do código-fonte do projeto e não deve ser versionado.

### 12.3 O que não deve ser confundido com o estado atual

- `[MUA3-SYNC]` não é uma correção ativa;
- telemetria específica de diagnóstico MUA3 foi removida;
- robust buffer access forçado em Qualcomm não ficou como solução;
- pulo de draws do geometry shader não ficou ativo;
- arquivo de configuração `mua3-diag.txt` não faz parte do fluxo atual;
- AHB error não é considerado causa principal do crash;
- JIT não foi escolhido como padrão.

---

## 13. Resultados por jogo

### 13.1 Mortal Kombat 11

- a correção de feedback loop eliminou corrupção visual observada em menus/cutscenes;
- listas de formato e UBWC reduziram stutter;
- chegou a 60 FPS em modo portátil nos testes da fase;
- JIT e NCE apresentaram desempenho semelhante em teste posterior, levantando dúvida sobre o benefício do NCE.

### 13.2 The Legend of Zelda: Tears of the Kingdom

- grande redução inicial de stutter com formatos/UBWC;
- o ganho final de FPS foi modesto e variável;
- melhor observação isolada na fase de batching: 17,4 FPS no cenário pesado;
- permaneceu um engasgo periódico perto de 450 ms;
- NCE e JIT ficaram próximos nos testes recentes.

### 13.3 The Legend of Zelda: Link's Awakening

- melhora com sincronização e pool de memória;
- melhor resultado histórico registrado: 45,3 FPS no trecho testado;
- um crash isolado após a mudança do contador do host foi registrado;
- JIT e NCE também ficaram próximos em desempenho recente.

### 13.4 Metroid Prime 4

- teve histórico de `DeviceLost` em fases anteriores;
- no estudo atual, o problema mais claro é stutter no NCE;
- o mesmo trecho em JIT foi descrito como sem stuttering;
- o problema de textura é separado e continuou existindo no JIT.

### 13.5 Marvel Ultimate Alliance 3

- V29 é necessária para o caminho visual trabalhado;
- o hang original não foi explicado pelo geometry shader, AHB error, vertex buffers, compilação paralela ou robust buffer access;
- Loop Safety pode atravessar o problema, mas com custo e regressões;
- JIT renderiza, NCE fica preto;
- a prioridade agora é investigar divergência NCE/JIT, não repetir as hipóteses descartadas.

### 13.6 Pokémon Legends: Z-A

- funciona melhor no JIT no teste citado;
- NCE apresentou falha/comportamento incompleto;
- reforça que a investigação deve começar no NCE comum a vários títulos.

### 13.7 Mario + Rabbids Kingdom Battle

- exibiu bugs gráficos quando a proteção de loop era imposta globalmente;
- foi a evidência decisiva para tornar Shader Loop Safety selecionável.

---

## 14. Telemetria e logs

### 14.1 Marcadores úteis ainda existentes

| Marcador | Significado |
|---|---|
| `[NEOV2-NCE2-R3]` | NCE Neo 2 R3 ativo |
| `[NEOV2-V29]` | Conversão especial do shader do MUA3 ativa |
| `[NEOV2-LOOP-GUARD]` | Estado da proteção de loops |
| `[NEOV2-MEMLIMIT]` | Região, slack e limite total de memória |
| `[NEOV2-ETAPA12]` | Estratégia de espera do VSync |
| `[NEOV2-ETAPA16]` | Resumo/origem dos flushes |
| `[NEOV2-ETAPA18]` | Capacidade do cache de texturas |
| `[NEOV2-ETAPA21]` | Pool de memória usando `ArrayPool.Shared` |

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

---

## 17. Próximos passos recomendados

### Prioridade 1 — estabilizar e medir a interface atual

- compilar externamente a árvore visual atual;
- validar launcher, tela de recentes e nome Ryujinx Neo;
- validar modos NEO e Switch;
- conferir configurações globais e por jogo;
- confirmar que DRAM usa seletor e opções binárias usam switches;
- confirmar persistência de layout, controle e estatísticas.

### Prioridade 2 — congelar a versão visual

- corrigir apenas falhas visuais comprovadas;
- criar commit e tag de recuperação;
- não misturar a próxima rodada de NCE com reforma visual.

### Prioridade 3 — NCE versus JIT

1. Escolher um trecho reproduzível do MP4 para stutter.
2. Escolher o ponto do MUA3 em que o NCE fica preto.
3. Comparar opções efetivas de boot e estado de memória.
4. Auditar cache maintenance, `mprotect`, sinais e retomada de contexto.
5. Implementar uma correção real acompanhada de métricas suficientes na mesma build.
6. Repetir em Pokémon Z-A para verificar se a causa é compartilhada.

### Prioridade 4 — problemas gráficos separados

- investigar a textura do MP4 sem atribuí-la automaticamente ao NCE;
- manter V29 enquanto seu benefício visual for necessário;
- usar Loop Safety somente onde o ganho superar as regressões.

### Prioridade 5 — validação ampla de desempenho

- repetir os benchmarks principais;
- decidir se etapas neutras ou arriscadas ainda justificam manutenção;
- documentar configuração recomendada por jogo.

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

---

## 19. Histórico condensado de commits do fork

| Commit | Descrição |
|---|---|
| `4c5abb58f` | Port da correção MUA3 V29 geometry-as-compute |
| `98e23e65e` | Regras de Git e documentação inicial do fork |
| `e564bb060` | Consolidação das otimizações Vulkan, Memory Slack e experimentos MUA3 |
| `f8d0b8e06` | Snapshot estável do MUA3 antes do NCE Neo 2 |
| `f24d6f706` | Introdução da arquitetura NCE Neo 2 |
| `5243dc5af` | Correção do campo duplicado da pilha alternativa |
| `cd86aa8f9` | Proteção de cache maintenance antes do mapeamento |
| `d29a1ebf5` | NCE2 R3 e Loop Safety selecionável |
| `7cb21885e` | Correção de escopo da proteção de loop |
| `762b4af08` | Biblioteca NEO e configurações por jogo |
| `54b072361` | Reforma visual com cartuchos e painéis modulares |

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

O Ryujinx Neo já deixou de ser apenas uma coleção de patches. Ele possui uma linha de pesquisa documentada, pontos de recuperação, uma arquitetura própria para configurações Android, identidade visual e alterações profundas no caminho Vulkan e no NCE.

Os resultados mais fortes estão no lado gráfico: cache, formatos, UBWC, barreiras, submissão e retenção de texturas. A investigação do MUA3 também produziu valor mesmo sem resolver tudo, porque eliminou várias hipóteses e separou o AHB error do hang real.

O gargalo decisivo agora é o NCE. A semelhança de desempenho com o JIT, o stutter exclusivo do MP4, a tela preta do MUA3 e o comportamento do Pokémon Z-A indicam que ainda existe problema de correção e/ou sincronização no caminho nativo. Resolver isso sem perder a eficiência térmica é o passo que falta para transformar o Ryujinx Neo em uma build realmente pronta para uso diário nos aparelhos-alvo.

Até essa validação, a descrição correta do projeto é: **fork Android experimental avançado, com ganhos gráficos reais, interface própria e NCE em desenvolvimento ativo**.
