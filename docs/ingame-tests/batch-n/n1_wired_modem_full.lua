-- Batch N: the full wired modem.
--
-- The automated part runs on any computer with a wired modem (wall or full) attached and checks
-- the shared wired-modem surface: peripheral_hub type, remote names/methods, getNameLocal and
-- isWireless. The six-face hub behaviour needs blocks placed in the world, so those checks are
-- staged at the end.
--
-- Transcript: /batch-n-results.txt
--   readlog_manual /batch-n-results.txt

local P, F, S = 0, 0, 0
local _transcript = {}
local fails, skips, staged = {}, {}, {}

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
end

local function group(name, fn)
  local okRun, err = pcall(fn)
  if not okRun then ok(name .. " crashed", false, err) end
end

local function stage(text)
  staged[#staged + 1] = text
  _transcript[#_transcript + 1] = "STAGED " .. text
end

local function finish(name)
  local lines = { "RESULT " .. name }
  lines[#lines + 1] = "  passed " .. P .. "   failed " .. F .. "   skipped " .. S
  for _, f in ipairs(fails) do lines[#lines + 1] = "  FAIL " .. f end
  for _, k in ipairs(skips) do lines[#lines + 1] = "  SKIP " .. k end
  for _, line in ipairs(_transcript) do lines[#lines + 1] = "    " .. line end

  local path = "/batch-n-results.txt"
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
  print("Automated: " .. P .. " ok, " .. F .. " failed")
  print("Staged for you: " .. #staged)
  print("File: " .. path .. " (readlog_manual)")
  print("RESULT " .. name .. ": passed " .. P .. "   failed " .. F .. "   skipped " .. S)
end

-- ---------------------------------------------------------------------------
term.clear()
term.setCursorPos(1, 1)

local function findWiredModem()
  for _, side in ipairs(peripheral.getNames()) do
    local methods = peripheral.getMethods(side) or {}
    for _, method in ipairs(methods) do
      if method == "getNamesRemote" then return side end
    end
  end
  return nil
end

local wired = findWiredModem()
if not wired then
  skip("no wired modem attached; place a full or wall wired modem next to this computer and re-run")
else
  group("wired modem surface", function()
    ok("wired modem reports peripheral_hub", peripheral.hasType(wired, "peripheral_hub"))
    eq("wired modem is not wireless", false, peripheral.call(wired, "isWireless"))

    local names = peripheral.call(wired, "getNamesRemote")
    ok("getNamesRemote returns a table", type(names) == "table", type(names))

    local localName = peripheral.call(wired, "getNameLocal")
    ok("getNameLocal returns a string or nil", localName == nil or type(localName) == "string", type(localName))
    if localName == nil then
      skip("getNameLocal is nil; right-click the modem to attach a peripheral and re-run for the full check")
    end
  end)
end

-- ---------------------------------------------------------------------------
-- Staged manual checks. These need blocks in the world, so they cannot be automated from a
-- computer alone.
stage("Craft a Wired Modem, then craft it shapelessly into a Full Wired Modem and back. Both items are named 'Wired Modem'.")
stage("Place the Full Wired Modem. All six faces should use the wired_modem_face textures; it should be a solid full cube.")
stage("Place chests/inventories on several faces. Right-click the full modem once. Chat should list every attached name, sorted, in one connected message per kind.")
stage("The block should switch to the 'peripheral' texture. Right-click again; it should return to the plain texture and list the same names as disconnected.")
stage("Attach a computer to one face. peripheral.getType(side) should be modem + peripheral_hub; getNameLocal() should return that face's name.")
stage("A second computer on the wired network should see all six attached names through getNamesRemote, and should not see its own local name.")
stage("Open a channel on one face and check isOpen on another face of the same full modem. They share one channel state, so both should report open.")
stage("Remove one adjacent chest. The full modem should refresh: that name disappears from the network and the peripheral texture turns off if nothing is left.")
stage("Break the full modem. It should drop a Full Wired Modem, the cable network should reconnect, and no stale peripheral names should remain.")
stage("Save and reload the world. The full modem should reattach its peripherals with the same type_id names and the same enabled state.")
stage("Check that a cable next to the full modem renders its arm into the modem's face, and that the full modem can be placed on any side of a cable.")

finish("batch-n full wired modem")
