import re

html = open(r'c:\Users\karol\OneDrive\Documents\GitHub\MikoNovelSources\scrape_output\chapter1.html', encoding='utf-8').read()

def extract_div_content(html, class_name):
    """Extract the inner HTML of a div with the given class by counting tag depth."""
    start_tag = f'<div class="{class_name}'
    idx = html.find(start_tag)
    if idx == -1:
        return None
    # Find the closing > of the opening div tag
    tag_end = html.find('>', idx)
    if tag_end == -1:
        return None
    content_start = tag_end + 1
    # Count div depth to find matching closing tag
    depth = 1
    pos = content_start
    in_string = False
    while pos < len(html) and depth > 0:
        c = html[pos]
        if c == '"':
            in_string = not in_string
        elif not in_string:
            if html[pos:pos+4] == '<div':
                depth += 1
            elif html[pos:pos+6] == '</div>':
                depth -= 1
                if depth == 0:
                    return html[content_start:pos]
        pos += 1
    return None

# Strategy 1: Extract from cha-words div
words_html = extract_div_content(html, "cha-words")
if words_html:
    print(f"cha-words HTML length: {len(words_html)}")
    # Extract paragraphs
    paras = re.findall(r'<p[^>]*>(.*?)</p>', words_html, re.DOTALL)
    text = '\n\n'.join(re.sub(r'<[^>]+>', '', p).strip() for p in paras if len(re.sub(r'<[^>]+>', '', p).strip()) > 10)
    print(f"Extracted text length: {len(text)}")
    print(f"Preview:\n{text[:1000]}")
else:
    print("cha-words not found")

# Strategy 2: Extract all cha-paragraph elements from the whole document
print("\n=== Strategy 2: cha-paragraph elements ===")
paras = re.findall(r'<p class="[^"]*cha-paragraph[^"]*"[^>]*>(.*?)</p>', html, re.DOTALL)
if paras:
    text2 = '\n\n'.join(re.sub(r'<[^>]+>', '', p).strip() for p in paras if len(re.sub(r'<[^>]+>', '', p).strip()) > 5)
    print(f"cha-paragraph count: {len(paras)}")
    print(f"Text length: {len(text2)}")
    print(f"Preview:\n{text2[:1000]}")
else:
    print("No cha-paragraph elements found")

# Strategy 3: Extract all paragraphs from within chapter_content div
print("\n=== Strategy 3: chapter_content paras ===")
chapter_html = extract_div_content(html, "chapter_content")
if chapter_html:
    paras = re.findall(r'<p[^>]*>(.*?)</p>', chapter_html, re.DOTALL)
    # Filter out nav/footer text
    filtered = []
    for p in paras:
        t = re.sub(r'<[^>]+>', '', p).strip()
        if len(t) > 20 and 'Library' not in t and 'Download App' not in t and 'Coins' not in t and 'comment' not in t.lower():
            filtered.append(t)
    text3 = '\n\n'.join(filtered)
    print(f"chapter_content paragraphs: {len(filtered)}")
    print(f"Text length: {len(text3)}")
    print(f"Preview:\n{text3[:1000]}")
else:
    print("chapter_content not found")
