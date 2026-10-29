-- Batch A / item 5: settings defaults + persistence.
--
-- Two phases, because persistence needs a reboot:
--
--   a5_settings check      (first run: verifies defaults, writes a throwaway key, saves)
--   ... reboot the computer ...
--   a5_settings check      (second run: verifies the throwaway key survived)
--
-- "check" is also the default, so `a5_settings` twice is fine.
--
-- Deliberately uses an unregistered key (batcha.test) for the persistence check so it
-- cannot clash with, or overwrite, a real setting. It is removed again at the end.

local P, F, S = 0, 0, 0
local fails = {}
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
local function skip(label) S = S + 1 print("SKIP " .. label) end
local function section(n) print("== " .. n .. " ==") end
-- Prints the summary, and also appends it to /batch-a-results.txt. The log matters because
-- the manual drills printed afterwards scroll a 19-row screen, so the on-screen copy is not
-- always the last thing visible. Review the log with:  type /batch-a-results.txt
local function finish(name)
  local lines = { "RESULT " .. name }
  lines[#lines + 1] = "  passed " .. P .. "   failed " .. F .. "   skipped " .. S
  for i = 1, #fails do lines[#lines + 1] = "  FAIL " .. fails[i] end

  term.clear()
  term.setCursorPos(1, 1)
  for _, line in ipairs(lines) do print(line) end

  local h = fs.open("/batch-a-results.txt", "a")
  if h then
    for _, line in ipairs(lines) do h.write(line .. "\n") end
    h.close()
  end
end

local KEY = "batcha.test"
local PHASE_FILE = ".batch-a-settings-phase"

-- ---------------------------------------------------------------------------
section("BIOS-registered defaults")

-- These are defined by bios.lua before the shell starts. A missing name means the
-- settings.define block did not run (or ran too late).
eq("edit.default_extension", "lua", settings.get("edit.default_extension"))
eq("paint.default_extension", "nfp", settings.get("paint.default_extension"))
eq("shell.allow_startup", true, settings.get("shell.allow_startup"))
eq("shell.autocomplete", true, settings.get("shell.autocomplete"))
eq("shell.autocomplete_hidden", false, settings.get("shell.autocomplete_hidden"))
eq("edit.autocomplete", true, settings.get("edit.autocomplete"))
eq("lua.autocomplete", true, settings.get("lua.autocomplete"))
eq("list.show_hidden", false, settings.get("list.show_hidden"))
eq("motd.enable", true, settings.get("motd.enable"))
eq("motd.path", "/rom/motd.txt:/motd.txt", settings.get("motd.path"))
eq("lua.function_args", true, settings.get("lua.function_args"))
eq("lua.function_source", false, settings.get("lua.function_source"))
eq("lua.warn_against_use_of_local", true, settings.get("lua.warn_against_use_of_local"))
eq("bios.strict_globals", false, settings.get("bios.strict_globals"))

-- motd.enable defaults to false on pocket computers and shell.allow_disk_startup to
-- false on command computers; report rather than assert.
if pocket then
  eq("motd.enable on a pocket computer", false, settings.get("motd.enable"))
else
  eq("motd.enable on a normal computer", true, settings.get("motd.enable"))
end

section("defaults are visible via getNames")
local names = settings.getNames()
local function has(name)
  for _, n in ipairs(names) do if n == name then return true end end
  return false
end
for _, name in ipairs({
  "shell.allow_startup", "shell.allow_disk_startup", "shell.autocomplete",
  "shell.autocomplete_hidden", "edit.autocomplete", "lua.autocomplete",
  "edit.default_extension", "paint.default_extension", "list.show_hidden",
  "motd.enable", "motd.path", "lua.warn_against_use_of_local", "lua.function_args",
  "lua.function_source", "bios.strict_globals",
}) do
  ok("getNames has " .. name, has(name))
end

section("type enforcement")
-- settings.set must reject a value of the wrong type for a registered setting.
local okSet = pcall(settings.set, "motd.path", { 1, 2, 3 })
ok("setting a table on a string setting raises", not okSet)
local okSet2 = pcall(settings.set, "shell.autocomplete", "yes")
ok("setting a string on a boolean setting raises", not okSet2)

-- ---------------------------------------------------------------------------
section("persistence across reboot")

local phase = "first"
if fs.exists(PHASE_FILE) then
  local h = fs.open(PHASE_FILE, "r")
  phase = h.readAll()
  h.close()
end

if phase ~= "second" then
  print("phase 1: writing " .. KEY .. "=42 and saving")
  settings.set(KEY, 42)
  ok("save() succeeded", settings.save() == true)
  eq("in-memory value", 42, settings.get(KEY))

  local h = fs.open(PHASE_FILE, "w")
  h.write("second")
  h.close()

else
  print("phase 2: checking the value survived the reboot")
  eq("" .. KEY .. " survived the reboot", 42, settings.get(KEY))
  eq("the value is still a number", "number", type(settings.get(KEY)))

  -- Clean up so the throwaway key does not linger in .settings.
  settings.unset(KEY)
  settings.save()
  fs.delete(PHASE_FILE)
  ok("" .. KEY .. " removed again", settings.get(KEY) == nil)
end

-- The phase goes in the RESULT name so the results log distinguishes the two runs.
local phaseName = (phase == "second") and "phase 2" or "phase 1"
finish("BATCH-A-5 settings (" .. phaseName .. ")")

if phase ~= "second" then
  -- Printed after finish() so clearing the screen cannot hide it.
  print("")
  print("NOW REBOOT (Ctrl+R) and run a5_settings again.")
  print("Phase 2 will verify that " .. KEY .. "=42 survived.")
end
