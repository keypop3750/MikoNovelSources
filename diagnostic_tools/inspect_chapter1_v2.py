import re, json

html = open(r'c:\Users\karol\OneDrive\Documents\GitHub\MikoNovelSources\scrape_output\chapter1.html', encoding='utf-8').read()

print("=== Looking for JSON state ===")
scripts = re.findall(r'<script[^>]*>(.*?)</script>', html, re.DOTALL)
print(f"Total scripts: {len(scripts)}")

for i, s in enumerate(scripts):
    if 'initialState' in s:
        print(f"Script {i}: contains 'initialState', length={len(s)}")
        # Show first 200 chars after initialState
        idx = s.find('initialState')
        print(f"  Around initialState: {s[max(0,idx-20):idx+80]}")
        break
else:
    print("No script contains 'initialState'")

# Try other state variable names
for name in ['__INITIAL_STATE__', '__APP_DATA__', 'window.__DATA__', 'window.__STATE__']:
    for i, s in enumerate(scripts):
        if name in s:
            print(f"Script {i}: contains '{name}'")
            break

print("\n=== Finding content by proximity to title ===")
# The title "Chapter 1: Nightmare Begins" should be near the story content
title_text = "Chapter 1: Nightmare Begins"
idx = html.find(title_text)
if idx != -1:
    print(f"Title found at position {idx}")
    # Look at surrounding HTML
    start = max(0, idx - 200)
    end = min(len(html), idx + 1000)
    snippet = html[start:end]
    print(f"Context around title:\n{snippet[:1500]}")
else:
    print("Title not found in raw HTML")

print("\n=== Finding content div patterns ===")
# Look for divs with content-related class names
for pattern in [
    r'<div[^>]*class="[^"]*content[^"]*"[^>]*>',
    r'<div[^>]*class="[^"]*text[^"]*"[^>]*>',
    r'<div[^>]*class="[^"]*chapter[^"]*"[^>]*>',
    r'<div[^>]*class="[^"]*body[^"]*"[^>]*>',
    r'<div[^>]*class="[^"]*read[^"]*"[^>]*>',
    r'<article[^>]*>',
    r'<section[^>]*>',
]:
    matches = list(re.finditer(pattern, html, re.I))
    if matches:
        print(f"Pattern '{pattern[:40]}' found {len(matches)} times")
        for m in matches[:3]:
            start = m.start()
            snippet = html[start:min(start+300, len(html))]
            print(f"  At {start}: {snippet[:200]}...")
