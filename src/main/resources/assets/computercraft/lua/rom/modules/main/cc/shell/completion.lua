-- SPDX-FileCopyrightText: 2020 The CC: Tweaked Developers
--
-- SPDX-License-Identifier: MPL-2.0

--- A collection of helper methods for shell completion, suitable for
-- registering with [`shell.setCompletionFunction`].
--
-- Unlike the lower-level [`cc.completion`] helpers (which work with
-- [`_G.read`]), these functions follow the
-- `function(shell, index, text, previous)` signature expected by
-- [`shell.setCompletionFunction`] and delegate to [`fs.complete`] so paths are
-- always resolved relative to the current working directory.
--
-- ## Example
--
-- Register completions for a hypothetical `copy` command whose first argument
-- is a source file and whose second argument is a destination directory:
--
-- ```lua
-- local sc = require "cc.shell.completion"
-- shell.setCompletionFunction("copy", function(shell, index, text, previous)
--     if index == 1 then return sc.file(shell, index, text, previous)
--     elseif index == 2 then return sc.dir(shell, index, text, previous)
--     end
-- end)
-- ```
--
-- @module cc.shell.completion
-- @see cc.completion For additional helpers to use with [`_G.read`].
-- @since 1.85.0

local expect     = require "cc.expect"
local expect     = expect.expect
local completion = require "cc.completion"

-- Capture the global `help` API before a local function of the same name is
-- declared below (in Lua, `local function f` puts `f` in scope inside its own
-- body, so without an alias `help.completeTopic` would try to index the local
-- function rather than the global API).
local _help_api = help

--- Complete the name of a file relative to the current working directory.
--
-- Directories are included in the completion list (with a trailing `"/"` so
-- the user can navigate further), but regular files are the primary target.
--
-- @tparam Shell shell  The shell we are completing in.
-- @tparam number index The argument index being completed.
-- @tparam string text  The current (partial) text to complete.
-- @tparam {string...} previous The arguments already typed.
-- @treturn {string...} A list of completion suffixes.
local function file(shell, index, text, previous)
    return fs.complete(text, shell.dir(), {
        include_files = true,
        include_dirs = false,
        include_hidden = settings.get("shell.autocomplete_hidden"),
    })
end

--- Complete the name of a directory relative to the current working directory.
--
-- Regular files are excluded. Each matching directory appears twice in the
-- completion list: once with a trailing `"/"` (for navigating into it) and
-- once without (so it can be accepted as a bare directory-name argument).
--
-- @tparam Shell shell  The shell we are completing in.
-- @tparam number index The argument index being completed.
-- @tparam string text  The current (partial) text to complete.
-- @tparam {string...} previous The arguments already typed.
-- @treturn {string...} A list of completion suffixes.
local function dir(shell, index, text, previous)
    return fs.complete(text, shell.dir(), {
        include_files = false,
        include_dirs = true,
        include_hidden = settings.get("shell.autocomplete_hidden"),
    })
end

--- Complete the name of a file or directory relative to the current working
-- directory.
--
-- Both regular files and directories are included.  Each matching directory
-- appears twice (with and without a trailing `"/"`) so the user can either
-- navigate into it or use the bare name directly as an argument.
--
-- @tparam Shell shell  The shell we are completing in.
-- @tparam number index The argument index being completed.
-- @tparam string text  The current (partial) text to complete.
-- @tparam {string...} previous The arguments already typed.
-- @treturn {string...} A list of completion suffixes.
local function dirOrFile(shell, index, text, previous)
    return fs.complete(text, shell.dir(), {
        include_files = true,
        include_dirs = true,
        include_hidden = settings.get("shell.autocomplete_hidden"),
    })
end

--- Complete the name of a program on the [`shell.path`].
--
-- @tparam Shell shell  The shell we are completing in.
-- @tparam number index The argument index being completed.
-- @tparam string text  The current (partial) text to complete.
-- @tparam {string...} previous The arguments already typed.
-- @treturn {string...}|nil A list of completions, or `nil` if `index ~= 1`.
local function program(shell, index, text, previous)
    if index == 1 then
        return shell.completeProgram(text)
    end
end

--- Complete the name of a program and its arguments.
--
-- On the first argument (`index == 1`) this behaves identically to [`program`].
-- On subsequent arguments it resolves the first argument as a program name,
-- looks up any completion function registered for that program via
-- [`shell.getCompletionInfo`], and delegates to it with a shifted argument
-- index and previous-arguments table so the sub-program's completion function
-- sees its own name at `previous[1]`.
--
-- @tparam Shell shell  The shell we are completing in.
-- @tparam number index The argument index being completed.
-- @tparam string text  The current (partial) text to complete.
-- @tparam {string...} previous The arguments already typed.
-- @treturn {string...}|nil A list of completions.
local function programWithArgs(shell, index, text, previous)
    if index == 1 then
        return shell.completeProgram(text)
    else
        -- previous[1] = the wrapper program; previous[2] = the nested program name.
        local program_name = previous[2]
        if not program_name then return nil end
        local resolved = shell.resolveProgram(program_name)
        if not resolved then return nil end
        local tInfo = shell.getCompletionInfo()
        local info = tInfo[resolved]
        if not info then return nil end
        -- Build a shifted previous table so the sub-program sees its own name
        -- at [1] (matching what the shell normally passes to completion functions).
        local tPrevious = {}
        for i = 2, #previous do
            tPrevious[i - 1] = previous[i]
        end
        return info.fnComplete(shell, index - 1, text, tPrevious)
    end
end

--- Complete the name of a help topic.
--
-- @tparam Shell shell  The shell we are completing in.
-- @tparam number index The argument index being completed.
-- @tparam string text  The current (partial) text to complete.
-- @tparam {string...} previous The arguments already typed.
-- @treturn {string...}|nil A list of completions, or `nil` if `index ~= 1`.
local function help(shell, index, text, previous)
    if index == 1 then
        return _help_api.completeTopic(text)
    end
end

--- Complete from a list of choices.
--
-- Returns a completion function that, when called, completes `text` against
-- the given list of `choices`.  The returned function is suitable for direct
-- use or as a spec inside [`build`].
--
-- @tparam {string...} choices The list of choices to complete from.
-- @tparam[opt] boolean add_space Whether to add a trailing space after each
-- completed item.  Defaults to `false`.
-- @treturn function(shell: Shell, index: number, text: string, previous: {string...}): {string...}
local function choice(choices, add_space)
    expect(1, choices, "table")
    expect(2, add_space, "boolean", "nil")
    return function(shell, index, text, previous)
        return completion.choice(text, choices, add_space)
    end
end

--- Complete the name of a currently attached peripheral.
--
-- @tparam Shell shell  The shell we are completing in.
-- @tparam number index The argument index being completed.
-- @tparam string text  The current (partial) text to complete.
-- @tparam {string...} previous The arguments already typed.
-- @treturn {string...} A list of completions.
local function peripheral(shell, index, text, previous)
    return completion.peripheral(text)
end

--- Complete the side of a computer.
--
-- @tparam Shell shell  The shell we are completing in.
-- @tparam number index The argument index being completed.
-- @tparam string text  The current (partial) text to complete.
-- @tparam {string...} previous The arguments already typed.
-- @treturn {string...} A list of completions.
local function side(shell, index, text, previous)
    return completion.side(text)
end

--- Complete the name of a [`settings`] key.
--
-- @tparam Shell shell  The shell we are completing in.
-- @tparam number index The argument index being completed.
-- @tparam string text  The current (partial) text to complete.
-- @tparam {string...} previous The arguments already typed.
-- @treturn {string...} A list of completions.
local function setting(shell, index, text, previous)
    return completion.setting(text)
end

--- Complete the name of a Minecraft command.
--
-- @tparam Shell shell  The shell we are completing in.
-- @tparam number index The argument index being completed.
-- @tparam string text  The current (partial) text to complete.
-- @tparam {string...} previous The arguments already typed.
-- @treturn {string...} A list of completions.
local function command(shell, index, text, previous)
    return completion.command(text)
end

--- Build a tab-completion function from a list of per-argument specs.
--
-- Each positional argument to `build` describes how to complete the
-- correspondingly-indexed argument of a shell command:
--
-- - `nil` — no completion for this argument.
-- - A function — used directly as a completion function, called with
--   `(shell, 1, text, previous)`.
-- - A table `{ fn }` — `fn` is used directly (same as the bare-function case).
-- - A table `{ factory, arg1, … }` — `factory(arg1, …)` is called **once** at
--   build time to obtain a completion function (e.g. `{ choice, {"yes","no"} }`
--   calls `choice({"yes","no"})` and stores the result).
--
-- Specs are resolved to completion functions once when `build` is called, not
-- on every keypress.  The index is always normalised to `1` when the stored
-- completion function is invoked so that the standard single-argument helpers
-- (which guard with `if index == 1`) work correctly.
--
-- @param ... [Spec...] Per-argument completion specs.
-- @treturn function The assembled completion function.
-- @usage Register completions for a hypothetical `example` command.
--
--     local sc = require "cc.shell.completion"
--     shell.setCompletionFunction("example",
--         sc.build(
--             { sc.choice, { "yes", "no", "maybe" } },
--             sc.file,
--             sc.dir
--         )
--     )
local function build(...)
    local arguments = table.pack(...)
    local completers = {}
    for i = 1, arguments.n do
        local arg = arguments[i]
        if arg ~= nil then
            if type(arg) == "function" then
                arg = { arg }
                arguments[i] = arg
            end

            if type(arg) ~= "table" or type(arg[1]) ~= "function" then
                error(("Bad table entry #1 at argument #%d (function expected, got %s)"):format(i, type(arg)), 2)
            end

            -- "many" means this completer keeps being used for every argument after it, so it
            -- only makes sense on the last spec.
            if arg.many and i < arguments.n then
                error(("Unexpected 'many' field on argument #%d (should only occur on the last argument)"):format(i), 2)
            end

            if #arg > 1 then
                -- Factory form: { cc.completion.choice, { "yes", "no" } }. The extra entries are
                -- applied once, at build time, and the returned function is the completer.
                local tArgs = {}
                for j = 2, #arg do tArgs[j - 1] = arg[j] end
                completers[i] = arg[1](table.unpack(tArgs))
            else
                completers[i] = arg[1]
            end
        end
    end

    local function isMany(index)
        local arg = arguments[index]
        return arg ~= nil and arg.many == true
    end

    return function(shell, index, text, previous)
        local completer = completers[index]
        if not completer then
            if index <= arguments.n then return end

            -- Past the last declared argument: only continue if that one was marked "many".
            completer, index = completers[arguments.n], arguments.n
            if not completer or not isMany(index) then return end
        end

        -- Our completers take the argument index as their second parameter (upstream drops it),
        -- so it is normalised to 1: each spec describes exactly one argument.
        return completer(shell, 1, text, previous)
    end
end

return {
    file            = file,
    dir             = dir,
    dirOrFile       = dirOrFile,
    program         = program,
    programWithArgs = programWithArgs,
    help            = help,
    choice          = choice,
    peripheral      = peripheral,
    side            = side,
    setting         = setting,
    command         = command,
    build           = build,
}

