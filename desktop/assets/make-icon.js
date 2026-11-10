// Generates a crisp 512x512 RGBA PNG icon (NoteGraph Purple Velvet + Rainbow Monogram Halo) using built-in zlib.
const fs = require('fs');
const path = require('path');
const zlib = require('zlib');

const SIZE = 512;
const raw = Buffer.alloc(SIZE * (1 + SIZE * 4));

function clamp(v, min, max) {
  return v < min ? min : v > max ? max : v;
}

function mix(a, b, t) {
  return a + (b - a) * clamp(t, 0, 1);
}

function rainbowAt(angle) {
  // angle in [0, 2*PI)
  const norm = ((angle / (Math.PI * 2)) % 1 + 1) % 1;
  const stops = [
    [0, 255, 255],     // Cyan
    [139, 92, 246],    // Electric Violet
    [236, 72, 153],    // Magenta
    [245, 158, 11],    // Warm Orange
    [0, 255, 255]      // Wrap Cyan
  ];
  const scaled = norm * 4;
  const idx = Math.floor(scaled) % 4;
  const frac = scaled - Math.floor(scaled);
  const c1 = stops[idx];
  const c2 = stops[idx + 1];
  return [
    mix(c1[0], c2[0], frac),
    mix(c1[1], c2[1], frac),
    mix(c1[2], c2[2], frac)
  ];
}

function distToSegment(px, py, x1, y1, x2, y2) {
  const dx = x2 - x1;
  const dy = y2 - y1;
  const lenSq = dx * dx + dy * dy;
  if (lenSq === 0) return Math.hypot(px - x1, py - y1);
  const t = clamp(((px - x1) * dx + (py - y1) * dy) / lenSq, 0, 1);
  return Math.hypot(px - (x1 + t * dx), py - (y1 + t * dy));
}

function squircleSDF(x, y, cx, cy, halfSize, power = 4.2) {
  const nx = Math.abs(x - cx) / halfSize;
  const ny = Math.abs(y - cy) / halfSize;
  return (Math.pow(nx, power) + Math.pow(ny, power)) ** (1 / power) - 1;
}

for (let y = 0; y < SIZE; y++) {
  const rowStart = y * (1 + SIZE * 4);
  raw[rowStart] = 0; // filter None
  for (let x = 0; x < SIZE; x++) {
    const px = rowStart + 1 + x * 4;
    const cx = SIZE / 2;
    const cy = SIZE / 2;
    const sdf = squircleSDF(x, y, cx, cy, 232, 4.4) * 232;

    if (sdf > 1.5) {
      raw[px] = 0;
      raw[px + 1] = 0;
      raw[px + 2] = 0;
      raw[px + 3] = 0;
      continue;
    }

    const alphaEdge = clamp(1.2 - sdf, 0, 1);
    const angle = Math.atan2(y - cy, x - cx);
    const [rHalo, gHalo, bHalo] = rainbowAt(angle + Math.PI / 4);

    // Background purple radial glow
    const dCenter = Math.hypot(x - cx, y - cy) / 260;
    let r = mix(34, 16, dCenter);
    let g = mix(16, 7, dCenter);
    let b = mix(66, 32, dCenter);

    // Rainbow outer border band
    if (sdf > -18 && sdf <= 1.5) {
      const bandT = clamp(1 - Math.abs(sdf + 8) / 10, 0, 1);
      r = mix(r, rHalo, bandT * 0.95);
      g = mix(g, gHalo, bandT * 0.95);
      b = mix(b, bHalo, bandT * 0.95);
    }

    // Subtle inner dashed orbit ring
    const dOrbit = Math.abs(Math.hypot(x - cx, y - cy) - 166);
    if (dOrbit < 4.5) {
      const seg = Math.sin(angle * 16);
      if (seg > -0.1) {
        const ringAlpha = clamp(1 - dOrbit / 4.5, 0, 1) * 0.55;
        r = mix(r, rHalo, ringAlpha);
        g = mix(g, gHalo, ringAlpha);
        b = mix(b, bHalo, ringAlpha);
      }
    }

    // 'N' stroke segments (left stem, diagonal, right stem)
    const strokeR = 14;
    const dN1 = distToSegment(x, y, 136, 330, 136, 192);
    const dN2 = distToSegment(x, y, 136, 192, 214, 330);
    const dN3 = distToSegment(x, y, 214, 330, 214, 192);
    const dN = Math.min(dN1, dN2, dN3);
    if (dN < strokeR + 1.5) {
      const a = clamp(1 - (dN - (strokeR - 1.5)) / 3, 0, 1);
      const tGrad = (x - 130) / 90;
      const nr = mix(0, 168, tGrad);
      const ng = mix(255, 85, tGrad);
      const nb = mix(255, 247, tGrad);
      r = mix(r, nr, a);
      g = mix(g, ng, a);
      b = mix(b, nb, a);
    }

    // 'G' C-arc + crossbar
    const gcx = 326;
    const gcy = 262;
    const dGRing = Math.abs(Math.hypot(x - gcx, y - gcy) - 66);
    const gAngle = Math.atan2(y - gcy, x - gcx);
    const inGap = gAngle > -0.45 && gAngle < 0.05;
    const dGBar = distToSegment(x, y, gcx, gcy + 6, gcx + 66, gcy + 6);
    const dG = Math.min(inGap ? 999 : dGRing, dGBar);
    if (dG < strokeR + 1.5) {
      const a = clamp(1 - (dG - (strokeR - 1.5)) / 3, 0, 1);
      const tGrad = (y - 196) / 132;
      const gr = mix(168, 245, tGrad);
      const gg = mix(85, 158, tGrad);
      const gb = mix(247, 11, tGrad);
      r = mix(r, gr, a);
      g = mix(g, gg, a);
      b = mix(b, gb, a);
    }

    // Center Fountain Pen Nib (Crisp White silhouette)
    const nibY = (y - 96) / 140;
    if (nibY >= 0 && nibY <= 1) {
      const halfW = nibY < 0.55 ? mix(2, 26, nibY / 0.55) : mix(26, 13, (nibY - 0.55) / 0.45);
      const dxNib = Math.abs(x - cx) - halfW;
      if (dxNib < 1.5) {
        const a = clamp(1 - dxNib / 2, 0, 1);
        const onSlit = y < 180 && Math.abs(x - cx) < 2.2;
        const onHole = Math.hypot(x - cx, y - 182) < 6.5;
        if (onSlit || onHole) {
          r = mix(r, 124, a);
          g = mix(g, 58, a);
          b = mix(b, 237, a);
        } else {
          r = mix(r, 255, a);
          g = mix(g, 255, a);
          b = mix(b, 255, a);
        }
      }
    }

    // Cyan sparkle diamond above nib
    const ds = Math.abs(x - cx) + Math.abs(y - 78);
    if (ds < 14) {
      const a = clamp(1 - (ds - 11) / 3, 0, 1);
      r = mix(r, 0, a);
      g = mix(g, 255, a);
      b = mix(b, 255, a);
    }

    raw[px] = clamp(Math.round(r), 0, 255);
    raw[px + 1] = clamp(Math.round(g), 0, 255);
    raw[px + 2] = clamp(Math.round(b), 0, 255);
    raw[px + 3] = clamp(Math.round(alphaEdge * 255), 0, 255);
  }
}

// Encode PNG chunks
function crc32(buf) {
  let c = 0xffffffff;
  for (let i = 0; i < buf.length; i++) {
    c ^= buf[i];
    for (let k = 0; k < 8; k++) {
      c = (c >>> 1) ^ (c & 1 ? 0xedb88320 : 0);
    }
  }
  return (c ^ 0xffffffff) >>> 0;
}

function chunk(type, data) {
  const len = Buffer.alloc(4);
  len.writeUInt32BE(data.length, 0);
  const typeBuf = Buffer.from(type, 'ascii');
  const crcBuf = Buffer.alloc(4);
  crcBuf.writeUInt32BE(crc32(Buffer.concat([typeBuf, data])), 0);
  return Buffer.concat([len, typeBuf, data, crcBuf]);
}

const ihdr = Buffer.alloc(13);
ihdr.writeUInt32BE(SIZE, 0);
ihdr.writeUInt32BE(SIZE, 4);
ihdr[8] = 8; // bit depth
ihdr[9] = 6; // RGBA
ihdr[10] = 0;
ihdr[11] = 0;
ihdr[12] = 0;

const idat = zlib.deflateSync(raw, { level: 9 });
const png = Buffer.concat([
  Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]),
  chunk('IHDR', ihdr),
  chunk('IDAT', idat),
  chunk('IEND', Buffer.alloc(0))
]);

const outPath = path.join(__dirname, 'icon.png');
fs.writeFileSync(outPath, png);
console.log(`Generated ${outPath} (${png.length} bytes)`);
