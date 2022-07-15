package com.badbones69.crazyenchantments.enchantments;

import com.badbones69.crazyenchantments.Methods;
import com.badbones69.crazyenchantments.api.CrazyManager;
import com.badbones69.crazyenchantments.api.enums.CEnchantments;
import com.badbones69.crazyenchantments.api.events.EnchantmentUseEvent;
import com.badbones69.crazyenchantments.api.objects.BlockProcessInfo;
import com.badbones69.crazyenchantments.api.objects.CEnchantment;
import com.badbones69.crazyenchantments.api.objects.ItemBuilder;
import com.badbones69.crazyenchantments.api.objects.TelepathyDrop;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

public class Tools implements Listener {
    
    private final static Random random = new Random();
    private final static CrazyManager crazyManager = CrazyManager.getInstance();
    private final static List<String> ignoreBlockTypes = Arrays.asList("air", "shulker_box", "chest", "head", "skull");
    
    @EventHandler
    public void onPlayerClick(PlayerInteractEvent e) {
        updateEffects(e.getPlayer());
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockBreakEvent e) {
        Block block = e.getBlock();
        Player player = e.getPlayer();

        if (e.isCancelled() || crazyManager.isIgnoredEvent(e) || ignoreBlockTypes(block)) {
            return;
        }

        ItemStack item = Methods.getItemInHand(player);

        updateEffects(player);

        if (player.getGameMode() != GameMode.CREATIVE) {
            List<CEnchantment> enchantments = crazyManager.getEnchantmentsOnItem(item);

            if (enchantments.contains(CEnchantments.TELEPATHY.getEnchantment()) && !enchantments.contains(CEnchantments.BLAST.getEnchantment())) {
                // This checks if the player is breaking a crop with harvester one. The harvester enchantment will control what happens with telepathy here.
                if ((Hoes.getHarvesterCrops().contains(block.getType()) && enchantments.contains(CEnchantments.HARVESTER.getEnchantment())) ||
                // This checks if the block is a spawner and if so the spawner classes will take care of this.
                // If Epic Spawners is enabled then telepathy will give the item from the API.
                // Otherwise, CE will ignore the spawner in this event.
                (block.getType() == Material.SPAWNER)) {
                    return;
                }

                EnchantmentUseEvent event = new EnchantmentUseEvent(player, CEnchantments.TELEPATHY, item);
                crazyManager.getPlugin().getServer().getPluginManager().callEvent(event);

                if (!event.isCancelled()) {
                    e.setExpToDrop(0);
                    e.setDropItems(false);
                    TelepathyDrop drop = getTelepathyDrops(new BlockProcessInfo(item, block));

                    if (Methods.isInventoryFull(player)) {
                        player.getWorld().dropItem(player.getLocation(), drop.getItem());
                    } else {
                        player.getInventory().addItem(drop.getItem());
                    }

                    if (drop.getSugarCaneBlocks().isEmpty()) {
                        block.setType(Material.AIR);
                    } else {
                        drop.getSugarCaneBlocks().forEach(cane -> cane.setType(Material.AIR));
                    }

                    if (drop.hasXp()) {
                        ExperienceOrb orb = block.getWorld().spawn(block.getLocation().add(.5, .5, .5), ExperienceOrb.class);
                        orb.setExperience(drop.getXp());
                    }

                    Methods.removeDurability(item, player);
                }
            }
        }
    }

    @SuppressWarnings("squid:CallToDeprecatedMethod")
    public static TelepathyDrop getTelepathyDrops(BlockProcessInfo processInfo) {
        ItemStack item = processInfo.getItem();
        Block block = processInfo.getBlock();
        List<CEnchantment> enchantments = crazyManager.getEnchantmentsOnItem(item);
        List<Block> sugarCaneBlocks = new ArrayList<>();
        boolean isOre = isOre(block);
        boolean hasSilkTouch = item.getItemMeta().hasEnchant(Enchantment.SILK_TOUCH);
        boolean hasFurnace = enchantments.contains(CEnchantments.FURNACE.getEnchantment());
        boolean hasAutoSmelt = enchantments.contains(CEnchantments.AUTOSMELT.getEnchantment());
        boolean hasExperience = enchantments.contains(CEnchantments.EXPERIENCE.getEnchantment());
        ItemBuilder itemDrop = null;
        int xp = 0;

        for (ItemStack drop : processInfo.getDrops()) {

            if (itemDrop == null) {
                // Amount is set to 0 as it adds to the drop amount and so it would add 1 to many.
                itemDrop = new ItemBuilder().setMaterial(drop.getType()).setAmount(0);
            }

            if (!hasSilkTouch) {
                if (hasFurnace && isOre) {
                    itemDrop = ItemBuilder.convertItemStack(getOreDrop(block)).setAmount(0);
                } else if (hasAutoSmelt && isOre && CEnchantments.AUTOSMELT.chanceSuccessful(item)) {
                    itemDrop = ItemBuilder.convertItemStack(getOreDrop(block)).setAmount(crazyManager.getLevel(item, CEnchantments.AUTOSMELT));
                }

                if (hasOreXP(block)) {
                    xp = Methods.percentPick(7, 3);
                    if (hasExperience && CEnchantments.EXPERIENCE.chanceSuccessful(item)) {
                        xp += Methods.percentPick(7, 3) * crazyManager.getLevel(item, CEnchantments.EXPERIENCE);
                    }
                }
            }

            if (block.getType() == Material.SUGAR_CANE) {
                sugarCaneBlocks = getSugarCaneBlocks(block);
                drop.setAmount(sugarCaneBlocks.size());
            }

            itemDrop.addAmount(drop.getAmount());
        }

        if (itemDrop == null) {
            // In case the drop is still null as no drops were found.
            itemDrop = new ItemBuilder().setMaterial(block.getType());
        }

        if (block.getType() == Material.COCOA) {
            // Coco drops 2-3 beans.
            itemDrop.setMaterial(Material.COCOA_BEANS)
                    .setAmount(crazyManager.getNMSSupport().isFullyGrown(block) ? random.nextInt(2) + 2 : 1);
        }

        if (itemDrop.getMaterial() == Material.WHEAT || itemDrop.getMaterial() == Material.BEETROOT_SEEDS) {
            itemDrop.setAmount(random.nextInt(3)); // Wheat and BeetRoots drops 0-3 seeds.
        } else if (itemDrop.getMaterial() == Material.POTATO || itemDrop.getMaterial() == Material.CARROT) {
            itemDrop.setAmount(random.nextInt(4) + 1); // Carrots and Potatoes drop 1-4 of them self's.
        }

        return new TelepathyDrop(itemDrop.build(), xp, sugarCaneBlocks);
    }

    private static List<Block> getSugarCaneBlocks(Block block) {
        List<Block> sugarCaneBlocks = new ArrayList<>();
        Block cane = block;

        while (cane.getType() == Material.SUGAR_CANE) {
            sugarCaneBlocks.add(cane);
            cane = cane.getLocation().add(0, 1, 0).getBlock();
        }

        Collections.reverse(sugarCaneBlocks);
        return sugarCaneBlocks;
    }

    private void updateEffects(Player player) {
        ItemStack item = Methods.getItemInHand(player);

        if (crazyManager.hasEnchantments(item)) {
            List<CEnchantment> enchantments = crazyManager.getEnchantmentsOnItem(item);
            int potionTime = 5 * 20;

            if (enchantments.contains(CEnchantments.HASTE.getEnchantment())) {
                EnchantmentUseEvent event = new EnchantmentUseEvent(player, CEnchantments.HASTE, item);
                crazyManager.getPlugin().getServer().getPluginManager().callEvent(event);

                if (!event.isCancelled()) {
                    int power = crazyManager.getLevel(item, CEnchantments.HASTE);
                    player.removePotionEffect(PotionEffectType.FAST_DIGGING);
                    player.addPotionEffect(new PotionEffect(PotionEffectType.FAST_DIGGING, potionTime, power - 1));
                }
            }

            if (enchantments.contains(CEnchantments.OXYGENATE.getEnchantment())) {
                EnchantmentUseEvent event = new EnchantmentUseEvent(player, CEnchantments.OXYGENATE, item);
                crazyManager.getPlugin().getServer().getPluginManager().callEvent(event);

                if (!event.isCancelled()) {
                    player.removePotionEffect(PotionEffectType.WATER_BREATHING);
                    player.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, potionTime, 5));
                }
            }
        }
    }
    
    private static boolean ignoreBlockTypes(Block block) {
        for (String name : ignoreBlockTypes) {
            if (block.getType().name().toLowerCase().contains(name)) {
                return true;
            }
        }

        return false;
    }
    
    private static boolean hasOreXP(Block block) {
        return switch (block.getType()) {
            case COAL_ORE, DIAMOND_ORE, EMERALD_ORE, LAPIS_ORE, REDSTONE_ORE -> true;
            default -> false;
        };
    }
    
    private static boolean isOre(Block block) {
        if (block.getType() == Material.NETHER_QUARTZ_ORE) {
            return true;
        }

        return switch (block.getType()) {
            case COAL_ORE, IRON_ORE, GOLD_ORE, DIAMOND_ORE, EMERALD_ORE, LAPIS_ORE, REDSTONE_ORE -> true;
            default -> false;
        };
    }
    
    private static ItemStack getOreDrop(Block block) {
        ItemBuilder dropItem = new ItemBuilder();

        if (block.getType() == Material.NETHER_QUARTZ_ORE) {
            dropItem.setMaterial(Material.QUARTZ);
        } else {
            switch (block.getType()) {
                case COAL_ORE -> dropItem.setMaterial(Material.COAL);
                case IRON_ORE -> dropItem.setMaterial(Material.IRON_INGOT);
                case GOLD_ORE -> dropItem.setMaterial(Material.GOLD_INGOT);
                case DIAMOND_ORE -> dropItem.setMaterial(Material.DIAMOND);
                case EMERALD_ORE -> dropItem.setMaterial(Material.EMERALD);
                case LAPIS_ORE -> dropItem.setMaterial(Material.LAPIS_LAZULI);
                case REDSTONE_ORE -> dropItem.setMaterial(Material.REDSTONE);
                default -> dropItem.setMaterial(Material.AIR);
            }
        }

        return dropItem.build();
    }
}