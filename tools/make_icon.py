"""生成 LinkGuard 应用图标（纯扁平 / 纯色拼搭）。

风格由命令行第一个参数决定：
    python make_icon.py green-shield     # 默认：白底 + 纯绿盾牌 + 白色链环（最接近 AdGuard）
    python make_icon.py blue-shield      #      白底 + 纯蓝盾牌 + 白色链环
    python make_icon.py blue-link-bar    #      纯蓝底 + 白色链环 + 红杠（两者垂直）
    python make_icon.py white-link-bar   #      白底 + 纯蓝链环 + 红杠

全部是纯色块拼接：无渐变、无柔光、无投影。

产物：
  res/mipmap-*/ic_launcher.png               传统方形图标（圆角方形满幅）
  res/mipmap-*/ic_launcher_round.png         圆形图标
  res/drawable-*/ic_launcher_background.png  自适应图标背景（纯色）
  res/drawable-*/ic_launcher_foreground.png  自适应图标前景（透明，内容在安全区内）
"""
import math
import os
import sys

from PIL import Image, ImageDraw

BASE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

LEGACY = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
ADAPTIVE = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}
MASTER = 1024

WHITE = (255, 255, 255, 255)
BLUE = (37, 99, 235, 255)        # #2563EB
GREEN = (22, 150, 90, 255)
RED = (239, 68, 68, 255)

GLYPH_IN_BADGE = 1.02        # 方形/圆形图标里图形占画布的比例
GLYPH_IN_FOREGROUND = 0.72   # 自适应图标前景里图形占画布的比例（安全区约 66%）

STYLES = {
    # 盾牌类图形本来就铺满画布，前景可以小一点
    "green-shield": {"kind": "shield", "bg": WHITE, "shield": GREEN, "glyph": WHITE,
                     "badge": 1.02, "fg": 0.72},
    "blue-shield": {"kind": "shield", "bg": WHITE, "shield": BLUE, "glyph": WHITE,
                    "badge": 1.02, "fg": 0.72},
    # 链环 + 红杠的实际外接框只有画布的 ~0.72，所以前景要放大才不显小
    "blue-link-bar": {"kind": "linkbar", "bg": BLUE, "link": WHITE, "bar": RED,
                      "badge": 1.02, "fg": 0.86},
    "white-link-bar": {"kind": "linkbar", "bg": WHITE, "link": BLUE, "bar": RED,
                       "badge": 1.02, "fg": 0.86},
}


def squircle(size, color):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    ImageDraw.Draw(img).rounded_rectangle(
        [0, 0, size - 1, size - 1], radius=int(size * 0.225), fill=color)
    return img


def bezier2(p0, p1, p2, n=48):
    out = []
    for i in range(n + 1):
        t = i / float(n)
        mt = 1 - t
        out.append((mt * mt * p0[0] + 2 * mt * t * p1[0] + t * t * p2[0],
                    mt * mt * p0[1] + 2 * mt * t * p1[1] + t * t * p2[1]))
    return out


def shield_mask(size, inset):
    """盾牌：圆角顶边 -> 直边 -> 收成带圆角的尖底。"""
    s = size
    x0, x1 = inset, s - inset
    y0 = inset
    y1 = s - inset * 0.7
    w = x1 - x0
    cx = s / 2.0
    r = w * 0.11
    ys = y0 + (y1 - y0) * 0.40

    mask = Image.new("L", (s, s), 0)
    d = ImageDraw.Draw(mask)
    d.rounded_rectangle([x0, y0, x1, ys + r], radius=r, fill=255)
    left = bezier2((x0, ys), (x0 + w * 0.05, y1 - (y1 - ys) * 0.34), (cx, y1))
    right = bezier2((cx, y1), (x1 - w * 0.05, y1 - (y1 - ys) * 0.34), (x1, ys))
    d.polygon(left + right, fill=255)
    return mask


def paste_masked(img, color, mask):
    layer = Image.new("RGBA", img.size, color)
    layer.putalpha(mask)
    return Image.alpha_composite(img, layer)


def ring(img, c, w, h, stroke, color, angle):
    """链环：竖直胶囊环旋转 angle 度（负值=顺时针，顶部往右倒 => / 方向）。"""
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    ImageDraw.Draw(layer).rounded_rectangle(
        [c - w / 2, c - h / 2, c + w / 2, c + h / 2],
        radius=w / 2, outline=color, width=stroke)
    return Image.alpha_composite(img, layer.rotate(angle, resample=Image.BICUBIC))


def bar(img, c, half, thickness, color, angle_deg=45):
    """圆头斜杠：angle_deg=45 是左上到右下（\\ 方向），与 / 的链环垂直。"""
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    a = math.radians(angle_deg)
    dx, dy = half * math.cos(a), half * math.sin(a)
    p1, p2 = (c - dx, c - dy), (c + dx, c + dy)
    d.line([p1, p2], fill=color, width=thickness)
    r = thickness / 2.0
    for p in (p1, p2):
        d.ellipse([p[0] - r, p[1] - r, p[0] + r, p[1] + r], fill=color)
    return Image.alpha_composite(img, layer)


def draw_glyph(style, size):
    """在透明画布上画主体图形。"""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    c = size / 2.0
    if style["kind"] == "shield":
        img = paste_masked(img, style["shield"], shield_mask(size, size * 0.135))
        return ring(img, c, size * 0.30, size * 0.45, int(size * 0.056), style["glyph"], -40)
    img = ring(img, c, size * 0.38, size * 0.66, int(size * 0.080), style["link"], -42)
    return bar(img, c, size * 0.345, int(size * 0.092), style["bar"], 45)


def render_badge(style, size, mask, glyph_ratio):
    img = squircle(size, style["bg"])
    inner = int(round(size * glyph_ratio))
    g = draw_glyph(style, inner)
    off = (size - inner) // 2
    img.paste(g, (off, off), g)
    img.putalpha(mask)
    return img


def render_foreground(style, size, ratio=None):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    inner = int(round(size * (ratio if ratio is not None else GLYPH_IN_FOREGROUND)))
    g = draw_glyph(style, inner)
    off = (size - inner) // 2
    img.paste(g, (off, off), g)
    return img


def rounded_mask(size, radius_ratio):
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).rounded_rectangle(
        [0, 0, size - 1, size - 1], radius=int(size * radius_ratio), fill=255)
    return mask


def circle_mask(size):
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).ellipse([0, 0, size - 1, size - 1], fill=255)
    return mask


def main():
    key = sys.argv[1] if len(sys.argv) > 1 else "blue-link-bar"
    if key not in STYLES:
        print("unknown style: %s ; available: %s" % (key, ", ".join(sorted(STYLES))))
        raise SystemExit(2)
    style = STYLES[key]
    print("style = %s" % key)

    badge_ratio = style.get("badge", GLYPH_IN_BADGE)
    fg_ratio = style.get("fg", GLYPH_IN_FOREGROUND)
    badge_square = render_badge(style, MASTER, rounded_mask(MASTER, 0.225), badge_ratio)
    badge_round = render_badge(style, MASTER, circle_mask(MASTER), badge_ratio * 0.88)
    master_bg = squircle(MASTER, style["bg"])
    master_fg = render_foreground(style, MASTER, fg_ratio)

    for name, px in LEGACY.items():
        d1 = os.path.join(BASE, "res", "mipmap-" + name)
        os.makedirs(d1, exist_ok=True)
        badge_square.resize((px, px), Image.LANCZOS).save(
            os.path.join(d1, "ic_launcher.png"), optimize=True)
        badge_round.resize((px, px), Image.LANCZOS).save(
            os.path.join(d1, "ic_launcher_round.png"), optimize=True)

    for name, px in ADAPTIVE.items():
        d2 = os.path.join(BASE, "res", "drawable-" + name)
        os.makedirs(d2, exist_ok=True)
        master_bg.resize((px, px), Image.LANCZOS).save(
            os.path.join(d2, "ic_launcher_background.png"), optimize=True)
        master_fg.resize((px, px), Image.LANCZOS).save(
            os.path.join(d2, "ic_launcher_foreground.png"), optimize=True)

    print("done")


if __name__ == "__main__":
    main()
