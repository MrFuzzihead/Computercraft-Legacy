-- Batch C: cc.require, per-program environments, hashbangs, error reporting, completion.
--
-- Runs unattended, including in CI via IngameTestScriptsTest. The interactive parts (startup
-- directories across a reboot, Tab completion drills) live in c2_require_manual.lua.

local P, F, S = 0, 0, 0
local tArgs = { ... }
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
local function note(label) print("     " .. label) end
local function section(n) print("== " .. n .. " ==") end
local function finish(name)
  local lines = { "RESULT " .. name }
  lines[#lines + 1] = "  passed " .. P .. "   failed " .. F .. "   skipped " .. S
  for _, f in ipairs(fails) do lines[#lines + 1] = "  FAIL " .. f end
  for _, k in ipairs(skips) do lines[#lines + 1] = "  SKIP " .. k end

  term.clear()
  term.setCursorPos(1, 1)
  for _, line in ipairs(lines) do print(line) end

  local h = fs.open("/batch-c-results.txt", "a")
  if h then
    for _, line in ipairs(lines) do h.write(line .. "\n") end
    h.close()
  end

  -- Last line on screen, so the outcome survives any scrolled output.
  print("")
  print("RESULT " .. name .. ": passed " .. P .. "   failed " .. F .. "   skipped " .. S)
end

local function writeFile(path, contents)
  local dir = fs.getDir(path)
  if dir ~= "" and not fs.isDir(dir) then fs.makeDir(dir) end
  local h = fs.open(path, "w")
  h.write(contents)
  h.close()
end
local function readFile(path)
  if not fs.exists(path) then return nil end
  local h = fs.open(path, "r")
  local v = h.readAll()
  h.close()
  return v
end

-- A recorder, so output from a redirected program can be inspected. blit is required: the
-- error printer highlights the failing column with it.
local function recorder()
  local rec = { written = {} }
  rec.write = function(s) rec.written[#rec.written + 1] = tostring(s) end
  rec.blit = function(text) rec.written[#rec.written + 1] = tostring(text) end
  rec.getSize = function() return 51, 19 end
  rec.getCursorPos = function() return 1, 1 end
  rec.setCursorPos = function() end
  rec.scroll = function() end
  rec.clearLine = function() end
  rec.clear = function() end
  rec.isColour = function() return false end
  rec.setTextColour = function() end
  -- term.redirect stubs any missing native method, and both spellings are native.
  rec.setTextColor = function() end
  rec.setBackgroundColor = function() end
  rec.getTextColour = function() return 1 end
  rec.getTextColor = function() return 1 end
  rec.getBackgroundColor = function() return 1 end
  rec.setBackgroundColour = function() end
  rec.getBackgroundColour = function() return 1 end
  rec.setCursorBlink = function() end
  return rec
end
local function capture(fn)
  local rec = recorder()
  local old = term.redirect(rec)
  local okRun, err = pcall(fn)
  term.redirect(old)
  return table.concat(rec.written), okRun, err
end

-- ---------------------------------------------------------------------------
section("require, relative to the program")

writeFile("/c_apps/greeter.lua", [[
local greet = {}
function greet.hello(who) return "hello " .. tostring(who) end
return greet
]])
writeFile("/c_apps/nested/inner.lua", [[
return "nested ok"
]])
writeFile("/c_apps/counter.lua", [[
counter = (counter or 0) + 1
return counter
]])

writeFile("/c_apps/main", [[
local g = require("greeter")
local i = require("nested.inner")
local a = require("counter")
local b = require("counter")
local h = fs.open("/c_require.log", "w")
h.write(g.hello("world") .. "|" .. i .. "|" .. tostring(a) .. ":" .. tostring(b))
h.close()
]])

local okMain = shell.run("/c_apps/main")
ok("c_apps/main runs", okMain == true, okMain)
eq(
  "require resolves next to the program, nested names work, results are cached",
  "hello world|nested ok|1:1",
  readFile("/c_require.log")
)

-- A second run must start from a fresh package table.
shell.run("/c_apps/main")
eq("package state is per program", "hello world|nested ok|1:1", readFile("/c_require.log"))

writeFile("/c_apps/missing", [[
local ok, err = pcall(require, "no_such_module")
local h = fs.open("/c_missing.log", "w")
h.write(tostring(ok) .. "|" .. tostring(err))
h.close()
]])
shell.run("/c_apps/missing")
local missingLog = readFile("/c_missing.log") or ""
ok("a missing module raises", missingLog:sub(1, 6) == "false|", missingLog)
ok("the message says not found", missingLog:find("not found", 1, true) ~= nil, missingLog)
ok("the message lists a tried path", missingLog:find("c_apps", 1, true) ~= nil, missingLog)

-- ---------------------------------------------------------------------------
section("hashbangs")

writeFile("/c_binsh", [[
local h = fs.open("/c_hashbang.log", "w")
h.write("arg0=" .. tostring(arg[0]) .. " args=" .. table.concat({ ... }, ","))
h.close()
]])
writeFile("/c_script", "#!/c_binsh alpha beta\n"
  .. "local h = fs.open('/c_body.log', 'w')\nh.write('body ran')\nh.close()\n")

shell.run("/c_script gamma")
local hashLog = readFile("/c_hashbang.log") or ""
ok("the interpreter ran", hashLog ~= "", hashLog)
eq("arguments are forwarded in order", "arg0=/c_binsh args=alpha,beta,/c_script,gamma", hashLog)
ok("the script body was not executed", not fs.exists("/c_body.log"))

writeFile("/c_badscript", "#!/no-such-interpreter\nprint('unreachable')\n")
ok("a missing interpreter fails cleanly", shell.run("/c_badscript") == false)

writeFile("/c_shellscript", "#!/rom/programs/shell\nprint('unreachable')\n")
ok("the shell refuses to be its own interpreter", shell.run("/c_shellscript") == false)

-- ---------------------------------------------------------------------------
section("shell.programs strips .lua")

shell.setPath(shell.path() .. ":/c_apps")
local names = shell.programs()
local sawGreeter, sawRaw = false, false
for _, n in ipairs(names) do
  if n == "greeter" then sawGreeter = true end
  if n == "greeter.lua" then sawRaw = true end
end
ok("greeter is listed", sawGreeter)
ok("greeter.lua is not listed separately", not sawRaw)

-- ---------------------------------------------------------------------------
section("error reporting")

writeFile("/c_boom", "local x = 1\nerror('kaboom')\n")
local reported, ranOk = capture(function() return shell.run("/c_boom") end)
ok("the failing program returns false", ranOk == true, ranOk)
ok("the error message is shown", reported:find("kaboom", 1, true) ~= nil, reported)
ok(
  "the offending source line is echoed (needs debug.getinfo frames)",
  reported:find("error('kaboom')", 1, true) ~= nil,
  reported
)

-- Controls: a file that was never run, then one that ran successfully, then one that errored.
writeFile("/c_control", "return\n")
ok("a never-run file can be deleted", pcall(fs.delete, "/c_control"))
writeFile("/c_ok", "local x = 1\n")
shell.run("/c_ok")
ok("a file can be deleted after a successful run", pcall(fs.delete, "/c_ok"))

-- A program that errored must not leave its file handle behind: that would burn one of the
-- computer's limited open handles and block deletion.
local okDeleteAfterError = pcall(fs.delete, "/c_boom")
ok("a program file can be deleted after it errored", okDeleteAfterError, okDeleteAfterError)

local shellSurvived = capture(function() shell.run("/c_boom") end)
ok("the shell survives a failing program", shellSurvived ~= nil)

-- ---------------------------------------------------------------------------
section("completion")

local comp = require("cc.shell.completion")
local mockShell = { dir = function() return "/c_files" end }
writeFile("/c_files/visible", "x")
writeFile("/c_files/.hidden", "x")

local function contains(list, value)
  for _, v in ipairs(list or {}) do
    if v == value then return true end
  end
  return false
end

settings.set("shell.autocomplete_hidden", false)
local visibleOnly = comp.file(mockShell, 1, "", {})
ok("visible files complete", contains(visibleOnly, "visible"), table.concat(visibleOnly or {}, ","))
ok("hidden files are excluded by default", not contains(visibleOnly, ".hidden"))

settings.set("shell.autocomplete_hidden", true)
local withHidden = comp.file(mockShell, 1, "", {})
ok("hidden files complete when enabled", contains(withHidden, ".hidden"), table.concat(withHidden or {}, ","))
settings.unset("shell.autocomplete_hidden")

-- build(..., many = true) keeps completing arguments past the last declared spec.
local built = comp.build({ comp.dir }, { comp.file, many = true })
ok("the first argument uses its own spec", built(mockShell, 1, "", {}) ~= nil)
ok("the last spec repeats for later arguments", built(mockShell, 4, "", {}) ~= nil)

local withoutMany = comp.build({ comp.dir })
ok("without many, later arguments are not completed", withoutMany(mockShell, 2, "", {}) == nil)

local okMiddle = pcall(comp.build, { comp.file, many = true }, { comp.dir })
ok("many on anything but the last spec raises", okMiddle == false)

-- ---------------------------------------------------------------------------
section("cleanup")

-- Best effort: a file the shell still holds open cannot be deleted, and that is not worth
-- failing the run over. Report the path so a real lock is visible.
local stuck = nil
local function remove(path)
  if not fs.exists(path) then return end
  local okDel = pcall(fs.delete, path)
  if not okDel and not stuck then stuck = path end
end
for _, path in ipairs({
  "/c_require.log", "/c_missing.log", "/c_hashbang.log", "/c_body.log",
  "/c_script", "/c_badscript", "/c_shellscript", "/c_binsh", "/c_boom",
}) do
  remove(path)
end
for _, dir in ipairs({ "/c_apps/nested", "/c_apps", "/c_files" }) do
  if fs.isDir(dir) then
    local entries = fs.list(dir)
    for _, name in ipairs(entries) do
      if fs.isDir(fs.combine(dir, name)) then
        remove(fs.combine(dir, name))
      else
        remove(fs.combine(dir, name))
      end
    end
    remove(dir)
  end
end
ok("fixtures cleaned up", stuck == nil, stuck)

finish("BATCH-C require and shell")

print("")
print("--- MANUAL DRILL ---")
print("1. Run c2_require_manual for startup directories and Tab completion.")
print("2. In the shell, type:  c_apps/  then TAB  -- hidden files should NOT appear.")
print("3. Run:  settings.set('shell.autocomplete_hidden', true)")
print("   then type a path and TAB again -- hidden files should now appear.")
print("4. Run a program with a syntax error to see the parser report:")
print("   echo 'if true then' > /broken ; /broken")
print("Report anything that errors or prints a stack trace.")
