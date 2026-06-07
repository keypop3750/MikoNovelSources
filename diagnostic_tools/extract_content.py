import re

html = open(r'c:\Users\karol\OneDrive\Documents\GitHub\MikoNovelSources\scrape_output\chapter1.html', encoding='utf-8').read()

# Find the _content div
m = re.search(r'<div[^>]*class="[^"]*_content[^"]*"[^>]*>(.*?)</div>', html, re.DOTALL | re.I)
if m:
    content_html = m.group(1)
    print(f"Content div length: {len(content_html)} chars")
    text = re.sub(r'<[^>]+>', '', content_html)
    print(f"Text length: {len(text)} chars")
    print(f"Preview:\n{text[:800]}...")
else:
    print("NO _content div found")
