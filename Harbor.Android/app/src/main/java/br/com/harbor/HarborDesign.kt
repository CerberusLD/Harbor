package br.com.harbor

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import kotlinx.coroutines.delay

/**
 * Harbor palette. The brand wine/sand tones stay recognisable in both themes while
 * surfaces, text and borders switch through [isDark]. The property is a Compose state,
 * so every screen that reads a colour is recomposed as soon as the theme changes.
 */
internal object HarborColors {
    var isDark by mutableStateOf(false)
        private set

    fun setDarkMode(enabled: Boolean) {
        isDark = enabled
    }

    private fun palette(light: Color, dark: Color): Color = if (isDark) dark else light

    /** Brand accent used for headings, icons and text. */
    val Wine get() = palette(Color(0xFF630626), Color(0xFFE9B6C4))

    /** Solid brand colour used behind buttons and selected cards. */
    val WineSolid get() = palette(Color(0xFF630626), Color(0xFF74102E))
    val WineDark get() = palette(Color(0xFF4A0218), Color(0xFF120A0E))
    val WineTop get() = palette(Color(0xFF8B1136), Color(0xFF5A0C22))
    val Cream get() = palette(Color(0xFFFBF7F2), Color(0xFF151012))
    val Sand get() = Color(0xFFFDE6C2)
    val SandDeep get() = palette(Color(0xFFF3C888), Color(0xFF3A272E))
    val Paper get() = palette(Color(0xFFFFFEFC), Color(0xFF22181C))
    val Ink get() = palette(Color(0xFF271C1E), Color(0xFFF4EAE5))
    val Muted get() = palette(Color(0xFF86707A), Color(0xFFC0A7B0))
    val Gold get() = palette(Color(0xFFC79A4B), Color(0xFFE0B366))
    val GoldSoft get() = palette(Color(0xFFF4E3C2), Color(0xFF4A3520))
    val RoseLine get() = palette(Color(0xFFEAD3D4), Color(0xFF4A343C))
    val Mint get() = palette(Color(0xFF2E7D5B), Color(0xFF70D6A9))
    val Amber get() = palette(Color(0xFFB7791F), Color(0xFFE8B45F))

    /** Warmed surface used by rows, inputs and low-emphasis cards. */
    val SurfaceWarm get() = palette(Color(0xFFFCF7F0), Color(0xFF2A1E23))
    val Field get() = palette(Color(0xFFFFFFFF), Color(0xFF302329))
    val WineOnLight get() = Color(0xFF630626)
    val OnWine get() = Color(0xFFFDE6C2)

    val HeaderBrush get() = Brush.verticalGradient(listOf(Color(0xFF8B1136), Color(0xFF630626), Color(0xFF4A0218)))
    val BottomBrush get() = Brush.verticalGradient(listOf(Color(0xFF7B0F30), Color(0xFF56061F)))
}

/** Uma edição do carrossel "Edições Passadas" (capa + título + referência mensal). */
internal data class EdicaoUi(val capa: Int, val titulo: String, val rotulo: String)

/**
 * Entrada suave (fade + subida) usada em cartões e listas para a interface não
 * aparecer "seca". O atraso permite animar itens em cascata.
 */
@Composable
fun Modifier.harborEntrance(delayMillis: Int = 0, slide: Float = 26f): Modifier {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(delayMillis.toLong()); visible = true }
    val progress by animateFloatAsState(if (visible) 1f else 0f, tween(380), label = "entrance")
    return this.graphicsLayer { alpha = progress; translationY = (1f - progress) * slide }
}

/** Escala do botão primário enquanto está pressionado. */
@Composable
private fun Modifier.harborPressScale(interaction: MutableInteractionSource): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, tween(120), label = "press")
    return this.graphicsLayer { scaleX = scale; scaleY = scale }
}

internal enum class HarborTab { Orders, Categories, Home, Offers, Profile }

/** Official invite link of the Harbor Discord community (also encoded in the QR). */
internal const val HarborDiscordInvite = "https://discord.com/invite/uPt6GjaV6j"

fun OpenDiscordInvite(context: android.content.Context) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(HarborDiscordInvite)))
    }.onFailure {
        Toast.makeText(context, "Nenhum app disponível para abrir o link.", Toast.LENGTH_SHORT).show()
    }
}

@Composable
internal fun HarborBrandLogo(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.logotipo_completo),
        contentDescription = "Harbor — Seu Porto Seguro para Novas Histórias",
        modifier = modifier,
        contentScale = ContentScale.Fit
    )
}

private fun toggleHarborTheme(context: android.content.Context) {
    val enabled = !HarborColors.isDark
    HarborColors.setDarkMode(enabled)
    context.getSharedPreferences("harbor", 0).edit().putBoolean("dark_mode", enabled).apply()
}

/** Compact theme control used over the wine header (login and onboarding). */
@Composable
internal fun HarborThemeToggle(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val dark = HarborColors.isDark
    Box(
        modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.12f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
            .clickable { toggleHarborTheme(context) }
            .semantics { contentDescription = if (dark) "Ativar modo claro" else "Ativar modo escuro" },
        contentAlignment = Alignment.Center
    ) {
        Text(if (dark) "☀" else "☾", color = HarborColors.Sand, fontSize = 17.sp)
    }
}

/** Appearance card for the profile screen. */
@Composable
internal fun HarborAppearanceCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val dark = HarborColors.isDark
    ModernCard(modifier) {
        Column(Modifier.padding(15.dp)) {
            HarborSectionLabel("Aparência")
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(15.dp))
                    .background(HarborColors.SurfaceWarm)
                    .clickable { toggleHarborTheme(context) }
                    .padding(horizontal = 13.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Modo escuro", color = HarborColors.Wine, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (dark) "Ativado — tons escuros confortáveis para a leitura" else "Desativado — tema claro vinho e creme",
                        color = HarborColors.Muted, fontSize = 10.sp, lineHeight = 13.sp
                    )
                }
                Switch(
                    checked = dark,
                    onCheckedChange = { toggleHarborTheme(context) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = HarborColors.Sand,
                        checkedTrackColor = HarborColors.WineSolid,
                        uncheckedThumbColor = HarborColors.Paper,
                        uncheckedTrackColor = HarborColors.RoseLine,
                        uncheckedBorderColor = HarborColors.Muted.copy(alpha = 0.5f)
                    )
                )
            }
        }
    }
}

@Composable
internal fun HarborDashboardFrame(
    content: @Composable ColumnScope.() -> Unit,
    selectedTab: HarborTab,
    onTab: (HarborTab) -> Unit,
) {
    Column(Modifier.fillMaxSize().background(HarborColors.Cream)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(160.dp)
                .background(HarborColors.HeaderBrush)
                .statusBarsPadding(),
            contentAlignment = Alignment.Center
        ) {
            HarborBrandLogo(Modifier.width(266.dp).height(101.dp).padding(top = 18.dp))
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .height(4.dp)
                    .width(118.dp)
                    .background(
                        Brush.horizontalGradient(listOf(Color.Transparent, HarborColors.Gold, Color.Transparent))
                    )
            )
        }
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                .background(HarborColors.Cream)
                .padding(horizontal = 18.dp),
            content = content
        )
        HarborBottomBar(selectedTab, onTab)
    }
}

@Composable
internal fun HarborBottomBar(selected: HarborTab, onSelect: (HarborTab) -> Unit) {
    val tabs = listOf(
        HarborTab.Orders to "Pedidos",
        HarborTab.Offers to "Ofertas",
        HarborTab.Home to "Início",
        HarborTab.Categories to "Categorias",
        HarborTab.Profile to "Perfil",
    )
    // The gradient paints behind the system navigation bar, so the bar always touches
    // the absolute bottom of the screen (no white strip below it).
    Column(Modifier.fillMaxWidth().background(HarborColors.BottomBrush).navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
            tabs.forEach { (tab, label) ->
                val active = tab == selected
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onSelect(tab) }
                        .semantics { contentDescription = label; role = Role.Tab },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    val scale by animateFloatAsState(if (active) 1.15f else 1f, tween(220))
                    Box(
                        Modifier
                            .size(if (active) 31.dp else 26.dp)
                            .graphicsLayer { scaleX = scale; scaleY = scale }
                            .clip(CircleShape)
                            .background(if (active) HarborColors.Sand.copy(alpha = 0.22f) else Color.Transparent),
                        contentAlignment = Alignment.Center
                    ) { HarborTabGlyph(tab, active) }
                    Text(label, Modifier.padding(top = 3.dp), fontSize = 9.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                        color = if (active) HarborColors.Sand else Color(0xFFC995A2), maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun HarborTabGlyph(tab: HarborTab, selected: Boolean) {
    val color = if (selected) HarborColors.Sand else Color(0xFFC995A2)
    Canvas(Modifier.size(19.dp)) {
        val sx = size.width / 24f
        val sy = size.height / 24f
        fun pt(x: Float, y: Float) = Offset(x * sx, y * sy)
        val stroke = Stroke(width = 1.9.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (tab) {
            HarborTab.Orders -> {
                drawRoundRect(color, pt(4f, 7f), androidx.compose.ui.geometry.Size(16f*sx, 13f*sy), androidx.compose.ui.geometry.CornerRadius(1.8f*sx), style = stroke)
                drawLine(color, pt(4f, 10f), pt(20f, 10f), stroke.width)
                drawLine(color, pt(9f, 7f), pt(9f, 10f), stroke.width)
                drawLine(color, pt(15f, 7f), pt(15f, 10f), stroke.width)
            }
            HarborTab.Categories -> {
                drawRoundRect(color, pt(4f, 8f), androidx.compose.ui.geometry.Size(16f*sx, 12f*sy), androidx.compose.ui.geometry.CornerRadius(2f*sx), style = stroke)
                drawArc(color, 195f, 150f, false, pt(8f, 3f), androidx.compose.ui.geometry.Size(8f*sx, 10f*sy), style = stroke)
                drawLine(color, pt(4f, 12f), pt(20f, 12f), stroke.width)
            }
            HarborTab.Home -> {
                val path = Path().apply { moveTo(2f*sx, 11f*sy); lineTo(12f*sx, 3f*sy); lineTo(22f*sx, 11f*sy); moveTo(5f*sx, 10f*sy); lineTo(5f*sx, 21f*sy); lineTo(19f*sx, 21f*sy); lineTo(19f*sx, 10f*sy); moveTo(10f*sx, 21f*sy); lineTo(10f*sx, 15f*sy); lineTo(14f*sx, 15f*sy); lineTo(14f*sx, 21f*sy) }
                drawPath(path, color, style = stroke)
            }
            HarborTab.Offers -> {
                val path = Path().apply {
                    moveTo(12f*sx, 21f*sy); cubicTo(10f*sx, 19f*sy, 3f*sx, 14f*sy, 3f*sx, 9f*sy)
                    cubicTo(3f*sx, 3f*sy, 10f*sx, 2f*sy, 12f*sx, 7f*sy)
                    cubicTo(15f*sx, 2f*sy, 21f*sx, 4f*sy, 21f*sx, 9f*sy)
                    cubicTo(21f*sx, 14f*sy, 14f*sx, 19f*sy, 12f*sx, 21f*sy); close()
                }
                drawPath(path, color, style = stroke)
            }
            HarborTab.Profile -> {
                drawCircle(color, 4f*sx, pt(12f, 7f), style = stroke)
                val path = Path().apply { moveTo(4f*sx, 21f*sy); cubicTo(4f*sx, 16f*sy, 8f*sx, 14f*sy, 12f*sx, 14f*sy); cubicTo(16f*sx, 14f*sy, 20f*sx, 16f*sy, 20f*sx, 21f*sy) }
                drawPath(path, color, style = stroke)
            }
        }
    }
}

@Composable
internal fun HarborAuthScreen(
    registration: Boolean,
    name: String,
    onName: (String) -> Unit,
    email: String,
    onEmail: (String) -> Unit,
    password: String,
    onPassword: (String) -> Unit,
    confirmPassword: String,
    onConfirmPassword: (String) -> Unit,
    consent: Boolean,
    onConsent: (Boolean) -> Unit,
    busy: Boolean,
    message: String,
    onSubmit: () -> Unit,
    onRegister: () -> Unit,
    onLogin: () -> Unit,
) {
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(HarborColors.HeaderBrush)
            .statusBarsPadding()
            .imePadding()
    ) {
        Canvas(Modifier.matchParentSize()) {
            drawCircle(HarborColors.Gold.copy(alpha = 0.10f), radius = size.width * .55f, center = Offset(size.width * 1.1f, size.height * .12f))
            drawCircle(HarborColors.Sand.copy(alpha = 0.08f), radius = size.width * .45f, center = Offset(size.width * -.15f, size.height * .65f))
        }
        val formTop = if (maxHeight < 740.dp) 185.dp else 213.dp
        HarborThemeToggle(Modifier.align(Alignment.TopEnd).padding(top = 18.dp, end = 18.dp))
        HarborBrandLogo(Modifier.align(Alignment.TopCenter).padding(top = 74.dp).width(148.dp).height(59.dp))
        Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(horizontal = 28.dp).padding(top = formTop)) {
            Text(if (registration) "Cadastre-se" else "Login", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = HarborColors.Sand, fontFamily = FontFamily.Serif)
            Spacer(Modifier.height(14.dp))
            if (registration) {
                HarborAuthField("Nome Completo", name, onName, contentDescription = "Nome completo")
                Spacer(Modifier.height(11.dp))
            }
            HarborAuthField("Email", email, onEmail, KeyboardType.Email, contentDescription = "E-mail")
            Spacer(Modifier.height(11.dp))
            HarborAuthField("Senha", password, onPassword, KeyboardType.Password, secret = true, contentDescription = "Senha")
            if (registration) {
                Spacer(Modifier.height(11.dp))
                HarborAuthField("Confirmar Senha", confirmPassword, onConfirmPassword, KeyboardType.Password, secret = true, contentDescription = "Confirmar senha")
                Row(
                    Modifier.fillMaxWidth().padding(top = 3.dp, bottom = 1.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = consent,
                        onCheckedChange = onConsent,
                        modifier = Modifier
                            .size(29.dp)
                            .clip(CircleShape),
                        colors = CheckboxDefaults.colors(checkedColor = HarborColors.Sand, uncheckedColor = HarborColors.Sand)
                    )
                    Text("Aceito a Política de Privacidade", color = HarborColors.Sand, fontSize = 12.sp)
                }
            }
            if (message.isNotBlank()) {
                Card(
                    Modifier.fillMaxWidth().padding(top = 10.dp),
                    shape = RoundedCornerShape(13.dp),
                    colors = CardDefaults.cardColors(containerColor = HarborColors.WineDark.copy(alpha = 0.5f))
                ) {
                    Text(message, Modifier.padding(horizontal = 13.dp, vertical = 10.dp), color = HarborColors.Sand, fontSize = 12.sp, lineHeight = 15.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
            HarborPrimaryButton(if (registration) "Criar Conta" else "Login", busy, onSubmit)
            TextButton(
                onClick = if (registration) onLogin else onRegister,
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                contentPadding = PaddingValues(vertical = 3.dp)
            ) {
                Text(
                    if (registration) "Já tem Conta? Entrar" else "Esqueci a Senha  /  Criar Conta",
                    color = Color(0xFFE5B7C3), fontSize = 12.sp,
                    textDecoration = TextDecoration.Underline
                )
            }
        }
        Image(
            painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = "Símbolo Harbor, livro e farol",
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp).size(40.dp).alpha(0.9f)
        )
        if (busy) LinearProgressIndicator(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp),
            color = HarborColors.Sand, trackColor = HarborColors.WineDark
        )
    }
}

@Composable
private fun HarborAuthField(
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboard: KeyboardType = KeyboardType.Text,
    secret: Boolean = false,
    contentDescription: String,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth().height(46.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(HarborColors.Field)
            .border(1.dp, HarborColors.RoseLine.copy(alpha = 0.7f), RoundedCornerShape(15.dp))
            .semantics { this.contentDescription = contentDescription },
        singleLine = true,
        textStyle = TextStyle(fontSize = 13.sp, color = HarborColors.Ink, fontFamily = FontFamily.SansSerif),
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        decorationBox = { inner ->
            Row(Modifier.fillMaxSize().padding(horizontal = 15.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text(placeholder, color = HarborColors.Muted, fontSize = 13.sp)
                    inner()
                }
            }
        }
    )
}

@Composable
internal fun HarborOnboardingProfile(
    name: String,
    onName: (String) -> Unit,
    email: String,
    address: String,
    onAddress: (String) -> Unit,
    consent: Boolean,
    onConsent: (Boolean) -> Unit,
    busy: Boolean,
    message: String,
    onSave: () -> Unit,
    onBack: () -> Unit,
    onTab: (HarborTab) -> Unit,
) {
    HarborDashboardFrame(content = {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 16.dp, bottom = 14.dp)) {
            HarborScreenTitle("Informações pessoais")
            ModernCard {
                Column(Modifier.padding(17.dp)) {
                    HarborSectionLabel("Seus dados")
                    HarborLightField("Nome completo", name, onName)
                    HarborLightField("E-mail", email, {}, enabled = false)
                    HarborLightField("Endereço de entrega", address, onAddress)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(consent, onConsent, colors = CheckboxDefaults.colors(checkedColor = HarborColors.Wine, uncheckedColor = HarborColors.Wine))
                        Text("Li e aceito a Política de Privacidade", fontSize = 11.sp, color = HarborColors.Wine)
                    }
                    if (message.isNotBlank()) ModernNotice(message)
                    HarborPrimaryButton("Salvar informações", busy, onSave)
                    TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Voltar", color = HarborColors.Wine, fontSize = 12.sp) }
                }
            }
        }
    }, selectedTab = HarborTab.Profile, onTab = onTab)
}

@Composable
internal fun ModernCard(
    modifier: Modifier = Modifier,
    container: Color = HarborColors.Paper,
    corner: Dp = 20.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(corner)
    val interaction = if (onClick != null) {
        Card(
            modifier.fillMaxWidth().shadow(5.dp, shape, ambientColor = Color(0x1A630626), spotColor = Color(0x26630626))
                .clip(shape)
                .border(1.dp, HarborColors.RoseLine.copy(alpha = 0.45f), shape)
                .clickable { onClick() },
            colors = CardDefaults.cardColors(containerColor = container),
            content = content
        )
    } else {
        Card(
            modifier.fillMaxWidth().shadow(5.dp, shape, ambientColor = Color(0x1A630626), spotColor = Color(0x26630626))
                .clip(shape)
                .border(1.dp, HarborColors.RoseLine.copy(alpha = 0.45f), shape),
            colors = CardDefaults.cardColors(containerColor = container),
            content = content
        )
    }
    interaction
}

@Composable
internal fun ModernNotice(text: String, tone: Color = HarborColors.Wine) {
    Card(
        Modifier
            .fillMaxWidth()
            .padding(top = 9.dp, bottom = 3.dp)
            .clip(RoundedCornerShape(13.dp)),
        shape = RoundedCornerShape(13.dp),
        colors = CardDefaults.cardColors(containerColor = tone.copy(alpha = 0.09f))
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(tone))
            Text(text, Modifier.padding(start = 9.dp), color = tone, fontSize = 11.sp, lineHeight = 14.sp)
        }
    }
}

@Composable
internal fun HarborScreenTitle(title: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(top = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, textAlign = TextAlign.Center, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 21.sp, color = HarborColors.Wine)
        Box(
            Modifier
                .padding(top = 7.dp)
                .width(56.dp)
                .height(3.dp)
                .clip(CircleShape)
                .background(Brush.horizontalGradient(listOf(Color.Transparent, HarborColors.Gold, Color.Transparent)))
        )
    }
}

@Composable
internal fun HarborSectionLabel(title: String, modifier: Modifier = Modifier) {
    Text(title, modifier.padding(bottom = 8.dp), fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = HarborColors.Wine, letterSpacing = 0.2.sp)
}

@Composable
internal fun HarborLightField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    keyboard: KeyboardType = KeyboardType.Text,
) {
    Column(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        Text(label, Modifier.padding(start = 5.dp, bottom = 4.dp), color = HarborColors.Muted, fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp)
        BasicTextField(
            value, onValueChange,
            Modifier.fillMaxWidth().defaultMinSize(minHeight = 44.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(if (enabled) HarborColors.Field else HarborColors.SurfaceWarm.copy(alpha = 0.55f))
                .border(1.dp, HarborColors.RoseLine.copy(alpha = if (enabled) 0.65f else 0.25f), RoundedCornerShape(13.dp))
                .padding(horizontal = 13.dp, vertical = 12.dp)
                .semantics { contentDescription = label },
            enabled = enabled,
            singleLine = singleLine,
            textStyle = TextStyle(fontSize = 13.sp, color = HarborColors.Ink, fontFamily = FontFamily.SansSerif),
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            decorationBox = { inner -> Box { if (value.isEmpty()) Text(label, color = HarborColors.Muted, fontSize = 12.sp); inner() } }
        )
    }
}

@Composable
internal fun HarborPrimaryButton(text: String, busy: Boolean = false, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
            .shadow(7.dp, RoundedCornerShape(24.dp), ambientColor = Color(0x33630626), spotColor = Color(0x40630626))
            .harborPressScale(interaction),
        enabled = !busy,
        interactionSource = interaction,
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = HarborColors.WineSolid,
            disabledContainerColor = HarborColors.WineSolid.copy(alpha = 0.55f),
            contentColor = HarborColors.Sand,
            disabledContentColor = HarborColors.Sand.copy(alpha = 0.7f)
        ),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 1, letterSpacing = 0.3.sp)
    }
}

@Composable
internal fun HarborDashboard(
    name: String,
    books: List<LivroUi>,
    activeBox: CaixaUi?,
    onSlideAction: (Int) -> Unit,
    onBook: (LivroUi) -> Unit,
    selectedTab: HarborTab,
    onTab: (HarborTab) -> Unit,
) {
    val pager = rememberPagerState(initialPage = 0, pageCount = { 6 })
    val capas = remember { listOf(R.drawable.capa_bussola_de_ouro, R.drawable.capa_o_exterminador, R.drawable.capa_atlantida, R.drawable.capa_jogos_vorazes) }
    val rotulos = remember { listOf("Jan 2026", "Dez 2025", "Nov 2025", "Out 2025", "Set 2025", "Ago 2025", "Jul 2025", "Jun 2025") }
    val edicoes = remember(books) {
        (0 until 8).map { i ->
            EdicaoUi(
                capa = capas[i % capas.size],
                titulo = books.getOrNull(i)?.titulo ?: "Edição ${String.format("%02d", i + 1)}",
                rotulo = rotulos[i]
            )
        }
    }
    HarborDashboardFrame(content = {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 18.dp, bottom = 10.dp)) {
            GreetingCard(name)
            Spacer(Modifier.height(19.dp))
            HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth().height(179.dp), pageSpacing = 12.dp) { page ->
                DashboardSlide(page, activeBox, onSlideAction)
            }
            Row(Modifier.fillMaxWidth().height(24.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                repeat(6) { index ->
                    val active = pager.currentPage == index
                    val width by animateDpAsState(if (active) 21.dp else 7.dp, tween(220))
                    Box(
                        Modifier
                            .padding(horizontal = 3.dp)
                            .size(width = width, height = 7.dp)
                            .clip(CircleShape)
                            .background(if (active) HarborColors.Gold else HarborColors.Wine.copy(alpha = 0.35f))
                    )
                }
            }
            Text("Edições Passadas!", Modifier.fillMaxWidth().padding(start = 8.dp, top = 8.dp, bottom = 11.dp), fontSize = 17.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif, color = HarborColors.Wine)
            EdicoesCarousel(edicoes) { index -> books.getOrNull(index)?.let(onBook) }
        }
    }, selectedTab = selectedTab, onTab = onTab)
}

/** Carrossel horizontal das edições anteriores, com avanço automático e indicador. */
@Composable
private fun EdicoesCarousel(edicoes: List<EdicaoUi>, onSelect: (Int) -> Unit) {
    if (edicoes.isEmpty()) return
    val items = remember(edicoes) { edicoes + edicoes }
    val listState = rememberLazyListState()
    LaunchedEffect(listState, edicoes.size) {
        while (true) {
            delay(3000)
            if (listState.isScrollInProgress) continue
            val next = listState.firstVisibleItemIndex + 1
            if (next >= edicoes.size) listState.scrollToItem(next - edicoes.size)
            else listState.animateScrollToItem(next)
        }
    }
    Column(Modifier.fillMaxWidth()) {
        LazyRow(
            state = listState,
            flingBehavior = rememberSnapFlingBehavior(listState),
            modifier = Modifier.fillMaxWidth().height(168.dp),
            contentPadding = PaddingValues(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            itemsIndexed(items, key = { index, _ -> index }) { _, edicao ->
                EdicaoItem(edicao) { onSelect(items.indexOf(edicao)) }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 9.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(edicoes.size) { index ->
                val active = (listState.firstVisibleItemIndex % edicoes.size) == index
                val width by animateDpAsState(if (active) 18.dp else 6.dp, tween(240))
                Box(
                    Modifier
                        .padding(horizontal = 3.dp)
                        .size(width = width, height = 6.dp)
                        .clip(CircleShape)
                        .background(if (active) HarborColors.Gold else HarborColors.Wine.copy(alpha = 0.35f))
                )
            }
        }
    }
}

@Composable
private fun EdicaoItem(edicao: EdicaoUi, onClick: () -> Unit) {
    Column(
        Modifier
            .width(112.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(HarborColors.SurfaceWarm)
            .border(1.dp, HarborColors.RoseLine.copy(alpha = 0.6f), RoundedCornerShape(17.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(84.dp).shadow(5.dp, CircleShape, ambientColor = Color(0x2B630626), spotColor = Color(0x40630626)).clip(CircleShape).background(HarborColors.Wine),
            contentAlignment = Alignment.Center
        ) {
            Image(painterResource(edicao.capa), "Capa de ${edicao.titulo}", Modifier.width(64.dp).height(92.dp), contentScale = ContentScale.FillBounds)
        }
        Spacer(Modifier.height(7.dp))
        Text(edicao.rotulo, fontSize = 9.sp, letterSpacing = 0.4.sp, fontWeight = FontWeight.Bold, color = HarborColors.Gold)
        Text(
            edicao.titulo,
            Modifier.padding(top = 2.dp),
            fontSize = 10.sp, lineHeight = 12.sp, color = HarborColors.Wine,
            textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun GreetingCard(name: String) {
    Row(
        Modifier.fillMaxWidth().height(122.dp).harborEntrance().shadow(6.dp, RoundedCornerShape(22.dp), ambientColor = Color(0x2B630626), spotColor = Color(0x33630626))
            .clip(RoundedCornerShape(22.dp))
            .background(HarborColors.SurfaceWarm)
            .border(1.dp, HarborColors.RoseLine.copy(alpha = 0.5f), RoundedCornerShape(22.dp))
            .padding(start = 27.dp, end = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("Olá, ${name.substringBefore(' ').ifBlank { "Nome do Leitor!" }}!", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = HarborColors.Wine, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Pronto para sua próxima história?", Modifier.padding(top = 5.dp), fontSize = 13.sp, lineHeight = 17.sp, color = HarborColors.Ink)
        }
        Image(painterResource(R.drawable.ilustracao_livro_farol), "Livro aberto com farol", Modifier.width(103.dp).height(110.dp), contentScale = ContentScale.Fit)
    }
}

@Composable
private fun DashboardSlide(page: Int, box: CaixaUi?, onAction: (Int) -> Unit) {
    when (page) {
        0 -> Card(
            Modifier.fillMaxSize().shadow(7.dp, RoundedCornerShape(22.dp), ambientColor = Color(0x2B630626), spotColor = Color(0x40630626)),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = HarborColors.WineSolid)
        ) {
            Column(Modifier.fillMaxSize().padding(horizontal = 15.dp, vertical = 11.dp)) {
                Text("A Caixa desse mês", Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif, color = HarborColors.Sand)
                Row(Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Image(painterResource(R.drawable.capa_jogos_vorazes), "Capa do livro do ciclo", Modifier.width(82.dp).fillMaxHeight().padding(vertical = 2.dp).clip(RoundedCornerShape(9.dp)))
                    Column(Modifier.weight(1f).padding(start = 13.dp)) {
                        Text(box?.livro?.titulo ?: "JOGOS VORAZES", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White, lineHeight = 16.sp)
                        Text(box?.livro?.sinopse ?: "Texto explicativo sobre a caixa do cliente, uma pequena sinopse do conteúdo.", Modifier.padding(top = 5.dp), fontSize = 12.sp, lineHeight = 15.sp, color = Color(0xFFEFD4DA))
                        Button(onClick = { onAction(0) }, Modifier.padding(top = 8.dp).fillMaxWidth(.72f).height(30.dp), contentPadding = PaddingValues(0.dp), shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.buttonColors(containerColor = HarborColors.Sand, contentColor = HarborColors.Wine)) { Text("Saiba Mais!", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
        1 -> Card(
            Modifier.fillMaxSize().shadow(7.dp, RoundedCornerShape(22.dp), ambientColor = Color(0x2B630626), spotColor = Color(0x40630626)),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = HarborColors.WineSolid)
        ) {
            Box(Modifier.fillMaxSize()) {
                Image(painterResource(R.drawable.promo_shelf), "Estante de livros clássicos", Modifier.fillMaxWidth().height(106.dp).align(Alignment.TopCenter), contentScale = ContentScale.Crop)
                Box(Modifier.fillMaxWidth().height(72.dp).align(Alignment.BottomCenter).background(HarborColors.Wine), contentAlignment = Alignment.Center) {
                    Button(onClick = { onAction(1) }, Modifier.fillMaxWidth(.78f).height(42.dp), shape = RoundedCornerShape(22.dp), colors = ButtonDefaults.buttonColors(containerColor = HarborColors.Sand, contentColor = HarborColors.Wine)) { Text("Conheça outros gêneros", fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
        2 -> Card(
            Modifier.fillMaxSize().shadow(7.dp, RoundedCornerShape(22.dp), ambientColor = Color(0x2B630626), spotColor = Color(0x40630626)),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = HarborColors.WineSolid)
        ) {
            Image(painterResource(R.drawable.promo_offers_art), "Ilustração das ofertas limitadas", Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 8.dp), contentScale = ContentScale.Fit)
            Box(Modifier.fillMaxSize().clickable { onAction(2) }.semantics { contentDescription = "Abrir ofertas limitadas" })
        }
        3 -> Card(
            Modifier.fillMaxSize().shadow(7.dp, RoundedCornerShape(22.dp), ambientColor = Color(0x2B630626), spotColor = Color(0x33C79A4B)),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = HarborColors.SurfaceWarm)
        ) {
            Row(Modifier.fillMaxSize().clickable { onAction(3) }.padding(start = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Clube da leitura", fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Bold, fontSize = 21.sp, color = HarborColors.Wine, textAlign = TextAlign.Center)
                    Button(onClick = { onAction(3) }, Modifier.padding(top = 8.dp).width(165.dp).height(34.dp), shape = RoundedCornerShape(20.dp), colors = ButtonDefaults.buttonColors(containerColor = HarborColors.WineSolid, contentColor = HarborColors.Sand)) { Text("Faça Parte", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                    Text("●   ◎   ♪", Modifier.padding(top = 8.dp), color = HarborColors.Wine, fontSize = 13.sp)
                }
                Image(painterResource(R.drawable.promo_club_books), "Estante colorida da comunidade de leitura", Modifier.width(125.dp).fillMaxHeight(), contentScale = ContentScale.Crop)
            }
        }
        5 -> Card(
            Modifier.fillMaxSize().shadow(7.dp, RoundedCornerShape(22.dp), ambientColor = Color(0x2B630626), spotColor = Color(0x40630626)),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = HarborColors.WineSolid)
        ) {
            Row(Modifier.fillMaxSize().clickable { onAction(5) }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painterResource(R.drawable.community_qr),
                    "QR Code do servidor Harbor no Discord",
                    Modifier.size(92.dp).clip(RoundedCornerShape(13.dp)).shadow(6.dp, RoundedCornerShape(13.dp), ambientColor = Color(0x2B630626), spotColor = Color(0x40630626)),
                    contentScale = ContentScale.Fit
                )
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text("Comunidade Harbor", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = HarborColors.Sand, maxLines = 1)
                    Text("Escaneie o QR e entre no Clube da Leitura no Discord.", Modifier.padding(top = 5.dp), fontSize = 12.sp, lineHeight = 15.sp, color = Color(0xFFEFD4DA), maxLines = 3)
                    Button(onClick = { onAction(5) }, Modifier.padding(top = 9.dp).width(150.dp).height(32.dp), contentPadding = PaddingValues(0.dp), shape = RoundedCornerShape(19.dp), colors = ButtonDefaults.buttonColors(containerColor = HarborColors.Sand, contentColor = HarborColors.Wine)) {
                        Text("Entrar no Discord", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
        }
        else -> Card(
            Modifier.fillMaxSize().shadow(7.dp, RoundedCornerShape(22.dp), ambientColor = Color(0x2B630626), spotColor = Color(0x40630626)),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = HarborColors.WineSolid)
        ) {
            Box(Modifier.fillMaxSize()) {
                Canvas(Modifier.fillMaxSize()) {
                    val dash = Path().apply { moveTo(size.width * .30f, size.height * .33f); cubicTo(size.width * .42f, size.height * .10f, size.width * .60f, size.height * .16f, size.width * .68f, size.height * .31f) }
                    drawPath(dash, HarborColors.Sand, style = Stroke(1.8.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))))
                    drawLine(HarborColors.Sand, Offset(size.width*.31f, size.height*.33f), Offset(size.width*.35f, size.height*.25f), 2.dp.toPx())
                    drawLine(HarborColors.Sand, Offset(size.width*.31f, size.height*.33f), Offset(size.width*.23f, size.height*.30f), 2.dp.toPx())
                }
                Image(painterResource(R.drawable.promo_tracking_art), "Caixa e livros prontos para rastreamento", Modifier.width(135.dp).height(121.dp).align(Alignment.CenterEnd).padding(end = 6.dp), contentScale = ContentScale.Fit)
                Button(onClick = { onAction(4) }, Modifier.align(Alignment.CenterStart).padding(start = 23.dp).width(195.dp).height(41.dp), shape = RoundedCornerShape(22.dp), colors = ButtonDefaults.buttonColors(containerColor = HarborColors.Sand, contentColor = HarborColors.Wine)) { Text("Rastreie sua caixa", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
internal fun HarborGenreScreen(
    selected: Set<String>,
    onToggle: (String) -> Unit,
    message: String,
    busy: Boolean,
    onSave: () -> Unit,
    selectedTab: HarborTab,
    onTab: (HarborTab) -> Unit,
) {
    val genres = listOf(
        "Romance" to R.drawable.genero_romance,
        "Fantasia" to R.drawable.genero_fantasia,
        "Suspense" to R.drawable.genero_suspense,
        "Terror" to R.drawable.genero_terror,
        "Ficção Científica" to R.drawable.genero_ciencia,
        "Aventura" to R.drawable.genero_aventura,
    )
    HarborDashboardFrame(content = {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 16.dp, bottom = 14.dp)) {
            HarborScreenTitle("Categorias Literárias")
            ModernCard {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 15.dp)) {
                    Text("Quais mundos você quer explorar?", Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = HarborColors.Wine)
                    Text("Selecione de 1 a 3 gêneros literários para direcionar a curadoria da sua assinatura.", Modifier.fillMaxWidth().padding(top = 7.dp, bottom = 16.dp, start = 8.dp, end = 8.dp), textAlign = TextAlign.Center, fontSize = 12.sp, lineHeight = 15.sp, color = HarborColors.Muted)
                    genres.chunked(3).forEachIndexed { rowIndex, row ->
                        Row(Modifier.fillMaxWidth().padding(bottom = if (rowIndex == 0) 13.dp else 0.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            row.forEachIndexed { colIndex, (name, asset) ->
                                val active = name in selected
                                val scale by animateFloatAsState(if (active) 1.03f else 1f, tween(180))
                                Card(
                                    Modifier
                                        .harborEntrance(rowIndex * 90 + colIndex * 45)
                                        .graphicsLayer { scaleX = scale; scaleY = scale }
                                        .weight(1f)
                                        .height(137.dp)
                                        .clickable { onToggle(name) }
                                        .semantics { contentDescription = "$name${if (active) ", selecionado" else ", não selecionado"}"; role = Role.Checkbox; this.selected = active },
                                    shape = RoundedCornerShape(18.dp),
                                    colors = CardDefaults.cardColors(containerColor = if (active) HarborColors.WineSolid else HarborColors.SurfaceWarm),
                                    elevation = CardDefaults.cardElevation(if (active) 6.dp else 3.dp)
                                ) {
                                    Box(
                                        Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 7.dp)
                                            .then(if (active) Modifier.border(2.dp, HarborColors.GoldSoft, RoundedCornerShape(18.dp)) else Modifier)
                                    ) {
                                        Image(painterResource(asset), "Ilustração do gênero $name", Modifier.align(Alignment.TopCenter).size(79.dp), contentScale = ContentScale.Fit)
                                        if (active) {
                                            Box(Modifier.align(Alignment.Center).offset(y = (-11).dp).size(24.dp).shadow(3.dp, CircleShape).clip(CircleShape).background(HarborColors.Sand), contentAlignment = Alignment.Center) {
                                                Text(if (name in setOf("Fantasia", "Suspense")) "◉" else "✓", color = HarborColors.WineOnLight, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                        Text(name, Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 5.dp), textAlign = TextAlign.Center, fontSize = 11.sp, lineHeight = 12.sp, fontWeight = FontWeight.SemiBold, color = if (active) HarborColors.Sand else HarborColors.Wine, maxLines = 2)
                                    }
                                }
                            }
                        }
                    }
                    Text("${selected.size}/3 selecionados", Modifier.fillMaxWidth().padding(top = 11.dp, bottom = 5.dp), textAlign = TextAlign.End, color = HarborColors.Muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            if (message.isNotBlank()) ModernNotice(message)
            Spacer(Modifier.height(11.dp))
            HarborPrimaryButton("Salvar Preferências", busy, onSave)
        }
    }, selectedTab = selectedTab, onTab = onTab)
}

@Composable
internal fun HarborPlansScreen(
    plans: List<PlanoReply>,
    selectedId: Int?,
    onSelect: (PlanoReply) -> Unit,
    changing: Boolean,
    busy: Boolean,
    onContinue: () -> Unit,
    selectedTab: HarborTab,
    onTab: (HarborTab) -> Unit,
) {
    HarborDashboardFrame(content = {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 16.dp, bottom = 10.dp)) {
            HarborScreenTitle("Plano de Assinatura")
            val sorted = plans.sortedBy { when (it.periodicidade.lowercase()) { "anual" -> 0; "trimestral" -> 1; else -> 2 } }
            sorted.forEachIndexed { index, plan ->
                val selected = plan.id == selectedId
                val primary = index == 0
                Box(Modifier.harborEntrance(index * 80)) {
                    PlanOption(plan, selected, primary) { onSelect(plan) }
                }
            }
            Text(
                "Todos os planos incluem: 1 Caixa Mensal Surpresa com livro e brindes exclusivos, acesso VIP à comunidade e rastreio detalhado em tempo real.",
                Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 4.dp),
                fontSize = 11.sp, lineHeight = 14.sp, color = HarborColors.Muted, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(7.dp))
            HarborPrimaryButton(if (changing) "Confirmar alteração" else "Continuar com o Plano", busy, onContinue)
        }
    }, selectedTab = selectedTab, onTab = onTab)
}

@Composable
private fun PlanOption(plan: PlanoReply, selected: Boolean, recommended: Boolean, onClick: () -> Unit) {
    val monthly = plan.preco
    val shape = RoundedCornerShape(19.dp)
    val borderColor = if (selected) HarborColors.Gold else HarborColors.RoseLine.copy(alpha = 0.6f)
    Card(
        Modifier.fillMaxWidth()
            .padding(top = if (recommended) 14.dp else 10.dp, bottom = 2.dp)
            .shadow(if (selected) 8.dp else 4.dp, shape, ambientColor = Color(0x2B630626), spotColor = if (selected) Color(0x40630626) else Color(0x2B630626))
            .clip(shape)
            .background(if (selected) HarborColors.WineSolid else HarborColors.SurfaceWarm)
            .border(if (selected) 2.dp else 1.dp, borderColor, shape)
            .clickable { onClick() },
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = if (selected) HarborColors.WineSolid else HarborColors.SurfaceWarm),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 15.dp).height(if (recommended) 154.dp else 61.dp)) {
            if (selected) Box(Modifier.align(Alignment.TopStart).offset(x = (-5).dp, y = (-2).dp).size(22.dp).shadow(3.dp, CircleShape).clip(CircleShape).background(HarborColors.Sand), contentAlignment = Alignment.Center) { Text("✓", color = HarborColors.WineOnLight, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
            Column(Modifier.fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = if (recommended) Arrangement.Top else Arrangement.Center) {
                Text(plan.nome, Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontFamily = FontFamily.Serif, fontSize = if (recommended) 17.sp else 14.sp, fontWeight = FontWeight.Bold, color = if (selected) HarborColors.Sand else HarborColors.Wine)
                Text("R$ ${"%.2f".format(monthly)} / mês", Modifier.padding(top = if (recommended) 3.dp else 0.dp), fontSize = if (recommended) 15.sp else 14.sp, fontWeight = FontWeight.Bold, color = if (selected) HarborColors.Sand else HarborColors.Wine)
                if (recommended) {
                    listOf("Livro Surpresa Todo Mês", "Brindes Exclusivos", "Acesso VIP ao Discord", "Rastreio Detalhado").forEach {
                        Text("⊙  $it", Modifier.fillMaxWidth().padding(start = 27.dp, top = 1.dp), fontSize = 10.sp, lineHeight = 12.sp, color = HarborColors.Sand)
                    }
                }
            }
            if (recommended) Box(Modifier.align(Alignment.CenterEnd).offset(x = 2.dp, y = (-15).dp).size(41.dp).shadow(4.dp, CircleShape).clip(CircleShape).background(Color(0xFFE7C969)), contentAlignment = Alignment.Center) { Text("Mais\nVendido", color = HarborColors.WineOnLight, fontSize = 8.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, lineHeight = 9.sp) }
            if (!recommended) Text(if (plan.periodicidade.equals("Trimestral", true)) "✉" else "✉", Modifier.align(Alignment.CenterEnd), color = HarborColors.Wine, fontSize = 18.sp)
        }
    }
}

@Composable
internal fun HarborProfileScreen(
    name: String,
    onName: (String) -> Unit,
    email: String,
    address: String,
    onAddress: (String) -> Unit,
    genres: Set<String>,
    subscriptionStatus: String,
    nextRenewal: String,
    busy: Boolean,
    message: String,
    onSave: () -> Unit,
    onGenres: () -> Unit,
    onPlan: () -> Unit,
    onCancel: () -> Unit,
    onOrders: () -> Unit,
    onDelete: () -> Unit,
    onLogout: () -> Unit,
    selectedTab: HarborTab,
    onTab: (HarborTab) -> Unit,
) {
    HarborDashboardFrame(content = {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 16.dp, bottom = 10.dp)) {
            HarborScreenTitle("Informações pessoais")
            ModernCard {
                Column(Modifier.padding(15.dp)) {
                    HarborSectionLabel("Dados da conta")
                    HarborLightField("Nome Completo", name, onName)
                    HarborLightField("E-mail", email, {}, enabled = false)
                    HarborLightField("Endereço", address, onAddress)
                    if (message.isNotBlank()) ModernNotice(message)
                    HarborPrimaryButton("Salvar Alterações", busy, onSave)
                }
            }
            ModernCard {
                Column(Modifier.padding(15.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        HarborSectionLabel("Minha assinatura", Modifier.weight(1f))
                        StatusChip(subscriptionStatus)
                    }
                    if (nextRenewal.isNotBlank()) Text("Próxima renovação: $nextRenewal", Modifier.padding(top = 2.dp, bottom = 4.dp), color = HarborColors.Muted, fontSize = 11.sp)
                    ModernActionRow("Planos e condições", "Cobrança mensal, trimestral ou anual", "›", onPlan)
                    if (subscriptionStatus.equals("Ativa", true)) ModernActionRow("Cancelar assinatura", "Deixa de renovar na próxima cobrança", "›", onCancel)
                }
            }
            HarborAppearanceCard(Modifier.padding(top = 11.dp))
            ModernCard(modifier = Modifier.padding(top = 11.dp)) {
                Column(Modifier.padding(15.dp)) {
                    HarborSectionLabel("Atalhos rápidos")
                    ProfileAction("Categorias Literárias", genres.joinToString().ifBlank { "Escolha de 1 a 3 gêneros" }, R.drawable.nav_genres, onGenres)
                    ProfileAction("Histórico de Pedidos", "Consulte suas caixas e pagamentos", R.drawable.nav_orders, onOrders)
                    ProfileAction("Livro de Endereços", address.ifBlank { "Adicione seu endereço de entrega" }, R.drawable.nav_orders, onOrders)
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Button(
                    onClick = onDelete,
                    Modifier.weight(1f).height(42.dp),
                    shape = RoundedCornerShape(21.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = HarborColors.Wine.copy(alpha = 0.12f), contentColor = HarborColors.Wine)
                ) { Text("Excluir conta", fontSize = 11.sp, fontWeight = FontWeight.Medium) }
                Button(
                    onClick = onLogout,
                    Modifier.weight(1f).height(42.dp),
                    shape = RoundedCornerShape(21.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = HarborColors.Wine.copy(alpha = 0.1f), contentColor = HarborColors.Wine)
                ) { Text("Sair da conta", fontSize = 11.sp, fontWeight = FontWeight.Medium) }
            }
        }
    }, selectedTab = selectedTab, onTab = onTab)
}

@Composable
internal fun ModernActionRow(title: String, subtitle: String, glyph: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(14.dp)).background(HarborColors.SurfaceWarm).clickable(onClick = onClick).padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = HarborColors.Wine, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = HarborColors.Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(glyph, color = HarborColors.Gold, fontSize = 20.sp)
    }
}

@Composable
internal fun StatusChip(status: String) {
    val tint = when {
        status.equals("Ativa", true) -> HarborColors.Mint
        status.equals("Pendente", true) -> HarborColors.Amber
        else -> HarborColors.Wine
    }
    val bg = tint.copy(alpha = if (HarborColors.isDark) 0.2f else 0.12f)
    Box(
        Modifier.clip(CircleShape).background(bg).padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(status, color = tint, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.3.sp)
    }
}

@Composable
private fun ProfileAction(title: String, subtitle: String, icon: Int, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(HarborColors.SurfaceWarm)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(painterResource(icon), null, Modifier.size(23.dp), colorFilter = ColorFilter.tint(HarborColors.Wine))
        Column(Modifier.weight(1f).padding(start = 11.dp)) {
            Text(title, color = HarborColors.Wine, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = HarborColors.Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("›", color = HarborColors.Gold, fontSize = 20.sp)
    }
}

@Composable
internal fun HarborOffersScreen(
    books: List<LivroUi>,
    cart: Map<Int, Int>,
    onAdd: (LivroUi) -> Unit,
    onCheckout: () -> Unit,
    onBook: (LivroUi) -> Unit,
    selectedTab: HarborTab,
    onTab: (HarborTab) -> Unit,
) {
    HarborDashboardFrame(content = {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 16.dp, bottom = 12.dp)) {
            HarborScreenTitle("Saldão das Caixas Passadas")
            Text("Edições Limitadas & Promoções Exclusivas", Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 10.dp), textAlign = TextAlign.Center, fontSize = 11.sp, color = HarborColors.Muted, letterSpacing = 0.3.sp)
            Card(
                Modifier.fillMaxWidth().height(34.dp).shadow(3.dp, RoundedCornerShape(17.dp), ambientColor = Color(0x2BC79A4B), spotColor = Color(0x33C79A4B)).clip(RoundedCornerShape(17.dp)),
                shape = RoundedCornerShape(17.dp),
                colors = CardDefaults.cardColors(containerColor = HarborColors.SandDeep.copy(alpha = 0.35f))
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Estoque Limitado. Garanta o Seu!", color = HarborColors.Wine, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.3.sp) }
            }
            val catalog = if (books.isEmpty()) emptyList() else books
            catalog.chunked(2).forEachIndexed { rowIndex, row ->
                Row(
                    Modifier.fillMaxWidth().padding(top = 11.dp).height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(11.dp)
                ) {
                    row.forEach { book ->
                        Card(
                            Modifier.weight(1f).fillMaxHeight().harborEntrance(rowIndex * 70 + row.indexOf(book) * 55).shadow(5.dp, RoundedCornerShape(19.dp), ambientColor = Color(0x1A630626), spotColor = Color(0x2B630626))
                                .clip(RoundedCornerShape(19.dp))
                                .border(1.dp, HarborColors.RoseLine.copy(alpha = 0.5f), RoundedCornerShape(19.dp))
                                .clickable { onAdd(book) },
                            shape = RoundedCornerShape(19.dp),
                            colors = CardDefaults.cardColors(containerColor = HarborColors.SurfaceWarm),
                            elevation = CardDefaults.cardElevation(0.dp)
                        ) {
                            Column(Modifier.fillMaxWidth().padding(9.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                    Image(painterResource(R.drawable.capa_atlantida), null, Modifier.width(46.dp).height(55.dp).clip(RoundedCornerShape(8.dp)).shadow(3.dp, RoundedCornerShape(8.dp)))
                                    Spacer(Modifier.width(7.dp))
                                    Image(painterResource(R.drawable.capa_jogos_vorazes), null, Modifier.width(46.dp).height(55.dp).clip(RoundedCornerShape(8.dp)).shadow(3.dp, RoundedCornerShape(8.dp)))
                                }
                                Text("Caixa ${String.format("%02d", rowIndex * 2 + row.indexOf(book) + 1)} • Edição limitada", Modifier.padding(top = 6.dp), fontSize = 10.sp, lineHeight = 13.sp, color = HarborColors.Wine, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2)
                                Text(book.titulo, Modifier.padding(top = 3.dp), fontSize = 9.sp, lineHeight = 12.sp, color = HarborColors.Muted, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text("R$ 65,80", Modifier.padding(top = 4.dp), fontSize = 12.sp, color = HarborColors.Wine, fontWeight = FontWeight.Bold)
                                Text("Últimas ${book.estoque.coerceAtMost(5)} unidades", Modifier.padding(top = 2.dp), fontSize = 8.sp, lineHeight = 10.sp, color = HarborColors.Amber, textAlign = TextAlign.Center)
                                Spacer(Modifier.height(7.dp))
                                Button(onClick = { onAdd(book) }, Modifier.fillMaxWidth().height(31.dp), contentPadding = PaddingValues(0.dp), shape = RoundedCornerShape(17.dp), colors = ButtonDefaults.buttonColors(containerColor = HarborColors.WineSolid, contentColor = HarborColors.Sand)) {
                                    Text(if ((cart[book.id] ?: 0) > 0) "Adicionar +" else "Comprar", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                                TextButton(onClick = { onBook(book) }, Modifier.fillMaxWidth().heightIn(min = 31.dp), contentPadding = PaddingValues(vertical = 2.dp)) { Text("Detalhes", fontSize = 10.sp, color = HarborColors.Wine, fontWeight = FontWeight.Medium) }
                            }
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            if (catalog.isEmpty()) ModernCard { Column(Modifier.padding(15.dp)) { Text("Estamos preparando novas caixas para o saldão.", color = HarborColors.Muted, fontSize = 12.sp) } }
            Spacer(Modifier.height(10.dp))
            HarborPrimaryButton("Ver carrinho (${cart.values.sum()})", cart.isEmpty(), onCheckout)
        }
    }, selectedTab = selectedTab, onTab = onTab)
}

@Composable
internal fun HarborCheckoutScreen(
    books: List<LivroUi>,
    cart: Map<Int, Int>,
    subscriptionPlan: PlanoReply?,
    cep: String,
    onCep: (String) -> Unit,
    coupon: String,
    onCoupon: (String) -> Unit,
    payment: String,
    onPayment: (String) -> Unit,
    busy: Boolean,
    message: String,
    onFinish: (Double) -> Unit,
    onBack: () -> Unit,
) {
    val items = books.filter { (cart[it.id] ?: 0) > 0 }
    val subtotal = items.sumOf { 65.80 * (cart[it.id] ?: 0) }
    val discount = if (coupon.equals("HARBOR10", true)) subtotal * .10 else 0.0
    val amount = if (items.isEmpty()) subscriptionPlan?.preco ?: 0.0 else subtotal
    val shipping = if (cep.length >= 8) 20.0 else 0.0
    val total = amount + shipping - discount
    Box(Modifier.fillMaxSize().background(HarborColors.Cream).statusBarsPadding().navigationBarsPadding()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 14.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                listOf("Revisão", "Pagamento", "Finalização").forEachIndexed { index, label ->
                    val active = index == 0
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        Text(if (index > 0) "  →  " else "", color = HarborColors.Gold, fontSize = 13.sp)
                        Text(label, color = if (active) HarborColors.Wine else HarborColors.Muted, fontSize = 11.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal, letterSpacing = 0.3.sp)
                    }
                }
            }
            ModernCard(corner = 22.dp) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
                    Text("Resumo do Pedido", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = HarborColors.Wine)
                    Spacer(Modifier.height(11.dp))
                    if (items.isEmpty() && subscriptionPlan != null) {
                        Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Image(painterResource(R.drawable.capa_jogos_vorazes), "Livro da assinatura mensal", Modifier.width(66.dp).height(75.dp).clip(RoundedCornerShape(11.dp)).shadow(4.dp, RoundedCornerShape(11.dp)), contentScale = ContentScale.Crop)
                            Column(Modifier.weight(1f).padding(start = 11.dp)) {
                                Text("${subscriptionPlan.nome} — caixa mensal", fontSize = 12.sp, lineHeight = 15.sp, color = HarborColors.Wine, maxLines = 2)
                                Text("Cobrança ${subscriptionPlan.periodicidade.lowercase()}", Modifier.padding(top = 4.dp), fontSize = 12.sp, color = HarborColors.Muted)
                            }
                            Text("R$ ${"%.2f".format(subscriptionPlan.preco)}", fontSize = 12.sp, color = HarborColors.Wine, fontWeight = FontWeight.Bold)
                        }
                    }
                    items.forEach { book ->
                        Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Image(painterResource(R.drawable.capa_atlantida), "Capa da caixa Harbor", Modifier.width(66.dp).height(75.dp).clip(RoundedCornerShape(11.dp)).shadow(4.dp, RoundedCornerShape(11.dp)), contentScale = ContentScale.Crop)
                            Column(Modifier.weight(1f).padding(start = 11.dp)) {
                                Text(book.titulo, fontSize = 12.sp, lineHeight = 15.sp, color = HarborColors.Wine, maxLines = 2)
                                Text("Quant.: ${cart[book.id]}", Modifier.padding(top = 4.dp), fontSize = 12.sp, color = HarborColors.Muted)
                            }
                            Text("R$ ${"%.2f".format(65.80 * (cart[book.id] ?: 0))}", fontSize = 12.sp, color = HarborColors.Wine, fontWeight = FontWeight.Bold)
                        }
                    }
                    CheckoutInput("CEP", cep, onCep, KeyboardType.Number)
                    CheckoutInput("Cupom", coupon, onCoupon)
                    HorizontalDivider(Modifier.padding(top = 2.dp, bottom = 8.dp), color = HarborColors.RoseLine, thickness = 1.dp)
                    CheckoutAmount("Subtotal", amount)
                    CheckoutAmount("Frete", shipping)
                    CheckoutAmount("Desconto", -discount)
                    HorizontalDivider(Modifier.padding(vertical = 6.dp), color = HarborColors.RoseLine, thickness = 1.dp)
                    CheckoutAmount("Total", total, bold = true)
                }
            }
            ModernCard(modifier = Modifier.padding(top = 14.dp), corner = 22.dp) {
                Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
                    Text("Forma de Pagamento", Modifier.fillMaxWidth(), color = HarborColors.Wine, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        listOf("Cartão", "Pix").forEach { method ->
                            FilterChip(selected = payment == method, onClick = { onPayment(method) }, label = { Text(method, fontSize = 11.sp, fontWeight = if (payment == method) FontWeight.Bold else FontWeight.Medium) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = HarborColors.WineSolid, selectedLabelColor = HarborColors.Sand))
                        }
                    }
                    Text("Pagamento protegido. Nenhum dado de cartão é armazenado.", Modifier.padding(top = 4.dp), color = HarborColors.Muted, fontSize = 9.sp)
                }
            }
            if (message.isNotBlank()) ModernNotice(message)
            HarborPrimaryButton(if (subscriptionPlan != null) "Finalizar assinatura" else "Finalizar o Pedido", busy, { onFinish(total) }, Modifier.padding(top = 16.dp))
            Text("Pagamento simulado via Pix na próxima etapa.", Modifier.fillMaxWidth().padding(top = 8.dp), textAlign = TextAlign.Center, color = HarborColors.Muted, fontSize = 10.sp)
            TextButton(onClick = onBack, Modifier.align(Alignment.CenterHorizontally)) { Text("Voltar", color = HarborColors.Wine, fontSize = 12.sp) }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter).height(3.dp), color = HarborColors.Wine, trackColor = HarborColors.Sand)
    }
}

@Composable
private fun CheckoutInput(label: String, value: String, onValue: (String) -> Unit, keyboard: KeyboardType = KeyboardType.Text) {
    Column(Modifier.fillMaxWidth().padding(bottom = 9.dp)) {
        Text(
            label,
            Modifier.padding(start = 4.dp, bottom = 4.dp),
            color = HarborColors.Muted, fontSize = 11.sp, fontWeight = FontWeight.Medium
        )
        BasicTextField(
            value, onValue,
            Modifier.fillMaxWidth().defaultMinSize(minHeight = 46.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(HarborColors.Field)
                .border(1.dp, HarborColors.RoseLine.copy(alpha = 0.75f), RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 13.dp)
                .semantics { contentDescription = label },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            textStyle = TextStyle(color = HarborColors.Ink, fontSize = 13.sp),
            cursorBrush = SolidColor(HarborColors.Wine),
            decorationBox = { inner ->
                Box {
                    if (value.isBlank()) Text(
                        if (label == "CEP") "00000-000" else "Código promocional",
                        color = HarborColors.Muted, fontSize = 12.sp, fontWeight = FontWeight.Medium
                    )
                    inner()
                }
            }
        )
    }
}

@Composable
private fun CheckoutAmount(label: String, amount: Double, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = if (bold) HarborColors.Wine else HarborColors.Muted, fontSize = 12.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
        Text("R$ ${"%.2f".format(amount)}", color = HarborColors.Wine, fontSize = if (bold) 13.sp else 12.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
    }
}

/** Gera uma matriz quadrada com finder/timing patterns e dados aleatórios, com cara de QR Code. */
private fun generatePixQrMatrix(size: Int = 25): Array<BooleanArray> {
    val random = java.util.Random()
    val matrix = Array(size) { BooleanArray(size) }
    for (y in 0 until size) for (x in 0 until size) matrix[y][x] = random.nextBoolean()

    fun finder(originX: Int, originY: Int) {
        for (y in -1..7) for (x in -1..7) {
            val px = originX + x
            val py = originY + y
            if (px < 0 || py < 0 || px >= size || py >= size) continue
            val inRing = x in 0..6 && y in 0..6 && (x == 0 || x == 6 || y == 0 || y == 6)
            val inCore = x in 2..4 && y in 2..4
            matrix[py][px] = inRing || inCore
        }
    }
    finder(0, 0)
    finder(size - 7, 0)
    finder(0, size - 7)
    for (i in 8 until size - 8) {
        matrix[6][i] = i % 2 == 0
        matrix[i][6] = i % 2 == 0
    }
    return matrix
}

@Composable
private fun PixQrCode(matrix: Array<BooleanArray>, modifier: Modifier = Modifier) {
    val dark = Color(0xFF1B1216) // módulos sempre escuros, em qualquer tema
    Canvas(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .padding(10.dp)
            .semantics { contentDescription = "QR Code aleatório do pagamento Pix" }
    ) {
        val n = matrix.size
        val cell = minOf(size.width, size.height) / n.toFloat()
        val originX = (size.width - cell * n) / 2f
        val originY = (size.height - cell * n) / 2f
        matrix.forEachIndexed { y, row ->
            row.forEachIndexed { x, on ->
                if (on) drawRect(dark, topLeft = Offset(originX + x * cell, originY + y * cell), size = Size(cell, cell))
            }
        }
    }
}

@Composable
private fun QrCodeWithScanLine(matrix: Array<BooleanArray>) {
    val infinite = rememberInfiniteTransition(label = "qrScan")
    val scan by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Reverse),
        label = "scan"
    )
    Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
        PixQrCode(matrix, Modifier.fillMaxSize())
        Canvas(Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp))) {
            val y = size.height * scan
            drawLine(
                HarborColors.Wine.copy(alpha = 0.5f),
                Offset(10f, y),
                Offset(size.width - 10f, y),
                strokeWidth = 2.dp.toPx()
            )
        }
    }
}

/** Aba simulada de pagamento por Pix: QR Code aleatório, valor da compra e confirmação. */
@Composable
internal fun HarborPixScreen(
    amount: Double,
    code: String,
    isSubscription: Boolean,
    busy: Boolean,
    message: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onBack: () -> Unit,
) {
    val qr = remember(code) { generatePixQrMatrix() }
    Box(Modifier.fillMaxSize().background(HarborColors.Cream).statusBarsPadding().navigationBarsPadding()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("‹", Modifier.clickable(onClick = onBack).padding(end = 12.dp), color = HarborColors.Wine, fontSize = 30.sp)
                Text("Pagamento via Pix", color = HarborColors.Wine, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
            }
            Spacer(Modifier.height(14.dp))
            ModernCard {
                Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Escaneie o QR Code para pagar", fontSize = 13.sp, lineHeight = 17.sp, color = HarborColors.Ink, textAlign = TextAlign.Center)
                    Text(
                        "Pagamento simulado — nenhuma cobrança real é feita",
                        Modifier.padding(top = 4.dp), fontSize = 10.sp, color = HarborColors.Muted, textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(14.dp))
                    QrCodeWithScanLine(qr)
                    Spacer(Modifier.height(14.dp))
                    Text("Valor a pagar", fontSize = 11.sp, color = HarborColors.Muted, letterSpacing = 0.4.sp)
                    Text(
                        "R$ ${"%.2f".format(amount)}",
                        Modifier.padding(top = 3.dp), fontSize = 27.sp, fontWeight = FontWeight.Bold, color = HarborColors.Wine
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Pix copia e cola (simulado)", fontSize = 10.sp, color = HarborColors.Muted)
                    Text(
                        code,
                        Modifier.padding(top = 4.dp).clip(RoundedCornerShape(9.dp)).background(HarborColors.SurfaceWarm)
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        fontSize = 9.sp, lineHeight = 12.sp, color = HarborColors.Ink, textAlign = TextAlign.Center,
                        maxLines = 3, overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (message.isNotBlank()) ModernNotice(message)
            HarborPrimaryButton("Confirmar pagamento", busy, onConfirm, Modifier.padding(top = 16.dp))
            TextButton(onClick = onCancel, Modifier.padding(top = 2.dp)) {
                Text("Cancelar pagamento", color = HarborColors.Wine, fontSize = 12.sp)
            }
            Text(
                if (isSubscription) "Ao confirmar, sua assinatura é ativada na hora."
                else "Ao confirmar, seu pedido é registrado e passa a ser preparado.",
                Modifier.padding(top = 6.dp), fontSize = 10.sp, lineHeight = 13.sp, color = HarborColors.Muted, textAlign = TextAlign.Center
            )
        }
        if (busy) LinearProgressIndicator(
            Modifier.fillMaxWidth().align(Alignment.TopCenter).height(3.dp),
            color = HarborColors.Wine, trackColor = HarborColors.Sand
        )
    }
}

/** Confirmação exibida depois do pagamento Pix aprovado. */
@Composable
internal fun HarborPixSuccessScreen(
    amount: Double,
    isSubscription: Boolean,
    onHome: () -> Unit,
) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val checkScale by animateFloatAsState(if (shown) 1f else 0.3f, spring(dampingRatio = 0.42f, stiffness = 320f), label = "check")
    val content by animateFloatAsState(if (shown) 1f else 0f, tween(460), label = "content")
    val rise by animateFloatAsState(if (shown) 0f else 26f, tween(460), label = "rise")
    Box(
        Modifier.fillMaxSize().background(HarborColors.HeaderBrush).statusBarsPadding().navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.padding(horizontal = 28.dp).graphicsLayer { alpha = content; translationY = rise },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier.size(88.dp).graphicsLayer { scaleX = checkScale; scaleY = checkScale }.clip(CircleShape).background(HarborColors.Sand),
                contentAlignment = Alignment.Center
            ) {
                Text("✓", color = HarborColors.WineOnLight, fontSize = 46.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                "Pagamento confirmado!",
                Modifier.padding(top = 18.dp), color = HarborColors.Sand, fontSize = 22.sp,
                fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif, textAlign = TextAlign.Center
            )
            Text(
                if (isSubscription) "Sua assinatura está ativa. A caixa deste mês já está sendo preparada com os gêneros que você escolheu."
                else "Seu pedido foi confirmado e já está em preparação. Acompanhe o rastreio na tela Pedidos.",
                Modifier.padding(top = 10.dp), color = Color(0xFFEFD4DA), fontSize = 13.sp, lineHeight = 18.sp, textAlign = TextAlign.Center
            )
            Text(
                "R$ ${"%.2f".format(amount)}",
                Modifier.padding(top = 16.dp), color = HarborColors.Sand, fontSize = 25.sp, fontWeight = FontWeight.Bold
            )
            Text("Pagamento simulado via Pix", Modifier.padding(top = 4.dp), color = Color(0xFFE5B7C3), fontSize = 10.sp)
            HarborPrimaryButton("Voltar ao início", onClick = onHome, modifier = Modifier.padding(top = 26.dp))
        }
    }
}

@Composable
internal fun HarborDiscordQrScreen(
    onBack: () -> Unit,
    selectedTab: HarborTab,
    onTab: (HarborTab) -> Unit,
) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().background(HarborColors.Cream)) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(HarborColors.HeaderBrush)
                .statusBarsPadding()
                .padding(horizontal = 16.dp)
                .height(58.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("‹", Modifier.clickable(onClick = onBack).padding(end = 13.dp).padding(vertical = 3.dp), color = HarborColors.Sand, fontSize = 32.sp)
            Column {
                Text("Comunidade Harbor", color = HarborColors.Sand, fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
                Text("Servidor oficial do Clube no Discord", color = Color(0xFFE5B7C3), fontSize = 10.sp)
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            CommunityPattern(Modifier.fillMaxSize())
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Card(
                    Modifier.fillMaxWidth().shadow(9.dp, RoundedCornerShape(26.dp), ambientColor = Color(0x1A630626), spotColor = Color(0x40630626)).clip(RoundedCornerShape(26.dp)),
                    shape = RoundedCornerShape(26.dp),
                    colors = CardDefaults.cardColors(containerColor = HarborColors.Paper)
                ) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.clip(CircleShape).background(HarborColors.Wine).padding(horizontal = 16.dp, vertical = 5.dp)) {
                            Text("DISCORD — CLUBE DO PORTO", color = HarborColors.Sand, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                        }
                        Image(
                            painterResource(R.drawable.discord_qr),
                            "QR Code do servidor Harbor no Discord",
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .padding(top = 14.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .border(1.dp, HarborColors.RoseLine, RoundedCornerShape(20.dp)),
                            contentScale = ContentScale.Fit
                        )
                        Text(
                            "Escaneie o código com a câmera do celular ou com o leitor de QR do Discord para entrar na tripulação.",
                            Modifier.padding(top = 12.dp),
                            textAlign = TextAlign.Center,
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            color = HarborColors.Muted
                        )
                        Text(
                            "ou toque no link abaixo:",
                            Modifier.padding(top = 8.dp),
                            textAlign = TextAlign.Center,
                            fontSize = 10.sp,
                            color = HarborColors.Muted
                        )
                        Text(
                            HarborDiscordInvite,
                            Modifier
                                .padding(top = 6.dp)
                                .clip(CircleShape)
                                .background(HarborColors.Sand.copy(alpha = 0.55f))
                                .clickable { OpenDiscordInvite(context) }
                                .padding(horizontal = 13.dp, vertical = 6.dp)
                                .semantics { contentDescription = "Toque para abrir o link do Discord" },
                            color = HarborColors.Wine,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            textDecoration = TextDecoration.Underline,
                            textAlign = TextAlign.Center
                        )
                        HarborPrimaryButton(
                            text = "Entrar no Discord",
                            busy = false,
                            onClick = { OpenDiscordInvite(context) },
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }
                }
                ModernCard(modifier = Modifier.padding(top = 13.dp)) {
                    Column(Modifier.padding(15.dp)) {
                        HarborSectionLabel("Como entrar")
                        listOf(
                            "1. Abra o Discord no seu celular.",
                            "2. Aponte a câmera ou o leitor de QR do Discord para o código.",
                            "3. Toque no link apresentado e aceite o convite do Clube Harbor.",
                        ).forEach { passo ->
                            Text(passo, Modifier.padding(bottom = 4.dp), color = HarborColors.Ink, fontSize = 11.sp, lineHeight = 14.sp)
                        }
                        Text("O mesmo QR também fica disponível no topo da tela de Comunidade.", Modifier.padding(top = 4.dp), color = HarborColors.Muted, fontSize = 10.sp, lineHeight = 13.sp)
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
        }
        HarborBottomBar(selectedTab, onTab)
    }
}

@Composable
internal fun HarborCommunityScreen(
    messages: List<MensagemUi>,
    ownName: String,
    text: String,
    onText: (String) -> Unit,
    busy: Boolean,
    error: String,
    onSend: () -> Unit,
    onOpenQr: () -> Unit,
    onBack: () -> Unit,
    selectedTab: HarborTab,
    onTab: (HarborTab) -> Unit,
) {
    Column(Modifier.fillMaxSize().background(HarborColors.Cream)) {
        Row(Modifier.fillMaxWidth().background(HarborColors.HeaderBrush).statusBarsPadding().padding(horizontal = 16.dp).height(58.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("‹", Modifier.clickable(onClick = onBack).padding(end = 13.dp).padding(vertical = 3.dp), color = HarborColors.Sand, fontSize = 32.sp)
            Text("Clube da Leitura - Comunidade", color = HarborColors.Sand, fontSize = 15.sp, fontWeight = FontWeight.Medium, fontFamily = FontFamily.Serif)
        }
        Row(
            Modifier.fillMaxWidth().background(HarborColors.BottomBrush).padding(horizontal = 24.dp, vertical = 9.dp).clickable { onOpenQr() }.semantics { contentDescription = "Ver QR Code do Discord em tela cheia" },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(painterResource(R.drawable.community_qr), "QR Code do servidor Harbor no Discord", Modifier.size(93.dp).clip(RoundedCornerShape(13.dp)).shadow(5.dp, RoundedCornerShape(13.dp)))
            Column(Modifier.weight(1f).padding(start = 13.dp)) {
                Text("Conheça nosso server no Discord", color = HarborColors.Sand, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
                Text("Junte-se à nossa tripulação no Discord para trocar ideias sobre as leituras, acompanhar os spoilers das caixas e prosear com outros leitores.", Modifier.padding(top = 5.dp), color = Color(0xFFE9C4CE), fontSize = 11.sp, lineHeight = 14.sp)
                Text("Toque para ver o QR em tela cheia ›", Modifier.padding(top = 5.dp), color = HarborColors.Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            CommunityPattern(Modifier.fillMaxSize())
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 12.dp)) {
                if (messages.isEmpty()) {
                    CommunityBubble("Cassiana", "Alguém já leu o último livro da Colleen Hoover?", R.drawable.community_avatar_cassiana, false)
                    CommunityBubble(ownName.ifBlank { "Eu" }, "Sim! Adorei a reviravolta no capítulo 20.", R.drawable.community_avatar_kevin, true)
                    CommunityBubble("Aryel", "Simm, muito bom, quase no final!!", R.drawable.community_avatar_aryel, false)
                    CommunityBubble(ownName.ifBlank { "Eu" }, "Ainda não comecei.", R.drawable.community_avatar_kevin, true)
                } else {
                    messages.forEach { item ->
                        val own = item.nome.equals(ownName, true)
                        if (item.tipo == "Avaliação") ReviewBubble(item, own) else CommunityBubble(item.nome, item.texto, if (own) R.drawable.community_avatar_kevin else R.drawable.community_avatar_cassiana, own)
                    }
                }
                if (error.isNotBlank()) ModernNotice(error)
            }
        }
        Row(Modifier.fillMaxWidth().background(HarborColors.Cream).navigationBarsPadding().imePadding().padding(horizontal = 22.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                text, onText,
                Modifier.weight(1f).heightIn(min = 42.dp, max = 80.dp).clip(CircleShape).background(HarborColors.Field)
                    .border(1.dp, HarborColors.RoseLine, CircleShape)
                    .padding(start = 14.dp, end = 9.dp, top = 10.dp, bottom = 8.dp)
                    .semantics { contentDescription = "Escreva uma mensagem para a comunidade" },
                textStyle = TextStyle(fontSize = 12.sp, color = HarborColors.Ink),
                decorationBox = { inner -> Box { if (text.isBlank()) Text("Escreva uma mensagem…", color = HarborColors.Muted, fontSize = 11.sp); inner() } }
            )
            TextButton(onClick = onSend, enabled = !busy, modifier = Modifier.width(47.dp), contentPadding = PaddingValues(0.dp)) { Text("➤", fontSize = 22.sp, color = HarborColors.Wine) }
        }
        HarborBottomBar(selectedTab, onTab)
    }
}

@Composable
private fun CommunityPattern(modifier: Modifier = Modifier) {
    Canvas(modifier.background(HarborColors.Cream)) {
        val color = Color(0x2A7C4853)
        val stroke = 1.15.dp.toPx()
        val columns = (size.width / 76.dp.toPx()).toInt() + 2
        val rows = (size.height / 88.dp.toPx()).toInt() + 2
        for (row in 0..rows) for (column in 0..columns) {
            val cx = column * 76.dp.toPx() + if (row % 2 == 0) 0f else 28.dp.toPx()
            val cy = row * 88.dp.toPx()
            val boat = Path().apply { moveTo(cx, cy+12); lineTo(cx+9, cy+19); lineTo(cx+19, cy+12); moveTo(cx+4, cy+14); lineTo(cx+4, cy+7); lineTo(cx+12, cy+14); moveTo(cx+12, cy+14); lineTo(cx+12, cy+4); lineTo(cx+17, cy+14) }
            drawPath(boat, color, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
            val lx = cx + 37.dp.toPx(); val ly = cy + 43.dp.toPx()
            val light = Path().apply { moveTo(lx-4,ly+9); lineTo(lx-2,ly-2); lineTo(lx+2,ly-2); lineTo(lx+4,ly+9); moveTo(lx-2,ly-2); lineTo(lx-3,ly-6); lineTo(lx+3,ly-6); lineTo(lx+2,ly-2); moveTo(lx-4,ly+9); lineTo(lx+4,ly+9) }
            drawPath(light, color, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

@Composable
private fun CommunityBubble(name: String, message: String, avatar: Int, own: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top, horizontalArrangement = if (own) Arrangement.End else Arrangement.Start) {
        if (!own) Image(painterResource(avatar), "Avatar de $name", Modifier.padding(end = 8.dp).size(41.dp).shadow(3.dp, CircleShape).clip(CircleShape))
        Column(
            Modifier.widthIn(max = 248.dp).shadow(4.dp, RoundedCornerShape(if (own) 18.dp else 17.dp), ambientColor = Color(0x1A630626), spotColor = Color(0x26630626))
                .clip(RoundedCornerShape(if (own) 18.dp else 17.dp))
                .background(if (own) HarborColors.WineSolid else HarborColors.Paper)
                .padding(horizontal = 12.dp, vertical = 9.dp)
        ) {
            Text("$name.:  $message", color = if (own) HarborColors.OnWine else HarborColors.Ink, fontSize = 12.sp, lineHeight = 15.sp)
        }
        if (own) Image(painterResource(avatar), "Seu avatar", Modifier.padding(start = 8.dp).size(2.dp).clearAndSetSemantics { })
    }
}

@Composable
private fun ReviewBubble(item: MensagemUi, own: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = if (own) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier.widthIn(max = 252.dp).shadow(4.dp, RoundedCornerShape(16.dp), ambientColor = Color(0x1A630626), spotColor = Color(0x26630626))
                .clip(RoundedCornerShape(16.dp))
                .background(HarborColors.Paper)
                .padding(11.dp)
        ) {
            Text("${item.nome} avaliou uma leitura", color = HarborColors.Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(item.texto, Modifier.padding(top = 4.dp), color = HarborColors.Ink, fontSize = 12.sp, lineHeight = 14.sp)
        }
    }
}

/** Pedido de exemplo exibido quando a conta ainda não tem caixas (demonstração). */
private val DemoPedidos = listOf(
    CaixaUi(
        id = 0,
        status = "Entregue",
        codigoRastreio = "BR1234567895",
        livro = LivroUi(0, "A Biblioteca da Meia-Noite", "Matt Haig", "Entre a vida e a morte existe uma biblioteca.", "Fantasia", 20),
        criadaEm = "2026-10-12T10:00:00",
    ),
)

@Composable
internal fun HarborOrderScreen(
    boxes: List<CaixaUi>,
    onRate: (CaixaUi) -> Unit,
    selectedTab: HarborTab,
    onTab: (HarborTab) -> Unit,
) {
    HarborDashboardFrame(content = {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 16.dp, bottom = 12.dp)) {
            HarborScreenTitle("Histórico de Pedidos")
            Image(painterResource(R.drawable.ilustracao_rastreamento), "Ilustração de caixa Harbor em transporte", Modifier.fillMaxWidth().height(102.dp).padding(top = 7.dp), contentScale = ContentScale.Fit)
            if (boxes.isEmpty()) ModernNotice("Exibindo pedido de exemplo — seus pedidos reais aparecem aqui após a compra.", HarborColors.Muted)
            val historico = boxes.ifEmpty { DemoPedidos }
            historico.forEachIndexed { index, box ->
                ModernCard(modifier = Modifier.padding(top = 10.dp).harborEntrance(index * 60)) {
                    Row(Modifier.fillMaxSize().padding(14.dp)) {
                        Box(Modifier.align(Alignment.CenterVertically).width(64.dp), contentAlignment = Alignment.Center) {
                            Image(painterResource(R.drawable.capa_jogos_vorazes), "Capa de ${box.livro?.titulo ?: "caixa Harbor"}", Modifier.width(58.dp).height(78.dp).clip(RoundedCornerShape(9.dp)).shadow(3.dp, RoundedCornerShape(9.dp)), contentScale = ContentScale.Crop)
                        }
                        Column(Modifier.weight(1f).padding(start = 13.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(box.livro?.titulo ?: "Caixa Harbor", Modifier.weight(1f), fontFamily = FontFamily.Serif, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = HarborColors.Wine, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                StatusChip(box.status)
                                Text("  •  ${box.criadaEm.take(10)}", Modifier.padding(start = 4.dp), color = HarborColors.Muted, fontSize = 11.sp)
                            }
                            if (box.codigoRastreio.isNotBlank()) Text("Rastreio: ${box.codigoRastreio}", Modifier.padding(top = 4.dp), color = HarborColors.Wine, fontSize = 11.sp)
                            if (box.status.equals("Entregue", true) && box.livro != null && box.id > 0) TextButton(onClick = { onRate(box) }, contentPadding = PaddingValues(vertical = 2.dp)) { Text("Avaliar esta leitura  ★", color = HarborColors.Gold, fontSize = 11.sp, fontWeight = FontWeight.Medium) }
                        }
                    }
                }
            }
        }
    }, selectedTab = selectedTab, onTab = onTab)
}

@Composable
internal fun HarborBookDetail(book: LivroUi?, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(HarborColors.Cream).statusBarsPadding().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().height(58.dp).background(HarborColors.HeaderBrush), verticalAlignment = Alignment.CenterVertically) {
            Text("‹", Modifier.clickable(onClick = onBack).padding(start = 16.dp), color = HarborColors.Sand, fontSize = 32.sp)
            if (book != null) Text(book.categoria.uppercase(), color = HarborColors.Gold, fontSize = 10.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Medium)
        }
        if (book != null) {
            Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.Top) {
                Image(painterResource(R.drawable.capa_jogos_vorazes), "Capa de ${book.titulo}", Modifier.width(120.dp).height(174.dp).clip(RoundedCornerShape(15.dp)).shadow(9.dp, RoundedCornerShape(15.dp), ambientColor = Color(0x2B630626), spotColor = Color(0x40630626)), contentScale = ContentScale.Crop)
                Column(Modifier.weight(1f).padding(start = 15.dp)) {
                    Text(book.categoria.uppercase(), color = HarborColors.Gold, fontSize = 10.sp, letterSpacing = 1.sp)
                    Text(book.titulo, Modifier.padding(top = 6.dp), fontFamily = FontFamily.Serif, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = HarborColors.Wine, lineHeight = 23.sp)
                    Text("por ${book.autor}", Modifier.padding(top = 5.dp), fontSize = 12.sp, color = HarborColors.Muted)
                }
            }
            HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = HarborColors.RoseLine, thickness = 1.dp)
            Text("Sobre esta história", Modifier.padding(horizontal = 20.dp).padding(top = 13.dp), fontFamily = FontFamily.Serif, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = HarborColors.Wine)
            Text(book.sinopse, Modifier.padding(horizontal = 20.dp, vertical = 9.dp), fontSize = 13.sp, lineHeight = 19.sp, color = HarborColors.Ink)
        }
    }
}

@Composable
internal fun HarborErrorScreen(
    name: String,
    onName: (String) -> Unit,
    email: String,
    onEmail: (String) -> Unit,
    passwordConfirmation: String,
    onPasswordConfirmation: (String) -> Unit,
    message: String,
    onRetry: () -> Unit,
    onLogin: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(HarborColors.WineDark).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Canvas(Modifier.matchParentSize()) {
            drawCircle(HarborColors.Gold.copy(alpha = 0.08f), radius = size.width * .5f, center = Offset(size.width * 1.05f, size.height * .1f))
            drawCircle(HarborColors.Sand.copy(alpha = 0.06f), radius = size.width * .4f, center = Offset(size.width * -.1f, size.height * .7f))
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 25.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            HarborBrandLogo(Modifier.padding(top = 36.dp).width(152.dp).height(61.dp))
            Text("Ops! Houve um Problema", Modifier.padding(top = 15.dp, bottom = 11.dp), fontFamily = FontFamily.Serif, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = HarborColors.Sand, textAlign = TextAlign.Center)
            Card(Modifier.fillMaxWidth().shadow(6.dp, RoundedCornerShape(19.dp)).clip(RoundedCornerShape(19.dp)), shape = RoundedCornerShape(19.dp), colors = CardDefaults.cardColors(containerColor = HarborColors.Sand)) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 13.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("⚠", color = HarborColors.Amber, fontSize = 25.sp)
                    Text("ERRO NO CADASTRO", color = HarborColors.Wine, fontWeight = FontWeight.Bold, fontSize = 14.sp, fontFamily = FontFamily.Serif)
                    Text(message.ifBlank { "Os dados informados não puderam ser validados." }, Modifier.padding(top = 5.dp), color = HarborColors.Ink, textAlign = TextAlign.Center, fontSize = 11.sp, lineHeight = 14.sp)
                }
            }
            Spacer(Modifier.height(13.dp))
            HarborAuthField("Nome Completo", name, onName, contentDescription = "Nome completo com erro")
            Text("Informe seu nome completo.", Modifier.fillMaxWidth().padding(start = 6.dp, top = 1.dp, bottom = 5.dp), color = HarborColors.Sand.copy(alpha = .8f), fontSize = 10.sp)
            HarborAuthField("Email", email, onEmail, KeyboardType.Email, contentDescription = "E-mail com erro")
            Text("Este e-mail já está cadastrado.", Modifier.fillMaxWidth().padding(start = 6.dp, top = 1.dp, bottom = 5.dp), color = HarborColors.Sand.copy(alpha = .8f), fontSize = 10.sp)
            HarborAuthField("Confirmar Senha", passwordConfirmation, onPasswordConfirmation, KeyboardType.Password, secret = true, contentDescription = "Confirmação de senha com erro")
            Text("As senhas não coincidem.", Modifier.fillMaxWidth().padding(start = 6.dp, top = 1.dp, bottom = 8.dp), color = HarborColors.Sand.copy(alpha = .8f), fontSize = 10.sp)
            TextButton(onClick = onRetry, Modifier.fillMaxWidth()) { Text("Tentar Novamente", color = HarborColors.Sand, fontSize = 13.sp, fontWeight = FontWeight.Medium) }
            Button(onClick = onLogin, Modifier.fillMaxWidth().height(42.dp), shape = RoundedCornerShape(21.dp), colors = ButtonDefaults.buttonColors(containerColor = HarborColors.Sand, contentColor = HarborColors.Wine)) { Text("Voltar para Login", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
        }
    }
}
