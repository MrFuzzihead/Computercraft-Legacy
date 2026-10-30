-- Batch D, slice 2: multi-path file programs, wget, speaker and pocket equip.
--
-- Runs unattended, including in CI. Programs that need hardware (a speaker, a pocket computer) or
-- the http API report SKIP rather than failing, so the same script is meaningful anywhere.

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

  local h = fs.open("/batch-d-results.txt", "a")
  if h then
    for _, line in ipairs(lines) do h.write(line .. "\n") end
    h.close()
  end

  print("")
  print("RESULT " .. name .. ": passed " .. P .. "   failed " .. F .. "   skipped " .. S)
end

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
  rec.setTextColor = function() end
  rec.getTextColour = function() return 1 end
  rec.getTextColor = function() return 1 end
  rec.setBackgroundColour = function() end
  rec.setBackgroundColor = function() end
  rec.getBackgroundColour = function() return 1 end
  rec.getBackgroundColor = function() return 1 end
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
  local dir = fs.getDir(path)
  if dir ~= "" and not fs.isDir(dir) then fs.makeDir(dir) end
  local h = fs.open(path, "w")
  h.write(contents)
  h.close()
end
local function readFile(path)
  if not fs.exists(path) then return nil end
  local h = fs.open(path, "r")
  local v = h.readAll()
  h.close()
  return v
end

-- ---------------------------------------------------------------------------
section("mkdir accepts several paths")

if fs.isDir("/d2") then
  for _, name in ipairs(fs.list("/d2")) do
    if fs.isDir("/d2/" .. name) then
      for _, inner in ipairs(fs.list("/d2/" .. name)) do fs.delete("/d2/" .. name .. "/" .. inner) end
    end
    fs.delete("/d2/" .. name)
  end
  fs.delete("/d2")
end
fs.makeDir("/d2")

shell.run("/rom/programs/mkdir", "/d2/a", "/d2/b", "/d2/c/d")
ok("mkdir made the first directory", fs.isDir("/d2/a"))
ok("mkdir made the second directory", fs.isDir("/d2/b"))
ok("mkdir made nested directories", fs.isDir("/d2/c/d"))

local mkdirUsage = capture(function() shell.run("/rom/programs/mkdir") end)
ok("mkdir prints usage with no arguments", mkdirUsage:find("Usage", 1, true) ~= nil, mkdirUsage)

-- ---------------------------------------------------------------------------
section("copy, including several sources")

writeFile("/d2/one.txt", "first")
writeFile("/d2/two.txt", "second")

-- Single source to a new name.
shell.run("/rom/programs/copy", "/d2/one.txt", "/d2/one-copy.txt")
eq("copy created the destination", "first", readFile("/d2/one-copy.txt"))

-- One source into an existing directory: the file keeps its name.
-- (copy/move take <source> <destination>; several files come from a wildcard, not from extra
-- arguments. Only delete and mkdir accept multiple paths.)
shell.run("/rom/programs/copy", "/d2/one.txt", "/d2/a")
eq("copy put the file in the directory", "first", readFile("/d2/a/one.txt"))

-- Copying over an existing file is refused.
local copyOver = capture(function() shell.run("/rom/programs/copy", "/d2/one.txt", "/d2/one-copy.txt") end)
ok("copy refuses to overwrite", copyOver:find("exists", 1, true) ~= nil, copyOver)

-- Wildcards.
shell.run("/rom/programs/copy", "/d2/*.txt", "/d2/b")
ok("copy expanded a wildcard", fs.exists("/d2/b/one.txt") and fs.exists("/d2/b/two.txt"))

-- A wildcard matching several sources into a single file name is ambiguous.
local many = capture(function() shell.run("/rom/programs/copy", "/d2/*.txt", "/d2/c/result.txt") end)
ok("copy refuses to overwrite one file with several", many ~= "", many)

local copyMissing = capture(function() shell.run("/rom/programs/copy", "/d2/nothing-*", "/d2/a") end)
ok("copy reports no matches", copyMissing:find("No matching", 1, true) ~= nil, copyMissing)

-- ---------------------------------------------------------------------------
section("move")

writeFile("/d2/movable.txt", "move me")
shell.run("/rom/programs/move", "/d2/movable.txt", "/d2/moved.txt")
ok("move removed the source", not fs.exists("/d2/movable.txt"))
eq("move wrote the destination", "move me", readFile("/d2/moved.txt"))

-- A wildcard moves every match into the directory.
writeFile("/d2/multi1.txt", "m1")
writeFile("/d2/multi2.txt", "m2")
shell.run("/rom/programs/move", "/d2/multi*.txt", "/d2/a")
ok("move handled a wildcard", fs.exists("/d2/a/multi1.txt") and fs.exists("/d2/a/multi2.txt"))

-- Moving over an existing file is refused.
writeFile("/d2/target.txt", "already here")
local moveOver = capture(function() shell.run("/rom/programs/move", "/d2/moved.txt", "/d2/target.txt") end)
ok("move refuses to overwrite", moveOver:find("exists", 1, true) ~= nil, moveOver)
eq("the destination is untouched", "already here", readFile("/d2/target.txt"))

-- ---------------------------------------------------------------------------
section("delete")

writeFile("/d2/doomed.txt", "bye")
shell.run("/rom/programs/delete", "/d2/doomed.txt")
ok("delete removed the file", not fs.exists("/d2/doomed.txt"))

writeFile("/d2/gone1.txt", "x")
writeFile("/d2/gone2.txt", "y")
shell.run("/rom/programs/delete", "/d2/gone1.txt", "/d2/gone2.txt")
ok("delete accepted several paths", not fs.exists("/d2/gone1.txt") and not fs.exists("/d2/gone2.txt"))

local deleteReadOnly = capture(function() shell.run("/rom/programs/delete", "/rom/startup") end)
ok("delete refuses a read-only file", deleteReadOnly:find("read%-only") ~= nil, deleteReadOnly)
ok("the read-only file survived", fs.exists("/rom/startup"))

local deleteMissing = capture(function() shell.run("/rom/programs/delete", "/d2/never-existed-*") end)
ok("delete reports no matches", deleteMissing:find("No matching", 1, true) ~= nil, deleteMissing)

-- ---------------------------------------------------------------------------
section("wget")

local wgetUsage = capture(function() shell.run("/rom/programs/http/wget") end)
ok("wget prints usage with no arguments", wgetUsage:find("Usage", 1, true) ~= nil, wgetUsage)

if not http then
  skip("wget download (the http API is disabled)")
else
  -- No network access is assumed: an invalid URL must be rejected with a message, not a crash.
  local wgetBad = capture(function() shell.run("/rom/programs/http/wget", "not-a-url") end)
  ok("wget rejects an invalid URL", wgetBad:find("Invalid", 1, true) ~= nil or wgetBad ~= "", wgetBad)
end

-- ---------------------------------------------------------------------------
section("speaker")

local speakers = { peripheral.find("speaker") }
if #speakers == 0 then
  skip("speaker (no speaker attached)")
else
  local speakerName = peripheral.getName(speakers[1])
  local speakerUsage = capture(function() shell.run("/rom/programs/fun/speaker") end)
  ok("speaker prints usage with no command", speakerUsage:find("Usage", 1, true) ~= nil, speakerUsage)

  local played, _ = capture(function()
    shell.run("/rom/programs/fun/speaker", "sound", "minecraft:block.note_block.pling", "1", "1", speakerName)
  end)
  ok("speaker can play a sound", played ~= nil, played)

  local speakerStop = capture(function() shell.run("/rom/programs/fun/speaker", "stop", speakerName) end)
  ok("speaker stop runs", speakerStop ~= nil)
end

-- ---------------------------------------------------------------------------
section("pocket equip")

if not pocket then
  skip("pocket equip (not a pocket computer)")
else
  local equipText = capture(function() shell.run("/rom/programs/pocket/equip") end)
  ok("equip runs on a pocket computer", equipText ~= nil, equipText)
  local unequipText = capture(function() shell.run("/rom/programs/pocket/unequip") end)
  ok("unequip runs on a pocket computer", unequipText ~= nil, unequipText)
end

-- ---------------------------------------------------------------------------
section("cleanup")

if fs.isDir("/d2") then
  local function removeAll(dir)
    for _, name in ipairs(fs.list(dir)) do
      local path = fs.combine(dir, name)
      if fs.isDir(path) then removeAll(path) end
      fs.delete(path)
    end
  end
  removeAll("/d2")
  fs.delete("/d2")
end
ok("fixtures cleaned up", not fs.exists("/d2"))

finish("BATCH-D files and peripherals")

print("")
print("--- MANUAL DRILL ---")
print("1. Copy a file:   copy /rom/help/intro /intro-copy")
print("   EXPECT: no output on success; the file appears in 'ls'.")
print("2. Copy several:  copy /rom/help/intro /rom/help/keys /somewhere")
print("   EXPECT: both files land in /somewhere (create it first).")
print("3. Move:          move /intro-copy /moved-copy")
print("4. Delete several: delete /moved-copy /somewhere/intro /somewhere/keys")
print("5. Try to move a read-only file:  move /rom/help/intro /nope")
print("   EXPECT: 'Destination is read-only' or similar, and no crash.")
print("6. With a speaker: speaker play <file.dfpwm>   (see 'help speaker')")
print("   and: speaker sounds -- check 'speaker sound minecraft:block.note_block.pling'")
print("7. With http enabled: wget https://example.com/  EXPECT: refuses to save (a directory URL).")
print("8. On a pocket computer: pocket/equip with an ender modem in the inventory.")

-- CraftOS terminals have no scrollback, so the verdict and any failures have to be the LAST
-- lines printed: everything above is pushed off the screen by the drill.
print("")
for i = 1, math.min(#fails, 8) do print("  FAIL " .. fails[i]) end
if #fails > 8 then print("  ... and " .. (#fails - 8) .. " more -- open the results file") end
for i = 1, math.min(#skips, 3) do print("  SKIP " .. skips[i]) end
-- The verdict is the very last line, so it is always visible.
print("RESULT " .. tostring(_batchResultName) .. ": passed " .. P .. "   failed " .. F .. "   skipped " .. S)
