package dan200.computercraft.shared.network;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;

public class ComputerCraftPacket implements IMessage {

    public static final byte TurnOn = 1;
    public static final byte Reboot = 2;
    public static final byte Shutdown = 3;
    public static final byte QueueEvent = 4;
    public static final byte RequestComputerUpdate = 5;
    public static final byte SetLabel = 6;
    public static final byte RequestTileEntityUpdate = 9;
    public static final byte ComputerChanged = 7;
    public static final byte ComputerDeleted = 8;
    public static final byte SpeakerAudio = 10;
    public static final byte SpeakerStop = 11;
    public byte m_packetType = 0;
    public String[] m_dataString = null;
    public int[] m_dataInt = null;
    public byte[][] m_dataByte = (byte[][]) null;
    public NBTTagCompound m_dataNBT = null;

    /**
     * Hard caps on decoded array sizes, defending against malicious packets that
     * declare huge lengths before the payload is read (server OOM vector).
     *
     * <p>
     * These are far above any legitimate use: real packets carry at most 1 string
     * (event name / label), 5 ints (speaker position + volume + format), 1 byte
     * array of at most 128 KiB (speaker audio, see {@code SpeakerPeripheral}),
     * and an NBT compound of at most a few KiB (terminal state).
     * </p>
     */
    private static final int MAX_STRING_COUNT = 64;
    private static final int MAX_INT_COUNT = 64;
    private static final int MAX_BYTE_ARRAY_COUNT = 64;
    private static final int MAX_STRING_LENGTH = 1024 * 1024;
    private static final int MAX_BYTE_ARRAY_LENGTH = 1024 * 1024;
    private static final int MAX_NBT_BYTES = 4 * 1024 * 1024;

    /**
     * Returns true if this packet carries at least {@code count} integer values.
     *
     * <p>
     * All payload arrays of a packet decoded from the network are
     * attacker-controlled, so handlers must guard every indexed access instead
     * of assuming the arrays match the packet type (the S4 NPE/AIOOBE vector
     * from {@code docs/CODEBASE_ANALYSIS.md}).
     * </p>
     */
    public boolean hasInts(int count) {
        return this.m_dataInt != null && this.m_dataInt.length >= count;
    }

    /**
     * Returns true if this packet carries at least {@code count} usable string
     * values (non-null, in {@code m_dataString[0..count-1]}).
     */
    public boolean hasStrings(int count) {
        if (this.m_dataString == null || this.m_dataString.length < count) {
            return false;
        }

        for (int i = 0; i < count; i++) {
            if (this.m_dataString[i] == null) {
                return false;
            }
        }

        return true;
    }

    /**
     * Validates a length read from the buffer before it is used for an
     * allocation. The length must be non-negative, within the given cap, and no
     * larger than the number of bytes actually remaining in the buffer.
     *
     * @param buffer    the buffer being decoded
     * @param requested the declared length/count
     * @param cap       the absolute upper bound for this field
     * @param what      field description used in the error message
     * @throws DecoderException if the declared length is impossible or excessive
     */
    private static void checkLength(ByteBuf buffer, int requested, int cap, String what) {
        if (requested < 0 || requested > cap || requested > buffer.readableBytes()) {
            throw new DecoderException(
                "ComputerCraftPacket: invalid " + what
                    + " length "
                    + requested
                    + " ("
                    + buffer.readableBytes()
                    + " bytes available)");
        }
    }

    public void toBytes(ByteBuf buffer) {
        buffer.writeByte(this.m_packetType);
        if (this.m_dataString != null) {
            buffer.writeByte(this.m_dataString.length);
        } else {
            buffer.writeByte(0);
        }

        if (this.m_dataInt != null) {
            buffer.writeByte(this.m_dataInt.length);
        } else {
            buffer.writeByte(0);
        }

        if (this.m_dataByte != null) {
            buffer.writeInt(this.m_dataByte.length);
        } else {
            buffer.writeInt(0);
        }

        if (this.m_dataString != null) {
            for (String s : this.m_dataString) {
                if (s != null) {
                    try {
                        byte[] b = s.getBytes("UTF-8");
                        buffer.writeBoolean(true);
                        buffer.writeInt(b.length);
                        buffer.writeBytes(b);
                    } catch (UnsupportedEncodingException var8) {
                        buffer.writeBoolean(false);
                    }
                } else {
                    buffer.writeBoolean(false);
                }
            }
        }

        if (this.m_dataInt != null) {
            for (int i : this.m_dataInt) {
                buffer.writeInt(i);
            }
        }

        if (this.m_dataByte != null) {
            for (byte[] bytes : this.m_dataByte) {
                if (bytes != null) {
                    buffer.writeInt(bytes.length);
                    buffer.writeBytes(bytes);
                } else {
                    buffer.writeInt(0);
                }
            }
        }

        if (this.m_dataNBT != null) {
            try {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                CompressedStreamTools.writeCompressed(this.m_dataNBT, bos);
                byte[] bytesx = bos.toByteArray();
                buffer.writeBoolean(true);
                buffer.writeInt(bytesx.length);
                buffer.writeBytes(bytesx);
            } catch (IOException var7) {
                buffer.writeBoolean(false);
            }
        } else {
            buffer.writeBoolean(false);
        }
    }

    public void fromBytes(ByteBuf buffer) {
        this.m_packetType = buffer.readByte();
        int nString = buffer.readUnsignedByte();
        int nInt = buffer.readUnsignedByte();
        int nByte = buffer.readInt();
        checkLength(buffer, nString, MAX_STRING_COUNT, "string count");
        checkLength(buffer, nInt, MAX_INT_COUNT, "int count");
        checkLength(buffer, nByte, MAX_BYTE_ARRAY_COUNT, "byte array count");
        if (nString == 0) {
            this.m_dataString = null;
        } else {
            this.m_dataString = new String[nString];

            for (int k = 0; k < nString; k++) {
                if (buffer.readBoolean()) {
                    int len = buffer.readInt();
                    checkLength(buffer, len, MAX_STRING_LENGTH, "string");
                    byte[] b = new byte[len];
                    buffer.readBytes(b);

                    try {
                        this.m_dataString[k] = new String(b, "UTF-8");
                    } catch (UnsupportedEncodingException var10) {
                        this.m_dataString[k] = null;
                    }
                }
            }
        }

        if (nInt == 0) {
            this.m_dataInt = null;
        } else {
            this.m_dataInt = new int[nInt];

            for (int kx = 0; kx < nInt; kx++) {
                this.m_dataInt[kx] = buffer.readInt();
            }
        }

        if (nByte == 0) {
            this.m_dataByte = (byte[][]) null;
        } else {
            this.m_dataByte = new byte[nByte][];

            for (int kx = 0; kx < nByte; kx++) {
                int length = buffer.readInt();
                checkLength(buffer, length, MAX_BYTE_ARRAY_LENGTH, "byte array");
                if (length > 0) {
                    this.m_dataByte[kx] = new byte[length];
                    buffer.readBytes(this.m_dataByte[kx]);
                }
            }
        }

        boolean bNBT = buffer.readBoolean();
        if (!bNBT) {
            this.m_dataNBT = null;
        } else {
            int byteLength = buffer.readInt();
            checkLength(buffer, byteLength, MAX_NBT_BYTES, "NBT");
            byte[] bytes = new byte[byteLength];
            buffer.getBytes(buffer.readerIndex(), bytes);

            try {
                ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
                this.m_dataNBT = CompressedStreamTools.readCompressed(bis);
            } catch (IOException var9) {
                this.m_dataNBT = null;
            }
        }
    }
}
