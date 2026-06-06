import urllib.request
import re

def fetch(url):
    req = urllib.request.Request(url, headers={'User-Agent':'Mozilla/5.0'})
    with urllib.request.urlopen(req, timeout=30) as resp:
        return resp.read().decode('utf-8', errors='replace')

# NovelsOnline novel page
html = fetch('https://novelsonline.org/brand-new-life-online-rise-of-the-goddess-of-harvest')
print('=== NovelsOnline novel page ===')
print('Length:', len(html))

# Find links containing the novel slug
slug = 'brand-new-life-online-rise-of-the-goddess-of-harvest'
links = re.findall(r'href="([^"]+)"[^>]*>([^<]{1,80})</a>', html, re.IGNORECASE)
novel_links = [(h, t) for h, t in links if slug in h.lower()]
print('Links with novel slug:', len(novel_links))
for href, text in novel_links[:20]:
    print(' ', href[:80], '|', text.strip()[:60])

# Find chapter list containers
containers = re.findall(r'<(ul|ol|div)[^>]*class=["\']([^"\']*(?:chapter|chapters|list|toc)[^"\']*)["\'][^>]*>', html, re.IGNORECASE)
print('Containers:', len(containers))
for tag, cls in containers[:10]:
    print(' ', tag, 'class="' + cls + '"')

# ScribbleHub series page
print('\n=== ScribbleHub series page ===')
html2 = fetch('https://www.scribblehub.com/series/2351740/i-reincarnated-as-the-villainess-and-i-refuse-to-follow-the-plot/')
print('Length:', len(html2))

# Find story ID
sid = re.search(r'id=["\']mypostid["\'][^>]*value=["\']([^"\']+)["\']', html2)
if sid: print('Story ID:', sid.group(1))

# Find chapter links
links2 = re.findall(r'href="([^"]+)"[^>]*>([^<]{1,80})</a>', html2, re.IGNORECASE)
ch_links = [(h, t) for h, t in links2 if 'chapter' in h.lower() or 'read/2351740' in h.lower()]
print('Chapter links:', len(ch_links))
for href, text in ch_links[:10]:
    print(' ', href[:80], '|', text.strip()[:60])

# Find toc container
m = re.search(r'<ol class="toc_ol">(.*?)</ol>', html2, re.IGNORECASE | re.DOTALL)
if m:
    print('Found ol.toc_ol')
    toc_links = re.findall(r'href="([^"]+)"[^>]*>([^<]{1,80})</a>', m.group(1), re.IGNORECASE)
    print('  Links inside toc_ol:', len(toc_links))
    for href, text in toc_links[:5]:
        print('   ', href[:80], '|', text.strip()[:60])

print('\nDone.')
