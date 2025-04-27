package dan200.computercraft.shared.media.items;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.IIcon;
import net.minecraft.util.WeightedRandomChestContent;
import net.minecraft.world.World;
import net.minecraftforge.common.ChestGenHooks;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import dan200.computercraft.ComputerCraft;
import dan200.computercraft.api.filesystem.IMount;
import dan200.computercraft.api.media.IMedia;
import dan200.computercraft.core.filesystem.SubMount;
import dan200.computercraft.shared.util.Colour;

public class ItemTreasureDisk extends Item implements IMedia {

    private static ItemStack[] s_treasureItems = null;

    public ItemTreasureDisk() {
        this.setMaxStackSize(1);
        this.setHasSubtypes(true);
        this.setUnlocalizedName("computercraft:treasure_disk");
    }

    public void getSubItems(Item itemID, CreativeTabs tabs, List list) {
        if (s_treasureItems != null) {
            Collections.addAll(list, s_treasureItems);
        }
    }

    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean bool) {
        String label = this.getTitle(stack);
        if (label != null && label.length() > 0) {
            list.add(label);
        }
    }

    public int getColorFromItemStack(ItemStack stack, int pass) {
        return pass == 0 ? 16777215 : this.getColour(stack);
    }

    public boolean requiresMultipleRenderPasses() {
        return true;
    }

    public IIcon getIconFromDamageForRenderPass(int damage, int pass) {
        return pass == 0 ? ItemDiskLegacy.s_frameIcon : ItemDiskLegacy.s_colourIcon;
    }

    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister par1IconRegister) {}

    public boolean doesSneakBypassUse(World world, int x, int y, int z, EntityPlayer player) {
        return true;
    }

    @Override
    public String getLabel(ItemStack stack) {
        return this.getTitle(stack);
    }

    @Override
    public boolean setLabel(ItemStack stack, String label) {
        return false;
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
        IMount rootTreasure = getTreasureMount();
        String subPath = this.getSubPath(stack);

        try {
            if (rootTreasure.exists(subPath)) {
                return new SubMount(rootTreasure, subPath);
            } else {
                return rootTreasure.exists("deprecated/" + subPath)
                    ? new SubMount(rootTreasure, "deprecated/" + subPath)
                    : null;
            }
        } catch (IOException var6) {
            return null;
        }
    }

    public static ItemStack create(String subPath, int colourIndex) {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setString("subPath", subPath);
        int slash = subPath.indexOf("/");
        if (slash >= 0) {
            String author = subPath.substring(0, slash);
            String title = subPath.substring(slash + 1);
            nbt.setString("title", "\"" + title + "\" by " + author);
        } else {
            nbt.setString("title", "untitled");
        }

        nbt.setInteger("colour", Colour.values()[colourIndex].getHex());
        ItemStack result = new ItemStack(ComputerCraft.Items.treasureDisk, 1, 0);
        result.setTagCompound(nbt);
        return result;
    }

    public static void registerDungeonLoot() {
        if (s_treasureItems == null) {
            List<String> paths = new ArrayList<>();

            try {
                IMount treasure = getTreasureMount();
                if (treasure != null) {
                    List<String> authors = new ArrayList<>();
                    treasure.list("", authors);

                    for (String author : authors) {
                        if (treasure.isDirectory(author) && !author.equals("deprecated")) {
                            List<String> titles = new ArrayList<>();
                            treasure.list(author, titles);

                            for (String title : titles) {
                                String path = author + "/" + title;
                                if (treasure.isDirectory(path)) {
                                    paths.add(path);
                                }
                            }
                        }
                    }
                }
            } catch (IOException var10) {}

            List<ItemStack> allTreasure = new ArrayList<>();

            for (String path : paths) {
                ItemStack stack = create(path, 4);
                allTreasure.add(stack);
            }

            s_treasureItems = allTreasure.toArray(new ItemStack[allTreasure.size()]);
            int n = 0;
            Random random = new Random();
            WeightedRandomChestContent[] content = new WeightedRandomChestContent[paths.size()
                * ComputerCraft.treasureDiskLootFrequency];
            WeightedRandomChestContent[] commonContent = new WeightedRandomChestContent[paths.size()
                * ComputerCraft.treasureDiskLootFrequency];

            for (String path : paths) {
                for (int i = 0; i < ComputerCraft.treasureDiskLootFrequency; i++) {
                    ItemStack stack = create(path, random.nextInt(16));
                    content[n] = new WeightedRandomChestContent(stack, 1, 1, 1);
                    commonContent[n] = new WeightedRandomChestContent(stack, 1, 1, 2);
                    n++;
                }
            }

            registerLoot("dungeonChest", content);
            registerLoot("mineshaftCorridor", content);
            registerLoot("strongholdCorridor", content);
            registerLoot("strongholdCrossing", content);
            registerLoot("strongholdLibrary", commonContent);
            registerLoot("pyramidDesertyChest", content);
            registerLoot("pyramidJungleChest", content);
        }
    }

    private static void registerLoot(String category, WeightedRandomChestContent[] content) {
        for (int i = 0; i < content.length; i++) {
            ChestGenHooks.getInfo(category)
                .addItem(content[i]);
        }
    }

    private static IMount getTreasureMount() {
        return ComputerCraft.createResourceMount(ComputerCraft.class, "computercraft", "lua/treasure");
    }

    public String getTitle(ItemStack stack) {
        NBTTagCompound nbt = stack.getTagCompound();
        return nbt != null && nbt.hasKey("title") ? nbt.getString("title") : "'alongtimeago' by dan200";
    }

    public String getSubPath(ItemStack stack) {
        NBTTagCompound nbt = stack.getTagCompound();
        return nbt != null && nbt.hasKey("subPath") ? nbt.getString("subPath") : "dan200/alongtimeago";
    }

    public int getColour(ItemStack stack) {
        NBTTagCompound nbt = stack.getTagCompound();
        return nbt != null && nbt.hasKey("colour") ? nbt.getInteger("colour") : Colour.Blue.getHex();
    }
}
