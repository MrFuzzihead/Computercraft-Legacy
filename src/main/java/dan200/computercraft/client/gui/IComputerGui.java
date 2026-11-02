package dan200.computercraft.client.gui;

/**
 * A GUI that is showing one computer's terminal, and so is a drop target for file uploads.
 *
 * <p>
 * The 1.7.10 screens do not share a base class ({@code GuiComputer} and {@code GuiTurtle} both
 * extend {@code GuiContainer}), so the two of them opt in here.
 * </p>
 */
public interface IComputerGui {

    /** The registry instance id of the computer being shown, or {@code -1} if it is not known. */
    int getComputerInstanceID();
}
