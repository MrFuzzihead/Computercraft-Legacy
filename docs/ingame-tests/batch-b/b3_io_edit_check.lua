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
-- If it cannot find the file it reports SKIP (never FAIL, so it is safe in CI) along with what it
-- looked for and what it can actually see.
--
-- NOTE: unlike the other in-game scripts this one does not clear the screen before printing the
-- result. Its output is short, and the SKIP reason plus the directory listing ARE the result --
-- clearing wiped exactly the information needed to diagnose a skip.

local P, F, S = 0, 0, 0
local tArgs = { ... }

local fails, skips, notes = {}, {}, {}
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
local function skip(label)
  S = S + 1
  skips[#skips + 1] = label
  print("SKIP " .. label)
end
local function note(label)
  notes[#notes + 1] = label
  print("     " .. label)
end

local function finish(name)
  local lines = { "RESULT " .. name }
  lines[#lines + 1] = "  passed " .. P .. "   failed " .. F .. "   skipped " .. S
  for _, f in ipairs(fails) do lines[#lines + 1] = "  FAIL " .. f end
  for _, k in ipairs(skips) do lines[#lines + 1] = "  SKIP " .. k end
  for _, n in ipairs(notes) do lines[#lines + 1] = "  NOTE " .. n end

  print("")
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

-- Files in / or one directory down whose name looks like the one we asked for.
local function findCandidates()
  local found = {}
  local function add(path)
    for _, existing in ipairs(found) do
      if existing == path then return end
    end
    found[#found + 1] = path
  end

  local function consider(dir, depth)
    local names = fs.list(dir)
    table.sort(names)
    for _, name in ipairs(names) do
      local path = fs.combine(dir, name)
      if fs.isDir(path) then
        -- Two levels down covers /batchB/notes.txt and /a/b/whatever.txt.
        if depth < 2 then consider(path, depth + 1) end
      else
        local lower = name:lower()
        -- .lua files are programs, not notes. Skipping them matters: this pack's own
        -- b2_io_manual.lua used to be offered as a "candidate" and made the search
        -- ambiguous, which turned a findable file into a skip.
        if not lower:match("%.lua$") then
          if lower:find("batchb", 1, true) or lower:find("manual", 1, true) then add(path) end
        end
      end
    end
  end

  consider("", 0)
  return found
end

-- Compact listing, capped so a busy disk cannot flood the screen.
local function listing(dir)
  local names = fs.list(dir)
  if #names == 0 then return "(empty)" end
  table.sort(names)
  local out = {}
  for i = 1, math.min(#names, 8) do out[i] = names[i] end
  if #names > 8 then out[#out + 1] = "... " .. (#names - 8) .. " more" end
  return table.concat(out, "  ")
end

local function describeTree()
  note("current directory: /" .. (shell.dir() == "" and "" or shell.dir() .. "/") )
  note("what this computer can see (the read-only rom mount is skipped):")
  note("  /  " .. listing(""))
  for _, name in ipairs(fs.list("")) do
    local dir = fs.combine("", name)
    if fs.isDir(dir) and name ~= "rom" then
      note("  " .. dir .. "/  " .. listing(dir))
      for _, inner in ipairs(fs.list(dir)) do
        local sub = fs.combine(dir, inner)
        if fs.isDir(sub) then note("  " .. sub .. "/  " .. listing(sub)) end
      end
    end
  end
end

-- ---------------------------------------------------------------------------
-- Work out which file to check.

-- An explicit path wins, whether or not it exists (so a typo is reported precisely).
local target
if tArgs[1] ~= nil then
  target = shell.resolve(tArgs[1])
  if not fs.exists(target) then
    skip("no such file: " .. target)
    note("you passed " .. tArgs[1] .. ", which resolves to " .. target)
    target = nil
  elseif fs.isDir(target) then
    skip("that is a directory, not a file: " .. target)
    target = nil
  end
elseif isFile(shell.resolve(NAME)) then
  -- Running from inside /batchB (or wherever you saved it) finds it directly.
  target = shell.resolve(NAME)
elseif isFile(DEFAULT_PATH) then
  target = DEFAULT_PATH
else
  local candidates = findCandidates()
  if #candidates == 1 then
    target = candidates[1]
    note("did not find " .. DEFAULT_PATH .. "; using " .. target .. " instead")
  elseif #candidates > 1 then
    skip("several files could be the one you meant")
    for _, path in ipairs(candidates) do note("candidate: " .. path) end
    note("re-run with one of them as an argument, e.g. b3_io_edit_check " .. candidates[1])
  else
    skip("no " .. DEFAULT_PATH .. " and nothing similar in / or two levels down")
  end
end

if target == nil then
  print("")
  describeTree()
  print("")
  print("To create it:  edit /" .. NAME .. "   (then Ctrl once -> Save, Ctrl -> Exit)")
  print("Or point this at a file you already saved:  b3_io_edit_check <path>")
  finish("BATCH-B edit check")
  return
end

-- ---------------------------------------------------------------------------
print("== reading " .. target .. " through io ==")

local f, err = io.open(target)
ok("io.open succeeded", f ~= nil, err)

if not f then
  print("")
  describeTree()
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
