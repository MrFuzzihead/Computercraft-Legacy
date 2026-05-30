package dan200.computercraft.compat.customnpcs.peripheral.npcinterface;

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

    /** Returns the stored NPC UUID, or {@code null} if unlinked. */
    String getLinkedUUID();

    /** Returns the cached NPC display name, or {@code null} if unlinked. */
    String getLinkedName();

    /**
     * Sets (or clears) the linked NPC UUID and display name.
     * Implementations must update {@link NpcInterfaceManager} registration and
     * persist the new values.
     *
     * @param uuid linked NPC UUID, or {@code null} to unlink
     * @param name cached NPC display name, or {@code null} to clear
     */
    void setLink(String uuid, String name);

    // -------------------------------------------------------------------------
    // Position (scan origin for link / linkNearest)
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
     * Resolves the live NPC entity. <strong>Must be called on the main
     * thread.</strong>
     *
     * @return the live {@link ICustomNpc}, or {@code null} if not found or CNPC
     *         is absent.
     */
    ICustomNpc<?> resolveNpc();

    // -------------------------------------------------------------------------
    // Event dispatch (called by NpcInterfaceManager from any thread)
    // -------------------------------------------------------------------------

    /**
     * Queues a CC event to every computer currently attached to this holder.
     */
    void queueNpcEvent(String event, Object... params);
}
