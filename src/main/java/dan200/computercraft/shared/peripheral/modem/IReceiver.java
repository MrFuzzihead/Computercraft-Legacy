package dan200.computercraft.shared.peripheral.modem;

import net.minecraft.util.Vec3;

public interface IReceiver {

    int getChannel();

    Vec3 getWorldPosition();

    double getReceiveRange();

    void receive(int var1, Object var2, double var3, Object var5);
}
