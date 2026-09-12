#!/usr/bin/env python3
"""
Cat Parallax Wallpaper — design tool.

Defines the wallpaper scene (dusk gradient + stars + orange tabby) once, in a
1000 x 1600 design space, then:
  1. renders preview PNGs with Pillow (what the Android Canvas renderer will draw)
  2. emits app/src/main/assets/cat/default_cat.json  (consumed by SceneLoader.kt)
  3. emits app icons / widget icons / wallpaper thumbnail

The JSON is the single source of truth: the Android renderer interprets the same
geometry with the same transform pipeline this tool uses for previews.

Run:  python3 tools/cat_design.py           # regenerate everything
      python3 tools/cat_design.py --previews-only
Requires: pillow
"""

import argparse
import json
import math
import os
import random

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "app", "src", "main", "assets", "cat")
RES = os.path.join(ROOT, "app", "src", "main", "res")
PREVIEWS = os.path.join(ROOT, "tools", "previews")

DW, DH = 1000.0, 1600.0  # design space

# ---------------------------------------------------------------- palette ----
C = {
    "sky_top":     "#141034",
    "sky_mid":     "#4A2C63",
    "sky_low":     "#C4597B",
    "sky_horizon": "#FFB37A",
    "hill_back":   "#33204F",
    "hill_front":  "#2A1B44",
    "ground":      "#221540",
    "glow":        "#FFD9A0",
    "star":        "#FFFFFF",
    "fur":         "#F59E4C",
    "fur_dark":    "#D97F35",
    "cream":       "#FFE9D2",
    "muzzle":      "#FFF3E4",
    "ear_inner":   "#F7AE9B",
    "nose":        "#E8837B",
    "eye":         "#43A85D",
    "pupil":       "#1F2A33",
    "sparkle":     "#FFFFFF",
    "line":        "#5A3A22",
    "whisker":     "#FFE9D0",
    "blush":       "#FF8E6B",
    "bokeh":       "#FFD9A0",
}

# ------------------------------------------------------- path d-string emit ----

def fmt(v):
    s = f"{v:.2f}".rstrip("0").rstrip(".")
    return s if s else "0"


def ell_d(cx, cy, rx, ry):
    """Ellipse as 4 cubic segments (kappa approximation), closed."""
    k = 0.5522847498
    x0, y0 = cx + rx, cy
    x1, y1 = cx, cy + ry
    x2, y2 = cx - rx, cy
    x3, y3 = cx, cy - ry
    c = [
        (cx + rx, cy + ry * k), (cx + rx * k, cy + ry), (cx, cy + ry),
        (cx - rx * k, cy + ry), (cx - rx, cy + ry * k), (cx - rx, cy),
        (cx - rx, cy - ry * k), (cx - rx * k, cy - ry), (cx, cy - ry),
        (cx + rx * k, cy - ry), (cx + rx, cy - ry * k), (cx + rx, cy),
    ]
    d = f"M {fmt(x0)} {fmt(y0)} "
    d += f"C {fmt(c[0][0])} {fmt(c[0][1])} {fmt(c[1][0])} {fmt(c[1][1])} {fmt(c[2][0])} {fmt(c[2][1])} "
    d += f"C {fmt(c[3][0])} {fmt(c[3][1])} {fmt(c[4][0])} {fmt(c[4][1])} {fmt(c[5][0])} {fmt(c[5][1])} "
    d += f"C {fmt(c[6][0])} {fmt(c[6][1])} {fmt(c[7][0])} {fmt(c[7][1])} {fmt(c[8][0])} {fmt(c[8][1])} "
    d += f"C {fmt(c[9][0])} {fmt(c[9][1])} {fmt(c[10][0])} {fmt(c[10][1])} {fmt(c[11][0])} {fmt(c[11][1])} Z"
    return d


def rrect_d(cx, cy, w, h, r):
    """Rounded rect centered at (cx, cy)."""
    x0, y0 = cx - w / 2.0, cy - h / 2.0
    x1, y1 = cx + w / 2.0, cy + h / 2.0
    k = 0.5522847498 * r
    d = f"M {fmt(x0 + r)} {fmt(y0)} "
    d += f"L {fmt(x1 - r)} {fmt(y0)} "
    d += f"C {fmt(x1 - r + k)} {fmt(y0)} {fmt(x1)} {fmt(y0 + r - k)} {fmt(x1)} {fmt(y0 + r)} "
    d += f"L {fmt(x1)} {fmt(y1 - r)} "
    d += f"C {fmt(x1)} {fmt(y1 - r + k)} {fmt(x1 - r + k)} {fmt(y1)} {fmt(x1 - r)} {fmt(y1)} "
    d += f"L {fmt(x0 + r)} {fmt(y1)} "
    d += f"C {fmt(x0 + r - k)} {fmt(y1)} {fmt(x0)} {fmt(y1 - r + k)} {fmt(x0)} {fmt(y1 - r)} "
    d += f"L {fmt(x0)} {fmt(y0 + r)} "
    d += f"C {fmt(x0)} {fmt(y0 + r - k)} {fmt(x0 + r - k)} {fmt(y0)} {fmt(x0 + r)} {fmt(y0)} Z"
    return d


def rpoly_d(points, r):
    """Polygon with rounded corners (quadratic corner join)."""
    n = len(points)
    pts = []
    for i in range(n):
        p_prev = points[(i - 1) % n]
        p = points[i]
        p_next = points[(i + 1) % n]

        def sub(a, b):
            return (a[0] - b[0], a[1] - b[1])

        def unit(a):
            l = math.hypot(*a)
            return (a[0] / l, a[1] / l) if l else (0, 0)

        d1 = unit(sub(p, p_prev))
        d2 = unit(sub(p_next, p))
        a = (p[0] - d1[0] * r, p[1] - d1[1] * r)
        b = (p[0] + d2[0] * r, p[1] + d2[1] * r)
        pts.append((a, p, b))
    d = f"M {fmt(pts[0][0][0])} {fmt(pts[0][0][1])} "
    for (a, p, b) in pts:
        d += f"L {fmt(a[0])} {fmt(a[1])} Q {fmt(p[0])} {fmt(p[1])} {fmt(b[0])} {fmt(b[1])} "
    d += "Z"
    return d


def spark_d(cx, cy, r):
    """4-point sparkle star (two concave diamonds)."""
    return (
        f"M {fmt(cx)} {fmt(cy - r)} Q {fmt(cx)} {fmt(cy)} {fmt(cx + r)} {fmt(cy)} "
        f"Q {fmt(cx)} {fmt(cy)} {fmt(cx)} {fmt(cy + r)} "
        f"Q {fmt(cx)} {fmt(cy)} {fmt(cx - r)} {fmt(cy)} "
        f"Q {fmt(cx)} {fmt(cy)} {fmt(cx)} {fmt(cy - r)} Z"
    )


def P(d, fill=None, alpha=1.0, stroke=None, sw=0.0):
    o = {"d": d}
    if fill:
        o["fill"] = fill
    o["alpha"] = alpha
    if stroke:
        o["stroke"] = stroke
        o["sw"] = sw
    return o


def fill_(d, color, alpha=1.0):
    return P(d, fill=color, alpha=alpha)


def stroke_(d, color, sw, alpha=1.0):
    return P(d, stroke=color, sw=sw, alpha=alpha)


# ------------------------------------------------------------ scene build ----

def build_stars(rng):
    shapes = []
    for _ in range(46):
        x = rng.uniform(-140, 1140)
        y = rng.uniform(30, 830)
        r = rng.uniform(1.5, 5.5)
        a = rng.uniform(0.20, 0.85)
        shapes.append({"c": "circle", "x": x, "y": y, "r": r, "fill": C["star"], "alpha": a})
    for _ in range(6):
        x = rng.uniform(-100, 1100)
        y = rng.uniform(60, 700)
        shapes.append({"c": "circle", "x": x, "y": y, "r": rng.uniform(6.5, 9),
                       "fill": C["star"], "alpha": rng.uniform(0.85, 1.0)})
    for _ in range(6):
        x = rng.uniform(-80, 1080)
        y = rng.uniform(80, 640)
        r = rng.uniform(9, 17)
        shapes.append({"c": "sparkle", "x": x, "y": y, "r": r,
                       "fill": C["star"], "alpha": rng.uniform(0.30, 0.55)})
    return shapes


def build_cat():
    """All cat part layers (design coordinates). Returns dict of layers."""
    # ---- body (egg + chest + head + muzzle + paws + stripes) ----
    body = [
        # torso egg
        fill_(
            "M 500 780 C 608 780 662 900 666 1042 C 670 1180 606 1258 500 1258 "
            "C 394 1258 330 1180 334 1042 C 338 900 392 780 500 780 Z", C["fur"]),
        # chest patch
        fill_(ell_d(500, 1030, 118, 172), C["cream"]),
        # head
        fill_(ell_d(500, 706, 156, 146), C["fur"]),
        # muzzle
        fill_(ell_d(500, 774, 60, 42), C["muzzle"]),
        # forehead stripes
        fill_(rrect_d(494, 590, 17, 66, 8), C["fur_dark"]),
        fill_(rrect_d(458, 596, 14, 54, 7), C["fur_dark"]),
        fill_(rrect_d(530, 596, 14, 54, 7), C["fur_dark"]),
        # cheek stripes
        fill_(rrect_d(376, 742, 48, 12, 6), C["fur_dark"]),
        fill_(rrect_d(372, 768, 40, 10, 5), C["fur_dark"]),
        fill_(rrect_d(624, 742, 48, 12, 6), C["fur_dark"]),
        fill_(rrect_d(628, 768, 40, 10, 5), C["fur_dark"]),
        # side stripes (curved tapered)
        fill_("M 352 952 C 386 958 404 972 412 986 C 392 982 368 970 348 958 Z", C["fur_dark"]),
        fill_("M 345 1030 C 380 1036 400 1050 410 1066 C 388 1060 362 1048 341 1036 Z", C["fur_dark"]),
        fill_("M 648 952 C 614 958 596 972 588 986 C 608 982 632 970 652 958 Z", C["fur_dark"]),
        fill_("M 655 1030 C 620 1036 600 1050 590 1066 C 612 1060 638 1048 659 1036 Z", C["fur_dark"]),
        # front paws
        fill_(rrect_d(444, 1216, 74, 58, 27), C["cream"]),
        fill_(rrect_d(556, 1216, 74, 58, 27), C["cream"]),
        # toe lines
        stroke_("M 430 1204 L 430 1232", C["fur_dark"], 4, 0.75),
        stroke_("M 458 1206 L 458 1236", C["fur_dark"], 4, 0.75),
        stroke_("M 542 1206 L 542 1236", C["fur_dark"], 4, 0.75),
        stroke_("M 570 1204 L 570 1232", C["fur_dark"], 4, 0.75),
    ]

    # ---- tail (drawn in front of the body, curls right then sweeps left) ----
    tail = [
        fill_(
            "M 598 1136 C 700 1148 760 1214 748 1272 C 736 1330 636 1352 512 1352 "
            "C 396 1352 300 1342 262 1320 C 252 1313 252 1296 263 1291 "
            "C 300 1312 398 1322 512 1322 C 620 1322 706 1304 712 1262 "
            "C 718 1222 668 1176 586 1166 Z", C["fur"]),
        # darker tail tip
        fill_(ell_d(272, 1306, 26, 20), C["fur_dark"], 0.95),
        # tail stripes
        fill_("M 700 1200 C 722 1224 730 1250 726 1272 C 716 1252 702 1226 686 1210 Z", C["fur_dark"]),
        fill_("M 588 1330 C 618 1328 646 1320 664 1308 C 648 1336 616 1346 588 1348 Z", C["fur_dark"]),
    ]

    # ---- ears (outer + inner, rounded triangles) ----
    ear_l = [
        fill_(rpoly_d([(402, 542), (312, 640), (452, 652)], 24), C["fur"]),
        fill_(rpoly_d([(402, 570), (344, 636), (436, 642)], 14), C["ear_inner"]),
    ]
    ear_r = [
        fill_(rpoly_d([(598, 542), (688, 640), (548, 652)], 24), C["fur"]),
        fill_(rpoly_d([(598, 570), (656, 636), (564, 642)], 14), C["ear_inner"]),
    ]

    # ---- face (nose, mouth, blush) ----
    face = [
        fill_(rpoly_d([(500, 764), (485, 747), (515, 747)], 6), C["nose"]),
        stroke_("M 500 772 Q 490 786 479 778", C["line"], 5, 0.9),
        stroke_("M 500 772 Q 510 786 521 778", C["line"], 5, 0.9),
        fill_(ell_d(398, 762, 27, 18), C["blush"], 0.32),
        fill_(ell_d(602, 762, 27, 18), C["blush"], 0.32),
    ]

    # ---- whiskers ----
    whiskers = [
        stroke_("M 352 712 Q 296 704 254 714", C["whisker"], 4, 0.9),
        stroke_("M 354 738 Q 296 740 256 752", C["whisker"], 4, 0.9),
        stroke_("M 358 688 Q 302 672 268 676", C["whisker"], 4, 0.9),
        stroke_("M 648 712 Q 704 704 746 714", C["whisker"], 4, 0.9),
        stroke_("M 646 738 Q 704 740 744 752", C["whisker"], 4, 0.9),
        stroke_("M 642 688 Q 698 672 732 676", C["whisker"], 4, 0.9),
    ]

    # ---- eyes (variants) ----
    def eye_open(cx):
        return [
            fill_(ell_d(cx, 710, 28, 34), C["eye"]),
            fill_(ell_d(cx, 714, 11, 20), C["pupil"]),
            fill_(ell_d(cx - 9, 697, 6.5, 6.5), C["sparkle"], 0.95),
            fill_(ell_d(cx + 9, 726, 3.6, 3.6), C["sparkle"], 0.8),
        ]

    def eye_closed(cx):
        return [stroke_(f"M {cx - 26} 718 Q {cx} 692 {cx + 26} 718", C["line"], 9, 0.95)]

    def eye_wide(cx):
        return [
            fill_(ell_d(cx, 710, 35, 42), C["eye"]),
            fill_(ell_d(cx, 714, 12, 14), C["pupil"]),
            fill_(ell_d(cx - 12, 694, 8, 8), C["sparkle"], 0.95),
            fill_(ell_d(cx + 12, 730, 4.4, 4.4), C["sparkle"], 0.8),
        ]

    eyes = {
        "defaultVariant": "open",
        "variants": {
            "open": eye_open(438) + eye_open(562),
            "closed": eye_closed(438) + eye_closed(562),
            "wide": eye_wide(438) + eye_wide(562),
        },
    }

    return {
        "tail": tail,
        "body": body,
        "ear_l": ear_l,
        "ear_r": ear_r,
        "face": face,
        "whiskers": whiskers,
        "eyes": eyes,
    }


def build_scene():
    rng = random.Random(23)
    cat = build_cat()

    hill_back = (
        "M -160 1600 L -160 1200 C 60 1080 240 1010 430 1046 "
        "C 610 1080 760 1180 1160 1160 L 1160 1600 Z"
    )
    hill_front = (
        "M -160 1600 L -160 1276 C 90 1190 330 1140 540 1172 "
        "C 750 1204 930 1300 1160 1290 L 1160 1600 Z"
    )

    layers = [
        {
            "id": "bg", "kind": "gradient", "depth": 0.0, "y0": 0, "y1": 1600,
            "stops": [
                [C["sky_top"], 0.0],
                [C["sky_mid"], 0.42],
                [C["sky_low"], 0.62],
                [C["sky_horizon"], 0.75],
            ],
        },
        {
            "id": "stars", "kind": "shapes", "depth": 0.14, "toggleable": True,
            "shapes": build_stars(rng),
        },
        {
            "id": "hills", "kind": "paths", "depth": 0.32, "toggleable": True,
            "paths": [
                fill_(hill_back, C["hill_back"]),
                fill_(hill_front, C["hill_front"]),
            ],
        },
        {
            "id": "ground", "kind": "paths", "depth": 0.5,
            "paths": [fill_("M -160 1188 L 1160 1188 L 1160 1800 L -160 1800 Z", C["ground"])],
        },
        {
            "id": "glow", "kind": "shapes", "depth": 0.45,
            "shapes": [
                {"c": "ellipse", "x": 500, "y": 1150, "rx": 430, "ry": 215,
                 "fill": C["glow"], "alpha": 0.10},
                {"c": "ellipse", "x": 500, "y": 1130, "rx": 300, "ry": 150,
                 "fill": C["glow"], "alpha": 0.07},
            ],
        },
        {
            "id": "body", "kind": "paths", "depth": 0.72, "role": "body",
            "pivot": [500, 1258], "paths": cat["body"],
        },
        {
            "id": "ear_l", "kind": "paths", "depth": 0.75, "role": "ear_l",
            "pivot": [404, 616], "paths": cat["ear_l"],
        },
        {
            "id": "ear_r", "kind": "paths", "depth": 0.75, "role": "ear_r",
            "pivot": [596, 616], "paths": cat["ear_r"],
        },
        {
            "id": "face", "kind": "paths", "depth": 0.78, "paths": cat["face"],
        },
        {
            "id": "whiskers", "kind": "paths", "depth": 0.80, "toggleable": True,
            "paths": cat["whiskers"],
        },
        {
            "id": "eyes", "kind": "paths", "depth": 0.82, "role": "eyes",
            **cat["eyes"],
        },
        {
            "id": "tail", "kind": "paths", "depth": 0.86, "role": "tail",
            "pivot": [598, 1150], "toggleable": True, "paths": cat["tail"],
        },
        {
            "id": "fg", "kind": "shapes", "depth": 1.0, "toggleable": True,
            "shapes": [
                {"c": "circle", "x": 130, "y": 1400, "r": 78, "fill": C["bokeh"], "alpha": 0.09},
                {"c": "circle", "x": 890, "y": 1330, "r": 58, "fill": C["bokeh"], "alpha": 0.08},
                {"c": "circle", "x": 90, "y": 1040, "r": 46, "fill": C["bokeh"], "alpha": 0.06},
                {"c": "circle", "x": 920, "y": 960, "r": 40, "fill": C["bokeh"], "alpha": 0.06},
                {"c": "sparkle", "x": 168, "y": 880, "r": 22, "fill": C["sparkle"], "alpha": 0.5},
                {"c": "sparkle", "x": 852, "y": 1096, "r": 17, "fill": C["sparkle"], "alpha": 0.42},
            ],
        },
    ]

    return {
        "schema": 1,
        "designWidth": DW,
        "designHeight": DH,
        "catPivot": [500, 1258],
        "layers": layers,
    }


# ---------------------------------------------------------------- parsing ----

def parse_hex(h):
    h = h.lstrip("#")
    r = int(h[0:2], 16)
    g = int(h[2:4], 16)
    b = int(h[4:6], 16)
    return (r, g, b)


def tokenize_d(d):
    toks = d.replace(",", " ").split()
    return toks


def flatten_d(d):
    """Parse path d-string into list of (points, closed) subpath polylines."""
    toks = tokenize_d(d)
    i = 0
    subs = []
    cur = []
    cur_start = None

    def take():
        nonlocal i
        v = float(toks[i])
        i += 1
        return v

    while i < len(toks):
        cmd = toks[i]
        i += 1
        if cmd == "M":
            if cur:
                subs.append((cur, False))
                cur = []
            x, y = take(), take()
            cur = [(x, y)]
            cur_start = (x, y)
        elif cmd == "L":
            cur.append((take(), take()))
        elif cmd == "C":
            x1, y1, x2, y2, x, y = take(), take(), take(), take(), take(), take()
            p0 = cur[-1]
            for s in range(1, 17):
                t = s / 16.0
                mt = 1 - t
                bx = mt ** 3 * p0[0] + 3 * mt * mt * t * x1 + 3 * mt * t * t * x2 + t ** 3 * x
                by = mt ** 3 * p0[1] + 3 * mt * mt * t * y1 + 3 * mt * t * t * y2 + t ** 3 * y
                cur.append((bx, by))
        elif cmd == "Q":
            x1, y1, x, y = take(), take(), take(), take()
            p0 = cur[-1]
            for s in range(1, 13):
                t = s / 12.0
                mt = 1 - t
                bx = mt * mt * p0[0] + 2 * mt * t * x1 + t * t * x
                by = mt * mt * p0[1] + 2 * mt * t * y1 + t * t * y
                cur.append((bx, by))
        elif cmd == "Z":
            if cur_start:
                cur.append(cur_start)
            if cur:
                subs.append((cur, True))
                cur = []
        else:
            raise ValueError(f"unknown path command: {cmd}")
    if cur:
        subs.append((cur, False))
    return subs


# --------------------------------------------------------------- renderer ----

class AnimFrame:
    def __init__(self, eye="open", tail_rot=0.0, ear_l_rot=0.0, ear_r_rot=0.0,
                 body_sx=1.0, body_sy=1.0, body_dy=0.0):
        self.eye = eye
        self.tail_rot = tail_rot
        self.ear_l_rot = ear_l_rot
        self.ear_r_rot = ear_r_rot
        self.body_sx = body_sx
        self.body_sy = body_sy
        self.body_dy = body_dy


MAX_SHIFT_X = 34.0   # design units at depth 1.0, sensitivity 1.0
MAX_SHIFT_Y = 24.0


def layer_anim_params(layer, anim):
    """(rotation_deg, scale_x, scale_y, dy, variant_override) for a layer."""
    role = layer.get("role")
    if anim is None or role is None:
        return (0.0, 1.0, 1.0, 0.0, None)
    if role == "tail":
        return (anim.tail_rot, 1.0, 1.0, 0.0, None)
    if role == "ear_l":
        return (anim.ear_l_rot, 1.0, 1.0, 0.0, None)
    if role == "ear_r":
        return (anim.ear_r_rot, 1.0, 1.0, 0.0, None)
    if role == "body":
        return (0.0, anim.body_sx, anim.body_sy, anim.body_dy, None)
    if role == "eyes":
        return (0.0, 1.0, 1.0, 0.0, anim.eye)
    return (0.0, 1.0, 1.0, 0.0, None)


def render_scene(scene, out_w, out_h, pitch=0.0, roll=0.0, anim=None,
                 sensitivity=1.0, ss=2, region=None, hide=None):
    """Render with the same transform pipeline as SceneRenderer.kt.

    region: optional (x0, y0, x1, y1) design-space crop (for icons).
    hide: set of layer ids to skip (previews of toggles).

    All drawing happens on a supersampled canvas (out*ss); every transform
    below is therefore expressed in supersampled pixels.
    """
    k = max(out_w / DW, out_h / DH)
    tx = (out_w - DW * k) / 2.0
    ty = (out_h - DH * k) / 2.0

    if region:
        rx0, ry0, rx1, ry1 = region
        rk = max(out_w / (rx1 - rx0), out_h / (ry1 - ry0))
        rtx = (out_w - DW * rk) / 2.0
        rty = (out_h - DH * rk) / 2.0

    W, H = out_w * ss, out_h * ss
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))

    def to_screen(pt, dx_px, dy_px):
        """design units -> supersampled pixels."""
        if region:
            return (pt[0] * rk * ss + rtx * ss, pt[1] * rk * ss + rty * ss)
        return (pt[0] * k * ss + tx * ss + dx_px * ss,
                pt[1] * k * ss + ty * ss + dy_px * ss)

    for layer in scene["layers"]:
        if hide and layer["id"] in hide:
            continue
        depth = layer.get("depth", 0.5)
        kind = layer["kind"]

        if kind == "gradient":
            stops = layer["stops"]
            y0, y1 = layer.get("y0", 0), layer.get("y1", DH)
            if region:
                gy0, gy1 = (ry0 * rk + rty) * ss, (ry1 * rk + rty) * ss
            else:
                gy0, gy1 = (y0 * k + ty) * ss, (y1 * k + ty) * ss
            band = Image.new("RGBA", (W, H))
            bd = ImageDraw.Draw(band)
            for py in range(H):
                fy = (py - gy0) / max(1e-6, (gy1 - gy0))
                fy = min(1.0, max(0.0, fy))
                for si in range(len(stops) - 1):
                    c0, p0 = stops[si]
                    c1, p1 = stops[si + 1]
                    if p0 <= fy <= p1:
                        t = 0 if p1 == p0 else (fy - p0) / (p1 - p0)
                        a = parse_hex(c0)
                        b = parse_hex(c1)
                        col = tuple(int(a[j] + (b[j] - a[j]) * t) for j in range(3)) + (255,)
                        bd.line([(0, py), (W, py)], fill=col)
                        break
                else:
                    # beyond last stop: clamp to last color
                    last = parse_hex(stops[-1][0]) + (255,)
                    bd.line([(0, py), (W, py)], fill=last)
            img.alpha_composite(band)
            continue

        # parallax offset for this layer (screen px)
        if region:
            dx_px = dy_px = 0.0
        else:
            dx_px = -roll * depth * MAX_SHIFT_X * k * sensitivity
            dy_px = pitch * depth * MAX_SHIFT_Y * k * sensitivity

        rot, sx, sy, dy, variant = layer_anim_params(layer, anim)
        pivot = layer.get("pivot") or [DW / 2, DH / 2]

        def xf(pt):
            x, y = pt
            # anim transforms in design space around pivot
            if rot:
                ra = math.radians(rot)
                px, py = pivot
                cx, cy = x - px, y - py
                x = px + cx * math.cos(ra) + cy * math.sin(ra)
                y = py - cx * math.sin(ra) + cy * math.cos(ra)
            if sx != 1.0 or sy != 1.0:
                px, py = pivot
                x = px + (x - px) * sx
                y = py + (y - py) * sy
            y += dy
            return to_screen((x, y), dx_px, dy_px)

        # draw the layer on its own transparent overlay, then composite:
        # ImageDraw writes raw RGBA (no blending), so translucent layers must
        # go through alpha_composite to tint what's underneath.
        overlay = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        draw = ImageDraw.Draw(overlay)

        if kind == "shapes":
            for s in layer["shapes"]:
                alpha = s.get("alpha", 1.0)
                col = parse_hex(s["fill"]) + (int(255 * alpha),)
                c = s["c"]
                kx = (rk if region else k) * ss
                if c == "circle":
                    p = xf((s["x"], s["y"]))
                    rr = s["r"] * kx
                    draw.ellipse([p[0] - rr, p[1] - rr, p[0] + rr, p[1] + rr], fill=col)
                elif c == "ellipse":
                    p = xf((s["x"], s["y"]))
                    draw.ellipse([p[0] - s["rx"] * kx, p[1] - s["ry"] * kx,
                                  p[0] + s["rx"] * kx, p[1] + s["ry"] * kx], fill=col)
                elif c == "sparkle":
                    cxp, cyp = xf((s["x"], s["y"]))
                    ax, ay = xf((s["x"], s["y"] - s["r"]))
                    bx, by = xf((s["x"] + s["r"], s["y"]))
                    poly = []
                    corners = [
                        ((ax, ay), (bx, by)),
                        ((bx, by), xf((s["x"], s["y"] + s["r"]))),
                        (xf((s["x"], s["y"] + s["r"])), xf((s["x"] - s["r"], s["y"]))),
                        (xf((s["x"] - s["r"], s["y"])), (ax, ay)),
                    ]
                    for (a, b) in corners:
                        for tt in range(1, 9):
                            t = tt / 8.0
                            mt = 1 - t
                            qx = mt * mt * a[0] + 2 * mt * t * cxp + t * t * b[0]
                            qy = mt * mt * a[1] + 2 * mt * t * cyp + t * t * b[1]
                            poly.append((qx, qy))
                    draw.polygon(poly, fill=col)
            img.alpha_composite(overlay)
            continue

        if kind == "paths":
            paths = layer.get("paths")
            if paths is None and "variants" in layer:
                v = variant or layer.get("defaultVariant", "open")
                paths = layer["variants"].get(v) or layer["variants"][layer["defaultVariant"]]
            kx = (rk if region else k) * ss
            for p in paths:
                alpha = p.get("alpha", 1.0)
                subs = flatten_d(p["d"])
                if p.get("fill"):
                    col = parse_hex(p["fill"]) + (int(255 * alpha),)
                    for pts, closed in subs:
                        poly = [xf(pt) for pt in pts]
                        if len(poly) >= 3:
                            draw.polygon(poly, fill=col)
                if p.get("stroke"):
                    col = parse_hex(p["stroke"]) + (int(255 * alpha),)
                    lw = p.get("sw", 4) * kx
                    for pts, closed in subs:
                        if len(pts) >= 2:
                            draw.line([xf(p2) for p2 in pts], fill=col,
                                      width=max(1, int(lw)), joint="curve")
                            a = xf(pts[0])
                            b = xf(pts[-1])
                            r = lw / 2.0
                            draw.ellipse([a[0] - r, a[1] - r, a[0] + r, a[1] + r], fill=col)
                            draw.ellipse([b[0] - r, b[1] - r, b[0] + r, b[1] + r], fill=col)
            img.alpha_composite(overlay)
            continue

    return img.resize((out_w, out_h), Image.LANCZOS)


# ------------------------------------------------------------------ output ----

def contact_sheet(scene, path):
    cells = [
        ("idle", dict(anim=AnimFrame())),
        ("blink", dict(anim=AnimFrame(eye="closed"))),
        ("wide (wake start)", dict(anim=AnimFrame(eye="wide", body_sx=1.05, body_sy=0.93,
                                                  ear_l_rot=-9, ear_r_rot=9))),
        ("tail flick +", dict(anim=AnimFrame(tail_rot=13))),
        ("tail flick -", dict(anim=AnimFrame(tail_rot=-7))),
        ("tilt left", dict(roll=-0.8)),
        ("tilt right", dict(roll=0.8)),
        ("no stars/fg/whiskers", dict(hide={"stars", "fg", "whiskers"})),
    ]
    cw, ch = 240, 428
    pad = 14
    cols = 4
    rows = (len(cells) + cols - 1) // cols
    sheet = Image.new("RGB", (cols * (cw + pad) + pad, rows * (ch + 46 + pad) + pad), (24, 22, 34))
    d = ImageDraw.Draw(sheet)
    try:
        font = ImageFont.load_default(size=15)
    except TypeError:
        font = ImageFont.load_default()
    for idx, (label, kw) in enumerate(cells):
        cell = render_scene(scene, cw, ch, **kw).convert("RGB")
        x = pad + (idx % cols) * (cw + pad)
        y = pad + (idx // cols) * (ch + 46 + pad)
        sheet.paste(cell, (x, y))
        d.text((x + 4, y + ch + 8), label, fill=(235, 230, 245), font=font)
    sheet.save(path)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--previews-only", action="store_true")
    args = ap.parse_args()

    os.makedirs(PREVIEWS, os.path.exists(PREVIEWS) and 0o777 or 0o777) if not os.path.exists(PREVIEWS) else None
    os.makedirs(PREVIEWS, exist_ok=True)
    scene = build_scene()

    # ---- previews (for design iteration) ----
    full = render_scene(scene, 540, 960)
    full.convert("RGB").save(os.path.join(PREVIEWS, "full_scene.png"))
    contact_sheet(scene, os.path.join(PREVIEWS, "states.png"))
    print("previews written to", PREVIEWS)

    if args.previews_only:
        return

    # ---- default_cat.json (single source of truth for the app) ----
    os.makedirs(ASSETS, exist_ok=True)
    with open(os.path.join(ASSETS, "default_cat.json"), "w") as f:
        json.dump(scene, f, indent=1)
    print("wrote", os.path.join(ASSETS, "default_cat.json"))

    # ---- wallpaper thumbnail (picker) ----
    thumb = render_scene(scene, 300, 534)
    nodpi = os.path.join(RES, "drawable-nodpi")
    os.makedirs(nodpi, exist_ok=True)
    thumb.convert("RGB").save(os.path.join(nodpi, "wallpaper_thumbnail.png"))

    # ---- launcher foreground (head closeup in safe zone) ----
    fg = render_scene(scene, 432, 432, region=(320, 545, 680, 905))
    xxxhdpi = os.path.join(RES, "mipmap-xxxhdpi")
    os.makedirs(xxxhdpi, exist_ok=True)
    fg.save(os.path.join(xxxhdpi, "ic_launcher_fg.png"))

    # ---- widget icons: cat head on/off ----
    def head_icon(anim, bg):
        icon = Image.new("RGBA", (108, 108), (0, 0, 0, 0))
        d = ImageDraw.Draw(icon)
        d.rounded_rectangle([4, 4, 104, 104], radius=26, fill=bg)
        head = render_scene(scene, 84, 84, anim=anim, region=(338, 560, 662, 884))
        icon.alpha_composite(head, (12, 12))
        return icon

    head_icon(AnimFrame(), "#FF9A76").save(os.path.join(nodpi, "ic_widget_cat_on.png"))
    head_icon(AnimFrame(eye="closed"), "#565661").save(os.path.join(nodpi, "ic_widget_cat_off.png"))

    # ---- tile icon: white silhouette ----
    tile = Image.new("RGBA", (96, 96), (0, 0, 0, 0))
    sil = build_scene()
    for l in sil["layers"]:
        if l["id"] == "body":
            l["paths"] = [fill_(p["d"], "#FFFFFF") for p in l["paths"][:1]]
        elif l["id"] in ("ear_l", "ear_r"):
            l["paths"] = [fill_(l["paths"][0]["d"], "#FFFFFF")]
        elif l["id"] in ("bg", "stars", "hills", "glow", "ground", "face",
                         "whiskers", "eyes", "tail", "fg"):
            l["depth"] = -1  # mark skip
    sil["layers"] = [l for l in sil["layers"] if l.get("depth", 0) >= 0]
    sils = render_scene(sil, 96, 96, region=(300, 520, 700, 920))
    tile.alpha_composite(sils)
    tile.save(os.path.join(nodpi, "ic_tile_cat.png"))

    # ---- widget picker preview ----
    wp = Image.new("RGBA", (320, 160), (0, 0, 0, 0))
    d = ImageDraw.Draw(wp)
    d.rounded_rectangle([0, 20, 319, 139], radius=28, fill=(179, 65, 123, 255))
    on = Image.open(os.path.join(nodpi, "ic_widget_cat_on.png")).resize((72, 72), Image.LANCZOS)
    wp.alpha_composite(on, (36, 44))
    try:
        f2 = ImageFont.load_default(size=26)
    except TypeError:
        f2 = ImageFont.load_default()
    d.text((124, 60), "Cat: ON", fill=(255, 255, 255, 255), font=f2)
    wp.save(os.path.join(nodpi, "widget_preview.png"))

    print("assets written")


if __name__ == "__main__":
    main()
