import json

d = json.load(open(r'c:\Users\karol\OneDrive\Documents\GitHub\MikoNovelSources\scrape_output\search_page_parsed.json', encoding='utf-8'))
sr = d['props']['initialState']['entities']['searchResultList']
k = list(sr.keys())[0]
result = sr[k]

bi = result['bookInfo']
print('bookInfo.total:', bi.get('total'))
print('bookInfo.isLast:', bi.get('isLast'))
print('bookInfo.bookItems type:', type(bi['bookItems']).__name__, 'len:', len(bi['bookItems']))
print()

for i, item in enumerate(bi['bookItems'][:5]):
    print(f'Item {i}: type={type(item).__name__}')
    if isinstance(item, dict):
        print(f'  keys: {list(item.keys())[:15]}')
        print(f'  bookName={item.get("bookName")}, author={item.get("authorName")}, id={item.get("bookId")}')
    elif isinstance(item, str):
        print(f'  value: {item[:100]}')
    else:
        print(f'  value: {item}')
