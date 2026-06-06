import urllib.request
import re

urls = [
    'https://www.scribblehub.com/series/1/life-in-a-space-sim-limbo/',
    'https://www.scribblehub.com/series/2/author-of-my-own-smut/',
    'https://www.scribblehub.com/series/10/the-wandering-inn/',
]

for url in urls:
    try:
        req = urllib.request.Request(url, headers={'User-Agent':'Mozilla/5.0'})
        with urllib.request.urlopen(req, timeout=10) as resp:
            html = resp.read().decode('utf-8', errors='replace')
            print(f'{url}: {resp.status}, {len(html)} bytes')
            sid = re.search(r'id=["\']mypostid["\'][^>]*value=["\']([^"\']+)["\']', html)
            if sid: print(f'  Story ID (id): {sid.group(1)}')
            sid2 = re.search(r'name=["\']mypostid["\'][^>]*value=["\']([^"\']+)["\']', html)
            if sid2: print(f'  Story ID (name): {sid2.group(1)}')
            sid3 = re.search(r'data-story-id=["\']([^"\']+)["\']', html)
            if sid3: print(f'  Story ID (attr): {sid3.group(1)}')
            toc = re.findall(r'class=["\'][^"\']*toc_w[^"\']*["\']', html)
            print(f'  toc_w elements: {len(toc)}')
    except Exception as e:
        print(f'{url}: ERROR - {type(e).__name__}: {e}')
    print()
