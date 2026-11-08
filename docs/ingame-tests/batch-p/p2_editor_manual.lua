-- Batch P: manual drill for the parts no script can check.
--
-- The CI runner skips files whose name contains "manual", because this one only sets up fixtures
-- and prints a checklist: the checks themselves need a real mouse, a real keyboard and human eyes.
--
-- Run p1_rom_parity first; it covers everything that can be asserted without a human.

local function step(n, text)
  print(n .. ". " .. text)
end

-- Fixtures the drill refers to.
local function touch(path, contents)
  local h = fs.open(path, "w")
  if h then
    if contents then h.write(contents) end
    h.close()
  end
end

touch("/batchp-editor.txt", "line one\nline two\n")
touch("/batchp-paint.txt", "")

if term.isColour() then
  term.setTextColour(colours.yellow)
end
print("BATCH-P manual drill")
print("Fixtures: /batchp-editor.txt and /batchp-paint.txt")
if term.isColour() then
  term.setTextColour(colours.white)
end
print("")

print("== editor ==")
step(1, "edit /batchp-editor.txt, go to the end of line 1, press TAB, type X.")
print("   EXPECT four spaces between 'one' and 'X', not two and not one.")
step(2, "With the cursor inside those four spaces, press BACKSPACE once.")
print("   EXPECT all four spaces to disappear in one press.")
step(3, "Press the NUMPAD Enter (keypad, not the main Return).")
print("   EXPECT a new line. On a client where numpad keys never arrive this does nothing:")
print("   check Options > Controls first, as was needed for PageUp/PageDown in batch I.")
step(4, "Press Ctrl, then LEFT/RIGHT.")
print("   EXPECT the menu bar to move between [Save] [Run] [Exit], and Ctrl again to close it.")
step(5, "Click the bottom line of the screen with the mouse.")
print("   EXPECT the menu to open. Click 'Save' and EXPECT 'Saved to /batchp-editor.txt'.")
step(6, "Type a few characters and press Ctrl then ESC-like cancel (Ctrl again).")
print("   EXPECT the characters to be kept and the menu to close without saving.")
step(7, "edit /rom/startup  (a read-only file)")
print("   EXPECT the footer to read 'File is read only' in RED, and no Save item in the menu.")
step(8, "Fill the computer's disk (copy a big file until 'Disk is low on space' appears).")
print("   EXPECT that message in the footer when opening a file.")

print("")
print("== paint ==")
step(9, "paint /batchp-picture  (no extension)")
print("   EXPECT the file to be saved as /batchp-picture.nfp, not /batchp-picture.")
step(10, "Click the bottom line of the screen.")
print("   EXPECT the menu to open, exactly as in the editor. Pick Save with the mouse.")
step(11, "Draw a pixel, Ctrl, S.")
print("   EXPECT 'Saved to ...' and the pixel to survive reopening the file.")
step(12, "paint /rom/startup then Ctrl, S.")
print("   EXPECT 'Access denied'.")

print("")
print("== shell and tabs ==")
step(13, "bg hello.lua")
print("   EXPECT the new tab to be titled 'hello', not 'hello.lua'.")
step(14, "settings.set('bios.use_multishell', false)  then reboot")
print("   EXPECT the plain shell: no tab bar, and 'bg'/'fg' report No such program.")
step(15, "settings.set('bios.use_multishell', true)   then reboot")
print("   EXPECT the tab bar back.")
step(16, "lua, then type: local x = 1")
print("   EXPECT a YELLOW warning: 'To access local variables in later inputs, remove the local keyword.'")
step(17, "settings.set('lua.warn_against_use_of_local', false), reboot, repeat 16.")
print("   EXPECT no warning.")

print("")
print("== small programs ==")
step(18, "id  with a music disc in a drive")
print("   EXPECT 'Has audio track \"...\"', not 'No disk in drive'.")
step(19, "id  with a data disk in a drive")
print("   EXPECT 'The disk is #N' and, if labelled, 'Labelled \"...\"'.")
step(20, "rename /disk /batchp-x   (with a floppy drive mounted as /disk)")
print("   EXPECT 'Can't rename mounts', and the drive to remain intact.")
step(21, "copy   (no arguments)")
print("   EXPECT 'Usage: copy <source> <destination>', not a rom/programs path.")

print("")
print("Anything unexpected: compare with docs/ROM_PARITY_GAPS.md.")
