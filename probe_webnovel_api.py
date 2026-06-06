import urllib.request
import urllib.error
import urllib.parse
import json

HEADERS = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36',
    'Accept': 'application/json, text/plain, */*',
    'Accept-Language': 'en-US,en;q=0.9',
    'Origin': 'https://www.webnovel.com',
    'Referer': 'https://www.webnovel.com/',
}

def try_endpoint(url, data=None, method='GET'):
    try:
        req = urllib.request.Request(url, headers=HEADERS, method=method)
        if data and method == 'POST':
            req.add_header('Content-Type', 'application/x-www-form-urlencoded')
            req.data = urllib.parse.urlencode(data).encode('utf-8')
        with urllib.request.urlopen(req, timeout=15) as resp:
            body = resp.read().decode('utf-8', errors='replace')
            print(f"  {method} {url}")
            print(f"    Status: {resp.status}")
            print(f"    Content-Type: {resp.headers.get('Content-Type', 'unknown')}")
            # Try parse as JSON
            try:
                j = json.loads(body)
                print(f"    JSON keys: {list(j.keys())[:15]}")
                if 'data' in j and isinstance(j['data'], dict):
                    print(f"    data keys: {list(j['data'].keys())[:15]}")
                return j
            except json.JSONDecodeError:
                print(f"    Not JSON. Length: {len(body)}")
                print(f"    Start: {body[:300]}")
                return body
    except urllib.error.HTTPError as e:
        print(f"  {method} {url}")
        print(f"    HTTP {e.code}: {e.reason}")
        body = e.read().decode('utf-8', errors='replace')
        print(f"    Body: {body[:300]}")
        return None
    except Exception as e:
        print(f"  {method} {url}")
        print(f"    ERROR: {type(e).__name__}: {e}")
        return None

print("=" * 60)
print("Probing Webnovel API endpoints")
print("=" * 60)

book_id = "22196546206090805"
chapter_id = "59583457017254387"

endpoints = [
    # Content API attempts
    f"https://www.webnovel.com/go/pcm/chapter/GetContent?bookId={book_id}&chapterId={chapter_id}",
    f"https://www.webnovel.com/go/pcm/chapter/GetContent",
    f"https://www.webnovel.com/apiajax/chapter/GetContent",
    f"https://www.webnovel.com/api/chapter/GetContent",
    f"https://www.webnovel.com/go/pcm/chapter/GetChapterList?bookId={book_id}",
    f"https://www.webnovel.com/apiajax/chapter/GetChapterList",
    # Known webnovel API patterns from other scrapers
    f"https://www.webnovel.com/go/pcm/comment/GetComments?bookId={book_id}&chapterId={chapter_id}",
    f"https://www.webnovel.com/go/pcm/comment/GetChapterReview?bookId={book_id}&chapterId={chapter_id}",
    # Try GraphQL / newer endpoints
    f"https://www.webnovel.com/api/novel/book/chapters?bookId={book_id}",
    f"https://www.webnovel.com/api/chapter/content?bookId={book_id}&chapterId={chapter_id}",
    # Mobile API patterns
    f"https://m.webnovel.com/book/{book_id}/{chapter_id}",
]

for ep in endpoints:
    print()
    result = try_endpoint(ep)
    if result and isinstance(result, dict):
        # Print more details if it's a dict
        if 'data' in result:
            data = result['data']
            if isinstance(data, dict):
                print(f"    Sample data fields: { {k: str(v)[:100] for k, v in list(data.items())[:3]} }")

print()
print("=" * 60)
print("POST attempts")
print("=" * 60)

post_endpoints = [
    ("https://www.webnovel.com/go/pcm/chapter/GetContent", {"bookId": book_id, "chapterId": chapter_id}),
    ("https://www.webnovel.com/apiajax/chapter/GetContent", {"bookId": book_id, "chapterId": chapter_id}),
    ("https://www.webnovel.com/api/chapter/content", {"bookId": book_id, "chapterId": chapter_id}),
]

for url, data in post_endpoints:
    print()
    try_endpoint(url, data, 'POST')

print("\nDone.")
