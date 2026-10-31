-- Batch L: GPS accuracy — fixed channel and dedupe.
--
-- The interesting half is covered by GpsLocateTest, which drives the real ROM chunk with scripted
-- modem events: the fixed channel, the dedupe, trilateration, and every rejection path. This script
-- covers what needs a live world and cannot block: the channel constant, the no-modem path, and the
-- geometry the ROM's maths depends on. Everything that waits for a real modem reply is in the
-- manual drill, because a headless run has no timers to wait on.
--
-- Transcript: /batch-l-results.txt
--   readlog_manual /batch-l-results.txt

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

  local path = "/batch-l-results.txt"
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

local function findWirelessModem()
  for _, side in ipairs(rs.getSides()) do
    if peripheral.getType(side) == "modem" and peripheral.call(side, "isWireless") then
      return side
    end
  end
  return nil
end

local modemSide = findWirelessModem()

-- ---------------------------------------------------------------------------
print("== constants ==")
group("constants", function()
  eq("the GPS channel is 65534", 65534, gps.CHANNEL_GPS)
  -- The old code keyed everything off the computer id; the fixed channel is what this batch changed.
  ok("the GPS channel is not the computer id", gps.CHANNEL_GPS ~= os.getComputerID())
end)

-- ---------------------------------------------------------------------------
print("== no modem ==")
group("no modem", function()
  if modemSide then
    -- Calling locate here would wait for a timer, which a headless run cannot provide.
    skip("a wireless modem is attached, so the no-modem path cannot be exercised")
    return
  end

  local x, y, z = gps.locate(1)
  ok("locate returns nothing without a modem", x == nil and y == nil and z == nil)
end)

-- ---------------------------------------------------------------------------
print("== the geometry the maths relies on ==")
group("geometry", function()
  local okLoad, vectorApi = pcall(require, "vector")
  if not okLoad or type(vectorApi) ~= "table" then
    skip("the vector API is not loadable here")
    return
  end

  -- Three coplanar hosts and a listener in the same plane: the two trilateration solutions coincide,
  -- which is why three hosts are enough for an unambiguous answer. These are the distances the hosts
  -- would report, and the same numbers GpsLocateTest asserts against.
  local listener = vectorApi.new(10, 0, 20)
  local hosts = { vectorApi.new(0, 0, 0), vectorApi.new(100, 0, 0), vectorApi.new(0, 0, 100) }
  local ranges = {}
  for i, host in ipairs(hosts) do
    ranges[i] = (host - listener):length()
  end

  local function round2(value)
    return math.floor(value * 100 + 0.5) / 100
  end

  eq("range to 0,0,0", 22.36, round2(ranges[1]))
  eq("range to 100,0,0", 92.19, round2(ranges[2]))
  eq("range to 0,0,100", 80.62, round2(ranges[3]))

  -- A host at the listener's own position reports zero, which is the case locate() answers immediately.
  eq("a host at our position reports 0", 0, (vectorApi.new(10, 0, 20) - listener):length())
end)

-- ---------------------------------------------------------------------------
print("")
print("--- MANUAL CHECKS (these need real modems and real time) ---")
print("1. ONE HOST IS ENOUGH TO PROVE THE CHANNEL. Put a wireless modem on this")
print("   computer, run  gps host <x> <y> <z>  in a second tab, then  gps locate")
print("   here. EXPECT the position you hosted, immediately: a reply at distance")
print("   ~0 is the answer, and it only arrives if the ping and the reply both use")
print("   channel 65534.")
print("2. NO HOST. Without any host,  gps locate  must return nil after about two")
print("   seconds -- not hang -- and must not leave channel 65534 open:")
print("     peripheral.call('<side>', 'isOpen', 65534)   EXPECT false")
print("3. THE DEDUPE, the point of this batch. Three wireless modems on computers")
print("   at known non-collinear positions,  gps host  on each, then  gps locate")
print("   on a fourth computer. EXPECT its exact position.")
print("4. THE DEDUPE, part two. Shut two hosts down and  gps locate  again.")
print("   EXPECT nil. Before this batch one chatty host could fill all three slots")
print("   and produce a wrong position; now a lone host produces none.")
print("5. TWO HOSTS IN THE SAME PLACE (within one block) plus a third elsewhere.")
print("   EXPECT nil: the two identical ones count as one, so there are only two.")
print("6. readlog_manual /batch-l-results.txt for the transcript.")
print("")

finish("BATCH-L gps")
