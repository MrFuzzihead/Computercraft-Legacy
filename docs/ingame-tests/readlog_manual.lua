-- Read an in-game test results file one screen at a time.
--
-- CraftOS terminals have no scrollback, so `type <file>` on a long log scrolls past faster than
-- it can be read. This pages it, waiting for a key between screens. It is also the easiest way to
-- send me the *full* transcript of a run: read it here and copy down what matters, or open it in
-- `edit` (which scrolls with the arrow keys).
--
-- Usage:
--   readlog_manual                  -- the default log, /batch-f-results.txt
--   readlog_manual /batch-d-results.txt
--
-- Deliberately named *_manual so the CI runner (which executes every other script under
-- docs/ingame-tests) skips it: it waits for keypresses.

local tArgs = { ... }
local path = tArgs[1] or "/batch-f-results.txt"

if not fs.exists(path) then
    printError("No such file: " .. path)
    print("")
    print("Results files are written next to the computer's root:")
    for _, name in ipairs(fs.list("")) do
        if name:match("%-results%.txt$") then print("  /" .. name) end
    end
    return
end

local handle = fs.open(path, "r")
local contents = handle.readAll()
handle.close()

term.clear()
term.setCursorPos(1, 1)
print("--- " .. path .. " ---")

local _, height = term.getSize()
-- Leave room for the "Press any key" line that pagedPrint draws at the bottom.
textutils.pagedPrint(contents, math.max(height - 3, 1))

print("")
print("End of file. `edit " .. path .. "` scrolls with the arrow keys.")
