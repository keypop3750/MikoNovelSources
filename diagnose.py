#!/usr/bin/env python3
"""
Diagnostic script for MikoNovelSources scrapers.
Fetches live pages and tests selectors against real HTML.
"""

import urllib.request
import re
import sys
from html.parser import HTMLParser

HEADERS = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36',
    'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
    'Accept-Language': 'en-US,en;q=0.5',
}


def fetch(url):
    print(f"\n=== Fetching: {url} ===")
    try:
        req = urllib.request.Request(url, headers=HEADERS)
        with urllib.request.urlopen(req, timeout=30) as resp:
            data = resp.read().decode('utf-8', errors='replace')
            print(f"Status: {resp.status}, Length: {len(data)} bytes")
            return data
    except Exception as e:
        print(f"ERROR: {e}")
        return None


class SimpleHtmlExtractor(HTMLParser):
    def __init__(self):
        super().__init__()
        self.tag_stack = []
        self.links = []
        self.chapter_sections = []
        self.in_chapter_container = False
        self.current_container = None

    def handle_starttag(self, tag, attrs):
        attr_dict = dict(attrs)
        classes = attr_dict.get('class', '').split()
        id_val = attr_dict.get('id', '')

        # Track chapter-related containers
        chapter_indicators = ['chapter', 'chapters', 'toc', 'chapter-list', 'chapterlist']
        for indicator in chapter_indicators:
            if indicator in ' '.join(classes).lower() or indicator in id_val.lower():
                self.current_container = {
                    'tag': tag,
                    'attrs': attr_dict,
                    'depth': len(self.tag_stack),
                    'links': []
                }

        # Track all links
        href = attr_dict.get('href', '')
        if href:
            link_info = {
                'tag': tag,
                'href': href,
                'text': '',
                'classes': classes,
                'parent_classes': [],
                'depth': len(self.tag_stack)
            }
            if self.current_container and len(self.tag_stack) >= self.current_container['depth']:
                self.current_container['links'].append(link_info)
            self.links.append(link_info)

        self.tag_stack.append((tag, attr_dict))

    def handle_endtag(self, tag):
        if self.tag_stack:
            self.tag_stack.pop()
        if self.current_container and len(self.tag_stack) < self.current_container['depth']:
            if self.current_container['links']:
                self.chapter_sections.append(self.current_container)
            self.current_container = None

    def handle_data(self, data):
        text = data.strip()
        if text and self.links and not self.links[-1]['text']:
            self.links[-1]['text'] = text


def analyze(html, url):
    if not html:
        return

    extractor = SimpleHtmlExtractor()
    try:
        extractor.feed(html)
    except Exception as e:
        print(f"Parse warning: {e}")

    print(f"\n  Total links found: {len(extractor.links)}")

    # Find links that look like chapters
    chapter_patterns = [
        r'chapter[_\-]?\d+',
        r'ch[_\-]?\d+',
        r'/chapter/',
        r'chapter\.html',
        r'read[_\-]?chapter',
    ]

    chapter_links = []
    for link in extractor.links:
        href_lower = link['href'].lower()
        text_lower = link['text'].lower()
        for pat in chapter_patterns:
            if re.search(pat, href_lower) or re.search(pat, text_lower):
                chapter_links.append(link)
                break

    print(f"  Links matching chapter patterns: {len(chapter_links)}")
    for i, link in enumerate(chapter_links[:10]):
        print(f"    [{i}] href={link['href'][:80]:<80} text='{link['text'][:60]}'")
    if len(chapter_links) > 10:
        print(f"    ... ({len(chapter_links) - 10} more)")

    # Find chapter-related containers
    print(f"\n  Chapter-related containers found: {len(extractor.chapter_sections)}")
    for i, section in enumerate(extractor.chapter_sections[:5]):
        print(f"    [{i}] <{section['attrs']}> - {len(section['links'])} links inside")
        for j, link in enumerate(section['links'][:3]):
            print(f"        href={link['href'][:60]:<60} text='{link['text'][:40]}'")

    # Specific selector tests
    print("\n--- Specific Selector Tests ---")
    selectors = {
        'novelfull': {
            'title': r'<h3[^>]*class="[^"]*title[^"]*"[^>]*>(.*?)</h3>',
            'chapters': r'<a[^>]*href="([^"]*)"[^>]*>([^<]*(?:Chapter|Ch\.?|Vol\.?)[^<]*)</a>',
            'chapters2': r'<li[^>]*>\s*<a[^>]*href="([^"]*)"[^>]*>(.*?)</a>\s*</li>',
        },
        'novelsonline': {
            'chapters_old': r'ul\.chapter-chs',
            'chapters_new': r'id=["\']chapters-list["\']',
            'any_chapter_link': r'href="([^"]*(?:chapter|read)[^"]*)"[^>]*>([^<]{3,60})</a>',
        },
        'scribblehub': {
            'story_id': r'<input[^>]*id=["\']mypostid["\'][^>]*>',
            'story_id_alt': r'<input[^>]*name=["\']mypostid["\'][^>]*>',
            'story_id_attr': r'data-story-id=["\']([^"\']*)["\']',
            'chapters': r'<li[^>]*class=["\'][^"\']*toc_w[^"\']*["\'][^>]*>(.*?)</li>',
        }
    }

    # Determine which source this is
    source = None
    for src in selectors:
        if src in url.lower():
            source = src
            break

    if not source:
        print("  (Could not determine source from URL)")
        return

    for name, pattern in selectors[source].items():
        if pattern.startswith('r"') or pattern.startswith("r'"):
            pattern = pattern[2:-1]  # crude strip of r"..."
        matches = re.findall(pattern, html, re.IGNORECASE | re.DOTALL)
        print(f"  [{name}] matches: {len(matches)}")
        for i, m in enumerate(matches[:5]):
            text = str(m).replace('\n', ' ').replace('\r', '').strip()
            if len(text) > 100:
                text = text[:100] + "..."
            print(f"    [{i}] {text}")


def main():
    urls = {
        'novelfull': 'https://novelfull.com/the-mech-touch.html',
        'novelsonline': 'https://novelsonline.net/one-piece',
        'scribblehub': 'https://www.scribblehub.com/series/770/the-daily-life-of-the-immortal-king/',
    }

    for name, url in urls.items():
        html = fetch(url)
        if html:
            # Save raw HTML for manual inspection
            safe_name = name + '_raw.html'
            with open(safe_name, 'w', encoding='utf-8') as f:
                f.write(html)
            print(f"  [Saved raw HTML to {safe_name}]")
        analyze(html, url)
        print()


if __name__ == '__main__':
    main()
