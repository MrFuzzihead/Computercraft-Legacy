package dan200.computercraft.shared.command;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.shared.computer.core.ServerComputer;

/**
 * {@code /computercraft} — the admin half of the mod: list computers, turn them on and shut them
 * down.
 *
 * <p>
 * Modelled on CC:Tweaked's {@code CommandComputerCraft}, restricted to what 1.7.10 can actually do.
 * Three subcommands are implemented:
 * </p>
 *
 * <ul>
 * <li>{@code dump} — list every loaded computer, or describe one;</li>
 * <li>{@code turn-on <id...>} — power computers on;</li>
 * <li>{@code shutdown <id...>} — shut them down.</li>
 * </ul>
 *
 * <p>
 * Upstream's {@code track} subcommand is deliberately absent: it is built on the Micrometer metrics
 * library and per-task aggregation that this tree has no equivalent for, and a version that printed
 * something invented would be worse than none. {@code computer-folder} is 1.20-only (a desktop
 * folder) and {@code tp}/{@code view}/{@code queue} depend on 1.20's command-computer queue API.
 * </p>
 *
 * <p>
 * A computer is addressed by its <em>instance id</em>: the id of the block, stable across reboots,
 * which is what {@code dump} prints. That differs from the computer's own id, which a program can
 * change; both are shown so the two are never confused.
 * </p>
 */
public class CommandComputerCraft extends CommandBase {

    /** Subcommands, in the order the usage line lists them. */
    static final String[] SUBCOMMANDS = { "dump", "turn-on", "shutdown" };

    @Override
    public String getCommandName() {
        return "computercraft";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/computercraft <dump|turn-on|shutdown> [instanceID...]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        // Everything here manipulates other people's computers, so it is op-only.
        return 2;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public List getCommandAliases() {
        return new ArrayList<>();
    }

    @Override
    public boolean isUsernameIndex(String[] args, int index) {
        return false;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length == 0) {
            throw new CommandException("commands.generic.usage", getCommandUsage(sender));
        }

        String subcommand = args[0].toLowerCase();
        List<ServerComputer> computers = loadedComputers();

        switch (subcommand) {
            case "dump": {
                if (args.length >= 2) {
                    ServerComputer computer = findComputer(computers, args[1]);
                    dumpOne(sender, computer);
                } else {
                    dumpAll(sender, computers);
                }
                break;
            }
            case "turn-on": {
                requireIds(sender, args);
                int changed = 0;
                for (int i = 1; i < args.length; i++) {
                    ServerComputer computer = findComputer(computers, args[i]);
                    if (!computer.isOn()) changed++;
                    computer.turnOn();
                }
                reply(
                    sender,
                    EnumChatFormatting.GREEN,
                    "Turned on " + changed + " of " + (args.length - 1) + " computer(s).");
                break;
            }
            case "shutdown": {
                requireIds(sender, args);
                int changed = 0;
                for (int i = 1; i < args.length; i++) {
                    ServerComputer computer = findComputer(computers, args[i]);
                    if (computer.isOn()) changed++;
                    computer.shutdown();
                }
                reply(
                    sender,
                    EnumChatFormatting.GREEN,
                    "Shut down " + changed + " of " + (args.length - 1) + " computer(s).");
                break;
            }
            default:
                throw new CommandException("commands.generic.usage", getCommandUsage(sender));
        }
    }

    /** Every loaded computer, in a stable order. */
    static List<ServerComputer> loadedComputers() {
        List<ServerComputer> computers = new ArrayList<>(ComputerCraft.serverComputerRegistry.getComputers());
        computers.sort(Comparator.comparingInt(ServerComputer::getInstanceID));
        return computers;
    }

    /**
     * Resolves an instance id argument.
     *
     * @param computers the loaded computers
     * @param argument  the argument, a decimal instance id
     * @return the computer
     * @throws CommandException if the argument is not a number, or names no loaded computer
     */
    static ServerComputer findComputer(List<ServerComputer> computers, String argument) {
        int instanceId;
        try {
            instanceId = Integer.parseInt(argument);
        } catch (NumberFormatException e) {
            throw new CommandException("Not a computer instance id: " + argument + " (see /computercraft dump)");
        }

        for (ServerComputer computer : computers) {
            if (computer.getInstanceID() == instanceId) return computer;
        }

        throw new CommandException("No loaded computer with instance id " + instanceId);
    }

    private static void requireIds(ICommandSender sender, String[] args) {
        if (args.length < 2) {
            throw new CommandException("commands.generic.usage", "/computercraft " + args[0] + " <instanceID...>");
        }
    }

    /** One line per computer: id, on/off, label, position. */
    static void dumpAll(ICommandSender sender, List<ServerComputer> computers) {
        if (computers.isEmpty()) {
            reply(sender, EnumChatFormatting.GRAY, "No computers are loaded.");
            return;
        }

        reply(sender, EnumChatFormatting.GRAY, computers.size() + " computer(s) loaded:");
        reply(
            sender,
            EnumChatFormatting.GRAY,
            String.format("%-6s %-8s %-4s %-20s %s", "Inst", "Id", "On", "Label", "Position"));

        ICommandSender reference = sender;
        // Sort nearest-first for a player, so the machine being watched is at the top.
        if (sender instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) sender;
            if (player.worldObj != null) {
                World world = player.worldObj;
                ChunkCoordinates origin = new ChunkCoordinates(
                    (int) Math.floor(player.posX),
                    (int) Math.floor(player.posY),
                    (int) Math.floor(player.posZ));
                computers.sort((a, b) -> {
                    boolean aHere = a.getWorld() == world && a.getPosition() != null;
                    boolean bHere = b.getWorld() == world && b.getPosition() != null;
                    if (aHere != bHere) return aHere ? -1 : 1;
                    if (!aHere) return Integer.compare(a.getInstanceID(), b.getInstanceID());
                    return Double
                        .compare(squaredDistance(a.getPosition(), origin), squaredDistance(b.getPosition(), origin));
                });
            }
        }

        for (ServerComputer computer : computers) {
            reply(
                sender,
                EnumChatFormatting.WHITE,
                String.format(
                    "%-6d %-8d %-4s %-20s %s",
                    computer.getInstanceID(),
                    computer.getID(),
                    computer.isOn() ? "yes" : "no",
                    truncate(computer.getLabel(), 20),
                    describePosition(reference, computer)));
        }
    }

    /** Full detail for one computer. */
    static void dumpOne(ICommandSender sender, ServerComputer computer) {
        reply(sender, EnumChatFormatting.GRAY, "Computer " + computer.getInstanceID());
        detail(sender, "Id", Integer.toString(computer.getID()));
        detail(sender, "Instance id", Integer.toString(computer.getInstanceID()));
        detail(
            sender,
            "Label",
            computer.getLabel() == null || computer.getLabel()
                .isEmpty() ? "(none)" : computer.getLabel());
        detail(sender, "On", computer.isOn() ? "yes" : "no");
        detail(sender, "Position", describePosition(sender, computer));

        for (int side = 0; side < 6; side++) {
            IPeripheral peripheral = computer.getPeripheral(side);
            if (peripheral != null) {
                detail(sender, "Peripheral " + sideName(side), peripheral.getType());
            }
        }
    }

    /** The peripheral side names 1.7.10 uses, so the output matches docs and the Lua API. */
    private static String sideName(int side) {
        switch (side) {
            case 0:
                return "down";
            case 1:
                return "up";
            case 2:
                return "north";
            case 3:
                return "south";
            case 4:
                return "west";
            case 5:
                return "east";
            default:
                return "?" + side;
        }
    }

    private static void detail(ICommandSender sender, String key, String value) {
        reply(sender, EnumChatFormatting.WHITE, String.format("  %-16s %s", key, value));
    }

    private static String describePosition(ICommandSender sender, ServerComputer computer) {
        World world = computer.getWorld();
        // A world always has a provider in practice; the null checks keep this callable from tests
        // and from anything holding a half-built computer.
        String dimension = world != null && world.provider != null ? world.provider.getDimensionName() : null;
        return describePosition(dimension, computer.getPosition());
    }

    /**
     * Formats a position for the listing.
     *
     * <p>
     * Kept pure — a dimension name and a position — because {@code World.provider} is a final field
     * in 1.7.10 and so cannot be stubbed, which would leave the formatting untestable.
     * </p>
     *
     * @param dimension the dimension's name, or {@code null} if it has none
     * @param position  the computer's block position, or {@code null} for a pocket computer
     * @return the human-readable position
     */
    static String describePosition(String dimension, ChunkCoordinates position) {
        if (position == null) return "(no position: pocket computer)";

        return String.format(
            "%d, %d, %d in %s",
            position.posX,
            position.posY,
            position.posZ,
            dimension != null ? dimension : "?");
    }

    private static double squaredDistance(ChunkCoordinates a, ChunkCoordinates b) {
        double dx = a.posX - b.posX;
        double dy = a.posY - b.posY;
        double dz = a.posZ - b.posZ;
        return dx * dx + dy * dy + dz * dz;
    }

    private static String truncate(String value, int limit) {
        if (value == null || value.isEmpty()) return "(none)";
        return value.length() <= limit ? value : value.substring(0, limit - 1) + "…";
    }

    private static void reply(ICommandSender sender, EnumChatFormatting colour, String message) {
        IChatComponent component = new ChatComponentText(message);
        component.setChatStyle(new ChatStyle().setColor(colour));
        sender.addChatMessage(component);
    }
}
