package dan200.computercraft.shared.computer.blocks;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.shared.computer.apis.CommandAPI;
import dan200.computercraft.shared.computer.core.IComputer;
import dan200.computercraft.shared.computer.core.ServerComputer;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.command.server.CommandBlockLogic;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;

public class TileCommandComputer extends TileComputer {
   private TileCommandComputer.CommandSender m_commandSender = new TileCommandComputer.CommandSender();

   public TileCommandComputer.CommandSender getCommandSender() {
      return this.m_commandSender;
   }

   @Override
   protected ServerComputer createComputer(int instanceID, int id) {
      ServerComputer computer = super.createComputer(instanceID, id);
      computer.addAPI(new CommandAPI(this));
      return computer;
   }

   @Override
   public boolean isUsable(EntityPlayer player, boolean ignoreRange) {
      MinecraftServer server = MinecraftServer.getServer();
      if (server == null || !server.isCommandBlockEnabled()) {
         player.addChatMessage(new ChatComponentTranslation("advMode.notEnabled", new Object[0]));
         return false;
      } else if (ComputerCraft.isPlayerOpped(player) && player.capabilities.isCreativeMode) {
         return super.isUsable(player, ignoreRange);
      } else {
         player.addChatMessage(new ChatComponentTranslation("advMode.notAllowed", new Object[0]));
         return false;
      }
   }

   public class CommandSender extends CommandBlockLogic {
      private Map<Integer, String> m_outputTable = new HashMap<>();

      public void clearOutput() {
         this.m_outputTable.clear();
      }

      public Map<Integer, String> getOutput() {
         return this.m_outputTable;
      }

      public String func_70005_c_() {
         IComputer computer = TileCommandComputer.this.getComputer();
         if (computer != null) {
            String label = computer.getLabel();
            if (label != null) {
               return computer.getLabel();
            }
         }

         return "@";
      }

      public IChatComponent func_145748_c_() {
         return new ChatComponentText(this.func_70005_c_());
      }

      public void addChatMessage(IChatComponent chatComponent) {
         this.m_outputTable.put(this.m_outputTable.size() + 1, chatComponent.getUnformattedText());
      }

      public boolean canUseCommand(int level, String command) {
         return level <= 2;
      }

      public ChunkCoordinates getPlayerCoordinates() {
         return new ChunkCoordinates(TileCommandComputer.this.xCoord, TileCommandComputer.this.yCoord, TileCommandComputer.this.zCoord);
      }

      public World getEntityWorld() {
         return TileCommandComputer.this.getWorldObj();
      }

      public void updateCommand() {
      }

      public int getCommandBlockType() {
         return 0;
      }

      public void fillInInfo(ByteBuf buf) {
      }
   }
}
