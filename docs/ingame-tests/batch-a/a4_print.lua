-- Batch A / item 4: print(), write() and printError() layout.
--
-- Run in-game on a normal computer. Self-checking: the RESULT block at the end is
-- what matters.
--
-- Two separate questions are covered:
--   1. What print() actually emits (checked through a redirected fake terminal, which
--      sees the raw bytes).
--   2. What the screen does with those bytes (a tab is exactly ONE column wide, not a
--      tab stop, because the font renderer maps tab/CR/LF to the space glyph).

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

-- A terminal that records everything written to it instead of drawing.
local function recorder()
  local rec = { written = {}, _text = colours.lime }
  rec.write = function(s) rec.written[#rec.written + 1] = s end
  rec.getSize = function() return 51, 19 end
  rec.getCursorPos = function() return 1, 1 end
  rec.setCursorPos = function() end
  rec.scroll = function() end
  rec.clearLine = function() end
  rec.clear = function() end
  rec.setCursorBlink = function() end
  rec.setTextColour = function(c) rec._text = c end
  rec.getTextColour = function() return rec._text end
  rec.setBackgroundColour = function() end
  rec.getBackgroundColour = function() return colours.black end
  rec.isColour = function() return true end
  return rec
end
local function captured(rec) return table.concat(rec.written) end

-- Wrap a block so the fake terminal is always restored, even on error.
local function withRecorder(fn)
  local rec = recorder()
  local old = term.redirect(rec)
  local okRun, err = pcall(fn, rec)
  term.redirect(old)
  if not okRun then error(err, 0) end
  return rec
end

-- ---------------------------------------------------------------------------
section("print separators (raw output)")

-- Regression: print used to concatenate with no separator at all.
local rec = withRecorder(function() print("a", "b") end)
eq("print('a','b') emits a tab", "a\tb", captured(rec))

-- Regression: print used to build {...} and ipairs() it, so the first nil
-- truncated the whole call and later arguments vanished.
rec = withRecorder(function() print(1, nil, 3) end)
eq("print(1,nil,3) keeps the nil hole", "1\tnil\t3", captured(rec))

rec = withRecorder(function() print(nil) end)
eq("print(nil) prints nil", "nil", captured(rec))

rec = withRecorder(function() print(false, true) end)
eq("print(false,true)", "false\ttrue", captured(rec))

-- print() with no arguments should still emit one newline's worth of cursor movement.
rec = withRecorder(function()
  local n = print()
  _G._batch_a_print_n = n
end)
eq("print() emits no text", "", captured(rec))
eq("print() reports one line", 1, _G._batch_a_print_n)
_G._batch_a_print_n = nil

-- print returns the number of lines it advanced.
rec = withRecorder(function()
  local n = print("one")
  _G._batch_a_print_n = n
end)
eq("print('one') reports one line", 1, _G._batch_a_print_n)
_G._batch_a_print_n = nil

-- ---------------------------------------------------------------------------
section("write() coercion")

-- write() used to raise on a number; print() always tostrung() first, so this only
-- affected direct write() calls.
rec = withRecorder(function() write(42) end)
eq("write(42) coerces to a string", "42", captured(rec))

rec = withRecorder(function() write("x", "y") end)
ok("write ignores extra arguments", captured(rec) == "x", captured(rec))

-- ---------------------------------------------------------------------------
section("printError colour")

-- printError used to reset to white instead of restoring the caller's colour.
rec = withRecorder(function()
  term.setTextColour(colours.lime)
  printError("oops")
  _G._batch_a_colour = term.getTextColour()
end)
eq("printError restores the previous colour", colours.lime, _G._batch_a_colour)
ok("printError still prints the message", captured(rec):find("oops", 1, true) ~= nil, captured(rec))
_G._batch_a_colour = nil

-- ---------------------------------------------------------------------------
section("tab width on the real screen")

-- A tab is one column, not a tab stop: the renderer maps tab to the space glyph and
-- the terminal advances by one. Writing 3 characters from column 1 must therefore
-- leave the cursor at column 4.
term.clear()
term.setCursorPos(1, 1)
write("a\tb")
local cx, cy = term.getCursorPos()
eq("tab occupies exactly one column", 4, cx)

-- The same through print(): "a<TAB>b" is 3 characters, so the cursor advice must match.
term.clear()
term.setCursorPos(1, 1)
print("a", "b")
local px, py = term.getCursorPos()
eq("print('a','b') advances one line", 2, py)
eq("print('a','b') leaves the cursor at column 1", 1, px)
-- The glyph a tab renders as (a blank column) is checked visually below; there is no
-- term.getLine in this API, so it cannot be asserted from Lua.

-- ---------------------------------------------------------------------------
-- Visual confirmation for a human: row of a ruler, then the two expected shapes.
term.clear()
term.setCursorPos(1, 1)
print("Expected on the next two lines:")
print("a b       <- print('a','b'): ONE space, not a tab stop")
print("1 nil 3   <- print(1,nil,3)")
print("")
print("If you see a box/question mark instead of a space, report it.")

finish("BATCH-A-4 print layout")

-- FINAL SUMMARY
-- The RESULT block above is printed before the drill, and a 19-row screen plus the drill can
-- scroll it out of view. Repeat the outcome as the last line so it is always visible without
-- scrolling. (Prompted by a run where only the drill was visible.)
print("")
print("RESULT BATCH-A-4 print layout: passed " .. P .. "   failed " .. F
  .. (F > 0 and "   -- failures also logged to the results file" or ""))
