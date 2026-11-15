package com.treasurequest;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class TqCommand implements TabExecutor {
    private final TreasureQuest plugin;

    public TqCommand(TreasureQuest plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        String sub = a.length == 0 ? "menu" : a[0].toLowerCase(Locale.ROOT);
        boolean admin = s.hasPermission("treasurequest.admin");
        switch (sub) {
            case "menu", "shop", "events", "track", "top" -> {
                if (!s.hasPermission("treasurequest.use")) {
                    s.sendMessage(plugin.msg("no-permission"));
                    return true;
                }
                userCmd(s, sub, a);
            }
            case "start", "stop", "map", "custom", "reload" -> {
                if (!admin) {
                    s.sendMessage(plugin.msg("no-permission"));
                    return true;
                }
                adminCmd(s, sub, a);
            }
            default -> s.sendMessage(plugin.msg("usage", "usage", "/tq [menu|shop|events|track|top" + (admin ? "|start|stop|map|custom|reload" : "") + "]"));
        }
        return true;
    }

    private void userCmd(CommandSender s, String sub, String[] a) {
        if (sub.equals("top")) {
            s.sendMessage(Msg.c("<gradient:#f7971e:#ffd200><bold>Top cazatesoros</bold></gradient>"));
            int i = 1;
            for (Map.Entry<UUID, Integer> e : plugin.data().top(10)) {
                OfflinePlayer op = Bukkit.getOfflinePlayer(e.getKey());
                s.sendMessage(Msg.c("<yellow>" + i++ + ". <white>" + (op.getName() == null ? "?" : op.getName()) + " <gray>- <gold>" + e.getValue()));
            }
            return;
        }
        if (!(s instanceof Player p)) {
            s.sendMessage(plugin.msg("only-player"));
            return;
        }
        switch (sub) {
            case "menu" -> plugin.menus().openMain(p);
            case "shop" -> plugin.menus().openShop(p);
            case "events" -> plugin.menus().openEvents(p);
            default -> {
                if (a.length > 1 && a[1].equalsIgnoreCase("off")) {
                    plugin.events().untrack(p);
                    return;
                }
                TreasureEvent ev = null;
                if (a.length > 1) {
                    try {
                        ev = plugin.events().get(Integer.parseInt(a[1].replace("#", "")));
                    } catch (NumberFormatException ignored) {
                    }
                } else {
                    ev = plugin.events().nearest(p);
                }
                if (ev == null) p.sendMessage(plugin.msg("no-events"));
                else plugin.events().track(p, ev.num);
            }
        }
    }

    private void adminCmd(CommandSender s, String sub, String[] a) {
        switch (sub) {
            case "reload" -> {
                plugin.reloadAll();
                s.sendMessage(plugin.msg("reloaded", "n", plugin.tiers().size()));
            }
            case "start" -> {
                if (a.length < 2) {
                    s.sendMessage(plugin.msg("usage", "usage", "/tq start <tier> [mundo]"));
                    return;
                }
                Tier t = plugin.tier(a[1]);
                if (t == null) {
                    s.sendMessage(plugin.msg("unknown-tier", "tier", a[1]));
                    return;
                }
                World w = a.length > 2 ? Bukkit.getWorld(a[2]) : (s instanceof Player p ? plugin.mapWorld(p) : plugin.defaultWorld());
                if (w == null) w = plugin.defaultWorld();
                plugin.events().startRandom(w, t, null, null, null);
                s.sendMessage(plugin.msg("started", "tier", t.display, "world", w.getName()));
            }
            case "stop" -> {
                if (a.length < 2 || a[1].equalsIgnoreCase("all")) {
                    s.sendMessage(plugin.msg("stopped", "n", plugin.events().stopAll()));
                } else {
                    try {
                        s.sendMessage(plugin.msg("stopped", "n", plugin.events().stop(Integer.parseInt(a[1].replace("#", ""))) ? 1 : 0));
                    } catch (NumberFormatException e) {
                        s.sendMessage(plugin.msg("usage", "usage", "/tq stop <all|número>"));
                    }
                }
            }
            case "map" -> mapCmd(s, a);
            default -> customCmd(s, a);
        }
    }

    private void mapCmd(CommandSender s, String[] a) {
        // /tq map give <jugador> <tier|custom:id>
        if (a.length < 4 || !a[1].equalsIgnoreCase("give")) {
            s.sendMessage(plugin.msg("usage", "usage", "/tq map give <jugador> <tier|custom:id>"));
            return;
        }
        Player target = Bukkit.getPlayer(a[2]);
        if (target == null) {
            s.sendMessage(Msg.c("<red>Jugador no encontrado."));
            return;
        }
        Tier t;
        CustomTreasure ct = null;
        if (a[3].toLowerCase(Locale.ROOT).startsWith("custom:")) {
            String id = a[3].substring(7);
            ct = plugin.customs().get(id);
            if (ct == null) {
                s.sendMessage(plugin.msg("unknown-custom", "id", id));
                return;
            }
            t = plugin.tier(ct.tier);
        } else {
            t = plugin.tier(a[3]);
        }
        if (t == null) {
            s.sendMessage(plugin.msg("unknown-tier", "tier", a[3]));
            return;
        }
        ItemStack map = plugin.maps().create(plugin.mapWorld(target), t, ct);
        for (ItemStack left : target.getInventory().addItem(map).values()) target.getWorld().dropItemNaturally(target.getLocation(), left);
        s.sendMessage(plugin.msg("map-given", "player", target.getName()));
        target.sendMessage(plugin.msg("gift", "tier", ct != null ? ct.display : t.display));
    }

    private void customCmd(CommandSender s, String[] a) {
        String usage = "/tq custom <create|edit|delete|spawn|map|price|tier|display|list> ...";
        if (a.length < 2) {
            s.sendMessage(plugin.msg("usage", "usage", usage));
            return;
        }
        String op = a[1].toLowerCase(Locale.ROOT);
        if (op.equals("list")) {
            s.sendMessage(Msg.c("<gold>Tesoros personalizados: <white>" + String.join(", ", plugin.customs().ids())));
            return;
        }
        if (a.length < 3) {
            s.sendMessage(plugin.msg("usage", "usage", usage));
            return;
        }
        String id = a[2].toLowerCase(Locale.ROOT);
        if (op.equals("create")) {
            if (!id.matches("[a-z0-9_-]+")) {
                s.sendMessage(Msg.c("<red>El id solo puede tener letras minúsculas, números, _ y -"));
                return;
            }
            if (plugin.customs().get(id) != null) {
                s.sendMessage(plugin.msg("custom-exists"));
                return;
            }
            String tid = a.length > 3 ? a[3] : "tier1";
            if (plugin.tier(tid) == null) {
                s.sendMessage(plugin.msg("unknown-tier", "tier", tid));
                return;
            }
            CustomTreasure ct = plugin.customs().create(id, plugin.tier(tid).id);
            s.sendMessage(plugin.msg("custom-created", "id", id));
            if (s instanceof Player p) plugin.menus().openEditor(p, ct);
            return;
        }
        CustomTreasure ct = plugin.customs().get(id);
        if (ct == null) {
            s.sendMessage(plugin.msg("unknown-custom", "id", id));
            return;
        }
        switch (op) {
            case "edit" -> {
                if (s instanceof Player p) plugin.menus().openEditor(p, ct);
                else s.sendMessage(plugin.msg("only-player"));
            }
            case "delete" -> {
                plugin.customs().delete(id);
                s.sendMessage(plugin.msg("custom-deleted", "id", id));
            }
            case "spawn" -> {
                Tier t = plugin.tier(ct.tier);
                if (!(s instanceof Player p)) {
                    s.sendMessage(plugin.msg("only-player"));
                } else if (t == null) {
                    s.sendMessage(plugin.msg("unknown-tier", "tier", ct.tier));
                } else {
                    plugin.events().startHere(p, t, ct);
                }
            }
            case "map" -> {
                String target = a.length > 3 ? a[3] : (s instanceof Player p ? p.getName() : "");
                mapCmd(s, new String[]{"map", "give", target, "custom:" + id});
            }
            case "price" -> {
                try {
                    ct.price = Double.parseDouble(a[3]);
                    plugin.customs().save();
                    s.sendMessage(plugin.msg("custom-updated", "id", id));
                } catch (RuntimeException e) {
                    s.sendMessage(plugin.msg("usage", "usage", "/tq custom price <id> <precio>"));
                }
            }
            case "tier" -> {
                if (a.length < 4 || plugin.tier(a[3]) == null) {
                    s.sendMessage(plugin.msg("unknown-tier", "tier", a.length < 4 ? "?" : a[3]));
                } else {
                    ct.tier = plugin.tier(a[3]).id;
                    plugin.customs().save();
                    s.sendMessage(plugin.msg("custom-updated", "id", id));
                }
            }
            case "display" -> {
                if (a.length < 4) {
                    s.sendMessage(plugin.msg("usage", "usage", "/tq custom display <id> <texto MiniMessage>"));
                } else {
                    ct.display = String.join(" ", Arrays.copyOfRange(a, 3, a.length));
                    plugin.customs().save();
                    s.sendMessage(plugin.msg("custom-updated", "id", id));
                }
            }
            default -> s.sendMessage(plugin.msg("usage", "usage", usage));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        boolean admin = s.hasPermission("treasurequest.admin");
        if (a.length == 1) {
            List<String> l1 = new ArrayList<>(List.of("menu", "shop", "events", "track", "top"));
            if (admin) l1.addAll(List.of("start", "stop", "map", "custom", "reload"));
            return filter(l1, a[0]);
        }
        if (!admin) return List.of();
        String sub = a[0].toLowerCase(Locale.ROOT);
        List<String> tiers = new ArrayList<>(plugin.tiers().keySet());
        switch (sub) {
            case "start":
                if (a.length == 2) return filter(tiers, a[1]);
                if (a.length == 3) return filter(Bukkit.getWorlds().stream().map(World::getName).toList(), a[2]);
                break;
            case "stop":
                if (a.length == 2) return filter(List.of("all"), a[1]);
                break;
            case "map":
                if (a.length == 2) return filter(List.of("give"), a[1]);
                if (a.length == 3) return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), a[2]);
                if (a.length == 4) {
                    List<String> o = new ArrayList<>(tiers);
                    for (String id : plugin.customs().ids()) o.add("custom:" + id);
                    return filter(o, a[3]);
                }
                break;
            case "custom":
                if (a.length == 2) return filter(List.of("create", "edit", "delete", "spawn", "map", "price", "tier", "display", "list"), a[1]);
                if (a.length == 3 && !a[1].equalsIgnoreCase("create") && !a[1].equalsIgnoreCase("list")) return filter(new ArrayList<>(plugin.customs().ids()), a[2]);
                if (a.length == 4 && (a[1].equalsIgnoreCase("create") || a[1].equalsIgnoreCase("tier"))) return filter(tiers, a[3]);
                break;
            default:
                break;
        }
        return List.of();
    }

    private List<String> filter(List<String> src, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String x : src) if (x.toLowerCase(Locale.ROOT).startsWith(p)) out.add(x);
        return out;
    }
}
