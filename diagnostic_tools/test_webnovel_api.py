#!/usr/bin/env python3
"""WebNovel API Diagnostic Script - uses urllib (no external deps)"""
import urllib.request
import urllib.parse
import http.cookiejar
import json
import re
import ssl

BASE_URL = "https://www.webnovel.com"
CTX = ssl.create_default_context()
CTX.check_hostname = False
CTX.verify_mode = ssl.CERT_NONE

# Shared cookie jar so session cookies persist across requests
COOKIE_JAR = http.cookiejar.CookieJar()
OPENER = urllib.request.build_opener(
    urllib.request.HTTPCookieProcessor(COOKIE_JAR),
    urllib.request.HTTPSHandler(context=CTX)
)

def fetch(url, params=None):
    if params:
        url = url + "?" + urllib.parse.urlencode(params)
    req = urllib.request.Request(url, headers={
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8",
        "Accept-Language": "en-US,en;q=0.9",
        "Accept-Encoding": "gzip, deflate, br",
        "Connection": "keep-alive",
        "Upgrade-Insecure-Requests": "1",
        "Sec-Fetch-Dest": "document",
        "Sec-Fetch-Mode": "navigate",
        "Sec-Fetch-Site": "none",
        "Cache-Control": "max-age=0",
    })
    with OPENER.open(req, timeout=15) as resp:
        return resp.read().decode("utf-8", errors="replace")

def get_csrf_token():
    print("=" * 60)
    print("STEP 1: Fetching homepage for CSRF token...")
    try:
        body = fetch(BASE_URL + "/")
    except urllib.error.HTTPError as e:
        print(f"  HTTP Error {e.code}: {e.reason}")
        print("  NOTE: WebNovel may block automated requests with Cloudflare.")
        print("  Run this script from an environment with Cloudflare bypass if needed.")
        return ""
    patterns = [
        r"_csrfToken['\"]?\s*[:=]\s*['\"]([^'\"]+)",
        r"csrfToken['\"]?\s*[:=]\s*['\"]([^'\"]+)",
        r"window\._csrfToken\s*=\s*['\"]([^'\"]+)",
    ]
    for pat in patterns:
        m = re.search(pat, body)
        if m:
            print(f"  CSRF Token found: {m.group(1)[:30]}...")
            return m.group(1)
    print("  ERROR: No CSRF token found!")
    return ""

def test_rankings(token, rank_type="pop"):
    print(f"\nSTEP 2: Testing {rank_type} rankings API...")
    try:
        body = fetch(BASE_URL + "/go/pcm/rank/getRank", {
            "_csrfToken": token, "categoryType": "0", "rankType": rank_type,
            "periodType": "4", "pageIndex": "1", "pageSize": "10",
            "encryptType": "3", "_fsae": "0",
        })
        data = json.loads(body)
        print(f"  Response keys: {list(data.keys())}")
        if "data" in data and isinstance(data["data"], dict):
            d = data["data"]
            print(f"  data keys: {list(d.keys())}")
            if "items" in d:
                print(f"  items count: {len(d['items'])}")
                if d["items"]:
                    print(f"  First item keys: {list(d['items'][0].keys())}")
        return data
    except Exception as e:
        print(f"  Error: {e}")
        return None

def test_search(token, query="Shadow Slave"):
    print(f"\nSTEP 3: Testing search API for '{query}'...")
    try:
        body = fetch(BASE_URL + "/go/pcm/search/result", {
            "_csrfToken": token, "pageIndex": "1", "encryptType": "3",
            "_fsae": "0", "keywords": query,
        })
        data = json.loads(body)
        print(f"  Response keys: {list(data.keys())}")
        if "data" in data and isinstance(data["data"], dict):
            d = data["data"]
            print(f"  data keys: {list(d.keys())}")
            if "bookInfo" in d:
                bi = d["bookInfo"]
                print(f"  bookInfo keys: {list(bi.keys())}")
                if "bookItems" in bi:
                    print(f"  bookItems count: {len(bi['bookItems'])}")
                    if bi["bookItems"]:
                        print(f"  First item keys: {list(bi['bookItems'][0].keys())}")
                        return bi["bookItems"][0].get("bookId", "")
        return ""
    except Exception as e:
        print(f"  Error: {e}")
        return ""

def test_book_info(token, book_id):
    print(f"\nSTEP 4: Testing book info API for bookId={book_id}...")
    try:
        body = fetch(BASE_URL + "/go/pcm/chapter/getContent", {
            "_csrfToken": token, "bookId": book_id, "chapterId": "0",
            "encryptType": "3", "_fsae": "0",
        })
        data = json.loads(body)
        print(f"  Response keys: {list(data.keys())}")
        if "data" in data and isinstance(data["data"], dict):
            d = data["data"]
            print(f"  data keys: {list(d.keys())}")
            if "bookInfo" in d:
                bi = d["bookInfo"]
                print(f"  bookInfo keys: {list(bi.keys())}")
                ck = [k for k in bi.keys() if "chapter" in k.lower() or "volume" in k.lower() or "catalog" in k.lower()]
                print(f"  Chapter-related keys in bookInfo: {ck}")
                for k in ck:
                    v = bi[k]
                    print(f"    {k}: {type(v).__name__} (len={len(v) if isinstance(v, (list, str, dict)) else 'n/a'})")
            ck2 = [k for k in d.keys() if "chapter" in k.lower() or "volume" in k.lower() or "catalog" in k.lower()]
            print(f"  Chapter-related keys in data: {ck2}")
        return data
    except Exception as e:
        print(f"  Error: {e}")
        return None

def test_chapter_list_apis(token, book_id):
    print(f"\nSTEP 5: Testing chapter list APIs...")
    endpoints = [
        "/go/pcm/chapter/get-chapter-list",
        "/go/pcm/chapter/getCatalog",
        "/go/pcm/chapter/getChapterList",
    ]
    for ep in endpoints:
        try:
            body = fetch(BASE_URL + ep, {
                "_csrfToken": token, "bookId": book_id, "encryptType": "3", "_fsae": "0",
            })
            data = json.loads(body)
            print(f"  {ep}: keys={list(data.keys())}")
            if "data" in data and isinstance(data["data"], dict):
                print(f"    data keys: {list(data['data'].keys())}")
        except Exception as e:
            print(f"  {ep}: Error - {e}")

def test_catalog_html(book_id):
    print(f"\nSTEP 6: Testing catalog HTML for bookId={book_id}...")
    try:
        body = fetch(BASE_URL + f"/book/{book_id}/catalog")
        print(f"  Content length: {len(body)}")
        scripts = re.findall(r'<script[^>]*>(.*?)</script>', body, re.DOTALL)
        print(f"  Script tags: {len(scripts)}")
        for i, s in enumerate(scripts[:10]):
            if "chapter" in s.lower() or "catalog" in s.lower() or "volume" in s.lower():
                print(f"  Script {i} has chapter/catalog keywords")
                m = re.search(r'chapterItems\s*:\s*(\[.*?\])', s, re.DOTALL)
                if m:
                    print(f"    chapterItems JSON snippet: {m.group(1)[:300]}")
        for sel in ["j_catalog_list", "volume-item", "data-report-cid", "data-cid", "chapter-item", "cha-content"]:
            print(f"  '{sel}' in HTML: {'YES' if sel in body else 'no'}")
    except Exception as e:
        print(f"  Error: {e}")

def main():
    print("WebNovel API Diagnostic Tool")
    token = get_csrf_token()
    if not token:
        print("FAILED: No CSRF token"); return

    test_rankings(token, "pop")
    test_rankings(token, "new")
    book_id = test_search(token, "Shadow Slave")
    if not book_id:
        book_id = "19357143205859905"
    print(f"\nUsing bookId: {book_id}")

    test_book_info(token, book_id)
    test_chapter_list_apis(token, book_id)
    test_catalog_html(book_id)
    print("\nDIAGNOSTIC COMPLETE")

if __name__ == "__main__":
    main()
