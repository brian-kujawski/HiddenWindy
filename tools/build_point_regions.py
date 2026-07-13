#!/usr/bin/env python3
"""Precompute "nearest named point" Voronoi partitions for Matching questions.

For any Matching question backed by a CSV of named point features (airports,
parks, museums, …) we build a Voronoi partition offline so the Android app can
shade the map without doing spatial math at runtime.

Input:
  csv/<dataset>.csv        (columns: name,latitude,longitude)

Output:
  app/src/main/assets/<dataset>_regions.geojson
    FeatureCollection, one Polygon/MultiPolygon feature per unique name. The
    geometry is the area of the play bounds that is nearest to that point.

Method:
  1. Read the named points from the CSV.
  2. Convert to a local metric projection so distances are uniform.
  3. Build a Voronoi diagram; each cell is nearest to its generating point.
  4. Clip every cell to the play bounds, project back to lon/lat, simplify.
  5. Write one GeoJSON feature per name.

Run:
  python tools/build_point_regions.py csv/transit_airport.csv

Requires shapely + scipy (pip install shapely scipy).
"""
from __future__ import annotations

import csv
import json
import math
import os
import sys
from typing import NamedTuple

from shapely.geometry import MultiPoint, box, mapping, shape
from shapely.ops import unary_union, voronoi_diagram
from shapely.strtree import STRtree

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
ASSETS_DIR = os.path.join(ROOT, "app", "src", "main", "assets")

# Must match ZoneGeometry.CHICAGO_BOUNDS in the app.
BOUNDS_WEST = -88.90
BOUNDS_SOUTH = 41.20
BOUNDS_EAST = -87.20
BOUNDS_NORTH = 42.80

# Douglas-Peucker tolerance for the output polygons, in metres.
SIMPLIFY_TOL_M = 25.0

# Local equirectangular projection centred on the play area.
_LAT0 = (BOUNDS_SOUTH + BOUNDS_NORTH) / 2.0
_M_PER_DEG_LAT = 111_320.0
_M_PER_DEG_LON = 111_320.0 * math.cos(math.radians(_LAT0))


def to_m(lon: float, lat: float) -> tuple[float, float]:
    return (lon * _M_PER_DEG_LON, lat * _M_PER_DEG_LAT)


def from_m(x: float, y: float) -> tuple[float, float]:
    return (x / _M_PER_DEG_LON, y / _M_PER_DEG_LAT)


class NamedPoint(NamedTuple):
    name: str
    lon: float
    lat: float


def load_csv(path: str) -> list[NamedPoint]:
    points: list[NamedPoint] = []
    with open(path, newline="", encoding="utf-8") as fh:
        reader = csv.DictReader(fh)
        for row in reader:
            points.append(NamedPoint(
                name=row["name"].strip(),
                lon=float(row["longitude"]),
                lat=float(row["latitude"]),
            ))
    return points


def _map_coords(gj: dict, fn):
    """Recursively apply fn to every (x, y) pair in a GeoJSON geometry mapping."""
    def walk(node):
        if isinstance(node[0], (int, float)):
            return list(fn(node[0], node[1]))
        return [walk(child) for child in node]
    return {"type": gj["type"], "coordinates": walk(gj["coordinates"])}


def _project_from_m(geom):
    return shape(_map_coords(mapping(geom), lambda x, y: from_m(x, y)))


def main(csv_path: str) -> None:
    stem = os.path.splitext(os.path.basename(csv_path))[0]
    out_path = os.path.join(ASSETS_DIR, f"{stem}_regions.geojson")

    points = load_csv(csv_path)
    if len(points) < 2:
        raise SystemExit(f"Need at least 2 points in {csv_path}; got {len(points)}.")

    print(f"{len(points)} named points: {[p.name for p in points]}")

    bounds_ll = box(BOUNDS_WEST, BOUNDS_SOUTH, BOUNDS_EAST, BOUNDS_NORTH)
    bx0, by0 = to_m(BOUNDS_WEST, BOUNDS_SOUTH)
    bx1, by1 = to_m(BOUNDS_EAST, BOUNDS_NORTH)
    bounds_m = box(bx0, by0, bx1, by1)

    sites_m = [to_m(p.lon, p.lat) for p in points]
    site_geom = MultiPoint(sites_m)

    print("Building Voronoi diagram...")
    cells = voronoi_diagram(site_geom, envelope=bounds_m)

    # Map each Voronoi cell to its generating named point via nearest-site lookup.
    site_shapes = list(site_geom.geoms)
    site_tree = STRtree(site_shapes)

    cells_by_name: dict[str, list] = {p.name: [] for p in points}
    for cell in cells.geoms:
        clipped = cell.intersection(bounds_m)
        if clipped.is_empty:
            continue
        idxs = site_tree.query(cell, predicate="contains")
        if len(idxs) == 0:
            idxs = [site_tree.nearest(cell.representative_point())]
        name = points[int(idxs[0])].name
        cells_by_name[name].append(clipped)

    simplify_deg = SIMPLIFY_TOL_M / _M_PER_DEG_LAT
    features = []
    for name, polys in cells_by_name.items():
        if not polys:
            print(f"  WARNING: no cell for '{p.name}' — skipped")
            continue
        region_m = unary_union(polys)
        region_ll = _project_from_m(region_m).intersection(bounds_ll)
        if region_ll.is_empty:
            continue
        region_ll = region_ll.simplify(simplify_deg, preserve_topology=True)
        features.append({
            "type": "Feature",
            "properties": {"name": name},
            "geometry": mapping(region_ll),
        })
        print(f"  {name}: {region_ll.geom_type}, area~{region_ll.area:.4f} deg^2")

    out = {"type": "FeatureCollection", "features": features}
    with open(out_path, "w", encoding="utf-8") as fh:
        json.dump(out, fh, separators=(",", ":"))
    size_kb = os.path.getsize(out_path) / 1024
    print(f"Wrote {out_path} ({len(features)} regions, {size_kb:.1f} KB)")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit(f"Usage: python {sys.argv[0]} csv/<dataset>.csv")
    main(sys.argv[1])
