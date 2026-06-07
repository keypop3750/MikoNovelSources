import re

html = open(r'c:\Users\karol\OneDrive\Documents\GitHub\MikoNovelSources\scrape_output\ranking_hot.html', encoding='utf-8').read()

# Find the first few book link containers and show their structure
for i, m in enumerate(re.finditer(r'href="(/book/[^"]+)"', html)):
    if i >= 3:
        break
    start = max(0, m.start() - 300)
    end = min(len(html), m.end() + 300)
    snippet = html[start:end]
    
    # Extract title from the anchor tag
    title_match = re.search(r'title="([^"]+)"', snippet)
    title = title_match.group(1) if title_match else 'NO TITLE'
    
    # Extract image src
    img_match = re.search(r'src="([^"]+bookcover[^"]*)"', snippet)
    cover = img_match.group(1) if img_match else 'NO COVER'
    
    # Extract nearby text that might be the book name
    name_match = re.search(r'class="[^"]*bookName[^"]*"[^>]*>([^<]+)', snippet)
    name = name_match.group(1).strip() if name_match else 'NO NAME'
    
    print(f'=== Book {i+1}: {title} ===')
    print(f'  href: {m.group(1)}')
    print(f'  name class: {name}')
    print(f'  cover: {cover[:80]}...')
    print()
