#!/usr/bin/env python3
"""Convert the bundled Chicago rail KML into two GeoJSON files.

Outputs:
  app/src/main/assets/transit_lines.geojson  (LineString features: name, color)
  app/src/main/assets/stations.geojson       (Point features: name, lines, ada, mapId)

The KML uses:
  * <Style id="line_X"> / <Style id="metra_N"> blocks holding a KML ABGR color.
  * A "Rail Lines" folder of LineString placemarks referencing those styles via
    <styleUrl>#id</styleUrl>.
  * A "Stations" folder of Point placemarks whose <description> carries
    "Lines: ...\nADA accessible: Yes/No\nMap ID: ...".

No third-party dependencies are required.
"""
from __future__ import annotations

import json
import os
import re
import xml.etree.ElementTree as ET

KML_NS = "{http://www.opengis.net/kml/2.2}"
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
KML_PATH = os.path.join(ROOT, "Chicago_Rail_CTA_Metra.kml")
ASSETS_DIR = os.path.join(ROOT, "app", "src", "main", "assets")


def tag(name: str) -> str:
    return f"{KML_NS}{name}"


def abgr_to_hex_rgb(abgr: str) -> str:
    """KML colors are aabbggrr (alpha, blue, green, red). Return #rrggbb."""
    abgr = abgr.strip().lower()
    if len(abgr) != 8:
        return "#808080"
    bb, gg, rr = abgr[2:4], abgr[4:6], abgr[6:8]
    return f"#{rr}{gg}{bb}"


def parse_styles(doc: ET.Element) -> dict[str, str]:
    """Map style id -> hex RGB color from its LineStyle."""
    colors: dict[str, str] = {}
    for style in doc.iter(tag("Style")):
        style_id = style.get("id")
        if not style_id:
            continue
        line_style = style.find(tag("LineStyle"))
        if line_style is None:
            continue
        color_el = line_style.find(tag("color"))
        if color_el is not None and color_el.text:
            colors[style_id] = abgr_to_hex_rgb(color_el.text)
    return colors


def parse_coords(text: str) -> list[list[float]]:
    """Parse a KML <coordinates> block into [[lon, lat], ...]."""
    points: list[list[float]] = []
    for token in text.split():
        parts = token.split(",")
        if len(parts) >= 2:
            lon, lat = float(parts[0]), float(parts[1])
            points.append([lon, lat])
    return points


def find_folder(doc: ET.Element, name: str) -> ET.Element | None:
    for folder in doc.iter(tag("Folder")):
        name_el = folder.find(tag("name"))
        if name_el is not None and (name_el.text or "").strip() == name:
            return folder
    return None


def build_lines(doc: ET.Element, colors: dict[str, str]) -> list[dict]:
    features: list[dict] = []
    # CTA-only output: ignore Metra folders in the combined source KML.
    folder = find_folder(doc, "Rail Lines")
    if folder is not None:
        _collect_line_features(folder, colors, features)
    return features


def _collect_line_features(
    folder: ET.Element, colors: dict[str, str], features: list[dict]
) -> None:
    for pm in folder.iter(tag("Placemark")):
        name_el = pm.find(tag("name"))
        name = (name_el.text or "").strip() if name_el is not None else ""
        style_el = pm.find(tag("styleUrl"))
        style_id = (style_el.text or "").lstrip("#").strip() if style_el is not None else ""
        color = colors.get(style_id, "#808080")
        for line in pm.iter(tag("LineString")):
            coords_el = line.find(tag("coordinates"))
            if coords_el is None or not coords_el.text:
                continue
            coords = parse_coords(coords_el.text)
            if len(coords) < 2:
                continue
            features.append(
                {
                    "type": "Feature",
                    "properties": {"name": name, "color": color},
                    "geometry": {"type": "LineString", "coordinates": coords},
                }
            )


def parse_station_description(desc: str) -> dict:
    lines: list[str] = []
    ada = False
    map_id = None
    for raw in (desc or "").splitlines():
        line = raw.strip()
        m = re.match(r"(?i)lines?:\s*(.+)", line)
        if m:
            lines = [s.strip() for s in m.group(1).split(",") if s.strip()]
            continue
        m = re.match(r"(?i)(?:ada accessible|wheelchair boarding):\s*(.+)", line)
        if m:
            ada = m.group(1).strip().lower().startswith("y")
            continue
        m = re.match(r"(?i)map id:\s*(.+)", line)
        if m:
            map_id = m.group(1).strip()
    return {"lines": lines, "ada": ada, "mapId": map_id}


def build_stations(doc: ET.Element) -> list[dict]:
    features: list[dict] = []
    # CTA-only output: ignore Metra folders in the combined source KML.
    folder = find_folder(doc, "Stations")
    if folder is None:
        return features

    for pm in folder.iter(tag("Placemark")):
        point = pm.find(tag("Point"))
        if point is None:
            continue
        coords_el = point.find(tag("coordinates"))
        if coords_el is None or not coords_el.text:
            continue
        coords = parse_coords(coords_el.text)
        if not coords:
            continue
        name_el = pm.find(tag("name"))
        name = (name_el.text or "").strip() if name_el is not None else ""
        desc_el = pm.find(tag("description"))
        meta = parse_station_description(desc_el.text if desc_el is not None else "")
        props = {"name": name}
        props.update(meta)
        features.append(
            {
                "type": "Feature",
                "properties": props,
                "geometry": {"type": "Point", "coordinates": coords[0]},
            }
        )
    return features


def write_fc(path: str, features: list[dict]) -> None:
    fc = {"type": "FeatureCollection", "features": features}
    with open(path, "w", encoding="utf-8") as fh:
        json.dump(fc, fh, ensure_ascii=False)
        fh.write("\n")


def main() -> None:
    tree = ET.parse(KML_PATH)
    doc = tree.getroot()
    colors = parse_styles(doc)
    lines = build_lines(doc, colors)
    stations = build_stations(doc)

    os.makedirs(ASSETS_DIR, exist_ok=True)
    write_fc(os.path.join(ASSETS_DIR, "transit_lines.geojson"), lines)
    write_fc(os.path.join(ASSETS_DIR, "stations.geojson"), stations)

    print(f"styles parsed:   {len(colors)}")
    print(f"line features:   {len(lines)}")
    print(f"station features:{len(stations)}")


if __name__ == "__main__":
    main()
