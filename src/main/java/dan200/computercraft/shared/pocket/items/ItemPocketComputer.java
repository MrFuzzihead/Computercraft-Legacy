package dan200.computercraft.shared.pocket.items;

import com.google.common.base.Objects;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import dan200.computercraft.ComputerCraft;
import dan200.computercraft.api.filesystem.IMount;
import dan200.computercraft.api.media.IMedia;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.shared.computer.core.ClientComputer;
import dan200.computercraft.shared.computer.core.ComputerFamily;
import dan200.computercraft.shared.computer.core.ServerComputer;
import dan200.computercraft.shared.computer.items.IComputerItem;
import dan200.computercraft.shared.pocket.apis.PocketAPI;
import dan200.computercraft.shared.pocket.peripherals.PocketModemPeripheral;
import java.util.List;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.IIcon;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

public class ItemPocketComputer extends Item implements IComputerItem, IMedia {
   public static IIcon[] s_icons;

   public ItemPocketComputer() {
      this.setMaxStackSize(1);
      this.setHasSubtypes(true);
      this.setUnlocalizedName("computercraft:pocket_computer");
      this.setCreativeTab(ComputerCraft.mainCreativeTab);
   }

   public ItemStack create(int id, String label, ComputerFamily family, boolean modem) {
      if (family != ComputerFamily.Normal && family != ComputerFamily.Advanced) {
         return null;
      } else {
         int damage = family == ComputerFamily.Advanced ? 1 : 0;
         ItemStack result = new ItemStack(this, 1, damage);
         if (id >= 0 || modem) {
            NBTTagCompound compound = new NBTTagCompound();
            if (id >= 0) {
               compound.setInteger("computerID", id);
            }

            if (modem) {
               compound.setInteger("upgrade", 1);
            }

            result.setTagCompound(compound);
         }

         if (label != null) {
            result.setStackDisplayName(label);
         }

         return result;
      }
   }

   public void getSubItems(Item itemID, CreativeTabs tabs, List list) {
      list.add(PocketComputerItemFactory.create(-1, null, ComputerFamily.Normal, false));
      list.add(PocketComputerItemFactory.create(-1, null, ComputerFamily.Normal, true));
      list.add(PocketComputerItemFactory.create(-1, null, ComputerFamily.Advanced, false));
      list.add(PocketComputerItemFactory.create(-1, null, ComputerFamily.Advanced, true));
   }

   public void func_77663_a(ItemStack stack, World world, Entity entity, int slotNum, boolean selected) {
      if (!world.isRemote) {
         IInventory inventory = entity instanceof EntityPlayer ? ((EntityPlayer)entity).inventory : null;
         ServerComputer computer = this.createServerComputer(world, inventory, stack);
         if (computer != null) {
            computer.keepAlive();
            computer.setWorld(world);
            int id = computer.getID();
            if (id != this.getComputerID(stack)) {
               this.setComputerID(stack, id);
               if (inventory != null) {
                  inventory.markDirty();
               }
            }

            String label = computer.getLabel();
            if (!Objects.equal(label, this.getLabel(stack))) {
               this.setLabel(stack, label);
               if (inventory != null) {
                  inventory.markDirty();
               }
            }

            IPeripheral peripheral = computer.getPeripheral(2);
            if (peripheral != null && peripheral instanceof PocketModemPeripheral) {
               PocketModemPeripheral modem = (PocketModemPeripheral)peripheral;
               if (entity instanceof EntityLivingBase) {
                  EntityLivingBase player = (EntityLivingBase)entity;
                  modem.setLocation(world, player.posX, player.posY + player.getEyeHeight(), player.posZ);
               } else {
                  modem.setLocation(world, entity.posX, entity.posY, entity.posZ);
               }

               boolean modemLight = modem.isActive();
               NBTTagCompound modemNBT = computer.getUserData();
               if (modemNBT.getBoolean("modemLight") != modemLight) {
                  modemNBT.setBoolean("modemLight", modemLight);
                  computer.updateUserData();
               }
            }
         }
      } else {
         ClientComputer computer = this.createClientComputer(stack);
         if (computer != null) {
         }
      }
   }

   public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
      if (!world.isRemote) {
         ServerComputer computer = this.createServerComputer(world, player.inventory, stack);
         if (computer != null) {
            computer.turnOn();
         }

         ComputerCraft.openPocketComputerGUI(player);
      }

      return stack;
   }

   public String getUnlocalizedName(ItemStack stack) {
      switch (this.getFamily(stack)) {
         case Normal:
         default:
            return "item.computercraft:pocket_computer";
         case Advanced:
            return "item.computercraft:advanced_pocket_computer";
      }
   }

   public String getItemStackDisplayName(ItemStack stack) {
      String baseString = this.getUnlocalizedName(stack);
      boolean modem = this.getHasModem(stack);
      return modem
         ? StatCollector.translateToLocalFormatted(baseString + ".upgraded.name", new Object[]{StatCollector.translateToLocal("upgrade.computercraft:wireless_modem.adjective")})
         : StatCollector.translateToLocal(baseString + ".name");
   }

   public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean debug) {
      if (debug) {
         int id = this.getComputerID(stack);
         if (id >= 0) {
            list.add("(Computer ID: " + id + ")");
         }
      }
   }

   public IIcon getIcon(ItemStack stack, int renderPass, EntityPlayer player, ItemStack usingItem, int useRemaining) {
      return this.func_77650_f(stack);
   }

   public IIcon getIcon(ItemStack stack, int pass) {
      return this.func_77650_f(stack);
   }

   public IIcon func_77650_f(ItemStack stack) {
      ComputerFamily family = this.getFamily(stack);
      int anim = this.getAnimation(stack);
      switch (family) {
         case Normal:
         default:
            return s_icons[anim];
         case Advanced:
            return s_icons[5 + anim];
      }
   }

   @SideOnly(Side.CLIENT)
   public void func_94581_a(IIconRegister iconRegister) {
      s_icons = new IIcon[10];
      s_icons[0] = iconRegister.registerIcon("computercraft:pocketComputer");
      s_icons[1] = iconRegister.registerIcon("computercraft:pocketComputerOn");
      s_icons[2] = iconRegister.registerIcon("computercraft:pocketComputerBlink");
      s_icons[3] = iconRegister.registerIcon("computercraft:pocketComputerOnModemOn");
      s_icons[4] = iconRegister.registerIcon("computercraft:pocketComputerBlinkModemOn");
      s_icons[5] = iconRegister.registerIcon("computercraft:pocketComputerAdvanced");
      s_icons[6] = iconRegister.registerIcon("computercraft:pocketComputerOnAdvanced");
      s_icons[7] = iconRegister.registerIcon("computercraft:pocketComputerBlinkAdvanced");
      s_icons[8] = iconRegister.registerIcon("computercraft:pocketComputerOnAdvancedModemOn");
      s_icons[9] = iconRegister.registerIcon("computercraft:pocketComputerBlinkAdvancedModemOn");
   }

   private ServerComputer createServerComputer(World world, IInventory inventory, ItemStack stack) {
      if (world.isRemote) {
         return null;
      } else {
         int instanceID = this.getInstanceID(stack);
         int sessionID = this.getSessionID(stack);
         int correctSessionID = ComputerCraft.serverComputerRegistry.getSessionID();
         ServerComputer computer;
         if (instanceID >= 0 && sessionID == correctSessionID && ComputerCraft.serverComputerRegistry.contains(instanceID)) {
            computer = ComputerCraft.serverComputerRegistry.get(instanceID);
         } else {
            if (instanceID < 0 || sessionID != correctSessionID) {
               instanceID = ComputerCraft.serverComputerRegistry.getUnusedInstanceID();
               this.setInstanceID(stack, instanceID);
               this.setSessionID(stack, correctSessionID);
            }

            int computerID = this.getComputerID(stack);
            if (computerID < 0) {
               computerID = ComputerCraft.createUniqueNumberedSaveDir(world, "computer");
               this.setComputerID(stack, computerID);
            }

            computer = new ServerComputer(world, computerID, this.getLabel(stack), instanceID, this.getFamily(stack), 26, 20);
            computer.addAPI(new PocketAPI());
            if (this.getHasModem(stack)) {
               computer.setPeripheral(2, new PocketModemPeripheral());
            }

            ComputerCraft.serverComputerRegistry.add(instanceID, computer);
            if (inventory != null) {
               inventory.markDirty();
            }
         }

         computer.setWorld(world);
         return computer;
      }
   }

   public ClientComputer createClientComputer(ItemStack stack) {
      int instanceID = this.getInstanceID(stack);
      if (instanceID >= 0) {
         if (!ComputerCraft.clientComputerRegistry.contains(instanceID)) {
            ComputerCraft.clientComputerRegistry.add(instanceID, new ClientComputer(instanceID));
         }

         return ComputerCraft.clientComputerRegistry.get(instanceID);
      } else {
         return null;
      }
   }

   private ClientComputer getClientComputer(ItemStack stack) {
      int instanceID = this.getInstanceID(stack);
      return instanceID >= 0 ? ComputerCraft.clientComputerRegistry.get(instanceID) : null;
   }

   @Override
   public int getComputerID(ItemStack stack) {
      NBTTagCompound compound = stack.getTagCompound();
      return compound != null && compound.hasKey("computerID") ? compound.getInteger("computerID") : -1;
   }

   private void setComputerID(ItemStack stack, int computerID) {
      if (!stack.hasTagCompound()) {
         stack.setTagCompound(new NBTTagCompound());
      }

      stack.getTagCompound().setInteger("computerID", computerID);
   }

   @Override
   public String getLabel(ItemStack stack) {
      return stack.hasDisplayName() ? stack.getDisplayName() : null;
   }

   @Override
   public ComputerFamily getFamily(ItemStack stack) {
      int damage = stack.getItemDamage();
      switch (damage) {
         case 0:
         default:
            return ComputerFamily.Normal;
         case 1:
            return ComputerFamily.Advanced;
      }
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
      ServerComputer computer = this.createServerComputer(world, null, stack);
      return computer != null ? computer.getRootMount() : null;
   }

   private int getInstanceID(ItemStack stack) {
      NBTTagCompound compound = stack.getTagCompound();
      return compound != null && compound.hasKey("instanceID") ? compound.getInteger("instanceID") : -1;
   }

   private void setInstanceID(ItemStack stack, int instanceID) {
      if (!stack.hasTagCompound()) {
         stack.setTagCompound(new NBTTagCompound());
      }

      stack.getTagCompound().setInteger("instanceID", instanceID);
   }

   private int getSessionID(ItemStack stack) {
      NBTTagCompound compound = stack.getTagCompound();
      return compound != null && compound.hasKey("sessionID") ? compound.getInteger("sessionID") : -1;
   }

   private void setSessionID(ItemStack stack, int sessionID) {
      if (!stack.hasTagCompound()) {
         stack.setTagCompound(new NBTTagCompound());
      }

      stack.getTagCompound().setInteger("sessionID", sessionID);
   }

   private int getAnimation(ItemStack stack) {
      ClientComputer computer = this.getClientComputer(stack);
      if (computer != null && computer.isOn()) {
         int animation = computer.isCursorDisplayed() ? 2 : 1;
         NBTTagCompound computerNBT = computer.getUserData();
         if (computerNBT != null && computerNBT.getBoolean("modemLight")) {
            animation += 2;
         }

         return animation;
      } else {
         return 0;
      }
   }

   public boolean getHasModem(ItemStack stack) {
      NBTTagCompound compound = stack.getTagCompound();
      return compound != null && compound.hasKey("upgrade") ? compound.getInteger("upgrade") == 1 : false;
   }
}
