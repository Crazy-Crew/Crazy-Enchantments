package com.badbones69.crazyenchantments.api.multisupport.interfaces;

import org.bukkit.block.Block;

public interface CropManager {
    
    void fullyGrowPlant(Block block);
    
    boolean isFullyGrown(Block block);
    
    void hydrateSoil(Block soil);
    
}