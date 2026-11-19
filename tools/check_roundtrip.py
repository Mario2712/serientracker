#!/usr/bin/env python3
"""Checks that the app's Excel export reproduces the original list.

Usage: python3 tools/check_roundtrip.py expected.json actual.json
Both files are made by tools/convert_excel.py: expected from the original Excel
(app/src/main/assets/seed/serien.json), actual from the app's export of that seed.
Exits with 1 and lists the differences if any field of any series differs.
"""
import json
import sys


def load(path):
    with open(path, encoding="utf-8") as f:
        return json.load(f)["series"]


def main(expected_path, actual_path):
    expected, actual = load(expected_path), load(actual_path)
    problems = []
    if len(expected) != len(actual):
        problems.append(f"Anzahl: erwartet {len(expected)}, Export {len(actual)}")

    by_title = {}
    for row in actual:
        by_title.setdefault(row["title"], []).append(row)
    for row in expected:
        candidates = by_title.get(row["title"], [])
        if not candidates:
            problems.append(f"fehlt im Export: {row['title']}")
            continue
        got = candidates.pop(0)
        for key in sorted(set(row) | set(got)):
            if row.get(key) != got.get(key):
                problems.append(f"{row['title']}: {key} erwartet {row.get(key)!r}, Export {got.get(key)!r}")

    same_order = [r["title"] for r in expected] == [r["title"] for r in actual]
    print(f"{len(actual)} Serien verglichen, Reihenfolge {'gleich' if same_order else 'abweichend'}")
    if problems:
        print(f"{len(problems)} Abweichungen:")
        for p in problems[:100]:
            print("  " + p)
        sys.exit(1)
    print("Round-Trip OK: Export entspricht der Original-Excel.")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    main(sys.argv[1], sys.argv[2])
