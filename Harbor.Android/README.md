# Harbor Android

Aplicativo nativo para Android 10+ feito em Kotlin e Jetpack Compose. As telas são compostas com controles Android reais: campos editáveis, validações, estados de carregamento, navegação e chamadas à API. As capturas de `UI Referencias/` são usadas como guia visual e não como fundos de tela.

## Executar e compilar

> Versão pronta para testar: baixe o APK na aba **Releases** — https://github.com/CerberusLD/Harbor/releases/latest (repo privado: entre no GitHub para baixar). Lembre que o APK aponta para a URL de API definida no build.

1. Inicie a API conforme [`Harbor.Api/README.md`](../Harbor.Api/README.md), na porta 5080.
2. Abra `Harbor.Android` no Android Studio ou use Gradle 8.13 com o Android SDK 35.
3. Inicie um emulador Android local e execute `assembleDebug`.

```bash
gradle assembleDebug
```

O APK de depuração fica em `app/build/outputs/apk/debug/app-debug.apk`. O endereço `http://10.0.2.2:5080/` permite que o emulador alcance a API executada no computador.

Para testar em um aparelho físico, informe o IP da máquina que executa a API:

```bash
gradle assembleDebug -PharborApiBaseUrl=http://192.168.55.120:5080/
adb -s <ip>:<porta> install -r app/build/outputs/apk/debug/app-debug.apk
```

Com depuração sem fio, conecte antes com `adb connect <ip>:<porta>`; o aparelho e o computador precisam estar na mesma rede e a porta 5080 liberada no firewall.

## Fluxos no cliente

- Cadastro com validação da API, consentimento e endereço de entrega; login e sessão cifrada com chave do Android Keystore.
- **Modo escuro** com alternância na tela de login e no cartão “Aparência” do perfil; a escolha fica gravada no aparelho.
- Curadoria com limite de 1 a 3 gêneros.
- Consulta e escolha de plano, resumo das condições e confirmação em modo de demonstração.
- **Pagamento Pix simulado**: ao finalizar a assinatura ou o pedido, surge uma aba com QR Code aleatório, o valor da compra e o botão “Confirmar pagamento”. Confirmar ativa a assinatura/registra o pedido na API; cancelar não faz nada.
- Início, ofertas e edições do catálogo, detalhe de livro, pedidos/rastreio, comunidade, preferências, perfil e anonimização.
- O histórico de pedidos mostra um pedido de exemplo enquanto a conta não tiver caixas, para apresentar o formato do acompanhamento.
- Mensagens da comunidade e operações protegidas são autorizadas pela API. Uma assinatura só é ativada após resposta de pagamento aprovado.
- Catálogo e planos ficam em cache local para leitura sem rede; mensagens e dados identificáveis não são gravados nesse cache.

O botão de confirmação chama o endpoint de demonstração de pagamento com aprovação simulada. Ele existe para permitir percorrer o fluxo acadêmico sem coletar ou armazenar dados de cartão. Para produção, substituir essa chamada por um provedor real e tokenizado; não usar a aprovação simulada como cobrança.

## Identidade visual e recursos

A interface usa a paleta vinho, creme e dourado, tipografia serifada nos títulos, cartões arredondados e a navegação inferior da referência. O logotipo, o símbolo livro/farol, a ilustração de rastreio e as capas foram recortados das referências e ampliados com reamostragem de alta qualidade e nitidez leve. Textos e elementos de interface são desenhados em Compose para permanecerem nítidos em diferentes densidades.

- `assets/extracted/`: recortes originais usados como fonte dos recursos gráficos.
- `app/src/main/res/drawable-nodpi/`: imagens ampliadas utilizadas na interface e ícone do launcher.
- `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`: ícone adaptável Harbor.
- `UI Referencias/`: imagens originais usadas para consulta visual, mantidas fora do pacote Android.
- `screenshots/`: capturas de versões anteriores; não representam prova visual da implementação Compose atual.

Os nomes das rotas e os modelos HTTP estão em `app/src/main/java/br/com/harbor/HarborApi.kt`; as telas e o estado de navegação ficam em `MainActivity.kt`.
