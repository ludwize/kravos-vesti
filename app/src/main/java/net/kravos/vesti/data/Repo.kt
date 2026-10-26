package net.kravos.vesti.data

import android.content.Context
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class Repo(context: Context, private val prefs: Prefs, private val saved: Saved) {
    private val net = Net(context)
    private val raw = ConcurrentHashMap<String, List<Article>>()
    private val failed = ConcurrentHashMap.newKeySet<String>()
    private val bodies = LruCache<String, ArticleBody>(40)
    private val heroes = ConcurrentHashMap<String, String>()
    private val lock = Mutex()

    private val _feed = MutableStateFlow<Feed?>(null)
    val feed: StateFlow<Feed?> = _feed
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing

    /** Paint whatever is on disk from the last session, before the network answers. */
    suspend fun warmStart() {
        if (_feed.value != null) return
        coroutineScope {
            enabled().map { s ->
                async(Dispatchers.IO) {
                    val list = s.feeds.flatMap { url -> net.peek(url)?.let { runCatching { Rss.parse(it, s) }.getOrNull() }.orEmpty() }
                    if (list.isNotEmpty()) raw.putIfAbsent(s.id, list)
                }
            }.awaitAll()
        }
        if (raw.isNotEmpty() && _feed.value == null) rebuild()
    }

    /** Fetch every enabled source in parallel. [force] skips the 4-minute cache (pull-to-refresh). */
    suspend fun refresh(force: Boolean = false) = lock.withLock {
        _refreshing.value = true
        try {
            coroutineScope {
                enabled().map { s -> async { fetch(s, if (force) 0 else 240) } }.awaitAll()
            }
            rebuild()
        } finally {
            _refreshing.value = false
        }
    }

    private suspend fun fetch(s: Source, ttl: Int) {
        var ok = false
        val all = ArrayList<Article>()
        for (url in s.feeds) {
            try {
                val xml = net.get(url, ttl)
                all += withContext(Dispatchers.Default) { Rss.parse(xml, s) }
                ok = true
            } catch (e: Exception) {
                Log.w(Http.TAG, "feed ${s.id} failed: ${e.message}")
            }
        }
        if (ok) { raw[s.id] = all; failed.remove(s.id) } else failed.add(s.id)
    }

    private fun enabled(): List<Source> = prefs.sources.value.let { on -> Sources.ALL.filter { it.id in on } }

    /** Re-apply filters and grouping (also called when settings change; no network). */
    suspend fun rebuild() = withContext(Dispatchers.Default) {
        val now = System.currentTimeMillis()
        val on = prefs.sources.value
        val hideSoft = prefs.hideSoft.value
        val seen = HashSet<String>()
        val seenTitle = HashSet<String>()
        val arts = ArrayList<Article>()
        for ((sid, list) in raw) {
            if (sid !in on) continue
            val src = Sources.of(sid) ?: continue
            for (a0 in list) {
                if (a0.tags.any { it in src.skip }) continue
                if (hideSoft && a0.tags.any { it in Sources.SOFT_TAGS }) continue
                val maxAge = if (src.section == Section.BEZBEDNOST) 7 * 86400_000L else 3 * 86400_000L
                if (now - a0.published > maxAge) continue
                val a = if (src.section == Section.SRBIJA && src.lang == "sr" && a0.tags.any { it in Sources.WORLD_TAGS }) a0.copy(section = Section.SVET) else a0
                if (!seen.add(a.id)) continue
                if (!seenTitle.add(sid + "|" + Text.norm(a.title))) continue
                arts += a
            }
        }
        arts.sortByDescending { it.published }
        val stories = Cluster.build(arts)
            .groupBy { it.section }
            .mapValues { (_, l) -> l.sortedByDescending { Cluster.score(it, now) } }
        _feed.value = Feed(arts, stories, failed.mapNotNull { Sources.of(it)?.name }.sorted(), now)
    }

    /** Reader text: saved copy → full text in the feed → extracted from the page. */
    suspend fun body(a: Article): ArticleBody {
        bodies.get(a.id)?.let { return it }
        saved.body(a.id)?.let { bodies.put(a.id, it); return it }
        val fromFeed = a.content?.let { c -> withContext(Dispatchers.Default) { Extract.fromFragment(c, a.link, a.title) } }
            ?.takeIf { it.textLength > 1200 }
        val b = fromFeed ?: run {
            val html = net.get(a.link, 6 * 3600, persist = false, memo = false)
            withContext(Dispatchers.Default) { Extract.fromPage(html, a.link, a.title) }
        }
        val withImage = if (b.image == null && a.image != null) b.copy(image = a.image) else b
        bodies.put(a.id, withImage)
        withImage.image?.let { heroes[a.id] = it }
        return withImage
    }

    /** A big picture for hero cards: the feed's image unless it's a tiny thumbnail, else og:image from the page. */
    suspend fun hero(a: Article): String? {
        heroes[a.id]?.let { return it }
        if (a.image != null && !isSmall(a.image)) return a.image
        val src = Sources.of(a.sourceId)
        if (src?.reader == false) return a.image
        val og = runCatching {
            val html = net.get(a.link, 6 * 3600, persist = false, memo = false)
            OG.find(html)?.groupValues?.get(1) ?: OG2.find(html)?.groupValues?.get(1)
        }.getOrNull()?.replace("&amp;", "&")
        val r = og ?: a.image
        if (r != null) heroes[a.id] = r
        return r
    }

    private fun isSmall(u: String) = u.contains("i.guim.co.uk") && u.contains("width=140")

    private companion object {
        val OG = Regex("<meta[^>]+property=[\"']og:image[\"'][^>]*content=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
        val OG2 = Regex("<meta[^>]+content=[\"']([^\"']+)[\"'][^>]*property=[\"']og:image[\"']", RegexOption.IGNORE_CASE)
    }
}
