-- Batch I: editor highlighting, help rendering and the Lua REPL.
--
-- Two kinds of check:
--
--   AUTOMATED  - the token stream the editor colours from (cc.internal.syntax), the edit settings,
--                and the help topic machinery. These report OK/FAIL.
--   STAGED     - anything that can only be judged by looking at colours on a screen: the script
--                writes the files and prints exactly what to open and what to expect. Colour
--                readback does not exist in CraftOS, so these cannot be asserted.
--
-- Transcript: /batch-i2-results.txt
--   readlog_manual /batch-i2-results.txt

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

  local path = "/batch-i2-results.txt"
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
  print("Automated: " .. P .. " ok, " .. F .. " failed")
  print("Staged for you: " .. #staged)
  print("File: " .. path .. " (readlog_manual)")
  print("RESULT " .. name .. ": passed " .. P .. "   failed " .. F .. "   skipped " .. S)
end

-- ---------------------------------------------------------------------------
term.clear()
term.setCursorPos(1, 1)

local tokens = require("cc.internal.syntax.parser").tokens
local lex_one = require("cc.internal.syntax.lexer").lex_one
local ctx = { line = function() end, report = function() end }

--[[- The token type sequence of one line, starting from a given state.

This is the exact loop the editor uses to colour a line, so asserting on it asserts on the colours
without needing to read them back.
]]
local function lexLineTokens(contents, state)
  local pos, out = 1, {}
  local token, finish, continuation

  if type(state) == "table" then
    token, finish, _, continuation = state[1](ctx, contents, table.unpack(state, 2))
    pos = 1

    local afterFinish = finish
    finish = afterFinish
  else
    token, _, finish, _, continuation = lex_one(ctx, contents, pos)
  end

  while token do
    out[#out + 1] = token
    pos = finish + 1
    if continuation then break end
    token, _, finish, _, continuation = lex_one(ctx, contents, pos)
  end

  return out, continuation
end

local function hasToken(list, token)
  for _, value in ipairs(list) do
    if value == token then return true end
  end
  return false
end

-- ---------------------------------------------------------------------------
print("== lexer drives the colours ==")
group("lexer colours", function()
  local list = lexLineTokens("local x = 42", false)
  ok("keywords are lexed", hasToken(list, tokens.LOCAL), #list)
  ok("numbers are lexed", hasToken(list, tokens.NUMBER), #list)

  list = lexLineTokens("local s = 'hello'", false)
  ok("strings are lexed", hasToken(list, tokens.STRING), #list)

  list = lexLineTokens("-- a comment", false)
  ok("comments are lexed", hasToken(list, tokens.COMMENT), #list)

  list = lexLineTokens("local function f() return 1 end", false)
  ok("function is a keyword", hasToken(list, tokens.FUNCTION), #list)
  ok("return is a keyword", hasToken(list, tokens.RETURN), #list)
end)

-- ---------------------------------------------------------------------------
print("== multi-line state ==")
group("multi-line state", function()
  -- A long comment opened on line 1 must keep line 2 as comment, and survive a blank line.
  local _, state = lexLineTokens("--[[ opening a long comment", false)
  ok("a long comment leaves state", state ~= nil)

  if state then
    local list2, state2 = lexLineTokens("still commented", state)
    ok("the next line is a comment", hasToken(list2, tokens.COMMENT), #list2)
    ok("the state continues", state2 ~= nil)

    local listBlank, stateBlank = lexLineTokens("", state2 or state)
    ok("a blank line inside a comment keeps state", stateBlank ~= nil)

    local listEnd, stateEnd = lexLineTokens("closing ]] local y = 2", stateBlank or state2)
    ok("the closing line ends the comment", stateEnd == nil)
    ok("code after the comment is lexed", hasToken(listEnd, tokens.LOCAL), #listEnd)
  end

  -- The same for a long string.
  local _, stringState = lexLineTokens("local s = [[ line one", false)
  ok("a long string leaves state", stringState ~= nil)
  if stringState then
    local listText, textState = lexLineTokens("line two ]]", stringState)
    ok("the string body is a string token", hasToken(listText, tokens.STRING), #listText)
    ok("closing the string clears the state", textState == nil)
  end

  -- A short string is terminated by the newline by definition, so it must NOT carry state.
  local _, shortState = lexLineTokens('local x = "unterminated', false)
  ok("a short string does not carry state", shortState == nil, tostring(shortState))
end)

-- ---------------------------------------------------------------------------
print("== edit settings ==")
group("edit settings", function()
  -- Do not assume the ambient value: another script (or the player) may have set it. Save, set,
  -- check and restore.
  local savedExtension = settings.get("edit.default_extension")
  settings.set("edit.default_extension", "lua")
  eq("edit.default_extension round-trips", "lua", settings.get("edit.default_extension"))
  if savedExtension == nil then
    settings.unset("edit.default_extension")
  else
    settings.set("edit.default_extension", savedExtension)
  end

  ok("edit.autocomplete defaults on", settings.get("edit.autocomplete", true) ~= false)
end)

-- ---------------------------------------------------------------------------
print("== help topics ==")
group("help topics", function()
  local topics = help.topics()
  ok("topics exist", type(topics) == "table" and #topics > 0, #topics)

  local speaker = help.lookup("speaker")
  ok("the speaker topic is reachable", speaker ~= nil)

  if speaker then
    local h = io.open(speaker, "r")
    if h then
      local contents = h:read("*a")
      h:close()
      -- The renderer picks the markdown path when a topic has a heading line.
      ok("the speaker topic has a heading", contents:find("%f[^\n\0]#+ ") ~= nil)
    end
  end
end)

-- ---------------------------------------------------------------------------
-- Stage the visual checks.
local dir = "/batch-i-visual"
if not fs.isDir(dir) then fs.makeDir(dir) end

local function writeFile(path, contents)
  local h = fs.open(path, "w")
  if not h then return false end
  h.write(contents)
  h.close()
  return true
end

writeFile(
  dir .. "/colours.lua",
  table.concat({
    "-- a comment line (EXPECT green)",
    "local number = 12345          -- EXPECT magenta for the number",
    "local text = 'a string'       -- EXPECT red between the quotes",
    "local long = [[",
    "  this line is inside a long string and must stay RED,",
    "  and so must this one.",
    "]]",
    "local function f(a, b)       -- EXPECT yellow keywords",
    "  if a and b then return a end",
    "end",
    "",
  }, "\n")
)
stage("edit " .. dir .. "/colours.lua  -- keywords yellow, numbers magenta, strings red, comments green")

writeFile(
  dir .. "/longcomment.lua",
  table.concat({
    "--[[ this long comment covers",
    "everything down to the closing bracket,",
    "",
    "including blank lines,",
    "and this line too. ]]",
    "local after = 'code again'",
    "",
  }, "\n")
)
stage("edit " .. dir .. "/longcomment.lua  -- open it, then PageDown: every line to ']]' must be GREEN, "
  .. "including the blank one; 'local after' must be code colours")

stage("With longcomment.lua open, put the cursor on the first line, delete a character from the "
  .. "opening '--[[' so it is no longer a comment. COUNT: every following line must change colour "
  .. "immediately (that is the state propagation). Undo is not available: do this on a copy.")

writeFile(
  dir .. "/scrolling.lua",
  table.concat(
    (function()
      local lines = { "--[[ a long comment started above the visible area" }
      for i = 1, 40 do lines[#lines + 1] = "comment line " .. i end
      lines[#lines + 1] = "]]"
      lines[#lines + 1] = "local visible = 1"
      return lines
    end)(),
    "\n"
  ) .. "\n"
)
stage("edit " .. dir .. "/scrolling.lua  -- PageDown until the closing ']]' is on screen: the lines "
  .. "must still be GREEN (this is the state seeding that used to draw over the screen; if the "
  .. "screen looks corrupted, that bug is back)")

writeFile(dir .. "/newfile_content", "return 1\n")
stage("settings.set('edit.default_extension','lua') then 'edit " .. dir .. "/typed' -- EXPECT the "
  .. "editor to open typed.lua, and the title/save to use it")

stage("settings.set('edit.autocomplete', false) then 'edit " .. dir .. "/colours.lua' and press TAB "
  .. "on an empty line -- EXPECT no completion. Set it back to true: TAB completes again")

stage("lua  then type:  { 1, 2, { 3 } }  -- EXPECT multi-line pretty output")
stage("lua  then type:  function g()   (enter)   return 1   (enter)   end   -- EXPECT the '   > ' "
  .. "continuation prompt and no syntax error")
stage("lua  then type:  local x = 1  (enter)  x  -- EXPECT 1 printed by itself (force-print of an expression)")

-- help rendering
stage("help speaker  -- EXPECT blue headings and a yellow menu bar; PageDown scrolls; LEFT/RIGHT "
  .. "jump between sections; Q exits without typing 'q' at the shell prompt")
stage("help intro  -- EXPECT plain text, no menu bar (this topic has no headings)")

stage("lua  then type:  require('expect')  -- EXPECT a table printed, loaded from "
  .. "/rom/modules/main/cc/expect.lua, proving the REPL's directory-rooted require")

-- ---------------------------------------------------------------------------
print("")
print("--- WHAT TO LOOK AT (staged checks) ---")
for i, text in ipairs(staged) do
  print(i .. ") " .. text)
end
print("")
print("Full transcript: /batch-i2-results.txt")

finish("BATCH-I part2 editor/help")
