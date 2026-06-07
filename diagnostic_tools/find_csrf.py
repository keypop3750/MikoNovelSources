import re

html = open(r'c:\Users\karol\OneDrive\Documents\GitHub\MikoNovelSources\scrape_output\homepage.html', encoding='utf-8').read()

print("Looking for CSRF token patterns...")
patterns = [
    r'_csrfToken["\']?\s*[:=]\s*["\']([^"\']+)',
    r'csrfToken["\']?\s*[:=]\s*["\']([^"\']+)',
    r'window\._csrfToken\s*=\s*["\']([^"\']+)',
    r'"_csrfToken":"([^"]+)"',
]
for pat in patterns:
    m = re.search(pat, html)
    if m:
        print(f"Pattern '{pat[:40]}' found: {m.group(1)[:60]}...")

# Also look for any token-like strings
tokens = re.findall(r'[a-zA-Z0-9]{20,60}', html)
print(f"\nPotential tokens (20-60 chars alphanumeric): {len(tokens)}")
for t in tokens[:10]:
    print(f"  {t}")
