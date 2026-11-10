#!/usr/bin/env python3
"""Fetch every candidate RSS feed (and one article page per feed) from a GitHub runner and summarize what comes back."""
import os, re, sys, urllib.request, urllib.error, xml.etree.ElementTree as ET

OUT = sys.argv[1]
os.makedirs(OUT, exist_ok=True)
UA = {"User-Agent": "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Mobile Safari/537.36",
      "Accept": "application/rss+xml, application/atom+xml, application/xml, text/xml, text/html, */*"}
log = open(os.path.join(OUT, "index.txt"), "w")

FEEDS = {
    # ljubimci
    "nova_ljub": "https://nova.rs/tag/ljubimci/feed/",
    "nova_ljub2": "https://nova.rs/magazin/ljubimci/feed/",
    "n1_ljub": "https://n1info.rs/tag/ljubimci/feed/",
    "danas_ljub": "https://www.danas.rs/tag/ljubimci/feed/",
    "danas_zivot": "https://www.danas.rs/tag/zivotinje/feed/",
    "blic_ljub": "https://www.blic.rs/rss/Zabava/Ljubimci",
    "kurir_ljub": "https://www.kurir.rs/rss/stars/ljubimci",
    "mondo_ljub": "https://mondo.rs/rss/656/Zabava/Ljubimci",
    "zena_ljub": "https://zena.blic.rs/rss/Ljubimci",
    "lepotaizdr": "https://www.lepotaizdravlje.rs/feed/",
    "dodo": "https://www.thedodo.com/rss",
    "dodo2": "https://www.thedodo.com/rss/feed",
    "guardian_animals": "https://www.theguardian.com/world/animals/rss",
    "guardian_pets": "https://www.theguardian.com/lifeandstyle/pets/rss",
    "bbc_animals": "https://feeds.bbci.co.uk/news/science_and_environment/rss.xml",
    "petmd": "https://www.petmd.com/rss.xml",
    # automobili
    "b92_auto": "https://www.b92.net/rss/automobili",
    "b92_auto2": "https://www.b92.net/info/rss/automobili.xml",
    "autobild": "https://www.autobild.rs/feed",
    "autobild2": "https://autobild.rs/feed/",
    "mondo_auto": "https://mondo.rs/rss/641/Auto",
    "kurir_auto": "https://www.kurir.rs/rss/auto",
    "blic_auto": "https://www.blic.rs/rss/Auto",
    "nova_auto": "https://nova.rs/magazin/auto/feed/",
    "n1_auto": "https://n1info.rs/magazin/auto/feed/",
    "polovni": "https://www.polovniautomobili.com/magazin/feed",
    "autoklub": "https://www.autoklub.rs/feed",
    "electrek": "https://electrek.co/feed/",
    "insideevs": "https://insideevs.com/rss/news/all/",
    "autocar": "https://www.autocar.co.uk/rss",
    "motor1": "https://www.motor1.com/rss/news/all/",
    "caranddriver": "https://www.caranddriver.com/rss/all.xml/",
    "guardian_cars": "https://www.theguardian.com/technology/motoring/rss",
}

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
