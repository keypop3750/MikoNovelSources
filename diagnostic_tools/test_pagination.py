#!/usr/bin/env python3
"""Test if top-novel and latest-updates support pagination."""
import subprocess

CURL_HEADERS = [
    "-H", "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "-H", "Accept: text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
    "-H", "Accept-Language: en-US,en;q=0.9",
    "-H", "Connection: keep-alive",
    "-L", "-s",
]

def curl_fetch(url):
    cmd = ["curl.exe"] + CURL_HEADERS + [url]
    result = subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=30)
    return result.stdout if result.returncode == 0 else None

# Test top-novel pagination
urls = [
    "https://novelsonline.org/top-novel",
    "https://novelsonline.org/top-novel?page=2",
    "https://novelsonline.org/top-novel/2",
    "https://novelsonline.org/latest-updates?page=2",
]

for url in urls:
    print(f"\nTesting: {url}")
    html = curl_fetch(url)
    if not html:
        print("  FAILED")
        continue
    print(f"  Size: {len(html)} chars")
    
    # Count top-novel blocks
    import re
    blocks = re.findall(r'<div[^>]*class="[^"]*top-novel-block[^"]*"[^>]*>', html)
    print(f"  top-novel-block count: {len(blocks)}")
    
    # Check if redirected
    if "404" in html[:500] or "Not Found" in html[:500]:
        print("  Might be 404 page")
