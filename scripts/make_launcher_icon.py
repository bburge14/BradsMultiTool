"""Make assets/launcher.ico (Snuffy's Launcher's window and desktop shortcut icon) from the Toolbelt's
artwork, assets/MultiTool.png: trimmed to the badge, made square, at the sizes Windows uses."""
import os
from PIL import Image

ROOT = os.path.normpath(os.path.join(os.path.dirname(__file__), ".."))
art = Image.open(os.path.join(ROOT, "assets", "MultiTool.png")).convert("RGBA")
art = art.crop(art.getchannel("A").getbbox())
side = max(art.size)
square = Image.new("RGBA", (side, side), (0, 0, 0, 0))
square.paste(art, ((side - art.width) // 2, (side - art.height) // 2))
out = os.path.join(ROOT, "assets", "launcher.ico")
square.save(out, sizes=[(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)])
print("wrote", out)
