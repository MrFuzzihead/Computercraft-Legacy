# Item 7 — `cc_default_settings` (server config)

This is the only Batch A change that cannot be driven from a computer: the value lives
in `config/ComputerCraft.cfg` and is injected as the `_CC_DEFAULT_SETTINGS` Lua global by
`CobaltMachine` at computer start-up. It needs a server restart, then a **new** computer
(existing computers re-read nothing — the parse happens in `bios.lua` at boot).

## What changed

Before Batch A the whole value was handed to `textutils.unserialize`. After:

| Input | Before | After |
|---|---|---|
| `shell.autocomplete=false` | `false` | `false` |
| `cc_test.number=7` | `7` | `7` |
| `edit.default_extension=txt` | `"txt"` | `"txt"` |
| `bios.strict_globals=nil` | **the string `"nil"`** | **unset** (registered default applies) |
| `cc_test.table={a=1}` | table | table (local extension, kept) |
| `motd.path={1,2,3}` on a string setting | raised → BIOS error | dropped, default applies, computer boots |

The last two rows are the interesting ones.

## Procedure

1. Stop the server.
2. Edit `config/ComputerCraft.cfg`, find `cc_default_settings`, and set:

   ```
   cc_default_settings=shell.autocomplete=false,edit.default_extension=txt,cc_test.number=7
   ```

3. Start the server and place a **new** computer.
4. On it, run:

   ```lua
   print(type(settings.get("shell.autocomplete")), settings.get("shell.autocomplete"))
   print(type(settings.get("edit.default_extension")), settings.get("edit.default_extension"))
   print(type(settings.get("cc_test.number")), settings.get("cc_test.number"))
   ```

   Expected:

   ```
   boolean false
   string  txt
   number  7
   ```

   **Failure mode to watch for:** `string false`. That means the value was stored raw.

5. Check `nil` unsets instead of storing `"nil"`:

   ```
   cc_default_settings=bios.strict_globals=nil
   ```

   Restart, place a new computer, and run:

   ```lua
   print(type(settings.get("bios.strict_globals")), settings.get("bios.strict_globals"))
   ```

   Expected `boolean false` (the registered default). Getting `string nil` is the bug
   this change fixed.

6. Check the local table extension still works, with a comma-free literal (entries are
   split on `,` before parsing, so `{a=1,b=2}` was never supported):

   ```
   cc_default_settings=cc_test.table={a=1}
   ```

   On a new computer:

   ```lua
   local t = settings.get("cc_test.table")
   print(type(t), t and t.a)
   ```

   Expected `table	1`.

7. Check a type-mismatched default cannot brick the boot:

   ```
   cc_default_settings=motd.path=true
   ```

   Restart and place a computer. It must **start normally**, with
   `print(settings.get("motd.path"))` still showing `/rom/motd.txt:/motd.txt`.
   A computer that hangs on "Press any key to continue" or shows a Lua error in the
   boot path is the failure this `pcall` guard exists to prevent.

8. Tidy up: restore `cc_default_settings=` (empty), restart, and remove the throwaway
   computers.

## Also worth one look

`_CC_DEFAULT_SETTINGS` is applied *before* `settings.load(".settings")`, so a persisted
`.settings` file wins over the config default. That is upstream's order. If you want to
confirm it, set `edit.default_extension=txt` in the config, then on a computer run
`settings.set("edit.default_extension","nfp"); settings.save()`, reboot, and check that
`nfp` (the saved value) wins, not `txt`.
