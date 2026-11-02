package dan200.computercraft.shared.network;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import dan200.computercraft.shared.computer.upload.FileSlice;
import dan200.computercraft.shared.computer.upload.FileUpload;
import dan200.computercraft.shared.computer.upload.UploadState;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;

/**
 * Unit tests for the file-upload wire format.
 *
 * <p>
 * Two things matter here and both are hostile-input questions: a legitimate upload must survive the
 * round trip byte for byte (a corrupted file would fail the checksum on the server instead, so this
 * pins the format itself), and a malformed message must be refused before anything is allocated,
 * because {@code m_dataByte}/{@code readInt} lengths are attacker-controlled on the netty thread.
 * </p>
 */
class FileUploadPacketTest {

    private static final int LIMIT = 512 * 1024;

    private static FileUpload upload(String name, String contents) {
        byte[] bytes = contents.getBytes(StandardCharsets.UTF_8);
        return new FileUpload(name, bytes, FileUpload.getDigest(bytes));
    }

    private static ByteBuf encode(FileUploadPacket packet) {
        ByteBuf buffer = Unpooled.buffer();
        packet.encode(buffer);
        return buffer;
    }

    @Test
    void roundTripsASingleSmallFile() {
        FileUpload file = upload("hello.txt", "hello world");
        List<FileUploadPacket> packets = FileUploadPacket.build(7, List.of(file));

        assertEquals(1, packets.size(), "a single small file must fit one message");
        FileUploadPacket decoded = FileUploadPacket.decode(encode(packets.get(0)), LIMIT);

        assertEquals(7, decoded.getInstanceID());
        assertTrue(decoded.isFirst());
        assertTrue(decoded.isLast());
        assertEquals(
            1,
            decoded.getFiles()
                .size());
        assertEquals(
            "hello.txt",
            decoded.getFiles()
                .get(0)
                .getName());
        assertArrayEquals(
            file.getBytes(),
            decoded.getSlices()
                .get(0)
                .getBytes(),
            "the slice must carry the file's bytes unchanged");

        // The decoded file is allocated zeroed; only the slices fill it in, so the checksum can
        // only be checked after applying them.
        UploadState state = new UploadState(decoded.getUploadID(), decoded.getFiles());
        assertTrue(state.applySlices(decoded.getSlices()));
        assertTrue(
            decoded.getFiles()
                .get(0)
                .checksumMatches());
    }

    @Test
    void splitsALargeFileAndReassemblesIt() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 60000; i++) {
            builder.append((char) ('a' + (i % 26)));
        }

        FileUpload file = upload("big.txt", builder.toString());
        List<FileUploadPacket> packets = FileUploadPacket.build(3, List.of(file));

        assertTrue(packets.size() > 1, "a 60 KB file must be split across messages");
        assertTrue(
            packets.get(0)
                .isFirst(),
            "the first message announces the files");
        assertFalse(
            packets.get(0)
                .isLast());
        assertFalse(
            packets.get(packets.size() - 1)
                .isFirst(),
            "only the first message carries the file list");
        assertTrue(
            packets.get(packets.size() - 1)
                .isLast());

        // Reassemble exactly as ServerFileUploadHandler does.
        List<FileUploadPacket> decoded = new ArrayList<>();
        for (FileUploadPacket packet : packets) {
            ByteBuf buffer = encode(packet);
            assertTrue(buffer.readableBytes() <= FileUploadPacket.MAX_PACKET_SIZE, "each message fits the budget");
            decoded.add(FileUploadPacket.decode(buffer, LIMIT));
        }

        int uploadID = decoded.get(0)
            .getUploadID();
        UploadState state = new UploadState(
            uploadID,
            decoded.get(0)
                .getFiles());
        for (FileUploadPacket packet : decoded) {
            assertEquals(uploadID, packet.getUploadID(), "every message carries the same upload id");
            assertTrue(state.applySlices(packet.getSlices()));
        }

        assertNull(state.finish(), "reassembled contents must still match the digest");
        assertArrayEquals(file.getBytes(), state.getFiles()[0].getBytes());
    }

    @Test
    void splitsEachFileAtItsOwnOffset() {
        FileUpload first = upload("a.txt", "0123456789");
        FileUpload second = upload("b.txt", "abcdefghij");
        List<FileSlice> slices = new ArrayList<>();
        for (FileUploadPacket packet : FileUploadPacket.build(1, List.of(first, second))) {
            slices.addAll(packet.getSlices());
        }

        FileUpload[] files = { new FileUpload("a.txt", new byte[10], first.getChecksum()),
            new FileUpload("b.txt", new byte[10], second.getChecksum()) };
        for (FileSlice slice : slices) {
            assertTrue(slice.apply(files), "a slice must land inside its own file");
        }

        assertArrayEquals(first.getBytes(), files[0].getBytes());
        assertArrayEquals(second.getBytes(), files[1].getBytes());
    }

    @Test
    void canSendAnEmptyFile() {
        FileUpload file = upload("empty.txt", "");
        List<FileUploadPacket> packets = FileUploadPacket.build(1, List.of(file));
        FileUploadPacket decoded = FileUploadPacket.decode(encode(packets.get(0)), LIMIT);

        assertEquals(
            0,
            decoded.getFiles()
                .get(0)
                .getLength());
        assertTrue(
            decoded.getSlices()
                .isEmpty());
        assertTrue(
            decoded.getFiles()
                .get(0)
                .checksumMatches());
    }

    @Test
    void rejectsATruncatedHeader() {
        ByteBuf buffer = Unpooled.buffer();
        buffer.writeByte(ComputerCraftPacket.FileUpload);
        buffer.writeInt(1);

        assertThrows(DecoderException.class, () -> FileUploadPacket.decode(buffer, LIMIT));
    }

    @Test
    void rejectsTheWrongPacketType() {
        ByteBuf buffer = Unpooled.buffer();
        buffer.writeByte(ComputerCraftPacket.SpeakerAudio);
        buffer.writeInt(0);
        buffer.writeInt(0);
        buffer.writeByte(0);
        buffer.writeInt(0);

        assertThrows(DecoderException.class, () -> FileUploadPacket.decode(buffer, LIMIT));
    }

    @Test
    void rejectsAFileCountAboveTheCap() {
        ByteBuf buffer = Unpooled.buffer();
        buffer.writeByte(ComputerCraftPacket.FileUpload);
        buffer.writeInt(1);
        buffer.writeInt(1);
        buffer.writeByte(1); // FIRST
        buffer.writeInt(UploadState.MAX_FILES + 1);
        buffer.writeInt(0);

        assertThrows(DecoderException.class, () -> FileUploadPacket.decode(buffer, LIMIT));
    }

    @Test
    void rejectsAFileLongerThanTheLimitBeforeAllocating() {
        ByteBuf buffer = Unpooled.buffer();
        buffer.writeByte(ComputerCraftPacket.FileUpload);
        buffer.writeInt(1);
        buffer.writeInt(1);
        buffer.writeByte(1); // FIRST
        buffer.writeInt(1);
        buffer.writeInt(0); // name length
        buffer.writeInt(LIMIT + 1); // declared size
        buffer.writeInt(0);

        assertThrows(DecoderException.class, () -> FileUploadPacket.decode(buffer, LIMIT));
    }

    @Test
    void rejectsFilesWhoseTotalExceedsTheLimit() {
        ByteBuf buffer = Unpooled.buffer();
        buffer.writeByte(ComputerCraftPacket.FileUpload);
        buffer.writeInt(1);
        buffer.writeInt(1);
        buffer.writeByte(1); // FIRST
        buffer.writeInt(2);
        for (int i = 0; i < 2; i++) {
            buffer.writeInt(0); // name length
            buffer.writeInt(LIMIT * 2 / 3 + 1); // each below the limit, together above it
            buffer.writeZero(FileUpload.CHECKSUM_LENGTH);
        }
        buffer.writeInt(0);

        assertThrows(DecoderException.class, () -> FileUploadPacket.decode(buffer, LIMIT));
    }

    @Test
    void rejectsASliceLongerThanItsData() {
        ByteBuf buffer = Unpooled.buffer();
        buffer.writeByte(ComputerCraftPacket.FileUpload);
        buffer.writeInt(1);
        buffer.writeInt(1);
        buffer.writeByte(0);
        buffer.writeInt(1); // one slice
        buffer.writeInt(0);
        buffer.writeInt(0);
        buffer.writeInt(4096); // claims 4096 bytes, supplies none
        assertThrows(DecoderException.class, () -> FileUploadPacket.decode(buffer, LIMIT));
    }

    @Test
    void rejectsANegativeLength() {
        ByteBuf buffer = Unpooled.buffer();
        buffer.writeByte(ComputerCraftPacket.FileUpload);
        buffer.writeInt(1);
        buffer.writeInt(1);
        buffer.writeByte(0);
        buffer.writeInt(-1);

        assertThrows(DecoderException.class, () -> FileUploadPacket.decode(buffer, LIMIT));
    }

    @Test
    void aSliceCannotWriteOutsideItsFile() {
        FileUpload file = new FileUpload("a.txt", new byte[4], new byte[FileUpload.CHECKSUM_LENGTH]);
        FileUpload[] files = { file };

        // Starts inside the file but runs past the end.
        assertFalse(new FileSlice(0, 2, new byte[4]).apply(files));
        // Wholly outside it.
        assertFalse(new FileSlice(0, 4, new byte[1]).apply(files));
        // A file id that was never announced.
        assertFalse(new FileSlice(1, 0, new byte[1]).apply(files));
        // Negative offset.
        assertFalse(new FileSlice(0, -1, new byte[1]).apply(files));
        // And the one that fits.
        assertTrue(new FileSlice(0, 3, new byte[1]).apply(files));
    }

    @Test
    void everyBuiltMessageFitsTheBudgetForManyFiles() {
        List<FileUpload> files = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            files.add(upload("file" + i + ".txt", "x".repeat(9000)));
        }

        for (FileUploadPacket packet : FileUploadPacket.build(1, files)) {
            ByteBuf buffer = encode(packet);
            assertTrue(
                buffer.readableBytes() <= FileUploadPacket.MAX_PACKET_SIZE,
                "message was " + buffer.readableBytes() + " bytes");
            assertNotNull(FileUploadPacket.decode(buffer, LIMIT));
        }
    }
}
