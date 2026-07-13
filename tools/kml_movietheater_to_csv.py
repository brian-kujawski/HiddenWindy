"""Convert kml/poi_movietheater.kml → csv/poi-movietheater.csv (name,latitude,longitude)."""
import csv
import xml.etree.ElementTree as ET
from pathlib import Path

from shapely.geometry import Polygon
from shapely.ops import unary_union

KML_NS = "{http://www.opengis.net/kml/2.2}"

ROOT = Path(__file__).parent.parent
KML_FILE = ROOT / "kml" / "poi_movietheater.kml"
OUT_CSV = ROOT / "csv" / "poi-movietheater.csv"


def parse_coords(text: str) -> list[tuple[float, float]]:
    pts = []
    for token in text.split():
        parts = token.split(",")
        if len(parts) >= 2:
            try:
                pts.append((float(parts[0]), float(parts[1])))
            except ValueError:
                pass
    return pts


def main():
    tree = ET.parse(KML_FILE)
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

    OUT_CSV.parent.mkdir(parents=True, exist_ok=True)
    with open(OUT_CSV, "w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow(["name", "latitude", "longitude"])
        writer.writerows(rows)

    print(f"Wrote {len(rows)} movie theaters to {OUT_CSV}  ({skipped} skipped)")


if __name__ == "__main__":
    main()
