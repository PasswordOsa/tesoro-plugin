package com.treasurequest;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public final class LootEditor implements InventoryHolder {
    public final CustomTreasure ct;
    private final Inventory inv;

    public LootEditor(TreasureQuest plugin, CustomTreasure ct) {
        this.ct = ct;
        this.inv = Bukkit.createInventory(this, 54, plugin.msg("menu-editor-title", "id", ct.id));
        for (ItemStack s : ct.loot) {
            if (inv.firstEmpty() >= 0) inv.addItem(s.clone());
        }
    }

    public void open(Player p) {
        p.openInventory(inv);
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }
}
