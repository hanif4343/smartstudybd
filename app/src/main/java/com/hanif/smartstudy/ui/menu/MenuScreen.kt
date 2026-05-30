package com.hanif.smartstudy.ui.menu

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.hanif.smartstudy.ui.menu.sections.*
import com.hanif.smartstudy.ui.theme.NotoSansBengali
import com.hanif.smartstudy.util.ReminderHelper
import com.hanif.smartstudy.viewmodel.AppTheme
import com.hanif.smartstudy.viewmodel.MenuViewModel

private val Indigo600 = Color(0xFF4F46E5)
private val DeepIndigo= Color(0xFF1E1B4B)
private val SlateText = Color(0xFF1E293B)
private val MutedText = Color(0xFF64748B)
private val CardBg    = Color(0xFFFFFFFF)
private val SlateLight= Color(0xFFF8FAFC)

// ── Sub-page enum ──
enum class MenuPage { MAIN, PROFILE, STATS, BOOKMARKS, LEADERBOARD, REMINDER, ADMIN }

@Composable
fun MenuScreen(viewModel: MenuViewModel = viewModel()) {
    val state   by viewModel.state.collectAsState()
    val context  = LocalContext.current
    var page    by remember { mutableStateOf(MenuPage.MAIN) }

    // ── Toast messages ──
    LaunchedEffect(state.successMsg, state.error) {
        // Snackbar handled via ScaffoldState in host
    }

    when (page) {
        MenuPage.MAIN       -> MainMenuPage(state, viewModel, context, onNav = { page = it })
        MenuPage.PROFILE    -> ProfilePage(state, viewModel, onBack = { page = MenuPage.MAIN })
        MenuPage.STATS      -> StatsPage(state, onBack = { page = MenuPage.MAIN })
        MenuPage.BOOKMARKS  -> BookmarksPage(state, onBack = { page = MenuPage.MAIN })
        MenuPage.LEADERBOARD-> LeaderboardPage(viewModel, onBack = { page = MenuPage.MAIN })
        MenuPage.REMINDER   -> ReminderPage(context, onBack = { page = MenuPage.MAIN })
        MenuPage.ADMIN      -> AdminPage(state, viewModel, onBack = { page = MenuPage.MAIN })
    }
}

@Composable
fun MainMenuPage(
    state   : com.hanif.smartstudy.viewmodel.MenuUiState,
    vm      : MenuViewModel,
    context : android.content.Context,
    onNav   : (MenuPage) -> Unit
) {
    LazyColumn(
        modifier       = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 100.dp)
    ) {
        // ── User hero card ──
        item { UserHeroCard(state, onEditProfile = { onNav(MenuPage.PROFILE) }) }

        // ── Quick stats ──
        item {
            Spacer(Modifier.height(6.dp))
            QuickStatsBar(state)
            Spacer(Modifier.height(10.dp))
        }

        // ── Menu sections ──
        item {
            MenuSection("📊 আমার তথ্য") {
                MenuItem("📈 পরিসংখ্যান ও XP ইতিহাস", Icons.Default.BarChart) { onNav(MenuPage.STATS) }
                MenuItem("⭐ সংরক্ষিত প্রশ্ন", Icons.Default.Bookmarks) { onNav(MenuPage.BOOKMARKS) }
                MenuItem("🏆 লিডারবোর্ড", Icons.Default.EmojiEvents) { onNav(MenuPage.LEADERBOARD) }
            }
        }

        item {
            MenuSection("⚙️ সেটিংস") {
                // Dark mode toggle
                SwitchMenuItem(
                    label    = "🌙 ডার্ক মোড",
                    checked  = state.isDarkMode,
                    onChange = { vm.toggleDarkMode() }
                )
                // Sound toggle
                SwitchMenuItem(
                    label    = "🔔 সাউন্ড ইফেক্ট",
                    checked  = state.isSoundOn,
                    onChange = { vm.toggleSound() }
                )
                MenuItem("⏰ পড়ার রিমাইন্ডার", Icons.Default.Alarm) { onNav(MenuPage.REMINDER) }
                MenuItem("🎨 থিম কালার", Icons.Default.Palette) { /* inline below */ }
                // Theme picker inline
                ThemePickerRow(current = state.appTheme, onSelect = { vm.setTheme(it) })
            }
        }

        item {
            MenuSection("🗂️ ডেটা") {
                MenuItem("🗑️ Cache মুছে ফেলুন", Icons.Default.DeleteOutline, danger = false) {
                    vm.clearCache()
                }
                MenuItem("🚪 লগআউট", Icons.Default.Logout, danger = true) {
                    vm.logout()
                }
            }
        }

        // Admin section
        if (state.user?.isAdmin() == true) {
            item {
                MenuSection("🛡️ Admin") {
                    MenuItem("Admin Panel", Icons.Default.AdminPanelSettings) { onNav(MenuPage.ADMIN) }
                }
            }
        }

        item {
            // Version
            Text("Smart Study v1.4 · Phase 5", fontSize = 10.sp, color = Color(0xFFCBD5E1),
                fontFamily = NotoSansBengali,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

// ─────────────────────────────────────────────────────────
@Composable
fun UserHeroCard(
    state       : com.hanif.smartstudy.viewmodel.MenuUiState,
    onEditProfile: () -> Unit
) {
    Box(
        Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color(0xFF312E81), Color(0xFF4F46E5))))
            .padding(20.dp)
    ) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Avatar
            Box(
                Modifier.size(70.dp).clip(CircleShape)
                    .background(Color.White.copy(0.15f))
                    .border(2.dp, Color.White.copy(0.4f), CircleShape)
                    .clickable { onEditProfile() },
                contentAlignment = Alignment.Center
            ) {
                val pic = state.user?.picture
                if (!pic.isNullOrEmpty()) {
                    AsyncImage(model = pic, contentDescription = null,
                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Text("👤", fontSize = 30.sp)
                }
                // Upload progress overlay
                if (state.uploadProgress) {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(0.5f)),
                        contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                    }
                }
            }

            Column(Modifier.weight(1f)) {
                Text(state.user?.displayName() ?: "ব্যবহারকারী",
                    fontSize = 18.sp, fontWeight = FontWeight.ExtraBold,
                    color = Color.White, fontFamily = NotoSansBengali)
                Text(state.user?.phone ?: "",
                    fontSize = 12.sp, color = Color.White.copy(0.6f), fontFamily = NotoSansBengali)
                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.user?.role?.let { role ->
                        Badge(containerColor = if (state.user.isAdmin()) Color(0xFFF59E0B) else Color.White.copy(0.2f),
                            contentColor = if (state.user.isAdmin()) Color(0xFF78350F) else Color.White) {
                            Text(role, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 4.dp))
                        }
                    }
                    state.user?.userType?.let {
                        Badge(containerColor = Color.White.copy(0.15f), contentColor = Color.White.copy(0.85f)) {
                            Text(it, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 4.dp))
                        }
                    }
                }
            }

            // Edit button
            IconButton(onClick = onEditProfile,
                modifier = Modifier.size(36.dp).clip(CircleShape)
                    .background(Color.White.copy(0.15f))) {
                Icon(Icons.Default.Edit, null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun QuickStatsBar(state: com.hanif.smartstudy.viewmodel.MenuUiState) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MiniStatCard("✅", state.totalCorrect.toString(), "সঠিক", Color(0xFFF0FDF4), Color(0xFF166534), Modifier.weight(1f))
        MiniStatCard("❌", state.totalWrong.toString(),   "ভুল",  Color(0xFFFFF1F2), Color(0xFF9F1239), Modifier.weight(1f))
        MiniStatCard("🎯", "${state.accuracyPct}%", "নির্ভুল", Color(0xFFF5F3FF), Indigo600, Modifier.weight(1f))
        MiniStatCard("⏱️", "${state.totalStudyMin}মি", "মোট",   Color(0xFFFFFBEB), Color(0xFF92400E), Modifier.weight(1f))
    }
}

@Composable
private fun MiniStatCard(icon: String, value: String, label: String, bg: Color, vc: Color, mod: Modifier) {
    Card(mod, shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(bg)) {
        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(icon, fontSize = 14.sp)
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold,
                color = vc, fontFamily = NotoSansBengali)
            Text(label, fontSize = 8.sp, color = MutedText, fontFamily = NotoSansBengali)
        }
    }
}

// ─────────────────────────────────────────────────────────
// Menu building blocks
// ─────────────────────────────────────────────────────────
@Composable
fun MenuSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
        Text(title, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold,
            color = MutedText, fontFamily = NotoSansBengali,
            modifier = Modifier.padding(bottom = 6.dp, top = 8.dp))
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(CardBg),
            elevation = CardDefaults.cardElevation(1.dp)) {
            Column { content() }
        }
    }
}

@Composable
fun MenuItem(label: String, icon: ImageVector, danger: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, null, tint = if (danger) Color(0xFFEF4444) else Indigo600,
            modifier = Modifier.size(20.dp))
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold,
            color = if (danger) Color(0xFFEF4444) else SlateText,
            fontFamily = NotoSansBengali, modifier = Modifier.weight(1f))
        Icon(Icons.Default.ChevronRight, null, tint = Color(0xFFCBD5E1), modifier = Modifier.size(16.dp))
    }
}

@Composable
fun SwitchMenuItem(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold,
            color = SlateText, fontFamily = NotoSansBengali, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Indigo600,
                checkedTrackColor = Indigo600.copy(0.3f)))
    }
}

@Composable
fun ThemePickerRow(current: AppTheme, onSelect: (AppTheme) -> Unit) {
    val themes = mapOf(
        AppTheme.INDIGO to Color(0xFF4F46E5),
        AppTheme.TEAL   to Color(0xFF0D9488),
        AppTheme.ROSE   to Color(0xFFE11D48),
        AppTheme.AMBER  to Color(0xFFD97706)
    )
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        themes.forEach { (theme, color) ->
            Box(
                Modifier.size(34.dp).clip(CircleShape)
                    .background(color)
                    .border(
                        width = if (current == theme) 3.dp else 0.dp,
                        color = if (current == theme) Color.White else Color.Transparent,
                        shape = CircleShape
                    )
                    .clickable { onSelect(theme) },
                contentAlignment = Alignment.Center
            ) {
                if (current == theme) Text("✓", fontSize = 14.sp, color = Color.White,
                    fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}
