"""Builds assets/gemini/font/inter_{regular,medium,semibold}.ttf from the @fontsource/inter npm package (OFL 1.1).

Merges the Latin, Latin Extended, Cyrillic and Greek subsets of weights 400, 500 and 600 into one TrueType file
each and gives every digit the same advance (tabular figures), so numbers in the HUD do not jitter.
usage: build_inter.py <fontsource-inter/files dir> <out dir>   (needs fonttools; rename inter_semibold.ttf as is)
"""
import sys
from fontTools.ttLib import TTFont
from fontTools.merge import Merger

src, out = sys.argv[1], sys.argv[2]
NAMES = {"400": "regular", "500": "medium", "600": "semibold"}
for w in ("400", "500", "600"):
    parts = []
    for sub in ("latin", "latin-ext", "cyrillic", "greek"):
        f = TTFont(f"{src}/inter-{sub}-{w}-normal.woff")
        f.flavor = None
        for t in ("GSUB", "GPOS", "GDEF", "DSIG", "STAT", "HVAR", "gvar", "fvar", "avar", "MVAR"):
            if t in f:
                del f[t]
        p = f"{out}/tmp-{sub}-{w}.ttf"
        f.save(p)
        parts.append(p)
    m = Merger().merge(parts)
    name = NAMES[w]
    # tabular figures: every digit gets the widest digit's advance, centred
    cmap = m.getBestCmap()
    hmtx = m["hmtx"]
    glyf = m["glyf"]
    numerals = [cmap[ord(c)] for c in "0123456789"]
    mx = max(hmtx[g][0] for g in numerals)
    for g in numerals:
        adv, lsb = hmtx[g]
        d = (mx - adv) // 2
        if adv != mx:
            gl = glyf[g]
            if gl.numberOfContours > 0:
                gl.coordinates.translate((d, 0))
                gl.recalcBounds(glyf)
            hmtx[g] = (mx, lsb + d)
    m.save(f"{out}/inter_{name}.ttf")
    f = TTFont(f"{out}/inter_{name}.ttf")
    cm = f.getBestCmap()
    print(name, len(cm), "chars", f["head"].unitsPerEm, "upm cap", f["OS/2"].sCapHeight, "x", f["OS/2"].sxHeight,
          "asc", f["hhea"].ascent, "desc", f["hhea"].descent, [f["hmtx"][cm[ord(c)]][0] for c in "0123"])
    print("  missing:", "".join(c for c in "★☆·→✓✔✕✖▸▶◀•…—–×°±≥≤←↑↓⌘⇧▾▴▲▼⚠ⓘ♥⏎⌫" if ord(c) not in cm))
