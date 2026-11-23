#!/usr/bin/env python3
"""
Generate high-resolution DMG background artwork for Focus Productivity Tracker.
Creates both 1x (720x460) and @2x Retina (1440x920) images using pure Python standard library.
"""

import math
import os
import struct
import zlib

FONT_5X7 = {
    ' ': [0, 0, 0, 0, 0],
    'A': [0x7E, 0x11, 0x11, 0x11, 0x7E],
    'B': [0x7F, 0x49, 0x49, 0x49, 0x36],
    'C': [0x3E, 0x41, 0x41, 0x41, 0x22],
    'D': [0x7F, 0x41, 0x41, 0x22, 0x1C],
    'E': [0x7F, 0x49, 0x49, 0x49, 0x41],
    'F': [0x7F, 0x09, 0x09, 0x09, 0x01],
    'G': [0x3E, 0x41, 0x49, 0x49, 0x7A],
    'H': [0x7F, 0x08, 0x08, 0x08, 0x7F],
    'I': [0x00, 0x41, 0x7F, 0x41, 0x00],
    'J': [0x20, 0x40, 0x41, 0x3F, 0x01],
    'K': [0x7F, 0x08, 0x14, 0x22, 0x41],
    'L': [0x7F, 0x40, 0x40, 0x40, 0x40],
    'M': [0x7F, 0x02, 0x0C, 0x02, 0x7F],
    'N': [0x7F, 0x04, 0x08, 0x10, 0x7F],
    'O': [0x3E, 0x41, 0x41, 0x41, 0x3E],
    'P': [0x7F, 0x09, 0x09, 0x09, 0x06],
    'Q': [0x3E, 0x41, 0x51, 0x21, 0x5E],
    'R': [0x7F, 0x09, 0x19, 0x29, 0x46],
    'S': [0x46, 0x49, 0x49, 0x49, 0x31],
    'T': [0x01, 0x01, 0x7F, 0x01, 0x01],
    'U': [0x3F, 0x40, 0x40, 0x40, 0x3F],
    'V': [0x1F, 0x20, 0x40, 0x20, 0x1F],
    'W': [0x7F, 0x20, 0x18, 0x20, 0x7F],
    'X': [0x63, 0x14, 0x08, 0x14, 0x63],
    'Y': [0x07, 0x08, 0x70, 0x08, 0x07],
    'Z': [0x61, 0x51, 0x49, 0x45, 0x43],
    '0': [0x3E, 0x51, 0x49, 0x45, 0x3E],
    '1': [0x00, 0x42, 0x7F, 0x40, 0x00],
    '2': [0x42, 0x61, 0x51, 0x49, 0x46],
    '3': [0x21, 0x41, 0x45, 0x4B, 0x31],
    '4': [0x18, 0x14, 0x12, 0x7F, 0x10],
    '5': [0x27, 0x45, 0x45, 0x45, 0x39],
    '6': [0x3C, 0x4A, 0x49, 0x49, 0x30],
    '7': [0x01, 0x71, 0x09, 0x05, 0x03],
    '8': [0x36, 0x49, 0x49, 0x49, 0x36],
    '9': [0x06, 0x49, 0x49, 0x29, 0x1E],
    '.': [0x00, 0x60, 0x60, 0x00, 0x00],
    ',': [0x00, 0x80, 0x60, 0x00, 0x00],
    ':': [0x00, 0x36, 0x36, 0x00, 0x00],
    '-': [0x08, 0x08, 0x08, 0x08, 0x08],
    '+': [0x08, 0x08, 0x3E, 0x08, 0x08],
    '/': [0x20, 0x10, 0x08, 0x04, 0x02],
    '\\': [0x02, 0x04, 0x08, 0x10, 0x20],
    '(': [0x00, 0x3E, 0x41, 0x00, 0x00],
    ')': [0x00, 0x00, 0x41, 0x3E, 0x00],
    '>': [0x41, 0x22, 0x14, 0x08, 0x00],
    '<': [0x00, 0x08, 0x14, 0x22, 0x41],
    '|': [0x00, 0x00, 0x7F, 0x00, 0x00],
    '*': [0x14, 0x08, 0x3E, 0x08, 0x14],
    '&': [0x36, 0x49, 0x55, 0x22, 0x50],
    '1': [0x00, 0x42, 0x7F, 0x40, 0x00],
}

for c in range(ord('a'), ord('z') + 1):
    FONT_5X7[chr(c)] = FONT_5X7[chr(c).upper()]


class ImageCanvas:
    def __init__(self, width, height):
        self.width = width
        self.height = height
        self.r = [0.0] * (width * height)
        self.g = [0.0] * (width * height)
        self.b = [0.0] * (width * height)
        self.a = [0.0] * (width * height)

    def set_pixel(self, x, y, r, g, b, a=1.0):
        if 0 <= x < self.width and 0 <= y < self.height:
            idx = y * self.width + x
            bg_a = self.a[idx]
            out_a = a + bg_a * (1.0 - a)
            if out_a > 0.0:
                self.r[idx] = (r * a + self.r[idx] * bg_a * (1.0 - a)) / out_a
                self.g[idx] = (g * a + self.g[idx] * bg_a * (1.0 - a)) / out_a
                self.b[idx] = (b * a + self.b[idx] * bg_a * (1.0 - a)) / out_a
                self.a[idx] = out_a

    def fill_gradient(self, top_color, bottom_color):
        r1, g1, b1 = top_color
        r2, g2, b2 = bottom_color
        for y in range(self.height):
            t = y / max(1, self.height - 1)
            t_smooth = t * t * (3.0 - 2.0 * t)
            cr = r1 + (r2 - r1) * t_smooth
            cg = g1 + (g2 - g1) * t_smooth
            cb = b1 + (b2 - b1) * t_smooth
            for x in range(self.width):
                idx = y * self.width + x
                self.r[idx] = cr
                self.g[idx] = cg
                self.b[idx] = cb
                self.a[idx] = 1.0

    def add_radial_glow(self, cx, cy, radius, color, max_alpha=0.35):
        r, g, b = color
        x_min = max(0, int(cx - radius))
        x_max = min(self.width, int(cx + radius + 1))
        y_min = max(0, int(cy - radius))
        y_max = min(self.height, int(cy + radius + 1))
        for y in range(y_min, y_max):
            for x in range(x_min, x_max):
                dist = math.hypot(x - cx, y - cy)
                if dist < radius:
                    t = dist / radius
                    alpha = max_alpha * (1.0 - t * t)
                    self.set_pixel(x, y, r, g, b, alpha)

    def draw_rounded_rect(self, x, y, w, h, radius, fill_color=None, border_color=None, border_width=1):
        x0, y0 = int(x), int(y)
        x1, y1 = int(x + w), int(y + h)
        r = float(radius)

        for py in range(max(0, y0), min(self.height, y1)):
            for px in range(max(0, x0), min(self.width, x1)):
                dx = max(0, x0 + r - px, px - (x1 - r - 1))
                dy = max(0, y0 + r - py, py - (y1 - r - 1))
                dist = math.hypot(dx, dy)

                if dist <= r:
                    is_border = (dist >= (r - border_width)) or (px - x0 < border_width) or (x1 - 1 - px < border_width) or (py - y0 < border_width) or (y1 - 1 - py < border_width)
                    if is_border and border_color:
                        cr, cg, cb, ca = border_color
                        self.set_pixel(px, py, cr, cg, cb, ca)
                    elif fill_color:
                        cr, cg, cb, ca = fill_color
                        self.set_pixel(px, py, cr, cg, cb, ca)

    def draw_text(self, text, start_x, start_y, scale=2, color=(1.0, 1.0, 1.0, 1.0), spacing=1):
        cr, cg, cb, ca = color
        curr_x = int(start_x)
        for char in text:
            bitmap = FONT_5X7.get(char, FONT_5X7.get(' '))
            for col_idx, col_bits in enumerate(bitmap):
                for row_idx in range(7):
                    if (col_bits >> row_idx) & 1:
                        for dy in range(scale):
                            for dx in range(scale):
                                px = curr_x + col_idx * scale + dx
                                py = int(start_y) + row_idx * scale + dy
                                self.set_pixel(px, py, cr, cg, cb, ca)
            curr_x += (len(bitmap) + spacing) * scale

    def measure_text(self, text, scale=2, spacing=1):
        width = 0
        for char in text:
            bitmap = FONT_5X7.get(char, FONT_5X7.get(' '))
            width += (len(bitmap) + spacing) * scale
        return width - (spacing * scale if width > 0 else 0)

    def draw_arrow(self, x0, y0, x1, y1, width, color):
        cr, cg, cb, ca = color
        dx = x1 - x0
        dy = y1 - y0
        length = math.hypot(dx, dy)
        if length == 0:
            return
        ux = dx / length
        uy = dy / length

        steps = int(length)
        for i in range(steps):
            cx = x0 + ux * i
            cy = y0 + uy * i
            for w in range(-int(width // 2), int(width // 2) + 1):
                px = int(cx - uy * w)
                py = int(cy + ux * w)
                self.set_pixel(px, py, cr, cg, cb, ca)

        head_len = width * 3.5
        head_w = width * 2.8
        for t in range(int(head_len)):
            arrow_w = head_w * (1.0 - t / head_len)
            cx = x1 - ux * t
            cy = y1 - uy * t
            for w in range(-int(arrow_w), int(arrow_w) + 1):
                px = int(cx - uy * w)
                py = int(cy + ux * w)
                self.set_pixel(px, py, cr, cg, cb, ca)

    def save_png(self, filepath):
        raw = bytearray()
        for y in range(self.height):
            raw.append(0)
            for x in range(self.width):
                idx = y * self.width + x
                r = min(255, max(0, int(self.r[idx] * 255.0 + 0.5)))
                g = min(255, max(0, int(self.g[idx] * 255.0 + 0.5)))
                b = min(255, max(0, int(self.b[idx] * 255.0 + 0.5)))
                a = min(255, max(0, int(self.a[idx] * 255.0 + 0.5)))
                raw.extend((r, g, b, a))

        compressed = zlib.compress(bytes(raw), 9)

        def make_chunk(ctype, data):
            crc = zlib.crc32(ctype + data) & 0xFFFFFFFF
            return struct.pack('>I', len(data)) + ctype + data + struct.pack('>I', crc)

        png = bytearray(b'\x89PNG\r\n\x1a\n')
        png.extend(make_chunk(b'IHDR', struct.pack('>IIBBBBB', self.width, self.height, 8, 6, 0, 0, 0)))
        png.extend(make_chunk(b'IDAT', compressed))
        png.extend(make_chunk(b'IEND', b''))

        os.makedirs(os.path.dirname(os.path.abspath(filepath)), exist_ok=True)
        with open(filepath, 'wb') as f:
            f.write(png)
        print(f"Generated: {filepath} ({self.width}x{self.height}, {len(png)} bytes)")


def render_dmg_background(output_1x, output_2x):
    # Canvas dimensions: 1320 x 880 (@2x) and 660 x 440 (1x)
    w2, h2 = 1320, 880
    c2 = ImageCanvas(w2, h2)

    # 1. Dark theme background
    c2.fill_gradient((0.08, 0.09, 0.12), (0.13, 0.14, 0.19))

    # 2. Ambient glows
    c2.add_radial_glow(w2 * 0.5, h2 * 0.48, 520, (0.28, 0.35, 0.70), max_alpha=0.22)
    c2.add_radial_glow(260, 440, 240, (0.20, 0.60, 0.95), max_alpha=0.18)
    c2.add_radial_glow(660, 440, 240, (0.39, 0.45, 0.95), max_alpha=0.18)
    c2.add_radial_glow(1060, 440, 240, (0.15, 0.65, 0.60), max_alpha=0.18)

    # 3. Header
    title = "FOCUS"
    tw = c2.measure_text(title, scale=7, spacing=2)
    c2.draw_text(title, (w2 - tw) // 2, 60, scale=7, color=(1.0, 1.0, 1.0, 0.96), spacing=2)

    subtitle = "AUTOMATED INSTALLATION & BACKGROUND SERVICE"
    sw = c2.measure_text(subtitle, scale=2, spacing=2)
    c2.draw_rounded_rect((w2 - sw) // 2 - 20, 135, sw + 40, 36, 18,
                         fill_color=(0.20, 0.24, 0.36, 0.75),
                         border_color=(0.45, 0.52, 0.85, 0.45), border_width=2)
    c2.draw_text(subtitle, (w2 - sw) // 2, 145, scale=2, color=(0.78, 0.84, 1.0, 0.95), spacing=2)

    # 4. Pedestals
    zone_size = 220
    zone_radius = 36
    border_w = 3

    # Pedestal 1 (Left: Install Focus.app - 1-Click Auto Setup)
    c2.draw_rounded_rect(260 - zone_size // 2, 440 - zone_size // 2, zone_size, zone_size, zone_radius,
                         fill_color=(0.14, 0.18, 0.28, 0.80),
                         border_color=(0.30, 0.65, 1.0, 0.55), border_width=border_w)
    badge1 = "1-CLICK SETUP"
    b1w = c2.measure_text(badge1, scale=2, spacing=1)
    c2.draw_rounded_rect(260 - b1w // 2 - 10, 440 - zone_size // 2 - 24, b1w + 20, 26, 12,
                         fill_color=(0.20, 0.55, 0.95, 0.85))
    c2.draw_text(badge1, 260 - b1w // 2, 440 - zone_size // 2 - 18, scale=2, color=(1.0, 1.0, 1.0, 1.0), spacing=1)
    lbl1 = "INSTALL FOCUS"
    l1w = c2.measure_text(lbl1, scale=3, spacing=1)
    c2.draw_text(lbl1, 260 - l1w // 2, 580, scale=3, color=(0.85, 0.92, 1.0, 0.95), spacing=1)

    # Pedestal 2 (Center: Focus.app)
    c2.draw_rounded_rect(660 - zone_size // 2, 440 - zone_size // 2, zone_size, zone_size, zone_radius,
                         fill_color=(0.14, 0.16, 0.22, 0.75),
                         border_color=(0.40, 0.48, 0.85, 0.45), border_width=border_w)
    lbl2 = "FOCUS.APP"
    l2w = c2.measure_text(lbl2, scale=3, spacing=1)
    c2.draw_text(lbl2, 660 - l2w // 2, 580, scale=3, color=(0.85, 0.88, 0.95, 0.9), spacing=1)

    # Pedestal 3 (Right: Applications)
    c2.draw_rounded_rect(1060 - zone_size // 2, 440 - zone_size // 2, zone_size, zone_size, zone_radius,
                         fill_color=(0.14, 0.16, 0.22, 0.75),
                         border_color=(0.25, 0.70, 0.65, 0.45), border_width=border_w)
    lbl3 = "APPLICATIONS"
    l3w = c2.measure_text(lbl3, scale=3, spacing=1)
    c2.draw_text(lbl3, 1060 - l3w // 2, 580, scale=3, color=(0.85, 0.88, 0.95, 0.9), spacing=1)

    # 5. Drag arrow between Focus.app and Applications
    c2.draw_arrow(660 + zone_size // 2 + 20, 440, 1060 - zone_size // 2 - 20, 440, width=5, color=(0.50, 0.60, 0.85, 0.75))

    # 6. Instructions
    inst1 = "Double-click 'Install Focus' to automatically setup everything & start service"
    i1w = c2.measure_text(inst1, scale=2, spacing=1)
    c2.draw_text(inst1, (w2 - i1w) // 2, 700, scale=2, color=(0.90, 0.94, 1.0, 0.95), spacing=1)

    inst2 = "Or drag Focus.app into Applications folder to install manually"
    i2w = c2.measure_text(inst2, scale=2, spacing=1)
    c2.draw_text(inst2, (w2 - i2w) // 2, 740, scale=2, color=(0.60, 0.66, 0.80, 0.85), spacing=1)

    footer = "v1.0.0 * Automatic LaunchAgent Service * Web Dashboard: http://localhost:8484"
    fw = c2.measure_text(footer, scale=2, spacing=1)
    c2.draw_text(footer, (w2 - fw) // 2, 810, scale=2, color=(0.45, 0.50, 0.62, 0.8), spacing=1)

    c2.save_png(output_2x)

    # 1x downsample
    w1, h1 = 660, 440
    c1 = ImageCanvas(w1, h1)
    for y in range(h1):
        for x in range(w1):
            i00 = (y * 2) * w2 + (x * 2)
            i01 = (y * 2) * w2 + (x * 2 + 1)
            i10 = (y * 2 + 1) * w2 + (x * 2)
            i11 = (y * 2 + 1) * w2 + (x * 2 + 1)
            idx = y * w1 + x
            c1.r[idx] = (c2.r[i00] + c2.r[i01] + c2.r[i10] + c2.r[i11]) * 0.25
            c1.g[idx] = (c2.g[i00] + c2.g[i01] + c2.g[i10] + c2.g[i11]) * 0.25
            c1.b[idx] = (c2.b[i00] + c2.b[i01] + c2.b[i10] + c2.b[i11]) * 0.25
            c1.a[idx] = (c2.a[i00] + c2.a[i01] + c2.a[i10] + c2.a[i11]) * 0.25

    c1.save_png(output_1x)


if __name__ == "__main__":
    script_dir = os.path.dirname(os.path.abspath(__file__))
    out_1x = os.path.join(script_dir, "dmg-background.png")
    out_2x = os.path.join(script_dir, "dmg-background@2x.png")
    render_dmg_background(out_1x, out_2x)
