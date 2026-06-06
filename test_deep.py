import urllib.request
import re
import json

HEADERS = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
    'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
}

def fetch(url):
    req = urllib.request.Request(url, headers=HEADERS)
    with urllib.request.urlopen(req, timeout=30) as resp:
        return resp.read().decode('utf-8', errors='replace')

# === FreeWebNovel deep check ===
print("=" * 60)
print("FreeWebNovel - Beautiful CEO chapter 1983")
print("=" * 60)
html = fetch("https://freewebnovel.com/novel/beautiful-ceos-super-expert/chapter-1983")

# Try various content selectors
selectors = [
    r'<div[^>]*id=["\']chr-content["\'][^>]*>(.*?)</div>\s*</div>\s*<div[^>]*class=["\']cha-foot',
    r'<div[^>]*id=["\']chr-content["\'][^>]*>(.*?)</div>',
    r'<div[^>]*class=["\']cha-content["\'][^>]*>(.*?)</div>',
    r'<div[^>]*class=["\']cha-words[^"\']*["\'][^>]*>(.*?)</div>',
    r'<div[^>]*class=["\'][^"\']*content[^"\']*["\'][^>]*>(.*?)</div>',
]
found = False
for i, sel in enumerate(selectors):
    m = re.search(sel, html, re.IGNORECASE | re.DOTALL)
    if m:
        text = re.sub(r'<[^>]+>', ' ', m.group(1)).strip()
        print(f"  Selector {i}: FOUND, text={text[:200]}...")
        found = True
        break
if not found:
    print("  No content selector matched. Saving HTML for inspection.")
    with open('fwn_chapter.html', 'w', encoding='utf-8') as f:
        f.write(html)

# === Webnovel deep check ===
print()
print("=" * 60)
print("Webnovel.com - Shadow Slave Chapter 1")
print("=" * 60)
html2 = fetch("https://www.webnovel.com/book/shadow-slave_22196546206090805/nightmare-begins_59583457017254387")

# Look for JSON data
m = re.search(r'window\.__INITIAL_STATE__\s*=\s*({.*?});', html2, re.DOTALL)
if m:
    try:
        data = json.loads(m.group(1))
        print(f"  JSON keys: {list(data.keys())[:20]}")
        
        # Look for chapter content
        if 'chapter' in data:
            ch = data['chapter']
            print(f"  Chapter keys: {list(ch.keys())[:20] if isinstance(ch, dict) else type(ch)}")
            if isinstance(ch, dict) and 'chapterInfo' in ch:
                info = ch['chapterInfo']
                print(f"  chapterInfo keys: {list(info.keys())[:20]}")
                if 'content' in info:
                    content = info['content']
                    print(f"  Content length: {len(content)}")
                    print(f"  Content preview: {content[:300]}...")
                if 'chapterName' in info:
                    print(f"  Chapter name: {info['chapterName']}")
        
        # Look for comments
        if 'comment' in data:
            print(f"  Comment data found!")
        if 'chapter' in data and isinstance(data['chapter'], dict):
            ch = data['chapter']
            if 'chapterInfo' in ch and isinstance(ch['chapterInfo'], dict):
                info = ch['chapterInfo']
                if 'paragraphReviews' in info:
                    print(f"  Paragraph reviews: {len(info['paragraphReviews'])}")
                if 'chapterReview' in info:
                    print(f"  Chapter reviews: {len(info['chapterReview'])}")
                if 'comment' in info:
                    print(f"  Comments in chapterInfo: {len(info['comment'])}")
    except json.JSONDecodeError as e:
        print(f"  JSON decode error: {e}")
        # Try to find the raw JSON start
        raw = m.group(1)[:500]
        print(f"  Raw start: {raw}")
else:
    print("  No __INITIAL_STATE__ found")
    # Save for inspection
    with open('webnovel_chapter.html', 'w', encoding='utf-8') as f:
        f.write(html2)
    print("  Saved to webnovel_chapter.html")

print()
print("Done.")
