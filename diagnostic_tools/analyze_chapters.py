#!/usr/bin/env python3
"""Analyze chapter URL patterns and page structure for multiple chapters."""
import subprocess
import re
import os

OUTPUT_DIR = r'c:\Users\karol\OneDrive\Documents\GitHub\MikoNovelSources\scrape_output'

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

def extract_div_content(html, class_name):
    start_tag = f'<div class="{class_name}'
    idx = html.find(start_tag)
    if idx == -1:
        # Try with leading space or other variations
        start_tag = f'class="{class_name}'
        idx = html.find(start_tag)
    if idx == -1:
        return None
    tag_end = html.find('>', idx)
    if tag_end == -1:
        return None
    content_start = tag_end + 1
    depth = 1
    pos = content_start
    in_string = False
    while pos < len(html) and depth > 0:
        c = html[pos]
        if c == '"':
            in_string = not in_string
        elif not in_string:
            if html[pos:pos+4] == '<div':
                depth += 1
            elif html[pos:pos+6] == '</div>':
                depth -= 1
                if depth == 0:
                    return html[content_start:pos]
        pos += 1
    return None

urls = [
    "https://www.webnovel.com/book/shadow-slave_22196546206090805/one-small-mistake_61205775955050790",
    "https://www.webnovel.com/book/shadow-slave_22196546206090805/bright-castle_62040600005889470",
    "https://www.webnovel.com/book/shadow-slave_22196546206090805/rubicon_62296775762957384",
    "https://www.webnovel.com/book/shadow-slave_22196546206090805/the-past_62316035201629285",
]

print("=" * 70)
print("CHAPTER URL PATTERN ANALYSIS")
print("=" * 70)

# First, analyze URL structure
print("\n--- URL Structure ---")
for url in urls:
    parts = url.split("/")
    book_part = parts[-2]  # shadow-slave_22196546206090805
    chapter_part = parts[-1]  # one-small-mistake_61205775955050790
    book_slug, book_id = book_part.rsplit("_", 1)
    ch_slug, ch_id = chapter_part.rsplit("_", 1)
    print(f"  Book: slug='{book_slug}', id='{book_id}'")
    print(f"  Chapter: slug='{ch_slug}', id='{ch_id}'")

print("\n--- Fetching and analyzing each chapter ---")
for i, url in enumerate(urls):
    print(f"\n{'='*70}")
    print(f"Chapter {i+1}: {url.split('/')[-1]}")
    print('='*70)

    html = curl_fetch(url)
    if not html:
        print("  FAILED to fetch")
        continue

    print(f"  Page size: {len(html)} chars")

    # Title
    m = re.search(r'<title>([^<]+)</title>', html)
    if m:
        print(f"  Title: {m.group(1).strip()}")

    # Check for chapter content
    words_html = extract_div_content(html, "cha-words")
    if words_html:
        paras = re.findall(r'<p[^>]*>(.*?)</p>', words_html, re.DOTALL)
        texts = [re.sub(r'<[^>]+>', '', p).strip() for p in paras]
        texts = [t for t in texts if len(t) > 10]
        print(f"  Content: cha-words div with {len(texts)} paragraphs, {sum(len(t) for t in texts)} chars")
        print(f"    First: {texts[0][:120]}...")
        print(f"    Last:  {texts[-1][:120]}...")
    else:
        print("  Content: NO cha-words div found!")

    # Check for prev/next chapter links
    prev_match = re.search(r'href="(/book/[^"]+)"[^>]*class="[^"]*prev[^"]*"', html, re.I)
    next_match = re.search(r'href="(/book/[^"]+)"[^>]*class="[^"]*next[^"]*"', html, re.I)
    if prev_match:
        print(f"  Prev: {prev_match.group(1)}")
    if next_match:
        print(f"  Next: {next_match.group(1)}")

    # Check for chapter list / catalog links
    ch_links = re.findall(r'href="(/book/[^"]+/[^"_]+_[0-9]+)"', html)
    unique_links = list(dict.fromkeys(ch_links))  # preserve order, dedupe
    print(f"  Chapter links in page: {len(unique_links)}")
    for link in unique_links[:5]:
        print(f"    {link}")

    # Check for JSON state
    scripts = re.findall(r'<script[^>]*>(.*?)</script>', html, re.DOTALL)
    has_state = any('initialState' in s for s in scripts)
    print(f"  Has initialState JSON: {has_state}")

    # Look for data-cid (chapter ID in data attributes)
    data_cids = re.findall(r'data-cid="([0-9]+)"', html)
    if data_cids:
        print(f"  data-cid: {data_cids[0]}")

    # Look for isLocked / paywall info
    lock_match = re.search(r'data-islock="([01])"', html)
    price_match = re.search(r'data-price="([0-9]+)"', html)
    if lock_match:
        print(f"  isLocked: {lock_match.group(1)}")
    if price_match:
        print(f"  price: {price_match.group(1)}")

print(f"\n{'='*70}")
print("ANALYSIS COMPLETE")
print('='*70)

# Summary
print("\n--- URL Pattern Summary ---")
print("Format: https://www.webnovel.com/book/{book-slug}_{book-id}/{chapter-slug}_{chapter-id}")
print("The chapter slug is the chapter name in kebab-case.")
print("The chapter ID is a 17-digit numeric ID.")
