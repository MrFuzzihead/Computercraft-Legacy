package dan200.computercraft.api;

import java.lang.reflect.Method;

import net.minecraft.world.World;

import dan200.computercraft.api.filesystem.IMount;
import dan200.computercraft.api.filesystem.IWritableMount;
import dan200.computercraft.api.media.IMediaProvider;
import dan200.computercraft.api.peripheral.IPeripheralProvider;
import dan200.computercraft.api.permissions.ITurtlePermissionProvider;
import dan200.computercraft.api.pocket.IPocketUpgrade;
import dan200.computercraft.api.redstone.IBundledRedstoneProvider;
import dan200.computercraft.api.turtle.ITurtleUpgrade;
import dan200.computercraft.api.turtle.TurtleRefuelHandler;

public final class ComputerCraftAPI {

    private static boolean ccSearched = false;
    private static Class computerCraft = null;
    private static Method computerCraft_getVersion = null;
    private static Method computerCraft_createUniqueNumberedSaveDir = null;
    private static Method computerCraft_createSaveDirMount = null;
    private static Method computerCraft_createResourceMount = null;
    private static Method computerCraft_registerPeripheralProvider = null;
    private static Method computerCraft_registerTurtleUpgrade = null;
    private static Method computerCraft_registerBundledRedstoneProvider = null;
    private static Method computerCraft_getDefaultBundledRedstoneOutput = null;
    private static Method computerCraft_registerMediaProvider = null;
    private static Method computerCraft_registerPermissionProvider = null;
    private static Method computerCraft_registerRefuelHandler = null;
    private static Method computerCraft_registerPocketUpgrade = null;

    public static boolean isInstalled() {
        findCC();
        return computerCraft != null;
    }

    public static String getInstalledVersion() {
        findCC();
        if (computerCraft_getVersion != null) {
            try {
                return (String) computerCraft_getVersion.invoke(null);
            } catch (Exception var1) {}
        }

        return "";
    }

    public static String getAPIVersion() {
        return "2.0";
    }

    public static int createUniqueNumberedSaveDir(World world, String parentSubPath) {
        findCC();
        if (computerCraft_createUniqueNumberedSaveDir != null) {
            try {
                return (Integer) computerCraft_createUniqueNumberedSaveDir.invoke(null, world, parentSubPath);
            } catch (Exception var3) {}
        }

        return -1;
    }

    public static IWritableMount createSaveDirMount(World world, String subPath, long capacity) {
        findCC();
        if (computerCraft_createSaveDirMount != null) {
            try {
                return (IWritableMount) computerCraft_createSaveDirMount.invoke(null, world, subPath, capacity);
            } catch (Exception var5) {}
        }

        return null;
    }

    public static IMount createResourceMount(Class modClass, String domain, String subPath) {
        findCC();
        if (computerCraft_createResourceMount != null) {
            try {
                return (IMount) computerCraft_createResourceMount.invoke(null, modClass, domain, subPath);
            } catch (Exception var4) {}
        }

        return null;
    }

    public static void registerPeripheralProvider(IPeripheralProvider handler) {
        findCC();
        if (computerCraft_registerPeripheralProvider != null) {
            try {
                computerCraft_registerPeripheralProvider.invoke(null, handler);
            } catch (Exception var2) {}
        }
    }

    public static void registerTurtleUpgrade(ITurtleUpgrade upgrade) {
        if (upgrade != null) {
            findCC();
            if (computerCraft_registerTurtleUpgrade != null) {
                try {
                    computerCraft_registerTurtleUpgrade.invoke(null, upgrade);
                } catch (Exception var2) {}
            }
        }
    }

    public static void registerBundledRedstoneProvider(IBundledRedstoneProvider handler) {
        findCC();
        if (computerCraft_registerBundledRedstoneProvider != null) {
            try {
                computerCraft_registerBundledRedstoneProvider.invoke(null, handler);
            } catch (Exception var2) {}
        }
    }

    public static int getBundledRedstoneOutput(World world, int x, int y, int z, int side) {
        findCC();
        if (computerCraft_getDefaultBundledRedstoneOutput != null) {
            try {
                return (Integer) computerCraft_getDefaultBundledRedstoneOutput.invoke(null, world, x, y, z, side);
            } catch (Exception var6) {}
        }

        return -1;
    }

    public static void registerMediaProvider(IMediaProvider handler) {
        findCC();
        if (computerCraft_registerMediaProvider != null) {
            try {
                computerCraft_registerMediaProvider.invoke(null, handler);
            } catch (Exception var2) {}
        }
    }

    public static void registerPermissionProvider(ITurtlePermissionProvider handler) {
        findCC();
        if (computerCraft_registerPermissionProvider != null) {
            try {
                computerCraft_registerPermissionProvider.invoke(null, handler);
            } catch (Exception var2) {}
        }
    }

    /**
     * Register a source of turtle fuel other than an item's furnace burn time, such as a charged battery. Handlers are
     * consulted in registration order by {@code turtle.refuel()}, and the first to accept an item supplies its fuel.
     *
     * @param handler the handler to add
     * @see TurtleRefuelHandler#refuel(dan200.computercraft.api.turtle.ITurtleAccess, net.minecraft.item.ItemStack, int,
     *      int)
     */
    public static void registerRefuelHandler(TurtleRefuelHandler handler) {
        if (handler != null) {
            findCC();
            if (computerCraft_registerRefuelHandler != null) {
                try {
                    computerCraft_registerRefuelHandler.invoke(null, handler);
                } catch (Exception var2) {}
            }
        }
    }

    /**
     * Register a peripheral that can be fitted to the back of a pocket computer.
     *
     * <p>
     * The upgrade's id must be at least {@link IPocketUpgrade#FIRST_MOD_UPGRADE_ID}; ids below that belong to
     * ComputerCraft and are stored in saved items. Registering a duplicate id is an error.
     * </p>
     *
     * @param upgrade the upgrade to add
     * @see IPocketUpgrade
     */
    public static void registerPocketUpgrade(IPocketUpgrade upgrade) {
        if (upgrade != null) {
            findCC();
            if (computerCraft_registerPocketUpgrade != null) {
                try {
                    computerCraft_registerPocketUpgrade.invoke(null, upgrade);
                } catch (Exception var2) {}
            }
        }
    }

    private static void findCC() {
        if (!ccSearched) {
            try {
                computerCraft = Class.forName("dan200.computercraft.ComputerCraft");
                computerCraft_getVersion = findCCMethod("getVersion", new Class[0]);
                computerCraft_createUniqueNumberedSaveDir = findCCMethod(
                    "createUniqueNumberedSaveDir",
                    new Class[] { World.class, String.class });
                computerCraft_createSaveDirMount = findCCMethod(
                    "createSaveDirMount",
                    new Class[] { World.class, String.class, long.class });
                computerCraft_createResourceMount = findCCMethod(
                    "createResourceMount",
                    new Class[] { Class.class, String.class, String.class });
                computerCraft_registerPeripheralProvider = findCCMethod(
                    "registerPeripheralProvider",
                    new Class[] { IPeripheralProvider.class });
                computerCraft_registerTurtleUpgrade = findCCMethod(
                    "registerTurtleUpgrade",
                    new Class[] { ITurtleUpgrade.class });
                computerCraft_registerBundledRedstoneProvider = findCCMethod(
                    "registerBundledRedstoneProvider",
                    new Class[] { IBundledRedstoneProvider.class });
                computerCraft_getDefaultBundledRedstoneOutput = findCCMethod(
                    "getDefaultBundledRedstoneOutput",
                    new Class[] { World.class, int.class, int.class, int.class, int.class });
                computerCraft_registerMediaProvider = findCCMethod(
                    "registerMediaProvider",
                    new Class[] { IMediaProvider.class });
                computerCraft_registerPermissionProvider = findCCMethod(
                    "registerPermissionProvider",
                    new Class[] { ITurtlePermissionProvider.class });
                computerCraft_registerRefuelHandler = findCCMethod(
                    "registerRefuelHandler",
                    new Class[] { TurtleRefuelHandler.class });
                computerCraft_registerPocketUpgrade = findCCMethod(
                    "registerPocketUpgrade",
                    new Class[] { IPocketUpgrade.class });
            } catch (Exception var4) {
                // Do not reference ComputerCraft.logger here: this lookup also runs when the mod is absent.
                org.apache.logging.log4j.LogManager.getLogger("ComputerCraftAPI")
                    .warn("ComputerCraftAPI: ComputerCraft not found.", var4);
            } finally {
                ccSearched = true;
            }
        }
    }

    private static Method findCCMethod(String name, Class[] args) {
        try {
            return computerCraft != null ? computerCraft.getMethod(name, args) : null;
        } catch (NoSuchMethodException var3) {
            org.apache.logging.log4j.LogManager.getLogger("ComputerCraftAPI")
                .warn("ComputerCraftAPI: ComputerCraft method " + name + " not found.", var3);
            return null;
        }
    }
}
