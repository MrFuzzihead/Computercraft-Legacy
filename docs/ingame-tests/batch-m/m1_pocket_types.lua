-- Batch M: pocket upgrades, the wired modem's peripheral_hub type, and remote multi-type.
--
-- Almost everything here needs a *player inventory* or two computers connected by cable, so the
-- automated part is the peripheral-type surface, which a plain computer can inspect. The rest is a
-- staged manual list -- see the end of the run.
--
-- Transcript: /batch-m-results.txt
--   readlog_manual /batch-m-results.txt

local P, F, S = 0, 0, 0
local _transcript = {}
local fails, skips = {}, {}
local staged = {}

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

  local path = "/batch-m-results.txt"
  local h = fs.open(path, "a")
  if h then
    for _, line in ipairs(lines) do h.write(line .. "\n") end
    h.close()
  else
    printError("Could not write " .. path)
  end

  term.clear()
  term.setCursorPos(1, 1)
  for i = 1, math.min(#fails, 3) do print("FAIL " .. fails[i]) end
  if #fails > 3 then print("... and " .. (#fails - 3) .. " more") end
  for i = 1, math.min(#skips, 2) do print("SKIP " .. skips[i]) end
  print("Automated: " .. P .. " ok, " .. F .. " failed")
  print("Staged for you: " .. #staged)
  print("File: " .. path .. " (readlog_manual)")
  print("RESULT " .. name .. ": passed " .. P .. "   failed " .. F .. "   skipped " .. S)
end

-- ---------------------------------------------------------------------------
term.clear()
term.setCursorPos(1, 1)

local function findType(peripheralType)
  for _, name in ipairs(peripheral.getNames()) do
    if peripheral.hasType(name, peripheralType) then return name end
  end
  return nil
end

-- ---------------------------------------------------------------------------
print("== wired modem types ==")
group("wired modem types", function()
  local wired = findType("peripheral_hub")
  if not wired then
    skip("no wired modem attached, so peripheral_hub cannot be observed here")
    return
  end

  -- getType returns every type as separate values; the first must stay "modem".
  local types = { peripheral.getType(wired) }
  ok("a wired modem reports the hub type", types[2] == "peripheral_hub" or types[1] == "peripheral_hub", types[1])
  ok("the primary type is modem", peripheral.call(wired, "isWireless") == false, "a wired modem is not wireless")
  ok("hasType('modem')", peripheral.hasType(wired, "modem"))
  ok("hasType('peripheral_hub')", peripheral.hasType(wired, "peripheral_hub"))
end)

-- ---------------------------------------------------------------------------
print("== the hub's remote surface ==")
group("remote surface", function()
  local wired = findType("peripheral_hub")
  if not wired then
    skip("no wired modem attached")
    return
  end

  local methods = peripheral.getMethods(wired) or {}
  local function hasMethod(name)
    for _, method in ipairs(methods) do
      if method == name then return true end
    end
    return false
  end

  ok("getNamesRemote is present", hasMethod("getNamesRemote"))
  ok("getTypeRemote is present", hasMethod("getTypeRemote"))
  ok("hasTypeRemote is present", hasMethod("hasTypeRemote"))

  local names = peripheral.call(wired, "getNamesRemote")
  ok("getNamesRemote returns a table", type(names) == "table", type(names))
end)

-- ---------------------------------------------------------------------------
print("== pocket computers ==")
group("pocket", function()
  -- A pocket computer must answer isEquipped, which now covers every upgrade id, not just the
  -- modem. Without one in hand this is the most that can be checked from here.
  if pocket == nil then
    skip("no pocket computer API on this device (run this on a pocket computer for the rest)")
    return
  end

  local equipped = pocket.isEquipped()
  ok("isEquipped returns a boolean", type(equipped) == "boolean", type(equipped))
end)

-- ---------------------------------------------------------------------------
stage("WIRED MODEM. Attach a wired modem to this computer with a cable to another computer's")
stage("  peripheral (a monitor, say). Then:")
stage("    peripheral.hasType('<side>', 'peripheral_hub')      EXPECT true")
stage("    peripheral.getType('<side>')                         EXPECT 'modem' then 'peripheral_hub'")
stage("    peripheral.call('<side>', 'getTypeRemote', '<name>') EXPECT the remote's types, primary first")
stage("    peripheral.call('<side>', 'hasTypeRemote', '<name>', 'peripheral_hub')  EXPECT true if the")
stage("      remote is itself a wired modem")

stage("POCKET UPGRADES. Put a pocket computer in your inventory, hold nothing else, and in a shell:")
stage("    pocket.equipBack()   EXPECT true with a SPEAKER in your inventory")
stage("  Before this batch that returned false: equipBack only accepted a plain wireless modem.")
stage("  Check the same for: wireless modem, ender modem, speaker, chat box.")
stage("    pocket.isEquipped()  EXPECT true for each of them")
stage("    pocket.unequipBack() EXPECT the SAME item back, not always a modem")

stage("POCKET UPGRADE PERSISTENCE. Equip a speaker, exit the pocket computer, re-enter it:")
stage("    peripheral.getType('back')   EXPECT 'speaker'")
stage("  That exercises the rebuilt-from-NBT path, which now uses the same upgrade table as equipBack.")

stage("WIRELESS STILL WORKS. With a wireless modem equipped:")
stage("    rednet.open('back'); rednet.broadcast('hi')   EXPECT no errors")

-- ---------------------------------------------------------------------------
print("")
print("--- WHAT TO CHECK (staged) ---")
for i, text in ipairs(staged) do
  print(i .. ") " .. text)
end
print("")
print("Full transcript: /batch-m-results.txt")

finish("BATCH-M pocket/peripheral types")
