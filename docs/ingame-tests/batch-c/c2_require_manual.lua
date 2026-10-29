-- Batch C: the parts that need a reboot or a real keyboard. CI skips this file (its name
-- contains "manual").
--
-- Two-phase, because /startup runs at boot:
--   c2_require_manual        -- creates /startup/01_first and /startup/02_second, then asks for a
--                               reboot
--   ... reboot ...
--   c2_require_manual        -- checks both ran, in sorted order, then cleans up
--
-- The Tab-completion drill at the end is for a human; the scripted checks above it use the same
-- completion code paths the shell does.

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
local function finish(name)
  local lines = { "RESULT " .. name }
  lines[#lines + 1] = "  passed " .. P .. "   failed " .. F .. "   skipped " .. S
  for _, f in ipairs(fails) do lines[#lines + 1] = "  FAIL " .. f end
  for _, k in ipairs(skips) do lines[#lines + 1] = "  SKIP " .. k end

  print("")
  for _, line in ipairs(lines) do print(line) end

  local h = fs.open("/batch-c-results.txt", "a")
  if h then
    for _, line in ipairs(lines) do h.write(line .. "\n") end
    h.close()
  end

  print("")
  print("RESULT " .. name .. ": passed " .. P .. "   failed " .. F .. "   skipped " .. S)
end

local PHASE_FILE = "/.batch-c-startup-phase"
local ORDER_LOG = "/startup-order.log"

local phase = read and nil
if fs.exists(PHASE_FILE) then
  local h = fs.open(PHASE_FILE, "r")
  phase = h.readAll()
  h.close()
end

-- ---------------------------------------------------------------------------
if phase ~= "second" then
  print("== phase 1: creating /startup programs ==")

  if fs.exists("/startup") and not fs.isDir("/startup") then
    skip("a /startup file already exists; move it aside to test the directory form")
    finish("BATCH-C startup (manual)")
    return
  end
  if not fs.isDir("/startup") then
    fs.makeDir("/startup")
  end

  local function writeStartup(name, text)
    local h = fs.open("/startup/" .. name, "w")
    h.write(text)
    h.close()
  end

  writeStartup(
    "01_first",
    "local h = fs.open('" .. ORDER_LOG .. "', 'w') h.write('first\\n') h.close()\n"
  )
  writeStartup(
    "02_second",
    "local h = fs.open('" .. ORDER_LOG .. "', 'a') h.write('second\\n') h.close()\n"
  )

  local h = fs.open(PHASE_FILE, "w")
  h.write("second")
  h.close()

  ok("/startup directory created", fs.isDir("/startup"))
  eq("both programs written", 2, #fs.list("/startup"))

  print("")
  print("NOW REBOOT (Ctrl+R or 'reboot') and run c2_require_manual again.")
  finish("BATCH-C startup (manual, phase 1)")
  return
end

-- ---------------------------------------------------------------------------
print("== phase 2: checking the startup programs ran ==")

local log = ""
if fs.exists(ORDER_LOG) then
  local f = fs.open(ORDER_LOG, "r")
  log = f.readAll()
  f.close()
end

eq("both /startup programs ran, in sorted order", "first\nsecond\n", log)

-- shell.allow_startup must be able to switch the whole thing off.
ok("shell.allow_startup defaults to true", settings.get("shell.allow_startup") == true)

-- ---------------------------------------------------------------------------
print("== phase 3: scripted completion checks ==")

local comp = require("cc.shell.completion")
local mockShell = { dir = function() return "/" end }

local function writeFile(path, contents)
  local h = fs.open(path, "w")
  h.write(contents)
  h.close()
end

if not fs.isDir("/c2_files") then fs.makeDir("/c2_files") end
writeFile("/c2_files/plain", "x")
writeFile("/c2_files/.secret", "x")

local function contains(list, value)
  for _, v in ipairs(list or {}) do
    if v == value then return true end
  end
  return false
end

mockShell.dir = function() return "/c2_files" end

settings.set("shell.autocomplete_hidden", false)
ok("hidden entries are hidden when the setting is off", not contains(comp.file(mockShell, 1, "", {}), ".secret"))
settings.set("shell.autocomplete_hidden", true)
ok("hidden entries appear when the setting is on", contains(comp.file(mockShell, 1, "", {}), ".secret"))
settings.unset("shell.autocomplete_hidden")

-- ---------------------------------------------------------------------------
print("== phase 4: cleanup ==")

for _, name in ipairs(fs.list("/startup")) do
  fs.delete(fs.combine("/startup", name))
end
fs.delete("/startup")
if fs.exists(ORDER_LOG) then fs.delete(ORDER_LOG) end
fs.delete(PHASE_FILE)
for _, name in ipairs(fs.list("/c2_files")) do fs.delete(fs.combine("/c2_files", name)) end
fs.delete("/c2_files")

ok("/startup directory removed", not fs.exists("/startup"))

finish("BATCH-C startup (manual)")

print("")
print("--- TAB COMPLETION DRILL ---")
print("1. Run:  mkdir /drill")
print("2. Run:  fs.open('/drill/.hidden','w').close()  and  fs.open('/drill/plain','w').close()")
print("3. Type: /drill/    then TAB")
print("   EXPECT: only 'plain' appears; hidden files are now excluded by default.")
print("4. Run:  settings.set('shell.autocomplete_hidden', true)")
print("5. Type: /drill/    then TAB again")
print("   EXPECT: '.hidden' now appears as well.")
print("6. Run:  settings.unset('shell.autocomplete_hidden')  and:  rm /drill")
print("")
print("Also worth a look: a program with a syntax error, e.g.")
print("  echo 'if true then' > /broken ; /broken")
print("EXPECT: the parser points at the missing 'end' with a code frame, not a bare message.")
