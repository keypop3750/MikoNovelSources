#!/usr/bin/env python3
"""
WebNovel.com Scraper & Analyzer
Uses curl (via subprocess) to bypass Cloudflare blocks that urllib hits.
Analyzes HTML structure and extracts data for the Miko extension.
"""
import subprocess
import re
import json
import os
import sys
from urllib.parse import urlencode, quote

BASE_URL = "https://www.webnovel.com"
OUTPUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "scrape_output")
os.makedirs(OUTPUT_DIR, exist_ok=True)

CURL_HEADERS = [
    "-H", "User-Agent: Mozilla/5.0 (Linux; Android 13; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
    "-H", "Accept: text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
    "-H", "Accept-Language: en-US,en;q=0.9",
    "-H", "Connection: keep-alive",
    "-L",  # follow redirects
    "-s",  # silent
]


def curl_fetch(url, save_path=None):
    """Fetch a URL with curl and return the HTML text."""
    cmd = ["curl.exe"] + CURL_HEADERS + [url]
    try:
        result = subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=30)
        if result.returncode != 0:
            print(f"  curl error: {result.stderr[:200]}")
            return None
        html = result.stdout
        if html is None:
            return None
        if save_path:
            with open(save_path, "w", encoding="utf-8") as f:
                f.write(html)
        return html
    except Exception as e:
        print(f"  Exception: {e}")
        return None


def save_json(data, filename):
    path = os.path.join(OUTPUT_DIR, filename)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
    print(f"  Saved JSON: {path}")


def extract_scripts(html):
    """Extract all <script> tag contents."""
    return re.findall(r'<script[^>]*>(.*?)</script>', html, re.DOTALL)


def find_json_in_scripts(scripts, keyword):
    """Find a JSON object/array in scripts containing a keyword."""
    for s in scripts:
        if keyword in s:
            # Try to find the JSON structure around the keyword
            idx = s.find(keyword)
            if idx == -1:
                continue
            # Walk backwards to find opening brace or bracket
            start = idx
            while start > 0 and s[start] not in "{[":
                start -= 1
            if start < 0 or s[start] not in "{[":
                continue
            # Walk forwards to find matching close
            open_char = s[start]
            close_char = "}" if open_char == "{" else "]"
            depth = 1
            in_string = False
            escape = False
            pos = start + 1
            while pos < len(s) and depth > 0:
                c = s[pos]
                if escape:
                    escape = False
                elif c == "\\":
                    escape = True
                elif c == '"' and not in_string:
                    in_string = True
                elif c == '"' and in_string:
                    in_string = False
                elif not in_string:
                    if c == open_char:
                        depth += 1
                    elif c == close_char:
                        depth -= 1
                pos += 1
            if depth == 0:
                return s[start:pos]
    return None


def extract_books_from_homepage(html):
    """Extract book listings from the homepage HTML."""
    books = []
    # Look for book links with specific class patterns
    # Modern WebNovel uses Next.js with class names like styles_*
    patterns = [
        r'<a[^>]*href="(/book/[^"]+)"[^>]*>.*?<div[^>]*class="[^"]*styles_bookName__[^"]*"[^>]*>([^<]+)</div>',
        r'<a[^>]*href="(/book/[^"]+)"[^>]*[^<]*<div[^>]*class="[^"]*bookName[^"]*"[^>]*>([^<]+)</div>',
        r'<a[^>]*href="(/book/[^"]+)"[^>]*>([^<]{3,100})</a>',
    ]
    for pat in patterns:
        matches = re.findall(pat, html, re.DOTALL)
        for href, title in matches:
            title = re.sub(r'<[^>]+>', '', title).strip()
            if title and len(title) > 2:
                books.append({"title": title, "url": BASE_URL + href})
    return books


def analyze_homepage():
    print("\n" + "=" * 60)
    print("STEP 1: Analyzing homepage (rankings/browse)")
    print("=" * 60)
    html = curl_fetch(BASE_URL + "/", os.path.join(OUTPUT_DIR, "homepage.html"))
    if not html:
        print("  FAILED to fetch homepage")
        return

    print(f"  HTML size: {len(html)} chars")

    # Look for JSON data in scripts
    scripts = extract_scripts(html)
    print(f"  Script tags found: {len(scripts)}")

    # Check for initial state data
    state_names = ["__INITIAL_STATE__", "__DATA__", "_SSR_HYDRATED_DATA", "initialState"]
    for name in state_names:
        json_str = find_json_in_scripts(scripts, name)
        if json_str:
            print(f"  FOUND {name} in scripts (len={len(json_str)})")
            try:
                data = json.loads(json_str)
                save_json(data, f"homepage_{name}.json")
            except json.JSONDecodeError as e:
                print(f"  JSON parse error for {name}: {e}")
                # Save raw for inspection
                with open(os.path.join(OUTPUT_DIR, f"homepage_{name}_raw.txt"), "w", encoding="utf-8") as f:
                    f.write(json_str[:5000])

    # Extract books from HTML
    books = extract_books_from_homepage(html)
    print(f"  Books extracted from HTML: {len(books)}")
    if books:
        for b in books[:5]:
            print(f"    - {b['title']}: {b['url']}")
        save_json(books, "homepage_books.json")

    # Look for CSRF token
    csrf_patterns = [
        r'_csrfToken["\']?\s*[:=]\s*["\']([^"\']+)',
        r'csrfToken["\']?\s*[:=]\s*["\']([^"\']+)',
        r'window\._csrfToken\s*=\s*["\']([^"\']+)',
    ]
    csrf = None
    for pat in csrf_patterns:
        m = re.search(pat, html)
        if m:
            csrf = m.group(1)
            print(f"  CSRF Token: {csrf[:40]}...")
            break
    return csrf


def analyze_book_page(book_url, book_id):
    print(f"\n{'=' * 60}")
    print(f"STEP 2: Analyzing book page: {book_id}")
    print("=" * 60)
    html = curl_fetch(book_url, os.path.join(OUTPUT_DIR, f"book_{book_id}.html"))
    if not html:
        print("  FAILED to fetch book page")
        return None

    print(f"  HTML size: {len(html)} chars")

    scripts = extract_scripts(html)
    print(f"  Script tags found: {len(scripts)}")

    # Look for initial state with book data
    for name in ["__INITIAL_STATE__", "__DATA__", "_SSR_HYDRATED_DATA", "initialState"]:
        json_str = find_json_in_scripts(scripts, name)
        if json_str:
            print(f"  FOUND {name} in scripts (len={len(json_str)})")
            try:
                data = json.loads(json_str)
                save_json(data, f"book_{book_id}_{name}.json")
                # Try to extract book info
                extract_book_info(data, book_id)
                # Try to extract chapters
                extract_chapters_from_json(data, book_id)
                return data
            except json.JSONDecodeError as e:
                print(f"  JSON parse error: {e}")
                with open(os.path.join(OUTPUT_DIR, f"book_{book_id}_{name}_raw.txt"), "w", encoding="utf-8") as f:
                    f.write(json_str[:10000])

    # Fallback: try to extract from HTML directly
    print("  No JSON state found, trying HTML extraction...")
    extract_book_from_html(html, book_id)
    return None


def extract_book_info(data, book_id):
    """Try to find book info (title, author, description) in JSON data."""
    info = {}
    # Flatten and search
    def search(obj, path=""):
        if isinstance(obj, dict):
            for k, v in obj.items():
                new_path = f"{path}.{k}" if path else k
                if k in ("bookName", "name", "title") and isinstance(v, str) and v:
                    info["title"] = v
                if k in ("authorName", "author", "writer") and isinstance(v, str) and v:
                    info["author"] = v
                if k in ("description", "synopsis", "intro", "bookDesc") and isinstance(v, str) and v:
                    info["description"] = v[:200]
                if k in ("cover", "coverUrl", "bookCover") and isinstance(v, str) and v:
                    info["cover"] = v
                if isinstance(v, (dict, list)):
                    search(v, new_path)
        elif isinstance(obj, list):
            for i, item in enumerate(obj):
                search(item, f"{path}[{i}]")

    search(data)
    if info:
        print(f"  Book info found: {json.dumps(info, ensure_ascii=False)}")
        save_json(info, f"book_{book_id}_info.json")


def extract_chapters_from_json(data, book_id):
    """Try to find chapter arrays in JSON data."""
    chapters = []

    def search(obj, path=""):
        nonlocal chapters
        if isinstance(obj, dict):
            for k, v in obj.items():
                new_path = f"{path}.{k}" if path else k
                if k in ("chapterItems", "chapterList", "chapters", "volumeItems", "groupItems") and isinstance(v, list):
                    print(f"  Found chapter array at '{new_path}' with {len(v)} items")
                    for item in v:
                        if isinstance(item, dict):
                            cid = item.get("chapterId") or item.get("id") or item.get("cid")
                            cname = item.get("chapterName") or item.get("name") or item.get("title")
                            if cid and cname:
                                chapters.append({
                                    "id": str(cid),
                                    "name": str(cname),
                                    "index": item.get("chapterIndex", item.get("index", 0)),
                                    "isLocked": item.get("isLocked") or item.get("isVip") or item.get("isPay")
                                })
                        elif isinstance(item, dict) and ("chapterItems" in item or "chapters" in item):
                            # Nested volume/group
                            search(item, new_path)
                elif isinstance(v, (dict, list)):
                    search(v, new_path)
        elif isinstance(obj, list):
            for i, item in enumerate(obj):
                search(item, f"{path}[{i}]")

    search(data)
    if chapters:
        print(f"  Total chapters extracted: {len(chapters)}")
        for c in chapters[:3]:
            print(f"    Ch.{c['index']} {c['name']} (id={c['id']}, locked={c['isLocked']})")
        if len(chapters) > 3:
            print(f"    ... and {len(chapters)-3} more")
        save_json(chapters, f"book_{book_id}_chapters.json")
    else:
        print("  No chapters found in JSON data")
    return chapters


def extract_book_from_html(html, book_id):
    """Fallback extraction from HTML if JSON state is not available."""
    info = {}
    # Title
    m = re.search(r'<h1[^>]*>([^<]+)</h1>', html)
    if m:
        info["title"] = m.group(1).strip()
    # Author
    m = re.search(r'author[^>]*>([^<]+)</', html, re.I)
    if m:
        info["author"] = m.group(1).strip()
    # Description
    m = re.search(r'description[^>]*>([^<]{20,500})</', html, re.I)
    if m:
        info["description"] = m.group(1).strip()[:200]
    if info:
        print(f"  HTML extraction: {json.dumps(info, ensure_ascii=False)}")
        save_json(info, f"book_{book_id}_html_info.json")


def analyze_catalog_page(book_id):
    """Try various catalog URL patterns."""
    print(f"\n{'=' * 60}")
    print(f"STEP 3: Analyzing catalog pages for book {book_id}")
    print("=" * 60)

    urls_to_try = [
        f"{BASE_URL}/book/{book_id}/catalog",
        f"{BASE_URL}/book/{book_id}/catalog/",
        f"https://m.webnovel.com/book/{book_id}/catalog",
        f"https://m.webnovel.com/book/{book_id}/catalog/",
    ]
    for url in urls_to_try:
        print(f"  Trying: {url}")
        html = curl_fetch(url)
        if html and len(html) > 50000:  # Real page should be substantial
            print(f"    SUCCESS! Size: {len(html)}")
            path = os.path.join(OUTPUT_DIR, f"catalog_{book_id}.html")
            with open(path, "w", encoding="utf-8") as f:
                f.write(html)
            scripts = extract_scripts(html)
            for name in ["__INITIAL_STATE__", "__DATA__"]:
                json_str = find_json_in_scripts(scripts, name)
                if json_str:
                    try:
                        data = json.loads(json_str)
                        save_json(data, f"catalog_{book_id}_{name}.json")
                        extract_chapters_from_json(data, book_id)
                    except json.JSONDecodeError:
                        pass
            return True
        elif html:
            title_match = re.search(r'<title>([^<]+)</title>', html)
            print(f"    Got page but small ({len(html)} chars), title: {title_match.group(1) if title_match else 'unknown'}")
    print("  All catalog URLs failed or returned small pages")
    return False


def analyze_chapter_content(book_id, chapter_id):
    """Fetch a chapter page and analyze content structure."""
    print(f"\n{'=' * 60}")
    print(f"STEP 4: Analyzing chapter content: book={book_id}, chapter={chapter_id}")
    print("=" * 60)

    # Try chapter URL patterns
    urls_to_try = [
        f"{BASE_URL}/book/{book_id}/chapter/{chapter_id}",
        f"https://m.webnovel.com/book/{book_id}/chapter/{chapter_id}",
    ]
    for url in urls_to_try:
        print(f"  Trying: {url}")
        html = curl_fetch(url, os.path.join(OUTPUT_DIR, f"chapter_{book_id}_{chapter_id}.html"))
        if html and len(html) > 10000:
            print(f"    SUCCESS! Size: {len(html)}")
            # Look for content
            content_selectors = [
                r'<div[^>]*class="[^"]*cha-content[^"]*"[^>]*>(.*?)</div>',
                r'<div[^>]*class="[^"]*_content[^"]*"[^>]*>(.*?)</div>',
                r'<div[^>]*class="[^"]*chapter-content[^"]*"[^>]*>(.*?)</div>',
                r'<article[^>]*>(.*?)</article>',
            ]
            for sel in content_selectors:
                m = re.search(sel, html, re.DOTALL | re.I)
                if m:
                    content = m.group(1)
                    text = re.sub(r'<[^>]+>', '', content)
                    print(f"    Found content with selector, length: {len(text)} chars")
                    print(f"    Preview: {text[:200]}...")
                    save_json({"selector": sel, "text_length": len(text), "preview": text[:500]},
                              f"chapter_{book_id}_{chapter_id}_content.json")
                    return True

            # Look for JSON state
            scripts = extract_scripts(html)
            for name in ["__INITIAL_STATE__", "__DATA__"]:
                json_str = find_json_in_scripts(scripts, name)
                if json_str:
                    try:
                        data = json.loads(json_str)
                        save_json(data, f"chapter_{book_id}_{chapter_id}_{name}.json")
                        # Look for content in the JSON
                        def find_content(obj):
                            if isinstance(obj, dict):
                                for k, v in obj.items():
                                    if k in ("content", "chapterContent", "textContent") and isinstance(v, str) and len(v) > 200:
                                        return v
                                    if isinstance(v, (dict, list)):
                                        result = find_content(v)
                                        if result:
                                            return result
                            elif isinstance(obj, list):
                                for item in obj:
                                    result = find_content(item)
                                    if result:
                                        return result
                            return None
                        content = find_content(data)
                        if content:
                            text = re.sub(r'<[^>]+>', '', content)
                            print(f"    Found content in JSON state, length: {len(text)} chars")
                            print(f"    Preview: {text[:200]}...")
                            return True
                    except json.JSONDecodeError:
                        pass
            return True
    print("  All chapter URLs failed")
    return False


def analyze_search(query="Shadow Slave"):
    print(f"\n{'=' * 60}")
    print(f"STEP 5: Analyzing search for '{query}'")
    print("=" * 60)

    # First get CSRF token from homepage
    html = curl_fetch(BASE_URL + "/")
    csrf = None
    if html:
        m = re.search(r'_csrfToken["\']?\s*[:=]\s*["\']([^"\']+)', html)
        if m:
            csrf = m.group(1)
            print(f"  CSRF token: {csrf[:40]}...")

    # Try search API
    if csrf:
        url = f"{BASE_URL}/go/pcm/search/result?_csrfToken={csrf}&pageIndex=1&encryptType=3&_fsae=0&keywords={quote(query)}"
        print(f"  Trying API: {url[:120]}...")
        html = curl_fetch(url, os.path.join(OUTPUT_DIR, "search_api.json"))
        if html:
            print(f"    Response size: {len(html)}")
            try:
                data = json.loads(html)
                save_json(data, "search_api_response.json")
                if "data" in data and isinstance(data["data"], dict):
                    d = data["data"]
                    if "bookInfo" in d and "bookItems" in d["bookInfo"]:
                        items = d["bookInfo"]["bookItems"]
                        print(f"    Found {len(items)} search results")
                        for item in items[:3]:
                            print(f"      - {item.get('bookName')} by {item.get('authorName')} (id={item.get('bookId')})")
                        return items
            except json.JSONDecodeError:
                print("    Not valid JSON, might be HTML error page")
                with open(os.path.join(OUTPUT_DIR, "search_api_raw.html"), "w", encoding="utf-8") as f:
                    f.write(html[:5000])

    # Try HTML search
    search_url = f"{BASE_URL}/search?keywords={quote(query)}"
    print(f"  Trying HTML search: {search_url}")
    html = curl_fetch(search_url, os.path.join(OUTPUT_DIR, "search_page.html"))
    if html:
        print(f"    Page size: {len(html)}")
        # Extract results
        results = []
        for m in re.finditer(r'href="(/book/[^"]+)"[^>]*>.*?<div[^>]*class="[^"]*bookName[^"]*"[^>]*>([^<]+)</div>', html, re.DOTALL):
            results.append({"url": BASE_URL + m.group(1), "title": re.sub(r'<[^>]+>', '', m.group(2)).strip()})
        if results:
            print(f"    Found {len(results)} results in HTML")
            for r in results[:5]:
                print(f"      - {r['title']}: {r['url']}")
    return []


def main():
    print("WebNovel.com Scraper & Analyzer")
    print("Output directory:", OUTPUT_DIR)

    # Step 1: Homepage
    csrf = analyze_homepage()

    # Step 2: Book page (Shadow Slave)
    book_id = "21942472205090805"
    book_url = f"{BASE_URL}/book/shadow-slave_{book_id}"
    book_data = analyze_book_page(book_url, book_id)

    # Step 3: Catalog
    analyze_catalog_page(book_id)

    # Step 4: Chapter content
    # Try to find a chapter ID from the book data or use a known one
    analyze_chapter_content(book_id, "66868237961935044")

    # Step 5: Search
    analyze_search("Shadow Slave")

    print("\n" + "=" * 60)
    print("ANALYSIS COMPLETE")
    print(f"All output saved to: {OUTPUT_DIR}")
    print("=" * 60)


if __name__ == "__main__":
    main()
