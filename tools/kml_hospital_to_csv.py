#!/usr/bin/env python3
"""Convert utilities_hospital.kml (Polygon/MultiGeometry placemarks) to a named-point CSV.

Each hospital may be a single Polygon or a MultiGeometry of Polygons. We union
all polygons per placemark and use the combined centroid as the representative point.

Input:  kml/utilities_hospital.kml
Output: csv/utilities-hospital.csv  (columns: name,latitude,longitude)

Run:
  python tools/kml_hospital_to_csv.py
  python tools/build_point_regions.py csv/utilities-hospital.csv
"""
from __future__ import annotations

import csv
import os
import xml.etree.ElementTree as ET

from shapely.geometry import Polygon
from shapely.ops import unary_union

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
KML_PATH = os.path.join(ROOT, "kml", "utilities_hospital.kml")
OUT_PATH = os.path.join(ROOT, "csv", "utilities-hospital.csv")

KML_NS = "{http://www.opengis.net/kml/2.2}"


def parse_coords(text: str) -> list[tuple[float, float]]:
    pts = []
    for token in text.split():
        parts = token.split(",")
        if len(parts) >= 2:
            pts.append((float(parts[0]), float(parts[1])))
    return pts


def polygons_from_placemark(pm: ET.Element) -> list[Polygon]:
    polys = []
    for coords_el in pm.findall(f".//{KML_NS}coordinates"):
        pts = parse_coords(coords_el.text or "")
        if len(pts) >= 3:
            polys.append(Polygon(pts))
    return polys


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

        polys = polygons_from_placemark(pm)
        if not polys:
            skipped += 1
            continue

        centroid = unary_union(polys).centroid
        rows.append((name, centroid.y, centroid.x))

    rows.sort(key=lambda r: r[0])

    os.makedirs(os.path.dirname(OUT_PATH), exist_ok=True)
    with open(OUT_PATH, "w", newline="", encoding="utf-8") as fh:
        writer = csv.writer(fh)
        writer.writerow(["name", "latitude", "longitude"])
        for name, lat, lon in rows:
            writer.writerow([name, f"{lat:.8f}", f"{lon:.8f}"])

    print(f"Wrote {len(rows)} hospitals to {OUT_PATH}  ({skipped} skipped)")


if __name__ == "__main__":
    main()
