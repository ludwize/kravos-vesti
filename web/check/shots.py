"""Open the live site in a real browser and save screenshots + basic stats (runs on a GitHub runner)."""
import asyncio, json, sys, urllib.request
from playwright.async_api import async_playwright

URL, OUT = sys.argv[1].rstrip("/") + "/", sys.argv[2]

async def main():
    d = json.load(urllib.request.urlopen(URL + "data.json"))
    with open(f"{OUT}/stats.txt", "w") as f:
        f.write(f"generated {d['generated']} failed {d['failed']}\n")
        for k, v in d["sections"].items():
            f.write(f"{k}: {len(v)} stories; top: {v[0]['a'][0]['t'][:80] if v else '-'}\n")
    async with async_playwright() as p:
        b = await p.chromium.launch()
        errs = []
        for name, w, h, scheme in [("desk", 1366, 900, "light"), ("mob", 390, 844, "dark")]:
            pg = await b.new_page(viewport={"width": w, "height": h}, color_scheme=scheme)
            pg.on("pageerror", lambda e: errs.append(str(e)))
            await pg.goto(URL + "#/danas", wait_until="networkidle"); await pg.wait_for_timeout(1500)
            await pg.screenshot(path=f"{OUT}/{name}-1-danas.png")
            await pg.goto(URL + "#/svet", wait_until="networkidle"); await pg.wait_for_timeout(1000)
            await pg.screenshot(path=f"{OUT}/{name}-2-svet.png")
            await pg.click(".story[data-s]"); await pg.wait_for_timeout(1200)
            await pg.screenshot(path=f"{OUT}/{name}-3-panel.png")
            await pg.goto(URL + "#/bezbednost?n", wait_until="networkidle"); await pg.wait_for_timeout(1000)
            await pg.screenshot(path=f"{OUT}/{name}-4-sajber-najnovije.png")
        open(f"{OUT}/stats.txt", "a").write(f"page errors: {errs}\n")
        await b.close()

asyncio.run(main())
