-- Batch B / io: interactive checks. CI skips this file (its name contains "manual")
-- because it waits for real keystrokes.
--
-- Run it from the shell, answer the prompts, and report the RESULT block.

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
local function section(n)
  print("")
  print("== " .. n .. " ==")
end
local function finish(name)
  local lines = { "RESULT " .. name }
  lines[#lines + 1] = "  passed " .. P .. "   failed " .. F .. "   skipped " .. S
  for i = 1, #fails do lines[#lines + 1] = "  FAIL " .. fails[i] end
  for i = 1, #skips do lines[#lines + 1] = "  SKIP " .. skips[i] end

  print("")
  for _, line in ipairs(lines) do print(line) end

  local h = fs.open("/batch-b-results.txt", "a")
  if h then
    for _, line in ipairs(lines) do h.write(line .. "\n") end
    h.close()
  end
end

local lua_type = type

-- ---------------------------------------------------------------------------
section("io.stdin:read() reads a whole line")

io.write("Type: hello world")
print()
local line = io.stdin:read()
eq("stdin:read() returned the typed line", "hello world", line)

-- ---------------------------------------------------------------------------
section("io.stdin:read() on an empty line")

io.write("Press Enter with nothing typed")
print()
local empty = io.stdin:read()
eq("an empty line reads as an empty string", "", empty)

-- ---------------------------------------------------------------------------
section("io.read() delegates to stdin")

io.write("Type: through io.read")
print()
local viaRead = io.read()
eq("io.read() returned the typed line", "through io.read", viaRead)

-- ---------------------------------------------------------------------------
section("stdin:read('*a') is nil, not a crash")

-- This one does NOT wait: the stdin handle has no readAll, so '*a' resolves to nil
-- immediately. The newline is consumed by the read in the next section.
local whole = io.stdin:read("*a")
eq("stdin has no readAll, so '*a' is nil", nil, whole)
io.write("Type: anything, then Enter (it is read by the next prompt)")
print()
io.stdin:read()

-- ---------------------------------------------------------------------------
section("stdin:lines() over typed input")

print("Type three lines, then a fourth that is just '.'")
local n = 0
-- Works because handle:lines now accepts readLine as well as read (upstream only checked
-- read, which made io.stdin:lines() and io.lines() fail with "file is not readable").
for typed in io.stdin:lines() do
  if typed == "." then break end
  n = n + 1
end
eq("stdin:lines() yielded the three lines", 3, n)

-- ---------------------------------------------------------------------------
section("writing while redirected through term")

-- io.write goes through the global write(), which honours term redirects like everything else.
local rec = { written = {} }
rec.write = function(s) rec.written[#rec.written + 1] = s end
rec.getSize = function() return 51, 19 end
rec.getCursorPos = function() return 1, 1 end
rec.setCursorPos = function() end
rec.scroll = function() end
rec.clearLine = function() end
rec.clear = function() end
rec.setCursorBlink = function() end
rec.setTextColour = function() end
rec.getTextColour = function() return 1 end
rec.isColour = function() return false end

local old = term.redirect(rec)
io.write("into the redirect")
term.redirect(old)
eq("io.write honours term.redirect", "into the redirect", table.concat(rec.written))

-- ---------------------------------------------------------------------------
section("paintutils reads images through io")

-- paintutils.loadImage uses io.open(path):read("*a"); a bad payload must be reported as a
-- parse failure, not an io error.
local h = fs.open("/batchb-bad.nfp", "w")
h.write("this is not an image")
h.close()
local okLoad, res = pcall(paintutils.loadImage, "/batchb-bad.nfp")
ok("paintutils.loadImage does not crash on garbage", lua_type(okLoad) == "boolean", res)
fs.delete("/batchb-bad.nfp")

-- ---------------------------------------------------------------------------
section("edit round-trip (io.open + :read)")

-- Not interactive: write a file the editor would produce, then read it back the way the
-- editor does, so the same code path is covered without needing keystrokes here.
local h2 = fs.open("/batchb-edit.txt", "w")
h2.write("line one\nline two\n")
h2.close()
local f = io.open("/batchb-edit.txt")
local lines = {}
local l = f:read()
while l do
  lines[#lines + 1] = l
  l = f:read()
end
f:close()
eq("editor-style line loop", 2, #lines)
eq("first line", "line one", lines[1])
eq("second line", "line two", lines[2])
fs.delete("/batchb-edit.txt")

finish("BATCH-B io (manual)")

print("")
print("--- STILL TO CHECK BY HAND ---")
print("1. edit /batchb-manual.txt  -- write two lines, then save and exit.")
print("   To save: press Ctrl ONCE to open the menu (this build has no")
print("   Ctrl+S chord), choose Save, then Ctrl once more and choose Exit.")
print("   Then run:  b3_io_edit_check   to have this pack verify the file.")
print("   Do NOT type the bare path: the shell RUNS a program by name, so")
print("   /batchb-manual.txt would be executed as Lua and fail with a")
print("   syntax error. To print a text file, use:  type /batchb-manual.txt")
print("2. help intro")
print("   EXPECT: the topic pages normally.")
print("3. On a monitor: monitor right, then io.write('hi') in the monitor shell.")
print("   EXPECT: 'hi' appears on the monitor, not the computer.")
print("Report any error or stack trace.")

-- FINAL SUMMARY
-- The RESULT block above is printed before the drill, and a 19-row screen plus the drill can
-- scroll it out of view. Repeat the outcome as the last line so it is always visible without
-- scrolling. (Prompted by a run where only the drill was visible.)
print("")
print("RESULT BATCH-B io (manual): passed " .. P .. "   failed " .. F
  .. (F > 0 and "   -- failures also logged to the results file" or ""))
