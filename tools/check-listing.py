#!/usr/bin/env python3
"""Checks the store listing against what RuStore will accept.

    python3 tools/check-listing.py

Reads the fenced blocks out of store/listing-rustore.md and measures them. The
limits are small enough that a sentence added in the console rather than here
can silently overrun one, and the console tells you by refusing to save after
you have already written the paragraph.

Also catches the content rules that are mechanical: superlatives and the other
clickbait words the moderation rules name outright, emoji, and links where they
are not allowed. The rules that need judgement — promises about health, claims
about results — are in the listing's own closing section, for a person to check.
"""

import os
import re
import sys

LISTING = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                       "..", "store", "listing-rustore.md")

# field heading -> (limit, is it name-or-short-description)
LIMITS = {
    "Название": (30, True),
    "Краткое описание": (80, True),
    "Полное описание": (4000, False),
    "Что нового": (None, False),
}

# Named in the moderation rules, plus the obvious inflections. Matched on word
# boundaries so «самый» does not fire on «самостоятельно».
CLICKBAIT = r"лучш|единствен|запрещённ|запрещенн|секретн|самый|самая|самое|№\s*1"


def blocks(text):
    """Every `## heading` with the first fenced block under it."""
    found = {}
    for heading, body in re.findall(r"^## (.+?)$\n(.*?)(?=^## |\Z)",
                                    text, re.M | re.S):
        fence = re.search(r"^```\n(.*?)^```", body, re.M | re.S)
        if fence:
            found[heading.strip()] = fence.group(1).rstrip("\n")
    return found


def main():
    with open(LISTING, encoding="utf-8") as f:
        found = blocks(f.read())

    ok = True

    def fail(msg):
        nonlocal ok
        print(f"FAIL {msg}", file=sys.stderr)
        ok = False

    for field, (limit, strict) in LIMITS.items():
        if field not in found:
            fail(f"«{field}»: no fenced block under that heading")
            continue
        value = found[field]
        length = len(value)
        if limit is None:
            print(f"ok    «{field}»: {length} символов (лимит не объявлен)")
        elif length > limit:
            fail(f"«{field}»: {length} символов, лимит {limit}")
        else:
            print(f"ok    «{field}»: {length} из {limit} символов")

        if re.search(CLICKBAIT, value, re.I):
            fail(f"«{field}»: кликбейт — {re.search(CLICKBAIT, value, re.I).group(0)}")
        if any(ord(c) > 0x2100 for c in value):
            bad = next(c for c in value if ord(c) > 0x2100)
            fail(f"«{field}»: эмодзи или спецсимвол U+{ord(bad):04X} ({bad})")
        if strict and re.search(r"https?://|www\.", value):
            fail(f"«{field}»: ссылка, здесь они запрещены")

    tags = found.get("Теги поиска (до 5)", "").strip().splitlines()
    if not 1 <= len(tags) <= 5:
        fail(f"тегов {len(tags)}, допустимо от 1 до 5")
    else:
        print(f"ok    тегов: {len(tags)} из 5")

    for field in ("Контакты разработчика", "Политика конфиденциальности"):
        value = found.get(field, "")
        if "ЗАПОЛНИТЬ" in value:
            print(f"TODO  «{field}»: {value.strip()}", file=sys.stderr)

    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
