package dan200.computercraft.core.lua;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@code rom/modules/main/cc/internal/menu.lua}, the menu bar shared by {@code edit}
 * and {@code paint}.
 *
 * <p>
 * The module exists so both programs handle their footer the same way, and so a menu can be driven
 * by the mouse as well as the keyboard. Those are exactly the behaviours that were missing while
 * each program drew its own bar, so they are pinned here rather than only in game.
 * </p>
 *
 * <p>
 * {@code term} and {@code keys} are stubbed: drawing just has to not throw, while the assertions
 * are about the value {@code handle_event} returns.
 * </p>
 */
class CcInternalMenuTest {

    /** Loads the real module and assigns it to {@code menu}; returns the shuttle source. */
    private static String prelude() {
        return """
            table = table or {}
            if not table.unpack then table.unpack = unpack end

            -- Minimal keys table: the module only compares against these names.
            keys = { left = 203, right = 205, enter = 28, numPadEnter = 156,
                     leftCtrl = 29, rightCtrl = 157, rightAlt = 184 }

            colours = { white = 1, yellow = 16 }
            term = {
              getSize = function() return 20, 10 end,
              setCursorPos = function() end,
              clearLine = function() end,
              setTextColour = function() end,
              write = function() end,
              isColour = function() return true end,
            }

            -- The module requires cc.expect; the real one lives in the ROM, so stub the two
            -- functions it exposes. Argument checking is covered by CcExpectTest.
            function require(name)
              assert(name == "cc.expect", "unexpected require: " .. tostring(name))
              return { expect = function() end, field = function() end }
            end

            -- Load the module from the ROM the same way a program would.
            local chunk = assert(loadstring(_menu_source, "@rom/modules/main/cc/internal/menu.lua"))
            menu = chunk()
            """;
    }

    private static String source() throws java.io.IOException {
        return LuaRomTestSupport.readResource("/assets/computercraft/lua/rom/modules/main/cc/internal/menu.lua")
            // Escape for the long-bracket literal below: the source has no "]==]" sequences, but
            // keep the helper honest if it ever grows one.
            .replace("]==]", "]===]");
    }

    private static LuaRomTestSupport.Capture runMenu(String body) throws java.io.IOException {
        LuaRomTestSupport.Capture capture = new LuaRomTestSupport.Capture();
        var machine = LuaRomTestSupport.machine(capture);
        String src = "_menu_source = [===[\n" + source() + "\n]===]\n" + prelude() + "\n" + body;
        LuaRomTestSupport.run(machine, src);
        return capture;
    }

    // -------------------------------------------------------------------------
    // create / draw
    // -------------------------------------------------------------------------

    @Test
    void createStartsOnTheFirstItem() throws Exception {
        LuaRomTestSupport.Capture cap = runMenu(
            "local m = menu.create({ 'Save', 'Exit' })\n_capture(m.selected, #m.items)");
        assertEquals(1.0, cap.number(0));
        assertEquals(2.0, cap.number(1));
    }

    @Test
    void drawDoesNotThrow() throws Exception {
        LuaRomTestSupport.Capture cap = runMenu("menu.draw(menu.create({ 'Save', 'Exit' }))\n_capture(true)");
        assertTrue(cap.bool(0));
    }

    // -------------------------------------------------------------------------
    // Keyboard
    // -------------------------------------------------------------------------

    @Test
    void rightWrapsToTheFirstItem() throws Exception {
        LuaRomTestSupport.Capture cap = runMenu("""
            local m = menu.create({ 'Save', 'Exit' })
            menu.handle_event(m, 'key', keys.right)
            menu.handle_event(m, 'key', keys.right)
            _capture(m.selected)
            """);
        assertEquals(1.0, cap.number(0), "two items, two presses: back to the first");
    }

    @Test
    void leftMovesBack() throws Exception {
        LuaRomTestSupport.Capture cap = runMenu("""
            local m = menu.create({ 'Save', 'Run', 'Exit' })
            menu.handle_event(m, 'key', keys.right)
            menu.handle_event(m, 'key', keys.right)
            menu.handle_event(m, 'key', keys.left)
            _capture(m.selected)
            """);
        assertEquals(2.0, cap.number(0));
    }

    @Test
    void enterSelectsTheCurrentItem() throws Exception {
        LuaRomTestSupport.Capture cap = runMenu("""
            local m = menu.create({ 'Save', 'Exit' })
            menu.handle_event(m, 'key', keys.right)
            _capture(menu.handle_event(m, 'key', keys.enter))
            """);
        assertEquals("Exit", cap.at(0));
    }

    @Test
    void numPadEnterSelectsToo() throws Exception {
        // The old hand-rolled bars only looked at keys.enter, so a numpad Enter did nothing.
        LuaRomTestSupport.Capture cap = runMenu("""
            local m = menu.create({ 'Save', 'Exit' })
            _capture(menu.handle_event(m, 'key', keys.numPadEnter))
            """);
        assertEquals("Save", cap.at(0));
    }

    @Test
    void ctrlClosesTheMenu() throws Exception {
        LuaRomTestSupport.Capture cap = runMenu(
            "local m = menu.create({ 'Save', 'Exit' })\n_capture(menu.handle_event(m, 'key', keys.leftCtrl))");
        assertEquals(Boolean.FALSE, cap.at(0));
    }

    @Test
    void rightAltClosesTheMenu() throws Exception {
        LuaRomTestSupport.Capture cap = runMenu(
            "local m = menu.create({ 'Save', 'Exit' })\n_capture(menu.handle_event(m, 'key', keys.rightAlt))");
        assertEquals(Boolean.FALSE, cap.at(0));
    }

    @Test
    void typingALetterSelectsTheMatchingItem() throws Exception {
        LuaRomTestSupport.Capture cap = runMenu("""
            local m = menu.create({ 'Save', 'Run', 'Exit' })
            _capture(menu.handle_event(m, 'char', 'r'))
            """);
        assertEquals("Run", cap.at(0));
    }

    @Test
    void unhandledEventsReturnNil() throws Exception {
        LuaRomTestSupport.Capture cap = runMenu("""
            local m = menu.create({ 'Save', 'Exit' })
            _capture(menu.handle_event(m, 'key', 999) == nil, menu.handle_event(m, 'paste', 'x') == nil)
            """);
        assertEquals(Boolean.TRUE, cap.at(0));
        assertEquals(Boolean.TRUE, cap.at(1));
    }

    // -------------------------------------------------------------------------
    // Mouse
    // -------------------------------------------------------------------------

    /**
     * The bar is drawn as {@code [Save] [Exit]} starting at column 1, so item {@code n} owns the
     * columns from its own start up to the next item's start: "Save" is 2..6 with the brackets,
     * "Exit" follows at 8.
     */
    @Test
    void clickingAnItemSelectsIt() throws Exception {
        LuaRomTestSupport.Capture cap = runMenu("""
            local m = menu.create({ 'Save', 'Exit' })
            _capture(menu.handle_event(m, 'mouse_click', 1, 8, 10))
            """);
        assertEquals("Exit", cap.at(0));
    }

    @Test
    void clickingTheFirstItemSelectsIt() throws Exception {
        LuaRomTestSupport.Capture cap = runMenu("""
            local m = menu.create({ 'Save', 'Exit' })
            _capture(menu.handle_event(m, 'mouse_click', 1, 2, 10))
            """);
        assertEquals("Save", cap.at(0));
    }

    @Test
    void clickingOffTheFooterClosesTheMenu() throws Exception {
        LuaRomTestSupport.Capture cap = runMenu(
            "local m = menu.create({ 'Save', 'Exit' })\n_capture(menu.handle_event(m, 'mouse_click', 1, 2, 3))");
        assertEquals(Boolean.FALSE, cap.at(0));
    }

    @Test
    void clickingPastTheLastItemDoesNothing() throws Exception {
        LuaRomTestSupport.Capture cap = runMenu(
            "local m = menu.create({ 'Save', 'Exit' })\n_capture(menu.handle_event(m, 'mouse_click', 1, 18, 10) == nil)");
        assertEquals(Boolean.TRUE, cap.at(0));
    }

    // -------------------------------------------------------------------------
    // Argument checking
    // -------------------------------------------------------------------------

    @Test
    void moduleExposesTheThreeFunctions() throws Exception {
        LuaRomTestSupport.Capture cap = runMenu(
            "_capture(type(menu.create), type(menu.draw), type(menu.handle_event))");
        assertNotNull(cap.at(0));
        assertEquals("function", cap.string(0));
        assertEquals("function", cap.string(1));
        assertEquals("function", cap.string(2));
    }
}
