# Plan: ChatBox × CustomNPCs Soft Integration

Forward NPC-fired CC events to the ChatBox (block, turtle, pocket).
All new behavior is **gated behind `AbstractNpcAPI.IsAvailable()`** — nothing is exposed or registered
if CustomNPCs is absent. The existing `say`/`tell` behavior and indices are untouched.
No new Lua methods are added to the ChatBox peripheral.

> **Status: Implemented and verified in production.**

---

## Key Implementation Finding: FML EventBus Dispatches by Concrete Class

FML's `EventBus` resolves handlers by walking the **concrete class hierarchy** of the fired event.
It does **not** walk Java interfaces. CustomNPCs defines:

- Public API interfaces: `noppes.npcs.api.event.INpcEvent.*`  (e.g. `INpcEvent.InteractEvent`)
- Concrete fired classes: `noppes.npcs.scripted.event.NpcEvent.*` (e.g. `NpcEvent.InteractEvent`)

The concrete classes implement the interfaces but are in a separate internal package.
**`@SubscribeEvent` handlers must declare the concrete `NpcEvent.*` parameter type**, not the
`INpcEvent.*` interface, or they will never be invoked.

---

## New CC Events (forwarded from CustomNPCs)

| Event name          | Parameters                                  | Source concrete class                                           |
|---------------------|---------------------------------------------|-----------------------------------------------------------------|
| `npc_interact`      | `playerName, npcName`                       | `noppes.npcs.scripted.event.NpcEvent$InteractEvent`             |
| `npc_dialog`        | `playerName, npcName, dialogId, optionId`   | `noppes.npcs.scripted.event.NpcEvent$DialogEvent`               |
| `npc_dialog_closed` | `playerName, npcName, dialogId, optionId`   | `noppes.npcs.scripted.event.NpcEvent$DialogClosedEvent`         |
| `npc_died`          | `npcName, killerName, damageType`           | `noppes.npcs.scripted.event.NpcEvent$DiedEvent`                 |
| `npc_spawned`       | `npcName`                                   | `noppes.npcs.scripted.event.NpcEvent$InitEvent`                 |
| `npc_damaged`       | `npcName, attackerName, damage, damageType` | `noppes.npcs.scripted.event.NpcEvent$DamagedEvent`              |
| `npc_killed_entity` | `npcName, entityName`                       | `noppes.npcs.scripted.event.NpcEvent$KilledEntityEvent`         |

> **`npc_chat` removed from scope.** CustomNPCs does not expose a global "NPC said something"
> Forge event — `ICustomNpc.say()` is a method call, not a fired event. Re-evaluate if a future
> CustomNPCs version adds such a hook.

> **`killerName`/`attackerName`/`entityName`**: resolved via a helper `entityName(IEntity)`:
> - If the source is an `IPlayer`, returns `IPlayer#getName()` (the player's username).
> - If the source is an `ICustomNpc`, returns `ICustomNpc#getName()` (the NPC's display name).
> - Otherwise returns `IEntity#getTypeName()` (e.g. `"Zombie"`, `"Skeleton"`), falling back to `""`
>   if `getTypeName()` itself returns null.
> - A null source (environmental damage — fall, lava, etc.) always returns `""`.
>
> **`damageType`** is the vanilla Minecraft damage source string (e.g. `"player"`, `"fall"`,
> `"lava"`, `"mob"`). When a player kills an NPC, `damageType = "player"` and
> `killerName = "<playerUsername>"`. This is correct behaviour.

> **Excluded events** (too noisy or too low-level for a ChatBox peripheral):
> `CollideEvent` (fires every tick near a player), `TargetEvent`, `TargetLostEvent`,
> `MeleeAttackEvent`, `RangedLaunchedEvent`, `SwingEvent`, `TimerEvent`, `UpdateEvent`.

---

## Implementation Steps

### Step 1 — `IChatBoxReceiver` & `ChatBoxManager`

**File:** `dan200.computercraft.shared.peripheral.chatbox.IChatBoxReceiver`

Add default-method callbacks so existing implementors don't break:

```java
default void onNpcInteractEvent(String playerName, String npcName) {}
default void onNpcDialogEvent(String playerName, String npcName, int dialogId, int optionId) {}
default void onNpcDialogClosedEvent(String playerName, String npcName, int dialogId, int optionId) {}
default void onNpcDiedEvent(String npcName, String killerName, String damageType) {}
```

**File:** `dan200.computercraft.shared.peripheral.chatbox.ChatBoxManager`

Add four new `dispatchNpc*()` methods mirroring the existing `dispatchChat` / `dispatchDeath` pattern
(synchronized snapshot copy, fan-out to all registered receivers):

```java
public static void dispatchNpcInteract(String playerName, String npcName) { ... }
public static void dispatchNpcDialog(String playerName, String npcName, int dialogId, int optionId) { ... }
public static void dispatchNpcDialogClosed(String playerName, String npcName, int dialogId, int optionId) { ... }
public static void dispatchNpcDied(String npcName, String killerName, String damageType) { ... }
```

---

### Step 2 — `CustomNpcChatBoxBridge` (new class)

**File:** `dan200.computercraft.compat.customnpcs.chatbox.CustomNpcChatBoxBridge`

A standalone Forge event listener. **Never referenced directly from the hot path** — only
instantiated after an `AbstractNpcAPI.IsAvailable()` guard.

> **Critical:** handlers use `NpcEvent.*` (concrete classes from `noppes.npcs.scripted.event`),
> **not** `INpcEvent.*` interfaces. FML's EventBus will not dispatch to interface-typed handlers.

```java
package dan200.computercraft.compat.customnpcs.chatbox;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import noppes.npcs.scripted.event.NpcEvent;
import dan200.computercraft.shared.peripheral.chatbox.ChatBoxManager;

public class CustomNpcChatBoxBridge {

    @SubscribeEvent
    public void onNpcInteract(NpcEvent.InteractEvent event) {
        ChatBoxManager.dispatchNpcInteract(event.getPlayer().getName(), event.getNpc().getName());
    }

    @SubscribeEvent
    public void onNpcDialog(NpcEvent.DialogEvent event) {
        ChatBoxManager.dispatchNpcDialog(
            event.getPlayer().getName(), event.getNpc().getName(),
            event.getDialogId(), event.getOptionId());
    }

    @SubscribeEvent
    public void onNpcDialogClosed(NpcEvent.DialogClosedEvent event) {
        ChatBoxManager.dispatchNpcDialogClosed(
            event.getPlayer().getName(), event.getNpc().getName(),
            event.getDialogId(), event.getOptionId());
    }

    @SubscribeEvent
    public void onNpcDied(NpcEvent.DiedEvent event) {
        // IEntity has getTypeName(), not getName(); null source = environmental damage
        String killer = event.getSource() != null ? event.getSource().getTypeName() : "";
        ChatBoxManager.dispatchNpcDied(event.getNpc().getName(), killer, event.getType());
    }
}
```

---

### Step 3 — Register bridge in `ComputerCraft.java`

**File:** `dan200.computercraft.ComputerCraft`

Register in **`FMLPostInitializationEvent`** (not `init`) so that CustomNPCs has completed its own
initialization and `IsAvailable()` reliably reflects the runtime state.

```java
@EventHandler
public void postInit(FMLPostInitializationEvent event) {
    registerCustomNpcCompat();
}

private static void registerCustomNpcCompat() {
    try {
        if (noppes.npcs.api.AbstractNpcAPI.IsAvailable()) {
            noppes.npcs.api.AbstractNpcAPI.Instance()
                .events()
                .register(new CustomNpcChatBoxBridge());
            logger.info("[ComputerCraft] CustomNPCs detected — ChatBox NPC integration enabled.");
        }
    } catch (Throwable t) {
        logger.debug("[ComputerCraft] CustomNPCs not available: {}", t.getMessage());
    }
}
```

Confirm registration succeeded by checking the server log for:
```
[ComputerCraft] CustomNPCs detected — ChatBox NPC integration enabled.
```

---

### Step 4 — Forward NPC events in `TileChatBox` and `PortableChatBoxPeripheral`

No changes are needed to `ChatBoxPeripheral` or `PortableChatBoxPeripheral`'s `getMethodNames()` or
`callMethod()` — the method list stays exactly `{ "say", "tell" }` regardless of whether CNPC is
installed.

Override the new `IChatBoxReceiver` default methods in both classes:

```java
@Override
public void onNpcInteractEvent(String playerName, String npcName) {
    queueEvent("npc_interact", playerName, npcName);
}

@Override
public void onNpcDialogEvent(String playerName, String npcName, int dialogId, int optionId) {
    queueEvent("npc_dialog", playerName, npcName, dialogId, optionId);
}

@Override
public void onNpcDialogClosedEvent(String playerName, String npcName, int dialogId, int optionId) {
    queueEvent("npc_dialog_closed", playerName, npcName, dialogId, optionId);
}

@Override
public void onNpcDiedEvent(String npcName, String killerName, String damageType) {
    queueEvent("npc_died", npcName, killerName, damageType);
}
```

---

### Step 5 — Tests

**File:** `src/test/java/dan200/computercraft/shared/peripheral/chatbox/ChatBoxTest.java`

Unit tests for `ChatBoxManager.dispatchNpc*()` fan-out (mock `TileChatBox`, verify `queueEvent` calls).

**File:** `src/test/java/dan200/computercraft/shared/peripheral/chatbox/CustomNpcChatBoxBridgeTest.java`

Unit tests for `CustomNpcChatBoxBridge` handlers. Mock **concrete** `NpcEvent.*` classes (not
`INpcEvent.*` interfaces) — this mirrors the real dispatch path and ensures the handler signatures
are correct.

**Lua integration tests:** `lua/tests/customnpcs/`
- `test_npc_interact.lua` — right-click an NPC, verify `npc_interact` fires
- `test_npc_dialog.lua` — open and close a dialog, verify both events fire
- `test_npc_died.lua` — kill an NPC, verify `npc_died` fires
- `test_all_npc_events.lua` — smoke test for all four events in one session

---

## Files Changed

| File                                                                  | Change type                                        |
|-----------------------------------------------------------------------|----------------------------------------------------|
| `shared/peripheral/chatbox/IChatBoxReceiver.java`                     | Add 4 default methods                              |
| `shared/peripheral/chatbox/ChatBoxManager.java`                       | Add 4 `dispatchNpc*()` methods                     |
| `compat/customnpcs/chatbox/CustomNpcChatBoxBridge.java`               | **New** — Forge event listener (concrete types)    |
| `shared/peripheral/chatbox/TileChatBox.java`                          | Implement new `IChatBoxReceiver` callbacks         |
| `shared/peripheral/chatbox/PortableChatBoxPeripheral.java`            | Implement new `IChatBoxReceiver` callbacks         |
| `ComputerCraft.java`                                                  | Register bridge in `postInit`; add import          |
| `src/test/.../ChatBoxTest.java`                                       | Add NPC dispatch unit tests                        |
| `src/test/.../CustomNpcChatBoxBridgeTest.java`                        | **New** — bridge handler unit tests                |
| `lua/tests/customnpcs/*.lua`                                          | **New** — in-game Lua integration tests            |
| `dependencies.gradle`                                                 | Add `runtimeOnly` + `testImplementation` for CNPC  |

## Files NOT Changed

- `BlockChatBox.java` — no block-level changes needed
- `ChatBoxPeripheral.java` — method list unchanged; no new Lua methods
- `TurtleChatBox.java` — delegates entirely to `PortableChatBoxPeripheral`; no direct changes
- `PocketChatBoxPeripheral.java` — same delegation; no direct changes

---

## Lessons Learned: FML EventBus and Interface-Typed Handlers

> **This applies to all future CustomNPCs event integrations.**

FML's `EventBus` (1.7.10) resolves `@SubscribeEvent` handlers using the parameter's **class**, not
its interfaces. The bus walks the superclass chain of the fired event (`NpcEvent →
CustomNPCsEvent → Event`) when looking up registered listeners. Interfaces in that chain are
**never consulted**.

CustomNPCs separates its event API into two layers:
- `noppes.npcs.api.event.INpcEvent.*` — public interfaces (what you'd expect to use)
- `noppes.npcs.scripted.event.NpcEvent.*` — internal concrete classes (what is actually fired)

Always declare `@SubscribeEvent` parameters using the concrete `noppes.npcs.scripted.event.*` class.
When writing unit tests, mock the concrete class (Mockito bypasses constructors automatically).
