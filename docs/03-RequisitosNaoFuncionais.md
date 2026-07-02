# 03 — Requisitos Não Funcionais

**Consultor de Processos**
Software Design Document · v1.0

---

## 1. Performance

| ID | Requisito | Meta |
|----|-----------|------|
| RNF-PERF-001 | Tempo de resposta da API para endpoints de leitura | < 200ms (p95) |
| RNF-PERF-002 | Tempo de resposta da API para endpoints de escrita | < 500ms (p95) |
| RNF-PERF-003 | Tempo máximo de consulta a um tribunal (HTTP direto) | < 10 segundos |
| RNF-PERF-004 | Tempo máximo de consulta a um tribunal (Playwright) | < 30 segundos |
| RNF-PERF-005 | Latência entre detecção de movimentação e envio de notificação | < 5 minutos |
| RNF-PERF-006 | O scheduler deve ser capaz de enfileirar 10.000 processos em menos de 60 segundos | Meta de crescimento |
| RNF-PERF-007 | A comparação de hash entre snapshots deve ser O(1) independente do tamanho do processo | Obrigatório |

---

## 2. Escalabilidade

| ID | Requisito |
|----|-----------|
| RNF-ESC-001 | O número de workers RabbitMQ deve ser configurável e escalável horizontalmente sem mudança de código |
| RNF-ESC-002 | A camada de cache Redis deve ser o ponto de deduplicação, permitindo múltiplos workers sem duplicar consultas |
| RNF-ESC-003 | O scheduler deve garantir que o mesmo processo não seja enfileirado duas vezes em uma mesma janela (usar Redis para controle de lock) |
| RNF-ESC-004 | A arquitetura de Providers deve suportar dezenas de tribunais sem impacto no tempo de boot da aplicação |
| RNF-ESC-005 | Novos tribunais devem ser adicionáveis sem restart da aplicação (via feature flags e registro em banco) |

---

## 3. Disponibilidade

| ID | Requisito | Meta |
|----|-----------|------|
| RNF-DISP-001 | Disponibilidade da API | > 99,5% mensal |
| RNF-DISP-002 | O sistema deve tolerar indisponibilidade temporária de um tribunal sem afetar os demais | Obrigatório |
| RNF-DISP-003 | Falhas de consulta devem ser tratadas com retry; não devem gerar downtime | Obrigatório |
| RNF-DISP-004 | Mensagens na Dead Letter Queue devem ser reprocessáveis manualmente pelo administrador | Obrigatório |
| RNF-DISP-005 | O sistema não deve perder mensagens em fila em caso de restart do backend | Obrigatório (RabbitMQ durable) |

---

## 4. Segurança

| ID | Requisito |
|----|-----------|
| RNF-SEC-001 | Todas as senhas devem ser armazenadas com bcrypt (custo mínimo: 12) |
| RNF-SEC-002 | O JWT deve ser assinado com RS256 (chave assimétrica) |
| RNF-SEC-003 | Refresh Tokens devem ser armazenados com hash no banco, nunca em texto plano |
| RNF-SEC-004 | Toda comunicação externa deve usar HTTPS (TLS 1.2+) |
| RNF-SEC-005 | As credenciais de integração (e-mail SMTP, Stripe, etc.) devem ser armazenadas em variáveis de ambiente, nunca em código |
| RNF-SEC-006 | O painel administrativo deve ser protegido por role separada (`ROLE_ADMIN`) |
| RNF-SEC-007 | Endpoints de administração não devem ser acessíveis por usuários comuns mesmo que a URL seja conhecida |
| RNF-SEC-008 | Todos os inputs do usuário devem ser validados e sanitizados antes de processamento |
| RNF-SEC-009 | O sistema deve implementar proteção contra enumeração de usuários (erro genérico em login) |
| RNF-SEC-010 | Headers de segurança HTTP devem ser configurados (HSTS, X-Content-Type-Options, X-Frame-Options, CSP) |
| RNF-SEC-011 | Dados sensíveis (e-mail, CPF se aplicável) devem ser mascarados em logs |
| RNF-SEC-012 | O acesso ao banco de dados deve ser feito por usuário com permissões mínimas necessárias |
| RNF-SEC-013 | O ambiente DEV nunca deve se conectar a tribunais reais |

---

## 5. Confiabilidade

| ID | Requisito |
|----|-----------|
| RNF-REL-001 | Toda falha de crawling deve ser registrada em `CrawlerExecution` com stack trace, duração e estratégia utilizada |
| RNF-REL-002 | O sistema de retry deve usar backoff exponencial com jitter para evitar thundering herd |
| RNF-REL-003 | Após N falhas consecutivas configuráveis (padrão: 3), o processo entra em estado `ERROR` e o usuário é notificado |
| RNF-REL-004 | Mensagens não processáveis após N retries devem ser movidas para Dead Letter Queue sem perda |
| RNF-REL-005 | O banco de dados deve ter backup automático diário com retenção de 30 dias |
| RNF-REL-006 | O sistema deve detectar e alertar o administrador quando o health score de um tribunal cair abaixo de um threshold configurável (padrão: 70%) |

---

## 6. Manutenibilidade

| ID | Requisito |
|----|-----------|
| RNF-MAN-001 | Cada módulo deve ter responsabilidade única e fronteiras explícitas; nenhum módulo deve conhecer os internals de outro |
| RNF-MAN-002 | A adição de um novo tribunal deve requerer apenas a criação de novos arquivos (Provider, Crawler, Parser); nenhuma modificação em arquivos existentes deve ser necessária (Open/Closed Principle) |
| RNF-MAN-003 | A adição de um novo canal de notificação deve seguir o mesmo princípio: novo arquivo, zero modificações |
| RNF-MAN-004 | Toda lógica de negócio deve residir na camada de serviço, nunca em controllers ou repositórios |
| RNF-MAN-005 | Controllers devem ser responsáveis apenas por receber requisições, delegar e retornar respostas |
| RNF-MAN-006 | Nenhum `if` de desvio por tipo de tribunal deve existir fora dos próprios Providers |
| RNF-MAN-007 | Toda migração de banco de dados deve ser versionada com Flyway |
| RNF-MAN-008 | O versionamento de parsers deve permitir identificar a partir de qual versão um parser começou a falhar |

---

## 7. Testabilidade

| ID | Requisito | Meta |
|----|-----------|------|
| RNF-TEST-001 | Cobertura de testes unitários nos módulos core (crawler, parser, monitoring, scheduler) | > 80% |
| RNF-TEST-002 | Cobertura de testes de integração na API | > 70% nos endpoints P0 |
| RNF-TEST-003 | O Mock Tribunal deve permitir simular todos os cenários de erro e sucesso de forma determinística | Obrigatório |
| RNF-TEST-004 | Cada Parser deve ter testes baseados em fixtures de HTML real, versionados por data de captura | Obrigatório |
| RNF-TEST-005 | O ambiente de testes deve ser completamente isolado do ambiente de produção | Obrigatório |
| RNF-TEST-006 | Testes de integração devem usar banco e Redis em container (Testcontainers) | Preferido |

---

## 8. Observabilidade

| ID | Requisito |
|----|-----------|
| RNF-OBS-001 | Todos os logs devem ser estruturados em JSON com campos: `timestamp`, `level`, `module`, `traceId`, `message`, `context` |
| RNF-OBS-002 | Cada requisição HTTP deve gerar um `traceId` propagado por toda a cadeia de processamento |
| RNF-OBS-003 | O sistema deve expor métricas via Micrometer: número de consultas, taxa de sucesso/erro, tempo médio por tribunal, tamanho da fila |
| RNF-OBS-004 | Dados sensíveis (e-mail, número de processo) devem ser mascarados nos logs |
| RNF-OBS-005 | Cada execução do scheduler deve ser logada com: quantidade de processos encontrados, enfileirados, ignorados |
| RNF-OBS-006 | O health score de cada tribunal deve ser recalculado a cada execução e armazenado para histórico |
| RNF-OBS-007 | O administrador deve poder acessar logs filtráveis no painel admin sem necessidade de acesso ao servidor |

---

## 9. Portabilidade e Deploy

| ID | Requisito |
|----|-----------|
| RNF-DEP-001 | A aplicação deve ser completamente containerizada com Docker |
| RNF-DEP-002 | O ambiente local deve ser inicializável com um único comando (`docker compose up`) |
| RNF-DEP-003 | Variáveis de ambiente devem ser o único mecanismo de configuração entre ambientes (12-factor app) |
| RNF-DEP-004 | O sistema deve ter perfis Spring distintos: `dev`, `test`, `prod` |
| RNF-DEP-005 | Em produção, o perfil `dev` deve ser fisicamente impossível de ativar (validação no startup) |
| RNF-DEP-006 | Migrações de banco devem ser executadas automaticamente no startup via Flyway |

---

## 10. Custo Operacional

| ID | Requisito |
|----|-----------|
| RNF-CUSTO-001 | A deduplicação de processos deve reduzir o número de consultas a tribunais proporcionalmente ao número de usuários compartilhando o mesmo processo |
| RNF-CUSTO-002 | Playwright deve ser instanciado sob demanda e encerrado após uso; nunca manter instâncias ociosas abertas |
| RNF-CUSTO-003 | Selenium deve ser tratado como último recurso e seu uso deve ser monitorado e alertado |
| RNF-CUSTO-004 | O cache Redis deve ter TTL adequado para evitar consultas desnecessárias sem comprometer a frescura dos dados |
| RNF-CUSTO-005 | O sistema deve ter capacidade de operar em um único servidor VPS de porte pequeno na fase inicial |
