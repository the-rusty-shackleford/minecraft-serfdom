# Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later
"""Serfdom's pixel art, drawn by hand in code: the Work Post's wood and board, the chain lead,
and the sheet of need icons. Run from the repo root:

    uv run --no-project --with pillow python devtools/art/art.py

Every pixel is placed here; nothing is traced or taken from vanilla's textures. Palettes follow
vanilla's oak and iron families so the pieces sit in the game's look.
"""
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
TEX = ROOT / "src/main/resources/assets/serfdom/textures"

# Oak-family browns, dark to light.
OAK = ["#4b3720", "#5e4527", "#73552f", "#8a6a3c", "#a07d48", "#b69257"]
# Iron, dark to light.
IRON = ["#2b2c30", "#46484e", "#6b6e75", "#9a9da4", "#c4c7cc", "#e6e8ea"]
INK = "#2a211b"
CREAM = "#efe4c4"


def hexrgb(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def canvas(w=16, h=16):
    return Image.new("RGBA", (w, h), (0, 0, 0, 0))


def put(img, x, y, colour):
    if 0 <= x < img.width and 0 <= y < img.height:
        img.putpixel((x, y), hexrgb(colour))


def post_wood():
    """A squared post: vertical grain, darker edges, two knots."""
    img = canvas()
    for x in range(16):
        for y in range(16):
            band = (x * 7 + (y // 5) * 3) % 11
            shade = 2 if band < 4 else 3 if band < 8 else 4
            if x in (0, 15):
                shade = 1
            if (x + y * 3) % 13 == 0:
                shade = max(1, shade - 1)
            put(img, x, y, OAK[shade])
    for kx, ky in ((5, 4), (10, 11)):
        put(img, kx, ky, OAK[0])
        put(img, kx + 1, ky, OAK[1])
        put(img, kx, ky + 1, OAK[1])
    return img


def post_top():
    """The post's end grain: rings around a dark heart."""
    img = canvas()
    for x in range(16):
        for y in range(16):
            d = max(abs(x - 7.5), abs(y - 7.5))
            ring = int(d) % 3
            put(img, x, y, OAK[[2, 3, 4][ring]] if d < 7 else OAK[1])
    put(img, 7, 7, OAK[0]); put(img, 8, 8, OAK[0]); put(img, 7, 8, OAK[1]); put(img, 8, 7, OAK[1])
    return img


def board():
    """The board's face: the model shows columns 1 to 14 and rows 3 to 11 of this texture, framed
    darker, with an axe and a hoe burnt into it, crossed. Planks fill the rest for the particles."""
    img = canvas()
    for x in range(16):
        for y in range(16):
            shade = 4 if (y // 3) % 2 == 0 else 3
            if (x * 5 + y) % 9 == 0:
                shade -= 1
            put(img, x, y, OAK[shade])
    for x in range(1, 15):
        put(img, x, 3, OAK[1]); put(img, x, 11, OAK[1])
    for y in range(3, 12):
        put(img, 1, y, OAK[1]); put(img, 14, y, OAK[1])
    # The axe: handle rising to the right, the wedge of its head at the top right.
    for x, y in ((5, 10), (6, 9), (7, 8), (8, 7), (9, 6), (10, 5)):
        put(img, x, y, INK)
    for x, y in ((10, 4), (11, 4), (11, 5), (12, 4), (12, 5), (12, 6), (11, 6)):
        put(img, x, y, INK)
    # The hoe: handle rising to the left, its blade a hook at the top left.
    for x, y in ((10, 10), (9, 9), (8, 8), (7, 7), (6, 6), (5, 5)):
        put(img, x, y, INK)
    for x, y in ((4, 5), (3, 5), (3, 6), (3, 7)):
        put(img, x, y, INK)
    return img


def chain_lead():
    """A loop of iron links with a hand loop, the lead's shape in iron: links alternate light and
    dark so the chain reads at a glance."""
    img = canvas()
    # The links run along a loop: a list of link centres, each drawn as a 2x2 ring.
    path = [(3, 12), (4, 10), (5, 8), (6, 6), (8, 5), (10, 4), (12, 4), (13, 6), (12, 8), (10, 9), (8, 10), (7, 12), (6, 14)]
    for i, (cx, cy) in enumerate(path):
        light = i % 2 == 0
        edge, body = (IRON[1], IRON[4]) if light else (IRON[0], IRON[2])
        for dx in (0, 1):
            for dy in (0, 1):
                put(img, cx + dx, cy + dy, body)
        put(img, cx - 1, cy, edge); put(img, cx + 2, cy + 1, edge)
        put(img, cx, cy - 1, edge); put(img, cx + 1, cy + 2, edge)
        if light:
            put(img, cx, cy, IRON[5])
    # The hand loop: a leather-brown ring at the end.
    for x, y in ((1, 13), (2, 14), (1, 14), (2, 12), (3, 15), (1, 15)):
        put(img, x, y, OAK[1])
    return img


def bubble():
    """A 16x16 cream bubble with a dark rim, the icons' ground, readable over sky and grass."""
    img = canvas()
    for x in range(1, 15):
        for y in range(1, 15):
            corner = (x in (1, 14)) and (y in (1, 14))
            if not corner:
                put(img, x, y, CREAM)
    for i in range(2, 14):
        put(img, i, 0, INK); put(img, i, 15, INK); put(img, 0, i, INK); put(img, 15, i, INK)
    for x, y in ((1, 1), (14, 1), (1, 14), (14, 14)):
        put(img, x, y, INK)
    return img


def icon_bed(img, ox):
    red, dark, white = "#b0302a", "#7a1f1b", "#e8e8e8"
    for x in range(3, 13):
        for y in range(8, 11):
            put(img, ox + x, y, red if x > 5 else white)
    for x in range(3, 13):
        put(img, ox + x, 11, dark)
    for y in range(6, 13):
        put(img, ox + 3, y, OAK[1])
    put(img, ox + 12, 12, OAK[1]); put(img, ox + 3, 12, OAK[1])


def icon_hungry(img, ox):
    # An empty wooden bowl, its dark inside showing over the rim: hungry (D-0005).
    for x in range(3, 13):
        put(img, ox + x, 7, OAK[4])
    for x in range(4, 12):
        put(img, ox + x, 8, "#3b3026")
    for x, y0 in ((3, 8), (12, 8)):
        put(img, ox + x, y0, OAK[3])
    for x in range(3, 13):
        put(img, ox + x, 9, OAK[3])
    for x in range(4, 12):
        put(img, ox + x, 10, OAK[2])
    for x in range(5, 11):
        put(img, ox + x, 11, OAK[1])
    for x in range(6, 10):
        put(img, ox + x, 12, OAK[0])


def icon_tool(img, ox):
    # An axe, greyed, with a red stroke through it: no tool.
    for i in range(7):
        put(img, ox + 4 + i, 11 - i, OAK[2])
    for x, y in ((9, 3), (10, 3), (11, 3), (10, 4), (11, 4), (12, 4), (11, 5), (12, 5), (12, 3)):
        put(img, ox + x, y, IRON[2])
    for i in range(10):
        put(img, ox + 3 + i, 3 + i, "#c8322b")


def icon_station(img, ox):
    # An anvil, greyed, with a red stroke through it: no station to work at (D-0002).
    for x in range(3, 13):
        put(img, ox + x, 4, IRON[4])
        put(img, ox + x, 5, IRON[3])
    put(img, ox + 2, 5, IRON[3])
    for x in range(6, 10):
        for y in range(6, 9):
            put(img, ox + x, y, IRON[2])
    for x in range(4, 12):
        put(img, ox + x, 9, IRON[3])
        put(img, ox + x, 10, IRON[2])
        put(img, ox + x, 11, IRON[1])
    for i in range(10):
        put(img, ox + 3 + i, 3 + i, "#c8322b")


def icon_fuel(img, ox):
    # A flame over a lump of coal.
    for x, y in ((7, 4), (8, 5), (7, 5), (6, 6), (7, 6), (8, 6), (9, 6), (6, 7), (7, 7), (8, 7), (9, 7)):
        put(img, ox + x, y, "#f0a020")
    for x, y in ((7, 6), (8, 7), (7, 7)):
        put(img, ox + x, y, "#f6d860")
    for x in range(5, 11):
        for y in range(9, 12):
            put(img, ox + x, y, "#2d2d2d" if (x + y) % 3 else "#4a4a4a")


def icon_materials(img, ox):
    # An empty open crate.
    for x in range(3, 13):
        put(img, ox + x, 6, OAK[1]); put(img, ox + x, 12, OAK[1])
    for y in range(6, 13):
        put(img, ox + 3, y, OAK[1]); put(img, ox + 12, y, OAK[1])
    for x in range(4, 12):
        for y in range(7, 12):
            put(img, ox + x, y, OAK[4] if y in (7, 11) else "#3b3026")


def icon_chest_full(img, ox):
    # A chest with its lid forced up by the stack inside.
    for x in range(3, 13):
        for y in range(8, 13):
            put(img, ox + x, y, OAK[3] if y != 10 else OAK[1])
    for x in range(3, 13):
        put(img, ox + x, 13, OAK[0])
    for x in range(4, 12):
        put(img, ox + x, 5, OAK[3])
    for x, y in ((4, 6), (11, 6)):
        put(img, ox + x, y, OAK[1])
    for x in range(5, 11):
        put(img, ox + x, 7, "#d8b040")
    put(img, ox + 7, 9, IRON[4]); put(img, ox + 8, 9, IRON[4])


def needs():
    """One 16x16 bubble per need, in the order Need declares them."""
    icons = [icon_bed, icon_hungry, icon_tool, icon_station, icon_fuel, icon_materials, icon_chest_full]
    sheet = canvas(16 * len(icons), 16)
    ground = bubble()
    for i, draw in enumerate(icons):
        sheet.paste(ground, (16 * i, 0), ground)
        draw(sheet, 16 * i)
    return sheet


def main():
    (TEX / "block").mkdir(parents=True, exist_ok=True)
    (TEX / "item").mkdir(parents=True, exist_ok=True)
    (TEX / "gui").mkdir(parents=True, exist_ok=True)
    post_wood().save(TEX / "block/work_post.png")
    post_top().save(TEX / "block/work_post_top.png")
    board().save(TEX / "block/work_post_board.png")
    chain_lead().save(TEX / "item/chain_lead.png")
    needs().save(TEX / "gui/needs.png")
    print("wrote", sorted(str(p.relative_to(ROOT)) for p in TEX.rglob("*.png")))


if __name__ == "__main__":
    main()
