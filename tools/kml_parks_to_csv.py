#!/usr/bin/env python3
"""Convert the Cook County parks KML to a named-point CSV for build_point_regions.py.

The input KML stores each park as a Point placemark whose name lives in
ExtendedData → <Data name="cfname"> rather than the <name> element.

Input:
  kml/natural_parks.kml

Output:
  csv/natural_park.csv  (columns: name,latitude,longitude)

Duplicate names are merged by averaging their coordinates (so each unique
park name ends up with exactly one representative point, which is what the
Voronoi builder expects).

Run:
  python tools/kml_parks_to_csv.py
"""
from __future__ import annotations

import csv
import os
import xml.etree.ElementTree as ET
from collections import defaultdict

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
KML_PATH = os.path.join(ROOT, "kml", "natural_parks.kml")
OUT_PATH = os.path.join(ROOT, "csv", "natural_park.csv")

KML_NS = "{http://www.opengis.net/kml/2.2}"


def main() -> None:
    tree = ET.parse(KML_PATH)
    root = tree.getroot()

    # name → list of (lon, lat) pairs
    by_name: dict[str, list[tuple[float, float]]] = defaultdict(list)
    skipped = 0

    for pm in root.iter(f"{KML_NS}Placemark"):
        # Name comes from ExtendedData/Data[@name='cfname']/value
        cfname = ""
        ext = pm.find(f"{KML_NS}ExtendedData")
        if ext is not None:
            for d in ext.findall(f"{KML_NS}Data"):
                if d.get("name") == "cfname":
                    v = d.find(f"{KML_NS}value")
                    if v is not None and v.text:
                        cfname = v.text.strip()
                    break

        if not cfname:
            skipped += 1
            continue

        # Coordinates come from Point/coordinates (lon,lat[,alt])
        coords_el = pm.find(f".//{KML_NS}Point/{KML_NS}coordinates")
        if coords_el is None or not coords_el.text:
            skipped += 1
            continue
        parts = coords_el.text.strip().split(",")
        if len(parts) < 2:
            skipped += 1
            continue
        try:
            lon, lat = float(parts[0]), float(parts[1])
        except ValueError:
            skipped += 1
            continue

        by_name[cfname].append((lon, lat))

    # Merge duplicates by centroid
    rows: list[tuple[str, float, float]] = []
    for name, pts in by_name.items():
        avg_lon = sum(p[0] for p in pts) / len(pts)
        avg_lat = sum(p[1] for p in pts) / len(pts)
        rows.append((name, avg_lat, avg_lon))

    # Sort by name for stable output
    rows.sort(key=lambda r: r[0])

    os.makedirs(os.path.dirname(OUT_PATH), exist_ok=True)
    with open(OUT_PATH, "w", newline="", encoding="utf-8") as fh:
        writer = csv.writer(fh)
        writer.writerow(["name", "latitude", "longitude"])
        for name, lat, lon in rows:
            writer.writerow([name, f"{lat:.8f}", f"{lon:.8f}"])

    print(f"Wrote {len(rows)} parks to {OUT_PATH}  ({skipped} placemarks skipped)")


if __name__ == "__main__":
    main()
