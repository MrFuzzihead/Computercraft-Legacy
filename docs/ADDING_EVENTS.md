# Adding New Lua Events to ComputerCraft-Legacy

This guide explains how to fire new Lua events from Java and how to listen for them in Lua using `os.pullEvent`. Use `modem_message` (peripheral) and `websocket_message` (core API) as reference implementations.

---

## Overview

Events are the primary way Java code communicates asynchronously with running Lua programs. A Lua program blocks on `os.pullEvent("my_event")` and resumes when Java calls `queueEvent("my_event", args)` on the computer.

There are two contexts where you will fire events:

| Context | Interface used | Example |
|---|---|---|
| **Peripheral** (attached to a computer) | `IComputerAccess.queueEvent` | `modem_message`, `chat`, `speaker_audio_empty` |
| **Core API** (internal `ILuaAPI`) | `IAPIEnvironment.queueEvent` | `websocket_message`, `websocket_closed`, `task_complete` |

---

## Naming Conventions

- Event names are **snake_case** strings, e.g. `"modem_message"`, `"speaker_audio_empty"`.
- Prefix with the peripheral/API name when the event is specific to one source (`"modem_message"`, `"websocket_closed"`).
- Use a generic name when the event is system-wide (`"redstone"`, `"terminate"`).

---

## Argument Types

Arguments are passed as `Object[]`. The Lua runtime maps Java types as follows:

| Java type | Lua type |
|---|---|
| `String` | `string` |
| `Double` / `double` | `number` |
| `Boolean` / `boolean` | `boolean` |
| `Map<Object,Object>` | `table` |
| `byte[]` | `string` (binary) |
| `null` / omitted | `nil` |

Pass `null` instead of an empty array when there are no arguments (e.g. `queueEvent("redstone", null)`).

---

## Pattern 1 — Event from a Peripheral

Use this when the event is fired by something that is attached to a specific computer via `IPeripheral.attach`.

### 1a. Single-computer peripheral

The peripheral holds one `IComputerAccess` (set in `attach`, cleared in `detach`).

```java
// MyPeripheral.java
public class MyPeripheral implements IPeripheral {

    private IComputerAccess m_computer = null;

    @Override
    public synchronized void attach(IComputerAccess computer) {
        m_computer = computer;
    }

    @Override
    public synchronized void detach(IComputerAccess computer) {
        m_computer = null;
    }

    // Called from server-side logic (e.g. a tile entity tick, a Forge event handler)
    public synchronized void onSomethingHappened(String detail, double value) {
        if (m_computer != null) {
            m_computer.queueEvent("my_event", new Object[] {
                m_computer.getAttachmentName(), // side/name the peripheral is on
                detail,
                value
            });
        }
    }
    // ...
}
```

Reference: `ModemPeripheral.receive` → fires `"modem_message"`.

### 1b. Multi-computer tile entity (broadcast)

When a tile entity can be attached to multiple computers at once, keep a `Set<IComputerAccess>` and snapshot it before iterating to avoid holding the lock while calling `queueEvent`.

```java
// MyTile.java
public class MyTile extends TileGeneric implements IPeripheralTile {

    private final Set<IComputerAccess> m_computers = new HashSet<>();

    synchronized void attachComputer(IComputerAccess computer) {
        m_computers.add(computer);
    }

    synchronized void detachComputer(IComputerAccess computer) {
        m_computers.remove(computer);
    }

    // Called from a manager / Forge event handler on the server thread
    void fireMyEvent(String detail) {
        Set<IComputerAccess> snapshot;
        synchronized (this) {
            snapshot = new HashSet<>(m_computers);
        }
        for (IComputerAccess computer : snapshot) {
            computer.queueEvent("my_event", new Object[] { detail });
        }
    }
}
```

Reference: `TileChatBox.queueEvent` → broadcasts `"chat"`, `"death"`, `"command"`.

---

## Pattern 2 — Event from a Core API

Use this when the event is fired from an `ILuaAPI` implementation inside `dan200.computercraft.core`, where you have access to `IAPIEnvironment` rather than `IComputerAccess`.

```java
// MyAPI.java (implements ILuaAPI)
public class MyAPI implements ILuaAPI {

    private final IAPIEnvironment m_environment;

    public MyAPI(IAPIEnvironment environment) {
        m_environment = environment;
    }

    // Fire from a background thread, a callback, or advance()
    private void onDataReceived(String url, String payload) {
        m_environment.queueEvent("my_data", new Object[] { url, payload });
    }
    // ...
}
```

Reference: `WebSocketRequest.onMessage` → fires `"websocket_message"`.

---

## Existing Events Reference

| Event name | Arguments | Fired from |
|---|---|---|
| `modem_message` | `side, channel, replyChannel, payload, distance` | `ModemPeripheral` |
| `chat` | `playerName, message` | `TileChatBox` via `ChatBoxManager` |
| `death` | `victimName, attackerName, cause` | `TileChatBox` via `ChatBoxManager` |
| `command` | `playerName, args` | `TileChatBox` via `ChatBoxManager` |
| `speaker_audio_empty` | *(none)* | `TileSpeaker`, `PortableSpeakerPeripheral` |
| `websocket_message` | `url, payload, isBinary` | `WebSocketRequest` |
| `websocket_closed` | `url` | `WebSocketRequest` |
| `task_complete` | `taskId, success [, errorMsg]` | `CobaltMachine`, `DelayedTasks` |
| `redstone` | *(none)* | `Computer` (redstone change detection) |
| `key` | `keyCode, isRepeat` | `WidgetTerminal` |
| `mouse_click` | `button, x, y` | `WidgetTerminal` |

---

## Lua-side: Listening with `os.pullEvent`

Once Java fires the event, any running Lua program on that computer can receive it.

### Basic usage

```lua
-- Block until the specific event arrives
local event, side, channel, replyChannel, message, distance = os.pullEvent("modem_message")
print("Got message on channel " .. channel .. ": " .. tostring(message))
```

### Event loop

```lua
-- Handle multiple event types in a loop
while true do
    local event, a, b, c = os.pullEvent()

    if event == "modem_message" then
        local side, channel, replyChannel, payload, distance = a, b, c, ...
        print("Modem: " .. tostring(payload))

    elseif event == "chat" then
        local playerName, message = a, b
        print(playerName .. " said: " .. message)

    elseif event == "terminate" then
        break
    end
end
```

### Filtering with `os.pullEventRaw`

Use `os.pullEventRaw` to receive the `"terminate"` event without it being converted into an error (e.g. for daemons that must clean up on termination):

```lua
while true do
    local event, a = os.pullEventRaw()
    if event == "terminate" then
        print("Shutting down cleanly")
        break
    end
    -- handle other events...
end
```

### Timeout with `os.startTimer`

```lua
local timer = os.startTimer(5)  -- fire "timer" event after 5 seconds

while true do
    local event, arg = os.pullEvent()
    if event == "my_event" then
        -- handle it
        break
    elseif event == "timer" and arg == timer then
        print("Timed out waiting for my_event")
        break
    end
end
```

---

## Checklist for Adding a New Event

1. **Choose a name** — snake_case, prefixed with your peripheral/API name.
2. **Define the argument list** — document the type and meaning of each argument.
3. **Fire the event** — call `computer.queueEvent(name, args)` (peripheral) or `environment.queueEvent(name, args)` (core API) at the right point in your Java logic.
4. **Guard with a null/state check** — never call `queueEvent` when the computer reference may be `null` or when the computer is not in a running state.
5. **Snapshot the computer set** — if broadcasting to multiple computers, copy the set before releasing the lock.
6. **Add a test** — verify that `queueEvent` is called with the correct event name and arguments using Mockito (`verify(computer).queueEvent("my_event", expectedArgs)`). Reference: `ChatBoxTest`.
7. **Document in Lua** — add an entry in the relevant `rom/help/` file if the event is user-facing.

---

*Reference implementations: `ModemPeripheral` (single-computer peripheral), `TileChatBox` + `ChatBoxManager` (multi-computer broadcast), `WebSocketRequest` (core API background thread).*

