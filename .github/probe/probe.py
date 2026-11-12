#!/usr/bin/env python3
"""Fetch every candidate RSS feed (and one article page per feed) from a GitHub runner and summarize what comes back."""
import os, re, sys, urllib.request, urllib.error, xml.etree.ElementTree as ET

OUT = sys.argv[1]
os.makedirs(OUT, exist_ok=True)
UA = {"User-Agent": "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Mobile Safari/537.36",
      "Accept": "application/rss+xml, application/atom+xml, application/xml, text/xml, text/html, */*"}
log = open(os.path.join(OUT, "index.txt"), "w")

FEEDS = {'n1_scitech': 'https://n1info.rs/magazin/scitech/feed/', 'n1_tag_teh': 'https://n1info.rs/tag/tehnologija/feed/', 'nova_tech': 'https://nova.rs/magazin/tehnologija/feed/', 'nova_tag_teh': 'https://nova.rs/tag/tehnologija/feed/', 'nova_tag_nauka': 'https://nova.rs/tag/nauka/feed/', 'danas_tag_teh': 'https://www.danas.rs/tag/tehnologija/feed/', 'danas_tag_nauka': 'https://www.danas.rs/tag/nauka/feed/', 'b92_tehno': 'https://www.b92.net/info/rss/tehnopolis.xml', 'b92_zivot': 'https://www.b92.net/info/rss/zivot.xml', 'startit': 'https://startit.rs/feed/', 'pcpress': 'https://pcpress.rs/feed/', 'benchmark': 'https://benchmark.rs/feed/', 'netokracija': 'https://www.netokracija.rs/feed', 'sk': 'https://www.sk.rs/rss.xml', 'rts_nauka': 'https://www.rts.rs/page/magazine/sr/rss/510/nauka.html', 'rts_teh': 'https://www.rts.rs/page/magazine/sr/rss/511/tehnologija.html', 'elementarijum': 'https://elementarijum.rs/feed/', 'naukakrozprice': 'https://naukakrozprice.rs/feed/', 'bbc_sr': 'https://feeds.bbci.co.uk/serbian/lat/rss.xml', 'mondo_tech': 'https://mondo.rs/rss/644/Mob-IT', 'kurir_tech': 'https://www.kurir.rs/rss/tech', 'telegraf_tech': 'https://www.telegraf.rs/rss/hi-tech', 'ars_all': 'https://feeds.arstechnica.com/arstechnica/index', 'verge': 'https://www.theverge.com/rss/index.xml', 'wired': 'https://www.wired.com/feed/rss', 'mittr': 'https://www.technologyreview.com/feed/', 'newscientist': 'https://www.newscientist.com/feed/home/', 'nature': 'https://www.nature.com/nature.rss', 'sciencedaily': 'https://www.sciencedaily.com/rss/top/science.xml', 'physorg': 'https://phys.org/rss-feed/', 'quanta': 'https://www.quantamagazine.org/feed/', 'bbc_sci': 'https://feeds.bbci.co.uk/news/science_and_environment/rss.xml', 'bbc_tech': 'https://feeds.bbci.co.uk/news/technology/rss.xml', 'guardian_sci': 'https://www.theguardian.com/science/rss', 'guardian_tech': 'https://www.theguardian.com/technology/rss', 'techcrunch': 'https://techcrunch.com/feed/', 'space': 'https://www.space.com/feeds/all', 'spacenews': 'https://spacenews.com/feed/', 'sciam': 'https://www.scientificamerican.com/platform/syndication/rss/', 'lightreading': 'https://www.lightreading.com/rss.xml', 'telecoms': 'https://www.telecoms.com/rss.xml', 'rcr': 'https://www.rcrwireless.com/feed', 'hn': 'https://hnrss.org/frontpage?points=300', 'danubg': 'https://www.danubeogradu.rs/feed/', 'beforeafter': 'https://www.beforeafter.rs/feed/', 'gdeizaci': 'https://www.gdeizaci.com/feed/', 'belgradebeat': 'https://belgrade-beat.rs/feed/', 'tob': 'https://www.tob.rs/rss', 'tob2': 'https://www.tob.rs/sr/feed', 'kcb': 'https://www.kcb.org.rs/feed/', 'beogradrs': 'https://www.beograd.rs/cir/rss/', 'beogradrs2': 'https://www.beograd.rs/lat/rss/', 'dob': 'https://domomladine.org/feed/', 'kolarac': 'https://www.kolarac.rs/feed/', 'sajam': 'https://sajam.rs/feed/', 'seecult': 'https://www.seecult.org/rss.xml', 'b92_kultura': 'https://www.b92.net/info/rss/kultura.xml', 'b92_beograd': 'https://www.b92.net/info/rss/beograd.xml', 'nova_kultura': 'https://nova.rs/kultura/feed/', 'n1_kultura': 'https://n1info.rs/kultura/feed/', 'danas_tag_koncert': 'https://www.danas.rs/tag/koncert/feed/', 'n1_tag_koncert': 'https://n1info.rs/tag/koncert/feed/', 'nova_tag_koncert': 'https://nova.rs/tag/koncert/feed/', 'balkanrock': 'https://balkanrock.com/feed/', 'headliner': 'https://headliner.rs/feed/', 'hellycherry': 'https://www.hellycherry.com/feeds/posts/default?alt=rss', 'nocturne': 'https://www.nocturnemagazine.net/feed/', 'beogradnocu': 'https://beogradnocu.com/feed/', 'kudaveceras': 'https://www.kudaveceras.rs/feed/', 'stillinbg': 'https://stillinbelgrade.com/feed/', 'bgonline': 'https://bgonline.rs/feed/', 'bilet': 'https://www.bilet.rs/feed/', 'kulturniks': 'https://kulturnik.rs/feed/', 'okruzenje': 'https://www.gradskeinfo.rs/feed/', 'mojbeograd': 'https://www.mojbeograd.rs/feed/', 'beoinfo': 'https://www.beoinfo.rs/feed/', 'gradnja': 'https://www.gradnja.rs/feed/', 'gn_dogadjaji': 'https://news.google.com/rss/search?q=Beograd+(koncert+OR+izlo%C5%BEba+OR+festival+OR+predstava+OR+sajam)+when:2d&hl=sr&gl=RS&ceid=RS:sr-Latn', 'gn_vikend': 'https://news.google.com/rss/search?q=%22u+Beogradu%22+(vikend+OR+doga%C4%91aji+OR+manifestacija)+when:3d&hl=sr&gl=RS&ceid=RS:sr-Latn'}

NS = {"content": "http://purl.org/rss/1.0/modules/content/", "media": "http://search.yahoo.com/mrss/",
      "atom": "http://www.w3.org/2005/Atom", "dc": "http://purl.org/dc/elements/1.1/"}


def fetch(url):
    try:
        with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=25) as r:
            return r.status, r.read(), r.geturl()
    except urllib.error.HTTPError as e:
        return e.code, e.read(), url
    except Exception as e:
        return 0, str(e).encode(), url


def text(el):
    return (el.text or "").strip() if el is not None else ""


summary = open(os.path.join(OUT, "summary.txt"), "w")
for name, url in FEEDS.items():
    code, body, final = fetch(url)
    open(os.path.join(OUT, name + ".xml"), "wb").write(body)
    log.write(f"{code}  {len(body):>8}  {name}  {url}\n")
    if code != 200:
        summary.write(f"\n### {name} -> {code} {body[:120]!r}\n")
        continue
    try:
        root = ET.fromstring(body)
    except Exception as e:
        summary.write(f"\n### {name} -> PARSE ERROR {e} {body[:160]!r}\n")
        continue
    items = root.findall(".//item") or root.findall(".//{http://purl.org/rss/1.0/}item") or root.findall(".//atom:entry", NS)
    import email.utils, time as _t
    ages = []
    for it in items:
        dd = text(it.find("pubDate")) or text(it.find("dc:date", NS)) or text(it.find("atom:updated", NS))
        try: ages.append((_t.time() - email.utils.parsedate_to_datetime(dd).timestamp()) / 3600)
        except Exception: pass
    fresh = f" newest {min(ages):.1f}h, last24h {sum(1 for a in ages if a < 24)}" if ages else ""
    summary.write(f"\n### {name}: {len(items)} items, root={root.tag}{fresh}\n")
    for it in items[:3]:
        def g(tag):
            return it.find(tag, NS) if ":" in tag else (it.find(tag) if it.find(tag) is not None else it.find("{http://purl.org/rss/1.0/}" + tag))
        title = text(g("title")) or text(it.find("atom:title", NS))
        link = text(g("link")) or (it.find("atom:link", NS).get("href") if it.find("atom:link", NS) is not None else "")
        date = text(g("pubDate")) or text(g("dc:date")) or text(it.find("atom:updated", NS))
        desc = text(g("description"))
        content = text(g("content:encoded"))
        media = [m.get("url") for m in it.findall("media:content", NS) + it.findall("media:thumbnail", NS)]
        enc = [e.get("url") for e in it.findall("enclosure")]
        cats = [text(c) for c in it.findall("category")][:5]
        summary.write(f"- {title[:110]}\n  link={link}\n  date={date} cats={cats}\n  desc={len(desc)} content={len(content)} media={media[:1]} enc={enc[:1]}\n  desc0={re.sub(r'<[^>]+>', ' ', desc)[:160]!r}\n")
log.close(); summary.close()
# keep only the text summaries (raw feeds can trip GitHub push protection)
for f in os.listdir(OUT):
    if not f.endswith(".txt"): os.remove(os.path.join(OUT, f))
print(open(os.path.join(OUT, "index.txt")).read())

import json, time
try:
    d = json.load(urllib.request.urlopen("https://vesti.kravos.net/data.json?x=%d" % time.time()))
    extra = "SITE: generated %d min ago; sections %s" % ((time.time() - d["generated"]) / 60, list(d["sections"]))
except Exception as e:
    extra = "SITE: " + str(e)
open(os.path.join(OUT, "index.txt"), "a").write(extra + "\n")
