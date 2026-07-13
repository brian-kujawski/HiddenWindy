#!/usr/bin/env python3
"""Convert utilities_consulates.kml (Polygon/MultiGeometry placemarks) to a named-point CSV.

Input:  kml/utilities_consulates.kml
Output: csv/utilities-consulates.csv  (columns: name,latitude,longitude)

Run:
  python tools/kml_consulates_to_csv.py
  python tools/build_point_regions.py csv/utilities-consulates.csv
"""
from __future__ import annotations

import csv
import os
import xml.etree.ElementTree as ET

from shapely.geometry import Polygon
from shapely.ops import unary_union

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
KML_PATH = os.path.join(ROOT, "kml", "utilities_consulates.kml")
OUT_PATH = os.path.join(ROOT, "csv", "utilities-consulates.csv")

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

        # Prefer polygon centroid; fall back to Point coordinates.
        polys = []
        for coords_el in pm.findall(f".//{KML_NS}coordinates"):
            pts = parse_coords(coords_el.text or "")
            if len(pts) >= 3:
                polys.append(Polygon(pts))

        if polys:
            centroid = unary_union(polys).centroid
            rows.append((name, centroid.y, centroid.x))
            continue

        pt_el = pm.find(f".//{KML_NS}Point/{KML_NS}coordinates")
        if pt_el is not None and pt_el.text:
            parts = pt_el.text.strip().split(",")
            if len(parts) >= 2:
                rows.append((name, float(parts[1]), float(parts[0])))
                continue

        skipped += 1

    rows.sort(key=lambda r: r[0])

    os.makedirs(os.path.dirname(OUT_PATH), exist_ok=True)
    with open(OUT_PATH, "w", newline="", encoding="utf-8") as fh:
        writer = csv.writer(fh)
        writer.writerow(["name", "latitude", "longitude"])
        for name, lat, lon in rows:
            writer.writerow([name, f"{lat:.8f}", f"{lon:.8f}"])

    print(f"Wrote {len(rows)} consulates to {OUT_PATH}  ({skipped} skipped)")


if __name__ == "__main__":
    main()
