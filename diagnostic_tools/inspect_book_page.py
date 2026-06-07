import re, json

html = open(r'c:\Users\karol\OneDrive\Documents\GitHub\MikoNovelSources\scrape_output\book_page.html', encoding='utf-8').read()

print("=== Looking for JSON state ===")
scripts = re.findall(r'<script[^>]*>(.*?)</script>', html, re.DOTALL)
print(f"Total scripts: {len(scripts)}")

# Look for any state-like variables
for name in ['initialState', '__INITIAL_STATE__', '__APP_DATA__', 'window.__DATA__', 'window.__STATE__', '__NEXT_DATA__']:
    for i, s in enumerate(scripts):
        if name in s:
            print(f"Script {i}: contains '{name}', length={len(s)}")
            idx = s.find(name)
            print(f"  Context: {s[max(0,idx-30):idx+80]}")
            # Try to extract JSON
            search_start = idx + len(name)
            while search_start < len(s) and s[search_start] in ' =':
                search_start += 1
            if search_start < len(s) and s[search_start] in '{[':
                start_char = s[search_start]
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
                    json_str = s[search_start:pos]
                    print(f"  Extracted JSON: {len(json_str)} chars")
                    try:
                        data = json.loads(json_str)
                        # Save it
                        path = r'c:\Users\karol\OneDrive\Documents\GitHub\MikoNovelSources\scrape_output\book_page_state.json'
                        with open(path, 'w', encoding='utf-8') as f:
                            json.dump(data, f, indent=2, ensure_ascii=False)
                        print(f"  Saved to {path}")
                        if isinstance(data, dict):
                            print(f"  Top keys: {list(data.keys())[:15]}")
                            # Unwrap Next.js wrapper
                            if 'props' in data and 'initialState' in data.get('props', {}):
                                st = data['props']['initialState']
                                print(f"  initialState keys: {list(st.keys())[:10]}")
                                if 'entities' in st:
                                    ent = st['entities']
                                    print(f"  entities keys: {list(ent.keys())[:15]}")
                                    if 'books' in ent:
                                        books = ent['books']
                                        print(f"  books: {len(books)} items")
                                    if 'chapters' in ent:
                                        chapters = ent['chapters']
                                        print(f"  chapters: {len(chapters)} items")
                                        for ch_id, ch in list(chapters.items())[:3]:
                                            print(f"    {ch_id}: {ch.get('chapterName')} (id={ch.get('chapterId')}, locked={ch.get('isLocked')})")
                                    if 'catalog' in ent:
                                        catalog = ent['catalog']
                                        print(f"  catalog: {len(catalog)} items")
                    except json.JSONDecodeError as e:
                        print(f"  JSON error: {e}")
            break  # Only first match

print("\n=== Looking for chapter links in HTML ===")
# Find all chapter links on the book page
ch_links = re.findall(r'href="(/book/[^"]+/[^"_]+_[0-9]+)"', html)
print(f"Chapter links found: {len(ch_links)}")
for link in ch_links[:10]:
    print(f"  {link}")

# Also look for data attributes
data_chapters = re.findall(r'data-cid="([0-9]+)"', html)
print(f"data-cid attributes: {len(data_chapters)}")
for cid in set(data_chapters)[:10]:
    print(f"  {cid}")
