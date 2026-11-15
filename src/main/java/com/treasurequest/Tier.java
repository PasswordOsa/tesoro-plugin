package com.treasurequest;

import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class Tier {
    public record MobSpec(String type, int amount, String name, double health, double damage, String gear) {}

    public record ItemRoll(Material material, int min, int max, double chance) {}

    public final String id;
    public final String display;
    public final BossBar.Color barColor;
    public final int difficulty, durationMin, mapMin, mapMax, moneyMin, moneyMax, xp;
    public final Material chest, pedestal, decor, mapItem;
    public final boolean lockUntilCleared;
    public final List<MobSpec> guardians = new ArrayList<>();
    public final MobSpec boss;
    public final List<ItemRoll> items = new ArrayList<>();
    public final List<String> commands;

    public Tier(String id, ConfigurationSection s) {
        this.id = id;
        this.display = s.getString("display", "<white>" + id);
        BossBar.Color col;
        try {
            col = BossBar.Color.valueOf(s.getString("bar-color", "WHITE").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            col = BossBar.Color.WHITE;
        }
        this.barColor = col;
        this.difficulty = Math.max(1, Math.min(5, s.getInt("difficulty", 1)));
        this.durationMin = Math.max(1, s.getInt("duration-minutes", 20));
        this.mapMin = s.getInt("map-distance.min", 200);
        this.mapMax = Math.max(mapMin + 1, s.getInt("map-distance.max", 1000));
        int[] money = range(String.valueOf(s.get("rewards.money", "0")));
        this.moneyMin = money[0];
        this.moneyMax = money[1];
        this.xp = s.getInt("rewards.xp", 0);
        this.chest = mat(s.getString("chest", "CHEST"), Material.CHEST);
        this.pedestal = mat(s.getString("pedestal", "STONE_BRICKS"), Material.STONE_BRICKS);
        String d = s.getString("decor", "NONE");
        this.decor = d.equalsIgnoreCase("NONE") ? null : mat(d, null);
        this.mapItem = mat(s.getString("map-item", "MAP"), Material.MAP);
        this.lockUntilCleared = s.getBoolean("lock-until-cleared", false);
        ConfigurationSection g = s.getConfigurationSection("guardians");
        if (g != null) {
            for (String k : g.getKeys(false)) {
                ConfigurationSection m = g.getConfigurationSection(k);
                if (m != null) guardians.add(mob(m));
            }
        }
        ConfigurationSection b = s.getConfigurationSection("boss");
        this.boss = b == null ? null : mob(b);
        for (String line : s.getStringList("rewards.items")) {
            ItemRoll r = parseItem(line);
            if (r != null) items.add(r);
        }
        this.commands = s.getStringList("rewards.commands");
    }

    public String stars() {
        return "★".repeat(difficulty) + "☆".repeat(5 - difficulty);
    }

    private static MobSpec mob(ConfigurationSection m) {
        return new MobSpec(m.getString("type", "ZOMBIE"), Math.max(1, m.getInt("amount", 1)),
                m.getString("name", "<red>Guardián"), m.getDouble("health", 20), m.getDouble("damage", 0),
                m.getString("gear", "NONE"));
    }

    private static ItemRoll parseItem(String s) {
        String[] p = s.split(":");
        Material m = Material.matchMaterial(p[0]);
        if (m == null) return null;
        int[] r = p.length > 1 ? range(p[1]) : new int[]{1, 1};
        double ch = p.length > 2 ? Double.parseDouble(p[2].trim()) : 100;
        return new ItemRoll(m, r[0], r[1], ch);
    }

    static int[] range(String s) {
        int a, b;
        if (s.contains("-")) {
            String[] x = s.split("-");
            a = Integer.parseInt(x[0].trim());
            b = Integer.parseInt(x[1].trim());
        } else {
            a = b = Integer.parseInt(s.trim());
        }
        return b < a ? new int[]{b, a} : new int[]{a, b};
    }

    private static Material mat(String name, Material def) {
        Material m = Material.matchMaterial(name);
        return m == null ? def : m;
    }
}
