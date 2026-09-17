package dan200.computercraft.core.filesystem;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.Set;

import org.junit.jupiter.api.Test;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.api.filesystem.IMount;
import dan200.computercraft.core.lua.binfs.LuaExceptionStub;

class FileSystemHandleProbeTest {

    @Test
    void rejectedHandleClosesItsStreamWithoutRemovingAcceptedHandles() throws Exception {
        IMount mount = mock(IMount.class);
        when(mount.exists("f.txt")).thenReturn(true);
        InputStream first = mock(InputStream.class);
        InputStream second = mock(InputStream.class);
        InputStream third = mock(InputStream.class);
        InputStream rejected = mock(InputStream.class);
        when(mount.openForRead("f.txt")).thenReturn(first, second, third, rejected);
        FileSystem fs = new FileSystem("probe", mount);
        int savedLimit = ComputerCraft.maxFilesHandles;
        try {
            ComputerCraft.maxFilesHandles = 3;
            fs.openForRead("f.txt");
            fs.openForRead("f.txt");
            fs.openForRead("f.txt");
            Field field = FileSystem.class.getDeclaredField("m_openFiles");
            field.setAccessible(true);
            Set<?> tracked = (Set<?>) field.get(fs);
            assertEquals(3, tracked.size());
            LuaExceptionStub error = assertThrows(LuaExceptionStub.class, () -> fs.openForRead("f.txt"));
            assertEquals("Too many file handles", error.getMessage());
            assertEquals(3, tracked.size());
            verify(rejected).close();
            verify(first, never()).close();
            verify(second, never()).close();
            verify(third, never()).close();
        } finally {
            fs.unload();
            ComputerCraft.maxFilesHandles = savedLimit;
        }
    }
}
