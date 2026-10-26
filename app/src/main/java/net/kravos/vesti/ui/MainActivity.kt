package net.kravos.vesti.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.kravos.vesti.App
import net.kravos.vesti.ui.screens.FeedScreen
import net.kravos.vesti.ui.screens.ReaderScreen
import net.kravos.vesti.ui.screens.SavedScreen
import net.kravos.vesti.ui.screens.SettingsScreen
import net.kravos.vesti.ui.screens.SourceScreen
import net.kravos.vesti.ui.screens.StoryScreen
import net.kravos.vesti.ui.theme.LocalPalette
import net.kravos.vesti.ui.theme.VestiTheme
import net.kravos.vesti.update.Release
import net.kravos.vesti.update.Updater

class MainActivity : ComponentActivity() {
    private val nav = Navigator()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as App
        setContent {
            val theme by app.prefs.theme.collectAsState()
            VestiTheme(theme) {
                // status/nav bar icons follow the app theme, not just the system one
                val dark = LocalPalette.current.dark
                LaunchedEffect(dark) {
                    val t = android.graphics.Color.TRANSPARENT
                    val style = if (dark) SystemBarStyle.dark(t) else SystemBarStyle.light(t, t)
                    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                }
                CompositionLocalProvider(LocalNav provides nav, LocalApp provides app) { Root(app) }
            }
        }
    }
}

@Composable
private fun Root(app: App) {
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    var release by remember { mutableStateOf<Release?>(null) }
    var bannerHidden by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { if (app.prefs.autoUpdateCheck) release = Updater.latest() }
    // Feeds: paint the last copy instantly, then refresh every 5 minutes while the app is on screen.
    LaunchedEffect(Unit) {
        app.repo.warmStart()
        while (true) {
            runCatching { app.repo.refresh() }
            delay(5 * 60_000L)
            while (!ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) delay(2_000)
        }
    }

    BackHandler(enabled = nav.stack.isNotEmpty() || nav.tab.value != Tab.DANAS) {
        if (!nav.back()) nav.tab(Tab.DANAS)
    }

    val top = nav.stack.lastOrNull()
    val holder = rememberSaveableStateHolder()
    val p = LocalPalette.current
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Column {
                Box(Modifier.fillMaxWidth().padding(0.dp).background(p.rule).padding(top = 0.5.dp))
                NavigationBar(containerColor = MaterialTheme.colorScheme.background, tonalElevation = 0.dp) {
                    Tab.entries.forEach { t ->
                        val sel = nav.tab.value == t && top == null
                        NavigationBarItem(
                            selected = sel,
                            onClick = { nav.tab(t) },
                            icon = {
                                Icon(
                                    when (t) {
                                        Tab.DANAS -> Icons.Filled.Newspaper
                                        Tab.SRBIJA -> Icons.Filled.Place
                                        Tab.SVET -> Icons.Filled.Public
                                        Tab.BEZBEDNOST -> Icons.Filled.Shield
                                        Tab.SPORT -> Icons.Filled.SportsSoccer
                                    },
                                    null,
                                )
                            },
                            label = { Text(t.short, fontSize = 11.sp, maxLines = 1) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                                selectedIconColor = p.accent,
                                selectedTextColor = p.ink,
                                unselectedIconColor = p.dim,
                                unselectedTextColor = p.dim,
                            ),
                        )
                    }
                }
            }
        },
    ) { pad ->
        Column(Modifier.fillMaxSize()) {
            val r = release
            val showBanner = r != null && Updater.isNewer(r) && !bannerHidden && r.build != app.prefs.skippedBuild
            if (showBanner) {
                Row(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer).statusBarsPadding()
                        .clickable { bannerHidden = true; nav.go(Route.Settings) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Text("Nova verzija 1.0.${r!!.build} je spremna — dodirni za instalaciju", Modifier.weight(1f), color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text("Kasnije", Modifier.clickable { bannerHidden = true; app.prefs.skippedBuild = r.build }, color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 13.sp)
                }
            }
            val padding = if (showBanner) PaddingValues(bottom = pad.calculateBottomPadding()) else pad
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                AnimatedContent(targetState = top ?: nav.tab.value, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "nav") { dest ->
                    holder.SaveableStateProvider(if (dest is Tab) "tab-${dest.name}" else "r-${nav.stack.size}-$dest") {
                        when (dest) {
                            is Route.Story -> StoryScreen(dest.id, padding)
                            is Route.Reader -> ReaderScreen(dest.article, padding)
                            is Route.SourceFeed -> SourceScreen(dest.sourceId, padding)
                            is Route.Settings -> SettingsScreen(padding, release) { scope.launch { release = Updater.latest() } }
                            is Route.Saved -> SavedScreen(padding)
                            is Tab -> FeedScreen(dest, padding)
                            else -> Unit
                        }
                    }
                }
                // paper-coloured strip behind the status bar so scrolled text doesn't run under the clock
                Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(MaterialTheme.colorScheme.background.copy(alpha = 0.96f)))
            }
        }
    }
}
