#!/usr/bin/env python3
"""Renders the Play Store icon (512x512) and feature graphic (1024x500).

The artwork is the launcher icon from android/app/src/main/res/drawable/
(ic_launcher_foreground.xml on ic_launcher_background.xml) redrawn with Pillow, so the
store listing matches the installed app. Output goes where the Gradle Play Publisher
plugin picks it up for `publishListing`.

    pip install pillow
    python3 scripts/generate_play_graphics.py

Set FONT_BOLD / FONT_REGULAR to TrueType files if the defaults are not installed.
"""

import os
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

GRAPHICS = Path("android/app/src/main/play/listings/en-US/graphics")
GREEN = "#1B5E4F"
WHITE = "#FFFFFF"
SUPERSAMPLE = 4
FONT_BOLD = os.environ.get("FONT_BOLD", "/usr/share/fonts/noto/NotoSans-Bold.ttf")
FONT_REGULAR = os.environ.get("FONT_REGULAR", "/usr/share/fonts/noto/NotoSans-Regular.ttf")
TITLE = "KeepSheet"
TAGLINE = "Scan, organize, and merge documents into clean, searchable PDFs."


def draw_sheet(draw: ImageDraw.ImageDraw, to_px) -> None:
    """The launcher icon's document, in its 108x108 viewport coordinates."""

    def box(x0, y0, x1, y1):
        return [*to_px(x0, y0), *to_px(x1, y1)]

    draw.rounded_rectangle(box(38, 26, 70, 86), radius=to_px(4, 0)[0] - to_px(0, 0)[0], fill=WHITE)
    # Folded corner: cut it away, then draw the fold.
    draw.polygon([to_px(58, 26), to_px(70, 26), to_px(70, 38)], fill=GREEN)
    draw.polygon([to_px(58, 26), to_px(70, 38), to_px(58, 38)], fill=GREEN)
    width = round(3 * (to_px(1, 0)[0] - to_px(0, 0)[0]))
    for x0, x1, y in ((42, 62, 52), (42, 62, 60), (42, 56, 68)):
        a, b = to_px(x0, y), to_px(x1, y)
        draw.line([a, b], fill=GREEN, width=width)
        for cx, cy in (a, b):
            draw.ellipse([cx - width / 2, cy - width / 2, cx + width / 2, cy + width / 2], fill=GREEN)


def sheet_image(size: int, window: float, center: tuple[float, float]) -> Image.Image:
    """A `size`x`size` square showing `window` viewport units around `center`."""
    big = size * SUPERSAMPLE
    scale = big / window
    ox, oy = center[0] - window / 2, center[1] - window / 2
    image = Image.new("RGB", (big, big), GREEN)
    draw_sheet(ImageDraw.Draw(image), lambda x, y: ((x - ox) * scale, (y - oy) * scale))
    return image.resize((size, size), Image.LANCZOS)


def wrap(draw, text, font, max_width):
    lines, line = [], ""
    for word in text.split():
        candidate = f"{line} {word}".strip()
        if draw.textlength(candidate, font=font) <= max_width:
            line = candidate
        else:
            lines.append(line)
            line = word
    return [*lines, line]


def feature_graphic() -> Image.Image:
    width, height = 1024, 500
    image = Image.new("RGB", (width, height), GREEN)
    sheet = sheet_image(360, window=70, center=(54, 56))
    image.paste(sheet, (70, (height - 360) // 2))
    draw = ImageDraw.Draw(image)
    title_font = ImageFont.truetype(FONT_BOLD, 96)
    tag_font = ImageFont.truetype(FONT_REGULAR, 34)
    left, max_width = 470, width - 470 - 50
    lines = wrap(draw, TAGLINE, tag_font, max_width)
    line_height = 46
    block = 96 + 24 + line_height * len(lines)
    top = (height - block) // 2 - 10
    draw.text((left, top), TITLE, font=title_font, fill=WHITE)
    for i, text in enumerate(lines):
        draw.text((left, top + 96 + 24 + i * line_height), text, font=tag_font, fill="#CFE6DF")
    return image


def main() -> None:
    (GRAPHICS / "icon").mkdir(parents=True, exist_ok=True)
    (GRAPHICS / "feature-graphic").mkdir(parents=True, exist_ok=True)
    sheet_image(512, window=88, center=(54, 56)).save(GRAPHICS / "icon" / "icon.png", optimize=True)
    feature_graphic().save(GRAPHICS / "feature-graphic" / "feature-graphic.png", optimize=True)


if __name__ == "__main__":
    main()
