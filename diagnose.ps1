#!/usr/bin/env pwsh
<#
.SYNOPSIS
    Diagnose novel extension scrapers by fetching live pages and checking selectors.
.DESCRIPTION
    This script fetches novel pages from supported sources and prints what each
    selector actually returns, making it easy to see why chapters might be missing.

    Run from PowerShell:
        .\diagnose.ps1
    Or pass a custom URL:
        .\diagnose.ps1 -Url "https://novelfull.com/some-novel.html" -Source "novelfull"
#>

param(
    [string]$Url = "",
    [string]$Source = ""
)

$USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"

function Fetch-Page($url) {
    Write-Host "`n=== Fetching: $url ===" -ForegroundColor Cyan
    try {
        $headers = @{
            "User-Agent" = $USER_AGENT
            "Accept" = "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8"
            "Accept-Language" = "en-US,en;q=0.9"
            "Upgrade-Insecure-Requests" = "1"
            "Sec-Fetch-Dest" = "document"
            "Sec-Fetch-Mode" = "navigate"
            "Sec-Fetch-Site" = "none"
        }
        $resp = Invoke-WebRequest -Uri $url -Headers $headers -UseBasicParsing -MaximumRedirection 5 -TimeoutSec 30
        Write-Host "Status: $($resp.StatusCode), Length: $($resp.RawContentLength) bytes" -ForegroundColor Green
        return $resp.Content
    } catch {
        Write-Host "ERROR fetching ${url}: $($_.Exception.Message)" -ForegroundColor Red
        return $null
    }
}

function Check-Selector($html, $selector, $label) {
    if (-not $html) { return }
    # Use a simple regex-based approach since we can't rely on external tools
    # For proper diagnosis we write the HTML to a temp file and use Python if available
    Write-Host "  [$label] selector: $selector" -ForegroundColor Yellow
}

# ---------------------------------------------------------------------------
# Predefined test URLs for each source
# ---------------------------------------------------------------------------
$tests = @{
    "novelfull" = @{
        url    = "https://novelfull.com/the-mech-touch.html"
        selectors = @{
            "title"       = "h3.title"
            "cover"       = "div.book > img"
            "description" = "div.desc-text"
            "author"      = "div.info > div:nth-child(1) > a"
            "chapters"    = ".list-chapter li a"
            "chapters_alt"= "#list-chapter .list-chapter li a"
        }
    }
    "novelsonline" = @{
        url    = "https://novelsonline.net/one-piece"
        selectors = @{
            "title"         = "h1"
            "chapters_old"  = "ul.chapter-chs li a"
            "chapters_new"  = "#chapters-list li a"
            "chapters_div"  = ".chapters li a"
            "chapters_any"  = "a[href*='chapter']"
            "page_links"    = "ul.chapter-chs li a"
        }
    }
    "scribblehub" = @{
        url    = "https://www.scribblehub.com/series/770/the-daily-life-of-the-immortal-king/"
        selectors = @{
            "story_id"    = "input#mypostid"
            "story_id_alt"= "input[name=mypostid]"
            "story_id_alt2"= "[data-story-id]"
            "chapters"    = "li.toc_w"
        }
    }
}

# ---------------------------------------------------------------------------
# If Python + BeautifulSoup is available, use it for proper parsing
# ---------------------------------------------------------------------------
$python = Get-Command python -ErrorAction SilentlyContinue
if (-not $python) { $python = Get-Command python3 -ErrorAction SilentlyContinue }

if ($python) {
    Write-Host "Python found at: $($python.Source)" -ForegroundColor Green

    # Create a temporary Python diagnostic script
    $pyScript = Join-Path $env:TEMP "novel_diag_$(Get-Random).py"
    @'
import sys, urllib.request, re
from html.parser import HTMLParser

class SimpleSelectorChecker(HTMLParser):
    def __init__(self, selector):
        super().__init__()
        self.selector = selector
        self.matches = []
        self.current_tag = None
        self.depth = 0
    def handle_starttag(self, tag, attrs):
        self.current_tag = tag
        self.depth += 1
        attr_dict = dict(attrs)
        # Very naive selector matching for common patterns
        sel = self.selector.strip()
        matched = False
        if sel.startswith('#') and 'id' in attr_dict and attr_dict['id'] == sel[1:]:
            matched = True
        elif sel.startswith('.') and 'class' in attr_dict:
            classes = attr_dict['class'].split()
            if sel[1:] in classes:
                matched = True
        elif sel == tag:
            matched = True
        elif ' ' in sel:
            # descendant selector - very naive
            pass
        if matched:
            self.matches.append((tag, attr_dict))
    def handle_endtag(self, tag):
        self.depth -= 1

url = sys.argv[1]
sel = sys.argv[2]
label = sys.argv[3] if len(sys.argv) > 3 else sel

try:
    req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
    with urllib.request.urlopen(req, timeout=30) as resp:
        html = resp.read().decode('utf-8', errors='replace')
except Exception as e:
    print(f"FETCH_ERROR: {e}")
    sys.exit(1)

# Use regex-based extraction for simple selectors
html_lower = html.lower()
count = 0

# Naive regex patterns for common selectors
patterns = {
    r'.list-chapter li a': r'<a[^>]*href="([^"]*)"[^>]*>([^<]*)</a>',
    r'#list-chapter .list-chapter li a': r'<a[^>]*href="([^"]*)"[^>]*>([^<]*)</a>',
    r'ul.chapter-chs li a': r'<a[^>]*href="([^"]*)"[^>]*>([^<]*)</a>',
    r'li.toc_w': r'<li[^>]*class="[^"]*toc_w[^"]*"[^>]*>(.*?)</li>',
    r'input#mypostid': r'<input[^>]*id="mypostid"[^>]*>',
    r'input[name=mypostid]': r'<input[^>]*name="mypostid"[^>]*>',
    r'[data-story-id]': r'data-story-id="([^"]*)"',
    r'h3.title': r'<h3[^>]*class="[^"]*title[^"]*"[^>]*>(.*?)</h3>',
    r'div.book > img': r'<img[^>]*>(?=.*?</div>)',
    r'div.desc-text': r'<div[^>]*class="[^"]*desc-text[^"]*"[^>]*>(.*?)</div>',
    r'#chapters-list li a': r'<a[^>]*href="([^"]*)"[^>]*>([^<]*)</a>',
    r'.chapters li a': r'<a[^>]*href="([^"]*)"[^>]*>([^<]*)</a>',
    r"a[href*='chapter']": r'href="([^"]*[Cc]hapter[^"]*)"[^>]*>([^<]*)</a>',
}

pat = patterns.get(sel, r'<[^>]*>')
if sel in patterns:
    matches = re.findall(patterns[sel], html, re.IGNORECASE | re.DOTALL)
    count = len(matches)
    print(f"MATCH_COUNT: {count}")
    for i, m in enumerate(matches[:5]):
        text = str(m).replace('\\n',' ').replace('\\r','').strip()
        if len(text) > 120:
            text = text[:120] + "..."
        print(f"  [{i}] {text}")
    if count > 5:
        print(f"  ... ({count-5} more)")
else:
    # fallback: count raw occurrences of selector text in html
    raw = sel.replace('.', 'class="').replace('#', 'id="')
    count = html_lower.count(raw.lower())
    print(f"MATCH_COUNT: {count} (naive string count)")
'@ | Set-Content -Path $pyScript -Encoding UTF8

    function Run-PythonCheck($url, $selector, $label) {
        Write-Host "  [$label] $selector" -ForegroundColor Yellow
        $output = & $python.Source $pyScript $url $selector $label 2>&1
        $output | ForEach-Object { Write-Host "    $_" }
    }

    if ($Source -and $Url) {
        $html = Fetch-Page $Url
        if ($tests[$Source]) {
            foreach ($kvp in $tests[$Source].selectors.GetEnumerator()) {
                Run-PythonCheck $Url $kvp.Value $kvp.Key
            }
        }
    } elseif ($Source) {
        $t = $tests[$Source]
        if (-not $t) {
            Write-Host "Unknown source. Available: $($tests.Keys -join ', ')" -ForegroundColor Red
            exit 1
        }
        $html = Fetch-Page $t.url
        foreach ($kvp in $t.selectors.GetEnumerator()) {
            Run-PythonCheck $t.url $kvp.Value $kvp.Key
        }
    } else {
        foreach ($src in $tests.GetEnumerator()) {
            $html = Fetch-Page $src.Value.url
            foreach ($sel in $src.Value.selectors.GetEnumerator()) {
                Run-PythonCheck $src.Value.url $sel.Value $sel.Key
            }
            Write-Host ""
        }
    }

    Remove-Item $pyScript -ErrorAction SilentlyContinue
} else {
    Write-Host "Python not found. Falling back to curl-only mode (no selector parsing)." -ForegroundColor Yellow
    Write-Host "Install Python for full selector diagnostics." -ForegroundColor Yellow

    if ($Source -and $tests[$Source]) {
        Fetch-Page $tests[$Source].url | Out-Null
    } elseif ($Url) {
        Fetch-Page $Url | Out-Null
    } else {
        foreach ($src in $tests.GetEnumerator()) {
            Fetch-Page $src.Value.url | Out-Null
        }
    }
}

Write-Host "`nDone." -ForegroundColor Green
