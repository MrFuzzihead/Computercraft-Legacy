package dan200.computercraft.shared.turtle.apis;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.google.common.base.Optional;

import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.turtle.ITurtleAccess;
import dan200.computercraft.api.turtle.ITurtleCommand;
import dan200.computercraft.api.turtle.TurtleSide;
import dan200.computercraft.core.apis.IAPIEnvironment;
import dan200.computercraft.core.apis.ILuaAPI;
import dan200.computercraft.shared.turtle.core.InteractDirection;
import dan200.computercraft.shared.turtle.core.MoveDirection;
import dan200.computercraft.shared.turtle.core.TurnDirection;
import dan200.computercraft.shared.turtle.core.TurtleAttackCommand;
import dan200.computercraft.shared.turtle.core.TurtleCompareCommand;
import dan200.computercraft.shared.turtle.core.TurtleCompareToCommand;
import dan200.computercraft.shared.turtle.core.TurtleDetectCommand;
import dan200.computercraft.shared.turtle.core.TurtleDigCommand;
import dan200.computercraft.shared.turtle.core.TurtleDropCommand;
import dan200.computercraft.shared.turtle.core.TurtleEquipCommand;
import dan200.computercraft.shared.turtle.core.TurtleInspectCommand;
import dan200.computercraft.shared.turtle.core.TurtleMoveCommand;
import dan200.computercraft.shared.turtle.core.TurtlePlaceCommand;
import dan200.computercraft.shared.turtle.core.TurtleRefuelCommand;
import dan200.computercraft.shared.turtle.core.TurtleSelectCommand;
import dan200.computercraft.shared.turtle.core.TurtleSuckCommand;
import dan200.computercraft.shared.turtle.core.TurtleTransferToCommand;
import dan200.computercraft.shared.turtle.core.TurtleTurnCommand;

public class TurtleAPI implements ILuaAPI {

    private IAPIEnvironment m_environment;
    private ITurtleAccess m_turtle;

    public TurtleAPI(IAPIEnvironment environment, ITurtleAccess turtle) {
        this.m_environment = environment;
        this.m_turtle = turtle;
    }

    @Override
    public String[] getNames() {
        return new String[] { "turtle" };
    }

    @Override
    public void startup() {}

    @Override
    public void advance(double _dt) {}

    @Override
    public void shutdown() {}

    @Override
    public String[] getMethodNames() {
        return new String[] { "forward", "back", "up", "down", "turnLeft", "turnRight", "dig", "digUp", "digDown",
            "place", "placeUp", "placeDown", "drop", "select", "getItemCount", "getItemSpace", "detect", "detectUp",
            "detectDown", "compare", "compareUp", "compareDown", "attack", "attackUp", "attackDown", "dropUp",
            "dropDown", "suck", "suckUp", "suckDown", "getFuelLevel", "refuel", "compareTo", "transferTo",
            "getSelectedSlot", "getFuelLimit", "equipLeft", "equipRight", "inspect", "inspectUp", "inspectDown",
            "getItemDetail" };
    }

    private Object[] tryCommand(ILuaContext context, ITurtleCommand command) throws LuaException, InterruptedException {
        return this.m_turtle.executeCommand(context, command);
    }

    private int parseSlotNumber(Object[] arguments, int index) throws LuaException {
        int slot = this.parseOptionalSlotNumber(arguments, index, 99);
        if (slot == 99) {
            throw new LuaException("Expected number");
        } else {
            return slot;
        }
    }

    private int parseOptionalSlotNumber(Object[] arguments, int index, int fallback) throws LuaException {
        if (arguments.length > index && arguments[index] instanceof Number) {
            int slot = ((Number) arguments[index]).intValue();
            if (slot >= 1 && slot <= 16) {
                return slot - 1;
            } else {
                throw new LuaException("Slot number " + slot + " out of range");
            }
        } else {
            return fallback;
        }
    }

    private int parseCount(Object[] arguments, int index) throws LuaException {
        if (arguments.length > index && arguments[index] instanceof Number) {
            int count = ((Number) arguments[index]).intValue();
            if (count >= 0 && count <= 64) {
                return count;
            } else {
                throw new LuaException("Item count " + count + " out of range");
            }
        } else {
            throw new LuaException("Expected number");
        }
    }

    private Optional<TurtleSide> parseSide(Object[] arguments, int index) throws LuaException {
        if (arguments.length <= index || arguments[index] == null) {
            return Optional.absent();
        } else if (!(arguments[index] instanceof String)) {
            throw new LuaException("Expected string");
        } else if (arguments[index].equals("left")) {
            return Optional.of(TurtleSide.Left);
        } else if (arguments[index].equals("right")) {
            return Optional.of(TurtleSide.Right);
        } else {
            throw new LuaException("Invalid side");
        }
    }

    @Override
    public Object[] callMethod(ILuaContext context, int method, Object[] args)
        throws LuaException, InterruptedException {
        switch (method) {
            case 0:
                return this.tryCommand(context, new TurtleMoveCommand(MoveDirection.Forward));
            case 1:
                return this.tryCommand(context, new TurtleMoveCommand(MoveDirection.Back));
            case 2:
                return this.tryCommand(context, new TurtleMoveCommand(MoveDirection.Up));
            case 3:
                return this.tryCommand(context, new TurtleMoveCommand(MoveDirection.Down));
            case 4:
                return this.tryCommand(context, new TurtleTurnCommand(TurnDirection.Left));
            case 5:
                return this.tryCommand(context, new TurtleTurnCommand(TurnDirection.Right));
            case 6: {
                Optional<TurtleSide> side = this.parseSide(args, 0);
                return this.tryCommand(context, new TurtleDigCommand(InteractDirection.Forward, side));
            }
            case 7: {
                Optional<TurtleSide> side = this.parseSide(args, 0);
                return this.tryCommand(context, new TurtleDigCommand(InteractDirection.Up, side));
            }
            case 8: {
                Optional<TurtleSide> side = this.parseSide(args, 0);
                return this.tryCommand(context, new TurtleDigCommand(InteractDirection.Down, side));
            }
            case 9:
                return this.tryCommand(context, new TurtlePlaceCommand(InteractDirection.Forward, args));
            case 10:
                return this.tryCommand(context, new TurtlePlaceCommand(InteractDirection.Up, args));
            case 11:
                return this.tryCommand(context, new TurtlePlaceCommand(InteractDirection.Down, args));
            case 12:
                int count = 64;
                if (args.length > 0) {
                    count = this.parseCount(args, 0);
                }

                return this.tryCommand(context, new TurtleDropCommand(InteractDirection.Forward, count));
            case 13: {
                int slot = this.parseSlotNumber(args, 0);
                return this.tryCommand(context, new TurtleSelectCommand(slot));
            }
            case 14: {
                int slot = this.parseOptionalSlotNumber(args, 0, this.m_turtle.getSelectedSlot());
                ItemStack stack = this.m_turtle.getInventory()
                    .getStackInSlot(slot);
                if (stack != null) {
                    return new Object[] { stack.stackSize };
                }

                return new Object[] { 0 };
            }
            case 15: {
                int slot = this.parseOptionalSlotNumber(args, 0, this.m_turtle.getSelectedSlot());
                ItemStack stack = this.m_turtle.getInventory()
                    .getStackInSlot(slot);
                if (stack != null) {
                    return new Object[] { Math.min(stack.getMaxStackSize(), 64) - stack.stackSize };
                }

                return new Object[] { 64 };
            }
            case 16:
                return this.tryCommand(context, new TurtleDetectCommand(InteractDirection.Forward));
            case 17:
                return this.tryCommand(context, new TurtleDetectCommand(InteractDirection.Up));
            case 18:
                return this.tryCommand(context, new TurtleDetectCommand(InteractDirection.Down));
            case 19:
                return this.tryCommand(context, new TurtleCompareCommand(InteractDirection.Forward));
            case 20:
                return this.tryCommand(context, new TurtleCompareCommand(InteractDirection.Up));
            case 21:
                return this.tryCommand(context, new TurtleCompareCommand(InteractDirection.Down));
            case 22: {
                Optional<TurtleSide> side = this.parseSide(args, 0);
                return this.tryCommand(context, new TurtleAttackCommand(InteractDirection.Forward, side));
            }
            case 23: {
                Optional<TurtleSide> side = this.parseSide(args, 0);
                return this.tryCommand(context, new TurtleAttackCommand(InteractDirection.Up, side));
            }
            case 24: {
                Optional<TurtleSide> side = this.parseSide(args, 0);
                return this.tryCommand(context, new TurtleAttackCommand(InteractDirection.Down, side));
            }
            case 25:
                int count25 = 64;
                if (args.length > 0) {
                    count25 = this.parseCount(args, 0);
                }

                return this.tryCommand(context, new TurtleDropCommand(InteractDirection.Up, count25));
            case 26:
                int count26 = 64;
                if (args.length > 0) {
                    count26 = this.parseCount(args, 0);
                }

                return this.tryCommand(context, new TurtleDropCommand(InteractDirection.Down, count26));
            case 27:
                int count27 = 64;
                if (args.length > 0) {
                    count27 = this.parseCount(args, 0);
                }

                return this.tryCommand(context, new TurtleSuckCommand(InteractDirection.Forward, count27));
            case 28:
                int count28 = 64;
                if (args.length > 0) {
                    count28 = this.parseCount(args, 0);
                }

                return this.tryCommand(context, new TurtleSuckCommand(InteractDirection.Up, count28));
            case 29:
                int count29 = 64;
                if (args.length > 0) {
                    count29 = this.parseCount(args, 0);
                }

                return this.tryCommand(context, new TurtleSuckCommand(InteractDirection.Down, count29));
            case 30:
                if (this.m_turtle.isFuelNeeded()) {
                    return new Object[] { this.m_turtle.getFuelLevel() };
                }

                return new Object[] { "unlimited" };
            case 31:
                int count31 = 64;
                if (args.length > 0) {
                    count31 = this.parseCount(args, 0);
                }

                return this.tryCommand(context, new TurtleRefuelCommand(count31));
            case 32: {
                int slot = this.parseSlotNumber(args, 0);
                return this.tryCommand(context, new TurtleCompareToCommand(slot));
            }
            case 33: {
                int slot = this.parseSlotNumber(args, 0);
                int count33 = 64;
                if (args.length > 1) {
                    count33 = this.parseCount(args, 1);
                }

                return this.tryCommand(context, new TurtleTransferToCommand(slot, count33));
            }
            case 34:
                return new Object[] { this.m_turtle.getSelectedSlot() + 1 };
            case 35:
                if (this.m_turtle.isFuelNeeded()) {
                    return new Object[] { this.m_turtle.getFuelLimit() };
                }

                return new Object[] { "unlimited" };
            case 36:
                return this.tryCommand(context, new TurtleEquipCommand(TurtleSide.Left));
            case 37:
                return this.tryCommand(context, new TurtleEquipCommand(TurtleSide.Right));
            case 38:
                return this.tryCommand(context, new TurtleInspectCommand(InteractDirection.Forward, true));
            case 39:
                return this.tryCommand(context, new TurtleInspectCommand(InteractDirection.Up, true));
            case 40:
                return this.tryCommand(context, new TurtleInspectCommand(InteractDirection.Down, true));
            case 41: {
                int slot = this.parseOptionalSlotNumber(args, 0, this.m_turtle.getSelectedSlot());
                ItemStack stack = this.m_turtle.getInventory()
                    .getStackInSlot(slot);
                if (stack != null && stack.stackSize > 0) {
                    Item item = stack.getItem();
                    String name = Item.itemRegistry.getNameForObject(item);
                    int damage = stack.getItemDamage();
                    int count41 = stack.stackSize;
                    Map<Object, Object> table = new HashMap<>();
                    table.put("name", name);
                    table.put("damage", damage);
                    table.put("count", count41);
                    return new Object[] { table };
                }

                return new Object[] { null };
            }
            default:
                return null;
        }
    }
}
