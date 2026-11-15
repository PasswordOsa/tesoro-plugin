package com.treasurequest;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class Menus {
    public record ShopEntry(String name, Material icon, Tier tier, CustomTreasure custom, double price) {}

    private static final int[] GRID = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};

    private final TreasureQuest plugin;

    public Menus(TreasureQuest plugin) {
        this.plugin = plugin;
    }

    private Menu base(String titleKey, int rows) {
        Menu m = new Menu(plugin.msg(titleKey), rows);
        m.fill(Items.pane(Material.BLACK_STAINED_GLASS_PANE));
        ItemStack acc = Items.pane(Material.ORANGE_STAINED_GLASS_PANE);
        int size = rows * 9;
        for (int c : new int[]{0, 1, 7, 8, size - 9, size - 8, size - 2, size - 1}) m.set(c, acc, null);
        return m;
    }

    private void back(Menu m, Player p, int slot) {
        m.set(slot, Items.of(Material.ARROW, "<gray>« Volver"), e -> openMain(p));
    }

    private void give(Player p, ItemStack it) {
        for (ItemStack left : p.getInventory().addItem(it).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
    }

    // ───────────── menú principal ─────────────

    public void openMain(Player p) {
        Menu m = base("menu-main-title", 5);
        int found = plugin.data().found(p.getUniqueId());
        m.set(4, Items.of(Material.NETHER_STAR, "<gradient:#f7971e:#ffd200><bold>TreasureQuest",
                "<gray>Jugador: <white>" + p.getName(), "<gray>Tesoros encontrados: <yellow>" + found), null);
        m.set(20, Items.of(Material.RECOVERY_COMPASS, "<aqua><bold>Eventos activos",
                "<gray>Tesoros en el mapa: <white>" + plugin.events().visibleTo(p).size(), "", "<yellow>▶ Clic para ver"),
                e -> openEvents(p));
        m.set(22, Items.of(Material.EMERALD, "<green><bold>Tienda de mapas",
                "<gray>Compra mapas del tesoro", "", "<yellow>▶ Clic para abrir"), e -> openShop(p));
        m.set(24, Items.of(Material.WRITTEN_BOOK, "<gold><bold>Mis estadísticas",
                "<gray>Tesoros encontrados: <yellow>" + found, "<gray>Usa <white>/tq top <gray>para el ranking"), null);
        m.set(31, Items.of(Material.BARRIER, "<red>Detener rastreo"), e -> {
            plugin.events().untrack(p);
            p.closeInventory();
        });
        if (p.hasPermission("treasurequest.admin")) {
            m.set(40, Items.of(Material.COMMAND_BLOCK, "<light_purple><bold>Panel de administración",
                    "<gray>Eventos, mapas y cofres personalizados"), e -> openAdmin(p));
        }
        m.open(p);
    }

    // ───────────── eventos activos ─────────────

    public void openEvents(Player p) {
        Menu m = base("menu-events-title", 6);
        List<TreasureEvent> list = plugin.events().visibleTo(p);
        if (list.isEmpty()) {
            m.set(22, Items.of(Material.BARRIER, "<red>No hay tesoros activos", "<gray>Espera el próximo evento..."), null);
        }
        for (int i = 0; i < list.size() && i < GRID.length; i++) {
            TreasureEvent ev = list.get(i);
            String label = ev.custom != null ? ev.custom.display : ev.tier.display;
            m.set(GRID[i], Items.of(ev.tier.chest, label + " <dark_gray>#" + ev.num,
                    "<gray>Dificultad: <yellow>" + ev.tier.stars(),
                    "<gray>Mundo: <white>" + ev.world.getName(),
                    "<gray>Coordenadas: <yellow>" + ev.x + ", " + ev.y + ", " + ev.z,
                    "<gray>Tiempo: <white>" + plugin.events().timeLeft(ev),
                    "<gray>Guardianes: <red>" + ev.mobs.size(), "", "<green>▶ Clic para rastrear"),
                    e -> {
                        plugin.events().track(p, ev.num);
                        p.closeInventory();
                    });
        }
        back(m, p, 49);
        m.open(p);
    }

    // ───────────── tienda ─────────────

    public List<ShopEntry> shopEntries() {
        List<ShopEntry> out = new ArrayList<>();
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("shop.items");
        if (sec != null) {
            for (String k : sec.getKeys(false)) {
                ConfigurationSection s = sec.getConfigurationSection(k);
                if (s == null) continue;
                CustomTreasure ct = s.contains("custom") ? plugin.customs().get(s.getString("custom", "")) : null;
                Tier t = plugin.tier(ct != null ? ct.tier : s.getString("tier", ""));
                if (t == null) continue;
                Material icon = Material.matchMaterial(s.getString("icon", "MAP"));
                if (icon == null) icon = Material.MAP;
                out.add(new ShopEntry(s.getString("name", ct != null ? ct.display : t.display), icon, t, ct, s.getDouble("price", 100)));
            }
        }
        for (CustomTreasure ct : plugin.customs().all()) {
            Tier t = plugin.tier(ct.tier);
            if (ct.price > 0 && t != null) out.add(new ShopEntry(ct.display, t.mapItem, t, ct, ct.price));
        }
        return out;
    }

    public void openShop(Player p) {
        Menu m = base("menu-shop-title", 5);
        List<ShopEntry> entries = shopEntries();
        for (int i = 0; i < entries.size() && i < GRID.length; i++) {
            ShopEntry en = entries.get(i);
            m.set(GRID[i], Items.of(en.icon(), en.name(),
                    "<gray>Dificultad: <yellow>" + en.tier().stars(),
                    "<gray>Cofre: " + en.tier().display,
                    "<gray>Precio: <gold>" + plugin.economy().format(en.price()), "", "<green>▶ Clic para comprar"),
                    e -> buy(p, en));
        }
        back(m, p, 40);
        m.open(p);
    }

    private void buy(Player p, ShopEntry en) {
        if (!plugin.economy().charge(p, en.price())) {
            p.sendMessage(plugin.msg("shop-no-money", "price", plugin.economy().format(en.price())));
            return;
        }
        give(p, plugin.maps().create(plugin.mapWorld(p), en.tier(), en.custom()));
        p.sendMessage(plugin.msg("shop-bought", "name", en.name(), "price", plugin.economy().format(en.price())));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
    }

    // ───────────── administración ─────────────

    public void openAdmin(Player p) {
        if (!p.hasPermission("treasurequest.admin")) return;
        Menu m = base("menu-admin-title", 6);
        int i = 0;
        for (Tier t : plugin.tiers().values()) {
            if (i >= 7) break;
            m.set(10 + i++, Items.of(t.chest, t.display, "<gray>Dificultad: <yellow>" + t.stars(), "",
                    "<yellow>Clic izq: <white>evento aleatorio", "<yellow>Clic der: <white>darte un mapa",
                    "<yellow>Shift+clic: <white>aparecer aquí"), e -> {
                p.closeInventory();
                if (e.getClick().isShiftClick()) plugin.events().startHere(p, t, null);
                else if (e.getClick().isRightClick()) give(p, plugin.maps().create(plugin.mapWorld(p), t, null));
                else plugin.events().startRandom(plugin.mapWorld(p), t, null, null, null);
            });
        }
        int j = 0;
        for (CustomTreasure ct : plugin.customs().all()) {
            if (j >= 14) break;
            int slot = j < 7 ? 28 + j : 37 + (j - 7);
            j++;
            m.set(slot, Items.of(Material.CHEST, ct.display + " <dark_gray>(" + ct.id + ")",
                    "<gray>Tier: " + ct.tier, "<gray>Items: <white>" + ct.loot.size(),
                    "<gray>Precio tienda: <gold>" + (ct.price > 0 ? ct.price : "no"), "",
                    "<yellow>Clic izq: <white>aparecer aquí", "<yellow>Clic der: <white>darte un mapa",
                    "<yellow>Shift+clic: <white>editar items"), e -> {
                p.closeInventory();
                Tier t = plugin.tier(ct.tier);
                if (e.getClick().isShiftClick()) openEditor(p, ct);
                else if (t == null) p.sendMessage(plugin.msg("unknown-tier", "tier", ct.tier));
                else if (e.getClick().isRightClick()) give(p, plugin.maps().create(plugin.mapWorld(p), t, ct));
                else plugin.events().startHere(p, t, ct);
            });
        }
        back(m, p, 49);
        m.open(p);
    }

    public void openEditor(Player p, CustomTreasure ct) {
        new LootEditor(plugin, ct).open(p);
    }
}
