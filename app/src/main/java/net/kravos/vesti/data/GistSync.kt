package net.kravos.vesti.data

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class SyncState(val connected: Boolean, val lastSync: Long?, val error: String?, val busy: Boolean = false)

/**
 * Keeps "Sačuvano" in step with the web edition through one secret GitHub gist (file kravos-vesti-saved.json).
 * The token is a fine-grained PAT with only "Gists: read and write"; it lives in the app's private prefs.
 */
class GistSync(private val prefs: Prefs, private val saved: Saved) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    private val _state = MutableStateFlow(SyncState(prefs.syncToken != null, prefs.syncLast.takeIf { it > 0 }, null))
    val state: StateFlow<SyncState> = _state
    private var pending: Job? = null

    init {
        // push local changes a moment after they happen (several quick taps become one write)
        saved.onChange = {
            if (prefs.syncToken != null) {
                pending?.cancel()
                pending = scope.launch { delay(1500); sync() }
            }
        }
    }

    suspend fun connect(token: String): Boolean {
        prefs.syncToken = token.trim()
        prefs.syncGistId = null
        val ok = sync()
        if (!ok) { prefs.syncToken = null; _state.value = _state.value.copy(connected = false) }
        return ok
    }

    fun disconnect() {
        prefs.syncToken = null; prefs.syncGistId = null; prefs.syncLast = 0
        _state.value = SyncState(false, null, null)
    }

    /** Pull, merge, push if needed. Safe to call often; returns false on error. */
    suspend fun sync(): Boolean = lock.withLock {
        val token = prefs.syncToken ?: return@withLock false
        _state.value = _state.value.copy(busy = true)
        try {
            val id = prefs.syncGistId ?: findOrCreate(token).also { prefs.syncGistId = it }
            val remote = readGist(token, id)
            if (remote == null) {
                // gist deleted on GitHub: start over with a new one next time
                prefs.syncGistId = null
                error("gist nije pronađen")
            }
            saved.merge(remote)
            val local = saved.snapshot()
            if (!sameDoc(local, remote)) writeGist(token, id, local)
            prefs.syncLast = System.currentTimeMillis()
            _state.value = SyncState(true, prefs.syncLast, null)
            true
        } catch (e: Exception) {
            Log.w(Http.TAG, "sync failed: ${e.message}")
            _state.value = SyncState(prefs.syncToken != null, prefs.syncLast.takeIf { it > 0 }, e.message ?: "greška")
            false
        }
    }

    private fun sameDoc(a: JSONObject, b: JSONObject): Boolean {
        fun key(o: JSONObject): String {
            val (items, removed) = Json.parseSyncDoc(o)
            return items.sortedBy { it.article.id }.joinToString { it.article.id + ":" + it.savedAt } + "|" + removed.toSortedMap()
        }
        return key(a) == key(b)
    }

    private suspend fun call(token: String, method: String, url: String, body: JSONObject? = null): Pair<Int, String> =
        withContext(Dispatchers.IO) {
            val b = Request.Builder().url(url)
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
            val rb = body?.toString()?.toRequestBody("application/json".toMediaType())
            when (method) { "POST" -> b.post(rb!!); "PATCH" -> b.patch(rb!!); else -> b.get() }
            Http.client().newCall(b.build()).execute().use { r -> r.code to (r.body?.string() ?: "") }
        }

    private fun check(code: Int, body: String) {
        if (code == 401) error("token nije ispravan ili je istekao")
        if (code == 403 || code == 404) error("token nema dozvolu za gistove (Gists: Read and write)")
        if (code !in 200..299) error("GitHub greška $code: ${body.take(80)}")
    }

    private suspend fun findOrCreate(token: String): String {
        for (page in 1..5) {
            val (code, body) = call(token, "GET", "https://api.github.com/gists?per_page=100&page=$page")
            check(code, body)
            val arr = JSONArray(body)
            for (i in 0 until arr.length()) {
                val g = arr.getJSONObject(i)
                if (g.optJSONObject("files")?.has(FILE) == true) return g.getString("id")
            }
            if (arr.length() < 100) break
        }
        val doc = JSONObject()
            .put("description", "Kravos Vesti – sačuvani članci (sinhronizacija aplikacije i weba)")
            .put("public", false)
            .put("files", JSONObject().put(FILE, JSONObject().put("content", saved.snapshot().toString(1))))
        val (code, body) = call(token, "POST", "https://api.github.com/gists", doc)
        check(code, body)
        return JSONObject(body).getString("id")
    }

    private suspend fun readGist(token: String, id: String): JSONObject? {
        val (code, body) = call(token, "GET", "https://api.github.com/gists/$id")
        if (code == 404) return null
        check(code, body)
        val f = JSONObject(body).optJSONObject("files")?.optJSONObject(FILE) ?: return JSONObject()
        val content = if (f.optBoolean("truncated")) {
            withContext(Dispatchers.IO) {
                Http.client().newCall(Request.Builder().url(f.getString("raw_url")).header("Authorization", "Bearer $token").build())
                    .execute().use { it.body?.string() ?: "{}" }
            }
        } else f.optString("content", "{}")
        return runCatching { JSONObject(content) }.getOrDefault(JSONObject())
    }

    private suspend fun writeGist(token: String, id: String, doc: JSONObject) {
        val body = JSONObject().put("files", JSONObject().put(FILE, JSONObject().put("content", doc.toString(1))))
        val (code, resp) = call(token, "PATCH", "https://api.github.com/gists/$id", body)
        check(code, resp)
    }

    companion object { const val FILE = "kravos-vesti-saved.json" }
}
