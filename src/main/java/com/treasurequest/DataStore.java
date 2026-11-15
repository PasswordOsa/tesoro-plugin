package com.treasurequest;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class DataStore {
    private final TreasureQuest plugin;
    private final File file;
    private final Set<String> claimed = new HashSet<>();
    private final Map<UUID, Integer> found = new HashMap<>();

    public DataStore(TreasureQuest plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        claimed.addAll(y.getStringList("claimed"));
        ConfigurationSection s = y.getConfigurationSection("found");
        if (s != null) {
            for (String k : s.getKeys(false)) {
                try {
                    found.put(UUID.fromString(k), s.getInt(k));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
    }

    public int found(UUID u) {
        return found.getOrDefault(u, 0);
    }

    public void addFound(UUID u) {
        found.merge(u, 1, Integer::sum);
        save();
    }

    public boolean isClaimed(UUID map) {
        return claimed.contains(map.toString());
    }

    public void claim(UUID map) {
        claimed.add(map.toString());
        save();
    }

    public List<Map.Entry<UUID, Integer>> top(int n) {
        List<Map.Entry<UUID, Integer>> l = new ArrayList<>(found.entrySet());
        l.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        return l.subList(0, Math.min(n, l.size()));
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("claimed", new ArrayList<>(claimed));
        for (Map.Entry<UUID, Integer> e : found.entrySet()) y.set("found." + e.getKey(), e.getValue());
        try {
            y.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("No se pudo guardar data.yml: " + e.getMessage());
        }
    }
}
