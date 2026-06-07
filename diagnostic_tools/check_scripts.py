import re

html = open(r'c:\Users\karol\OneDrive\Documents\GitHub\MikoNovelSources\scrape_output\search_page.html', encoding='utf-8').read()
scripts = re.findall(r'<script[^>]*>(.*?)</script>', html, re.DOTALL)

print('Total scripts:', len(scripts))
for i, s in enumerate(scripts):
    if 'initial' in s.lower() or 'state' in s.lower() or 'data' in s.lower():
        idx = s.lower().find('initialstate')
        idx2 = s.lower().find('__initial_state__')
        idx3 = s.lower().find('__data__')
        found = []
        if idx != -1: found.append(f'initialState@{idx}')
        if idx2 != -1: found.append(f'__INITIAL_STATE__@{idx2}')
        if idx3 != -1: found.append(f'__DATA__@{idx3}')
        if found:
            print(f'Script {i}: {", ".join(found)}')
            snippet = s[max(0, idx-50):idx+200] if idx != -1 else s[:200]
            print(repr(snippet))
            print('---')
