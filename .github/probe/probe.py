#!/usr/bin/env python3
"""Check DNS + HTTPS for the custom domain of the web edition."""
import os, subprocess, sys
OUT = sys.argv[1]; os.makedirs(OUT, exist_ok=True)
cmds = [
    "dig +short CNAME vesti.kravos.net",
    "curl -sI -m 20 http://vesti.kravos.net | head -4",
    "curl -sI -m 20 https://vesti.kravos.net | head -3",
    "curl -sI -m 20 https://ludwize.github.io/kravos-vesti/ | grep -i -E '^(HTTP|location)'",
    "curl -s -m 20 https://vesti.kravos.net/data.json | python3 -c 'import json,sys,time; d=json.load(sys.stdin); print(\"generated\", round((time.time()-d[\"generated\"])/60), \"min ago;\", {k: len(v) for k, v in d[\"sections\"].items()})'",
]
with open(os.path.join(OUT, "index.txt"), "w") as f:
    for c in cmds:
        r = subprocess.run(c, shell=True, capture_output=True, text=True)
        f.write(f"$ {c}\n{r.stdout}{r.stderr}\n")
print(open(os.path.join(OUT, "index.txt")).read())
