-- Batch D: the small ROM programs (about, motd, set, peripherals) and the option additions
-- (clear palette/all, monitor scale, list.show_hidden).
--
-- Runs unattended, including in CI. Anything needing a peripheral (a monitor) is reported as a
-- skip rather than a failure.

local P, F, S = 0, 0, 0
local fails, skips, _transcript = {}, {}, {}
local function ok(label, cond, detail)
  _transcript[#_transcript + 1] = (cond and "OK   " or "FAIL ") .. label
    .. ((not cond and detail ~= nil) and ("  [" .. tostring(detail) .. "]") or "")
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
  _transcript[#_transcript + 1] = "SKIP " .. label
  S = S + 1
  skips[#skips + 1] = label
  print("SKIP " .. label)
end
local function section(n) print("== " .. n .. " ==") end
local function finish(name)
  _batchResultName = name
  local lines = { "RESULT " .. name }
  lines[#lines + 1] = "  passed " .. P .. "   failed " .. F .. "   skipped " .. S
  for _, f in ipairs(fails) do lines[#lines + 1] = "  FAIL " .. f end
  for _, k in ipairs(skips) do lines[#lines + 1] = "  SKIP " .. k end
  for _, line in ipairs(_transcript) do lines[#lines + 1] = "    " .. line end

  term.clear()
  term.setCursorPos(1, 1)
  for _, line in ipairs(lines) do print(line) end

  local h = fs.open("/batch-d-results.txt", "a")
  if h then
    for _, line in ipairs(lines) do h.write(line .. "\n") end
    h.close()
  end

  print("")
  print("RESULT " .. name .. ": passed " .. P .. "   failed " .. F .. "   skipped " .. S)
end

-- Capture everything a redirected program writes, then restore the terminal.
local function recorder()
  local rec = { written = {} }
  rec.write = function(s) rec.written[#rec.written + 1] = tostring(s) end
  rec.blit = function(text) rec.written[#rec.written + 1] = tostring(text) end
  rec.getSize = function() return 51, 19 end
  rec.getCursorPos = function() return 1, 1 end
  rec.setCursorPos = function() end
  rec.scroll = function() end
  rec.clearLine = function() end
  rec.clear = function() end
  rec.isColour = function() return true end
  rec.setTextColour = function() end
  -- term.redirect stubs any missing native method, and both spellings are native.
  rec.setTextColor = function() end
  rec.setBackgroundColor = function() end
  rec.getTextColour = function() return 1 end
  rec.getTextColor = function() return 1 end
  rec.getBackgroundColor = function() return 1 end
  rec.setBackgroundColour = function() end
  rec.getBackgroundColour = function() return 1 end
  rec.setCursorBlink = function() end
  return rec
end
local function capture(fn)
  local rec = recorder()
  local old = term.redirect(rec)
  local okRun, err = pcall(fn)
  term.redirect(old)
  return table.concat(rec.written), okRun, err
end

local function writeFile(path, contents)
  local h = fs.open(path, "w")
  h.write(contents)
  h.close()
end

-- ---------------------------------------------------------------------------
section("about")

local aboutText, aboutOk = capture(function() shell.run("/rom/programs/about") end)
ok("about runs", aboutOk, aboutText)
ok("about reports the CraftOS version", aboutText:find("CraftOS", 1, true) ~= nil, aboutText)
ok("about reports the host", aboutText:find(tostring(_HOST), 1, true) ~= nil, aboutText)

-- ---------------------------------------------------------------------------
section("motd")

-- motd prints a random line from motd.path, so point it at a file we control.
writeFile("/c_motd.txt", "only one message\n")
settings.set("motd.path", "/c_motd.txt")
local motdText, motdOk = capture(function() shell.run("/rom/programs/motd") end)
ok("motd runs", motdOk, motdText)
eq("motd printed the only message", "only one message", (motdText:gsub("\n$", "")))

writeFile("/c_motd.txt", "line one\nline two\n")
local manyText = capture(function() shell.run("/rom/programs/motd") end)
ok(
  "motd picks one of several lines",
  manyText:find("line one", 1, true) ~= nil or manyText:find("line two", 1, true) ~= nil,
  manyText
)

settings.set("motd.path", "/does-not-exist")
local missingText, missingOk = capture(function() shell.run("/rom/programs/motd") end)
ok("motd survives a missing file", missingOk, missingText)
settings.unset("motd.path")
fs.delete("/c_motd.txt")

-- ---------------------------------------------------------------------------
section("peripherals")

local periphText, periphOk = capture(function() shell.run("/rom/programs/peripherals") end)
ok("peripherals runs", periphOk, periphText)
ok("peripherals lists what is attached", periphText:find("Peripheral", 1, true) ~= nil, periphText)

-- ---------------------------------------------------------------------------
section("set")

local listText, listOk = capture(function() shell.run("/rom/programs/set") end)
ok("set lists settings when given no arguments", listOk, listText)
ok("the listing includes a known setting", listText:find("shell.autocomplete", 1, true) ~= nil, listText)

local showText = capture(function() shell.run("/rom/programs/set", "edit.default_extension") end)
ok("set <name> shows a single setting", showText:find("lua", 1, true) ~= nil, showText)

local setText = capture(function() shell.run("/rom/programs/set", "list.show_hidden", "true") end)
ok("set <name> <value> confirms", setText:find("set to", 1, true) ~= nil, setText)
eq("the setting really changed", true, settings.get("list.show_hidden"))

local unsetText = capture(function() shell.run("/rom/programs/set", "list.show_hidden", "nil") end)
ok("set <name> nil unsets", unsetText:find("unset", 1, true) ~= nil, unsetText)
eq("the setting went back to its default", false, settings.get("list.show_hidden"))

local badTypeText = capture(function() shell.run("/rom/programs/set", "list.show_hidden", "not-a-boolean") end)
ok("a value of the wrong type is rejected", badTypeText:find("not a valid", 1, true) ~= nil, badTypeText)

-- ---------------------------------------------------------------------------
section("list.show_hidden")

if not fs.isDir("/c_list") then fs.makeDir("/c_list") end
writeFile("/c_list/plain", "x")
writeFile("/c_list/.secret", "x")

settings.set("list.show_hidden", false)
local hiddenText = capture(function() shell.run("/rom/programs/list", "/c_list") end)
ok("list shows visible files", hiddenText:find("plain", 1, true) ~= nil, hiddenText)
ok("list hides dot files by default", hiddenText:find(".secret", 1, true) == nil, hiddenText)

settings.set("list.show_hidden", true)
local shownText = capture(function() shell.run("/rom/programs/list", "/c_list") end)
ok("list.show_hidden reveals dot files", shownText:find(".secret", 1, true) ~= nil, shownText)
settings.unset("list.show_hidden")

-- ---------------------------------------------------------------------------
section("clear")

ok("clear runs", pcall(function() shell.run("/rom/programs/clear") end))
ok("clear palette runs", pcall(function() shell.run("/rom/programs/clear", "palette") end))
ok("clear all runs", pcall(function() shell.run("/rom/programs/clear", "all") end))
local usageText = capture(function() shell.run("/rom/programs/clear", "nonsense") end)
ok("clear prints usage for an unknown argument", usageText:find("Usages", 1, true) ~= nil, usageText)

-- clear palette must actually reset a changed palette colour. nativePaletteColour returns
-- r, g, b, so compare component-wise rather than by a packed value.
local nr, ng, nb = term.nativePaletteColour(colours.red)
term.setPaletteColour(colours.red, 0.5, 0.25, 0.125)
shell.run("/rom/programs/clear", "palette")
local gr, gg, gb = term.getPaletteColour(colours.red)
ok(
  "clear palette restores the native colour",
  math.abs(gr - nr) < 0.002 and math.abs(gg - ng) < 0.002 and math.abs(gb - nb) < 0.002,
  string.format("got %.3f %.3f %.3f, expected %.3f %.3f %.3f", gr, gg, gb, nr, ng, nb)
)

-- ---------------------------------------------------------------------------
section("monitor scale")

local monitors = {}
for _, name in ipairs(peripheral.getNames()) do
  if peripheral.getType(name) == "monitor" then monitors[#monitors + 1] = name end
end

if #monitors == 0 then
  skip("monitor scale (no monitor attached)")
else
  local name = monitors[1]
  capture(function() shell.run("/rom/programs/monitor", "scale", name, "1.5") end)
  eq("scale 1.5 applied", 1.5, peripheral.call(name, "getTextScale"))
  local badText = capture(function() shell.run("/rom/programs/monitor", "scale", name, "9") end)
  ok("an out-of-range scale is rejected", badText:find("Invalid scale", 1, true) ~= nil, badText)
  peripheral.call(name, "setTextScale", 1)
end

-- ---------------------------------------------------------------------------
section("cleanup")

for _, name in ipairs(fs.list("/c_list")) do fs.delete(fs.combine("/c_list", name)) end
fs.delete("/c_list")
ok("fixtures cleaned up", not fs.exists("/c_list"))

finish("BATCH-D programs")

print("")
print("--- MANUAL DRILL ---")
print("1. Run:  about       EXPECT: CraftOS version and the host mod name.")
print("2. Run:  set         EXPECT: every setting with its current value.")
print("3. Run:  set motd.enable false  then reboot")
print("   EXPECT: no message of the day on boot.")
print("4. Run:  peripherals EXPECT: the attached peripherals, or 'None'.")
print("5. Run:  clear palette  after: term.setPaletteColour(colours.red, 0x123456)")
print("   EXPECT: red returns to its normal shade.")
print("6. With a monitor:  monitor scale <name> 2  EXPECT: text doubles in size.")
print("7. Run:  list  then:  set list.show_hidden true  then:  list")
print("   EXPECT: dot files appear only in the second listing.")

-- CraftOS terminals have no scrollback, so the verdict and any failures have to be the LAST
-- lines printed: everything above is pushed off the screen by the drill.
print("")
for i = 1, math.min(#fails, 8) do print("  FAIL " .. fails[i]) end
if #fails > 8 then print("  ... and " .. (#fails - 8) .. " more -- open the results file") end
for i = 1, math.min(#skips, 3) do print("  SKIP " .. skips[i]) end
-- The verdict is the very last line, so it is always visible.
print("RESULT " .. tostring(_batchResultName) .. ": passed " .. P .. "   failed " .. F .. "   skipped " .. S)
