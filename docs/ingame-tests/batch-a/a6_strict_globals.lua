-- Batch A / item 6: bios.strict_globals, end to end.
--
-- Single run, non-destructive: the setting is restored before the program exits, so
-- no reboot is needed. (Persistence itself is covered by a5_settings.)
--
-- What is being checked: os.run() installs a __newindex guard when
-- bios.strict_globals is true, so a program that assigns to an undeclared global fails
-- with "Attempt to create global <name>" instead of silently polluting its env.
--
-- NOTE: this must be run from the shell as `a6_strict_globals`. Running it from the
-- shell means the shell's own env chain is untouched -- we only ever pass fresh tables
-- to os.run.

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

local CHILD = ".a6_child"
local function writeChild(body)
  local h = fs.open(CHILD, "w")
  h.write(body)
  h.close()
end

local original = settings.get("bios.strict_globals")
print("bios.strict_globals is currently: " .. tostring(original))

-- ---------------------------------------------------------------------------
section("strict globals OFF (default)")

settings.unset("bios.strict_globals")
eq("default reads false", false, settings.get("bios.strict_globals"))

writeChild("leaked_global = 1")
local env = {}
local okRun, err = os.run(env, "/" .. CHILD)
eq("a program may create a global", true, okRun)
eq("the global landed in the program env", 1, env.leaked_global)
eq("and not in _G", nil, _G.leaked_global)

-- ---------------------------------------------------------------------------
section("strict globals ON")

settings.set("bios.strict_globals", true)
eq("setting reads true", true, settings.get("bios.strict_globals"))

env = {}
local okRun2, err2 = os.run(env, "/" .. CHILD)
eq("the program now fails", false, okRun2)
ok("error is a string", type(err2) == "string", type(err2))
ok(
  "error mentions 'global'",
  type(err2) == "string" and err2:find("global", 1, true) ~= nil,
  err2
)
eq("no global was created", nil, env.leaked_global)
eq("nothing leaked into _G either", nil, _G.leaked_global)

section("strict globals allow reads through __index")
-- Locals and reads of existing globals must still work; only *new* globals fail. A
-- successful run is the assertion: `fs` resolving proves __index is intact, and a
-- failure would surface as okRun3 == false. (os.run discards the chunk's return
-- values, so the program cannot report back through its return value.)
writeChild("local x = fs.getSize(\"/\")\nlocal y = tostring(x)") -- reads only, no global writes
env = {}
local okRun3, err3 = os.run(env, "/" .. CHILD)
eq("a read-only program still runs", true, okRun3)
ok("and reported no error", err3 == nil, err3)

section("strict globals do not affect the caller")
-- The shell (and therefore this program) must be unaffected: we never handed it to
-- os.run, so its env metatable is untouched.
ok("this program can still use globals of its own", type(_G) == "table", type(_G))

-- ---------------------------------------------------------------------------
-- Restore.
settings.unset("bios.strict_globals")
settings.save()
fs.delete(CHILD)
eq("bios.strict_globals restored to unset", false, settings.get("bios.strict_globals"))

finish("BATCH-A-6 strict globals")

-- FINAL SUMMARY
-- The RESULT block above is printed before the drill, and a 19-row screen plus the drill can
-- scroll it out of view. Repeat the outcome as the last line so it is always visible without
-- scrolling. (Prompted by a run where only the drill was visible.)
print("")
print("RESULT BATCH-A-6 strict globals: passed " .. P .. "   failed " .. F
  .. (F > 0 and "   -- failures also logged to the results file" or ""))
