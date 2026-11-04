"""Extract image-only illustrations from Harbor UI references and upscale them cleanly.
Text, controls and full screenshots are intentionally excluded; Compose renders those natively.
"""
from pathlib import Path
from PIL import Image, ImageEnhance, ImageFilter, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
REFERENCES = ROOT / "UI Referencias"
OUTPUT = ROOT / "Harbor.Android/app/src/main/res/drawable-nodpi"
SCALE = 4

# Source image crop boxes are measured in the 375 x 812 reference viewport.
CROPS = {
    "promo_shelf": ("6.png", (16, 340, 359, 445)),
    "promo_offers_art": ("7.png", (68, 365, 309, 482)),
    "promo_club_books": ("8.png", (244, 341, 359, 516)),
    "promo_tracking_art": ("9.png", (227, 375, 360, 493)),
    "community_qr": ("4.png", (26, 102, 124, 200)),
}

def upscale(source: Image.Image, output: Path, scale: int = SCALE) -> None:
    source = source.convert("RGBA")
    source = source.resize((source.width * scale, source.height * scale), Image.Resampling.LANCZOS)
    rgb = source.convert("RGB")
    rgb = ImageEnhance.Sharpness(rgb).enhance(1.18)
    rgb = ImageEnhance.Contrast(rgb).enhance(1.015)
    rgb.putalpha(source.getchannel("A"))
    rgb.save(output, optimize=True)

for name, (filename, bounds) in CROPS.items():
    image = Image.open(REFERENCES / filename).crop(bounds)
    upscale(image, OUTPUT / f"{name}.png")

# Round avatar crops are isolated image assets; chat text and bubble shapes stay native.
for name, bounds in {
    "community_avatar_cassiana": ("4.png", (31, 267, 87, 323)),
    "community_avatar_aryel": ("4.png", (31, 446, 87, 502)),
    "community_avatar_kevin": ("4.png", (31, 581, 87, 637)),
}.items():
    image = Image.open(REFERENCES / bounds[0]).crop(bounds[1]).convert("RGBA")
    # Keep the crop's original anti-aliased circle while upscaling it for current displays.
    upscale(image, OUTPUT / f"{name}.png")

# Recreate the small native bottom-tab glyphs as scalable vector drawables in Compose.
# The existing bitmap icon crops are not used because they magnify visibly pixelated.
print("Generated image-only reference assets at 4x with Lanczos resampling and mild sharpening.")
