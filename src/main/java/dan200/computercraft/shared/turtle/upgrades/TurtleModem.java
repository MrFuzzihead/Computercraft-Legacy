package dan200.computercraft.shared.turtle.upgrades;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.IIcon;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.turtle.ITurtleAccess;
import dan200.computercraft.api.turtle.ITurtleUpgrade;
import dan200.computercraft.api.turtle.TurtleCommandResult;
import dan200.computercraft.api.turtle.TurtleSide;
import dan200.computercraft.api.turtle.TurtleUpgradeType;
import dan200.computercraft.api.turtle.TurtleVerb;
import dan200.computercraft.shared.peripheral.PeripheralType;
import dan200.computercraft.shared.peripheral.common.PeripheralItemFactory;
import dan200.computercraft.shared.peripheral.modem.TileWirelessModem;
import dan200.computercraft.shared.peripheral.modem.WirelessModemPeripheral;

public class TurtleModem implements ITurtleUpgrade {

    private int m_id;

    public TurtleModem(int id) {
        this.m_id = id;
    }

    @Override
    public int getUpgradeID() {
        return this.m_id;
    }

    @Override
    public String getUnlocalisedAdjective() {
        return "upgrade.computercraft:wireless_modem.adjective";
    }

    @Override
    public TurtleUpgradeType getType() {
        return TurtleUpgradeType.Peripheral;
    }

    @Override
    public ItemStack getCraftingItem() {
        return PeripheralItemFactory.create(PeripheralType.WirelessModem, null, 1);
    }

    @Override
    public IIcon getIcon(ITurtleAccess turtle, TurtleSide side) {
        boolean active = false;
        if (turtle != null) {
            NBTTagCompound turtleNBT = turtle.getUpgradeNBTData(side);
            if (turtleNBT.hasKey("active")) {
                active = turtleNBT.getBoolean("active");
            }
        }

        return TileWirelessModem.getItemTexture(3, active);
    }

    @Override
    public IPeripheral createPeripheral(ITurtleAccess turtle, TurtleSide side) {
        return new TurtleModem.Peripheral(turtle);
    }

    @Override
    public TurtleCommandResult useTool(ITurtleAccess turtle, TurtleSide side, TurtleVerb verb, int dir) {
        return null;
    }

    @Override
    public void update(ITurtleAccess turtle, TurtleSide side) {
        if (!turtle.getWorld().isRemote) {
            IPeripheral peripheral = turtle.getPeripheral(side);
            if (peripheral != null && peripheral instanceof TurtleModem.Peripheral) {
                TurtleModem.Peripheral modemPeripheral = (TurtleModem.Peripheral) peripheral;
                if (modemPeripheral.pollChanged()) {
                    turtle.getUpgradeNBTData(side)
                        .setBoolean("active", modemPeripheral.isActive());
                    turtle.updateUpgradeNBTData(side);
                }
            }
        }
    }

    private static class Peripheral extends WirelessModemPeripheral implements IPeripheral {

        private final ITurtleAccess m_turtle;

        public Peripheral(ITurtleAccess turtle) {
            this.m_turtle = turtle;
        }

        @Override
        protected World getWorld() {
            return this.m_turtle.getWorld();
        }

        @Override
        protected Vec3 getPosition() {
            ChunkCoordinates turtlePos = this.m_turtle.getPosition();
            return Vec3.createVectorHelper(turtlePos.posX, turtlePos.posY, turtlePos.posZ);
        }

        @Override
        public boolean equals(IPeripheral other) {
            if (other instanceof TurtleModem.Peripheral) {
                TurtleModem.Peripheral otherModem = (TurtleModem.Peripheral) other;
                return otherModem.m_turtle == this.m_turtle;
            } else {
                return false;
            }
        }
    }
}
