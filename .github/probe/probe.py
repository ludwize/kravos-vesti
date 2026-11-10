#!/usr/bin/env python3
"""Build the web edition from live feeds, serve it locally and screenshot the new sections."""
import asyncio, collections, json, os, subprocess, sys, time, traceback
OUT = sys.argv[1]; os.makedirs(OUT, exist_ok=True)
L = []
def bail():
    L.append(traceback.format_exc()); open(f"{OUT}/index.txt", "w").write("\n".join(L) + "\n"); print("\n".join(L)); sys.exit(0)
try:
    r = subprocess.run("python3 web/build.py /tmp/site && cp web/index.html /tmp/site/", shell=True, capture_output=True, text=True)
    L.append(r.stdout[-800:] + r.stderr[-1500:])
    d = json.load(open("/tmp/site/data.json"))
    names = {k: v["name"] for k, v in d["sources"].items()}
    for sec in ["AUTO", "LJUBIMCI"]:
        lst = d["sections"].get(sec, [])
        c = collections.Counter(x for st in lst for a in st["a"] for x in a["c"])
        srcs = collections.Counter(names.get(a["s"], a["s"]) for st in lst for a in st["a"])
        L.append(f"\n{sec}: {len(lst)} stories; cats {dict(c)}; sources {dict(srcs)}")
        for st in lst[:25]: L.append(f"   [{','.join(st['a'][0]['c'])}] {names.get(st['a'][0]['s'])}: {st['a'][0]['t'][:90]}")
    subprocess.run("pip install --quiet playwright && python3 -m playwright install --with-deps chromium > /dev/null 2>&1", shell=True)
    srv = subprocess.Popen(["python3", "-m", "http.server", "8765", "-d", "/tmp/site"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    time.sleep(2)
    from playwright.async_api import async_playwright
    async def main():
        async with async_playwright() as p:
            b = await p.chromium.launch(); errs = []
            for name, w, h in [("desk", 1366, 900), ("mob", 390, 844)]:
                pg = await b.new_page(viewport={"width": w, "height": h})
                pg.on("pageerror", lambda e: errs.append(str(e)))
                for tab in ["aktuelno", "auto", "ljubimci", "danas"]:
                    await pg.goto(f"http://localhost:8765/index.html#/{tab}"); await pg.wait_for_timeout(1500)
                    await pg.screenshot(path=f"{OUT}/{name}-{tab}.png")
                await pg.goto("http://localhost:8765/index.html#/auto"); await pg.wait_for_timeout(800)
                await pg.click("#catbtn"); await pg.wait_for_timeout(400); await pg.screenshot(path=f"{OUT}/{name}-auto-menu.png")
            L.append(f"page errors: {errs}")
            await b.close()
    asyncio.run(main()); srv.terminate()
    open(f"{OUT}/index.txt", "w").write("\n".join(L) + "\n"); print("\n".join(L))

except Exception:
    bail()
