-- Batch G: command-computer parity — dimensions, block range ordering, entity selectors.
--
-- MUST run on a COMMAND COMPUTER. If `commands` is not present the run reports one SKIP and
-- stops, so it is safe to run anywhere by accident.
--
-- The whole run is written to /batch-g-results.txt, so it can be read afterwards with
--   readlog_manual /batch-g-results.txt
-- (a terminal cannot be scrolled, and these scripts print more than one screen).

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

  -- The full transcript goes to a file: a terminal cannot scroll, and the headless harness in
  -- IngameTestScriptsTest requires the verdict to be the last thing on screen.
  local path = "/batch-g-results.txt"
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
  print("File: " .. path .. "  (readlog_manual)")
  print("RESULT " .. name .. ": passed " .. P .. "   failed " .. F .. "   skipped " .. S)
end
-- ---------------------------------------------------------------------------
term.clear()
term.setCursorPos(1, 1)

if type(commands) ~= "table" then
  skip("not a command computer -- nothing to test")
  print("Run this on a command computer.")
  finish("BATCH-G commands")
  return
end

local function expectError(label, fn, want)
  local gotOk, err = pcall(fn)
  if gotOk then
    ok(label .. " should have raised", false, "returned normally")
    return
  end
  if want then
    ok(label, tostring(err):lower():find(want, 1, true) ~= nil, tostring(err))
  else
    pass(label)
  end
end

-- ---------------------------------------------------------------------------
print("== getDimension ==")
group("getDimension", function()
  local dim = commands.getDimension()
  ok("getDimension returns a string", type(dim) == "string", dim)
  ok("getDimension looks namespaced", tostring(dim):find(":", 1, true) ~= nil, dim)
  print("  dimension: " .. tostring(dim))
end)

-- ---------------------------------------------------------------------------
print("== dimension arguments ==")
group("dimension arguments", function()
  local x, y, z = commands.getBlockPosition()

  -- numeric id (pre-Batch-G form) must still work
  local byId = commands.getBlockInfo(x, y, z, 0)
  ok("getBlockInfo(.., 0) still accepted", type(byId) == "table" and type(byId.name) == "string")

  -- the same place named
  local byName = commands.getBlockInfo(x, y, z, "minecraft:overworld")
  ok("getBlockInfo(.., name) accepted", type(byName) == "table" and type(byName.name) == "string")
  eq("id and name agree", byId and byId.name, byName and byName.name)

  -- a different dimension, reached from here
  local netherOk, nether = pcall(commands.getBlockInfo, 0, 64, 0, "minecraft:the_nether")
  if netherOk then
    ok("getBlockInfo into the nether", type(nether) == "table" and type(nether.name) == "string")
    print("  0,64,0 in the nether: " .. tostring(nether and nether.name))
  else
    ok("getBlockInfo into the nether", false, nether)
  end

  -- bare aliases and case-insensitivity
  ok("nether alias", pcall(commands.getBlockInfo, 0, 64, 0, "nether"))
  ok("end alias", pcall(commands.getBlockInfo, 0, 64, 0, "end"))
  ok("upper-case name", pcall(commands.getBlockInfo, 0, 64, 0, "Minecraft:The_Nether"))

  expectError("unknown dimension name", function() commands.getBlockInfo(0, 64, 0, "nope:nothing") end, "unknown")
end)

-- ---------------------------------------------------------------------------
print("== getBlockInfos ordering ==")
group("getBlockInfos ordering", function()
  local x, y, z = commands.getBlockPosition()
  -- A 2x2x2 box. CC:Tweaked traverses y, then z, then x, so the index is
  --   x + z*width + y*width*depth + 1, with width = depth = 2.
  local blocks = commands.getBlockInfos(x, y, z, x + 1, y + 1, z + 1)
  ok("getBlockInfos returns a table", type(blocks) == "table")
  eq("2x2x2 has 8 entries", 8, #blocks)

  local mismatches = 0
  for dy = 0, 1 do
    for dz = 0, 1 do
      for dx = 0, 1 do
        local idx = dx + dz * 2 + dy * 4 + 1
        local single = commands.getBlockInfo(x + dx, y + dy, z + dz)
        local ranged = blocks[idx]
        if not ranged or ranged.name ~= single.name then
          mismatches = mismatches + 1
          if mismatches <= 3 then
            print(
              string.format(
                "  [order] %d,%d,%d at index %d: got %s want %s",
                x + dx,
                y + dy,
                z + dz,
                idx,
                tostring(ranged and ranged.name),
                tostring(single.name)
              )
            )
          end
        end
      end
    end
  end
  eq("index = x + z*width + y*width*depth + 1", 0, mismatches)

  -- range arguments may be given in any corner order
  local reversed = commands.getBlockInfos(x + 1, y + 1, z + 1, x, y, z)
  eq("reversed corners give the same count", 8, #reversed)
  eq("reversed corners give the same first block", blocks[1] and blocks[1].name, reversed[1] and reversed[1].name)

  expectError("more than 4096 blocks", function() commands.getBlockInfos(0, 0, 0, 20, 20, 20) end, "too many")
end)

-- ---------------------------------------------------------------------------
print("== commands.list ==")
group("commands.list", function()
  local all = commands.list()
  ok("list() returns a table", type(all) == "table")
  ok("list() is not empty", #all > 0, #all)
  print("  commands visible: " .. #all)

  local prefixed = commands.list("ti")
  ok("list(prefix) returns a table", type(prefixed) == "table")
  local bad = 0
  for _, name in ipairs(prefixed) do
    if type(name) ~= "string" or name:sub(1, 2) ~= "ti" then bad = bad + 1 end
  end
  eq("every entry matches the prefix", 0, bad)
  ok("list(prefix) narrows the set", #prefixed <= #all, #prefixed .. " vs " .. #all)
end)

-- ---------------------------------------------------------------------------
print("== getEntities ==")
group("getEntities", function()
  local players = commands.getEntities("@a")
  ok("@a returns a table", type(players) == "table")
  ok("@a finds at least one player", #players >= 1, #players)
  for i = 1, math.min(#players, 3) do
    local p = players[i]
    ok("@a entry " .. i .. " has a name", type(p) == "table" and type(p.name) == "string", p and p.name)
    ok(
      "@a entry " .. i .. " has a displayName",
      type(p) == "table" and type(p.displayName) == "string",
      p and p.displayName
    )
    print("  player: " .. tostring(p.name) .. " / " .. tostring(p.displayName))
  end

  -- @p and @r
  ok("@p resolves", #commands.getEntities("@p") == 1)
  ok("@r resolves", #commands.getEntities("@r") <= 1)

  -- a player by name
  local selfName = players[1] and players[1].displayName
  if selfName then
    local byName = commands.getEntities(selfName)
    ok("a player can be named", #byName >= 1, #byName)
  end

  -- @e scanning
  local all_e = commands.getEntities("@e[type=zombie,distance=..128,limit=10]")
  ok("@e[type=zombie] returns a table", type(all_e) == "table")
  print("  zombies within 128: " .. #all_e)
  for i = 1, math.min(#all_e, 3) do
    eq("@e entry " .. i .. " name", "zombie", all_e[i].name)
  end

  local limited = commands.getEntities("@e[distance=..128,limit=1]")
  ok("limit= is honoured", #limited <= 1, #limited)

  -- failures
  expectError("unknown filter", function() commands.getEntities("@e[tag=foo]") end, "unsupported")
  expectError("malformed selector", function() commands.getEntities("@e[type") end)
end)

-- ---------------------------------------------------------------------------
-- The drill is printed BEFORE the verdict: the verdict must stay the last line on screen.
print("")
print("--- MANUAL DRILL ---")
print("1. Spawn a zombie within 10 blocks, then run:")
print("   commands.getEntities('@e[type=zombie,distance=..10]')")
print("   EXPECT one table with name='zombie', displayName='Zombie', health/maxHealth.")
print("2. Stand in the nether and run commands.getDimension()")
print("   EXPECT 'minecraft:the_nether'.")
print("3. From the overworld read a nether block:")
print("   commands.getBlockInfo(0,64,0,'minecraft:the_nether').name")
print("4. Put a distinct block at y+1 next to the computer and confirm the 2x2x2")
print("   ordering check above still reports 0 mismatches.")
print("5. commands.getEntities('@e[nbt=foo]') must raise 'Unsupported selector filter'.")
print("6. readlog_manual /batch-g-results.txt for the full transcript.")

finish("BATCH-G commands")
