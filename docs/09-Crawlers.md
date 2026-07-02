# 09 — Arquitetura de Crawlers

**Consultor de Processos**
Software Design Document · v1.0

---

## 1. Princípio Fundamental

O crawler é um **detalhe de implementação**, não uma entidade central.

O restante do sistema nunca sabe **como** um processo foi consultado. Sabe apenas que recebeu um `ProcessSnapshot`. Isso significa que:

- Trocar Jsoup por Playwright em um tribunal → zero impacto fora do módulo `crawler`
- Adicionar um novo tribunal → criar novos arquivos; nenhum arquivo existente é alterado
- Mudar a estratégia de anti-bloqueio → alteração interna ao Provider

Este princípio é garantido pela interface `CourtProvider` como contrato único entre o módulo `crawler` e todo o resto do sistema.

---

## 2. Componentes e Responsabilidades

```
┌─────────────────────────────────────────────────────────────────┐
│                        MÓDULO CRAWLER                           │
│                                                                 │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │                   CourtProvider (interface)              │  │
│  │   ProcessSnapshot consultar(String processNumber)        │  │
│  └──────────────────────────────────────────────────────────┘  │
│           │                    │                   │            │
│    ┌──────▼──────┐    ┌────────▼──────┐  ┌────────▼──────┐   │
│    │ STFProvider │    │ EprocProvider │  │STJRJProvider  │   │
│    └──────┬──────┘    └──────┬────────┘  └──────┬────────┘   │
│           │                  │                   │             │
│    Usa internamente:                                            │
│    ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐  │
│    │   Crawler    │  │    Parser    │  │    Validator /   │  │
│    │  (obtém HTML)│  │(extrai dados)│  │    Normalizer    │  │
│    └──────────────┘  └──────────────┘  └──────────────────┘  │
│                                                                 │
│    Infraestrutura transversal:                                  │
│    ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐  │
│    │HashGenerator │  │  Comparator  │  │ExecutionRecorder │  │
│    └──────────────┘  └──────────────┘  └──────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

### Responsabilidade de cada componente

| Componente | Responsabilidade | O que NÃO faz |
|------------|-----------------|---------------|
| `CourtProvider` | Orquestra a consulta; decide qual crawler tentar | Não sabe nada sobre notificações ou banco |
| `Crawler` | Obtém o HTML/JSON bruto do tribunal | Não interpreta o conteúdo |
| `Parser` | Transforma HTML em `ParsedData` estruturado | Não faz requisições HTTP |
| `Validator` | Verifica se `ParsedData` é completo e coerente | Não altera dados |
| `Normalizer` | Padroniza datas, textos e formatos → `ProcessSnapshot` | Não valida |
| `HashGenerator` | Gera SHA-256 do snapshot para comparação | Não persiste nada |
| `SnapshotComparator` | Compara hash novo com hash anterior | Não envia notificações |
| `ExecutionRecorder` | Persiste `CrawlerExecution` com métricas | Não afeta o fluxo principal |

---

## 3. Interface `CourtProvider`

Contrato único entre o módulo crawler e o restante do sistema.

```java
public interface CourtProvider {

    /**
     * Consulta o estado atual de um processo no tribunal.
     *
     * @param processNumber Número do processo normalizado (formato CNJ)
     * @return ProcessSnapshot com o estado atual do processo
     * @throws CourtUnavailableException quando todas as estratégias falharam
     * @throws ProcessNotFoundException  quando o processo não foi localizado no tribunal
     */
    ProcessSnapshot consultar(String processNumber);

    /**
     * Código único do tribunal que este provider atende.
     * Deve coincidir com Court.code no banco de dados.
     */
    String getCourtCode();
}
```

**Regras invioláveis:**
1. O retorno é sempre `ProcessSnapshot` — nunca HTML, nunca objetos de biblioteca
2. Exceções lançadas devem ser do domínio (`CourtUnavailableException`, `ProcessNotFoundException`), nunca `IOException` ou exceções de libs externas vazando
3. Todo Provider deve ser um bean Spring com `@Component` e registrar-se automaticamente no `CourtProviderFactory`

---

## 4. `CourtProviderFactory`

Responsável por rotear para o Provider correto com base no código do tribunal.

```java
@Component
public class CourtProviderFactory {

    private final Map<String, CourtProvider> providers;

    // Spring injeta automaticamente todos os beans que implementam CourtProvider
    public CourtProviderFactory(List<CourtProvider> providerList) {
        this.providers = providerList.stream()
            .collect(Collectors.toMap(
                CourtProvider::getCourtCode,
                Function.identity()
            ));
    }

    public CourtProvider getProvider(String courtCode) {
        CourtProvider provider = providers.get(courtCode);
        if (provider == null) {
            throw new ProviderNotFoundException(
                "Nenhum Provider registrado para o tribunal: " + courtCode
            );
        }
        return provider;
    }
}
```

> Adicionar um novo tribunal = criar um novo `@Component` que implementa `CourtProvider`. O Factory o encontra automaticamente via injeção de dependência. Zero modificações em código existente.

---

## 5. Estratégias de Crawling (por ordem de preferência)

Cada Provider define sua própria cadeia de fallback. A ordem padrão é:

```
1. HTTP Direto        → mais rápido, zero overhead
       ↓ falhou
2. Jsoup (HTTP+parse) → leve, suporta parsing básico no processo de obtenção
       ↓ falhou
3. Playwright         → browser headless moderno; lida com JavaScript
       ↓ falhou
4. Selenium           → último recurso; pesado e lento
       ↓ falhou
   → CrawlException → política de retry → DLQ
```

**Critério de falha:** qualquer estratégia é considerada falha se:
- Lança exceção de conexão ou timeout
- Retorna HTML que o Parser não consegue interpretar
- Retorna resposta de bloqueio/CAPTCHA
- Retorna HTTP 4xx/5xx inesperado

---

## 6. Crawler HTTP Direto

Realiza uma requisição HTTP simples sem renderização de JavaScript. Ideal para tribunais com API pública ou endpoints JSON.

```java
public class HttpCrawler {

    private final HttpClient httpClient;
    private final CourtConfig config;

    public RawResponse fetch(String url, CrawlContext context) {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("User-Agent", context.getUserAgent())
            .header("Accept", "text/html,application/json")
            .header("Accept-Language", "pt-BR,pt;q=0.9")
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build();

        HttpResponse<String> response = httpClient.send(request,
            HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 403 || response.statusCode() == 429) {
            throw new CourtBlockedException("Tribunal retornou " + response.statusCode());
        }

        return new RawResponse(
            response.body(),
            response.statusCode(),
            RawResponseType.HTML,
            CrawlerStrategy.HTTP
        );
    }
}
```

---

## 7. Crawler Jsoup

Usa Jsoup para fazer a requisição e já retornar um `Document` parseável. Útil quando o tribunal serve HTML estático mas com headers específicos ou redirecionamentos.

```java
public class JsoupCrawler {

    public RawResponse fetch(String url, CrawlContext context) {
        Connection connection = Jsoup.connect(url)
            .userAgent(context.getUserAgent())
            .header("Accept-Language", "pt-BR")
            .timeout(12_000)
            .followRedirects(true)
            .ignoreHttpErrors(false);

        // Injeta cookies de sessão se existirem no contexto
        if (context.hasCookies()) {
            connection.cookies(context.getCookies());
        }

        Document doc = connection.get();

        return new RawResponse(
            doc.html(),
            200,
            RawResponseType.HTML,
            CrawlerStrategy.JSOUP
        );
    }
}
```

---

## 8. Crawler Playwright

Usado quando o tribunal depende de JavaScript para renderizar o conteúdo. Abre um browser headless, aguarda a renderização e captura o HTML final.

```java
@Component
public class PlaywrightCrawlerFactory {

    // Playwright é instanciado sob demanda, nunca mantido ocioso
    public RawResponse fetch(String url, CrawlContext context) {
        try (Playwright playwright = Playwright.create()) {
            BrowserType.LaunchOptions options = new BrowserType.LaunchOptions()
                .setHeadless(true)
                .setArgs(List.of(
                    "--no-sandbox",
                    "--disable-dev-shm-usage",
                    "--disable-blink-features=AutomationControlled"
                ));

            try (Browser browser = playwright.chromium().launch(options)) {
                BrowserContext browserContext = browser.newContext(
                    new Browser.NewContextOptions()
                        .setUserAgent(context.getUserAgent())
                        .setLocale("pt-BR")
                        .setTimezoneId("America/Sao_Paulo")
                        .setViewportSize(1280, 800)
                );

                Page page = browserContext.newPage();

                // Remove headers que identificam automação
                page.addInitScript(
                    "Object.defineProperty(navigator, 'webdriver', {get: () => undefined})"
                );

                page.navigate(url, new Page.NavigateOptions()
                    .setTimeout(30_000)
                    .setWaitUntil(WaitUntilState.NETWORKIDLE));

                // Aguarda seletor específico do tribunal (configurável por Provider)
                page.waitForSelector(context.getWaitForSelector(),
                    new Page.WaitForSelectorOptions().setTimeout(15_000));

                String html = page.content();

                return new RawResponse(
                    html,
                    200,
                    RawResponseType.HTML,
                    CrawlerStrategy.PLAYWRIGHT
                );
            }
        }
    }
}
```

**Importante:** Playwright é instanciado e encerrado a cada uso com `try-with-resources`. Nunca manter instâncias abertas entre consultas para economizar memória.

---

## 9. Crawler Selenium (Último Recurso)

Reservado para casos onde Playwright falha (compatibilidade específica, fingerprint diferente). É mais lento, consome mais memória e deve ser monitorado via feature flag.

```java
@Component
public class SeleniumCrawler {

    public RawResponse fetch(String url, CrawlContext context) {
        ChromeOptions options = new ChromeOptions();
        options.addArguments(
            "--headless=new",
            "--no-sandbox",
            "--disable-dev-shm-usage",
            "--disable-blink-features=AutomationControlled",
            "--user-agent=" + context.getUserAgent()
        );
        options.setExperimentalOption("excludeSwitches",
            List.of("enable-automation"));

        WebDriver driver = new ChromeDriver(options);
        try {
            driver.get(url);
            new WebDriverWait(driver, Duration.ofSeconds(30))
                .until(ExpectedConditions.presenceOfElementLocated(
                    By.cssSelector(context.getWaitForSelector())
                ));

            return new RawResponse(
                driver.getPageSource(),
                200,
                RawResponseType.HTML,
                CrawlerStrategy.SELENIUM
            );
        } finally {
            driver.quit(); // SEMPRE encerrar o driver
        }
    }
}
```

---

## 10. Modelo de Dados do Crawler

### `RawResponse`

Saída dos crawlers. Contém apenas dados brutos.

```java
public record RawResponse(
    String content,          // HTML ou JSON bruto
    int httpStatusCode,
    RawResponseType type,    // HTML, JSON
    CrawlerStrategy strategy // HTTP, JSOUP, PLAYWRIGHT, SELENIUM
) {}
```

### `ParsedData`

Saída dos parsers. Dados já estruturados, mas ainda não normalizados.

```java
public record ParsedData(
    String processNumber,
    String processTitle,
    List<RawMovement> movements,  // movimentações brutas do tribunal
    String parserVersion,
    LocalDateTime parsedAt
) {}

public record RawMovement(
    String rawDate,        // data como veio do tribunal: "15/03/2025"
    String rawDescription  // descrição crua, pode ter HTML ou espaços extras
) {}
```

### `ProcessSnapshot`

Saída dos Providers. Contrato com o resto do sistema.

```java
public record ProcessSnapshot(
    String processNumber,
    String courtCode,
    String contentHash,           // SHA-256 do conteúdo normalizado
    String rawContentJson,        // JSON serializado do conteúdo
    List<Movement> movements,     // movimentações normalizadas
    CrawlerStrategy strategyUsed,
    String parserVersion,
    Instant capturedAt
) {}

public record Movement(
    LocalDate date,
    String description            // texto limpo e normalizado
) {}
```

---

## 11. Pipeline Interno de um Provider

Fluxo completo dentro de um `CourtProvider.consultar()`:

```
consultar(processNumber)
    │
    ▼
[1] RateLimiter.acquire(courtCode)
    │  Bloqueia se exceder requisições por minuto
    │
    ▼
[2] DelayStrategy.apply(courtCode)
    │  Aguarda intervalo aleatório (minDelay..maxDelay ms)
    │
    ▼
[3] SessionManager.getOrCreate(courtCode)
    │  Recupera cookies/sessão do Redis (se aplicável)
    │
    ▼
[4] CrawlerChain.execute(url, context)
    │  Tenta cada estratégia em ordem
    │  Registra CrawlerExecution (sucesso ou falha por tentativa)
    │
    ▼
[5] RawResponse
    │
    ▼
[6] BlockDetector.check(rawResponse)
    │  Verifica sinais de bloqueio: status 403/429,
    │  presença de palavras como "CAPTCHA", "bloqueado", "acesso negado"
    │  → lança CourtBlockedException se detectado
    │
    ▼
[7] Parser.parse(rawResponse)
    │  Extrai dados estruturados → ParsedData
    │  → lança ParseException se HTML não corresponde ao esperado
    │
    ▼
[8] Validator.validate(parsedData)
    │  Verifica se campos obrigatórios estão presentes
    │  Verifica se há ao menos uma movimentação
    │  → lança ValidationException se inválido
    │
    ▼
[9] Normalizer.normalize(parsedData)
    │  Padroniza datas: "15/03/2025" → LocalDate(2025,3,15)
    │  Remove HTML de descrições
    │  Remove espaços duplos, caracteres inválidos
    │
    ▼
[10] HashGenerator.generate(normalizedData)
    │  Serializa canonicamente e gera SHA-256
    │
    ▼
[11] ProcessSnapshot (retorno)
```

---

## 12. Parser — Estrutura e Versionamento

### Estrutura de um Parser

Cada tribunal possui seu próprio Parser. Quando o HTML do tribunal muda, apenas o Parser é alterado.

```java
public interface CourtParser {
    ParsedData parse(RawResponse rawResponse);
    String getVersion();      // versão do parser: "1.0.0", "1.1.0"
    String getCourtCode();
}
```

**Exemplo — STF Parser:**

```java
@Component
public class STFParser implements CourtParser {

    private static final String VERSION = "1.1.0";

    @Override
    public ParsedData parse(RawResponse rawResponse) {
        Document doc = Jsoup.parse(rawResponse.content());

        // Seletor específico do STF — documentado e testado
        Elements movementRows = doc.select("table#tabelaTodasMovimentacoes tr");

        if (movementRows.isEmpty()) {
            throw new ParseException(
                "Seletor 'tabelaTodasMovimentacoes' não encontrou resultados. "
                + "O layout do STF pode ter mudado. Parser versão: " + VERSION
            );
        }

        List<RawMovement> movements = movementRows.stream()
            .skip(1) // pula cabeçalho
            .map(this::parseRow)
            .collect(Collectors.toList());

        return new ParsedData(
            extractProcessNumber(doc),
            extractTitle(doc),
            movements,
            VERSION,
            LocalDateTime.now()
        );
    }

    private RawMovement parseRow(Element row) {
        Elements cols = row.select("td");
        return new RawMovement(
            cols.get(0).text().trim(),   // coluna de data
            cols.get(1).text().trim()    // coluna de descrição
        );
    }

    @Override
    public String getVersion() { return VERSION; }

    @Override
    public String getCourtCode() { return "STF"; }
}
```

### Versionamento de Parsers

Cada vez que o layout de um tribunal muda e o parser precisa ser atualizado:

1. Incrementa a constante `VERSION` no arquivo Java (`"1.1.0"` → `"1.2.0"`)
2. Registra nova entrada em `parser_versions` via endpoint admin ou migração Flyway
3. Todos os `ProcessSnapshot` gerados a partir de então referenciam a nova versão
4. Se houver falha generalizada, o admin identifica exatamente a partir de qual versão os problemas começaram consultando `crawler_executions JOIN parser_versions`

### Fixtures de Teste dos Parsers

Para cada parser, devem existir fixtures HTML salvas em:

```
src/test/resources/fixtures/parsers/
├── stf/
│   ├── v1.0.0_processo_normal.html          # HTML real capturado na data de criação
│   ├── v1.0.0_processo_sem_movimentacoes.html
│   ├── v1.1.0_novo_layout_2025-02.html
│   └── v1.1.0_processo_com_captcha.html
├── eproc/
│   ├── v1.0.0_processo_normal.html
│   └── v1.0.0_processo_bloqueado.html
└── stjrj/
    └── v1.0.0_processo_normal.html
```

Cada fixture é nomeada com a versão do parser que a processa. Nunca deletar fixtures antigas — elas servem como prova do comportamento histórico do parser.

---

## 13. Estratégias Anti-Bloqueio

### 13.1 Rate Limiting

Implementado via `RateLimiter` do Guava ou Resilience4j, por tribunal:

```java
@Component
public class CourtRateLimiter {

    // Um RateLimiter por tribunal, configurado com o valor do banco
    private final Map<String, RateLimiter> limiters = new ConcurrentHashMap<>();

    public void acquire(String courtCode, int permitsPerMinute) {
        limiters.computeIfAbsent(courtCode,
            k -> RateLimiter.create(permitsPerMinute / 60.0)) // converte para por segundo
            .acquire();
    }
}
```

### 13.2 Delay Aleatório

Após cada consulta, aguarda um intervalo aleatório entre `minDelayMs` e `maxDelayMs` configurados no banco:

```java
private void applyDelay(Court court) {
    long delay = ThreadLocalRandom.current().nextLong(
        court.getMinDelayMs(),
        court.getMaxDelayMs()
    );
    Thread.sleep(delay);
}
```

### 13.3 Rotação de User-Agent

Pool de User-Agents reais de browsers modernos, rotacionado por consulta:

```java
@Component
public class UserAgentRotator {

    private static final List<String> USER_AGENTS = List.of(
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:123.0) Gecko/20100101 Firefox/123.0",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_3) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.3 Safari/605.1.15"
    );

    private final AtomicInteger index = new AtomicInteger(0);

    public String next() {
        return USER_AGENTS.get(index.getAndIncrement() % USER_AGENTS.size());
    }
}
```

### 13.4 Gerenciamento de Sessão (Redis)

Alguns tribunais exigem login ou manutenção de cookies de sessão. O `SessionManager` persiste a sessão no Redis entre consultas:

```java
@Component
public class SessionManager {

    private final RedisTemplate<String, String> redis;
    private static final String KEY_PREFIX = "crawler:session:";

    public Optional<Map<String, String>> getSession(String courtCode) {
        String json = redis.opsForValue().get(KEY_PREFIX + courtCode);
        return Optional.ofNullable(json).map(this::deserializeCookies);
    }

    public void saveSession(String courtCode, Map<String, String> cookies, Duration ttl) {
        redis.opsForValue().set(
            KEY_PREFIX + courtCode,
            serializeCookies(cookies),
            ttl
        );
    }

    public void invalidateSession(String courtCode) {
        redis.delete(KEY_PREFIX + courtCode);
    }
}
```

### 13.5 Detecção de Bloqueio

O `BlockDetector` analisa a resposta antes do parse para identificar sinais de bloqueio:

```java
@Component
public class BlockDetector {

    private static final List<String> BLOCK_SIGNALS = List.of(
        "captcha", "recaptcha", "acesso negado", "bloqueado",
        "muitas requisições", "too many requests", "403", "429"
    );

    public void check(RawResponse response) {
        if (response.httpStatusCode() == 403 || response.httpStatusCode() == 429) {
            throw new CourtBlockedException(
                "HTTP " + response.httpStatusCode() + " — possível bloqueio por IP"
            );
        }

        String lowerContent = response.content().toLowerCase();
        for (String signal : BLOCK_SIGNALS) {
            if (lowerContent.contains(signal)) {
                throw new CourtBlockedException(
                    "Sinal de bloqueio detectado: '" + signal + "'"
                );
            }
        }
    }
}
```

### 13.6 Proxy (Fase Futura)

A interface `FingerprintStrategy` foi projetada para suportar proxies quando necessário:

```java
public interface FingerprintStrategy {
    String getUserAgent();
    Map<String, String> getExtraHeaders();
    Optional<Proxy> getProxy();
}

// Implementações planejadas:
// DefaultFingerprintStrategy    → user-agent rotativo, sem proxy
// ResidentialProxyStrategy      → user-agent + proxy residencial
// CloudProxyStrategy            → user-agent + proxy datacenter
```

Não será implementado na fase inicial. A estrutura existe para que a adição seja cirúrgica.

---

## 14. Normalização do Número de Processo (CNJ)

Antes de qualquer consulta, o número de processo é normalizado para o formato CNJ padrão.

**Formato CNJ:** `NNNNNNN-DD.AAAA.J.TT.OOOO`

```
7 dígitos do processo
2 dígitos do dígito verificador
4 dígitos do ano
1 dígito da justiça
2 dígitos do tribunal
4 dígitos da origem
```

**Exemplo:** `0001234-55.2020.8.26.0001`

O `ProcessNumberNormalizer` aceita múltiplos formatos de entrada:

```java
@Component
public class ProcessNumberNormalizer {

    // Remove tudo que não for dígito e aplica a máscara CNJ
    public String normalize(String input) {
        String digits = input.replaceAll("[^0-9]", "");

        if (digits.length() != 20) {
            throw new InvalidProcessNumberException(
                "Número de processo deve ter 20 dígitos. Recebido: " + digits.length()
            );
        }

        // NNNNNNN-DD.AAAA.J.TT.OOOO
        return String.format("%s-%s.%s.%s.%s.%s",
            digits.substring(0, 7),   // processo
            digits.substring(7, 9),   // dígito verificador
            digits.substring(9, 13),  // ano
            digits.substring(13, 14), // justiça
            digits.substring(14, 16), // tribunal
            digits.substring(16, 20)  // origem
        );
    }
}
```

**Formatos de entrada aceitos:**
- `0001234-55.2020.8.26.0001` (já normalizado)
- `00012345520208260001` (só dígitos)
- `0001234.55.2020.8.26.0001` (ponto no lugar do hífen)
- `0001234 55 2020 8 26 0001` (espaços)

---

## 15. Mock Tribunal (Ambiente DEV)

O Mock Tribunal é um servidor Spring Boot separado que simula os tribunais reais para testes.

### Porta e roteamento

```
localhost:9000
├── /mock/stf/{processNumber}    → HTML simulado do STF
├── /mock/eproc/{processNumber}  → HTML simulado do eProc
├── /mock/stjrj/{processNumber}  → HTML simulado do STJRJ
└── /control/...                 → endpoints de controle
```

### Endpoints de controle

```
POST /control/inject-change/{court}/{processNumber}
     Body: { "description": "Nova movimentação de teste", "date": "2025-03-15" }
     Efeito: próxima consulta a este processo retornará um HTML com nova movimentação

POST /control/inject-timeout/{court}
     Efeito: próximas N requisições ao tribunal retornarão timeout
     Body: { "count": 3 }

POST /control/inject-block/{court}
     Efeito: próximas N requisições retornarão 403 com HTML de bloqueio
     Body: { "count": 2 }

POST /control/inject-captcha/{court}
     Efeito: próxima requisição retornará página com CAPTCHA

POST /control/inject-parse-error/{court}
     Efeito: próxima requisição retornará HTML com layout quebrado (sem seletores esperados)

POST /control/reset
     Efeito: reseta TODOS os estados injetados para o comportamento padrão (sucesso)

GET  /control/state
     Retorna estado atual de todas as injeções ativas
```

### Comportamento padrão do Mock

Por padrão, para qualquer número de processo válido, o Mock retorna um HTML estático que os Parsers conseguem interpretar corretamente, com 2–3 movimentações fixas. O `processNumber` é refletido no HTML para que o sistema identifique corretamente o processo.

### `MockCourtProvider`

Em DEV, o `CourtProviderFactory` usa um provider único que encaminha todas as consultas para o Mock Tribunal:

```java
@Component
@Profile("dev")
@Primary  // sobrescreve qualquer outro CourtProvider em DEV
public class MockCourtProvider implements CourtProvider {

    private final MockTribunalClient client;  // cliente HTTP para localhost:9000

    @Override
    public ProcessSnapshot consultar(String processNumber) {
        // Simula delay realista
        Thread.sleep(ThreadLocalRandom.current().nextLong(200, 800));

        RawResponse response = client.fetch(
            "http://localhost:9000/mock/" + detectCourt(processNumber) + "/" + processNumber
        );

        return buildSnapshot(response, processNumber);
    }
}
```

---

## 16. Registro de Execuções e Observabilidade

Toda tentativa de crawling — bem-sucedida ou não — é registrada em `crawler_executions`.

```java
@Component
public class CrawlerExecutionRecorder {

    private final CrawlerExecutionRepository repository;

    public CrawlerExecution record(CrawlerExecutionContext ctx) {
        CrawlerExecution execution = new CrawlerExecution();
        execution.setProcess(ctx.getProcess());
        execution.setCourt(ctx.getCourt());
        execution.setStrategy(ctx.getStrategy());
        execution.setSuccess(ctx.isSuccess());
        execution.setDurationMs(ctx.getDurationMs());
        execution.setHttpStatusCode(ctx.getHttpStatusCode());
        execution.setErrorType(ctx.getErrorType());
        execution.setErrorMessage(truncate(ctx.getErrorMessage(), 500));
        execution.setParserVersion(ctx.getParserVersion());
        execution.setExecutedAt(Instant.now());
        return repository.save(execution);
    }
}
```

**Métricas expostas via Micrometer:**

| Métrica | Tags | Tipo |
|---------|------|------|
| `crawler.executions.total` | `court`, `strategy`, `success` | Counter |
| `crawler.executions.duration` | `court`, `strategy` | Histogram |
| `crawler.errors.total` | `court`, `errorType` | Counter |
| `crawler.strategy.fallback` | `court`, `from`, `to` | Counter |
| `crawler.blocks.total` | `court` | Counter |

Estas métricas alimentam o cálculo do health score e o dashboard do painel admin.

---

## 17. Implementação dos Três Tribunais Iniciais

### 17.1 STF (Supremo Tribunal Federal)

| Item | Valor |
|------|-------|
| URL de consulta | `https://portal.stf.jus.br/processos/detalhe.asp?incidente={numero}` |
| Estratégia primária | HTTP Direto |
| Requer JavaScript | Não (HTML estático) |
| Requer login/sessão | Não |
| Seletor principal | `table#tabelaTodasMovimentacoes` |
| Rate limit | 5 req/min |
| Delay | 2000–5000ms |
| Sinais de bloqueio conhecidos | Status 403, texto "Captcha" |

### 17.2 eProc

| Item | Valor |
|------|-------|
| URL de consulta | A mapear durante implementação |
| Estratégia primária | Jsoup (lida melhor com sessão) |
| Requer JavaScript | Parcialmente |
| Requer login/sessão | Depende do processo (público vs. restrito) |
| Seletor principal | A mapear |
| Rate limit | 5 req/min |
| Delay | 2000–5000ms |
| Particularidade | Pode exigir Playwright para processos com JavaScript |

### 17.3 STJRJ (Superior Tribunal de Justiça - RJ)

| Item | Valor |
|------|-------|
| URL de consulta | A mapear durante implementação |
| Estratégia primária | HTTP Direto |
| Requer JavaScript | A verificar |
| Requer login/sessão | Não (consultas públicas) |
| Seletor principal | A mapear |
| Rate limit | 5 req/min |
| Delay | 2000–5000ms |

> Os detalhes de URL e seletores de cada tribunal serão levantados e documentados no início da Fase 6 do Roadmap, antes de qualquer linha de código ser escrita para cada Provider.

---

## 18. Checklist para Adicionar Novo Tribunal

Ao implementar um novo tribunal, seguir estritamente esta ordem:

- [ ] **1.** Inspecionar o portal do tribunal manualmente: identificar URL de consulta, estrutura HTML, presença de JavaScript, cookies necessários, sinais de bloqueio
- [ ] **2.** Salvar HTML de exemplo em `src/test/resources/fixtures/parsers/{tribunal}/v1.0.0_processo_normal.html`
- [ ] **3.** Criar e registrar o tribunal no banco via migration Flyway (`courts` + `parser_versions` + `court_feature_flags`)
- [ ] **4.** Criar `{Tribunal}Parser` com testes unitários baseados nas fixtures
- [ ] **5.** Criar `{Tribunal}HttpCrawler` (ou Jsoup, conforme necessidade)
- [ ] **6.** Criar `{Tribunal}Provider` implementando `CourtProvider`
- [ ] **7.** Adicionar cenário ao Mock Tribunal (`/mock/{tribunal}/`)
- [ ] **8.** Testar o fluxo completo em DEV com Mock Tribunal
- [ ] **9.** Ativar `court.active = true` no banco
- [ ] **10.** Monitorar health score nas primeiras 48h em produção
