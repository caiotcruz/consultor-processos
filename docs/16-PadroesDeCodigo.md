# 16 — Padrões de Código

**Consultor de Processos**
Software Design Document · v1.0

---

## 1. Filosofia

Código é lido muito mais do que escrito. Todo padrão aqui existe para reduzir a carga cognitiva de quem lê — inclusive você mesmo, seis meses depois.

**Regras que nunca são negociadas:**
1. O código deve ser autoexplicativo. Comentário que explica *o quê* o código faz é sinal de que o código precisa ser reescrito.
2. Nomes mentem menos que comentários. Um nome bom elimina a necessidade de documentação inline.
3. Funções fazem uma coisa. Se você precisar de "e" para descrever o que uma função faz, ela faz coisas demais.
4. Falhas devem ser explícitas. Nunca suprimir exceção silenciosamente.

---

## 2. Estrutura de Pacotes

Conforme definido em `04-Arquitetura.md` seção 4, a organização é **por módulo**, nunca por camada técnica.

```
✅ CORRETO
com.consultorprocessos.
  crawler.provider.stf.STFParser
  crawler.provider.stf.STFProvider
  crawler.service.SnapshotComparator
  process.controller.ProcessController
  process.service.ProcessService

❌ ERRADO
com.consultorprocessos.
  controller.ProcessController
  controller.AuthController
  service.ProcessService
  service.CrawlerService
  repository.ProcessRepository
```

> A estrutura por módulo torna imediatamente óbvio onde uma classe pertence e o que ela faz. A estrutura por camada técnica espelha o framework, não o domínio.

---

## 3. Nomenclatura

### 3.1 Classes

| Tipo | Sufixo | Exemplo |
|------|--------|---------|
| Controller REST | `Controller` | `ProcessController` |
| Serviço de negócio | `Service` | `ProcessService`, `MonitoringService` |
| Repositório JPA | `Repository` | `ProcessRepository` |
| Entidade JPA | (sem sufixo) | `Process`, `User`, `Court` |
| DTO de entrada | `Request` | `CreateProcessRequest`, `LoginRequest` |
| DTO de saída | `Response` ou `Dto` | `ProcessResponse`, `UserDto` |
| DTO interno (entre módulos) | `Dto` | `PendingProcess`, `CrawlRequestMessage` |
| Interface de Provider | `Provider` | `CourtProvider` |
| Implementação de Provider | `{Tribunal}Provider` | `STFProvider`, `EprocProvider` |
| Interface de canal de notificação | `Channel` | `NotificationChannel` |
| Implementação de canal | `{Canal}NotificationChannel` | `EmailNotificationChannel` |
| Crawler concreto | `{Tribunal}{Estrategia}Crawler` | `STFHttpCrawler`, `STFPlaywrightCrawler` |
| Parser concreto | `{Tribunal}Parser` | `STFParser`, `EprocParser` |
| Exceção de domínio | `Exception` | `ProcessLimitReachedException` |
| Exceção de infraestrutura | `Exception` | `CrawlException`, `NotificationException` |
| Evento de domínio | `Event` | `MovimentacaoDetectadaEvent` |
| Configuração Spring | `Config` | `RabbitConfig`, `SecurityConfig` |
| Utilitário | (sem sufixo genérico) | `ProcessNumberNormalizer`, `HashGenerator` |
| Enum | (sem sufixo) | `ProcessStatus`, `NotificationChannelType` |
| Record imutável | (sem sufixo) | `ProcessSnapshot`, `RawResponse` |

### 3.2 Métodos

Verbos no infinitivo. Sem prefixos desnecessários como `get` para métodos que fazem mais do que buscar.

```java
// ✅ CORRETO
processService.subscribe(user, processNumber, courtCode, alias)
monitoringService.findPendingProcesses()
snapshotComparator.compareAndPersist(processId, snapshot)
redisLockService.tryAcquire(key, ttl)
courtProvider.consultar(processNumber)
blockDetector.check(rawResponse)
normalizer.normalize(parsedData)

// ❌ ERRADO
processService.doSubscribe(user, processNumber, courtCode, alias)
monitoringService.getPendingProcesses()   // "get" implica acesso simples; findAll comunica busca com critério
snapshotComparator.compareSnapshotAndPersistIfChanged(...)  // longo demais
```

### 3.3 Variáveis e parâmetros

- `camelCase` para variáveis e parâmetros
- Nomes que revelam intenção; evitar abreviações exceto as consagradas (`id`, `dto`, `url`, `http`)
- Nunca usar nomes de uma letra exceto em loops triviais e lambdas de uma linha

```java
// ✅ CORRETO
List<ProcessSubscription> activeSubscriptions = subscriptionRepository.findActiveByUserId(userId);
long durationMs = System.currentTimeMillis() - startTime;
String normalizedNumber = normalizer.normalize(rawInput);

// ❌ ERRADO
List<ProcessSubscription> list = subscriptionRepository.findActiveByUserId(userId);
long d = System.currentTimeMillis() - startTime;
String n = normalizer.normalize(rawInput);
```

### 3.4 Constantes

`SCREAMING_SNAKE_CASE` em classes próprias ou no topo da classe que as usa.

```java
// Constantes de infraestrutura — em classes Config
public static final String QUEUE_CRAWL_REQUESTS = "crawl.requests";
public static final String EXCHANGE_CRAWL       = "crawl.exchange";

// Constantes de domínio — na própria classe
public class ProcessService {
    private static final int MAX_ALIAS_LENGTH     = 200;
    private static final int MAX_CONSECUTIVE_ERRORS = 3;
}
```

### 3.5 Pacotes

`lowercase`, singular para submódulos técnicos, plural para coleções de implementações.

```
crawler.provider.stf      ✅
crawler.providers.stf     ❌ (provider é sufixo da classe, não do pacote)
crawler.Provider.STF      ❌ (nunca maiúsculo)
```

---

## 4. Estrutura Interna de Classes

### 4.1 Ordem dos membros

```java
@Component  // ou @Service, @Controller, etc.
@Slf4j      // Lombok logger — sempre antes da classe
public class ExemploService {

    // 1. Constantes estáticas
    private static final int    MAX_RETRIES  = 3;
    private static final String KEY_PREFIX   = "exemplo:";

    // 2. Dependências injetadas (final — imutáveis)
    private final ExemploRepository repository;
    private final OutroService       outroService;

    // 3. Construtor (único — injeção via construtor, nunca @Autowired em campo)
    public ExemploService(ExemploRepository repository, OutroService outroService) {
        this.repository  = repository;
        this.outroService = outroService;
    }

    // 4. Métodos públicos (interface do serviço)
    public ResultDto executar(InputDto input) { ... }

    // 5. Métodos privados (implementação)
    private void validar(InputDto input) { ... }
    private ResultDto transformar(Entidade entidade) { ... }
}
```

### 4.2 Injeção de dependência

**Sempre por construtor.** Nunca `@Autowired` em campo.

```java
// ✅ CORRETO — injeção por construtor
@Service
@RequiredArgsConstructor  // Lombok gera o construtor com todos os campos final
public class ProcessService {
    private final ProcessRepository    processRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final CourtService         courtService;
}

// ❌ ERRADO — injeção por campo
@Service
public class ProcessService {
    @Autowired private ProcessRepository    processRepository;
    @Autowired private SubscriptionRepository subscriptionRepository;
}
```

**Por quê:** injeção por construtor permite testes sem Spring, torna dependências explícitas e evita dependências circulares difíceis de detectar.

---

## 5. Controllers

Controllers são finos. Recebem request, delegam, retornam response. Sem lógica de negócio.

```java
@RestController
@RequestMapping("/v1/processes")
@RequiredArgsConstructor
@Validated
public class ProcessController {

    private final ProcessService processService;

    @PostMapping
    public ResponseEntity<ApiResponse<SubscriptionResponse>> create(
            @RequestBody @Valid CreateProcessRequest request,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {

        SubscriptionResult result = processService.subscribe(
            userDetails.getUserId(),
            request.processNumber(),
            request.courtCode(),
            request.alias()
        );

        return ResponseEntity
            .status(result.isNewCourt() ? HttpStatus.ACCEPTED : HttpStatus.CREATED)
            .body(ApiResponse.success(SubscriptionMapper.toResponse(result)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<ProcessSummaryResponse>>> list(
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) ProcessStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
                Pageable pageable,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {

        Page<ProcessSummaryResponse> page = processService
            .listByUser(userDetails.getUserId(), active, status, pageable)
            .map(ProcessMapper::toSummaryResponse);

        return ResponseEntity.ok(ApiResponse.success(page));
    }
}
```

**Regras para controllers:**
- Máximo de 20 linhas por método de controller
- Nunca acessar repositório diretamente
- Nunca conter `if/else` de negócio
- Nunca lançar exceções de negócio diretamente — o serviço lança, o handler captura
- Sempre usar `@Valid` em request bodies

---

## 6. Services

Contêm toda a lógica de negócio. São o coração de cada módulo.

```java
@Service
@Transactional          // transação no nível do serviço, não do repositório
@RequiredArgsConstructor
@Slf4j
public class ProcessService {

    private final ProcessRepository          processRepository;
    private final SubscriptionRepository     subscriptionRepository;
    private final CourtService               courtService;
    private final PlanService                planService;
    private final ProcessNumberNormalizer    normalizer;
    private final CourtRequestService        courtRequestService;
    private final ApplicationEventPublisher  eventPublisher;

    public SubscriptionResult subscribe(UUID userId, String rawNumber,
                                        String courtCode, String alias) {
        String normalizedNumber = normalizer.normalize(rawNumber);

        Court court = courtService.findByCode(courtCode)
            .orElseGet(() -> {
                courtRequestService.register(userId, courtCode, normalizedNumber);
                return null;
            });

        if (court == null) {
            return SubscriptionResult.courtUnavailable();
        }

        planService.assertHasCapacity(userId); // lança ProcessLimitReachedException se cheio

        Process process = processRepository
            .findByProcessNumberAndCourtId(normalizedNumber, court.getId())
            .orElseGet(() -> createProcess(normalizedNumber, rawNumber, court));

        if (subscriptionRepository.existsByUserIdAndProcessId(userId, process.getId())) {
            throw new SubscriptionAlreadyExistsException(normalizedNumber);
        }

        ProcessSubscription subscription = new ProcessSubscription();
        subscription.setUserId(userId);
        subscription.setProcess(process);
        subscription.setAlias(alias);
        subscriptionRepository.save(subscription);

        log.info("Usuário {} assinou processo {} no tribunal {}.",
            userId, normalizedNumber, courtCode);

        return SubscriptionResult.created(subscription);
    }

    @Transactional(readOnly = true)
    public Page<ProcessSummary> listByUser(UUID userId, Boolean active,
                                           ProcessStatus status, Pageable pageable) {
        return subscriptionRepository.findByUserId(userId, active, status, pageable);
    }

    private Process createProcess(String normalizedNumber, String rawNumber, Court court) {
        Process process = new Process();
        process.setProcessNumber(normalizedNumber);
        process.setProcessNumberRaw(rawNumber);
        process.setCourt(court);
        process.setStatus(ProcessStatus.PENDING);
        return processRepository.save(process);
    }
}
```

**Regras para services:**
- `@Transactional` no nível do serviço; `readOnly = true` para métodos de leitura
- Lançar exceções de domínio específicas, nunca genéricas
- Logar ações significativas com `log.info` (criação, mudanças de estado)
- Logar avisos com `log.warn` (situações incomuns mas recuperáveis)
- Logar erros com `log.error` apenas para falhas inesperadas

---

## 7. Repositórios

Estendem `JpaRepository`. Métodos de query seguem a convenção de nomenclatura do Spring Data ou usam `@Query` para queries complexas.

```java
public interface ProcessRepository extends JpaRepository<Process, UUID> {

    // Convenção de nomenclatura Spring Data — sem @Query necessário
    Optional<Process> findByProcessNumberAndCourtId(String processNumber, UUID courtId);

    // @Query para queries complexas — sempre JPQL ou SQL nativo documentado
    @Query("""
        SELECT DISTINCT p FROM Process p
        JOIN p.subscriptions s
        JOIN s.user u
        JOIN u.plan pl
        WHERE p.status IN ('PENDING', 'OK')
          AND s.active = true
          AND u.status = 'ACTIVE'
          AND (
              p.lastCheckedAt IS NULL OR
              p.lastCheckedAt < :cutoff
          )
        GROUP BY p
        HAVING MIN(pl.checkIntervalHours) <= :intervalHours
        ORDER BY p.lastCheckedAt ASC NULLS FIRST
        """)
    List<PendingProcessProjection> findAllPendingWithEffectiveInterval(
        @Param("cutoff") Instant cutoff,
        @Param("intervalHours") int intervalHours
    );

    // Projeções para queries de listagem — evita SELECT * desnecessário
    @Query("SELECT p.id as processId, p.processNumber, p.status, p.lastCheckedAt " +
           "FROM Process p WHERE p.id = :id")
    Optional<ProcessSummaryProjection> findSummaryById(@Param("id") UUID id);

    // Métodos de update com @Modifying — sem carregar entidade
    @Modifying
    @Query("UPDATE Process p SET p.lastCheckedAt = :checkedAt WHERE p.id = :id")
    void updateLastChecked(@Param("id") UUID id, @Param("checkedAt") Instant checkedAt);

    @Modifying
    @Query("UPDATE Process p SET p.consecutiveErrors = p.consecutiveErrors + 1 WHERE p.id = :id")
    void incrementConsecutiveErrors(@Param("id") UUID id);
}
```

**Regras para repositórios:**
- Nunca conter lógica de negócio
- Queries complexas sempre com `@Query` e JPQL (preferível) ou SQL nativo
- SQL nativo documentado com comentário explicando por que JPQL não é suficiente
- Usar projeções (`Projection`) para queries de listagem — evita `SELECT *`
- `@Modifying` para updates/deletes sem carregar entidade

---

## 8. DTOs e Mapeamento

### 8.1 Records Java para DTOs imutáveis

```java
// Request — validado com Bean Validation
public record CreateProcessRequest(
    @NotBlank(message = "Número do processo é obrigatório.")
    @Size(max = 50, message = "Número do processo muito longo.")
    String processNumber,

    @NotBlank(message = "Código do tribunal é obrigatório.")
    @Size(max = 20)
    String courtCode,

    @Size(max = 200, message = "Alias não pode ter mais de 200 caracteres.")
    String alias
) {}

// Response — imutável, serializado pelo Jackson
public record SubscriptionResponse(
    UUID   subscriptionId,
    UUID   processId,
    String processNumber,
    String alias,
    CourtSummary court,
    ProcessStatus status,
    boolean active,
    Instant createdAt
) {}

public record CourtSummary(String code, String name) {}
```

### 8.2 Mappers — sem biblioteca de mapeamento mágico

Mappers são métodos estáticos explícitos. Sem MapStruct ou ModelMapper — o mapeamento deve ser legível e debugável.

```java
public class ProcessMapper {

    private ProcessMapper() {} // utilitário — sem instância

    public static SubscriptionResponse toResponse(SubscriptionResult result) {
        return new SubscriptionResponse(
            result.subscription().getId(),
            result.subscription().getProcess().getId(),
            result.subscription().getProcess().getProcessNumber(),
            result.subscription().getAlias(),
            new CourtSummary(
                result.subscription().getProcess().getCourt().getCode(),
                result.subscription().getProcess().getCourt().getName()
            ),
            result.subscription().getProcess().getStatus(),
            result.subscription().isActive(),
            result.subscription().getCreatedAt()
        );
    }

    public static ProcessSummaryResponse toSummaryResponse(ProcessSubscription sub) {
        Process process = sub.getProcess();
        return new ProcessSummaryResponse(
            sub.getId(),
            process.getId(),
            process.getProcessNumber(),
            sub.getAlias(),
            new CourtSummary(process.getCourt().getCode(), process.getCourt().getName()),
            process.getStatus(),
            sub.isActive(),
            process.getLastCheckedAt(),
            process.getLastMovementAt(),
            sub.getCreatedAt()
        );
    }
}
```

---

## 9. Tratamento de Exceções

### 9.1 Hierarquia de exceções

```java
// Raiz de todas as exceções de domínio
public abstract class DomainException extends RuntimeException {
    private final String errorCode;
    private final HttpStatus httpStatus;

    protected DomainException(String errorCode, String message, HttpStatus httpStatus) {
        super(message);
        this.errorCode  = errorCode;
        this.httpStatus = httpStatus;
    }
    // getters
}

// Exceções concretas de negócio
public class ProcessLimitReachedException extends DomainException {
    public ProcessLimitReachedException(int limit) {
        super("PROCESS_LIMIT_REACHED",
            "Você atingiu o limite de " + limit + " processos do seu plano.",
            HttpStatus.UNPROCESSABLE_ENTITY);
    }
}

public class SubscriptionAlreadyExistsException extends DomainException {
    public SubscriptionAlreadyExistsException(String processNumber) {
        super("SUBSCRIPTION_ALREADY_EXISTS",
            "Você já acompanha o processo " + processNumber + ".",
            HttpStatus.CONFLICT);
    }
}

public class CourtNotFoundException extends DomainException {
    public CourtNotFoundException(String code) {
        super("NOT_FOUND",
            "Tribunal '" + code + "' não encontrado.",
            HttpStatus.NOT_FOUND);
    }
}

// Exceções de infraestrutura (não são DomainException)
public class CrawlException extends RuntimeException {
    public CrawlException(String courtCode, String message, Throwable cause) {
        super("Falha ao consultar tribunal " + courtCode + ": " + message, cause);
    }
}

public class CourtBlockedException extends RuntimeException {
    public CourtBlockedException(String reason) {
        super("Tribunal bloqueou o acesso: " + reason);
    }
}
```

### 9.2 Handler global

```java
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // Exceções de domínio — mensagem controlada, status definido pela exceção
    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiResponse<Void>> handleDomain(DomainException ex) {
        return ResponseEntity
            .status(ex.getHttpStatus())
            .body(ApiResponse.error(ex.getErrorCode(), ex.getMessage()));
    }

    // Validação de Bean Validation (@Valid)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(
            MethodArgumentNotValidException ex) {

        List<FieldErrorDetail> details = ex.getBindingResult().getFieldErrors().stream()
            .map(e -> new FieldErrorDetail(e.getField(), e.getDefaultMessage()))
            .collect(Collectors.toList());

        return ResponseEntity
            .badRequest()
            .body(ApiResponse.validationError(details));
    }

    // Autenticação / Autorização
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .body(ApiResponse.error("FORBIDDEN", "Acesso negado."));
    }

    // Erros inesperados — nunca expor stack trace ao cliente
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex,
                                                            HttpServletRequest request) {
        log.error("Erro inesperado em {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.error("INTERNAL_ERROR",
                "Ocorreu um erro interno. Tente novamente em instantes."));
    }
}
```

**Regra de ouro:** o cliente nunca recebe stack trace, nome de classe, mensagem de SQL ou qualquer detalhe interno. Sempre mensagem de negócio controlada.

---

## 10. Envelope de Resposta

```java
public record ApiResponse<T>(
    boolean       success,
    T             data,
    ErrorDetail   error,
    PageMeta      meta
) {
    // Factory methods — nunca instanciar diretamente
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null, null);
    }

    public static <T> ApiResponse<T> success(Page<T> page) {
        return new ApiResponse<>(true, page.getContent(), null,
            new PageMeta(page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages()));
    }

    public static ApiResponse<Void> error(String code, String message) {
        return new ApiResponse<>(false, null,
            new ErrorDetail(code, message, List.of()), null);
    }

    public static ApiResponse<Void> validationError(List<FieldErrorDetail> details) {
        return new ApiResponse<>(false, null,
            new ErrorDetail("VALIDATION_ERROR",
                "Um ou mais campos são inválidos.", details), null);
    }
}

public record ErrorDetail(
    String               code,
    String               message,
    List<FieldErrorDetail> details
) {}

public record FieldErrorDetail(String field, String message) {}

public record PageMeta(int page, int pageSize, long totalElements, int totalPages) {}
```

---

## 11. Logs

### 11.1 Níveis de log

| Nível | Quando usar |
|-------|-------------|
| `ERROR` | Falha inesperada que requer atenção imediata. Sempre com stack trace. |
| `WARN` | Situação incomum mas recuperável. Retry, bloqueio de tribunal, token inválido. |
| `INFO` | Eventos de negócio significativos. Processo cadastrado, movimentação detectada, usuário criado. |
| `DEBUG` | Detalhes de execução úteis para desenvolvimento. Nunca em produção. |
| `TRACE` | Rastreamento granular. Apenas para debug pontual; nunca commitado ativo. |

### 11.2 Formato

Todos os logs seguem formato estruturado. O Logback serializa como JSON em produção.

```java
// ✅ CORRETO — mensagem com contexto, dados como parâmetros (lazy)
log.info("Movimentação detectada. processo={} tribunal={} hash={}",
    process.getProcessNumber(), courtCode, newHash);

log.warn("Falha na consulta. processo={} tentativa={}/{} erro={}",
    processNumber, retryCount, MAX_RETRIES, e.getMessage());

log.error("Erro inesperado ao processar mensagem. processId={} erro={}",
    message.processId(), e.getMessage(), e);  // stack trace como último parâmetro

// ❌ ERRADO — concatenação de string (avaliada mesmo se o nível estiver desabilitado)
log.debug("Processando: " + processNumber + " no tribunal: " + courtCode);

// ❌ ERRADO — dados sensíveis no log
log.info("Usuário {} com e-mail {} fez login.", user.getId(), user.getEmail());
// ✅ CORRETO — mascarar e-mail
log.info("Login realizado. userId={}", user.getId());
```

### 11.3 Propagação de `traceId`

Todo request HTTP recebe um `traceId` que é propagado por toda a cadeia de processamento, incluindo mensagens de fila.

```java
@Component
public class TraceIdFilter implements Filter {

    private static final String TRACE_ID_HEADER = "X-Trace-Id";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {
        String traceId = ((HttpServletRequest) request).getHeader(TRACE_ID_HEADER);
        if (traceId == null) {
            traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }
        MDC.put("traceId", traceId);
        ((HttpServletResponse) response).setHeader(TRACE_ID_HEADER, traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}
```

---

## 12. Entidades JPA

```java
@Entity
@Table(name = "processes",
    uniqueConstraints = @UniqueConstraint(
        name = "processes_number_court_unique",
        columnNames = {"process_number", "court_id"}
    )
)
@Getter
@Setter
@NoArgsConstructor
public class Process {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "process_number", nullable = false, length = 25)
    private String processNumber;

    @Column(name = "process_number_raw", nullable = false, length = 50)
    private String processNumberRaw;

    @ManyToOne(fetch = FetchType.LAZY)  // sempre LAZY — carregar explicitamente quando necessário
    @JoinColumn(name = "court_id", nullable = false)
    private Court court;

    @Enumerated(EnumType.STRING)       // sempre STRING — nunca ORDINAL
    @Column(name = "status", nullable = false, length = 20)
    private ProcessStatus status = ProcessStatus.PENDING;

    @Column(name = "last_checked_at")
    private Instant lastCheckedAt;

    @Column(name = "last_movement_at")
    private Instant lastMovementAt;

    @Column(name = "last_snapshot_hash", length = 64)
    private String lastSnapshotHash;

    @Column(name = "consecutive_errors", nullable = false)
    private int consecutiveErrors = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
```

**Regras para entidades:**
- `FetchType.LAZY` em todos os relacionamentos — carregar explicitamente com `JOIN FETCH` quando necessário
- `@Enumerated(EnumType.STRING)` sempre — nunca `ORDINAL` (quebraria ao reordenar o enum)
- `@PrePersist` e `@PreUpdate` para `createdAt` e `updatedAt`
- Sem lógica de negócio nas entidades — são apenas mapeamento de dados
- Lombok `@Getter @Setter @NoArgsConstructor` — sem `@Data` (evita problemas com `equals/hashCode` em entidades JPA)

---

## 13. Configuração Spring

```java
@Configuration
@EnableAsync         // se módulo usa @Async
@EnableScheduling    // apenas em SchedulerConfig
public class SchedulerConfig {

    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("scheduler-");
        scheduler.setErrorHandler(t ->
            log.error("Erro não tratado no scheduler: {}", t.getMessage(), t));
        return scheduler;
    }
}
```

**Regras para classes de configuração:**
- Uma classe de configuração por responsabilidade (`RabbitConfig`, `SecurityConfig`, `RedisConfig`)
- Nunca misturar configurações de diferentes infraestruturas na mesma classe
- Beans com nome explícito quando houver ambiguidade
- Propriedades via `@ConfigurationProperties` para grupos de configuração relacionados

```java
@ConfigurationProperties(prefix = "app.scheduler")
@Validated
public record SchedulerProperties(
    @NotNull boolean enabled,
    @NotBlank String  cron,
    @Min(30) int      lockTtlSeconds
) {}
```

---

## 14. Testes — Padrões de Escrita

Conforme detalhado em `13-Testes.md`, reforçando as convenções de nomenclatura:

```java
// Estrutura: dado → quando → então (Given → When → Then)
@Test
@DisplayName("deve lançar ProcessLimitReachedException quando usuário atingir limite do plano")
void shouldThrowWhenPlanLimitReached() {
    // Given
    User user = TestBuilders.buildActiveUser();
    user.setPlan(TestBuilders.buildGratuitoPlan()); // max 5 processos
    createSubscriptions(user, 5);

    // When / Then
    assertThatThrownBy(() ->
        processService.subscribe(user.getId(), "0009999-11.2025.8.26.0001", "STF", null)
    )
    .isInstanceOf(ProcessLimitReachedException.class)
    .hasMessageContaining("5");
}
```

**Regras:**
- `@DisplayName` em português, descritivo, sempre com "deve"
- Estrutura Given/When/Then com comentários quando não óbvio
- Um `assert` por teste — exceções para testes de validação com múltiplos campos
- `TestBuilders` para toda criação de objeto de teste
- Mocks somente para dependências externas (banco, fila, HTTP) — nunca para lógica interna

---

## 15. Git — Branches e Commits

### 15.1 Estratégia de branches

```
main          ← produção; nunca commitar diretamente
  └─ develop  ← integração (opcional para times maiores)
       └─ feature/nome-descritivo
       └─ fix/nome-do-bug
       └─ refactor/nome-da-mudanca
       └─ docs/nome-do-documento
```

### 15.2 Nomenclatura de branches

```
feature/add-stf-provider
feature/implement-retry-policy
fix/scheduler-duplicate-enqueue
fix/stf-parser-selector-change
refactor/extract-rate-limiter
docs/update-crawler-architecture
```

### 15.3 Commits — Conventional Commits

```
<tipo>(<escopo>): <descrição em imperativo, minúsculo>

[corpo opcional — por quê, não o quê]

[rodapé opcional — breaking changes, closes #issue]
```

**Tipos:**

| Tipo | Quando usar |
|------|-------------|
| `feat` | Nova funcionalidade |
| `fix` | Correção de bug |
| `refactor` | Refatoração sem mudança de comportamento |
| `test` | Adiciona ou corrige testes |
| `docs` | Documentação |
| `chore` | Configuração, dependências, build |
| `perf` | Melhoria de performance |
| `style` | Formatação, sem mudança lógica |

**Exemplos:**

```
feat(crawler): add STF provider with HTTP and Jsoup fallback

Implements STFProvider, STFHttpCrawler, STFJsoupCrawler and STFParser.
Fixtures added for v1.0.0. Parser tested against real HTML captured on 2025-03-15.

fix(scheduler): prevent duplicate enqueue when Redis lock already exists

The scheduler was publishing the same process twice when two executions
overlapped. Added tryAcquire check before publishing to crawl.requests.

refactor(process): extract plan capacity check to PlanService

Moved the limit validation from ProcessService to a dedicated PlanService
to make it reusable in the reactivation flow.

test(auth): add integration tests for login lockout mechanism

chore(deps): upgrade Spring Boot to 3.2.4
```

### 15.4 Pull Requests

Todo PR deve:
- Ter título no formato Conventional Commits
- Descrever **o que** mudou e **por quê**
- Referenciar o requisito (ex: `RF-CRAWL-003`)
- Ter CI verde antes de merge
- Ser revisado por ao menos uma pessoa (quando em time)
- Não misturar refatoração com nova funcionalidade

---

## 16. Checklist de Qualidade por Entregável

### Novo endpoint
- [ ] Controller delega para service; sem lógica no controller
- [ ] Request validado com `@Valid` e Bean Validation
- [ ] Resposta usa `ApiResponse<T>`
- [ ] Exceções de negócio lançadas como `DomainException`
- [ ] Teste de integração cobrindo sucesso + erro principal
- [ ] Teste de segurança (acesso sem token, acesso com role errada)
- [ ] Documentado em `07-API.md`

### Novo tribunal
- [ ] Checklist de `09-Crawlers.md` seção 18 seguido integralmente
- [ ] Parser tem testes com fixtures HTML
- [ ] Mock Tribunal tem cenário para o novo tribunal
- [ ] Migration Flyway cria registro em `courts` + `parser_versions` + `court_feature_flags`
- [ ] Health score monitorado nas primeiras 48h

### Nova entidade JPA
- [ ] Migration Flyway numerada sequencialmente
- [ ] `createdAt` e `updatedAt` com `@PrePersist`/`@PreUpdate`
- [ ] Todos os relacionamentos com `FetchType.LAZY`
- [ ] Enums com `@Enumerated(EnumType.STRING)`
- [ ] Índices definidos na migration

### Nova exceção de domínio
- [ ] Estende `DomainException`
- [ ] `errorCode` corresponde à tabela de códigos de `07-API.md`
- [ ] Mensagem em português, sem dados de sistema internos
- [ ] Coberta por teste (que lança a exceção e verifica o código)

### Novo canal de notificação
- [ ] Implementa `NotificationChannel`
- [ ] `isEnabledFor(prefs)` implementado corretamente
- [ ] Falha isolada (não interrompe outros canais)
- [ ] Registra em `NotificationHistory`
- [ ] Tem `LogOnly` equivalente para DEV
- [ ] Zero modificações em `NotificationService`

---

## 17. O que Nunca Fazer

| Proibição | Motivo |
|-----------|--------|
| `System.out.println` | Usar `log.debug` |
| `e.printStackTrace()` | Usar `log.error("...", e)` |
| Suprimir exceção com catch vazio | Falhas devem ser explícitas |
| `@Transactional` em controller | Transação pertence ao serviço |
| `FetchType.EAGER` | Causa N+1 e carregamento desnecessário |
| `@Enumerated(EnumType.ORDINAL)` | Quebrará ao reordenar o enum |
| `@Data` em entidade JPA | `equals/hashCode` baseado em todos os campos causa problemas |
| Lógica de negócio em entidade | Entidades são mapeamento, não comportamento |
| String hardcoded de `courtCode` fora do Provider | Viola Open/Closed |
| `if (courtCode.equals("STF"))` fora de `STFProvider` | Idem |
| Acessar repositório no controller | Viola separação de camadas |
| Expor stack trace ao cliente | Vazamento de informação interna |
| Commitar com `.env` | Dados sensíveis nunca no repositório |
| `spring.jpa.hibernate.ddl-auto=create` em produção | Apaga o banco |
| Requisição real a tribunal em perfil `dev` ou `test` | Viola isolamento de ambiente |
