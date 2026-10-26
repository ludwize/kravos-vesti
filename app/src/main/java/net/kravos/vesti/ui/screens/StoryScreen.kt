package net.kravos.vesti.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.kravos.vesti.data.Article
import net.kravos.vesti.data.Sources
import net.kravos.vesti.ui.Fmt
import net.kravos.vesti.ui.LocalApp
import net.kravos.vesti.ui.LocalNav
import net.kravos.vesti.ui.Route
import net.kravos.vesti.ui.components.Empty
import net.kravos.vesti.ui.components.HeroImage
import net.kravos.vesti.ui.components.Kicker
import net.kravos.vesti.ui.components.LiveTag
import net.kravos.vesti.ui.components.Rule
import net.kravos.vesti.ui.components.TopBar
import net.kravos.vesti.ui.components.lat
import net.kravos.vesti.ui.components.rememberOpenArticle
import net.kravos.vesti.ui.theme.LocalPalette

/** One event, every outlet's version side by side (Ground News style). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StoryScreen(id: String, pad: PaddingValues) {
    val app = LocalApp.current
    val nav = LocalNav.current
    val feed by app.repo.feed.collectAsState()
    val s = feed?.story(id)
    val open = rememberOpenArticle()
    val p = LocalPalette.current

    Column(Modifier.fillMaxSize()) {
        TopBar(s?.section?.label)
        if (s == null) { Empty("Ova priča više nije u feedu."); return }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = pad.calculateBottomPadding() + 24.dp)) {
            item("head") {
                Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (s.live) { LiveTag(); Spacer(Modifier.padding(4.dp)) }
                        Kicker(Fmt.izvora(s.sources.size) + " · prvi izveštaj " + Fmt.ago(s.first))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(lat(s.lead.title), style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(14.dp))
                    HeroImage(s.lead, s.image)
                    Spacer(Modifier.height(16.dp))
                    Text("PRATE PRIČU", style = MaterialTheme.typography.labelSmall, color = p.dim)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        s.sources.forEach { sid ->
                            Surface(
                                shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.background,
                                border = BorderStroke(1.dp, p.rule), modifier = Modifier.clickable { nav.go(Route.SourceFeed(sid)) },
                            ) {
                                Text(Sources.name(sid), Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Rule(Modifier.padding(horizontal = 18.dp))
                Text(
                    "Kako ko piše", Modifier.padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 4.dp),
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            items(s.articles, key = { it.id }) { a ->
                Coverage(a) { open(a) }
                Rule(Modifier.padding(horizontal = 18.dp))
            }
        }
    }
}

@Composable
private fun Coverage(a: Article, onClick: () -> Unit) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(Sources.name(a.sourceId).uppercase(), style = MaterialTheme.typography.labelSmall, color = p.accent)
            Text(Fmt.ago(a.published), style = MaterialTheme.typography.labelMedium, color = p.dim)
            if (a.live) LiveTag()
        }
        Spacer(Modifier.height(5.dp))
        Text(lat(a.title), style = MaterialTheme.typography.titleMedium)
        if (a.summary.isNotBlank()) {
            Spacer(Modifier.height(5.dp))
            Text(lat(a.summary), style = MaterialTheme.typography.bodyMedium, color = p.dim, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}
