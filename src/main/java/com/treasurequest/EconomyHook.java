package com.treasurequest;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

/** Usa Vault si existe; si no, la tienda cobra niveles de experiencia. */
public final class EconomyHook {
    private Economy eco;

    public EconomyHook(TreasureQuest plugin) {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") != null) {
            RegisteredServiceProvider<Economy> rsp = plugin.getServer().getServicesManager().getRegistration(Economy.class);
            if (rsp != null) eco = rsp.getProvider();
        }
    }

    public boolean hasEconomy() {
        return eco != null;
    }

    public boolean charge(Player p, double price) {
        if (eco != null) {
            if (!eco.has(p, price)) return false;
            return eco.withdrawPlayer(p, price).transactionSuccess();
        }
        int lv = (int) price;
        if (p.getLevel() < lv) return false;
        p.setLevel(p.getLevel() - lv);
        return true;
    }

    public void deposit(Player p, double amount) {
        if (eco != null) eco.depositPlayer(p, amount);
    }

    public String format(double v) {
        return eco != null ? eco.format(v) : ((int) v) + " niveles";
    }
}
