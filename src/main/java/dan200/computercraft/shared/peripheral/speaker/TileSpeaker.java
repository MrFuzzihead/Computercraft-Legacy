package dan200.computercraft.shared.peripheral.speaker;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.IIcon;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.shared.common.TileGeneric;
import dan200.computercraft.shared.network.ComputerCraftPacket;
import dan200.computercraft.shared.peripheral.PeripheralType;
import dan200.computercraft.shared.peripheral.common.IPeripheralTile;

public class TileSpeaker extends TileGeneric implements IPeripheralTile {

    // -------------------------------------------------------------------------
    // Inner data classes
    // -------------------------------------------------------------------------

    static final class PendingNote {

        final String soundEvent;
        final float volume;
        final float pitch;

        PendingNote(String soundEvent, float volume, float pitch) {
            this.soundEvent = soundEvent;
            this.volume = volume;
            this.pitch = pitch;
        }
    }

    static final class PendingSound {

        final String name;
        final float volume;
        final float pitch;

        PendingSound(String name, float volume, float pitch) {
            this.name = name;
            this.volume = volume;
            this.pitch = pitch;
        }
    }

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    /** Facing direction (2–5, matching MC side indices). Stored in NBT. */
    int m_direction = 2;

    /** Notes queued this tick; flushed by {@link #updateEntity()}. Guarded by {@code this}. */
    final List<PendingNote> m_pendingNotes = new ArrayList<>();

    /** Sound queued this tick; flushed by {@link #updateEntity()}. Guarded by {@code this}. */
    PendingSound m_pendingSound = null;

    /** DFPWM encoder + back-pressure state for {@code playAudio}. Guarded by {@code this}. */
    SpeakerAudioState m_audioState = null;

    /** Set by {@code stop()}; processed once in {@link #updateEntity()}. Guarded by {@code this}. */
    boolean m_shouldStop = false;

    /** Computers currently attached to this peripheral. Guarded by {@code this}. */
    private final Set<IComputerAccess> m_computers = new HashSet<>();

    // -------------------------------------------------------------------------
    // IPeripheralTile / IDirectionalTile
    // -------------------------------------------------------------------------

    @Override
    public PeripheralType getPeripheralType() {
        return PeripheralType.Speaker;
    }

    @Override
    public IPeripheral getPeripheral(int side) {
        return new SpeakerPeripheral(this);
    }

    @Override
    public String getLabel() {
        return null;
    }

    @Override
    public int getDirection() {
        return m_direction;
    }

    @Override
    public void setDirection(int dir) {
        if (dir < 2 || dir > 5) dir = 2;
        m_direction = dir;
    }

    // -------------------------------------------------------------------------
    // Computer tracking (called by SpeakerPeripheral.attach / .detach)
    // -------------------------------------------------------------------------

    synchronized void attachComputer(IComputerAccess computer) {
        m_computers.add(computer);
    }

    synchronized void detachComputer(IComputerAccess computer) {
        m_computers.remove(computer);
    }

    // -------------------------------------------------------------------------
    // TileEntity
    // -------------------------------------------------------------------------

    @Override
    public IIcon getTexture(int side) {
        return BlockSpeaker.getSpeakerIcon(side, m_direction);
    }

    @Override
    public void getDroppedItems(java.util.List<ItemStack> drops, int fortune, boolean creative, boolean silkTouch) {
        if (!creative) {
            drops.add(new ItemStack(ComputerCraft.Blocks.speaker));
        }
    }

    @Override
    public ItemStack getPickedItem() {
        return new ItemStack(ComputerCraft.Blocks.speaker);
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        if (nbt.hasKey("dir")) {
            m_direction = nbt.getInteger("dir");
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setInteger("dir", m_direction);
    }

    @Override
    protected void writeDescription(NBTTagCompound nbt) {
        nbt.setInteger("dir", m_direction);
    }

    @Override
    protected void readDescription(NBTTagCompound nbt) {
        m_direction = nbt.getInteger("dir");
        updateBlock();
    }

    @Override
    public void updateEntity() {
        if (worldObj.isRemote) return;

        // ---- 1. Handle stop ----
        boolean shouldStop;
        synchronized (this) {
            shouldStop = m_shouldStop;
            if (shouldStop) {
                m_shouldStop = false;
                m_audioState = null;
                m_pendingSound = null;
            }
        }
        if (shouldStop) {
            ComputerCraftPacket stopPacket = new ComputerCraftPacket();
            stopPacket.m_packetType = ComputerCraftPacket.SpeakerStop;
            stopPacket.m_dataInt = new int[] { xCoord, yCoord, zCoord };
            ComputerCraft.sendToAllPlayers(stopPacket);
            return;
        }

        // ---- 2. Flush pending notes ----
        List<PendingNote> notesToPlay;
        synchronized (this) {
            if (!m_pendingNotes.isEmpty()) {
                notesToPlay = new ArrayList<>(m_pendingNotes);
                m_pendingNotes.clear();
            } else {
                notesToPlay = null;
            }
        }
        if (notesToPlay != null) {
            for (PendingNote note : notesToPlay) {
                worldObj.playSoundEffect(
                    xCoord + 0.5,
                    yCoord + 0.5,
                    zCoord + 0.5,
                    note.soundEvent,
                    note.volume,
                    note.pitch);
            }
        }

        // ---- 3. Flush pending sound ----
        PendingSound pendingSound;
        synchronized (this) {
            pendingSound = m_pendingSound;
            m_pendingSound = null;
        }
        if (pendingSound != null) {
            worldObj.playSoundEffect(
                xCoord + 0.5,
                yCoord + 0.5,
                zCoord + 0.5,
                pendingSound.name,
                pendingSound.volume,
                pendingSound.pitch);
        }

        // ---- 4. Flush pending audio ----
        SpeakerAudioState audioState;
        synchronized (this) {
            audioState = m_audioState;
        }
        if (audioState != null) {
            long now = System.nanoTime();
            if (audioState.shouldSendPending(now)) {
                byte[] dfpwm = audioState.pullPending(now);
                float volume = audioState.pendingVolume;

                ComputerCraftPacket audioPacket = new ComputerCraftPacket();
                audioPacket.m_packetType = ComputerCraftPacket.SpeakerAudio;
                audioPacket.m_dataInt = new int[] { xCoord, yCoord, zCoord, Math.round(volume * 1000) };
                audioPacket.m_dataByte = new byte[][] { dfpwm };
                ComputerCraft.sendToAllAround(
                    audioPacket,
                    worldObj,
                    xCoord + 0.5,
                    yCoord + 0.5,
                    zCoord + 0.5,
                    ComputerCraft.speaker_audio_range);

                // Queue speaker_audio_empty on all attached computers.
                Set<IComputerAccess> snapshot;
                synchronized (this) {
                    snapshot = new HashSet<>(m_computers);
                }
                for (IComputerAccess computer : snapshot) {
                    computer.queueEvent("speaker_audio_empty", new Object[0]);
                }
            }
        }
    }
}
