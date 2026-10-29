-- Batch A / item 8: textutils JSON round-trips, non-ASCII handling, SNBT parsing.
--
-- All non-ASCII input is built with string.char() so this file stays pure ASCII and
-- cannot be mangled by however it gets onto the computer.
--
-- Expectations come from released CC:Tweaked 1.120.2 behaviour:
--   * default escaping escapes bytes >= 0x7F, uppercase hex  -> "\u00C3\u00A9"
--   * unicode_strings escapes code points                    -> "\u00E9"
--   * code points above U+FFFF become a UTF-16 surrogate pair

local P, F, S = 0, 0, 0
local fails = {}
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
-- Skips are recorded as well as printed: finish() clears the screen, which would otherwise
-- destroy the only explanation for a non-zero skipped count.
local skips = {}
local function skip(label)
  S = S + 1
  skips[#skips + 1] = label
  print("SKIP " .. label)
end
local function section(n) print("== " .. n .. " ==") end
-- Prints the summary, and also appends it to /batch-a-results.txt. The log matters because
-- the manual drills printed afterwards scroll a 19-row screen, so the on-screen copy is not
-- always the last thing visible. Review the log with:  type /batch-a-results.txt
local function finish(name)
  local lines = { "RESULT " .. name }
  lines[#lines + 1] = "  passed " .. P .. "   failed " .. F .. "   skipped " .. S
  for i = 1, #fails do lines[#lines + 1] = "  FAIL " .. fails[i] end
  for i = 1, #skips do lines[#lines + 1] = "  SKIP " .. skips[i] end

  term.clear()
  term.setCursorPos(1, 1)
  for _, line in ipairs(lines) do print(line) end

  local h = fs.open("/batch-a-results.txt", "a")
  if h then
    for _, line in ipairs(lines) do h.write(line .. "\n") end
    h.close()
  end
end

local E_ACUTE = string.char(195, 169)          -- U+00E9
local EURO = string.char(226, 130, 172)        -- U+20AC
local EMOJI = string.char(240, 159, 152, 128)  -- U+1F600

-- ---------------------------------------------------------------------------
section("plain JSON basics")

eq("string", "\"hello\"", textutils.serializeJSON("hello"))
eq("number", "42", textutils.serializeJSON(42))
eq("boolean", "true", textutils.serializeJSON(true))
eq("empty table is an object", "{}", textutils.serializeJSON({}))
eq("empty array sentinel", "[]", textutils.serializeJSON(textutils.empty_json_array))
eq("null sentinel", "null", textutils.serializeJSON(textutils.json_null))
eq("array", "[10,20,30]", textutils.serializeJSON({ 10, 20, 30 }))
eq("array holes become null", "[1,null,3]", textutils.serializeJSON({ 1, nil, 3 }))
eq("control chars are escaped", "\"a\\nb\"", textutils.serializeJSON("a\nb"))

section("sentinels are immutable and stringify")
eq("tostring(json_null)", "null", tostring(textutils.json_null))
eq("tostring(empty_json_array)", "[]", tostring(textutils.empty_json_array))
local okMutate = pcall(function() textutils.json_null.x = 1 end)
ok("json_null rejects writes", not okMutate)
eq("json_null is still empty", nil, next(textutils.json_null))

-- ---------------------------------------------------------------------------
section("non-ASCII: default path escapes bytes")

-- Regression: this used to emit raw UTF-8, which is not ASCII-safe JSON.
eq("default escapes each byte", "\"\\u00C3\\u00A9\"", textutils.serializeJSON(E_ACUTE))

local ascii = true
local json = textutils.serializeJSON(E_ACUTE)
for i = 1, #json do
  if json:byte(i) >= 128 then ascii = false end
end
ok("default output is pure ASCII", ascii, json)

section("non-ASCII: unicode_strings escapes code points")

eq("2-byte code point", "\"\\u00E9\"", textutils.serializeJSON(E_ACUTE, { unicode_strings = true }))
eq("3-byte code point", "\"\\u20AC\"", textutils.serializeJSON(EURO, { unicode_strings = true }))
eq("4-byte code point", "\"\\uD83D\\uDE00\"", textutils.serializeJSON(EMOJI, { unicode_strings = true }))
eq("ASCII unaffected", "\"hello\"", textutils.serializeJSON("hello", { unicode_strings = true }))
eq("newline still escaped", "\"a\\nb\"", textutils.serializeJSON("a\nb", { unicode_strings = true }))

section("round-trip")

for _, case in ipairs({
  { "ascii", "plain" },
  { "2-byte", E_ACUTE },
  { "3-byte", EURO },
  { "4-byte", EMOJI },
  { "empty", "" },
}) do
  local name, value = case[1], case[2]
  local encoded = textutils.serializeJSON(value, { unicode_strings = true })
  eq("round-trip " .. name, value, textutils.unserializeJSON(encoded))
end

section("malformed UTF-8 must not throw")

local okBad, badJson = pcall(textutils.serializeJSON, string.char(255) .. "x", { unicode_strings = true })
ok("invalid byte does not raise", okBad, badJson)
eq("invalid byte is escaped", "\"\\u00FFx\"", badJson)

-- ---------------------------------------------------------------------------
section("unserializeJSON options")

eq("plain number", 42, textutils.unserializeJSON("42"))
eq("plain string", "x", textutils.unserializeJSON("\"x\""))
eq("trailing content rejected", nil, (textutils.unserializeJSON("42 extra")))
eq("malformed rejected", nil, (textutils.unserializeJSON("{")))

eq(
  "parse_null returns the sentinel",
  true,
  textutils.unserializeJSON("{\"a\":null}", { parse_null = true }).a == textutils.json_null
)
eq("null defaults to nil", nil, textutils.unserializeJSON("{\"a\":null}").a)
eq(
  "legacy { null = ... } still works",
  "MISSING",
  textutils.unserializeJSON("{\"a\":null}", { null = "MISSING" }).a
)

eq("empty array -> sentinel", true, textutils.unserializeJSON("[]") == textutils.empty_json_array)
eq(
  "parse_empty_array=false -> fresh table",
  false,
  textutils.unserializeJSON("[]", { parse_empty_array = false }) == textutils.empty_json_array
)

section("unicode escapes on input")

eq("\\u0041", "A", textutils.unserializeJSON("\"\\u0041\""))
eq("\\u00e9 is 2 UTF-8 bytes", 2, #textutils.unserializeJSON("\"\\u00e9\""))
eq("\\u20ac is 3 UTF-8 bytes", 3, #textutils.unserializeJSON("\"\\u20ac\""))
eq("surrogate pair is 4 UTF-8 bytes", 4, #textutils.unserializeJSON("\"\\uD83D\\uDE00\""))
eq("surrogate pair encodes to the emoji", EMOJI, textutils.unserializeJSON("\"\\uD83D\\uDE00\""))

-- ---------------------------------------------------------------------------
section("NBT style (SNBT)")

local nbt = [[{id:"minecraft:stone",Count:1b,Damage:0s,tag:{display:{Name:"Test"}}}]]
local t = textutils.unserializeJSON(nbt, { nbt_style = true })
ok("SNBT parsed", t ~= nil, "nil")
if t then
  eq("id", "minecraft:stone", t.id)
  eq("Count with byte suffix", 1, t.Count)
  eq("Damage with short suffix", 0, t.Damage)
  eq("nested tag", "Test", t.tag and t.tag.display and t.tag.display.Name)
end

local inv = [[{Inventory:[{Slot:0b,id:"minecraft:dirt",Count:3b}],Level:5}]]
local t2 = textutils.unserializeJSON(inv, { nbt_style = true })
ok("inventory SNBT parsed", t2 ~= nil, "nil")
if t2 then
  eq("list length", 1, #t2.Inventory)
  eq("suffixed count inside a list", 3, t2.Inventory[1].Count)
  eq("plain number", 5, t2.Level)
end

eq("single-quoted string", "quoted", textutils.unserializeJSON("{a:'quoted'}", { nbt_style = true }).a)
local iarr = textutils.unserializeJSON("[I;1,2,3]", { nbt_style = true })
eq("int array length", 3, #iarr)
eq("int array element", 3, iarr[3])
local barr = textutils.unserializeJSON("[B;1b,2b]", { nbt_style = true })
eq("byte array length", 2, #barr)
local larr = textutils.unserializeJSON("[L;1L,2L]", { nbt_style = true })
eq("long array length", 2, #larr)

eq("unquoted keys rejected without the flag", nil, (textutils.unserializeJSON("{a:1}")))

section("nbt_style output")

local out = textutils.serializeJSON({ a = 1 }, { nbt_style = true })
eq("unquoted key on output", "{a:1}", out)
eq(
  "nbt_style round-trip",
  1,
  textutils.unserializeJSON(out, { nbt_style = true }).a
)

section("aliases")

eq("serialiseJSON", "true", textutils.serialiseJSON(true))
eq("unserialiseJSON", 7, textutils.unserialiseJSON("7"))

finish("BATCH-A-8 JSON")

-- FINAL SUMMARY
-- The RESULT block above is printed before the drill, and a 19-row screen plus the drill can
-- scroll it out of view. Repeat the outcome as the last line so it is always visible without
-- scrolling. (Prompted by a run where only the drill was visible.)
print("")
print("RESULT BATCH-A-8 JSON: passed " .. P .. "   failed " .. F
  .. (F > 0 and "   -- failures also logged to the results file" or ""))
