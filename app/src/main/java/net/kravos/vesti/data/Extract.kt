package net.kravos.vesti.data

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.util.IdentityHashMap

/**
 * Reader mode: finds the article body in a page and turns it into clean [Block]s.
 * Heuristic: score every <p> by length/commas (minus link density), then take the deepest
 * element that holds ≥60% of all paragraph weight. Tested on all sources in .github/probe.
 */
object Extract {
    private val STRIP = "script,style,noscript,iframe,form,nav,header,footer,aside,button,svg,select,input,template,object,video,audio"
    private val JUNK = Regex(
        "(share|social|related|comment|newsletter|promo|advert|\\bad-|-ad\\b|\\bads?\\b|banner|sidebar|recommend|subscribe|cookie|popup|modal|" +
            "breadcrumb|author-box|read-?more|most-?read|menu|navbar|footer|widget|outbrain|taboola|sponsor|tweet|instagram|tiktok|embed|toolbar|" +
            "paywall|donat|webinar)",
        RegexOption.IGNORE_CASE,
    )
    /** Everything from one of these lines on is page furniture. */
    private val END = Regex(
        "^(related( articles| topics| stories| content)?:?|povezane vesti|povezani tekstovi|pročitajte (još|i)|procitajte (jos|i)|more on this story|" +
            "read more:?|preporučujemo|preporucujemo|keywords for this article|tags?:)$",
        RegexOption.IGNORE_CASE,
    )
    private val BOILER = Regex(
        "^(pratite nas|watch:|to display this content|if you rely on|subscribe|prijavite se|neko mora da vas|follow us|sign up|" +
            "podržite|podrzite|ukoliko želite|ukoliko zelite|share this|copyright|©|getty images|photo:|foto:|izvor:)",
        RegexOption.IGNORE_CASE,
    )

    fun fromPage(html: String, url: String, title: String): ArticleBody {
        val doc = Jsoup.parse(html, url)
        fun meta(k: String) = doc.selectFirst("meta[property=$k], meta[name=$k]")?.attr("content")?.takeIf { it.isNotBlank() }
        val image = meta("og:image")?.let { if (it.startsWith("//")) "https:$it" else it }
        val author = meta("author")?.takeIf { !it.startsWith("http") && it.length < 80 }
        doc.select(STRIP).remove()

        val score = IdentityHashMap<Element, Double>()
        var total = 0.0
        for (p in doc.select("p")) {
            val v = pScore(p)
            if (v <= 0) continue
            total += v
            for (a in p.parents()) score[a] = (score[a] ?: 0.0) + v
        }
        if (total == 0.0) return ArticleBody(image, author, emptyList())
        val best = score.entries
            .filter { it.value >= 0.6 * total && it.key.tagName() != "html" && it.key.tagName() != "#root" }
            .maxByOrNull { it.key.parents().size }?.key ?: return ArticleBody(image, author, emptyList())

        for (t in best.select("*").toList()) {
            if (t === best || t.parent() == null) continue
            val cid = t.className() + " " + t.id()
            if (cid.isNotBlank() && JUNK.containsMatchIn(cid) && t.select("p").size < 3) t.remove()
        }
        return ArticleBody(image, author, clean(blocks(best), title, image))
    }

    /** Full text that came inside the feed (content:encoded). */
    fun fromFragment(html: String, url: String, title: String): ArticleBody {
        val doc = Jsoup.parseBodyFragment(html, url)
        doc.select(STRIP).remove()
        return ArticleBody(null, null, clean(blocks(doc.body()), title, null))
    }

    private fun pScore(p: Element): Double {
        val tx = p.text().trim()
        if (tx.length < 30) return 0.0
        val links = p.select("a").sumOf { it.text().length }
        val density = links.toDouble() / tx.length
        return (1 + minOf(tx.length, 600) / 100.0 + tx.count { it == ',' }) * (1 - density * 1.5).coerceAtLeast(0.0)
    }

    private fun blocks(root: Element): List<Block> {
        val out = ArrayList<Block>()
        val seenImg = HashSet<String>()
        for (el in root.select("p, h2, h3, h4, blockquote, li, img")) {
            when (el.tagName()) {
                "img" -> {
                    val src = imgSrc(el) ?: continue
                    if (!seenImg.add(src)) continue
                    val cap = el.closest("figure")?.selectFirst("figcaption")?.text()?.trim()?.takeIf { it.isNotBlank() }
                    out.add(Block.Img(src, cap))
                }
                else -> {
                    if (el.tagName() == "p" && el.closest("blockquote, li") != null) continue
                    if (el.tagName() != "p" && el.parents().any { it.tagName() == "li" || it.tagName() == "blockquote" }) continue
                    if (el.closest("figcaption") != null) continue
                    val tx = el.text().replace(' ', ' ').replace(Regex("\\s+"), " ").trim()
                    if (tx.isEmpty()) continue
                    val links = el.select("a").sumOf { it.text().length }
                    if (tx.length < 160 && links > 0.6 * tx.length) continue
                    out.add(
                        when (el.tagName()) {
                            "p" -> Block.P(tx)
                            "blockquote" -> Block.Quote(tx)
                            "li" -> Block.Li(tx)
                            else -> Block.H(tx)
                        },
                    )
                }
            }
        }
        return out
    }

    private fun imgSrc(el: Element): String? {
        val cands = listOf("data-src", "data-lazy-src", "data-original", "src").map { el.absUrl(it).ifEmpty { el.attr(it) } } +
            el.attr("srcset").split(',').map { it.trim().substringBefore(' ') }.filter { it.isNotBlank() }.reversed().take(1)
        val u = cands.firstOrNull { it.startsWith("http") && !it.startsWith("data:") } ?: return null
        if (Regex("(logo|icon|avatar|pixel|1x1|spacer|\\.svg|blank\\.gif|gravatar)", RegexOption.IGNORE_CASE).containsMatchIn(u)) return null
        val w = el.attr("width").toIntOrNull()
        if (w != null && w < 200) return null
        return u
    }

    private fun clean(blocks: List<Block>, title: String, hero: String?): List<Block> {
        val out = ArrayList<Block>()
        val tNorm = Text.norm(title)
        for (b in blocks) {
            val t = when (b) { is Block.P -> b.text; is Block.H -> b.text; is Block.Quote -> b.text; is Block.Li -> b.text; is Block.Img -> null }
            if (t != null) {
                if (END.matches(t.trim())) break
                if (BOILER.containsMatchIn(t.trim())) continue
                if (Text.norm(t) == tNorm) continue
                if (b is Block.H && t.length > 160) { out.add(Block.P(t)); continue }
            }
            if (b is Block.Img && hero != null && sameImage(b.url, hero)) continue
            out.add(b)
        }
        // trailing images are usually "more stories" thumbnails
        while (out.isNotEmpty() && out.last() is Block.Img) out.removeAt(out.lastIndex)
        return out
    }

    private fun sameImage(a: String, b: String): Boolean {
        fun key(u: String) = u.substringBefore('?').substringAfterLast('/').substringBefore('.').take(24)
        return key(a) == key(b)
    }
}
