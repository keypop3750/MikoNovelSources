#!/usr/bin/env python3
"""Detailed analysis of NovelsOnline structure."""
import re

# Analyze book page
html = open(r'c:\Users\karol\OneDrive\Documents\GitHub\MikoNovelSources\scrape_output\novelsonline_tensei-shitara-slime-datta-ken-ln.html', encoding='utf-8').read()

print("=" * 70)
print("BOOK PAGE ANALYSIS")
print("=" * 70)

# Title
m = re.search(r'<h1[^>]*>(.*?)</h1>', html, re.S)
if m:
    print(f"H1: {re.sub(r'<[^>]+>', '', m.group(1)).strip()}")

# Cover
m = re.search(r'<div[^>]*class="[^"]*novel-cover[^"]*"[^>]*>.*?<img[^>]*src="([^"]+)"', html, re.S)
if m:
    print(f"Cover: {m.group(1)}")

# Look for novel-detail-item blocks
detail_items = re.findall(r'<div[^>]*class="novel-detail-item"[^>]*>(.*?)</div>\s*</div>', html, re.S)
print(f"\nNovel detail items: {len(detail_items)}")
for item in detail_items:
    header = re.search(r'<div[^>]*class="novel-detail-header"[^>]*>(.*?)</div>', item, re.S)
    body = re.search(r'<div[^>]*class="novel-detail-body"[^>]*>(.*?)</div>', item, re.S)
    if header and body:
        h_text = re.sub(r'<[^>]+>', '', header.group(1)).strip()
        b_text = re.sub(r'<[^>]+>', '', body.group(1)).strip()
        print(f"  [{h_text}] {b_text[:100]}")

# Chapter list - look for chapter-chs
ch_items = re.findall(r'<li[^>]*>(.*?)</li>', html, re.S)
chapter_links = []
for li in ch_items:
    a = re.search(r'<a[^>]*href="([^"]*tensei-shitara-slime[^"]*)"[^>]*>(.*?)</a>', li, re.S)
    if a:
        href = a.group(1)
        text = re.sub(r'<[^>]+>', '', a.group(2)).strip()
        if text:
            chapter_links.append((href, text))
print(f"\nChapter links in book page: {len(chapter_links)}")
for href, text in chapter_links[:10]:
    print(f"  {href} -> {text}")

# Look for any chapter listing structure
print("\n--- Looking for chapter list patterns ---")
# Find ul with chapter links
uls = re.findall(r'<ul[^>]*class="[^"]*chapter[^"]*"[^>]*>(.*?)</ul>', html, re.S)
print(f"UL with 'chapter' in class: {len(uls)}")
for ul in uls:
    links = re.findall(r'<a[^>]*href="([^"]+)"[^>]*>(.*?)</a>', ul, re.S)
    print(f"  Contains {len(links)} links")

# Look for volume structures
volumes = re.findall(r'<div[^>]*class="[^"]*volume[^"]*"[^>]*>(.*?)</div>', html, re.S|re.I)
print(f"Volume divs: {len(volumes)}")

# Look for chapter list panel
panels = re.findall(r'<div[^>]*class="panel panel-default"[^>]*>(.*?)</div>\s*</div>', html, re.S)
print(f"Panel panel-default divs: {len(panels)}")
for i, panel in enumerate(panels[:3]):
    header = re.search(r'<div[^>]*class="panel-heading"[^>]*>(.*?)</div>', panel, re.S)
    if header:
        h = re.sub(r'<[^>]+>', '', header.group(1)).strip()
        print(f"  Panel {i}: {h}")
    links = re.findall(r'<a[^>]*href="([^"]+)"[^>]*>(.*?)</a>', panel, re.S)
    print(f"    Links: {len(links)}")

# ======================
# LATEST UPDATES ANALYSIS
# ======================
print(f"\n{'='*70}")
print("LATEST UPDATES ANALYSIS")
print("=" * 70)

html2 = open(r'c:\Users\karol\OneDrive\Documents\GitHub\MikoNovelSources\scrape_output\novelsonline_latest-updates.html', encoding='utf-8').read()

# The latest updates page likely has a different structure
# Look for novel links with chapter info
# Each entry might be: novel title + chapter info
m = re.search(r'<div[^>]*class="list-by-word-body"[^>]*>(.*?)</div>\s*</div>', html2, re.S)
if m:
    body = m.group(1)
    print("Found list-by-word-body container")
    lis = re.findall(r'<li[^>]*>(.*?)</li>', body, re.S)
    print(f"  Contains {len(lis)} li items")
    for li in lis[:5]:
        a = re.search(r'<a[^>]*href="([^"]+)"[^>]*>(.*?)</a>', li, re.S)
        if a:
            href = a.group(1)
            text = re.sub(r'<[^>]+>', '', a.group(2)).strip()
            print(f"    {href}")
            print(f"      Text: {text}")
else:
    print("No list-by-word-body found")
    # Look for any main content area
    m = re.search(r'<div[^>]*class="[^"]*col-xs-12[^"]*col-md-9[^"]*"[^>]*>(.*?)</div>\s*</div>\s*</div>', html2, re.S)
    if m:
        print("Found main content area")
        content = m.group(1)
        # Look for all novel links
        links = re.findall(r'<a[^>]*href="/([^"/]+)"[^>]*>(.*?)</a>', content, re.S)
        print(f"  Novel links found: {len(links)}")
        for href, text in links[:10]:
            text = re.sub(r'<[^>]+>', '', text).strip()
            if text and len(text) > 2 and not text.startswith('Home'):
                print(f"    /{href} -> {text}")

# Look for novel blocks (similar to top-novel-block but for latest)
blocks = re.findall(r'<div[^>]*class="[^"]*novel-block[^"]*"[^>]*>(.*?)</div>\s*</div>', html2, re.S)
print(f"\nNovel blocks: {len(blocks)}")

# Look for row structures
rows = re.findall(r'<div[^>]*class="row"[^>]*>(.*?)</div>\s*</div>', html2, re.S)
print(f"Row divs: {len(rows)}")
for row in rows[:5]:
    imgs = re.findall(r'<img[^>]*src="([^"]+)"', row)
    links = re.findall(r'href="/([^"/]+)"', row)
    if imgs or links:
        print(f"  Row with imgs={len(imgs)} links={len(links)}")
        for l in links[:3]:
            print(f"    /{l}")

# ======================
# HOMEPAGE ANALYSIS
# ======================
print(f"\n{'='*70}")
print("HOMEPAGE ANALYSIS")
print("=" * 70)

html3 = open(r'c:\Users\karol\OneDrive\Documents\GitHub\MikoNovelSources\scrape_output\novelsonline_homepage.html', encoding='utf-8').read()

# Look for section titles and their content
sections = [
    ("latest updates", r'Latest Updates|Lastest Updates'),
    ("top novels", r'Top Novels'),
    ("completed", r'Completed'),
    ("most popular", r'Most Popular'),
]

for name, pattern in sections:
    m = re.search(rf'<h[2-4][^>]*>.*?{pattern}.*?</h[2-4]>', html3, re.S|re.I)
    if m:
        print(f"\nSection: {name}")
        # Find the parent container
        heading = m.group(0)
        pos = m.end()
        # Look ahead for content
        snippet = html3[pos:pos+2000]
        # Find first row or div with links
        links = re.findall(r'<a[^>]*href="/([^"/]+)"[^>]*>(.*?)</a>', snippet, re.S)
        print(f"  Links in section: {len(links)}")
        for href, text in links[:5]:
            text = re.sub(r'<[^>]+>', '', text).strip()
            if text and len(text) > 2 and not text.startswith('Read more'):
                print(f"    /{href} -> {text}")

# Look for widget/section structures
widgets = re.findall(r'<div[^>]*class="widget[^"]*"[^>]*>(.*?)</div>\s*</div>', html3, re.S)
print(f"\nWidget divs: {len(widgets)}")
for w in widgets[:3]:
    title = re.search(r'<h[2-4][^>]*>(.*?)</h[2-4]>', w, re.S)
    if title:
        t = re.sub(r'<[^>]+>', '', title.group(1)).strip()
        print(f"  Widget: {t}")
