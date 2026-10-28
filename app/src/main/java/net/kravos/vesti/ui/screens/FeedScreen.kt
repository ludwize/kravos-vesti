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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.unit.sp
import net.kravos.vesti.data.Categories
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.kravos.vesti.data.Article
import net.kravos.vesti.data.Feed
import net.kravos.vesti.data.Section
import net.kravos.vesti.data.Story
import net.kravos.vesti.ui.Fmt
import net.kravos.vesti.ui.LocalApp
import net.kravos.vesti.ui.LocalNav
import net.kravos.vesti.ui.Route
import net.kravos.vesti.ui.Tab
import net.kravos.vesti.ui.components.ArticleRow
import net.kravos.vesti.ui.components.ErrorBox
import net.kravos.vesti.ui.components.Kicker
import net.kravos.vesti.ui.components.LiveTag
import net.kravos.vesti.ui.components.Rule
import net.kravos.vesti.ui.components.Spinner
import net.kravos.vesti.ui.components.StoryHero
import net.kravos.vesti.ui.components.StoryRow
import net.kravos.vesti.ui.components.lat
import net.kravos.vesti.ui.components.rememberOpenArticle
import net.kravos.vesti.ui.theme.LocalPalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(tab: Tab, pad: PaddingValues) {
    val app = LocalApp.current
    val nav = LocalNav.current
    val feed by app.repo.feed.collectAsState()
    val refreshing by app.repo.refreshing.collectAsState()
    var mode by rememberSaveable(tab) { mutableIntStateOf(0) }
    var cat by rememberSaveable(tab) { mutableStateOf<String?>(null) }
    var pulled by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val animOff = Fmt.animationsOff(LocalContext.current)
    val open = rememberOpenArticle()
    val listState = rememberLazyListState()
    val reselect = nav.reselect.intValue
    val reselectAtStart = remember { reselect }
    LaunchedEffect(reselect) { if (reselect != reselectAtStart && nav.tab.value == tab) { if (animOff) listState.scrollToItem(0) else listState.animateScrollToItem(0) } }
    val counts = remember(feed, tab) {
        val m = HashMap<String, Int>()
        tab.section?.let { sec -> feed?.stories?.get(sec)?.forEach { st -> st.cats.forEach { c -> m[c] = (m[c] ?: 0) + 1 } } }
        m
    }
    val openStory: (Story) -> Unit = { s -> if (s.articles.size == 1) open(s.lead) else nav.go(Route.Story(s.id)) }

    PullToRefreshBox(
        isRefreshing = pulled && refreshing && !animOff,
        onRefresh = { pulled = true; scope.launch { app.repo.refresh(force = true); pulled = false } },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = pad.calculateBottomPadding() + 24.dp)) {
            val f = feed
            item("mast") { Masthead(tab, cat, counts) { cat = it; scope.launch { listState.scrollToItem(0) } } }
            item("mode") { ModeSwitch(mode) { mode = it } }
            when {
                f == null -> item("load") { Spinner() }
                f.articles.isEmpty() -> item("err") {
                    if (refreshing) Spinner()
                    else ErrorBox(if (f.failed.isNotEmpty()) "Ne odgovaraju: " + f.failed.joinToString(", ") else null) { scope.launch { app.repo.refresh(force = true) } }
                }
                mode == 1 -> latest(f, tab.section, cat, open)
                tab == Tab.DANAS -> digest(f, openStory, open) { nav.tab(it) }
                else -> section(f, tab.section!!, cat, openStory)
            }
            if (f != null && f.articles.isNotEmpty()) item("foot") { Footer(f) }
        }
    }
}

/** Date line, section title (a category menu on section tabs), saved + settings icons, heavy rule. */
@Composable
private fun Masthead(tab: Tab, cat: String?, counts: Map<String, Int>, onCat: (String?) -> Unit) {
    val nav = LocalNav.current
    val p = LocalPalette.current
    val sec = tab.section
    var menu by remember { mutableStateOf(false) }
    val catLabel = cat?.let { Categories.of(it)?.label }
    Column(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 18.dp, end = 6.dp, top = 14.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    (if (catLabel != null) tab.label else Fmt.masthead()).uppercase(),
                    style = MaterialTheme.typography.labelSmall, color = if (catLabel != null) p.accent else p.dim, maxLines = 1,
                )
                Spacer(Modifier.height(2.dp))
                Box {
                    Row(
                        if (sec != null) Modifier.clickable { menu = true } else Modifier,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            catLabel ?: tab.label, Modifier.weight(1f, fill = false),
                            style = if (catLabel == null) MaterialTheme.typography.displaySmall
                            else MaterialTheme.typography.displaySmall.copy(fontSize = 28.sp, lineHeight = 33.sp),
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                        if (sec != null) Icon(Icons.Filled.ArrowDropDown, "Kategorija", Modifier.size(34.dp), tint = p.dim)
                    }
                    if (sec != null) DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
                        CatItem("Sve vesti", null, cat == null) { onCat(null); menu = false }
                        HorizontalDivider(color = p.rule)
                        Categories.forSection(sec).forEach { c ->
                            val n = counts[c.id] ?: 0
                            if (n > 0) CatItem(c.label, n, cat == c.id) { onCat(c.id); menu = false }
                        }
                    }
                }
            }
            IconButton(onClick = { nav.go(Route.Saved) }) { Icon(Icons.Outlined.BookmarkBorder, "Sačuvano", tint = p.dim) }
            IconButton(onClick = { nav.go(Route.Settings) }) { Icon(Icons.Outlined.Settings, "Podešavanja", tint = p.dim) }
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().padding(end = 12.dp).height(2.dp).background(p.ink))
    }
}

@Composable
private fun CatItem(label: String, count: Int?, selected: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    DropdownMenuItem(
        text = {
            Row(Modifier.width(220.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = if (selected) p.accent else p.ink)
                if (count != null) Text("$count", style = MaterialTheme.typography.labelMedium, color = p.dim)
            }
        },
        onClick = onClick,
    )
}

@Composable
private fun ModeSwitch(mode: Int, onChange: (Int) -> Unit) {
    val p = LocalPalette.current
    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            listOf("Najvažnije", "Najnovije").forEachIndexed { i, label ->
                val sel = i == mode
                Column(Modifier.clickable { onChange(i) }.padding(top = 12.dp)) {
                    Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium, color = if (sel) p.ink else p.dim)
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.width(if (sel) 28.dp else 0.dp).height(2.dp).background(p.accent))
                }
            }
        }
        Rule()
    }
}

private fun LazyListScope.digest(f: Feed, openStory: (Story) -> Unit, open: (Article) -> Unit, goTab: (Tab) -> Unit) {
    val live = f.articles.filter { it.live && it.section == Section.SRBIJA && System.currentTimeMillis() - it.published < 12 * 3600_000L }.take(2)
    if (live.isNotEmpty()) {
        items(live, key = { "live-" + it.id }) { a -> LiveRow(a) { open(a) } }
        item("live-rule") { Rule() }
    }
    val plan = listOf(Triple(Section.SRBIJA, Tab.SRBIJA, 6), Triple(Section.BEOGRAD, Tab.BEOGRAD, 4), Triple(Section.SVET, Tab.SVET, 5), Triple(Section.BEZBEDNOST, Tab.BEZBEDNOST, 4), Triple(Section.SPORT, Tab.SPORT, 4))
    for ((sec, tab, n) in plan) {
        val list = f.stories[sec].orEmpty().take(n)
        if (list.isEmpty()) continue
        item("h-${sec.name}") { SectionHeader(sec.label) { goTab(tab) } }
        list.forEachIndexed { i, s ->
            item("d-${sec.name}-${s.id}") {
                if (i == 0 && sec != Section.BEZBEDNOST) StoryHero(s) { openStory(s) } else StoryRow(s) { openStory(s) }
                if (i < list.lastIndex) Rule(Modifier.padding(horizontal = 18.dp))
            }
        }
    }
}

private fun LazyListScope.section(f: Feed, sec: Section, cat: String?, openStory: (Story) -> Unit) {
    val list = f.stories[sec].orEmpty().filter { cat == null || cat in it.cats }.take(150)
    if (list.isEmpty()) { item("none") { net.kravos.vesti.ui.components.Empty(if (cat != null) "Trenutno nema vesti u ovoj kategoriji." else "Nema vesti iz uključenih izvora.") }; return }
    list.forEachIndexed { i, s ->
        item("s-${s.id}") {
            if (i == 0) StoryHero(s) { openStory(s) } else StoryRow(s) { openStory(s) }
            Rule(Modifier.padding(horizontal = 18.dp))
        }
    }
}

private fun LazyListScope.latest(f: Feed, sec: Section?, cat: String?, open: (Article) -> Unit) {
    val coverage = HashMap<String, Int>()
    f.stories.values.forEach { l -> l.forEach { s -> val n = s.sources.size - 1; if (n > 0) s.articles.forEach { coverage[it.id] = n } } }
    val list = f.articles.filter { (sec == null || it.section == sec) && (cat == null || cat in it.cats) }.take(200)
    items(list, key = { "n-" + it.id }) { a ->
        ArticleRow(a, coverage[a.id] ?: 0) { open(a) }
        Rule(Modifier.padding(horizontal = 18.dp))
    }
}

@Composable
private fun SectionHeader(label: String, onAll: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 22.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Kicker(label, Modifier.weight(1f))
            Text("Sve ›", Modifier.clickable(onClick = onAll).padding(4.dp), style = MaterialTheme.typography.labelMedium, color = LocalPalette.current.dim)
        }
        Spacer(Modifier.height(6.dp))
        Rule()
    }
}

@Composable
private fun LiveRow(a: Article, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        LiveTag()
        Spacer(Modifier.width(10.dp))
        Text(lat(a.title), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Footer(f: Feed) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 20.dp)) {
        Text("Ažurirano u ${Fmt.clock(f.fetchedAt)} · povuci nadole za osvežavanje", style = MaterialTheme.typography.labelMedium, color = p.dim)
        if (f.failed.isNotEmpty()) Text("Trenutno ne odgovaraju: " + f.failed.joinToString(", "), style = MaterialTheme.typography.labelMedium, color = p.dim)
    }
}
