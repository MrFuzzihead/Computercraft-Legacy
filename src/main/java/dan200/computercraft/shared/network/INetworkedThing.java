package dan200.computercraft.shared.network;

import net.minecraft.entity.player.EntityPlayer;

public interface INetworkedThing {
   void handlePacket(ComputerCraftPacket var1, EntityPlayer var2);
}
