#!/usr/bin/env python3
"""Checks the translations against the source strings.

Usage: scripts/check-strings.py [lang ...]        (default: every values-* folder)

For each language it reports keys that are missing, keys that do not exist in the source,
placeholder mismatches (%1$s, %d, ...), unescaped apostrophes, and strings identical to the
English source. Exit code 1 when a missing key or a placeholder/escape error is found.
"""
import glob
import os
import re
import sys

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "res")
STRING_RE = re.compile(r'<string name="([^"]+)"([^>]*)>(.*?)</string>', re.S)
PLACEHOLDER_RE = re.compile(r"%(\d+\$)?[sdf]")
APOSTROPHE_RE = re.compile(r"(?<!\\)'")


def load(path):
    out = {}
    nontranslatable = set()
    with open(path, encoding="utf-8") as f:
        for name, attrs, text in STRING_RE.findall(f.read()):
            out[name] = text
            if 'translatable="false"' in attrs:
                nontranslatable.add(name)
    return out, nontranslatable


def main():
    source, nontranslatable = load(os.path.join(ROOT, "values", "strings.xml"))
    keys = [k for k in source if k not in nontranslatable]
    langs = sys.argv[1:] or sorted(
        os.path.basename(d)[len("values-"):] for d in glob.glob(os.path.join(ROOT, "values-*"))
        if os.path.exists(os.path.join(d, "strings.xml"))
    )
    failed = False
    for lang in langs:
        path = os.path.join(ROOT, f"values-{lang}", "strings.xml")
        if not os.path.exists(path):
            print(f"{lang}: no strings.xml")
            continue
        target, _ = load(path)
        missing = [k for k in keys if k not in target]
        extra = [k for k in target if k not in source]
        placeholder = [k for k in keys if k in target and
                       sorted(PLACEHOLDER_RE.findall(source[k])) != sorted(PLACEHOLDER_RE.findall(target[k]))]
        escape = [k for k in target if APOSTROPHE_RE.search(target[k]) and "<![CDATA[" not in target[k]]
        same = [k for k in keys if k in target and target[k].strip() == source[k].strip()]
        print(f"{lang:8s} translated {len(keys) - len(missing):4d}/{len(keys)}  missing {len(missing):3d}  "
              f"extra {len(extra):3d}  placeholder {len(placeholder):2d}  escape {len(escape):2d}  same-as-English {len(same):3d}")
        for label, items in (("missing", missing), ("extra", extra), ("placeholder", placeholder), ("escape", escape)):
            if items and ("-v" in os.environ.get("CHECK_STRINGS_FLAGS", "") or len(langs) == 1):
                print(f"    {label}: {', '.join(items)}")
        if missing or placeholder or escape:
            failed = True
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
