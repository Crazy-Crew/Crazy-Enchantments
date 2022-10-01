package com.badbones69.crazyenchantments.enchantments;

import com.badbones69.crazyenchantments.Methods;
import com.badbones69.crazyenchantments.api.CrazyManager;
import com.badbones69.crazyenchantments.api.FileManager.Files;
import com.badbones69.crazyenchantments.api.PluginSupport;
import com.badbones69.crazyenchantments.api.PluginSupport.SupportedPlugins;
import com.badbones69.crazyenchantments.api.enums.ArmorType;
import com.badbones69.crazyenchantments.api.enums.CEnchantments;
import com.badbones69.crazyenchantments.api.events.ArmorEquipEvent;
import com.badbones69.crazyenchantments.api.events.AuraActiveEvent;
import com.badbones69.crazyenchantments.api.events.EnchantmentUseEvent;
import com.badbones69.crazyenchantments.api.multisupport.anticheats.NoCheatPlusSupport;
import com.badbones69.crazyenchantments.api.multisupport.anticheats.SpartanSupport;
import com.badbones69.crazyenchantments.api.objects.ArmorEnchantment;
import com.badbones69.crazyenchantments.api.objects.PotionEffects;
import com.badbones69.crazyenchantments.controllers.ProtectionCrystal;
import com.badbones69.crazyenchantments.processors.ArmorMoveProcessor;
import com.badbones69.crazyenchantments.processors.Processor;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;
import java.util.Map.Entry;

public class Armor implements Listener {

    private final List<Player> fall = new ArrayList<>();
    private final HashMap<Player, HashMap<CEnchantments, Calendar>> timer = new HashMap<>();
    private final CrazyManager crazyManager = CrazyManager.getInstance();
    private final PluginSupport pluginSupport = PluginSupport.INSTANCE;
    private final Processor<PlayerMoveEvent> armorMoveProcessor = new ArmorMoveProcessor();

    public Armor() {
        armorMoveProcessor.start();
    }

    public void stop() {
        armorMoveProcessor.stop();
    }

    @EventHandler
    public void onEquip(ArmorEquipEvent event) {
        Player player = event.getPlayer();
        ItemStack newItem = event.getNewArmorPiece();
        ItemStack oldItem = event.getOldArmorPiece();

        if (crazyManager.hasEnchantments(oldItem)) { // Removing the potion effects.
            for (CEnchantments enchantment : crazyManager.getEnchantmentPotions().keySet()) {
                if (enchantment.isActivated() && crazyManager.hasEnchantment(oldItem, enchantment.getEnchantment())) {
                    Map<PotionEffectType, Integer> effects = crazyManager.getUpdatedEffects(player, new ItemStack(Material.AIR), oldItem, enchantment);
                    updatePlayerEfefcts(effects, player);
                }
            }
        }

        if (crazyManager.hasEnchantments(newItem)) { // Adding the potion effects.
            for (CEnchantments enchantment : crazyManager.getEnchantmentPotions().keySet()) {
                if (enchantment.isActivated() && crazyManager.hasEnchantment(newItem, enchantment.getEnchantment())) {
                    Map<PotionEffectType, Integer> effects = crazyManager.getUpdatedEffects(player, newItem, oldItem, enchantment);
                    updatePlayerEfefcts(effects, player);
                }
            }
        }
    }

    private void updatePlayerEfefcts(Map<PotionEffectType, Integer> effects, Player player) {
        for (Entry<PotionEffectType, Integer> type : effects.entrySet()) {
            if (type.getValue() < 0) {
                player.removePotionEffect(type.getKey());
            } else {
                player.removePotionEffect(type.getKey());
                player.addPotionEffect(new PotionEffect(type.getKey(), Integer.MAX_VALUE, type.getValue()));
            }
        }
    }

    // Ryder start
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onArmorRightClickEquip(PlayerInteractEvent event) {
        if (!(event.getAction() == Action.RIGHT_CLICK_AIR) || !(event.getAction() == Action.RIGHT_CLICK_BLOCK)) return;

        Player player = event.getPlayer();

        ItemStack itemStack = player.getInventory().getItemInMainHand();

        runEquip(player, ArmorType.matchType(itemStack), new ItemStack(Material.AIR), itemStack);
    }

    private void runEquip(Player player, ArmorType armorType, ItemStack itemStack, ItemStack activeItem) {
        ArmorEquipEvent armorEquipEvent = new ArmorEquipEvent(player, ArmorEquipEvent.EquipMethod.HOTBAR, armorType, itemStack, activeItem);
        player.getServer().getPluginManager().callEvent(armorEquipEvent);
    }
    // Ryder end

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        if (crazyManager.isIgnoredEvent(event) || crazyManager.isIgnoredUUID(event.getDamager().getUniqueId())) return;
        if (pluginSupport.isFriendly(event.getDamager(), event.getEntity())) return;

        if (event.getDamager() instanceof final LivingEntity damager && event.getEntity() instanceof final Player player) {
            for (ItemStack armor : player.getEquipment().getArmorContents()) {
                if (crazyManager.hasEnchantments(armor)) {
                    for (ArmorEnchantment armorEnchantment : crazyManager.getArmorManager().getArmorEnchantments()) {
                        CEnchantments enchantment = armorEnchantment.getEnchantment();

                        if (crazyManager.hasEnchantment(armor, enchantment) && enchantment.chanceSuccessful(armor)) {
                            EnchantmentUseEvent useEvent = new EnchantmentUseEvent(player, enchantment, armor);
                            crazyManager.getPlugin().getServer().getPluginManager().callEvent(useEvent);

                            if (!useEvent.isCancelled()) {
                                if (armorEnchantment.isPotionEnchantment()) {
                                    for (PotionEffects effect : armorEnchantment.getPotionEffects()) {
                                        damager.addPotionEffect(new PotionEffect(effect.getPotionEffect(), effect.getDuration(), (armorEnchantment.isLevelAddedToAmplifier() ? crazyManager.getLevel(armor, enchantment) : 0) + effect.getAmplifier()));
                                    }
                                } else {
                                    event.setDamage(event.getDamage() * ((armorEnchantment.isLevelAddedToAmplifier() ? crazyManager.getLevel(armor, enchantment) : 0) + armorEnchantment.getDamageAmplifier()));
                                }
                            }
                        }
                    }

                    if (player.getHealth() <= 8 && crazyManager.hasEnchantment(armor, CEnchantments.ROCKET.getEnchantment()) && CEnchantments.ROCKET.chanceSuccessful(armor)) {
                        EnchantmentUseEvent useEvent = new EnchantmentUseEvent(player, CEnchantments.ROCKET.getEnchantment(), armor);
                        crazyManager.getPlugin().getServer().getPluginManager().callEvent(useEvent);

                        // Anti cheat support here with AAC or any others.

                        if (!useEvent.isCancelled()) {
                            Bukkit.getScheduler().runTaskLater(crazyManager.getPlugin(), () -> player.setVelocity(player.getLocation().toVector().subtract(damager.getLocation().toVector()).normalize().setY(1)), 1);

                            fall.add(player);
                            player.getWorld().spawnParticle(Particle.EXPLOSION_HUGE, player.getLocation(), 1);

                            Bukkit.getScheduler().runTaskLater(crazyManager.getPlugin(), () -> {
                                fall.remove(player);
                            }, 8 * 20);

                        }
                    }

                    if (crazyManager.hasEnchantment(armor, CEnchantments.ENLIGHTENED) && CEnchantments.ENLIGHTENED.chanceSuccessful(armor) && player.getHealth() > 0) {
                        EnchantmentUseEvent useEvent = new EnchantmentUseEvent(player, CEnchantments.ENLIGHTENED.getEnchantment(), armor);
                        crazyManager.getPlugin().getServer().getPluginManager().callEvent(useEvent);

                        if (!useEvent.isCancelled()) {
                            double heal = crazyManager.getLevel(armor, CEnchantments.ENLIGHTENED);
                            // Uses getValue as if the player has health boost it is modifying the base so the value after the modifier is needed.
                            double maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();

                            if (player.getHealth() + heal < maxHealth) {
                                player.setHealth(player.getHealth() + heal);
                            }

                            if (player.getHealth() + heal >= maxHealth) {
                                player.setHealth(maxHealth);
                            }
                        }
                    }

                    if (crazyManager.hasEnchantment(armor, CEnchantments.INSOMNIA)) {
                        EnchantmentUseEvent useEvent = new EnchantmentUseEvent(player, CEnchantments.INSOMNIA.getEnchantment(), armor);
                        crazyManager.getPlugin().getServer().getPluginManager().callEvent(useEvent);

                        if (!useEvent.isCancelled()) {
                            double damage = crazyManager.getLevel(armor, CEnchantments.INSOMNIA);

                            damager.damage(event.getDamage() + damage);
                        }
                    }

                    if (crazyManager.hasEnchantment(armor, CEnchantments.MOLTEN) && CEnchantments.MOLTEN.chanceSuccessful(armor)) {
                        EnchantmentUseEvent useEvent = new EnchantmentUseEvent(player, CEnchantments.MOLTEN.getEnchantment(), armor);
                        crazyManager.getPlugin().getServer().getPluginManager().callEvent(useEvent);

                        if (!useEvent.isCancelled()) {
                            damager.setFireTicks((crazyManager.getLevel(armor, CEnchantments.MOLTEN) * 2) * 20);
                        }
                    }

                    if (crazyManager.hasEnchantment(armor, CEnchantments.SAVIOR) && CEnchantments.SAVIOR.chanceSuccessful(armor)) {
                        EnchantmentUseEvent useEvent = new EnchantmentUseEvent(player, CEnchantments.SAVIOR.getEnchantment(), armor);
                        crazyManager.getPlugin().getServer().getPluginManager().callEvent(useEvent);

                        if (!useEvent.isCancelled()) {
                            event.setDamage(event.getDamage() / 2);
                        }
                    }

                    if (crazyManager.hasEnchantment(armor, CEnchantments.CACTUS) && CEnchantments.CACTUS.chanceSuccessful(armor)) {
                        EnchantmentUseEvent useEvent = new EnchantmentUseEvent(player, CEnchantments.CACTUS.getEnchantment(), armor);
                        crazyManager.getPlugin().getServer().getPluginManager().callEvent(useEvent);

                        if (!useEvent.isCancelled()) {
                            damager.damage(crazyManager.getLevel(armor, CEnchantments.CACTUS));
                        }
                    }

                    if (crazyManager.hasEnchantment(armor, CEnchantments.STORMCALLER) && CEnchantments.STORMCALLER.chanceSuccessful(armor)) {
                        EnchantmentUseEvent useEvent = new EnchantmentUseEvent(player, CEnchantments.STORMCALLER.getEnchantment(), armor);
                        crazyManager.getPlugin().getServer().getPluginManager().callEvent(useEvent);

                        if (!useEvent.isCancelled()) {
                            Location loc = damager.getLocation();
                            loc.getWorld().spigot().strikeLightningEffect(loc, true);
                            int lightningSoundRange = Files.CONFIG.getFile().getInt("Settings.EnchantmentOptions.Lightning-Sound-Range", 160);

                            try {
                                loc.getWorld().playSound(loc, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, (float) lightningSoundRange / 16f, 1);
                            } catch (Exception ignore) {
                            }

                            // AntiCheat Support.

                            if (SupportedPlugins.NO_CHEAT_PLUS.isPluginLoaded()) {
                                NoCheatPlusSupport.allowPlayer(player);
                            }

                            if (SupportedPlugins.SPARTAN.isPluginLoaded()) {
                                SpartanSupport.cancelNoSwing(player);
                            }

                            for (LivingEntity en : Methods.getNearbyLivingEntities(loc, 2D, player)) {
                                EntityDamageByEntityEvent damageByEntityEvent = new EntityDamageByEntityEvent(player, en, DamageCause.CUSTOM, 5D);
                                crazyManager.addIgnoredEvent(damageByEntityEvent);
                                crazyManager.addIgnoredUUID(player.getUniqueId());
                                crazyManager.getPlugin().getServer().getPluginManager().callEvent(damageByEntityEvent);

                                if (!damageByEntityEvent.isCancelled() && pluginSupport.allowsCombat(en.getLocation()) && !pluginSupport.isFriendly(player, en)) {
                                    en.damage(5D);
                                }

                                crazyManager.removeIgnoredEvent(damageByEntityEvent);
                                crazyManager.removeIgnoredUUID(player.getUniqueId());
                            }

                            if (SupportedPlugins.NO_CHEAT_PLUS.isPluginLoaded()) {
                                NoCheatPlusSupport.denyPlayer(player);
                            }

                            damager.damage(5D);
                        }
                    }
                }
            }

            if (damager instanceof Player) {
                for (ItemStack armor : Objects.requireNonNull(damager.getEquipment()).getArmorContents()) {
                    if (crazyManager.hasEnchantment(armor, CEnchantments.LEADERSHIP) && CEnchantments.LEADERSHIP.chanceSuccessful(armor) && (PluginSupport.SupportedPlugins.FACTIONS_UUID.isPluginLoaded())) {
                        int radius = 4 + crazyManager.getLevel(armor, CEnchantments.LEADERSHIP);
                        int players = 0;

                        for (Entity entity : damager.getNearbyEntities(radius, radius, radius)) {
                            if (!(entity instanceof Player other)) continue;
                            if (pluginSupport.isFriendly(damager, other)) {
                                players++;
                            }
                        }

                        if (players > 0) {
                            EnchantmentUseEvent useEvent = new EnchantmentUseEvent((Player) damager, CEnchantments.LEADERSHIP.getEnchantment(), armor);
                            crazyManager.getPlugin().getServer().getPluginManager().callEvent(useEvent);

                            if (!useEvent.isCancelled()) {
                                event.setDamage(event.getDamage() + (players / 2d));
                            }
                        }
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onAura(AuraActiveEvent event) {
        Player player = event.getPlayer();
        Player other = event.getOther();

        if (!player.canSee(other) || !other.canSee(player)) return;
        if (pluginSupport.isVanished(player) || pluginSupport.isVanished(other)) return;

        CEnchantments enchant = event.getEnchantment();
        int level = event.getLevel();

        if (pluginSupport.allowsCombat(other.getLocation()) && !pluginSupport.isFriendly(player, other) && !Methods.hasPermission(other, "bypass.aura", false)) {
            Calendar cal = Calendar.getInstance();
            HashMap<CEnchantments, Calendar> effect = new HashMap<>();

            if (timer.containsKey(other)) {
                effect = timer.get(other);
            }

            HashMap<CEnchantments, Calendar> finalEffect = effect;

            switch (enchant) {
                case BLIZZARD:
                    if (CEnchantments.BLIZZARD.isActivated()) {
                        other.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 5 * 20, level - 1));
                    }
                    break;
                case INTIMIDATE:
                    if (CEnchantments.INTIMIDATE.isActivated()) {
                        other.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 3 * 20, level - 1));
                    }
                    break;
                case ACIDRAIN:
                    if (CEnchantments.ACIDRAIN.isActivated() && (!timer.containsKey(other) ||
                            (timer.containsKey(other) && !timer.get(other).containsKey(enchant)) ||
                            (timer.containsKey(other) && timer.get(other).containsKey(enchant) &&
                                    cal.after(timer.get(other).get(enchant))
                                    && CEnchantments.ACIDRAIN.chanceSuccessful()))) {
                        other.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 4 * 20, 1));
                        int time = 35 - (level * 5);
                        cal.add(Calendar.SECOND, time > 0 ? time : 5);
                        finalEffect.put(enchant, cal);
                    }
                    break;
                case SANDSTORM:
                    if (CEnchantments.SANDSTORM.isActivated() && (!timer.containsKey(other) ||
                            (timer.containsKey(other) && !timer.get(other).containsKey(enchant)) ||
                            (timer.containsKey(other) && timer.get(other).containsKey(enchant) &&
                                    cal.after(timer.get(other).get(enchant))
                                    && CEnchantments.SANDSTORM.chanceSuccessful()))) {
                        other.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 10 * 20, 0));
                        int time = 35 - (level * 5);
                        cal.add(Calendar.SECOND, time > 0 ? time : 5);
                        finalEffect.put(enchant, cal);
                    }
                    break;
                case RADIANT:
                    if (CEnchantments.RADIANT.isActivated() && (!timer.containsKey(other) ||
                            (timer.containsKey(other) && !timer.get(other).containsKey(enchant)) ||
                            (timer.containsKey(other) && timer.get(other).containsKey(enchant) &&
                                    cal.after(timer.get(other).get(enchant))
                                    && CEnchantments.RADIANT.chanceSuccessful()))) {
                        other.setFireTicks(5 * 20);
                        int time = 20 - (level * 5);
                        cal.add(Calendar.SECOND, Math.max(time, 0));
                        finalEffect.put(enchant, cal);
                    }
                    break;
                default:
                    break;
            }

            timer.put(other, effect);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onMovement(PlayerMoveEvent event) {
        if (!event.hasChangedBlock()) return;
        armorMoveProcessor.add(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent e) {
        Player player = e.getEntity();

        if (player.getKiller() == null) return;

        Player killer = player.getKiller();

        if (!pluginSupport.allowsCombat(player.getLocation())) return;

        if (CEnchantments.SELFDESTRUCT.isActivated()) {
            for (ItemStack item : Objects.requireNonNull(player.getEquipment()).getArmorContents()) {
                if (crazyManager.hasEnchantments(item) && crazyManager.hasEnchantment(item, CEnchantments.SELFDESTRUCT.getEnchantment())) {

                    EnchantmentUseEvent useEvent = new EnchantmentUseEvent(player, CEnchantments.SELFDESTRUCT.getEnchantment(), item);
                    crazyManager.getPlugin().getServer().getPluginManager().callEvent(useEvent);

                    if (!useEvent.isCancelled()) {
                        Methods.explode(player);
                        List<ItemStack> items = new ArrayList<>();

                        for (ItemStack drop : e.getDrops()) {
                            if (drop != null && ProtectionCrystal.isProtected(drop) && ProtectionCrystal.isProtectionSuccessful(player)) {
                                items.add(drop);
                            }
                        }

                        e.getDrops().clear();
                        e.getDrops().addAll(items);
                    }
                }
            }
        }

        if (CEnchantments.RECOVER.isActivated()) {
            for (ItemStack item : Objects.requireNonNull(killer.getEquipment()).getArmorContents()) {
                if (crazyManager.hasEnchantments(item) && crazyManager.hasEnchantment(item, CEnchantments.RECOVER)) {
                    EnchantmentUseEvent useEvent = new EnchantmentUseEvent(player, CEnchantments.RECOVER.getEnchantment(), item);
                    crazyManager.getPlugin().getServer().getPluginManager().callEvent(useEvent);

                    if (!useEvent.isCancelled()) {
                        killer.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 8 * 20, 2));
                        killer.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 5 * 20, 1));
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerFallDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!fall.contains(player)) return;
        if (!DamageCause.FALL.equals(event.getCause())) return;
        event.setCancelled(true);
    }
}