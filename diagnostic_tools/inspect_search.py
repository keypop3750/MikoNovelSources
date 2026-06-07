import json

d = json.load(open(r'c:\Users\karol\OneDrive\Documents\GitHub\MikoNovelSources\scrape_output\search_page_parsed.json', encoding='utf-8'))
sr = d['props']['initialState']['entities']['searchResultList']

print('Search result keys:', list(sr.keys()))
k = list(sr.keys())[0]
print('First result keys:', list(sr[k].keys()))

bi = sr[k]['bookInfo']
print('bookInfo type:', type(bi).__name__, 'len:', len(bi) if isinstance(bi, list) else 'n/a')

if isinstance(bi, list) and bi:
    print('First bookInfo item keys:', list(bi[0].keys()))
    for book in bi[:3]:
        print(f"  - {book.get('bookName')} by {book.get('authorName')} (id={book.get('bookId')}, score={book.get('totalScore')})")
