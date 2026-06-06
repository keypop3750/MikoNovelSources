#!/usr/bin/env python3
"""
Comprehensive diagnostic for ALL MikoNovelSources extensions.
Tests novel detail pages and chapter list extraction for every source.
"""

import urllib.request
import urllib.error
import re
import json

HEADERS = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36',
    'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
    'Accept-Language': 'en-US,en;q=0.5',
}


def fetch(url):
    try:
        req = urllib.request.Request(url, headers=HEADERS)
        with urllib.request.urlopen(req, timeout=30) as resp:
            return resp.read().decode('utf-8', errors='replace')
    except urllib.error.HTTPError as e:
        return f"HTTP_{e.code}"
    except Exception as e:
        return f"ERROR_{type(e).__name__}: {e}"


SOURCES = {
    "AllNovel": {
        "base": "https://allnovel.org",
        "test_novel": "https://allnovel.org/the-mech-touch",
        "title_sel": r'<h3[^>]*class="[^"]*title[^"]*"[^>]*>(.*?)</h3>',
        "chapter_sel": r'<a[^>]*href="(/the-mech-touch/chapter-[^"]*)"[^>]*>\s*<span[^>]*class="chapter-text"[^>]*>(.*?)</span>',
        "chapter_sel_alt": r'href="(/the-mech-touch/chapter-[^"]*)"[^>]*>(?:Chapter|Ch\.?)[^<]*</a>',
    },
    "BoxNovel": {
        "base": "https://boxnovel.com",
        "test_novel": "https://boxnovel.com/novel/absolute-resonance/",
        "title_sel": r'<h1[^>]*>(.*?)</h1>',
        "chapter_sel": r'href="([^"]*chapter[^"]*)"[^>]*>(?:Chapter|Ch\.?)[^<]*</a>',
    },
    "FreeWebNovel": {
        "base": "https://freewebnovel.com",
        "test_novel": "https://freewebnovel.com/necropolis-immortal.html",
        "title_sel": r'<h1[^>]*class="tit"[^>]*>(.*?)</h1>',
        "chapter_sel": r'href="(/read[^"]*)"[^>]*>(?:Chapter|Ch\.?)[^<]*</a>',
    },
    "LibRead": {
        "base": "https://libread.com",
        "test_novel": "https://libread.com/novel/perfect-run.html",
        "title_sel": r'<h1[^>]*>(.*?)</h1>',
        "chapter_sel": r'href="(/novel/perfect-run/chapter-[^"]*)"[^>]*>(?:Chapter|Ch\.?)[^<]*</a>',
    },
    "LightNovelPub": {
        "base": "https://www.lightnovelpub.com",
        "test_novel": "https://www.lightnovelpub.com/novel/omniscient-readers-viewpoint-23021325",
        "title_sel": r'<h1[^>]*>(.*?)</h1>',
        "chapter_sel": r'href="(/novel/[^/]+/chapter-[^"]*)"[^>]*>(?:Chapter|Ch\.?)[^<]*</a>',
    },
    "NovelBin": {
        "base": "https://novelbin.com",
        "test_novel": "https://novelbin.com/novel/absolute-resonance",
        "title_sel": r'<h1[^>]*class="title"[^>]*>(.*?)</h1>',
        "chapter_sel": r'href="(/novel/absolute-resonance/chapter-[^"]*)"[^>]*>(?:Chapter|Ch\.?)[^<]*</a>',
    },
    "NovelFull": {
        "base": "https://novelfull.com",
        "test_novel": "https://novelfull.com/the-mech-touch.html",
        "title_sel": r'<h3[^>]*class="title"[^>]*>(.*?)</h3>',
        "chapter_sel": r'href="(/the-mech-touch/chapter-[^"]*)"[^>]*>(?:Chapter|Ch\.?)[^<]*</a>',
    },
    "NovelsOnline": {
        "base": "https://novelsonline.org",
        "test_novel": "https://novelsonline.org/brand-new-life-online-rise-of-the-goddess-of-harvest",
        "title_sel": r'<h1[^>]*>(.*?)</h1>',
        "chapter_sel": r'href="(/brand-new-life-online-rise-of-the-goddess-of-harvest/chapter-[^"]*)"[^>]*>(?:Chapter|Ch\.?)[^<]*</a>',
        "chapter_sel_alt": r'href="(/[^/]+/chapter-[^"]*)"[^>]*>(?:Chapter|Ch\.?)[^<]*</a>',
    },
    "ReadLightNovel": {
        "base": "https://www.readlightnovel.me",
        "test_novel": "https://www.readlightnovel.me/one-piece",
        "title_sel": r'<h1[^>]*>(.*?)</h1>',
        "chapter_sel": r'href="(/one-piece/chapter-[^"]*)"[^>]*>(?:Chapter|Ch\.?)[^<]*</a>',
    },
    "ReadNovelFull": {
        "base": "https://readnovelfull.com",
        "test_novel": "https://readnovelfull.com/the-mech-touch.html",
        "title_sel": r'<h3[^>]*class="title"[^>]*>(.*?)</h3>',
        "chapter_sel": r'href="(/the-mech-touch/chapter-[^"]*)"[^>]*>(?:Chapter|Ch\.?)[^<]*</a>',
    },
    "RoyalRoad": {
        "base": "https://www.royalroad.com",
        "test_novel": "https://www.royalroad.com/fiction/21220/mother-of-learning",
        "title_sel": r'<h1[^>]*>(.*?)</h1>',
        "chapter_sel": r'href="(/fiction/21220/mother-of-learning/chapter/[^"]*)"[^>]*>(?:Chapter|Ch\.?)[^<]*</a>',
    },
    "ScribbleHub": {
        "base": "https://www.scribblehub.com",
        "test_novel": "https://www.scribblehub.com/read/2351740-i-reincarnated-as-the-villainess-and-i-refuse-to-follow-the-plot",
        "title_sel": r'<h1[^>]*>(.*?)</h1>',
        "chapter_sel": r'href="(/read/2351740[^"]*/chapter/[^"]*)"[^>]*>(?:Chapter|Ch\.?)[^<]*</a>',
        "story_id_sel": r'id=["\']mypostid["\'][^>]*value=["\']([^"\']+)["\']',
    },
}


def test_source(name, config):
    print(f"\n{'='*60}")
    print(f"SOURCE: {name}")
    print(f"URL: {config['test_novel']}")
    print(f"{'='*60}")

    html = fetch(config['test_novel'])
    if html.startswith('HTTP_') or html.startswith('ERROR_'):
        print(f"  [FAIL] Could not fetch: {html}")
        return {"source": name, "status": html, "chapters_found": 0}

    # Test title selector
    title_match = re.search(config['title_sel'], html, re.IGNORECASE | re.DOTALL)
    if title_match:
        title = re.sub(r'<[^>]+>', '', title_match.group(1)).strip()
        print(f"  [OK] Title: {title[:80]}")
    else:
        print(f"  [WARN] Title selector returned nothing")

    # Test chapter selectors
    chapter_results = {}
    for key in ['chapter_sel', 'chapter_sel_alt']:
        if key not in config:
            continue
        matches = re.findall(config[key], html, re.IGNORECASE | re.DOTALL)
        if matches:
            print(f"  [OK] {key}: {len(matches)} chapters found")
            for i, m in enumerate(matches[:5]):
                href = m if isinstance(m, str) else m[0]
                text = m[1] if isinstance(m, tuple) and len(m) > 1 else ''
                print(f"      [{i}] {href[:70]:<70} | {text[:40]}")
            if len(matches) > 5:
                print(f"      ... ({len(matches)-5} more)")
            chapter_results[key] = len(matches)
        else:
            print(f"  [FAIL] {key}: 0 chapters found")
            chapter_results[key] = 0

    # Special checks
    if 'story_id_sel' in config:
        sid = re.search(config['story_id_sel'], html)
        if sid:
            print(f"  [OK] Story ID: {sid.group(1)}")
        else:
            print(f"  [FAIL] Story ID not found")

    total_chapters = max(chapter_results.values()) if chapter_results else 0
    return {"source": name, "status": "OK", "chapters_found": total_chapters}


def main():
    results = []
    for name, config in SOURCES.items():
        result = test_source(name, config)
        results.append(result)

    print(f"\n{'='*60}")
    print("SUMMARY")
    print(f"{'='*60}")
    for r in results:
        status = "OK" if r['chapters_found'] > 0 else "FAIL"
        print(f"  {status:>4} | {r['source']:<20} | chapters={r['chapters_found']}")

    # Save results
    with open('diagnose_results.json', 'w') as f:
        json.dump(results, f, indent=2)
    print(f"\n[Saved results to diagnose_results.json]")


if __name__ == '__main__':
    main()
