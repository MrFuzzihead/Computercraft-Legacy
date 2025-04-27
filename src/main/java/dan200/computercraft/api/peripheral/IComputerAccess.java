package dan200.computercraft.api.peripheral;

import dan200.computercraft.api.filesystem.IMount;
import dan200.computercraft.api.filesystem.IWritableMount;

public interface IComputerAccess {

    String mount(String var1, IMount var2);

    String mount(String var1, IMount var2, String var3);

    String mountWritable(String var1, IWritableMount var2);

    String mountWritable(String var1, IWritableMount var2, String var3);

    void unmount(String var1);

    int getID();

    void queueEvent(String var1, Object[] var2);

    String getAttachmentName();
}
