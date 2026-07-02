# 13 — Testes

**Consultor de Processos**
Software Design Document · v1.0

---

## 1. Filosofia de Testes

O sistema foi projetado com testabilidade como requisito de primeira classe, não como adendo. Três princípios guiam a estratégia:

**1. Testes determinísticos acima de tudo.** Nenhum teste depende de tribunal real, de horário do sistema ou de dados não controlados. O Mock Tribunal e o Testcontainers garantem isso.

**2. Testar comportamento, não implementação.** Testes verificam o que o código faz, não como ele faz. Trocar Jsoup por Playwright internamente não deve quebrar nenhum teste de nível superior.

**3. A pirâmide é respeitada.** A maioria dos testes é unitária (rápida, barata). Testes de integração cobrem contratos entre módulos. Testes end-to-end cobrem apenas os fluxos mais críticos.

```
        /\
       /  \   E2E (poucos — fluxos críticos)
      /────\
     /      \  Integração (contratos entre módulos, API)
    /────────\
   /          \  Unitários (lógica de negócio, parsers, comparadores)
  /────────────\
```

---

## 2. Metas de Cobertura

| Módulo | Meta de cobertura | Justificativa |
|--------|------------------|---------------|
| `crawler` (Provider, Parser, Comparator) | > 85% | Núcleo do sistema; falha aqui é silenciosa |
| `monitoring` | > 80% | Lógica de decisão complexa |
| `scheduler` | > 75% | Coordena todo o pipeline |
| `auth` | > 80% | Segurança crítica |
| `process` | > 75% | Regras de negócio do usuário |
| `notification` | > 70% | Canais são simples; orquestração é o risco |
| `admin` | > 60% | Majoritariamente leitura; menos risco |
| `payment` | > 80% | Dinheiro; zero tolerância para bugs |

> Cobertura de linha é um indicador, não um fim. Um teste que apenas incrementa cobertura sem verificar comportamento é inútil.

---

## 3. Dependências de Teste

```xml
<!-- pom.xml -->
<dependencies>
    <!-- Spring Boot Test (JUnit 5, Mockito, AssertJ incluídos) -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>

    <!-- Testcontainers: banco e infra reais em containers -->
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>postgresql</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>rabbitmq</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>com.redis</groupId>
        <artifactId>testcontainers-redis</artifactId>
        <scope>test</scope>
    </dependency>

    <!-- WireMock: simulação de HTTP externo (tribunais) -->
    <dependency>
        <groupId>org.wiremock</groupId>
        <artifactId>wiremock-standalone</artifactId>
        <scope>test</scope>
    </dependency>

    <!-- Awaitility: asserções assíncronas (filas, eventos) -->
    <dependency>
        <groupId>org.awaitility</groupId>
        <artifactId>awaitility</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

---

## 4. Testes Unitários

### 4.1 Estrutura de pacotes de teste

```
src/test/java/com/consultorprocessos/
├── auth/
│   ├── JwtServiceTest.java
│   ├── PasswordHashServiceTest.java
│   └── RefreshTokenServiceTest.java
├── crawler/
│   ├── provider/
│   │   ├── stf/
│   │   │   ├── STFParserTest.java
│   │   │   └── STFProviderTest.java
│   │   ├── eproc/
│   │   │   └── EprocParserTest.java
│   │   └── stjrj/
│   │       └── STJRJParserTest.java
│   ├── HashGeneratorTest.java
│   ├── SnapshotComparatorTest.java
│   ├── BlockDetectorTest.java
│   └── ProcessNumberNormalizerTest.java
├── monitoring/
│   └── MonitoringServiceTest.java
├── scheduler/
│   └── SchedulerServiceTest.java
├── process/
│   ├── ProcessServiceTest.java
│   └── SubscriptionServiceTest.java
├── notification/
│   ├── NotificationServiceTest.java
│   ├── EmailNotificationChannelTest.java
│   └── TemplateRendererTest.java
└── shared/
    └── CNJValidatorTest.java
```

### 4.2 Testes de Parser (os mais importantes)

Os parsers são os componentes mais frágeis — quebram quando o tribunal muda o HTML. Por isso possuem a cobertura mais rigorosa, baseada em **fixtures reais**.

```java
class STFParserTest {

    private final STFParser parser = new STFParser();

    @Test
    @DisplayName("deve extrair movimentações de HTML válido do STF")
    void shouldParseValidHtml() {
        String html = loadFixture("stf/v1.1.0_processo_normal.html");
        RawResponse raw = new RawResponse(html, 200, RawResponseType.HTML, CrawlerStrategy.HTTP);

        ParsedData result = parser.parse(raw);

        assertThat(result.processNumber()).isEqualTo("0001234-55.2020.8.26.0001");
        assertThat(result.movements()).hasSize(3);
        assertThat(result.movements().get(0).rawDate()).isEqualTo("15/03/2025");
        assertThat(result.movements().get(0).rawDescription())
            .isEqualTo("Conclusos para julgamento.");
        assertThat(result.parserVersion()).isEqualTo("1.1.0");
    }

    @Test
    @DisplayName("deve lançar ParseException quando seletor principal não for encontrado")
    void shouldThrowWhenSelectorNotFound() {
        String html = loadFixture("stf/v1.1.0_layout_quebrado.html");
        RawResponse raw = new RawResponse(html, 200, RawResponseType.HTML, CrawlerStrategy.HTTP);

        assertThatThrownBy(() -> parser.parse(raw))
            .isInstanceOf(ParseException.class)
            .hasMessageContaining("tabelaTodasMovimentacoes");
    }

    @Test
    @DisplayName("deve retornar lista vazia quando processo não tem movimentações")
    void shouldReturnEmptyMovementsWhenNone() {
        String html = loadFixture("stf/v1.1.0_sem_movimentacoes.html");
        RawResponse raw = new RawResponse(html, 200, RawResponseType.HTML, CrawlerStrategy.HTTP);

        ParsedData result = parser.parse(raw);

        assertThat(result.movements()).isEmpty();
    }

    private String loadFixture(String path) {
        return new String(
            getClass().getClassLoader()
                .getResourceAsStream("fixtures/parsers/" + path)
                .readAllBytes()
        );
    }
}
```

### 4.3 Teste do `HashGenerator`

```java
class HashGeneratorTest {

    private final HashGenerator generator = new HashGenerator();

    @Test
    @DisplayName("mesmo conteúdo deve gerar mesmo hash")
    void shouldGenerateSameHashForSameContent() {
        ProcessSnapshot s1 = buildSnapshot("Conclusos para julgamento.", "15/03/2025");
        ProcessSnapshot s2 = buildSnapshot("Conclusos para julgamento.", "15/03/2025");

        assertThat(generator.generate(s1)).isEqualTo(generator.generate(s2));
    }

    @Test
    @DisplayName("conteúdo diferente deve gerar hash diferente")
    void shouldGenerateDifferentHashForDifferentContent() {
        ProcessSnapshot s1 = buildSnapshot("Conclusos para julgamento.", "15/03/2025");
        ProcessSnapshot s2 = buildSnapshot("Julgamento realizado.",       "16/03/2025");

        assertThat(generator.generate(s1)).isNotEqualTo(generator.generate(s2));
    }

    @Test
    @DisplayName("espaços e capitalização não devem afetar o hash após normalização")
    void shouldBeInsensitiveToWhitespaceAfterNormalization() {
        ProcessSnapshot s1 = buildSnapshot("Conclusos para julgamento.", "15/03/2025");
        ProcessSnapshot s2 = buildSnapshot("  conclusos para julgamento.  ", "15/03/2025");

        // O Normalizer padroniza antes de gerar o hash
        assertThat(generator.generate(s1)).isEqualTo(generator.generate(s2));
    }
}
```

### 4.4 Teste do `SnapshotComparator`

```java
@ExtendWith(MockitoExtension.class)
class SnapshotComparatorTest {

    @Mock private ProcessRepository           processRepository;
    @Mock private ProcessSnapshotRepository   snapshotRepository;
    @Mock private ProcessHistoryRepository    historyRepository;
    @Mock private ApplicationEventPublisher   eventPublisher;
    @Mock private CrawlerExecutionRecorder    recorder;

    @InjectMocks
    private SnapshotComparator comparator;

    @Test
    @DisplayName("não deve publicar evento quando hash for idêntico")
    void shouldNotPublishEventWhenHashUnchanged() {
        Process process = buildProcess("hash-antigo");
        ProcessSnapshot snapshot = buildSnapshot("hash-antigo"); // mesmo hash

        when(processRepository.findById(any())).thenReturn(Optional.of(process));

        comparator.compareAndPersist(process.getId(), snapshot);

        verify(eventPublisher, never()).publishEvent(any());
        verify(snapshotRepository, never()).save(any());
        verify(processRepository).updateLastChecked(eq(process.getId()), any());
    }

    @Test
    @DisplayName("deve salvar snapshot e publicar evento quando hash mudar")
    void shouldSaveAndPublishEventWhenHashChanged() {
        Process process   = buildProcess("hash-antigo");
        ProcessSnapshot snapshot = buildSnapshot("hash-novo"); // hash diferente

        when(processRepository.findById(any())).thenReturn(Optional.of(process));
        when(snapshotRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        comparator.compareAndPersist(process.getId(), snapshot);

        verify(snapshotRepository).save(any());
        verify(historyRepository).saveAll(anyList());
        verify(eventPublisher).publishEvent(any(MovimentacaoDetectadaEvent.class));
    }
}
```

### 4.5 Teste do `MonitoringService`

```java
@ExtendWith(MockitoExtension.class)
class MonitoringServiceTest {

    @Mock private ProcessRepository processRepository;
    @InjectMocks private MonitoringService monitoringService;

    @Test
    @DisplayName("deve retornar processos cuja lastCheckedAt ultrapassou o intervalo efetivo")
    void shouldReturnProcessesDueForCheck() {
        PendingProcess pendingProcess = new PendingProcess(
            UUID.randomUUID(), "STF", "0001234-55.2020.8.26.0001"
        );
        when(processRepository.findAllPendingWithEffectiveInterval())
            .thenReturn(List.of(pendingProcess));

        List<PendingProcess> result = monitoringService.findPendingProcesses();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).courtCode()).isEqualTo("STF");
    }

    @Test
    @DisplayName("deve retornar lista vazia quando nenhum processo estiver pendente")
    void shouldReturnEmptyWhenNoPendingProcesses() {
        when(processRepository.findAllPendingWithEffectiveInterval())
            .thenReturn(Collections.emptyList());

        assertThat(monitoringService.findPendingProcesses()).isEmpty();
    }
}
```

### 4.6 Teste do `ProcessNumberNormalizer`

```java
class ProcessNumberNormalizerTest {

    private final ProcessNumberNormalizer normalizer = new ProcessNumberNormalizer();

    @ParameterizedTest(name = "entrada: {0} → esperado: {1}")
    @CsvSource({
        "0001234-55.2020.8.26.0001, 0001234-55.2020.8.26.0001",  // já normalizado
        "00012345520208260001,       0001234-55.2020.8.26.0001",  // só dígitos
        "0001234.55.2020.8.26.0001, 0001234-55.2020.8.26.0001",  // ponto no hífen
        "0001234 55 2020 8 26 0001, 0001234-55.2020.8.26.0001"   // espaços
    })
    void shouldNormalizeVariousFormats(String input, String expected) {
        assertThat(normalizer.normalize(input)).isEqualTo(expected);
    }

    @Test
    @DisplayName("deve lançar exceção para número com menos de 20 dígitos")
    void shouldThrowForInvalidLength() {
        assertThatThrownBy(() -> normalizer.normalize("12345"))
            .isInstanceOf(InvalidProcessNumberException.class);
    }
}
```

### 4.7 Teste do `BlockDetector`

```java
class BlockDetectorTest {

    private final BlockDetector detector = new BlockDetector();

    @Test
    @DisplayName("deve lançar exceção para HTTP 403")
    void shouldThrowFor403() {
        RawResponse response = new RawResponse("<html>Forbidden</html>", 403,
            RawResponseType.HTML, CrawlerStrategy.HTTP);

        assertThatThrownBy(() -> detector.check(response))
            .isInstanceOf(CourtBlockedException.class)
            .hasMessageContaining("403");
    }

    @Test
    @DisplayName("deve lançar exceção quando HTML contém palavra captcha")
    void shouldThrowWhenCaptchaDetected() {
        RawResponse response = new RawResponse(
            "<html><body>Por favor resolva o CAPTCHA</body></html>",
            200, RawResponseType.HTML, CrawlerStrategy.HTTP);

        assertThatThrownBy(() -> detector.check(response))
            .isInstanceOf(CourtBlockedException.class);
    }

    @Test
    @DisplayName("não deve lançar exceção para resposta normal")
    void shouldNotThrowForNormalResponse() {
        RawResponse response = new RawResponse(
            "<html><body>Processo encontrado.</body></html>",
            200, RawResponseType.HTML, CrawlerStrategy.HTTP);

        assertThatNoException().isThrownBy(() -> detector.check(response));
    }
}
```

---

## 5. Testes de Integração

Testes de integração usam **Testcontainers** para subir PostgreSQL, Redis e RabbitMQ reais — sem mocks de infraestrutura.

### 5.1 Configuração base

```java
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
public abstract class BaseIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
        .withDatabaseName("consultorprocessos_test")
        .withUsername("test")
        .withPassword("test");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7")
        .withExposedPorts(6379);

    @Container
    static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3-management");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host",     redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.rabbitmq.host",       rabbitmq::getHost);
        registry.add("spring.rabbitmq.port", () -> rabbitmq.getMappedPort(5672));
    }
}
```

### 5.2 Testes de API (Controller → Service → Banco)

```java
@AutoConfigureMockMvc
class AuthControllerIT extends BaseIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;

    @Test
    @DisplayName("POST /auth/register deve criar usuário e retornar 201")
    void shouldRegisterUser() throws Exception {
        mockMvc.perform(post("/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name":     "João Silva",
                      "email":    "joao@test.com",
                      "password": "Senha123!"
                    }
                """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.email").value("joao@test.com"))
            .andExpect(jsonPath("$.data.status").value("PENDING_VERIFICATION"));

        assertThat(userRepository.findByEmail("joao@test.com")).isPresent();
    }

    @Test
    @DisplayName("POST /auth/register deve retornar 409 para e-mail duplicado")
    void shouldReturn409ForDuplicateEmail() throws Exception {
        createUser("joao@test.com"); // cria o usuário primeiro

        mockMvc.perform(post("/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name":     "Outro João",
                      "email":    "joao@test.com",
                      "password": "Senha123!"
                    }
                """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("POST /auth/login deve retornar tokens para credenciais válidas")
    void shouldLoginWithValidCredentials() throws Exception {
        createVerifiedUser("joao@test.com", "Senha123!");

        mockMvc.perform(post("/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "email": "joao@test.com", "password": "Senha123!" }
                """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
            .andExpect(jsonPath("$.data.expiresIn").value(900));
    }
}
```

### 5.3 Testes de Processo — Deduplicação

```java
class ProcessServiceIT extends BaseIntegrationTest {

    @Autowired private ProcessService    processService;
    @Autowired private ProcessRepository processRepository;
    @Autowired private SubscriptionRepository subscriptionRepository;

    @Test
    @DisplayName("dois usuários cadastrando o mesmo processo devem compartilhar um único registro")
    void shouldDeduplicateProcessForMultipleUsers() {
        User userA = createActiveUser("a@test.com");
        User userB = createActiveUser("b@test.com");

        SubscriptionResult resultA = processService.subscribe(userA,
            "0001234-55.2020.8.26.0001", "STF", null);
        SubscriptionResult resultB = processService.subscribe(userB,
            "0001234-55.2020.8.26.0001", "STF", null);

        // Mesmo processo — dois assinantes
        assertThat(resultA.processId()).isEqualTo(resultB.processId());
        assertThat(processRepository.count()).isEqualTo(1);
        assertThat(subscriptionRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("deve bloquear cadastro quando usuário atingir limite do plano")
    void shouldRejectWhenPlanLimitReached() {
        User user = createActiveUser("c@test.com"); // plano GRATUITO: 5 processos
        createSubscriptions(user, 5); // preenche o limite

        assertThatThrownBy(() ->
            processService.subscribe(user, "0009999-11.2025.8.26.0001", "STF", null)
        ).isInstanceOf(ProcessLimitReachedException.class);
    }
}
```

### 5.4 Testes de Scheduler + Fila (Integração Assíncrona)

```java
class SchedulerIT extends BaseIntegrationTest {

    @Autowired private SchedulerService      scheduler;
    @Autowired private ProcessRepository     processRepository;
    @Autowired private RabbitTemplate        rabbitTemplate;

    @Test
    @DisplayName("scheduler deve enfileirar processos pendentes no RabbitMQ")
    void shouldEnqueuePendingProcesses() {
        Process process = createProcessPendingConsultation();

        scheduler.execute();

        // Aguarda mensagem aparecer na fila (assíncrono)
        await().atMost(5, SECONDS).untilAsserted(() -> {
            Object message = rabbitTemplate.receiveAndConvert(
                RabbitConfig.QUEUE_CRAWL_REQUESTS);
            assertThat(message).isInstanceOf(CrawlRequestMessage.class);

            CrawlRequestMessage msg = (CrawlRequestMessage) message;
            assertThat(msg.processId()).isEqualTo(process.getId());
        });
    }

    @Test
    @DisplayName("scheduler não deve enfileirar o mesmo processo duas vezes")
    void shouldNotEnqueueSameProcessTwice() {
        Process process = createProcessPendingConsultation();

        scheduler.execute();
        scheduler.execute(); // segunda execução imediata

        await().atMost(5, SECONDS).untilAsserted(() -> {
            // Apenas uma mensagem deve estar na fila
            List<Object> messages = drainQueue(RabbitConfig.QUEUE_CRAWL_REQUESTS);
            assertThat(messages).hasSize(1);
        });
    }
}
```

### 5.5 Testes do Crawler com WireMock

Testes que verificam o comportamento dos Providers contra um tribunal simulado via HTTP.

```java
@SpringBootTest
@ActiveProfiles("test")
class STFProviderIT extends BaseIntegrationTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
        .options(wireMockConfig().dynamicPort())
        .build();

    @Autowired private STFProvider stfProvider;

    @BeforeEach
    void configureProvider() {
        // Aponta o provider para o WireMock em vez da URL real do STF
        stfProvider.setBaseUrl(wireMock.baseUrl());
    }

    @Test
    @DisplayName("deve retornar ProcessSnapshot para HTML válido do STF")
    void shouldReturnSnapshotForValidHtml() {
        wireMock.stubFor(get(urlPathMatching("/processos/detalhe.*"))
            .willReturn(ok()
                .withHeader("Content-Type", "text/html; charset=UTF-8")
                .withBodyFile("stf/v1.1.0_processo_normal.html")));

        ProcessSnapshot snapshot = stfProvider.consultar("0001234-55.2020.8.26.0001");

        assertThat(snapshot).isNotNull();
        assertThat(snapshot.courtCode()).isEqualTo("STF");
        assertThat(snapshot.movements()).isNotEmpty();
        assertThat(snapshot.contentHash()).hasSize(64); // SHA-256 hex
    }

    @Test
    @DisplayName("deve lançar CourtBlockedException para resposta 403")
    void shouldThrowBlockedExceptionFor403() {
        wireMock.stubFor(get(urlPathMatching("/processos/detalhe.*"))
            .willReturn(forbidden()));

        assertThatThrownBy(() ->
            stfProvider.consultar("0001234-55.2020.8.26.0001")
        ).isInstanceOf(CourtBlockedException.class);
    }

    @Test
    @DisplayName("deve executar fallback para Jsoup quando HTTP direto retornar 503")
    void shouldFallbackToJsoupWhen503() {
        // Primeira requisição retorna 503
        wireMock.stubFor(get(urlPathMatching("/processos/detalhe.*"))
            .inScenario("fallback")
            .whenScenarioStateIs(STARTED)
            .willReturn(serviceUnavailable())
            .willSetStateTo("jsoup-attempt"));

        // Segunda requisição (Jsoup) retorna HTML válido
        wireMock.stubFor(get(urlPathMatching("/processos/detalhe.*"))
            .inScenario("fallback")
            .whenScenarioStateIs("jsoup-attempt")
            .willReturn(ok().withBodyFile("stf/v1.1.0_processo_normal.html")));

        ProcessSnapshot snapshot = stfProvider.consultar("0001234-55.2020.8.26.0001");

        assertThat(snapshot).isNotNull();
        assertThat(snapshot.strategyUsed()).isEqualTo(CrawlerStrategy.JSOUP);
    }

    @Test
    @DisplayName("deve lançar CrawlException quando todas as estratégias falharem")
    void shouldThrowWhenAllStrategiesFail() {
        wireMock.stubFor(get(anyUrl())
            .willReturn(serverError()));

        assertThatThrownBy(() ->
            stfProvider.consultar("0001234-55.2020.8.26.0001")
        ).isInstanceOf(CrawlException.class)
         .hasMessageContaining("todas as estratégias falharam");
    }
}
```

---

## 6. Testes de Notificação

```java
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private EmailNotificationChannel emailChannel;
    @Mock private PushNotificationChannel  pushChannel;
    @Mock private SubscriptionRepository   subscriptionRepository;
    @Mock private NotificationHistoryRepository historyRepository;
    @Mock private NotificationRequestFactory factory;

    @InjectMocks private NotificationService notificationService;

    @Test
    @DisplayName("deve notificar apenas canais habilitados pelo usuário")
    void shouldOnlyNotifyEnabledChannels() {
        User user = buildUser(emailEnabled: true, pushEnabled: false);
        NotificationMessage event = buildMovementEvent();

        when(subscriptionRepository.findActiveByProcessId(any()))
            .thenReturn(List.of(buildSubscription(user)));
        when(factory.build(any(), any(), any()))
            .thenReturn(buildRequest(user));

        notificationService.handleMovimentacao(event);

        verify(emailChannel).send(any());
        verify(pushChannel, never()).send(any());
    }

    @Test
    @DisplayName("falha no e-mail não deve impedir envio do push")
    void shouldContinueWithPushWhenEmailFails() {
        User user = buildUser(emailEnabled: true, pushEnabled: true);

        when(subscriptionRepository.findActiveByProcessId(any()))
            .thenReturn(List.of(buildSubscription(user)));
        when(factory.build(any(), any(), any()))
            .thenReturn(buildRequest(user));
        doThrow(new NotificationException("SMTP timeout"))
            .when(emailChannel).send(any());

        assertThatNoException().isThrownBy(() ->
            notificationService.handleMovimentacao(buildMovementEvent())
        );

        verify(pushChannel).send(any()); // push continua mesmo com e-mail falhando
        verify(historyRepository, times(2)).save(any()); // EMAIL(FAILED) + PUSH(SENT)
    }
}
```

---

## 7. Testes de Segurança

```java
@AutoConfigureMockMvc
class SecurityIT extends BaseIntegrationTest {

    @Autowired private MockMvc mockMvc;

    @Test
    @DisplayName("endpoint admin deve retornar 403 para usuário comum")
    void shouldReturn403ForNonAdminUser() throws Exception {
        String userToken = loginAndGetToken("user@test.com", "Senha123!");

        mockMvc.perform(get("/v1/admin/dashboard")
                .header("Authorization", "Bearer " + userToken))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("endpoint protegido deve retornar 401 sem JWT")
    void shouldReturn401WithoutToken() throws Exception {
        mockMvc.perform(get("/v1/processes"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("JWT expirado deve retornar 401")
    void shouldReturn401ForExpiredToken() throws Exception {
        String expiredToken = generateExpiredToken();

        mockMvc.perform(get("/v1/processes")
                .header("Authorization", "Bearer " + expiredToken))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("usuário não deve acessar processo de outro usuário")
    void shouldReturn404WhenAccessingAnotherUsersProcess() throws Exception {
        User owner = createVerifiedUser("owner@test.com");
        User other = createVerifiedUser("other@test.com");

        UUID subscriptionId = createSubscription(owner).getId();
        String otherToken = loginAndGetToken("other@test.com", "Senha123!");

        mockMvc.perform(get("/v1/processes/" + subscriptionId)
                .header("Authorization", "Bearer " + otherToken))
            .andExpect(status().isNotFound()); // 404, não 403 (não revelar existência)
    }
}
```

---

## 8. Testes do Mock Tribunal (DEV)

Testes que verificam o próprio servidor Mock Tribunal, garantindo que os cenários controlados funcionam corretamente.

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class MockTribunalServerTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private MockTribunalController mockController;

    @Test
    @DisplayName("deve retornar HTML padrão para processo conhecido")
    void shouldReturnDefaultHtml() {
        ResponseEntity<String> response = restTemplate
            .getForEntity("/mock/stf/0001234-55.2020.8.26.0001", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("tabelaTodasMovimentacoes");
    }

    @Test
    @DisplayName("deve retornar mudança de movimentação após injeção via endpoint de controle")
    void shouldReturnChangedMovementAfterInjection() throws Exception {
        // Captura hash inicial
        String html1 = restTemplate
            .getForObject("/mock/stf/0001234-55.2020.8.26.0001", String.class);

        // Injeta mudança
        restTemplate.postForEntity("/control/inject-change", Map.of(
            "court",       "STF",
            "processNumber", "0001234-55.2020.8.26.0001",
            "description", "Nova movimentação de teste",
            "date",        "2025-03-16"
        ), Void.class);

        // Verifica que HTML mudou
        String html2 = restTemplate
            .getForObject("/mock/stf/0001234-55.2020.8.26.0001", String.class);

        assertThat(html1).isNotEqualTo(html2);
        assertThat(html2).contains("Nova movimentação de teste");
    }

    @Test
    @DisplayName("deve retornar 408 após injeção de timeout")
    void shouldReturnTimeoutAfterInjection() {
        restTemplate.postForEntity("/control/inject-timeout",
            Map.of("court", "STF", "count", 1), Void.class);

        ResponseEntity<String> response = restTemplate
            .getForEntity("/mock/stf/0001234-55.2020.8.26.0001", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.REQUEST_TIMEOUT);
    }
}
```

---

## 9. Testes de Carga (Básico)

Não fazem parte do CI padrão — executados manualmente antes de releases maiores.

### 9.1 Cenário: Scheduler com muitos processos

**Objetivo:** verificar que o scheduler consegue enfileirar 10.000 processos em menos de 60 segundos.

**Ferramenta:** JMH (Java Microbenchmark Harness) ou teste JUnit com medição manual.

```java
@Test
@Tag("load")
@DisplayName("scheduler deve enfileirar 10.000 processos em menos de 60 segundos")
void schedulerShouldHandle10kProcesses() {
    createProcesses(10_000); // insere no banco de teste

    long start = System.currentTimeMillis();
    scheduler.execute();
    long duration = System.currentTimeMillis() - start;

    assertThat(duration).isLessThan(60_000);
    assertThat(getQueueSize(QUEUE_CRAWL_REQUESTS)).isEqualTo(10_000);
}
```

### 9.2 Cenário: Workers paralelos sem duplicação

**Objetivo:** verificar que 10 workers consumindo a mesma fila não processam o mesmo processo duas vezes.

```java
@Test
@Tag("load")
@DisplayName("workers paralelos não devem processar o mesmo processo duas vezes")
void parallelWorkersShouldNotDuplicate() throws InterruptedException {
    Process process = createProcessPendingConsultation();
    publishToCrawlQueue(process); // publica 1 mensagem

    CountDownLatch latch = new CountDownLatch(1);
    AtomicInteger processedCount = new AtomicInteger(0);

    // Simula 10 workers concorrentes
    ExecutorService pool = Executors.newFixedThreadPool(10);
    for (int i = 0; i < 10; i++) {
        pool.submit(() -> {
            crawlerConsumer.consume(/* mensagem da fila */);
            processedCount.incrementAndGet();
            latch.countDown();
        });
    }

    latch.await(10, SECONDS);
    assertThat(processedCount.get()).isEqualTo(1); // apenas 1 worker processou
}
```

---

## 10. Organização e Convenções

### 10.1 Nomenclatura de testes

```
[deve] + [comportamento esperado] + [contexto quando relevante]

Exemplos:
✅ "deve retornar 201 ao cadastrar usuário com dados válidos"
✅ "deve lançar ProcessLimitReachedException quando limite do plano for atingido"
✅ "não deve publicar evento quando hash for idêntico"
❌ "testRegister"
❌ "testHashComparison"
```

### 10.2 Tags JUnit 5

```java
@Tag("unit")        // testes unitários (sem Spring, sem infra)
@Tag("integration") // testes com Testcontainers
@Tag("security")    // testes de autorização e autenticação
@Tag("load")        // testes de carga (excluídos do CI padrão)
@Tag("parser")      // testes de parser (subcategoria de unit)
```

CI executa: `unit` + `integration` + `security`
CI exclui: `load` (executado manualmente)

### 10.3 Builders de teste

Para evitar repetição na criação de objetos de teste, todos os módulos possuem um `TestBuilders`:

```java
public class TestBuilders {

    public static User buildUser(String email) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setName("Usuário Teste");
        user.setEmail(email);
        user.setPasswordHash("$2a$12$hash...");
        user.setStatus(UserStatus.ACTIVE);
        user.setPlan(buildGratuitoPlan());
        return user;
    }

    public static Process buildProcess(String lastHash) {
        Process process = new Process();
        process.setId(UUID.randomUUID());
        process.setProcessNumber("0001234-55.2020.8.26.0001");
        process.setStatus(ProcessStatus.OK);
        process.setLastSnapshotHash(lastHash);
        return process;
    }

    public static ProcessSnapshot buildSnapshot(String hash) {
        return new ProcessSnapshot(
            "0001234-55.2020.8.26.0001", "STF", hash,
            "{\"movements\":[{\"date\":\"2025-03-15\",\"desc\":\"Conclusos.\"}]}",
            List.of(new Movement(LocalDate.of(2025, 3, 15), "Conclusos.")),
            CrawlerStrategy.HTTP, "1.1.0", Instant.now()
        );
    }
}
```

### 10.4 `application-test.yml`

```yaml
spring:
  jpa:
    show-sql: false
    hibernate:
      ddl-auto: validate

app:
  scheduler:
    enabled: false        # desabilitado nos testes; ativado apenas quando necessário
  notifications:
    email:
      enabled: false      # sem envio real de e-mails nos testes
    push:
      enabled: false
  courts:
    real-requests-enabled: false  # NUNCA acessa tribunais reais nos testes

logging:
  level:
    com.consultorprocessos: WARN   # menos ruído nos logs de teste
    org.testcontainers:     WARN
```

### 10.5 Checklist antes de cada PR

- [ ] Novos parsers possuem testes baseados em fixtures HTML
- [ ] Novos endpoints possuem testes de integração cobrindo sucesso e erro principal
- [ ] Novas regras de negócio possuem testes unitários
- [ ] Nenhum teste depende de tribunal real, hora do sistema ou dados não controlados
- [ ] Cobertura do módulo afetado mantida ou melhorada
- [ ] Tags JUnit aplicadas corretamente
- [ ] CI passa localmente com `./mvnw test -P unit,integration`
