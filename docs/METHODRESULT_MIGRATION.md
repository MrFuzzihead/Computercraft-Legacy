# Migrating to `MethodResult` (Cobalt 0.9.9)

ComputerCraft's Cobalt 0.9.9 upgrade removed blocking from `ILuaContext`. This document
describes what breaks in a companion mod and how to update it.

## What changed

```java
public interface ILuaContext {

    // REMOVED
    // Object[] pullEvent(String filter) throws LuaException, InterruptedException;
    // Object[] pullEventRaw(String filter) throws InterruptedException;
    // Object[] yield(Object[] args) throws InterruptedException;

    // CHANGED: return type
    MethodResult executeMainThreadTask(ILuaTask task) throws LuaException;

    // unchanged
    long issueMainThreadTask(ILuaTask task) throws LuaException;
}
```

New type: `dan200.computercraft.api.lua.MethodResult`.

## Why

`ComputerThread` executes a computer's tasks **serially on a shared worker pool**, one lane per
computer. The old design let a peripheral block its thread inside `pullEvent` until an event
arrived — but that thread is the one that would have delivered the event. Blocking it deadlocks
the computer, so the wait is now a **coroutine suspension** instead: the worker is released
immediately and the Lua call is resumed from `handleEvent` when a matching event arrives.

The practical consequence is that **peripheral code can no longer wait by itself**, and no longer
needs a retry loop.

## Before

```java
@Override
public Object[] callMethod(IComputerAccess computer, ILuaContext context, int method, Object[] args)
    throws LuaException, InterruptedException {

    long id = context.issueMainThreadTask(task);
    Object[] response;
    do {
        response = context.pullEvent("my_event");
    } while (response.length < 3 || !id.equals(response[1]));

    return new Object[] { response[2] };
}
```

## After

```java
@Override
public Object[] callMethod(IComputerAccess computer, ILuaContext context, int method, Object[] args)
    throws LuaException {

    long id = context.issueMainThreadTask(task);
    return new Object[] { MethodResult.event("my_event", id, 2) };
}
```

No loop, no `InterruptedException`, and the bridge skips events that are not yours.

## Signalling a wait

A peripheral signals a suspension by returning a **single-element array whose only element is a
`MethodResult`**. The bridge (`CobaltMachine.CobaltCallback`) recognises the sentinel; anything
else is returned to Lua as ordinary values.

| Factory | Use when |
|---|---|
| `MethodResult.of(Object... values)` | No waiting. Lets a method return uniformly. |
| `MethodResult.task(String event, long id)` | The event is `(event, id, success, ...values)`. A `false` success flag becomes a Lua error carrying `values[0]`. This is the shape `executeMainThreadTask` uses. |
| `MethodResult.event(String event, long id, int offset)` | The event is `(event, id, ...values)` with no success flag; everything from `offset` onwards is returned. |

## Do I need to change anything?

**No**, if your peripheral:
- only *reads* the context (e.g. `context.getComputer()`, or your own accessor methods), or
- returns values directly without waiting, or
- uses `issueMainThreadTask` for fire-and-forget work.

**Yes**, if it calls `pullEvent`, `pullEventRaw`, `yield`, or relies on the *return values* of
`executeMainThreadTask`. These no longer compile, which is deliberate: a compile error points at
the line to fix, where a runtime failure would surface later as a computer that silently stops
responding.

## Testing

Unit tests that call `callMethod` directly bypass the bridge and will see the sentinel. Wrap the
result:

```java
Object[] result = LuaResults.unwrap(peripheral.callMethod(computer, context, METHOD, args));
```

`unwrap` returns the values for an immediate `MethodResult`, and fails the test with a clear
message for a suspending one — a unit test has no event loop to resume it.

## Known limitations in this stage

- **One wait per call.** A peripheral can suspend at most once per invocation. Chained waits — sleep
  then act, or a wait with a `finally` that must run on completion — are not yet expressible. This
  is why `WebSocketHandle.receive`, which races a timeout and cancels a timer in a `finally`,
  still throws `UnsupportedOperationException`.
- **`DelayedTasks.cancel(id)` on a failed task** is not called; the bridge raises the error instead.
