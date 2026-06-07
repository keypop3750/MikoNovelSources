#!/usr/bin/env python3
"""Inspect chapter list structure in detail."""
import re

html = open(r'c:\Users\karol\OneDrive\Documents\GitHub\MikoNovelSources\scrape_output\novelsonline_tensei-shitara-slime-datta-ken-ln.html', encoding='utf-8').read()

print("=== Chapter List Structure ===")

# Find all panel-default divs
panels = re.findall(r'(<div[^>]*class="panel panel-default"[^>]*>.*?)</div>\s*</div>\s*</div>', html, re.S)
print(f"Total panels: {len(panels)}")

for i, panel in enumerate(panels[:3]):
    print(f"\n--- Panel {i} ---")
    # Extract heading
    heading = re.search(r'<div[^>]*class="panel-heading"[^>]*>(.*?)</div>', panel, re.S)
    if heading:
        h_text = re.sub(r'<[^>]+>', '', heading.group(1)).strip()
        print(f"  Heading: {h_text}")
    
    # Extract all links in this panel
    links = re.findall(r'<a[^>]*href="([^"]+)"[^>]*>(.*?)</a>', panel, re.S)
    print(f"  Links: {len(links)}")
    for href, text in links[:5]:
        text = re.sub(r'<[^>]+>', '', text).strip()
        if text:
            print(f"    {href} -> {text}")

# Also check if there's a better overall selector
print("\n\n=== Looking for chapter list container ===")
# The chapter list might be in a specific div
m = re.search(r'<div[^>]*class="col-xs-12[^"]*col-md-9[^"]*"[^>]*>(.*?)</div>\s*</div>\s*</div>', html, re.S)
if m:
    print("Found main content area")
    content = m.group(1)
    # Count all chapter links
    all_links = re.findall(r'href="(/[^"/]+/volume-\d+/chapter-[^"]+)"', content)
    print(f"  Volume/chapter links: {len(all_links)}")
    
    # Count panel headings  
    headings = re.findall(r'<div[^>]*class="panel-heading"[^>]*>(.*?)</div>', content, re.S)
    print(f"  Panel headings: {len(headings)}")
    for h in headings[:5]:
        print(f"    {re.sub(r'<[^>]+>', '', h).strip()}")

# Check if there's an alternative chapter list structure
print("\n\n=== Alternative: raw li > a in book page ===")
lis = re.findall(r'<li[^>]*>(.*?)</li>', html, re.S)
ch_li = [(href, re.sub(r'<[^>]+>', '', text).strip()) 
         for li in lis 
         for href, text in re.findall(r'<a[^>]*href="(/[^"/]+/volume-\d+/chapter-[^"]+)"[^>]*>(.*?)</a>', li, re.S)
         if re.sub(r'<[^>]+>', '', text).strip()]
print(f"Chapter list items (li > a): {len(ch_li)}")
for href, text in ch_li[:10]:
    print(f"  {href} -> {text}")
