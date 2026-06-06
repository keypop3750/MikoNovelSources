import urllib.request
import re

url = 'https://novelsonline.org/brand-new-life-online-rise-of-the-goddess-of-harvest'
req = urllib.request.Request(url, headers={'User-Agent':'Mozilla/5.0'})
with urllib.request.urlopen(req, timeout=30) as resp:
    html = resp.read().decode('utf-8', errors='replace')

print('Length:', len(html))

containers = re.findall(r'<(ul|ol|div)[^>]*class=["\']([^"\']*(?:chapter|chapters|toc|list)[^"\']*)["\'][^>]*>', html, re.IGNORECASE)
print('Containers:', len(containers))
for tag, cls in containers[:10]:
    print('  <' + tag + ' class="' + cls + '">')

links = re.findall(r'href="([^"]+)"[^>]*>([^<]{1,120})</a>', html, re.IGNORECASE)
print('Total links:', len(links))

for href, text in links:
    if 'chapter' in href.lower() or re.search(r'/\d+/?$', href):
        print('  CHAPTER:', href[:70], '|', text.strip()[:50])

# Look for any AJAX or JS chapter loading
ajax = re.findall(r'ajax|json|api|fetch', html, re.IGNORECASE)
print('AJAX/JSON/API mentions:', len(ajax))

# Save for inspection
with open('novelsonline_detail.html', 'w', encoding='utf-8') as f:
    f.write(html)
print('Saved to novelsonline_detail.html')
