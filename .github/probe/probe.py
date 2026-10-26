#!/usr/bin/env python3
"""Check DNS + HTTPS for the custom domain of the web edition."""
import os, subprocess, sys
OUT = sys.argv[1]; os.makedirs(OUT, exist_ok=True)
cmds = [
    "dig +short CNAME vesti.kravos.net",
    "dig +short A vesti.kravos.net",
    "dig +short TXT _github-pages-challenge-ludwize.kravos.net",
    "dig +short NS kravos.net",
    "curl -sI -m 20 http://vesti.kravos.net | head -5",
    "curl -sI -m 20 https://vesti.kravos.net | head -5",
    "echo | openssl s_client -connect vesti.kravos.net:443 -servername vesti.kravos.net 2>/dev/null | openssl x509 -noout -subject -issuer -dates",
    "curl -sI -m 20 https://ludwize.github.io/kravos-vesti/ | head -5",
]
with open(os.path.join(OUT, "index.txt"), "w") as f:
    for c in cmds:
        r = subprocess.run(c, shell=True, capture_output=True, text=True)
        f.write(f"$ {c}\n{r.stdout}{r.stderr}\n")
print(open(os.path.join(OUT, "index.txt")).read())
