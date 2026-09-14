package dan200.computercraft.shared.network;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.Test;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;

/**
 * Unit tests for {@link ComputerCraftPacket} encoding/decoding.
 *
 * <p>
 * Verifies that legitimate packets (including the largest real ones, such as
 * 128 KiB speaker audio and terminal-state NBT) round-trip unchanged, and that
 * maliciously crafted packets declaring huge lengths are rejected with a
 * {@link DecoderException} instead of triggering multi-gigabyte allocations
 * (the S1 server-OOM vector from {@code docs/CODEBASE_ANALYSIS.md}).
 * </p>
 */
class ComputerCraftPacketTest {

    private static ByteBuf encode(ComputerCraftPacket packet) {
        ByteBuf buffer = Unpooled.buffer();
        packet.toBytes(buffer);
        return buffer;
    }

    private static ComputerCraftPacket decode(ByteBuf buffer) {
        ComputerCraftPacket packet = new ComputerCraftPacket();
        packet.fromBytes(buffer);
        return packet;
    }

    private static ComputerCraftPacket roundTrip(ComputerCraftPacket packet) {
        return decode(encode(packet));
    }

    // =========================================================================
    // Round-trip tests (legitimate traffic must keep working)
    // =========================================================================

    @Test
    void roundTripsEmptyPacket() {
        ComputerCraftPacket packet = new ComputerCraftPacket();
        packet.m_packetType = ComputerCraftPacket.TurnOn;

        ComputerCraftPacket decoded = roundTrip(packet);

        assertEquals(ComputerCraftPacket.TurnOn, decoded.m_packetType);
        assertNull(decoded.m_dataString);
        assertNull(decoded.m_dataInt);
        assertNull(decoded.m_dataByte);
        assertNull(decoded.m_dataNBT);
    }

    @Test
    void roundTripsFullPacket() {
        ComputerCraftPacket packet = new ComputerCraftPacket();
        packet.m_packetType = ComputerCraftPacket.QueueEvent;
        packet.m_dataString = new String[] { "hello", null, "wörld" };
        packet.m_dataInt = new int[] { 1, -2, 3, Integer.MIN_VALUE, Integer.MAX_VALUE };
        packet.m_dataByte = new byte[][] { { 1, 2, 3 }, null, new byte[0] };
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setInteger("x", 42);
        nbt.setString("s", "test");
        packet.m_dataNBT = nbt;

        ComputerCraftPacket decoded = roundTrip(packet);

        assertEquals(ComputerCraftPacket.QueueEvent, decoded.m_packetType);
        assertArrayEquals(packet.m_dataString, decoded.m_dataString);
        assertArrayEquals(packet.m_dataInt, decoded.m_dataInt);
        assertEquals(3, decoded.m_dataByte.length);
        assertArrayEquals(new byte[] { 1, 2, 3 }, decoded.m_dataByte[0]);
        assertNull(decoded.m_dataByte[1]);
        // Legacy asymmetry (pre-dates the length-validation fix): toBytes writes
        // zero-length arrays as length=0, but fromBytes leaves the slot null
        // unless length > 0. Callers (e.g. ComputerCraftProxyCommon) handle null.
        assertNull(decoded.m_dataByte[2]);
        assertEquals(42, decoded.m_dataNBT.getInteger("x"));
        assertEquals("test", decoded.m_dataNBT.getString("s"));
    }

    @Test
    void roundTripsSpeakerAudioPacket() {
        // The largest legitimate packet: 128 KiB of speaker audio (the cap
        // enforced by SpeakerPeripheral), 5 ints, no strings, no NBT.
        ComputerCraftPacket packet = new ComputerCraftPacket();
        packet.m_packetType = ComputerCraftPacket.SpeakerAudio;
        packet.m_dataInt = new int[] { 100, 64, -200, 1500, 0 };
        byte[] audio = new byte[128 * 1024];
        Arrays.fill(audio, (byte) 0x55);
        packet.m_dataByte = new byte[][] { audio };

        ComputerCraftPacket decoded = roundTrip(packet);

        assertEquals(ComputerCraftPacket.SpeakerAudio, decoded.m_packetType);
        assertArrayEquals(packet.m_dataInt, decoded.m_dataInt);
        assertEquals(1, decoded.m_dataByte.length);
        assertArrayEquals(audio, decoded.m_dataByte[0]);
    }

    @Test
    void roundTripsTerminalStateNbt() {
        // Terminal-state NBT is the largest legitimate NBT payload.
        ComputerCraftPacket packet = new ComputerCraftPacket();
        packet.m_packetType = ComputerCraftPacket.ComputerChanged;
        packet.m_dataInt = new int[] { 7 };
        NBTTagCompound nbt = new NBTTagCompound();
        for (int i = 0; i < 20; i++) {
            char[] line = new char[51];
            Arrays.fill(line, (char) ('a' + (i % 26)));
            nbt.setString("l" + i, new String(line));
        }
        packet.m_dataNBT = nbt;

        ComputerCraftPacket decoded = roundTrip(packet);

        assertEquals(ComputerCraftPacket.ComputerChanged, decoded.m_packetType);
        assertArrayEquals(packet.m_dataInt, decoded.m_dataInt);
        for (int i = 0; i < 20; i++) {
            assertEquals(nbt.getString("l" + i), decoded.m_dataNBT.getString("l" + i));
        }
    }

    // =========================================================================
    // Malicious packet tests (must reject without large allocations)
    // =========================================================================

    @Test
    void rejectsHugeStringCount() {
        // type=1, nString=200 (over the 64 cap), nInt=0, nByte=0, no NBT.
        ByteBuf buffer = Unpooled.buffer();
        buffer.writeByte(1);
        buffer.writeByte(200);
        buffer.writeByte(0);
        buffer.writeInt(0);
        buffer.writeBoolean(false);

        ComputerCraftPacket packet = new ComputerCraftPacket();
        assertThrows(DecoderException.class, () -> packet.fromBytes(buffer));
    }

    @Test
    void rejectsHugeStringLength() {
        // Declares a string of 2 GiB but provides no payload bytes.
        ByteBuf buffer = Unpooled.buffer();
        buffer.writeByte(1);
        buffer.writeByte(1);
        buffer.writeByte(0);
        buffer.writeInt(0);
        buffer.writeBoolean(true);
        buffer.writeInt(Integer.MAX_VALUE);

        ComputerCraftPacket packet = new ComputerCraftPacket();
        assertThrows(DecoderException.class, () -> packet.fromBytes(buffer));
    }

    @Test
    void rejectsHugeByteArrayCount() {
        // Declares 0x7FFFFFFF byte arrays; must fail before allocating the
        // outer array (previously: new byte[0x7FFFFFFF][] → OOM).
        ByteBuf buffer = Unpooled.buffer();
        buffer.writeByte(1);
        buffer.writeByte(0);
        buffer.writeByte(0);
        buffer.writeInt(Integer.MAX_VALUE);
        buffer.writeBoolean(false);

        ComputerCraftPacket packet = new ComputerCraftPacket();
        assertThrows(DecoderException.class, () -> packet.fromBytes(buffer));
    }

    @Test
    void rejectsHugeByteArrayLength() {
        // Declares one byte array of 2 GiB with no payload.
        ByteBuf buffer = Unpooled.buffer();
        buffer.writeByte(1);
        buffer.writeByte(0);
        buffer.writeByte(0);
        buffer.writeInt(1);
        buffer.writeInt(Integer.MAX_VALUE);
        buffer.writeBoolean(false);

        ComputerCraftPacket packet = new ComputerCraftPacket();
        assertThrows(DecoderException.class, () -> packet.fromBytes(buffer));
    }

    @Test
    void rejectsHugeNbtLength() {
        // Declares 2 GiB of NBT bytes with no payload.
        ByteBuf buffer = Unpooled.buffer();
        buffer.writeByte(1);
        buffer.writeByte(0);
        buffer.writeByte(0);
        buffer.writeInt(0);
        buffer.writeBoolean(true);
        buffer.writeInt(Integer.MAX_VALUE);

        ComputerCraftPacket packet = new ComputerCraftPacket();
        assertThrows(DecoderException.class, () -> packet.fromBytes(buffer));
    }

    @Test
    void rejectsNegativeCounts() {
        ByteBuf buffer = Unpooled.buffer();
        buffer.writeByte(1);
        buffer.writeByte(0);
        buffer.writeByte(0);
        buffer.writeInt(-5);
        buffer.writeBoolean(false);

        ComputerCraftPacket packet = new ComputerCraftPacket();
        assertThrows(DecoderException.class, () -> packet.fromBytes(buffer));
    }

    @Test
    void rejectsLengthBeyondAvailableBytes() {
        // A well-formed-looking packet whose declared string length exceeds the
        // bytes actually present in the buffer (truncation / over-read guard).
        ByteBuf buffer = Unpooled.buffer();
        buffer.writeByte(1);
        buffer.writeByte(1);
        buffer.writeByte(0);
        buffer.writeInt(0);
        buffer.writeBoolean(true);
        buffer.writeInt(1000);
        buffer.writeBytes(new byte[] { 1, 2, 3 }); // only 3 of the 1000 bytes

        ComputerCraftPacket packet = new ComputerCraftPacket();
        assertThrows(DecoderException.class, () -> packet.fromBytes(buffer));
    }

    @Test
    void decoderExceptionIsCaughtByPacketHandler() {
        // PacketHandler catches Exception (not Throwable); the rejection must
        // therefore surface as a RuntimeException, not an Error, so a hostile
        // packet only produces a logged stack trace rather than crashing the
        // network thread.
        assertEquals(
            RuntimeException.class,
            DecoderException.class.getSuperclass()
                .getSuperclass());
    }

    @Test
    void nbtCompressionRoundTripsIndependently() throws Exception {
        // Sanity check that CompressedStreamTools works in the unit-test
        // environment (no obfuscated dependencies), so the NBT tests above are
        // meaningful.
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setInteger("k", 123);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        CompressedStreamTools.writeCompressed(nbt, bos);
        NBTTagCompound read = CompressedStreamTools.readCompressed(new ByteArrayInputStream(bos.toByteArray()));
        assertEquals(123, read.getInteger("k"));
    }

    // =========================================================================
    // S4: payload-shape predicates used by the packet handlers
    // =========================================================================

    @Test
    void hasIntsRejectsMissingOrShortPayload() {
        ComputerCraftPacket packet = new ComputerCraftPacket();
        assertFalse(packet.hasInts(1));

        packet.m_dataInt = new int[] { 1 };
        assertTrue(packet.hasInts(1));
        assertFalse(packet.hasInts(2));
    }

    @Test
    void hasStringsRejectsMissingOrShortPayload() {
        ComputerCraftPacket packet = new ComputerCraftPacket();
        assertFalse(packet.hasStrings(1));

        packet.m_dataString = new String[] { "a", null };
        assertTrue(packet.hasStrings(1));
        assertFalse(packet.hasStrings(2));
    }

    @Test
    void hasStringsRejectsNullElements() {
        ComputerCraftPacket packet = new ComputerCraftPacket();
        packet.m_dataString = new String[] { null };

        assertFalse(packet.hasStrings(1));
    }

    @Test
    void malformedQueueEventPacketDecodesWithoutStringsAndIsRejected() {
        // A network-decoded packet with string count 0 decodes to a null
        // array; the handler guard must reject it instead of indexing into it
        // (the S4 NPE vector).
        ComputerCraftPacket packet = new ComputerCraftPacket();
        packet.m_packetType = ComputerCraftPacket.QueueEvent;
        packet.m_dataInt = new int[] { 1 };
        packet.m_dataString = null;

        ComputerCraftPacket decoded = roundTrip(packet);

        assertEquals(ComputerCraftPacket.QueueEvent, decoded.m_packetType);
        assertNull(decoded.m_dataString);
        assertFalse(decoded.hasStrings(1));
        assertTrue(decoded.hasInts(1));
    }
}
