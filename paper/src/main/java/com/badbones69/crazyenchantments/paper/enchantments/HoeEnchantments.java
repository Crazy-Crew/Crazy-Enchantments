package com.badbones69.crazyenchantments.paper.enchantments;

import com.badbones69.crazyenchantments.paper.CrazyEnchantments;
import com.badbones69.crazyenchantments.paper.Methods;
import com.badbones69.crazyenchantments.paper.Starter;
import com.badbones69.crazyenchantments.paper.api.CrazyManager;
import com.badbones69.crazyenchantments.paper.api.enums.CEnchantments;
import com.badbones69.crazyenchantments.paper.api.objects.CEnchantment;
import com.badbones69.crazyenchantments.paper.controllers.settings.EnchantmentBookSettings;
import com.badbones69.crazyenchantments.paper.controllers.settings.EnchantmentSettings;
import com.badbones69.crazyenchantments.paper.utilities.misc.EnchantUtils;
import com.badbones69.crazyenchantments.paper.utilities.misc.EventUtils;
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

import java.util.*;

public class HoeEnchantments implements Listener {

    private final CrazyEnchantments plugin = CrazyEnchantments.getPlugin();

    private final Starter starter = plugin.getStarter();

    private final Methods methods = starter.getMethods();

    private final CrazyManager crazyManager = starter.getCrazyManager();

    // Settings.
    private final EnchantmentSettings enchantmentSettings = starter.getEnchantmentSettings();

    private final EnchantmentBookSettings enchantmentBookSettings = starter.getEnchantmentBookSettings();

    private final Material soilBlock = Material.FARMLAND;
    private final Material grassBlock = Material.GRASS_BLOCK;
    private final HashMap<UUID, HashMap<Block, BlockFace>> blocks = new HashMap<>();

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        if (event.getHand() != EquipmentSlot.HAND) return;

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            ItemStack hoe = methods.getItemInHand(player);
            Block block = event.getClickedBlock();
            if (block == null) return;
            Map<CEnchantment, Integer> enchantments = enchantmentBookSettings.getEnchantments(hoe);

            // Crop is not fully grown.
            if (enchantmentSettings.getSeedlings().contains(block.getType())
                    && !crazyManager.getNMSSupport().isFullyGrown(block)
                    && EnchantUtils.isEventActive(CEnchantments.GREENTHUMB, player, hoe, enchantments)) {
                fullyGrowPlant(block);
                if (player.getGameMode() != GameMode.CREATIVE) methods.removeDurability(hoe, player);
            }

            if (block.getType() == grassBlock || block.getType() == Material.DIRT || block.getType() == Material.SOUL_SAND || block.getType() == soilBlock) {
                boolean hasGreenThumb = CEnchantments.GREENTHUMB.isActivated() && enchantments.containsKey(CEnchantments.GREENTHUMB.getEnchantment());

                if (EnchantUtils.isEventActive(CEnchantments.TILLER, player, hoe, enchantments)) {
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

                        if (enchantments.containsKey(CEnchantments.PLANTER.getEnchantment())) plantSeedSuccess(soil, player, hasGreenThumb);

                        // Take durability from the hoe for each block set to a soil.
                        if (player.getGameMode() != GameMode.CREATIVE) methods.removeDurability(hoe, player);
                    }
                }

                // Take durability from players not in Creative
                // Checking else to make sure the item does have Tiller.
                if (player.getGameMode() != GameMode.CREATIVE && CEnchantments.PLANTER.isActivated()
                        && enchantments.containsKey(CEnchantments.PLANTER.getEnchantment())
                        && !enchantments.containsKey(CEnchantments.TILLER.getEnchantment())
                        && plantSeedSuccess(block, player, hasGreenThumb)) methods.removeDurability(hoe, player);
            }
        } else if (event.getAction() == Action.LEFT_CLICK_BLOCK && CEnchantments.HARVESTER.isActivated() && crazyManager.hasEnchantment(methods.getItemInHand(player), CEnchantments.HARVESTER)) {
            HashMap<Block, BlockFace> blockFace = new HashMap<>();
            blockFace.put(event.getClickedBlock(), event.getBlockFace());
            blocks.put(player.getUniqueId(), blockFace);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (!event.isDropItems()) return;
        if (!event.isCancelled() && !EventUtils.isIgnoredEvent(event)) {
            Player player = event.getPlayer();
            Block plant = event.getBlock();

            if (!enchantmentSettings.getHarvesterCrops().contains(plant.getType())) return;

            ItemStack hoe = methods.getItemInHand(player);
            Map<CEnchantment, Integer> enchantments = enchantmentBookSettings.getEnchantments(hoe);
            if (!blocks.containsKey(player.getUniqueId())) return;

            if (!EnchantUtils.isEventActive(CEnchantments.HARVESTER, player, hoe, enchantments)) return;

            BlockFace blockFace = blocks.get(player.getUniqueId()).get(plant);
            blocks.remove(player.getUniqueId());

            if (!crazyManager.getNMSSupport().isFullyGrown(plant)) return;

            getAreaCrops(player, plant, blockFace).forEach(Block::breakNaturally);
        }
    }

    private void fullyGrowPlant(Block block) {
        crazyManager.getNMSSupport().fullyGrowPlant(block);
        block.getLocation().getWorld().spawnParticle(Particle.VILLAGER_HAPPY, block.getLocation(), 20, .25F, .25F, .25F);
    }

    private boolean plantSeedSuccess(Block soil, Player player, boolean hasGreenThumb) {
        boolean isSoulSand = soil.getType() == Material.SOUL_SAND;
        Material seedType;
        ItemStack playerSeedItem;
        Block plant = soil.getLocation().add(0, 1, 0).getBlock();

        if (plant.isEmpty()) return false;

        seedType = getPlanterSeed(player.getEquipment().getItemInOffHand());
        playerSeedItem = player.getEquipment().getItemInOffHand();

        if (isSoulSand) { // If on soul sand we want it to plant Nether Warts not normal seeds.
            if (playerSeedItem.getType() != Material.NETHER_WART) seedType = null;
        } else {
            if (playerSeedItem.getType() == Material.NETHER_WART) seedType = null;
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

            if (hasGreenThumb) fullyGrowPlant(plant); // TODO re-add enchant check

            return true;
        }

        return false;
    }

    private Material getPlanterSeed(ItemStack item) {
        return item != null ? enchantmentSettings.getPlanterSeed(item.getType()) : null;
    }

    private List<Block> getAreaCrops(Player player, Block block, BlockFace blockFace) {
        List<Block> blockList = new ArrayList<>();

        for (Block crop : getAreaBlocks(block, blockFace, 1)) { // Radius of 1 is 3x3
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
        for (Block soil : getAreaBlocks(block, 1)) {
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

    private Set<Block> getAreaBlocks(Block block, int radius) {
        return getAreaBlocks(block, BlockFace.UP, radius);
    }

    private Set<Block> getAreaBlocks(Block block, BlockFace blockFace, int radius) {
        Location loc = block.getLocation();
        Location loc2 = block.getLocation();

        switch (blockFace) {
            case SOUTH -> {
                loc.add(-radius, radius, -0);
                loc2.add(radius, -radius, 0);
            }

            case WEST -> {
                loc.add(0, radius, -radius);
                loc2.add(0, -radius, radius);
            }

            case EAST -> {
                loc.add(-0, radius, radius);
                loc2.add(0, -radius, -radius);
            }

            case NORTH -> {
                loc.add(radius, radius, 0);
                loc2.add(-radius, -radius, 0);
            }

            case UP -> {
                loc.add(-radius, -0, -radius);
                loc2.add(radius, 0, radius);
            }

            case DOWN -> {
                loc.add(radius, 0, radius);
                loc2.add(-radius, 0, -radius);
            }

            default -> {}
        }

        return methods.getEnchantBlocks(loc, loc2);
    }
}