import subprocess, json, re

BASE_URL = "https://www.webnovel.com"
BOOK_ID = "22196546206090805"

CURL_HEADERS = [
    "-H", "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "-H", "Accept: text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
    "-H", "Accept-Language: en-US,en;q=0.9",
    "-H", "Connection: keep-alive",
    "-L", "-s",
]

def curl_fetch(url):
    cmd = ["curl.exe"] + CURL_HEADERS + [url]
    result = subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=30)
    return result.stdout if result.returncode == 0 else None

# 1. Get CSRF token
print("Fetching homepage for CSRF token...")
home = curl_fetch(BASE_URL + "/")
if not home:
    print("Failed to fetch homepage")
    exit(1)

m = re.search(r'_csrfToken["\']?\s*[:=]\s*["\']([^"\']+)', home)
if not m:
    print("No CSRF token found")
    exit(1)
csrf = m.group(1)
print(f"CSRF token: {csrf[:40]}...")

# 2. Test catalog API
endpoints = [
    f"{BASE_URL}/go/pcm/chapter/getCatalog?_csrfToken={csrf}&bookId={BOOK_ID}&encryptType=3&_fsae=0",
    f"{BASE_URL}/go/pcm/chapter/getChapterList?_csrfToken={csrf}&bookId={BOOK_ID}&encryptType=3&_fsae=0",
    f"{BASE_URL}/go/pcm/book/getBookInfo?_csrfToken={csrf}&bookId={BOOK_ID}&encryptType=3&_fsae=0&chapterId=0",
]

for url in endpoints:
    print(f"\n{'='*60}")
    print(f"Testing: {url[:80]}...")
    print('='*60)
    resp = curl_fetch(url)
    if not resp:
        print("  No response")
        continue
    print(f"  Response size: {len(resp)} chars")
    print(f"  First 500 chars: {resp[:500]}")
    try:
        data = json.loads(resp)
        print(f"  JSON top keys: {list(data.keys())}")
        if 'data' in data:
            d = data['data']
            print(f"  data keys: {list(d.keys())[:15]}")
            # Look for chapter arrays
            for k in d.keys():
                v = d[k]
                if isinstance(v, list) and len(v) > 0:
                    print(f"    {k}: list with {len(v)} items")
                    if isinstance(v[0], dict):
                        print(f"      First item keys: {list(v[0].keys())[:10]}")
                        print(f"      First item: {v[0].get('chapterName', v[0].get('name', 'unknown'))}")
                elif isinstance(v, dict):
                    for sk, sv in v.items():
                        if isinstance(sv, list) and len(sv) > 0:
                            print(f"    {k}.{sk}: list with {len(sv)} items")
                            if isinstance(sv[0], dict):
                                print(f"      First: {sv[0].get('chapterName', sv[0].get('name', 'unknown'))}")
    except json.JSONDecodeError:
        print("  Not valid JSON")
