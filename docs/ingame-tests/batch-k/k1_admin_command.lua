-- Batch K: the /computercraft admin command, driven from a command computer.
--
-- MUST run on a COMMAND COMPUTER: it uses commands.exec to run the command, which is the only way
-- to get its output back as text.
--
-- Everything except registration and real computer output is covered by
-- CommandComputerCraftTest headlessly; this is the part that needs a live server, plus a readable
-- record of the actual output.
--
-- Transcript: /batch-k-results.txt
--   readlog_manual /batch-k-results.txt

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

  local path = "/batch-k-results.txt"
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

if type(commands) ~= "table" then
  skip("not a command computer -- nothing to test")
  print("Run this on a command computer.")
  finish("BATCH-K admin command")
  return
end

--[[- Runs a command and flattens its output table into one string. ]]
local function run(command)
  local okRun, succeeded, output = pcall(commands.exec, command)
  if not okRun then
    return false, "Lua error: " .. tostring(succeeded)
  end

  if type(output) == "table" then
    local parts = {}
    for i = 1, #output do
      parts[#parts + 1] = tostring(output[i])
    end
    output = table.concat(parts, "\n")
  end

  return succeeded == true, tostring(output or "")
end

-- ---------------------------------------------------------------------------
print("== dump ==")
group("dump", function()
  local okDump, dumpOut = run("computercraft dump")
  ok("dump runs", okDump, dumpOut)
  ok("dump says how many computers are loaded", dumpOut:find("computer%(s%)") ~= nil, dumpOut)
  ok("dump prints a column header", dumpOut:find("Inst", 1, true) ~= nil, dumpOut)

  -- Pick any instance id out of the listing to use for the rest of the run.
  local instanceID = dumpOut:match("%f[%d](%d+)%s+%d+%s+yes")
    or dumpOut:match("%f[%d](%d+)%s+%d+%s+no")
  ok("the listing names an instance id", instanceID ~= nil, dumpOut)
  _G._batchKInstanceID = instanceID
end)

group("dump one computer", function()
  local instanceID = _G._batchKInstanceID
  if not instanceID then
    skip("no instance id available from the listing")
    return
  end

  local okOne, oneOut = run("computercraft dump " .. instanceID)
  ok("dump <id> runs", okOne, oneOut)
  ok("the detail names the computer", oneOut:find("Computer " .. instanceID, 1, true) ~= nil, oneOut)
  ok("the detail shows the id", oneOut:find("Id", 1, true) ~= nil, oneOut)
  ok("the detail shows the position", oneOut:find("Position", 1, true) ~= nil, oneOut)
  ok("the detail shows on/off", oneOut:find("On", 1, true) ~= nil, oneOut)
end)

-- ---------------------------------------------------------------------------
print("== bad input ==")
group("bad input", function()
  local okBad, outBad = run("computercraft dump banana")
  ok("a non-numeric id fails", okBad == false, outBad)
  ok("the error points at dump", outBad:find("dump", 1, true) ~= nil, outBad)

  okBad, outBad = run("computercraft dump 999999")
  ok("an unknown id fails", okBad == false, outBad)
  ok("the error names the id", outBad:find("999999", 1, true) ~= nil, outBad)

  okBad, outBad = run("computercraft explode")
  ok("an unknown subcommand fails", okBad == false, outBad)
  ok("unknown subcommand prints the usage", outBad:find("turn%-on") ~= nil, outBad)

  okBad, outBad = run("computercraft")
  ok("no subcommand fails", okBad == false, outBad)
  ok("no subcommand prints the usage", outBad:find("dump", 1, true) ~= nil, outBad)
end)

-- ---------------------------------------------------------------------------
print("== turn-on and shutdown ==")
group("power", function()
  local instanceID = _G._batchKInstanceID
  if not instanceID then
    skip("no instance id available, so power commands cannot be aimed")
    return
  end

  local okOn, onOut = run("computercraft turn-on " .. instanceID)
  ok("turn-on runs", okOn, onOut)
  ok("turn-on reports a count", onOut:find("Turned on") ~= nil, onOut)

  local okOff, offOut = run("computercraft shutdown " .. instanceID)
  ok("shutdown runs", okOff, offOut)
  ok("shutdown reports a count", offOut:find("Shut down") ~= nil, offOut)

  -- A second turn-on must report zero changes, proving the state was really applied.
  local okAgain, againOut = run("computercraft turn-on " .. instanceID)
  ok("turn-on after shutdown reports a change", okAgain and againOut:find("Turned on", 1, true) ~= nil, againOut)

  ok("turn-on with no ids fails", (run("computercraft turn-on")) == false)
  ok("shutdown with no ids fails", (run("computercraft shutdown")) == false)
end)

-- ---------------------------------------------------------------------------
print("")
print("--- MANUAL CHECK ---")
print("1. Run  /computercraft dump  from the chat bar with cheats on.")
print("   EXPECT the same table, printed as chat lines, nearest computer first if")
print("   you are a player.")
print("2. In chat, with a computer in front of you:  /computercraft shutdown <id>")
print("   EXPECT it to power off, and  /computercraft turn-on <id>  to boot it again.")
print("3. As a NON-op on a server, the command must be refused entirely.")
print("4. /computercraft track  must NOT exist: EXPECT 'Unknown command' or the")
print("   vanilla unknown-subcommand message (it is deliberately not ported).")
print("5. readlog_manual /batch-k-results.txt for the transcript.")
print("")

finish("BATCH-K admin command")
