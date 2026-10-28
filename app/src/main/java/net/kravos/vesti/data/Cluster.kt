package net.kravos.vesti.data

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Groups articles about the same event into [Story]s (Ground News style).
 * Tf-idf over stemmed title (weight 2) + summary tokens, cosine similarity, greedy assignment
 * newest-first within a 36 h window. Tuned on the 26 Oct 2026 feeds (threshold 0.30).
 */
object Cluster {
    private const val THR = 0.30
    private const val WINDOW = 36 * 3600_000L

    private class Doc(val a: Article, val title: Set<String>, val vec: Map<String, Double>, val norm: Double)
    private class Group(val section: Section, val start: Long) { val docs = ArrayList<Doc>() }

    fun build(articles: List<Article>): List<Story> {
        if (articles.isEmpty()) return emptyList()
        val raw = articles.map { a ->
            val t = Text.tokens(a.title)
            val s = Text.tokens(a.summary.take(240)) - t
            Triple(a, t, s)
        }
        val df = HashMap<String, Int>()
        raw.forEach { (_, t, s) -> (t + s).forEach { df[it] = (df[it] ?: 0) + 1 } }
        val n = raw.size.toDouble()
        fun idf(t: String) = ln(1 + n / (df[t] ?: 1))
        val docs = raw.map { (a, t, s) ->
            val v = HashMap<String, Double>()
            t.forEach { v[it] = 2 * idf(it) }
            s.forEach { v[it] = (v[it] ?: 0.0) + idf(it) }
            Doc(a, t, v, sqrt(v.values.sumOf { it * it }).coerceAtLeast(1e-9))
        }.sortedByDescending { it.a.published }

        val groups = ArrayList<Group>()
        for (d in docs) {
            var best: Group? = null
            var bs = 0.0
            for (g in groups) {
                if (g.section != d.a.section || g.start - d.a.published > WINDOW) continue
                var mx = 0.0; var sum = 0.0
                for (m in g.docs) { val x = sim(d, m); sum += x; if (x > mx) mx = x }
                val avg = sum / g.docs.size
                if (mx >= THR && avg >= THR * 0.6 && mx > bs) { best = g; bs = mx }
            }
            if (best != null) best.docs.add(d) else groups.add(Group(d.a.section, d.a.published).also { it.docs.add(d) })
        }
        return groups.map { g ->
            val arts = order(g.docs.map { it.a })
            Story(g.section.name + ":" + arts.minBy { it.published }.id, g.section, arts)
        }
    }

    private fun sim(a: Doc, b: Doc): Double {
        val small = if (a.vec.size < b.vec.size) a else b
        val big = if (small === a) b else a
        var dot = 0.0; var sharedTitle = 0
        for ((t, w) in small.vec) {
            val o = big.vec[t] ?: continue
            dot += w * o
            if (t in a.title || t in b.title) sharedTitle++
        }
        if (sharedTitle < 2) return 0.0
        return dot / (a.norm * b.norm)
    }

    /** Lead article first: one with an image and a real summary from the earliest-listed source, then the rest newest-first. */
    private fun order(arts: List<Article>): List<Article> {
        if (arts.size == 1) return arts
        val rank = Sources.ALL.withIndex().associate { it.value.id to it.index }
        val lead = arts.minWith(compareBy<Article>({ if (it.image != null && it.summary.length > 40) 0 else 1 }, { rank[it.sourceId] ?: 99 }, { -it.published }))
        return listOf(lead) + arts.filter { it !== lead }.sortedByDescending { it.published }
    }

    /** Importance: coverage breadth with an ~8 h half-life; live coverage gets a nudge. */
    fun score(s: Story, now: Long): Double {
        val ageH = (now - s.latest).coerceAtLeast(0) / 3600_000.0
        val breadth = s.sources.size.toDouble().pow(1.6) + 0.15 * (s.articles.size - s.sources.size)
        return breadth * exp(-ageH * ln(2.0) / 8.0) * (if (s.live) 1.3 else 1.0)
    }
}
