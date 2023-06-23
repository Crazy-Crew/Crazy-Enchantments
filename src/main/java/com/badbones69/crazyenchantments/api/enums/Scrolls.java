package com.badbones69.crazyenchantments.api.enums;

import com.badbones69.crazyenchantments.CrazyEnchantments;
import com.badbones69.crazyenchantments.api.FileManager.Files;
import com.badbones69.crazyenchantments.api.objects.ItemBuilder;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public enum Scrolls {
    
    BLACK_SCROLL("Black-Scroll", "BlackScroll", Arrays.asList("b", "black", "blackscroll")),
    WHITE_SCROLL("White-Scroll", "WhiteScroll", Arrays.asList("w", "white", "whitescroll")),
    TRANSMOG_SCROLL("Transmog-Scroll", "TransmogScroll", Arrays.asList("t", "transmog", "transmogscroll"));
    
    private static final HashMap<Scrolls, ItemBuilder> itemBuilderScrolls = new HashMap<>();
    private final String name;
    private final String configName;
    private final List<String> knownNames;
    
    Scrolls(String name, String configName, List<String> knowNames) {
        this.name = name;
        this.knownNames = knowNames;
        this.configName = configName;
    }
    
    public static void loadScrolls() {
        FileConfiguration config = Files.CONFIG.getFile();
        itemBuilderScrolls.clear();

        for (Scrolls scroll : values()) {
            String path = "Settings." + scroll.getConfigName() + ".";
            itemBuilderScrolls.put(scroll, new ItemBuilder()
            .setName(config.getString(path + "Name"))
            .setLore(config.getStringList(path + "Item-Lore"))
            .setMaterial(config.getString(path + "Item"))
            .setGlow(config.getBoolean(path + "Glowing")));
        }
    }
    
    public static Scrolls getFromName(String nameString) {
        for (Scrolls scroll : Scrolls.values()) {
            if (scroll.getKnownNames().contains(nameString.toLowerCase())) return scroll;
        }

        return null;
    }
    
    public String getName() {
        return name;
    }
    
    public List<String> getKnownNames() {
        return knownNames;
    }
    
    public String getConfigName() {
        return configName;
    }

    private static final NamespacedKey scroll = new NamespacedKey(CrazyEnchantments.getPlugin(), "Crazy_Scroll");

    public static Scrolls getFromPDC(ItemStack item) {
        PersistentDataContainer data = item.getItemMeta().getPersistentDataContainer();
        if (!item.hasItemMeta() || !data.has(scroll)) return null;

        return getFromName(data.get(scroll, PersistentDataType.STRING));
    }
    public ItemStack getScroll() {
        ItemStack item = itemBuilderScrolls.get(this).build();
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(scroll, PersistentDataType.STRING, configName);
        item.setItemMeta(meta);
        return item;
    }
    public ItemStack getScroll(int amount) {
        ItemStack item = itemBuilderScrolls.get(this).setAmount(amount).build();
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(scroll, PersistentDataType.STRING, configName);
        item.setItemMeta(meta);
        return item;
    }
}