#!/usr/bin/env python3
"""Prototype + validate the FIXED water isodistance contouring on the real grid.

This mirrors the algorithm we will port to WaterDistanceGrid.kt:
  - march the grid for a chosen polarity (within = dist<=t, or above = dist>t)
  - orient each segment geometrically so the "inside" region is on its left
  - stitch directed segments into closed rings
  - classify rings as shells (CCW) vs holes (CW) by signed area
  - nest holes into shells by containment -> valid polygons

We validate that the shaded region for a sample scenario matches expectation:
  at 0.8 mi from water, "Closer" => shade everything MORE than 0.8 mi from water,
  water itself must be CLEAR (not shaded).
"""
import numpy as np
from shapely.geometry import Polygon, Point as SPoint
from shapely.ops import unary_union

F = "app/src/main/assets/natural_water_distance.bin"
raw = open(F, "rb").read()
nl = raw.index(b"\n")
hdr = raw[:nl].decode().split()
cols, rows = int(hdr[1]), int(hdr[2])
west, south, east, north = map(float, hdr[3:7])
grid = np.frombuffer(raw[nl + 1:nl + 1 + cols * rows * 2], dtype="<u2").reshape(rows, cols)

lon_step = (east - west) / cols
lat_step = (north - south) / rows


def lon_at(c):  # centre of col c
    return west + (c + 0.5) * lon_step


def lat_at(r):  # centre of row r (row 0 = north)
    return north - (r + 0.5) * lat_step


def dist_at(r, c):
    if r < 0 or c < 0 or r >= rows or c >= cols:
        return None  # outside grid -> treated as NOT inside
    return float(grid[r, c])


def contour(threshold, want_within):
    """Return list of shapely Polygons for the region we want (valid, with holes)."""
    t = threshold

    def inside(r, c):
        d = dist_at(r, c)
        if d is None:
            return False  # padding = outside region so contours close at bounds
        return (d <= t) if want_within else (d > t)

    def clampx(x):
        return min(max(x, west), east)

    def clampy(y):
        return min(max(y, south), north)

    def interp(lonA, latA, insA, lonB, latB, insB):
        # crossing at the mid of the edge (binary field -> midpoint is fine and stable)
        return (clampx((lonA + lonB) / 2.0), clampy((latA + latB) / 2.0))

    segs = []  # oriented (x1,y1,x2,y2) with inside on the LEFT
    # march over corners (r,c),(r,c+1),(r+1,c+1),(r+1,c) with padding -1..rows-1 / -1..cols-1
    for r in range(-1, rows):
        for c in range(-1, cols):
            tl = inside(r, c)
            tr = inside(r, c + 1)
            br = inside(r + 1, c + 1)
            bl = inside(r + 1, c)
            code = (8 if tl else 0) | (4 if tr else 0) | (2 if br else 0) | (1 if bl else 0)
            if code == 0 or code == 15:
                continue
            lonL, lonR = lon_at(c), lon_at(c + 1)
            latT, latB = lat_at(r), lat_at(r + 1)

            top = interp(lonL, latT, tl, lonR, latT, tr)
            right = interp(lonR, latT, tr, lonR, latB, br)
            bottom = interp(lonL, latB, bl, lonR, latB, br)
            left = interp(lonL, latT, tl, lonL, latB, bl)

            # filled corner positions (for orientation)
            corners = {
                "tl": (lonL, latT, tl), "tr": (lonR, latT, tr),
                "br": (lonR, latB, br), "bl": (lonL, latB, bl),
            }
            filled_pts = [(x, y) for (x, y, f) in corners.values() if f]

            def emit(a, b):
                # orient so nearest filled corner is on the LEFT of a->b
                mx, my = (a[0] + b[0]) / 2, (a[1] + b[1]) / 2
                fx, fy = min(filled_pts, key=lambda p: (p[0] - mx) ** 2 + (p[1] - my) ** 2)
                cross = (b[0] - a[0]) * (fy - a[1]) - (b[1] - a[1]) * (fx - a[0])
                if cross > 0:  # filled already on left
                    segs.append((a[0], a[1], b[0], b[1]))
                else:
                    segs.append((b[0], b[1], a[0], a[1]))

            pairs = {
                1: [(left, bottom)], 2: [(bottom, right)], 3: [(left, right)],
                4: [(top, right)], 6: [(top, bottom)], 7: [(left, top)],
                8: [(top, left)], 9: [(top, bottom)], 11: [(top, right)],
                12: [(left, right)], 13: [(bottom, right)], 14: [(left, bottom)],
                5: [(left, top), (bottom, right)], 10: [(top, right), (bottom, left)],
            }[code]
            for a, b in pairs:
                emit(a, b)

    # stitch directed segments into rings
    def key(x, y):
        return (round(x * 1e6), round(y * 1e6))

    from collections import defaultdict, deque
    start_map = defaultdict(deque)
    for i, s in enumerate(segs):
        start_map[key(s[0], s[1])].append(i)
    used = [False] * len(segs)
    rings = []
    for i0 in range(len(segs)):
        if used[i0]:
            continue
        ring = []
        cur = i0
        guard = 0
        while not used[cur] and guard <= len(segs):
            guard += 1
            used[cur] = True
            s = segs[cur]
            ring.append((s[0], s[1]))
            nxt = None
            dq = start_map.get(key(s[2], s[3]))
            while dq:
                cand = dq.popleft()
                if not used[cand]:
                    nxt = cand
                    break
            if nxt is None:
                ring.append((s[2], s[3]))
                break
            cur = nxt
        if len(ring) >= 4:
            rings.append(ring)

    # classify + nest
    def signed_area(ring):
        a = 0.0
        for i in range(len(ring) - 1):
            x1, y1 = ring[i]
            x2, y2 = ring[i + 1]
            a += x1 * y2 - x2 * y1
        return a / 2.0

    shells, holes = [], []
    for ring in rings:
        if ring[0] != ring[-1]:
            ring = ring + [ring[0]]
        sa = signed_area(ring)
        if abs(sa) < 1e-12:
            continue
        (shells if sa > 0 else holes).append(ring)

    shell_polys = [Polygon(s) for s in shells]
    out = []
    used_holes = [False] * len(holes)
    for sp in shell_polys:
        sp_holes = []
        for hi, h in enumerate(holes):
            if used_holes[hi]:
                continue
            hp = Polygon(h)
            if sp.contains(hp.representative_point()):
                sp_holes.append(h)
                used_holes[hi] = True
        out.append(Polygon(sp.exterior.coords, [h for h in sp_holes]))
    return out, len(shells), len(holes)


MI = 1609.344
threshold = 0.8 * MI

polys_far, ns_far, nh_far = contour(threshold, want_within=False)   # "closer" shades this
polys_near, ns_near, nh_near = contour(threshold, want_within=True)  # "farther" shades this

far = unary_union([p for p in polys_far if p.is_valid or p.buffer(0)])
near = unary_union([p for p in polys_near if p.is_valid or p.buffer(0)])

print(f"closer/shade-far : {ns_far} shells, {nh_far} holes, area(deg^2)={far.area:.4f}")
print(f"farther/shade-near: {ns_near} shells, {nh_near} holes, area(deg^2)={near.area:.4f}")

# --- validation points ---
def check(name, lon, lat):
    d = float(grid[int((north - lat) / lat_step), int((lon - west) / lon_step)])
    p = SPoint(lon, lat)
    print(f"{name}: dist={d:.0f}m  in_shadeFar(closer)={far.contains(p)}  in_shadeNear(farther)={near.contains(p)}")

# A river/water cell: distance ~0 => CLOSER must NOT shade it; FARTHER must shade it.
# find a water cell near downtown
wr, wc = np.argwhere(grid == 0)[len(np.argwhere(grid == 0)) // 2]
check("water-cell", lon_at(wc), lat_at(wr))
check("downtown", -87.63, 41.88)
# A far-from-water cell (max distance area)
fr, fc = np.unravel_index(np.argmax(np.where(grid == 65535, 0, grid)), grid.shape)
check("far-inland", lon_at(fc), lat_at(fr))
print("EXPECT: water-cell closer=False (clear), farther=True (shaded)")
