-- Batch A / item 3: multishell still works after the print/write/os.run changes.
--
-- Batch A touches print(), write(), os.run() and the per-program environment chain,
-- all of which multishell and the shell sit on top of. This checks the scriptable half;
-- the Ctrl+T / Ctrl+W keybinds in the drill at the end cannot be driven from Lua.
--
-- Skips cleanly on a computer without multishell (monochrome terminal, or
-- bios.use_multishell = false).

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

section("environment")
if multishell == nil then
  skip("multishell is not available on this computer")
  print("(monochrome terminal, or settings.set('bios.use_multishell', false))")
else
  ok("multishell table present", type(multishell) == "table")

  local count0 = multishell.getCount()
  ok("at least one tab", count0 >= 1, count0)
  ok("getCurrent returns a number", type(multishell.getCurrent()) == "number", type(multishell.getCurrent()))

  -- getFocus() is nil while /startup runs (and therefore also when this script is launched
  -- from /startup, as the automated harness does): the shell's process is resumed inside
  -- launchProcess() and only becomes nCurrentProcess once selectProcess() runs, after
  -- launchProcess() returns. Harmless, but it must not be asserted away.
  local focus0 = multishell.getFocus()
  if type(focus0) == "number" then
    ok("getFocus returns a number", true)
  else
    skip("getFocus before the menu is first selected (" .. tostring(focus0) .. " when run from /startup)")
  end

  section("title")
  local cur = multishell.getCurrent()
  local oldTitle = multishell.getTitle(cur)
  multishell.setTitle(cur, "batch-a")
  eq("setTitle/getTitle round-trip", "batch-a", multishell.getTitle(cur))
  multishell.setTitle(cur, oldTitle)

  section("launch a second tab")
  -- A program that prints once and stays alive so the tab persists.
  local h = fs.open("/.a3_tab", "w")
  h.write("print('a3 tab running: ' .. tostring(multishell and multishell.getCurrent()))\n")
  h.write("while true do sleep(1) end\n")
  h.close()

  local tabId = multishell.launch({}, "/.a3_tab")
  -- Report what launch() actually returned rather than just "not a number": this IS the
  -- diagnostic a human needs if the tab id is unexpectedly nil.
  ok("launch returned a tab id (" .. tostring(tabId) .. ")", type(tabId) == "number", type(tabId))

  -- launch() appends to the process list synchronously, so the count is already
  -- updated; no sleep() here, which also keeps this script runnable without timers.
  ok(
    "tab count increased (" .. count0 .. " -> " .. tostring(multishell.getCount()) .. ")",
    multishell.getCount() > count0
  )

  if type(tabId) == "number" then
    ok("the new tab has a title (" .. tostring(multishell.getTitle(tabId)) .. ")", multishell.getTitle(tabId) ~= nil)

    section("focus switching")
    local before = multishell.getFocus()
    multishell.setFocus(tabId)
    eq("setFocus moved focus", tabId, multishell.getFocus())
    if type(before) == "number" then
      multishell.setFocus(before)
      eq("focus restored", before, multishell.getFocus())
    else
      -- getFocus() is nil until the menu has been shown at least once in some builds.
      skip("focus restore (getFocus() was " .. tostring(before) .. ")")
    end
  else
    skip("title and focus checks (no tab id)")
  end

  -- Terminate the extra tab by alternating focus; multishell has no close API in this
  -- version, so leave it for the user to close with Ctrl+W in the drill.
  fs.delete("/.a3_tab")
  ok("temporary tab program removed", not fs.exists("/.a3_tab"))
end

finish("BATCH-A-3 multishell")

-- ---------------------------------------------------------------------------
-- Human drill, printed after finish() so the clear above cannot hide it.

print("--- MANUAL DRILL (multishell keybinds) ---")
print("1. Ctrl+T            -> a new tab appears, shell banner shows.")
print("   EXPECT: two tabs drawn at the top, active one highlighted.")
print("2. In the new tab type:  print('a','b')")
print("   EXPECT: 'a b' (one space), no error.")
print("3. Ctrl+W            -> the current tab closes.")
print("4. Ctrl+T again, then Ctrl+Left/Right (or click a tab)")
print("   EXPECT: focus moves between tabs, prompt follows.")
print("5. In tab 1 run:  help")
print("   EXPECT: paged output still scrolls and prompts correctly.")
print("6. Close every extra tab so only the shell remains.")
print("")
print("Report any crash, blank tab, or 'attempt to index nil' error.")

