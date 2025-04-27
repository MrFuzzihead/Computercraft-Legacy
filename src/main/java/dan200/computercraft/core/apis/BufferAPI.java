package dan200.computercraft.core.apis;

import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.ILuaObject;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.core.terminal.TextBuffer;

public class BufferAPI implements ILuaAPI {

    public BufferAPI(IAPIEnvironment _env) {}

    @Override
    public String[] getNames() {
        return new String[] { "buffer" };
    }

    @Override
    public void startup() {}

    @Override
    public void advance(double _dt) {}

    @Override
    public void shutdown() {}

    @Override
    public String[] getMethodNames() {
        return new String[] { "new" };
    }

    @Override
    public Object[] callMethod(ILuaContext context, int method, Object[] arguments)
        throws LuaException, InterruptedException {
        switch (method) {
            case 0:
                if (arguments.length >= 1 && arguments[0] instanceof String) {
                    String text = (String) arguments[0];
                    int repetitions = 1;
                    if (arguments.length >= 2 && arguments[1] != null) {
                        if (!(arguments[1] instanceof Number)) {
                            throw new LuaException("Expected string, number");
                        }

                        repetitions = ((Number) arguments[1]).intValue();
                        if (repetitions < 0) {
                            throw new LuaException("Expected positive number");
                        }
                    }

                    TextBuffer buffer = new TextBuffer(text, repetitions);
                    return new Object[] { new BufferAPI.BufferLuaObject(buffer) };
                }

                throw new LuaException("Expected string");
            default:
                return null;
        }
    }

    private static class BufferLuaObject implements ILuaObject {

        private TextBuffer m_buffer;

        public BufferLuaObject(TextBuffer buffer) {
            this.m_buffer = buffer;
        }

        @Override
        public String[] getMethodNames() {
            return new String[] { "len", "tostring", "read", "write", "fill" };
        }

        @Override
        public Object[] callMethod(ILuaContext context, int method, Object[] arguments)
            throws LuaException, InterruptedException {
            switch (method) {
                case 0:
                    return new Object[] { this.m_buffer.length() };
                case 1:
                    return new Object[] { this.m_buffer.toString() };
                case 2:
                    int startxx = 0;
                    if (arguments.length >= 1 && arguments[0] != null) {
                        if (!(arguments[0] instanceof Number)) {
                            throw new LuaException("Expected number");
                        }

                        startxx = ((Number) arguments[1]).intValue() - 1;
                    }

                    int end = this.m_buffer.length();
                    if (arguments.length >= 2 && arguments[1] != null) {
                        if (!(arguments[1] instanceof Number)) {
                            throw new LuaException("Expected number, number");
                        }

                        end = ((Number) arguments[1]).intValue();
                    }

                    return new Object[] { this.m_buffer.read(startxx, end) };
                case 3:
                    if (arguments.length >= 1 && arguments[0] instanceof String) {
                        String textx = (String) arguments[0];
                        int startx = 0;
                        if (arguments.length >= 2 && arguments[1] != null) {
                            if (!(arguments[1] instanceof Number)) {
                                throw new LuaException("Expected string, number");
                            }

                            startx = ((Number) arguments[1]).intValue() - 1;
                        }

                        int end3 = startx + textx.length();
                        if (arguments.length >= 3 && arguments[2] != null) {
                            if (!(arguments[2] instanceof Number)) {
                                throw new LuaException("Expected string, number, number");
                            }

                            end3 = ((Number) arguments[2]).intValue();
                        }

                        this.m_buffer.write(textx, startx, end3);
                        return null;
                    }

                    throw new LuaException("Expected string");
                case 4:
                    if (arguments.length >= 1 && arguments[0] instanceof String) {
                        String text = (String) arguments[0];
                        int start = 0;
                        if (arguments.length >= 2 && arguments[1] != null) {
                            if (!(arguments[1] instanceof Number)) {
                                throw new LuaException("Expected string, number");
                            }

                            start = ((Number) arguments[1]).intValue() - 1;
                        }

                        int end4 = this.m_buffer.length();
                        if (arguments.length >= 3 && arguments[2] != null) {
                            if (!(arguments[2] instanceof Number)) {
                                throw new LuaException("Expected string, number, number");
                            }

                            end4 = ((Number) arguments[2]).intValue();
                        }

                        this.m_buffer.fill(text, start, end4);
                        return null;
                    }

                    throw new LuaException("Expected string");
                default:
                    return null;
            }
        }
    }
}
