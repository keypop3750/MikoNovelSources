import json, re, os

OUTPUT_DIR = r'c:\Users\karol\OneDrive\Documents\GitHub\MikoNovelSources\scrape_output'

def extract_json_from_script(html, var_name):
    scripts = re.findall(r'<script[^>]*>(.*?)</script>', html, re.DOTALL)
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

for fname in ['ranking_hot.html', 'ranking_latest.html']:
    path = os.path.join(OUTPUT_DIR, fname)
    html = open(path, encoding='utf-8').read()
    print(f"\n=== {fname} ({len(html)} chars) ===")
    
    # Check title
    m = re.search(r'<title>([^<]+)</title>', html)
    print('Title:', m.group(1).strip() if m else 'NO TITLE')
    
    # Extract initialState
    for var_name in ['initialState', 'window.__INITIAL_STATE__', 'window.__DATA__']:
        json_str = extract_json_from_script(html, var_name)
        if json_str:
            print(f'Found {var_name}: {len(json_str)} chars')
            try:
                data = json.loads(json_str)
                out_path = os.path.join(OUTPUT_DIR, f"{fname.replace('.html', '')}_{var_name.replace('.', '_')}.json")
                with open(out_path, 'w', encoding='utf-8') as f:
                    json.dump(data, f, indent=2, ensure_ascii=False)
                print(f'  Saved to {out_path}')
                
                # Navigate to find books
                if 'props' in data and 'initialState' in data['props']:
                    st = data['props']['initialState']
                    if 'entities' in st:
                        ent = st['entities']
                        print(f'  entities keys: {list(ent.keys())[:15]}')
                        
                        # Look for ranking/list data
                        for k in ent.keys():
                            if 'rank' in k.lower() or 'list' in k.lower() or 'book' in k.lower():
                                v = ent[k]
                                if isinstance(v, dict):
                                    print(f'    {k}: dict with {len(v)} items')
                                    for sk in list(v.keys())[:3]:
                                        sv = v[sk]
                                        if isinstance(sv, dict):
                                            print(f'      {sk}: dict keys={list(sv.keys())[:10]}')
                                        elif isinstance(sv, list):
                                            print(f'      {sk}: list len={len(sv)}')
                                elif isinstance(v, list):
                                    print(f'    {k}: list with {len(v)} items')
                                    if v and isinstance(v[0], dict):
                                        print(f'      First item keys: {list(v[0].keys())[:10]}')
                                        for item in v[:2]:
                                            print(f'        - {item.get("bookName", item.get("name", "unknown"))}')
            except json.JSONDecodeError as e:
                print(f'  JSON decode error: {e}')
            break
    
    # Also try simple book extraction from HTML
    books = []
    for m in re.finditer(r'href="(/book/[^"]+)"', html):
        href = m.group(1)
        books.append(href)
    print(f'  Book links found: {len(books)}')
    for b in books[:5]:
        print(f'    {b}')
