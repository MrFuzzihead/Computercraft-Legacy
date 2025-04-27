package dan200.computercraft.shared.computer.core;

import dan200.computercraft.shared.common.ITerminal;

public interface IComputer extends ITerminal {

    int getInstanceID();

    int getID();

    String getLabel();

    boolean isOn();

    boolean isCursorDisplayed();

    void turnOn();

    void shutdown();

    void reboot();

    void queueEvent(String var1);

    void queueEvent(String var1, Object[] var2);
}
