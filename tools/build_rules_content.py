#!/usr/bin/env python3
"""Scrape the Jet Lag: Hide & Seek rulebook + expansion into a bundled JSON asset.

Sources
-------
* Base rulebook (Docusaurus): https://www.lifack.ch/docs/quick_start_guide/
  Crawled in reading order by following the "Next" pagination link (36 pages).
* Expansion reference rules: https://rules.jetlagthegame.com/expansion/
  A single page: Percentage Time Bonuses, Time Traps, and ~30 additional Curses.

Both bodies of text are Wendover Productions content; lifack.ch is a community
reproduction of the official rules. The two are MERGED so the expansion slots
into the base structure:
  * "Percentage Time Bonuses" and "Time Traps" are appended inside the base
    "Time Bonuses" section.
  * The expansion Curses are merged alphabetically into the base "Curses" list.

Output
------
app/src/main/assets/rules.json — an ordered tree consumed by RulesRepository.kt.

Schema
------
{
  "version": 1,
  "attribution": "<string>",
  "sections": [ Section, ... ]
}
Section = {
  "id":     "<stable slug>",
  "title":  "<display title>",
  "source": "base" | "expansion",     # provenance for an inline tag
  "blocks": [ Block, ... ],           # this section's own content
  "children": [ Section, ... ]        # nested subsections
}
Block = {
  "type": "heading" | "paragraph" | "bullet",
  "text": "<string>",                 # heading / paragraph
  "level": <int>,                     # heading only (2..4)
  "items": [ "<string>", ... ],       # bullet only
  "source": "base" | "expansion"      # optional; present on merged expansion content
}

Run with the repo venv:
    tools/.venv/bin/python tools/build_rules_content.py
"""

from __future__ import annotations

import json
import re
import time
from pathlib import Path
from typing import Dict, List, Optional

import requests
from bs4 import BeautifulSoup, Tag

BASE_ORIGIN = "https://www.lifack.ch"
BASE_START = "/docs/quick_start_guide/"
EXPANSION_URL = "https://rules.jetlagthegame.com/expansion/"

HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 "
        "(KHTML, like Gecko) Chrome/120 Mobile Safari/537.36"
    )
}

OUTPUT = (
    Path(__file__).resolve().parent.parent
    / "app"
    / "src"
    / "main"
    / "assets"
    / "rules.json"
)

ATTRIBUTION = (
    "Rules content \u00a9 Wendover Productions LLC. Base rulebook text reproduced "
    "from the community reference at lifack.ch; expansion rules from "
    "rules.jetlagthegame.com. Bundled for offline reference only; not affiliated "
    "with Jet Lag: The Game."
)

# Top-level grouping of base doc URL prefixes into a friendly table of contents.
# Order defines TOC order. Each entry: (url-segment, display title).
CATEGORY_ORDER = [
    ("quick_start_guide", "Quick Start Guide"),
    ("setting_up_your_map", "Setting Up Your Map"),
    ("seeking", "Seeking"),
    ("hiding", "Hiding"),
    ("general_tips", "General Tips"),
    ("experimental_game_designs", "Experimental Game Designs"),
]


def fetch(url: str) -> BeautifulSoup:
    resp = requests.get(url, headers=HEADERS, timeout=30)
    resp.raise_for_status()
    resp.encoding = "utf-8"
    return BeautifulSoup(resp.text, "html.parser")


def clean(text: str) -> str:
    # Drop zero-width spaces that Docusaurus injects into heading permalinks.
    text = text.replace("\u200b", "").replace("\ufeff", "")
    return re.sub(r"\s+", " ", text).strip()


def blocks_from_content(content: Tag, *, skip_first_h1: bool = True) -> List[dict]:
    """Convert a doc content container into an ordered list of blocks.

    Only direct, meaningful elements are emitted: headings (h2-h4), paragraphs,
    and bullet lists. The page's <h1> is used as the section title elsewhere, so
    it is skipped here.
    """
    blocks: List[dict] = []
    seen_h1 = False
    for el in content.find_all(["h1", "h2", "h3", "h4", "p", "ul", "ol"], recursive=True):
        name = el.name
        if name == "h1":
            seen_h1 = True
            continue
        if name in ("h2", "h3", "h4"):
            text = clean(el.get_text(" ", strip=True))
            if text:
                blocks.append({"type": "heading", "level": int(name[1]), "text": text})
        elif name == "p":
            text = clean(el.get_text(" ", strip=True))
            if text:
                blocks.append({"type": "paragraph", "text": text})
        elif name in ("ul", "ol"):
            # Avoid double-emitting nested lists: only handle top-level lists.
            if el.find_parent(["ul", "ol"]) is not None:
                continue
            items = [
                clean(li.get_text(" ", strip=True))
                for li in el.find_all("li", recursive=False)
            ]
            items = [i for i in items if i]
            if items:
                blocks.append({"type": "bullet", "items": items})
    if skip_first_h1 and not seen_h1:
        pass
    return blocks


def crawl_base() -> List[dict]:
    """Follow the Next pagination chain, returning ordered page dicts.

    Each page dict: { url, segments, title, blocks }.
    """
    pages: List[dict] = []
    seen: set = set()
    url = BASE_START
    while url and url not in seen:
        seen.add(url)
        soup = fetch(BASE_ORIGIN + url)
        content = soup.select_one(".theme-doc-markdown") or soup.find("article")
        if content is None:
            raise RuntimeError(f"No content container on {url}")
        h1 = content.find("h1")
        title = clean(h1.get_text(" ", strip=True)) if h1 else url.strip("/").split("/")[-1]
        segments = [s for s in url.strip("/").split("/") if s and s != "docs"]
        pages.append(
            {
                "url": url,
                "segments": segments,
                "title": title,
                "blocks": blocks_from_content(content),
            }
        )
        nxt = soup.select_one("a.pagination-nav__link--next")
        url = nxt.get("href") if nxt else None
        time.sleep(0.25)
    return pages


def parse_expansion() -> dict:
    """Return expansion pieces: percentage bonuses, time traps, and curse groups.

    { "time_extras": [Block, ...], "curses": [ {name, blocks:[Block]}, ... ] }
    """
    soup = fetch(EXPANSION_URL)
    main = soup.find("main") or soup.body
    nodes = main.find_all(["h2", "h3", "p", "ul", "ol"], recursive=True)

    time_extras: List[dict] = []
    curses: List[dict] = []
    mode: Optional[str] = None  # None -> before curses; "curses" once we hit Curses h2
    current: Optional[dict] = None
    in_time_subsection = False  # becomes True at the first time-extra h3

    for el in nodes:
        name = el.name
        text = clean(el.get_text(" ", strip=True))
        if name == "h2":
            if text.lower() == "curses":
                mode = "curses"
                current = None
            continue
        if name == "h3":
            if mode == "curses":
                current = {"name": text, "blocks": []}
                curses.append(current)
            else:
                # Percentage Time Bonuses / Time Traps subsection heading.
                current = None
                in_time_subsection = True
                time_extras.append(
                    {"type": "heading", "level": 3, "text": text, "source": "expansion"}
                )
            continue
        # paragraphs / lists belong to whatever section we're in.
        if name == "p":
            if not text:
                continue
            block = {"type": "paragraph", "text": text, "source": "expansion"}
        elif name in ("ul", "ol"):
            if el.find_parent(["ul", "ol"]) is not None:
                continue
            items = [
                clean(li.get_text(" ", strip=True))
                for li in el.find_all("li", recursive=False)
            ]
            items = [i for i in items if i]
            if not items:
                continue
            block = {"type": "bullet", "items": items, "source": "expansion"}
        else:
            continue

        if mode == "curses":
            if current is not None:
                current["blocks"].append(block)
            # else: stray paragraph before first curse (e.g. the PDF link) — ignore.
        elif in_time_subsection:
            time_extras.append(block)
        # else: intro paragraph before the first time-extra heading (e.g. the
        # "Download the PDF version" link) — not part of the merged content.

    return {"time_extras": time_extras, "curses": curses}


CURSE_PREFIX = re.compile(r"^curse of (the |an |a )?", re.IGNORECASE)
TRAILING_DASH = re.compile(r"[\s\u2013\u2014\-]+$")


def curse_sort_key(name: str) -> str:
    stripped = CURSE_PREFIX.sub("", name)
    return stripped.lower()


def normalize_curse_name(name: str) -> str:
    return TRAILING_DASH.sub("", name).strip()


def split_curses_section(blocks: List[dict]) -> tuple:
    """Split a Curses page's blocks into (intro_blocks, curse_groups).

    A curse group starts at each h3 heading and runs until the next h3.
    """
    intro: List[dict] = []
    groups: List[dict] = []
    current: Optional[dict] = None
    for b in blocks:
        if b["type"] == "heading" and b.get("level") == 3:
            current = {"name": normalize_curse_name(b["text"]), "blocks": []}
            groups.append(current)
        elif current is None:
            intro.append(b)
        else:
            current["blocks"].append(b)
    return intro, groups


def build_curses_section(base_section: dict, expansion_curses: List[dict]) -> None:
    """Rebuild base Curses section blocks with expansion curses merged A-Z."""
    intro, base_groups = split_curses_section(base_section["blocks"])

    merged: List[dict] = []
    for g in base_groups:
        merged.append({"name": g["name"], "blocks": g["blocks"], "source": "base"})
    for g in expansion_curses:
        merged.append(
            {"name": normalize_curse_name(g["name"]), "blocks": g["blocks"], "source": "expansion"}
        )
    merged.sort(key=lambda g: curse_sort_key(g["name"]))

    new_blocks: List[dict] = list(intro)
    for g in merged:
        heading = {"type": "heading", "level": 3, "text": g["name"]}
        if g["source"] == "expansion":
            heading["source"] = "expansion"
        new_blocks.append(heading)
        new_blocks.extend(g["blocks"])
    base_section["blocks"] = new_blocks


def build_tree(pages: List[dict]) -> List[dict]:
    """Assemble ordered base pages into a nested section tree keyed by URL segments."""
    # node registry keyed by tuple(segments)
    roots: List[dict] = []
    index: Dict[tuple, dict] = {}

    def category_title(seg: str) -> str:
        for key, title in CATEGORY_ORDER:
            if key == seg:
                return title
        return seg.replace("_", " ").title()

    for page in pages:
        segs = page["segments"]
        # Ensure ancestor category node exists.
        top = segs[0]
        top_key = (top,)
        if top_key not in index:
            node = {
                "id": top,
                "title": category_title(top),
                "source": "base",
                "blocks": [],
                "children": [],
            }
            index[top_key] = node
            roots.append(node)

        if len(segs) == 1:
            # Landing page IS the category node — attach its blocks/title.
            node = index[top_key]
            node["title"] = page["title"]
            node["blocks"] = page["blocks"]
            continue

        # Ensure intermediate nodes (e.g. hiding/the_hider_deck) exist.
        parent = index[top_key]
        for depth in range(2, len(segs) + 1):
            key = tuple(segs[:depth])
            if key not in index:
                node = {
                    "id": "_".join(segs[:depth]),
                    "title": page["title"] if depth == len(segs) else segs[depth - 1].replace("_", " ").title(),
                    "source": "base",
                    "blocks": [],
                    "children": [],
                }
                index[key] = node
                parent["children"].append(node)
            if depth == len(segs):
                # Final segment: this is the actual page.
                index[key]["title"] = page["title"]
                index[key]["blocks"] = page["blocks"]
            parent = index[key]

    return roots


def find_section(sections: List[dict], predicate) -> Optional[dict]:
    for s in sections:
        if predicate(s):
            return s
        found = find_section(s.get("children", []), predicate)
        if found is not None:
            return found
    return None


def main() -> None:
    print("Crawling base rulebook...")
    pages = crawl_base()
    print(f"  {len(pages)} pages")

    print("Parsing expansion...")
    exp = parse_expansion()
    print(f"  {len(exp['curses'])} expansion curses, {len(exp['time_extras'])} time-extra blocks")

    tree = build_tree(pages)

    # Merge: Percentage Time Bonuses + Time Traps into the base Time Bonuses section.
    tb = find_section(tree, lambda s: s["title"].strip().lower() == "time bonuses")
    if tb is None:
        raise RuntimeError("Could not locate base 'Time Bonuses' section for merge")
    tb["blocks"].extend(exp["time_extras"])

    # Merge: expansion curses alphabetically into the base Curses section.
    curses = find_section(tree, lambda s: s["title"].strip().lower() == "curses")
    if curses is None:
        raise RuntimeError("Could not locate base 'Curses' section for merge")
    build_curses_section(curses, exp["curses"])

    doc = {"version": 1, "attribution": ATTRIBUTION, "sections": tree}
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(json.dumps(doc, ensure_ascii=False, indent=2), encoding="utf-8")
    size_kb = OUTPUT.stat().st_size / 1024
    print(f"Wrote {OUTPUT} ({size_kb:.1f} KB)")


if __name__ == "__main__":
    main()
