#!/usr/bin/env python3
"""Regenerates the three launcher-icon vector drawables.

The icon is a baby's foot on the app's own dune field. Neither layer is drawn
by hand: the dunes come out of the same generator as `NoiseField.kt`, and the
foot is a width profile swept along the sole rather than a set of tuned beziers,
so both can be re-derived instead of re-eyeballed.

    python3 docs/icon.py

Writes app/src/main/res/drawable/ic_launcher_{background,foreground,monochrome}.xml
and fails loudly if the geometry drifts outside what a launcher mask allows.
"""

import math
import os
import sys

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                   "..", "app", "src", "main", "res", "drawable")

# --------------------------------------------------------------------------
# The field
# --------------------------------------------------------------------------
# Verbatim from the Dunes table in NoiseField.kt: crestRatio, amplitudeRatio,
# wavelength, periodSeconds, direction. Keep these in step with that file.
DUNES = [
    (0.18, 0.12, 1.15, 70.0, 1.0),
    (0.32, 0.15, 0.95, 55.0, -1.0),
    (0.48, 0.16, 1.30, 42.0, 1.0),
    (0.64, 0.14, 0.85, 32.0, -1.0),
    (0.78, 0.13, 1.05, 25.0, 1.0),
]

# Which moment of the field to freeze. Scanning the first two minutes, 86 seconds
# is the phase where all five crests clear each other by the widest margin; most
# phases collapse two of them into a single edge. Intensity 0 is the field at
# rest, where the generator folds each amplitude halfway back toward its crest —
# the state the app is in before anything is playing, which is the right thing
# for an icon to show.
SECONDS, INTENSITY = 86.0, 0.0

FIELD = 108.0  # adaptive-icon layers are 108x108dp
STEP = 4.0     # sampling along the width; finer than the eye can tell at 48dp

# The app's dune greys are neutral and sit on #000000. The icon's sky is the
# warm near-black #0C0A09, so these are the same ramp shifted warm. The front
# dune is the lightest thing here and still sits below the app's #353535 ceiling
# in luminance (51.5 against 53).
SKY = "#FF0C0A09"
DUNE_FILL = ["#FF141111", "#FF1B1613", "#FF241D17", "#FF30271D", "#FF3E3223"]
AMBER = "#FFF8BA7D"


def dune_path(dune):
    crest, amplitude_ratio, wavelength, period, direction = dune
    crest_y = crest * FIELD
    amplitude = amplitude_ratio * FIELD * (0.5 + 0.5 * INTENSITY)
    phase = SECONDS * (2 * math.pi / period) * direction

    points, x = [], 0.0
    while True:
        xc = min(x, FIELD)
        nx = (xc / FIELD) * 2 * math.pi * wavelength
        # Two harmonics, as in the app: one sine reads as a graph, the second
        # makes the crests uneven enough to read as terrain.
        offset = (math.sin(nx + phase) * amplitude
                  + math.cos(nx * 0.5 - phase * 0.6) * (amplitude * 0.35))
        points.append((xc, crest_y + offset))
        if xc >= FIELD:
            break
        x += STEP
    return "M0,108" + "".join(f"L{px:g},{py:.1f}" for px, py in points) + "L108,108Z"


# --------------------------------------------------------------------------
# The foot
# --------------------------------------------------------------------------
# The sole is a width profile swept down the foot: at each fraction of the
# length, how far the outline sits to the lateral (right) and medial (left)
# side. The medial column is the one that dips — that dip is the arch, and it
# is the single feature that stops the silhouette reading as a sack.
SOLE_Y0, SOLE_LENGTH, SOLE_CX = 42.0, 46.0, 54.0
CAP_TOP, CAP_BOTTOM = 0.26, 0.22   # elliptical caps, as fractions of the length
KNOTS = [0.26, 0.36, 0.46, 0.56, 0.66, 0.78]
LATERAL = [18.0, 17.6, 17.0, 16.2, 15.2, 13.8]
MEDIAL = [18.0, 17.2, 15.8, 13.6, 11.8, 13.2]
BALL_HALF_WIDTH = 18.0

# Toes, biggest to smallest. Only the x positions and the radii are chosen; each
# y falls out of where the ball's dome happens to be under that toe, which is
# what keeps the row hugging the sole instead of arcing over it like a crown.
TOE_X = [37.6, 47.2, 55.7, 63.4, 69.8]
TOE_R = [5.3, 4.0, 3.6, 3.2, 2.8]
TOE_GAP = 1.2

TILT_DEGREES = -8.0
# Material's keyline range for artwork inside the 72dp mask is 44-66dp. 55 keeps
# the foot clear of every mask shape and leaves the field room to be seen.
CONTENT_DIAMETER = 55.0

OUTLINE_SAMPLES = 140  # dense enough that the caps show no faceting at 512px


def _profile(t, column):
    if t <= KNOTS[0]:
        return column[0]
    if t >= KNOTS[-1]:
        return column[-1]
    for i in range(len(KNOTS) - 1):
        if KNOTS[i] <= t <= KNOTS[i + 1]:
            u = (t - KNOTS[i]) / (KNOTS[i + 1] - KNOTS[i])
            return column[i] + (column[i + 1] - column[i]) * (u * u * (3 - 2 * u))
    return column[-1]


def _cap(t):
    if t < CAP_TOP:
        return math.sqrt(max(0.0, 1 - ((CAP_TOP - t) / CAP_TOP) ** 2))
    if t > 1 - CAP_BOTTOM:
        return math.sqrt(max(0.0, 1 - ((t - (1 - CAP_BOTTOM)) / CAP_BOTTOM) ** 2))
    return 1.0


def sole_outline():
    right, left = [], []
    for i in range(OUTLINE_SAMPLES + 1):
        t = i / OUTLINE_SAMPLES
        y = SOLE_Y0 + SOLE_LENGTH * t
        k = _cap(t)
        right.append((SOLE_CX + _profile(t, LATERAL) * k, y))
        left.append((SOLE_CX - _profile(t, MEDIAL) * k, y))
    return right + left[::-1]


def _dome_y(x):
    """Where the top cap's edge sits under a given x."""
    h = CAP_TOP * SOLE_LENGTH
    u = (x - SOLE_CX) / BALL_HALF_WIDTH
    return (SOLE_Y0 + h) - h * math.sqrt(max(0.0, 1 - u * u))


TOES = [(x, _dome_y(x) - r - TOE_GAP, r) for x, r in zip(TOE_X, TOE_R)]


def place():
    """Tilt, centre and scale the foot onto the 108dp layer."""
    outline = sole_outline()
    extremes = list(outline)
    for x, y, r in TOES:
        extremes += [(x - r, y), (x + r, y), (x, y - r), (x, y + r)]
    bx = (min(p[0] for p in extremes) + max(p[0] for p in extremes)) / 2
    by = (min(p[1] for p in extremes) + max(p[1] for p in extremes)) / 2

    a = math.radians(TILT_DEGREES)
    ca, sa = math.cos(a), math.sin(a)
    rot = lambda x, y: ((x - bx) * ca - (y - by) * sa, (x - bx) * sa + (y - by) * ca)

    def radius(scale):
        worst = max(math.hypot(*(c * scale for c in rot(x, y))) for x, y in outline)
        for x, y, r in TOES:
            worst = max(worst, math.hypot(*(c * scale for c in rot(x, y))) + r * scale)
        return worst

    scale = CONTENT_DIAMETER / (2 * radius(1.0))
    put = lambda x, y: tuple(54 + c * scale for c in rot(x, y))
    return ([put(x, y) for x, y in outline],
            [(*put(x, y), r * scale) for x, y, r in TOES],
            radius(scale))


def foot_path(outline, toes):
    sole = "M" + "L".join(f"{x:.2f},{y:.2f}" for x, y in outline) + "Z"
    # VectorDrawable has no <circle>; each toe is two half-arcs.
    for cx, cy, r in toes:
        sole += (f"M{cx - r:.2f},{cy:.2f}"
                 f"a{r:.2f},{r:.2f} 0 1,0 {2 * r:.2f},0"
                 f"a{r:.2f},{r:.2f} 0 1,0 {-2 * r:.2f},0Z")
    return sole


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


def path_element(fill, data):
    return f'    <path\n        android:fillColor="{fill}"\n        android:pathData="{data}" />\n'


def write(name, comment, body):
    path = os.path.join(OUT, f"ic_launcher_{name}.xml")
    with open(path, "w") as f:
        f.write(VECTOR.format(comment=comment, body=body))
    print(f"wrote {os.path.relpath(path, os.getcwd())}")


def check(outline, toes, worst):
    """A launcher mask is unforgiving and invisible until it isn't."""
    ok = True

    def fail(msg):
        nonlocal ok
        print(f"FAIL {msg}", file=sys.stderr)
        ok = False

    # The mask shows the inner 72x72dp; its roundest form is a 72dp circle.
    print(f"foot:  content diameter {2 * worst:.1f}dp   (44-66 is Material's keyline range)")
    if worst >= 36:
        fail(f"foot reaches {worst:.1f}dp from centre, past the 36dp round mask")

    pts = list(outline)
    for x, y, r in toes:
        pts += [(x - r, y), (x + r, y), (x, y - r), (x, y + r)]
    x0, x1 = min(p[0] for p in pts), max(p[0] for p in pts)
    y0, y1 = min(p[1] for p in pts), max(p[1] for p in pts)
    print(f"       bbox x {x0:.1f}..{x1:.1f}  y {y0:.1f}..{y1:.1f}   (safe zone 18..90)")
    if x0 < 18 or x1 > 90 or y0 < 18 or y1 > 90:
        fail("foot leaves the 72x72 safe zone")

    # Toes have to stay apart, or the five of them read as one scalloped blob.
    for i, ((ax, ay, ar), (bx_, by_, br)) in enumerate(zip(toes, toes[1:])):
        gap = math.hypot(bx_ - ax, by_ - ay) - ar - br
        if gap < 0.4:
            fail(f"toes {i + 1} and {i + 2} are {gap:.2f}dp apart; they will merge")

    # And the crests have to stay apart for the same reason.
    gaps = []
    for a, b in zip(DUNES, DUNES[1:]):
        pa, pb = [], []
        for src, dst in ((a, pa), (b, pb)):
            crest, ar_, wl, per, dirn = src
            cy = crest * FIELD
            amp = ar_ * FIELD * (0.5 + 0.5 * INTENSITY)
            ph = SECONDS * (2 * math.pi / per) * dirn
            for i in range(55):
                x = i * 2.0
                nx = (x / FIELD) * 2 * math.pi * wl
                dst.append(cy + math.sin(nx + ph) * amp
                           + math.cos(nx * 0.5 - ph * 0.6) * (amp * 0.35))
        gaps.append(min(q - p for p, q in zip(pa, pb)))
    print(f"dunes: crest gaps {[round(g, 1) for g in gaps]}dp")
    if min(gaps) < 2.0:
        fail("dune crests merge at this phase; pick another SECONDS")

    return ok


def main():
    outline, toes, worst = place()
    if not check(outline, toes, worst):
        return 1

    foot = foot_path(outline, toes)

    background = path_element(SKY, "M0,0h108v108H0z")
    for dune, fill in zip(DUNES, DUNE_FILL):
        background += path_element(fill, dune_path(dune))

    write("background", """<!-- Generated by docs/icon.py — edit that, not this.

     The app's own dune field, frozen 86 seconds in and at rest. Five crests,
     back to front, drawn by the generator in NoiseField.kt rather than
     redrawn by eye, so the icon stays a quotation and not an impression. -->
""", background)

    write("foreground", """<!-- Generated by docs/icon.py — edit that, not this.

     A baby's foot: the ball, the arch, and five toes largest to smallest. Five
     shapes stepping down is the figure the old spectrum icon drew and the one
     the fade-out traces, except here it is anatomy rather than a chart. -->
""", path_element(AMBER, foot))

    write("monochrome", """<!-- Generated by docs/icon.py — edit that, not this.

     A themed icon keeps only this layer's alpha and tints it, so the foot has
     to carry the icon alone: no field, no second colour, and nothing knocked
     out in black — black over amber still has alpha 1 and would vanish here. -->
""", path_element("#FFFFFFFF", foot))
    return 0


if __name__ == "__main__":
    sys.exit(main())
