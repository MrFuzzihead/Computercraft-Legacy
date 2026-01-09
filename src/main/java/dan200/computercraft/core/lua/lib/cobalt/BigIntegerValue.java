package dan200.computercraft.core.lua.lib.cobalt;

import static org.squiddev.cobalt.Constants.*;
import static org.squiddev.cobalt.ValueFactory.valueOf;

import java.math.BigInteger;
import java.util.Random;

import org.squiddev.cobalt.ErrorFactory;
import org.squiddev.cobalt.LuaDouble;
import org.squiddev.cobalt.LuaError;
import org.squiddev.cobalt.LuaInteger;
import org.squiddev.cobalt.LuaNumber;
import org.squiddev.cobalt.LuaState;
import org.squiddev.cobalt.LuaString;
import org.squiddev.cobalt.LuaTable;
import org.squiddev.cobalt.LuaValue;
import org.squiddev.cobalt.function.ThreeArgFunction;

public final class BigIntegerValue extends LuaValue {

    private static final String NAME = "biginteger";

    private final BigInteger number;
    private LuaTable metatable;

    private BigIntegerValue(BigInteger number, LuaTable metatable) {
        super(TUSERDATA);
        this.number = number;
        this.metatable = metatable;
    }

    @Override
    public LuaTable getMetatable(LuaState state) {
        return metatable;
    }

    @Override
    public double toDouble() {
        return number.doubleValue();
    }

    @Override
    public int toInteger() {
        return number.intValue();
    }

    @Override
    public long toLong() {
        return number.longValue();
    }

    @Override
    public LuaValue toNumber() {
        return valueOf(number.doubleValue());
    }

    @Override
    public double optDouble(double def) {
        return number.doubleValue();
    }

    @Override
    public int optInteger(int def) {
        return number.intValue();
    }

    @Override
    public LuaInteger optLuaInteger(LuaInteger def) {
        return valueOf(number.intValue());
    }

    @Override
    public long optLong(long def) {
        return number.longValue();
    }

    @Override
    public LuaNumber optNumber(LuaNumber def) {
        return valueOf(number.doubleValue());
    }

    @Override
    public int checkInteger() {
        return number.intValue();
    }

    @Override
    public LuaInteger checkLuaInteger() {
        return valueOf(number.intValue());
    }

    @Override
    public long checkLong() {
        return number.longValue();
    }

    @Override
    public LuaNumber checkNumber() {
        return valueOf(number.doubleValue());
    }

    @Override
    public LuaNumber checkNumber(String s) {
        return valueOf(number.doubleValue());
    }

    @Override
    public String checkString() {
        return number.toString();
    }

    @Override
    public LuaString checkLuaString() {
        return valueOf(number.toString());
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof BigIntegerValue && number.equals(((BigIntegerValue) o).number));
    }

    @Override
    public int hashCode() {
        return number.hashCode();
    }

    public static void setup(LuaTable env) {
        env.rawset(NAME, BigIntegerFunction.makeTable(env));
    }

    private static BigInteger getValue(LuaValue value) {
        if (value instanceof BigIntegerValue) {
            return ((BigIntegerValue) value).number;
        } else if (value.type() == TSTRING) {
            try {
                return new BigInteger(value.toString());
            } catch (NumberFormatException e) {
                throw ErrorFactory.argError(value, "number");
            }
        } else {
            return BigInteger.valueOf(value.checkLong());
        }
    }

    private static class BigIntegerFunction extends ThreeArgFunction {

        private static final String[] META_NAMES = new String[] { "unm", "add", "sub", "mul", "mod", "pow", "div",
            "idiv", "band", "bor", "bxor", "shl", "shr", "bnot", "eq", "lt", "le", "tostring", "tonumber", };

        private static final String[] MAIN_NAMES = new String[] { "new", "modinv", "gcd", "modpow", "abs", "min", "max",
            "isProbPrime", "nextProbPrime", "newProbPrime" };

        private static final int CREATE_INDEX = 19;

        private LuaTable metatable;

        private BigIntegerFunction(LuaTable metatable) {
            this.metatable = metatable;
        }

        @Override
        public LuaValue call(LuaState state, LuaValue left, LuaValue right, LuaValue third) {
            try {
                switch (opcode) {
                    case 0: { // unm
                        BigInteger leftB = getValue(left);
                        return new BigIntegerValue(leftB.negate(), metatable);
                    }
                    case 1: { // add
                        BigInteger leftNum = getValue(left), rightNum = getValue(right);
                        return new BigIntegerValue(leftNum.add(rightNum), metatable);
                    }
                    case 2: { // sub
                        BigInteger leftNum = getValue(left), rightNum = getValue(right);
                        return new BigIntegerValue(leftNum.subtract(rightNum), metatable);
                    }
                    case 3: { // mul
                        BigInteger leftNum = getValue(left), rightNum = getValue(right);
                        return new BigIntegerValue(leftNum.multiply(rightNum), metatable);
                    }
                    case 4: { // mod
                        BigInteger leftNum = getValue(left), rightNum = getValue(right);
                        return new BigIntegerValue(leftNum.remainder(rightNum), metatable);
                    }
                    case 5: { // pow
                        BigInteger leftNum = getValue(left);
                        return new BigIntegerValue(leftNum.pow(right.checkInteger()), metatable);
                    }
                    case 6:
                    case 7: { // div
                        BigInteger leftNum = getValue(left), rightNum = getValue(right);
                        return new BigIntegerValue(leftNum.divide(rightNum), metatable);
                    }
                    case 8: { // band
                        BigInteger leftNum = getValue(left), rightNum = getValue(right);
                        return new BigIntegerValue(leftNum.and(rightNum), metatable);
                    }
                    case 9: { // bor
                        BigInteger leftNum = getValue(left), rightNum = getValue(right);
                        return new BigIntegerValue(leftNum.or(rightNum), metatable);
                    }
                    case 10: { // bxor
                        BigInteger leftNum = getValue(left), rightNum = getValue(right);
                        return new BigIntegerValue(leftNum.xor(rightNum), metatable);
                    }
                    case 11: { // shl
                        BigInteger leftNum = getValue(left);
                        return new BigIntegerValue(leftNum.shiftLeft(right.checkInteger()), metatable);
                    }
                    case 12: { // shr
                        BigInteger leftNum = getValue(left);
                        return new BigIntegerValue(leftNum.shiftRight(right.checkInteger()), metatable);
                    }
                    case 13: { // bnot
                        BigInteger leftNum = getValue(left);
                        return new BigIntegerValue(leftNum.not(), metatable);
                    }
                    case 14: { // eq
                        BigInteger leftNum = getValue(left), rightNum = getValue(right);
                        return leftNum.equals(rightNum) ? TRUE : FALSE;
                    }
                    case 15: { // lt
                        BigInteger leftNum = getValue(left), rightNum = getValue(right);
                        return leftNum.compareTo(rightNum) < 0 ? TRUE : FALSE;
                    }
                    case 16: { // le
                        BigInteger leftNum = getValue(left), rightNum = getValue(right);
                        return leftNum.compareTo(rightNum) <= 0 ? TRUE : FALSE;
                    }
                    case 17: { // tostring
                        return valueOf(getValue(left).toString());
                    }
                    case 18: { // tonumber
                        return valueOf(getValue(left).doubleValue());
                    }
                    case 19: { // new
                        if (left instanceof BigIntegerValue) {
                            return left;
                        } else if (left.type() == TSTRING) {
                            try {
                                return new BigIntegerValue(new BigInteger(left.toString()), metatable);
                            } catch (NumberFormatException e) {
                                throw ErrorFactory.argError(left, "number");
                            }
                        } else {
                            return new BigIntegerValue(BigInteger.valueOf(left.checkLong()), metatable);
                        }
                    }
                    case 20: { // modinv
                        BigInteger leftNum = getValue(left), rightNum = getValue(right);
                        return new BigIntegerValue(leftNum.modInverse(rightNum), metatable);
                    }
                    case 21: { // gcd
                        BigInteger leftNum = getValue(left), rightNum = getValue(right);
                        return new BigIntegerValue(leftNum.gcd(rightNum), metatable);
                    }
                    case 22: { // modpow
                        BigInteger leftNum = getValue(left), rightNum = getValue(right), thirdNum = getValue(third);
                        return new BigIntegerValue(leftNum.modPow(rightNum, thirdNum), metatable);
                    }
                    case 23: { // abs
                        BigInteger leftNum = getValue(left);
                        return new BigIntegerValue(leftNum.abs(), metatable);
                    }
                    case 24: { // min
                        BigInteger leftNum = getValue(left), rightNum = getValue(right);
                        return new BigIntegerValue(leftNum.min(rightNum), metatable);
                    }
                    case 25: { // max
                        BigInteger leftNum = getValue(left), rightNum = getValue(right);
                        return new BigIntegerValue(leftNum.max(rightNum), metatable);
                    }
                    case 26: { // isProbPrime
                        BigInteger leftNum = getValue(left);
                        int rightProb = right.optInteger(100);
                        return leftNum.isProbablePrime(rightProb) ? TRUE : FALSE;
                    }
                    case 27: { // nextProbPrime
                        BigInteger leftNum = getValue(left);
                        return new BigIntegerValue(leftNum.nextProbablePrime(), metatable);
                    }
                    case 28: { // newProbPrime
                        int length = left.checkInteger();
                        Random seed = right.isNil() ? state.random : new Random(right.checkInteger());
                        return new BigIntegerValue(BigInteger.probablePrime(length, seed), metatable);
                    }
                    default:
                        throw new LuaError("No such method " + opcode);
                }
            } catch (ArithmeticException e) {
                // TODO: Handle this more sensibly
                return LuaDouble.NAN;
            }
        }

        private static LuaTable makeTable(LuaTable env) {
            LuaTable meta = new LuaTable(0, META_NAMES.length + 2);
            LuaTable table = new LuaTable(0, META_NAMES.length + MAIN_NAMES.length);

            for (int i = 0; i < META_NAMES.length; i++) {
                BigIntegerFunction func = new BigIntegerFunction(meta);
                func.opcode = i;
                func.name = META_NAMES[i];
                func.env = env;
                table.rawset(META_NAMES[i], func);
                meta.rawset("__" + META_NAMES[i], func);
            }

            for (int i = 0; i < MAIN_NAMES.length; i++) {
                BigIntegerFunction func = new BigIntegerFunction(meta);
                func.opcode = i + META_NAMES.length;
                func.name = MAIN_NAMES[i];
                func.env = env;
                table.rawset(MAIN_NAMES[i], func);
            }

            meta.rawset("__index", table);
            meta.rawset("__type", valueOf(NAME));

            return table;
        }
    }
}
