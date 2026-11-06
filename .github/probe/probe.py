#!/usr/bin/env python3
"""Why does vesti.kravos.net look stale? Check data.json freshness, newest article per section, failing feeds, cache headers."""
import json, os, subprocess, sys, time, urllib.request
OUT = sys.argv[1]; os.makedirs(OUT, exist_ok=True)
L = []
def sh(c): return subprocess.run(c, shell=True, capture_output=True, text=True).stdout.strip()
for u in ["https://vesti.kravos.net/", "https://vesti.kravos.net/data.json"]:
    L.append(u + "\n" + sh(f"curl -sI -m 20 '{u}' | grep -i -E '^(HTTP|last-modified|cache-control|age|x-cache|etag|expires)'"))
d = json.load(urllib.request.urlopen("https://vesti.kravos.net/data.json?x=%d" % time.time()))
now = time.time()
L.append(f"generated {round((now - d['generated'])/60)} min ago; failed: {d['failed']}")
for sec, lst in d["sections"].items():
    ts = [a["ts"] for st in lst for a in st["a"]]
    by = {}
    for st in lst:
        for a in st["a"]: by[a["s"]] = max(by.get(a["s"], 0), a["ts"])
    stale = {k: round((now - v)/3600, 1) for k, v in sorted(by.items(), key=lambda x: x[1])}
    L.append(f"{sec}: {len(lst)} stories, newest article {round((now - max(ts))/60)} min ago; top: {lst[0]['a'][0]['t'][:70]}\n   newest per source (h ago): {stale}")
# fetch feeds directly from the runner too
for name, url in [("n1", "https://n1info.rs/feed/"), ("danas", "https://www.danas.rs/feed/"), ("nova", "https://nova.rs/feed/"), ("bbc", "https://feeds.bbci.co.uk/news/world/rss.xml")]:
    L.append(f"direct {name}: " + sh(f"curl -s -m 20 -A 'Mozilla/5.0' -o /tmp/f.xml -w '%{{http_code}} %{{size_download}}' '{url}'; grep -o -m1 '<pubDate>[^<]*' /tmp/f.xml | head -2 | tr '\\n' ' '"))
open(f"{OUT}/index.txt", "w").write("\n".join(L) + "\n"); print("\n".join(L))
