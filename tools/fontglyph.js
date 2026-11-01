// Minimal PNG reader, enough to inspect CC:Tweaked's terminal font atlas.
const fs = require('fs');
const zlib = require('zlib');

function readPng(file) {
  const b = fs.readFileSync(file);
  if (b.readUInt32BE(0) !== 0x89504e47) throw new Error('not a png');
  let pos = 8, ihdr = null, palette = null, trns = null;
  const idat = [];
  while (pos < b.length) {
    const len = b.readUInt32BE(pos);
    const type = b.toString('ascii', pos + 4, pos + 8);
    const data = b.subarray(pos + 8, pos + 8 + len);
    if (type === 'IHDR') {
      ihdr = {
        width: data.readUInt32BE(0), height: data.readUInt32BE(4),
        depth: data[8], colour: data[9], interlace: data[12],
      };
    } else if (type === 'PLTE') palette = data;
    else if (type === 'tRNS') trns = data;
    else if (type === 'IDAT') idat.push(data);
    else if (type === 'IEND') break;
    pos += 12 + len;
  }
  if (ihdr.interlace) throw new Error('interlaced png not supported');
  const raw = zlib.inflateSync(Buffer.concat(idat));
  const channels = { 0: 1, 2: 3, 3: 1, 4: 2, 6: 4 }[ihdr.colour];
  const bpp = Math.ceil((channels * ihdr.depth) / 8);
  const stride = Math.ceil((ihdr.width * channels * ihdr.depth) / 8);
  const out = Buffer.alloc(ihdr.height * stride);
  let p = 0;
  for (let y = 0; y < ihdr.height; y++) {
    const filter = raw[p++];
    const line = raw.subarray(p, p + stride); p += stride;
    const cur = out.subarray(y * stride, (y + 1) * stride);
    const prev = y > 0 ? out.subarray((y - 1) * stride, y * stride) : Buffer.alloc(stride);
    for (let x = 0; x < stride; x++) {
      const a = x >= bpp ? cur[x - bpp] : 0, bb = prev[x], c = x >= bpp ? prev[x - bpp] : 0;
      let v = line[x];
      if (filter === 1) v += a; else if (filter === 2) v += bb;
      else if (filter === 3) v += (a + bb) >> 1;
      else if (filter === 4) {
        const pp = a + bb - c, pa = Math.abs(pp - a), pb = Math.abs(pp - bb), pc = Math.abs(pp - c);
        v += (pa <= pb && pa <= pc) ? a : (pb <= pc ? bb : c);
      }
      cur[x] = v & 0xff;
    }
  }
  return { ...ihdr, stride, bpp, channels, palette, trns, pixels: out };
}

function sampleIndex(img, x, y) {
  const bitsPerPixel = img.channels * img.depth;
  if (bitsPerPixel >= 8) return img.pixels[y * img.stride + x * img.bpp];
  const bitPos = x * bitsPerPixel;
  const byte = img.pixels[y * img.stride + (bitPos >> 3)];
  const shift = 8 - bitsPerPixel - (bitPos & 7);
  return (byte >> shift) & ((1 << bitsPerPixel) - 1);
}

function alphaAt(img, x, y) {
  if (img.colour === 6) return img.pixels[y * img.stride + x * img.bpp + 3];
  if (img.colour === 4) return img.pixels[y * img.stride + x * img.bpp + (img.depth === 16 ? 2 : 1)];
  const idx = sampleIndex(img, x, y);
  if (img.colour === 3) return img.trns && idx < img.trns.length ? img.trns[idx] : 255;
  if (img.colour === 0) return img.depth === 1 ? idx * 255 : idx;
  return 255;
}

const img = readPng(process.argv[2]);
console.log(`atlas ${img.width}x${img.height}, depth ${img.depth}, colour type ${img.colour}`);

// CC:Tweaked: glyph (code) is at column = code % 16, row = code / 16, with a 1px pad and an
// 8x11 cell, glyph 6 wide and 9 tall.
const glyph = (code) => {
  const col = code % 16, row = Math.floor(code / 16);
  const x0 = 1 + col * 8, y0 = 1 + row * 11;
  const lines = [];
  for (let y = 0; y < 9; y++) {
    let s = '';
    for (let x = 0; x < 6; x++) s += alphaAt(img, x0 + x, y0 + y) > 128 ? '#' : '.';
    lines.push(s);
  }
  return lines;
};

for (const code of process.argv.slice(3).map((c) => parseInt(c, 16))) {
  console.log(`\n= 0x${code.toString(16)} =`);
  glyph(code).forEach((l) => console.log(l));
}
