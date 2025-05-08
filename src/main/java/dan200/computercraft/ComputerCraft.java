package dan200.computercraft;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.Mod.Instance;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppedEvent;
import cpw.mods.fml.common.network.FMLEventChannel;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.internal.FMLProxyPacket;
import cpw.mods.fml.relauncher.Side;
import dan200.computercraft.api.filesystem.IMount;
import dan200.computercraft.api.filesystem.IWritableMount;
import dan200.computercraft.api.media.IMedia;
import dan200.computercraft.api.media.IMediaProvider;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.peripheral.IPeripheralProvider;
import dan200.computercraft.api.permissions.ITurtlePermissionProvider;
import dan200.computercraft.api.redstone.IBundledRedstoneProvider;
import dan200.computercraft.api.turtle.ITurtleUpgrade;
import dan200.computercraft.core.filesystem.ComboMount;
import dan200.computercraft.core.filesystem.FileMount;
import dan200.computercraft.core.filesystem.JarMount;
import dan200.computercraft.shared.common.DefaultBundledRedstoneProvider;
import dan200.computercraft.shared.computer.blocks.BlockCommandComputer;
import dan200.computercraft.shared.computer.blocks.BlockComputer;
import dan200.computercraft.shared.computer.blocks.TileComputer;
import dan200.computercraft.shared.computer.core.ClientComputerRegistry;
import dan200.computercraft.shared.computer.core.ComputerFamily;
import dan200.computercraft.shared.computer.core.ServerComputerRegistry;
import dan200.computercraft.shared.media.items.ItemDiskExpanded;
import dan200.computercraft.shared.media.items.ItemDiskLegacy;
import dan200.computercraft.shared.media.items.ItemPrintout;
import dan200.computercraft.shared.media.items.ItemTreasureDisk;
import dan200.computercraft.shared.network.ComputerCraftPacket;
import dan200.computercraft.shared.network.PacketHandler;
import dan200.computercraft.shared.peripheral.common.BlockCable;
import dan200.computercraft.shared.peripheral.common.BlockPeripheral;
import dan200.computercraft.shared.peripheral.diskdrive.TileDiskDrive;
import dan200.computercraft.shared.peripheral.printer.TilePrinter;
import dan200.computercraft.shared.pocket.items.ItemPocketComputer;
import dan200.computercraft.shared.proxy.ICCTurtleProxy;
import dan200.computercraft.shared.proxy.IComputerCraftProxy;
import dan200.computercraft.shared.turtle.blocks.BlockTurtle;
import dan200.computercraft.shared.turtle.blocks.TileTurtle;
import dan200.computercraft.shared.turtle.upgrades.TurtleAxe;
import dan200.computercraft.shared.turtle.upgrades.TurtleCraftingTable;
import dan200.computercraft.shared.turtle.upgrades.TurtleHoe;
import dan200.computercraft.shared.turtle.upgrades.TurtleModem;
import dan200.computercraft.shared.turtle.upgrades.TurtleShovel;
import dan200.computercraft.shared.turtle.upgrades.TurtleSword;
import dan200.computercraft.shared.turtle.upgrades.TurtleTool;
import dan200.computercraft.shared.util.Colour;
import dan200.computercraft.shared.util.CreativeTabMain;
import dan200.computercraft.shared.util.IDAssigner;
import dan200.computercraft.shared.util.IEntityDropConsumer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

@Mod(modid = ComputerCraft.MODID, name = ComputerCraft.MODID, version = Tags.VERSION)
public class ComputerCraft {

    public static final String MODID = "ComputerCraft";

    public static final int diskDriveGUIID = 100;
    public static final int computerGUIID = 101;
    public static final int printerGUIID = 102;
    public static final int turtleGUIID = 103;
    public static final int printoutGUIID = 105;
    public static final int pocketComputerGUIID = 106;
    public static boolean http_enable = true;
    public static String http_whitelist = "*";
    public static boolean disable_lua51_features = false;
    public static boolean enableCommandBlock = false;
    public static boolean turtlesNeedFuel = true;
    public static int turtleFuelLimit = 20000;
    public static int advancedTurtleFuelLimit = 100000;
    public static boolean turtlesObeyBlockProtection = true;
    public static boolean turtlesCanPush = true;
    public static final int terminalWidth_computer = 51;
    public static final int terminalHeight_computer = 19;
    public static final int terminalWidth_turtle = 39;
    public static final int terminalHeight_turtle = 13;
    public static final int terminalWidth_pocketComputer = 26;
    public static final int terminalHeight_pocketComputer = 20;
    public static int modem_range = 64;
    public static int modem_highAltitudeRange = 384;
    public static int modem_rangeDuringStorm = 16;
    public static int modem_highAltitudeRangeDuringStorm = 96;
    public static int computerSpaceLimit = 1000000;
    public static int floppySpaceLimit = 125000;
    public static int treasureDiskLootFrequency = 1;
    public static ClientComputerRegistry clientComputerRegistry = new ClientComputerRegistry();
    public static ServerComputerRegistry serverComputerRegistry = new ServerComputerRegistry();
    public static FMLEventChannel networkEventChannel;
    public static CreativeTabMain mainCreativeTab;
    private static List<IPeripheralProvider> peripheralProviders = new ArrayList<>();
    private static List<IBundledRedstoneProvider> bundledRedstoneProviders = new ArrayList<>();
    private static List<IMediaProvider> mediaProviders = new ArrayList<>();
    private static List<ITurtlePermissionProvider> permissionProviders = new ArrayList<>();
    @Instance(value = ComputerCraft.MODID)
    public static ComputerCraft instance;
    @SidedProxy(
        clientSide = "dan200.computercraft.client.proxy.ComputerCraftProxyClient",
        serverSide = "dan200.computercraft.server.proxy.ComputerCraftProxyServer")
    public static IComputerCraftProxy proxy;
    @SidedProxy(
        clientSide = "dan200.computercraft.client.proxy.CCTurtleProxyClient",
        serverSide = "dan200.computercraft.server.proxy.CCTurtleProxyServer")
    public static ICCTurtleProxy turtleProxy;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        Configuration config = new Configuration(event.getSuggestedConfigurationFile());
        config.load();
        Property prop = config.get("general", "http_enable", http_enable);
        prop.comment = "Enable the \"http\" API on Computers (see \"http_whitelist\" for more fine grained control than this)";
        http_enable = prop.getBoolean(http_enable);
        prop = config.get("general", "http_whitelist", http_whitelist);
        prop.comment = "A semicolon limited list of wildcards for domains that can be accessed through the \"http\" API on Computers. Set this to \"*\" to access to the entire internet. Example: \"*.pastebin.com;*.github.com;*.computercraft.info\" will restrict access to just those 3 domains.";
        http_whitelist = prop.getString();
        prop = config.get("general", "disable_lua51_features", disable_lua51_features);
        prop.comment = "Set this to true to disable Lua 5.1 functions that will be removed in a future update. Useful for ensuring forward compatibility of your programs now.";
        disable_lua51_features = prop.getBoolean(disable_lua51_features);
        prop = config.get("general", "enableCommandBlock", enableCommandBlock);
        prop.comment = "Enable Command Block peripheral support";
        enableCommandBlock = prop.getBoolean(enableCommandBlock);
        prop = config.get("general", "modem_range", modem_range);
        prop.comment = "The range of Wireless Modems at low altitude in clear weather, in meters";
        modem_range = Math.min(prop.getInt(), 100000);
        prop = config.get("general", "modem_highAltitudeRange", modem_highAltitudeRange);
        prop.comment = "The range of Wireless Modems at maximum altitude in clear weather, in meters";
        modem_highAltitudeRange = Math.min(prop.getInt(), 100000);
        prop = config.get("general", "modem_rangeDuringStorm", modem_rangeDuringStorm);
        prop.comment = "The range of Wireless Modems at low altitude in stormy weather, in meters";
        modem_rangeDuringStorm = Math.min(prop.getInt(), 100000);
        prop = config.get("general", "modem_highAltitudeRangeDuringStorm", modem_highAltitudeRangeDuringStorm);
        prop.comment = "The range of Wireless Modems at maximum altitude in stormy weather, in meters";
        modem_highAltitudeRangeDuringStorm = Math.min(prop.getInt(), 100000);
        prop = config.get("general", "computerSpaceLimit", computerSpaceLimit);
        prop.comment = "The disk space limit for computers and turtles, in bytes";
        computerSpaceLimit = prop.getInt();
        prop = config.get("general", "floppySpaceLimit", floppySpaceLimit);
        prop.comment = "The disk space limit for floppy disks, in bytes";
        floppySpaceLimit = prop.getInt();
        prop = config.get("general", "treasureDiskLootFrequency", treasureDiskLootFrequency);
        prop.comment = "The frequency that treasure disks will be found in dungeon chests, from 0 to 100. Increase this value if running a modpack with lots of mods that add dungeon loot, or you just want more treasure disks. Set to 0 to disable treasure disks.";
        treasureDiskLootFrequency = prop.getInt();
        prop = config.get("general", "turtlesNeedFuel", turtlesNeedFuel);
        prop.comment = "Set whether Turtles require fuel to move";
        turtlesNeedFuel = prop.getBoolean(turtlesNeedFuel);
        prop = config.get("general", "turtleFuelLimit", turtleFuelLimit);
        prop.comment = "The fuel limit for Turtles";
        turtleFuelLimit = prop.getInt(turtleFuelLimit);
        prop = config.get("general", "advancedTurtleFuelLimit", advancedTurtleFuelLimit);
        prop.comment = "The fuel limit for Advanced Turtles";
        advancedTurtleFuelLimit = prop.getInt(advancedTurtleFuelLimit);
        prop = config.get("general", "turtlesObeyBlockProtection", turtlesObeyBlockProtection);
        prop.comment = "If set to true, Turtles will be unable to build, dig, or enter protected areas (such as near the server spawn point)";
        turtlesObeyBlockProtection = prop.getBoolean(turtlesObeyBlockProtection);
        prop = config.get("general", "turtlesCanPush", turtlesCanPush);
        prop.comment = "If set to true, Turtles will push entities out of the way instead of stopping if there is space to do so";
        turtlesCanPush = prop.getBoolean(turtlesCanPush);
        config.save();
        networkEventChannel = NetworkRegistry.INSTANCE.newEventDrivenChannel("CC");
        networkEventChannel.register(new PacketHandler());
        proxy.preInit();
        turtleProxy.preInit();
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init();
        turtleProxy.init();
    }

    @EventHandler
    public void onServerStarting(FMLServerStartingEvent event) {
        ItemTreasureDisk.registerDungeonLoot();
    }

    @EventHandler
    public void onServerStart(FMLServerStartedEvent event) {
        if (FMLCommonHandler.instance()
            .getEffectiveSide() == Side.SERVER) {
            serverComputerRegistry.reset();
        }
    }

    @EventHandler
    public void onServerStopped(FMLServerStoppedEvent event) {
        if (FMLCommonHandler.instance()
            .getEffectiveSide() == Side.SERVER) {
            serverComputerRegistry.reset();
        }
    }

    public static String getVersion() {
        return "1.75";
    }

    public static boolean isClient() {
        return proxy.isClient();
    }

    public static boolean getGlobalCursorBlink() {
        return proxy.getGlobalCursorBlink();
    }

    public static Object getFixedWidthFontRenderer() {
        return proxy.getFixedWidthFontRenderer();
    }

    public static void playRecord(String record, String recordInfo, World world, int x, int y, int z) {
        proxy.playRecord(record, recordInfo, world, x, y, z);
    }

    public static String getRecordInfo(ItemStack recordStack) {
        return proxy.getRecordInfo(recordStack);
    }

    public static void openDiskDriveGUI(EntityPlayer player, TileDiskDrive drive) {
        player.openGui(instance, 100, player.getEntityWorld(), drive.xCoord, drive.yCoord, drive.zCoord);
    }

    public static void openComputerGUI(EntityPlayer player, TileComputer computer) {
        player.openGui(instance, 101, player.getEntityWorld(), computer.xCoord, computer.yCoord, computer.zCoord);
    }

    public static void openPrinterGUI(EntityPlayer player, TilePrinter printer) {
        player.openGui(instance, 102, player.getEntityWorld(), printer.xCoord, printer.yCoord, printer.zCoord);
    }

    public static void openTurtleGUI(EntityPlayer player, TileTurtle turtle) {
        player.openGui(instance, 103, player.getEntityWorld(), turtle.xCoord, turtle.yCoord, turtle.zCoord);
    }

    public static void openPrintoutGUI(EntityPlayer player) {
        player.openGui(instance, 105, player.getEntityWorld(), 0, 0, 0);
    }

    public static void openPocketComputerGUI(EntityPlayer player) {
        player.openGui(instance, 106, player.getEntityWorld(), 0, 0, 0);
    }

    public static File getBaseDir() {
        return FMLCommonHandler.instance()
            .getMinecraftServerInstance()
            .getFile(".");
    }

    public static File getResourcePackDir() {
        return new File(getBaseDir(), "resourcepacks");
    }

    public static File getWorldDir(World world) {
        return proxy.getWorldDir(world);
    }

    private static FMLProxyPacket encode(ComputerCraftPacket packet) {
        ByteBuf buffer = Unpooled.buffer();
        packet.toBytes(buffer);
        return new FMLProxyPacket(buffer, "CC");
    }

    public static void sendToPlayer(EntityPlayer player, ComputerCraftPacket packet) {
        networkEventChannel.sendTo(encode(packet), (EntityPlayerMP) player);
    }

    public static void sendToAllPlayers(ComputerCraftPacket packet) {
        networkEventChannel.sendToAll(encode(packet));
    }

    public static void sendToServer(ComputerCraftPacket packet) {
        networkEventChannel.sendToServer(encode(packet));
    }

    public static void handlePacket(ComputerCraftPacket packet, EntityPlayer player) {
        proxy.handlePacket(packet, player);
    }

    public static boolean isPlayerOpped(EntityPlayer player) {
        MinecraftServer server = MinecraftServer.getServer();
        return server != null ? server.getConfigurationManager()
            .func_152596_g(player.getGameProfile()) : false;
    }

    public static void registerPermissionProvider(ITurtlePermissionProvider provider) {
        if (provider != null && !permissionProviders.contains(provider)) {
            permissionProviders.add(provider);
        }
    }

    public static boolean isBlockEnterable(World world, int x, int y, int z, EntityPlayer player) {
        MinecraftServer server = MinecraftServer.getServer();
        if (server != null && !world.isRemote && server.isBlockProtected(world, x, y, z, player)) {
            return false;
        } else {
            for (int i = 0; i < permissionProviders.size(); i++) {
                ITurtlePermissionProvider provider = permissionProviders.get(i);
                if (!provider.isBlockEnterable(world, x, y, z)) {
                    return false;
                }
            }

            return true;
        }
    }

    public static boolean isBlockEditable(World world, int x, int y, int z, EntityPlayer player) {
        MinecraftServer server = MinecraftServer.getServer();
        if (server != null && !world.isRemote && server.isBlockProtected(world, x, y, z, player)) {
            return false;
        } else {
            for (int i = 0; i < permissionProviders.size(); i++) {
                ITurtlePermissionProvider provider = permissionProviders.get(i);
                if (!provider.isBlockEditable(world, x, y, z)) {
                    return false;
                }
            }

            return true;
        }
    }

    public static void registerPeripheralProvider(IPeripheralProvider provider) {
        if (provider != null && !peripheralProviders.contains(provider)) {
            peripheralProviders.add(provider);
        }
    }

    public static void registerBundledRedstoneProvider(IBundledRedstoneProvider provider) {
        if (provider != null && !bundledRedstoneProviders.contains(provider)) {
            bundledRedstoneProviders.add(provider);
        }
    }

    public static void registerMediaProvider(IMediaProvider provider) {
        if (provider != null && !mediaProviders.contains(provider)) {
            mediaProviders.add(provider);
        }
    }

    public static IPeripheral getPeripheralAt(World world, int x, int y, int z, int side) {
        for (IPeripheralProvider handler : peripheralProviders) {
            try {
                IPeripheral peripheral = handler.getPeripheral(world, x, y, z, side);
                if (peripheral != null) {
                    return peripheral;
                }
            } catch (Exception var8) {}
        }

        return null;
    }

    public static int getDefaultBundledRedstoneOutput(World world, int x, int y, int z, int side) {
        return y >= 0 && y < world.getHeight()
            ? DefaultBundledRedstoneProvider.getDefaultBundledRedstoneOutput(world, x, y, z, side)
            : -1;
    }

    public static int getBundledRedstoneOutput(World world, int x, int y, int z, int side) {
        if (y >= 0 && y < world.getHeight()) {
            int combinedSignal = -1;

            for (IBundledRedstoneProvider handler : bundledRedstoneProviders) {
                try {
                    int signal = handler.getBundledRedstoneOutput(world, x, y, z, side);
                    if (signal >= 0) {
                        if (combinedSignal < 0) {
                            combinedSignal = signal & 65535;
                        } else {
                            combinedSignal |= signal & 65535;
                        }
                    }
                } catch (Exception var9) {}
            }

            return combinedSignal;
        } else {
            return -1;
        }
    }

    public static IMedia getMedia(ItemStack stack) {
        if (stack != null) {
            for (IMediaProvider handler : mediaProviders) {
                try {
                    IMedia media = handler.getMedia(stack);
                    if (media != null) {
                        return media;
                    }
                } catch (Exception var4) {}
            }

            return null;
        } else {
            return null;
        }
    }

    public static int createUniqueNumberedSaveDir(World world, String parentSubPath) {
        return IDAssigner.getNextIDFromDirectory(new File(getWorldDir(world), parentSubPath));
    }

    public static IWritableMount createSaveDirMount(World world, String subPath, long capacity) {
        try {
            return new FileMount(new File(getWorldDir(world), subPath), capacity);
        } catch (Exception var5) {
            return null;
        }
    }

    public static IMount createResourceMount(Class modClass, String domain, String subPath) {
        List<IMount> mounts = new ArrayList<>();
        subPath = "assets/" + domain + "/" + subPath;
        File codeDir = getDebugCodeDir();
        if (codeDir != null) {
            File subResource = new File(codeDir, subPath);
            if (subResource.exists()) {
                IMount resourcePackMount = new FileMount(subResource, 0L);
                mounts.add(resourcePackMount);
            }
        }

        File modJar = getContainingJar(modClass);
        if (modJar != null) {
            try {
                IMount jarMount = new JarMount(modJar, subPath);
                mounts.add(jarMount);
            } catch (IOException var13) {}
        }

        File resourcePackDir = getResourcePackDir();
        if (resourcePackDir.exists() && resourcePackDir.isDirectory()) {
            String[] resourcePacks = resourcePackDir.list();

            for (int i = 0; i < resourcePacks.length; i++) {
                try {
                    File resourcePack = new File(resourcePackDir, resourcePacks[i]);
                    if (!resourcePack.isDirectory()) {
                        IMount resourcePackMount = new JarMount(resourcePack, subPath);
                        mounts.add(resourcePackMount);
                    } else {
                        File subResource = new File(resourcePack, subPath);
                        if (subResource.exists()) {
                            IMount resourcePackMount = new FileMount(subResource, 0L);
                            mounts.add(resourcePackMount);
                        }
                    }
                } catch (IOException var12) {}
            }
        }

        if (mounts.size() >= 2) {
            IMount[] mountArray = new IMount[mounts.size()];
            mounts.toArray(mountArray);
            return new ComboMount(mountArray);
        } else {
            return mounts.size() == 1 ? mounts.get(0) : null;
        }
    }

    private static File getContainingJar(Class modClass) {
        String path = modClass.getProtectionDomain()
            .getCodeSource()
            .getLocation()
            .getPath();
        int bangIndex = path.indexOf("!");
        if (bangIndex >= 0) {
            path = path.substring(0, bangIndex);
        }

        URL url;
        try {
            url = new URL(path);
        } catch (MalformedURLException var7) {
            return null;
        }

        File file;
        try {
            file = new File(url.toURI());
        } catch (URISyntaxException var6) {
            file = new File(url.getPath());
        }

        return file;
    }

    private static File getDebugCodeDir() {
        String path = ComputerCraft.class.getProtectionDomain()
            .getCodeSource()
            .getLocation()
            .getPath();
        int bangIndex = path.indexOf("!");
        return bangIndex >= 0 ? null : new File(new File(path).getParentFile(), "../..");
    }

    public static void registerTurtleUpgrade(ITurtleUpgrade upgrade) {
        turtleProxy.registerTurtleUpgrade(upgrade);
    }

    public static ITurtleUpgrade getTurtleUpgrade(int id) {
        return turtleProxy.getTurtleUpgrade(id);
    }

    public static int getTurtleUpgradeID(ITurtleUpgrade upgrade) {
        return upgrade != null ? upgrade.getUpgradeID() : 0;
    }

    public static ITurtleUpgrade getTurtleUpgrade(ItemStack item) {
        return turtleProxy.getTurtleUpgrade(item);
    }

    public static void addAllUpgradedTurtles(List<ItemStack> list) {
        turtleProxy.addAllUpgradedTurtles(list);
    }

    public static void setEntityDropConsumer(Entity entity, IEntityDropConsumer consumer) {
        turtleProxy.setEntityDropConsumer(entity, consumer);
    }

    public static void clearEntityDropConsumer(Entity entity) {
        turtleProxy.clearEntityDropConsumer(entity);
    }

    public static void getTurtleModelTextures(List<ResourceLocation> list, ComputerFamily family, Colour colour) {
        turtleProxy.getTurtleModelTextures(list, family, colour);
    }

    public static class Blocks {

        public static BlockComputer computer;
        public static BlockPeripheral peripheral;
        public static BlockCable cable;
        public static BlockTurtle turtle;
        public static BlockTurtle turtleExpanded;
        public static BlockTurtle turtleAdvanced;
        public static BlockCommandComputer commandComputer;
    }

    public static class Items {

        public static ItemDiskLegacy disk;
        public static ItemDiskExpanded diskExpanded;
        public static ItemPrintout printout;
        public static ItemTreasureDisk treasureDisk;
        public static ItemPocketComputer pocketComputer;
    }

    public static class Upgrades {

        public static TurtleModem modem;
        public static TurtleCraftingTable craftingTable;
        public static TurtleSword diamondSword;
        public static TurtleShovel diamondShovel;
        public static TurtleTool diamondPickaxe;
        public static TurtleAxe diamondAxe;
        public static TurtleHoe diamondHoe;
    }
}
