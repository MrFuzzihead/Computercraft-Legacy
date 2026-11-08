-- Batch P: ROM parity fixes.
--
-- Runs unattended, including in CI. The editor, paint, the mouse and a real keyboard need a human,
-- so those live in p2_editor_manual (skipped by the runner); everything here is what a script can
-- assert about the settings, the shell API and the small programs.

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

  local h = fs.open("/batch-p-results.txt", "a")
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
  rec.setTextColor = function() end
  rec.setBackgroundColor = function() end
  rec.getTextColour = function() return 1 end
  rec.getTextColor = function() return 1 end
  rec.getBackgroundColor = function() return 1 end
  rec.setBackgroundColour = function() end
  rec.setCursorBlink = function() end
  rec.getLine = function() return "" end
  rec.getColour = function() return 1 end
  rec.getColor = function() return 1 end
  rec.setTextScale = function() end
  rec.getTextScale = function() return 1 end
  return rec
end

-- Runs a shell command with output captured, and returns what it wrote (joined).
local function capture(...)
  local rec = recorder()
  local previous = term.current()
  term.redirect(rec)
  local okRun, err = pcall(shell.run, ...)
  term.redirect(previous)
  local text = table.concat(rec.written, "")
  if not okRun then text = text .. " ERROR:" .. tostring(err) end
  return text
end

local function contains(haystack, needle)
  return string.find(tostring(haystack), needle, 1, true) ~= nil
end

local function touch(path, contents)
  local h = fs.open(path, "w")
  if h then
    if contents then h.write(contents) end
    h.close()
  end
end

-- ---------------------------------------------------------------------------
section("settings that used to be dead")
-- ---------------------------------------------------------------------------

do
  local details = settings.getDetails("bios.use_multishell")
  ok("bios.use_multishell is defined", details ~= nil and details.type == "boolean", details and details.type)
  if term.isColour() then
    eq("bios.use_multishell defaults to true on a colour terminal", true, settings.get("bios.use_multishell"))
  else
    skip("bios.use_multishell default (monochrome terminal)")
  end
end

do
  local details = settings.getDetails("lua.warn_against_use_of_local")
  ok(
    "lua.warn_against_use_of_local is defined",
    details ~= nil and details.type == "boolean",
    details and details.type
  )
  eq("lua.warn_against_use_of_local defaults to true", true, settings.get("lua.warn_against_use_of_local"))
end

do
  local details = settings.getDetails("paint.default_extension")
  ok("paint.default_extension is defined", details ~= nil and details.type == "string", details and details.type)
  eq("paint.default_extension defaults to nfp", "nfp", settings.get("paint.default_extension"))
end

-- ---------------------------------------------------------------------------
section("rename validates before calling fs.move")
-- ---------------------------------------------------------------------------

do
  fs.delete("/batch-p-src")
  fs.delete("/batch-p-dst")

  local out = capture("rename", "/batch-p-missing", "/batch-p-x")
  ok("a missing source is reported, not a traceback", contains(out, "No matching files"), out)
  ok("nothing was created for a missing source", not fs.exists("/batch-p-x"))

  out = capture("rename", "/rom", "/batch-p-x")
  ok("a mount root is refused", contains(out, "Can't rename mounts"), out)

  out = capture("rename", "/rom/startup", "/batch-p-x")
  ok("a read-only source is refused", contains(out, "Source is read-only"), out)

  touch("/batch-p-src", "one")
  touch("/batch-p-dst", "two")
  out = capture("rename", "/batch-p-src", "/batch-p-dst")
  ok("an existing destination is refused rather than overwritten", contains(out, "Destination exists"), out)
  ok("the source is untouched after a refused rename", fs.exists("/batch-p-src"))
  ok("the destination still holds its own contents", fs.getSize("/batch-p-dst") == 3, fs.getSize("/batch-p-dst"))

  out = capture("rename", "/batch-p-src", "/batch-p-renamed")
  eq("a valid rename prints nothing", "", out)
  ok("a valid rename moved the file", fs.exists("/batch-p-renamed") and not fs.exists("/batch-p-src"))

  fs.delete("/batch-p-dst")
  fs.delete("/batch-p-renamed")
end

-- ---------------------------------------------------------------------------
section("arg[0] is the command as typed")
-- ---------------------------------------------------------------------------

do
  -- copy/delete/mkdir/move print usage from arg[0]; it used to be the resolved path, so the
  -- message read "Usage: rom/programs/copy <source> <destination>".
  local out = capture("copy")
  ok("copy's usage line names the program, not its path", contains(out, "Usage: copy <source>"), out)
  ok("copy's usage line does not contain a path", not contains(out, "rom/programs/copy"), out)

  out = capture("delete")
  ok("delete's usage line names the program", contains(out, "Usage: delete"), out)

  if shell.openTab then
    -- An alias keeps its own name, which is what a user typed.
    out = capture("cp")
    ok("an alias is preserved in arg[0]", contains(out, "Usage: cp <source>"), out)
  else
    skip("alias usage line (no shell.openTab)")
  end
end

-- ---------------------------------------------------------------------------
section("shell.execute validates its arguments")
-- ---------------------------------------------------------------------------

do
  local called, err = pcall(shell.execute, "copy", 5)
  ok("a non-string argument is rejected", called == false, tostring(called))
  ok("the rejection names the expected type", contains(err, "string expected"), tostring(err))
end

-- ---------------------------------------------------------------------------
section("shell tab title drops a .lua extension")
-- ---------------------------------------------------------------------------

if multishell then
  -- shell.run titles the tab from the program's file name. A probe reads its own title while it
  -- is running, which is the only moment the title is the program's rather than the shell's.
  touch("/batch-p-tabprobe.lua", "local h = fs.open('/batch-p-title.txt', 'w')\n"
    .. "h.write(multishell.getTitle(multishell.getCurrent()))\n"
    .. "h.close()\n")
  fs.delete("/batch-p-title.txt")
  capture("/batch-p-tabprobe.lua")

  local h = fs.open("/batch-p-title.txt", "r")
  local title = h and h.readAll() or ""
  if h then h.close() end
  eq("a tab title drops the .lua extension", "batch-p-tabprobe", title)
  fs.delete("/batch-p-tabprobe.lua")
  fs.delete("/batch-p-title.txt")
else
  skip("tab title (bios.use_multishell is off, or no multishell)")
end

-- ---------------------------------------------------------------------------
section("id reports audio and non-disk sources")
-- ---------------------------------------------------------------------------

do
  -- A disk drive is a peripheral; without one there is nothing to ask.
  local drive = nil
  for _, name in ipairs(peripheral.getNames()) do
    if peripheral.getType(name) == "drive" then
      drive = name
      break
    end
  end

  if drive == nil then
    skip("id on a disk drive (no drive attached)")
  elseif not disk.isPresent(drive) then
    local out = capture("id", drive)
    ok("id reports an empty drive", contains(out, "No disk in drive"), out)
  elseif disk.hasAudio(drive) then
    local out = capture("id", drive)
    ok("id reports an audio disc as audio", contains(out, "audio"), out)
    ok("id does not call an audio disc empty", not contains(out, "No disk in drive"), out)
  else
    local out = capture("id", drive)
    ok("id reports a data disk", contains(out, "The disk is #") or contains(out, "Non-disk data source"), out)
  end
end

-- ---------------------------------------------------------------------------
print("")
print("--- MANUAL DRILL ---")
print("The editor, paint and the mouse need a human: run p2_editor_manual.")
print("")

finish("BATCH-P rom parity")
