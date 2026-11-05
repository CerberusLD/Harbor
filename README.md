# Harbor ⚓

Harbor é um clube de assinatura de livros com curadoria por preferências, rastreamento de caixas, saldão de edições passadas e comunidade no Discord. Este repositório contém **o servidor (API) e o cliente Android**, além da documentação de execução e do deploy no Render.

- [Servidor e documentação da API](Harbor.Api/README.md)
- [Cliente Android](Harbor.Android/README.md)
- [Publicar o servidor no Render](docs/DEPLOY_RENDER.md)
- [Documento de requisitos A2](Documento_A2_Harbor.pdf)

---

## 📱 Baixar o aplicativo (APK)

O APK de debug assinado para testes fica na aba **Releases**:

> **Link de download:** https://github.com/CerberusLD/Harbor/releases/latest

Como o repositório é **privado**, é preciso estar autenticado no GitHub e ter acesso ao repo para baixar. Duas formas:

1. **Navegador:** abra o link acima, entre com sua conta GitHub e clique no arquivo `.apk` em *Assets*.
2. **Linha de comando (GitHub CLI):**

```bash
gh release download --repo CerberusLD/Harbor --pattern "*.apk"
```

Instalação no Android (aparelho físico):

```bash
adb install -r Harbor-v*.apk
```

Ao instalar, aceite a instalação de "app desconhecido" para o gerenciador que estiver usando (navegador/Drive/Files). O APK é de **debug** — para distribuição final, gere um release assinado em modo `release`.

> **Importante:** o APK publicado aponta para a URL da API embutida em tempo de build (`-PharborApiBaseUrl=...`). A partir da release **v0.1.2**, o APK aponta para o servidor publicado no Render (`https://harbor-dl93.onrender.com/`) e funciona em qualquer rede, inclusive dados móveis. Para apontar a outra instância, gere um novo build com `-PharborApiBaseUrl=<URL>/` (veja `docs/DEPLOY_RENDER.md`, seção 6).

---

## 🆕 Novidades da v0.1.3

- **Animações de interface**: transição entre telas (fade + deslize), botões com escala ao pressionar e cartões/listas com entrada em cascata.
- **Edições Passadas em carrossel**: 8 edições com rolagem automática, indicador e *snap* ao deslizar (antes eram 3 capas fixas).
- **Efeito de escaneamento** na aba de pagamento Pix: linha que percorre o QR Code.
- **Tela de confirmação** com o “✓” animado (spring).

Notas completas da versão: https://github.com/CerberusLD/Harbor/releases/tag/v0.1.3

---

## ✨ Novidades da v0.1.2

- **Modo escuro** com alternância na tela de login e no cartão “Aparência” do perfil; a escolha fica salva no aparelho.
- **Barra inferior** com “Ofertas” e “Categorias” trocadas de posição (Pedidos · Ofertas · Início · Categorias · Perfil).
- **Ofertas**: cartões com altura intrínseca, sem texto cortado.
- **Checkout**: campos de CEP e Cupom redesenhados e legíveis.
- **Pagamento Pix simulado**: aba com QR Code aleatório, valor da compra e botão “Confirmar pagamento” (cancelar não faz nada), além da tela de confirmação.
- **Histórico de pedidos** com pedido de exemplo enquanto a conta está vazia.
- **API**: `POST /api/pedidos` e `POST /api/pedidos/{id}/pagamento`; pagamento simulado disponible fora do `Development` (`simulado: true`).

Notas completas da versão: https://github.com/CerberusLD/Harbor/releases/tag/v0.1.2

---

## 🗂 Estrutura

```
.
├── Harbor.Api/        # API REST em ASP.NET Core 9 + EF Core (SQLite)
│   ├── Program.cs     # bootstrap, seeds, health, swagger
│   ├── Endpoints/     # rotas REST (contas, assinatura, pedidos...)
│   ├── Contracts/     # modelos de requisição/resposta
│   ├── Data/          # DbContext Harbor
│   ├── Dockerfile     # imagem pronta para o Render
│   └── README.md      # rotas, execução local e segurança
├── Harbor.Android/    # App nativo Android 10+ (Kotlin + Jetpack Compose)
│   ├── app/           # código Kotlin, recursos e ícone do launcher
│   ├── screenshots/   # capturas antigas de referência
│   └── README.md      # como compilar e testar
├── docs/
│   └── DEPLOY_RENDER.md  # passo a passo do servidor no Render
└── UI Referencias/    # telas de referência visual do protótipo
```

## 🚀 Comece rápido (rodando tudo local)

1. **API** (requer .NET SDK 9):

```bash
cd Harbor.Api
dotnet restore
dotnet run --urls http://0.0.0.0:5080
# Swagger: http://localhost:5080/swagger · Health: http://localhost:5080/health
```

2. **App Android** (Gradle 8.13 + SDK 35):

```bash
cd Harbor.Android
gradle assembleDebug                      # emulador usa http://10.0.2.2:5080/
gradle assembleDebug -PharborApiBaseUrl=http://IP_DO_COMPUTADOR:5080/   # aparelho físico
```

Detalhes completos em [`Harbor.Api/README.md`](Harbor.Api/README.md) e [`Harbor.Android/README.md`](Harbor.Android/README.md), incluindo depuração sem fio via `adb`.

## ☁️ Produção no Render

O guia [docs/DEPLOY_RENDER.md](docs/DEPLOY_RENDER.md) cobre todo o processo: criação do Web Service com o Dockerfile incluído, variáveis de ambiente obrigatórias (`HARBOR_TOKEN_KEY` e `HARBOR_DATA_KEY`), disco persistente para o SQLite, health check `​/health` e como gerar o APK apontando ao servidor publicado.

## 🔐 Segurança aplicada

- Senhas com hash **bcrypt**; tokens de acesso (15 min) e refresh rotativo (30 dias, apenas hash no banco).
- Dados pessoais, textos da comunidade e avaliações cifrados em repouso com **AES-256-GCM**; e-mails indexados por **HMAC-SHA-256**.
- Chaves de operação (`HARBOR_TOKEN_KEY`, `HARBOR_DATA_KEY`) ficam fora do repositório — em produção, apenas no Environment do Render.
- Pagamento é simulado propositalmente (fluxo acadêmico); nenhum dado de cartão é coletado.

## 💬 Comunidade no Discord

O clube tem um servidor oficial no Discord (QR escaneável dentro do próprio app, na aba Início → “Comunidade Harbor” → QR grande, ou na tela Comunidade):

> https://discord.com/invite/uPt6GjaV6j

## 🖼 Identidade visual

Paleta vinho/creme/dourado, tipografia serif nos títulos, cartões arredondados e navegação inferior. O logo livro + farol também é o **ícone do aplicativo** (adaptativo, sem recortes). As telas de `UI Referencias/` guiam o layout mas não são embutidas no app.
