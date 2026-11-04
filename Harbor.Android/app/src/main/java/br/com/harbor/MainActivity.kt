package br.com.harbor

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.gson.Gson
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private enum class HarborPage {
    Login, Cadastro, ErroCadastro, PerfilInicial, Preferencias, Planos,
    Checkout, Inicio, Ofertas, Caixa, Rastreamento, Comunidade, ComunidadeQr, Perfil
}

internal data class LivroUi(
    val id: Int,
    val titulo: String,
    val autor: String,
    val sinopse: String,
    val categoria: String,
    val estoque: Int,
)

internal data class CaixaUi(
    val id: Int,
    val status: String,
    val codigoRastreio: String,
    val livro: LivroUi?,
    val criadaEm: String,
)

internal data class MensagemUi(
    val id: Int,
    val tipo: String,
    val nome: String,
    val texto: String,
    val criadaEm: String,
)

class MainActivity : ComponentActivity() {
    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Draw edge-to-edge: the app paints behind the system bars, and Compose
        // pads the content using WindowInsets (status bars / navigation bars).
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.rgb(139, 17, 54)
        window.navigationBarColor = android.graphics.Color.rgb(86, 6, 31)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        setContent { HarborApp() }
    }
}

/** Keeps only access/refresh tokens, encrypted using a non-exportable Android Keystore key. */
private object SessionVault {
    private const val Store = "harbor"
    private const val Entry = "session_encrypted_v1"
    private const val Alias = "HarborSessionKeyV1"

    fun read(context: android.content.Context): Pair<String, String> {
        val prefs = context.getSharedPreferences(Store, 0)
        val stored = prefs.getString(Entry, null) ?: run {
            prefs.edit().remove("token").apply()
            return "" to ""
        }
        return try {
            val parts = stored.split(':', limit = 2)
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val ciphertext = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            val values = String(cipher.doFinal(ciphertext), Charsets.UTF_8).split('|', limit = 2)
            values[0] to values.getOrElse(1) { "" }
        } catch (_: Exception) {
            prefs.edit().remove(Entry).apply()
            "" to ""
        }
    }

    fun write(context: android.content.Context, token: String, refreshToken: String) {
        val prefs = context.getSharedPreferences(Store, 0)
        prefs.edit().remove("token").apply()
        if (token.isBlank()) {
            prefs.edit().remove(Entry).apply()
            return
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal("$token|$refreshToken".toByteArray(Charsets.UTF_8))
        val value = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(encrypted, Base64.NO_WRAP)
        prefs.edit().putString(Entry, value).apply()
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(Alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                Alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }
}

@Composable
private fun HarborApp() {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("harbor", 0) }
    val gson = remember { Gson() }
    val savedSession = remember { SessionVault.read(context) }
    val scope = rememberCoroutineScope()

    var token by remember { mutableStateOf(savedSession.first) }
    var refreshToken by remember { mutableStateOf(savedSession.second) }
    var page by remember { mutableStateOf(if (savedSession.first.isBlank()) HarborPage.Login else HarborPage.Inicio) }
    var returnAfterPreferences by remember { mutableStateOf(HarborPage.Inicio) }
    var onboarding by remember { mutableStateOf(false) }
    var changingPlan by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var authError by remember { mutableStateOf("") }

    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordConfirmation by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var consent by remember { mutableStateOf(false) }

    var genres by remember { mutableStateOf(setOf("Fantasia", "Suspense")) }
    var genreMessage by remember { mutableStateOf("") }
    var plans by remember { mutableStateOf(emptyList<PlanoReply>()) }
    var selectedPlan by remember { mutableStateOf<PlanoReply?>(null) }
    var books by remember { mutableStateOf(emptyList<LivroUi>()) }
    var boxes by remember { mutableStateOf(emptyList<CaixaUi>()) }
    var messages by remember { mutableStateOf(emptyList<MensagemUi>()) }
    var selectedBook by remember { mutableStateOf<LivroUi?>(null) }
    var cart by remember { mutableStateOf<Map<Int, Int>>(emptyMap()) }
    var subscriptionStatus by remember { mutableStateOf("Sem assinatura") }
    var renewalDate by remember { mutableStateOf("") }

    var chatText by remember { mutableStateOf("") }
    var checkoutCep by remember { mutableStateOf("") }
    var checkoutCoupon by remember { mutableStateOf("") }
    var paymentMethod by remember { mutableStateOf("Cartão") }
    var reviewTarget by remember { mutableStateOf<CaixaUi?>(null) }
    var reviewStars by remember { mutableIntStateOf(5) }
    var reviewText by remember { mutableStateOf("") }
    var showCancelDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var communityError by remember { mutableStateOf("") }

    fun saveSession(access: String, refresh: String = refreshToken) {
        token = access
        refreshToken = refresh
        SessionVault.write(context, access, refresh)
    }

    suspend fun renewSession(): Boolean {
        if (refreshToken.isBlank()) return false
        return try {
            val newSession = HarborApiClient.api.renovar(RefreshBody(refreshToken))
            saveSession(newSession.token, newSession.refreshToken)
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun apiAction(action: suspend () -> Unit) {
        try {
            action()
        } catch (first: Exception) {
            if (first is HttpException && first.code() == 401 && renewSession()) {
                action()
            } else {
                throw first
            }
        }
    }

    fun runAction(action: suspend () -> Unit) {
        scope.launch {
            busy = true
            error = ""
            try {
                apiAction(action)
            } catch (failure: Exception) {
                error = failure.toHarborMessage()
            } finally {
                busy = false
            }
        }
    }

    suspend fun fetchBooks() {
        val response = HarborApiClient.api.livros()
        books = response.map { it.toUi() }
        preferences.edit().putString("books_cache", gson.toJson(response)).apply()
    }

    suspend fun fetchBoxes() {
        boxes = HarborApiClient.api.caixas("Bearer $token").map { box ->
            CaixaUi(box.id, box.status, box.codigoRastreio, box.livro?.toUi(), box.criadaEm)
        }
    }

    suspend fun fetchCommunity() {
        messages = HarborApiClient.api.comunidade("Bearer $token").map {
            MensagemUi(it.id, it.tipo, it.nome, it.texto, it.criadaEm)
        }
    }

    LaunchedEffect(Unit) {
        preferences.getString("plans_cache", null)?.let { json ->
            runCatching { plans = gson.fromJson(json, Array<PlanoReply>::class.java).toList() }
        }
        preferences.getString("books_cache", null)?.let { json ->
            runCatching { books = gson.fromJson(json, Array<LivroReply>::class.java).map { it.toUi() } }
        }
        runCatching {
            plans = HarborApiClient.api.planos().sortedBy { it.planSortOrder() }
            preferences.edit().putString("plans_cache", gson.toJson(plans)).apply()
            selectedPlan = plans.firstOrNull()
            fetchBooks()
        }
    }

    LaunchedEffect(token) {
        if (token.isBlank()) return@LaunchedEffect
        try {
            if (savedSession.first.isNotBlank() && refreshToken == savedSession.second) {
                if (!renewSession()) {
                    saveSession("")
                    page = HarborPage.Login
                    return@LaunchedEffect
                }
            }
            apiAction {
                val profile = HarborApiClient.api.perfil("Bearer $token")
                name = profile.nome
                email = profile.email
                address = profile.endereco
                if (profile.generos.isNotEmpty()) genres = profile.generos.toSet()
                fetchBoxes()
                val status = runCatching { HarborApiClient.api.assinatura("Bearer $token") }.getOrNull()
                subscriptionStatus = status?.get("status")?.toString() ?: "Sem assinatura"
                renewalDate = status?.get("proximaRenovacao")?.toString()?.take(10).orEmpty()
                if (page == HarborPage.PerfilInicial && !onboarding) page = HarborPage.Inicio
            }
        } catch (failure: Exception) {
            if (failure is HttpException && failure.code() == 401) {
                saveSession("")
                page = HarborPage.Login
            }
        }
    }

    LaunchedEffect(page) {
        if (page == HarborPage.Comunidade && token.isNotBlank()) {
            communityError = ""
            runCatching { fetchCommunity() }.onFailure { communityError = it.toHarborMessage() }
        }
    }

    fun navigateTab(tab: HarborTab) {
        error = ""
        when (tab) {
            HarborTab.Orders -> page = HarborPage.Rastreamento
            HarborTab.Categories -> {
                returnAfterPreferences = page
                page = HarborPage.Preferencias
            }
            HarborTab.Home -> page = HarborPage.Inicio
            HarborTab.Offers -> page = HarborPage.Ofertas
            HarborTab.Profile -> page = HarborPage.Perfil
        }
    }

    fun saveGenres() {
        if (genres.isEmpty()) {
            genreMessage = "Selecione ao menos um gênero."
            return
        }
        runAction {
            HarborApiClient.api.salvarPreferencias("Bearer $token", PreferenciasBody(genres.toList()))
            genreMessage = ""
            if (onboarding) { changingPlan = false; page = HarborPage.Planos } else page = returnAfterPreferences
        }
    }

    fun register() {
        val validation = when {
            name.isBlank() -> "Informe seu nome completo."
            !email.contains('@') -> "Informe um e-mail válido."
            password.length < 8 -> "A senha precisa ter ao menos 8 caracteres."
            password != passwordConfirmation -> "As senhas não coincidem."
            !consent -> "Aceite a Política de Privacidade para continuar."
            else -> ""
        }
        if (validation.isNotBlank()) {
            authError = validation
            page = HarborPage.ErroCadastro
            return
        }
        runAction {
            val session = HarborApiClient.api.cadastrar(
                ContaBody(name.trim(), email.trim(), password, "Endereço a informar", consentimento = true),
            )
            saveSession(session.token, session.refreshToken)
            onboarding = true
            page = HarborPage.PerfilInicial
        }
    }

    fun saveProfile() {
        if (address.isBlank()) {
            error = "Informe o endereço de entrega."
            return
        }
        runAction {
            HarborApiClient.api.atualizarPerfil("Bearer $token", PerfilBody(name.trim(), address.trim()))
            if (onboarding) {
                page = HarborPage.Preferencias
                returnAfterPreferences = HarborPage.Inicio
            }
        }
    }

    fun continueWithPlan() {
        val plan = selectedPlan
        if (plan == null) {
            error = "Escolha um plano para continuar."
            return
        }
        if (token.isBlank()) {
            page = HarborPage.Cadastro
            return
        }
        if (changingPlan) {
            runAction {
                HarborApiClient.api.alterarPlano("Bearer $token", AssinarBody(plan.id))
                changingPlan = false
                page = HarborPage.Perfil
            }
        } else {
            page = HarborPage.Checkout
        }
    }

    fun finishCheckout() {
        if (checkoutCep.length < 8 && cart.isNotEmpty()) {
            error = "Informe um CEP válido para calcular a entrega."
            return
        }
        if (paymentMethod.isBlank()) {
            error = "Escolha uma forma de pagamento."
            return
        }
        runAction {
            val plan = selectedPlan
            if (plan != null && cart.isEmpty()) {
                val subscription = HarborApiClient.api.assinar("Bearer $token", AssinarBody(plan.id))
                HarborApiClient.api.confirmarPagamento("Bearer $token", subscription.id, PagamentoBody(aprovado = true))
                subscriptionStatus = "Ativa"
            } else {
                val order = HarborApiClient.api.criarPedido(
                    "Bearer $token",
                    PedidoBody(
                        cep = checkoutCep,
                        cupom = checkoutCoupon,
                        formaPagamento = paymentMethod,
                        itens = cart.map { (bookId, quantity) -> PedidoItemBody(bookId, quantity) },
                    ),
                )
                HarborApiClient.api.pagarPedido("Bearer $token", order.id, PagamentoBody(aprovado = true))
                cart = emptyMap()
            }
            fetchBoxes()
            page = HarborPage.Inicio
        }
    }

    MaterialTheme(colorScheme = lightColorScheme(primary = HarborColors.Wine, onPrimary = HarborColors.Sand, secondary = HarborColors.Gold, background = HarborColors.Cream, surface = HarborColors.Paper, onSurface = HarborColors.Ink, error = HarborColors.Wine)) {
        when (page) {
            HarborPage.Login, HarborPage.Cadastro -> HarborAuthScreen(
                registration = page == HarborPage.Cadastro,
                name = name,
                onName = { name = it; authError = "" },
                email = email,
                onEmail = { email = it; authError = "" },
                password = password,
                onPassword = { password = it; authError = "" },
                confirmPassword = passwordConfirmation,
                onConfirmPassword = { passwordConfirmation = it; authError = "" },
                consent = consent,
                onConsent = { consent = it; authError = "" },
                busy = busy,
                message = authError.ifBlank { error },
                onSubmit = {
                    if (page == HarborPage.Cadastro) register() else runAction {
                        val session = HarborApiClient.api.entrar(LoginBody(email.trim(), password))
                        saveSession(session.token, session.refreshToken)
                        page = HarborPage.Inicio
                    }
                },
                onRegister = { authError = ""; error = ""; page = HarborPage.Cadastro },
                onLogin = { authError = ""; error = ""; page = HarborPage.Login },
            )

            HarborPage.ErroCadastro -> HarborErrorScreen(
                name = name,
                onName = { name = it },
                email = email,
                onEmail = { email = it },
                passwordConfirmation = passwordConfirmation,
                onPasswordConfirmation = { passwordConfirmation = it },
                message = authError,
                onRetry = { authError = ""; error = ""; page = HarborPage.Cadastro },
                onLogin = { authError = ""; error = ""; page = HarborPage.Login },
            )

            HarborPage.PerfilInicial -> HarborOnboardingProfile(
                name = name,
                onName = { name = it },
                email = email,
                address = address,
                onAddress = { address = it },
                consent = consent,
                onConsent = { consent = it },
                busy = busy,
                message = error,
                onSave = { saveProfile() },
                onBack = { page = HarborPage.Cadastro },
                onTab = ::navigateTab,
            )

            HarborPage.Preferencias -> HarborGenreScreen(
                selected = genres,
                onToggle = { genre ->
                    genreMessage = ""
                    genres = when {
                        genre in genres -> genres - genre
                        genres.size < 3 -> genres + genre
                        else -> {
                            genreMessage = "Você pode escolher no máximo 3 gêneros."
                            genres
                        }
                    }
                },
                message = genreMessage,
                busy = busy,
                onSave = { saveGenres() },
                selectedTab = HarborTab.Categories,
                onTab = ::navigateTab,
            )

            HarborPage.Planos -> HarborPlansScreen(
                plans = plans,
                selectedId = selectedPlan?.id,
                onSelect = { selectedPlan = it; error = "" },
                changing = changingPlan,
                busy = busy,
                onContinue = { continueWithPlan() },
                selectedTab = HarborTab.Orders,
                onTab = ::navigateTab,
            )

            HarborPage.Checkout -> HarborCheckoutScreen(
                books = books,
                cart = cart,
                subscriptionPlan = if (cart.isEmpty()) selectedPlan else null,
                cep = checkoutCep,
                onCep = { checkoutCep = it.filter(Char::isDigit).take(8); error = "" },
                coupon = checkoutCoupon,
                onCoupon = { checkoutCoupon = it.trim().take(24); error = "" },
                payment = paymentMethod,
                onPayment = { paymentMethod = it },
                busy = busy,
                message = error,
                onFinish = { finishCheckout() },
                onBack = { page = if (cart.isNotEmpty()) HarborPage.Ofertas else HarborPage.Planos },
            )

            HarborPage.Inicio -> HarborDashboard(
                name = name,
                books = books,
                activeBox = boxes.firstOrNull(),
                onSlideAction = { index ->
                    when (index) {
                        0 -> { selectedBook = boxes.firstOrNull()?.livro; page = HarborPage.Caixa }
                        1 -> { returnAfterPreferences = HarborPage.Inicio; page = HarborPage.Preferencias }
                        2 -> page = HarborPage.Ofertas
                        3 -> page = HarborPage.Comunidade
                        5 -> page = HarborPage.ComunidadeQr
                        else -> page = HarborPage.Rastreamento
                    }
                },
                onBook = { selectedBook = it; page = HarborPage.Caixa },
                selectedTab = HarborTab.Home,
                onTab = ::navigateTab,
            )

            HarborPage.Ofertas -> HarborOffersScreen(
                books = books,
                cart = cart,
                onAdd = { book -> cart = cart + (book.id to ((cart[book.id] ?: 0) + 1)); error = "" },
                onCheckout = {
                    if (cart.isEmpty()) error = "Adicione ao menos uma caixa ao carrinho."
                    else if (token.isBlank()) page = HarborPage.Login
                    else page = HarborPage.Checkout
                },
                onBook = { selectedBook = it; page = HarborPage.Caixa },
                selectedTab = HarborTab.Offers,
                onTab = ::navigateTab,
            )

            HarborPage.Caixa -> HarborBookDetail(selectedBook) { page = HarborPage.Inicio }

            HarborPage.Rastreamento -> HarborOrderScreen(
                boxes = boxes,
                onRate = { reviewTarget = it },
                selectedTab = HarborTab.Orders,
                onTab = ::navigateTab,
            )

            HarborPage.Comunidade -> HarborCommunityScreen(
                messages = messages,
                ownName = name,
                text = chatText,
                onText = { chatText = it.take(1000) },
                busy = busy,
                error = communityError,
                onSend = {
                    if (subscriptionStatus != "Ativa") communityError = "Apenas assinantes ativos podem publicar na comunidade."
                    else if (chatText.isBlank()) communityError = "Escreva uma mensagem antes de publicar."
                    else runAction {
                        HarborApiClient.api.publicar("Bearer $token", MensagemBody(chatText.trim()))
                        chatText = ""
                        fetchCommunity()
                    }
                },
                onOpenQr = { page = HarborPage.ComunidadeQr },
                onBack = { page = HarborPage.Inicio },
                selectedTab = HarborTab.Profile,
                onTab = ::navigateTab,
            )

            HarborPage.ComunidadeQr -> HarborDiscordQrScreen(
                onBack = { page = HarborPage.Inicio },
                selectedTab = HarborTab.Home,
                onTab = ::navigateTab,
            )

            HarborPage.Perfil -> HarborProfileScreen(
                name = name,
                onName = { name = it },
                email = email,
                address = address,
                onAddress = { address = it },
                genres = genres,
                subscriptionStatus = subscriptionStatus,
                nextRenewal = renewalDate,
                busy = busy,
                message = error,
                onSave = { saveProfile() },
                onGenres = { returnAfterPreferences = HarborPage.Perfil; page = HarborPage.Preferencias },
                onPlan = { changingPlan = subscriptionStatus.equals("Ativa", true); page = HarborPage.Planos },
                onCancel = { showCancelDialog = true },
                onOrders = { page = HarborPage.Rastreamento },
                onDelete = { showDeleteDialog = true },
                onLogout = {
                    runAction {
                        runCatching { HarborApiClient.api.sair(RefreshBody(refreshToken)) }
                        saveSession("")
                        page = HarborPage.Login
                    }
                },
                selectedTab = HarborTab.Profile,
                onTab = ::navigateTab,
            )
        }
    }

    reviewTarget?.let { box ->
        AlertDialog(
            onDismissRequest = { reviewTarget = null },
            title = { Text("Avalie sua leitura", color = HarborColors.Wine) },
            text = {
                Column {
                    Text(box.livro?.titulo.orEmpty(), fontFamily = androidx.compose.ui.text.font.FontFamily.Serif, color = HarborColors.Wine)
                    Row(Modifier.padding(vertical = 8.dp)) {
                        (1..5).forEach { star ->
                            Text(if (star <= reviewStars) "★" else "☆", Modifier.clickable { reviewStars = star }.padding(3.dp), color = HarborColors.Gold, fontSize = 27.sp)
                        }
                    }
                    OutlinedTextField(reviewText, { reviewText = it.take(500) }, label = { Text("Seu comentário") }, maxLines = 3)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    runAction {
                        HarborApiClient.api.avaliar("Bearer $token", AvaliacaoBody(box.livro!!.id, reviewStars, reviewText.trim()))
                        reviewTarget = null
                        reviewText = ""
                        fetchCommunity()
                    }
                }) { Text("Publicar avaliação", color = HarborColors.Wine) }
            },
            dismissButton = { TextButton(onClick = { reviewTarget = null }) { Text("Cancelar") } },
        )
    }

    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title = { Text("Cancelar assinatura?", color = HarborColors.Wine) },
            text = { Text("A assinatura deixará de renovar. O prazo legal de arrependimento de 7 dias e as condições do plano serão respeitados.") },
            confirmButton = {
                TextButton(onClick = {
                    runAction {
                        HarborApiClient.api.cancelarAssinatura("Bearer $token")
                        subscriptionStatus = "Cancelada"
                        showCancelDialog = false
                    }
                }) { Text("Confirmar cancelamento", color = HarborColors.Wine) }
            },
            dismissButton = { TextButton(onClick = { showCancelDialog = false }) { Text("Manter plano") } },
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Excluir minha conta?", color = HarborColors.Wine) },
            text = { Text("Os dados identificáveis serão anonimizados e sua sessão será encerrada.") },
            confirmButton = {
                TextButton(onClick = {
                    runAction {
                        HarborApiClient.api.excluirPerfil("Bearer $token")
                        saveSession("")
                        showDeleteDialog = false
                        page = HarborPage.Login
                    }
                }) { Text("Excluir e anonimizar", color = HarborColors.Wine) }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Voltar") } },
        )
    }
}

private fun LivroReply.toUi() = LivroUi(id, titulo, autor, sinopse, categoria, estoque)

private fun PlanoReply.planSortOrder(): Int = when (periodicidade.lowercase()) {
    "anual" -> 0
    "trimestral" -> 1
    else -> 2
}

private fun Throwable.toHarborMessage(): String {
    if (this is HttpException) {
        val response = response()?.errorBody()?.string().orEmpty()
        val serverMessage = Regex("\"mensagem\"\\s*:\\s*\"([^\"]+)\"").find(response)?.groupValues?.getOrNull(1)
        if (!serverMessage.isNullOrBlank()) return serverMessage
        return when (code()) {
            400 -> "Confira os dados informados e tente novamente."
            401 -> "E-mail ou senha inválidos."
            403 -> "Este recurso está disponível apenas para assinantes ativos."
            409 -> "Já existe uma conta com este e-mail."
            503 -> "O serviço está temporariamente indisponível. Tente novamente."
            else -> "Não foi possível concluir agora. Tente novamente."
        }
    }
    return "Não foi possível conectar ao Harbor. Verifique sua conexão e tente novamente."
}
