
local nativesetfenv = setfenv
local nativegetfenv = getfenv
if _VERSION == "Lua 5.1" then
    -- Install parts of the Lua 5.2 API so that programs can be written against it now
    local nativeload = load
    local nativeloadstring = loadstring
    function load( x, name, mode, env )
        if mode ~= nil and mode ~= "t" then
            error( "Binary chunk loading prohibited", 2 )
        end
        local ok, p1, p2 = pcall( function()
            if type(x) == "string" then
                local result, err = nativeloadstring( x, name )
                if result then
                    if env then
                        env._ENV = env
                        nativesetfenv( result, env )
                    end
                    return result
                else
                    return nil, err
                end
            else
                local result, err = nativeload( x, name )
                if result then
                    if env then
                        env._ENV = env
                        nativesetfenv( result, env )
                    end
                    return result
                else
                    return nil, err
                end
            end
        end )
        if ok then
            return p1, p2
        else
            error( p1, 2 )
        end
    end
    table.unpack = unpack
    table.pack = function( ... ) return { ... } end

    local nativebit = bit
    bit32 = {}
    bit32.arshift = nativebit.brshift
    bit32.band = nativebit.band
    bit32.bnot = nativebit.bnot
    bit32.bor = nativebit.bor
    bit32.btest = function( a, b ) return nativebit.band(a,b) ~= 0 end
    bit32.bxor = nativebit.bxor
    bit32.lshift = nativebit.blshift
    bit32.rshift = nativebit.blogic_rshift

    if _CC_DISABLE_LUA51_FEATURES then
        -- Remove the Lua 5.1 features that will be removed when we update to Lua 5.2, for compatibility testing.
        -- See "disable_lua51_functions" in ComputerCraft.cfg
        setfenv = nil
        getfenv = nil
        loadstring = nil
        unpack = nil
        math.log10 = nil
        table.maxn = nil
        bit = nil
    end
end

-- Install fix for luaj's broken string.sub/string.find
do
    local nativestringfind = string.find
    local nativestringsub = string.sub
    local nativepcall = pcall
    local stringCopy = {}
    for k,v in pairs(string) do
        stringCopy[k] = v
    end
    stringCopy.sub = function( s, start, _end )
        local ok, r = nativepcall( nativestringsub, s, start, _end )
        if ok then
            if r then
                return r .. ""
            end
            return nil
        else
            error( r, 2 )
        end
    end
    stringCopy.find = function( s, ... )
        return nativestringfind( s .. "", ... );
    end
    string = stringCopy
end

-- Prevent access to metatables or environments of strings, as these are global between all computers
do
    local nativegetmetatable = getmetatable
    local nativeerror = error
    local nativetype = type
    local string_metatable = nativegetmetatable("")
    function getmetatable( t )
        local mt = nativegetmetatable( t )
        if mt == string_metatable then
            nativeerror( "Attempt to access string metatable", 2 )
        else
            return mt
        end
    end
    if _VERSION == "Lua 5.1" and not _CC_DISABLE_LUA51_FEATURES then
        local string_env = nativegetfenv(("").gsub)
        function getfenv( env )
            if env == nil then
                env = 2
            elseif nativetype( env ) == "number" and env > 0 then
                env = env + 1
            end
            local fenv = nativegetfenv(env)
            if fenv == string_env then
                --nativeerror( "Attempt to access string metatable", 2 )
                return nativegetfenv( 0 )
            else
                return fenv
            end
        end
    end
end

-- Install lua parts of the os api
--
-- The version string is "CraftOS 1.9" to match CC:Tweaked, so that programs which
-- feature-detect against os.version() take the modern code path. The mod itself is
-- still 1.75-based; this is a compatibility label, not a claim about the codebase.
function os.version()
    return "CraftOS 1.9"
end

function os.pullEventRaw( sFilter )
    return coroutine.yield( sFilter )
end

-- Forward all event return values without converting them to a table. A
-- table would create holes around nil arguments and could truncate the
-- request ID used by blocking HTTP/WebSocket calls; a vararg pass-through
-- preserves both nil slots and the original event arity.
local function checkTerminate( ... )
    local sEvent = ...
    if sEvent == "terminate" then
        error( "Terminated", 0 )
    end
    return ...
end

function os.pullEvent( sFilter )
    return checkTerminate( os.pullEventRaw( sFilter ) )
end

-- Install globals
function sleep( nTime )
    local timer = os.startTimer( nTime or 0 )
    repeat
        local sEvent, param = os.pullEvent( "timer" )
    until param == timer
end

function write( sText )
    -- CC:Tweaked accepts numbers here too (write(42)); tostring keeps that working
    -- without changing how strings are handled.
    sText = tostring( sText )
    local w,h = term.getSize()
    local x,y = term.getCursorPos()

    local nLinesPrinted = 0
    local function newLine()
        if y + 1 <= h then
            term.setCursorPos(1, y + 1)
        else
            term.setCursorPos(1, h)
            term.scroll(1)
        end
        x, y = term.getCursorPos()
        nLinesPrinted = nLinesPrinted + 1
    end

    -- Print the line with proper word wrapping
    while string.len(sText) > 0 do
        local whitespace = string.match( sText, "^[ \t]+" )
        if whitespace then
            -- Print whitespace
            term.write( whitespace )
            x,y = term.getCursorPos()
            sText = string.sub( sText, string.len(whitespace) + 1 )
        end

        local newline = string.match( sText, "^\n" )
        if newline then
            -- Print newlines
            newLine()
            sText = string.sub( sText, 2 )
        end

        local text = string.match( sText, "^[^ \t\n]+" )
        if text then
            sText = string.sub( sText, string.len(text) + 1 )
            if string.len(text) > w then
                -- Print a multiline word
                while string.len( text ) > 0 do
                    if x > w then
                        newLine()
                    end
                    term.write( text )
                    text = string.sub( text, (w-x) + 2 )
                    x,y = term.getCursorPos()
                end
            else
                -- Print a word normally
                if x + string.len(text) - 1 > w then
                    newLine()
                end
                term.write( text )
                x,y = term.getCursorPos()
            end
        end
    end

    return nLinesPrinted
end

-- Matches CC:Tweaked: arguments are separated by a tab, and select("#", ...) is used
-- so that embedded nils are printed as "nil" rather than truncating the whole call.
function print( ... )
    local nLinesPrinted = 0
    local nLimit = select( "#", ... )
    for n = 1, nLimit do
        local s = tostring( (select( n, ... )) )
        if n < nLimit then
            s = s .. "\t"
        end
        nLinesPrinted = nLinesPrinted + write( s )
    end
    nLinesPrinted = nLinesPrinted + write( "\n" )
    return nLinesPrinted
end

function printError( ... )
    local oldColour
    if term.isColour() then
        oldColour = term.getTextColour()
        term.setTextColour( colors.red )
    end
    print( ... )
    if term.isColour() then
        term.setTextColour( oldColour )
    end
end

function read( _sReplaceChar, _tHistory, _fnComplete, _sDefault )
    term.setCursorBlink( true )

    local sLine = ""
    local nHistoryPos
    local nPos = 0
    if _sReplaceChar then
        _sReplaceChar = string.sub( _sReplaceChar, 1, 1 )
    end
    if _sDefault ~= nil then
        sLine = tostring( _sDefault )
        nPos = string.len( sLine )
    end

    local tCompletions
    local nCompletion
    local function recomplete()
        if _fnComplete and nPos == string.len(sLine) then
            tCompletions = _fnComplete( sLine )
            if tCompletions and #tCompletions > 0 then
                nCompletion = 1
            else
                nCompletion = nil
            end
        else
            tCompletions = nil
            nCompletion = nil
        end
    end

    local function uncomplete()
        tCompletions = nil
        nCompletion = nil
    end

    local w = term.getSize()
    local sx = term.getCursorPos()

    local function redraw( _bClear )
        local nScroll = 0
        if sx + nPos >= w then
            nScroll = (sx + nPos) - w
        end

        local cx,cy = term.getCursorPos()
        term.setCursorPos( sx, cy )
        local sReplace = (_bClear and " ") or _sReplaceChar
        if sReplace then
            term.write( string.rep( sReplace, math.max( string.len(sLine) - nScroll, 0 ) ) )
        else
            term.write( string.sub( sLine, nScroll + 1 ) )
        end

        if nCompletion then
            local sCompletion = tCompletions[ nCompletion ]
            local oldText, oldBg
            if not _bClear then
                oldText = term.getTextColor()
                oldBg = term.getBackgroundColor()
                term.setTextColor( colors.white )
                term.setBackgroundColor( colors.gray )
            end
            if sReplace then
                term.write( string.rep( sReplace, string.len( sCompletion ) ) )
            else
                term.write( sCompletion )
            end
            if not _bClear then
                term.setTextColor( oldText )
                term.setBackgroundColor( oldBg )
            end
        end

        term.setCursorPos( sx + nPos - nScroll, cy )
    end

    local function clear()
        redraw( true )
    end

    recomplete()
    redraw()

    local function acceptCompletion()
        if nCompletion then
            -- Clear
            clear()

            -- Find the common prefix of all the other suggestions which start with the same letter as the current one
            local sCompletion = tCompletions[ nCompletion ]
            local sFirstLetter = string.sub( sCompletion, 1, 1 )
            local sCommonPrefix = sCompletion
            for n=1,#tCompletions do
                local sResult = tCompletions[n]
                if n ~= nCompletion and string.find( sResult, sFirstLetter, 1, true ) == 1 then
                    while #sCommonPrefix > 1 do
                        if string.find( sResult, sCommonPrefix, 1, true ) == 1 then
                            break
                        else
                            sCommonPrefix = string.sub( sCommonPrefix, 1, #sCommonPrefix - 1 )
                        end
                    end
                end
            end

            -- Append this string
            sLine = sLine .. sCommonPrefix
            nPos = string.len( sLine )
        end

        recomplete()
        redraw()
    end
    while true do
        local sEvent, param = os.pullEvent()
        if sEvent == "char" then
            -- Typed key
            clear()
            sLine = string.sub( sLine, 1, nPos ) .. param .. string.sub( sLine, nPos + 1 )
            nPos = nPos + 1
            recomplete()
            redraw()

        elseif sEvent == "paste" then
            -- Pasted text
            clear()
            sLine = string.sub( sLine, 1, nPos ) .. param .. string.sub( sLine, nPos + 1 )
            nPos = nPos + string.len( param )
            recomplete()
            redraw()

        elseif sEvent == "key" then
            if param == keys.enter then
                -- Enter
                if nCompletion then
                    clear()
                    uncomplete()
                    redraw()
                end
                break

            elseif param == keys.left then
                -- Left
                if nPos > 0 then
                    clear()
                    nPos = nPos - 1
                    recomplete()
                    redraw()
                end

            elseif param == keys.right then
                -- Right
                if nPos < string.len(sLine) then
                    -- Move right
                    clear()
                    nPos = nPos + 1
                    recomplete()
                    redraw()
                else
                    -- Accept autocomplete
                    acceptCompletion()
                end

            elseif param == keys.up or param == keys.down then
                -- Up or down
                if nCompletion then
                    -- Cycle completions
                    clear()
                    if param == keys.up then
                        nCompletion = nCompletion - 1
                        if nCompletion < 1 then
                            nCompletion = #tCompletions
                        end
                    elseif param == keys.down then
                        nCompletion = nCompletion + 1
                        if nCompletion > #tCompletions then
                            nCompletion = 1
                        end
                    end
                    redraw()

                elseif _tHistory then
                    -- Cycle history
                    clear()
                    if param == keys.up then
                        -- Up
                        if nHistoryPos == nil then
                            if #_tHistory > 0 then
                                nHistoryPos = #_tHistory
                            end
                        elseif nHistoryPos > 1 then
                            nHistoryPos = nHistoryPos - 1
                        end
                    else
                        -- Down
                        if nHistoryPos == #_tHistory then
                            nHistoryPos = nil
                        elseif nHistoryPos ~= nil then
                            nHistoryPos = nHistoryPos + 1
                        end
                    end
                    if nHistoryPos then
                        sLine = _tHistory[nHistoryPos]
                        nPos = string.len( sLine )
                    else
                        sLine = ""
                        nPos = 0
                    end
                    uncomplete()
                    redraw()

                end

            elseif param == keys.backspace then
                -- Backspace
                if nPos > 0 then
                    clear()
                    sLine = string.sub( sLine, 1, nPos - 1 ) .. string.sub( sLine, nPos + 1 )
                    nPos = nPos - 1
                    recomplete()
                    redraw()
                end

            elseif param == keys.home then
                -- Home
                if nPos > 0 then
                    clear()
                    nPos = 0
                    recomplete()
                    redraw()
                end

            elseif param == keys.delete then
                -- Delete
                if nPos < string.len(sLine) then
                    clear()
                    sLine = string.sub( sLine, 1, nPos ) .. string.sub( sLine, nPos + 2 )
                    recomplete()
                    redraw()
                end

            elseif param == keys["end"] then
                -- End
                if nPos < string.len(sLine ) then
                    clear()
                    nPos = string.len(sLine)
                    recomplete()
                    redraw()
                end

            elseif param == keys.tab then
                -- Tab (accept autocomplete)
                acceptCompletion()

            end

        elseif sEvent == "term_resize" then
            -- Terminal resized
            w = term.getSize()
            redraw()

        end
    end

    local cx, cy = term.getCursorPos()
    term.setCursorBlink( false )
    term.setCursorPos( w + 1, cy )
    print()

    return sLine
end

-- loadfile( path [, mode [, env]] )
--
-- The 2-argument form loadfile(path, env) is accepted as a convenience, matching
-- CC:Tweaked. `mode` is passed straight through to load(); a nil mode means "allow
-- whatever the VM allows", which for Cobalt is text chunks only.
loadfile = function( _sFile, _sMode, _tEnv )
    if type( _sMode ) == "table" and _tEnv == nil then
        _sMode, _tEnv = nil, _sMode
    end
    local file = fs.open( _sFile, "r" )
    if file then
        -- Chunk names follow CC:Tweaked's "@/path" form, which shows up in tracebacks.
        -- fs.combine() is not used here because this codebase's version takes two
        -- arguments (the 1.7.10 contract), unlike the modern one.
        local sName = ( string.sub( _sFile, 1, 1 ) == "/" ) and _sFile or ( "/" .. _sFile )
        local func, err = load( file.readAll(), "@" .. sName, _sMode, _tEnv )
        file.close()
        return func, err
    end
    return nil, "File not found"
end

-- dofile() is part of the standard Lua library, so it must exist regardless of the
-- underlying Lua version. It used to be defined only in the Lua 5.1 branch, which left
-- it undefined on the Lua 5.2 runtime (Cobalt).
function dofile( _sFile )
    local fnFile, e = loadfile( _sFile, nil, _G )
    if fnFile then
        return fnFile()
    else
        error( e, 2 )
    end
end

-- require / package
-- Provides a Lua-5.2-style require that resolves modules from rom/modules/main/.
-- package.path uses semicolon-separated templates where '?' is the slash-converted
-- module name (e.g. "cc.expect" -> "cc/expect").
package = {}
package.loaded = {}
package.path = "rom/modules/main/?.lua;rom/modules/main/?/init.lua"

function require( _name )
    if package.loaded[_name] ~= nil then
        return package.loaded[_name]
    end

    local fragment = _name:gsub( "%.", "/" )
    local searched = {}
    for template in package.path:gmatch( "[^;]+" ) do
        local path = template:gsub( "%?", fragment )
        if fs.exists( path ) then
            -- Set an empty-table sentinel so that circular requires receive a
            -- usable table rather than boolean true.  This matches the Lua 5.2+
            -- package library behaviour.
            local loading = {}
            package.loaded[_name] = loading
            local fn, err = loadfile( path )
            if not fn then
                package.loaded[_name] = nil
                error( "error loading module '" .. _name .. "' from '" .. path .. "':\n" .. tostring( err ), 2 )
            end
            local result = fn( _name )
            -- If the module body returned nil keep the sentinel table so callers
            -- always get a table from require(), never a boolean.
            package.loaded[_name] = ( result ~= nil ) and result or loading
            return package.loaded[_name]
        end
        searched[#searched + 1] = "  no file '" .. path .. "'"
    end
    error( "module '" .. _name .. "' not found:\n" .. table.concat( searched, "\n" ), 2 )
end


function os.run( _tEnv, _sPath, ... )
    local tEnv = _tEnv
    -- Only install the _G fallback when the caller has not already set up a
    -- metatable on the env.  The shell creates a fresh per-run table with
    -- __index pointing at its own tEnv (which contains 'shell', 'multishell',
    -- etc.) and then chains _G from tEnv itself.  Overwriting that metatable
    -- here would sever the shell chain and make 'shell' invisible to programs.
    local mt = getmetatable( tEnv )
    if mt == nil then
        setmetatable( tEnv, { __index = _G } )
        mt = getmetatable( tEnv )
    end

    -- "bios.strict_globals": assigning to an undeclared global inside a program is an
    -- error. load() assigns _ENV on this table, which would trip __newindex, so _ENV has
    -- to be set before the protection is installed.
    if settings and settings.get( "bios.strict_globals", false ) then
        tEnv._ENV = tEnv
        if type( mt ) == "table" then
            mt.__newindex = function( _, name )
                error( "Attempt to create global " .. tostring( name ), 2 )
            end
        end
    end

    local fnFile, err = loadfile( _sPath, nil, tEnv )
    if fnFile then
        local ok, err = pcall( fnFile, ... )
        if not ok then
            if err and err ~= "" then
                printError( err )
            end
            return false, err
        end
        return true
    end
    if err and err ~= "" then
        printError( err )
    end
    return false, err
end

local tAPIsLoading = {}
function os.loadAPI( _sPath )
    local sName = fs.getName( _sPath )
    -- Match CC:Tweaked: an API file named "foo.lua" is exposed as "foo".
    if string.sub( sName, -4 ) == ".lua" then
        sName = string.sub( sName, 1, -5 )
    end
    if tAPIsLoading[sName] == true then
        printError( "API "..sName.." is already being loaded" )
        return false
    end
    tAPIsLoading[sName] = true

    local tEnv = {}
    setmetatable( tEnv, { __index = _G } )
    local fnAPI, err = loadfile( _sPath, tEnv )
    if fnAPI then
        local ok, err = pcall( fnAPI )
        if not ok then
            printError( err )
            tAPIsLoading[sName] = nil
            return false
        end
    else
        printError( err )
        tAPIsLoading[sName] = nil
        return false
    end

    local tAPI = {}
    for k,v in pairs( tEnv ) do
        if k ~= "_ENV" then
            tAPI[k] =  v
        end
    end

    _G[sName] = tAPI
    tAPIsLoading[sName] = nil
    return true
end

function os.unloadAPI( _sName )
    if _sName ~= "_G" and type(_G[_sName]) == "table" then
        _G[_sName] = nil
    end
end

function os.sleep( nTime )
    sleep( nTime )
end

local nativeShutdown = os.shutdown
function os.shutdown()
    nativeShutdown()
    while true do
        coroutine.yield()
    end
end

local nativeReboot = os.reboot
function os.reboot()
    nativeReboot()
    while true do
        coroutine.yield()
    end
end

-- Install the lua part of the HTTP api (if enabled)
if http then
    local nativeHTTPRequest = http.request
    local nextRequestID = 0
    local function allocateRequestID()
        nextRequestID = nextRequestID + 1
        return nextRequestID
    end

    -- wrapRequest: performs a synchronous HTTP request and waits for the result.
    -- args: url, post body, headers, method verb, timeout (seconds), binary flag,
    --       redirect flag
    -- Returns: response on success; nil, errMsg, [responseOnError] on failure.
    local function wrapRequest( _url, _post, _headers, _method, _timeout, _binary, _redirect )
        local requestID = allocateRequestID()
        local ok, err = nativeHTTPRequest(
            _url, _post, _headers, _method, _timeout, _binary, requestID, true, _redirect ~= false
        )
        if ok then
            -- Like other blocking APIs, unrelated events are not buffered here.
            -- Use parallel or http.request when other events must be handled.
            -- The native request's timeout bounds socket I/O, but it does not
            -- bound this Lua event wait. Add a small grace-period watchdog for
            -- explicitly bounded requests so a lost completion event cannot
            -- wedge the computer forever.
            local watchdog
            if type(_timeout) == "number" and _timeout > 0 then
                watchdog = os.startTimer(_timeout + 1)
            end
            while true do
                local event, param1, param2, param3, param4 = os.pullEvent()
                if watchdog and event == "timer" and param1 == watchdog then
                    return nil, "Request timed out"
                elseif event == "http_internal_success" and param1 == requestID then
                    if watchdog then pcall(os.cancelTimer, watchdog) end
                    return param3
                elseif event == "http_internal_failure" and param1 == requestID then
                    if watchdog then pcall(os.cancelTimer, watchdog) end
                    return nil, param3, param4
                elseif event == "http_success" and param1 == _url and param3 == requestID then
                    if watchdog then pcall(os.cancelTimer, watchdog) end
                    return param2
                elseif event == "http_failure" and param1 == _url then
                    -- Newer events keep an explicit nil response slot before
                    -- requestID. Older/bridged events may collapse that slot;
                    -- accept requestID in the third position as a safe
                    -- compatibility fallback (reason is always a string).
                    if param4 == requestID then
                        if watchdog then pcall(os.cancelTimer, watchdog) end
                        return nil, param2, param3
                    elseif param4 == nil and param3 == requestID then
                        if watchdog then pcall(os.cancelTimer, watchdog) end
                        return nil, param2, nil

                end
            end
        end
        end
        return nil, err
    end

    -- http.get(url [, headers [, binary]])
    -- http.get({ url=, headers=, binary=, timeout=, redirect= })
    http.get = function( _url, _headers, _binary )
        if type( _url ) == "table" then
            local opts = _url
            return wrapRequest( opts.url, nil, opts.headers, nil, opts.timeout, opts.binary, opts.redirect )
        end
        return wrapRequest( _url, nil, _headers, nil, nil, _binary )
    end

    -- http.post(url, body [, headers [, binary]])
    -- http.post({ url=, body=, headers=, method=, binary=, timeout=, redirect= })
    http.post = function( _url, _post, _headers, _binary )
        if type( _url ) == "table" then
            local opts = _url
            return wrapRequest(
                opts.url, opts.body or "", opts.headers, opts.method, opts.timeout, opts.binary, opts.redirect
            )
        end
        return wrapRequest( _url, _post or "", _headers, nil, nil, _binary )
    end

    -- http.request(url [, body [, headers [, method [, timeout [, binary]]]]])
    -- http.request({ url=, body=, headers=, method=, timeout=, binary=, redirect= })
    --
    -- `redirect` defaults to true, matching upstream. Set redirect = false to see the 3xx
    -- response itself; otherwise redirects are followed (up to 16) and every hop is
    -- re-checked against the domain patterns and the local-address rule.
    http.request = function( _url, _post, _headers, _method, _timeout, _binary )
        if type( _url ) == "table" then
            local opts = _url
            local ok, err = nativeHTTPRequest(
                opts.url, opts.body, opts.headers, opts.method, opts.timeout, opts.binary, nil, false, opts.redirect ~= false
            )
            if not ok then
                os.queueEvent( "http_failure", opts.url, err )
            end
            return ok, err
        end
        local ok, err = nativeHTTPRequest( _url, _post, _headers, _method, _timeout, _binary )
        if not ok then
            os.queueEvent( "http_failure", _url, err )
        end
        return ok, err
    end

    http.checkURLAsync = function( _url )
        local ok, err = http.checkURL( _url )
        if ok then
            os.queueEvent( "check_url_success", _url )
        else
            os.queueEvent( "check_url_failure", _url, err )
        end
    end

    local nativeWebsocket = http.websocket

    -- Both websocket entry points accept (url [, headers]) or a single options table
    -- { url =, headers =, timeout = }. The timeout is in seconds and covers the handshake.
    local function websocketArgs( _url, _headers )
        if type( _url ) == "table" then
            local opts = _url
            if type( opts.url ) ~= "string" then
                error( "bad field 'url' (string expected, got " .. type( opts.url ) .. ")", 3 )
            end
            if opts.headers ~= nil and type( opts.headers ) ~= "table" then
                error( "bad field 'headers' (table expected, got " .. type( opts.headers ) .. ")", 3 )
            end
            if opts.timeout ~= nil and type( opts.timeout ) ~= "number" then
                error( "bad field 'timeout' (number expected, got " .. type( opts.timeout ) .. ")", 3 )
            end
            return opts.url, opts.headers, opts.timeout
        end
        if type( _url ) ~= "string" then
            error( "bad argument #1 (string expected, got " .. type( _url ) .. ")", 3 )
        end
        return _url, _headers, nil
    end

    http.websocketAsync = function( _url, _headers )
        local url, headers, timeout = websocketArgs( _url, _headers )
        local ok, err = nativeWebsocket( url, headers, nil, timeout )
        if not ok then
            os.queueEvent( "websocket_failure", url, err )
        end
    end

    http.websocket = function( _url, _headers )
        -- Resolve the real url first: comparing the events against a table would never match,
        -- which is what an `http.websocket({ url = ... })` call used to do -- it hung forever.
        local url, headers, timeout = websocketArgs( _url, _headers )
        local requestID = allocateRequestID()
        local ok, err = nativeWebsocket( url, headers, requestID, timeout )
        if not ok then
            return false, err
        end
        while true do
            local event, evUrl, param, eventID = os.pullEvent()
            if event == "websocket_success" and evUrl == url and eventID == requestID then
                return param
            elseif event == "websocket_failure" and evUrl == url and eventID == requestID then
                return false, param
            end
        end
    end
end

-- Install the lua part of the FS api
local tEmpty = {}
function fs.complete( sPath, sLocation, bIncludeFiles, bIncludeDirs )
    -- Allow arg 3 to be an options table (CC:Tweaked 1.101.0 compat)
    local bIncludeHidden
    if type(bIncludeFiles) == "table" then
        local opts = bIncludeFiles
        bIncludeFiles = (opts.include_files ~= false)
        bIncludeDirs  = (opts.include_dirs  ~= false)
        bIncludeHidden = (opts.include_hidden ~= false)
    else
        bIncludeFiles = (bIncludeFiles ~= false)
        bIncludeDirs  = (bIncludeDirs  ~= false)
        -- Hidden entries stay included by default, so existing callers which pass
        -- booleans keep the pre-1.101 behaviour.
        bIncludeHidden = true
    end
    local sDir = sLocation
    local nStart = 1
    local nSlash = string.find( sPath, "[/\\]", nStart )
    if nSlash == 1 then
        sDir = ""
        nStart = 2
    end
    local sName
    while not sName do
        local nSlash = string.find( sPath, "[/\\]", nStart )
        if nSlash then
            local sPart = string.sub( sPath, nStart, nSlash - 1 )
            sDir = fs.combine( sDir, sPart )
            nStart = nSlash + 1
        else
            sName = string.sub( sPath, nStart )
        end
    end

    if fs.isDir( sDir ) then
        local tResults = {}
        if bIncludeDirs and sPath == "" then
            table.insert( tResults, "." )
        end
        if sDir ~= "" then
            if sPath == "" then
                table.insert( tResults, (bIncludeDirs and "..") or "../" )
            elseif sPath == "." then
                table.insert( tResults, (bIncludeDirs and ".") or "./" )
            end
        end
        local tFiles = fs.list( sDir )
        for n=1,#tFiles do
            local sFile = tFiles[n]
            if #sFile >= #sName and string.sub( sFile, 1, #sName ) == sName and (
                bIncludeHidden or string.sub( sFile, 1, 1 ) ~= "." or string.sub( sName, 1, 1 ) == "."
            ) then
                local bIsDir = fs.isDir( fs.combine( sDir, sFile ) )
                local sResult = string.sub( sFile, #sName + 1 )
                if bIsDir then
                    table.insert( tResults, sResult .. "/" )
                    if bIncludeDirs and #sResult > 0 then
                        table.insert( tResults, sResult )
                    end
                else
                    if bIncludeFiles and #sResult > 0 then
                        table.insert( tResults, sResult )
                    end
                end
            end
        end
        return tResults
    end
    return tEmpty
end

-- Load APIs
local bAPIError = false
local tApis = fs.list( "rom/apis" )
for n,sFile in ipairs( tApis ) do
    if string.sub( sFile, 1, 1 ) ~= "." then
        local sPath = fs.combine( "rom/apis", sFile )
        if not fs.isDir( sPath ) then
            if not os.loadAPI( sPath ) then
                bAPIError = true
            end
        end
    end
end

if turtle then
    -- Load turtle APIs
    local tApis = fs.list( "rom/apis/turtle" )
    for n,sFile in ipairs( tApis ) do
        if string.sub( sFile, 1, 1 ) ~= "." then
            local sPath = fs.combine( "rom/apis/turtle", sFile )
            if not fs.isDir( sPath ) then
                if not os.loadAPI( sPath ) then
                    bAPIError = true
                end
            end
        end
    end
end

if pocket and fs.isDir( "rom/apis/pocket" ) then
    -- Load pocket APIs
    local tApis = fs.list( "rom/apis/pocket" )
    for n,sFile in ipairs( tApis ) do
        if string.sub( sFile, 1, 1 ) ~= "." then
            local sPath = fs.combine( "rom/apis/pocket", sFile )
            if not fs.isDir( sPath ) then
                if not os.loadAPI( sPath ) then
                    bAPIError = true
                end
            end
        end
    end
end

if commands and fs.isDir( "rom/apis/command" ) then
    -- Load command APIs
    if os.loadAPI( "rom/apis/command/commands" ) then
        -- Add a special case-insensitive metatable to the commands api
        local tCaseInsensitiveMetatable = {
            __index = function( table, key )
                local value = rawget( table, key )
                if value ~= nil then
                    return value
                end
                if type(key) == "string" then
                    local value = rawget( table, string.lower(key) )
                    if value ~= nil then
                        return value
                    end
                end
                return nil
            end
        }
        setmetatable( commands, tCaseInsensitiveMetatable )
        setmetatable( commands.async, tCaseInsensitiveMetatable )

        -- Add global "exec" function
        exec = commands.exec
    else
        bAPIError = true
    end
end

if bAPIError then
    print( "Press any key to continue" )
    os.pullEvent( "key" )
    term.clear()
    term.setCursorPos( 1,1 )
end

-- Set default settings
-- Mirrors CC:Tweaked's bios defaults so that programs can call settings.get() without
-- having to know whether a value was ever persisted. Individual features read these
-- definitions as they are ported (shell completion, edit extensions, list/motd, ...).
if settings then
    settings.define( "shell.allow_startup", {
        default = true,
        description = "Run startup files when the computer turns on.",
        type = "boolean",
    } )
    settings.define( "shell.allow_disk_startup", {
        default = (commands == nil),
        description = "Run startup files from disk drives when the computer turns on.",
        type = "boolean",
    } )

    settings.define( "shell.autocomplete", {
        default = true,
        description = "Autocomplete program and arguments in the shell.",
        type = "boolean",
    } )
    settings.define( "shell.autocomplete_hidden", {
        default = false,
        description = [[Autocomplete hidden files and folders (those starting with ".").]],
        type = "boolean",
    } )
    settings.define( "edit.autocomplete", {
        default = true,
        description = "Autocomplete API and function names in the editor.",
        type = "boolean",
    } )
    settings.define( "lua.autocomplete", {
        default = true,
        description = "Autocomplete API and function names in the Lua REPL.",
        type = "boolean",
    } )

    settings.define( "edit.default_extension", {
        default = "lua",
        description = [[The file extension the editor will use if none is given. Set to "" to disable.]],
        type = "string",
    } )
    settings.define( "paint.default_extension", {
        default = "nfp",
        description = [[The file extension the paint program will use if none is given. Set to "" to disable.]],
        type = "string",
    } )

    settings.define( "list.show_hidden", {
        default = false,
        description = [[Whether the list program shows hidden files (those starting with ".").]],
        type = "boolean",
    } )

    settings.define( "motd.enable", {
        default = (pocket == nil),
        description = "Display a random message when the computer starts up.",
        type = "boolean",
    } )
    settings.define( "motd.path", {
        default = "/rom/motd.txt:/motd.txt",
        description = [[The path to load random messages from. Should be a colon (":") separated string of file paths.]],
        type = "string",
    } )

    settings.define( "lua.warn_against_use_of_local", {
        default = true,
        description = [[Print a message when input in the Lua REPL starts with the word 'local'. Local variables defined in the Lua REPL are inaccessible on the next input.]],
        type = "boolean",
    } )
    settings.define( "lua.function_args", {
        default = true,
        description = "Show function arguments when printing functions.",
        type = "boolean",
    } )
    settings.define( "lua.function_source", {
        default = false,
        description = "Show where a function was defined when printing functions.",
        type = "boolean",
    } )
    settings.define( "bios.strict_globals", {
        default = false,
        description = "Prevents assigning variables into a program's environment. Make sure you use the local keyword or assign to _G explicitly.",
        type = "boolean",
    } )

    if term.isColour() then
        settings.define( "bios.use_multishell", {
            default = true,
            description = [[Allow running multiple programs at once, through the use of the "fg" and "bg" programs.]],
            type = "boolean",
        } )
    end
end

-- Load default settings from _CC_DEFAULT_SETTINGS (if set), then persisted settings
if settings then
    if type(_CC_DEFAULT_SETTINGS) == "string" then
        for sPair in string.gmatch( _CC_DEFAULT_SETTINGS, "[^,]+" ) do
            local sName, sValue = string.match( sPair, "([^=]*)=(.*)" )
            if sName and sValue then
                local value
                if sValue == "true" then
                    value = true
                elseif sValue == "false" then
                    value = false
                elseif sValue == "nil" then
                    value = nil
                elseif tonumber( sValue ) then
                    value = tonumber( sValue )
                elseif string.match( sValue, "^[%{\[\"']" ) then
                    -- Local extension over CC:Tweaked, which would store the raw text here:
                    -- accept table and quoted-string literals so existing configs that used
                    -- them keep working. unserialize evaluates with an empty environment.
                    local ok, parsed = pcall( textutils.unserialise, sValue )
                    value = ok and parsed ~= nil and parsed or sValue
                else
                    value = sValue
                end

                if value ~= nil then
                    -- A value that violates the registered type must not abort the BIOS; a
                    -- typo in cc_default_settings should not brick every computer.
                    if not pcall( settings.set, sName, value ) then
                        settings.unset( sName )
                    end
                else
                    settings.unset( sName )
                end
            end
        end
    end
    settings.load( ".settings" )
end

-- Run the shell
local ok, err = pcall( function()
    parallel.waitForAny(
        function()
            if term.isColour() then
                os.run( {}, "rom/programs/advanced/multishell" )
            else
                os.run( {}, "rom/programs/shell" )
            end
            os.run( {}, "rom/programs/shutdown" )
        end,
        function()
            rednet.run()
        end )
end )

-- If the shell errored, let the user read it.
term.redirect( term.native() )
if not ok then
    printError( err )
    pcall( function()
        term.setCursorBlink( false )
        print( "Press any key to continue" )
        os.pullEvent( "key" )
    end )
end

-- End
os.shutdown()
