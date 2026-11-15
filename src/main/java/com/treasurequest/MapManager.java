package com.treasurequest;

import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class MapManager {
    public record MapData(UUID id, String world, int x, int z, String tier, String custom) {}

    private final TreasureQuest plugin;
    private final NamespacedKey kId, kWorld, kX, kZ, kTier, kCustom;

    public MapManager(TreasureQuest plugin) {
        this.plugin = plugin;
        kId = new NamespacedKey(plugin, "map_id");
        kWorld = new NamespacedKey(plugin, "map_world");
        kX = new NamespacedKey(plugin, "map_x");
        kZ = new NamespacedKey(plugin, "map_z");
        kTier = new NamespacedKey(plugin, "map_tier");
        kCustom = new NamespacedKey(plugin, "map_custom");
    }

    public ItemStack create(World w, Tier t, CustomTreasure ct) {
        int[] pt = plugin.events().randomPoint(w, t.mapMin, t.mapMax);
        String label = ct != null ? ct.display : t.display;
        String[] kv = {"tier", label, "stars", t.stars(), "world", w.getName(), "x", String.valueOf(pt[0]), "z", String.valueOf(pt[1])};

        ItemStack it = new ItemStack(t.mapItem);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Msg.item(plugin.fmt(plugin.getConfig().getString("maps.name", "Mapa %tier%"), (Object[]) kv)));
        List<Component> lore = new ArrayList<>();
        for (String line : plugin.getConfig().getStringList("maps.lore")) {
            lore.add(Msg.item(plugin.fmt(line, (Object[]) kv)));
        }
        meta.lore(lore);
        meta.setEnchantmentGlintOverride(true);
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(kId, PersistentDataType.STRING, UUID.randomUUID().toString());
        pdc.set(kWorld, PersistentDataType.STRING, w.getName());
        pdc.set(kX, PersistentDataType.INTEGER, pt[0]);
        pdc.set(kZ, PersistentDataType.INTEGER, pt[1]);
        pdc.set(kTier, PersistentDataType.STRING, t.id);
        if (ct != null) pdc.set(kCustom, PersistentDataType.STRING, ct.id);
        it.setItemMeta(meta);
        return it;
    }

    public MapData read(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        String id = pdc.get(kId, PersistentDataType.STRING);
        if (id == null) return null;
        Integer x = pdc.get(kX, PersistentDataType.INTEGER);
        Integer z = pdc.get(kZ, PersistentDataType.INTEGER);
        String world = pdc.get(kWorld, PersistentDataType.STRING);
        String tier = pdc.get(kTier, PersistentDataType.STRING);
        if (x == null || z == null || world == null || tier == null) return null;
        try {
            return new MapData(UUID.fromString(id), world, x, z, tier, pdc.get(kCustom, PersistentDataType.STRING));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
