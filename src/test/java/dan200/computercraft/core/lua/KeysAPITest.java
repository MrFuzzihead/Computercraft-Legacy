package dan200.computercraft.core.lua;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Field;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.squiddev.cobalt.LuaTable;

import dan200.computercraft.core.lua.LuaRomTestSupport.Capture;
import dan200.computercraft.core.lua.lib.cobalt.CobaltMachine;

/**
 * Tests for {@code rom/apis/keys}.
 *
 * <h2>What is actually being asserted</h2>
 * <p>
 * 1.7.10 delivers LWJGL2 key codes, which are the {@code org.lwjgl.input.Keyboard.KEY_*}
 * constants — a completely different numbering scheme from the GLFW scancodes modern
 * CC:Tweaked uses. Parity work therefore must <em>not</em> copy the modern numeric values; it
 * may only add the modern <em>names</em>. These tests pin both halves:
 * </p>
 * <ul>
 * <li>every legacy constant keeps its original LWJGL2 value, so no existing program changes
 * meaning;</li>
 * <li>every modern alias exists and resolves to the same code as its legacy spelling;</li>
 * <li>keys 1.7.10 physically cannot deliver ({@code f20}-{@code f25},
 * {@code world1}/{@code world2}) are absent rather than invented.</li>
 * </ul>
 */
class KeysAPITest {

    private static String keysSource;

    @BeforeAll
    static void loadSource() throws IOException {
        keysSource = LuaRomTestSupport.readResource("/assets/computercraft/lua/rom/apis/keys");
    }

    /**
     * The {@code keys} ROM file assigns straight into {@code _ENV}, so loading it into a machine
     * leaves {@code enter}, {@code up}, ... as globals plus a global {@code getName}. That is
     * exactly the shape {@code os.loadAPI} copies into the {@code keys} table.
     */
    private static LuaTable globalsAfterLoadingKeys() {
        CobaltMachine machine = LuaRomTestSupport.machine(new Capture());
        LuaRomTestSupport.run(machine, keysSource, "_loaded = true");
        LuaTable globals = globalsOf(machine);
        assertEquals(
            true,
            globals.rawget("_loaded")
                .toBoolean(),
            "keys ROM file failed to run to completion");
        return globals;
    }

    private static LuaTable globalsOf(CobaltMachine machine) {
        try {
            Field f = CobaltMachine.class.getDeclaredField("globals");
            f.setAccessible(true);
            return (LuaTable) f.get(machine);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static int key(LuaTable keys, String name) {
        assertTrue(
            keys.rawget(name)
                .isNumber(),
            "keys." + name + " must be defined as a number");
        return keys.rawget(name)
            .toInteger();
    }

    // -------------------------------------------------------------------------
    // Legacy values
    // -------------------------------------------------------------------------

    @Test
    void legacyConstantsKeepTheirLwjgl2Values() {
        LuaTable keys = globalsAfterLoadingKeys();

        // Spot-check one value from each block of the table. If any of these move, existing
        // programs change meaning, so they are pinned individually.
        assertEquals(2, key(keys, "one"), "KEY_1");
        assertEquals(11, key(keys, "zero"), "KEY_0");
        assertEquals(14, key(keys, "backspace"), "KEY_BACK");
        assertEquals(15, key(keys, "tab"), "KEY_TAB");
        assertEquals(28, key(keys, "enter"), "KEY_RETURN");
        assertEquals(29, key(keys, "leftCtrl"), "KEY_LCONTROL");
        assertEquals(39, key(keys, "semiColon"), "KEY_SEMICOLON");
        assertEquals(57, key(keys, "space"), "KEY_SPACE");
        assertEquals(59, key(keys, "f1"), "KEY_F1");
        assertEquals(70, key(keys, "scollLock"), "KEY_SCROLL");
        assertEquals(87, key(keys, "f11"), "KEY_F11");
        assertEquals(144, key(keys, "cimcumflex"), "KEY_CIRCUMFLEX");
        assertEquals(184, key(keys, "rightAlt"), "KEY_RMENU");
        assertEquals(197, key(keys, "pause"), "KEY_PAUSE");
        assertEquals(199, key(keys, "home"), "KEY_HOME");
        assertEquals(200, key(keys, "up"), "KEY_UP");
        assertEquals(205, key(keys, "right"), "KEY_RIGHT");
        assertEquals(208, key(keys, "down"), "KEY_DOWN");
        assertEquals(211, key(keys, "delete"), "KEY_DELETE");
    }

    @Test
    void returnRemainsAnAliasOfEnter() {
        LuaTable keys = globalsAfterLoadingKeys();
        assertEquals(key(keys, "enter"), key(keys, "return"));
    }

    /**
     * Regression guard for the historical off-by-four in the original table.
     *
     * <p>
     * ComputerCraft 1.75's {@code keys} file placed {@code numPadEnter} and {@code rightCtrl} at
     * codes 152 and 153. LWJGL2 defines nothing there — the real values are 156 and 157
     * ({@code KEY_NUMPADENTER}, {@code KEY_RCONTROL}) — so both constants could never match a
     * key event. They are corrected here; the values are pinned so the old numbers cannot come
     * back.
     * </p>
     */
    @Test
    void numPadEnterAndRightCtrlMatchLwjgl2() {
        LuaTable keys = globalsAfterLoadingKeys();

        assertEquals(156, key(keys, "numPadEnter"), "KEY_NUMPADENTER");
        assertEquals(157, key(keys, "rightCtrl"), "KEY_RCONTROL");
    }

    @Test
    void getNameAgreesWithTheCorrectedCodes() {
        Capture capture = new Capture();
        CobaltMachine machine = LuaRomTestSupport.machine(capture);
        LuaRomTestSupport.run(machine, keysSource, "_capture(getName(151), getName(152), getName(156), getName(157))");

        // 152/153 are not LWJGL2 key codes, so no name may be registered there any more.
        assertNull(capture.at(0), "code 151 (KEY_UNLABELED) is intentionally unnamed");
        assertNull(capture.at(1), "code 152 must not map to numPadEnter");
        assertEquals("numPadEnter", capture.string(2));
        assertEquals("rightCtrl", capture.string(3));
    }

    // -------------------------------------------------------------------------
    // Modern aliases
    // -------------------------------------------------------------------------

    @Test
    void modernSpellingsAliasTheLegacyCodes() {
        LuaTable keys = globalsAfterLoadingKeys();

        assertAlias(keys, "semicolon", "semiColon");
        assertAlias(keys, "scrollLock", "scollLock");
        assertAlias(keys, "circumflex", "cimcumflex");
        assertAlias(keys, "numPadMultiply", "multiply");
        assertAlias(keys, "numPadEqual", "numPadEquals");
    }

    private static void assertAlias(LuaTable keys, String modern, String legacy) {
        assertEquals(key(keys, legacy), key(keys, modern), "keys." + modern + " must alias keys." + legacy);
    }

    @Test
    void omittedLwjgl2KeysAreAdded() {
        LuaTable keys = globalsAfterLoadingKeys();

        assertEquals(103, key(keys, "f16"), "KEY_F16");
        assertEquals(104, key(keys, "f17"), "KEY_F17");
        assertEquals(105, key(keys, "f18"), "KEY_F18");
        assertEquals(113, key(keys, "f19"), "KEY_F19");
        assertEquals(183, key(keys, "printScreen"), "KEY_SYSRQ");
        assertEquals(219, key(keys, "leftSuper"), "KEY_LMETA");
        assertEquals(220, key(keys, "rightSuper"), "KEY_RMETA");
        assertEquals(221, key(keys, "menu"), "KEY_APPS");
    }

    @Test
    void keysThePlatformCannotDeliverAreNotInvented() {
        LuaTable keys = globalsAfterLoadingKeys();

        // LWJGL2 stops at KEY_F19, so f20-f25 must not be fabricated.
        for (int i = 20; i <= 25; i++) {
            assertTrue(
                keys.rawget("f" + i)
                    .isNil(),
                "keys.f" + i + " must not exist: 1.7.10/LWJGL2 has no such key code");
        }
        // No LWJGL2 constant exists for these keys; CC:Tweaked leaves them commented out too.
        assertTrue(
            keys.rawget("world1")
                .isNil(),
            "keys.world1 must not exist on 1.7.10");
        assertTrue(
            keys.rawget("world2")
                .isNil(),
            "keys.world2 must not exist on 1.7.10");
    }

    // -------------------------------------------------------------------------
    // getName
    // -------------------------------------------------------------------------

    @Test
    void getNameResolvesLegacyAndAddedNames() {
        Capture capture = new Capture();
        CobaltMachine machine = LuaRomTestSupport.machine(capture);
        LuaRomTestSupport.run(
            machine,
            keysSource,
            "_capture(getName(39), getName(70), getName(103), getName(183), getName(219), getName(221), getName(190))");

        // getName keeps the canonical (legacy) spelling so output stays stable.
        assertEquals("semiColon", capture.string(0));
        assertEquals("scollLock", capture.string(1));
        // Newly added names must be resolvable through the same function.
        assertEquals("f16", capture.string(2));
        assertEquals("printScreen", capture.string(3));
        assertEquals("leftSuper", capture.string(4));
        assertEquals("menu", capture.string(5));
        assertNull(capture.at(6), "getName must return nil for an unmapped code");
    }
}
