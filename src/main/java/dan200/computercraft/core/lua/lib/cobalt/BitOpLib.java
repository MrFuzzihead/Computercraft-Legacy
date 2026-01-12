package dan200.computercraft.core.lua.lib.cobalt;

import static org.squiddev.cobalt.Constants.NIL;
import static org.squiddev.cobalt.ValueFactory.valueOf;

import org.squiddev.cobalt.LuaState;
import org.squiddev.cobalt.LuaTable;
import org.squiddev.cobalt.LuaValue;
import org.squiddev.cobalt.Varargs;
import org.squiddev.cobalt.function.OneArgFunction;
import org.squiddev.cobalt.function.TwoArgFunction;
import org.squiddev.cobalt.function.VarArgFunction;

/**
 * Reimplementation of the bitop library
 *
 * http://bitop.luajit.org/api.html
 */
public class BitOpLib {

    private static final String[] names = new String[] { "tobit", "bnot", "bswap", "tohex", "lshift", "rshift",
        "arshift", "rol", "ror", "band", "bor", "bxor", };

    private static class BitOneArg extends OneArgFunction {

        @Override
        public LuaValue call(LuaState state, LuaValue luaValue) {
            switch (opcode) {
                case 0: // tobit
                    return luaValue.checkLuaInteger();
                case 1: // bnot
                    return valueOf(~luaValue.checkInteger());
                case 2: // bswap
                {
                    int i = luaValue.checkInteger();
                    return valueOf((i & 0xff) << 24 | (i & 0xff00) << 8 | (i & 0xff0000) >> 8 | (i >> 24) & 0xff);
                }
                default:
                    return NIL;
            }
        }

        private static void bind(LuaTable table, LuaTable env) {
            for (int i = 0; i < 3; i++) {
                BitOneArg func = new BitOneArg();
                func.opcode = i;
                func.name = names[i];
                func.env = env;
                table.rawset(names[i], func);
            }
        }
    }

    private static final byte[] lowerHexDigits = new byte[] { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a',
        'b', 'c', 'd', 'e', 'f' };
    private static final byte[] upperHexDigits = new byte[] { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A',
        'B', 'C', 'D', 'E', 'F' };

    private static class BitTwoArg extends TwoArgFunction {

        @Override
        public LuaValue call(LuaState state, LuaValue bitValue, LuaValue nValue) {
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
                    for (int i = n - 1; i >= 0; i--) {
                        out[i] = hexes[bit & 15];
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
        }

        private static void bind(LuaTable table, LuaTable env) {
            for (int i = 3; i < 9; i++) {
                BitTwoArg func = new BitTwoArg();
                func.opcode = i - 3;
                func.name = names[i];
                func.env = env;
                table.rawset(names[i], func);
            }
        }
    }

    private static class BitVarArg extends VarArgFunction {

        @Override
        public Varargs invoke(LuaState state, Varargs varargs) {
            int value = varargs.first()
                .checkInteger(), len = varargs.count();
            if (len == 1) return varargs.first();

            switch (opcode) {
                case 0: {
                    for (int i = 2; i <= len; i++) {
                        value &= varargs.arg(i)
                            .checkInteger();
                    }
                    break;
                }
                case 1: {
                    for (int i = 2; i <= len; i++) {
                        value |= varargs.arg(i)
                            .checkInteger();
                    }
                    break;
                }
                case 2: {
                    for (int i = 2; i <= len; i++) {
                        value ^= varargs.arg(i)
                            .checkInteger();
                    }
                    break;
                }
            }

            return valueOf(value);
        }

        private static void bind(LuaTable table, LuaTable env) {
            for (int i = 9; i < 12; i++) {
                BitVarArg func = new BitVarArg();
                func.opcode = i - 9;
                func.name = names[i];
                func.env = env;
                table.rawset(names[i], func);
            }
        }
    }

    public static void setup(LuaTable env) {
        LuaTable table = new LuaTable(0, names.length + 3);
        BitOneArg.bind(table, env);
        BitTwoArg.bind(table, env);
        BitVarArg.bind(table, env);

        table.rawset("blshift", table.rawget("lshift"));
        table.rawset("brshift", table.rawget("arlshift"));
        table.rawset("blogic_rshift", table.rawget("rshift"));

        env.rawset("bitop", table);
    }
}
