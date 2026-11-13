package net.kravos.vesti.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class SavedItem(val article: Article, val savedAt: Long)

/**
 * "Sačuvaj za kasnije": the list in saved.json, each article's extracted text in saved/<id>.json for offline reading.
 * Removals are remembered (id -> time) so a sync with the web edition can tell "removed here" from "never seen".
 */
class Saved(context: Context) {
    private val index = File(context.filesDir, "saved.json")
    private val removedFile = File(context.filesDir, "saved-removed.json")
    private val dir = File(context.filesDir, "saved").apply { mkdirs() }
    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<SavedItem>> = _items
    @Volatile private var removed: Map<String, Long> = loadRemoved()

    /** Called after every local change (the sync pushes it to the gist). */
    @Volatile var onChange: (() -> Unit)? = null

    fun isSaved(id: String) = _items.value.any { it.article.id == id }

    suspend fun save(a: Article, body: ArticleBody?) = withContext(Dispatchers.IO) {
        if (body != null) runCatching { File(dir, "${a.id}.json").writeText(Json.body(body).toString()) }
        _items.value = listOf(SavedItem(a, System.currentTimeMillis())) + _items.value.filterNot { it.article.id == a.id }
        removed = removed - a.id
        write()
        onChange?.invoke()
    }

    suspend fun remove(id: String) = withContext(Dispatchers.IO) {
        File(dir, "$id.json").delete()
        _items.value = _items.value.filterNot { it.article.id == id }
        removed = removed + (id to System.currentTimeMillis())
        write()
        onChange?.invoke()
    }

    suspend fun body(id: String): ArticleBody? = withContext(Dispatchers.IO) {
        runCatching { Json.body(JSONObject(File(dir, "$id.json").readText())) }.getOrNull()
    }

    /** Current state in the shared sync format. */
    fun snapshot(): JSONObject = Json.syncDoc(_items.value, removed)

    /**
     * Merge a remote sync document into the local list: for each id the latest action (save or remove) wins.
     * Returns true when the local list changed.
     */
    suspend fun merge(remote: JSONObject): Boolean = withContext(Dispatchers.IO) {
        val (rItems, rRemoved) = Json.parseSyncDoc(remote)
        val (items, rem) = Json.mergeSync(_items.value, removed, rItems, rRemoved)
        val changed = items.map { it.article.id to it.savedAt } != _items.value.map { it.article.id to it.savedAt } || rem != removed
        if (changed) {
            val keep = items.map { it.article.id }.toSet()
            _items.value.filter { it.article.id !in keep }.forEach { File(dir, "${it.article.id}.json").delete() }
            _items.value = items
            removed = rem
            write()
        }
        changed
    }

    private fun write() {
        val a = JSONArray()
        _items.value.forEach { a.put(Json.article(it.article).put("savedAt", it.savedAt)) }
        runCatching { index.writeText(a.toString()) }
        val r = JSONObject(); removed.forEach { (k, v) -> r.put(k, v) }
        runCatching { removedFile.writeText(r.toString()) }
    }

    private fun load(): List<SavedItem> = runCatching {
        val a = JSONArray(index.readText())
        (0 until a.length()).map { a.getJSONObject(it) }.map { SavedItem(Json.article(it), it.optLong("savedAt")) }
    }.getOrDefault(emptyList())

    private fun loadRemoved(): Map<String, Long> = runCatching {
        val o = JSONObject(removedFile.readText())
        o.keys().asSequence().associateWith { o.getLong(it) }
    }.getOrDefault(emptyMap())
}

object Json {
    /** Shared format with the web edition: {"v":1, "items":[article + savedAt], "removed":{id: ms}} */
    fun syncDoc(items: List<SavedItem>, removed: Map<String, Long>): JSONObject {
        val a = JSONArray()
        items.forEach { a.put(article(it.article).put("savedAt", it.savedAt)) }
        val r = JSONObject(); removed.forEach { (k, v) -> r.put(k, v) }
        return JSONObject().put("v", 1).put("items", a).put("removed", r)
    }

    fun parseSyncDoc(o: JSONObject): Pair<List<SavedItem>, Map<String, Long>> {
        val a = o.optJSONArray("items") ?: JSONArray()
        val items = (0 until a.length()).mapNotNull { i ->
            runCatching { a.getJSONObject(i).let { SavedItem(article(it), it.optLong("savedAt")) } }.getOrNull()
        }
        val r = o.optJSONObject("removed") ?: JSONObject()
        return items to r.keys().asSequence().associateWith { r.optLong(it) }
    }

    /** Latest action per id wins; removal marks older than 120 days are dropped. */
    fun mergeSync(
        aItems: List<SavedItem>, aRemoved: Map<String, Long>, bItems: List<SavedItem>, bRemoved: Map<String, Long>,
    ): Pair<List<SavedItem>, Map<String, Long>> {
        val removed = HashMap(aRemoved)
        bRemoved.forEach { (k, v) -> if (v > (removed[k] ?: 0)) removed[k] = v }
        val byId = HashMap<String, SavedItem>()
        (aItems + bItems).forEach { s -> val cur = byId[s.article.id]; if (cur == null || s.savedAt > cur.savedAt) byId[s.article.id] = s }
        val items = byId.values.filter { it.savedAt > (removed[it.article.id] ?: 0) }.sortedByDescending { it.savedAt }
        val keep = items.map { it.article.id }.toSet()
        val cutoff = System.currentTimeMillis() - 120L * 86400_000L
        return items to removed.filter { (k, v) -> k !in keep && v > cutoff }
    }

    fun article(a: Article): JSONObject = JSONObject()
        .put("id", a.id).put("src", a.sourceId).put("title", a.title).put("link", a.link).put("summary", a.summary)
        .put("image", a.image ?: "").put("published", a.published).put("section", a.section.name).put("live", a.live)

    fun article(o: JSONObject) = Article(
        id = o.getString("id"),
        sourceId = o.getString("src"),
        title = o.getString("title"),
        link = o.getString("link"),
        summary = o.optString("summary"),
        content = null,
        image = o.optString("image").ifEmpty { null },
        published = o.optLong("published"),
        section = runCatching { Section.valueOf(o.getString("section")) }.getOrDefault(Section.SRBIJA),
        live = o.optBoolean("live"),
        tags = emptySet(),
    )

    fun body(b: ArticleBody): JSONObject {
        val arr = JSONArray()
        b.blocks.forEach { bl ->
            arr.put(
                when (bl) {
                    is Block.P -> JSONObject().put("k", "p").put("t", bl.text)
                    is Block.H -> JSONObject().put("k", "h").put("t", bl.text)
                    is Block.Quote -> JSONObject().put("k", "q").put("t", bl.text)
                    is Block.Li -> JSONObject().put("k", "li").put("t", bl.text)
                    is Block.Img -> JSONObject().put("k", "img").put("t", bl.url).put("c", bl.caption ?: "")
                },
            )
        }
        return JSONObject().put("image", b.image ?: "").put("author", b.author ?: "").put("blocks", arr)
    }

    fun body(o: JSONObject): ArticleBody {
        val arr = o.optJSONArray("blocks") ?: JSONArray()
        val blocks = (0 until arr.length()).map { arr.getJSONObject(it) }.map { b ->
            val t = b.optString("t")
            when (b.optString("k")) {
                "h" -> Block.H(t)
                "q" -> Block.Quote(t)
                "li" -> Block.Li(t)
                "img" -> Block.Img(t, b.optString("c").ifEmpty { null })
                else -> Block.P(t)
            }
        }
        return ArticleBody(o.optString("image").ifEmpty { null }, o.optString("author").ifEmpty { null }, blocks)
    }
}
