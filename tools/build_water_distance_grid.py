#!/usr/bin/env python3
"""Precompute a distance-to-nearest-water grid over the Chicago play bounds.

Runtime buffering of the ~27k-vertex water geometry with JTS overruns Android's
ANR watchdog. Instead we do the heavy work once, offline: rasterize the water
mask, run an exact Euclidean distance transform, and store a compact grid the app
can threshold in milliseconds to produce an isodistance ("closer/farther") region.

Output (app/src/main/assets/natural_water_distance.bin):
    line 1 (ASCII, newline-terminated):
        "WDIST1 <cols> <rows> <west> <south> <east> <north> <cellMeters> <maxMeters>"
    then rows*cols little-endian uint16 distances in METRES, row-major,
    NORTH row first (top) to south, WEST to east within a row. The value at
    (row, col) is the distance from that cell centre to the nearest water edge,
    clamped to <maxMeters> (also used as the sentinel for "at/over max").

Accuracy is bounded only by CELL_METERS, which is a pure offline cost.

Run:  tools/.venv/bin/python tools/build_water_distance_grid.py
"""
from __future__ import annotations

import json
import math
import os
import struct
import sys

import numpy as np
from scipy import ndimage
from shapely.geometry import shape
from shapely.ops import unary_union
from shapely import contains_xy

# --- Play bounds (must match ZoneGeometry.CHICAGO_BOUNDS) ---------------------
WEST, SOUTH, EAST, NORTH = -88.90, 41.20, -87.20, 42.80

# Grid resolution. 120 m balances accuracy against asset size (~3-4 MB uint16).
CELL_METERS = 120.0

# Distances are clamped/stored in uint16 metres; 65535 also marks "at least max".
MAX_METERS = 65535

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(REPO, "app", "src", "main", "assets", "natural_water_regions.geojson")
OUT = os.path.join(REPO, "app", "src", "main", "assets", "natural_water_distance.bin")


def metres_per_degree(center_lat: float) -> tuple[float, float]:
    """Local equirectangular scale, matching PolygonUnion's projection."""
    m_per_deg_lat = 110_540.0
    m_per_deg_lon = 111_320.0 * math.cos(math.radians(center_lat))
    return m_per_deg_lon, m_per_deg_lat


def main() -> int:
    if not os.path.exists(SRC):
        print(f"missing input: {SRC}", file=sys.stderr)
        return 1

    center_lat = (SOUTH + NORTH) / 2.0
    m_per_deg_lon, m_per_deg_lat = metres_per_degree(center_lat)

    # Grid dimensions from the bounds and desired cell size.
    width_m = (EAST - WEST) * m_per_deg_lon
    height_m = (NORTH - SOUTH) * m_per_deg_lat
    cols = int(math.ceil(width_m / CELL_METERS))
    rows = int(math.ceil(height_m / CELL_METERS))
    print(f"grid {cols} x {rows} cells @ {CELL_METERS:.0f} m "
          f"({cols * rows:,} cells, span {width_m/1000:.1f} x {height_m/1000:.1f} km)")

    # Dissolve all named water bodies into one geometry for clean point-in-water tests.
    data = json.load(open(SRC))
    geoms = [shape(f["geometry"]) for f in data["features"]]
    water = unary_union(geoms)
    print(f"dissolved {len(geoms)} bodies")

    # Cell-centre coordinates (lon per col, lat per row). Row 0 = NORTH (top).
    lon_step = (EAST - WEST) / cols
    lat_step = (NORTH - SOUTH) / rows
    lons = WEST + (np.arange(cols) + 0.5) * lon_step
    lats = NORTH - (np.arange(rows) + 0.5) * lat_step

    # Boolean water mask via vectorised shapely contains (row-major, north-first).
    lon_grid, lat_grid = np.meshgrid(lons, lats)
    mask = contains_xy(water, lon_grid.ravel(), lat_grid.ravel()).reshape(rows, cols)
    print(f"water cells: {int(mask.sum()):,} / {rows * cols:,}")

    # Exact EDT gives distance (in CELLS) from every land cell to the nearest
    # water cell. Cells INSIDE water get distance 0. Anisotropic sampling matters:
    # a lon cell and lat cell cover different metres, so scale each axis.
    #   distance_transform_edt(input) measures distance to the nearest ZERO cell,
    #   so we invert the mask (land=True -> we want distance from land to water).
    land = ~mask
    cell_h_m = lat_step * m_per_deg_lat  # metres per row step
    cell_w_m = lon_step * m_per_deg_lon  # metres per col step
    dist_cells = ndimage.distance_transform_edt(land, sampling=(cell_h_m, cell_w_m))

    dist_m = np.clip(np.round(dist_cells), 0, MAX_METERS).astype(np.uint16)

    header = (f"WDIST1 {cols} {rows} {WEST!r} {SOUTH!r} {EAST!r} {NORTH!r} "
              f"{CELL_METERS!r} {MAX_METERS}\n")
    with open(OUT, "wb") as fh:
        fh.write(header.encode("ascii"))
        fh.write(dist_m.tobytes(order="C"))  # little-endian on all target archs

    size_mb = os.path.getsize(OUT) / (1024 * 1024)
    print(f"wrote {OUT}  ({size_mb:.2f} MB)")
    print(f"max stored distance: {int(dist_m.max())} m")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
