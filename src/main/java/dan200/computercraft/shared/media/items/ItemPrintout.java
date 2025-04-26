package dan200.computercraft.shared.media.items;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import dan200.computercraft.ComputerCraft;
import java.util.List;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;

public class ItemPrintout extends Item {
   public static final int LINES_PER_PAGE = 21;
   public static final int LINE_MAX_LENGTH = 25;
   public static final int MAX_PAGES = 16;
   public static IIcon[] s_icons;

   public ItemPrintout() {
      this.setMaxStackSize(1);
      this.setHasSubtypes(true);
      this.setUnlocalizedName("computercraft:page");
      this.setCreativeTab(ComputerCraft.mainCreativeTab);
   }

   public void getSubItems(Item itemID, CreativeTabs tabs, List list) {
      list.add(createSingleFromTitleAndText(null, new String[21], new String[21]));
      list.add(createMultipleFromTitleAndText(null, new String[42], new String[42]));
      list.add(createBookFromTitleAndText(null, new String[42], new String[42]));
   }

   public IIcon func_77617_a(int damage) {
      return damage >= 0 && damage <= s_icons.length ? s_icons[damage] : s_icons[0];
   }

   public void addInformation(ItemStack itemstack, EntityPlayer par2EntityPlayer, List list, boolean flag) {
      String title = getTitle(itemstack);
      if (title != null && title.length() > 0) {
         list.add(title);
      }
   }

   public String getUnlocalizedName(ItemStack stack) {
      ItemPrintout.Type type = getType(stack);
      switch (type) {
         case Single:
         default:
            return "item.computercraft:page";
         case Multiple:
            return "item.computercraft:pages";
         case Book:
            return "item.computercraft:book";
      }
   }

   public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
      if (!world.isRemote) {
         ComputerCraft.openPrintoutGUI(player);
      }

      return stack;
   }

   private static ItemStack createFromTitleAndText(ItemPrintout.Type type, String title, String[] text, String[] colours) {
      int damage;
      switch (type) {
         case Single:
         default:
            damage = 0;
            break;
         case Multiple:
            damage = 1;
            break;
         case Book:
            damage = 2;
      }

      ItemStack stack = new ItemStack(ComputerCraft.Items.printout, 1, damage);
      NBTTagCompound nbt = new NBTTagCompound();
      if (title != null) {
         nbt.setString("title", title);
      }

      if (text != null) {
         nbt.setInteger("pages", text.length / 21);

         for (int i = 0; i < text.length; i++) {
            if (text[i] != null) {
               nbt.setString("line" + i, text[i]);
            }
         }
      }

      if (colours != null) {
         for (int ix = 0; ix < colours.length; ix++) {
            if (colours[ix] != null) {
               nbt.setString("colour" + ix, colours[ix]);
            }
         }
      }

      stack.setTagCompound(nbt);
      return stack;
   }

   public static ItemStack createSingleFromTitleAndText(String title, String[] text, String[] colours) {
      return createFromTitleAndText(ItemPrintout.Type.Single, title, text, colours);
   }

   public static ItemStack createMultipleFromTitleAndText(String title, String[] text, String[] colours) {
      return createFromTitleAndText(ItemPrintout.Type.Multiple, title, text, colours);
   }

   public static ItemStack createBookFromTitleAndText(String title, String[] text, String[] colours) {
      return createFromTitleAndText(ItemPrintout.Type.Book, title, text, colours);
   }

   public static ItemPrintout.Type getType(ItemStack stack) {
      int damage = stack.getItemDamage();
      switch (damage) {
         case 0:
         default:
            return ItemPrintout.Type.Single;
         case 1:
            return ItemPrintout.Type.Multiple;
         case 2:
            return ItemPrintout.Type.Book;
      }
   }

   public static String getTitle(ItemStack stack) {
      NBTTagCompound nbt = stack.getTagCompound();
      return nbt != null && nbt.hasKey("title") ? nbt.getString("title") : null;
   }

   public static int getPageCount(ItemStack stack) {
      NBTTagCompound nbt = stack.getTagCompound();
      return nbt != null && nbt.hasKey("pages") ? nbt.getInteger("pages") : 1;
   }

   public static String[] getText(ItemStack stack) {
      NBTTagCompound nbt = stack.getTagCompound();
      int numLines = getPageCount(stack) * 21;
      String[] lines = new String[numLines];

      for (int i = 0; i < lines.length; i++) {
         if (nbt != null) {
            lines[i] = nbt.getString("line" + i);
         } else {
            lines[i] = "";
         }
      }

      return lines;
   }

   public static String[] getColours(ItemStack stack) {
      NBTTagCompound nbt = stack.getTagCompound();
      int numLines = getPageCount(stack) * 21;
      String[] lines = new String[numLines];

      for (int i = 0; i < lines.length; i++) {
         if (nbt != null) {
            lines[i] = nbt.getString("colour" + i);
         } else {
            lines[i] = "";
         }
      }

      return lines;
   }

   @SideOnly(Side.CLIENT)
   public void func_94581_a(IIconRegister par1IconRegister) {
      s_icons = new IIcon[3];
      s_icons[0] = par1IconRegister.registerIcon("computercraft:page");
      s_icons[1] = par1IconRegister.registerIcon("computercraft:pageBundle");
      s_icons[2] = par1IconRegister.registerIcon("computercraft:book");
   }

   public static enum Type {
      Single,
      Multiple,
      Book;
   }
}
