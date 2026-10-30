-- Batch F: item detail fields, per-item getItemLimit, and named block states.
--
-- Works on any computer:
--   * an attached inventory peripheral (e.g. a chest from a mod that provides one, or a turtle's
--     own inventory is NOT a peripheral) gives the item-detail and getItemLimit checks;
--   * a turtle adds turtle.getItemDetail and turtle.inspectDown;
--   * a command computer adds commands.getBlockInfo.
-- Anything not present is reported as SKIP with a reason, and the whole run is written to
-- /batch-f-results.txt so it can be read afterwards with `readlog_manual /batch-f-results.txt`.
--
-- Note: turtle.getItemDetail(slot) returns the *basic* shape. The full field set needs
-- turtle.getItemDetail(slot, true) — that is upstream's default, not a bug.

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

local function section(n) print("== " .. n .. " ==") end

-- Run a group of checks; a crash inside one group becomes a failure instead of killing the run
-- (and losing the log, which is what happened the first time this was run in game).
local function group(name, fn)
  local okRun, err = pcall(fn)
  if not okRun then ok(name .. " crashed", false, err) end
end

local function finish(name)
  _batchResultName = name
  local lines = { "RESULT " .. name }
  lines[#lines + 1] = "  passed " .. P .. "   failed " .. F .. "   skipped " .. S
  for _, f in ipairs(fails) do lines[#lines + 1] = "  FAIL " .. f end
  for _, k in ipairs(skips) do lines[#lines + 1] = "  SKIP " .. k end
  for _, line in ipairs(_transcript) do lines[#lines + 1] = "    " .. line end

  term.clear()
  term.setCursorPos(1, 1)
  for _, line in ipairs(lines) do print(line) end

  local path = "/batch-f-results.txt"
  local h = fs.open(path, "a")
  if h then
    for _, line in ipairs(lines) do h.write(line .. "\n") end
    h.close()
  else
    printError("Could not write " .. path)
  end
end

local function report()
  print("")
  for i = 1, math.min(#fails, 8) do print("  FAIL " .. fails[i]) end
  if #fails > 8 then print("  ... and " .. (#fails - 8) .. " more -- see the results file") end
  for i = 1, math.min(#skips, 3) do print("  SKIP " .. skips[i]) end
  print("RESULT " .. tostring(_batchResultName) .. ": passed " .. P .. "   failed " .. F .. "   skipped " .. S)
end

local function findInventory()
  local found = peripheral.find("inventory")
  return found, found and peripheral.getName(found) or nil
end

local isTurtle = turtle ~= nil
local isCommand = commands ~= nil

-- ---------------------------------------------------------------------------
section("inventory peripheral")

local chest, chestName = findInventory()
if chest then
  print("     using inventory peripheral: " .. tostring(chestName))
else
  skip("item detail (no inventory peripheral attached -- a vanilla chest is not one)")
end

group("inventory detail", function()
  if not chest then return end

  local detail = chest.getItemDetail(1)
  if detail == nil then
    skip("inventory slot 1 is empty")
    return
  end

  ok("name is a string", type(detail.name) == "string", detail.name)
  ok("count is a number", type(detail.count) == "number", detail.count)
  ok("maxCount is a number", type(detail.maxCount) == "number", detail.maxCount)
  ok("displayName is a string", type(detail.displayName) == "string", detail.displayName)
  ok("itemGroups is an empty table", type(detail.itemGroups) == "table" and next(detail.itemGroups) == nil)

  local limit = chest.getItemLimit(1)
  ok("getItemLimit is a number", type(limit) == "number", limit)
  ok("getItemLimit never exceeds the item's max stack", limit <= (detail.maxCount or 64), limit)
end)

-- ---------------------------------------------------------------------------
section("turtle item detail")

if not isTurtle then
  skip("turtle.getItemDetail (not a turtle)")
else
  group("turtle detail", function()
    turtle.select(1)

    local basic = turtle.getItemDetail(1)
    ok("basic detail is not nil", basic ~= nil, basic)
    if basic then
      ok("basic detail has a name", type(basic.name) == "string", basic.name)
      ok("basic detail has no maxCount", basic.maxCount == nil, basic.maxCount)
      ok("basic detail has no displayName", basic.displayName == nil, basic.displayName)
    end

    local full = turtle.getItemDetail(1, true)
    ok("detailed form is not nil", full ~= nil, full)
    if full then
      ok("detailed form has maxCount", type(full.maxCount) == "number", full.maxCount)
      ok("detailed form has displayName", type(full.displayName) == "string", full.displayName)
      ok("itemGroups is an empty table", type(full.itemGroups) == "table" and next(full.itemGroups) == nil)

      if full.maxDamage then
        ok("a damageable item reports damage", type(full.damage) == "number", full.damage)
        ok(
          "durability is a fraction in 0..1",
          type(full.durability) == "number" and full.durability >= 0 and full.durability <= 1,
          full.durability
        )
      else
        skip("damage/maxDamage/durability (slot 1 does not hold a damageable item)")
      end
    end
  end)
end

-- ---------------------------------------------------------------------------
section("block state")

if not isCommand then
  skip("commands.getBlockInfo (not a command computer)")
else
  group("getBlockInfo", function()
    local info = commands.getBlockInfo(commands.getBlockPosition())
    ok("getBlockInfo returns a table", type(info) == "table", info)
    if not info then return end

    ok("it has a name", type(info.name) == "string", info.name)
    ok("it has a state table", type(info.state) == "table", info.state)
    if not info.state then return end

    local named = {}
    for k in pairs(info.state) do
      if k ~= "metadata" then named[#named + 1] = k end
    end
    if #named > 0 then
      print("     GTNHLib named properties present: " .. table.concat(named, ", "))
      pass("GTNHLib block states are active")
    else
      ok("falls back to state.metadata", type(info.state.metadata) == "number", info.state.metadata)
      print("     (no GTNHLib, or this block has no registered properties)")
    end
  end)
end

if isTurtle then
  group("inspectDown", function()
    -- A failed turtle command returns false plus a message, not nil, so index only a table.
    local below, reason = turtle.inspectDown()
    if type(below) ~= "table" then
      skip("turtle.inspectDown returned " .. tostring(below) .. " (" .. tostring(reason) .. ")")
      return
    end
    ok("inspectDown reports a name", type(below.name) == "string", below.name)
    ok("inspectDown reports a state table", type(below.state) == "table", below.state)
  end)
end

-- ---------------------------------------------------------------------------
term.clear()
term.setCursorPos(1, 1)
finish("BATCH-F item detail")

print("")
print("Full transcript: /batch-f-results.txt")
print("Read it with:   readlog_manual /batch-f-results.txt")
print("")
print("--- MANUAL DRILL ---")
print("1. Run this on a TURTLE with stone in slot 1, or on a computer")
print("   with an inventory peripheral attached.")
print("2. Name a sword on an anvil, put it in slot 1:")
print("   turtle.getItemDetail(1, true).displayName  EXPECT: the custom name.")
print("3. Put a potion in slot 1: ...potionEffects  EXPECT: name/displayName/duration.")
print("4. Anvil a lore line on: ...lore  EXPECT: the lines as plain strings.")
print("5. Two identical stacks must share .nbt; rename one and it must differ.")
print("6. With GTNHLib installed, turtle.inspectDown().state should show named")
print("   properties instead of just metadata.")
report()
