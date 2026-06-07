#!/usr/bin/env python3
"""Scraper to analyze NovelsOnline.org HTML structure."""
import subprocess
import re
import json

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
    if result.returncode != 0:
        print(f"  CURL ERROR: {result.returncode}")
        return None
    return result.stdout

urls = [
    ("Book page", "https://novelsonline.org/tensei-shitara-slime-datta-ken-ln"),
    ("Chapter 1", "https://novelsonline.org/tensei-shitara-slime-datta-ken-ln/volume-22/chapter-1"),
    ("Chapter PR", "https://novelsonline.org/tensei-shitara-slime-datta-ken-ln/volume-1/chapter-pr"),
    ("Top Novels", "https://novelsonline.org/top-novel"),
    ("Latest Updates", "https://novelsonline.org/latest-updates"),
    ("Homepage", "https://novelsonline.org/"),
]

for label, url in urls:
    print(f"\n{'='*70}")
    print(f"{label}: {url}")
    print('='*70)

    html = curl_fetch(url)
    if not html:
        print("  FAILED")
        continue

    print(f"  Page size: {len(html)} chars")

    # Title
    m = re.search(r'<title>([^<]+)</title>', html)
    if m:
        print(f"  Title: {m.group(1).strip()}")

    # Check for Cloudflare / protection
    if "Checking your browser" in html or "cf-browser-verification" in html:
        print("  WARNING: Cloudflare/JS challenge detected!")
    if "Just a moment" in html:
        print("  WARNING: Cloudflare challenge page!")

    # Save for inspection
    slug = url.replace("https://novelsonline.org/", "").replace("/", "_") or "homepage"
    out_path = f"c:\\Users\\karol\\OneDrive\\Documents\\GitHub\\MikoNovelSources\\scrape_output\\novelsonline_{slug}.html"
    with open(out_path, 'w', encoding='utf-8') as f:
        f.write(html)
    print(f"  Saved to: {out_path}")

    # For book page: look for chapters, description, cover, author
    if "tensei-shitara-slime" in url and "/volume-" not in url and "/chapter-" not in url:
        print("\n  --- Book page analysis ---")

        # Title
        m = re.search(r'<h1[^>]*>(.*?)</h1>', html, re.S)
        if m:
            print(f"  H1 title: {re.sub(r'<[^>]+>', '', m.group(1)).strip()}")

        # Cover
        m = re.search(r'<div[^>]*class="[^"]*novel-cover[^"]*"[^>]*>.*?<img[^>]*src="([^"]+)"', html, re.S)
        if m:
            print(f"  Cover: {m.group(1)}")

        # Description
        m = re.search(r'<div[^>]*class="[^"]*novel-detail-item[^"]*"[^>]*>.*?Description.*?</div>.*?<div[^>]*class="[^"]*novel-detail-body[^"]*"[^>]*>(.*?)</div>', html, re.S|re.I)
        if m:
            desc = re.sub(r'<[^>]+>', ' ', m.group(1)).strip()
            print(f"  Description: {desc[:200]}...")

        # Author
        m = re.search(r'<div[^>]*class="[^"]*novel-detail-item[^"]*"[^>]*>.*?Author.*?</div>.*?<div[^>]*class="[^"]*novel-detail-body[^"]*"[^>]*>(.*?)</div>', html, re.S|re.I)
        if m:
            author = re.sub(r'<[^>]+>', ' ', m.group(1)).strip()
            print(f"  Author: {author}")

        # Chapter list
        ch_links = re.findall(r'<a[^>]*href="([^"]*tensei-shitara-slime[^"]*)"[^>]*>(.*?)</a>', html, re.S)
        unique = {}
        for href, text in ch_links:
            text = re.sub(r'<[^>]+>', '', text).strip()
            if text and len(text) > 2:
                unique[href] = text
        print(f"  Chapter links found: {len(unique)}")
        for href, text in list(unique.items())[:10]:
            print(f"    {href} -> {text}")

    # For chapter pages: look for content
    if "/volume-" in url:
        print("\n  --- Chapter page analysis ---")

        # Content
        m = re.search(r'<div[^>]*id="contentall"[^>]*>(.*?)</div>', html, re.S)
        if m:
            content = re.sub(r'<[^>]+>', ' ', m.group(1)).strip()
            print(f"  #contentall length: {len(content)} chars")
            print(f"    Preview: {content[:200]}...")
        else:
            print("  #contentall NOT FOUND")
            # Try alternatives
            for sel in ['chapter-content3', 'chapter-content', 'content', 'entry-content']:
                m = re.search(rf'<div[^>]*class="[^"]*{sel}[^"]*"[^>]*>(.*?)</div>', html, re.S)
                if m:
                    content = re.sub(r'<[^>]+>', ' ', m.group(1)).strip()
                    print(f"  .{sel} found: {len(content)} chars")
                    break

        # Prev/Next
        for name in ['prev', 'previous', 'next']:
            m = re.search(rf'<a[^>]*href="([^"]*)"[^>]*class="[^"]*{name}[^"]*"', html, re.S|re.I)
            if m:
                print(f"  {name}: {m.group(1)}")

    # For top-novel page
    if "top-novel" in url:
        print("\n  --- Top Novels analysis ---")
        blocks = re.findall(r'<div[^>]*class="[^"]*top-novel-block[^"]*"[^>]*>(.*?)</div>\s*</div>\s*</div>', html, re.S)
        if not blocks:
            # Try simpler pattern
            blocks = re.findall(r'<div[^>]*class="[^"]*top-novel-block[^"]*"[^>]*>(.*?)</div>', html, re.S)
        print(f"  top-novel-block count: {len(blocks)}")
        for block in blocks[:3]:
            title_m = re.search(r'<h2[^>]*>.*?<a[^>]*href="([^"]+)"[^>]*>(.*?)</a>', block, re.S)
            cover_m = re.search(r'<img[^>]*src="([^"]+)"', block)
            if title_m:
                title = re.sub(r'<[^>]+>', '', title_m.group(2)).strip()
                print(f"    Title: {title}")
                print(f"    URL: {title_m.group(1)}")
            if cover_m:
                print(f"    Cover: {cover_m.group(1)}")

    # For latest-updates
    if "latest-updates" in url:
        print("\n  --- Latest Updates analysis ---")
        lis = re.findall(r'<li[^>]*>(.*?)</li>', html, re.S)
        novel_items = []
        for li in lis:
            a = re.search(r'<a[^>]*href="([^"]+)"[^>]*>(.*?)</a>', li, re.S)
            if a:
                href = a.group(1)
                text = re.sub(r'<[^>]+>', '', a.group(2)).strip()
                if text and len(text) > 3:
                    novel_items.append((href, text))
        print(f"  List items with links: {len(novel_items)}")
        for href, text in novel_items[:5]:
            print(f"    {href} -> {text}")

print(f"\n{'='*70}")
print("ANALYSIS COMPLETE")
print('='*70)
