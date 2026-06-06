import urllib.request
import urllib.parse

url = 'https://www.scribblehub.com/wp-admin/admin-ajax.php'
data = urllib.parse.urlencode({
    'action': 'wi_gettocchp',
    'strSID': '2351740',
    'page': '1'
}).encode('utf-8')

headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
    'Content-Type': 'application/x-www-form-urlencoded',
    'X-Requested-With': 'XMLHttpRequest',
    'Referer': 'https://www.scribblehub.com/series/2351740/i-reincarnated-as-the-villainess-and-i-refuse-to-follow-the-plot/',
}

req = urllib.request.Request(url, data=data, headers=headers, method='POST')

try:
    with urllib.request.urlopen(req, timeout=30) as resp:
        html = resp.read().decode('utf-8', errors='replace')
        print('Status:', resp.status)
        print('Length:', len(html))
        print('Has toc_w:', 'toc_w' in html)
        print('Start:', html[:500])
        with open('scribblehub_ajax2.html', 'w', encoding='utf-8') as f:
            f.write(html)
        print('Saved')
except Exception as e:
    print('ERROR:', type(e).__name__, e)
