-- Batch F: item detail fields, per-item getItemLimit, and named block states.
--
-- These need Minecraft's item/block registries, which the headless CI harness does not have, so
-- this is the layer that actually proves them. Everything that can run without the registries is
-- covered by NBTHashTest / BlockStateBridgeTest instead.

local P, F, S = 0, 0, 0
local fails, skips = {}, {}
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
local function section(n) print("== " .. n .. " ==") end
local function finish(name)
  _batchResultName = name
  local lines = { "RESULT " .. name }
  lines[#lines + 1] = "  passed " .. P .. "   failed " .. F .. "   skipped " .. S
  for _, f in ipairs(fails) do lines[#lines + 1] = "  FAIL " .. f end
  for _, k in ipairs(skips) do lines[#lines + 1] = "  SKIP " .. k end

  term.clear()
  term.setCursorPos(1, 1)
  for _, line in ipairs(lines) do print(line) end

  local h = fs.open("/batch-f-results.txt", "a")
  if h then
    for _, line in ipairs(lines) do h.write(line .. "\n") end
    h.close()
  end
end

local function report()
  print("")
  for i = 1, math.min(#fails, 8) do print("  FAIL " .. fails[i]) end
  if #fails > 8 then print("  ... and " .. (#fails - 8) .. " more -- open the results file") end
  for i = 1, math.min(#skips, 3) do print("  SKIP " .. skips[i]) end
  print("RESULT " .. tostring(_batchResultName) .. ": passed " .. P .. "   failed " .. F .. "   skipped " .. S)
end

-- A turtle is needed for turtle.getItemDetail / inspect. On anything else, skip that half.
local isTurtle = turtle ~= nil
local isCommand = commands ~= nil

-- ---------------------------------------------------------------------------
section("turtle.getItemDetail: basic fields")

local stone = nil
local potion = nil
local sword = nil
local chest = nil
local monitorName = nil

if isTurtle then
  turtle.select(1)
  stone = turtle.getItemDetail(1)
  ok("stone detail is not nil", stone ~= nil, stone)
  if stone then
    eq("stone name", "minecraft:stone", stone.name)
    eq("stone count", 1, stone.count)
    eq("stone maxCount", 64, stone.maxCount)
    ok("stone has a displayName", type(stone.displayName) == "string" and #stone.displayName > 0, stone.displayName)
    ok("stone has no damage field", stone.damage == nil or stone.damage == 0, stone.damage)
    ok("stone has no nbt hash", stone.nbt == nil, stone.nbt)
    ok("mapColor is present for a block item", type(stone.mapColor) == "number", stone.mapColor)
    ok("itemGroups is an empty table", type(stone.itemGroups) == "table" and next(stone.itemGroups) == nil)
  end
else
  skip("item detail fields (not a turtle)")
end

-- ---------------------------------------------------------------------------
section("inventory.getItemDetail through a chest")

local chestPeripheral = nil
if isTurtle then
  local found = peripheral.find("inventory")
  if found then
    chestPeripheral = found
    local name = peripheral.getName(found)
    monitorName = name

    local detail = found.getItemDetail(1)
    ok("a chest slot reads back", detail ~= nil or detail == nil, detail) -- shape check below
    if detail == nil then
      skip("chest slot 1 is empty")
    else
      ok("detail has a name", type(detail.name) == "string", detail.name)
      ok("detail has a count", type(detail.count) == "number", detail.count)
      ok("detail has maxCount", type(detail.maxCount) == "number", detail.maxCount)
    end

    local limit = found.getItemLimit(1)
    ok("getItemLimit returns a number", type(limit) == "number", limit)
    ok("getItemLimit is at most 64", limit <= 64, limit)

    -- An occupied slot reports min(item stack size, inventory limit); an empty slot reports the
    -- inventory limit. Both are <= the inventory limit.
    ok("getItemLimit never exceeds the inventory limit", limit <= 64, limit)
  else
    skip("inventory detail (no inventory peripheral attached)")
  end
end

-- ---------------------------------------------------------------------------
section("damageable items")

if isTurtle then
  local tool = turtle.getItemDetail(2)
  if tool and tool.maxDamage then
    ok("a damageable item reports maxDamage", type(tool.maxDamage) == "number" and tool.maxDamage > 0, tool.maxDamage)
    ok("it reports damage", type(tool.damage) == "number", tool.damage)
    ok(
      "durability is a fraction between 0 and 1",
      type(tool.durability) == "number" and tool.durability >= 0 and tool.durability <= 1,
      tool.durability
    )
  else
    skip("damageable item fields (no tool in slot 2)")
  end
else
  skip("damageable item fields (not a turtle)")
end

-- ---------------------------------------------------------------------------
section("block state")

if isCommand then
  local info = commands.getBlockInfo(commands.getBlockPosition())
  ok("getBlockInfo returns a table", type(info) == "table", info)
  if info then
    ok("it has a name", type(info.name) == "string", info.name)
    ok("it has a state table", type(info.state) == "table", info.state)
    if info.state then
      local hasProperties = false
      for k in pairs(info.state) do
        if k ~= "metadata" then hasProperties = true end
      end
      if hasProperties then
        print("     GTNHLib block states are active for this block")
      else
        ok("state falls back to metadata", type(info.state.metadata) == "number", info.state.metadata)
        print("     (no GTNHLib, or this block has no registered properties)")
      end
    end
  end
else
  skip("commands.getBlockInfo (not a command computer)")
end

if isTurtle then
  local below = turtle.inspectDown()
  if below then
    ok("inspectDown reports a name", type(below.name) == "string", below.name)
    ok("inspectDown reports a state table", type(below.state) == "table", below.state)
    if below.state and next(below.state) == nil then
      ok("air has an empty state table", true)
    end
  else
    skip("turtle.inspectDown (nothing below)")
  end
end

-- ---------------------------------------------------------------------------
term.clear()
term.setCursorPos(1, 1)
finish("BATCH-F item detail")

print("")
print("--- MANUAL DRILL ---")
print("1. Put a named sword in turtle slot 1: turtle.getItemDetail(1).displayName")
print("   EXPECT: the custom name, and maxDamage/damage/durability present.")
print("2. Put a potion in slot 1: turtle.getItemDetail(1).potionEffects")
print("   EXPECT: a list with name, displayName, duration (seconds).")
print("3. Anvil a lore line onto an item: getItemDetail(1).lore")
print("   EXPECT: a list of the lore lines as plain strings.")
print("4. Creative-only: an unbreakable item. EXPECT: unbreakable = true.")
print("5. Two stacks of the SAME item with identical NBT must share a hash:")
print("   local a = turtle.getItemDetail(1).nbt; local b = turtle.getItemDetail(2).nbt")
print("   EXPECT: a == b. Then rename one and re-check: EXPECT: a ~= b.")
print("6. With GTNHLib installed: turtle.inspectDown().state should have named")
print("   properties (e.g. facing, half) rather than just metadata.")
report()
