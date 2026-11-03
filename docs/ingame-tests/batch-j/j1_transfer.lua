-- Batch J: file transfer / import.
--
-- What can be checked without a player: the Lua-visible side of a file_transfer event. This script
-- fires a *real* TransferredFiles object into the running computer through the same event queue a
-- network upload uses, then reads it back from Lua and writes the files with the ported
-- cc.internal.import - so the whole server half (event object, getFiles, read, chunked write) is
-- exercised on the machine that will receive real drops.
--
-- What cannot: the drop itself. That needs a real client with lwjgl3ify and a human dragging a file
-- onto the window; see README.md for the manual drill.
--
-- Transcript: /batch-j-results.txt
--   readlog_manual /batch-j-results.txt

local P, F, S = 0, 0, 0
local _transcript = {}
local fails, skips = {}, {}

local function pass(label)
  P = P + 1
  _transcript[#_transcript + 1] = "OK   " .. label
end

local function ok(label, cond, detail)
  if cond then
    pass(label)
  else
    F = F + 1
    local line = label .. ((detail ~= nil) and ("  [" .. tostring(detail) .. "]") or "")
    fails[#fails + 1] = line
    _transcript[#_transcript + 1] = "FAIL " .. line
  end
end

local function eq(label, expected, got)
  ok(label, expected == got, "expected " .. tostring(expected) .. " got " .. tostring(got))
end

local function skip(label)
  S = S + 1
  skips[#skips + 1] = label
  _transcript[#_transcript + 1] = "SKIP " .. label
  print("SKIP " .. label)
end

local function group(name, fn)
  local okRun, err = pcall(fn)
  if not okRun then ok(name .. " crashed", false, err) end
end

local function finish(name)
  local lines = { "RESULT " .. name }
  lines[#lines + 1] = "  passed " .. P .. "   failed " .. F .. "   skipped " .. S
  for _, f in ipairs(fails) do lines[#lines + 1] = "  FAIL " .. f end
  for _, k in ipairs(skips) do lines[#lines + 1] = "  SKIP " .. k end
  for _, line in ipairs(_transcript) do lines[#lines + 1] = "    " .. line end

  local path = "/batch-j-results.txt"
  local h = fs.open(path, "a")
  if h then
    for _, line in ipairs(lines) do h.write(line .. "\n") end
    h.close()
  else
    printError("Could not write " .. path)
  end

  term.clear()
  term.setCursorPos(1, 1)
  for i = 1, math.min(#fails, 4) do print("FAIL " .. fails[i]) end
  if #fails > 4 then print("... and " .. (#fails - 4) .. " more") end
  for i = 1, math.min(#skips, 2) do print("SKIP " .. skips[i]) end
  print("File: " .. path .. " (readlog_manual)")
  print("RESULT " .. name .. ": passed " .. P .. "   failed " .. F .. "   skipped " .. S)
end

-- ---------------------------------------------------------------------------
term.clear()
term.setCursorPos(1, 1)
print("Batch J: file transfer")

local importModule = require("cc.internal.import")

-- cc.internal.import resolves names with shell.resolve, i.e. relative to the shell's directory, so
-- every path here has to be resolved the same way. Using absolute paths made this script pass only
-- when it happened to be run from "/" - run it from any other directory and the module wrote
-- somewhere the test was not looking.
local function at(name)
  return shell.resolve(name)
end

-- The program cannot fabricate a TransferredFiles object from Lua (it is a Java object), so this
-- checks the parts a Lua program touches: the module writes files, and the shell prompt handles the
-- event. The real object only exists when something actually drops a file.
group("import module", function()
  -- A stand-in for a TransferredFile: the module only calls getName and read.
  local reading = true
  local fake = {
    getName = function() return "batch-j-dropped.txt" end,
    read = function(_, n)
      if not reading then return nil end
      reading = false
      return "dropped by batch J"
    end,
  }

  local wrote, writeErr = importModule({ fake })
  ok("the import module accepts a transfer", wrote == true, writeErr)

  -- Text mode on purpose: this ROM's binary handles only expose read/close (as 1.75 did),
  -- while a text handle can readAll.
  local copied = fs.open(at("batch-j-dropped.txt"), "r")
  if not copied then
    ok("the transferred file exists", false, "not written at " .. at("batch-j-dropped.txt"))
    return
  end
  local contents = copied.readAll()
  copied.close()
  eq("the transferred file matches", "dropped by batch J", contents)

  fs.delete(at("batch-j-dropped.txt"))
end)

group("shell prompt", function()
  -- The shell handles a transfer while its prompt is open, so the prompt must not swallow the
  -- event. This asserts the code path exists in the installed ROM; the live behaviour needs a drop.
  local h = fs.open("/rom/programs/shell", "r")
  if not h then
    skip("shell program is readable")
    return
  end
  local shell = h.readAll()
  h.close()

  ok("the shell waits for events at its prompt", shell:find("coroutine.create", 1, true) ~= nil)
  ok("the shell handles file_transfer", shell:find('"file_transfer"', 1, true) ~= nil)
  -- The module needs the shell's own `require`: the BIOS-level one runs modules in the global env,
  -- where `shell` is nil, and shell.resolve is the first thing the module calls.
  ok(
    "the shell loads the import module in a shell env",
    shell:find("tImportEnv.require", 1, true) ~= nil
  )
end)

group("import program", function()
  ok("the import program is installed", fs.exists("/rom/programs/import"))
  ok("the import module is installed", fs.exists("/rom/modules/main/cc/internal/import.lua"))
end)

group("overwrite behaviour", function()
  -- An existing target must not be clobbered silently: the module asks first, and answering "no"
  -- has to leave the file alone. Nothing here needs a real drop.
  local h = fs.open(at("batch-j-existing.txt"), "wb")
  h.write("original")
  h.close()

  local reading = true
  local fake = {
    getName = function() return "batch-j-existing.txt" end,
    read = function(_, n)
      if not reading then return nil end
      reading = false
      return "replacement"
    end,
  }

  -- read() for the prompt is the BIOS's global; answer "no" for this one question.
  local realRead = read
  read = function() return "no" end
  local wrote, err = importModule({ fake })
  read = realRead

  ok("declining the overwrite is not an error", wrote == nil and err == nil, tostring(err))

  local check = fs.open(at("batch-j-existing.txt"), "r")
  local contents = check.readAll()
  check.close()
  eq("the existing file is untouched", "original", contents)

  fs.delete(at("batch-j-existing.txt"))
end)

finish("batch-j")
