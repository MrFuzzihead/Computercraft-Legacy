-- Batch O: turtle refuelling.
--
-- Runs on a turtle. On a plain computer it reports a single skip and writes an empty verdict, so the
-- headless harness can still execute it.
--
-- The automated part needs no particular item: it checks the fuel surface, the empty-slot guard, and —
-- if the selected slot happens to hold something burnable or charged — the probe/consume contract. The
-- interesting distinction (a battery is drained, not eaten) is automated when a battery is in a slot and
-- staged otherwise, because a turtle program cannot give itself items.
--
-- Transcript: /batch-o-results.txt
--   readlog_manual /batch-o-results.txt

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
  print("SKIP " .. label)
end

local function note(text)
  _transcript[#_transcript + 1] = text
end

local function group(name, fn)
  local okRun, err = pcall(fn)
  if not okRun then ok(name .. " crashed", false, err) end
end

local function stage(text)
  staged[#staged + 1] = text
  _transcript[#_transcript + 1] = "STAGED " .. text
end

-- ---------------------------------------------------------------------------
-- Fuel surface
-- ---------------------------------------------------------------------------

local function fuelSurface()
  group("fuel surface", function()
    local level = turtle.getFuelLevel()
    local limit = turtle.getFuelLimit()

    eq("getFuelLevel returns a number", "number", type(level))
    eq("getFuelLimit returns a number", "number", type(limit))
    ok("fuel level is never negative", level == "unlimited" or level >= 0, tostring(level))
    ok(
      "fuel level is within the limit",
      level == "unlimited" or limit == "unlimited" or level <= limit,
      tostring(level) .. " / " .. tostring(limit)
    )
  end)
end

-- ---------------------------------------------------------------------------
-- The empty-slot guard
-- ---------------------------------------------------------------------------

local function emptySlotGuard()
  group("empty selected slot", function()
    local empty = nil
    for slot = 1, 16 do
      if turtle.getItemCount(slot) == 0 then
        empty = slot
        break
      end
    end

    if empty == nil then
      skip("every slot is occupied; clear one slot to check the empty-slot guard")
      return
    end

    turtle.select(empty)
    local okRefuel, why = turtle.refuel(1)
    eq("refuel with an empty slot fails", false, okRefuel)
    eq("refuel with an empty slot reports no items", "No items to combust", why)

    local okProbe = turtle.refuel(0)
    eq("refuel(0) with an empty slot also fails", false, okProbe)
  end)
end

-- ---------------------------------------------------------------------------
-- A burnable or charged item, if one happens to be in the inventory
-- ---------------------------------------------------------------------------

local function probeAndConsume()
  group("probe and consume", function()
    local candidate = nil
    for slot = 1, 16 do
      if turtle.getItemCount(slot) > 0 then
        turtle.select(slot)
        if turtle.refuel(0) then
          candidate = slot
          break
        end
      end
    end

    if candidate == nil then
      skip("no fuel or charged item in the inventory; put one in a slot and re-run")
      return
    end

    pass("refuel(0) recognises the item in slot " .. candidate)

    local beforeDetail = turtle.getItemDetail(candidate) or {}
    local beforeItems = turtle.getItemCount(candidate)
    local beforeFuel = turtle.getFuelLevel()
    local okRefuel = turtle.refuel(1)
    local afterDetail = turtle.getItemDetail(candidate) or {}
    local afterItems = turtle.getItemCount(candidate)
    local afterFuel = turtle.getFuelLevel()

    note(
      ("     slot %d held %s x%d at fuel %s; now x%d at fuel %s"):format(
        candidate,
        tostring(beforeDetail.name),
        beforeItems,
        tostring(beforeFuel),
        afterItems,
        tostring(afterFuel)
      )
    )

    eq("refuel(1) succeeds for a recognised item", true, okRefuel)

    local unlimited = beforeFuel == "unlimited" or afterFuel == "unlimited"
    local tankFull = (not unlimited) and beforeFuel >= turtle.getFuelLimit()

    ok("fuel did not go down", unlimited or afterFuel >= beforeFuel, tostring(beforeFuel) .. " -> " .. tostring(afterFuel))

    if afterItems < beforeItems then
      -- Burnable fuel: the item is gone, so it must have paid out unless the tank was already full.
      ok("a consumed item added fuel", unlimited or afterFuel > beforeFuel or tankFull, tostring(beforeFuel) .. " -> " .. tostring(afterFuel))
    else
      -- Charged fuel: the item is drained, not eaten. It stays in the slot and the fuel still goes up, unless the
      -- battery was empty or the tank was already full.
      ok(
        "an unconsumed item is still the same item",
        afterItems == beforeItems and afterDetail.name == beforeDetail.name,
        tostring(beforeDetail.name) .. " -> " .. tostring(afterDetail.name)
      )
      ok(
        "a drained item added fuel, or it had none to give",
        unlimited or afterFuel > beforeFuel or tankFull,
        tostring(beforeFuel) .. " -> " .. tostring(afterFuel)
      )
    end
  end)
end

-- ---------------------------------------------------------------------------
-- Staged manual checks
-- ---------------------------------------------------------------------------

local function stagedChecks()
  stage("Refuel from coal. Put 5 coal in slot 1, run: turtle.select(1) turtle.refuel(2). Exactly two coal should be consumed and the fuel level should rise by 2 * 20 (coal burns 80 ticks, at 5 fuel per second of burn).")
  stage("Refuel with no argument. turtle.refuel() should consume up to a full stack's worth, capped by the space left in the tank - it must not burn items the tank cannot hold.")
  stage("Check the tank cap. Fill the turtle nearly to its limit, then turtle.refuel(64) with a full stack of coal. The tank must top out and leftover coal must remain in the slot.")
  stage("Refuel from an IC2 battery (IC2 installed). Put a charged RE Battery in slot 1, run turtle.select(1) print(turtle.refuel(0)). It should print true. Then print(turtle.refuel(1)) - it should print true, the fuel level should rise, and THE BATTERY MUST STILL BE IN THE SLOT, with less charge. This is the electric path: the item is drained, not consumed.")
  stage("Check the IC2 ratio. With the default config (turtleEuRefuelAmount = 25), a 10000 EU battery with an empty tank should be worth 400 fuel. Change the ratio in config/ComputerCraft.cfg and confirm the yield scales; set it to 0 and confirm the battery is no longer accepted.")
  stage("Refuel from a CoFH energy cell (CoFH Core installed). Same as the IC2 check: turtle.refuel(0) true, refuel(1) raises fuel, the cell stays in the slot. Default turtleRfRefuelAmount = 100.")
  stage("Refuel from a lava bucket. Two things should happen at once: the bucket is consumed for fuel AND an empty bucket appears in the inventory. A full tank should consume nothing at all.")
  stage("Disable the action. Add 'refuel' to turtleDisabledActions in config/ComputerCraft.cfg, restart, and run turtle.refuel(1) with coal selected. It must fail with 'Turtle action \"refuel\" is disabled' and eat nothing. This check is the one the old CCTweaks mixin broke.")
  stage("Probe a partially empty battery. With an empty battery in slot 1, turtle.refuel(0) should still report true (the item is recognised) and turtle.refuel(1) should gain nothing but must not consume or crash.")
end

-- ---------------------------------------------------------------------------
-- Run
-- ---------------------------------------------------------------------------

if type(turtle) ~= "table" then
  skip("this is not a turtle: run o1_turtle_fuel on a turtle")
else
  fuelSurface()
  emptySlotGuard()
  probeAndConsume()
  stagedChecks()
end

-- ---------------------------------------------------------------------------
-- Report
-- ---------------------------------------------------------------------------

-- Log first: nothing the human-facing output does can then lose the machine-readable result.
local function finish(name)
  local lines = { "RESULT " .. name }
  lines[#lines + 1] = "  passed " .. P .. "   failed " .. F .. "   skipped " .. S
  for _, f in ipairs(fails) do lines[#lines + 1] = "  FAIL " .. f end
  for _, k in ipairs(skips) do lines[#lines + 1] = "  SKIP " .. k end
  for _, line in ipairs(staged) do lines[#lines + 1] = "  STAGED " .. line end
  for _, line in ipairs(_transcript) do lines[#lines + 1] = "    " .. line end

  local path = "/batch-o-results.txt"
  local h = fs.open(path, "a")
  if h then
    for _, line in ipairs(lines) do
      h.write(line .. "\n")
    end
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

term.clear()
term.setCursorPos(1, 1)

if type(turtle) ~= "table" then
  skip("this is not a turtle: run o1_turtle_fuel on a turtle")
else
  fuelSurface()
  emptySlotGuard()
  probeAndConsume()
  stagedChecks()
end

finish("batch-o turtle refuelling")
