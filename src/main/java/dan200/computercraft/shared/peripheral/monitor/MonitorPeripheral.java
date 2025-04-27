package dan200.computercraft.shared.peripheral.monitor;

import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.core.apis.TermAPI;
import dan200.computercraft.core.terminal.Terminal;

public class MonitorPeripheral implements IPeripheral {

    private final TileMonitor m_monitor;

    public MonitorPeripheral(TileMonitor monitor) {
        this.m_monitor = monitor;
    }

    @Override
    public String getType() {
        return "monitor";
    }

    @Override
    public String[] getMethodNames() {
        return new String[] { "write", "scroll", "setCursorPos", "setCursorBlink", "getCursorPos", "getSize", "clear",
            "clearLine", "setTextScale", "setTextColour", "setTextColor", "setBackgroundColour", "setBackgroundColor",
            "isColour", "isColor", "getTextColour", "getTextColor", "getBackgroundColour", "getBackgroundColor",
            "blit" };
    }

    @Override
    public Object[] callMethod(IComputerAccess computer, ILuaContext context, int method, Object[] args)
        throws LuaException {
        switch (method) {
            case 0: {
                String text;
                if (args.length > 0 && args[0] != null) {
                    text = args[0].toString();
                } else {
                    text = "";
                }

                Terminal terminal = this.m_monitor.getTerminal()
                    .getTerminal();
                terminal.write(text);
                terminal.setCursorPos(terminal.getCursorX() + text.length(), terminal.getCursorY());
                return null;
            }
            case 1:
                if (args.length >= 1 && args[0] instanceof Number) {
                    Terminal terminalx = this.m_monitor.getTerminal()
                        .getTerminal();
                    terminalx.scroll(((Number) args[0]).intValue());
                    return null;
                }

                throw new LuaException("Expected number");
            case 2:
                if (args.length >= 2 && args[0] instanceof Number && args[1] instanceof Number) {
                    int x = ((Number) args[0]).intValue() - 1;
                    int y = ((Number) args[1]).intValue() - 1;
                    Terminal terminalx = this.m_monitor.getTerminal()
                        .getTerminal();
                    terminalx.setCursorPos(x, y);
                    return null;
                }

                throw new LuaException("Expected number, number");
            case 3:
                if (args.length >= 1 && args[0] instanceof Boolean) {
                    Terminal terminalx = this.m_monitor.getTerminal()
                        .getTerminal();
                    terminalx.setCursorBlink((Boolean) args[0]);
                    return null;
                }

                throw new LuaException("Expected boolean");
            case 4: {
                Terminal terminal = this.m_monitor.getTerminal()
                    .getTerminal();
                return new Object[] { terminal.getCursorX() + 1, terminal.getCursorY() + 1 };
            }
            case 5: {
                Terminal terminal = this.m_monitor.getTerminal()
                    .getTerminal();
                return new Object[] { terminal.getWidth(), terminal.getHeight() };
            }
            case 6: {
                Terminal terminal = this.m_monitor.getTerminal()
                    .getTerminal();
                terminal.clear();
                return null;
            }
            case 7: {
                Terminal terminal = this.m_monitor.getTerminal()
                    .getTerminal();
                terminal.clearLine();
                return null;
            }
            case 8:
                if (args.length >= 1 && args[0] instanceof Number) {
                    int scale = (int) (((Number) args[0]).doubleValue() * 2.0);
                    if (scale >= 1 && scale <= 10) {
                        this.m_monitor.setTextScale(scale);
                        return null;
                    }

                    throw new LuaException("Expected number in range 0.5-5");
                }

                throw new LuaException("Expected number");
            case 9:
            case 10: {
                int colour = TermAPI.parseColour(
                    args,
                    this.m_monitor.getTerminal()
                        .isColour());
                Terminal terminal = this.m_monitor.getTerminal()
                    .getTerminal();
                terminal.setTextColour(colour);
                return null;
            }
            case 11:
            case 12: {
                int colour = TermAPI.parseColour(
                    args,
                    this.m_monitor.getTerminal()
                        .isColour());
                Terminal terminal = this.m_monitor.getTerminal()
                    .getTerminal();
                terminal.setBackgroundColour(colour);
                return null;
            }
            case 13:
            case 14:
                return new Object[] { this.m_monitor.getTerminal()
                    .isColour() };
            case 15:
            case 16: {
                Terminal terminal = this.m_monitor.getTerminal()
                    .getTerminal();
                return TermAPI.encodeColour(terminal.getTextColour());
            }
            case 17:
            case 18: {
                Terminal terminal = this.m_monitor.getTerminal()
                    .getTerminal();
                return TermAPI.encodeColour(terminal.getBackgroundColour());
            }
            case 19:
                if (args.length >= 3 && args[0] instanceof String
                    && args[1] instanceof String
                    && args[2] instanceof String) {
                    String text = (String) args[0];
                    String textColour = (String) args[1];
                    String backgroundColour = (String) args[2];
                    if (textColour.length() == text.length() && backgroundColour.length() == text.length()) {
                        Terminal terminalx = this.m_monitor.getTerminal()
                            .getTerminal();
                        terminalx.blit(text, textColour, backgroundColour);
                        terminalx.setCursorPos(terminalx.getCursorX() + text.length(), terminalx.getCursorY());
                        return null;
                    }

                    throw new LuaException("Arguments must be the same length");
                }

                throw new LuaException("Expected string, string, string");
            default:
                return null;
        }
    }

    @Override
    public void attach(IComputerAccess computer) {
        this.m_monitor.addComputer(computer);
    }

    @Override
    public void detach(IComputerAccess computer) {
        this.m_monitor.removeComputer(computer);
    }

    @Override
    public boolean equals(IPeripheral other) {
        if (other != null && other instanceof MonitorPeripheral) {
            MonitorPeripheral otherMonitor = (MonitorPeripheral) other;
            if (otherMonitor.m_monitor == this.m_monitor) {
                return true;
            }
        }

        return false;
    }
}
