"""按关于页的插画重新生成应用图标。

用法（在仓库根目录）：

    uv run --with pillow python -P tools/gen-icons.py

`-P` 让脚本目录不进 sys.path，避免 tools/ 下的文件撞上标准库模块名。

写入：

- app/src/main/res/mipmap-*/ 的 ic_launcher、ic_launcher_foreground、ic_launcher_background
- docs/icon.png（README 用，512）
- web-ui/public/icon.png（Web UI 空状态用，256）
- web-ui/public/favicon.ico（48/64/128/256，全部用插画）

插画占 108dp 画布的 78%，相对画布中心左移 1.5%、上移 6%；圆角 20.8%，背景取插画主色 #F4EEEF。
改插画或参数后重跑一遍，再按 docs/custom/checklist.md 的 C90、C92、C94 核对。
"""

import os

from PIL import Image, ImageDraw

SRC = r"app/src/main/res/drawable-nodpi/about_logo.webp"
BG = (244, 238, 239, 255)
SCALE = 0.78
DX = -0.015
DY = -0.06
SS = 4
RADIUS = 0.208

img = Image.open(SRC).convert("RGBA")
w, h = img.size
side = min(w, h)
img = img.crop(
    ((w - side) // 2, (h - side) // 2, (w - side) // 2 + side, (h - side) // 2 + side)
)


def art_layer(size):
    s = size * SS
    art = img.resize((round(s * SCALE),) * 2, Image.LANCZOS)
    layer = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    x = round(s * (0.5 + DX) - art.width / 2)
    y = round(s * (0.5 + DY) - art.height / 2)
    layer.alpha_composite(art, (x, y))
    return layer


def round_out(canvas, size, radius_px):
    s = canvas.width
    mask = Image.new("L", (s, s), 0)
    ImageDraw.Draw(mask).rounded_rectangle(
        (0, 0, s - 1, s - 1), radius=radius_px, fill=255
    )
    out = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    out.paste(canvas, (0, 0), mask)
    return out.resize((size, size), Image.LANCZOS)


def icon(size):
    s = size * 2
    canvas = Image.new("RGBA", (s, s), BG)
    art = img.resize((round(s * SCALE),) * 2, Image.LANCZOS)
    x = round(s * (0.5 + DX) - art.width / 2)
    y = round(s * (0.5 + DY) - art.height / 2)
    canvas.alpha_composite(art, (x, y))
    return round_out(canvas, size, int(s * RADIUS))


def legacy(size):
    s = size * SS
    canvas = Image.new("RGBA", (s, s), BG)
    canvas.alpha_composite(art_layer(size))
    return round_out(canvas, size, round(RADIUS * s))


def foreground(size):
    return art_layer(size).resize((size, size), Image.LANCZOS)


def background(size):
    return Image.new("RGBA", (size, size), BG)


for name, scale in {
    "mdpi": 1,
    "hdpi": 1.5,
    "xhdpi": 2,
    "xxhdpi": 3,
    "xxxhdpi": 4,
}.items():
    d = f"app/src/main/res/mipmap-{name}"
    os.makedirs(d, exist_ok=True)
    legacy(round(48 * scale)).save(f"{d}/ic_launcher.png")
    foreground(round(108 * scale)).save(f"{d}/ic_launcher_foreground.png")
    background(round(108 * scale)).save(f"{d}/ic_launcher_background.png")

icon(512).save(r"docs/icon.png")
icon(256).save(r"web-ui/public/icon.png")

# favicon：统一用插画，标签页那枚 16px 由浏览器从 48 帧缩下去
FAVICON = [(48, 48), (64, 64), (128, 128), (256, 256)]
frames = [icon(s) for s, _ in FAVICON]
frames[-1].save(
    r"web-ui/public/favicon.ico", format="ICO", sizes=FAVICON, append_images=frames[:-1]
)
print("done")
