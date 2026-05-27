#!/usr/bin/env python3
from PIL import Image, ImageOps

INPUT_FILES = [
    "screenshots/01-home.jpeg",
    "screenshots/02-ready-to-download.jpeg",
    "screenshots/03-downloading.jpeg",
    "screenshots/04-completion-notification.jpeg",
]

OUTPUT_NAMES = [
    "screenshots/framed/01-home.png",
    "screenshots/framed/02-ready-to-download.png",
    "screenshots/framed/03-downloading.png",
    "screenshots/framed/04-completion-notification.png",
]

BORDER_COLOR = (229, 231, 235)
OUTER_BG = (11, 15, 23)
PADDING = 28
BORDER_WIDTH = 4

def frame_image(input_path, output_path):
    img = Image.open(input_path).convert("RGB")

    bordered = ImageOps.expand(img, border=BORDER_WIDTH, fill=BORDER_COLOR)

    canvas_size = (
        bordered.width + 2 * PADDING,
        bordered.height + 2 * PADDING,
    )
    canvas = Image.new("RGB", canvas_size, OUTER_BG)
    canvas.paste(bordered, (PADDING, PADDING))

    canvas.save(output_path, "PNG")
    print(f"  Saved {output_path} ({canvas.width}x{canvas.height})")

def main():
    for inp, out in zip(INPUT_FILES, OUTPUT_NAMES):
        frame_image(inp, out)

if __name__ == "__main__":
    main()
