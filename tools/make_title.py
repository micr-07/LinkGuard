# -*- coding: utf-8 -*-
"""用 Afacad Flux 渲染 LinkGuard 标题 → 各档密度的透明 PNG（纯白，当遮罩用）。

思路：headless Chrome 加载本地 TTF（@font-face），把文字画在透明画布上截屏，
再用 PIL 裁掉透明边、缩放到各密度，落进 res/drawable-{mdpi,xhdpi,xxhdpi}/。
"""
import os
import subprocess
import sys

from PIL import Image

ROOT = r"D:\工作\应用逆向\构建APK\LinkGuard"
TMP = r"C:\Users\micr07\AppData\Local\Temp\lgsh\titlefont"
CHROME = r"C:\Program Files\Google\Chrome\Application\chrome.exe"
TEXT = "LinkGuard"

# 目标：1x 下墨迹高 24dp（原 22sp 标题的视觉高度），保持宽高比
TARGET_H_DP = 24

if not os.path.isdir(TMP):
    os.makedirs(TMP)

ttf = os.path.join(TMP, "AfacadFlux-600.ttf")
if not os.path.isfile(ttf):
    print("缺少字体文件: " + ttf)
    sys.exit(1)

# ---- 1) 生成 HTML（大尺寸渲染，方便缩小时保持锐利）----
font_url = "file:///" + ttf.replace("\\", "/")
html = """<!doctype html>
<html><head><meta charset="utf-8">
<style>
  @font-face {
    font-family: 'AfacadFlux';
    src: url('%s') format('truetype');
    font-weight: 600;
    font-style: normal;
  }
  html, body { margin:0; padding:0; background:transparent; }
  #t {
    font-family: 'AfacadFlux';
    font-weight: 600;
    font-size: 400px;
    line-height: 1;
    color: #FFFFFF;
    white-space: nowrap;
    display: inline-block;
    padding: 60px;
  }
</style></head>
<body><div id="t">%s</div></body></html>
""" % (font_url, TEXT)

html_path = os.path.join(TMP, "title.html")
with open(html_path, "w", encoding="utf-8") as f:
    f.write(html)

# ---- 2) 截屏 ----
raw = os.path.join(TMP, "raw.png")
if os.path.exists(raw):
    os.remove(raw)
cmd = [
    CHROME, "--headless", "--disable-gpu", "--no-sandbox", "--hide-scrollbars",
    "--default-background-color=00000000",
    "--force-device-scale-factor=1",
    "--window-size=3000,900",
    "--screenshot=" + raw,
    "file:///" + html_path.replace("\\", "/"),
]
subprocess.run(cmd, capture_output=True, timeout=120)
if not os.path.isfile(raw):
    print("Chrome 渲染失败")
    sys.exit(1)

img = Image.open(raw).convert("RGBA")
bbox = img.getbbox()          # 自动找非透明区域
if bbox is None:
    print("渲染出来是空白")
    sys.exit(1)
ink = img.crop(bbox)
print("  原始墨迹: %dx%d" % ink.size)

# ---- 3) 三档密度 ----
scales = [("mdpi", 1), ("hdpi", 1.5), ("xhdpi", 2), ("xxhdpi", 3), ("xxxhdpi", 4)]
for name, k in scales:
    h = round(TARGET_H_DP * k)
    w = max(1, round(ink.size[0] * h / ink.size[1]))
    out_dir = os.path.join(ROOT, "res", "drawable-" + name)
    if not os.path.isdir(out_dir):
        os.makedirs(out_dir)
    resized = ink.resize((w, h), Image.LANCZOS)
    out = os.path.join(out_dir, "logo_linkguard.png")
    resized.save(out, "PNG", optimize=True)
    print("  drawable-%-8s %3dx%-3d  %d 字节" % (name, w, h, os.path.getsize(out)))
