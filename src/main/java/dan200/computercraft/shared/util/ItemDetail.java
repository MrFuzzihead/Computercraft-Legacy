package dan200.computercraft.shared.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.StatCollector;

/**
 * Builds the item detail tables returned by {@code turtle.getItemDetail} and
 * {@code inventory.getItemDetail}, following CC:Tweaked's {@code ItemDetails}.
 *
 * <p>
 * Two shapes are produced: a basic one (used by {@code inventory.list}), and a full one. Several
 * fields are approximations on 1.7.10, which are called out on each helper.
 * </p>
 */
public final class ItemDetail {

    private ItemDetail() {}

    /** The id a program sees for an item, e.g. {@code minecraft:stone}. */
    public static String nameOf(ItemStack stack) {
        String name = Item.itemRegistry.getNameForObject(stack.getItem());
        return name != null ? name : "minecraft:air";
    }

    /**
     * The compact form used by {@code inventory.list}.
     *
     * <p>
     * CC:Tweaked's basic detail is {@code name}, {@code count} and the {@code nbt} hash. This adds
     * {@code damage}, which 1.7.10 uses as the item subtype and which programs here have always
     * seen.
     * </p>
     */
    public static Map<Object, Object> basic(ItemStack stack) {
        Map<Object, Object> detail = new HashMap<Object, Object>();
        detail.put("name", nameOf(stack));
        detail.put("count", stack.stackSize);
        detail.put("damage", stack.getItemDamage());

        String hash = NBTHash.of(stack.getTagCompound());
        if (hash != null) detail.put("nbt", hash);

        return detail;
    }

    /**
     * The full form used by {@code getItemDetail}.
     *
     * <p>
     * Approximations versus 1.20, all inherited from the platform:
     * </p>
     * <ul>
     * <li>{@code lore} entries are plain strings; 1.7.10 has no JSON text components.</li>
     * <li>{@code potionEffects[].name} is the effect's unlocalised name minus the {@code potion.}
     * prefix, since 1.7.10 has no effect registry id.</li>
     * <li>{@code durability} is reconstructed from the damage ratio, because 1.7.10 has no
     * {@code getBarWidth}.</li>
     * <li>{@code mapColor} is only present for block items.</li>
     * </ul>
     */
    public static Map<Object, Object> full(ItemStack stack) {
        Map<Object, Object> detail = basic(stack);
        detail.put("displayName", stack.getDisplayName());
        detail.put("maxCount", stack.getMaxStackSize());

        Item item = stack.getItem();
        int maxDamage = stack.getMaxDamage();
        int damage = stack.getItemDamage();
        if (maxDamage > 0 && damage >= 0) {
            detail.put("damage", damage);
            detail.put("maxDamage", maxDamage);

            // CC:Tweaked: getBarWidth() / 13, where the bar is 13 pixels wide.
            int width = Math.round(13.0F - damage * 13.0F / (float) maxDamage);
            if (width < 0) width = 0;
            if (width > 13) width = 13;
            detail.put("durability", width / 13.0);
        }

        addLore(detail, stack);
        addEnchantments(detail, stack);
        addPotionEffects(detail, stack);

        NBTTagCompound tag = stack.getTagCompound();
        if (tag != null && tag.getBoolean("Unbreakable")) {
            detail.put("unbreakable", true);
        }

        if (item instanceof ItemBlock) {
            Block block = ((ItemBlock) item).field_150939_a;
            if (block != null) {
                detail.put("mapColor", block.getMapColor(stack.getItemDamage()).colorValue & 0xFFFFFF);
            }
        }

        // Deprecated upstream, but still always present for shape compatibility.
        detail.put("itemGroups", new HashMap<Object, Object>());

        return detail;
    }

    /** {@code display.Lore}, as plain strings. */
    private static void addLore(Map<Object, Object> detail, ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.hasKey("display")) return;

        NBTTagCompound display = tag.getCompoundTag("display");
        if (!display.hasKey("Lore")) return;

        NBTTagList lore = display.getTagList("Lore", 8);
        if (lore.tagCount() == 0) return;

        List<Object> lines = new ArrayList<Object>(lore.tagCount());
        for (int i = 0; i < lore.tagCount(); i++) {
            String line = lore.getStringTagAt(i);
            // 1.7.10 lore is plain text, so this is the whole string rather than a parsed
            // component. Anything that looks like a JSON component is left as-is.
            lines.add(line);
        }
        detail.put("lore", lines);
    }

    /** Visible enchantments, matching the shape the turtle already used. */
    private static void addEnchantments(Map<Object, Object> detail, ItemStack stack) {
        NBTTagList enchants = stack.getEnchantmentTagList();
        if (enchants == null || enchants.tagCount() == 0) return;

        Map<Object, Object> out = new HashMap<Object, Object>();
        int n = 1;
        for (int i = 0; i < enchants.tagCount(); i++) {
            NBTTagCompound entry = enchants.getCompoundTagAt(i);
            int id = entry.getShort("id");
            int level = entry.getShort("lvl");
            net.minecraft.enchantment.Enchantment enchantment = id >= 0
                && id < net.minecraft.enchantment.Enchantment.enchantmentsList.length
                    ? net.minecraft.enchantment.Enchantment.enchantmentsList[id]
                    : null;
            if (enchantment == null) continue;

            Map<Object, Object> one = new HashMap<Object, Object>();
            one.put("name", enchantment.getName());
            one.put("level", level);
            one.put("displayName", StatCollector.translateToLocal(enchantment.getName()));
            out.put(n++, one);
        }

        if (!out.isEmpty()) detail.put("enchantments", out);
    }

    /** Potion effects, for potions and any item carrying {@code customPotionEffects}. */
    private static void addPotionEffects(Map<Object, Object> detail, ItemStack stack) {
        List<PotionEffect> effects = null;
        if (stack.getItem() instanceof ItemPotion) {
            // getEffects is an instance method in 1.7.10, unlike later versions' static helper.
            effects = ((ItemPotion) stack.getItem()).getEffects(stack);
        }

        if (effects == null || effects.isEmpty()) {
            // Splash/lingering variants aside, some mods store effects in NBT directly.
            NBTTagCompound tag = stack.getTagCompound();
            if (tag != null && tag.hasKey("CustomPotionEffects")) {
                NBTTagList list = tag.getTagList("CustomPotionEffects", 10);
                effects = new ArrayList<PotionEffect>(list.tagCount());
                for (int i = 0; i < list.tagCount(); i++) {
                    effects.add(PotionEffect.readCustomPotionEffectFromNBT(list.getCompoundTagAt(i)));
                }
            }
        }

        if (effects == null || effects.isEmpty()) return;

        List<Object> out = new ArrayList<Object>(effects.size());
        for (PotionEffect effect : effects) {
            Potion potion = Potion.potionTypes[effect.getPotionID()];
            String id = potion != null ? potion.getName() : "potion." + effect.getPotionID();
            // 1.7.10 has no effect registry, so the unlocalised name stands in for the id.
            if (id.startsWith("potion.")) id = id.substring("potion.".length());

            Map<Object, Object> one = new HashMap<Object, Object>();
            one.put("name", id);
            one.put("displayName", StatCollector.translateToLocal(effect.getEffectName()));
            // CC:Tweaked reports the displayed potency, i.e. amplifier + 1.
            if (effect.getAmplifier() > 0) one.put("potency", effect.getAmplifier() + 1);
            // Ticks to seconds, as upstream does.
            if (effect.getDuration() > 1) one.put("duration", effect.getDuration() / 20.0);
            out.add(one);
        }
        detail.put("potionEffects", out);
    }
}
