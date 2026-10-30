#!/usr/bin/env python3
"""Make the plugins' icons from the logos in assets/ (Alerts, GemCrab, MultiTool, PvM, Skilling).

Usage: python3 scripts/update_icons.py   (needs Pillow: pip install pillow)

- assets/hub/<plugin>.png: each plugin's Plugin Hub icon (within the Hub's 48x72 limit); split_plugins.py puts
  it at the root of the plugin's repository as icon.png. The all-in-one's is also icon.png here.
- src/main/resources/com/bradsmultitool/icons/<plugin>.png: the sidebar tab icon (16x16).
"""
import os

from PIL import Image

ROOT = os.path.normpath(os.path.join(os.path.dirname(__file__), ".."))
ASSETS = os.path.join(ROOT, "assets")
TAB_DIR = os.path.join(ROOT, "src", "main", "resources", "com", "bradsmultitool", "icons")
# plugin id -> logo in assets/
LOGOS = {"multitool": "MultiTool", "skilltracker": "Skilling", "pvm": "PvM", "gemcrab": "GemCrab", "voices": "Alerts"}
HUB_WIDTH, HUB_HEIGHT = 48, 72
TAB_SIZE = 16


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


def hub_icon(logo):
    # A badge is about as tall as it's wide, so it's 48 wide and no taller than it needs to be
    height = min(HUB_HEIGHT, round(HUB_WIDTH * logo.height / logo.width))
    return fit(logo, HUB_WIDTH, height)


def main():
    os.makedirs(os.path.join(ASSETS, "hub"), exist_ok=True)
    os.makedirs(TAB_DIR, exist_ok=True)
    for plugin, name in LOGOS.items():
        logo = badge(name)
        hub_icon(logo).save(os.path.join(ASSETS, "hub", plugin + ".png"), optimize=True)
        fit(logo, TAB_SIZE, TAB_SIZE).save(os.path.join(TAB_DIR, plugin + ".png"), optimize=True)
    hub_icon(badge(LOGOS["multitool"])).save(os.path.join(ROOT, "icon.png"), optimize=True)
    print("Wrote Hub icons to assets/hub/, icon.png, and tab icons to %s" % os.path.relpath(TAB_DIR, ROOT))


if __name__ == "__main__":
    main()
