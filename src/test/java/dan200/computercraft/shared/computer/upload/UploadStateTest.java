package dan200.computercraft.shared.computer.upload;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import dan200.computercraft.core.apis.transfer.TransferredFile;

/**
 * Tests for the server-side assembly of an upload.
 *
 * <p>
 * This is where a lying packet lands: the first message declares each file's length and digest, later
 * messages fill it in, and the only thing standing between a hostile client and an out-of-bounds write
 * is {@link FileSlice#apply}. The checksum check is pinned from both sides - a correct upload must
 * pass, one altered byte must fail - because a silently failing check would leave files corrupt on the
 * computer instead of rejecting them.
 * </p>
 */
class UploadStateTest {

    private static FileUpload upload(String name, String contents) {
        byte[] bytes = contents.getBytes(StandardCharsets.UTF_8);
        return new FileUpload(name, bytes, FileUpload.getDigest(bytes));
    }

    /** A file whose bytes will arrive later, as the first message describes it. */
    private static FileUpload announced(String name, int length) {
        return new FileUpload(name, new byte[length], new byte[FileUpload.CHECKSUM_LENGTH]);
    }

    @Test
    void anIntactUploadPassesItsChecksum() {
        FileUpload file = upload("a.txt", "hello");
        UploadState state = new UploadState(1, List.of(new FileUpload("a.txt", new byte[5], file.getChecksum())));

        assertTrue(state.applySlices(List.of(new FileSlice(0, 0, file.getBytes()))));
        assertNull(state.finish());
    }

    @Test
    void oneAlteredByteFailsTheUpload() {
        FileUpload file = upload("a.txt", "hello");
        UploadState state = new UploadState(1, List.of(new FileUpload("a.txt", new byte[5], file.getChecksum())));

        assertTrue(state.applySlices(List.of(new FileSlice(0, 0, "hellp".getBytes(StandardCharsets.UTF_8)))));
        assertEquals("Checksum failed to match for a.txt", state.finish());
    }

    @Test
    void slicesMayArriveOutOfOrder() {
        FileUpload file = upload("a.txt", "abcdef");
        UploadState state = new UploadState(1, List.of(new FileUpload("a.txt", new byte[6], file.getChecksum())));

        byte[] contents = file.getBytes();
        assertTrue(
            state.applySlices(
                List.of(
                    new FileSlice(0, 4, new byte[] { contents[4], contents[5] }),
                    new FileSlice(0, 0, new byte[] { contents[0], contents[1], contents[2], contents[3] }))));

        assertNull(state.finish());
    }

    @Test
    void aSliceOutsideItsFileIsRefused() {
        UploadState state = new UploadState(1, List.of(announced("a.txt", 4)));

        assertFalse(
            state.applySlices(List.of(new FileSlice(0, 2, new byte[4]))),
            "a slice running past the end must be refused, not clipped");
        assertFalse(state.applySlices(List.of(new FileSlice(0, 5, new byte[1]))));
        assertFalse(state.applySlices(List.of(new FileSlice(3, 0, new byte[1]))), "no such file");
        assertTrue(state.applySlices(List.of(new FileSlice(0, 0, new byte[4]))));
    }

    @Test
    void anUploadOnlyMatchesItsOwnId() {
        UploadState state = new UploadState(41, List.of(announced("a.txt", 1)));

        assertTrue(state.matches(41));
        assertFalse(state.matches(42));
    }

    @Test
    void buildsTheLuaVisibleFiles() {
        FileUpload file = upload("a.txt", "hi");
        UploadState state = new UploadState(1, List.of(new FileUpload("a.txt", new byte[2], file.getChecksum())));
        state.applySlices(List.of(new FileSlice(0, 0, file.getBytes())));

        List<TransferredFile> files = state.toTransferredFiles();
        assertEquals(1, files.size());
        assertEquals(
            "a.txt",
            files.get(0)
                .getName());
        assertArrayEquals(
            file.getBytes(),
            files.get(0)
                .getContents());
        assertNull(state.finish(), "the reassembled file is intact");
    }

    @Test
    void totalsWhatAnUploadAnnounces() {
        assertEquals(9, UploadState.totalSize(List.of(announced("a", 4), announced("b", 5))));
        assertEquals(0, UploadState.totalSize(List.of()));
    }
}
