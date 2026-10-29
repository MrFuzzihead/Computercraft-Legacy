-- Batch A / item 9: fs.complete include_hidden.
--
-- The first half is fully automated. The second half is an interactive drill: the
-- shell's own completion path is what users actually hit, and it cannot be driven from
-- Lua, so it prints exact keystrokes and waits for you.
--
-- Layout created under /bat:
--   .hidden      (file)
--   visible      (file)
--   sub/         (directory)

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
-- Skips are recorded as well as printed: finish() clears the screen, which would otherwise
-- destroy the only explanation for a non-zero skipped count.
local skips = {}
local function skip(label)
  S = S + 1
  skips[#skips + 1] = label
  print("SKIP " .. label)
end
local function section(n) print("== " .. n .. " ==") end
-- Prints the summary, and also appends it to /batch-a-results.txt. The log matters because
-- the manual drills printed afterwards scroll a 19-row screen, so the on-screen copy is not
-- always the last thing visible. Review the log with:  type /batch-a-results.txt
local function finish(name)
  local lines = { "RESULT " .. name }
  lines[#lines + 1] = "  passed " .. P .. "   failed " .. F .. "   skipped " .. S
  for i = 1, #fails do lines[#lines + 1] = "  FAIL " .. fails[i] end
  for i = 1, #skips do lines[#lines + 1] = "  SKIP " .. skips[i] end

  term.clear()
  term.setCursorPos(1, 1)
  for _, line in ipairs(lines) do print(line) end

  local h = fs.open("/batch-a-results.txt", "a")
  if h then
    for _, line in ipairs(lines) do h.write(line .. "\n") end
    h.close()
  end
end

local function contains(list, value)
  for _, v in ipairs(list) do if v == value then return true end end
  return false
end
local function dump(list) return "[" .. table.concat(list, ",") .. "]" end

-- ---------------------------------------------------------------------------
section("setup")

if fs.exists("/bat") then fs.delete("/bat") end
fs.makeDir("/bat")
local h = fs.open("/bat/.hidden", "w") h.write("x") h.close()
h = fs.open("/bat/visible", "w") h.write("x") h.close()
fs.makeDir("/bat/sub")
ok("fixture created", fs.exists("/bat/.hidden") and fs.exists("/bat/visible") and fs.isDir("/bat/sub"))

-- ---------------------------------------------------------------------------
section("legacy boolean arguments keep hidden entries")

-- This is the important compatibility case: every existing caller passes booleans,
-- and must keep seeing hidden files.
local r = fs.complete("", "/bat", true, false)
ok("boolean form includes .hidden", contains(r, ".hidden"), dump(r))
ok("boolean form includes visible", contains(r, "visible"), dump(r))

-- ---------------------------------------------------------------------------
section("include_hidden = false")

r = fs.complete("", "/bat", { include_files = true, include_dirs = false, include_hidden = false })
ok("visible still offered", contains(r, "visible"), dump(r))
ok(".hidden filtered out", not contains(r, ".hidden"), dump(r))

r = fs.complete("", "/bat", { include_files = true, include_dirs = false, include_hidden = true })
ok(".hidden offered when re-enabled", contains(r, ".hidden"), dump(r))

-- Typing a leading dot is an explicit request for hidden entries, so they must still be
-- offered even when include_hidden is false.
r = fs.complete(".h", "/bat", { include_files = true, include_dirs = false, include_hidden = false })
ok("'.h' completes .hidden anyway", contains(r, "idden"), dump(r))

-- ---------------------------------------------------------------------------
section("include_dirs")

r = fs.complete("", "/bat", { include_files = true, include_dirs = false })
ok("directory is offered with a trailing slash", contains(r, "sub/"), dump(r))
ok("raw directory entry suppressed", not contains(r, "sub"), dump(r))

r = fs.complete("", "/bat", { include_files = true, include_dirs = true })
ok("raw directory entry restored", contains(r, "sub"), dump(r))

-- ---------------------------------------------------------------------------
section("prefix filtering and defaults")

r = fs.complete("vis", "/bat", true, true)
ok("prefix match works", contains(r, "ible"), dump(r))
ok("non-matching entry excluded", not contains(r, "sub"), dump(r))

r = fs.complete("", "/bat", { include_files = false, include_dirs = false })
ok("both flags false yields no plain entries", #r <= 2, dump(r))

-- A non-existent directory returns an empty table rather than throwing.
local okMissing, missing = pcall(fs.complete, "", "/does-not-exist", true, true)
ok("missing directory does not raise", okMissing, missing)
ok("missing directory returns empty", okMissing and #missing == 0, missing)

-- ---------------------------------------------------------------------------
section("cleanup")
fs.delete("/bat")
ok("fixture removed", not fs.exists("/bat"))

-- ---------------------------------------------------------------------------
-- Interactive drill. The shell uses fs.complete through shell.complete, which passes
-- booleans, so hidden entries must complete.
finish("BATCH-A-9 fs.complete")


print("--- MANUAL DRILL (shell completion) ---")
print("1. Run:  mkdir /drill")
print("2. Run:  fs.open('/drill/.secret','w').close()")
print("3. Run:  fs.open('/drill/plain','w').close()")
print("4. Type: /drill/    then press TAB")
print("   EXPECT: both '.secret' and 'plain' appear.")
print("   (Hidden entries are still completed by default.)")
print("5. Type: ls /drill/ then TAB, and confirm 'list' still completes")
print("   file arguments without error.")
print("")
print("Report anything that differs. Clean up with: rm /drill")

-- FINAL SUMMARY
-- The RESULT block above is printed before the drill, and a 19-row screen plus the drill can
-- scroll it out of view. Repeat the outcome as the last line so it is always visible without
-- scrolling. (Prompted by a run where only the drill was visible.)
print("")
print("RESULT BATCH-A-9 fs.complete: passed " .. P .. "   failed " .. F
  .. (F > 0 and "   -- failures also logged to the results file" or ""))
