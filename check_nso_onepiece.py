import urllib.request
import re

url = 'https://novelsonline.org/one-piece'
req = urllib.request.Request(url, headers={'User-Agent':'Mozilla/5.0'})
with urllib.request.urlopen(req, timeout=30) as resp:
    html = resp.read().decode('utf-8', errors='replace')

print('Length:', len(html))
print('Title:', re.search(r'<title>(.*?)</title>', html, re.IGNORECASE | re.DOTALL).group(1).strip()[:100] if re.search(r'<title>(.*?)</title>', html, re.IGNORECASE | re.DOTALL) else 'N/A')

# Check for chapter containers
containers = re.findall(r'<(ul|ol|div)[^>]*class=["\']([^"\']*(?:chapter|chapters|toc)[^"\']*)["\'][^>]*>', html, re.IGNORECASE)
print('Chapter containers:', len(containers))
for tag, cls in containers[:10]:
    print(' ', tag, 'class="' + cls + '"')

# All links
links = re.findall(r'href="([^"]+)"[^>]*>([^<]{1,80})</a>', html, re.IGNORECASE)
print('Total links:', len(links))

# Links that look like chapters for one-piece
op_links = [(h, t) for h, t in links if 'one-piece' in h.lower() and ('ch' in h.lower() or 'chapter' in h.lower())]
print('One Piece chapter-like links:', len(op_links))
for href, text in op_links[:20]:
    print(' ', href[:80], '|', text.strip()[:50])

with open('nso_onepiece.html', 'w', encoding='utf-8') as f:
    f.write(html)
print('Saved to nso_onepiece.html')
