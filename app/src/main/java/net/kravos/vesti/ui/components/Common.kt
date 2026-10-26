package net.kravos.vesti.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import net.kravos.vesti.data.Article
import net.kravos.vesti.data.Sources
import net.kravos.vesti.data.Story
import net.kravos.vesti.data.Text
import net.kravos.vesti.ui.Fmt
import net.kravos.vesti.ui.LocalApp
import net.kravos.vesti.ui.LocalNav
import net.kravos.vesti.ui.Route
import net.kravos.vesti.ui.theme.LocalPalette

/** Latinize Cyrillic when the setting is on. */
@Composable
fun lat(s: String): String {
    val on by LocalApp.current.prefs.latin.collectAsState()
    return if (on) Text.lat(s) else s
}

// ---------- images ----------

@Composable
fun NetImage(url: String?, modifier: Modifier, contentScale: ContentScale = ContentScale.Crop) {
    val ph = LocalPalette.current.placeholder
    if (url == null) { Box(modifier.background(ph)); return }
    SubcomposeAsyncImage(
        model = url,
        contentDescription = null,
        contentScale = contentScale,
        modifier = modifier.background(ph),
        loading = { Box(Modifier.background(ph)) },
        error = { Box(Modifier.background(ph)) },
    )
}

/** Wide picture for the top story; fetches og:image when the feed only has a thumbnail. */
@Composable
fun HeroImage(a: Article, fallback: String?, modifier: Modifier = Modifier) {
    val repo = LocalApp.current.repo
    var url by remember(a.id) { mutableStateOf(fallback ?: a.image) }
    LaunchedEffect(a.id) { repo.hero(a)?.let { url = it } }
    if (url == null) return
    NetImage(url, modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(4.dp)))
}

// ---------- chrome ----------

@Composable
fun Rule(modifier: Modifier = Modifier) =
    HorizontalDivider(modifier, thickness = 0.5.dp, color = LocalPalette.current.rule)

@Composable
fun Kicker(text: String, modifier: Modifier = Modifier, color: androidx.compose.ui.graphics.Color = LocalPalette.current.accent) =
    Text(text.uppercase(), modifier, style = MaterialTheme.typography.labelSmall, color = color)

@Composable
fun TopBar(title: String? = null, actions: @Composable RowScope.() -> Unit = {}) {
    val nav = LocalNav.current
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Nazad") }
            Text(
                title.orEmpty(), Modifier.weight(1f), style = MaterialTheme.typography.labelMedium,
                color = LocalPalette.current.dim, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            actions()
        }
        Rule()
    }
}

@Composable
fun Spinner(modifier: Modifier = Modifier) {
    val off = Fmt.animationsOff(LocalContext.current)
    Box(modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
        if (off) CircularProgressIndicator(progress = { 0.7f }, modifier = Modifier.size(26.dp), strokeWidth = 2.dp)
        else CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 2.dp)
    }
}

@Composable
fun ErrorBox(msg: String?, onRetry: () -> Unit) =
    Column(Modifier.fillMaxWidth().padding(36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Filled.CloudOff, null, tint = LocalPalette.current.dim, modifier = Modifier.size(32.dp))
        Spacer(Modifier.height(10.dp))
        Text("Vesti trenutno nisu dostupne", style = MaterialTheme.typography.titleMedium)
        if (!msg.isNullOrBlank()) Text(msg.take(140), style = MaterialTheme.typography.bodySmall, color = LocalPalette.current.dim, textAlign = TextAlign.Center)
        TextButton(onClick = onRetry) { Text("Pokušaj ponovo") }
    }

@Composable
fun Empty(text: String) =
    Box(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 56.dp), contentAlignment = Alignment.Center) {
        Text(text, color = LocalPalette.current.dim, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
    }

@Composable
fun LiveTag() {
    val p = LocalPalette.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(p.live))
        Spacer(Modifier.width(5.dp))
        Text("UŽIVO", style = MaterialTheme.typography.labelSmall, color = p.live)
    }
}

// ---------- meta line ----------

/** "5 izvora  N1 · Danas · Nova  ·  pre 12 min" */
@Composable
fun StoryMeta(s: Story, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val names = s.sources.map { Sources.name(it) }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (s.live) LiveTag()
        if (names.size > 1) Text(Fmt.izvora(names.size), style = MaterialTheme.typography.labelMedium, color = p.accent, fontWeight = FontWeight.SemiBold)
        Text(
            names.take(3).joinToString(" · ") + (if (names.size > 3) " +${names.size - 3}" else ""),
            Modifier.weight(1f, fill = false), style = MaterialTheme.typography.labelMedium, color = p.dim, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Text("·", style = MaterialTheme.typography.labelMedium, color = p.dim)
        Text(Fmt.ago(s.latest), style = MaterialTheme.typography.labelMedium, color = p.dim, maxLines = 1)
    }
}

// ---------- list items ----------

/** Top story: picture, big serif headline, two lines of summary. */
@Composable
fun StoryHero(s: Story, onClick: () -> Unit) {
    val a = s.lead
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 14.dp)) {
        HeroImage(a, s.image)
        Spacer(Modifier.height(12.dp))
        Text(lat(a.title), style = MaterialTheme.typography.headlineSmall)
        if (a.summary.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(lat(a.summary), style = MaterialTheme.typography.bodyMedium, color = LocalPalette.current.dim, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(10.dp))
        StoryMeta(s)
    }
}

/** Regular story row: serif headline, meta line, small square picture on the right. */
@Composable
fun StoryRow(s: Story, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 14.dp)) {
        Column(Modifier.weight(1f)) {
            Text(lat(s.lead.title), style = MaterialTheme.typography.titleMedium, maxLines = 4, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            StoryMeta(s)
        }
        val img = s.image
        if (img != null) {
            Spacer(Modifier.width(14.dp))
            NetImage(img, Modifier.size(76.dp).clip(RoundedCornerShape(4.dp)))
        }
    }
}

/** Chronological row ("Najnovije"): source + time on top, headline below. */
@Composable
fun ArticleRow(a: Article, others: Int, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 12.dp)) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (a.live) LiveTag()
                Text(Sources.name(a.sourceId).uppercase(), style = MaterialTheme.typography.labelSmall, color = p.ink)
                Text(Fmt.ago(a.published), style = MaterialTheme.typography.labelMedium, color = p.dim)
                if (others > 0) Text("+$others", style = MaterialTheme.typography.labelMedium, color = p.accent)
            }
            Spacer(Modifier.height(4.dp))
            Text(lat(a.title), style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, lineHeight = 22.sp), maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        if (a.image != null) {
            Spacer(Modifier.width(12.dp))
            NetImage(a.image, Modifier.size(56.dp).clip(RoundedCornerShape(4.dp)))
        }
    }
}

/** Tapping an article: in-app reader, or the browser for sources the reader can't handle. */
@Composable
fun rememberOpenArticle(): (Article) -> Unit {
    val nav = LocalNav.current
    val ctx = LocalContext.current
    return remember(nav, ctx) {
        { a: Article ->
            if (Sources.of(a.sourceId)?.reader == false) openUrl(ctx, a.link) else nav.go(Route.Reader(a))
        }
    }
}

fun openUrl(ctx: android.content.Context, url: String) {
    runCatching {
        ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

