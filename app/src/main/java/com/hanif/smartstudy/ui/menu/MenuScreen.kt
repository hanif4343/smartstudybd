package com.hanif.smartstudy.ui.menu

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.hanif.smartstudy.ui.theme.*
import com.hanif.smartstudy.util.SessionManager
import com.hanif.smartstudy.viewmodel.MenuViewModel
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────
//  MenuScreen — Profile / Settings / Stats / Admin
// ─────────────────────────────────────────────────────────────

@Composable
fun MenuScreen(
    vm            : MenuViewModel = viewModel(),
    onLogout      : () -> Unit    = {},
    onSearchClick : () -> Unit    = {},
    onTypingClick : () -> Unit    = {},
    initialPage   : String?       = null   // "reports" | "techniques" | "stats" | "bookmarks" | "admin"
) {
    val state   by vm.state.collectAsStateWithLifecycle()
    val darkMode = LocalDarkMode.current
    val theme    = LocalAppTheme.current

    // Sync dark mode toggle with theme composition
    LaunchedEffect(state.isDarkMode) { darkMode.value = state.isDarkMode }
    LaunchedEffect(state.appTheme)   { theme.value    = state.appTheme  }

    // Toast
    state.toast?.let { msg ->
        LaunchedEffect(msg) {
            kotlinx.coroutines.delay(2500)
            vm.clearToast()
        }
    }

    // Sub-screen nav — initialPage থেকে শুরু করো
    val startScreen = remember(initialPage) {
        when (initialPage?.lowercase()) {
            "stats"       -> MenuNav.STATS
            "bookmarks"   -> MenuNav.BOOKMARKS
            "admin"       -> MenuNav.ADMIN
            "leaderboard" -> MenuNav.LEADERBOARD
            "profile"     -> MenuNav.PROFILE
            else          -> MenuNav.MAIN
        }
    }
    var screen by remember { mutableStateOf(startScreen) }

    // initialPage বদলালে navigate করো (notification click এর পরে)
    LaunchedEffect(initialPage) {
        if (initialPage != null) screen = startScreen
    }

    // Sub-screen এ থাকলে back দিলে MAIN এ ফেরত
    androidx.activity.compose.BackHandler(enabled = screen != MenuNav.MAIN) {
        screen = MenuNav.MAIN
    }

    AnimatedContent(
        targetState = screen,
        transitionSpec = {
            if (targetState.ordinal > initialState.ordinal)
                slideInHorizontally { it } + fadeIn() togetherWith slideOutHorizontally { -it } + fadeOut()
            else
                slideInHorizontally { -it } + fadeIn() togetherWith slideOutHorizontally { it } + fadeOut()
        },
        label = "menuNav"
    ) { nav ->
        when (nav) {
            MenuNav.MAIN        -> MainMenuScreen(state, vm, onLogout, onNavigate = { screen = it },
                                    onSearchClick = onSearchClick, onTypingClick = onTypingClick)
            MenuNav.PROFILE     -> ProfileScreen(state, vm, onBack = { screen = MenuNav.MAIN })
            MenuNav.STATS       -> StatsScreen(state, vm, onBack = { screen = MenuNav.MAIN })
            MenuNav.SETTINGS    -> SettingsScreen(state, vm, onBack = { screen = MenuNav.MAIN })
            MenuNav.BOOKMARKS   -> BookmarksMenuScreen(onBack = { screen = MenuNav.MAIN })
            MenuNav.LEADERBOARD -> LeaderboardScreen(state, onBack = { screen = MenuNav.MAIN })
            MenuNav.ADMIN       -> AdminScreen(state, vm, onBack = { screen = MenuNav.MAIN })
            MenuNav.PRIVACY     -> PrivacyPolicyScreen(onBack = { screen = MenuNav.MAIN })
        }
    }

    // Toast overlay
    if (state.toast != null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Surface(
                modifier = Modifier.padding(20.dp),
                shape    = RoundedCornerShape(12.dp),
                color    = MaterialTheme.colorScheme.inverseSurface
            ) {
                Text(
                    state.toast!!,
                    modifier   = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    color      = MaterialTheme.colorScheme.inverseOnSurface,
                    fontFamily = NotoSansBengali,
                    fontSize   = 13.sp
                )
            }
        }
    }
}

enum class MenuNav { MAIN, PROFILE, STATS, SETTINGS, BOOKMARKS, LEADERBOARD, ADMIN, PRIVACY }

// ─────────────────────────────────────────────────────────────
//  Main Menu Screen
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainMenuScreen(
    state         : com.hanif.smartstudy.viewmodel.MenuUiState,
    vm            : MenuViewModel,
    onLogout      : () -> Unit,
    onNavigate    : (MenuNav) -> Unit,
    onSearchClick : () -> Unit = {},
    onTypingClick : () -> Unit = {}
) {
    val context    = LocalContext.current
    val scope      = rememberCoroutineScope()
    val uiScale    = LocalUiScale.current
    val session    = remember { SessionManager(context) }

    // reducedUi flag আছে এমন user এর জন্যই শুধু button দেখাবো
    val isReducedUiUser = state.user?.reducedUi == true

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("👤 আমার প্রোফাইল", fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {

            // ── User card ──
            UserProfileCard(state, onClick = { onNavigate(MenuNav.PROFILE) })

            Spacer(Modifier.height(4.dp))

            // ── App Size Control — শুধু reducedUi user এর জন্য ──
            if (isReducedUiUser) {
                AppSizeControlCard(
                    uiScale = uiScale.value,
                    onIncrease = {
                        scope.launch {
                            val next = (uiScale.value + 0.05f).coerceAtMost(1.5f)
                            session.setFontScale(next)
                            uiScale.value = next
                        }
                    },
                    onDecrease = {
                        scope.launch {
                            val next = (uiScale.value - 0.05f).coerceAtLeast(0.4f)
                            session.setFontScale(next)
                            uiScale.value = next
                        }
                    },
                    onReset = {
                        scope.launch {
                            session.setFontScale(1.0f)
                            uiScale.value = 1.0f
                        }
                    }
                )
            }

            // ── Menu items ──
            MenuGroup("📊 তথ্য") {
                MenuRow("📈 পরিসংখ্যান",   "সঠিক/ভুল, XP ইতিহাস",  Icons.Default.BarChart)     { onNavigate(MenuNav.STATS) }
                MenuRow("⭐ সংরক্ষিত",      "bookmark করা প্রশ্ন",   Icons.Default.Bookmark)     { onNavigate(MenuNav.BOOKMARKS) }
                MenuRow("🏆 লিডারবোর্ড",   "শীর্ষ শিক্ষার্থীরা",   Icons.Default.Leaderboard)  { onNavigate(MenuNav.LEADERBOARD) }
            }

            MenuGroup("🛠 Tools") {
                MenuRow("🔍 Global Search",   "সব প্রশ্ন একসাথে খুঁজুন", Icons.Default.Search)    { onSearchClick() }
                MenuRow("⌨️ Typing Practice", "টাইপিং স্পিড বাড়ান",     Icons.Default.Keyboard)  { onTypingClick() }
            }

            MenuGroup("⚙️ সেটিংস") {
                MenuRow("🎨 থিম ও রঙ",     "Dark mode, রঙ বদলাও",   Icons.Default.Palette)      { onNavigate(MenuNav.SETTINGS) }
                MenuRow("🔔 রিমাইন্ডার",   "পড়ার সময় নির্ধারণ",   Icons.Default.Alarm)        { onNavigate(MenuNav.SETTINGS) }
                MenuRow("🔊 সাউন্ড",       "শব্দ চালু/বন্ধ",         Icons.Default.VolumeUp)     { onNavigate(MenuNav.SETTINGS) }
            }

            if (state.isAdmin) {
                MenuGroup("🔑 অ্যাডমিন") {
                    MenuRow("🛡 Admin Panel", "ইউজার ম্যানেজমেন্ট", Icons.Default.AdminPanelSettings) { onNavigate(MenuNav.ADMIN) }
                }
                AdminSwitchTagSection(
                    currentTag = state.adminViewingTag,
                    onSwitch   = { vm.adminSwitchAudienceTag(it) }
                )
            }

            MenuGroup("❓ অন্যান্য") {
                MenuRow("🔒 গোপনীয়তা নীতি", "Privacy Policy", Icons.Default.Security) { onNavigate(MenuNav.PRIVACY) }
                MenuRow("🗑 ডেটা রিসেট",   "সব progress মুছে দাও",  Icons.Default.DeleteForever, tint = Red500) {
                    vm.resetData()
                }
                MenuRow("🚪 লগআউট",        "অ্যাকাউন্ট থেকে বের হও", Icons.AutoMirrored.Filled.ExitToApp, tint = Red500) {
                    vm.logout(); onLogout()
                }
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}

// ── User profile card ─────────────────────────────────────────

@Composable
fun UserProfileCard(
    state   : com.hanif.smartstudy.viewmodel.MenuUiState,
    onClick : () -> Unit
) {
    val user = state.user
    Card(
        onClick   = onClick,
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
        modifier  = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Box(
                Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onPrimary.copy(0.2f)),
                contentAlignment = Alignment.Center
            ) {
                if (!user?.picture.isNullOrBlank()) {
                    AsyncImage(
                        model  = user!!.picture,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        (user?.name?.firstOrNull()?.toString() ?: "?").uppercase(),
                        fontSize   = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    user?.displayName() ?: "ব্যবহারকারী",
                    fontSize   = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.onPrimary,
                    fontFamily = NotoSansBengali
                )
                Text(
                    user?.phone ?: "",
                    fontSize   = 12.sp,
                    color      = MaterialTheme.colorScheme.onPrimary.copy(0.75f),
                    fontFamily = NotoSansBengali
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "XP: ${user?.xp ?: 0}  •  ${user?.userType ?: "Student"}",
                    fontSize   = 11.sp,
                    color      = MaterialTheme.colorScheme.onPrimary.copy(0.65f),
                    fontFamily = NotoSansBengali
                )
            }
            Icon(
                Icons.Default.Edit, null,
                tint = MaterialTheme.colorScheme.onPrimary.copy(0.7f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ── App Size Control Card — only for reducedUi users ─────────

@Composable
fun AppSizeControlCard(
    uiScale    : Float,
    onIncrease : () -> Unit,
    onDecrease : () -> Unit,
    onReset    : () -> Unit
) {
    val percent = (uiScale * 100).toInt()

    Card(
        shape  = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {

            // Title row
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.TextFields,
                    contentDescription = null,
                    tint     = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "🔤 App সাইজ",
                    fontSize   = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = NotoSansBengali,
                    color      = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.weight(1f))
                // Current % দেখাও
                Text(
                    "$percent%",
                    fontSize   = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.primary,
                    fontFamily = NotoSansBengali
                )
            }

            Spacer(Modifier.height(12.dp))

            // − / Reset / + buttons
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                // − ছোট করো
                FilledIconButton(
                    onClick = onDecrease,
                    enabled = uiScale > 0.4f,
                    colors  = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "ছোট", tint = MaterialTheme.colorScheme.onPrimary)
                }

                // Reset — normal size
                OutlinedButton(
                    onClick  = onReset,
                    modifier = Modifier.weight(1f),
                    shape    = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        "Normal",
                        fontFamily = NotoSansBengali,
                        fontSize   = 12.sp
                    )
                }

                // + বড় করো
                FilledIconButton(
                    onClick = onIncrease,
                    enabled = uiScale < 1.5f,
                    colors  = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "বড়", tint = MaterialTheme.colorScheme.onPrimary)
                }
            }

            Spacer(Modifier.height(6.dp))
            Text(
                "− চাপলে ছোট, + চাপলে বড়, Normal চাপলে default",
                fontSize   = 10.sp,
                color      = MaterialTheme.colorScheme.onSurface.copy(0.45f),
                fontFamily = NotoSansBengali
            )
        }
    }
}

// ── Menu group + row ──────────────────────────────────────────

@Composable
fun MenuGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            title,
            fontSize   = 12.sp,
            fontWeight = FontWeight.Bold,
            color      = MaterialTheme.colorScheme.onSurface.copy(0.5f),
            fontFamily = NotoSansBengali,
            modifier   = Modifier.padding(start = 4.dp, bottom = 4.dp)
        )
        Card(
            shape  = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(content = content)
        }
    }
}

@Composable
fun MenuRow(
    title    : String,
    subtitle : String,
    icon     : ImageVector,
    tint     : Color = MaterialTheme.colorScheme.primary,
    onClick  : () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(36.dp)
                .background(tint.copy(0.1f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title,    fontSize = 14.sp, fontWeight = FontWeight.Medium, fontFamily = NotoSansBengali, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.5f), fontFamily = NotoSansBengali)
        }
        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurface.copy(0.3f), modifier = Modifier.size(18.dp))
    }
    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(0.15f))
}

// ─────────────────────────────────────────────────────────────
//  Admin Audience Switch Section
// ─────────────────────────────────────────────────────────────

private val AUDIENCE_TAGS = listOf(
    "" to "👔 Job Seeker (Default)",
    "Job" to "👔 Job",
    "Honours 1" to "🎓 Honours 1st",
    "Honours 2" to "🎓 Honours 2nd",
    "Honours 3" to "🎓 Honours 3rd",
    "Honours 4" to "🎓 Honours 4th",
    "Masters 1" to "🎓 Masters 1st",
    "Masters 2" to "🎓 Masters 2nd",
    "Class 9"   to "📚 Class 9",
    "Class 10"  to "📚 Class 10",
    "Class 11"  to "📚 Class 11",
    "Class 12"  to "📚 Class 12"
)

@Composable
fun AdminSwitchTagSection(currentTag: String, onSwitch: (String) -> Unit) {
    val activeColor = Color(0xFF4F46E5)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.SwapHoriz, null, tint = activeColor, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("🔄 Switch Audience", fontSize = 13.sp,
                    fontWeight = FontWeight.Bold, fontFamily = NotoSansBengali)
                Text(
                    "এখন: ${AUDIENCE_TAGS.find { it.first == currentTag }?.second ?: currentTag.ifBlank { "Job Seeker" }}",
                    fontSize = 11.sp,
                    color = if (currentTag.isBlank()) MaterialTheme.colorScheme.onSurface.copy(0.5f) else activeColor,
                    fontFamily = NotoSansBengali, fontWeight = FontWeight.Bold
                )
            }
        }
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(0.15f))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(AUDIENCE_TAGS.size) { i ->
                val (tag, label) = AUDIENCE_TAGS[i]
                val isActive = tag == currentTag
                Surface(
                    onClick = { onSwitch(tag) },
                    shape   = RoundedCornerShape(20.dp),
                    color   = if (isActive) Color(0xFFEEF2FF) else MaterialTheme.colorScheme.surfaceVariant,
                    border  = if (isActive) androidx.compose.foundation.BorderStroke(1.5.dp, activeColor) else null,
                    modifier = Modifier.height(34.dp)
                ) {
                    Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (isActive) { Icon(Icons.Default.Check, null, tint = activeColor, modifier = Modifier.size(11.dp)); Spacer(Modifier.width(3.dp)) }
                        Text(label, fontSize = 11.sp, fontFamily = NotoSansBengali,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                            color = if (isActive) activeColor else MaterialTheme.colorScheme.onSurface.copy(0.7f))
                    }
                }
            }
        }
    }
}
