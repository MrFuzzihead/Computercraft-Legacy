package dan200.computercraft.api.lua;

public class LuaException extends Exception {

    private final int m_level;

    public LuaException() {
        this("error", 1);
    }

    public LuaException(String message) {
        this(message, 1);
    }

    public LuaException(String message, int level) {
        super(message);
        this.m_level = level;
    }

    public int getLevel() {
        return this.m_level;
    }
}
