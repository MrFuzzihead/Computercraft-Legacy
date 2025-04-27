package dan200.computercraft.api.peripheral;

import net.minecraft.world.World;

public interface IPeripheralProvider {

    IPeripheral getPeripheral(World var1, int var2, int var3, int var4, int var5);
}
