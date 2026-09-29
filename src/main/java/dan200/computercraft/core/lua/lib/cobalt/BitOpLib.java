package dan200.computercraft.core.lua.lib.cobalt;

import static org.squiddev.cobalt.Constants.NIL;
import static org.squiddev.cobalt.ValueFactory.valueOf;

import org.squiddev.cobalt.LuaTable;
import org.squiddev.cobalt.function.LibFunction;

/**
 * Reimplementation of the bitop library
 * <p>
 * <a href="http://bitop.luajit.org/api.html">...</a>
 */
public class BitOpLib {

    private static final String[] names = new String[] { "tobit", "bnot", "bswap", "tohex", "lshift", "rshift",
        "arshift", "rol", "ror", "band", "bor", "bxor", };

    // The OneArgFunction/TwoArgFunction/VarArgFunction base classes are now package-private in
    // Cobalt, as are LibFunction's name/env fields, so functions are built through the public
    // LibFunction.create/createV factories instead. The opcode dispatch is kept as-is so the
    // observable behaviour is unchanged.

    private static void bindOneArg(LuaTable table) {
        for (int i = 0; i < 3; i++) {
            final int opcode = i;
            table.rawset(names[i], LibFunction.create((state, luaValue) -> {
                switch (opcode) {
                    case 0: // tobit
                        return valueOf(luaValue.checkInteger());
                    case 1: // bnot
                        return valueOf(~luaValue.checkInteger());
                    case 2: // bswap
                    {
                        int b = luaValue.checkInteger();
                        return valueOf((b & 0xff) << 24 | (b & 0xff00) << 8 | (b & 0xff0000) >> 8 | (b >> 24) & 0xff);
                    }
                    default:
                        return NIL;
                }
            }));
        }
    }

    private static final byte[] lowerHexDigits = new byte[] { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a',
        'b', 'c', 'd', 'e', 'f' };
    private static final byte[] upperHexDigits = new byte[] { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A',
        'B', 'C', 'D', 'E', 'F' };

    private static void bindTwoArg(LuaTable table) {
        for (int i = 3; i < 9; i++) {
            final int opcode = i - 3;
            table.rawset(names[i], LibFunction.create((state, bitValue, nValue) -> {
                switch (opcode) {
                    case 0: // tohex
                    {
                        int n = nValue.optInteger(8);
                        int bit = bitValue.checkInteger();

                        byte[] hexes = lowerHexDigits;
                        if (n < 0) {
                            n = -n;
                            hexes = upperHexDigits;
                        }
                        if (n > 8) n = 8;

                        byte[] out = new byte[n];
                        for (int j = n - 1; j >= 0; j--) {
                            out[j] = hexes[bit & 15];
                            bit >>= 4;
                        }

                        return valueOf(out);
                    }
                    case 1: // lshift
                        return valueOf(bitValue.checkInteger() << (nValue.checkInteger() & 31));
                    case 2: // rshift
                        return valueOf(bitValue.checkInteger() >>> (nValue.checkInteger() & 31));
                    case 3: // arshift
                        return valueOf(bitValue.checkInteger() >> (nValue.checkInteger() & 31));
                    case 4: // rol
                    {
                        int b = bitValue.checkInteger();
                        int n = nValue.checkInteger() & 31;
                        return valueOf((b << n) | (b >>> (32 - n)));
                    }
                    case 5: // ror
                    {
                        int b = bitValue.checkInteger();
                        int n = nValue.checkInteger() & 31;
                        return valueOf((b << (32 - n)) | (b >>> n));
                    }
                    default:
                        return NIL;
                }
            }));
        }
    }

    private static void bindVarArg(LuaTable table) {
        for (int i = 9; i < 12; i++) {
            final int opcode = i - 9;
            table.rawset(names[i], LibFunction.createV((state, varargs) -> {
                int value = varargs.first()
                    .checkInteger(), len = varargs.count();
                if (len == 1) return varargs.first();

                switch (opcode) {
                    case 0: {
                        for (int j = 2; j <= len; j++) {
                            value &= varargs.arg(j)
                                .checkInteger();
                        }
                        break;
                    }
                    case 1: {
                        for (int j = 2; j <= len; j++) {
                            value |= varargs.arg(j)
                                .checkInteger();
                        }
                        break;
                    }
                    case 2: {
                        for (int j = 2; j <= len; j++) {
                            value ^= varargs.arg(j)
                                .checkInteger();
                        }
                        break;
                    }
                }

                return valueOf(value);
            }));
        }
    }

    public static void setup(LuaTable env) {
        LuaTable table = new LuaTable(0, names.length + 3);
        bindOneArg(table);
        bindTwoArg(table);
        bindVarArg(table);

        table.rawset("blshift", table.rawget("lshift"));
        table.rawset("brshift", table.rawget("arlshift"));
        table.rawset("blogic_rshift", table.rawget("rshift"));

        env.rawset("bitop", table);
    }
}
