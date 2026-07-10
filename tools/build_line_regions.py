#!/usr/bin/env python3
"""Precompute the "nearest transit line" partition of the play area.

For the Matching question ("is your nearest line the same as mine?") we need to
shade the map by which named line is closest. The lines never move, so we bake
this offline and ship the result as a bundled asset.

Input:
  app/src/main/assets/transit_lines.geojson  (LineString features: name, color)

Output:
  app/src/main/assets/line_regions.geojson
    FeatureCollection, one (Multi)Polygon feature per line NAME. The geometry is
    the area of the play bounds where that line is the nearest line. Regions may
    OVERLAP where lines share track (Option A): a location on shared rails counts
    as nearest for every line running there.

Method:
  1. Group LineStrings by name (branches collapse into one line).
  2. Densify every segment into sample points (~SAMPLE_SPACING_M apart) in a
     local metric projection so distances are even.
  3. De-duplicate co-located samples, remembering the SET of line names present
     at each unique location (this is what makes shared track belong to many
     lines at once).
  4. Build a Voronoi diagram over the unique sample points. Each cell is nearest
     to its generating sample; label the cell with that sample's name set.
  5. For each line name, union all cells whose label set contains it, clip to the
     play bounds, simplify, and emit one feature.

Run:
  tools/.venv/bin/python tools/build_line_regions.py

Requires shapely + scipy (installed in tools/.venv).
"""
from __future__ import annotations

import json
import math
import os
from collections import defaultdict

from shapely.geometry import MultiPoint, Polygon, box, mapping, shape
from shapely.ops import unary_union, voronoi_diagram
from shapely.strtree import STRtree

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
ASSETS_DIR = os.path.join(ROOT, "app", "src", "main", "assets")
LINES_PATH = os.path.join(ASSETS_DIR, "transit_lines.geojson")
OUT_PATH = os.path.join(ASSETS_DIR, "line_regions.geojson")

# Must match ZoneGeometry.CHICAGO_BOUNDS in the app.
BOUNDS_WEST = -88.90
BOUNDS_SOUTH = 41.20
BOUNDS_EAST = -87.20
BOUNDS_NORTH = 42.80

# Sample spacing along each line, in metres. Smaller = crisper region borders and
# a bigger file. ~30 m keeps Voronoi cells well under a city block.
SAMPLE_SPACING_M = 30.0

# Douglas-Peucker tolerance for the output polygons, in metres. ~25 m removes
# Voronoi staircase noise while staying visually faithful at play zoom.
SIMPLIFY_TOL_M = 25.0

# Rounding used to decide two samples are "the same place" (metres).
DEDUP_SNAP_M = 5.0

# Local equirectangular projection centred on the play area.
_LAT0 = (BOUNDS_SOUTH + BOUNDS_NORTH) / 2.0
_M_PER_DEG_LAT = 111_320.0
_M_PER_DEG_LON = 111_320.0 * math.cos(math.radians(_LAT0))


def to_m(lon: float, lat: float) -> tuple[float, float]:
    return (lon * _M_PER_DEG_LON, lat * _M_PER_DEG_LAT)


def from_m(x: float, y: float) -> tuple[float, float]:
    return (x / _M_PER_DEG_LON, y / _M_PER_DEG_LAT)


def densify(coords_m: list[tuple[float, float]], spacing: float) -> list[tuple[float, float]]:
    """Return points along the polyline [coords_m] at ~[spacing] metre intervals."""
    if not coords_m:
        return []
    out: list[tuple[float, float]] = [coords_m[0]]
    for (x0, y0), (x1, y1) in zip(coords_m, coords_m[1:]):
        seg = math.hypot(x1 - x0, y1 - y0)
        if seg == 0:
            continue
        steps = max(1, int(seg // spacing))
        for i in range(1, steps + 1):
            t = i / steps
            out.append((x0 + (x1 - x0) * t, y0 + (y1 - y0) * t))
    return out


def main() -> None:
    with open(LINES_PATH, encoding="utf-8") as fh:
        data = json.load(fh)

    # 1 + 2: densify every line's segments into metric sample points, per name.
    labels_by_point: dict[tuple[int, int], set[str]] = defaultdict(set)
    snap = DEDUP_SNAP_M
    total_features = 0
    for feat in data["features"]:
        geom = feat.get("geometry") or {}
        if geom.get("type") != "LineString":
            continue
        name = (feat.get("properties") or {}).get("name")
        if not name:
            continue
        total_features += 1
        coords_m = [to_m(lon, lat) for lon, lat in geom["coordinates"]]
        for x, y in densify(coords_m, SAMPLE_SPACING_M):
            # 3: snap to a grid so co-located samples on shared track collapse to
            # one location carrying every line name present there.
            key = (round(x / snap), round(y / snap))
            labels_by_point[key].add(name)

    if not labels_by_point:
        raise SystemExit("No LineString features found; nothing to do.")

    keys = list(labels_by_point.keys())
    points_m = [(kx * snap, ky * snap) for kx, ky in keys]
    label_sets = [labels_by_point[k] for k in keys]
    names = sorted({n for s in label_sets for n in s})
    print(
        f"{total_features} line features -> {len(points_m)} unique samples "
        f"across {len(names)} names"
    )

    # 4: Voronoi over the unique samples, extended to (and later clipped by) the
    # play bounds in metres.
    bounds_ll = box(BOUNDS_WEST, BOUNDS_SOUTH, BOUNDS_EAST, BOUNDS_NORTH)
    bx0, by0 = to_m(BOUNDS_WEST, BOUNDS_SOUTH)
    bx1, by1 = to_m(BOUNDS_EAST, BOUNDS_NORTH)
    bounds_m = box(bx0, by0, bx1, by1)

    site_geom = MultiPoint(points_m)
    print("Building Voronoi diagram...")
    cells = voronoi_diagram(site_geom, envelope=bounds_m)

    # Map each Voronoi cell back to its generating sample (exactly one per cell).
    site_points = [pt for pt in site_geom.geoms]
    site_tree = STRtree(site_points)

    cells_by_name: dict[str, list[Polygon]] = defaultdict(list)
    for cell in cells.geoms:
        clipped = cell.intersection(bounds_m)
        if clipped.is_empty:
            continue
        # The single site strictly inside this cell.
        idxs = site_tree.query(cell, predicate="contains")
        if len(idxs) == 0:
            # Fall back to nearest site if containment is numerically fuzzy.
            idxs = [site_tree.nearest(cell.representative_point())]
        label_set = label_sets[int(idxs[0])]
        for nm in label_set:
            cells_by_name[nm].append(clipped)

    # 5: union per name, project back to lon/lat, simplify, and write out.
    print("Dissolving cells per line...")
    features = []
    simplify_deg = SIMPLIFY_TOL_M / _M_PER_DEG_LAT
    for nm in names:
        region_m = unary_union(cells_by_name[nm])
        region_ll = _project_geom_from_m(region_m).intersection(bounds_ll)
        if region_ll.is_empty:
            continue
        region_ll = region_ll.simplify(simplify_deg, preserve_topology=True)
        features.append(
            {
                "type": "Feature",
                "properties": {"name": nm},
                "geometry": mapping(region_ll),
            }
        )
        print(f"  {nm}: {region_ll.geom_type}, area~{region_ll.area:.4f} deg^2")

    out = {"type": "FeatureCollection", "features": features}
    with open(OUT_PATH, "w", encoding="utf-8") as fh:
        json.dump(out, fh, separators=(",", ":"))
    size_kb = os.path.getsize(OUT_PATH) / 1024
    print(f"Wrote {OUT_PATH} ({len(features)} regions, {size_kb:.0f} KB)")


def _project_geom_from_m(geom):
    """Return a copy of [geom] with metric coordinates converted back to lon/lat."""
    return shape(_map_coords(mapping(geom), lambda x, y: from_m(x, y)))


def _map_coords(gj: dict, fn):
    """Recursively apply [fn] to every (x, y) pair in a GeoJSON geometry mapping."""
    gtype = gj["type"]
    coords = gj["coordinates"]

    def walk(node):
        if isinstance(node[0], (int, float)):
            return list(fn(node[0], node[1]))
        return [walk(child) for child in node]

    return {"type": gtype, "coordinates": walk(coords)}


if __name__ == "__main__":
    main()
