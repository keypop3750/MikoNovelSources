import urllib.request
import re

HEADERS = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
    'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
}

def fetch(url):
    try:
        req = urllib.request.Request(url, headers=HEADERS)
        with urllib.request.urlopen(req, timeout=30) as resp:
            return resp.read().decode('utf-8', errors='replace')
    except Exception as e:
        return f"ERROR: {e}"

# Test LibRead URL equivalents
urls = [
    ("LibRead novel page", "https://libread.com/novel/beautiful-ceos-super-expert.html"),
    ("LibRead chapter", "https://libread.com/novel/beautiful-ceos-super-expert/chapter-1983"),
    ("FreeWebNovel chapter", "https://freewebnovel.com/novel/beautiful-ceos-super-expert/chapter-1983"),
]

for name, url in urls:
    print(f"\n=== {name} ===")
    print(f"URL: {url}")
    html = fetch(url)
    if html.startswith("ERROR"):
        print(f"  FAIL: {html}")
        continue
    print(f"  Length: {len(html)}")
    title = re.search(r'<title>(.*?)</title>', html, re.IGNORECASE | re.DOTALL)
    if title:
        print(f"  Title: {title.group(1).strip()[:100]}")

    # Check for div.txt
    m = re.search(r'<div[^>]*class="txt"[^>]*>(.*?)</div>\s*</div>', html, re.IGNORECASE | re.DOTALL)
    if m:
        text = re.sub(r'<[^>]+>', ' ', m.group(1)).strip()
        print(f"  div.txt FOUND, text={text[:300]}...")
    else:
        # Try without extra closing div
        m2 = re.search(r'<div[^>]*class="txt"[^>]*>(.*?)</div>', html, re.IGNORECASE | re.DOTALL)
        if m2:
            text = re.sub(r'<[^>]+>', ' ', m2.group(1)).strip()
            print(f"  div.txt FOUND (alt), text={text[:300]}...")
        else:
            print(f"  div.txt NOT FOUND")

    # Check for option elements (chapter list)
    options = re.findall(r'<option[^>]*value="([^"]+)"[^>]*>([^<]+)</option>', html, re.IGNORECASE)
    if options:
        print(f"  Options found: {len(options)}")
        for val, text in options[:5]:
            print(f"    {val[:60]:<60} | {text.strip()[:40]}")

print("\nDone.")
