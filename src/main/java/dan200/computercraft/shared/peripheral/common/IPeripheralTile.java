package dan200.computercraft.shared.peripheral.common;

import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.shared.common.IDirectionalTile;
import dan200.computercraft.shared.peripheral.PeripheralType;

public interface IPeripheralTile extends IDirectionalTile {

    PeripheralType getPeripheralType();

    IPeripheral getPeripheral(int var1);

    String getLabel();
}
