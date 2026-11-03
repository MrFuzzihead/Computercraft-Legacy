package dan200.computercraft.shared.network;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import dan200.computercraft.shared.computer.upload.FileSlice;
import dan200.computercraft.shared.computer.upload.FileUpload;
import dan200.computercraft.shared.computer.upload.UploadState;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;

/**
 * Client to server: one message of a file upload.
 *
 * <p>
 * Ported from CC: Tweaked's {@code dan200.computercraft.shared.network.server.UploadFileMessage}
 * (MPL-2.0; see NOTICE). The first message of an upload announces the files (names, lengths and
 * SHA-256 digests); every message carries slices, because a file does not necessarily fit one
 * message. Unlike upstream this is not a {@code ComputerCraftPacket}: the generic packet's
 * fixed string/int/byte payload cannot express a per-file list plus slices without encoding the whole
 * thing as NBT, so it has its own codec, dispatched by the packet type byte in
 * {@link PacketHandler}.
 * </p>
 */
public final class FileUploadPacket {

    /** The payload budget of one message. Upstream's value; the netty frame limit is far higher. */
    public static final int MAX_PACKET_SIZE = 30 * 1024;

    private static final int FLAG_FIRST = 1;
    private static final int FLAG_LAST = 2;

    private static final int HEADER_SIZE = 1 + 4 + 4 + 1; // type, instance id, upload id, flags
    private static final int MAX_SLICES = 1024;

    private final int instanceID;
    private final int uploadID;
    private final int flags;
    private final List<FileUpload> files;
    private final List<FileSlice> slices;

    private FileUploadPacket(int instanceID, int uploadID, int flags, List<FileUpload> files, List<FileSlice> slices) {
        this.instanceID = instanceID;
        this.uploadID = uploadID;
        this.flags = flags;
        this.files = files;
        this.slices = slices;
    }

    public int getInstanceID() {
        return this.instanceID;
    }

    public int getUploadID() {
        return this.uploadID;
    }

    public boolean isFirst() {
        return (this.flags & FLAG_FIRST) != 0;
    }

    public boolean isLast() {
        return (this.flags & FLAG_LAST) != 0;
    }

    public List<FileUpload> getFiles() {
        return this.files;
    }

    public List<FileSlice> getSlices() {
        return this.slices;
    }

    public void encode(ByteBuf buffer) {
        buffer.writeByte(ComputerCraftPacket.FileUpload);
        buffer.writeInt(this.instanceID);
        buffer.writeInt(this.uploadID);
        buffer.writeByte(this.flags);

        if (this.isFirst()) {
            buffer.writeInt(this.files.size());
            for (FileUpload file : this.files) {
                byte[] name = file.getName()
                    .getBytes(StandardCharsets.UTF_8);
                buffer.writeInt(name.length);
                buffer.writeBytes(name);
                buffer.writeInt(file.getLength());
                buffer.writeBytes(file.getChecksum());
            }
        }

        buffer.writeInt(this.slices.size());
        for (FileSlice slice : this.slices) {
            buffer.writeInt(slice.getFileId());
            buffer.writeInt(slice.getOffset());
            buffer.writeInt(slice.getBytes().length);
            buffer.writeBytes(slice.getBytes());
        }
    }

    /**
     * Decodes one message.
     *
     * @param buffer      The payload, positioned at the type byte.
     * @param maxFileSize The largest single file or total upload the server will accept.
     * @throws DecoderException if the message is malformed, oversized, or lies about its lengths.
     */
    public static FileUploadPacket decode(ByteBuf buffer, int maxFileSize) {
        if (buffer == null || buffer.readableBytes() < HEADER_SIZE) {
            throw new DecoderException("FileUploadPacket: truncated header");
        }

        int type = buffer.readByte();
        if (type != ComputerCraftPacket.FileUpload) {
            throw new DecoderException("FileUploadPacket: wrong packet type " + type);
        }

        int instanceID = buffer.readInt();
        int uploadID = buffer.readInt();
        int flags = buffer.readByte() & 0xFF;

        List<FileUpload> files = null;
        if ((flags & FLAG_FIRST) != 0) {
            int count = readCount(buffer, UploadState.MAX_FILES, "file count");
            files = new ArrayList<>(count);
            int total = 0;
            for (int i = 0; i < count; i++) {
                int nameLength = readCount(buffer, UploadState.MAX_FILE_NAME, "file name length");
                byte[] name = new byte[nameLength];
                buffer.readBytes(name);

                // A declared size, not a payload: the bytes arrive in later messages, so only the
                // cap applies here.
                int size = readDeclared(buffer, maxFileSize, "file size");
                total += size;
                if (total > maxFileSize) {
                    throw new DecoderException("FileUploadPacket: files are too large");
                }

                byte[] checksum = new byte[FileUpload.CHECKSUM_LENGTH];
                buffer.readBytes(checksum);

                files.add(new FileUpload(new String(name, StandardCharsets.UTF_8), new byte[size], checksum));
            }
        }

        int sliceCount = readCount(buffer, MAX_SLICES, "slice count");
        List<FileSlice> slices = new ArrayList<>(sliceCount);
        for (int i = 0; i < sliceCount; i++) {
            int fileId = buffer.readInt();
            int offset = buffer.readInt();
            int length = readCount(buffer, MAX_PACKET_SIZE, "slice length");
            byte[] bytes = new byte[length];
            buffer.readBytes(bytes);
            slices.add(new FileSlice(fileId, offset, bytes));
        }

        return new FileUploadPacket(instanceID, uploadID, flags, files, slices);
    }

    /**
     * Reads a length whose payload follows in this same buffer (a name, a slice, a count of entries)
     * and refuses it unless it is plausible: non-negative, within the field's cap, and no larger than
     * what is actually left to read. Without this a ~20 byte packet can declare a size of 2 GiB and be
     * allocated before the read fails.
     */
    private static int readCount(ByteBuf buffer, int cap, String what) {
        int value = readDeclared(buffer, cap, what);
        if (value > buffer.readableBytes()) {
            throw new DecoderException(
                "FileUploadPacket: " + what + " " + value + " but only " + buffer.readableBytes() + " bytes left");
        }

        return value;
    }

    /**
     * Reads a length that is only declared here: its payload arrives in a later message, so it can be
     * capped but not compared against this buffer.
     */
    private static int readDeclared(ByteBuf buffer, int cap, String what) {
        if (buffer.readableBytes() < 4) {
            throw new DecoderException("FileUploadPacket: truncated " + what);
        }

        int value = buffer.readInt();
        if (value < 0 || value > cap) {
            throw new DecoderException("FileUploadPacket: invalid " + what + " " + value);
        }

        return value;
    }

    /**
     * Splits an upload into messages that each fit {@link #MAX_PACKET_SIZE}.
     *
     * @param instanceID The target computer.
     * @param files      The files, with their digests already computed.
     * @return The messages, in order. Never empty.
     */
    public static List<FileUploadPacket> build(int instanceID, List<FileUpload> files) {
        int uploadID = java.util.concurrent.ThreadLocalRandom.current()
            .nextInt();

        List<FileUploadPacket> packets = new ArrayList<>();
        List<FileSlice> slices = new ArrayList<>();
        boolean first = true;

        for (int fileId = 0; fileId < files.size(); fileId++) {
            byte[] contents = files.get(fileId)
                .getBytes();
            if (contents.length == 0) {
                continue;
            }

            int offset = 0;
            do {
                // 12 bytes per slice: file id, offset and length, then the payload itself.
                int room = MAX_PACKET_SIZE - sizeOf(files, first, slices);
                if (room <= 12) {
                    packets.add(
                        new FileUploadPacket(
                            instanceID,
                            uploadID,
                            first ? FLAG_FIRST : 0,
                            first ? files : null,
                            new ArrayList<>(slices)));
                    slices.clear();
                    first = false;
                    continue;
                }

                int canWrite = Math.min(room - 12, contents.length - offset);
                byte[] slice = new byte[canWrite];
                System.arraycopy(contents, offset, slice, 0, canWrite);
                slices.add(new FileSlice(fileId, offset, slice));
                offset += canWrite;
            } while (offset < contents.length);
        }

        packets.add(
            new FileUploadPacket(
                instanceID,
                uploadID,
                FLAG_LAST | (first ? FLAG_FIRST : 0),
                first ? files : null,
                new ArrayList<>(slices)));

        return packets;
    }

    /** The size of a message carrying these slices, including every field and every payload byte. */
    private static int sizeOf(List<FileUpload> files, boolean first, List<FileSlice> slices) {
        int size = HEADER_SIZE + 4;
        if (first) {
            size += 4;
            for (FileUpload file : files) {
                size += 4 + file.getName()
                    .getBytes(StandardCharsets.UTF_8).length + 4 + FileUpload.CHECKSUM_LENGTH;
            }
        }

        for (FileSlice slice : slices) {
            size += 12 + slice.getBytes().length;
        }

        return size;
    }
}
