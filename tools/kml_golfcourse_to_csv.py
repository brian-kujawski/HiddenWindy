#!/usr/bin/env python3
"""Convert poi_golfcourse.kml (Polygon placemarks) to a named-point CSV.

Input:  kml/poi_golfcourse.kml
Output: csv/poi-golfcourse.csv  (columns: name,latitude,longitude)

Run:
  python tools/kml_golfcourse_to_csv.py
  python tools/build_point_regions.py csv/poi-golfcourse.csv
"""
from __future__ import annotations

import csv
import os
import xml.etree.ElementTree as ET

from shapely.geometry import Polygon

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
KML_PATH = os.path.join(ROOT, "kml", "poi_golfcourse.kml")
OUT_PATH = os.path.join(ROOT, "csv", "poi-golfcourse.csv")

KML_NS = "{http://www.opengis.net/kml/2.2}"


def parse_coords(text: str) -> list[tuple[float, float]]:
    pts = []
    for token in text.split():
        parts = token.split(",")
        if len(parts) >= 2:
            pts.append((float(parts[0]), float(parts[1])))
    return pts


def main() -> None:
    tree = ET.parse(KML_PATH)
    root = tree.getroot()

    rows: list[tuple[str, float, float]] = []
    skipped = 0

    for pm in root.iter(f"{KML_NS}Placemark"):
        name_el = pm.find(f"{KML_NS}name")
        name = name_el.text.strip() if name_el is not None and name_el.text else ""
        if not name:
            skipped += 1
            continue

        coords_el = pm.find(f".//{KML_NS}coordinates")
        if coords_el is None or not coords_el.text:
            skipped += 1
            continue

        pts = parse_coords(coords_el.text)
        if len(pts) < 3:
            skipped += 1
            continue

        centroid = Polygon(pts).centroid
        rows.append((name, centroid.y, centroid.x))

    rows.sort(key=lambda r: r[0])

    os.makedirs(os.path.dirname(OUT_PATH), exist_ok=True)
    with open(OUT_PATH, "w", newline="", encoding="utf-8") as fh:
        writer = csv.writer(fh)
        writer.writerow(["name", "latitude", "longitude"])
        for name, lat, lon in rows:
            writer.writerow([name, f"{lat:.8f}", f"{lon:.8f}"])

    print(f"Wrote {len(rows)} golf courses to {OUT_PATH}  ({skipped} skipped)")


if __name__ == "__main__":
    main()
