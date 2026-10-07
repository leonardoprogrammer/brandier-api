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
- **Editar nome e trocar a senha pelo painel**: `PUT /usuario` (só o nome) e `POST /usuario/alterar-senha` (senha atual + nova, mesma política de senha do cadastro; a senha atual só é exigida se a conta já tiver uma — ver "Login com Google" abaixo). Trocar a senha revoga os refresh tokens e encerra a sessão (os cookies são limpos, como na redefinição por e-mail); editar o nome não afeta a sessão. As duas rotas usam o mesmo limitador de tentativas por usuário da exclusão de conta.
- **Sem enumeração de contas**: cadastro, reenvio de verificação e "esqueci minha senha" respondem sempre a mesma mensagem, exista ou não a conta.
- **Um único tipo de conta**: não há papel global. Gestor e promotor são papéis relativos a cada marca, de modo que a mesma pessoa pode gerir marcas e ser promotora em outras.

### Cadastro real, sem CPF

- A prova de identidade é a **posse do e-mail**: a conta nasce pendente e só entra depois de abrir um link de uso único (hash no banco, validade de 24 horas, intervalo mínimo entre reenvios).
- **E-mails descartáveis são bloqueados** com uma lista pública de domínios (`disposable-email-domains.txt`, licença CC0). Se o arquivo faltar, a API sobe normalmente e registra um erro no log, sem bloquear cadastros.
- **Aliases contam como o mesmo endereço**: no Gmail, pontos e `+tag` são ignorados (e `googlemail.com` vale `gmail.com`); `+tag` também é ignorado em Outlook, iCloud, Proton e similares. Isso evita criar várias contas com uma única caixa de e-mail.
- O aceite dos termos é registrado com data.

### Login com Google e contas sem senha

- Além de e-mail/senha, a conta pode entrar com o **Google** (Google Identity Services, verificação de ID token — sem OAuth2 Authorization Code e sem *client secret*). O botão aparece no front só quando `brandier.google.client-id` está preenchido.
- `POST /usuario/entrar-google` verifica o token recebido do Google (assinatura, emissor, audiência e expiração) e exige e-mail verificado pelo Google; se a conta não existir, é criada na hora, já **ATIVA** (sem precisar verificar e-mail de novo, já que o Google verificou).
- Uma conta criada assim nasce **sem senha** (`senha_definida=false`): tentar entrar com e-mail/senha nessa conta responde `403 SENHA_NAO_DEFINIDA`, sem sequer tentar autenticar. Definir uma senha pela primeira vez usa a mesma rota `POST /usuario/alterar-senha`, só que sem exigir a senha atual. Excluir uma conta assim (`POST /usuario/excluir-conta`) também dispensa a senha, bastando a confirmação.
- Como a conta criada pelo Google ainda não passou pela tela de cadastro, ela nasce **sem aceitar os termos**. `POST /usuario/aceitar-termos` (autenticado, idempotente) registra o aceite; até lá, o front bloqueia o acesso ao painel e mostra uma tela só com o aceite dos termos e um botão para sair.

### Redefinição de senha

- Link de uso único, hash no banco, validade de 30 minutos e intervalo mínimo entre pedidos. Só contas ativas recebem o link.
- Trocar a senha **encerra todas as sessões** da conta.

### Proteção contra abuso

- **Cloudflare Turnstile** no cadastro (`brandier.turnstile.*`): o front envia o token do widget em `turnstileToken` e a API o confirma no Cloudflare antes de criar a conta. Desligado por padrão (`brandier.turnstile.enabled=false`); ao ligar, `brandier.turnstile.secret-key` é obrigatória e a API não sobe sem ela. Se o Cloudflare estiver fora do ar ou responder erro, o cadastro é recusado (`CAPTCHA_INVALIDO`), nunca liberado. Para testar localmente, o Cloudflare publica chaves de teste (as de "sempre passa" e "sempre falha").
- **Teto global de contas** (`brandier.limits.max-accounts`, padrão 500): ao atingir o limite, todo cadastro responde `409 LIMITE_CONTAS_ATINGIDO`, inclusive o de e-mails que já têm conta, para não revelar quem está cadastrado. A contagem inclui contas pendentes (que a limpeza periódica apaga em 7 dias) e pode passar do teto por alguns cadastros simultâneos.
- **Exclusão da própria conta** (`POST /usuario/excluir-conta`, com a senha no corpo): não apaga a conta de verdade, para não quebrar relatórios de terceiros — apaga os convites pendentes enviados ao e-mail dela e os seus vínculos de promotor em marcas de outros gestores, e **anonimiza** a conta (e-mail e senha trocados por valores que não batem mais com nada; o e-mail original fica livre para um novo cadastro; o nome é mantido, para que reposições já registradas continuem identificando o autor). As marcas que ela gerencia são apagadas (com lojas, produtos, promotores e convites) quando nunca tiveram reposições, ou ficam **inativas** (sem novos convites, produtos ou reposições) quando já têm histórico, preservando os dados de quem promoveu nelas. Limita as tentativas por usuário e encerra a sessão (limpa os cookies).

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
| `POST` | `/usuario/entrar-google` | Entra ou cria a conta com o Google (ID token) | Público |
| `POST` | `/usuario/refresh-token` | Renova a sessão (rotação do refresh token) | Cookie |
| `POST` | `/usuario/logout` | Encerra a sessão | Público |
| `GET` | `/usuario/eu` | Dados do usuário autenticado | Autenticado |
| `POST` | `/usuario/esqueci-senha` | Pede o link de redefinição de senha | Público |
| `POST` | `/usuario/redefinir-senha` | Define a nova senha com o token do link | Público |
| `POST` | `/usuario/excluir-conta` | Exclui a própria conta (senha só se a conta tiver uma) | Autenticado |
| `PUT` | `/usuario` | Atualiza o próprio nome | Autenticado |
| `POST` | `/usuario/alterar-senha` | Troca ou define a senha (senha atual só se já existir uma) | Autenticado |
| `POST` | `/usuario/aceitar-termos` | Aceita os termos de uso (contas criadas pelo Google) | Autenticado |
| `GET` | `/marcas` | Lista as marcas do usuário e o limite por conta | Autenticado |
| `GET` | `/marcas/{id}` | Detalha uma marca do usuário | Autenticado |
| `POST` | `/marcas` | Cria uma marca (`201` com `Location`) | Autenticado |
| `PUT` | `/marcas/{id}` | Atualiza nome e descrição | Autenticado |
| `DELETE` | `/marcas/{id}` | Exclui a marca (`204`) | Autenticado |
| `GET` | `/marcas/{marcaId}/lojas` | Lista as lojas da marca e o limite por marca | Gestor ou promotor da marca |
| `GET` | `/marcas/{marcaId}/lojas/{id}` | Detalha uma loja | Gestor da marca |
| `POST` | `/marcas/{marcaId}/lojas` | Cria uma loja (`201` com `Location`) | Gestor da marca |
| `PUT` | `/marcas/{marcaId}/lojas/{id}` | Atualiza nome, cidade, UF e endereço | Gestor da marca |
| `DELETE` | `/marcas/{marcaId}/lojas/{id}` | Exclui a loja (`204`) | Gestor da marca |
| `GET` | `/marcas/{marcaId}/produtos` | Lista o catálogo da marca (promotores veem só os ativos) | Gestor ou promotor da marca |
| `GET` | `/marcas/{marcaId}/produtos/{id}` | Detalha um produto | Gestor da marca |
| `POST` | `/marcas/{marcaId}/produtos` | Cria um produto (`201` com `Location`) | Gestor da marca |
| `PUT` | `/marcas/{marcaId}/produtos/{id}` | Atualiza nome, código, cor e, opcionalmente, `ativo` (reativa) | Gestor da marca |
| `DELETE` | `/marcas/{marcaId}/produtos/{id}` | Exclui o produto (`204`) ou, se já tem reposições, inativa (`200`) | Gestor da marca |
| `GET` | `/marcas/{marcaId}/promotores` | Promotores, convites e uso do limite | Gestor da marca |
| `DELETE` | `/marcas/{marcaId}/promotores/{id}` | Remove o promotor da marca (`204`) | Gestor da marca |
| `POST` | `/marcas/{marcaId}/convites` | Convida um promotor por e-mail (`201`) | Gestor da marca |
| `DELETE` | `/marcas/{marcaId}/convites/{id}` | Cancela ou remove um convite (`204`) | Gestor da marca |
| `POST` | `/convites/consultar` | Mostra o convite do token (no corpo) | Autenticado |
| `POST` | `/convites/aceitar` | Aceita o convite e vira promotor | Autenticado |
| `GET` | `/convites/recebidos` | Convites pendentes endereçados ao e-mail do usuário | Autenticado |
| `POST` | `/convites/{id}/aceitar` | Aceita um convite recebido, pelo id | Autenticado |
| `POST` | `/convites/{id}/recusar` | Recusa um convite recebido, pelo id (`204`) | Autenticado |
| `POST` | `/convites/recusar` | Recusa o convite (`204`) | Autenticado |
| `GET` | `/promotores/eu` | Marcas em que o usuário é promotor | Autenticado |
| `DELETE` | `/promotores/eu/{marcaId}` | O usuário sai da marca (`204`) | Promotor da marca |
| `GET` | `/marcas/{marcaId}/reposicoes` | Reposições da marca, paginadas (promotor vê só as próprias) | Gestor ou promotor da marca |
| `GET` | `/marcas/{marcaId}/reposicoes/{id}` | Detalha uma reposição | Gestor ou autor |
| `POST` | `/marcas/{marcaId}/reposicoes` | Registra uma reposição (`201`) | Gestor ou promotor da marca |
| `PUT` | `/marcas/{marcaId}/reposicoes/{id}` | Altera uma reposição | Gestor, ou autor dentro do prazo |
| `DELETE` | `/marcas/{marcaId}/reposicoes/{id}` | Exclui uma reposição (`204`) | Gestor, ou autor dentro do prazo |
| `GET` | `/reposicoes` | Reposições do usuário em todas as marcas, paginadas | Autenticado |
| `GET` | `/painel/gestor` | Painel das marcas do usuário (totais do período e uso dos limites) | Autenticado |
| `GET` | `/marcas/{marcaId}/painel` | Painel detalhado da marca: por loja, promotor e produto | Gestor da marca |
| `GET` | `/painel/minhas-reposicoes` | Painel das reposições do usuário (por marca, loja e produto) | Autenticado |
| `GET` | `/marcas/{marcaId}/relatorios/reposicoes` | Relatório paginado com filtros e ordenação | Gestor ou promotor da marca |
| `GET` | `/marcas/{marcaId}/relatorios/reposicoes/csv` | Exporta o relatório em CSV | Gestor ou promotor da marca |

Marca de outro usuário responde `404`. O limite de marcas por conta (`brandier.limits.max-brands-per-manager`, padrão 2) responde `409` com o código `LIMITE_MARCAS_ATINGIDO`, e o nome repetido na mesma conta, `409` com `MARCA_NOME_DUPLICADO`. A contagem é feita com **lock pessimista** na linha do usuário, então requisições paralelas não estouram o limite.

Lojas e produtos seguem a mesma regra, com o lock na linha da **marca**: até 5 lojas (`brandier.limits.max-stores-per-brand`, `LIMITE_LOJAS_ATINGIDO`) e 10 produtos (`brandier.limits.max-products-per-brand`, `LIMITE_PRODUTOS_ATINGIDO`) por marca, com nome único dentro da marca (`LOJA_NOME_DUPLICADO`, `PRODUTO_NOME_DUPLICADO`). Escrever (criar, alterar, excluir) é só do gestor da marca; ler a lista de lojas e de produtos também é permitido aos promotores. Quem não tem vínculo com a marca recebe `404`, nunca `403`.

**Promotores e convites.** O gestor convida por e-mail; o convite tem token de uso único (guardado só como hash), vale 7 dias (`brandier.invitation.expiration-days`) e só pode ser aceito pela conta com o mesmo e-mail (aliases do Gmail e similares contam como o mesmo). A resposta é a mesma exista ou não conta para o e-mail. O limite de 5 por marca (`brandier.limits.max-promoters-per-brand`) conta promotores **e** convites pendentes, também sob lock da marca. Remover um promotor não apaga as reposições dele.

O convidado também vê os convites pendentes dentro da plataforma (`GET /convites/recebidos`) e responde pelo id, com as mesmas regras do link do e-mail: só a conta com o e-mail convidado enxerga e responde, e qualquer outro id responde `404`.

**Produtos.** `cor` é opcional, no formato hexadecimal `#RRGGBB` (ex.: `#4F46E5`); guardada em minúsculas, usada pelo front para destacar o produto no formulário de reposição.

**Reposições.** Uma reposição tem loja, data e hora, itens (produto + quantidade inteira maior que zero, sem repetir produto) e observações. O gestor altera e exclui qualquer reposição da marca; o promotor, só as próprias e até 24 horas depois do registro (`brandier.replenishment.edit-window-hours`), caso contrário `403 JANELA_EDICAO_ENCERRADA`. Produto com reposições não é excluído: fica inativo e deixa de contar no limite. Loja com reposições não pode ser excluída (`409 LOJA_COM_REPOSICOES`). Marca com reposições também não é excluída (nem ao excluir a conta do gestor): fica **inativa**, sem aceitar novas reposições (`409 MARCA_INATIVA`), mas continua visível nos relatórios de quem já promoveu nela.

**Painel e relatórios.** Os totais e rankings são calculados no banco (`GROUP BY`), nunca em memória. O período é `de` (inclusive) e `ate` (exclusivo), com padrão de 30 dias e máximo de 366. O relatório combina filtros (loja, promotor, produto, período), pagina no servidor e ordena por data, loja ou autor; o promotor só enxerga as próprias reposições. O CSV usa UTF-8 com BOM, separador `;` e uma linha por item, neutraliza células que começam com `=`, `+`, `-` ou `@` (injeção de fórmula) e recusa exportar mais de 5.000 reposições de uma vez.

Como `spring.jpa.open-in-view` está desligado, os serviços que devolvem dados com associações lazy (convites, vínculos e reposições) montam o DTO ainda dentro da transação.

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
| Cadastro seguro | `brandier.turnstile.*` |
| Login com Google | `brandier.google.client-id` |
| Convites e reposições | `brandier.invitation.*`, `brandier.replenishment.*` |

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

## Licença

Código sob a [licença MIT](LICENSE): você pode usar, copiar, modificar e distribuir. A instância online da demonstração continua sem fins comerciais.
