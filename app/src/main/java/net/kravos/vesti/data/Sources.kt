package net.kravos.vesti.data

import net.kravos.vesti.data.Section.BEZBEDNOST
import net.kravos.vesti.data.Section.SRBIJA
import net.kravos.vesti.data.Section.SVET

/** All outlets, verified from a GitHub runner on 26 Oct 2026 (.github/probe). */
object Sources {
    private fun gn(site: String) =
        "https://news.google.com/rss/search?q=when:1d+site:$site&hl=en-US&gl=US&ceid=US:en"

    val ALL = listOf(
        // Srbija
        Source("n1", "N1", SRBIJA, listOf("https://n1info.rs/feed/"), skip = setOf("english", "najave-emisija")),
        Source("danas", "Danas", SRBIJA, listOf("https://www.danas.rs/feed/")),
        Source("nova", "Nova", SRBIJA, listOf("https://nova.rs/feed/"), skip = setOf("emisije")),
        Source("vreme", "Vreme", SRBIJA, listOf("https://vreme.com/feed/")),
        Source("radar", "Radar", SRBIJA, listOf("https://radar.nova.rs/feed/")),
        Source("insajder", "Insajder", SRBIJA, listOf("https://insajder.net/feed"), skip = setOf("prenosimo")),
        Source("krik", "KRIK", SRBIJA, listOf("https://www.krik.rs/feed/")),
        Source("cins", "CINS", SRBIJA, listOf("https://www.cins.rs/feed/")),
        Source("istinomer", "Istinomer", SRBIJA, listOf("https://www.istinomer.rs/feed/")),
        Source("bi", "Balkan Insight", SRBIJA, listOf("https://balkaninsight.com/feed/"), lang = "en"),
        // Svet
        Source("reuters", "Reuters", SVET, listOf(gn("reuters.com")), lang = "en", reader = false),
        Source("ap", "AP", SVET, listOf(gn("apnews.com")), lang = "en", reader = false),
        Source("bbc", "BBC", SVET, listOf("https://feeds.bbci.co.uk/news/world/rss.xml", "https://feeds.bbci.co.uk/news/world/europe/rss.xml"), lang = "en"),
        Source("guardian", "Guardian", SVET, listOf("https://www.theguardian.com/world/rss"), lang = "en"),
        Source("politico", "Politico Europe", SVET, listOf("https://www.politico.eu/feed/"), lang = "en"),
        Source("euronews", "Euronews", SVET, listOf("https://www.euronews.com/rss?level=theme&name=news"), lang = "en"),
        Source("aljazeera", "Al Jazeera", SVET, listOf("https://www.aljazeera.com/xml/rss/all.xml"), lang = "en"),
        Source("dw", "DW", SVET, listOf("https://rss.dw.com/xml/rss-en-world"), lang = "en"),
        Source("france24", "France 24", SVET, listOf("https://www.france24.com/en/rss"), lang = "en", defaultOn = false),
        Source("npr", "NPR", SVET, listOf("https://feeds.npr.org/1004/rss.xml"), lang = "en", defaultOn = false),
        // Bezbednost
        Source("therecord", "The Record", BEZBEDNOST, listOf("https://therecord.media/feed"), lang = "en"),
        Source("bleeping", "BleepingComputer", BEZBEDNOST, listOf("https://www.bleepingcomputer.com/feed/"), lang = "en"),
        Source("securityweek", "SecurityWeek", BEZBEDNOST, listOf("https://www.securityweek.com/feed/"), lang = "en"),
        Source("krebs", "Krebs on Security", BEZBEDNOST, listOf("https://krebsonsecurity.com/feed/"), lang = "en"),
        Source("thn", "The Hacker News", BEZBEDNOST, listOf("https://feeds.feedburner.com/TheHackersNews"), lang = "en"),
        Source("darkreading", "Dark Reading", BEZBEDNOST, listOf("https://www.darkreading.com/rss.xml"), lang = "en", reader = false),
        Source("ars", "Ars Technica", BEZBEDNOST, listOf("https://arstechnica.com/security/feed/"), lang = "en"),
    )

    private val byId = ALL.associateBy { it.id }
    fun of(id: String): Source? = byId[id]
    fun name(id: String): String = byId[id]?.name ?: id

    /** Serbian-language world sections that should land in the "Svet" tab. */
    val WORLD_TAGS = setOf("svet", "global", "world")

    /** Sport / entertainment / lifestyle, hidden when "Sakrij sport i zabavu" is on. */
    val SOFT_TAGS = setOf(
        "sport", "sport-klub", "fudbal", "košarka", "kosarka", "tenis", "odbojka", "vaterpolo", "formula 1",
        "zabava", "magazin", "showbiz", "lifestyle", "zivot", "život", "priča se", "prica-se", "horoskop", "auto",
        "radar-kolaz", "kolaž", "video", "football", "sports", "culture", "music", "film", "tv-and-radio",
        "fashion", "lifeandstyle", "travel", "food",
    )
}
