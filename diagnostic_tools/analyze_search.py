import re
html = open(r'c:\Users\karol\OneDrive\Documents\GitHub\MikoNovelSources\scrape_output\search_page.html', encoding='utf-8').read()

# Pattern 1: Modern WebNovel class names
matches = re.findall(r'href="(/book/[^"]+)"[^>]*>.*?<div[^>]*class="[^"]*styles_bookName__[^"]*"[^>]*>([^<]+)</div>', html, re.DOTALL)
print('Pattern 1 (styles_bookName):', len(matches))
for m in matches[:5]:
    print(' ', m[0], '-', m[1].strip())

# Pattern 2: Any /book/ link with nearby text
matches2 = re.findall(r'href="(/book/[^"]+)"[^>]*>([^<]{10,100})</a>', html)
print('\nPattern 2 (simple anchor):', len(matches2))
for m in matches2[:5]:
    t = re.sub(r'<[^>]+>', '', m[1]).strip()
    print(' ', m[0], '-', t[:60])

# Pattern 3: Look for the JSON state in the search page
scripts = re.findall(r'<script[^>]*>(.*?)</script>', html, re.DOTALL)
for s in scripts:
    if '__INITIAL_STATE__' in s or 'initialState' in s:
        print('\nFound script with initialState/initial state')
        # Try to extract JSON
        idx = s.find('initialState')
        if idx != -1:
            print('  initialState at position', idx)
        break
