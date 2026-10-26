package net.kravos.vesti.data

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.security.MessageDigest
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** RSS 2.0 / RDF / Atom → [Article]s. Uses jsoup's lenient XML parser so broken entities don't kill a feed. */
object Rss {
    fun parse(xml: String, src: Source): List<Article> {
        val doc = Jsoup.parse(xml, "", Parser.xmlParser())
        val items = doc.getElementsByTag("item").ifEmpty { doc.getElementsByTag("entry") }
        val now = System.currentTimeMillis()
        return items.mapNotNull { runCatching { item(it, src, now) }.getOrNull() }
    }

    private fun Element.child(tag: String): Element? = getElementsByTag(tag).firstOrNull()
    private fun Element.childText(tag: String): String = child(tag)?.text()?.trim().orEmpty()

    private fun item(it: Element, src: Source, now: Long): Article? {
        var title = Text.plain(it.childText("title"))
        if (title.isBlank()) return null
        val linkEl = it.child("link")
        var link = linkEl?.text()?.trim().orEmpty().ifEmpty { linkEl?.attr("href").orEmpty() }
        if (link.isBlank()) link = it.childText("guid")
        if (!link.startsWith("http")) return null
        link = cleanUrl(link)

        val descHtml = it.childText("description").ifEmpty { it.childText("summary") }
        val contentHtml = it.child("content:encoded")?.text()?.ifBlank { null }
            ?: it.getElementsByTag("content").firstOrNull { e -> e.attr("type").contains("html") }?.text()
        val published = date(it.childText("pubDate").ifEmpty { it.childText("dc:date") }.ifEmpty { it.childText("published") }.ifEmpty { it.childText("updated") })
            ?.coerceAtMost(now) ?: (now - 6 * 3600_000L)

        // Google News: "Headline - Reuters"
        if (src.feeds.any { f -> f.contains("news.google.com") }) title = title.replace(Regex("\\s+[-–]\\s+[^-–]{2,30}$"), "")

        var live = false
        Regex("^\\(?(BLOG|UŽIVO|UZIVO|LIVE)\\)?\\s*[:|-]?\\s*", RegexOption.IGNORE_CASE).find(title)?.let { m ->
            live = true; title = title.substring(m.range.last + 1).trim()
        }
        if (Regex("(/live/|/blog-|uzivo|uživo)", RegexOption.IGNORE_CASE).containsMatchIn(link)) live = true
        // "(VIDEO) ...", "FOTO: ..." — media labels add noise to a clean headline
        title = title.replace(Regex("^(\\(?(VIDEO|FOTO|AUDIO|FOTO/VIDEO|VIDEO/FOTO|ВИДЕО|ФОТО)\\)?\\s*[:|-]?\\s*)+", RegexOption.IGNORE_CASE), "").trim()

        val path = link.substringAfter("://").substringAfter('/').substringBefore('?').split('/').filter { s -> s.isNotBlank() && !s.all(Char::isDigit) }
        val cats = it.getElementsByTag("category").map { c -> c.text().trim().lowercase() }
        val tags = (path.dropLast(1).map { s -> s.lowercase() } + cats).toSet()

        val summary = Text.clip(Text.plain(descHtml).let { s -> if (s.startsWith(title)) s.removePrefix(title).trim() else s }, 320)
        val image = image(it, descHtml, contentHtml)

        return Article(
            id = hash(link),
            sourceId = src.id,
            title = title,
            link = link,
            summary = if (src.feeds.any { f -> f.contains("news.google.com") }) "" else summary,
            content = contentHtml,
            image = image,
            published = published,
            section = src.section,
            live = live,
            tags = tags,
        )
    }

    private fun image(it: Element, desc: String, content: String?): String? {
        val media = it.getElementsByTag("media:content").filter { m ->
            val u = m.attr("url"); val t = m.attr("type"); val md = m.attr("medium")
            u.startsWith("http") && (md == "image" || t.startsWith("image") || (md.isEmpty() && t.isEmpty() && Regex("\\.(jpe?g|png|webp)", RegexOption.IGNORE_CASE).containsMatchIn(u)))
        }.maxByOrNull { m -> m.attr("width").toIntOrNull() ?: 0 }?.attr("url")
        val thumb = it.getElementsByTag("media:thumbnail").maxByOrNull { m -> m.attr("width").toIntOrNull() ?: 0 }?.attr("url")
        val enc = it.getElementsByTag("enclosure").firstOrNull { e -> e.attr("type").startsWith("image") }?.attr("url")
        val inHtml = sequenceOf(content, desc).filterNotNull()
            .mapNotNull { h -> Regex("<img[^>]+src=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).find(h)?.groupValues?.get(1) }
            .firstOrNull { u -> u.startsWith("http") && !u.contains("feedburner") && !u.contains("pixel") }
        val url = (media ?: thumb ?: enc ?: inHtml)?.replace("&amp;", "&") ?: return null
        return upgrade(url)
    }

    /** Ask for bigger thumbnails where the CDN allows it. */
    private fun upgrade(u: String): String = when {
        u.contains("ichef.bbci.co.uk") -> u.replace(Regex("/(standard|news)/\\d+/"), "/$1/800/")
        else -> u
    }

    private val TRACK = Regex("^(utm_[a-z]+|at_medium|at_campaign|traffic_source|maca|ref|cmpid|CMP|ito)$")
    fun cleanUrl(u: String): String {
        val q = u.indexOf('?')
        if (q < 0 || u.contains("news.google.com")) return u
        val keep = u.substring(q + 1).split('&').filter { p -> p.isNotBlank() && !TRACK.matches(p.substringBefore('=')) }
        val base = u.substring(0, q)
        return if (keep.isEmpty()) base else base + "?" + keep.joinToString("&")
    }

    private val RFC = DateTimeFormatter.RFC_1123_DATE_TIME
    private val ALT = DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm:ss zzz", Locale.ENGLISH)

    fun date(s: String): Long? {
        val t = s.trim()
        if (t.isEmpty()) return null
        return runCatching { ZonedDateTime.parse(t, RFC).toInstant().toEpochMilli() }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(t).toInstant().toEpochMilli() }.getOrNull()
            ?: runCatching { Instant.parse(t).toEpochMilli() }.getOrNull()
            ?: runCatching { ZonedDateTime.parse(t, ALT).toInstant().toEpochMilli() }.getOrNull()
            ?: runCatching { ZonedDateTime.parse(t.replace(Regex("\\s+"), " "), RFC).toInstant().toEpochMilli() }.getOrNull()
    }

    fun hash(s: String): String {
        val d = MessageDigest.getInstance("SHA-1").digest(s.toByteArray())
        return d.take(8).joinToString("") { b -> "%02x".format(b) }
    }
}
