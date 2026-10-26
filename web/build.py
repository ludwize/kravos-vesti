#!/usr/bin/env python3
"""
Kravos Vesti for the web: fetch every RSS source, group stories, assign categories, write data.json.

Sources and categories are read straight from the Android app's Kotlin files
(Sources.kt, Categories.kt), so the app and the site always agree. The grouping
and ranking mirror Cluster.kt. Runs on a GitHub runner every ~10 minutes.

usage: build.py <out-dir> [--offline <dir-with-feed-xml>]
"""
import concurrent.futures as cf
import email.utils
import hashlib
import html
import json
import math
import os
import re
import sys
import time
import unicodedata
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET
from datetime import datetime, timezone

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
KT = os.path.join(ROOT, "app/src/main/java/net/kravos/vesti/data")
UA = {"User-Agent": "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Mobile Safari/537.36",
      "Accept": "application/rss+xml, application/xml, text/xml, text/html;q=0.9, */*;q=0.8"}

# ---------------------------------------------------------------- Kotlin definitions

def split_args(s):
    """Split a Kotlin argument list at top-level commas."""
    out, depth, cur, q = [], 0, "", False
    i = 0
    while i < len(s):
        c = s[i]
        if q:
            cur += c
            if c == "\\":
                cur += s[i + 1]; i += 1
            elif c == '"':
                q = False
        elif c == '"':
            q = True; cur += c
        elif c in "([":
            depth += 1; cur += c
        elif c in ")]":
            depth -= 1; cur += c
        elif c == "," and depth == 0:
            out.append(cur.strip()); cur = ""
        else:
            cur += c
        i += 1
    if cur.strip():
        out.append(cur.strip())
    return out


def call_body(src, start):
    """Text inside the parentheses that open at src[start]."""
    depth, q, i = 0, False, start
    while i < len(src):
        c = src[i]
        if q:
            if c == "\\":
                i += 1
            elif c == '"':
                q = False
        elif c == '"':
            q = True
        elif c == "(":
            depth += 1
        elif c == ")":
            depth -= 1
            if depth == 0:
                return src[start + 1:i]
        i += 1
    raise ValueError("unbalanced")


def kt_value(v, consts):
    v = v.strip()
    if v.startswith('"'):
        return json.loads(v)
    if v.startswith(("setOf(", "listOf(")):
        return [kt_value(x, consts) for x in split_args(call_body(v, v.index("(")))]
    if v == "emptySet()":
        return []
    if v.startswith("gn("):
        site = kt_value(call_body(v, 2), consts)
        return f"https://news.google.com/rss/search?q=when:1d+site:{site}&hl=en-US&gl=US&ceid=US:en"
    if v in ("true", "false"):
        return v == "true"
    if v in consts:
        return consts[v]
    return v  # enum name like SRBIJA


def kt_calls(path, name, consts=None):
    src = open(path, encoding="utf-8").read()
    src = re.sub(r"(?m)(^|\s)//\s[^\n]*", r"\1", src)  # comments, not https://
    consts = dict(consts or {})
    for m in re.finditer(r'private const val (\w+) = ("(?:[^"\\]|\\.)*")', src):
        consts[m.group(1)] = json.loads(m.group(2))
    out = []
    for m in re.finditer(r"\b" + name + r"\(", src):
        if src[m.start() - 6:m.start()].strip().endswith("class"):
            continue
        args = split_args(call_body(src, m.end() - 1))
        pos, named = [], {}
        for a in args:
            mm = re.match(r"^(\w+)\s*=\s*(.*)$", a, re.S)
            if mm and not a.startswith('"'):
                named[mm.group(1)] = kt_value(mm.group(2), consts)
            else:
                pos.append(kt_value(a, consts))
        out.append((pos, named))
    return out


def load_sources():
    res = []
    for pos, kw in kt_calls(os.path.join(KT, "Sources.kt"), "Source"):
        if len(pos) < 4:
            continue
        res.append(dict(id=pos[0], name=pos[1], section=pos[2], feeds=pos[3], lang=kw.get("lang", "sr"),
                        on=kw.get("defaultOn", True), reader=kw.get("reader", True), skip=set(kw.get("skip", []))))
    return res


def load_set(path, name):
    src = open(path, encoding="utf-8").read()
    m = re.search(r"val " + name + r" = setOf\(", src)
    return set(kt_value("setOf(" + call_body(src, m.end() - 1) + ")", {}))


def load_cats():
    res = []
    for pos, kw in kt_calls(os.path.join(KT, "Categories.kt"), "Cat"):
        if len(pos) < 5:
            continue
        words = pos[4].split()
        res.append(dict(id=pos[0], label=pos[1], section=pos[2], tags={norm(t) for t in pos[3]},
                        exact={w[1:] for w in words if w.startswith("=")}, prefixes=[w for w in words if not w.startswith("=")],
                        sources=set(pos[5]) if len(pos) > 5 else set()))
    return res

# ---------------------------------------------------------------- text (mirrors Text.kt)

CYR = dict(zip("абвгдђежзијклљмнњопрстћуфхцчџш",
               "a b v g d đ e ž z i j k l lj m n nj o p r s t ć u f h c č dž š".split()))


def lat(s):
    if not any("Ѐ" <= c <= "ӿ" for c in s):
        return s
    out = []
    for i, c in enumerate(s):
        r = CYR.get(c.lower())
        if r is None:
            out.append(c); continue
        if c.isupper():
            nxt = s[i + 1] if i + 1 < len(s) else ""
            prv = s[i - 1] if i > 0 else ""
            r = r.upper() if len(r) == 2 and (nxt.isupper() or prv.isupper()) else r[0].upper() + r[1:]
        out.append(r)
    return "".join(out)


def norm(s):
    s = lat(s).lower().replace("đ", "dj")
    return "".join(c for c in unicodedata.normalize("NFD", s) if unicodedata.category(c) != "Mn")


STOP = set(norm(w) for w in """i u na je da se za od sa su o po iz kao što sto koji koja koje kojeg kojem kojima ali ili a to ne bi će ce biti bio bila bilo
smo ste sam nije nisu još jos već vec kod do kad kada ovo ta taj te ti tu pre posle nakon zbog bez preko protiv između izmedju
oko prema kroz pod nad pri uz ga mu joj ih im ju me mi vi on ona ono oni one njegov njen njihov ima imaju može moze mogu treba
sve svi sva ovaj ova ove ovi tog toga tome tim takođe takodje jer pa dok ako samo vise više manje jedan jedna jedno dva tri
godine godina dan danas juče juce sutra kaže kaze rekao rekla izjavio izjavila navodi navode the an of to in on for and or is
are was were be by with at from as that this it its has have had not but after over new says said will would could than into
about more up out who what how why when where his her their they he she we you our your vs video foto uživo uzivo live blog
watch photos update updates latest""".split())


def tokens(s):
    out = set()
    for w in re.findall(r"[a-z0-9]+", norm(s)):
        if w in STOP:
            continue
        if w.isdigit():
            if len(w) >= 3:
                out.add(w)
            continue
        if len(w) < 3:
            continue
        out.add(w[:5])
    return out


TAG_RE = re.compile(r"<[^>]+>")


def plain(h):
    if not h:
        return ""
    return re.sub(r"\s+", " ", html.unescape(TAG_RE.sub(" ", html.unescape(h)))).strip()


def clip(s, n):
    if len(s) <= n:
        return s
    cut = s[:n]
    sp = cut.rfind(" ")
    return (cut[:sp] if sp > n // 2 else cut).rstrip(",;: ") + "…"

# ---------------------------------------------------------------- RSS (mirrors Rss.kt)

NS = {"content": "http://purl.org/rss/1.0/modules/content/", "media": "http://search.yahoo.com/mrss/",
      "atom": "http://www.w3.org/2005/Atom", "dc": "http://purl.org/dc/elements/1.1/", "rdf": "http://purl.org/rss/1.0/"}
TRACK = re.compile(r"^(utm_[a-z]+|at_medium|at_campaign|traffic_source|maca|ref|cmpid|CMP|ito)$")


def clean_url(u):
    if "?" not in u or "news.google.com" in u:
        return u
    base, q = u.split("?", 1)
    keep = [p for p in q.split("&") if p and not TRACK.match(p.split("=")[0])]
    return base + ("?" + "&".join(keep) if keep else "")


def parse_date(s):
    s = (s or "").strip()
    if not s:
        return None
    try:
        return email.utils.parsedate_to_datetime(s).timestamp()
    except Exception:
        pass
    try:
        return datetime.fromisoformat(s.replace("Z", "+00:00")).timestamp()
    except Exception:
        return None


def sha(s):
    return hashlib.sha1(s.encode()).hexdigest()[:16]


def txt(el):
    return (el.text or "").strip() if el is not None else ""


def find(it, *tags):
    for t in tags:
        e = it.find(t, NS)
        if e is not None:
            return e
    return None


def parse_feed(xml, src, now):
    root = ET.fromstring(xml)
    items = root.findall(".//item") or root.findall(".//rdf:item", NS) or root.findall(".//atom:entry", NS)
    gn = any("news.google.com" in f for f in src["feeds"])
    out = []
    for it in items:
        title = plain(txt(find(it, "title", "rdf:title", "atom:title")))
        if not title:
            continue
        le = find(it, "link", "rdf:link", "atom:link")
        link = txt(le) or (le.get("href") if le is not None else "") or txt(it.find("guid"))
        if not link.startswith("http"):
            continue
        link = clean_url(link)
        desc = txt(find(it, "description", "rdf:description", "atom:summary"))
        content = txt(it.find("content:encoded", NS))
        ts = parse_date(txt(find(it, "pubDate", "dc:date", "atom:published", "atom:updated"))) or (now - 6 * 3600)
        ts = min(ts, now)
        if gn:
            title = re.sub(r"\s+[-–]\s+[^-–]{2,30}$", "", title)
        live = False
        m = re.match(r"^\(?(BLOG|UŽIVO|UZIVO|LIVE)\)?\s*[:|-]?\s*", title, re.I)
        if m:
            live = True; title = title[m.end():].strip()
        if re.search(r"(/live/|/blog-|uzivo|uživo)", link, re.I):
            live = True
        title = re.sub(r"^(\(?(VIDEO|FOTO|AUDIO|FOTO/VIDEO|VIDEO/FOTO|ВИДЕО|ФОТО)\)?\s*[:|-]?\s*)+", "", title).strip()
        path = [p for p in link.split("://", 1)[-1].split("?")[0].split("/")[1:] if p and not p.isdigit()]
        cats = [txt(c).lower() for c in it.findall("category")]
        tags = set(p.lower() for p in path[:-1]) | set(cats)
        summary = plain(desc)
        if summary.startswith(title):
            summary = summary[len(title):].strip()
        out.append(dict(id=sha(link), src=src["id"], title=title, link=link, summary="" if gn else clip(summary, 320),
                        image=image(it, desc, content), ts=ts, section=src["section"], live=live, tags=tags))
    return out


def image(it, desc, content):
    best, bw = None, -1
    for m in it.findall("media:content", NS):
        u, t, md = m.get("url", ""), m.get("type", ""), m.get("medium", "")
        if u.startswith("http") and (md == "image" or t.startswith("image") or (not md and not t and re.search(r"\.(jpe?g|png|webp)", u, re.I))):
            w = int(m.get("width") or 0)
            if w > bw:
                best, bw = u, w
    if not best:
        th = it.findall("media:thumbnail", NS)
        if th:
            best = max(th, key=lambda x: int(x.get("width") or 0)).get("url")
    if not best:
        for e in it.findall("enclosure"):
            if e.get("type", "").startswith("image"):
                best = e.get("url"); break
    if not best:
        for h in (content, desc):
            m = re.search(r"<img[^>]+src=[\"']([^\"']+)[\"']", h or "", re.I)
            if m and m.group(1).startswith("http") and "feedburner" not in m.group(1):
                best = m.group(1); break
    if best and "ichef.bbci.co.uk" in best:
        best = re.sub(r"/(standard|news)/\d+/", r"/\1/800/", best)
    return html.unescape(best) if best else None

# ---------------------------------------------------------------- categories + grouping (mirror Categories.kt, Cluster.kt)

def assign(a, cats):
    tag_norm = [norm(t) for t in a["tags"]]
    words = set(re.findall(r"[a-z0-9]+", norm(a["title"])))
    for t in tag_norm:
        words |= set(re.findall(r"[a-z0-9]+", t))
    out = []
    for c in cats:
        if c["section"] != a["section"]:
            continue
        if (a["src"] in c["sources"] or any(t in c["tags"] for t in tag_norm) or words & c["exact"]
                or any(w.startswith(p) for p in c["prefixes"] for w in words)):
            out.append(c["id"])
    return out


THR, WINDOW = 0.30, 36 * 3600


def cluster(arts, order_rank):
    if not arts:
        return []
    raw = []
    for a in arts:
        t = tokens(a["title"]); s = tokens(a["summary"][:240]) - t
        raw.append((a, t, s))
    df = {}
    for _, t, s in raw:
        for w in t | s:
            df[w] = df.get(w, 0) + 1
    n = len(raw)
    idf = lambda w: math.log(1 + n / df.get(w, 1))
    docs = []
    for a, t, s in raw:
        v = {w: 2 * idf(w) for w in t}
        for w in s:
            v[w] = v.get(w, 0) + idf(w)
        docs.append(dict(a=a, title=t, v=v, n=math.sqrt(sum(x * x for x in v.values())) or 1e-9))
    docs.sort(key=lambda d: -d["a"]["ts"])

    def sim(x, y):
        small, big = (x, y) if len(x["v"]) < len(y["v"]) else (y, x)
        dot, shared = 0.0, 0
        for w, wt in small["v"].items():
            o = big["v"].get(w)
            if o is None:
                continue
            dot += wt * o
            if w in x["title"] or w in y["title"]:
                shared += 1
        return 0.0 if shared < 2 else dot / (x["n"] * y["n"])

    groups = []
    for d in docs:
        best, bs = None, 0
        for g in groups:
            if g["sec"] != d["a"]["section"] or g["start"] - d["a"]["ts"] > WINDOW:
                continue
            ss = [sim(d, m) for m in g["docs"]]
            mx, av = max(ss), sum(ss) / len(ss)
            if mx >= THR and av >= THR * 0.6 and mx > bs:
                best, bs = g, mx
        if best:
            best["docs"].append(d)
        else:
            groups.append(dict(sec=d["a"]["section"], start=d["a"]["ts"], docs=[d]))
    stories = []
    for g in groups:
        arts = [d["a"] for d in g["docs"]]
        if len(arts) > 1:
            lead = min(arts, key=lambda a: (0 if a["image"] and len(a["summary"]) > 40 else 1, order_rank.get(a["src"], 99), -a["ts"]))
            arts = [lead] + sorted([a for a in arts if a is not lead], key=lambda a: -a["ts"])
        stories.append(dict(id=min(arts, key=lambda a: a["ts"])["id"], section=g["sec"], articles=arts))
    return stories


def score(st, now):
    srcs = {a["src"] for a in st["articles"]}
    age_h = max(0, now - max(a["ts"] for a in st["articles"])) / 3600
    breadth = len(srcs) ** 1.6 + 0.15 * (len(st["articles"]) - len(srcs))
    live = any(a["live"] for a in st["articles"])
    return breadth * math.exp(-age_h * math.log(2) / 8) * (1.3 if live else 1.0)

# ---------------------------------------------------------------- fetching

def fetch(url, timeout=25):
    req = urllib.request.Request(url, headers=UA)
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return r.read()


OG = re.compile(rb"<meta[^>]+property=[\"']og:image[\"'][^>]*content=[\"']([^\"']+)[\"']", re.I)
OG2 = re.compile(rb"<meta[^>]+content=[\"']([^\"']+)[\"'][^>]*property=[\"']og:image[\"']", re.I)


def og_image(url):
    try:
        b = fetch(url, 15)[:400_000]
        m = OG.search(b) or OG2.search(b)
        return html.unescape(m.group(1).decode("utf-8", "replace")) if m else None
    except Exception:
        return None


def main():
    out = sys.argv[1]
    offline = sys.argv[sys.argv.index("--offline") + 1] if "--offline" in sys.argv else None
    os.makedirs(out, exist_ok=True)
    sources = load_sources()
    cats = load_cats()
    world_tags = load_set(os.path.join(KT, "Sources.kt"), "WORLD_TAGS")
    sport_tags = load_set(os.path.join(KT, "Sources.kt"), "SPORT_TAGS")
    ent_tags = load_set(os.path.join(KT, "Sources.kt"), "ENT_TAGS")
    print(f"{len(sources)} sources, {len(cats)} categories")
    now = time.time()
    rank = {s["id"]: i for i, s in enumerate(sources)}
    on = [s for s in sources if s["on"]]

    def get_src(s):
        arts, ok = [], False
        for i, url in enumerate(s["feeds"]):
            try:
                if offline:
                    name = s.get("offline", [None])[i] if "offline" in s else None
                    xml = open(os.path.join(offline, name), "rb").read() if name else None
                    if xml is None:
                        continue
                else:
                    xml = fetch(url)
                arts += parse_feed(xml, s, now); ok = True
            except Exception as e:
                print(f"  ! {s['id']} {url[:60]}: {e}")
        return s, arts, ok

    if offline:  # map local probe files onto source ids for testing
        mp = json.load(open(os.path.join(offline, "map.json")))
        for s in on:
            if s["id"] in mp:
                s["offline"] = mp[s["id"]]
        on = [s for s in on if "offline" in s]

    seen, seen_title, arts, failed = set(), set(), [], []
    with cf.ThreadPoolExecutor(10) as ex:
        results = list(ex.map(get_src, on))
    for s, lst, ok in results:
        if not ok:
            failed.append(s["name"])
        for a in lst:
            if a["tags"] & s["skip"] or a["tags"] & ent_tags:
                continue
            max_age = 7 * 86400 if s["section"] == "BEZBEDNOST" else 3 * 86400
            if now - a["ts"] > max_age:
                continue
            if s["section"] in ("SPORT", "BEZBEDNOST"):
                sec = s["section"]
            elif a["tags"] & sport_tags:
                sec = "SPORT"
            elif s["section"] == "SRBIJA" and s["lang"] == "sr" and a["tags"] & world_tags:
                sec = "SVET"
            else:
                sec = s["section"]
            a["section"] = sec
            if a["id"] in seen:
                continue
            key = s["id"] + "|" + norm(a["title"])
            if key in seen_title:
                continue
            seen.add(a["id"]); seen_title.add(key)
            a["cats"] = assign(a, cats)
            arts.append(a)
    arts.sort(key=lambda a: -a["ts"])
    stories = cluster(arts, rank)
    by_sec = {}
    for st in stories:
        by_sec.setdefault(st["section"], []).append(st)
    for sec in by_sec:
        by_sec[sec].sort(key=lambda st: -score(st, now))
        by_sec[sec] = by_sec[sec][:160]

    # bigger pictures for the stories that get a wide card
    want = []
    for sec, lst in by_sec.items():
        for st in lst[:3]:
            lead = st["articles"][0]
            img = lead["image"]
            src = next((s for s in sources if s["id"] == lead["src"]), None)
            small = img and (("i.guim.co.uk" in img and "width=140" in img) or "/thumbs/" in img)
            if (not img or small) and src and src["reader"] and not offline:
                want.append(lead)
    with cf.ThreadPoolExecutor(8) as ex:
        for a, img in zip(want, ex.map(lambda a: og_image(a["link"]), want)):
            if img:
                a["image"] = img

    def pack(a):
        return dict(id=a["id"], s=a["src"], t=lat(a["title"]), u=a["link"], d=lat(a["summary"]), i=a["image"],
                    ts=int(a["ts"]), l=a["live"], c=a["cats"])

    data = dict(
        generated=int(now),
        sources={s["id"]: dict(name=s["name"], section=s["section"], lang=s["lang"]) for s in sources},
        categories=[dict(id=c["id"], label=c["label"], section=c["section"]) for c in cats],
        sections={sec: [dict(id=st["id"], a=[pack(a) for a in st["articles"]]) for st in lst] for sec, lst in by_sec.items()},
        failed=sorted(failed),
    )
    with open(os.path.join(out, "data.json"), "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, separators=(",", ":"))
    total = sum(len(v) for v in by_sec.values())
    print(f"{len(arts)} articles -> {total} stories; failed: {failed}; data.json {os.path.getsize(os.path.join(out, 'data.json')) // 1024} KB")


if __name__ == "__main__":
    main()
