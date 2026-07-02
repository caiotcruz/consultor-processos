# 01 — Visão Geral

**Consultor de Processos**
Software Design Document · v1.0
Última atualização: 2025

---

## 1. Objetivo

O **Consultor de Processos** é uma plataforma (Web + Mobile) que monitora automaticamente processos judiciais em tribunais brasileiros e notifica o usuário sempre que houver novas movimentações.

O usuário registra o número do processo, seleciona o tribunal e, a partir desse momento, o sistema assume toda a responsabilidade de monitoramento periódico. Nenhuma ação adicional é necessária por parte do usuário.

---

## 2. Escopo

### 2.1 Está no escopo

- Autenticação completa (registro, login, recuperação de senha, refresh token)
- Cadastro e gerenciamento de processos pelo usuário
- Monitoramento periódico de processos em tribunais brasileiros
- Notificação por e-mail e push ao detectar movimentações
- Sistema de planos com limites por quantidade de processos e intervalo de consulta
- Painel administrativo para gerenciamento de tribunais, erros, filas e métricas
- Ambiente de desenvolvimento controlado com tribunais simulados (Mock Tribunal)
- Solicitação de novos tribunais pelo usuário com notificação automática ao administrador
- Aplicativo mobile via Ionic
- Pagamento por assinatura (última fase de implementação)

### 2.2 Fora do escopo (versão inicial)

- Acompanhamento de processos administrativos (não judiciais)
- OCR em documentos anexados a processos
- Exportação de histórico em PDF
- Integrações com advogados ou escritórios de advocacia
- Chat ou suporte integrado ao app
- SMS como canal de notificação (previsto para fases futuras)

---

## 3. Público-Alvo

| Perfil | Descrição |
|--------|-----------|
| Pessoa física | Acompanha seu próprio processo judicial (trabalhista, cível, criminal, etc.) |
| Advogado autônomo | Monitora processos de múltiplos clientes |
| Pequeno escritório | Equipe que precisa de monitoramento centralizado |
| Estudante de direito | Acompanha processos de interesse acadêmico |

---

## 4. Tecnologias

### 4.1 Backend

| Tecnologia | Versão | Função |
|------------|--------|--------|
| Java | 21 (LTS) | Linguagem principal |
| Spring Boot | 3.x | Framework principal |
| Spring Security | 6.x | Autenticação e autorização |
| Spring Data JPA | 3.x | Persistência |
| PostgreSQL | 16 | Banco de dados principal |
| Redis | 7 | Cache e controle de concorrência |
| RabbitMQ | 3.x | Fila de mensagens |
| Jsoup | 1.17+ | Parser HTML leve |
| Playwright (Java) | 1.x | Automação de browser (fallback primário) |
| Selenium | 4.x | Automação de browser (último recurso) |
| Flyway | 9.x | Migrações de banco de dados |
| Micrometer + Prometheus | — | Métricas e observabilidade |

### 4.2 Frontend / Mobile

| Tecnologia | Versão | Função |
|------------|--------|--------|
| Angular | 17+ | Aplicação web |
| Ionic | 7+ | Aplicação mobile (iOS + Android) |
| Capacitor | 5+ | Bridge nativo para mobile |

### 4.3 Infraestrutura

| Tecnologia | Função |
|------------|--------|
| Docker | Containerização |
| Docker Compose | Orquestração local e staging |
| Nginx | Proxy reverso e SSL termination |

---

## 5. Arquitetura Geral

O backend adota o padrão **Monólito Modular**: um único artefato deployável, organizado internamente em módulos bem definidos com fronteiras explícitas e sem acoplamento entre eles.

Essa abordagem foi escolhida porque:

- Elimina a complexidade operacional de microserviços desnecessária para o porte atual
- Permite isolar e substituir módulos sem afetar o sistema como um todo
- Facilita testes unitários e de integração
- Mantém custo operacional baixo (um único container backend)
- Viabiliza migração futura para microserviços se necessário

### 5.1 Módulos principais

```
API
│
├── auth          → Login, cadastro, JWT, refresh token, recuperação de senha
├── user          → Perfil, plano, limites, preferências
├── process       → Cadastro e gerenciamento de processos pelo usuário
├── court         → Cadastro de tribunais e seus Providers
├── subscription  → Vínculo entre usuário e processo (deduplicação)
├── monitoring    → Decisão: "esse processo precisa ser consultado agora?"
├── scheduler     → Execução periódica e enfileiramento de consultas
├── crawler       → Providers, Crawlers, Parsers, Snapshots
├── notification  → E-mail, Push, futuramente Webhook/SMS
├── admin         → Painel administrativo, logs, métricas, filas
├── payment       → Assinaturas, planos, Stripe / Mercado Pago (fase final)
└── shared        → DTOs comuns, utilitários, exceções, enums
```

---

## 6. Fluxo Principal

```
Usuário
  │
  ├─► Registra conta
  │
  ├─► Faz login
  │
  ├─► Informa número do processo + tribunal
  │
  │     [Backend verifica se tribunal existe e está ativo]
  │       ├─ SIM → Cria Process + Subscription → Responde 201
  │       └─ NÃO → Cria CourtRequest → Notifica admin por e-mail
  │                → Responde ao usuário com previsão de 1 semana
  │
  └─► Aguarda notificações
        │
        [Scheduler executa a cada N minutos]
          │
          ├─► Seleciona processos pendentes de consulta
          │
          ├─► Publica mensagens na fila (RabbitMQ)
          │
          └─► Worker consome mensagem
                │
                ├─► Provider seleciona a estratégia de consulta
                │
                ├─► Crawler obtém HTML / JSON do tribunal
                │
                ├─► Parser extrai movimentações
                │
                ├─► Normalizer padroniza os dados
                │
                ├─► Comparator gera hash e compara com snapshot anterior
                │
                │     [Hash mudou?]
                │       ├─ SIM → Salva novo ProcessSnapshot
                │       │        → Registra movimentações no histórico
                │       │        → Dispara evento de notificação
                │       │        → Todos os assinantes recebem alerta
                │       └─ NÃO → Atualiza apenas ultimaConsulta
                │
                └─► Atualiza health score do tribunal
```

---

## 7. Modelo de Planos

| Plano | Processos | Intervalo de Consulta | Preço |
|-------|-----------|----------------------|-------|
| Gratuito | 5 | 12 horas | R$ 0,00 |
| Básico | 10 | 8 horas | A definir |
| Avançado | Ilimitado | 4 horas | A definir |

> Os limites de plano são armazenados no banco de dados e nunca hardcoded no código. A adição de novos planos não requer alteração de código.

---

## 8. Tribunais Iniciais

| Código | Nome | Status |
|--------|------|--------|
| `EPROC` | eProc (TRF) | Fase 1 |
| `STF` | Supremo Tribunal Federal | Fase 1 |
| `STJRJ` | Superior Tribunal de Justiça RJ | Fase 1 |

> Novos tribunais são adicionados implementando apenas um novo `CourtProvider`. Nenhuma outra parte do sistema é alterada.

---

## 9. Premissas e Restrições

| # | Premissa / Restrição |
|---|---------------------|
| P1 | Tribunais brasileiros podem alterar seu layout HTML sem aviso prévio |
| P2 | Alguns tribunais implementam bloqueios por acesso repetitivo (rate limiting, CAPTCHA) |
| P3 | O custo de operação deve ser mantido baixo durante a fase inicial |
| P4 | O sistema deve ser extensível para dezenas de tribunais sem mudança arquitetural |
| P5 | Selenium deve ser evitado ao máximo por ser lento e pesado; Playwright é o fallback preferido |
| P6 | Dois usuários acompanhando o mesmo processo no mesmo tribunal geram apenas uma consulta ao tribunal |
| P7 | Pagamento será a última funcionalidade implementada |
| P8 | O ambiente de desenvolvimento deve permitir testes completos sem depender de tribunais reais |

---

## 10. Critérios de Sucesso

- Sistema capaz de monitorar 3 tribunais em produção com taxa de sucesso > 95%
- Latência de notificação inferior a 5 minutos após detecção de movimentação
- Arquitetura que permite adicionar um novo tribunal em menos de 4 horas de trabalho
- Cobertura de testes > 80% nos módulos core (crawler, parser, monitoring, scheduler)
- Zero downtime em deploys (rolling update)
