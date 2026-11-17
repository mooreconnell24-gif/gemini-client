"""Writes the font definitions assets/gemini/font/ui_<size>_<weight>_x<n>.json.

One definition per size, weight and pixel density (n = real pixels per GUI unit), so that a glyph bitmap maps 1:1
onto screen pixels. usage: gen_json.py <font dir> [shift overrides]
"""
import json
import sys

out = sys.argv[1]
SIZES = {"small": 8.0, "body": 9.5, "large": 13.0, "title": 18.0, "hero": 27.0}
WEIGHTS = {"regular": "inter_regular.ttf", "medium": "inter_medium.ttf", "bold": "inter_semibold.ttf"}
CAP = 1490.0 / 2048.0
MAX_DENSITY = 6
# vanilla puts a TrueType baseline 7 GUI units below the draw position; we want the top of the capitals on it
for size_name, size in SIZES.items():
    for weight, file in WEIGHTS.items():
        for n in range(1, MAX_DENSITY + 1):
            px = int(size * n + 0.5)             # the pixel size the game asks FreeType for
            cap_px = round(px * CAP)             # height of a capital in real pixels
            shift_px = cap_px - 7 * n            # move the baseline down so the capitals start at y
            doc = {
                "providers": [
                    {
                        "type": "ttf",
                        "file": "gemini:" + file,
                        "size": px / n,
                        "oversample": float(n),
                        "shift": [0.0, shift_px / n],
                    },
                    {"type": "reference", "id": "minecraft:default"},
                ]
            }
            with open(f"{out}/ui_{size_name}_{weight}_x{n}.json", "w") as f:
                json.dump(doc, f, indent=2)
                f.write("\n")
print("ok")
