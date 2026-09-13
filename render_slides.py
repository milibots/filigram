import os, math, random
from PIL import Image, ImageDraw, ImageFilter, ImageFont

welcome_path = r'C:\Users\mili\.gemini\antigravity-ide\brain\99f8f468-14dd-45fd-8d1a-013ad112284c\mascot_slide_welcome_1789190949680.jpg'
mascot_path = r'C:\Users\mili\.gemini\antigravity-ide\brain\99f8f468-14dd-45fd-8d1a-013ad112284c\filigram_mascot_rooster_1789189653631.jpg'
out_dir = r'D:\filigram\app\src\main\res\drawable'

# --- Slide 1: Welcome VIP Thumbs-up ---
img1 = Image.open(welcome_path).convert("RGBA")
img1.convert("RGB").save(os.path.join(out_dir, "mascot_slide_1.jpg"), quality=95)
print("Slide 1 saved.")

# --- Slide 2: Mascot eating Cinema Popcorn ---
base2 = Image.open(welcome_path).convert("RGBA")
w, h = base2.size

# Let's draw an awesome 3D cinema popcorn bucket in front of him!
overlay2 = Image.new("RGBA", (w, h), (0, 0, 0, 0))
draw2 = ImageDraw.Draw(overlay2)

# Bucket position: center-bottom, overlapping chest
bx1, by1 = w * 0.30, h * 0.58
bx2, by2 = w * 0.70, h * 0.94

# Draw bucket polygon (trapezoid tapering down)
poly = [
    (bx1, by1 + 50),
    (bx2, by1 + 50),
    (bx2 - 35, by2),
    (bx1 + 35, by2)
]

# Bucket background: dark red and gold stripes
bucket_img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
b_draw = ImageDraw.Draw(bucket_img)
b_draw.polygon(poly, fill=(160, 20, 25, 250), outline=(212, 175, 55, 255))

# Draw gold stripes on bucket
stripes = 7
for i in range(stripes):
    if i % 2 == 1:
        t1 = i / stripes
        t2 = (i + 1) / stripes
        p1 = (bx1 + (bx2 - bx1) * t1, by1 + 50)
        p2 = (bx1 + (bx2 - bx1) * t2, by1 + 50)
        bot_x1 = (bx1 + 35) + ((bx2 - 35) - (bx1 + 35)) * t1
        bot_x2 = (bx1 + 35) + ((bx2 - 35) - (bx1 + 35)) * t2
        b_draw.polygon([p1, p2, (bot_x2, by2), (bot_x1, by2)], fill=(212, 175, 55, 240))

# Gold emblem on the bucket: "FILIGRAM POPCORN"
cx = (bx1 + bx2) / 2
cy = (by1 + 50 + by2) / 2
b_draw.ellipse([cx - 70, cy - 35, cx + 70, cy + 35], fill=(15, 15, 15, 240), outline=(255, 215, 0, 255), width=3)
# Draw gold stars inside emblem
b_draw.polygon([(cx, cy - 20), (cx + 5, cy - 7), (cx + 18, cy - 7), (cx + 8, cy + 2), (cx + 12, cy + 15), (cx, cy + 7), (cx - 12, cy + 15), (cx - 8, cy + 2), (cx - 18, cy - 7), (cx - 5, cy - 7)], fill=(255, 215, 0, 255))

# Now draw mountains of fluffy popcorn overflowing from the bucket top!
random.seed(42)
popcorn_colors = [
    (255, 248, 220),  # Cornsilk
    (255, 235, 150),  # Buttery yellow
    (250, 215, 100),  # Golden butter
    (245, 245, 230),  # White-ish
    (210, 150, 50),   # Toasted butter shadow
]

popcorn_centers = []
# Dense pile in bucket top
for row in range(5):
    y_row = (by1 + 50) - row * 22
    count = 14 - row * 2
    for col in range(count):
        x_col = bx1 + 20 + (bx2 - bx1 - 40) * (col + (row % 2) * 0.5) / count + random.randint(-10, 10)
        popcorn_centers.append((x_col, y_row + random.randint(-8, 8), random.randint(18, 28)))

# Flying popcorn kernels popping into the air around his beak/face
flying_pops = [
    (w * 0.28, h * 0.46, 24),
    (w * 0.33, h * 0.38, 20),
    (w * 0.42, h * 0.34, 26),  # Near beak!
    (w * 0.48, h * 0.30, 22),
    (w * 0.58, h * 0.36, 25),
    (w * 0.65, h * 0.44, 28),
    (w * 0.22, h * 0.52, 22),
    (w * 0.74, h * 0.50, 24),
    (w * 0.38, h * 0.26, 18),
]
popcorn_centers.extend(flying_pops)

for px, py, pr in popcorn_centers:
    # Each popcorn piece is composed of 3-4 overlapping bubbles for organic shape
    for _ in range(4):
        ox = px + random.randint(-pr//2, pr//2)
        oy = py + random.randint(-pr//2, pr//2)
        r = pr * random.uniform(0.6, 0.9)
        color = random.choice(popcorn_colors)
        b_draw.ellipse([ox - r, oy - r, ox + r, oy + r], fill=color + (255,), outline=(190, 140, 40, 180), width=2)
    # highlight center
    b_draw.ellipse([px - pr//3, py - pr//3, px + pr//3, py + pr//3], fill=(255, 255, 240, 240))

# Add soft drop shadow below bucket
shadow = Image.new("RGBA", (w, h), (0, 0, 0, 0))
s_draw = ImageDraw.Draw(shadow)
s_draw.ellipse([bx1 + 20, by2 - 20, bx2 - 20, by2 + 40], fill=(0, 0, 0, 180))
shadow = shadow.filter(ImageFilter.GaussianBlur(15))

# Cinema lighting overlay (warm amber glow on right, soft blue fill on left)
cin_light = Image.new("RGBA", (w, h), (0, 0, 0, 0))
cl_draw = ImageDraw.Draw(cin_light)
cl_draw.rectangle([0, 0, w, h], fill=(0, 0, 0, 0))

composite2 = Image.alpha_composite(base2, shadow)
composite2 = Image.alpha_composite(composite2, bucket_img)
composite2.convert("RGB").save(os.path.join(out_dir, "mascot_slide_2.jpg"), quality=95)
print("Slide 2 (Popcorn) saved.")

# --- Slide 3: Multi-Engine Holographic Radar ---
base3 = Image.open(mascot_path).convert("RGBA")
radar_img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
r_draw = ImageDraw.Draw(radar_img)

rcx, rcy = w * 0.5, h * 0.48
# Radar concentric rings
for radius in [140, 240, 340, 440]:
    r_draw.ellipse([rcx - radius, rcy - radius, rcx + radius, rcy + radius], outline=(212, 175, 55, 160), width=2)

# Crosshairs and radial ticks
r_draw.line([(rcx - 460, rcy), (rcx + 460, rcy)], fill=(212, 175, 55, 120), width=1)
r_draw.line([(rcx, rcy - 460), (rcx, rcy + 460)], fill=(212, 175, 55, 120), width=1)

# Degree ticks
for deg in range(0, 360, 15):
    rad = math.radians(deg)
    x1 = rcx + 430 * math.cos(rad)
    y1 = rcy + 430 * math.sin(rad)
    x2 = rcx + 445 * math.cos(rad)
    y2 = rcy + 445 * math.sin(rad)
    r_draw.line([(x1, y1), (x2, y2)], fill=(255, 215, 0, 180), width=2)

# Ping targets with pulse rings (The 3 Engines: Movielix, RezFlix, AlmasMovie)
engines_hud = [
    (rcx + 220, rcy - 160, "MOVIELIX: 45ms", (0, 255, 128)),
    (rcx - 240, rcy - 120, "REZFLIX: 78ms", (255, 215, 0)),
    (rcx + 180, rcy + 220, "ALMASMOVIE: 62ms", (0, 220, 255)),
]

for ex, ey, label, col in engines_hud:
    # Ping blip
    r_draw.ellipse([ex - 10, ey - 10, ex + 10, ey + 10], fill=col + (255,), outline=(255, 255, 255, 255), width=2)
    # Pulse rings
    r_draw.ellipse([ex - 25, ey - 25, ex + 25, ey + 25], outline=col + (180,), width=2)
    r_draw.ellipse([ex - 45, ey - 45, ex + 45, ey + 45], outline=col + (90,), width=1)
    # Tag box
    bx = ex + 30 if ex < rcx else ex - 180
    by = ey - 15
    r_draw.rounded_rectangle([bx, by, bx + 150, by + 32], radius=6, fill=(10, 10, 10, 220), outline=col + (200,), width=2)

# Sweep sector beam
sweep = Image.new("RGBA", (w, h), (0, 0, 0, 0))
sw_draw = ImageDraw.Draw(sweep)
# Draw radar scan wedge
for a in range(45):
    alpha = int(140 * (1 - a / 45))
    rad = math.radians(135 + a)
    x = rcx + 440 * math.cos(rad)
    y = rcy + 440 * math.sin(rad)
    sw_draw.line([(rcx, rcy), (x, y)], fill=(212, 175, 55, alpha), width=3)

composite3 = Image.alpha_composite(base3, sweep)
composite3 = Image.alpha_composite(composite3, radar_img)
composite3.convert("RGB").save(os.path.join(out_dir, "mascot_slide_3.jpg"), quality=95)
print("Slide 3 (Radar & Multi-Engine) saved.")

# --- Slide 4: Curated Playlists & Gold Offline VIP DB ---
base4 = Image.open(mascot_path).convert("RGBA")
play_img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
p_draw = ImageDraw.Draw(play_img)

# Luxury Golden VIP Cinema Tickets and Playlist collection cards in foreground
# Ticket 1 (Rotated left)
t1 = Image.new("RGBA", (320, 160), (0, 0, 0, 0))
t1_draw = ImageDraw.Draw(t1)
t1_draw.rounded_rectangle([0, 0, 318, 158], radius=16, fill=(18, 18, 18, 245), outline=(212, 175, 55, 255), width=3)
# Perforated stub cutouts
t1_draw.ellipse([80 - 15, -15, 80 + 15, 15], fill=(0, 0, 0, 0))
t1_draw.ellipse([80 - 15, 160 - 15, 80 + 15, 160 + 15], fill=(0, 0, 0, 0))
t1_draw.line([(80, 20), (80, 140)], fill=(212, 175, 55, 180), width=2)
# Stars & Gold Emblem
t1_draw.ellipse([180 - 25, 45 - 25, 180 + 25, 45 + 25], outline=(255, 215, 0, 255), width=2)
t1_draw.polygon([(180, 32), (184, 42), (195, 42), (186, 49), (189, 60), (180, 53), (171, 60), (174, 49), (165, 42), (176, 42)], fill=(255, 215, 0, 255))
# Barcode lines on stub
for b_i in range(12):
    bx = 15 + b_i * 5
    bw = 2 if b_i % 3 != 0 else 3
    t1_draw.line([(bx, 30), (bx, 130)], fill=(212, 175, 55, 220), width=bw)

t1_rot = t1.rotate(-15, expand=True, resample=Image.BICUBIC)
play_img.paste(t1_rot, (int(w * 0.10), int(h * 0.65)), t1_rot)

# Ticket 2 (Rotated right) - VIP COLLECTION / PLAYLIST
t2 = Image.new("RGBA", (340, 170), (0, 0, 0, 0))
t2_draw = ImageDraw.Draw(t2)
t2_draw.rounded_rectangle([0, 0, 338, 168], radius=16, fill=(28, 24, 15, 245), outline=(255, 215, 0, 255), width=4)
# Perforated stub cutouts
t2_draw.ellipse([250 - 15, -15, 250 + 15, 15], fill=(0, 0, 0, 0))
t2_draw.ellipse([250 - 15, 170 - 15, 250 + 15, 170 + 15], fill=(0, 0, 0, 0))
t2_draw.line([(250, 20), (250, 150)], fill=(255, 215, 0, 180), width=2)
# Heart / Favorite icon on Ticket 2
t2_draw.polygon([(120, 45), (140, 65), (160, 45), (150, 30), (130, 30)], fill=(255, 215, 0, 255))
# Playlists badge
t2_draw.rounded_rectangle([25, 40, 220, 130], radius=10, fill=(10, 10, 10, 220), outline=(212, 175, 55, 200), width=2)
# Draw 3 playlist stacked lines inside badge
for line_i in range(3):
    ly = 65 + line_i * 22
    t2_draw.line([(45, ly), (180, ly)], fill=(255, 215, 0, 255), width=4)
    t2_draw.ellipse([190, ly - 4, 202, ly + 4], fill=(255, 215, 0, 255))

t2_rot = t2.rotate(14, expand=True, resample=Image.BICUBIC)
play_img.paste(t2_rot, (int(w * 0.52), int(h * 0.63)), t2_rot)

# Sparkling gold particles floating around
for _ in range(50):
    px = random.randint(50, w - 50)
    py = random.randint(50, h - 50)
    size = random.randint(2, 6)
    p_draw.ellipse([px, py, px + size, py + size], fill=(255, 215, 0, random.randint(120, 255)))

composite4 = Image.alpha_composite(base4, play_img)
composite4.convert("RGB").save(os.path.join(out_dir, "mascot_slide_4.jpg"), quality=95)
print("Slide 4 (Playlists & Tickets) saved.")
