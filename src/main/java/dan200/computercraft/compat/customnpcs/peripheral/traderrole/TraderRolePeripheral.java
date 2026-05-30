package dan200.computercraft.compat.customnpcs.peripheral.traderrole;

import java.util.HashMap;
import java.util.Map;

import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import noppes.npcs.api.AbstractNpcAPI;
import noppes.npcs.api.entity.IPlayer;
import noppes.npcs.api.item.IItemStack;
import noppes.npcs.api.roles.IRoleTrader;

/**
 * Peripheral for the NPC Trader Role block.
 *
 * <p>
 * Methods 0–4 (link management) work without CustomNPCs installed.
 * Methods 5–15 require CustomNPCs to be installed AND the peripheral to be
 * linked to a live NPC that has a Trader role (role type 1). All NPC-facing
 * calls run on the Minecraft main thread via
 * {@link ILuaContext#executeMainThreadTask}.
 * </p>
 *
 * <p>
 * This peripheral is entirely poll-based — CustomNPCs fires no Forge events
 * for trades, so reactive automation requires polling (e.g. looping on
 * {@code getPurchaseNum}).
 * </p>
 *
 * <p>
 * <strong>Note on stock management:</strong> The version of CustomNPCs bundled
 * with this mod does not expose stock-system methods ({@code isStockEnabled},
 * {@code getMaxStock}, etc.) through {@link IRoleTrader}. Those methods are
 * unavailable and are therefore not exposed through this peripheral.
 * </p>
 */
public class TraderRolePeripheral implements IPeripheral {

    // Methods 0–4: link management (always available)
    // Methods 5–15: trader role operations (require CNPC + link)
    static final String[] METHOD_NAMES = { "link", // 0
        "linkNearest", // 1
        "unlink", // 2
        "isLinked", // 3
        "getLinkedName", // 4
        "getSellOption", // 5
        "getCurrency", // 6
        "setSellOption", // 7
        "removeSellOption", // 8
        "isSlotEnabled", // 9
        "enableSlot", // 10
        "disableSlot", // 11
        "getPurchaseNum", // 12
        "resetPurchaseNum", // 13
        "getMarket", // 14
        "setMarket", // 15
    };

    private final TileTraderRole m_tile;

    public TraderRolePeripheral(TileTraderRole tile) {
        this.m_tile = tile;
    }

    @Override
    public String getType() {
        return "npc_trader";
    }

    @Override
    public String[] getMethodNames() {
        return METHOD_NAMES;
    }

    @Override
    public void attach(IComputerAccess computer) {
        m_tile.attachComputer(computer);
    }

    @Override
    public void detach(IComputerAccess computer) {
        m_tile.detachComputer(computer);
    }

    @Override
    public boolean equals(IPeripheral other) {
        return other instanceof TraderRolePeripheral && ((TraderRolePeripheral) other).m_tile == m_tile;
    }

    @Override
    public Object[] callMethod(IComputerAccess computer, ILuaContext context, int method, Object[] args)
        throws LuaException, InterruptedException {

        switch (method) {

            // ---- Link management (0–4) -----------------------------------
            case 0:
                return methodLink(context, args);
            case 1:
                return methodLinkNearest(context, args);
            case 2: {
                m_tile.setLink(null, null);
                return null;
            }
            case 3:
                return new Object[] { m_tile.getLinkedUUID() != null };
            case 4:
                return new Object[] { m_tile.getLinkedName() };

            // ---- Trade slots (5–8) ---------------------------------------
            case 5: { // getSellOption(slot) → ItemTable|nil
                int slot = requireSlot(args, 0);
                return trader(context, tr -> {
                    IItemStack item = tr.getSellOption(slot);
                    return new Object[] { item != null ? itemTable(item) : null };
                });
            }
            case 6: { // getCurrency(slot) → ItemTable, ItemTable
                int slot = requireSlot(args, 0);
                return trader(context, tr -> {
                    IItemStack[] cur = tr.getCurrency(slot);
                    return new Object[] { cur != null && cur.length > 0 && cur[0] != null ? itemTable(cur[0]) : null,
                        cur != null && cur.length > 1 && cur[1] != null ? itemTable(cur[1]) : null, };
                });
            }
            case 7: { // setSellOption(slot, soldName, soldCount, cur1Name, cur1Count [, cur2Name, cur2Count])
                int slot = requireSlot(args, 0);
                String soldName = requireString(args, 1, "soldName");
                int soldCount = requireInt(args, 2, "soldCount");
                String cur1Name = requireString(args, 3, "currency1Name");
                int cur1Count = requireInt(args, 4, "currency1Count");
                boolean hasCur2 = args.length > 6 && args[5] instanceof String && args[6] instanceof Number;
                String cur2Name = hasCur2 ? (String) args[5] : null;
                int cur2Count = hasCur2 ? ((Number) args[6]).intValue() : 0;
                return trader(context, tr -> {
                    AbstractNpcAPI api = AbstractNpcAPI.Instance();
                    if (api == null) throw new LuaException("CustomNPCs API unavailable");
                    IItemStack sold = api.createItem(soldName, 0, soldCount);
                    IItemStack cur1 = api.createItem(cur1Name, 0, cur1Count);
                    if (sold == null) throw new LuaException("Unknown item: " + soldName);
                    if (cur1 == null) throw new LuaException("Unknown item: " + cur1Name);
                    if (hasCur2) {
                        IItemStack cur2 = api.createItem(cur2Name, 0, cur2Count);
                        if (cur2 == null) throw new LuaException("Unknown item: " + cur2Name);
                        tr.setSellOption(slot, cur1, cur2, sold);
                    } else {
                        tr.setSellOption(slot, cur1, sold);
                    }
                    return null;
                });
            }
            case 8: { // removeSellOption(slot)
                int slot = requireSlot(args, 0);
                return trader(context, tr -> {
                    tr.removeSellOption(slot);
                    return null;
                });
            }

            // ---- Slot enable/disable (9–11) ------------------------------
            case 9: { // isSlotEnabled(slot [, playerName]) → boolean
                int slot = requireSlot(args, 0);
                String pName = optString(args, 1);
                return trader(context, tr -> {
                    if (pName != null) {
                        return new Object[] { tr.isSlotEnabled(slot, requirePlayer(pName)) };
                    }
                    return new Object[] { tr.isSlotEnabled(slot) };
                });
            }
            case 10: { // enableSlot(slot [, playerName])
                int slot = requireSlot(args, 0);
                String pName = optString(args, 1);
                return trader(context, tr -> {
                    if (pName != null) {
                        tr.enableSlot(slot, requirePlayer(pName));
                    } else {
                        tr.enableSlot(slot);
                    }
                    return null;
                });
            }
            case 11: { // disableSlot(slot [, playerName])
                int slot = requireSlot(args, 0);
                String pName = optString(args, 1);
                return trader(context, tr -> {
                    if (pName != null) {
                        tr.disableSlot(slot, requirePlayer(pName));
                    } else {
                        tr.disableSlot(slot);
                    }
                    return null;
                });
            }

            // ---- Purchase counts (12–13) ---------------------------------
            case 12: { // getPurchaseNum(slot [, playerName]) → number
                int slot = requireSlot(args, 0);
                String pName = optString(args, 1);
                return trader(context, tr -> {
                    if (pName != null) {
                        return new Object[] { tr.getPurchaseNum(slot, requirePlayer(pName)) };
                    }
                    return new Object[] { tr.getPurchaseNum(slot) };
                });
            }
            case 13: { // resetPurchaseNum([slot [, playerName]])
                Integer slot = optInt(args, 0);
                String pName = optString(args, 1);
                return trader(context, tr -> {
                    if (slot == null) {
                        tr.resetPurchaseNum();
                    } else if (pName != null) {
                        tr.resetPurchaseNum(slot, requirePlayer(pName));
                    } else {
                        tr.resetPurchaseNum(slot);
                    }
                    return null;
                });
            }

            // ---- Market (14–15) ------------------------------------------
            case 14:
                return trader(context, tr -> new Object[] { tr.getMarket() });
            case 15: {
                String name = requireString(args, 0, "name");
                return trader(context, tr -> {
                    tr.setMarket(name);
                    return null;
                });
            }

            default:
                return null;
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    @FunctionalInterface
    private interface TraderAction {

        Object[] run(IRoleTrader trader) throws LuaException;
    }

    private Object[] trader(ILuaContext ctx, TraderAction action) throws LuaException, InterruptedException {
        requireCnpc();
        return ctx.executeMainThreadTask(() -> action.run(m_tile.resolveTrader()));
    }

    private void requireCnpc() throws LuaException {
        try {
            if (!AbstractNpcAPI.IsAvailable()) {
                throw new LuaException("CustomNPCs is not installed");
            }
        } catch (LuaException e) {
            throw e;
        } catch (Throwable t) {
            throw new LuaException("CustomNPCs is not installed");
        }
    }

    private Object[] methodLink(ILuaContext context, Object[] args) throws LuaException, InterruptedException {
        requireCnpc();
        String name = requireString(args, 0, "npcName");
        double radius = (args.length > 1 && args[1] instanceof Number) ? ((Number) args[1]).doubleValue() : 16.0;
        final double rSq = radius * radius;
        return context.executeMainThreadTask(() -> {
            AbstractNpcAPI api = AbstractNpcAPI.Instance();
            if (api == null) return new Object[] { false };
            for (noppes.npcs.api.entity.IEntity<?> entity : api.getLoadedEntities()) {
                if (!(entity instanceof noppes.npcs.api.entity.ICustomNpc)) continue;
                noppes.npcs.api.entity.ICustomNpc<?> npc = (noppes.npcs.api.entity.ICustomNpc<?>) entity;
                if (!name.equals(npc.getName())) continue;
                double dx = npc.getX() - (m_tile.xCoord + 0.5);
                double dy = npc.getY() - (m_tile.yCoord + 0.5);
                double dz = npc.getZ() - (m_tile.zCoord + 0.5);
                if (dx * dx + dy * dy + dz * dz <= rSq) {
                    m_tile.setLink(npc.getUniqueID(), npc.getName());
                    return new Object[] { true };
                }
            }
            return new Object[] { false };
        });
    }

    private Object[] methodLinkNearest(ILuaContext context, Object[] args) throws LuaException, InterruptedException {
        requireCnpc();
        double radius = (args.length > 0 && args[0] instanceof Number) ? ((Number) args[0]).doubleValue() : 16.0;
        final double rSq = radius * radius;
        return context.executeMainThreadTask(() -> {
            AbstractNpcAPI api = AbstractNpcAPI.Instance();
            if (api == null) return new Object[] { false };
            noppes.npcs.api.entity.ICustomNpc<?> best = null;
            double bestDist = Double.MAX_VALUE;
            for (noppes.npcs.api.entity.IEntity<?> entity : api.getLoadedEntities()) {
                if (!(entity instanceof noppes.npcs.api.entity.ICustomNpc)) continue;
                noppes.npcs.api.entity.ICustomNpc<?> npc = (noppes.npcs.api.entity.ICustomNpc<?>) entity;
                double dx = npc.getX() - (m_tile.xCoord + 0.5);
                double dy = npc.getY() - (m_tile.yCoord + 0.5);
                double dz = npc.getZ() - (m_tile.zCoord + 0.5);
                double dist = dx * dx + dy * dy + dz * dz;
                if (dist <= rSq && dist < bestDist) {
                    best = npc;
                    bestDist = dist;
                }
            }
            if (best == null) return new Object[] { false };
            m_tile.setLink(best.getUniqueID(), best.getName());
            return new Object[] { true };
        });
    }

    /** Resolves an online player by name. Must be called on the main thread. */
    private static IPlayer<?> requirePlayer(String name) throws LuaException {
        AbstractNpcAPI api = AbstractNpcAPI.Instance();
        if (api != null) {
            IPlayer<?> player = api.getPlayer(name);
            if (player != null) return player;
        }
        throw new LuaException("Player not found or not online: " + name);
    }

    private static Map<String, Object> itemTable(IItemStack item) {
        Map<String, Object> t = new HashMap<>();
        t.put("name", item.getName());
        t.put("displayName", item.getDisplayName());
        t.put("stackSize", item.getStackSize());
        t.put("damage", item.getItemDamage());
        return t;
    }

    private static int requireSlot(Object[] args, int i) throws LuaException {
        if (args.length <= i || !(args[i] instanceof Number)) {
            throw new LuaException("Expected number for slot (0–17)");
        }
        int slot = ((Number) args[i]).intValue();
        if (slot < 0 || slot > 17) {
            throw new LuaException("Slot must be 0–17, got " + slot);
        }
        return slot;
    }

    static String requireString(Object[] args, int i, String name) throws LuaException {
        if (args.length <= i || !(args[i] instanceof String)) {
            throw new LuaException("Expected string for " + name);
        }
        return (String) args[i];
    }

    static int requireInt(Object[] args, int i, String name) throws LuaException {
        if (args.length <= i || !(args[i] instanceof Number)) {
            throw new LuaException("Expected number for " + name);
        }
        return ((Number) args[i]).intValue();
    }

    private static String optString(Object[] args, int i) {
        return (args.length > i && args[i] instanceof String) ? (String) args[i] : null;
    }

    private static Integer optInt(Object[] args, int i) {
        return (args.length > i && args[i] instanceof Number) ? ((Number) args[i]).intValue() : null;
    }
}
