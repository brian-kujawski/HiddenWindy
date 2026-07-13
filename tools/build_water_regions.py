#!/usr/bin/env python3
"""Precompute named "body of water" polygons for the Measuring question.

The Measuring -> Natural -> Body of Water question asks "compared to me, are you
closer to or farther from a body of water?". Unlike the point-based POI datasets
(airports, museums, …) the water source is a large polygon dataset, so we build
actual water-body shapes (not a Voronoi partition) dissolved by name.

Input:
  geojson/waterbodies.geojson
    Cook County water bodies (MultiPolygon features). Names live in the
    `anno_name` property; ~77% are "UNKNOWN" and are dropped. A single logical
    body is split across many polygon fragments, so we dissolve by name.

Output:
  app/src/main/assets/natural_water_regions.geojson
    FeatureCollection, one Polygon/MultiPolygon feature per named body, with a
    `name` property. Matches the shape the app's PointRegionsRepository.loadRegions
    expects, so the runtime can load it exactly like the POI region files.

Method:
  1. Read features, keep only named bodies (drop UNKNOWN / UNKOWN typo / blank).
  2. Group by name, dissolve each name's fragments into one geometry.
  3. Clip to the play bounds, simplify (Douglas-Peucker) to keep the asset small.
  4. Optionally drop bodies below MIN_AREA_M2 (set to 0 = keep all named bodies).
  5. Write one GeoJSON feature per name.

Run:
  tools/.venv/bin/python tools/build_water_regions.py

Requires shapely (pip install shapely).
"""
from __future__ import annotations

import json
import math
import os

from shapely.geometry import box, mapping, shape
from shapely.ops import transform, unary_union
from shapely import make_valid

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
ASSETS_DIR = os.path.join(ROOT, "app", "src", "main", "assets")
INPUT = os.path.join(ROOT, "geojson", "waterbodies.geojson")
OUTPUT = os.path.join(ASSETS_DIR, "natural_water_regions.geojson")

# Must match ZoneGeometry.CHICAGO_BOUNDS in the app.
BOUNDS_WEST = -88.90
BOUNDS_SOUTH = 41.20
BOUNDS_EAST = -87.20
BOUNDS_NORTH = 42.80

# Douglas-Peucker tolerance in degrees (~0.0001 deg ~= 11 m). Keeps the dissolved
# geometry near ~1 MB while preserving city-scale shape for distance shading.
SIMPLIFY_TOL_DEG = 0.0001

# Minimum dissolved area to keep, in square metres. 0 keeps every named body.
MIN_AREA_M2 = 0.0

# Names to treat as unnamed (the source has an "UNKNOWN" plus a misspelt "UNKOWN").
UNNAMED = {"", "UNKNOWN", "UNKOWN"}

_M_PER_DEG_LAT = 110_540.0


def area_m2(geom) -> float:
    """Approximate planar area in m^2 via an equirectangular projection at the centroid."""
    c = geom.centroid
    m_per_deg_lon = 111_320.0 * math.cos(math.radians(c.y))
    projected = transform(
        lambda xs, ys, z=None: ([x * m_per_deg_lon for x in xs], [y * _M_PER_DEG_LAT for y in ys]),
        geom,
    )
    return projected.area


def main() -> None:
    with open(INPUT, "r", encoding="utf-8") as fh:
        data = json.load(fh)

    bounds = box(BOUNDS_WEST, BOUNDS_SOUTH, BOUNDS_EAST, BOUNDS_NORTH)

    by_name: dict[str, list] = {}
    for feature in data.get("features", []):
        name = (feature.get("properties", {}).get("anno_name") or "").strip()
        if name.upper() in UNNAMED:
            continue
        geom = shape(feature["geometry"])
        if geom.is_empty:
            continue
        if not geom.is_valid:
            geom = make_valid(geom)
        by_name.setdefault(name, []).append(geom)

    out_features = []
    dropped_small = 0
    for name in sorted(by_name):
        dissolved = unary_union(by_name[name])
        clipped = dissolved.intersection(bounds)
        if clipped.is_empty:
            continue
        simplified = clipped.simplify(SIMPLIFY_TOL_DEG, preserve_topology=True)
        if simplified.is_empty:
            continue
        if MIN_AREA_M2 > 0 and area_m2(simplified) < MIN_AREA_M2:
            dropped_small += 1
            continue
        out_features.append(
            {
                "type": "Feature",
                "properties": {"name": name},
                "geometry": mapping(simplified),
            }
        )

    out = {"type": "FeatureCollection", "features": out_features}
    os.makedirs(ASSETS_DIR, exist_ok=True)
    with open(OUTPUT, "w", encoding="utf-8") as fh:
        json.dump(out, fh, separators=(",", ":"))

    size_mb = os.path.getsize(OUTPUT) / 1e6
    print(f"named bodies written: {len(out_features)}")
    if dropped_small:
        print(f"dropped below {MIN_AREA_M2:.0f} m^2: {dropped_small}")
    print(f"output: {OUTPUT} ({size_mb:.2f} MB)")


if __name__ == "__main__":
    main()
