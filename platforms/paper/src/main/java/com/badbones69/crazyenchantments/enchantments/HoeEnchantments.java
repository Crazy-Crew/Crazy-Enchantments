package com.badbones69.crazyenchantments.enchantments;

import com.badbones69.crazyenchantments.CrazyEnchantments;
import com.badbones69.crazyenchantments.Methods;
import com.badbones69.crazyenchantments.Starter;
import com.badbones69.crazyenchantments.api.CrazyManager;
import com.badbones69.crazyenchantments.api.enums.CEnchantments;
import com.badbones69.crazyenchantments.api.objects.CEnchantment;
import com.badbones69.crazyenchantments.api.objects.ItemBuilder;
import com.badbones69.crazyenchantments.controllers.settings.EnchantmentBookSettings;
import com.badbones69.crazyenchantments.controllers.settings.EnchantmentSettings;
import com.badbones69.crazyenchantments.utilities.misc.EventUtils;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public class HoeEnchantments implements Listener {

    private final CrazyEnchantments plugin = CrazyEnchantments.getPlugin();

    private final Starter starter = plugin.getStarter();

    private final Methods methods = starter.getMethods();

    private final CrazyManager crazyManager = starter.getCrazyManager();

    // Settings.
    private final EnchantmentSettings enchantmentSettings = starter.getEnchantmentSettings();

    private final EnchantmentBookSettings enchantmentBookSettings = starter.getEnchantmentBookSettings();

    private final Random random = new Random();
    private final Material soilBlock = Material.FARMLAND;
    private final Material grassBlock = Material.GRASS_BLOCK;
    private final HashMap<UUID, HashMap<Block, BlockFace>> blocks = new HashMap<>();

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        Player player = e.getPlayer();

        if (e.getHand() != EquipmentSlot.HAND) return;

        if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            ItemStack hoe = methods.getItemInHand(player);
            Block block = e.getClickedBlock();
            List<CEnchantment> enchantments = enchantmentBookSettings.getEnchantmentsOnItem(hoe);

            // Crop is not fully grown.
            if (CEnchantments.GREENTHUMB.isActivated() && enchantments.contains(CEnchantments.GREENTHUMB.getEnchantment()) && enchantmentSettings.getSeedlings().contains(block.getType()) && !crazyManager.getNMSSupport().isFullyGrown(block)) {
                fullyGrowPlant(hoe, block, player);

                if (player.getGameMode() != GameMode.CREATIVE) methods.removeDurability(hoe, player);
            }

            assert block != null;
            if (block.getType() == grassBlock || block.getType() == Material.DIRT || block.getType() == Material.SOUL_SAND || block.getType() == soilBlock) {
                boolean hasGreenThumb = CEnchantments.GREENTHUMB.isActivated() && enchantments.contains(CEnchantments.GREENTHUMB.getEnchantment());

                if (enchantments.contains(CEnchantments.TILLER.getEnchantment())) {
                    for (Block soil : getSoil(player, block)) {

                        if (soil.getType() != soilBlock && soil.getType() != Material.SOUL_SAND) soil.setType(soilBlock);

                        if (soil.getType() != Material.SOUL_SAND) {
                            for (Block water : getAreaBlocks(soil, 4)) {
                                if (water.getType() == Material.WATER) {
                                    crazyManager.getNMSSupport().hydrateSoil(soil);
                                    break;
                                }
                            }
                        }

                        if (enchantments.contains(CEnchantments.PLANTER.getEnchantment())) plantSeedSuccess(hoe, soil, player, hasGreenThumb);

                        // Take durability from the hoe for each block set to a soil.
                        if (player.getGameMode() != GameMode.CREATIVE) methods.removeDurability(hoe, player);
                    }
                }

                // Take durability from players not in Creative
                // Checking else to make sure the item does have Tiller.
                if (player.getGameMode() != GameMode.CREATIVE && CEnchantments.PLANTER.isActivated() && enchantments.contains(CEnchantments.PLANTER.getEnchantment()) && !enchantments.contains(CEnchantments.TILLER.getEnchantment()) && plantSeedSuccess(hoe, block, player, hasGreenThumb)) methods.removeDurability(hoe, player);
            }
        } else if (e.getAction() == Action.LEFT_CLICK_BLOCK && CEnchantments.HARVESTER.isActivated() && crazyManager.hasEnchantment(methods.getItemInHand(player), CEnchantments.HARVESTER)) {
            HashMap<Block, BlockFace> blockFace = new HashMap<>();
            blockFace.put(e.getClickedBlock(), e.getBlockFace());
            blocks.put(player.getUniqueId(), blockFace);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (!event.isCancelled() && !EventUtils.isIgnoredEvent(event)) {
            Player player = event.getPlayer();
            Block plant = event.getBlock();

            if (!enchantmentSettings.getHarvesterCrops().contains(plant.getType())) return;

            ItemStack hoe = methods.getItemInHand(player);
            List<CEnchantment> enchantments = enchantmentBookSettings.getEnchantmentsOnItem(hoe);
            if (!blocks.containsKey(player.getUniqueId())) return;
            if (enchantments.isEmpty()) return;
            if (!CEnchantments.HARVESTER.isActivated()) return;
            if (!enchantments.contains(CEnchantments.HARVESTER.getEnchantment())) return;

            BlockFace blockFace = blocks.get(player.getUniqueId()).get(plant);
            blocks.remove(player.getUniqueId());

            if (!crazyManager.getNMSSupport().isFullyGrown(plant)) return;
            boolean hasTelepathy = enchantments.contains(CEnchantments.TELEPATHY.getEnchantment());

            for (Block crop : getAreaCrops(player, plant, blockFace)) {
                if (hasTelepathy) {
                    List<ItemStack> droppedItems = new ArrayList<>();

                    if (crop.getType() == Material.COCOA) {
                        droppedItems.add(new ItemBuilder().setMaterial(Material.COCOA_BEANS).setAmount(random.nextInt(2) + 2).build()); // Coco drops 2-3 beans.
                    } else if (crop.getType() == Material.WHEAT) {
                        droppedItems.add(new ItemBuilder().setMaterial(Material.WHEAT).build());
                        droppedItems.add(new ItemBuilder().setMaterial(Material.WHEAT_SEEDS).setAmount(random.nextInt(3)).build()); // Wheat drops 0-3 seeds.
                    } else if (crop.getType() == Material.BEETROOTS) {
                        droppedItems.add(new ItemBuilder().setMaterial(Material.BEETROOT).build());
                        droppedItems.add(new ItemBuilder().setMaterial(Material.BEETROOT_SEEDS).setAmount(random.nextInt(3)).build()); // BeetRoots drops 0-3 seeds.
                    } else if (crop.getType() == Material.POTATO) {
                        droppedItems.add(new ItemBuilder().setMaterial(Material.POTATO).setAmount(random.nextInt(4) + 1).build()); // Potatoes drop 1-4 of them self's.
                    } else if (crop.getType() == Material.CARROTS) {
                        droppedItems.add(new ItemBuilder().setMaterial(Material.CARROT).setAmount(random.nextInt(4) + 1).build()); // Carrots drop 1-4 of them self's.
                    } else if (crop.getType() == Material.NETHER_WART) {
                        droppedItems.add(new ItemBuilder().setMaterial(Material.NETHER_WART).setAmount(random.nextInt(3) + 2).build()); // Nether Warts drop 2-4 of them self's.
                    }

                    if (!droppedItems.isEmpty()) {
                        for (ItemStack droppedItem : droppedItems) {
                            if (droppedItem.getAmount() > 0) {
                                if (methods.isInventoryFull(player)) {
                                    player.getWorld().dropItem(player.getLocation(), droppedItem);
                                } else {
                                    player.getInventory().addItem(droppedItem);
                                }
                            }
                        }

                        event.setDropItems(false);
                        crop.setType(Material.AIR);
                        continue;
                    }
                }

                crop.breakNaturally();
            }
        }
    }

    private void fullyGrowPlant(ItemStack hoe, Block block, Player player) {
        if (CEnchantments.GREENTHUMB.chanceSuccessful(hoe) || player.getGameMode() == GameMode.CREATIVE) {
            crazyManager.getNMSSupport().fullyGrowPlant(block);
            player.getLocation().getWorld().spawnParticle(Particle.VILLAGER_HAPPY, player.getLocation(), 20, .25F, .25F, .25F);
        }
    }

    private boolean plantSeedSuccess(ItemStack hoe, Block soil, Player player, boolean hasGreenThumb) {
        boolean isSoulSand = soil.getType() == Material.SOUL_SAND;
        Material seedType;
        ItemStack playerSeedItem;
        Block plant = soil.getLocation().add(0, 1, 0).getBlock();

        if (plant.getType() == Material.AIR) {
            seedType = getPlanterSeed(player.getEquipment().getItemInOffHand());
            playerSeedItem = player.getEquipment().getItemInOffHand();

            if (isSoulSand) { // If on soul sand we want it to plant Nether Warts not normal seeds.
                if (playerSeedItem != null && playerSeedItem.getType() != Material.NETHER_WART) seedType = null;
            } else {
                if (playerSeedItem != null && playerSeedItem.getType() == Material.NETHER_WART) seedType = null;
            }

            if (seedType == null) {
                for (int slot = 0; slot < 9; slot++) {
                    seedType = getPlanterSeed(player.getInventory().getItem(slot));
                    playerSeedItem = player.getInventory().getItem(slot);

                    if (isSoulSand) { // If on soul sand we want it to plant Nether Warts not normal seeds.
                        if (playerSeedItem != null && playerSeedItem.getType() != Material.NETHER_WART) seedType = null;
                    } else {
                        if (playerSeedItem != null && playerSeedItem.getType() == Material.NETHER_WART) seedType = null; // Makes sure nether warts are not put on soil.
                    }

                    if (seedType != null) break;
                }
            }

            if (seedType != null) {
                if (soil.getType() != soilBlock && !isSoulSand) soil.setType(soilBlock);

                if (player.getGameMode() != GameMode.CREATIVE) methods.removeItem(playerSeedItem, player); // Take seed from player

                plant.setType(seedType);

                if (hasGreenThumb) fullyGrowPlant(hoe, plant, player);

                return true;
            }
        }

        return false;
    }

    private Material getPlanterSeed(ItemStack item) {
        return item != null ? enchantmentSettings.getPlanterSeed(item.getType()) : null;
    }

    private List<Block> getAreaCrops(Player player, Block block, BlockFace blockFace) {
        List<Block> blockList = new ArrayList<>();

        for (Block crop : getAreaBlocks(block, blockFace, 0, 1)) { // Radius of 1 is 3x3
            if (enchantmentSettings.getHarvesterCrops().contains(crop.getType()) && crazyManager.getNMSSupport().isFullyGrown(crop)) {
                BlockBreakEvent useEvent = new BlockBreakEvent(crop, player);
                EventUtils.addIgnoredEvent(useEvent);
                plugin.getServer().getPluginManager().callEvent(useEvent);

                if (!useEvent.isCancelled()) { // This stops players from breaking blocks that might be in protected areas.
                    blockList.add(crop);
                    EventUtils.removeIgnoredEvent(useEvent);
                }
            }
        }

        return blockList;
    }

    private List<Block> getSoil(Player player, Block block) {
        List<Block> soilBlocks = new ArrayList<>();
        for (Block soil : getAreaBlocks(block)) {
            if (soil.getType() == grassBlock || soil.getType() == Material.DIRT || soil.getType() == Material.SOUL_SAND || soil.getType() == soilBlock) {
                BlockBreakEvent useEvent = new BlockBreakEvent(soil, player);
                EventUtils.addIgnoredEvent(useEvent);
                plugin.getServer().getPluginManager().callEvent(useEvent);

                if (!useEvent.isCancelled()) { // This stops players from breaking blocks that might be in protected areas.
                    soilBlocks.add(soil);
                    EventUtils.removeIgnoredEvent(useEvent);
                }
            }
        }

        return soilBlocks;
    }

    private List<Block> getAreaBlocks(Block block) {
        return getAreaBlocks(block, BlockFace.UP, 0, 1); // Radius of 1 is 3x3.
    }

    private List<Block> getAreaBlocks(Block block, int radius) {
        return getAreaBlocks(block, BlockFace.UP, 0, radius);
    }

    private List<Block> getAreaBlocks(Block block, BlockFace blockFace, int depth, int radius) {
        Location loc = block.getLocation();
        Location loc2 = block.getLocation();

        switch (blockFace) {
            case SOUTH -> {
                loc.add(-radius, radius, -depth);
                loc2.add(radius, -radius, 0);
            }

            case WEST -> {
                loc.add(depth, radius, -radius);
                loc2.add(0, -radius, radius);
            }

            case EAST -> {
                loc.add(-depth, radius, radius);
                loc2.add(0, -radius, -radius);
            }

            case NORTH -> {
                loc.add(radius, radius, depth);
                loc2.add(-radius, -radius, 0);
            }

            case UP -> {
                loc.add(-radius, -depth, -radius);
                loc2.add(radius, 0, radius);
            }

            case DOWN -> {
                loc.add(radius, depth, radius);
                loc2.add(-radius, 0, -radius);
            }

            default -> {}
        }

        return methods.getEnchantBlocks(loc, loc2);
    }
}