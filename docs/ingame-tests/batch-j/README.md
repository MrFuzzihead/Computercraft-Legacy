# Batch J — file transfer / import

`j1_transfer.lua` is self-checking and runs headlessly (the CI runner executes it on a fresh
computer). It covers the half that does not need a human: the imported ROM files exist, the import
module writes what it is handed, and the shell still runs its prompt in a coroutine.

| Check | What it proves |
|---|---|
| the shell waits for events at its prompt | `rom/programs/shell` still runs `read` in a coroutine, so a transfer can be handled mid-prompt |
| the shell handles `file_transfer` | the prompt's transfer branch was not lost in a later edit |
| the shell loads the import module in a shell env | the prompt uses the shell's own `require`; the BIOS-level one leaves `shell` nil inside the module |
| the import module accepts a transfer | `cc.internal.import` writes a stand-in file and the contents match |
| the import program / module are installed | both ROM files shipped |

## What needs a human (do this on a lwjgl3ify client)

Everything else about this batch happens in the client: the drop itself cannot be faked from Lua.

1. Put a computer down, right-click it, and stay in its terminal.
2. Drag a text file from your file manager onto the game window, still over the terminal.
3. Expected, with the shell prompt open (this is the case that failed first: the prompt used to
   load the import module with the BIOS-level `require`, so it died with
   `attempt to index global 'shell' (a nil value)`):
   - the prompt is abandoned, `Transferring <name>` prints, and the prompt is redrawn below it;
   - the file is in `ls`, and `type <name>` or `edit <name>` shows the original contents;
   - `drop.txt` from a nested folder arrives as just `drop.txt`.
4. Run `import`, then drop another file: it should be written without the prompt being involved, and
   pressing `q` should return to the shell.
5. Drop a file with the *same name* as an existing one while at the prompt: it must ask
   `Overwrite? (yes/no)`. Answer `no` — the old file must be untouched.
6. Drop a directory: it must be skipped silently, not rejected.
7. Drop something larger than `upload_size_limit` (512 KiB by default): the game log must say the
   upload was refused, and no partial file may appear.
8. Close the terminal, then drop a file over the desktop: nothing should happen at all (no computer
   is open, so it is not a drop target).
9. lwjgl3ify is now a **required** dependency (`required-after:lwjgl3ify`), so removing it should
   make Forge refuse to load the mod with a dependency error, not crash halfway through startup. If
   SDL itself is broken on an otherwise valid install, the log should contain exactly one
   `Could not install the file drop handler` error and the rest of the mod must work normally.

## Notes

- The drop coordinates are deliberately ignored: any drop over the open computer screen counts, which
  is what modern ComputerCraft does too.
- `upload_size_limit = 0` in `config/ComputerCraft.cfg` disables uploads entirely.
