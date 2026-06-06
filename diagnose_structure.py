#!/usr/bin/env python3
"""
Structural diagnostic: find chapter-like links in raw HTML for each source.
"""

import urllib.request
import urllib.error
import re

HEADERS = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
    'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
    'Accept-Language': 'en-US,en;q=0.5',
}


def fetch(url):
    try:
        req = urllib.request.Request(url, headers=HEADERS)
        with urllib.request.urlopen(req, timeout=30) as resp:
            return resp.read().decode('utf-8', errors='replace')
    except Exception as e:
        return f"ERROR: {e}"


def analyze_chapters(html, source_name):
    print(f"\n--- {source_name} ---")
    if html.startswith('ERROR'):
        print(f"  Fetch failed: {html}")
        return

    # Extract ALL hrefs and their text
    links = re.findall(r'href="([^"]+)"[^>]*>([^<]{1,120})</a>', html, re.IGNORECASE)
    print(f"  Total links: {len(links)}")

    # Find links that look like chapters
    chapter_patterns = [
        r'chapter[_\-]?\d+',
        r'/chapter/',
        r'ch[_\-]?\d+',
        r'/ch\d+',
        r'/ch-?\d+',
        r'/read/\d+',
        r'/read/[^/]+/chapter',
        r'chapter\.html',
        r'chapter[_\-]',
    ]

    candidates = []
    for href, text in links:
        href_l = href.lower()
        text_l = text.strip().lower()
        for pat in chapter_patterns:
            if re.search(pat, href_l) or re.search(pat, text_l):
                candidates.append((href, text.strip()))
                break

    if candidates:
        print(f"  Chapter-like links: {len(candidates)}")
        # Show a sample
        for i, (href, text) in enumerate(candidates[:10]):
            print(f"    [{i}] {href[:70]:<70} | '{text[:50]}'")
        if len(candidates) > 10:
            print(f"    ... ({len(candidates)-10} more)")
    else:
        print(f"  No chapter-like links found. Showing first 15 general links:")
        for i, (href, text) in enumerate(links[:15]):
            print(f"    [{i}] {href[:70]:<70} | '{text[:50]}'")

    # Look for chapter containers (ul/ol/div with chapter-related classes)
    containers = re.findall(
        r'<(ul|ol|div)[^>]*class=["\']([^"\']*(?:chapter|chapters|chapter-list|chapterlist|toc|volume)[^"\']*)["\'][^>]*>',
        html, re.IGNORECASE
    )
    if containers:
        print(f"  Chapter containers found: {len(containers)}")
        for tag, cls in containers[:5]:
            print(f"    <{tag} class=\"{cls}\">")

    # Look for input/select elements that might hold chapter data
    inputs = re.findall(r'<(input|select)[^>]*id=["\']([^"\']*(?:chapter|chapters|mypostid|sid|story)[^"\']*)["\'][^>]*>', html, re.IGNORECASE)
    if inputs:
        print(f"  Data inputs found: {len(inputs)}")
        for tag, idval in inputs[:5]:
            print(f"    <{tag} id=\"{idval}\">")


SOURCES = {
    "NovelFull": "https://novelfull.com/the-mech-touch.html",
    "NovelsOnline": "https://novelsonline.org/brand-new-life-online-rise-of-the-goddess-of-harvest",
    "NovelsOnline_chapter": "https://novelsonline.org/brand-new-life-online-rise-of-the-goddess-of-harvest/chapter-1802",
    "ReadNovelFull": "https://readnovelfull.com/the-mech-touch.html",
    "RoyalRoad": "https://www.royalroad.com/fiction/21220/mother-of-learning",
    "FreeWebNovel": "https://freewebnovel.com/necropolis-immortal.html",
    "LightNovelPub": "https://www.lightnovelpub.com/novel/omniscient-readers-viewpoint-23021325",
    "ScribbleHub": "https://www.scribblehub.com/read/2351740-i-reincarnated-as-the-villainess-and-i-refuse-to-follow-the-plot/chapter/2379453/",
    "ScribbleHub_series": "https://www.scribblehub.com/series/2351740/i-reincarnated-as-the-villainess-and-i-refuse-to-follow-the-plot/",
    "BoxNovel": "https://boxnovel.com/novel/absolute-resonance/",
    "AllNovel": "https://allnovel.org/the-mech-touch",
}

for name, url in SOURCES.items():
    html = fetch(url)
    analyze_chapters(html, name)

print("\nDone.")
