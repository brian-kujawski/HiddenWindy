#!/usr/bin/env python3
"""Build the municipality region asset for the "same municipality" matching question.

Source: geojson/municipalities.geojson — every municipality in Cook County (cities, villages) plus
the unincorporated township remainders. These features form a clean non-overlapping partition
(townships cover only the land not inside an incorporated municipality), so each feature becomes one
named region.

Output: app/src/main/assets/municipality_regions.geojson — a FeatureCollection where every feature
carries a `name` property, matching the `<dataset>_regions.geojson` convention consumed by
PointRegionsRepository.loadRegions("municipality"). Geometry is simplified to keep the asset small
while preserving each region's topology.

Run: tools/.venv/bin/python tools/build_municipality_regions.py
"""
import json
import os

from shapely.geometry import shape, mapping

SRC = "geojson/municipalities.geojson"
DST = "app/src/main/assets/municipality_regions.geojson"
SIMPLIFY_TOLERANCE_DEG = 0.0004  # ~40 m; plenty accurate for a "which region am I nearest" question


def region_name(props: dict) -> str:
    """Prefer the incorporated municipality name; fall back to the township for unincorporated areas."""
    muni = props.get("MUNICIPALITY")
    if muni:
        return muni
    agency = props.get("AGENCY_DESC") or "Unknown"
    # e.g. "HYDE PARK TWP" -> "Hyde Park Township"
    return agency.title().replace(" Twp", " Township")


def main() -> None:
    with open(SRC) as f:
        data = json.load(f)

    out_features = []
    for feature in data["features"]:
        geom = shape(feature["geometry"])
        simplified = geom.simplify(SIMPLIFY_TOLERANCE_DEG, preserve_topology=True)
        if simplified.is_empty:
            simplified = geom
        out_features.append({
            "type": "Feature",
            "properties": {"name": region_name(feature["properties"])},
            "geometry": mapping(simplified),
        })

    out = {"type": "FeatureCollection", "features": out_features}
    os.makedirs(os.path.dirname(DST), exist_ok=True)
    with open(DST, "w") as f:
        json.dump(out, f, separators=(",", ":"))

    names = sorted({feat["properties"]["name"] for feat in out_features})
    print(f"wrote {len(out_features)} regions ({len(names)} unique names) -> {DST}")
    print(f"size: {os.path.getsize(DST):,} bytes")


if __name__ == "__main__":
    main()
