package com.treasurequest;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class TreasureQuest extends JavaPlugin {
    private final Map<String, Tier> tiers = new LinkedHashMap<>();
    private EventManager events;
    private MapManager maps;
    private CustomManager customs;
    private DataStore data;
    private EconomyHook economy;
    private Menus menus;
    private String prefix = "";

    @Override
    public void onEnable() {
        saveDefaultConfig();
        data = new DataStore(this);
        customs = new CustomManager(this);
        economy = new EconomyHook(this);
        events = new EventManager(this);
        maps = new MapManager(this);
        menus = new Menus(this);
        reloadAll();
        events.startLoops();
        getServer().getPluginManager().registerEvents(new Listeners(this), this);
        TqCommand cmd = new TqCommand(this);
        PluginCommand pc = getCommand("treasure");
        if (pc != null) {
            pc.setExecutor(cmd);
            pc.setTabCompleter(cmd);
        }
        getLogger().info("TreasureQuest activo. Tiers: " + tiers.size() + " | Economía: " + (economy.hasEconomy() ? "Vault" : "niveles de XP"));
    }

    @Override
    public void onDisable() {
        if (events != null) events.shutdown();
        if (data != null) data.save();
    }

    public void reloadAll() {
        reloadConfig();
        prefix = getConfig().getString("messages.prefix", "");
        tiers.clear();
        ConfigurationSection sec = getConfig().getConfigurationSection("tiers");
        if (sec != null) {
            for (String id : sec.getKeys(false)) {
                ConfigurationSection t = sec.getConfigurationSection(id);
                if (t == null) continue;
                try {
                    tiers.put(id.toLowerCase(Locale.ROOT), new Tier(id.toLowerCase(Locale.ROOT), t));
                } catch (Exception ex) {
                    getLogger().warning("Tier inválido '" + id + "': " + ex.getMessage());
                }
            }
        }
        customs.load();
        events.reschedule();
    }

    public String fmt(String s, Object... kv) {
        return Msg.fmt(s.replace("%prefix%", prefix), kv);
    }

    public Component msg(String key, Object... kv) {
        return Msg.c(fmt(getConfig().getString("messages." + key, "<red>Falta el mensaje: " + key), kv));
    }

    public Tier tier(String id) {
        return id == null ? null : tiers.get(id.toLowerCase(Locale.ROOT));
    }

    public Map<String, Tier> tiers() {
        return tiers;
    }

    public World defaultWorld() {
        for (String n : getConfig().getStringList("events.worlds")) {
            World w = Bukkit.getWorld(n);
            if (w != null) return w;
        }
        return Bukkit.getWorlds().get(0);
    }

    public World mapWorld(org.bukkit.entity.Player p) {
        return p.getWorld().getEnvironment() == World.Environment.NORMAL ? p.getWorld() : defaultWorld();
    }

    public EventManager events() { return events; }
    public MapManager maps() { return maps; }
    public CustomManager customs() { return customs; }
    public DataStore data() { return data; }
    public EconomyHook economy() { return economy; }
    public Menus menus() { return menus; }
}
