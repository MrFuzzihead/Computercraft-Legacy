const fs = require('fs'), path = require('path');

function walk(d, out = []) {
  for (const e of fs.readdirSync(d, { withFileTypes: true })) {
    const p = path.join(d, e.name);
    if (e.isDirectory()) walk(p, out); else out.push(p);
  }
  return out;
}

const SEP = String.fromCharCode(92);

// Collect every name that looks like a member of the file's API table:
//   function api.foo(...)        function api:foo(...)      api.foo = ...
function extract(dir) {
  const out = {};
  for (const f of walk(dir)) {
    const s = fs.readFileSync(f, 'utf8');
    const rel = path.relative(dir, f).split(SEP).join('/').replace(/.lua$/, '');
    const names = new Set();
    for (const m of s.matchAll(/^\s*function\s+([A-Za-z0-9_]+)[.:]([A-Za-z0-9_]+)\s*\(/gm)) names.add(m[2]);
    for (const m of s.matchAll(/^\s*([A-Za-z0-9_]+)\.([A-Za-z0-9_]+)\s*=/gm)) names.add(m[2]);
    if (names.size) out[rel] = [...names].sort();
  }
  return out;
}

const ours = extract('src/main/resources/assets/computercraft/lua/rom/apis');
const ref = extract('migrate/CC-Tweaked-1.20.1-1.120.2/projects/core/src/main/resources/data/computercraft/lua/rom/apis');
const all = new Set([...Object.keys(ours), ...Object.keys(ref)]);

for (const k of [...all].sort()) {
  const a = Ours(k), b = Ref(k);
  const missing = b.filter(x => !a.includes(x));
  const extra = a.filter(x => !b.includes(x));
  if (missing.length || extra.length) {
    console.log(k);
    if (missing.length) console.log('  missing: ' + missing.join(', '));
    if (extra.length) console.log('  extra:   ' + extra.join(', '));
  }
}

function Ours(k) { return ours[k] || []; }
function Ref(k) { return ref[k] || []; }
