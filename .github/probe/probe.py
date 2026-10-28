#!/usr/bin/env python3
"""Open sports.kravos.net in a real browser: page errors, rendered events, screenshots, network refresh cadence."""
import asyncio, os, subprocess, sys
OUT = sys.argv[1]; os.makedirs(OUT, exist_ok=True)
subprocess.run("pip install --quiet playwright && python3 -m playwright install --with-deps chromium > /dev/null 2>&1", shell=True)
from playwright.async_api import async_playwright

async def main():
    log = []
    async with async_playwright() as p:
        b = await p.chromium.launch()
        pg = await b.new_page(viewport={"width": 390, "height": 844})
        errs, reqs = [], []
        pg.on("pageerror", lambda e: errs.append(str(e)))
        pg.on("request", lambda r: reqs.append(r.url) if ("fotmob" in r.url or "espn" in r.url or "euroleague" in r.url) else None)
        await pg.goto("https://sports.kravos.net/?v=2", wait_until="networkidle"); await pg.wait_for_timeout(4000)
        await pg.screenshot(path=f"{OUT}/1-list.png")
        n = await pg.evaluate("document.querySelectorAll('a.ev').length")
        live = await pg.evaluate("[...document.querySelectorAll('a.ev')].filter(a => /'|\\d+\\+?'|Uživo|LIVE/i.test(a.innerText)).length")
        log.append(f"events on list: {n}; looks live: {live}")
        first = len(reqs); await pg.wait_for_timeout(25000)
        log.append(f"data requests during 25 s idle on list: {len(reqs) - first}")
        await pg.click("a.ev"); await pg.wait_for_timeout(4000)
        await pg.screenshot(path=f"{OUT}/2-match.png")
        await pg.mouse.wheel(0, 600); await pg.wait_for_timeout(500)
        y0 = await pg.evaluate("scrollY"); r0 = len(reqs)
        await pg.wait_for_timeout(23000)
        y1 = await pg.evaluate("scrollY")
        log.append(f"match page: requests in 23 s {len(reqs) - r0}; scroll {y0} -> {y1}; spinner now: {await pg.evaluate('!!document.querySelector(\".spin\")')}")
        await pg.screenshot(path=f"{OUT}/3-match-after.png")
        log.append(f"page errors: {errs}")
        await b.close()
    open(f"{OUT}/index.txt", "w").write("\n".join(log) + "\n")
    print("\n".join(log))

asyncio.run(main())
