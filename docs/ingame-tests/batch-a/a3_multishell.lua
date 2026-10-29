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
  -- The tab program reports back through a file rather than through the screen, because
  -- its output lands in its own window. It also checks that the tab can see `multishell`:
  -- launch() only passes the env you give it, so a bare {} leaves `multishell` nil inside
  -- the tab (getCurrent() then evaluates to nil). That is a caller mistake, not a bug, and
  -- the same applies to shell.openTab(), which passes the shell env for you.
  local h = fs.open("/.a3_tab", "w")
  h.write("local id = multishell and multishell.getCurrent()\n")
  h.write("local f = fs.open(\"/.a3_tab_result\", \"w\")\n")
  h.write("f.write(tostring(id))\n")
  h.write("f.close()\n")
  h.write("while true do sleep(1) end\n")
  h.close()

  local tabId = multishell.launch({ multishell = multishell }, "/.a3_tab")
  -- Report what launch() actually returned rather than just "not a number": this IS the
  -- diagnostic a human needs if the tab id is unexpectedly nil.
  ok("launch returned a tab id (" .. tostring(tabId) .. ")", type(tabId) == "number", type(tabId))

  -- launch() resumes the new process and appends to the list synchronously, so neither the
  -- count nor the tab result file needs a sleep().
  ok(
    "tab count increased (" .. count0 .. " -> " .. tostring(multishell.getCount()) .. ")",
    multishell.getCount() > count0
  )

  if type(tabId) == "number" then
    ok("the new tab has a title (" .. tostring(multishell.getTitle(tabId)) .. ")", multishell.getTitle(tabId) ~= nil)

    if fs.exists("/.a3_tab_result") then
      local r = fs.open("/.a3_tab_result", "r")
      local reported = r.readAll()
      r.close()
      eq("the tab sees its own id via multishell.getCurrent()", tostring(tabId), reported)
    else
      ok("the tab program ran", false, "/.a3_tab_result was not written")
    end

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
    skip("title, tab-env and focus checks (no tab id)")
  end

  fs.delete("/.a3_tab")
  fs.delete("/.a3_tab_result")
  ok("temporary tab files removed", not fs.exists("/.a3_tab"))
end

finish("BATCH-A-3 multishell")

-- ---------------------------------------------------------------------------
-- Human drill, printed after finish() so the clear above cannot hide it.

print("--- MANUAL DRILL: what this build actually supports ---")
print("There are NO keyboard shortcuts for tabs in this build.")
print("multishell only switches tabs on mouse_click, and the Java side")
print("reserves Ctrl+T / Ctrl+R / Ctrl+S. Checking it, in order:")
print("")
print("1. Type:  bg shell")
print("   EXPECT: a second tab appears at the top, titled shell.")
print("   (bg opens a tab; fg opens one and switches to it.)")
print("2. CLICK the tab labels at the top to switch between them.")
print("   EXPECT: the prompt follows the click. Arrow keys do nothing.")
print("3. Tap Ctrl+T, then Ctrl+W. EXPECT: NOTHING happens.")
print("   This is correct: no handler exists for Ctrl+W, and Ctrl+T")
print("   needs a 1-second HOLD before it fires.")
print("4. In a spare tab, HOLD Ctrl+T for about a second.")
print("   EXPECT: the running program is terminated (a terminate")
print("   event), then the tab shows Press any key and closes.")
print("   Ctrl+R held = reboot, Ctrl+S held = shutdown. Do not test")
print("   those here unless you are happy to reboot.")
print("5. In a remaining tab run:  help")
print("   EXPECT: paged output still scrolls and prompts correctly;")
print("   this is the path that exercises print/write after Batch A.")
print("")
print("Report any crash, blank tab, or attempt-to-index-nil error.")
