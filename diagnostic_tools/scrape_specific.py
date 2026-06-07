#!/usr/bin/env python3
"""Scrape the specific book and chapters the user provided."""
import subprocess
import re
import json
import os

OUTPUT_DIR = r'c:\Users\karol\OneDrive\Documents\GitHub\MikoNovelSources\scrape_output'
os.makedirs(OUTPUT_DIR, exist_ok=True)

CURL_HEADERS = [
    "-H", "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "-H", "Accept: text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
    "-H", "Accept-Language: en-US,en;q=0.9",
    "-H", "Connection: keep-alive",
    "-L", "-s",
]

def curl_fetch(url, save_path=None):
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

def extract_scripts(html):
    return re.findall(r'<script[^>]*>(.*?)</script>', html, re.DOTALL)

def find_json_in_scripts(scripts, var_name):
    for s in scripts:
        idx = s.find(var_name)
        if idx == -1:
            continue
        search_start = idx + len(var_name)
        while search_start < len(s) and s[search_start] in ' =':
            search_start += 1
        if search_start >= len(s):
            continue
        start_char = s[search_start]
        if start_char not in '{[':
            continue
        close_char = '}' if start_char == '{' else ']'
        depth = 1
        in_string = False
        escape = False
        pos = search_start + 1
        while pos < len(s) and depth > 0:
            c = s[pos]
            if escape:
                escape = False
            elif c == '\\':
                escape = True
            elif c == '"' and not in_string:
                in_string = True
            elif c == '"' and in_string:
                in_string = False
            elif not in_string:
                if c == start_char:
                    depth += 1
                elif c == close_char:
                    depth -= 1
            pos += 1
        if depth == 0:
            return s[search_start:pos]
    return None

def main():
    urls = [
        ("https://www.webnovel.com/book/shadow-slave_22196546206090805", "book_page.html"),
        ("https://www.webnovel.com/book/shadow-slave_22196546206090805/nightmare-begins_59583457017254387", "chapter1.html"),
        ("https://www.webnovel.com/book/shadow-slave_22196546206090805/slave-caravan_59606162361867867", "chapter2.html"),
    ]

    for url, filename in urls:
        print(f"\n{'='*60}")
        print(f"Fetching: {url}")
        print('='*60)
        path = os.path.join(OUTPUT_DIR, filename)
        html = curl_fetch(url, path)
        if not html:
            print("  FAILED")
            continue

        print(f"  Size: {len(html)} chars")
        title_match = re.search(r'<title>([^<]+)</title>', html)
        print(f"  Title: {title_match.group(1).strip() if title_match else 'NO TITLE'}")

        scripts = extract_scripts(html)
        print(f"  Scripts: {len(scripts)}")

        # Look for initialState / __INITIAL_STATE__
        for name in ['initialState', 'window.__INITIAL_STATE__', 'window.__DATA__']:
            json_str = find_json_in_scripts(scripts, name)
            if json_str:
                print(f"  FOUND {name}: {len(json_str)} chars")
                json_path = os.path.join(OUTPUT_DIR, f"{filename.replace('.html', '')}_{name.replace('.', '_')}.json")
                with open(json_path, "w", encoding="utf-8") as f:
                    f.write(json_str)
                print(f"    Saved raw JSON to {json_path}")
                try:
                    data = json.loads(json_str)
                    # If it's the Next.js wrapper, unwrap it
                    if 'props' in data and 'initialState' in data.get('props', {}):
                        st = data['props']['initialState']
                        print(f"    initialState keys: {list(st.keys())[:10]}")
                        if 'entities' in st:
                            ent = st['entities']
                            print(f"    entities keys: {list(ent.keys())[:15]}")
                            # If it's the book page, look for book data
                            if 'books' in ent:
                                books = ent['books']
                                print(f"    books count: {len(books)}")
                                for bk_id, bk in list(books.items())[:1]:
                                    print(f"    Book {bk_id} keys: {list(bk.keys())[:15]}")
                                    print(f"    Title: {bk.get('bookName')}")
                                    print(f"    Author: {bk.get('authorName')}")
                                    print(f"    Description: {str(bk.get('description', ''))[:100]}...")
                            # If it's a chapter page, look for chapter content
                            if 'chapters' in ent:
                                chapters = ent['chapters']
                                print(f"    chapters count: {len(chapters)}")
                                for ch_id, ch in list(chapters.items())[:1]:
                                    print(f"    Chapter {ch_id} keys: {list(ch.keys())[:15]}")
                                    content = ch.get('content', '')
                                    if content:
                                        print(f"    Content length: {len(content)}")
                                        print(f"    Content preview: {content[:200]}...")
                    elif isinstance(data, dict):
                        print(f"    Top keys: {list(data.keys())[:15]}")
                except json.JSONDecodeError as e:
                    print(f"    JSON parse error: {e}")
                break  # Only process first found

        # Quick HTML extraction fallback
        if 'book_page' in filename:
            print("\n  --- HTML extraction ---")
            # Try to find book name in the page
            for pattern in [
                r'<h1[^>]*>([^<]+)</h1>',
                r'class="[^"]*bookName[^"]*"[^>]*>([^<]+)',
                r'title="([^"]+)"[^>]*>.*?/book/shadow-slave',
            ]:
                m = re.search(pattern, html, re.DOTALL | re.I)
                if m:
                    print(f"    Pattern '{pattern[:40]}...' found: {m.group(1).strip()[:80]}")

        elif 'chapter' in filename:
            print("\n  --- HTML content extraction ---")
            # Look for chapter content in HTML
            for sel in ['_content', 'cha-content', 'chapter-content', 'content']:
                m = re.search(rf'<div[^>]*class="[^"]*{sel}[^"]*"[^>]*>(.*?)</div>', html, re.DOTALL | re.I)
                if m:
                    text = re.sub(r'<[^>]+>', '', m.group(1))
                    print(f"    Selector '{sel}' found: {len(text)} chars, preview: {text[:150]}...")
                    break

            # Also try <p> tag clustering
            paragraphs = re.findall(r'<p[^>]*>([^<]{30,})</p>', html)
            if paragraphs:
                print(f"    Found {len(paragraphs)} substantial <p> tags")
                for p in paragraphs[:3]:
                    print(f"      {p[:100]}...")

    print(f"\n{'='*60}")
    print("Done. Check scrape_output/ for saved files.")
    print('='*60)

if __name__ == "__main__":
    main()
