package ChestLoot121;

import com.seedfinding.mcfeature.loot.LootPool;
import com.seedfinding.mcfeature.loot.LootTable;
import com.seedfinding.mcfeature.loot.entry.EmptyEntry;
import com.seedfinding.mcfeature.loot.entry.ItemEntry;
import com.seedfinding.mcfeature.loot.function.ApplyDamageFunction;
import com.seedfinding.mcfeature.loot.function.SetCountFunction;
import com.seedfinding.mcfeature.loot.item.Items;
import com.seedfinding.mcfeature.loot.roll.ConstantRoll;
import com.seedfinding.mcfeature.loot.roll.UniformRoll;

public class MCLootTables21 {
    public static final LootTable BASTION_TREASURE_CHEST_1_21 = new LootTable(
            new LootPool(new ConstantRoll(3),
                    new ItemEntry(com.seedfinding.mcfeature.loot.item.Items.NETHERITE_INGOT, 15),
                    new ItemEntry(com.seedfinding.mcfeature.loot.item.Items.ANCIENT_DEBRIS, 10),
                    new ItemEntry(com.seedfinding.mcfeature.loot.item.Items.NETHERITE_SCRAP, 8),
                    new ItemEntry(com.seedfinding.mcfeature.loot.item.Items.ANCIENT_DEBRIS, 4).apply(version -> SetCountFunction.constant(2)),
                    new ItemEntry(com.seedfinding.mcfeature.loot.item.Items.DIAMOND_SWORD, 6).apply(version -> new ApplyDamageFunction(), version -> new EnchantRandomly21(Items.DIAMOND_SWORD)),
                    new ItemEntry(com.seedfinding.mcfeature.loot.item.Items.DIAMOND_CHESTPLATE, 6).apply(version -> new ApplyDamageFunction(), version -> new EnchantRandomly21(Items.DIAMOND_CHESTPLATE)),
                    new ItemEntry(com.seedfinding.mcfeature.loot.item.Items.DIAMOND_HELMET, 6).apply(version -> new ApplyDamageFunction(), version -> new EnchantRandomly21(Items.DIAMOND_HELMET)),
                    new ItemEntry(com.seedfinding.mcfeature.loot.item.Items.DIAMOND_LEGGINGS, 6).apply(version -> new ApplyDamageFunction(), version -> new EnchantRandomly21(Items.DIAMOND_LEGGINGS)),
                    new ItemEntry(com.seedfinding.mcfeature.loot.item.Items.DIAMOND_BOOTS, 6).apply(version -> new ApplyDamageFunction(), version -> new EnchantRandomly21(Items.DIAMOND_BOOTS)),
                    new ItemEntry(com.seedfinding.mcfeature.loot.item.Items.DIAMOND_SWORD, 6),
                    new ItemEntry(com.seedfinding.mcfeature.loot.item.Items.DIAMOND_CHESTPLATE, 5),
                    new ItemEntry(com.seedfinding.mcfeature.loot.item.Items.DIAMOND_HELMET, 5),
                    new ItemEntry(com.seedfinding.mcfeature.loot.item.Items.DIAMOND_BOOTS, 5),
                    new ItemEntry(com.seedfinding.mcfeature.loot.item.Items.DIAMOND_LEGGINGS, 5),
                    new ItemEntry(com.seedfinding.mcfeature.loot.item.Items.DIAMOND, 5).apply(version -> SetCountFunction.uniform(2.0F, 6.0F)),
                    new ItemEntry(com.seedfinding.mcfeature.loot.item.Items.ENCHANTED_GOLDEN_APPLE, 2)),
            new LootPool(new UniformRoll(3.0F, 4.0F),
                    new ItemEntry(com.seedfinding.mcfeature.loot.item.Items.SPECTRAL_ARROW).apply(version -> SetCountFunction.uniform(12.0F, 25.0F)),
                    new ItemEntry(com.seedfinding.mcfeature.loot.item.Items.GOLD_BLOCK).apply(version -> SetCountFunction.uniform(2.0F, 5.0F)),
                    new ItemEntry(com.seedfinding.mcfeature.loot.item.Items.IRON_BLOCK).apply(version -> SetCountFunction.uniform(2.0F, 5.0F)),
                    new ItemEntry(com.seedfinding.mcfeature.loot.item.Items.GOLD_INGOT).apply(version -> SetCountFunction.uniform(3.0F, 9.0F)),
                    new ItemEntry(com.seedfinding.mcfeature.loot.item.Items.IRON_INGOT).apply(version -> SetCountFunction.uniform(3.0F, 9.0F)),
                    new ItemEntry(com.seedfinding.mcfeature.loot.item.Items.CRYING_OBSIDIAN).apply(version -> SetCountFunction.uniform(3.0F, 5.0F)),
                    new ItemEntry(com.seedfinding.mcfeature.loot.item.Items.QUARTZ).apply(version -> SetCountFunction.uniform(8.0F, 23.0F)),
                    new ItemEntry(com.seedfinding.mcfeature.loot.item.Items.GILDED_BLACKSTONE).apply(version -> SetCountFunction.uniform(5.0F, 15.0F)),
                    new ItemEntry(Items.MAGMA_CREAM).apply(version -> SetCountFunction.uniform(3.0F, 8.0F))),
            new LootPool(new ConstantRoll(1),
                    new EmptyEntry(11),
                    new ItemEntry(Items21.SNOUT_ARMOR_TRIM_SMITHING_TEMPLATE)),
            new LootPool(new ConstantRoll(1),
                    new ItemEntry(Items21.NETHERITE_UPGRADE_SMITHING_TEMPLATE))
    );

    public static final LootTable BURIED_TREASURE_CHEST_1_21 = new LootTable(
            new LootPool(new ConstantRoll(1),
                    new ItemEntry(Items.HEART_OF_THE_SEA)),
            new LootPool(new UniformRoll(5.0F, 8.0F),
                    new ItemEntry(Items.IRON_INGOT, 20).apply(version -> SetCountFunction.uniform(1.0F, 4.0F)),
                    new ItemEntry(Items.GOLD_INGOT, 10).apply(version -> SetCountFunction.uniform(1.0F, 4.0F)),
                    new ItemEntry(Items.TNT, 5).apply(version -> SetCountFunction.uniform(1.0F, 2.0F))),
            new LootPool(new UniformRoll(1.0F, 3.0F),
                    new ItemEntry(Items.EMERALD, 5).apply(version -> SetCountFunction.uniform(4.0F, 8.0F)),
                    new ItemEntry(Items.DIAMOND, 5).apply(version -> SetCountFunction.uniform(1.0F, 2.0F)),
                    new ItemEntry(Items.PRISMARINE_CRYSTALS, 5).apply(version -> SetCountFunction.uniform(1.0F, 5.0F))),
            new LootPool(new UniformRoll(0.0F, 1.0F),
                    new ItemEntry(Items.LEATHER_CHESTPLATE),
                    new ItemEntry(Items.IRON_SWORD)),
            new LootPool(new ConstantRoll(2),
                    new ItemEntry(Items.COOKED_COD).apply(version -> SetCountFunction.uniform(2.0F, 4.0F)),
                    new ItemEntry(Items.COOKED_SALMON).apply(version -> SetCountFunction.uniform(2.0F, 4.0F))),
            new LootPool(new UniformRoll(0.0F, 2.0F),
                    new ItemEntry(Items21.WATER_BREATHING_POTION))
    );

    public static final LootTable RUINED_PORTAL_CHEST_1_21 = new LootTable(
            new LootPool(new UniformRoll(4.0F, 8.0F),
                    new ItemEntry(Items.OBSIDIAN, 40).apply(version -> SetCountFunction.uniform(1.0F, 2.0F)),
                    new ItemEntry(Items.FLINT, 40).apply(version -> SetCountFunction.uniform(1.0F, 4.0F)),
                    new ItemEntry(Items.IRON_NUGGET, 40).apply(version -> SetCountFunction.uniform(9.0F, 18.0F)),
                    new ItemEntry(Items.FLINT_AND_STEEL, 40),
                    new ItemEntry(Items.FIRE_CHARGE, 40),
                    new ItemEntry(Items.GOLDEN_APPLE, 15),
                    new ItemEntry(Items.GOLD_NUGGET, 15).apply(version -> SetCountFunction.uniform(4.0F, 24.0F)),
                    new ItemEntry(Items.GOLDEN_SWORD, 15).apply(version -> new EnchantRandomly21(Items.GOLDEN_SWORD)),
                    new ItemEntry(Items.GOLDEN_AXE, 15).apply(version -> new EnchantRandomly21(Items.GOLDEN_AXE)),
                    new ItemEntry(Items.GOLDEN_HOE, 15).apply(version -> new EnchantRandomly21(Items.GOLDEN_HOE)),
                    new ItemEntry(Items.GOLDEN_SHOVEL, 15).apply(version -> new EnchantRandomly21(Items.GOLDEN_SHOVEL)),
                    new ItemEntry(Items.GOLDEN_PICKAXE, 15).apply(version -> new EnchantRandomly21(Items.GOLDEN_PICKAXE)),
                    new ItemEntry(Items.GOLDEN_BOOTS, 15).apply(version -> new EnchantRandomly21(Items.GOLDEN_BOOTS)),
                    new ItemEntry(Items.GOLDEN_CHESTPLATE, 15).apply(version -> new EnchantRandomly21(Items.GOLDEN_CHESTPLATE)),
                    new ItemEntry(Items.GOLDEN_HELMET, 15).apply(version -> new EnchantRandomly21(Items.GOLDEN_HELMET)),
                    new ItemEntry(Items.GOLDEN_LEGGINGS, 15).apply(version -> new EnchantRandomly21(Items.GOLDEN_LEGGINGS)),
                    new ItemEntry(Items.GLISTERING_MELON_SLICE, 5).apply(version -> SetCountFunction.uniform(4.0F, 12.0F)),
                    new ItemEntry(Items.GOLDEN_HORSE_ARMOR, 5),
                    new ItemEntry(Items.LIGHT_WEIGHTED_PRESSURE_PLATE, 5),
                    new ItemEntry(Items.GOLDEN_CARROT, 5).apply(version -> SetCountFunction.uniform(4.0F, 12.0F)),
                    new ItemEntry(Items.CLOCK, 5),
                    new ItemEntry(Items.GOLD_INGOT, 5).apply(version -> SetCountFunction.uniform(2.0F, 8.0F)),
                    new ItemEntry(Items.BELL),
                    new ItemEntry(Items.ENCHANTED_GOLDEN_APPLE),
                    new ItemEntry(Items.GOLD_BLOCK).apply(version -> SetCountFunction.uniform(1.0F, 2.0F)))
    );
    public static final LootTable DESERT_PYRAMID_CHEST_1_21_9 =new LootTable(
            new LootPool(new UniformRoll(2.0F, 4.0F),
                    new ItemEntry(Items.DIAMOND, 5).apply(version -> SetCountFunction.uniform(1.0F, 3.0F)),
                    new ItemEntry(Items.IRON_INGOT, 15).apply(version -> SetCountFunction.uniform(1.0F, 5.0F)),
                    new ItemEntry(Items.GOLD_INGOT, 15).apply(version -> SetCountFunction.uniform(2.0F, 7.0F)),
                    new ItemEntry(Items.EMERALD, 15).apply(version -> SetCountFunction.uniform(1.0F, 3.0F)),
                    new ItemEntry(Items.BONE, 25).apply(version -> SetCountFunction.uniform(4.0F, 6.0F)),
                    new ItemEntry(Items.SPIDER_EYE, 25).apply(version -> SetCountFunction.uniform(1.0F, 3.0F)),
                    new ItemEntry(Items.ROTTEN_FLESH, 25).apply(version -> SetCountFunction.uniform(3.0F, 7.0F)),
                    new ItemEntry(Items.LEATHER, 20).apply(version -> SetCountFunction.uniform(1.0F, 5.0F)),
                    new ItemEntry(Items21.COPPER_HORSE_ARMOR, 15),
                    new ItemEntry(Items.IRON_HORSE_ARMOR, 15),
                    new ItemEntry(Items.GOLDEN_HORSE_ARMOR, 10),
                    new ItemEntry(Items.DIAMOND_HORSE_ARMOR, 5),
                    new ItemEntry(Items.ENCHANTED_BOOK, 20).apply(version -> new EnchantRandomly21(Items.ENCHANTED_BOOK)),
                    new ItemEntry(Items.GOLDEN_APPLE, 20),
                    new ItemEntry(Items.ENCHANTED_GOLDEN_APPLE, 2),
                    new EmptyEntry(15)),
            new LootPool(new ConstantRoll(4),
                    new ItemEntry(Items.BONE, 10).apply(version -> SetCountFunction.uniform(1.0F, 8.0F)),
                    new ItemEntry(Items.GUNPOWDER, 10).apply(version -> SetCountFunction.uniform(1.0F, 8.0F)),
                    new ItemEntry(Items.ROTTEN_FLESH, 10).apply(version -> SetCountFunction.uniform(1.0F, 8.0F)),
                    new ItemEntry(Items.STRING, 10).apply(version -> SetCountFunction.uniform(1.0F, 8.0F)),
                    new ItemEntry(Items.SAND, 10).apply(version -> SetCountFunction.uniform(1.0F, 8.0F))),
            new LootPool(new ConstantRoll(1),
                    new EmptyEntry(6),
                    new ItemEntry(Items21.DUNE_ARMOR_TRIM_SMITHING_TEMPLATE, 1).apply(version -> SetCountFunction.constant(2)))
    );
    public static final LootTable DESERT_PYRAMID_CHEST_1_19 =new LootTable(
            new LootPool(new UniformRoll(2.0F, 4.0F),
                    new ItemEntry(Items.DIAMOND, 5).apply(version -> SetCountFunction.uniform(1.0F, 3.0F)),
                    new ItemEntry(Items.IRON_INGOT, 15).apply(version -> SetCountFunction.uniform(1.0F, 5.0F)),
                    new ItemEntry(Items.GOLD_INGOT, 15).apply(version -> SetCountFunction.uniform(2.0F, 7.0F)),
                    new ItemEntry(Items.EMERALD, 15).apply(version -> SetCountFunction.uniform(1.0F, 3.0F)),
                    new ItemEntry(Items.BONE, 25).apply(version -> SetCountFunction.uniform(4.0F, 6.0F)),
                    new ItemEntry(Items.SPIDER_EYE, 25).apply(version -> SetCountFunction.uniform(1.0F, 3.0F)),
                    new ItemEntry(Items.ROTTEN_FLESH, 25).apply(version -> SetCountFunction.uniform(3.0F, 7.0F)),
                    new ItemEntry(Items.SADDLE, 20),
                    new ItemEntry(Items.IRON_HORSE_ARMOR, 15),
                    new ItemEntry(Items.GOLDEN_HORSE_ARMOR, 10),
                    new ItemEntry(Items.DIAMOND_HORSE_ARMOR, 5),
                    new ItemEntry(Items.ENCHANTED_BOOK, 20).apply(version -> new EnchantRandomly21(Items.ENCHANTED_BOOK)),
                    new ItemEntry(Items.GOLDEN_APPLE, 20),
                    new ItemEntry(Items.ENCHANTED_GOLDEN_APPLE, 2),
                    new EmptyEntry(15)),
            new LootPool(new ConstantRoll(4),
                    new ItemEntry(Items.BONE, 10).apply(version -> SetCountFunction.uniform(1.0F, 8.0F)),
                    new ItemEntry(Items.GUNPOWDER, 10).apply(version -> SetCountFunction.uniform(1.0F, 8.0F)),
                    new ItemEntry(Items.ROTTEN_FLESH, 10).apply(version -> SetCountFunction.uniform(1.0F, 8.0F)),
                    new ItemEntry(Items.STRING, 10).apply(version -> SetCountFunction.uniform(1.0F, 8.0F)),
                    new ItemEntry(Items.SAND, 10).apply(version -> SetCountFunction.uniform(1.0F, 8.0F)))
    );
}