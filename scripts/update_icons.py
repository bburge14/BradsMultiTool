#!/usr/bin/env python3
"""Make the plugins' icons from the logos in assets/ (Alerts, GemCrab, MultiTool, PvM, Skilling, TickCounter).

Usage: python3 scripts/update_icons.py   (needs Pillow: pip install pillow)

- assets/hub/<plugin>.png: each plugin's Plugin Hub icon (within the Hub's 48x72 limit); split_plugins.py puts
  it at the root of the plugin's folder under plugins/ as icon.png. The Toolbelt's is also icon.png here.
- src/main/resources/com/bradsmultitool/icons/skilltracker.png: the Skill Tracker's sidebar tab icon (16x16),
  the Skilling logo, in the all-in-one too.
"""
import os

from PIL import Image, ImageChops, ImageEnhance, ImageFilter

ROOT = os.path.normpath(os.path.join(os.path.dirname(__file__), ".."))
ASSETS = os.path.join(ROOT, "assets")
TAB_DIR = os.path.join(ROOT, "src", "main", "resources", "com", "bradsmultitool", "icons")
# plugin id -> logo in assets/
LOGOS = {"multitool": "MultiTool", "skilltracker": "Skilling", "pvm": "PvM", "gemcrab": "GemCrab", "voices": "Alerts",
         "tickcounter": "TickCounter"}
HUB_WIDTH, HUB_HEIGHT = 48, 72
TAB_SIZE = 16
# RuneLite's own tab icons leave a pixel or so of space around them
TAB_ART = 13


def badge(name):
    """The logo cropped to the badge itself (the files have empty space around it)."""
    image = Image.open(os.path.join(ASSETS, name + ".png")).convert("RGBA")
    return image.crop(image.getchannel("A").point(lambda a: 255 if a > 8 else 0).getbbox())


def fit(image, width, height):
    """Scaled to fit width x height, centred on a transparent canvas of that size."""
    scale = min(width / image.width, height / image.height)
    resized = image.resize((max(1, round(image.width * scale)), max(1, round(image.height * scale))), Image.LANCZOS)
    canvas = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    canvas.paste(resized, ((width - resized.width) // 2, (height - resized.height) // 2), resized)
    return canvas


def brighter(image, brightness=1.45, contrast=1.25):
    """The badge lifted so it stands out on the Hub's dark background."""
    rgb = ImageEnhance.Contrast(ImageEnhance.Brightness(image.convert("RGB")).enhance(brightness)).enhance(contrast)
    rgb.putalpha(image.getchannel("A"))
    return rgb


def glyph(logo, inner=0.5, low=0.4, span=0.3, thicken=5):
    """Just the badge's glowing symbol, solid bright cyan and slightly thickened, for the 16px sidebar tab
    (RuneLite shrinks tab icons to 16x16, where the stone frame would only make it smaller and darker)."""
    side = min(logo.size) * inner
    left, top = (logo.width - side) / 2, (logo.height - side) / 2
    middle = logo.crop((int(left), int(top), int(left + side), int(top + side)))
    # How bright the glow is (the brighter of green and blue), stretched, times the badge's own transparency
    glow = ImageChops.lighter(middle.getchannel("G"), middle.getchannel("B"))
    glow = glow.point(lambda v: min(255, max(0, int((v / 255 - low) / span * 255))))
    mask = ImageChops.multiply(glow, middle.getchannel("A"))
    mask = mask.filter(ImageFilter.MaxFilter(thicken))
    out = Image.new("RGBA", middle.size, (110, 235, 255, 0))
    out.putalpha(mask)
    return out.crop(mask.point(lambda v: 255 if v > 60 else 0).getbbox())


def hub_icon(logo):
    # A badge is about as tall as it's wide, so it's 48 wide and no taller than it needs to be
    height = min(HUB_HEIGHT, round(HUB_WIDTH * logo.height / logo.width))
    return fit(logo, HUB_WIDTH, height)


def main():
    os.makedirs(os.path.join(ASSETS, "hub"), exist_ok=True)
    os.makedirs(TAB_DIR, exist_ok=True)
    for plugin, name in LOGOS.items():
        logo = badge(name)
        hub_icon(brighter(logo)).save(os.path.join(ASSETS, "hub", plugin + ".png"), optimize=True)
    # The only sidebar tab is the Skill Tracker's
    tab = Image.new("RGBA", (TAB_SIZE, TAB_SIZE), (0, 0, 0, 0))
    art = fit(glyph(badge(LOGOS["skilltracker"])), TAB_ART, TAB_ART)
    tab.paste(art, ((TAB_SIZE - TAB_ART) // 2 + 1, (TAB_SIZE - TAB_ART) // 2 + 1), art)
    tab.save(os.path.join(TAB_DIR, "skilltracker.png"), optimize=True)
    hub_icon(brighter(badge(LOGOS["multitool"]))).save(os.path.join(ROOT, "icon.png"), optimize=True)
    print("Wrote Hub icons to assets/hub/, icon.png, and tab icons to %s" % os.path.relpath(TAB_DIR, ROOT))


if __name__ == "__main__":
    main()
