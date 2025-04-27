package dan200.computercraft.shared.network;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;

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
    public byte m_packetType = 0;
    public String[] m_dataString = null;
    public int[] m_dataInt = null;
    public byte[][] m_dataByte = (byte[][]) null;
    public NBTTagCompound m_dataNBT = null;

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
        byte nString = buffer.readByte();
        byte nInt = buffer.readByte();
        int nByte = buffer.readInt();
        if (nString == 0) {
            this.m_dataString = null;
        } else {
            this.m_dataString = new String[nString];

            for (int k = 0; k < nString; k++) {
                if (buffer.readBoolean()) {
                    int len = buffer.readInt();
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
                if (length > 0) {
                    this.m_dataByte[kx] = new byte[length];
                    buffer.getBytes(buffer.readerIndex(), this.m_dataByte[kx]);
                }
            }
        }

        boolean bNBT = buffer.readBoolean();
        if (!bNBT) {
            this.m_dataNBT = null;
        } else {
            int byteLength = buffer.readInt();
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
