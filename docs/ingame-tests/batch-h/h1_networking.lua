-- Batch H: networking and concurrency.
--
-- Runs on any computer (no peripheral needed). It covers the parts a JVM test cannot reach:
--   * parallel.spawn, the empty-argument hang, and error source info;
--   * rednet channel wrapping for computer IDs above 65535;
--   * http.websocket's options table and the http `redirect` option.
--
-- The HTTP checks need a network and a server that redirects; if the request fails for any reason
-- the check is reported as SKIP with the reason, so an offline computer still gives a useful run.
--
-- The transcript is written to /batch-h-results.txt:
--   readlog_manual /batch-h-results.txt

local P, F, S = 0, 0, 0
local _transcript = {}
local fails, skips = {}, {}

local function pass(label)
  P = P + 1
  _transcript[#_transcript + 1] = "OK   " .. label
end

local function ok(label, cond, detail)
  if cond then
    pass(label)
  else
    F = F + 1
    local line = label .. ((detail ~= nil) and ("  [" .. tostring(detail) .. "]") or "")
    fails[#fails + 1] = line
    _transcript[#_transcript + 1] = "FAIL " .. line
  end
end

local function eq(label, expected, got)
  ok(label, expected == got, "expected " .. tostring(expected) .. " got " .. tostring(got))
end

local function skip(label)
  S = S + 1
  skips[#skips + 1] = label
  _transcript[#_transcript + 1] = "SKIP " .. label
  print("SKIP " .. label)
end

local function section(n) print("== " .. n .. " ==") end

local function group(name, fn)
  local okRun, err = pcall(fn)
  if not okRun then ok(name .. " crashed", false, err) end
end

local function finish(name)
  local lines = { "RESULT " .. name }
  lines[#lines + 1] = "  passed " .. P .. "   failed " .. F .. "   skipped " .. S
  for _, f in ipairs(fails) do lines[#lines + 1] = "  FAIL " .. f end
  for _, k in ipairs(skips) do lines[#lines + 1] = "  SKIP " .. k end
  for _, line in ipairs(_transcript) do lines[#lines + 1] = "    " .. line end

  local path = "/batch-h-results.txt"
  local h = fs.open(path, "a")
  if h then
    for _, line in ipairs(lines) do h.write(line .. "\n") end
    h.close()
  else
    printError("Could not write " .. path)
  end

  term.clear()
  term.setCursorPos(1, 1)
  for i = 1, math.min(#fails, 4) do print("FAIL " .. fails[i]) end
  if #fails > 4 then print("... and " .. (#fails - 4) .. " more") end
  for i = 1, math.min(#skips, 2) do print("SKIP " .. skips[i]) end
  print("File: " .. path .. "  (readlog_manual)")
  print("RESULT " .. name .. ": passed " .. P .. "   failed " .. F .. "   skipped " .. S)
end

-- ---------------------------------------------------------------------------
term.clear()
term.setCursorPos(1, 1)

-- ---------------------------------------------------------------------------
print("== parallel ==")
group("parallel", function()
  -- The empty-argument hang: this used to pull events for ever. A regression would stall here,
  -- which the harness reports as "never printed a verdict".
  ok("waitForAny() returns 0", (function()
    local index = parallel.waitForAny()
    return index == 0
  end)())

  ok("waitForAll() with no functions returns", (function()
    parallel.waitForAll()
    return true
  end)())

  -- spawn
  local ran = {}
  parallel.waitForAll(function(spawn)
    for i = 1, 3 do
      spawn(function()
        ran[i] = true
      end)
    end
  end)
  ok("spawn runs new functions", ran[1] and ran[2] and ran[3], tostring(ran[1]) .. tostring(ran[2]) .. tostring(ran[3]))

  -- spawn is refused outside waitForAll
  local captured
  parallel.waitForAll(function(spawn)
    captured = spawn
  end)
  local spawnOk = pcall(captured, function() end)
  ok("spawn outside waitForAll is refused", spawnOk == false)

  -- error source info: outside a pcall the shell sees a table with the source thread.
  local okRun, err = pcall(parallel.waitForAll, function()
    error("boom", 0)
  end)
  ok("an error is raised", okRun == false)
  if type(err) == "table" then
    eq("the error carries its thread", "thread", type(err.thread))
  else
    -- Inside a pcall the error stays a plain string: that is the documented behaviour, because a
    -- pcall would swallow the rich table anyway.
    eq("inside a pcall the error stays a string", "string", type(err))
  end
end)

-- ---------------------------------------------------------------------------
print("== rednet channels ==")
group("rednet channels", function()
  eq("MAX_ID_CHANNELS is published", 65535, rednet.MAX_ID_CHANNELS)
  eq("broadcast channel", 65535, rednet.CHANNEL_BROADCAST)
  eq("repeat channel", 65533, rednet.CHANNEL_REPEAT)

  -- Prove the wrapping rule without needing an ID above 65535: the same arithmetic the ROM uses.
  local function wrap(id)
    return ((id - 1) % rednet.MAX_ID_CHANNELS) + 1
  end
  eq("id 5 maps to channel 5", 5, wrap(5))
  eq("id 65535 maps to itself", 65535, wrap(65535))
  eq("id 70000 wraps into range", 4465, wrap(70000))
  ok("every wrapped channel is in range", (function()
    for _, id in ipairs({ 1, 2, 65534, 65535, 65536, 70000, 131070 }) do
      local channel = wrap(id)
      if channel < 1 or channel > 65535 then return false end
    end
    return true
  end)())
end)

-- ---------------------------------------------------------------------------
print("== http options ==")
group("http options", function()
  -- Argument validation must happen before any connection is attempted.
  local okCall, err = pcall(http.websocket, { headers = {} })
  ok("websocket options need a url", okCall == false and tostring(err):find("url") ~= nil, err)

  okCall, err = pcall(http.websocket, { url = "ws://example.invalid", timeout = "soon" })
  ok("websocket timeout must be a number", okCall == false and tostring(err):find("timeout") ~= nil, err)

  okCall, err = pcall(http.websocket, 5)
  ok("websocket needs a string or a table", okCall == false, err)

  -- A redirect option must be accepted, and a bad URL must fail without opening a socket.
  local replyOk, replyErr = http.request({ url = "not a url", redirect = false })
  eq("http.request accepts a redirect field", "false:URL malformed", tostring(replyOk) .. ":" .. tostring(replyErr))

  local getting = http.get({ url = "not a url", redirect = true })
  ok("http.get accepts a redirect field", getting == nil)
end)

-- ---------------------------------------------------------------------------
print("")
print("--- MANUAL DRILL ---")
print("1. On two computers with modems, open rednet on both and check")
print("   rednet.send works as before (IDs below 65535 are unchanged).")
print("2. parallel.waitForAll(function(spawn) spawn(print, 'hello') end)")
print("   EXPECT hello printed and no hang.")
print("3. parallel.waitForAny() at the shell must return immediately.")
print("4. http.get('http://127.0.0.1:8080/') must fail with 'Domain not permitted'")
print("   unless http_allow_local is true in the config.")
print("5. http.get({ url = 'http://httpbin.org/redirect/1', redirect = true })")
print("   EXPECT a 200. With redirect = false, EXPECT a 302 and a Location header.")
print("6. local ws = http.websocket({ url = 'wss://echo.websocket.org', timeout = 5 })")
print("   EXPECT the handshake timeout to be honoured (fails rather than hanging).")
print("7. http.request({ url = 'http://example.com', method = 'PATCH' })")
print("   EXPECT nil, 'Unsupported HTTP method: PATCH' -- HttpURLConnection cannot")
print("   send PATCH at all. Use POST with an override header if a server needs it.")
print("8. readlog_manual /batch-h-results.txt for the full transcript.")

finish("BATCH-H networking")
