# 02 — Requisitos Funcionais

**Consultor de Processos**
Software Design Document · v1.0

---

> Convenção de identificação: `RF-[módulo]-[número]`
> Prioridades: **P0** = Bloqueante, **P1** = Alta, **P2** = Média, **P3** = Baixa / Futura

---

## Módulo: AUTH

| ID | Requisito | Prioridade |
|----|-----------|-----------|
| RF-AUTH-001 | O sistema deve permitir que o usuário crie uma conta com nome, e-mail e senha | P0 |
| RF-AUTH-002 | O sistema deve validar o formato do e-mail e rejeitar e-mails já cadastrados | P0 |
| RF-AUTH-003 | A senha deve ter no mínimo 8 caracteres e ser armazenada com hash bcrypt | P0 |
| RF-AUTH-004 | O sistema deve enviar um e-mail de verificação após o cadastro | P0 |
| RF-AUTH-005 | O usuário só pode fazer login com conta verificada | P0 |
| RF-AUTH-006 | O sistema deve autenticar o usuário via e-mail e senha, retornando JWT + Refresh Token | P0 |
| RF-AUTH-007 | O JWT deve ter validade de 15 minutos | P0 |
| RF-AUTH-008 | O Refresh Token deve ter validade de 7 dias e ser rotacionado a cada uso | P0 |
| RF-AUTH-009 | O sistema deve oferecer endpoint de recuperação de senha via e-mail | P0 |
| RF-AUTH-010 | O link de recuperação de senha deve expirar em 1 hora e ser de uso único | P0 |
| RF-AUTH-011 | O sistema deve permitir logout, invalidando o Refresh Token no servidor | P0 |
| RF-AUTH-012 | O sistema deve suportar login automático em ambiente DEV (bypass de autenticação) | P1 |
| RF-AUTH-013 | Após 5 tentativas de login com falha, a conta deve ser bloqueada temporariamente por 15 minutos | P1 |

---

## Módulo: USER

| ID | Requisito | Prioridade |
|----|-----------|-----------|
| RF-USER-001 | O usuário deve poder visualizar e editar seu perfil (nome, e-mail) | P0 |
| RF-USER-002 | O usuário deve poder alterar sua senha quando autenticado | P0 |
| RF-USER-003 | O sistema deve exibir o plano atual do usuário e seus limites | P0 |
| RF-USER-004 | O sistema deve exibir quantos processos o usuário já cadastrou e quantos ainda pode cadastrar | P0 |
| RF-USER-005 | O usuário deve poder configurar preferências de notificação (e-mail, push, ambos) | P1 |
| RF-USER-006 | O sistema deve registrar a data de criação da conta e último login | P1 |
| RF-USER-007 | O usuário deve poder deletar sua conta, removendo todos os dados pessoais | P2 |

---

## Módulo: PROCESS

| ID | Requisito | Prioridade |
|----|-----------|-----------|
| RF-PROC-001 | O usuário deve poder cadastrar um processo informando o número e o tribunal | P0 |
| RF-PROC-002 | O sistema deve normalizar o número do processo para o formato CNJ padrão antes de armazenar | P0 |
| RF-PROC-003 | O sistema deve validar se o número do processo possui formato válido (CNJ: NNNNNNN-DD.AAAA.J.TT.OOOO) | P0 |
| RF-PROC-004 | O sistema deve verificar se o tribunal informado existe e está ativo | P0 |
| RF-PROC-005 | Se o tribunal não existir ou estiver inativo, o sistema deve informar o usuário com previsão de implementação e notificar o administrador | P0 |
| RF-PROC-006 | O usuário não pode cadastrar mais processos do que o permitido pelo seu plano | P0 |
| RF-PROC-007 | O usuário deve poder listar todos os seus processos cadastrados com status e última movimentação | P0 |
| RF-PROC-008 | O usuário deve poder visualizar o histórico completo de movimentações de um processo | P0 |
| RF-PROC-009 | O usuário deve poder desativar o acompanhamento de um processo sem deletar o histórico | P0 |
| RF-PROC-010 | O usuário deve poder reativar o acompanhamento de um processo previamente desativado | P1 |
| RF-PROC-011 | O usuário deve poder deletar um processo, liberando a vaga no limite do plano | P1 |
| RF-PROC-012 | O sistema deve exibir o status da última consulta (sucesso, erro, pendente) para cada processo | P1 |
| RF-PROC-013 | Se dois usuários cadastrarem o mesmo processo no mesmo tribunal, o backend deve deduplá-los (consulta única, resultado compartilhado via cache) | P0 |

---

## Módulo: COURTS

| ID | Requisito | Prioridade |
|----|-----------|-----------|
| RF-COURT-001 | O sistema deve manter um cadastro de tribunais com nome, código, status e provider associado | P0 |
| RF-COURT-002 | O sistema deve retornar a lista de tribunais disponíveis para o usuário | P0 |
| RF-COURT-003 | O administrador deve poder ativar e desativar tribunais | P0 |
| RF-COURT-004 | Quando um usuário solicita um tribunal não disponível, o sistema deve registrar a solicitação (CourtRequest) | P0 |
| RF-COURT-005 | Quando uma solicitação de novo tribunal atingir um threshold configurável (padrão: 1), o administrador deve receber notificação por e-mail | P0 |
| RF-COURT-006 | O sistema deve exibir o health score de cada tribunal no painel administrativo | P1 |
| RF-COURT-007 | O sistema deve registrar o histórico de feature flags por tribunal | P1 |
| RF-COURT-008 | O administrador deve poder habilitar/desabilitar feature flags por tribunal sem deploy | P1 |

---

## Módulo: MONITORING

| ID | Requisito | Prioridade |
|----|-----------|-----------|
| RF-MON-001 | O sistema deve determinar, para cada processo ativo, se está na hora de realizar uma nova consulta com base no plano do assinante com maior prioridade | P0 |
| RF-MON-002 | Um processo com múltiplos assinantes deve ser consultado no intervalo do plano mais frequente entre eles | P0 |
| RF-MON-003 | Processos recentemente consultados (dentro da janela do plano) não devem ser enfileirados novamente | P0 |
| RF-MON-004 | Processos com status de erro devem entrar em política de retry com backoff exponencial | P0 |
| RF-MON-005 | Após N tentativas falhas consecutivas (padrão: 3), o processo deve ser marcado como `ERROR` e o usuário notificado | P1 |
| RF-MON-006 | O sistema deve priorizar na fila processos que estão há mais tempo sem consulta | P1 |

---

## Módulo: SCHEDULER

| ID | Requisito | Prioridade |
|----|-----------|-----------|
| RF-SCH-001 | O scheduler deve executar periodicamente (configurável, padrão: a cada 10 minutos) | P0 |
| RF-SCH-002 | A cada execução, o scheduler deve buscar todos os processos ativos que precisam ser consultados | P0 |
| RF-SCH-003 | O scheduler deve publicar uma mensagem na fila RabbitMQ para cada processo pendente | P0 |
| RF-SCH-004 | O scheduler não deve executar a consulta diretamente; deve apenas enfileirar | P0 |
| RF-SCH-005 | Mensagens que falham no processamento devem ser encaminhadas para uma Dead Letter Queue | P0 |
| RF-SCH-006 | O scheduler deve respeitar o rate limit configurado por tribunal | P1 |

---

## Módulo: CRAWLER

| ID | Requisito | Prioridade |
|----|-----------|-----------|
| RF-CRAWL-001 | Cada tribunal deve ter um `CourtProvider` que implementa uma interface única | P0 |
| RF-CRAWL-002 | O `CourtProvider` deve retornar apenas um `ProcessSnapshot`, nunca HTML, DOM ou objetos de biblioteca | P0 |
| RF-CRAWL-003 | Cada Provider pode ter múltiplos crawlers com ordem de fallback definida: HTTP → Jsoup → Playwright → Selenium | P0 |
| RF-CRAWL-004 | O crawler e o parser devem ser componentes separados | P0 |
| RF-CRAWL-005 | O parser deve ter um identificador de versão armazenado no banco | P0 |
| RF-CRAWL-006 | Cada `ProcessSnapshot` deve armazenar a versão do parser que o gerou | P1 |
| RF-CRAWL-007 | A detecção de mudança deve ser baseada em hash SHA-256 das movimentações, nunca comparação textual direta | P0 |
| RF-CRAWL-008 | O sistema deve implementar rate limiting por tribunal (requisições por minuto configuráveis) | P1 |
| RF-CRAWL-009 | O sistema deve adicionar intervalos aleatórios entre consultas para evitar bloqueios | P1 |
| RF-CRAWL-010 | O sistema deve suportar estratégias de fingerprint configuráveis por tribunal (user-agent, cookies, proxy) | P2 |
| RF-CRAWL-011 | O sistema deve registrar cada tentativa de crawling com duração, status e erro em `CrawlerExecution` | P1 |

---

## Módulo: NOTIFICATION

| ID | Requisito | Prioridade |
|----|-----------|-----------|
| RF-NOTIF-001 | O sistema deve notificar o usuário por e-mail ao detectar nova movimentação em seu processo | P0 |
| RF-NOTIF-002 | O sistema deve notificar o usuário via push notification ao detectar nova movimentação | P0 |
| RF-NOTIF-003 | O usuário deve poder configurar quais canais deseja receber notificações | P1 |
| RF-NOTIF-004 | O sistema deve registrar o histórico de notificações enviadas (canal, status, data) | P1 |
| RF-NOTIF-005 | O e-mail de notificação deve conter a descrição resumida da movimentação e link para o app | P0 |
| RF-NOTIF-006 | O sistema deve notificar o usuário quando o acompanhamento de um processo falhar repetidamente | P1 |
| RF-NOTIF-007 | O sistema deve suportar templates de e-mail configuráveis por tipo de evento | P2 |
| RF-NOTIF-008 | Webhook deve ser suportado como canal futuro sem alteração de arquitetura | P3 |
| RF-NOTIF-009 | SMS deve ser suportado como canal futuro sem alteração de arquitetura | P3 |

---

## Módulo: ADMIN

| ID | Requisito | Prioridade |
|----|-----------|-----------|
| RF-ADM-001 | O painel administrativo deve listar todos os tribunais com health score, status e feature flags | P0 |
| RF-ADM-002 | O administrador deve poder reprocessar uma consulta manualmente para um processo específico | P1 |
| RF-ADM-003 | O administrador deve poder visualizar e reprocessar mensagens na Dead Letter Queue | P1 |
| RF-ADM-004 | O painel deve exibir métricas: total de consultas, taxa de sucesso/erro, tempo médio de resposta por tribunal | P1 |
| RF-ADM-005 | O painel deve exibir as solicitações de novos tribunais agrupadas por tribunal e quantidade | P0 |
| RF-ADM-006 | O administrador deve poder cadastrar novos tribunais (nome, código, provider) | P0 |
| RF-ADM-007 | O painel deve exibir logs estruturados filtráveis por nível, módulo e intervalo de tempo | P1 |
| RF-ADM-008 | O administrador deve poder gerenciar versões de parsers por tribunal | P1 |
| RF-ADM-009 | O administrador deve poder ativar/desativar feature flags por tribunal em tempo real | P1 |
| RF-ADM-010 | O painel deve exibir a fila atual do RabbitMQ com quantidade de mensagens pendentes | P1 |

---

## Módulo: DEV / TEST (Ambiente controlado)

| ID | Requisito | Prioridade |
|----|-----------|-----------|
| RF-DEV-001 | Em modo DEV, o sistema deve bypassar autenticação com usuário e plano fixo pré-configurado | P0 |
| RF-DEV-002 | Em modo DEV, o banco deve ser populado com dados iniciais (processos, tribunais, histórico fake) | P0 |
| RF-DEV-003 | O sistema deve oferecer um Mock Tribunal acessível localmente (ex: `localhost:9000`) | P0 |
| RF-DEV-004 | O Mock Tribunal deve simular: retorno HTML normal, timeout, erro 500, CAPTCHA, bloqueio e mudança de movimentação | P0 |
| RF-DEV-005 | O Mock Tribunal deve simular cada um dos 3 tribunais iniciais (Eproc, STF, STJRJ) | P0 |
| RF-DEV-006 | O Mock Tribunal deve ter endpoints para controlar o comportamento dinamicamente (ex: forçar mudança de movimentação) | P1 |

---

## Módulo: PAYMENT (Fase Final)

| ID | Requisito | Prioridade |
|----|-----------|-----------|
| RF-PAY-001 | O sistema deve suportar assinaturas mensais e anuais | P2 |
| RF-PAY-002 | O sistema deve integrar com Stripe e/ou Mercado Pago | P2 |
| RF-PAY-003 | O upgrade/downgrade de plano deve ser refletido imediatamente nos limites do usuário | P2 |
| RF-PAY-004 | O cancelamento de assinatura deve manter o plano ativo até o fim do período pago | P2 |
| RF-PAY-005 | O sistema deve enviar e-mail de confirmação de pagamento e de falha na cobrança | P2 |
