import urllib.request
import re

url = 'https://novelsonline.org/one-piece'
req = urllib.request.Request(url, headers={'User-Agent':'Mozilla/5.0'})
with urllib.request.urlopen(req, timeout=30) as resp:
    html = resp.read().decode('utf-8', errors='replace')

# Find all links
links = re.findall(r'href="([^"]+)"[^>]*>([^<]+)</a>', html, re.IGNORECASE)
print(f'Total links: {len(links)}')
for href, text in links[:30]:
    print(f'  {href:<80} | {text.strip()[:60]}')

# Look for chapter containers
print('\n--- Looking for chapter containers ---')
containers = re.findall(r'<(ul|ol|div)[^>]*class=["\']([^"\']*(?:chapter|chapters|toc)[^"\']*)["\'][^>]*>', html, re.IGNORECASE)
for tag, cls in containers:
    print(f'  <{tag} class="{cls}">')

# Look for chapter-like links
chapter_links = [(h,t) for h,t in links if 'chapter' in h.lower() or re.search(r'/\d+/?$', h)]
print(f'\nChapter-like links: {len(chapter_links)}')
for href, text in chapter_links[:10]:
    print(f'  {href:<80} | {text.strip()[:60]}')
