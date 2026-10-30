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
  _batchResultName = name
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
  -- Open a REAL shell tab, not a throwaway sleep loop: a background program that never
  -- reads input looks exactly like a broken tab (typing does nothing, and the only way out
  -- is Ctrl+T), which is misleading. shell.openTab() also passes the proper program env,
  -- so the tab can see `multishell` -- launch({}, ...) would leave it nil.
  local tabId
  if shell and shell.openTab then
    tabId = shell.openTab("shell")
    ok("shell.openTab returned a tab id (" .. tostring(tabId) .. ")", type(tabId) == "number", type(tabId))
  else
    -- Not running under a shell (e.g. os.run from the BIOS): fall back to launch().
    tabId = multishell.launch({ multishell = multishell }, "/rom/programs/shell")
    ok(
      "multishell.launch returned a tab id (" .. tostring(tabId) .. ")",
      type(tabId) == "number",
      type(tabId)
    )
  end

  -- launch() resumes the new process and appends to the list synchronously, so no sleep().
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
      -- getFocus() is nil for anything that runs from /startup; see the note above.
      skip("focus restore (getFocus() was " .. tostring(before) .. ")")
    end
  else
    skip("title and focus checks (no tab id)")
  end

  -- The extra tab is left open on purpose: it is the subject of the test, and this
  -- version of multishell has no API to close a tab. The drill below says how to close it.
  print("")
  print("NOTE: this test left one extra shell tab open.")
  print("It is a normal shell -- type in it. Close it with a held Ctrl+T (see drill).")
end

finish("BATCH-A-3 multishell")

-- ---------------------------------------------------------------------------
-- Human drill, printed after finish() so the clear above cannot hide it.

print("--- MANUAL DRILL (a second shell tab is already open) ---")
print("")
print("There are NO keyboard shortcuts for tabs in this build:")
print("multishell switches tabs on mouse_click only, and the Java")
print("side reserves Ctrl+T / Ctrl+R / Ctrl+S.")
print("")
print("1. CLICK the second tab label at the top.")
print("   EXPECT: an empty prompt (banner + '/>').")
print("   Then type:  print('a','b')")
print("   EXPECT: a b   <- one space; that is the Batch A change.")
print("2. CLICK back to the first tab.")
print("   EXPECT: this report is still there and the prompt works.")
print("3. CLICK the second tab again, then HOLD Ctrl+T for ~1s.")
print("   EXPECT: the tab is terminated, shows Press any key,")
print("   and then disappears. Tapping Ctrl+T does nothing.")
print("   (Ctrl+W and arrow keys have no handler at all.)")
print("4. Type:  bg shell    EXPECT: a new tab appears.")
print("   Type:  fg shell    EXPECT: opens a tab AND switches to it.")
print("5. In a remaining tab run:  help")
print("   EXPECT: paged output still scrolls and prompts correctly;")
print("   this is the path that exercises print/write after Batch A.")
print("")
print("Report any crash, blank tab, or attempt-to-index-nil error.")

-- CraftOS terminals have no scrollback, so the verdict and any failures have to be the LAST
-- lines printed: everything above is pushed off the screen by the drill.
print("")
for i = 1, math.min(#fails, 8) do print("  FAIL " .. fails[i]) end
if #fails > 8 then print("  ... and " .. (#fails - 8) .. " more -- open the results file") end
for i = 1, math.min(#skips, 3) do print("  SKIP " .. skips[i]) end
-- The verdict is the very last line, so it is always visible.
print("RESULT " .. tostring(_batchResultName) .. ": passed " .. P .. "   failed " .. F .. "   skipped " .. S)
