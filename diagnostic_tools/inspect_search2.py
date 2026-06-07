import json

d = json.load(open(r'c:\Users\karol\OneDrive\Documents\GitHub\MikoNovelSources\scrape_output\search_page_parsed.json', encoding='utf-8'))
sr = d['props']['initialState']['entities']['searchResultList']
k = list(sr.keys())[0]
result = sr[k]

print('Result keys:', list(result.keys()))
print()

bi = result['bookInfo']
print('bookInfo keys:', list(bi.keys()))
print()

# Look at the actual book list
for bk in ['items', 'bookItems', 'list', 'data']:
    if bk in bi:
        v = bi[bk]
        print(f"bookInfo.{bk}: {type(v).__name__} len={len(v) if hasattr(v, '__len__') else 'n/a'}")
        if isinstance(v, list) and v:
            for book in v[:3]:
                print(f"  - {book.get('bookName')} by {book.get('authorName')} (id={book.get('bookId')})")
        break
else:
    print('No standard list key found. bookInfo contents:')
    for k2, v2 in bi.items():
        t = type(v2).__name__
        l = len(v2) if hasattr(v2, '__len__') and not isinstance(v2, str) else 'n/a'
        print(f"  {k2}: {t} (len={l})")
        if isinstance(v2, list) and v2:
            print(f'    First item keys: {list(v2[0].keys())[:10]}')
            for item in v2[:2]:
                print(f"      - {item.get('bookName', item.get('name', 'unknown'))}")
