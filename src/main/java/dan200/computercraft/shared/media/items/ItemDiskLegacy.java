package dan200.computercraft.shared.media.items;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import dan200.computercraft.ComputerCraft;
import dan200.computercraft.api.ComputerCraftAPI;
import dan200.computercraft.api.filesystem.IMount;
import dan200.computercraft.api.media.IMedia;
import dan200.computercraft.shared.util.Colour;
import java.util.List;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;

public class ItemDiskLegacy extends Item implements IMedia {
   public static IIcon s_frameIcon;
   public static IIcon s_colourIcon;

   public ItemDiskLegacy() {
      this.setMaxStackSize(1);
      this.setHasSubtypes(true);
      this.setUnlocalizedName("computercraft:disk");
      this.setCreativeTab(ComputerCraft.mainCreativeTab);
   }

   public void getSubItems(Item itemID, CreativeTabs tabs, List list) {
      for (int colour = 0; colour < 16; colour++) {
         ItemStack stack = createFromIDAndColour(-1, null, Colour.values()[colour].getHex());
         if (stack.getItem() == this) {
            list.add(stack);
         }
      }
   }

   public static ItemStack createFromIDAndColour(int id, String label, int colour) {
      if (colour != Colour.Blue.getHex()) {
         return ItemDiskExpanded.createFromIDAndColour(id, label, colour);
      } else {
         ItemStack stack = new ItemStack(ComputerCraft.Items.disk, 1);
         ComputerCraft.Items.disk.setDiskID(stack, id);
         ComputerCraft.Items.disk.setLabel(stack, label);
         return stack;
      }
   }

   public int getDiskID(ItemStack stack) {
      int damage = stack.getItemDamage();
      return damage > 0 ? damage : -1;
   }

   protected void setDiskID(ItemStack stack, int id) {
      if (id > 0) {
         stack.setItemDamage(id);
      } else {
         stack.setItemDamage(0);
      }
   }

   public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean debug) {
      if (debug) {
         int id = this.getDiskID(stack);
         if (id >= 0) {
            list.add("(Disk ID: " + id + ")");
         }
      }
   }

   @Override
   public String getLabel(ItemStack stack) {
      return stack.hasDisplayName() ? stack.getDisplayName() : null;
   }

   @Override
   public boolean setLabel(ItemStack stack, String label) {
      if (label != null) {
         stack.setStackDisplayName(label);
      } else {
         stack.func_135074_t();
      }

      return true;
   }

   @Override
   public String getAudioTitle(ItemStack stack) {
      return null;
   }

   @Override
   public String getAudioRecordName(ItemStack stack) {
      return null;
   }

   @Override
   public IMount createDataMount(ItemStack stack, World world) {
      int diskID = this.getDiskID(stack);
      if (diskID < 0) {
         diskID = ComputerCraft.createUniqueNumberedSaveDir(world, "computer/disk");
         this.setDiskID(stack, diskID);
      }

      return ComputerCraftAPI.createSaveDirMount(world, "computer/disk/" + diskID, ComputerCraft.floppySpaceLimit);
   }

   public int func_82790_a(ItemStack stack, int layer) {
      return layer == 0 ? 16777215 : this.getColor(stack);
   }

   public boolean func_77623_v() {
      return true;
   }

   public IIcon func_77618_c(int layer, int par2) {
      return par2 == 0 ? s_frameIcon : s_colourIcon;
   }

   public int getColor(ItemStack stack) {
      return Colour.Blue.getHex();
   }

   public boolean doesSneakBypassUse(World world, int x, int y, int z, EntityPlayer player) {
      return true;
   }

   @SideOnly(Side.CLIENT)
   public void func_94581_a(IIconRegister par1IconRegister) {
      s_frameIcon = par1IconRegister.registerIcon("computercraft:diskFrame");
      s_colourIcon = par1IconRegister.registerIcon("computercraft:diskColour");
   }
}
