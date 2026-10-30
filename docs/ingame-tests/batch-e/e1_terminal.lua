-- Batch E: terminal and API polish.
--
--   * term.setPaletteColour(colour, rgb24) as well as (colour, r, g, b)
--   * term.blit accepts uppercase hex
--   * colours.toBlit validates its argument
--   * paintutils.drawLine validates startY (and no longer leaks globals)
--
-- Runs unattended, including in CI.

local P, F, S = 0, 0, 0
local fails, skips = {}, {}
local function ok(label, cond, detail)
  if cond then
    P = P + 1
  else
    F = F + 1
    fails[#fails + 1] = label .. "  [" .. tostring(detail) .. "]"
  end
end
local function eq(label, expected, got)
  ok(label, expected == got, "expected " .. tostring(expected) .. " got " .. tostring(got))
end
local function skip(label)
  S = S + 1
  skips[#skips + 1] = label
  print("SKIP " .. label)
end
local function near(a, b) return math.abs(a - b) < 0.01 end
local function section(n) print("== " .. n .. " ==") end
local function finish(name)
  _batchResultName = name
  local lines = { "RESULT " .. name }
  lines[#lines + 1] = "  passed " .. P .. "   failed " .. F .. "   skipped " .. S
  for _, f in ipairs(fails) do lines[#lines + 1] = "  FAIL " .. f end
  for _, k in ipairs(skips) do lines[#lines + 1] = "  SKIP " .. k end

  term.clear()
  term.setCursorPos(1, 1)
  for _, line in ipairs(lines) do print(line) end

  local h = fs.open("/batch-e-results.txt", "a")
  if h then
    for _, line in ipairs(lines) do h.write(line .. "\n") end
    h.close()
  end
end

-- ---------------------------------------------------------------------------
section("setPaletteColour(colour, rgb24)")

local savedR, savedG, savedB = term.getPaletteColour(colours.red)

term.setPaletteColour(colours.red, 0xFF8000)
local r, g, b = term.getPaletteColour(colours.red)
ok("packed colour sets red", near(r, 1))
ok("packed colour sets green", near(g, 0.502), g)
ok("packed colour sets blue", near(b, 0))

-- The three-channel form must still work.
term.setPaletteColour(colours.red, 0.25, 0.5, 0.75)
r, g, b = term.getPaletteColour(colours.red)
ok("separate channels still work", near(r, 0.25) and near(g, 0.5) and near(b, 0.75), r .. "," .. g .. "," .. b)

-- colours.packRGB feeds the packed form directly.
term.setPaletteColour(colours.red, colours.packRGB(0.1, 0.2, 0.3))
r, g, b = term.getPaletteColour(colours.red)
ok("packRGB round-trips", near(r, 0.1) and near(g, 0.2) and near(b, 0.3), r .. "," .. g .. "," .. b)

term.setPaletteColour(colours.red, savedR, savedG, savedB)

section("blit accepts uppercase hex")

-- Write two cells with uppercase colour letters; the cursor must advance by two.
term.setCursorPos(1, 1)
local okBlit = pcall(term.blit, "ab", "FC", "0A")
ok("uppercase blit does not raise", okBlit, okBlit)

local okLower = pcall(term.blit, "cd", "fc", "0a")
ok("lowercase blit still works", okLower, okLower)

section("colours.toBlit validation")

ok("toBlit(1) is white", colours.toBlit(1) == "0")
ok("toBlit(32768) is black", colours.toBlit(colors.black) == "f")
ok("toBlit rejects 0", not pcall(colours.toBlit, 0))
ok("toBlit rejects 65536", not pcall(colours.toBlit, 65536))
ok("toBlit rejects a string", not pcall(colours.toBlit, "red"))
eq("toBlit floors non-power-of-two values", "2", colours.toBlit(6))

section("paintutils.drawLine")

local savedBg = term.getBackgroundColour()
local okLine = pcall(paintutils.drawLine, 1, 2, 5, 2, colours.red)
ok("drawLine works with numbers", okLine, okLine)
ok("a string startY is rejected", not pcall(paintutils.drawLine, 1, "two", 3, 4, 1))
ok("a string endX is rejected", not pcall(paintutils.drawLine, 1, 1, "x", 4, 1))
ok("drawLine still accepts a nil colour", pcall(paintutils.drawLine, 1, 1, 3, 3))
term.setBackgroundColour(savedBg)

-- Regression: maxX/minY/maxY used to be globals.
paintutils.drawLine(1, 1, 4, 3, colours.red)
eq("drawLine does not define a global maxX", nil, rawget(_G, "maxX"))
eq("drawLine does not define a global minY", nil, rawget(_G, "minY"))
eq("drawLine does not define a global maxY", nil, rawget(_G, "maxY"))
term.setBackgroundColour(savedBg)

-- ---------------------------------------------------------------------------
term.clear()
term.setCursorPos(1, 1)
finish("BATCH-E terminal")

print("")
print("--- MANUAL DRILL ---")
print("1. term.setPaletteColour(colours.red, 0xFF8000)")
print("   EXPECT: red becomes orange straight away.")
print("2. term.blit('hi', 'FF', '00')  then  term.blit('hi', 'ff', '00')")
print("   EXPECT: both draw; neither errors.")
print("3. clear palette  EXPECT: the palette returns to normal.")
print("4. paintutils.drawLine(1, 1, 10, 10, colours.blue)")
print("   EXPECT: a diagonal line, and 'maxX' is still nil afterwards.")
print("")

-- No scrollback: repeat the verdict and any failures as the last lines.
for i = 1, math.min(#fails, 8) do print("  FAIL " .. fails[i]) end
if #fails > 8 then print("  ... and " .. (#fails - 8) .. " more -- open the results file") end
for i = 1, math.min(#skips, 3) do print("  SKIP " .. skips[i]) end
print("RESULT " .. tostring(_batchResultName) .. ": passed " .. P .. "   failed " .. F .. "   skipped " .. S)
