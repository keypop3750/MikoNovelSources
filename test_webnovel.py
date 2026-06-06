import urllib.request
import re
import json

HEADERS = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36',
    'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8',
    'Accept-Language': 'en-US,en;q=0.5',
    'Accept-Encoding': 'identity',
    'Connection': 'keep-alive',
    'Upgrade-Insecure-Requests': '1',
    'Sec-Fetch-Dest': 'document',
    'Sec-Fetch-Mode': 'navigate',
    'Sec-Fetch-Site': 'none',
    'Sec-Fetch-User': '?1',
}

def fetch(url):
    req = urllib.request.Request(url, headers=HEADERS)
    with urllib.request.urlopen(req, timeout=30) as resp:
        return resp.read().decode('utf-8', errors='replace')

print("=" * 60)
print("Webnovel.com - Shadow Slave Chapter 1")
print("=" * 60)
html = fetch("https://www.webnovel.com/book/shadow-slave_22196546206090805/nightmare-begins_59583457017254387")
print(f"Length: {len(html)}")

# Look for JSON data
m = re.search(r'window\.__INITIAL_STATE__\s*=\s*({.*?});', html, re.DOTALL)
if m:
    try:
        data = json.loads(m.group(1))
        print(f"JSON keys: {list(data.keys())[:20]}")

        # Look for chapter content
        if 'chapter' in data:
            ch = data['chapter']
            print(f"Chapter type: {type(ch)}")
            if isinstance(ch, dict):
                print(f"Chapter keys: {list(ch.keys())[:20]}")
                if 'chapterInfo' in ch:
                    info = ch['chapterInfo']
                    print(f"chapterInfo keys: {list(info.keys())[:20]}")
                    if 'content' in info:
                        content = info['content']
                        print(f"Content length: {len(content)}")
                        print(f"Content preview: {content[:300]}...")
                    if 'chapterName' in info:
                        print(f"Chapter name: {info['chapterName']}")
                    if 'paragraphReviews' in info:
                        print(f"Paragraph reviews count: {len(info['paragraphReviews'])}")
                        if info['paragraphReviews']:
                            for i, pr in enumerate(info['paragraphReviews'][:3]):
                                print(f"  Review {i}: {str(pr)[:200]}")
                    if 'chapterReview' in info:
                        print(f"Chapter review: {info['chapterReview']}")

        # Look for global comments
        if 'comment' in data:
            cmt = data['comment']
            print(f"Comment data: {str(cmt)[:500]}")
        if 'commentV2' in data:
            cmt = data['commentV2']
            print(f"CommentV2 data: {str(cmt)[:500]}")

    except json.JSONDecodeError as e:
        print(f"JSON decode error: {e}")
        raw = m.group(1)[:500]
        print(f"Raw start: {raw}")
else:
    print("No __INITIAL_STATE__ found")
    # Check for other patterns
    m2 = re.search(r'\bcontent\b.*?:\s*"([^"]{100,})"', html)
    if m2:
        print(f"Found content-like string: {m2.group(1)[:200]}...")
    with open('webnovel_chapter.html', 'w', encoding='utf-8') as f:
        f.write(html)
    print("Saved to webnovel_chapter.html")

print()
print("Done.")
