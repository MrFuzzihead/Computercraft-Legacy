-- Probe: which key codes does this platform actually deliver?
--
-- Named *_manual so the headless harness skips it: it waits for real key presses.
--
-- Run it, then press each key it asks for. It prints the code the game delivered next to the code
-- the ROM's `keys` table expects, which is the whole question: if they differ, every program that
-- compares `param == keys.pageUp` can never match, no matter what it does.

term.clear()
term.setCursorPos(1, 1)

local probes = { "pageUp", "pageDown", "up", "down", "home", "end", "insert" }

print("ROM says:")
for _, name in ipairs(probes) do
  print(("  keys.%-9s = %s"):format(name, tostring(keys[name])))
end
print("")

-- The reverse map, so a delivered code can be named.
local names = {}
for name, code in pairs(keys) do
  if type(code) == "number" and names[code] == nil then names[code] = name end
end

print("Now: press PageUp, PageDown, Up, Down, Home, End in any order.")
print("Press Ctrl+T when you are done.")
print("")

local seen = {}
while true do
  local event = { os.pullEventRaw() }
  local name = event[1]

  if name == "key" then
    local code, repeat_ = event[2], event[3]
    local known = names[code] or "?"
    print(("key   %d  -> keys.%s%s"):format(code, known, repeat_ and " (repeat)" or ""))
    seen[code] = (seen[code] or 0) + 1
  elseif name == "char" then
    -- Ignore: a key press also produces one of these, and printing it doubles the noise.
  elseif name == "mouse_scroll" then
    print("mouse_scroll " .. tostring(event[2]))
  elseif name == "terminate" then
    break
  end
end

print("")
print("Summary")
for _, probe in ipairs(probes) do
  local code = keys[probe]
  local hit = code and seen[code]
  print(("  %-9s expected %-4s %s"):format(probe, tostring(code), hit and ("SEEN x" .. hit) or "not seen"))
end

print("")
print("If PageUp/PageDown arrived under a different code, that is the bug: the ROM's `keys` table")
print("uses the LWJGL2 codes (201/209), and a GLFW-based layer reports 266/267 instead.")
print("Either way, note which codes appeared for the keys that did work.")
