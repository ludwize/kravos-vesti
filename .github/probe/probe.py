#!/usr/bin/env python3
"""Fetch every candidate RSS feed (and one article page per feed) from a GitHub runner and summarize what comes back."""
import os, re, sys, urllib.request, urllib.error, xml.etree.ElementTree as ET

OUT = sys.argv[1]
os.makedirs(OUT, exist_ok=True)
UA = {"User-Agent": "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Mobile Safari/537.36",
      "Accept": "application/rss+xml, application/atom+xml, application/xml, text/xml, text/html, */*"}
log = open(os.path.join(OUT, "index.txt"), "w")

FEEDS = {
    "n1_sport": "https://n1info.rs/sport-klub/feed/",
    "nova_sport": "https://nova.rs/sport/feed/",
    "danas_sport": "https://www.danas.rs/sport/feed/",
    "b92_sport": "https://www.b92.net/rss/sport",
    "b92_sport2": "https://www.b92.net/info/rss/sport.xml",
    "sportske": "https://www.sportske.net/rss",
    "mozzart": "https://www.mozzartsport.com/rss",
    "meridian": "https://meridiansport.rs/feed/",
    "rts_sport": "https://www.rts.rs/page/sport/ci/rss.html",
    "blic_sport": "https://www.blic.rs/rss/Sport",
    "novosti_sport": "https://www.novosti.rs/rss/2-Sport",
    "sportklub": "https://sportklub.n1info.rs/feed/",
    "telegraf_sport": "https://www.telegraf.rs/sport/rss",
    "zurnal": "https://www.zurnal.rs/rss",
    "bbc_sport": "https://feeds.bbci.co.uk/sport/rss.xml",
    "bbc_football": "https://feeds.bbci.co.uk/sport/football/rss.xml",
    "guardian_sport": "https://www.theguardian.com/sport/rss",
    "guardian_football": "https://www.theguardian.com/football/rss",
    "espn": "https://www.espn.com/espn/rss/news",
    "eurohoops": "https://www.eurohoops.net/en/feed/",
    "skysports": "https://www.skysports.com/rss/12040",
    "atp": "https://www.atptour.com/en/media/rss-feed/xml-feed",
    "motorsport": "https://www.motorsport.com/rss/f1/news/",
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
    summary.write(f"\n### {name}: {len(items)} items, root={root.tag}\n")
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
    # sample one article page for the reader extractor
    first = items[0] if items else None
    if first is not None:
        link = text(first.find("link")) or (first.find("atom:link", NS).get("href") if first.find("atom:link", NS) is not None else "")
        if link:
            c2, b2, fin = fetch(link)
            open(os.path.join(OUT, name + ".article.html"), "wb").write(b2)
            log.write(f"   article {c2} {len(b2)} {fin}\n")
log.close(); summary.close()
print(open(os.path.join(OUT, "index.txt")).read())
