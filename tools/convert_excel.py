#!/usr/bin/env python3
"""Converts the "Serien" sheet of Serienliste.xlsx into the app's seed file.

Usage: python3 tools/convert_excel.py path/to/Serienliste.xlsx
Writes app/src/main/assets/seed/serien.json and prints a short report.
Requires: pip install openpyxl
"""
import collections
import datetime
import json
import pathlib
import sys

import openpyxl

COLUMNS = {
    "Status": "status",
    "fertig": "finished",
    "Serie": "title",
    "Wer": "who",
    "Staffel": "season",
    "Episode": "episode",
    "Special": "special",
    "Letzte Ausstrahlung": "lastAired",
    "ungesehen": "unseen",
    "nächste": "nextAired",
    "maxStaffel": "maxSeason",
    "maxEpisode": "maxEpisode",
    "maxSpecial": "maxSpecial",
    "Genre": "genre",
    "aktualisiert": "updated",
    "gesehen": "watched",
    "von": "total",
}
INT_FIELDS = {"status", "finished", "season", "episode", "special", "maxSeason",
              "maxEpisode", "maxSpecial", "watched", "total"}
DATE_FIELDS = {"lastAired", "nextAired", "updated"}
OUT = pathlib.Path(__file__).resolve().parent.parent / "app/src/main/assets/seed/serien.json"


def clean(field, value):
    if value is None or (isinstance(value, str) and not value.strip()):
        return None
    if field in DATE_FIELDS:
        if isinstance(value, datetime.datetime):
            return value.date().isoformat()
        return None
    if field in INT_FIELDS:
        try:
            return int(value)
        except (TypeError, ValueError):
            return None
    if field == "unseen":
        return str(value).strip().lower() == "x"
    if field == "title" and isinstance(value, float) and value.is_integer():
        value = int(value)
    return str(value).strip()


def main(path):
    ws = openpyxl.load_workbook(path, data_only=True)["Serien"]
    rows = ws.iter_rows(values_only=True)
    header = [str(h).strip() if h is not None else "" for h in next(rows)]
    index = {COLUMNS[h]: i for i, h in enumerate(header) if h in COLUMNS}
    missing = set(COLUMNS.values()) - set(index)
    if missing:
        sys.exit(f"Spalten fehlen im Blatt 'Serien': {sorted(missing)}")

    series = []
    for row in rows:
        entry = {f: clean(f, row[i]) for f, i in index.items()}
        if entry["title"]:
            series.append(entry)

    OUT.parent.mkdir(parents=True, exist_ok=True)
    # One series per line keeps the file compact and the git diffs readable.
    lines = ",\n".join(json.dumps(e, ensure_ascii=False, separators=(",", ":")) for e in series)
    OUT.write_text('{"version":1,"series":[\n' + lines + "\n]}\n", encoding="utf-8")

    def status(e):
        if e["finished"] == 1:
            return "COMPLETED"
        if e["status"] == 1:
            return "WATCHING"
        if e["status"] == 2:
            caught_up = e["watched"] is not None and e["watched"] > 0 and e["watched"] == e["total"]
            return "CAUGHT_UP" if caught_up else "PAUSED"
        return "PLAN_TO_WATCH"

    print(f"{len(series)} Serien -> {OUT}")
    print("Status:", dict(collections.Counter(status(e) for e in series)))
    print("Wer:", dict(collections.Counter(e["who"] for e in series)))


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    main(sys.argv[1])
