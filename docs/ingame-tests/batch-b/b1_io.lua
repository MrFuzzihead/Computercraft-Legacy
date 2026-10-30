-- Batch B / io: the parts that can run unattended on a real computer.
--
-- Everything here also runs in CI through IngameTestScriptsTest, so a broken assertion fails
-- the build. The interactive parts (typing into io.stdin, the edit/help smoke tests) are in
-- b2_io_manual.lua, which CI skips.

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
-- Prints the summary, and also appends it to /batch-b-results.txt (the manual drills can
-- scroll the on-screen copy away). Review with:  type /batch-b-results.txt
local function finish(name)
  _batchResultName = name
  local lines = { "RESULT " .. name }
  lines[#lines + 1] = "  passed " .. P .. "   failed " .. F .. "   skipped " .. S
  for i = 1, #fails do lines[#lines + 1] = "  FAIL " .. fails[i] end
  for i = 1, #skips do lines[#lines + 1] = "  SKIP " .. skips[i] end

  term.clear()
  term.setCursorPos(1, 1)
  for _, line in ipairs(lines) do print(line) end

  local h = fs.open("/batch-b-results.txt", "a")
  if h then
    for _, line in ipairs(lines) do h.write(line .. "\n") end
    h.close()
  end
end

-- `type` is shadowed by io.type once the io API loads, so keep the real one.
local lua_type = type

local function mkfile(path, contents)
  local h = fs.open(path, "w")
  h.write(contents)
  h.close()
end

-- ---------------------------------------------------------------------------
section("API surface")

for _, name in ipairs({
  "open", "close", "flush", "input", "lines", "output", "read", "type", "write",
}) do
  eq("io." .. name .. " exists", "function", lua_type(io[name]))
end
eq("io.stdin", "file", io.type(io.stdin))
eq("io.stdout", "file", io.type(io.stdout))
eq("io.stderr", "file", io.type(io.stderr))

-- ---------------------------------------------------------------------------
section("reading real ROM files")

do
  local f, err = io.open("/rom/help/intro")
  ok("opened /rom/help/intro", f ~= nil, err)
  if f then
    local first = f:read()
    ok("read() returned a line", lua_type(first) == "string" and #first > 0, first)
    local rest = f:read("*a")
    ok("read('*a') returned a string", lua_type(rest) == "string", rest)
    f:close()
    eq("closed handle type", "closed file", io.type(f))
  end
end

do
  local f = io.open("/definitely-not-a-topic")
  ok("missing file returns nil", f == nil)
end

-- ---------------------------------------------------------------------------
section("formats")

mkfile("/batchb.txt", "one\ntwo\nrest of it")

do
  local f = io.open("/batchb.txt")
  eq("read('l')", "one", f:read("l"))
  eq("read('L') keeps the newline", "two\n", f:read("L"))
  eq("read('a')", "rest of it", f:read("a"))
  f:close()
end

do
  local f = io.open("/batchb.txt")
  local a, b = f:read(3, 2)
  eq("read(3)", "one", a)
  eq("read(2)", "\nt", b)
  f:close()
end

do
  local f = io.open("/batchb.txt")
  ok("read('*n') is nil (unsupported)", f:read("*n") == nil)
  f:close()
end

do
  local f = io.open("/batchb.txt")
  local okCall = pcall(f.read, f, "z")
  ok("invalid format raises", not okCall)
  f:close()
  ok("read on a closed file raises", not pcall(f.read, f))
end

-- ---------------------------------------------------------------------------
section("lines")

do
  local n = 0
  for line in io.lines("/batchb.txt") do
    ok("line is a string", lua_type(line) == "string", line)
    n = n + 1
  end
  eq("io.lines counted all lines", 3, n)
end

do
  local f = io.open("/batchb.txt")
  local out = {}
  for line in f:lines("L") do out[#out + 1] = line end
  eq("handle:lines('L') keeps newlines", "one\n|two\n|rest of it", table.concat(out, "|"))
  eq("handle:lines does not close", "file", io.type(f))
  f:close()
end

-- ---------------------------------------------------------------------------
section("writing and seeking")

do
  local f = io.open("/batchb7.txt", "w+")
  ok("w+ handle is a file", io.type(f) == "file", io.type(f))
  local w = f:write("abcdef")
  eq("write returns the handle", f, w)
  eq("seek('end') reports size", 6, f:seek("end"))
  f:seek("set", 1)
  eq("read after seek", "bcd", f:read(3))
  eq("seek('cur') is 4", 4, f:seek())
  f:close()
end

do
  local f = io.open("/batchb8.txt", "w")
  ok("writing to a text handle works", pcall(f.write, f, "x", 1))
  f:close()
  local g = io.open("/batchb8.txt")
  eq("numbers are written with Lua formatting", "x1", g:read("*a"))
  g:close()
end

-- Regression: binary handles used to accept only single byte numbers, silently dropping
-- strings, which broke io.output (it opens "wb").
do
  local f = io.open("/batchb9.txt", "wb")
  f:write("binary text")
  f:close()
  local g = io.open("/batchb9.txt")
  eq("string written through a wb handle", "binary text", g:read("*a"))
  g:close()
end

-- ---------------------------------------------------------------------------
section("io.write / io.output / io.input")

-- io.write must reach the terminal. The marker is also checked by the CI harness.
local h = io.write("BATCHB-IO-WRITE")
ok("io.write returns the output handle", h == io.stdout)
io.write("\n")

do
  local out = io.output("/batchb-redirect.txt")
  io.write("redirected")
  out:close()
  io.output(io.stdout)

  local f = io.open("/batchb-redirect.txt")
  local v = f and f:read("*a")
  if f then f:close() end
  eq("io.output redirected io.write", "redirected", v)
end

do
  -- Keep the handle so it can be closed, otherwise the fixture cannot be deleted below.
  local inf = io.input("/batchb.txt")
  eq("io.input redirected io.read", "one", io.read())
  inf:close()
  io.input(io.stdin)
end

eq("io.input() with no argument is a getter", io.stdin, io.input())
eq("io.output() with no argument is a getter", io.stdout, io.output())

-- ---------------------------------------------------------------------------
section("stderr")

do
  local before = term.getTextColour()
  -- Capture what stderr actually writes by pointing the current output at it? No: stderr has
  -- its own writer, so just check it does not error and restores the colour.
  local okWrite = pcall(io.stderr.write, io.stderr, "BATCHB-STDERR")
  ok("io.stderr:write works", okWrite)
  eq("io.stderr restores the text colour", before, term.getTextColour())
end

-- ---------------------------------------------------------------------------
section("error handling")

ok("io.close rejects a non-handle", not pcall(io.close, {}))
ok("io.input rejects a non-handle", not pcall(io.input, 42))
ok("io.output rejects a non-handle", not pcall(io.output, {}))
ok("io.open with no path raises", not pcall(io.open))
eq("io.type of a plain table is nil", nil, io.type({}))
eq("setvbuf is a no-op", true, pcall(io.stdout.setvbuf, io.stdout, "full", 1024))

-- ---------------------------------------------------------------------------
-- Clean up the fixtures so the computer is left tidy.
for _, path in ipairs({
  "/batchb.txt", "/batchb7.txt", "/batchb8.txt", "/batchb9.txt", "/batchb-redirect.txt",
}) do
  if fs.exists(path) then fs.delete(path) end
end

finish("BATCH-B io")

-- ---------------------------------------------------------------------------
-- Printed after finish() so clearing the screen cannot hide it.
print("")
print("--- MANUAL DRILL (interactive parts) ---")
print("1. Copy b2_io_manual.lua across and run it.")
print("2. Then run:  edit /batchb-edit.txt")
print("   Type a couple of lines. To save: press Ctrl ONCE to open the menu")
print("   (no Ctrl+S chord in this build), choose Save, then Ctrl once more")
print("   and choose Exit. Then: type /batchb-edit.txt  EXPECT: lines print.")
print("3. Run:  help intro")
print("   EXPECT: the intro topic pages normally (io.open + :read on /rom).")
print("4. With a disk drive and floppy:  copy /rom/help/intro /disk/intro")
print("   EXPECT: succeeds (io is not involved, but it is the same fs path).")
print("")
print("Report anything that errors or prints a stack trace.")

-- CraftOS terminals have no scrollback, so the verdict and any failures have to be the LAST
-- lines printed: everything above is pushed off the screen by the drill.
print("")
for i = 1, math.min(#fails, 8) do print("  FAIL " .. fails[i]) end
if #fails > 8 then print("  ... and " .. (#fails - 8) .. " more -- open the results file") end
for i = 1, math.min(#skips, 3) do print("  SKIP " .. skips[i]) end
-- The verdict is the very last line, so it is always visible.
print("RESULT " .. tostring(_batchResultName) .. ": passed " .. P .. "   failed " .. F .. "   skipped " .. S)
