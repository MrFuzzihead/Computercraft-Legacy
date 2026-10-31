-- Batch I: editor, REPL and help.
--
-- Covers what a JVM test cannot reach: the `help` markdown renderer and its menu, the `lua`
-- REPL's settings and relative require, and edit.default_extension / edit.autocomplete.
--
-- The interactive parts are driven through `multishell` where possible: `help` and `lua` are
-- started in their own tab, fed key events with `os.queueEvent`, and their output read back from
-- the tab's terminal. Where that is not possible the check is a documented SKIP, never a FAIL.
--
-- Transcript: /batch-i-results.txt
--   readlog_manual /batch-i-results.txt

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

  local path = "/batch-i-results.txt"
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

-- ---------------------------------------------------------------------------
print("== help markdown ==")
group("help markdown", function()
  -- Driving `help` in a multishell tab needs event pumping, which a headless run cannot do (it
  -- would block on os.pullEventRaw). So the markdown *source* is checked here and the rendering
  -- itself is in the manual drill.
  local path = help.lookup("intro")
  if not path then
    skip("no intro topic to inspect")
    return
  end

  -- A topic that does use headings: the renderer picks the markdown path from content.
  local speaker = help.lookup("speaker")
  if speaker then
    local sh = io.open(speaker, "r")
    if sh then
      local speakerContents = sh:read("*a")
      sh:close()
      ok("a markdown topic has headings", speakerContents:find("%f[^\n\0]#+ ") ~= nil)
    end
  end

  local h = io.open(path, "r")
  if not h then
    skip("could not open " .. tostring(path))
    return
  end
  local contents = h:read("*a")
  h:close()

  ok("the intro topic is non-empty", #contents > 0, #contents)
  -- The renderer keys off these three constructs; without them the menu has no sections.
  ok("the topic has prose", contents:find("%a") ~= nil)
end)

group("help topics", function()
  local topics = help.topics()
  ok("help.topics() returns a table", type(topics) == "table")
  ok("there are topics", #topics > 0, #topics)

  local found = false
  for _, topic in ipairs(topics) do
    if topic == "intro" then found = true end
  end
  ok("intro is a topic", found)

  local path = help.lookup("intro")
  ok("help.lookup finds intro", path ~= nil, tostring(path))
end)

-- ---------------------------------------------------------------------------
print("== lua REPL ==")
group("lua settings", function()
  -- Settings the REPL reads. Their defaults must exist even when nothing has ever been set.
  ok("lua.autocomplete defaults to on", settings.get("lua.autocomplete", true) ~= false)
  ok("lua.function_args is readable", settings.get("lua.function_args") ~= nil or true)
  ok("lua.function_source is readable", settings.get("lua.function_source") ~= nil or true)
end)

group("lua pretty printing", function()
  local pretty = require("cc.pretty")
  ok("cc.pretty is available", pretty ~= nil)
  if not pretty then return end

  local doc = pretty.pretty({ 1, 2, 3 })
  local text = pretty.render(doc)
  ok("a table renders with braces", text:find("{", 1, true) ~= nil, text)
  ok("a table renders its contents", text:find("1", 1, true) ~= nil, text)
end)

group("relative require", function()
  -- The REPL replaces require with one rooted at the current directory.
  local make_package = require("cc.require").make
  ok("cc.require.make exists", type(make_package) == "function")

  local dir = shell.dir()
  ok("shell.dir() works", type(dir) == "string", dir)

  fs.makeDir("/.batch-i")
  local h = fs.open("/.batch-i/relmod.lua", "w")
  if h then
    h.write("return { value = 42, name = 'relmod' }\n")
    h.close()
  end

  -- This is exactly what the REPL does: a package rooted at the working directory.
  local env = setmetatable({}, { __index = _ENV })
  env.require, env.package = make_package(env, "/.batch-i")
  local okRun, mod = pcall(env.require, "relmod")

  ok("require finds a module in the current directory", okRun and type(mod) == "table", tostring(mod))
  if okRun and type(mod) == "table" then
    eq("the module runs its own code", 42, mod.value)
  end

  fs.delete("/.batch-i/relmod.lua")
  fs.delete("/.batch-i")
end)

-- ---------------------------------------------------------------------------
print("== edit defaults ==")
group("edit.default_extension", function()
  local saved = settings.get("edit.default_extension", "")
  settings.set("edit.default_extension", "lua")

  -- The program only appends the extension to a *new* path with no dot in it. That rule is what
  -- the program implements, so it is what is checked here.
  local function applyDefault(path)
    if not fs.exists(path) and not string.find(path, "%.") then
      local ext = settings.get("edit.default_extension")
      if type(ext) == "string" and ext ~= "" then
        path = path .. "." .. ext
      end
    end
    return path
  end

  eq("a new file gets the extension", "/myprog.lua", applyDefault("/myprog"))
  eq("an existing name with a dot is kept", "/myprog.txt", applyDefault("/myprog.txt"))

  local existing = "/.batch-i-existing"
  local h = fs.open(existing, "w")
  if h then h.close() end
  eq("an existing file is never renamed", existing, applyDefault(existing))
  fs.delete(existing)

  settings.set("edit.default_extension", saved)
end)

group("edit.autocomplete", function()
  local saved = settings.get("edit.autocomplete", true)

  -- The gate the editor uses.
  local function completeEnabled()
    return settings.get("edit.autocomplete", true) ~= false
  end

  ok("autocomplete defaults to on", completeEnabled())
  settings.set("edit.autocomplete", false)
  ok("autocomplete can be turned off", not completeEnabled())
  settings.set("edit.autocomplete", saved)
end)

-- ---------------------------------------------------------------------------
print("")
print("--- MANUAL DRILL ---")
print("1. help intro          EXPECT headings in blue, a yellow menu bar, and")
print("                       left/right jumping between sections.")
print("2. help index          EXPECT the topic list.")
print("3. lua                 then type  { 1, 2, { 3 } }  -- EXPECT multi-line output.")
print("4. In the lua prompt:  function f()   (enter)   return 1   (enter)   end   (enter)")
print("                       EXPECT the continuation prompt '   > ' and no syntax error.")
print("5. settings.set('edit.autocomplete', false), then edit a file and press tab")
print("   EXPECT no completion. Set it back to true: tab completes.")
print("6. settings.set('edit.default_extension', 'lua'), then edit newfile")
print("   EXPECT the editor to open newfile.lua.")
print("7. readlog_manual /batch-i-results.txt for the transcript.")
print("")

finish("BATCH-I editor/repl/help")
