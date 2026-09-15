package ChestLoot121;

import com.seedfinding.mccore.util.data.Pair;
import com.seedfinding.mcfeature.loot.LootContext;
import com.seedfinding.mcfeature.loot.function.LootFunction;
import com.seedfinding.mcfeature.loot.item.Item;
import com.seedfinding.mcfeature.loot.item.ItemStack;
import com.seedfinding.mcfeature.loot.item.Items;

import java.util.HashMap;
import java.util.List;

public class EnchantRandomly21 implements LootFunction {
    public static final HashMap<Item, ItemType> ITEM_TO_TYPE = new HashMap<>();
    public static final HashMap<ItemType, List<String>> ENCHANTMENTS = new HashMap<>();
    public static final HashMap<String, Integer> MAX_LEVELS = new HashMap<>();

    public final ItemType type;

    public EnchantRandomly21(Item item) {
        type = ITEM_TO_TYPE.getOrDefault(item, null);
        if (type == null) {
            throw new IllegalStateException("1.21 random enchant for " + item.getName() + " is not supported (yet)");
        }
    }

    @Override
    public ItemStack process(ItemStack baseStack, LootContext context) {
        List<String> possibleEnchants = ENCHANTMENTS.get(type);
        int eid = context.nextInt(possibleEnchants.size());
        String enchant = possibleEnchants.get(eid);
        int maxLevel = MAX_LEVELS.get(enchant);
        int level = maxLevel > 1 ? context.nextInt(maxLevel) + 1 : 1;
        Item newItem = new Item(baseStack.getItem().getName());
        newItem.getEnchantments().add(new Pair<>(enchant, level));
        return new ItemStack(newItem, baseStack.getCount());
    }

    public enum ItemType {
        HELMET,
        CHESTPLATE,
        LEGGINGS,
        BOOTS,
        SWORD,
        AXE,
        PICKAXE,
        SHOVEL,
        HOE,
        FISHING_ROD,
        CROSSBOW,
        ENCHANTED_BOOK
    }

    static {
        ITEM_TO_TYPE.put(Items.NETHERITE_HELMET, ItemType.HELMET);
        ITEM_TO_TYPE.put(Items.DIAMOND_HELMET, ItemType.HELMET);
        ITEM_TO_TYPE.put(Items.GOLDEN_HELMET, ItemType.HELMET);
        ITEM_TO_TYPE.put(Items.IRON_HELMET, ItemType.HELMET);
        ITEM_TO_TYPE.put(Items.CHAINMAIL_HELMET, ItemType.HELMET);
        ITEM_TO_TYPE.put(Items.LEATHER_HELMET, ItemType.HELMET);

        ITEM_TO_TYPE.put(Items.NETHERITE_CHESTPLATE, ItemType.CHESTPLATE);
        ITEM_TO_TYPE.put(Items.DIAMOND_CHESTPLATE, ItemType.CHESTPLATE);
        ITEM_TO_TYPE.put(Items.GOLDEN_CHESTPLATE, ItemType.CHESTPLATE);
        ITEM_TO_TYPE.put(Items.IRON_CHESTPLATE, ItemType.CHESTPLATE);
        ITEM_TO_TYPE.put(Items.CHAINMAIL_CHESTPLATE, ItemType.CHESTPLATE);
        ITEM_TO_TYPE.put(Items.LEATHER_CHESTPLATE, ItemType.CHESTPLATE);

        ITEM_TO_TYPE.put(Items.NETHERITE_LEGGINGS, ItemType.LEGGINGS);
        ITEM_TO_TYPE.put(Items.DIAMOND_LEGGINGS, ItemType.LEGGINGS);
        ITEM_TO_TYPE.put(Items.GOLDEN_LEGGINGS, ItemType.LEGGINGS);
        ITEM_TO_TYPE.put(Items.IRON_LEGGINGS, ItemType.LEGGINGS);
        ITEM_TO_TYPE.put(Items.CHAINMAIL_LEGGINGS, ItemType.LEGGINGS);
        ITEM_TO_TYPE.put(Items.LEATHER_LEGGINGS, ItemType.LEGGINGS);

        ITEM_TO_TYPE.put(Items.NETHERITE_BOOTS, ItemType.BOOTS);
        ITEM_TO_TYPE.put(Items.DIAMOND_BOOTS, ItemType.BOOTS);
        ITEM_TO_TYPE.put(Items.GOLDEN_BOOTS, ItemType.BOOTS);
        ITEM_TO_TYPE.put(Items.IRON_BOOTS, ItemType.BOOTS);
        ITEM_TO_TYPE.put(Items.CHAINMAIL_BOOTS, ItemType.BOOTS);
        ITEM_TO_TYPE.put(Items.LEATHER_BOOTS, ItemType.BOOTS);

        ITEM_TO_TYPE.put(Items.NETHERITE_SWORD, ItemType.SWORD);
        ITEM_TO_TYPE.put(Items.DIAMOND_SWORD, ItemType.SWORD);
        ITEM_TO_TYPE.put(Items.GOLDEN_SWORD, ItemType.SWORD);
        ITEM_TO_TYPE.put(Items.IRON_SWORD, ItemType.SWORD);
        ITEM_TO_TYPE.put(Items.STONE_SWORD, ItemType.SWORD);
        ITEM_TO_TYPE.put(Items.WOODEN_SWORD, ItemType.SWORD);

        ITEM_TO_TYPE.put(Items.NETHERITE_AXE, ItemType.AXE);
        ITEM_TO_TYPE.put(Items.DIAMOND_AXE, ItemType.AXE);
        ITEM_TO_TYPE.put(Items.GOLDEN_AXE, ItemType.AXE);
        ITEM_TO_TYPE.put(Items.IRON_AXE, ItemType.AXE);
        ITEM_TO_TYPE.put(Items.STONE_AXE, ItemType.AXE);
        ITEM_TO_TYPE.put(Items.WOODEN_AXE, ItemType.AXE);

        ITEM_TO_TYPE.put(Items.NETHERITE_PICKAXE, ItemType.PICKAXE);
        ITEM_TO_TYPE.put(Items.DIAMOND_PICKAXE, ItemType.PICKAXE);
        ITEM_TO_TYPE.put(Items.GOLDEN_PICKAXE, ItemType.PICKAXE);
        ITEM_TO_TYPE.put(Items.IRON_PICKAXE, ItemType.PICKAXE);
        ITEM_TO_TYPE.put(Items.STONE_PICKAXE, ItemType.PICKAXE);
        ITEM_TO_TYPE.put(Items.WOODEN_PICKAXE, ItemType.PICKAXE);

        ITEM_TO_TYPE.put(Items.NETHERITE_SHOVEL, ItemType.SHOVEL);
        ITEM_TO_TYPE.put(Items.DIAMOND_SHOVEL, ItemType.SHOVEL);
        ITEM_TO_TYPE.put(Items.GOLDEN_SHOVEL, ItemType.SHOVEL);
        ITEM_TO_TYPE.put(Items.IRON_SHOVEL, ItemType.SHOVEL);
        ITEM_TO_TYPE.put(Items.STONE_SHOVEL, ItemType.SHOVEL);
        ITEM_TO_TYPE.put(Items.WOODEN_SHOVEL, ItemType.SHOVEL);

        ITEM_TO_TYPE.put(Items.NETHERITE_HOE, ItemType.HOE);
        ITEM_TO_TYPE.put(Items.DIAMOND_HOE, ItemType.HOE);
        ITEM_TO_TYPE.put(Items.GOLDEN_HOE, ItemType.HOE);
        ITEM_TO_TYPE.put(Items.IRON_HOE, ItemType.HOE);
        ITEM_TO_TYPE.put(Items.STONE_HOE, ItemType.HOE);
        ITEM_TO_TYPE.put(Items.WOODEN_HOE, ItemType.HOE);

        ITEM_TO_TYPE.put(Items.FISHING_ROD, ItemType.FISHING_ROD);

        ITEM_TO_TYPE.put(Items.CROSSBOW, ItemType.CROSSBOW);
        ITEM_TO_TYPE.put(Items.ENCHANTED_BOOK, ItemType.ENCHANTED_BOOK);

        ENCHANTMENTS.put(ItemType.HELMET, List.of("protection", "fire_protection", "blast_protection", "projectile_protection", "respiration", "aqua_affinity", "thorns", "unbreaking", "binding_curse", "vanishing_curse", "mending"));
        ENCHANTMENTS.put(ItemType.CHESTPLATE, List.of("protection", "fire_protection", "blast_protection", "projectile_protection", "thorns", "unbreaking", "binding_curse", "vanishing_curse", "mending"));
        ENCHANTMENTS.put(ItemType.LEGGINGS, List.of("protection", "fire_protection", "blast_protection", "projectile_protection", "thorns", "unbreaking", "binding_curse", "vanishing_curse", "mending"));
        ENCHANTMENTS.put(ItemType.BOOTS, List.of("protection", "fire_protection", "feather_falling", "blast_protection", "projectile_protection", "thorns", "depth_strider", "unbreaking", "binding_curse", "vanishing_curse", "frost_walker", "mending"));
        ENCHANTMENTS.put(ItemType.SWORD, List.of("sharpness", "smite", "bane_of_arthropods", "knockback", "fire_aspect", "looting", "sweeping_edge", "unbreaking", "vanishing_curse", "mending"));
        ENCHANTMENTS.put(ItemType.AXE, List.of("sharpness", "smite", "bane_of_arthropods", "efficiency", "silk_touch", "unbreaking", "fortune", "vanishing_curse", "mending"));
        ENCHANTMENTS.put(ItemType.PICKAXE, List.of("efficiency", "silk_touch", "unbreaking", "fortune", "vanishing_curse", "mending"));
        ENCHANTMENTS.put(ItemType.SHOVEL, List.of("efficiency", "silk_touch", "unbreaking", "fortune", "vanishing_curse", "mending"));
        ENCHANTMENTS.put(ItemType.HOE, List.of("efficiency", "silk_touch", "unbreaking", "fortune", "vanishing_curse", "mending"));
        ENCHANTMENTS.put(ItemType.FISHING_ROD, List.of("unbreaking", "luck_of_the_sea", "lure", "vanishing_curse", "mending"));
        ENCHANTMENTS.put(ItemType.CROSSBOW, List.of("unbreaking", "multishot", "quick_charge", "piercing", "vanishing_curse", "mending"));
        ENCHANTMENTS.put(ItemType.ENCHANTED_BOOK, List.of("protection","fire_protection","feather_falling",
                "blast_protection","blast_protection","projectile_protection","respiration","aqua_affinity",
                "thorns","depth_strider","sharpness","smite","bane_of_arthropods","knockback","fire_aspect",
                "looting","sweeping_edge","efficiency","silk_touch","unbreaking","fortune","power","punch",
                "flame","infinity","luck_of_the_sea","lure","loyalty","impaling","riptide","channeling","multishot","quick_charge",
                "piercing","density","breach","binding_curse","vanishing_curse","frost_walker","mending"));

        MAX_LEVELS.put("protection", 4);
        MAX_LEVELS.put("fire_protection", 4);
        MAX_LEVELS.put("feather_falling", 4);
        MAX_LEVELS.put("blast_protection", 4);
        MAX_LEVELS.put("projectile_protection", 4);
        MAX_LEVELS.put("respiration", 3);
        MAX_LEVELS.put("aqua_affinity", 1);
        MAX_LEVELS.put("thorns", 3);
        MAX_LEVELS.put("depth_strider", 3);
        MAX_LEVELS.put("sharpness", 5);
        MAX_LEVELS.put("smite", 5);
        MAX_LEVELS.put("bane_of_arthropods", 5);
        MAX_LEVELS.put("knockback", 2);
        MAX_LEVELS.put("fire_aspect", 2);
        MAX_LEVELS.put("looting", 3);
        MAX_LEVELS.put("sweeping_edge", 3);
        MAX_LEVELS.put("efficiency", 5);
        MAX_LEVELS.put("silk_touch", 1);
        MAX_LEVELS.put("unbreaking", 3);
        MAX_LEVELS.put("fortune", 3);
        MAX_LEVELS.put("power", 5);
        MAX_LEVELS.put("punch", 2);
        MAX_LEVELS.put("flame", 1);
        MAX_LEVELS.put("infinity", 1);
        MAX_LEVELS.put("luck_of_the_sea", 3);
        MAX_LEVELS.put("lure", 3);
        MAX_LEVELS.put("loyalty", 3);
        MAX_LEVELS.put("impaling", 5);
        MAX_LEVELS.put("riptide", 3);
        MAX_LEVELS.put("channeling", 1);
        MAX_LEVELS.put("multishot", 1);
        MAX_LEVELS.put("quick_charge", 3);
        MAX_LEVELS.put("piercing", 4);
        MAX_LEVELS.put("density", 5);
        MAX_LEVELS.put("breach", 4);
        MAX_LEVELS.put("binding_curse", 1);
        MAX_LEVELS.put("vanishing_curse", 1);
        MAX_LEVELS.put("frost_walker", 2);
        MAX_LEVELS.put("mending", 1);
    }
}