# Batch C — in-game validation pack

`cc.require` / per-program environments, hashbangs, modern error reporting, completion and
`/startup` directories.

## Install

```bat
copy docs\ingame-tests\batch-c\*.lua "run\client\saves\<World>\computer\<id>\"
```

| File | What it proves | Needs |
|---|---|---|
| `c1_require.lua` | `require` rooted at the program's directory (flat, nested, cached, isolated per run), missing-module errors, hashbangs (interpreter, arguments, `arg[0]`, body not executed, missing interpreter, shell guard), `shell.programs()` `.lua` stripping, error frames, and `completion` (`many` + `shell.autocomplete_hidden`). Also asserts an errored program's file can still be deleted. | — |
| `c2_require_manual.lua` | `/startup` **directory** (two files, sorted order) across a reboot, `shell.allow_startup`, and scripted hidden-completion checks. Ends with a Tab drill. | Two runs, one reboot |

Both append to `/batch-c-results.txt`; `type /batch-c-results.txt` shows every run. `c1_require`
also runs in CI through the auto-discovering `IngameTestScriptsTest`, so a broken assertion fails
the build.

## Still needs a human

1. **`/startup` across a reboot.** Run `c2_require_manual`, reboot when asked, run it again. It
   creates `/startup/01_first` and `/startup/02_second`, checks they ran in order, then removes them.
2. **Tab completion, hidden files.** In the shell: `mkdir /drill`, create `/drill/.hidden` and
   `/drill/plain`, then type `/drill/` + Tab — only `plain` should appear. Then
   `settings.set("shell.autocomplete_hidden", true)` and Tab again; `.hidden` should appear.
   Finish with `settings.unset(...)` and `rm /drill`.
3. **A syntax error's report.** `echo 'if true then' > /broken` then `/broken`. Expect the parser
   to point at the missing `end` with a code frame and an "Expected ..." message, not a bare
   `'end' expected` line.
4. **A runtime error's report.** Any program that raises should print the offending source line
   with a caret under the failing column. Worth checking on a monitor too, where the width differs.

## Fixed because of this pack

`cc.internal.exception`'s `report()` opens the failing program to echo the offending line and
**never closed the handle** (upstream does the same). Symptom: after a reported error the program's
file could not be deleted ("Access denied"), and every reported error leaked one of the computer's
file handles. `c1_require` now asserts the deletion works, and the port closes the handle.
