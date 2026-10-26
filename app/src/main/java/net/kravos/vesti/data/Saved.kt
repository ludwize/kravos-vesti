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

/** "Sačuvaj za kasnije": the list in saved.json, each article's extracted text in saved/<id>.json for offline reading. */
class Saved(context: Context) {
    private val index = File(context.filesDir, "saved.json")
    private val dir = File(context.filesDir, "saved").apply { mkdirs() }
    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<SavedItem>> = _items

    fun isSaved(id: String) = _items.value.any { it.article.id == id }

    suspend fun save(a: Article, body: ArticleBody?) = withContext(Dispatchers.IO) {
        if (body != null) runCatching { File(dir, "${a.id}.json").writeText(Json.body(body).toString()) }
        _items.value = listOf(SavedItem(a, System.currentTimeMillis())) + _items.value.filterNot { it.article.id == a.id }
        write()
    }

    suspend fun remove(id: String) = withContext(Dispatchers.IO) {
        File(dir, "$id.json").delete()
        _items.value = _items.value.filterNot { it.article.id == id }
        write()
    }

    suspend fun body(id: String): ArticleBody? = withContext(Dispatchers.IO) {
        runCatching { Json.body(JSONObject(File(dir, "$id.json").readText())) }.getOrNull()
    }

    private fun write() {
        val a = JSONArray()
        _items.value.forEach { a.put(Json.article(it.article).put("savedAt", it.savedAt)) }
        runCatching { index.writeText(a.toString()) }
    }

    private fun load(): List<SavedItem> = runCatching {
        val a = JSONArray(index.readText())
        (0 until a.length()).map { a.getJSONObject(it) }.map { SavedItem(Json.article(it), it.optLong("savedAt")) }
    }.getOrDefault(emptyList())
}

object Json {
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
