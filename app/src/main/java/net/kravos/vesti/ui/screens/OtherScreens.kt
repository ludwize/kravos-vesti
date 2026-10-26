package net.kravos.vesti.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.kravos.vesti.BuildConfig
import net.kravos.vesti.data.Section
import net.kravos.vesti.data.Sources
import net.kravos.vesti.data.ThemeMode
import net.kravos.vesti.ui.Fmt
import net.kravos.vesti.ui.LocalApp
import net.kravos.vesti.ui.LocalNav
import net.kravos.vesti.ui.Route
import net.kravos.vesti.ui.components.ArticleRow
import net.kravos.vesti.ui.components.Empty
import net.kravos.vesti.ui.components.Kicker
import net.kravos.vesti.ui.components.NetImage
import net.kravos.vesti.ui.components.Rule
import net.kravos.vesti.ui.components.TopBar
import net.kravos.vesti.ui.components.lat
import net.kravos.vesti.ui.components.rememberOpenArticle
import net.kravos.vesti.ui.theme.LocalPalette
import net.kravos.vesti.update.Release
import net.kravos.vesti.update.Updater

// ---------- Sačuvano ----------

@Composable
fun SavedScreen(pad: PaddingValues) {
    val app = LocalApp.current
    val nav = LocalNav.current
    val items by app.saved.items.collectAsState()
    val scope = rememberCoroutineScope()
    val p = LocalPalette.current
    Column(Modifier.fillMaxSize()) {
    TopBar("Za kasnije")
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = pad.calculateBottomPadding() + 24.dp)) {
        item("mast") {
            Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 14.dp)) {
                Spacer(Modifier.height(2.dp))
                Text("Sačuvano", style = MaterialTheme.typography.displaySmall)
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().height(2.dp).background(p.ink))
            }
        }
        if (items.isEmpty()) item("empty") { Empty("Ništa još nije sačuvano.\nU čitaču dodirni ikonicu obeleživača i članak ostaje ovde, i bez interneta.") }
        items(items, key = { it.article.id }) { s ->
            val a = s.article
            Row(
                Modifier.fillMaxWidth().clickable { nav.go(Route.Reader(a)) }.padding(start = 18.dp, end = 6.dp, top = 14.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (a.image != null) {
                    NetImage(a.image, Modifier.size(56.dp).clip(RoundedCornerShape(4.dp)))
                    Spacer(Modifier.width(12.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(Sources.name(a.sourceId).uppercase() + " · " + Fmt.ago(a.published), style = MaterialTheme.typography.labelSmall, color = p.dim)
                    Spacer(Modifier.height(4.dp))
                    Text(lat(a.title), style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = { scope.launch { app.saved.remove(a.id) } }) { Icon(Icons.Outlined.Delete, "Ukloni", tint = p.dim) }
            }
            Rule(Modifier.padding(horizontal = 18.dp))
        }
    }
    }
}

// ---------- jedan izvor ----------

@Composable
fun SourceScreen(sourceId: String, pad: PaddingValues) {
    val app = LocalApp.current
    val feed by app.repo.feed.collectAsState()
    val open = rememberOpenArticle()
    val list = feed?.articles?.filter { it.sourceId == sourceId }.orEmpty()
    Column(Modifier.fillMaxSize()) {
        TopBar(Sources.name(sourceId))
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = pad.calculateBottomPadding() + 24.dp)) {
            item("t") {
                Text(Sources.name(sourceId), Modifier.padding(start = 18.dp, top = 14.dp, bottom = 8.dp), style = MaterialTheme.typography.headlineMedium)
                Rule()
            }
            if (list.isEmpty()) item("e") { Empty("Nema skorašnjih članaka.") }
            items(list, key = { it.id }) { a ->
                ArticleRow(a, 0) { open(a) }
                Rule(Modifier.padding(horizontal = 18.dp))
            }
        }
    }
}

// ---------- Podešavanja ----------

@Composable
fun SettingsScreen(pad: PaddingValues, release: Release?, onCheck: () -> Unit) {
    val app = LocalApp.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val p = LocalPalette.current
    val theme by app.prefs.theme.collectAsState()
    val latin by app.prefs.latin.collectAsState()
    val hideEnt by app.prefs.hideEnt.collectAsState()
    val on by app.prefs.sources.collectAsState()
    var progress by remember { mutableStateOf<Float?>(null) }
    var updErr by remember { mutableStateOf<String?>(null) }

    fun changed() { scope.launch { app.repo.refresh() } }

    Column(Modifier.fillMaxSize()) {
        TopBar("Podešavanja")
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = pad.calculateBottomPadding() + 32.dp)) {
            item("izgled") {
                Group("Izgled")
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { m ->
                        val sel = m == theme
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(6.dp))
                                .background(if (sel) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.background)
                                .clickable { app.prefs.setTheme(m) }.padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text(m.label, style = MaterialTheme.typography.labelMedium, fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium) }
                    }
                }
                Toggle("Sve latinicom", "Ćirilične izvore prikaži latinicom", latin) { app.prefs.setLatin(it) }
                Toggle("Sakrij zabavu i lifestyle", "Šou-biznis, magazin i lifestyle ne ulaze u feed (sport ima svoj tab)", hideEnt) { app.prefs.setHideEnt(it); changed() }
            }
            Section.entries.forEach { sec ->
                item("src-${sec.name}") {
                    Group("Izvori · ${sec.label}")
                    Sources.ALL.filter { it.section == sec }.forEach { s ->
                        Toggle(
                            s.name,
                            if (!s.reader) "otvara se u pregledaču" else if (s.lang == "en") "na engleskom" else null,
                            s.id in on,
                        ) { app.prefs.toggleSource(s.id); changed() }
                    }
                }
            }
            item("upd") {
                Group("Verzija")
                Column(Modifier.padding(horizontal = 18.dp, vertical = 6.dp)) {
                    Text("Kravos Vesti 1.0.${BuildConfig.BUILD_NUMBER}", style = MaterialTheme.typography.bodyMedium)
                    val r = release
                    Spacer(Modifier.height(8.dp))
                    when {
                        r != null && Updater.isNewer(r) -> {
                            Text("Dostupna je verzija 1.0.${r.build}", style = MaterialTheme.typography.bodyMedium, color = p.accent)
                            Spacer(Modifier.height(8.dp))
                            val pr = progress
                            if (pr != null) LinearProgressIndicator(progress = { pr }, modifier = Modifier.fillMaxWidth())
                            else OutlinedButton(onClick = {
                                updErr = null
                                scope.launch {
                                    progress = 0f
                                    runCatching { Updater.download(ctx, r) { progress = it } }
                                        .onSuccess { f -> progress = null; Updater.install(ctx, f) }
                                        .onFailure { progress = null; updErr = it.message }
                                }
                            }) { Text("Instaliraj") }
                        }
                        r != null -> Text("Imaš najnoviju verziju.", style = MaterialTheme.typography.bodyMedium, color = p.dim)
                    }
                    updErr?.let { Text("Greška: $it", style = MaterialTheme.typography.labelMedium, color = p.live) }
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(onClick = onCheck) { Text("Proveri ažuriranja") }
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    "Vesti stižu iz javnih RSS kanala izdavača. Ista vest iz više izvora se grupiše u jednu priču; dodirni je da vidiš kako je ko prenosi.",
                    Modifier.padding(horizontal = 18.dp), style = MaterialTheme.typography.labelMedium, color = p.dim,
                )
            }
        }
    }
}

@Composable
private fun Group(title: String) {
    Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 24.dp, bottom = 4.dp)) {
        Kicker(title)
        Spacer(Modifier.height(6.dp))
        Rule()
    }
}

@Composable
private fun Toggle(title: String, sub: String?, value: Boolean, onChange: (Boolean) -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!value) }.padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (sub != null) Text(sub, style = MaterialTheme.typography.labelMedium, color = p.dim)
        }
        Switch(
            checked = value, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = p.accent, checkedThumbColor = MaterialTheme.colorScheme.background),
        )
    }
}
