package dan200.computercraft.compat.customnpcs.peripheral.npcinterface;

import java.util.List;
import java.util.Map;

import dan200.computercraft.api.peripheral.IComputerAccess;
import noppes.npcs.api.entity.ICustomNpc;

/**
 * Abstraction over the three possible carriers of an NPC Interface peripheral:
 * a placed block ({@link TileNpcInterface}), a turtle upgrade, or a pocket
 * computer upgrade.
 *
 * <p>
 * All implementations must be thread-safe for the methods called from Forge's
 * event thread ({@link #queueNpcEvent}) as well as the main server thread.
 * </p>
 */
public interface INpcInterfaceHolder {

    // -------------------------------------------------------------------------
    // UUID / link state
    // -------------------------------------------------------------------------

    /**
     * Returns the first linked NPC UUID (insertion order), or {@code null} if
     * no NPCs are linked.
     */
    String getLinkedUUID();

    /**
     * Returns the first linked NPC's cached display name, or {@code null} if
     * no NPCs are linked.
     */
    String getLinkedName();

    /**
     * Returns a snapshot copy of all linked NPCs as an ordered UUID → name map.
     */
    Map<String, String> getLinkedNpcs();

    /**
     * Replaces the entire link set with a single NPC, or clears all links if
     * both arguments are {@code null}.
     * Implementations must update {@link NpcInterfaceManager} registration and
     * persist the new values.
     *
     * @param uuid linked NPC UUID, or {@code null} to clear
     * @param name cached NPC display name, or {@code null}
     */
    void setLink(String uuid, String name);

    /**
     * Adds a single NPC to the linked set. No-op if the UUID is already linked.
     * Implementations must update {@link NpcInterfaceManager} and persist.
     *
     * @param uuid NPC UUID to add
     * @param name cached display name
     */
    void addLink(String uuid, String name);

    /**
     * Removes a single NPC from the linked set. No-op if not linked.
     * Implementations must update {@link NpcInterfaceManager} and persist.
     *
     * @param uuid NPC UUID to remove
     */
    void removeLink(String uuid);

    /**
     * Removes all linked NPCs.
     * Implementations must update {@link NpcInterfaceManager} and persist.
     */
    void clearLinks();

    // -------------------------------------------------------------------------
    // Position (scan origin for link / linkNearest / scanNpcs)
    // -------------------------------------------------------------------------

    double getPositionX();

    double getPositionY();

    double getPositionZ();

    // -------------------------------------------------------------------------
    // Computer tracking
    // -------------------------------------------------------------------------

    void attachComputer(IComputerAccess computer);

    void detachComputer(IComputerAccess computer);

    // -------------------------------------------------------------------------
    // NPC resolution
    // -------------------------------------------------------------------------

    /**
     * Resolves the first linked NPC entity. <strong>Must be called on the main
     * thread.</strong>
     *
     * @return the live {@link ICustomNpc}, or {@code null} if not found or CNPC
     *         is absent.
     */
    ICustomNpc<?> resolveNpc();

    /**
     * Resolves all currently loaded linked NPC entities.
     * <strong>Must be called on the main thread.</strong>
     *
     * @return a list of live {@link ICustomNpc} instances (may be empty).
     */
    List<ICustomNpc<?>> resolveNpcs();

    // -------------------------------------------------------------------------
    // Event dispatch (called by NpcInterfaceManager from any thread)
    // -------------------------------------------------------------------------

    /**
     * Queues a CC event to every computer currently attached to this holder.
     */
    void queueNpcEvent(String event, Object... params);
}
