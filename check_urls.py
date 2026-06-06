import urllib.request
import urllib.error
import re
import socket

socket.setdefaulttimeout(10)

novel = 'one-piece'
base = 'https://novelsonline.org'
patterns = [
    f'{base}/{novel}/chapters',
    f'{base}/{novel}/chapter-list',
    f'{base}/read/{novel}',
    f'{base}/novel/{novel}',
    f'{base}/{novel}/1',
]

for url in patterns:
    try:
        req = urllib.request.Request(url, headers={'User-Agent':'Mozilla/5.0'})
        with urllib.request.urlopen(req, timeout=10) as resp:
            html = resp.read().decode('utf-8', errors='replace')
            links = re.findall(r'href="([^"]+)"[^>]*>([^<]+)</a>', html, re.IGNORECASE)
            chapter_links = [(h,t) for h,t in links if 'chapter' in h.lower() or re.search(r'\d+', t)]
            print(f'{url}: {resp.status}, {len(links)} links, {len(chapter_links)} chapter-like')
            for h, t in chapter_links[:5]:
                print(f'  {h[:60]:<60} | {t.strip()[:40]}')
    except urllib.error.HTTPError as e:
        print(f'{url}: HTTP {e.code}')
    except Exception as e:
        print(f'{url}: ERROR - {type(e).__name__}: {e}')
    print()
