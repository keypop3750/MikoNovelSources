import json, re, os

OUTPUT_DIR = r'c:\Users\karol\OneDrive\Documents\GitHub\MikoNovelSources\scrape_output'

def extract_json_from_script(html, var_name):
    scripts = re.findall(r'<script[^>]*>(.*?)</script>', html, re.DOTALL)
    for s in scripts:
        idx = s.find(var_name)
        if idx == -1:
            continue
        # Find opening brace or bracket after the variable assignment
        search_start = idx + len(var_name)
        # Skip past = sign and whitespace
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

for fname in ['search_page.html']:
    path = os.path.join(OUTPUT_DIR, fname)
    if not os.path.exists(path):
        print(f"Missing: {path}")
        continue
    html = open(path, encoding='utf-8').read()
    print(f"\n=== {fname} ({len(html)} chars) ===")
    for var_name in ['window.__INITIAL_STATE__', 'window.__DATA__', 'window._SSR_HYDRATED_DATA', 'initialState']:
        json_str = extract_json_from_script(html, var_name)
        if json_str:
            print(f"Found {var_name}: {len(json_str)} chars")
            try:
                data = json.loads(json_str)
                out_path = os.path.join(OUTPUT_DIR, f"{fname.replace('.html', '')}_{var_name.replace('.', '_')}.json")
                with open(out_path, 'w', encoding='utf-8') as f:
                    json.dump(data, f, indent=2, ensure_ascii=False)
                print(f"  Saved to {out_path}")
                if isinstance(data, dict):
                    print(f"  Top keys: {list(data.keys())[:10]}")
                    if 'initialState' in data:
                        st = data['initialState']
                        print(f"  initialState keys: {list(st.keys())[:10]}")
                        if 'entities' in st:
                            ent = st['entities']
                            print(f"  entities keys: {list(ent.keys())[:10]}")
                            for k in ent.keys():
                                v = ent[k]
                                if isinstance(v, dict):
                                    print(f"    {k}: dict with {len(v)} items")
                                    for subk in list(v.keys())[:3]:
                                        print(f"      {subk}: {type(v[subk]).__name__}")
                                elif isinstance(v, list):
                                    print(f"    {k}: list with {len(v)} items")
                                else:
                                    print(f"    {k}: {type(v).__name__}")
            except json.JSONDecodeError as e:
                print(f"  JSON parse error: {e}")
                raw_path = os.path.join(OUTPUT_DIR, f"{fname.replace('.html', '')}_{var_name.replace('.', '_')}_raw.txt")
                with open(raw_path, 'w', encoding='utf-8') as f:
                    f.write(json_str[:5000])
                print(f"  Saved raw to {raw_path}")
            break
