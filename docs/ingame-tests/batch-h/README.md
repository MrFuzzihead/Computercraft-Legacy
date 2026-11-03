# Batch H — in-game validation pack

Networking and concurrency: `parallel.spawn` and the empty-argument hang, error source
information, `rednet` channel wrapping, and the `http` `redirect` / websocket options.

## Why an in-game pack is needed

Two of the five items are covered by JVM tests:

| Item | Where it is tested |
|---|---|
| `http` `redirect` option | `HTTPRedirectTest` (real local server, follow/no-follow, 303, hop budget, non-http targets) |
| Private/CGNAT/link-local blocking | `PrivateAddressesTest` (the whole IANA range table, plus the config flag) |
| `parallel.spawn`, empty-argument hang, error wrapping | **this pack** |
| `rednet` `MAX_ID_CHANNELS` wrapping | **this pack** |
| `http.websocket` options table / timeout | **this pack** (argument validation) |

The `parallel` and `rednet` halves are Lua that needs `os`, `peripheral` and a real computer; the
headless test machine used by the other Lua tests has neither, and building a fake `os` there would
test the fake rather than the ROM. The socket-level `http` checks are here too, because a JVM test
has no network.

## Run

```bat
copy docs\ingame-tests\batch-h\*.lua "run\client\saves\<World>\computer\<id>\"
copy docs\ingame-tests\readlog_manual.lua "run\client\saves\<World>\computer\<id>\"
```

Then type **`h1_networking`**. No peripherals are needed.

```
readlog_manual /batch-h-results.txt
```

The script deliberately performs **no network I/O**: every socket call blocks, and the headless
harness that runs these scripts pumps a fixed number of ticks, so a blocking call there would look
like a hang. Anything that opens a connection is in the manual drill instead.

## What is checked automatically

| Section | Checks |
|---|---|
| `parallel` | `waitForAny()` and `waitForAll()` with no arguments return instead of hanging; `spawn` starts new functions; `spawn` outside `waitForAll` is refused; an error escaping `waitForAll` carries its source thread (or stays a string inside a `pcall`, which is the documented behaviour) |
| `rednet` | `MAX_ID_CHANNELS` is published as 65535; the broadcast/repeat channels are unchanged; the wrapping rule maps 5→5, 65535→65535, 70000→4465, and always stays in 1..65535 |
| `http` | `http.request`/`http.get` accept a `redirect` field; the websocket options table validates `url`, `headers` and `timeout` before any connection is attempted |

## Manual drill

The five things the script cannot do headlessly, in order of importance:

1. **Two computers with modems**: `rednet.open("back")` on both, then `rednet.send(id, "hi")`.
   IDs below 65535 must behave exactly as before.
2. `parallel.waitForAll(function(spawn) spawn(print, "hello") end)` — prints `hello`, no hang.
3. `parallel.waitForAny()` at the shell returns immediately.
4. `http.get("http://127.0.0.1:8080/")` fails with `Domain not permitted`, unless the config has
   `http_allow_local = true`. **This is the security-relevant one**: before Batch H a computer could
   read any service on the server's own machine and network.
5. `http.get({ url = "http://httpbin.org/redirect/1", redirect = true })` → 200; with
   `redirect = false` → 302 plus a `Location` header.

## Notes on what changed, and what did not

**Redirects are now followed by the request itself** rather than by `HttpURLConnection`. Java's
built-in follow-redirects do not re-enter our validation, so a public URL redirecting to
`http://192.168.1.1/` would have bypassed the address rule entirely. The loop re-checks both the
domain patterns and the resolved address on every hop, and only follows `http`/`https`.

**The address rule is deny-by-default.** `http_allow_local` restores the old behaviour for LAN
servers. The check resolves the host, so a public name pointing at a private address is caught too,
and it runs on the HTTP worker thread where DNS already happened, so it never blocks a computer.

**`rednet.lookup` already had a timeout** (a 2 second timer), so that audit item needed no change —
the comment in the module now says so, next to the timer.

**The `@e`-style selector work is not here**: it was Batch G.
