package dan200.computercraft.core.apis;

import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.core.computer.IComputerEnvironment;
import dan200.computercraft.core.terminal.Terminal;

public class TermAPI implements ILuaAPI {

    private Terminal m_terminal;
    private IComputerEnvironment m_environment;

    public TermAPI(IAPIEnvironment _environment) {
        this.m_terminal = _environment.getTerminal();
        this.m_environment = _environment.getComputerEnvironment();
    }

    @Override
    public String[] getNames() {
        return new String[] { "term" };
    }

    @Override
    public void startup() {}

    @Override
    public void advance(double _dt) {}

    @Override
    public void shutdown() {}

    @Override
    public String[] getMethodNames() {
        return new String[] { "write", "scroll", "setCursorPos", "setCursorBlink", "getCursorPos", "getSize", "clear",
            "clearLine", "setTextColour", "setTextColor", "setBackgroundColour", "setBackgroundColor", "isColour",
            "isColor", "getTextColour", "getTextColor", "getBackgroundColour", "getBackgroundColor", "blit" };
    }

    public static int parseColour(Object[] args, boolean _enableColours) throws LuaException {
        if (args.length == 1 && args[0] != null && args[0] instanceof Double) {
            int colour = (int) ((Double) args[0]).doubleValue();
            if (colour <= 0) {
                throw new LuaException("Colour out of range");
            } else {
                colour = getHighestBit(colour) - 1;
                if (colour < 0 || colour > 15) {
                    throw new LuaException("Colour out of range");
                } else if (!_enableColours && colour != 0 && colour != 15 && colour != 7 && colour != 8) {
                    throw new LuaException("Colour not supported");
                } else {
                    return colour;
                }
            }
        } else {
            throw new LuaException("Expected number");
        }
    }

    public static Object[] encodeColour(int colour) throws LuaException {
        return new Object[] { 1 << colour };
    }

    @Override
    public Object[] callMethod(ILuaContext context, int method, Object[] args) throws LuaException {
        switch (method) {
            case 0:
                String text;
                if (args.length > 0 && args[0] != null) {
                    text = args[0].toString();
                } else {
                    text = "";
                }

                synchronized (this.m_terminal) {
                    this.m_terminal.write(text);
                    this.m_terminal
                        .setCursorPos(this.m_terminal.getCursorX() + text.length(), this.m_terminal.getCursorY());
                    return null;
                }
            case 1:
                if (args.length == 1 && args[0] != null && args[0] instanceof Double) {
                    int y = (int) ((Double) args[0]).doubleValue();
                    synchronized (this.m_terminal) {
                        this.m_terminal.scroll(y);
                        return null;
                    }
                }

                throw new LuaException("Expected number");
            case 2:
                if (args.length == 2 && args[0] != null
                    && args[0] instanceof Double
                    && args[1] != null
                    && args[1] instanceof Double) {
                    int x = (int) ((Double) args[0]).doubleValue() - 1;
                    int y = (int) ((Double) args[1]).doubleValue() - 1;
                    synchronized (this.m_terminal) {
                        this.m_terminal.setCursorPos(x, y);
                        return null;
                    }
                }

                throw new LuaException("Expected number, number");
            case 3:
                if (args.length == 1 && args[0] != null && args[0] instanceof Boolean) {
                    boolean b = (Boolean) args[0];
                    synchronized (this.m_terminal) {
                        this.m_terminal.setCursorBlink(b);
                        return null;
                    }
                }

                throw new LuaException("Expected boolean");
            case 4:
                int x;
                int y;
                synchronized (this.m_terminal) {
                    x = this.m_terminal.getCursorX();
                    y = this.m_terminal.getCursorY();
                }

                return new Object[] { x + 1, y + 1 };
            case 5:
                int width;
                int height;
                synchronized (this.m_terminal) {
                    width = this.m_terminal.getWidth();
                    height = this.m_terminal.getHeight();
                }

                return new Object[] { width, height };
            case 6:
                synchronized (this.m_terminal) {
                    this.m_terminal.clear();
                    return null;
                }
            case 7:
                synchronized (this.m_terminal) {
                    this.m_terminal.clearLine();
                    return null;
                }
            case 8:
            case 9:
                int colour = parseColour(args, this.m_environment.isColour());
                synchronized (this.m_terminal) {
                    this.m_terminal.setTextColour(colour);
                    return null;
                }
            case 10:
            case 11:
                int colour11 = parseColour(args, this.m_environment.isColour());
                synchronized (this.m_terminal) {
                    this.m_terminal.setBackgroundColour(colour11);
                    return null;
                }
            case 12:
            case 13:
                return new Object[] { this.m_environment.isColour() };
            case 14:
            case 15:
                return encodeColour(this.m_terminal.getTextColour());
            case 16:
            case 17:
                return encodeColour(this.m_terminal.getBackgroundColour());
            case 18:
                if (args.length >= 3 && args[0] instanceof String
                    && args[1] instanceof String
                    && args[2] instanceof String) {
                    String text18 = (String) args[0];
                    String textColour = (String) args[1];
                    String backgroundColour = (String) args[2];
                    if (textColour.length() == text18.length() && backgroundColour.length() == text18.length()) {
                        synchronized (this.m_terminal) {
                            this.m_terminal.blit(text18, textColour, backgroundColour);
                            this.m_terminal.setCursorPos(
                                this.m_terminal.getCursorX() + text18.length(),
                                this.m_terminal.getCursorY());
                            return null;
                        }
                    }

                    throw new LuaException("Arguments must be the same length");
                }

                throw new LuaException("Expected string, string, string");
            default:
                return null;
        }
    }

    private static int getHighestBit(int group) {
        int bit;
        for (bit = 0; group > 0; bit++) {
            group >>= 1;
        }

        return bit;
    }
}
