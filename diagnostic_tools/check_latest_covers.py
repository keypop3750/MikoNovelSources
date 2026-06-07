#!/usr/bin/env python3
"""Check if latest updates page has cover images."""
import re

html = open(r'c:\Users\karol\OneDrive\Documents\GitHub\MikoNovelSources\scrape_output\novelsonline_latest-updates.html', encoding='utf-8').read()

# Look for img tags in the list-by-word-body
m = re.search(r'(<div[^>]*class="list-by-word-body"[^>]*>.*?)<div[^>]*class="row">', html, re.S)
if m:
    body = m.group(1)
    imgs = re.findall(r'<img[^>]*src="([^"]+)"', body)
    print(f"Images in list-by-word-body: {len(imgs)}")
    for img in imgs[:5]:
        print(f"  {img}")

# Look for any img tags in the main content
m = re.search(r'<div[^>]*class="col-xs-12[^"]*col-md-9[^"]*"[^>]*>(.*?)</div>\s*</div>\s*</div>', html, re.S)
if m:
    content = m.group(1)
    imgs = re.findall(r'<img[^>]*src="([^"]+)"', content)
    print(f"\nImages in main content: {len(imgs)}")
    for img in imgs[:10]:
        print(f"  {img}")

# Check if each li has any associated image
lis = re.findall(r'<li[^>]*>(.*?)</li>', m.group(1) if m else html, re.S)
for i, li in enumerate(lis[:5]):
    has_img = '<img' in li
    a = re.search(r'<a[^>]*href="([^"]+)"[^>]*>(.*?)</a>', li, re.S)
    if a:
        text = re.sub(r'<[^>]+>', '', a.group(2)).strip()
        print(f"\nli {i}: '{text[:60]}'")
        print(f"  Has image: {has_img}")
