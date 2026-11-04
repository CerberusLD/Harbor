# Publicar a Harbor API no Render

Este guia mostra como colocar o servidor (Harbor.Api) no ar usando o [Render](https://render.com), com o repo do GitHub e modo Docker. Ao final, o aplicativo Android aponta para um endereço `https://...onrender.com` e funciona de qualquer lugar.

---

## 0. Visão geral

| Item do serviço | Valor recomendado |
|---|---|
| Tipo | Web Service |
| Runtime | Docker (usa o `Harbor.Api/Dockerfile` do repo) |
| Root directory | `Harbor.Api` |
| Porta interna | `10000` (padrão do Render) |
| Health check path | `/health` |
| Banco | SQLite em disco persistente (`/data/harbor.db`) |

O servidor é portável e autossuficiente: cria as tabelas e semeia planos e livros de exemplo na primeira execução (`EnsureCreated` no `Program.cs`), expõe Swagger em `/swagger` e health check em `/health`, e usa CORS liberado.

---

## 1. Criar o serviço no Render

1. Crie uma conta em [render.com](https://render.com) (pode entrar com GitHub).
2. No painel, clique em **New +** → **Web Service**.
3. Escolha **Build and deploy from a Git repository** → **connect** GitHub → autorize o app da Render e conceda acesso ao repositório `Harbor` (privado; só os repo selecionados).
4. Selecione o repositório `Harbor` e o branch `main`.
5. Configure:
   - **Name:** `harbor-api` (gera a URL `https://harbor-api.onrender.com`)
   - **Language/Runtime:** Docker
   - **Root Directory:** `Harbor.Api`
   - **Branch:** `main`
   - **Instance Type:** controle o custo; o plano gratuito *dorme após inatividade* e a primeira requisição demora ~50 s para acordar.
6. Em **Health Check Path**, informe `/health`.

## 2. Variáveis de ambiente

Na seção **Environment**, crie as variáveis abaixo. Gere cada chave com alta entropia:

```bash
openssl rand -base64 32   # repita para cada chave
```

| Nome | Valor | Para que serve |
|---|---|---|
| `HARBOR_TOKEN_KEY` | sequência forte (32+ bytes) | Assina e valida os access/refresh tokens. **NÃO perca**: sessões deixam de ser válidas se mudar. |
| `HARBOR_DATA_KEY` | sequência forte (32+ bytes) | Criptografia AES-256-GCM dos dados pessoais em repouso. **NÃO perca**: sem esta chave o banco fica ilegível. |
| `ConnectionStrings__Harbor` | `Data Source=/data/harbor.db` | Coloca o banco SQLite no disco persistente (veja passo 3). |
| `ASPNETCORE_URLS` | `http://0.0.0.0:10000` | Porta interna que o Render espera (o `Dockerfile` já define isso). |

> Em produção, `Program.cs` exige `HARBOR_TOKEN_KEY`; sem ela o serviço não sobe (comportamento intencional de segurança).

## 3. Disco persistente para o SQLite

O plano gratuito do Render **apaga o filesystem a cada novo deploy** — com SQLite, os cadastros desapareciam. Duas opções:

1. **Disco persistente (recomendado):** em **Advanced → Add Disk**:
   - Mount path: `/data`
   - Size: 1 GB já serve para o MVP
   - Disponível em instâncias pagas; o banco em `/data/harbor.db` sobrevive a deploys.
   - Depois, garanta `ConnectionStrings__Harbor = Data Source=/data/harbor.db`.
2. **Somente free tier:** sem disco, o banco recomeça (planos/livros de exemplo) a cada deploy — serve apenas para demo/desenvolvimento.

## 4. Deploy e verificação

1. Clique em **Create Web Service**. O Render faz o build do Docker na primeira vez (~3–5 min).
2. Abra:
   - `https://SEU-SERVICO.onrender.com/health` → deve responder `{"status":"ok", ...}`
   - `https://SEU-SERVICO.onrender.com/swagger` → aparece o Swagger com todas as rotas.
3. Teste o fluxo pelo Swagger: `POST /api/contas` (cadastro), `POST /api/sessao` (login).

## 5. Deploys automáticos

Cada `git push` no branch configurado dispara um novo deploy. Para trocar algo do servidor: edite, commit, push, pronto.

Para recriar os seeds/novos endpoints depois de mudar `Program.cs`, nenhuma ação adicional é necessária (`EnsureCreated` cuida do primeiro boot).

## 6. Apontar o aplicativo Android para o servidor no Render

O cliente Android lê `HARBOR_API_BASE_URL` (BuildConfig). Para gerar o APK apontando ao Render:

```bash
gradle assembleDebug -PharborApiBaseUrl=https://SEU-SERVICO.onrender.com/
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

O app já permite HTTPS (`usesCleartextTraffic` cobre HTTP local durante o desenvolvimento; o endereço do Render é HTTPS normal). Assim, o dispositivo funciona em qualquer rede Wi-Fi ou dados móveis, sem depender do IP do seu computador.

Em seguida, publique o novo APK em **Releases** do repo para as pessoas baixarem (ver `README.md`, seção _Baixar o aplicativo_).

## 7. Guarda de segredos e backup

- **`HARBOR_DATA_KEY` e `HARBOR_TOKEN_KEY`**: guarde nos *Secrets*/alertas da própria Render (Environment) fora do repo. Se `HARBOR_DATA_KEY` se perder, os textos cifrados não podem mais ser lidos.
- **Backup do banco**: com disco, copie periodicamente `harbor.db` (Render Shell `cp`/download manual ou serviço agendado). Em demo, joque fora sem culpa.

## 8. Limitações conhecidas no Render

- Plano gratuito: o serviço **hiberna**; a primeira chamada depois de 15 min de inatividade demora ~50 s (cold start), o que pode aparentar travamento no app na primeira ação.
- SQLite não é recomendado para múltiplas instâncias; mantenha 1 instância enquanto usar SQLite.
- O pagamento é simulado (fluxo acadêmico): nenhum provedor real está integrado.
