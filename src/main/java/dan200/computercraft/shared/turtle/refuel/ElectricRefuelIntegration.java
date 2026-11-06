package dan200.computercraft.shared.turtle.refuel;

import cpw.mods.fml.common.Loader;
import dan200.computercraft.ComputerCraft;

/**
 * Class-load isolation shim for the electric turtle-refuel integrations.
 *
 * <p>
 * IC2 and CoFH Core are compile-only dependencies, so their API classes are absent at runtime unless the player
 * installed them. This class contains no {@code ic2.*} or {@code cofh.*} imports of its own: it checks for the mod,
 * and only then constructs a handler class that does. A game without them therefore never has to resolve those classes,
 * which is the difference between "electric refuelling is unavailable" and a {@link NoClassDefFoundError} while loading
 * ComputerCraft.
 * </p>
 *
 * <p>
 * Same shim pattern as {@code dan200.computercraft.shared.peripheral.generic.energy.rf.RFIntegration}.
 * </p>
 */
public final class ElectricRefuelIntegration {

    /** IC2's mod id; provides {@code ic2.api.item.*}. */
    private static final String IC2_MODID = "IC2";

    /** CoFH Core's mod id; provides {@code cofh.api.energy.*}. */
    private static final String COFH_MODID = "CoFHCore";

    private ElectricRefuelIntegration() {}

    /**
     * Registers whichever electric refuel handlers this instance can use. Safe to call when neither mod is installed,
     * and safe to call outside a running game.
     */
    public static void register() {
        if (isLoaded(IC2_MODID)) {
            try {
                ComputerCraft.registerRefuelHandler(new IC2RefuelHandler());
            } catch (RuntimeException | LinkageError e) {
                // A mod with IC2's id but not its item API, typically a fork. Refuelling simply stays furnace-only.
                ComputerCraft.logger.warn(
                    "IC2 is loaded but its electric item API is unusable; "
                        + "turtles will not refuel from IC2 batteries.",
                    e);
            }
        }

        if (isLoaded(COFH_MODID)) {
            try {
                ComputerCraft.registerRefuelHandler(new RFRefuelHandler());
            } catch (RuntimeException | LinkageError e) {
                ComputerCraft.logger.warn(
                    "CoFH Core is loaded but its energy item API is unusable; "
                        + "turtles will not refuel from RF items.",
                    e);
            }
        }
    }

    /**
     * Whether an API-providing mod is present. Resolved through {@link Loader} so that the handler classes stay
     * unreferenced when it is not.
     *
     * @param modId the mod id to test
     * @return whether the mod is loaded
     */
    static boolean isLoaded(String modId) {
        try {
            return Loader.isModLoaded(modId);
        } catch (RuntimeException | LinkageError ignored) {
            // FML's Loader is only populated inside a running game, so this fails in unit tests and very early
            // startup. "Not available" is the safe answer: skip the integration rather than resolve absent classes.
            return false;
        }
    }
}
