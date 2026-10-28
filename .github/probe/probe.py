#!/usr/bin/env python3
"""Fetch every candidate RSS feed (and one article page per feed) from a GitHub runner and summarize what comes back."""
import os, re, sys, urllib.request, urllib.error, xml.etree.ElementTree as ET

OUT = sys.argv[1]
os.makedirs(OUT, exist_ok=True)
UA = {"User-Agent": "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Mobile Safari/537.36",
      "Accept": "application/rss+xml, application/atom+xml, application/xml, text/xml, text/html, */*"}
log = open(os.path.join(OUT, "index.txt"), "w")

FEEDS = {
    "n1_bg_tag": "https://n1info.rs/tag/beograd/feed/",
    "n1_bg_cat": "https://n1info.rs/vesti/beograd/feed/",
    "danas_bg": "https://www.danas.rs/beograd/feed/",
    "danas_bg2": "https://www.danas.rs/vesti/beograd/feed/",
    "danas_bg_tag": "https://www.danas.rs/tag/beograd/feed/",
    "nova_bg": "https://nova.rs/vesti/beograd/feed/",
    "nova_bg_tag": "https://nova.rs/tag/beograd/feed/",
    "radar_bg_tag": "https://radar.nova.rs/tag/beograd/feed/",
    "blic_bg": "https://www.blic.rs/rss/Vesti/Beograd",
    "beobuild": "https://www.beobuild.rs/feed",
    "beobuild2": "https://beobuild.rs/feed/",
    "telegraf_bg": "https://www.telegraf.rs/rss/vesti/beograd",
    "kurir_bg": "https://www.kurir.rs/rss/vesti/beograd",
    "novosti_bg": "https://www.novosti.rs/rss/1-Beograd",
    "rts_bg": "https://www.rts.rs/page/stories/sr/rss/9/Beograd.html",
    "mondo_bg": "https://mondo.rs/rss/628/Info/Beograd",
    "b92_bg": "https://www.b92.net/rss/beograd",
    "nedeljnik": "https://www.nedeljnik.rs/feed/",
    "beogradske": "https://beogradske-vesti.rs/feed/",
    "infobeograd": "https://infobeograd.rs/feed/",
    "011info": "https://011info.com/feed",
    "gn_bg": "https://news.google.com/rss/search?q=Beograd+when:1d&hl=sr&gl=RS&ceid=RS:sr-Latn",
    "n1": "https://n1info.rs/feed/",
    "danas": "https://www.danas.rs/feed/",
    "nova": "https://nova.rs/feed/",
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
