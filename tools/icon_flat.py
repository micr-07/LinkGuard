"""扁平化图标候选 v2：修正盾牌形状 + 链环/红杠垂直。"""
import math
import os

from PIL import Image, ImageDraw

BASE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(BASE, "tools", "_flat")
SIZE = 768
FINAL = 192

BLUE = (37, 99, 235, 255)       # #2563EB
GREEN = (22, 150, 90, 255)
WHITE = (255, 255, 255, 255)
RED = (239, 68, 68, 255)


def squircle(size, color):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    ImageDraw.Draw(img).rounded_rectangle(
        [0, 0, size - 1, size - 1], radius=int(size * 0.225), fill=color)
    return img


def bezier2(p0, p1, p2, n=40):
    out = []
    for i in range(n + 1):
        t = i / float(n)
        mt = 1 - t
        out.append((mt * mt * p0[0] + 2 * mt * t * p1[0] + t * t * p2[0],
                    mt * mt * p0[1] + 2 * mt * t * p1[1] + t * t * p2[1]))
    return out


def shield_mask(size, inset):
    """正经的盾牌：圆角顶边 -> 直边 -> 收成（略圆的）尖底。"""
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


def put(img, color, mask):
    layer = Image.new("RGBA", img.size, color)
    layer.putalpha(mask)
    return Image.alpha_composite(img, layer)


def ring(img, c, w, h, stroke, color, angle):
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    ImageDraw.Draw(layer).rounded_rectangle(
        [c - w / 2, c - h / 2, c + w / 2, c + h / 2],
        radius=w / 2, outline=color, width=stroke)
    return Image.alpha_composite(img, layer.rotate(angle, resample=Image.BICUBIC))


def bar(img, c, half, thickness, color, angle_deg=45):
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


def a_blue_link_bar(size):
    """纯色蓝底 + 白链环(/) + 红杠(\\)"""
    img = squircle(size, BLUE)
    c = size / 2.0
    img = ring(img, c, size * 0.38, size * 0.66, int(size * 0.080), WHITE, -42)
    img = bar(img, c, size * 0.345, int(size * 0.092), RED, 45)
    return img


def b_white_link_bar(size):
    """白底 + 蓝链环(/) + 红杠(\\)"""
    img = squircle(size, WHITE)
    c = size / 2.0
    img = ring(img, c, size * 0.38, size * 0.66, int(size * 0.080), BLUE, -42)
    img = bar(img, c, size * 0.345, int(size * 0.092), RED, 45)
    return img


def c_green_shield_link(size):
    """AdGuard 式：白底 + 纯绿盾牌 + 白链环"""
    img = squircle(size, WHITE)
    img = put(img, GREEN, shield_mask(size, size * 0.155))
    c = size / 2.0
    img = ring(img, c, size * 0.28, size * 0.42, int(size * 0.052), WHITE, -40)
    return img


def d_blue_shield_link(size):
    """白底 + 纯蓝盾牌 + 白链环"""
    img = squircle(size, WHITE)
    img = put(img, BLUE, shield_mask(size, size * 0.155))
    c = size / 2.0
    img = ring(img, c, size * 0.28, size * 0.42, int(size * 0.052), WHITE, -40)
    return img


def e_blue_shield_link_bar(size):
    """白底 + 纯蓝盾牌 + 白链环(/) + 红杠(\\)"""
    img = squircle(size, WHITE)
    img = put(img, BLUE, shield_mask(size, size * 0.145))
    c = size * 0.5
    img = ring(img, c, size * 0.26, size * 0.38, int(size * 0.046), WHITE, -40)
    img = bar(img, c, size * 0.195, int(size * 0.052), RED, 45)
    return img


def f_blue_shield_slash(size):
    """白底 + 纯蓝盾牌 + 红杠（最简：盾牌即防线）"""
    img = squircle(size, WHITE)
    img = put(img, BLUE, shield_mask(size, size * 0.155))
    img = bar(img, size / 2.0, size * 0.185, int(size * 0.058), RED, 45)
    return img


def main():
    os.makedirs(OUT, exist_ok=True)
    variants = (
        ("a-blue-link-bar", a_blue_link_bar),
        ("b-white-link-bar", b_white_link_bar),
        ("c-green-shield-link", c_green_shield_link),
        ("d-blue-shield-link", d_blue_shield_link),
        ("e-blue-shield-link-bar", e_blue_shield_link_bar),
        ("f-blue-shield-slash", f_blue_shield_slash),
    )
    for name, fn in variants:
        fn(SIZE).resize((FINAL, FINAL), Image.LANCZOS).save(os.path.join(OUT, name + ".png"))
        print(name)


if __name__ == "__main__":
    main()
