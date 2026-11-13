#!/usr/bin/env python3
"""Fetch every candidate RSS feed (and one article page per feed) from a GitHub runner and summarize what comes back."""
import os, re, sys, urllib.request, urllib.error, xml.etree.ElementTree as ET

OUT = sys.argv[1]
os.makedirs(OUT, exist_ok=True)
UA = {"User-Agent": "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Mobile Safari/537.36",
      "Accept": "application/rss+xml, application/atom+xml, application/xml, text/xml, text/html, */*"}
log = open(os.path.join(OUT, "index.txt"), "w")

FEEDS = {'n1_zdravlje': 'https://n1info.rs/magazin/zdravlje/feed/', 'n1_tag_zdr': 'https://n1info.rs/tag/zdravlje/feed/', 'nova_zdr': 'https://nova.rs/magazin/zdravlje/feed/', 'nova_tag_zdr': 'https://nova.rs/tag/zdravlje/feed/', 'danas_tag_zdr': 'https://www.danas.rs/tag/zdravlje/feed/', 'danas_zdr': 'https://www.danas.rs/zivot/zdravlje/feed/', 'b92_zdravlje': 'https://www.b92.net/info/rss/zdravlje.xml', 'kurir_zdr': 'https://www.kurir.rs/rss/zdravlje', 'blic_zdr': 'https://www.blic.rs/rss/Vesti/Zdravlje', 'rts_zdr': 'https://www.rts.rs/page/stories/sr/rss/1158/zdravlje.html', 'telegraf_zdr': 'https://www.telegraf.rs/rss/zdravlje', 'mondo_zdr': 'https://mondo.rs/rss/680/Zdravlje', 'zdravlje_rs': 'https://www.zdravlje.org.rs/index.php/sr/?format=feed&type=rss', 'batut': 'https://www.batut.org.rs/index.php?format=feed&type=rss', 'stetoskop': 'https://www.stetoskop.info/rss', 'krstarica_zdr': 'https://zdravlje.krstarica.com/feed/', 'zdravlje_plus': 'https://www.zdravljeplus.rs/feed/', 'bbc_health': 'https://feeds.bbci.co.uk/news/health/rss.xml', 'guardian_health': 'https://www.theguardian.com/society/health/rss', 'guardian_wellness': 'https://www.theguardian.com/lifeandstyle/health-and-wellbeing/rss', 'medicalxpress': 'https://medicalxpress.com/rss-feed/', 'statnews': 'https://www.statnews.com/feed/', 'sciencedaily_health': 'https://www.sciencedaily.com/rss/health_medicine.xml', 'who': 'https://www.who.int/rss-feeds/news-english.xml', 'nih': 'https://www.nih.gov/news-releases/feed.xml', 'harvard': 'https://www.health.harvard.edu/blog/feed', 'medicalnewstoday': 'https://www.medicalnewstoday.com/newsfeeds/rss', 'npr_health': 'https://feeds.npr.org/1128/rss.xml', 'newscientist_health': 'https://www.newscientist.com/subject/health/feed/', 'kffhealth': 'https://kffhealthnews.org/feed/', 'nyt_health': 'https://rss.nytimes.com/services/xml/rss/nyt/Health.xml', 'reuters_health_gn': 'https://news.google.com/rss/search?q=when:1d+site:reuters.com/business/healthcare-pharmaceuticals&hl=en-US&gl=US&ceid=US:en'}

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
