package com.badbones69.crazyenchantments.paper.api.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public class AngelUseEvent extends Event implements Cancellable {
    
    private static final HandlerList handlers = new HandlerList();
    private final Player player;
    private final ItemStack item;
    private boolean cancel;
    
    public AngelUseEvent(Player player, ItemStack item) {
        this.player = player;
        this.item = item;
        this.cancel = false;
    }
    
    /**
     * @return The player that uses the enchantment.
     */
    public Player getPlayer() {
        return player;
    }
    
    /**
     * @return The item the enchantment is on.
     */
    public ItemStack getItem() {
        return item;
    }
    
    @Override
    public boolean isCancelled() {
        return cancel;
    }
    
    @Override
    public void setCancelled(boolean cancel) {
        this.cancel = cancel;
    }

    /**
     * Gets a list of handlers handling this event.
     *
     * @return A list of handlers handling this event.
     */
    @Override
    public @NotNull HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}