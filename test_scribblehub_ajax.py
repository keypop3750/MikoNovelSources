import urllib.request
import urllib.parse
import re

url = 'https://www.scribblehub.com/wp-admin/admin-ajax.php'
data = urllib.parse.urlencode({
    'action': 'wi_gettocchp',
    'strSID': '2351740',
    'page': '1'
}).encode('utf-8')

req = urllib.request.Request(url, data=data, headers={
    'User-Agent': 'Mozilla/5.0',
    'Content-Type': 'application/x-www-form-urlencoded',
    'X-Requested-With': 'XMLHttpRequest',
}, method='POST')

try:
    with urllib.request.urlopen(req, timeout=30) as resp:
        html = resp.read().decode('utf-8', errors='replace')
        print('Status:', resp.status)
        print('Length:', len(html))
        print('Has li.toc_w:', 'toc_w' in html)
        print('Has li:', '<li' in html)
        
        # Show first li elements
        lis = re.findall(r'<li[^>]*>(.*?)</li>', html, re.IGNORECASE | re.DOTALL)
        print('LI count:', len(lis))
        for i, li in enumerate(lis[:5]):
            text = re.sub(r'<[^>]+>', ' ', li).strip()
            print(f'  [{i}] {text[:120]}')
        
        # Show raw start
        print('Raw start:', html[:500])
        
        # Save
        with open('scribblehub_ajax.html', 'w', encoding='utf-8') as f:
            f.write(html)
        print('Saved to scribblehub_ajax.html')
except Exception as e:
    print('ERROR:', type(e).__name__, e)
