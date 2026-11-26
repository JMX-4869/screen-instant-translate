from PIL import Image, ImageDraw, ImageFont
import os

S = 512
img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
d = ImageDraw.Draw(img)

# 渐变底
grad = Image.new("RGB", (S, S))
gd = ImageDraw.Draw(grad)
c1 = (59, 130, 246)   # blue
c2 = (124, 58, 237)   # violet
for y in range(S):
    t = y / (S - 1)
    # 斜向渐变近似：按行混合
    r = int(c1[0] + (c2[0] - c1[0]) * t)
    g = int(c1[1] + (c2[1] - c1[1]) * t)
    b = int(c1[2] + (c2[2] - c1[2]) * t)
    gd.line([(0, y), (S, y)], fill=(r, g, b))

mask = Image.new("L", (S, S), 0)
ImageDraw.Draw(mask).rounded_rectangle([0, 0, S - 1, S - 1], radius=int(S * 0.235), fill=255)
img.paste(grad, (0, 0), mask)

# 对话气泡
bubble = Image.new("RGBA", (S, S), (0, 0, 0, 0))
bd = ImageDraw.Draw(bubble)
bx0, by0, bx1, by1 = int(S*0.19), int(S*0.20), int(S*0.81), int(S*0.63)
bd.rounded_rectangle([bx0, by0, bx1, by1], radius=int(S*0.10), fill=(255, 255, 255, 255))
# 尾巴
bd.polygon([(int(S*0.33), by1 - 6), (int(S*0.27), int(S*0.75)), (int(S*0.45), by1 - 6)], fill=(255, 255, 255, 255))
img.alpha_composite(bubble)

# 字
font = None
for p in (r"C:\Windows\Fonts\msyhbd.ttc", r"C:\Windows\Fonts\msyh.ttc", r"C:\Windows\Fonts\simhei.ttf"):
    if os.path.exists(p):
        font = ImageFont.truetype(p, int(S * 0.30))
        break
d2 = ImageDraw.Draw(img)
txt = "译"
bbox = d2.textbbox((0, 0), txt, font=font)
tw, th = bbox[2] - bbox[0], bbox[3] - bbox[1]
cx = S / 2 - tw / 2 - bbox[0]
cy = (by0 + by1) / 2 - th / 2 - bbox[1] - S * 0.005
d2.text((cx, cy), txt, font=font, fill=(37, 56, 122))

os.makedirs("out", exist_ok=True)
img.save("out/icon-512.png")
for name, size in (("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)):
    img.resize((size, size), Image.LANCZOS).save(f"out/ic_launcher_{name}.png")
print("ok")
