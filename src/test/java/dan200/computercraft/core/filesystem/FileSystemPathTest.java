package dan200.computercraft.core.filesystem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class FileSystemPathTest {

    @Test
    void toLocalPreservesRootAndNestedMountPaths() {
        assertEquals("", FileSystem.toLocal("", ""));
        assertEquals("rom/programs/shell", FileSystem.toLocal("rom/programs/shell", ""));
        assertEquals("", FileSystem.toLocal("rom", "rom"));
        assertEquals("programs/shell", FileSystem.toLocal("rom/programs/shell", "rom"));
        assertEquals("shell", FileSystem.toLocal("rom/programs/shell", "rom/programs"));
    }

    @Test
    void toLocalSanitizesBeforeCheckingContainment() {
        assertEquals("shell", FileSystem.toLocal("/rom/./programs/../shell/", "/rom/"));
        assertEquals("shell", FileSystem.toLocal("rom\\shell", "rom"));
    }

    @Test
    void toLocalRejectsNonContainedPathsWithOrWithoutAssertions() {
        for (String[] example : new String[][] {
            { "romario/file", "rom" }, { "other/file", "rom" }, { "ro", "rom" },
            { "", "rom" }, { "rom/../outside", "rom" }, { "../outside", "" }, { "..", "" } }) {
            IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class, () -> FileSystem.toLocal(example[0], example[1]));
            assertEquals("Path is outside mount location", error.getMessage());
        }
    }
}
