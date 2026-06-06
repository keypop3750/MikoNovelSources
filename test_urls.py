import urllib.request
import urllib.error
import re

HEADERS = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
    'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
    'Accept-Language': 'en-US,en;q=0.5',
}

def fetch(url):
    try:
        req = urllib.request.Request(url, headers=HEADERS)
        with urllib.request.urlopen(req, timeout=30) as resp:
            return resp.read().decode('utf-8', errors='replace')
    except Exception as e:
        return f"ERROR: {e}"

# Test 1: FreeWebNovel/LibRead content
print("=" * 60)
print("Test 1: FreeWebNovel chapter content")
print("=" * 60)
url1 = "https://freewebnovel.com/novel/beautiful-ceos-super-expert/chapter-1983"
html1 = fetch(url1)
if html1.startswith("ERROR"):
    print(f"  FAIL: {html1}")
else:
    print(f"  Status: OK, length={len(html1)}")
    title = re.search(r'<h1[^>]*class=["\']chapter-title["\'][^>]*>(.*?)</h1>', html1, re.IGNORECASE | re.DOTALL)
    if title:
        print(f"  Title: {re.sub(r'<[^>]+>', '', title.group(1)).strip()[:100]}")
    else:
        title2 = re.search(r'<title>(.*?)</title>', html1, re.IGNORECASE | re.DOTALL)
        if title2:
            print(f"  Title: {title2.group(1).strip()[:100]}")
    # Check for content container
    content = re.search(r'<div[^>]*id=["\']chapter-content["\'][^>]*>(.*?)</div>', html1, re.IGNORECASE | re.DOTALL)
    if content:
        text = re.sub(r'<[^>]+>', ' ', content.group(1)).strip()
        print(f"  Content preview: {text[:200]}...")
    else:
        # Try other content selectors
        content2 = re.search(r'<div[^>]*class=["\'][^"\']*content[^"\']*["\'][^>]*>(.*?)</div>', html1, re.IGNORECASE | re.DOTALL)
        if content2:
            text = re.sub(r'<[^>]+>', ' ', content2.group(1)).strip()
            print(f"  Content (alt): {text[:200]}...")
        else:
            print("  Content: not found with basic selectors")

# Test 2: Webnovel.com chapter
print()
print("=" * 60)
print("Test 2: Webnovel.com chapter content")
print("=" * 60)
url2 = "https://www.webnovel.com/book/shadow-slave_22196546206090805/nightmare-begins_59583457017254387"
html2 = fetch(url2)
if html2.startswith("ERROR"):
    print(f"  FAIL: {html2}")
else:
    print(f"  Status: OK, length={len(html2)}")
    title = re.search(r'<title>(.*?)</title>', html2, re.IGNORECASE | re.DOTALL)
    if title:
        print(f"  Title: {title.group(1).strip()[:100]}")
    # Check for chapter content
    content = re.search(r'<div[^>]*class=["\'][^"\']*chapter[^"\']*["\'][^>]*>(.*?)</div>', html2, re.IGNORECASE | re.DOTALL)
    if content:
        text = re.sub(r'<[^>]+>', ' ', content.group(1)).strip()
        print(f"  Content preview: {text[:200]}...")
    else:
        print("  Content: not found with basic selectors, checking for JSON data...")
        # Webnovel often loads content via JSON/JS
        json_match = re.search(r'window\.__INITIAL_STATE__\s*=\s*({.*?});', html2, re.DOTALL)
        if json_match:
            print(f"  Found __INITIAL_STATE__ JSON, length={len(json_match.group(1))}")
        else:
            print("  No __INITIAL_STATE__ found either")
    # Check for comments
    comments = re.search(r'comment|Comment|COMMENT', html2)
    if comments:
        print("  Comments keyword found in HTML")
    else:
        print("  Comments keyword NOT found")

# Test 3: LibRead (using the same URL pattern as FreeWebNovel)
print()
print("=" * 60)
print("Test 3: LibRead (test URL)")
print("=" * 60)
url3 = "https://libread.com/novel/beautiful-ceos-super-expert.html"
html3 = fetch(url3)
if html3.startswith("ERROR"):
    print(f"  FAIL: {html3}")
else:
    print(f"  Status: OK, length={len(html3)}")
    title = re.search(r'<title>(.*?)</title>', html3, re.IGNORECASE | re.DOTALL)
    if title:
        print(f"  Title: {title.group(1).strip()[:100]}")
    # Check for chapter links
    links = re.findall(r'href="([^"]*chapter[^"]*)"', html3, re.IGNORECASE)
    print(f"  Chapter links: {len(links)}")
    for l in links[:5]:
        print(f"    {l[:80]}")

print()
print("Done.")
