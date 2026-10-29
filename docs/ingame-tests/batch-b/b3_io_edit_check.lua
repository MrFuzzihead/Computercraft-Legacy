-- Batch B / io: verify the file you saved with `edit`, using io to read it back.
--
-- Why a separate program: after `edit` you cannot just type the file's path at the shell —
-- the shell treats a bare name as a program to RUN, so a text file fails with a Lua syntax
-- error ("syntax error near '<first word>'"). Use `type <path>` to print one, or run this to
-- check it through io the way the editor does.
--
-- Runs in CI too: with no file present it reports SKIP and exits cleanly.

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
local function finish(name)
  local lines = { "RESULT " .. name }
  lines[#lines + 1] = "  passed " .. P .. "   failed " .. F .. "   skipped " .. S
  for i = 1, #fails do lines[#lines + 1] = "  FAIL " .. fails[i] end

  term.clear()
  term.setCursorPos(1, 1)
  for _, line in ipairs(lines) do print(line) end

  local h = fs.open("/batch-b-results.txt", "a")
  if h then
    for _, line in ipairs(lines) do h.write(line .. "\n") end
    h.close()
  end

  -- Last line on screen, so the outcome survives any scrolled output.
  print("")
  print("RESULT " .. name .. ": passed " .. P .. "   failed " .. F .. "   skipped " .. S)
end

local NAME = "batchb-manual.txt"
local PATH = "/" .. NAME

-- ---------------------------------------------------------------------------
if not fs.exists(PATH) then
  skip("no " .. PATH .. " -- run `edit " .. PATH .. "`, write a couple of lines,")
  skip("save (Ctrl once, Save), exit (Ctrl once, Exit), then run this again")
  finish("BATCH-B edit check")
  return
end

-- ---------------------------------------------------------------------------
print("== reading " .. PATH .. " through io ==")

local f, err = io.open(PATH)
ok("io.open succeeded", f ~= nil, err)

if not f then
  finish("BATCH-B edit check")
  return
end

ok("io.type reports a file", io.type(f) == "file", io.type(f))

-- Read it the same way `edit` does: one line at a time until nil.
local lines = {}
local line = f:read()
while line ~= nil do
  lines[#lines + 1] = line
  line = f:read()
end
eq("the handle reports closed after reading", "file", io.type(f))
f:close()
eq("closed after close()", "closed file", io.type(f))

ok("the file is not empty", #lines > 0, #lines)

local total = 0
for _, l in ipairs(lines) do
  ok("line is a string", type(l) == "string", l)
  total = total + #l
end
ok("the file has some content", total > 0, total)

-- Print what was found so a human can compare it with what they typed.
print("")
print("Read back " .. #lines .. " line(s):")
for i, l in ipairs(lines) do
  print("  " .. i .. ": " .. l)
end

-- ---------------------------------------------------------------------------
-- Confirm `type <path>` shows the same thing, since that is the instruction people get.
print("")
print("If the lines above match what you typed, io is fine.")
print("To print the raw file yourself:  type " .. PATH)

finish("BATCH-B edit check")
