package dan200.computercraft.shared.proxy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.oredict.RecipeSorter;
import net.minecraftforge.oredict.RecipeSorter.Category;

import cpw.mods.fml.common.ObfuscationReflectionHelper;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.registry.GameRegistry;
import dan200.computercraft.ComputerCraft;
import dan200.computercraft.api.turtle.ITurtleUpgrade;
import dan200.computercraft.api.turtle.TurtleUpgradeType;
import dan200.computercraft.shared.computer.core.ComputerFamily;
import dan200.computercraft.shared.computer.items.ComputerItemFactory;
import dan200.computercraft.shared.turtle.blocks.BlockTurtle;
import dan200.computercraft.shared.turtle.blocks.TileTurtle;
import dan200.computercraft.shared.turtle.blocks.TileTurtleAdvanced;
import dan200.computercraft.shared.turtle.blocks.TileTurtleExpanded;
import dan200.computercraft.shared.turtle.items.ItemTurtleAdvanced;
import dan200.computercraft.shared.turtle.items.ItemTurtleLegacy;
import dan200.computercraft.shared.turtle.items.ItemTurtleNormal;
import dan200.computercraft.shared.turtle.items.TurtleItemFactory;
import dan200.computercraft.shared.turtle.recipes.TurtleRecipe;
import dan200.computercraft.shared.turtle.recipes.TurtleUpgradeRecipe;
import dan200.computercraft.shared.turtle.upgrades.TurtleAxe;
import dan200.computercraft.shared.turtle.upgrades.TurtleCraftingTable;
import dan200.computercraft.shared.turtle.upgrades.TurtleHoe;
import dan200.computercraft.shared.turtle.upgrades.TurtleModem;
import dan200.computercraft.shared.turtle.upgrades.TurtleShovel;
import dan200.computercraft.shared.turtle.upgrades.TurtleSword;
import dan200.computercraft.shared.turtle.upgrades.TurtleTool;
import dan200.computercraft.shared.util.IEntityDropConsumer;
import dan200.computercraft.shared.util.ImpostorRecipe;
import dan200.computercraft.shared.util.InventoryUtil;

public abstract class CCTurtleProxyCommon implements ICCTurtleProxy {

    private Map<Integer, ITurtleUpgrade> m_turtleUpgrades = new HashMap<>();
    private Map<Entity, IEntityDropConsumer> m_dropConsumers = new WeakHashMap<>();

    @Override
    public void preInit() {
        this.registerItems();
    }

    @Override
    public void init() {
        this.registerForgeHandlers();
        this.registerTileEntities();
    }

    @Override
    public void registerTurtleUpgrade(ITurtleUpgrade upgrade) {
        int id = upgrade.getUpgradeID();
        if (id >= 0 && id < 64) {
            throw new RuntimeException(
                "Error registering '" + upgrade.getUnlocalisedAdjective()
                    + " Turtle'. UpgradeID '"
                    + id
                    + "' is reserved by ComputerCraft");
        } else {
            this.registerTurtleUpgradeInternal(upgrade);
        }
    }

    @Override
    public ITurtleUpgrade getTurtleUpgrade(int id) {
        return this.m_turtleUpgrades.get(id);
    }

    @Override
    public ITurtleUpgrade getTurtleUpgrade(ItemStack stack) {
        for (ITurtleUpgrade upgrade : this.m_turtleUpgrades.values()) {
            try {
                ItemStack upgradeStack = upgrade.getCraftingItem();
                if (InventoryUtil.areItemsStackable(upgradeStack, stack)) {
                    return upgrade;
                }
            } catch (Exception var5) {}
        }

        return null;
    }

    public static boolean isUpgradeSuitableForFamily(ComputerFamily family, ITurtleUpgrade upgrade) {
        return family == ComputerFamily.Beginners ? upgrade.getType() == TurtleUpgradeType.Tool : true;
    }

    private void addAllUpgradedTurtles(ComputerFamily family, List<ItemStack> list) {
        ItemStack basicStack = TurtleItemFactory.create(-1, null, null, family, null, null, 0, null, null);
        if (basicStack != null) {
            list.add(basicStack);
        }

        for (ITurtleUpgrade leftUpgrade : this.m_turtleUpgrades.values()) {
            if (leftUpgrade.getUpgradeID() < 64 && isUpgradeSuitableForFamily(family, leftUpgrade)) {
                ItemStack stack = TurtleItemFactory.create(-1, null, null, family, leftUpgrade, null, 0, null, null);
                if (stack != null) {
                    list.add(stack);
                }
            }
        }
    }

    @Override
    public void addAllUpgradedTurtles(List<ItemStack> list) {
        this.addAllUpgradedTurtles(ComputerFamily.Normal, list);
        this.addAllUpgradedTurtles(ComputerFamily.Advanced, list);
        this.addAllUpgradedTurtles(ComputerFamily.Beginners, list);
    }

    @Override
    public void setEntityDropConsumer(Entity entity, IEntityDropConsumer consumer) {
        if (!this.m_dropConsumers.containsKey(entity)) {
            boolean captured = (Boolean) ObfuscationReflectionHelper
                .getPrivateValue(Entity.class, entity, new String[] { "captureDrops" });
            if (!captured) {
                ObfuscationReflectionHelper
                    .setPrivateValue(Entity.class, entity, new Boolean(true), new String[] { "captureDrops" });
                ArrayList<EntityItem> items = (ArrayList<EntityItem>) ObfuscationReflectionHelper
                    .getPrivateValue(Entity.class, entity, new String[] { "capturedDrops" });
                if (items == null || items.size() == 0) {
                    this.m_dropConsumers.put(entity, consumer);
                }
            }
        }
    }

    @Override
    public void clearEntityDropConsumer(Entity entity) {
        if (this.m_dropConsumers.containsKey(entity)) {
            boolean captured = (Boolean) ObfuscationReflectionHelper
                .getPrivateValue(Entity.class, entity, new String[] { "captureDrops" });
            if (captured) {
                ObfuscationReflectionHelper
                    .setPrivateValue(Entity.class, entity, new Boolean(false), new String[] { "captureDrops" });
                ArrayList<EntityItem> items = (ArrayList<EntityItem>) ObfuscationReflectionHelper
                    .getPrivateValue(Entity.class, entity, new String[] { "capturedDrops" });
                if (items != null) {
                    this.dispatchEntityDrops(entity, items);
                    items.clear();
                }
            }

            this.m_dropConsumers.remove(entity);
        }
    }

    private void registerTurtleUpgradeInternal(ITurtleUpgrade upgrade) {
        int id = upgrade.getUpgradeID();
        if (id >= 0 && id < 32767) {
            ITurtleUpgrade existing = this.m_turtleUpgrades.get(id);
            if (existing != null) {
                throw new RuntimeException(
                    "Error registering '" + upgrade.getUnlocalisedAdjective()
                        + " Turtle'. UpgradeID '"
                        + id
                        + "' is already registered by '"
                        + existing.getUnlocalisedAdjective()
                        + " Turtle'");
            } else {
                this.m_turtleUpgrades.put(id, upgrade);
                if (upgrade.getUpgradeID() < 64) {
                    List recipeList = CraftingManager.getInstance()
                        .getRecipeList();
                    ItemStack craftingItem = upgrade.getCraftingItem();

                    for (ComputerFamily family : ComputerFamily.values()) {
                        if (isUpgradeSuitableForFamily(family, upgrade)) {
                            ItemStack baseTurtle = TurtleItemFactory
                                .create(-1, null, null, family, null, null, 0, null, null);
                            if (baseTurtle != null) {
                                ItemStack craftedTurtle = TurtleItemFactory
                                    .create(-1, null, null, family, upgrade, null, 0, null, null);
                                ItemStack craftedTurtleFlipped = TurtleItemFactory
                                    .create(-1, null, null, family, null, upgrade, 0, null, null);
                                recipeList.add(
                                    new ImpostorRecipe(
                                        2,
                                        1,
                                        new ItemStack[] { baseTurtle, craftingItem },
                                        craftedTurtle));
                                recipeList.add(
                                    new ImpostorRecipe(
                                        2,
                                        1,
                                        new ItemStack[] { craftingItem, baseTurtle },
                                        craftedTurtleFlipped));

                                for (ITurtleUpgrade otherUpgrade : this.m_turtleUpgrades.values()) {
                                    if (otherUpgrade.getUpgradeID() < 64
                                        && isUpgradeSuitableForFamily(family, otherUpgrade)) {
                                        ItemStack otherCraftingItem = otherUpgrade.getCraftingItem();
                                        ItemStack otherCraftedTurtle = TurtleItemFactory
                                            .create(-1, null, null, family, null, otherUpgrade, 0, null, null);
                                        ItemStack comboCraftedTurtle = TurtleItemFactory
                                            .create(-1, null, null, family, upgrade, otherUpgrade, 0, null, null);
                                        ItemStack otherCraftedTurtleFlipped = TurtleItemFactory
                                            .create(-1, null, null, family, otherUpgrade, null, 0, null, null);
                                        ItemStack comboCraftedTurtleFlipped = TurtleItemFactory
                                            .create(-1, null, null, family, otherUpgrade, upgrade, 0, null, null);
                                        recipeList.add(
                                            new ImpostorRecipe(
                                                2,
                                                1,
                                                new ItemStack[] { otherCraftingItem, craftedTurtle },
                                                comboCraftedTurtle));
                                        recipeList.add(
                                            new ImpostorRecipe(
                                                2,
                                                1,
                                                new ItemStack[] { otherCraftedTurtle, craftingItem },
                                                comboCraftedTurtle));
                                        recipeList.add(
                                            new ImpostorRecipe(
                                                2,
                                                1,
                                                new ItemStack[] { craftedTurtleFlipped, otherCraftingItem },
                                                comboCraftedTurtleFlipped));
                                        recipeList.add(
                                            new ImpostorRecipe(
                                                2,
                                                1,
                                                new ItemStack[] { craftingItem, otherCraftedTurtleFlipped },
                                                comboCraftedTurtleFlipped));
                                        recipeList.add(
                                            new ImpostorRecipe(
                                                3,
                                                1,
                                                new ItemStack[] { otherCraftingItem, baseTurtle, craftingItem },
                                                comboCraftedTurtle));
                                        recipeList.add(
                                            new ImpostorRecipe(
                                                3,
                                                1,
                                                new ItemStack[] { craftingItem, baseTurtle, otherCraftingItem },
                                                comboCraftedTurtleFlipped));
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            throw new RuntimeException(
                "Error registering '" + upgrade.getUnlocalisedAdjective()
                    + " Turtle'. UpgradeID '"
                    + id
                    + "' is out of range");
        }
    }

    private void registerItems() {
        ComputerCraft.Blocks.turtle = BlockTurtle.createTurtleBlock();
        GameRegistry.registerBlock(ComputerCraft.Blocks.turtle, ItemTurtleLegacy.class, "CC-Turtle");
        ComputerCraft.Blocks.turtleExpanded = BlockTurtle.createTurtleBlock();
        GameRegistry.registerBlock(ComputerCraft.Blocks.turtleExpanded, ItemTurtleNormal.class, "CC-TurtleExpanded");
        ComputerCraft.Blocks.turtleAdvanced = BlockTurtle.createTurtleBlock();
        GameRegistry.registerBlock(ComputerCraft.Blocks.turtleAdvanced, ItemTurtleAdvanced.class, "CC-TurtleAdvanced");
        RecipeSorter.register("computercraft:turtle", TurtleRecipe.class, Category.SHAPED, "after:minecraft:shapeless");
        RecipeSorter.register(
            "computercraft:turtle_upgrade",
            TurtleUpgradeRecipe.class,
            Category.SHAPED,
            "after:minecraft:shapeless");
        GameRegistry.addRecipe(
            new TurtleRecipe(
                new Item[] { Items.iron_ingot, Items.iron_ingot, Items.iron_ingot, Items.iron_ingot,
                    Item.getItemFromBlock(ComputerCraft.Blocks.computer), Items.iron_ingot, Items.iron_ingot,
                    Item.getItemFromBlock(Blocks.chest), Items.iron_ingot },
                ComputerFamily.Normal));
        GameRegistry.addRecipe(new TurtleUpgradeRecipe());
        ItemStack iron = new ItemStack(Items.iron_ingot, 1);
        GameRegistry.addRecipe(
            new ImpostorRecipe(
                3,
                3,
                new ItemStack[] { iron, iron, iron, iron, ComputerItemFactory.create(-1, null, ComputerFamily.Normal),
                    iron, iron, new ItemStack(Blocks.chest, 1), iron },
                TurtleItemFactory.create(-1, null, null, ComputerFamily.Normal, null, null, 0, null, null)));
        GameRegistry.addRecipe(
            new TurtleRecipe(
                new Item[] { Items.gold_ingot, Items.gold_ingot, Items.gold_ingot, Items.gold_ingot,
                    Item.getItemFromBlock(ComputerCraft.Blocks.computer), Items.gold_ingot, Items.gold_ingot,
                    Item.getItemFromBlock(Blocks.chest), Items.gold_ingot },
                ComputerFamily.Advanced));
        ItemStack gold = new ItemStack(Items.gold_ingot, 1);
        GameRegistry.addRecipe(
            new ImpostorRecipe(
                3,
                3,
                new ItemStack[] { gold, gold, gold, gold, ComputerItemFactory.create(-1, null, ComputerFamily.Advanced),
                    gold, gold, new ItemStack(Blocks.chest, 1), gold },
                TurtleItemFactory.create(-1, null, null, ComputerFamily.Advanced, null, null, 0, null, null)));
        ComputerCraft.Upgrades.modem = new TurtleModem(1);
        this.registerTurtleUpgradeInternal(ComputerCraft.Upgrades.modem);
        ComputerCraft.Upgrades.craftingTable = new TurtleCraftingTable(2);
        this.registerTurtleUpgradeInternal(ComputerCraft.Upgrades.craftingTable);
        ComputerCraft.Upgrades.diamondSword = new TurtleSword(
            3,
            "upgrade.minecraft:diamond_sword.adjective",
            Items.diamond_sword);
        this.registerTurtleUpgradeInternal(ComputerCraft.Upgrades.diamondSword);
        ComputerCraft.Upgrades.diamondShovel = new TurtleShovel(
            4,
            "upgrade.minecraft:diamond_shovel.adjective",
            Items.diamond_shovel);
        this.registerTurtleUpgradeInternal(ComputerCraft.Upgrades.diamondShovel);
        ComputerCraft.Upgrades.diamondPickaxe = new TurtleTool(
            5,
            "upgrade.minecraft:diamond_pickaxe.adjective",
            Items.diamond_pickaxe);
        this.registerTurtleUpgradeInternal(ComputerCraft.Upgrades.diamondPickaxe);
        ComputerCraft.Upgrades.diamondAxe = new TurtleAxe(
            6,
            "upgrade.minecraft:diamond_axe.adjective",
            Items.diamond_axe);
        this.registerTurtleUpgradeInternal(ComputerCraft.Upgrades.diamondAxe);
        ComputerCraft.Upgrades.diamondHoe = new TurtleHoe(
            7,
            "upgrade.minecraft:diamond_hoe.adjective",
            Items.diamond_hoe);
        this.registerTurtleUpgradeInternal(ComputerCraft.Upgrades.diamondHoe);
    }

    private void registerTileEntities() {
        GameRegistry.registerTileEntity(TileTurtle.class, "turtle");
        GameRegistry.registerTileEntity(TileTurtleExpanded.class, "turtleex");
        GameRegistry.registerTileEntity(TileTurtleAdvanced.class, "turtleadv");
    }

    private void registerForgeHandlers() {
        CCTurtleProxyCommon.ForgeHandlers handlers = new CCTurtleProxyCommon.ForgeHandlers();
        MinecraftForge.EVENT_BUS.register(handlers);
    }

    private void dispatchEntityDrops(Entity entity, ArrayList<EntityItem> drops) {
        IEntityDropConsumer consumer = this.m_dropConsumers.get(entity);
        if (consumer != null) {
            for (EntityItem entityItem : drops) {
                consumer.consumeDrop(entity, entityItem.getEntityItem());
            }

            drops.clear();
        }
    }

    public class ForgeHandlers {

        private ForgeHandlers() {}

        @SubscribeEvent
        public void onEntityLivingDrops(LivingDropsEvent event) {
            CCTurtleProxyCommon.this.dispatchEntityDrops(event.entity, event.drops);
        }
    }
}
