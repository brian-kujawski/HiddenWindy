#!/usr/bin/env python3
"""Build csv/station_name_length.csv for the Station's Name Length Matching question.

Each row represents one station point, but the 'name' column holds the count
of alphabetic characters in the station's name rather than the station name
itself.  When build_point_regions.py processes this CSV it naturally unions
every station's Voronoi cell that shares the same count, producing one region
per distinct alphabetic-character count.

Input:
  app/src/main/assets/stations.geojson

Output:
  csv/station_name_length.csv  (columns: name,latitude,longitude)
  where 'name' is an integer string like "6", "7", etc.

Run:
  python tools/build_station_name_length_csv.py
  python tools/build_point_regions.py csv/station_name_length.csv
"""
from __future__ import annotations

import csv
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
STATIONS_PATH = os.path.join(ROOT, "app", "src", "main", "assets", "stations.geojson")
OUT_PATH = os.path.join(ROOT, "csv", "station_name_length.csv")


def main() -> None:
    with open(STATIONS_PATH, encoding="utf-8") as fh:
        gj = json.load(fh)

    rows: list[tuple[str, float, float]] = []
    for feat in gj["features"]:
        name: str = feat["properties"].get("name", "")
        alpha_count = sum(1 for c in name if c.isalnum())
        coords = feat["geometry"]["coordinates"]  # [lon, lat]
        lon, lat = float(coords[0]), float(coords[1])
        rows.append((str(alpha_count), lat, lon))

    os.makedirs(os.path.dirname(OUT_PATH), exist_ok=True)
    with open(OUT_PATH, "w", newline="", encoding="utf-8") as fh:
        writer = csv.writer(fh)
        writer.writerow(["name", "latitude", "longitude"])
        for name, lat, lon in rows:
            writer.writerow([name, f"{lat:.8f}", f"{lon:.8f}"])

    # Summary
    from collections import Counter
    counts = Counter(r[0] for r in rows)
    print(f"Wrote {len(rows)} stations to {OUT_PATH}")
    print("Alpha-length distribution:")
    for length, n in sorted(counts.items(), key=lambda x: int(x[0])):
        print(f"  {length:>3} letters: {n} stations")


if __name__ == "__main__":
    main()
