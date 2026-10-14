#!/usr/bin/env python3
"""Turns the logo into the launcher icon and the store icon.

The drawing lives in `design/noisedune-logo.svg` and is edited in Inkscape.
This script is the only thing that copies it into the app, so the icon on the
phone and the icon on the storefront cannot drift apart from each other or from
the source: there is one drawing and three renderings of it.

    python3 docs/icon.py

Writes app/src/main/res/drawable/ic_launcher_{background,foreground,monochrome}.xml
and fails loudly if the artwork lands outside what a launcher mask allows.

    python3 docs/icon.py --store

Also writes store/assets/icon-512.png, the 512x512 PNG the storefront asks for.
Needs `rsvg-convert` and Pillow; the drawables need neither.
"""

import os
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

HERE = os.path.dirname(os.path.abspath(__file__))
LOGO = os.path.join(HERE, "..", "design", "noisedune-logo.svg")
DRAWABLE = os.path.join(HERE, "..", "app", "src", "main", "res", "drawable")
STORE = os.path.join(HERE, "..", "store", "assets", "icon-512.png")

SVG = "{http://www.w3.org/2000/svg}"

# --------------------------------------------------------------------------
# Where the artwork sits
# --------------------------------------------------------------------------
# An adaptive icon is two 108dp layers of which a launcher shows the middle
# 72dp, and the mask that crops them is the OEM's business: a circle, a
# squircle, a rounded square. The emblem is drawn to fill its own 72dp square
# edge to edge, which leaves a circular mask nothing to spare — the ring would
# be shaved flush. 63dp is the diameter it gets instead: inside Material's
# 44-66dp keyline range, inside the 66dp circle every mask is guaranteed to
# show, and with 4.5dp of dark between the ring and the nearest crop.
TARGET_DIAMETER = 63.0

FIELD = 108.0
SAFE_DIAMETER = 66.0   # guaranteed visible under any mask
MASK_DIAMETER = 72.0   # the most generous mask there is

# The app's own sky, from Theme.kt. The emblem carries its own near-black disc;
# this is what fills the corners a square mask leaves, and it is close enough to
# the disc that the seam between them does not read as an edge.
SKY = "#FF0C0A09"

# A themed icon keeps only the monochrome layer's alpha and tints it, so
# anything filled becomes a solid blob: the emblem's sky alone would come out a
# plain white disc with nothing in it. These three are the drawing's outlines —
# the ring and the two dune crests — and as strokes on transparency they
# survive the tinting as a line drawing.
MONOCHROME = ("circle11", "path3", "path4")

# Those strokes are between 1.0 and 2.1dp once placed, which is legible at
# 144px and gone by 48. The line drawing has to carry the icon alone, so it is
# allowed to be heavier than the same lines are inside the full-colour emblem.
MONOCHROME_STROKE_BOOST = 1.6


def style(element):
    return dict(p.split(":", 1)
                for p in (element.get("style") or "").split(";") if ":" in p)


def circle_path(element):
    """VectorDrawable has no <circle>; two half-arcs draw one."""
    cx, cy, r = (float(element.get(a)) for a in ("cx", "cy", "r"))
    return (f"M{cx - r},{cy}a{r},{r} 0 1,0 {2 * r},0"
            f"a{r},{r} 0 1,0 {-2 * r},0Z")


def read_logo():
    """The foreground layer's shapes, and the transform its group carries."""
    root = ET.parse(LOGO).getroot()
    layers = [g for g in root.iter(SVG + "g") if g.get("id") == "layer-fg"]
    if not layers:
        raise SystemExit(f"{LOGO}: no layer-fg; was the layer renamed?")
    groups = [g for g in layers[0] if g.tag == SVG + "g"]
    if len(groups) != 1:
        raise SystemExit(f"{LOGO}: expected one group in layer-fg, found {len(groups)}")

    matrix = re.match(r"matrix\(([-\d.,\s]+)\)", groups[0].get("transform", ""))
    if not matrix:
        raise SystemExit(f"{LOGO}: the artwork group needs a matrix() transform")
    a, b, c, d, e, f = (float(v) for v in matrix.group(1).split(","))
    if b or c or a != d:
        raise SystemExit(f"{LOGO}: only uniform scale and translate can be baked in")

    shapes = []
    for element in groups[0]:
        css = style(element)
        if css.get("display") == "none":
            continue
        tag = element.tag.split("}")[1]
        if tag == "path":
            data = re.sub(r"\s+", " ", element.get("d").strip())
        elif tag == "circle":
            data = circle_path(element)
        else:
            continue
        shapes.append({
            "id": element.get("id"),
            "d": data,
            "fill": css.get("fill", "none"),
            "stroke": css.get("stroke", "none"),
            "width": float(css.get("stroke-width", 1.0)),
        })
    return shapes, (a, e, f), groups[0]


def fit(scale, translate, ring):
    """Place the emblem at TARGET_DIAMETER, centred exactly on the layer.

    Measured off the outer ring rather than off a rendered bounding box: the
    ring is the outermost thing in the drawing, and its stroke hangs half
    outside its own radius.
    """
    cx, cy, r = (float(ring.get(a)) for a in ("cx", "cy", "r"))
    outer = (r + float(style(ring)["stroke-width"]) / 2) * scale
    centre = (cx * scale + translate[0], cy * scale + translate[1])

    k = (TARGET_DIAMETER / 2) / outer
    return (k * scale,
            k * (translate[0] - centre[0]) + FIELD / 2,
            k * (translate[1] - centre[1]) + FIELD / 2,
            2 * outer)


# --------------------------------------------------------------------------
# Output
# --------------------------------------------------------------------------
VECTOR = """{comment}<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
{body}</vector>
"""

GENERATED = ("<!-- Generated by docs/icon.py from design/noisedune-logo.svg.\n"
             "     Edit the drawing, then re-run the script. Not this file.\n")


def argb(colour):
    """`#rrggbb` to the `#aarrggbb` the platform wants."""
    return colour if len(colour) == 9 else "#FF" + colour.lstrip("#").upper()


def path_element(shape, tint=None, boost=1.0):
    attrs = []
    if shape["fill"] != "none":
        attrs.append(f'android:fillColor="{argb(tint or shape["fill"])}"')
    if shape["stroke"] != "none":
        attrs.append(f'android:strokeColor="{argb(tint or shape["stroke"])}"')
        attrs.append(f'android:strokeWidth="{shape["width"] * boost:g}"')
    attrs.append(f'android:pathData="{shape["d"]}"')
    body = "\n            ".join(attrs)
    return f"        <path\n            {body} />\n"


def group(scale, tx, ty, body):
    # A VectorDrawable group scales, then rotates, then translates, so with no
    # rotation and the pivot at the origin this is exactly the matrix() the
    # drawing carries, composed with the fit above. Stroke widths scale with it.
    return (f'    <group\n'
            f'        android:scaleX="{scale:.6f}"\n'
            f'        android:scaleY="{scale:.6f}"\n'
            f'        android:translateX="{tx:.5f}"\n'
            f'        android:translateY="{ty:.5f}">\n'
            f'{body}    </group>\n')


def write(name, comment, body):
    path = os.path.join(DRAWABLE, f"ic_launcher_{name}.xml")
    with open(path, "w", encoding="utf-8") as f:
        f.write(VECTOR.format(comment=comment, body=body))
    print(f"wrote {os.path.relpath(path, os.getcwd())}")


def store_icon(shapes, scale, tx, ty):
    """The storefront asset: the same emblem, over the same sky, cropped to the
    72dp a launcher shows, square because the store rounds the corners itself.
    RGB with no alpha channel, because the store asks for no transparent edges
    and the way to have none is to never have had any."""
    from PIL import Image

    sky = "#" + SKY[3:]            # the drawables want #aarrggbb, SVG wants #rrggbb
    body = "".join(
        f'<path d="{s["d"]}"'
        f' fill="{s["fill"]}" stroke="{s["stroke"]}" stroke-width="{s["width"]:g}"/>'
        for s in shapes)
    svg = (f'<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512"'
           f' viewBox="18 18 72 72">'
           f'<rect x="0" y="0" width="108" height="108" fill="{sky}"/>'
           f'<g transform="matrix({scale},0,0,{scale},{tx},{ty})">{body}</g></svg>')

    os.makedirs(os.path.dirname(STORE), exist_ok=True)
    tmp = STORE + ".svg"
    with open(tmp, "w", encoding="utf-8") as f:
        f.write(svg)
    try:
        subprocess.run(["rsvg-convert", "-w", "512", "-h", "512", tmp, "-o", STORE],
                       check=True)
    except FileNotFoundError:
        raise SystemExit("rsvg-convert is not installed; the store icon needs it")
    finally:
        os.remove(tmp)

    image = Image.open(STORE)
    if image.mode != "RGB":
        image.convert("RGB").save(STORE, "PNG", optimize=True)
        image = Image.open(STORE)
    if image.size != (512, 512) or image.mode != "RGB":
        raise SystemExit(f"wrote {image.size} {image.mode}, wanted 512x512 RGB")
    size = os.path.getsize(STORE)
    if size > 3 * 1024 * 1024:
        raise SystemExit(f"store icon is {size / 1024 / 1024:.1f} MB, over the 3 MB limit")
    print(f"store: {os.path.relpath(STORE, os.getcwd())}  512x512 RGB, "
          f"{size / 1024:.0f} KB (limit 3 MB, no alpha channel)")


def check(diameter, shapes):
    """A launcher mask is unforgiving and invisible until it isn't."""
    ok = True

    def fail(message):
        nonlocal ok
        print(f"FAIL {message}", file=sys.stderr)
        ok = False

    print(f"emblem: drawn at {diameter:.1f}dp, placed at {TARGET_DIAMETER:.1f}dp "
          f"(44-66 is Material's keyline range)")
    if TARGET_DIAMETER > SAFE_DIAMETER:
        fail(f"{TARGET_DIAMETER}dp is past the {SAFE_DIAMETER}dp circle every mask shows")
    if TARGET_DIAMETER > MASK_DIAMETER:
        fail(f"{TARGET_DIAMETER}dp does not fit the layer's visible {MASK_DIAMETER}dp")

    margin = (MASK_DIAMETER - TARGET_DIAMETER) / 2
    print(f"        {margin:.1f}dp of clearance to the roundest mask")

    missing = [name for name in MONOCHROME
               if name not in {s["id"] for s in shapes}]
    if missing:
        fail(f"the monochrome layer wants {missing}, which the drawing no longer has")

    for name in MONOCHROME:
        for shape in shapes:
            if shape["id"] == name and shape["stroke"] == "none":
                fail(f"{name} has no stroke; the monochrome layer would lose it")
    return ok


def main():
    shapes, (scale0, tx0, ty0), group_element = read_logo()
    rings = [e for e in group_element if e.get("id") == "circle11"]
    if not rings:
        raise SystemExit(f"{LOGO}: no circle11; the fit is measured off that ring")
    scale, tx, ty, drawn = fit(scale0, (tx0, ty0), rings[0])

    if not check(drawn, shapes):
        return 1

    if "--store" in sys.argv[1:]:
        store_icon(shapes, scale, tx, ty)

    write("background", GENERATED + """
     Flat. The emblem brings its own near-black disc; this is only what fills
     the corners a square mask leaves, in the app's own sky colour. -->
""", f'    <path\n        android:fillColor="{SKY}"\n'
     f'        android:pathData="M0,0h108v108H0z" />\n')

    write("foreground", GENERATED + """
     The emblem: dunes under a night sky inside a ring. Placed at 63dp so that
     every mask an OEM might apply has something to crop into. -->
""", group(scale, tx, ty, "".join(path_element(s) for s in shapes)))

    write("monochrome", GENERATED + """
     A themed icon keeps only this layer's alpha and tints it, so the emblem's
     fills would come back as one solid disc. What is left is its outlines —
     the ring and two crests — drawn heavier, because here they are the icon
     rather than a detail inside it. -->
""", group(scale, tx, ty, "".join(
        path_element(s, tint="#FFFFFFFF", boost=MONOCHROME_STROKE_BOOST)
        for s in shapes if s["id"] in MONOCHROME)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
