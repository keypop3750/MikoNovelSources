#!/usr/bin/env python3
"""Inspect latest updates page structure."""
import re

html = open(r'c:\Users\karol\OneDrive\Documents\GitHub\MikoNovelSources\scrape_output\novelsonline_latest-updates.html', encoding='utf-8').read()

print("=== Latest Updates Structure ===")

# The list-by-word-body container
m = re.search(r'(<div[^>]*class="list-by-word-body"[^>]*>.*?)<div[^>]*class="row">', html, re.S)
if m:
    body = m.group(1)
    print(f"list-by-word-body length: {len(body)} chars")
    
    # Find all li items
    lis = re.findall(r'<li[^>]*>(.*?)</li>', body, re.S)
    print(f"li items: {len(lis)}")
    
    for li in lis[:5]:
        a = re.search(r'<a[^>]*href="([^"]+)"[^>]*>(.*?)</a>', li, re.S)
        if a:
            href = a.group(1)
            text = re.sub(r'<[^>]+>', '', a.group(2)).strip()
            
            # Extract novel URL from chapter URL
            # URL: /novel-slug/chapter-1684
            novel_url = href.rsplit('/', 1)[0] if '/' in href else ''
            
            # Extract title from text
            # Text: "Brand New Life Online: Rise Of The Goddess Of Harv.. Ch. 1684"
            title = text.rsplit(' Ch. ', 1)[0] if ' Ch. ' in text else text
            
            print(f"  href: {href}")
            print(f"  text: {text}")
            print(f"  novel_url: {novel_url}")
            print(f"  title: {title}")
            print()

# Check if there's a better container
print("\n=== Looking for main content structure ===")
m = re.search(r'<div[^>]*class="col-xs-12[^"]*col-md-9[^"]*"[^>]*>(.*?)</div>\s*</div>\s*</div>', html, re.S)
if m:
    content = m.group(1)
    print(f"Main content length: {len(content)} chars")
    
    # Find all chapter links in main content
    links = re.findall(r'<a[^>]*href="(/[^"/]+/chapter-[^"]+)"[^>]*>(.*?)</a>', content, re.S)
    print(f"Chapter links: {len(links)}")
    for href, text in links[:5]:
        text = re.sub(r'<[^>]+>', '', text).strip()
        if text:
            novel_url = href.rsplit('/', 1)[0]
            title = text.rsplit(' Ch. ', 1)[0] if ' Ch. ' in text else text
            print(f"  {novel_url} -> {title}")

# Look at page title
m = re.search(r'<title>([^<]+)</title>', html)
if m:
    print(f"\nPage title: {m.group(1).strip()}")

# Check for pagination
m = re.search(r'<ul[^>]*class="pagination"[^>]*>(.*?)</ul>', html, re.S)
if m:
    print("Pagination found")
    pages = re.findall(r'>(\d+)<', m.group(1))
    print(f"  Pages: {pages}")
else:
    print("No pagination found")
