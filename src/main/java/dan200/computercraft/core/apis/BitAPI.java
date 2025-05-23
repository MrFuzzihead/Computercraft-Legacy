package dan200.computercraft.core.apis;

import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;

public class BitAPI implements ILuaAPI {

    private static final int BNOT = 0;
    private static final int BAND = 1;
    private static final int BOR = 2;
    private static final int BXOR = 3;
    private static final int BRSHIFT = 4;
    private static final int BLSHIFT = 5;
    private static final int BLOGIC_RSHIFT = 6;

    private static int checkInt(Object o, int count) throws LuaException {
        if (o instanceof Number) {
            return (int) ((Number) o).longValue();
        } else if (count == 2) {
            throw new LuaException("Expected number, number");
        } else {
            throw new LuaException("Expected number");
        }
    }

    public BitAPI(IAPIEnvironment _environment) {}

    @Override
    public String[] getNames() {
        return new String[] { "bit" };
    }

    @Override
    public void startup() {}

    @Override
    public void advance(double _dt) {}

    @Override
    public void shutdown() {}

    @Override
    public String[] getMethodNames() {
        return new String[] { "bnot", "band", "bor", "bxor", "brshift", "blshift", "blogic_rshift" };
    }

    @Override
    public Object[] callMethod(ILuaContext context, int method, Object[] args) throws LuaException {
        Object a = args.length > 0 ? args[0] : null;
        Object b = args.length > 1 ? args[1] : null;
        int ret = 0;
        switch (method) {
            case 0:
                ret = ~checkInt(a, 1);
                break;
            case 1:
                ret = checkInt(a, 2) & checkInt(b, 2);
                break;
            case 2:
                ret = checkInt(a, 2) | checkInt(b, 2);
                break;
            case 3:
                ret = checkInt(a, 2) ^ checkInt(b, 2);
                break;
            case 4:
                ret = checkInt(a, 2) >> checkInt(b, 2);
                break;
            case 5:
                ret = checkInt(a, 2) << checkInt(b, 2);
                break;
            case 6:
                ret = checkInt(a, 2) >>> checkInt(b, 2);
        }

        return new Object[] { ret & 4294967295L };
    }
}
