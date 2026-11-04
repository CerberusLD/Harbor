# Harbor API

API REST do MVP do clube de livros Harbor, em ASP.NET Core 9, Entity Framework Core e SQLite. O código prioriza rotas pequenas e modelos próximos dos conceitos do produto.

## Executar

> Para publicar este servidor em produção na nuvem (Render), veja [`../docs/DEPLOY_RENDER.md`](../docs/DEPLOY_RENDER.md): Dockerfile incluso, variáveis exigidas e disco persistente para o SQLite.

Requisitos: .NET SDK 9. Na pasta `Harbor.Api`:

```bash
dotnet restore
dotnet run --urls http://0.0.0.0:5080
```

Abra `http://localhost:5080/swagger`; verifique o serviço em `/health`. Na primeira execução, o arquivo `harbor.db` é criado com três planos e livros de exemplo. Para Android Emulator, use `http://10.0.2.2:5080`.

Defina `HARBOR_TOKEN_KEY` com uma chave secreta forte antes de qualquer uso compartilhado. Sem ela, o projeto usa uma chave fixa apenas para desenvolvimento local. O access token expira em 15 minutos; o refresh token rotativo expira em 30 dias e só seu hash é armazenado.

Os campos pessoais de clientes, textos/comentários da comunidade e avaliação, além do responsável de operação, são cifrados com AES-256-GCM em repouso. Defina `HARBOR_DATA_KEY` como segredo de alta entropia antes de usar fora do ambiente local; a mesma chave precisa ser mantida para ler o banco. No modo `Development`, a aplicação usa uma chave de desenvolvimento. A migração de compatibilidade cifra os registros em texto que existiam na base antes desta mudança. E-mails são localizados por HMAC-SHA-256, sem guardar um índice de busca em texto puro.

## Rotas

Todas as rotas de `/api` retornam JSON. Envie `Authorization: Bearer <token>` nas rotas de conta. O cadastro/login retornam o token.

| Método e caminho | Uso |
|---|---|
| `POST /api/contas` | Cadastro: `nome`, `email`, `senha`, `endereco`, `consentimento` |
| `POST /api/sessao` | Login: `email`, `senha` |
| `POST /api/sessao/renovar` | Trocar o refresh token por novo access token e novo refresh token |
| `POST /api/sessao/sair` | Revogar o refresh token da sessão |
| `GET/PUT/DELETE /api/perfil` | Consultar, atualizar ou anonimizar o perfil |
| `GET /api/planos` | Planos e valores vigentes |
| `POST /api/assinatura` | Solicitar assinatura por `planoId`; fica `Pendente` até pagamento aprovado |
| `POST /api/assinatura/{id}/pagamento` | Aprovar ou recusar o pagamento no fluxo acadêmico de demonstração; somente aprovação ativa |
| `PUT /api/assinatura/plano` | Solicitar novo plano; novo valor vale para a próxima cobrança |
| `POST /api/assinatura/cancelar` | Cancelar e impedir renovação futura |
| `GET /api/assinatura` | Consultar assinatura da conta autenticada |
| `POST /api/preferencias` | Salvar de 1 a 3 gêneros: `generos: ["Fantasia"]` |
| `GET /api/livros` | Catálogo público |
| `POST /api/operacao/curadoria` | Sugerir obra em estoque compatível com os gêneros do assinante |
| `GET /api/caixas` | Caixas e situação de entrega do cliente autenticado |
| `POST /api/avaliacoes` | Avaliar livro recebido: `livroId`, `nota` (1–5), `comentario` |
| `GET/POST /api/comunidade` | Ler mensagens / publicar para assinante ativo |
| `POST /api/operacao/livros` | Cadastrar título e estoque |
| `POST /api/operacao/caixas` | Reservar livro e montar caixa para assinatura ativa |
| `PUT /api/operacao/caixas/{id}/tratativa` | Registrar reposição ou reembolso para uma entrega problemática |
| `PUT /api/operacao/caixas/{id}/envio` | Registrar `codigoRastreio` |
| `PUT /api/operacao/caixas/{id}/status` | Atualizar situação da entrega |

Exemplo de cadastro:

```json
{
  "nome": "Ana Souza",
  "email": "ana@example.com",
  "senha": "senha-segura",
  "endereco": "Rua das Flores, 10, Curitiba",
  "consentimento": true
}
```

## Regras implementadas

- E-mail único, senha armazenada com hash BCrypt e token assinado com validade de 15 minutos.
- Refresh token rotativo com validade máxima de 30 dias; o valor é mantido cifrado no Android Keystore e o servidor guarda somente SHA-256.
- AES-256-GCM para dados pessoais em repouso; índice de email com HMAC-SHA-256.
- Preferências limitadas a 1–3 gêneros; estoque decrementado ao montar caixa.
- Só se avalia livro entregue; somente assinante ativo publica na comunidade.
- Pedido de assinatura sem gateway de pagamento permanece pendente. Não são recebidos nem armazenados dados de cartão.
- Exclusão de perfil anonimiza os dados identificadores imediatamente.
- Pagamento simulado só pode ser confirmado no ambiente `Development`; rotas operacionais exigem `X-Operations-Key` fora dele.

## Escopo e pendências conhecidas

Este repositório continha originalmente um CRUD de embarcações, que não correspondia ao relatório Harbor. A implementação atual troca esse domínio pelo MVP do relatório. Para manter as regras comerciais, a assinatura não é ativada até existir integração de pagamento aprovada.

A confirmação de pagamento atualmente é uma rota de demonstração e só responde no ambiente `Development`; não substitui uma confirmação assinada por um provedor. Os planos podem ser trocados e a assinatura pode ser cancelada; os endpoints operacionais permitem curadoria por gênero, estoque, montagem/envio e tratativas. Em ambiente compartilhado, configure uma `HARBOR_OPERATIONS_KEY` forte e envie-a no header `X-Operations-Key`.

Ainda faltam itens de produção definidos no documento: integração real de pagamento com TLS 1.3 e tokenização, renovação agendada e suspensão/notificação por falha, consulta automática de rastreio a cada 10 minutos, integração de carga ao Discord, trilha imutável por 12 meses, backup incremental/RPO/RTO, monitoramento de uptime e identidade/roles administrativas completas. A rota operacional usa uma chave compartilhada e não substitui um provedor de identidade administrativo. O prazo de arrependimento de 7 dias é informado no fluxo, mas ainda não há integração de reembolso automatizado. Metas RNF01/RNF02/RNF07/RNF13 também exigem instrumentação, operação e evidência de carga/cobertura. O projeto já cifra dados pessoais em desenvolvimento, mas ainda precisa de gestão operacional da chave, TLS de ponta a ponta e demais controles antes de receber dados reais.

## Organização

`Program.cs` configura o serviço e dados demonstrativos; `Models/HarborModels.cs` contém as entidades; `Data/HarborDbContext.cs` mapeia SQLite; `Endpoints/HarborEndpoints.cs` reúne rotas e regras do MVP. `EnsureCreated` é adequado ao protótipo; em evolução com dados persistentes, adotar migrações EF Core.
