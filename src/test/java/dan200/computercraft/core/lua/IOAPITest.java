package dan200.computercraft.core.lua;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dan200.computercraft.core.apis.FSAPI;
import dan200.computercraft.core.filesystem.FileMount;
import dan200.computercraft.core.filesystem.FileSystem;
import dan200.computercraft.core.filesystem.FileSystemException;
import dan200.computercraft.core.lua.lib.cobalt.CobaltMachine;
import dan200.computercraft.testsupport.StubApiEnvironment;

/**
 * Tests for {@code rom/apis/io}, ported from CC:Tweaked 1.120.2.
 *
 * <h2>Strategy</h2>
 * <p>
 * Handles are backed by a real {@link FileSystem} mounted on a temp directory (the same approach
 * {@code FSAPITest} uses), so modes, the read formats, {@code seek} and {@code close} are
 * exercised against the real {@code fs.open} contract rather than a mock that could drift from it.
 * Only the three standard streams are stubbed: {@code write}/{@code read} are replaced with
 * recorders, and {@code term}/{@code colors} with just enough of a terminal for
 * {@code io.stderr}.
 * </p>
 */
class IOAPITest {

    @TempDir
    Path tempDir;

    private LuaRomTestSupport.Capture capture;
    private CobaltMachine machine;
    private FileSystem fileSystem;
    private String prelude;

    private static final String STREAMS = String.join(
        "\n",
        "-- io.lua defines a global `type`, shadowing the builtin. Keep a reference to the real",
        "-- one before the API loads so tests can inspect plain Lua values.",
        "_type = type",
        "",
        "-- Stubs for the standard streams, which io captures at load time.",
        "_writes = {}",
        "write = function( s ) _writes[#_writes + 1] = tostring( s ) end",
        "_stdin_queue = {}",
        "read = function()",
        "  if #_stdin_queue > 0 then return table.remove( _stdin_queue, 1 ) end",
        "  return nil",
        "end",
        "",
        "-- Minimal terminal + colours for io.stderr.",
        "_text_colour = 1",
        "colors = { red = 16384 }",
        "term = {",
        "  isColour = function() return true end,",
        "  getTextColour = function() return _text_colour end,",
        "  setTextColour = function( c ) _text_colour = c end,",
        "}",
        "");

    @BeforeEach
    void setUp() throws FileSystemException, IOException {
        capture = new LuaRomTestSupport.Capture();
        machine = LuaRomTestSupport.machine(capture);

        fileSystem = new FileSystem("hdd", new FileMount(tempDir.toFile(), 1024 * 1024L));
        // FSAPI caches the FileSystem in startup(), so both calls are required.
        FSAPI fsApi = new FSAPI(new StubApiEnvironment(fileSystem));
        machine.addAPI(fsApi);
        fsApi.startup();

        String expect = LuaRomTestSupport.readResource("/assets/computercraft/lua/rom/modules/main/cc/expect.lua");
        String ioSource = LuaRomTestSupport.readResource("/assets/computercraft/lua/rom/apis/io");
        // The API source must run in its own environment, exactly like os.loadAPI does.
        // If it ran in the shared env it would define a global `read`, which the io.stdin handle
        // looks up through _G.read at call time -- an infinite recursion. The real system
        // never has that problem because each API gets a private env, and the resulting table
        // is exposed as `io`.
        prelude = STREAMS
            // cc.expect is a module with a `return`, so it has to be wrapped before anything
            // else can follow it in the same chunk. io.lua fetches it with require().
            + "local _expect_module = (function()\n"
            + expect
            + "\nend)()\n"
            + "function require( _name )\n"
            + "  if _name == 'cc.expect' then return _expect_module end\n"
            + "  error( 'module not found: ' .. tostring( _name ) )\n"
            + "end\n"
            + "local _io_source = [====[\n"
            + ioSource
            + "\n]====]\n"
            + "local _io_env = setmetatable({}, { __index = _G })\n"
            + "assert(load(_io_source, 'rom/apis/io', 't', _io_env))()\n"
            + "io = _io_env\n";
    }

    /**
     * Releases any file the test left open.
     *
     * <p>
     * Tests deliberately leave handles open to check {@code io.input()}/{@code io.output()} and
     * {@code io.lines()} semantics. On Windows an open {@code RandomAccessFile} prevents JUnit from
     * deleting the temp directory, so the filesystem has to be unloaded before that happens.
     * </p>
     */
    @AfterEach
    void tearDown() {
        if (fileSystem != null) fileSystem.unload();
    }

    /** Runs the io source followed by {@code body}, and returns the capture. */
    private LuaRomTestSupport.Capture run(String body) {
        LuaRomTestSupport.run(machine, prelude + body);
        return capture;
    }

    /**
     * Writes a fixture file into the computer's save directory. Checked I/O errors are rethrown as
     * unchecked: a failure to write into the JUnit temp dir is a broken test, not an assertion to
     * thread through every method.
     */
    private void writeFile(String name, String content) {
        try {
            Files.write(tempDir.resolve(name), content.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    // -------------------------------------------------------------------------
    // io.open / modes
    // -------------------------------------------------------------------------

    @Test
    void openReturnsAFileHandle() {
        writeFile("a.txt", "hello\n");
        LuaRomTestSupport.Capture cap = run(
            "local f = io.open('/a.txt')\n" + "_capture(io.type(f), tostring(f):sub(1, 4), f ~= nil)");
        assertEquals("file", cap.string(0), "io.type() should report an open file");
        assertEquals("file", cap.string(1), "__tostring should start with \"file\"");
        assertEquals(true, cap.bool(2));
    }

    @Test
    void openMissingFileReturnsNilAndReason() {
        LuaRomTestSupport.Capture cap = run("local f, e = io.open('/nope.txt')\n" + "_capture(f == nil, _type(e))");
        assertEquals(true, cap.bool(0));
        assertEquals("string", cap.string(1), "a failed open must explain itself");
    }

    @Test
    void openRejectsBadArguments() {
        LuaRomTestSupport.Capture cap = run(
            "local a = pcall(open)\n" + "local b = pcall(open, 42)\n"
                + "local c = pcall(open, '/x', 5)\n"
                + "_capture(a, b, c)");
        assertEquals(false, cap.bool(0), "io.open() with no path must raise");
        assertEquals(false, cap.bool(1), "a non-string path must raise");
        assertEquals(false, cap.bool(2), "a non-string mode must raise");
    }

    @Test
    void openWithUnsupportedModeRaises() {
        // fs.open rejects the mode rather than returning nil, so io.open propagates that.
        // (Documented difference from Lua 5.1, and the same as CC:Tweaked.)
        writeFile("a.txt", "x");
        LuaRomTestSupport.Capture cap = run("local ok = pcall(open, '/a.txt', 'q')\n_capture(ok)");
        assertEquals(false, cap.bool(0));
    }

    // -------------------------------------------------------------------------
    // Reading
    // -------------------------------------------------------------------------

    @Test
    void readDefaultsToWholeLine() {
        writeFile("lines.txt", "one\ntwo\nthree\n");
        LuaRomTestSupport.Capture cap = run(
            "local f = io.open('/lines.txt')\n" + "local a, b, c, d = f:read(), f:read(), f:read(), f:read()\n"
                + "f:close()\n"
                + "_capture(a, b, c, d == nil)");
        assertEquals("one", cap.string(0));
        assertEquals("two", cap.string(1));
        assertEquals("three", cap.string(2));
        assertEquals(true, cap.bool(3), "io.read() past EOF must return nil");
    }

    @Test
    void readStarLAcceptsTheStarPrefix() {
        writeFile("lines.txt", "one\ntwo");
        LuaRomTestSupport.Capture cap = run(
            "local f = io.open('/lines.txt')\nlocal v = f:read('*l')\nf:close()\n_capture(v)");
        assertEquals("one", cap.string(0));
    }

    @Test
    void readBigLIncludesTheTrailingNewline() {
        writeFile("lines.txt", "one\ntwo");
        LuaRomTestSupport.Capture cap = run(
            "local f = io.open('/lines.txt')\n" + "local a = f:read('*L')\n"
                + "local b = f:read('L')\n"
                + "f:close()\n"
                + "_capture(a, b)");
        assertEquals("one\n", cap.string(0));
        assertEquals("two", cap.string(1), "the last line has no trailing newline to keep");
    }

    @Test
    void readStarAReturnsTheRestOfTheFile() {
        writeFile("a.txt", "abcdef");
        LuaRomTestSupport.Capture cap = run(
            "local f = io.open('/a.txt')\nlocal v = f:read('*a')\nf:close()\n_capture(v)");
        assertEquals("abcdef", cap.string(0));
    }

    @Test
    void readAtEofReturnsEmptyStringForStarA() {
        writeFile("a.txt", "");
        LuaRomTestSupport.Capture cap = run(
            "local f = io.open('/a.txt')\nlocal v = f:read('*a')\nf:close()\n_capture(v)");
        assertEquals("", cap.string(0), "an empty file must read as \"\", not nil");
    }

    @Test
    void readNumericFormatReadsBytes() {
        writeFile("a.txt", "abcdef");
        LuaRomTestSupport.Capture cap = run(
            "local f = io.open('/a.txt')\nlocal a = f:read(3)\nlocal b = f:read(2)\nf:close()\n_capture(a, b)");
        assertEquals("abc", cap.string(0));
        assertEquals("de", cap.string(1));
    }

    @Test
    void readMultipleFormatsInOneCall() {
        writeFile("a.txt", "one\ntwo\nrest of file");
        LuaRomTestSupport.Capture cap = run(
            "local f = io.open('/a.txt')\nlocal a, b, c = f:read('l', 'L', 'a')\nf:close()\n_capture(a, b, c)");
        assertEquals("one", cap.string(0));
        assertEquals("two\n", cap.string(1));
        assertEquals("rest of file", cap.string(2));
    }

    @Test
    void readRejectsAnInvalidFormat() {
        writeFile("a.txt", "x");
        LuaRomTestSupport.Capture cap = run(
            "local f = io.open('/a.txt')\nlocal ok, err = pcall(f.read, f, 'z')\nf:close()\n_capture(ok, _type(err))");
        assertEquals(false, cap.bool(0));
        assertEquals("string", cap.string(1));
    }

    @Test
    void readNumberFormatIsNotImplemented() {
        // Lua 5.1's "*n" is deliberately unsupported: it yields nil rather than guessing.
        writeFile("a.txt", "42");
        LuaRomTestSupport.Capture cap = run(
            "local f = io.open('/a.txt')\nlocal v = f:read('*n')\nf:close()\n_capture(v == nil)");
        assertEquals(true, cap.bool(0));
    }

    @Test
    void readOnAWriteOnlyHandleReturnsNilAndReason() {
        LuaRomTestSupport.Capture cap = run(
            "local f = io.open('/w.txt', 'w')\nlocal v, e = f:read()\nf:close()\n_capture(v == nil, _type(e))");
        assertEquals(true, cap.bool(0));
        assertEquals("string", cap.string(1), "reading a write-only file should explain itself");
    }

    // -------------------------------------------------------------------------
    // Writing
    // -------------------------------------------------------------------------

    @Test
    void writeAcceptsStringsAndNumbersAndReturnsSelf() {
        LuaRomTestSupport.Capture cap = run(
            "local f = io.open('/w.txt', 'w')\n" + "local r = f:write('a', 1, 'b')\n"
                + "f:close()\n"
                + "local g = io.open('/w.txt')\n"
                + "local contents = g:read('*a')\n"
                + "g:close()\n"
                + "_capture(r == f, contents)");
        assertEquals(true, cap.bool(0), "write must return the handle for chaining");
        assertEquals("a1b", cap.string(1));
    }

    @Test
    void writeRejectsNonStringValues() {
        LuaRomTestSupport.Capture cap = run(
            "local f = io.open('/w.txt', 'w')\n" + "local a = pcall(f.write, f, true)\n"
                + "local b = pcall(f.write, f, {})\n"
                + "f:close()\n"
                + "_capture(a, b)");
        assertEquals(false, cap.bool(0), "booleans are not writable");
        assertEquals(false, cap.bool(1), "tables are not writable");
    }

    @Test
    void writeOnAReadOnlyHandleReturnsNilAndReason() {
        writeFile("a.txt", "x");
        LuaRomTestSupport.Capture cap = run(
            "local f = io.open('/a.txt')\nlocal v, e = f:write('y')\nf:close()\n_capture(v == nil, _type(e))");
        assertEquals(true, cap.bool(0));
        assertEquals("string", cap.string(1));
    }

    @Test
    void appendModeKeepsExistingContent() {
        writeFile("a.txt", "first");
        LuaRomTestSupport.Capture cap = run(
            "local f = io.open('/a.txt', 'a')\nf:write('second')\nf:close()\n"
                + "local g = io.open('/a.txt')\nlocal v = g:read('*a')\ng:close()\n_capture(v)");
        assertEquals("firstsecond", cap.string(0));
    }

    // -------------------------------------------------------------------------
    // lines
    // -------------------------------------------------------------------------

    @Test
    void handleLinesIteratesWithoutClosingTheHandle() {
        writeFile("a.txt", "l1\nl2\nl3\n");
        LuaRomTestSupport.Capture cap = run(
            "local f = io.open('/a.txt')\n" + "local out = {}\n"
                + "for line in f:lines() do out[#out + 1] = line end\n"
                + "_capture(#out, table.concat(out, ','), io.type(f))");
        assertEquals(3.0, cap.number(0));
        assertEquals("l1,l2,l3", cap.string(1));
        assertEquals("file", cap.string(2), "handle:lines must not close the handle");
    }

    @Test
    void handleLinesPassesFormatsThrough() {
        writeFile("a.txt", "l1\nl2\n");
        LuaRomTestSupport.Capture cap = run(
            "local f = io.open('/a.txt')\n" + "local out = {}\n"
                + "for line in f:lines('L') do out[#out + 1] = line end\n"
                + "f:close()\n"
                + "_capture(table.concat(out, '|'))");
        assertEquals("l1\n|l2\n", cap.string(0));
    }

    @Test
    void ioLinesClosesTheFileAtEof() {
        writeFile("a.txt", "l1\nl2\n");
        LuaRomTestSupport.Capture cap = run(
            "local n = 0\n" + "for line in io.lines('/a.txt') do n = n + 1 end\n"
                + "-- io.lines owns its handle, so nothing should be left open.\n"
                + "_capture(n, io.type(io.open('/a.txt')))");
        assertEquals(2.0, cap.number(0));
        assertEquals("file", cap.string(1));
    }

    @Test
    void ioLinesWithNoFilenameUsesTheCurrentInput() {
        writeFile("a.txt", "only\n");
        LuaRomTestSupport.Capture cap = run(
            "io.input('/a.txt')\n" + "local out = {}\n"
                + "for line in io.lines() do out[#out + 1] = line end\n"
                + "_capture(table.concat(out, ','))");
        assertEquals("only", cap.string(0));
    }

    @Test
    void ioLinesOnAMissingFileRaises() {
        LuaRomTestSupport.Capture cap = run("local ok, err = pcall(lines, '/missing.txt')\n_capture(ok, _type(err))");
        assertEquals(false, cap.bool(0));
        assertEquals("string", cap.string(1));
    }

    // -------------------------------------------------------------------------
    // seek
    // -------------------------------------------------------------------------

    @Test
    void seekReportsAndMovesThePosition() {
        writeFile("a.txt", "0123456789");
        LuaRomTestSupport.Capture cap = run(
            "local f = io.open('/a.txt')\n" + "local start = f:seek()\n"
                + "local fromEnd = f:seek('end')\n"
                + "f:seek('set', 2)\n"
                + "local read = f:read(3)\n"
                + "local afterRead = f:seek()\n"
                + "f:close()\n"
                + "_capture(start, fromEnd, read, afterRead)");
        assertEquals(0.0, cap.number(0), "a fresh handle starts at 0");
        assertEquals(10.0, cap.number(1), "seek('end') reports the size");
        assertEquals("234", cap.string(2));
        assertEquals(5.0, cap.number(3), "reading advances the cursor");
    }

    @Test
    void seekOnAnUnseekableStreamReturnsNilAndReason() {
        // The default streams have no underlying handle, so they are not seekable.
        LuaRomTestSupport.Capture cap = run("local v, e = io.stdout:seek()\n_capture(v == nil, _type(e))");
        assertEquals(true, cap.bool(0));
        assertEquals("string", cap.string(1));
    }

    // -------------------------------------------------------------------------
    // Standard streams
    // -------------------------------------------------------------------------

    @Test
    void writesGoToTheCurrentOutput() {
        LuaRomTestSupport.Capture cap = run("io.write('a', 'b', 3)\n_capture(table.concat(_writes))");
        assertEquals("ab3", cap.string(0));
    }

    @Test
    void writeReturnsTheOutputHandle() {
        LuaRomTestSupport.Capture cap = run("_capture(io.write('x') == io.stdout)");
        assertEquals(true, cap.bool(0));
    }

    @Test
    void stdoutAndStderrAreFileHandles() {
        LuaRomTestSupport.Capture cap = run("_capture(io.type(io.stdin), io.type(io.stdout), io.type(io.stderr))");
        assertEquals("file", cap.string(0));
        assertEquals("file", cap.string(1));
        assertEquals("file", cap.string(2));
    }

    @Test
    void stderrWritesInRedAndRestoresTheColour() {
        LuaRomTestSupport.Capture cap = run(
            "_text_colour = 8\n" + "io.stderr:write('bad')\n" + "_capture(table.concat(_writes), _text_colour)");
        assertEquals("bad", cap.string(0));
        assertEquals(8.0, cap.number(1), "the previous text colour must be restored");
    }

    @Test
    void closingAStandardStreamReturnsNilAndReason() {
        LuaRomTestSupport.Capture cap = run("local v, e = io.stdout:close()\n_capture(v == nil, _type(e))");
        assertEquals(true, cap.bool(0));
        assertEquals("string", cap.string(1));
    }

    @Test
    void ioCloseWithNoArgumentClosesTheCurrentOutput() {
        LuaRomTestSupport.Capture cap = run(
            "local f = io.open('/w.txt', 'w')\n" + "io.output(f)\n"
                + "local v = io.close()\n"
                + "io.output(io.stdout)\n"
                + "_capture(v, io.type(f))");
        assertEquals(true, cap.bool(0));
        assertEquals("closed file", cap.string(1));
    }

    @Test
    void ioCloseRejectsANonHandle() {
        LuaRomTestSupport.Capture cap = run("local ok = pcall(close, {})\n_capture(ok)");
        assertEquals(false, cap.bool(0));
    }

    // -------------------------------------------------------------------------
    // input / output
    // -------------------------------------------------------------------------

    @Test
    void inputAndOutputDoubleAsGetters() {
        LuaRomTestSupport.Capture cap = run("_capture(io.input() == io.stdin, io.output() == io.stdout)");
        assertEquals(true, cap.bool(0));
        assertEquals(true, cap.bool(1));
    }

    @Test
    void inputAcceptsAHandleAndAPath() {
        writeFile("a.txt", "via path\n");
        LuaRomTestSupport.Capture cap = run(
            "local f = io.open('/a.txt')\n" + "io.input(f)\n"
                + "local a = io.read()\n"
                + "io.input('/a.txt')\n"
                + "local b = io.read()\n"
                + "io.input(io.stdin)\n"
                + "_capture(a, b)");
        assertEquals("via path", cap.string(0));
        assertEquals("via path", cap.string(1));
    }

    @Test
    void outputAcceptsAHandleAndAPath() {
        LuaRomTestSupport.Capture cap = run(
            // Switching output does not close the previous file (standard Lua behaves the same),
            // so close it explicitly before reading it back.
            "local out = io.output('/out.txt')\n" + "io.write('to file')\n"
                + "out:close()\n"
                + "io.output(io.stdout)\n"
                + "io.write('to screen')\n"
                + "local f = io.open('/out.txt')\n"
                + "local v = f:read('*a')\n"
                + "f:close()\n"
                + "_capture(table.concat(_writes, '|'), v)");
        assertEquals("to screen", cap.string(0), "the file write must not reach the terminal");
        assertEquals("to file", cap.string(1));
    }

    @Test
    void inputAndOutputRejectBadArguments() {
        LuaRomTestSupport.Capture cap = run(
            "local a = pcall(input, 42)\n" + "local b = pcall(output, {})\n"
                + "local c = pcall(input, '/does-not-exist')\n"
                + "_capture(a, b, c)");
        assertEquals(false, cap.bool(0));
        assertEquals(false, cap.bool(1));
        assertEquals(false, cap.bool(2), "a path that cannot be opened must raise");
    }

    @Test
    void readDelegatesToTheCurrentInput() {
        writeFile("a.txt", "line\n");
        LuaRomTestSupport.Capture cap = run("io.input('/a.txt')\nlocal v = io.read()\n_capture(v)");
        assertEquals("line", cap.string(0));
    }

    // -------------------------------------------------------------------------
    // io.type / closed handles
    // -------------------------------------------------------------------------

    @Test
    void typeDistinguishesOpenClosedAndOther() {
        LuaRomTestSupport.Capture cap = run(
            "local f = io.open('/x.txt', 'w')\n" + "local before = io.type(f)\n"
                + "f:close()\n"
                + "local after = io.type(f)\n"
                + "_capture(before, after, io.type({}), io.type('str'))");
        assertEquals("file", cap.string(0));
        assertEquals("closed file", cap.string(1));
        assertNull(cap.at(2), "a plain table is not a file handle");
        assertNull(cap.at(3));
    }

    @Test
    void usingAClosedHandleRaises() {
        LuaRomTestSupport.Capture cap = run(
            "local f = io.open('/x.txt', 'w')\n" + "f:close()\n"
                + "local a = pcall(f.read, f)\n"
                + "local b = pcall(f.write, f, 'x')\n"
                + "local c = pcall(f.close, f)\n"
                + "local d = pcall(f.seek, f)\n"
                + "local e = pcall(f.flush, f)\n"
                + "_capture(a, b, c, d, e)");
        assertEquals(false, cap.bool(0), "read on a closed file must raise");
        assertEquals(false, cap.bool(1), "write on a closed file must raise");
        assertEquals(false, cap.bool(2), "double close must raise");
        assertEquals(false, cap.bool(3), "seek on a closed file must raise");
        assertEquals(false, cap.bool(4), "flush on a closed file must raise");
    }

    @Test
    void closeReturnsTrue() {
        LuaRomTestSupport.Capture cap = run("local f = io.open('/x.txt', 'w')\n_capture(f:close())");
        assertEquals(true, cap.bool(0));
    }

    @Test
    void flushReturnsTrue() {
        LuaRomTestSupport.Capture cap = run(
            "local f = io.open('/x.txt', 'w')\nlocal v = f:flush()\nf:close()\n_capture(v)");
        assertEquals(true, cap.bool(0));
    }

    @Test
    void setvbufIsANoOp() {
        LuaRomTestSupport.Capture cap = run(
            "local f = io.open('/x.txt', 'w')\nlocal a = pcall(f.setvbuf, f, 'full', 1024)\nf:close()\n_capture(a)");
        assertEquals(true, cap.bool(0), "setvbuf must exist and accept its arguments");
    }

    // -------------------------------------------------------------------------
    // Handle method errors
    // -------------------------------------------------------------------------

    @Test
    void handleMethodsRejectNonHandles() {
        // The method can be reached through the metatable with any receiver.
        LuaRomTestSupport.Capture cap = run(
            "local ok, err = pcall(function() " + "return getmetatable(io.stdout).__index.read({}) end)\n"
                + "_capture(ok, tostring(err):find('FILE expected') ~= nil)");
        assertEquals(false, cap.bool(0));
        assertEquals(true, cap.bool(1), "the error should name FILE");
    }

    @Test
    void defaultInputReadsThroughTheReadGlobal() {
        LuaRomTestSupport.Capture cap = run(
            "_stdin_queue = { 'typed', 'typed' }\n" + "local a = io.stdin:read()\n"
                + "local b = io.stdin:read('L')\n"
                + "_capture(a, b)");
        assertEquals("typed", cap.string(0));
        // Upstream would pass `true` into io.read() here and raise; the port wraps it instead.
        assertEquals("typed", cap.string(1), "io.stdin:read('*L') must not raise");
    }

    @Test
    void stdinLinesYieldsTypedLines() {
        // lines() requires handle.read upstream, but the standard input only provides readLine,
        // so io.stdin:lines() returned nil, "file is not readable" even though read() works.
        LuaRomTestSupport.Capture cap = run(
            "_stdin_queue = { 'first', 'second' }\n" + "local out = {}\n"
                + "for line in io.stdin:lines() do out[#out + 1] = line end\n"
                + "_capture( #out, table.concat( out, '|' ) )");
        assertEquals(2.0, cap.number(0));
        assertEquals("first|second", cap.string(1));
    }

    @Test
    void ioLinesWithNoFilenameReadsStdin() {
        LuaRomTestSupport.Capture cap = run(
            "_stdin_queue = { 'only' }\n" + "local out = {}\n"
                + "for line in io.lines() do out[#out + 1] = line end\n"
                + "_capture( table.concat( out, '|' ) )");
        assertEquals("only", cap.string(0));
    }

    @Test
    void ioSourceIsLoadable() {
        assertNotNull(prelude);
        assertFalse(prelude.isEmpty());
        assertTrue(prelude.contains("handleMetatable"));
    }
}
