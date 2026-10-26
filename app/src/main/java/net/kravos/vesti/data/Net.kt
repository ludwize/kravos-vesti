package net.kravos.vesti.data

import android.content.Context
import android.util.LruCache
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** HTTP GET with in-memory TTL cache, in-flight de-duplication and a disk copy for instant/offline paint. */
class Net(context: Context) {
    private val disk = File(context.cacheDir, "net").apply { mkdirs() }
    private val mem = LruCache<String, Pair<Long, String>>(120)
    private val inflight = ConcurrentHashMap<String, CompletableDeferred<String>>()
    private val gate = Semaphore(8)

    /** [ttl] in seconds; 0 forces a network fetch. Falls back to the last good copy on failure. [memo]=false keeps big pages out of RAM. */
    suspend fun get(url: String, ttl: Int, persist: Boolean = true, memo: Boolean = true): String {
        val now = System.currentTimeMillis()
        mem.get(url)?.let { (t, body) -> if (now - t < ttl * 1000L) return body }
        inflight[url]?.let { return it.await() }
        val d = CompletableDeferred<String>()
        val prev = inflight.putIfAbsent(url, d)
        if (prev != null) return prev.await()
        try {
            val body = gate.withPermit { Http.get(url) }
            if (memo) mem.put(url, now to body)
            if (persist) withContext(Dispatchers.IO) { runCatching { file(url).writeText(body) } }
            d.complete(body)
            return body
        } catch (e: Exception) {
            val stale = mem.get(url)?.second ?: peek(url)
            if (stale != null) { d.complete(stale); return stale }
            d.completeExceptionally(e)
            throw e
        } finally {
            inflight.remove(url)
        }
    }

    /** Last copy saved on disk, without touching the network. */
    suspend fun peek(url: String): String? = withContext(Dispatchers.IO) {
        runCatching { file(url).takeIf { it.exists() }?.readText() }.getOrNull()
    }

    fun age(url: String): Long? = file(url).takeIf { it.exists() }?.let { System.currentTimeMillis() - it.lastModified() }

    private fun file(url: String) = File(disk, Rss.hash(url) + "_" + url.replace(Regex("[^A-Za-z0-9._-]"), "_").takeLast(60))
}
