package com.treasurequest;

import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class CustomTreasure {
    public final String id;
    public String display;
    public String tier = "tier1";
    public double price = 0;
    public List<ItemStack> loot = new ArrayList<>();

    public CustomTreasure(String id) {
        this.id = id;
        this.display = "<gold>" + id;
    }
}
