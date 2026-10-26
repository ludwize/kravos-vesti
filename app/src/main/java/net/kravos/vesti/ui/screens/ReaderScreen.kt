package net.kravos.vesti.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import net.kravos.vesti.data.Article
import net.kravos.vesti.data.ArticleBody
import net.kravos.vesti.data.Block
import net.kravos.vesti.data.Sources
import net.kravos.vesti.ui.Fmt
import net.kravos.vesti.ui.LocalApp
import net.kravos.vesti.ui.components.Kicker
import net.kravos.vesti.ui.components.LiveTag
import net.kravos.vesti.ui.components.NetImage
import net.kravos.vesti.ui.components.Rule
import net.kravos.vesti.ui.components.Spinner
import net.kravos.vesti.ui.components.TopBar
import net.kravos.vesti.ui.components.lat
import net.kravos.vesti.ui.components.openUrl
import net.kravos.vesti.ui.theme.Fonts
import net.kravos.vesti.ui.theme.LocalPalette

private val SIZES = listOf(16, 18, 20, 23)

@Composable
fun ReaderScreen(a: Article, pad: PaddingValues) {
    val app = LocalApp.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val p = LocalPalette.current
    val savedItems by app.saved.items.collectAsState()
    val isSaved = savedItems.any { it.article.id == a.id }
    val size by app.prefs.textSize.collectAsState()
    var body by remember(a.id) { mutableStateOf<ArticleBody?>(null) }
    var err by remember(a.id) { mutableStateOf<String?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    val srcName = Sources.name(a.sourceId)

    LaunchedEffect(a.id, tick) {
        err = null
        try { body = app.repo.body(a) } catch (e: CancellationException) { throw e } catch (e: Exception) { err = e.message ?: "greška" }
    }

    val fs = SIZES[size].sp
    val bodyStyle = TextStyle(fontFamily = Fonts.Serif, fontSize = fs, lineHeight = fs * 1.55f, color = p.ink)

    Column(Modifier.fillMaxSize()) {
        TopBar(srcName) {
            IconButton(onClick = { app.prefs.setTextSize((size + 1) % SIZES.size) }) { Icon(Icons.Filled.FormatSize, "Veličina teksta", tint = p.dim) }
            IconButton(onClick = { scope.launch { if (isSaved) app.saved.remove(a.id) else app.saved.save(a, body) } }) {
                Icon(if (isSaved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder, if (isSaved) "Ukloni iz sačuvanih" else "Sačuvaj", tint = if (isSaved) p.accent else p.dim)
            }
            IconButton(onClick = {
                val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, a.title).putExtra(Intent.EXTRA_TEXT, "${a.title}\n${a.link}")
                runCatching { ctx.startActivity(Intent.createChooser(i, "Podeli").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }) { Icon(Icons.Filled.Share, "Podeli", tint = p.dim) }
            IconButton(onClick = { openUrl(ctx, a.link) }) { Icon(Icons.AutoMirrored.Filled.OpenInNew, "Otvori original", tint = p.dim) }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = pad.calculateBottomPadding() + 32.dp)) {
            item("head") {
                Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp)) {
                    Row {
                        Kicker(srcName + " · " + Fmt.ago(a.published))
                        if (a.live) { Spacer(Modifier.width(10.dp)); LiveTag() }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(lat(a.title), style = MaterialTheme.typography.headlineMedium)
                    body?.author?.let { Spacer(Modifier.height(8.dp)); Text(lat(it), style = MaterialTheme.typography.labelMedium, color = p.dim) }
                    Spacer(Modifier.height(16.dp))
                }
            }
            val img = body?.image ?: a.image
            if (img != null) item("img") {
                NetImage(img, Modifier.fillMaxWidth().padding(bottom = 14.dp), ContentScale.FillWidth)
            }
            val b = body
            when {
                b == null && err == null -> item("load") { Spinner() }
                b == null || b.textLength < 300 -> item("short") {
                    Column(Modifier.padding(horizontal = 20.dp)) {
                        if (a.summary.isNotBlank()) Text(lat(a.summary), style = bodyStyle)
                        Spacer(Modifier.height(18.dp))
                        Text(
                            if (err != null) "Članak trenutno ne može da se učita." else "Ceo tekst nije dostupan u čitaču.",
                            style = MaterialTheme.typography.bodyMedium, color = p.dim,
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(onClick = { openUrl(ctx, a.link) }) { Text("Otvori na sajtu $srcName") }
                        if (err != null) OutlinedButton(onClick = { tick++ }) { Text("Pokušaj ponovo") }
                    }
                }
                else -> {
                    itemsIndexed(b.blocks, key = { i, _ -> "b$i" }) { _, bl -> BlockView(bl, bodyStyle) }
                    item("end") {
                        Column(Modifier.padding(horizontal = 20.dp, vertical = 20.dp)) {
                            Rule()
                            Spacer(Modifier.height(14.dp))
                            OutlinedButton(onClick = { openUrl(ctx, a.link) }) { Text("Pročitaj original na sajtu $srcName") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BlockView(b: Block, style: TextStyle) {
    val p = LocalPalette.current
    when (b) {
        is Block.P -> Text(lat(b.text), Modifier.padding(horizontal = 20.dp, vertical = 7.dp), style = style)
        is Block.H -> Text(lat(b.text), Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp), style = MaterialTheme.typography.titleLarge)
        is Block.Li -> Row(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
            Text("•", style = style, color = p.accent)
            Spacer(Modifier.width(10.dp))
            Text(lat(b.text), style = style)
        }
        is Block.Quote -> Row(Modifier.padding(horizontal = 20.dp, vertical = 10.dp).height(IntrinsicSize.Min)) {
            Box(Modifier.width(3.dp).fillMaxHeight().background(p.accent))
            Spacer(Modifier.width(14.dp))
            Text(lat(b.text), style = style.copy(fontStyle = FontStyle.Italic))
        }
        is Block.Img -> Column(Modifier.padding(vertical = 10.dp)) {
            NetImage(b.url, Modifier.fillMaxWidth().clip(RoundedCornerShape(0.dp)), ContentScale.FillWidth)
            b.caption?.let { Text(lat(it), Modifier.padding(horizontal = 20.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium, color = p.dim) }
        }
    }
}
