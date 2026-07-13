#!/usr/bin/env python3
"""Convert the Lake Michigan coastline WKT into a GeoJSON asset.

The Measuring -> Natural -> Coastline question ("are you closer to or farther
from the coastline than me?") needs the coastline as a single LineString the app
can buffer at runtime. The source is one WKT LINESTRING row.

Input:
  csv/coastline.csv        (columns: WKT,name,description; one LINESTRING row)

Output:
  app/src/main/assets/coastline.geojson
    FeatureCollection with a single LineString feature (property `name`).

Run:
  tools/.venv/bin/python tools/build_coastline.py

Requires shapely (pip install shapely).
"""
from __future__ import annotations

import csv
import json
import os

from shapely import wkt
from shapely.geometry import mapping

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
ASSETS_DIR = os.path.join(ROOT, "app", "src", "main", "assets")
INPUT = os.path.join(ROOT, "csv", "coastline.csv")
OUTPUT = os.path.join(ASSETS_DIR, "coastline.geojson")


def main() -> None:
    with open(INPUT, "r", encoding="utf-8") as fh:
        rows = list(csv.DictReader(fh))
    if not rows:
        raise SystemExit("coastline.csv has no data rows")

    row = rows[0]
    geom = wkt.loads(row["WKT"])
    name = (row.get("name") or "").strip() or "Lake Michigan coastline"

    feature = {
        "type": "Feature",
        "properties": {"name": name},
        "geometry": mapping(geom),
    }
    out = {"type": "FeatureCollection", "features": [feature]}

    os.makedirs(ASSETS_DIR, exist_ok=True)
    with open(OUTPUT, "w", encoding="utf-8") as fh:
        json.dump(out, fh, separators=(",", ":"))

    coords = len(geom.coords) if geom.geom_type == "LineString" else 0
    print(f"coastline written: {name} ({coords} vertices)")
    print(f"output: {OUTPUT} ({os.path.getsize(OUTPUT) / 1e3:.1f} KB)")


if __name__ == "__main__":
    main()
