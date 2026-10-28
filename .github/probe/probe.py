#!/usr/bin/env python3
"""Check DNS + HTTPS for every Kravos web edition (guessing likely subdomains)."""
import os, subprocess, sys
OUT = sys.argv[1]; os.makedirs(OUT, exist_ok=True)
hosts = ["vesti", "vreme", "weather", "sports", "sport", "jezik", "bas", "bass", "tab"]
def sh(c):
    return subprocess.run(c, shell=True, capture_output=True, text=True).stdout.strip()
with open(os.path.join(OUT, "index.txt"), "w") as f:
    for h in hosts:
        d = f"{h}.kravos.net"
        cname = sh(f"dig +short CNAME {d}")
        if not cname:
            f.write(f"{d}: no CNAME ({sh(f'dig +short A {d}') or 'no record'})\n\n"); continue
        http = sh(f"curl -s -o /dev/null -w '%{{http_code}} -> %{{redirect_url}}' -m 20 http://{d}/")
        https = sh(f"curl -s -o /dev/null -w '%{{http_code}}' -m 20 https://{d}/")
        cert = sh(f"echo | openssl s_client -connect {d}:443 -servername {d} 2>/dev/null | openssl x509 -noout -subject -enddate")
        lm = sh(f"curl -sI -m 20 https://{d}/ | grep -i last-modified")
        title = sh(f"curl -s -m 20 https://{d}/ | grep -o -i '<title>[^<]*' | head -1")
        f.write(f"{d}: CNAME {cname}\n  http  {http}\n  https {https}  {title}\n  cert  {cert.replace(chr(10), ' | ')}\n  {lm}\n\n")
    for r in ["kravos-weather", "kravos-sports", "kravos-jezik", "kravos-bass"]:
        f.write(f"ludwize.github.io/{r}/ -> " + sh(f"curl -s -o /dev/null -w '%{{http_code}} %{{redirect_url}}' -m 20 https://ludwize.github.io/{r}/") + "\n")
print(open(os.path.join(OUT, "index.txt")).read())
