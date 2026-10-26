package net.kravos.vesti.data

import org.jsoup.Jsoup
import java.text.Normalizer

object Text {
    private val CYR: Map<Char, String> = run {
        val cyr = "абвгдђежзијклљмнњопрстћуфхцчџш"
        val lat = listOf("a", "b", "v", "g", "d", "đ", "e", "ž", "z", "i", "j", "k", "l", "lj", "m", "n", "nj", "o", "p", "r", "s", "t", "ć", "u", "f", "h", "c", "č", "dž", "š")
        val m = HashMap<Char, String>()
        cyr.forEachIndexed { i, c ->
            m[c] = lat[i]
            m[c.uppercaseChar()] = lat[i].replaceFirstChar { it.uppercaseChar() }
        }
        m
    }

    fun hasCyrillic(s: String) = s.any { it in 'Ѐ'..'ӿ' }

    /** Serbian Cyrillic → Latin. "ЉУБАВ" → "LJUBAV", "Љубав" → "Ljubav". */
    fun lat(s: String): String {
        if (!hasCyrillic(s)) return s
        val sb = StringBuilder(s.length + 8)
        for (i in s.indices) {
            val c = s[i]
            val r = CYR[c]
            if (r == null) { sb.append(c); continue }
            if (r.length == 2 && c.isUpperCase()) {
                val nextUpper = s.getOrNull(i + 1)?.isUpperCase() == true
                val prevUpper = s.getOrNull(i - 1)?.isUpperCase() == true
                sb.append(if (nextUpper || prevUpper) r.uppercase() else r)
            } else sb.append(r)
        }
        return sb.toString()
    }

    /** Lowercase ASCII-ish form used for matching: latinized, diacritics removed, đ→dj. */
    fun norm(s: String): String {
        val l = lat(s).lowercase().replace("đ", "dj")
        return Normalizer.normalize(l, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
    }

    private val STOP = """
        i u na je da se za od sa su o po iz kao što sto koji koja koje kojeg kojem kojima ali ili a to ne bi će ce biti bio bila bilo
        smo ste sam nije nisu još jos već vec kod do kad kada ovo ta taj te ti tu pre posle nakon zbog bez preko protiv između izmedju
        oko prema kroz pod nad pri uz ga mu joj ih im ju me mi vi on ona ono oni one njegov njen njihov ima imaju može moze mogu treba
        sve svi sva ovaj ova ove ovi tog toga tome tim takođe takodje jer pa dok ako samo vise više manje jedan jedna jedno dva tri
        godine godina dan danas juče juce sutra kaže kaze rekao rekla izjavio izjavila navodi navode the an of to in on for and or is
        are was were be by with at from as that this it its has have had not but after over new says said will would could than into
        about more up out who what how why when where his her their they he she we you our your vs video foto uživo uzivo live blog
        watch photos update updates latest
    """.trim().split(Regex("\\s+")).map { norm(it) }.toSet()

    /** Matching tokens: stopwords dropped, words cut to a 5-char stem (handles Serbian cases and English plurals). */
    fun tokens(s: String): Set<String> {
        val out = HashSet<String>()
        Regex("[a-z0-9]+").findAll(norm(s)).forEach { m ->
            val w = m.value
            if (w in STOP) return@forEach
            if (w.all { it.isDigit() }) { if (w.length >= 3) out.add(w); return@forEach }
            if (w.length < 3) return@forEach
            out.add(if (w.length > 5) w.substring(0, 5) else w)
        }
        return out
    }

    /** HTML fragment → plain text, whitespace collapsed. */
    fun plain(html: String?): String {
        if (html.isNullOrBlank()) return ""
        val t = if (html.contains('<') || html.contains('&')) Jsoup.parse(html).text() else html
        return t.replace(Regex("\\s+"), " ").trim()
    }

    fun clip(s: String, max: Int): String {
        if (s.length <= max) return s
        val cut = s.substring(0, max)
        val sp = cut.lastIndexOf(' ')
        return (if (sp > max / 2) cut.substring(0, sp) else cut).trimEnd(',', ';', ':', ' ') + "…"
    }
}
