import re

html = open(r'c:\Users\karol\OneDrive\Documents\GitHub\MikoNovelSources\scrape_output\ranking_hot.html', encoding='utf-8').read()

# Find a few book links and show context around them
for i, m in enumerate(re.finditer(r'href="(/book/[^"]+)"', html)):
    if i >= 3:
        break
    start = max(0, m.start() - 200)
    end = min(len(html), m.end() + 400)
    snippet = html[start:end]
    print(f'=== Book link {i+1}: {m.group(1)} ===')
    print(snippet)
    print()
