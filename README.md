# Brandier API

API REST da **Brandier**, uma plataforma de gestão de promotores de marca: o gestor de uma marca cadastra lojas e produtos, convida promotores e acompanha as reposições que eles registram nos pontos de venda.

> Projeto de portfólio. A aplicação que está no ar **não é para uso comercial**. O código é livre: você pode clonar, estudar e fazer fork.

O front-end fica no repositório `brandier-web`.

## Tecnologias

| Área | O que é usado |
|---|---|
| Linguagem e framework | Java 17, Spring Boot 3.5 (Web, Security, Data JPA, Validation, Mail, Actuator) |
| Banco de dados | PostgreSQL, com migrations Flyway |
| Autenticação | JWT (jjwt), BCrypt, cookies `HttpOnly` |
| Documentação | springdoc-openapi (Swagger UI), com um grupo por versão da API |
| Build | Maven (wrapper incluído) |
| Testes | JUnit 5, Mockito e AssertJ |

## Estrutura do código

Pacote base: `com.leonardoprogrammer.brandier_api`.

| Pacote | Responsabilidade |
|---|---|
| `controller.v1` | Endpoints REST da versão 1. Finos: validam a entrada e delegam para os serviços. |
| `service` | Regras de negócio (cadastro, verificação de e-mail, redefinição de senha, tokens, limpeza periódica). |
| `repository` | Acesso a dados (Spring Data JPA). |
| `model.entity` / `model.dto` | Entidades JPA e objetos de requisição/resposta. As entidades nunca são expostas. |
| `security` | Configuração do Spring Security, filtro JWT, cookies de autenticação, tokens opacos. |
| `ratelimit` | Limite de tentativas por IP e por e-mail. |
| `email` | Bloqueio de e-mails descartáveis e canonicalização de endereços. |
| `event` | Eventos de aplicação e envio assíncrono de e-mails. |
| `exception` | Tratamento global de erros (RFC 7807). |
| `validation` | Validações customizadas (força e tamanho da senha). |
| `config` | Configurações de infraestrutura (versão da API, OpenAPI, relógio, agendamento). |

### Convenções

- **Conceitos do negócio em português** (`Usuario`, `Marca`, `nome`, `senha`); **peças técnicas em inglês** (`config`, `security`, `exception`, `RefreshToken`).
- A API é **versionada pela URL** (`/api/v1/...`). Uma `v2` só nasce com mudança incompatível.
- Erros seguem o formato `ProblemDetail`, com um campo `codigo` estável (por exemplo `NAO_AUTENTICADO`, `MUITAS_TENTATIVAS`).

## Decisões técnicas

### Sessão e segurança

- **Cookies `HttpOnly`**: o token de acesso (JWT de curta duração) e o *refresh token* nunca chegam ao JavaScript do navegador. O cookie de acesso usa `SameSite=Lax`; o de atualização, `SameSite=Strict` e restrito ao caminho de autenticação.
- **Refresh token opaco e rotativo**: guardado só como hash SHA-256, com **rotação** a cada uso. Reapresentar um token já usado revoga todas as sessões do usuário (detecção de reuso).
- **CSRF** pelo padrão *cookie para cabeçalho* (`XSRF-TOKEN` / `X-XSRF-TOKEN`), compatível com o Angular.
- **Senhas** com BCrypt, entre 8 e 72 caracteres (e no máximo 72 bytes, limite do BCrypt), com letras e números.
- **Sem enumeração de contas**: cadastro, reenvio de verificação e "esqueci minha senha" respondem sempre a mesma mensagem, exista ou não a conta.
- **Um único tipo de conta**: não há papel global. Gestor e promotor serão papéis relativos a cada marca (em desenvolvimento), de modo que a mesma pessoa possa gerir marcas e ser promotora em outras.

### Cadastro real, sem CPF

- A prova de identidade é a **posse do e-mail**: a conta nasce pendente e só entra depois de abrir um link de uso único (hash no banco, validade de 24 horas, intervalo mínimo entre reenvios).
- **E-mails descartáveis são bloqueados** com uma lista pública de domínios (`disposable-email-domains.txt`, licença CC0). Se o arquivo faltar, a API sobe normalmente e registra um erro no log, sem bloquear cadastros.
- **Aliases contam como o mesmo endereço**: no Gmail, pontos e `+tag` são ignorados (e `googlemail.com` vale `gmail.com`); `+tag` também é ignorado em Outlook, iCloud, Proton e similares. Isso evita criar várias contas com uma única caixa de e-mail.
- O aceite dos termos é registrado com data.

### Redefinição de senha

- Link de uso único, hash no banco, validade de 30 minutos e intervalo mínimo entre pedidos. Só contas ativas recebem o link.
- Trocar a senha **encerra todas as sessões** da conta.

### Proteção contra abuso

- **Limite de tentativas** em memória, por janela fixa, por IP e por e-mail (login, cadastro, reenvio, redefinição). Ao exceder, responde `429` com o cabeçalho `Retry-After`. Os limites são configuráveis.
- **Limpeza periódica** (`@Scheduled`): remove contas pendentes antigas (7 dias, configurável) e tokens expirados.
- Tokens de uso único são consumidos com `UPDATE` condicional, o que evita uso duplo em requisições concorrentes.

### E-mails

- Enviados **depois do commit da transação** e de forma assíncrona (eventos de aplicação), para que uma falha de SMTP nunca desfaça o cadastro nem atrase a resposta.

## Endpoints (v1)

Todos sob `/api/v1`. As requisições `POST` exigem o cabeçalho `X-XSRF-TOKEN` (valor do cookie `XSRF-TOKEN`).

| Método | Caminho | Descrição | Acesso |
|---|---|---|---|
| `POST` | `/usuario` | Cria a conta e envia o link de verificação | Público |
| `POST` | `/usuario/verificar-email` | Confirma o e-mail com o token do link | Público |
| `POST` | `/usuario/reenviar-verificacao` | Reenvia o link de verificação | Público |
| `POST` | `/usuario/login` | Autentica e define os cookies de sessão | Público |
| `POST` | `/usuario/refresh-token` | Renova a sessão (rotação do refresh token) | Cookie |
| `POST` | `/usuario/logout` | Encerra a sessão | Público |
| `GET` | `/usuario/eu` | Dados do usuário autenticado | Autenticado |
| `POST` | `/usuario/esqueci-senha` | Pede o link de redefinição de senha | Público |
| `POST` | `/usuario/redefinir-senha` | Define a nova senha com o token do link | Público |
| `GET` | `/marcas` | Lista as marcas do usuário e o limite por conta | Autenticado |
| `GET` | `/marcas/{id}` | Detalha uma marca do usuário | Autenticado |
| `POST` | `/marcas` | Cria uma marca (`201` com `Location`) | Autenticado |
| `PUT` | `/marcas/{id}` | Atualiza nome e descrição | Autenticado |
| `DELETE` | `/marcas/{id}` | Exclui a marca (`204`) | Autenticado |

Marca de outro usuário responde `404`. O limite de marcas por conta (`brandier.limits.max-brands-per-manager`, padrão 2) responde `409` com o código `LIMITE_MARCAS_ATINGIDO`, e o nome repetido na mesma conta, `409` com `MARCA_NOME_DUPLICADO`. A contagem é feita com **lock pessimista** na linha do usuário, então requisições paralelas não estouram o limite.

A documentação interativa fica em `/api/swagger-ui.html` (e o JSON da versão em `/api/v3/api-docs/v1`).

## Como executar

Requisitos: **JDK 17+** e **PostgreSQL**.

1. Crie um banco de dados e um usuário para a aplicação.
2. Copie `src/main/resources/application-example.properties` para `src/main/resources/application.properties` e preencha os valores. O arquivo real **não é versionado** (veja a seção de configuração).
3. Inicie a aplicação:

```bash
./mvnw spring-boot:run
```

No Windows, use `mvnw.cmd spring-boot:run`. A API sobe em `http://localhost:8080/api` e o *health check*, em `http://localhost:9090/actuator/health`.

Para rodar o front-end junto, veja o README do `brandier-web` (o `ng serve` encaminha `/api` para a porta 8080).

### Banco de dados

O esquema é criado e atualizado por **migrations Flyway** (`src/main/resources/db/migration`), que **não são versionadas neste repositório**. Sem elas, a API não sobe em um banco vazio.

### Configuração

Segredos e dados de ambiente **não ficam no Git**: `application.properties`, `application-*.properties` e `.env` estão no `.gitignore`; só o `application-example.properties` é versionado, com valores fictícios. Em produção, variáveis de ambiente têm precedência sobre o arquivo (por exemplo `SPRING_DATASOURCE_URL`).

| Grupo | Chaves |
|---|---|
| Banco | `spring.datasource.*`, `spring.jpa.*` |
| JWT e cookies | `jwt.secret`, `jwt.expiration`, `jwt.refresh-expiration`, `brandier.cookie.secure` |
| E-mail | `brandier.mail.*`, `spring.mail.*`, `brandier.base-url` |
| Cadastro | `brandier.verification.*`, `brandier.password-reset.*`, `brandier.email.block-disposable` |
| Limite de tentativas | `brandier.rate-limit.*` |
| Limpeza periódica | `brandier.cleanup.*` |
| Limites de uso | `brandier.limits.*` |

#### E-mail em desenvolvimento

Os e-mails (verificação de conta e redefinição de senha) são enviados por **SMTP**, inclusive em desenvolvimento. Com uma conta do Gmail:

1. Ative a verificação em duas etapas na conta Google.
2. Crie uma **senha de app** (Conta Google, Segurança, Senhas de app).
3. No `application.properties`, defina `brandier.mail.enabled=true`, `spring.mail.host=smtp.gmail.com`, `spring.mail.port=587`, `spring.mail.username` (o e-mail da conta), `spring.mail.password` (a senha de app) e `brandier.mail.from` com o mesmo endereço (o Gmail só envia como a conta autenticada).

Qualquer outro provedor SMTP serve; basta trocar host, porta e credenciais. Os tempos de conexão e de resposta do SMTP são limitados a 5 segundos.

Se preferir não enviar e-mails de verdade, `brandier.mail.enabled=false` faz a API **escrever o e-mail no log** (com o link) em vez de enviá-lo. Use apenas em desenvolvimento: o log passa a conter os tokens.

## Testes

Testes unitários com JUnit 5, Mockito e AssertJ, sem depender de banco, de rede ou de Docker:

```bash
./mvnw test
```

Testes de integração (com Testcontainers) serão adicionados quando o projeto passar a usar Docker.
