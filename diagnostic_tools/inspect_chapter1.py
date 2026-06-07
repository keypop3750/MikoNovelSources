import re, json

html = open(r'c:\Users\karol\OneDrive\Documents\GitHub\MikoNovelSources\scrape_output\chapter1.html', encoding='utf-8').read()

print("=== Finding story paragraphs ===")
# Find all <p> tags with substantial text
paragraphs = []
for m in re.finditer(r'<p[^>]*>(.*?)</p>', html, re.DOTALL):
    text = re.sub(r'<[^>]+>', '', m.group(1)).strip()
    if len(text) > 20:
        paragraphs.append(text)

print(f"Total substantial paragraphs: {len(paragraphs)}")
print(f"First 3 paragraphs:")
for p in paragraphs[:3]:
    print(f"  - {p[:120]}...")
print(f"Last paragraph:")
print(f"  - {paragraphs[-1][:120]}...")

# Try to find the parent container of the paragraphs
# Look for the paragraph containing the first text we found
first_text = paragraphs[0]
idx = html.find(first_text[:50])
if idx != -1:
    # Look backwards for a <div or <section
    start = idx
    while start > 0:
        if html[start:start+4] == '<div' or html[start:start+8] == '<article':
            break
        start -= 1
    snippet = html[start:min(start+500, len(html))]
    print(f"\nParent container near first paragraph:\n{snippet}")

print("\n=== Looking for JSON state in scripts ===")
scripts = re.findall(r'<script[^>]*>(.*?)</script>', html, re.DOTALL)
for i, s in enumerate(scripts):
    if 'initialState' in s or 'chapter' in s.lower() or 'content' in s.lower():
        idx = s.find('initialState')
        if idx != -1:
            print(f"Script {i}: initialState at {idx}, len={len(s)}")
            # Show context around the variable name
            print(f"  Context: {s[max(0,idx-30):idx+100]}")
            # Try to extract the JSON object
            search_start = idx + len('initialState')
            while search_start < len(s) and s[search_start] in ' =':
                search_start += 1
            if search_start < len(s) and s[search_start] == '{':
                close_char = '}'
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
                        if c == '{':
                            depth += 1
                        elif c == close_char:
                            depth -= 1
                    pos += 1
                if depth == 0:
                    json_str = s[search_start:pos]
                    print(f"  Extracted JSON length: {len(json_str)}")
                    try:
                        data = json.loads(json_str)
                        print(f"  Top keys: {list(data.keys())[:10]}")
                        # Save for inspection
                        path = r'c:\Users\karol\OneDrive\Documents\GitHub\MikoNovelSources\scrape_output\chapter1_initialState.json'
                        with open(path, 'w', encoding='utf-8') as f:
                            json.dump(data, f, indent=2, ensure_ascii=False)
                        print(f"  Saved to {path}")
                        # Look for chapter content
                        def find_content(obj, path=""):
                            if isinstance(obj, dict):
                                for k, v in obj.items():
                                    if k in ('content', 'chapterContent', 'textContent', 'body', 'chapterBody') and isinstance(v, str) and len(v) > 100:
                                        return v
                                    if isinstance(v, (dict, list)):
                                        result = find_content(v, f"{path}.{k}")
                                        if result:
                                            return result
                            elif isinstance(obj, list):
                                for i, item in enumerate(obj):
                                    result = find_content(item, f"{path}[{i}]")
                                    if result:
                                        return result
                            return None
                        content = find_content(data)
                        if content:
                            print(f"  FOUND CONTENT! Length: {len(content)}")
                            text = re.sub(r'<[^>]+>', '', content)
                            print(f"  Text preview: {text[:200]}...")
                    except json.JSONDecodeError as e:
                        print(f"  JSON parse error: {e}")
            break  # Only process first script with initialState
