package net.kravos.vesti.data

import net.kravos.vesti.data.Section.AUTO
import net.kravos.vesti.data.Section.BEOGRAD
import net.kravos.vesti.data.Section.LJUBIMCI
import net.kravos.vesti.data.Section.NAUKA
import net.kravos.vesti.data.Section.SPORT
import net.kravos.vesti.data.Section.SRBIJA
import net.kravos.vesti.data.Section.SVET

/**
 * A topic inside a section. An article belongs to it when one of its RSS categories / URL
 * sections is in [tags], its source is in [sources], or a title word starts with one of [words]
 * ("=word" means the exact word). An article can be in several categories; the rest show under "Sve".
 * Rules were checked on the 26 Oct 2026 feeds: ~80% of articles get at least one category.
 */
class Cat(val id: String, val label: String, val section: Section, tags: Set<String>, words: String, val sources: Set<String> = emptySet()) {
    val tags: Set<String> = tags.map { Text.norm(it) }.toSet()
    val exact: Set<String> = words.split(' ').filter { it.startsWith("=") }.map { it.drop(1) }.toSet()
    val prefixes: List<String> = words.split(' ').filter { it.isNotBlank() && !it.startsWith("=") }
}

object Categories {
    private const val REGION = "hrvatsk bosn crnogorsk =crna =crne podgoric sloven makedon kosov pristin sarajev zagreb ljubljan dodik plenkovic milanovic mostar =banjaluka"

    val ALL = listOf(
        // Srbija
        Cat("politika", "Politika", SRBIJA, setOf("politika", "izbori", "izbori26", "izbori 2026", "izbori-2026", "skupština srbije", "vanredni parlamentarni izbori"),
            "izbor izborn skupstin =vlada =vlade =vladi =vladu vucic =sns =sps =rik ministar ministr premijer predsedni stranka stranke stranci opozicij poslani koalicij glasanj glasal glasac birac birack referendum mandat"),
        Cat("drustvo", "Društvo", SRBIJA, setOf("društvo", "život"),
            "obrazovanj skol ucitelj univerzitet fakultet zdravstv bolnic lekar penzij penzion migrant roditelj =dece =decu =deca saobracaj protest"),
        Cat("ekonomija", "Ekonomija", SRBIJA, setOf("ekonomija", "biznis", "economy", "business"),
            "=plata =plate =zarada zarad =cene =cena =cenama inflacij dinar =evra =evro =nbs bank budzet investic izvoz uvoz =bdp kredit porez =struja =gas =nis privred kompanij =firma =firme fabrik"),
        Cat("hronika", "Hronika", SRBIJA, setOf("hronika", "crna hronika"),
            "uhapsen hapsen ubist ubijen ubio pucnjav nesrec saobracajn policij policaj tuzilast tuzil osumnjic pritvor presud optuz krivicn napadnut nozem"),
        Cat("kultura", "Kultura", SRBIJA, setOf("kultura", "kultura-radar", "culture"),
            "film pozorist koncert festival knjig izlozb muzej roman pisac pisca glumac glumic reditelj bioskop"),
        Cat("misljenja", "Mišljenja", SRBIJA, setOf("mišljenja", "komentar", "kolumne", "analize", "analiza", "radar forum"), ""),
        Cat("region", "Region", SRBIJA, setOf("region"), REGION),
        Cat("istrazivanja", "Istraživanja", SRBIJA, setOf("istraživanja", "viralne manipulacije"), "", setOf("krik", "cins", "istinomer")),
        // Svet
        Cat("evropa", "Evropa", SVET, emptySet(),
            "=eu =evropska =evropske =evropski evrop europ brisel brussels nemack german berlin merz francusk france french pariz paris macron ital spanij spain spanish poljsk poland polish hungar madjar orban austri holand dutch netherland belgi greek grck bulgar bugar romani rumun britan british london starmer ireland irsk sweden svedsk swedish norw finsk finland denmark dansk danish portug czech cesk slovak"),
        Cat("rusija", "Rusija i Ukrajina", SVET, emptySet(),
            "rusij ruski ruska russia russian ukrajin ukrain kremlj kremlin putin zelensk kijev kyiv moskv moscow donbas harkov kharkiv odes zaporo"),
        Cat("bliski", "Bliski istok", SVET, emptySet(),
            "izrael israel =gaza =gazi =gazu =gaze palestin =iran iranian iransk liban leban sirij syria jemen yemen hamas hezbolah hezbollah netanyah saudi saudij =iraq =irak jordan egypt egipat egipt erdogan turkish tursk turkey katar qatar hebron houthi"),
        Cat("sad", "SAD", SVET, emptySet(),
            "=sad =us =usa america amerik trump tramp washington vasington congress kongres senat biden pentagon republican republikan democrat demokrat =fbi =cia michigan california texas midterm"),
        Cat("azija", "Azija", SVET, emptySet(),
            "china chinese =kina =kine =kini =kinu kines =xi japan korea korej =kore india indij taiwan tajvan pakistan afghan avganist vietnam vijetnam indonez philippin filipin beijing peking myanmar thailand tajland"),
        Cat("afrika", "Afrika", SVET, emptySet(),
            "africa african afrik nigeri sudan congo kongo ethiop etiop somali kenya keni uganda algeri alzir =niger =mali sahel ebola"),
        Cat("latam", "Latinska Amerika", SVET, emptySet(),
            "brazil venezuel mexic meksik argentin colombi kolumbij chile =cile =peru cuba =kuba lula bolsonaro machado maduro ecuador ekvador"),
        Cat("region_s", "Region", SVET, setOf("region"), REGION),
        Cat("ekonomija_s", "Ekonomija", SVET, setOf("economy", "business", "ekonomija", "biznis"),
            "econom ekonom market trzist inflation inflacij tariff carin =imf =mmf stock berz =oil naft trade trgovin =gdp =ecb =fed"),
        Cat("tehnologija", "Tehnologija", SVET, setOf("technology", "tehnologija", "scitech"),
            "=ai artificial vestack tehnolog technolog google apple openai microsoft =chip chips nvidia"),
        Cat("klima", "Klima i katastrofe", SVET, emptySet(),
            "climate klim flood poplav earthquake zemljotres hurricane uragan wildfire pozar drought susa storm oluj typhoon tajfun"),
        // Nauka i tehnologija
        Cat("ai", "Veštačka inteligencija", NAUKA, setOf("ai", "artificial intelligence"),
            "=ai =llm llms chatgpt openai gemini copilot anthropic claude artificial vestack =agent =agents agentic nvidia"),
        Cat("sajber", "Sajber bezbednost", NAUKA, setOf("security", "bezbednost"),
            "sajber cyber haker hack hacked hacker ransomware malware phishing breach vulnerab =cve cve exploit botnet spyware zero",
            setOf("therecord", "bleeping", "securityweek", "krebs", "thn", "darkreading")),
        Cat("svemir", "Svemir", NAUKA, setOf("space", "svemir", "astronomy"),
            "svemir kosmos kosmic =nasa =esa spacex raket rocket satelit satellite =mars =mesec =moon asteroid galax planet teleskop telescope orbit astronaut starship launch", setOf("space")),
        Cat("nauka", "Nauka i medicina", NAUKA, setOf("nauka", "наука", "природа", "science", "health", "biology", "physics"),
            "naucni naucnic istraziv studij study research scientist fizik physics biolog hemij chemi medicin =lek vakcin vaccine cancer genet =dna fosil fossil dinosaur klim climate quantum kvant arheolo archaeolog brain mozak",
            setOf("newscientist", "sciam", "quanta", "sciencedaily", "physorg", "bbcsci", "guardiansci")),
        Cat("uredjaji", "Uređaji i igre", NAUKA, setOf("uređaji", "gadgets", "gaming", "telefoni"),
            "telefon phone iphone samsung galaxy pixel android =ios laptop tablet konzol playstation xbox nintendo =gta =igra =igre gaming =game =games windows =mac macbook apple gadget ekran"),
        Cat("internet", "Internet i kompanije", NAUKA, setOf("softver i servisi", "social media", "apps"),
            "google =meta facebook instagram tiktok twitter amazon microsoft musk startup aplikacij =app =apps mrez social platform internet softver software"),
        Cat("telekom", "Telekomunikacije", NAUKA, emptySet(),
            "=5g =6g telekom telecom operator mobiln spektr spectrum vodafone ericsson nokia huawei =mts yettel starlink broadband fiber roaming esim =ran", setOf("lightreading", "telecoms")),
        // Beograd
        Cat("saobracaj", "Saobraćaj i GSP", BEOGRAD, setOf("saobraćaj", "gsp"),
            "=gsp saobracaj sudar guzv autobus tramvaj trolejbus metro =linij =linije linija prevoz parking naplatn zastoj obustav =most mosta =bus semafor"),
        Cat("komunalno", "Struja, voda, grejanje", BEOGRAD, emptySet(),
            "struj =voda =vode =vodu =vodom isklju grejanj toplan bojler vodovod kanalizac krpelj komarc cistoc smece deratizac =eps elektrodistrib"),
        Cat("gradnja", "Gradnja i urbanizam", BEOGRAD, setOf("gradnja", "urbanizam"),
            "gradnj izgradnj zgrad stanov rekonstru urbanist =hala =halu investitor =blok =bloku kvart akvarij =expo =ekspo pijac", setOf("beobuild")),
        Cat("vlast", "Gradska vlast", BEOGRAD, setOf("beograd u pokretu", "skupština grada"),
            "sapic gradonacelni gradsk =izbori izbora =cls budzet"),
        Cat("hronika_bg", "Hronika", BEOGRAD, setOf("hronika", "crna hronika"),
            "uhapsen hapsen ubist ubijen pucnjav nesrec policij tuzilast osumnjic pritvor napad napadac =hitna pozar eksplozij teroris"),
        Cat("koncerti", "Koncerti i muzika", BEOGRAD, setOf("koncert", "koncerti", "muzika", "najave", "music guide"),
            "koncert nastup svirk turnej =album bend =dj festival", setOf("headliner", "n1kon", "novakon")),
        Cat("pozoriste", "Pozorište i film", BEOGRAD, setOf("film", "pozorište", "bioskopski repertoari"),
            "pozorist predstav premijer =film =filma =filmu bioskop repertoar =fest =bitef glumac glumic opera balet"),
        Cat("izlozbe", "Izložbe i sajmovi", BEOGRAD, setOf("izložbe", "beogradski sajam"),
            "izlozb muzej galerij sajam =sajma bazar vasar manifestac", setOf("bgsajam")),
        Cat("izlasci", "Izlasci i vikend", BEOGRAD, setOf("izlasci", "shopping", "what's on", "city guide"),
            "vikend izlasci zurk restoran kafan market dogadjaj", setOf("danubg", "stillinbg")),
            "koncert festival izlozb pozorist muzej sajam manifestac trofej maraton dogadjaj premijer =bitef =fest"),
        // Automobili
        Cat("ev", "Električni i hibridi", AUTO, setOf("električni automobili", "electric vehicles", "ev"),
            "elektri =ev =evs =phev =bev hibrid hybrid baterij battery punjac punjenj charging charger tesla =byd domet range plug", setOf("electrek", "insideevs")),
        Cat("modeli", "Novi modeli i testovi", AUTO, setOf("test", "review", "first drive"),
            "novi =nova predstav premijer otkriv =test testir review drive debut unveil reveal revealed facelift generacij generation"),
        Cat("saveti", "Saveti i propisi", AUTO, setOf("saveti"),
            "savet trik =kazna =kazne kazni propis zakon pravil vozac vozace registrac gorivo gorivu =cena =cene =cenu tips =fine =fines =law"),
        Cat("industrija", "Tržište i industrija", AUTO, emptySet(),
            "prodaj prodat trzist fabrik proizvodnj carin tarif =sales market factory plant recall opoziv akcij =jobs tariff"),
        // Ljubimci
        Cat("psi", "Psi", LJUBIMCI, emptySet(), "=pas =psa =psi =psu =pse psima =pasa =psom kucenc =dog =dogs puppy pupp"),
        Cat("macke", "Mačke", LJUBIMCI, emptySet(), "mack =mace macor =cat =cats kitten"),
        Cat("zdravlje_lj", "Zdravlje i nega", LJUBIMCI, emptySet(), "veterin =vet =vets zdravlj bolest vakcin ishran hran health disease vaccin food"),
        Cat("divlje", "Divlje životinje", LJUBIMCI, setOf("animals", "wildlife"), "divlj medved =vuk vukov ptic orao =zoo wild bird whale =bear wolf species"),
        Cat("zastita", "Zaštita i udomljavanje", LJUBIMCI, emptySet(), "udomlj udomi azil napusten zlostavlj zastit welfare rescue shelter adopt abuse cruelty"),
        // Sport
        Cat("fudbal", "Fudbal", SPORT, setOf("fudbal", "football", "soccer", "superliga", "domaći fudbal", "lige petice", "premier league", "champions league", "liga šampiona"),
            "fudbal football soccer =fk uefa fifa premier superlig =gol =golova =goal champions manchester arsenal liverpool chelsea barcelon =real bayern juventus =milan =inter =psg tottenham"),
        Cat("kosarka", "Košarka", SPORT, setOf("košarka", "euroleague", "evroliga", "nba", "aba liga", "basketball", "heba"),
            "kosark euroleag evrolig =nba =aba =kk basket jokic doncic eurocup", setOf("eurohoops")),
        Cat("tenis", "Tenis", SPORT, setOf("tenis", "tennis"), "tenis tennis =atp =wta djokovic sinner siner alcaraz alkaraz wimbledon roland"),
        Cat("f1", "Formula 1", SPORT, setOf("formula 1", "f1"), "formul =f1 =prix verstappen hamilton antonelli mclaren norris piastri leclerc", setOf("motorsport")),
        Cat("vecni", "Zvezda i Partizan", SPORT, emptySet(), "zvezd partizan derbi"),
        Cat("ostalo_s", "Ostali sportovi", SPORT, setOf("odbojka", "vaterpolo", "rukomet", "atletika", "boks"),
            "vaterpol odbojk volleyball rukomet handball atletik athletic plivan swimming =boks boxing =ufc =mma golf =nfl =mlb =nhl cricket rugby cycling biciklizam skijas"),
    )

    private val byId = ALL.associateBy { it.id }
    fun of(id: String): Cat? = byId[id]
    fun forSection(s: Section): List<Cat> = ALL.filter { it.section == s }

    fun assign(a: Article): Set<String> {
        val tagNorm = a.tags.map { Text.norm(it) }
        val words = HashSet<String>()
        Regex("[a-z0-9]+").findAll(Text.norm(a.title)).forEach { words.add(it.value) }
        tagNorm.forEach { t -> Regex("[a-z0-9]+").findAll(t).forEach { words.add(it.value) } }
        val out = HashSet<String>()
        for (c in ALL) {
            if (c.section != a.section) continue
            val hit = a.sourceId in c.sources ||
                tagNorm.any { it in c.tags } ||
                words.any { it in c.exact } ||
                c.prefixes.any { p -> words.any { it.startsWith(p) } }
            if (hit) out.add(c.id)
        }
        return out
    }
}
