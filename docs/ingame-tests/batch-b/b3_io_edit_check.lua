-- Batch B / io: verify a file you saved with `edit`, reading it back through io.
--
-- Usage:
--   b3_io_edit_check                      -- looks for /batchb-manual.txt
--   b3_io_edit_check /batchB/notes.txt    -- or point it at whatever you actually saved
--
-- Why a separate program: you cannot just type a file's path at the shell. The shell treats a
-- bare name as a program to RUN, so a text file is parsed as Lua and fails with
-- "syntax error near ...". Use `type <path>` to print one, or run this to read it via io.
--
-- If it cannot find the file it says exactly what it looked for and what it can see, and it
-- reports SKIP rather than failing, so this also runs safely in CI where the file never exists.

local P, F, S = 0, 0, 0
local tArgs = { ... }
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
local function note(label) print("     " .. label) end
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
local DEFAULT_PATH = "/" .. NAME

local function isFile(path)
  return fs.exists(path) and not fs.isDir(path)
end

-- Any file in the root or one directory down whose name looks like the one we asked for.
local function findCandidates()
  local found = {}
  local function consider(dir)
    local names = fs.list(dir)
    for _, name in ipairs(names) do
      local path = fs.combine(dir, name)
      if fs.isDir(path) then
        if dir == "" then consider(path) end -- one level deep only
      else
        local lower = name:lower()
        if lower:find("manual", 1, true) or lower:find("batchb", 1, true) then
          found[#found + 1] = path
        end
      end
    end
  end
  consider("")
  return found
end

local function listing(dir)
  local names = fs.list(dir)
  if #names == 0 then return "(empty)" end
  table.sort(names)
  return table.concat(names, "  ")
end

-- ---------------------------------------------------------------------------
-- Work out which file to check.

local target = nil

if tArgs[1] then
  -- Explicit path wins.
  target = shell.resolve(tArgs[1])
  if not fs.exists(target) then
    skip("no such file: " .. target)
    note("You typed: " .. tArgs[1] .. "  (resolved to " .. target .. ")")
    target = nil
  elseif fs.isDir(target) then
    skip("that is a directory, not a file: " .. target)
    target = nil
  end
elseif isFile(DEFAULT_PATH) then
  target = DEFAULT_PATH
else
  local candidates = findCandidates()
  if #candidates == 1 then
    target = candidates[1]
    note("did not find " .. DEFAULT_PATH .. ", using " .. target .. " instead")
  elseif #candidates > 1 then
    skip("several files could be the one you meant; pass one as an argument")
    for _, path in ipairs(candidates) do note("candidate: " .. path) end
  else
    skip("no " .. DEFAULT_PATH .. " and nothing similar in / or one directory down")
  end
end

if target == nil then
  print("")
  print("What this program can see:")
  print("  /  " .. listing(""))
  for _, name in ipairs(fs.list("")) do
    local dir = fs.combine("", name)
    if fs.isDir(dir) then print("  " .. dir .. "/  " .. listing(dir)) end
  end
  print("")
  print("Run: edit /" .. NAME)
  print("Then: Ctrl once -> Save, Ctrl once -> Exit, then run this again.")
  print("Or point it at what you saved, e.g.  b3_io_edit_check /notes.txt")
  finish("BATCH-B edit check")
  return
end

-- ---------------------------------------------------------------------------
print("== reading " .. target .. " through io ==")

local f, err = io.open(target)
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
f:close()
eq("closed after close()", "closed file", io.type(f))

ok("the file has at least one line", #lines > 0, #lines)

local total = 0
for _, l in ipairs(lines) do
  ok("line is a string", type(l) == "string", l)
  total = total + #l
end
ok("the file has some content", total > 0, total)

-- Echo what was found so a human can compare it with what they typed.
print("")
print("Read back " .. #lines .. " line(s):")
for i, l in ipairs(lines) do print("  " .. i .. ": " .. l) end

print("")
print("If those match what you typed, io is fine.")
print("To print the raw file yourself:  type " .. target)

finish("BATCH-B edit check")
