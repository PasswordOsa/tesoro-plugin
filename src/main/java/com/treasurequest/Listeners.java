package com.treasurequest;

import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class Listeners implements Listener {
    private final TreasureQuest plugin;

    public Listeners(TreasureQuest plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_BLOCK && a != Action.RIGHT_CLICK_AIR) return;
        Player p = e.getPlayer();
        Block b = e.getClickedBlock();
        if (b != null) {
            TreasureEvent ev = plugin.events().at(b);
            if (ev != null) {
                e.setCancelled(true);
                if (ev.isChest(b)) plugin.events().claim(p, ev);
                return;
            }
        }
        MapManager.MapData d = plugin.maps().read(p.getInventory().getItemInMainHand());
        if (d != null) {
            e.setCancelled(true);
            if (plugin.data().isClaimed(d.id())) p.sendMessage(plugin.msg("map-used"));
            else plugin.events().activateMap(p, d);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (plugin.events().at(e.getBlock()) != null) e.setCancelled(true);
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(b -> plugin.events().at(b) != null);
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent e) {
        e.blockList().removeIf(b -> plugin.events().at(b) != null);
    }

    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        if (plugin.events().onMobDeath(e.getEntity())) {
            e.getDrops().clear();
            e.setDroppedExp(0);
        }
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent e) {
        for (Entity en : e.getEntities()) plugin.events().cleanOrphan(en);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (e.getInventory().getHolder() instanceof Menu m) m.click(e);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Menu) e.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder() instanceof LootEditor le) {
            List<ItemStack> list = new ArrayList<>();
            for (ItemStack it : e.getInventory().getContents()) {
                if (it != null && !it.getType().isAir()) list.add(it.clone());
            }
            le.ct.loot = list;
            plugin.customs().save();
            e.getPlayer().sendMessage(plugin.msg("custom-saved", "id", le.ct.id, "n", list.size()));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        plugin.events().forget(e.getPlayer());
    }
}
