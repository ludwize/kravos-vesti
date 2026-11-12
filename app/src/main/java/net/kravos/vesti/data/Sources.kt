package net.kravos.vesti.data

import net.kravos.vesti.data.Section.AUTO
import net.kravos.vesti.data.Section.BEOGRAD
import net.kravos.vesti.data.Section.LJUBIMCI
import net.kravos.vesti.data.Section.NAUKA
import net.kravos.vesti.data.Section.SPORT
import net.kravos.vesti.data.Section.SRBIJA
import net.kravos.vesti.data.Section.SVET

/** All outlets, verified from a GitHub runner on 26 Oct 2026 (.github/probe). */
object Sources {
    /** Belgrade and its main venues: event sources that also cover other cities keep only these. */
    private val BG_PLACES = setOf(
        "beograd", "sava centr", "mts dvoran", "stark aren", "beogradska aren", "hangar", "drugstore", "dom omladin", "kolarc",
        "tasmajdan", "zappa", "barutan", "kc grad", "kombank dvoran", "usce", "kalemegdan", "ada ciganlij", "belgrade",
    )

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
        // Nauka i tehnologija (the former "Bezbednost" section; security outlets stay as its "Sajber bezbednost" category)
        Source("n1sci", "N1 SciTech", NAUKA, listOf("https://n1info.rs/magazin/scitech/feed/")),
        Source("b92tehno", "B92 Tehnopolis", NAUKA, listOf("https://www.b92.net/info/rss/tehnopolis.xml")),
        Source("benchmark", "Benchmark", NAUKA, listOf("https://benchmark.rs/feed/")),
        Source("telegraftech", "Telegraf Hi-Tech", NAUKA, listOf("https://www.telegraf.rs/rss/hi-tech")),
        Source("startit", "Startit", NAUKA, listOf("https://startit.rs/feed/")),
        Source("rtsnauka", "RTS Nauka", NAUKA, listOf("https://www.rts.rs/page/magazine/sr/rss/510/nauka.html"), only = setOf("наука", "технологија", "природа")),
        Source("ars", "Ars Technica", NAUKA, listOf("https://feeds.arstechnica.com/arstechnica/index"), lang = "en"),
        Source("verge", "The Verge", NAUKA, listOf("https://www.theverge.com/rss/index.xml"), lang = "en"),
        Source("wired", "Wired", NAUKA, listOf("https://www.wired.com/feed/rss"), lang = "en"),
        Source("mittr", "MIT Technology Review", NAUKA, listOf("https://www.technologyreview.com/feed/"), lang = "en"),
        Source("techcrunch", "TechCrunch", NAUKA, listOf("https://techcrunch.com/feed/"), lang = "en", defaultOn = false),
        Source("bbctech", "BBC Tech", NAUKA, listOf("https://feeds.bbci.co.uk/news/technology/rss.xml"), lang = "en"),
        Source("guardiantech", "Guardian Tech", NAUKA, listOf("https://www.theguardian.com/technology/rss"), lang = "en"),
        Source("newscientist", "New Scientist", NAUKA, listOf("https://www.newscientist.com/feed/home/"), lang = "en"),
        Source("sciam", "Scientific American", NAUKA, listOf("https://www.scientificamerican.com/platform/syndication/rss/"), lang = "en"),
        Source("quanta", "Quanta Magazine", NAUKA, listOf("https://www.quantamagazine.org/feed/"), lang = "en"),
        Source("sciencedaily", "ScienceDaily", NAUKA, listOf("https://www.sciencedaily.com/rss/top/science.xml"), lang = "en"),
        Source("physorg", "Phys.org", NAUKA, listOf("https://phys.org/rss-feed/"), lang = "en", defaultOn = false),
        Source("bbcsci", "BBC Science", NAUKA, listOf("https://feeds.bbci.co.uk/news/science_and_environment/rss.xml"), lang = "en"),
        Source("guardiansci", "Guardian Science", NAUKA, listOf("https://www.theguardian.com/science/rss"), lang = "en"),
        Source("space", "Space.com", NAUKA, listOf("https://www.space.com/feeds/all"), lang = "en"),
        Source("lightreading", "Light Reading", NAUKA, listOf("https://www.lightreading.com/rss.xml"), lang = "en"),
        Source("telecoms", "Telecoms.com", NAUKA, listOf("https://www.telecoms.com/rss.xml"), lang = "en"),
        Source("therecord", "The Record", NAUKA, listOf("https://therecord.media/feed"), lang = "en"),
        Source("bleeping", "BleepingComputer", NAUKA, listOf("https://www.bleepingcomputer.com/feed/"), lang = "en"),
        Source("securityweek", "SecurityWeek", NAUKA, listOf("https://www.securityweek.com/feed/"), lang = "en", defaultOn = false),
        Source("krebs", "Krebs on Security", NAUKA, listOf("https://krebsonsecurity.com/feed/"), lang = "en"),
        Source("thn", "The Hacker News", NAUKA, listOf("https://feeds.feedburner.com/TheHackersNews"), lang = "en", defaultOn = false),
        Source("darkreading", "Dark Reading", NAUKA, listOf("https://www.darkreading.com/rss.xml"), lang = "en", reader = false, defaultOn = false),
        // Beograd (dedicated city feeds; their articles also stay in Srbija when they come from a general feed)
        Source("blicbg", "Blic Beograd", BEOGRAD, listOf("https://www.blic.rs/rss/Vesti/Beograd")),
        Source("kurirbg", "Kurir Beograd", BEOGRAD, listOf("https://www.kurir.rs/rss/vesti/beograd")),
        Source("n1bg", "N1", BEOGRAD, listOf("https://n1info.rs/tag/beograd/feed/")),
        Source("novabg", "Nova", BEOGRAD, listOf("https://nova.rs/tag/beograd/feed/"), skip = setOf("vremenska-prognoza", "vremenska prognoza")),
        Source("danasbg", "Danas", BEOGRAD, listOf("https://www.danas.rs/tag/beograd/feed/")),
        Source("beobuild", "Beobuild", BEOGRAD, listOf("https://www.beobuild.rs/feed"), skip = setOf("predstavljamo")),
        // Beograd: events (what's on, concerts, fairs)
        Source("danubg", "Dan u Beogradu", BEOGRAD, listOf("https://www.danubeogradu.rs/feed/")),
        Source("headliner", "Headliner", BEOGRAD, listOf("https://headliner.rs/feed/"), mention = BG_PLACES),
        Source("n1kon", "N1", BEOGRAD, listOf("https://n1info.rs/tag/koncert/feed/"), mention = BG_PLACES),
        Source("novakon", "Nova", BEOGRAD, listOf("https://nova.rs/tag/koncert/feed/"), mention = BG_PLACES),
        Source("bgsajam", "Beogradski sajam", BEOGRAD, listOf("https://sajam.rs/feed/")),
        Source("stillinbg", "Still in Belgrade", BEOGRAD, listOf("https://stillinbelgrade.com/feed/"), lang = "en"),
        Source(
            "gnbgd", "Najave (Google vesti)", BEOGRAD,
            listOf("https://news.google.com/rss/search?q=Beograd+(koncert+OR+izlo%C5%BEba+OR+festival+OR+predstava+OR+sajam)+when:2d&hl=sr&gl=RS&ceid=RS:sr-Latn"),
            reader = false,
        ),
        // Automobili (auto pieces from N1, Nova, Danas... are routed here too)
        Source("b92auto", "B92 Automobili", AUTO, listOf("https://www.b92.net/info/rss/automobili.xml")),
        Source("kurirauto", "Kurir Auto", AUTO, listOf("https://www.kurir.rs/rss/auto")),
        Source("n1auto", "N1 Auto", AUTO, listOf("https://n1info.rs/magazin/auto/feed/")),
        Source("electrek", "Electrek", AUTO, listOf("https://electrek.co/feed/"), lang = "en"),
        Source("insideevs", "InsideEVs", AUTO, listOf("https://insideevs.com/rss/news/all/"), lang = "en"),
        Source("autocar", "Autocar", AUTO, listOf("https://www.autocar.co.uk/rss"), lang = "en"),
        Source("motor1", "Motor1", AUTO, listOf("https://www.motor1.com/rss/news/all/"), lang = "en"),
        Source("caranddriver", "Car and Driver", AUTO, listOf("https://www.caranddriver.com/rss/all.xml/"), lang = "en", defaultOn = false),
        // Ljubimci (few dedicated feeds; pet stories from every other source are copied here by PET_* words)
        Source("n1ljub", "N1", LJUBIMCI, listOf("https://n1info.rs/tag/ljubimci/feed/")),
        Source("guardianpets", "Guardian", LJUBIMCI, listOf("https://www.theguardian.com/lifeandstyle/pets/rss", "https://www.theguardian.com/world/animals/rss"), lang = "en"),
        // Sport (N1, Nova, Danas, BBC, Al Jazeera... also feed their sport pieces into this tab)
        Source("sportklub", "Sport Klub", SPORT, listOf("https://sportklub.n1info.rs/feed/")),
        Source("b92sport", "B92 Sport", SPORT, listOf("https://www.b92.net/rss/sport")),
        Source("rtssport", "RTS Sport", SPORT, listOf("https://www.rts.rs/page/sport/ci/rss.html")),
        Source("zurnal", "Žurnal", SPORT, listOf("https://www.zurnal.rs/rss")),
        Source("meridian", "Meridian Sport", SPORT, listOf("https://meridiansport.rs/feed/")),
        Source("sportske", "Sportske.net", SPORT, listOf("https://www.sportske.net/rss"), reader = false),
        Source("bbcsport", "BBC Sport", SPORT, listOf("https://feeds.bbci.co.uk/sport/rss.xml"), lang = "en"),
        Source("guardiansport", "Guardian Sport", SPORT, listOf("https://www.theguardian.com/sport/rss"), lang = "en"),
        Source("eurohoops", "Eurohoops", SPORT, listOf("https://www.eurohoops.net/en/feed/"), lang = "en"),
        Source("motorsport", "Motorsport F1", SPORT, listOf("https://www.motorsport.com/rss/f1/news/"), lang = "en"),
    )

    private val byId = ALL.associateBy { it.id }
    fun of(id: String): Source? = byId[id]
    fun name(id: String): String = byId[id]?.name ?: id

    /** Serbian-language world sections that should land in the "Svet" tab. */
    val WORLD_TAGS = setOf("svet", "global", "world")

    /** Sport pieces from general outlets move to the Sport tab. */
    val SPORT_TAGS = setOf(
        "sport", "sport-klub", "sport klub", "sports", "fudbal", "košarka", "kosarka", "tenis", "odbojka", "vaterpolo",
        "formula 1", "football", "rukomet",
    )

    /** Titles that are shopping/listing filler rather than news (deal and coupon posts, daily funeral lists...). */
    val JUNK_TITLE = Regex(
        "(coupon|promo code|discount code|black friday deal|best .{0,40} deals?\\b|% off|sahrane i kremacije)",
        RegexOption.IGNORE_CASE,
    )

    /** Car pieces from general outlets move to the Automobili tab (instead of being hidden as lifestyle). */
    val AUTO_TAGS = setOf("auto", "automobili", "automobil", "motoring", "cars", "auto moto")

    /** A title word equal to one of these (normalized, no diacritics) puts the article in Ljubimci too. */
    val PET_EXACT = setOf(
        "pas", "psa", "psi", "psu", "pse", "psima", "pasa", "psom", "kucence", "kucici", "mace", "macor", "mackica",
        "dog", "dogs", "puppy", "puppies", "cat", "cats", "kitten", "kittens", "pets",
    )
    /** ...or starts with one of these. */
    val PET_PREFIX = setOf("macka", "macke", "macku", "mackom", "mackama", "ljubimc", "ljubimac", "zivotinj", "veterin", "animal")

    /** Showbiz / lifestyle, hidden when "Sakrij zabavu i lifestyle" is on. */
    val ENT_TAGS = setOf(
        "zabava", "magazin", "showbiz", "lifestyle", "zivot", "život", "priča se", "prica-se", "horoskop", "auto",
        "radar-kolaz", "kolaž", "video", "fashion", "lifeandstyle", "travel", "food", "tv-and-radio",
    )
}
