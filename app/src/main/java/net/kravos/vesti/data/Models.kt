package net.kravos.vesti.data

enum class Section(val label: String) { SRBIJA("Srbija"), BEOGRAD("Beograd"), SVET("Svet"), BEZBEDNOST("Bezbednost"), SPORT("Sport") }

/**
 * A news outlet. [reader] = false means the in-app reader can't get the text (e.g. Google News
 * redirects or anti-bot pages), so tapping opens the browser instead.
 */
data class Source(
    val id: String,
    val name: String,
    val section: Section,
    val feeds: List<String>,
    val lang: String = "sr",
    val defaultOn: Boolean = true,
    val reader: Boolean = true,
    /** URL path segments to drop entirely (agency reposts, English duplicates...). */
    val skip: Set<String> = emptySet(),
)

data class Article(
    val id: String,
    val sourceId: String,
    val title: String,
    val link: String,
    val summary: String,
    /** Full text from the feed (content:encoded), when the feed carries it. */
    val content: String?,
    val image: String?,
    val published: Long,
    val section: Section,
    val live: Boolean,
    /** Lower-cased categories + URL path segments, used for filtering. */
    val tags: Set<String>,
    /** Category ids from [Categories], assigned when the feed is rebuilt. */
    val cats: Set<String> = emptySet(),
)

data class Story(val id: String, val section: Section, val articles: List<Article>) {
    val lead: Article get() = articles.first()
    val sources: List<String> get() = articles.map { it.sourceId }.distinct()
    val latest: Long get() = articles.maxOf { it.published }
    val first: Long get() = articles.minOf { it.published }
    val image: String? get() = articles.firstNotNullOfOrNull { it.image }
    val live: Boolean get() = articles.any { it.live }
    val cats: Set<String> get() = articles.flatMapTo(HashSet()) { it.cats }
}

data class Feed(
    val articles: List<Article>,
    val stories: Map<Section, List<Story>>,
    val failed: List<String>,
    val fetchedAt: Long,
) {
    fun story(id: String): Story? = stories.values.firstNotNullOfOrNull { l -> l.firstOrNull { it.id == id } }
    fun storyOf(articleId: String): Story? = stories.values.firstNotNullOfOrNull { l -> l.firstOrNull { s -> s.articles.any { it.id == articleId } } }
}

sealed class Block {
    data class P(val text: String) : Block()
    data class H(val text: String) : Block()
    data class Quote(val text: String) : Block()
    data class Li(val text: String) : Block()
    data class Img(val url: String, val caption: String?) : Block()
}

data class ArticleBody(val image: String?, val author: String?, val blocks: List<Block>) {
    val textLength: Int get() = blocks.sumOf { b ->
        when (b) { is Block.P -> b.text.length; is Block.Quote -> b.text.length; is Block.Li -> b.text.length; is Block.H -> b.text.length; else -> 0 }
    }
}
