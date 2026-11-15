package com.treasurequest;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CustomManager {
    private final TreasureQuest plugin;
    private final File file;
    private final Map<String, CustomTreasure> map = new LinkedHashMap<>();

    public CustomManager(TreasureQuest plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "customs.yml");
    }

    public void load() {
        map.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        for (String id : y.getKeys(false)) {
            ConfigurationSection s = y.getConfigurationSection(id);
            if (s == null) continue;
            CustomTreasure c = new CustomTreasure(id);
            c.display = s.getString("display", "<gold>" + id);
            c.tier = s.getString("tier", "tier1");
            c.price = s.getDouble("price", 0);
            List<?> l = s.getList("loot");
            if (l != null) {
                for (Object o : l) {
                    if (o instanceof ItemStack is) c.loot.add(is);
                }
            }
            map.put(id, c);
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (CustomTreasure c : map.values()) {
            y.set(c.id + ".display", c.display);
            y.set(c.id + ".tier", c.tier);
            y.set(c.id + ".price", c.price);
            y.set(c.id + ".loot", c.loot);
        }
        try {
            y.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("No se pudo guardar customs.yml: " + e.getMessage());
        }
    }

    public CustomTreasure get(String id) {
        return id == null ? null : map.get(id.toLowerCase(Locale.ROOT));
    }

    public CustomTreasure create(String id, String tier) {
        CustomTreasure c = new CustomTreasure(id.toLowerCase(Locale.ROOT));
        c.tier = tier;
        map.put(c.id, c);
        save();
        return c;
    }

    public boolean delete(String id) {
        boolean r = map.remove(id.toLowerCase(Locale.ROOT)) != null;
        if (r) save();
        return r;
    }

    public Collection<CustomTreasure> all() {
        return map.values();
    }

    public Collection<String> ids() {
        return map.keySet();
    }
}
