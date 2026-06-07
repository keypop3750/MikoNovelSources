import json, re

html = open(r'c:\Users\karol\OneDrive\Documents\GitHub\MikoNovelSources\scrape_output\search_page.html', encoding='utf-8').read()
scripts = re.findall(r'<script[^>]*>(.*?)</script>', html, re.DOTALL)

# Script 17 contains the initialState JSON directly
s = scripts[17]
print('Script length:', len(s))
print('First 200 chars:', s[:200])
print()

try:
    data = json.loads(s)
    print('Parsed successfully! Top keys:', list(data.keys()))
    
    if 'props' in data:
        props = data['props']
        print('props keys:', list(props.keys()))
        
        if 'initialState' in props:
            st = props['initialState']
            print('initialState keys:', list(st.keys()))
            
            if 'entities' in st:
                ent = st['entities']
                print('entities keys:', list(ent.keys()))
                
                # Look for search results or books
                for k in ent.keys():
                    v = ent[k]
                    if isinstance(v, dict):
                        print(f'  {k}: dict with {len(v)} items')
                        for sk in list(v.keys())[:3]:
                            sv = v[sk]
                            print(f'    {sk}: {type(sv).__name__} (keys: {list(sv.keys())[:8] if isinstance(sv, dict) else "n/a"})')
                    elif isinstance(v, list):
                        print(f'  {k}: list with {len(v)} items')
                    else:
                        print(f'  {k}: {type(v).__name__} = {v}')
            
            # Also check for search-related data at top level of initialState
            for k in st.keys():
                if k != 'entities':
                    print(f'initialState.{k}: {type(st[k]).__name__}')
    
    # Save the full parsed data
    with open(r'c:\Users\karol\OneDrive\Documents\GitHub\MikoNovelSources\scrape_output\search_page_parsed.json', 'w', encoding='utf-8') as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
    print('\nSaved full parsed data to search_page_parsed.json')
        
except json.JSONDecodeError as e:
    print('JSON decode error:', e)
    print('Trying to find the JSON object bounds...')
    # Find first { and matching }
    start = s.find('{')
    if start != -1:
        print('First { at', start)
        print(s[start:start+500])
