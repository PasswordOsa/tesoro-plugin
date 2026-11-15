package com.treasurequest;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public final class EventManager {
    private static final String[] ARROWS = {"⬆", "↗", "➡", "↘", "⬇", "↙", "⬅", "↖"};

    private final TreasureQuest plugin;
    private final Random rnd = new Random();
    private final Map<Integer, TreasureEvent> events = new LinkedHashMap<>();
    private final Map<String, TreasureEvent> blocks = new HashMap<>();
    private final Map<UUID, Integer> tracking = new HashMap<>();
    private final Set<UUID> activating = new HashSet<>();
    private final List<Runnable> pending = new ArrayList<>();
    private final NamespacedKey mobKey;
    private int counter = 0;
    private BukkitTask loop, trackLoop, scheduler, gifts;

    public EventManager(TreasureQuest plugin) {
        this.plugin = plugin;
        this.mobKey = new NamespacedKey(plugin, "event");
    }

    // ───────────── ciclo de vida ─────────────

    public void startLoops() {
        loop = Bukkit.getScheduler().runTaskTimer(plugin, this::tickSecond, 20L, 20L);
        trackLoop = Bukkit.getScheduler().runTaskTimer(plugin, this::tickTrack, 10L, 10L);
    }

    public void shutdown() {
        if (loop != null) loop.cancel();
        if (trackLoop != null) trackLoop.cancel();
        if (scheduler != null) scheduler.cancel();
        if (gifts != null) gifts.cancel();
        for (TreasureEvent ev : new ArrayList<>(events.values())) end(ev, 0);
        for (Runnable r : new ArrayList<>(pending)) r.run();
        pending.clear();
    }

    public void reschedule() {
        if (scheduler != null) scheduler.cancel();
        if (gifts != null) gifts.cancel();
        FileConfiguration c = plugin.getConfig();
        if (c.getBoolean("events.enabled", true)) scheduleNext();
        if (c.getBoolean("random-gifts.enabled", true)) {
            long t = Math.max(1, c.getLong("random-gifts.interval-minutes", 45)) * 1200L;
            gifts = Bukkit.getScheduler().runTaskTimer(plugin, this::giftTick, t, t);
        }
    }

    private void scheduleNext() {
        FileConfiguration c = plugin.getConfig();
        int min = Math.max(1, c.getInt("events.min-interval-minutes", 20));
        int max = Math.max(min, c.getInt("events.max-interval-minutes", 40));
        long ticks = (min + rnd.nextInt(max - min + 1)) * 1200L;
        scheduler = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            randomEvent();
            scheduleNext();
        }, ticks);
    }

    private void randomEvent() {
        FileConfiguration c = plugin.getConfig();
        if (Bukkit.getOnlinePlayers().size() < c.getInt("events.min-players", 1)) return;
        int global = 0;
        for (TreasureEvent ev : events.values()) if (ev.owner == null) global++;
        if (global >= c.getInt("events.max-concurrent", 3)) return;
        List<World> ws = new ArrayList<>();
        for (String n : c.getStringList("events.worlds")) {
            World w = Bukkit.getWorld(n);
            if (w != null && w.getEnvironment() == World.Environment.NORMAL) ws.add(w);
        }
        if (ws.isEmpty()) return;
        Tier t = pickTier(c.getConfigurationSection("events.weights"));
        if (t == null) return;
        startRandom(ws.get(rnd.nextInt(ws.size())), t, null, null, null);
    }

    private void giftTick() {
        FileConfiguration c = plugin.getConfig();
        List<Player> ps = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (ps.isEmpty() || rnd.nextDouble() * 100 > c.getDouble("random-gifts.chance", 60)) return;
        Player p = ps.get(rnd.nextInt(ps.size()));
        Tier t = pickTier(c.getConfigurationSection("random-gifts.weights"));
        if (t == null) return;
        ItemStack map = plugin.maps().create(plugin.mapWorld(p), t, null);
        for (ItemStack left : p.getInventory().addItem(map).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
        p.sendMessage(plugin.msg("gift", "tier", t.display));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.4f);
    }

    private Tier pickTier(ConfigurationSection w) {
        if (w == null) return null;
        int total = 0;
        Map<Tier, Integer> m = new LinkedHashMap<>();
        for (String k : w.getKeys(false)) {
            Tier t = plugin.tier(k);
            int wt = w.getInt(k, 0);
            if (t != null && wt > 0) {
                m.put(t, wt);
                total += wt;
            }
        }
        if (total <= 0) return null;
        int r = rnd.nextInt(total);
        for (Map.Entry<Tier, Integer> e : m.entrySet()) {
            r -= e.getValue();
            if (r < 0) return e.getKey();
        }
        return null;
    }

    // ───────────── inicio de eventos ─────────────

    public int[] randomPoint(World w, int min, int max) {
        Location sp = w.getSpawnLocation();
        int x = sp.getBlockX(), z = sp.getBlockZ();
        for (int i = 0; i < 10; i++) {
            double ang = rnd.nextDouble() * Math.PI * 2;
            double d = min + rnd.nextDouble() * Math.max(1, max - min);
            x = sp.getBlockX() + (int) Math.round(Math.cos(ang) * d);
            z = sp.getBlockZ() + (int) Math.round(Math.sin(ang) * d);
            if (w.getWorldBorder().isInside(new Location(w, x, 64, z))) break;
        }
        return new int[]{x, z};
    }

    public void startRandom(World w, Tier t, CustomTreasure ct, UUID mapId, Player owner) {
        FileConfiguration c = plugin.getConfig();
        trySpot(w, t, ct, mapId, owner, c.getInt("events.min-distance", 300), c.getInt("events.max-distance", 3000), 0);
    }

    private void trySpot(World w, Tier t, CustomTreasure ct, UUID mapId, Player owner, int min, int max, int attempt) {
        int[] p = randomPoint(w, min, max);
        w.getChunkAtAsync(p[0] >> 4, p[1] >> 4).thenAccept(ch -> Bukkit.getScheduler().runTask(plugin, () -> {
            Block top = w.getHighestBlockAt(p[0], p[1]);
            if (top.isLiquid() && attempt < 6) {
                trySpot(w, t, ct, mapId, owner, min, max, attempt + 1);
                return;
            }
            spawn(w, t, ct, p[0], p[1], mapId, owner);
        }));
    }

    public void startHere(Player p, Tier t, CustomTreasure ct) {
        Location l = p.getLocation();
        spawn(p.getWorld(), t, ct, l.getBlockX(), l.getBlockZ(), null, null);
    }

    public void activateMap(Player p, MapManager.MapData d) {
        for (TreasureEvent ev : events.values()) {
            if (d.id().equals(ev.mapId)) {
                p.sendMessage(plugin.msg("map-already"));
                track(p, ev.num);
                return;
            }
        }
        World w = Bukkit.getWorld(d.world());
        Tier t = plugin.tier(d.tier());
        CustomTreasure ct = d.custom() == null ? null : plugin.customs().get(d.custom());
        if (w == null || t == null || (d.custom() != null && ct == null)) {
            p.sendMessage(plugin.msg("map-invalid"));
            return;
        }
        if (!activating.add(d.id())) return;
        w.getChunkAtAsync(d.x() >> 4, d.z() >> 4).thenAccept(ch -> Bukkit.getScheduler().runTask(plugin, () -> {
            activating.remove(d.id());
            spawn(w, t, ct, d.x(), d.z(), d.id(), p);
        }));
    }

    private void spawn(World w, Tier t, CustomTreasure ct, int x, int z, UUID mapId, Player owner) {
        int y = w.getHighestBlockYAt(x, z);
        int minutes = mapId != null ? plugin.getConfig().getInt("maps.duration-minutes", 30) : t.durationMin;
        TreasureEvent ev = new TreasureEvent(++counter, t, ct, w, x, y + 1, z, mapId,
                owner == null ? null : owner.getUniqueId(), minutes * 60_000L,
                BossBar.bossBar(Component.empty(), 1f, t.barColor, BossBar.Overlay.PROGRESS));

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = 0; dy <= 3; dy++) {
                    Block b = w.getBlockAt(x + dx, y + dy, z + dz);
                    ev.saved.add(b.getState());
                    if (dy <= 1) blocks.put(key(b), ev);
                }
            }
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                w.getBlockAt(x + dx, y, z + dz).setType(t.pedestal, false);
                for (int dy = 1; dy <= 3; dy++) w.getBlockAt(x + dx, y + dy, z + dz).setType(Material.AIR, false);
            }
        }
        w.getBlockAt(x, y + 1, z).setType(t.chest, false);
        if (t.decor != null) {
            for (int sx = -1; sx <= 1; sx += 2) {
                for (int sz = -1; sz <= 1; sz += 2) w.getBlockAt(x + sx, y + 1, z + sz).setType(t.decor, false);
            }
        }
        events.put(ev.num, ev);

        for (Tier.MobSpec m : t.guardians) {
            for (int i = 0; i < m.amount(); i++) spawnMob(ev, m, 3 + rnd.nextInt(4), false);
        }
        if (t.boss != null) spawnMob(ev, t.boss, 2, true);

        Object[] kv = kv(ev, minutes);
        if (owner != null) {
            tracking.put(owner.getUniqueId(), ev.num);
            owner.sendMessage(plugin.msg("map-activated", kv));
            owner.playSound(owner.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 1f);
        } else {
            for (String line : plugin.getConfig().getStringList("messages.announce")) Bukkit.broadcast(Msg.c(plugin.fmt(line, kv)));
            Title title = Title.title(
                    Msg.c(plugin.fmt(plugin.getConfig().getString("messages.title-main", "%tier%"), kv)),
                    Msg.c(plugin.fmt(plugin.getConfig().getString("messages.title-sub", ""), kv)),
                    Title.Times.times(Duration.ofMillis(600), Duration.ofSeconds(6), Duration.ofSeconds(1)));
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.showTitle(title);
                p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 1f);
            }
        }
    }

    private Object[] kv(TreasureEvent ev, int minutes) {
        return new Object[]{"tier", ev.custom != null ? ev.custom.display : ev.tier.display, "num", ev.num,
                "world", ev.world.getName(), "x", ev.x, "y", ev.y, "z", ev.z, "stars", ev.tier.stars(),
                "time", minutes, "mobs", ev.mobs.size()};
    }

    private void spawnMob(TreasureEvent ev, Tier.MobSpec m, int radius, boolean boss) {
        EntityType type = Registry.ENTITY_TYPE.get(NamespacedKey.minecraft(m.type().toLowerCase(Locale.ROOT)));
        if (type == null) return;
        double ang = rnd.nextDouble() * Math.PI * 2;
        int gx = ev.x + (int) Math.round(Math.cos(ang) * radius);
        int gz = ev.z + (int) Math.round(Math.sin(ang) * radius);
        int gy = ev.world.getHighestBlockYAt(gx, gz) + 1;
        Entity e = ev.world.spawnEntity(new Location(ev.world, gx + 0.5, gy, gz + 0.5), type);
        if (!(e instanceof LivingEntity le)) {
            e.remove();
            return;
        }
        le.customName(Msg.c(m.name()));
        le.setCustomNameVisible(true);
        le.setRemoveWhenFarAway(false);
        le.setPersistent(true);
        if (m.health() > 0) {
            attr(le, Attribute.MAX_HEALTH, Math.min(1024, m.health()));
            AttributeInstance mh = le.getAttribute(Attribute.MAX_HEALTH);
            if (mh != null) le.setHealth(Math.min(mh.getValue(), m.health()));
        }
        if (m.damage() > 0) attr(le, Attribute.ATTACK_DAMAGE, m.damage());
        if (boss) {
            attr(le, Attribute.SCALE, 1.5);
            le.setGlowing(true);
        }
        EntityEquipment eq = le.getEquipment();
        String g = m.gear().toUpperCase(Locale.ROOT);
        if (eq != null && !g.equals("NONE")) {
            eq.setHelmet(piece(g, "HELMET"));
            eq.setChestplate(piece(g, "CHESTPLATE"));
            eq.setLeggings(piece(g, "LEGGINGS"));
            eq.setBoots(piece(g, "BOOTS"));
            if (!(le instanceof org.bukkit.entity.Skeleton)) eq.setItemInMainHand(piece(g, "SWORD"));
        }
        le.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, PotionEffect.INFINITE_DURATION, 0, false, false));
        le.getPersistentDataContainer().set(mobKey, PersistentDataType.STRING, ev.uuid.toString());
        ev.mobs.add(le.getUniqueId());
    }

    private void attr(LivingEntity le, Attribute a, double v) {
        AttributeInstance ai = le.getAttribute(a);
        if (ai != null) ai.setBaseValue(v);
    }

    private ItemStack piece(String gear, String part) {
        Material mt = Material.matchMaterial(gear + "_" + part);
        return mt == null ? null : new ItemStack(mt);
    }

    // ───────────── reclamar / terminar ─────────────

    public void claim(Player p, TreasureEvent ev) {
        if (ev.claimed) return;
        if (ev.tier.lockUntilCleared && !ev.mobs.isEmpty()) {
            p.sendMessage(plugin.msg("locked", "mobs", ev.mobs.size()));
            p.playSound(p.getLocation(), Sound.BLOCK_CHEST_LOCKED, 1f, 1f);
            return;
        }
        ev.claimed = true;
        List<String> got = giveRewards(p, ev);
        if (ev.mapId != null) plugin.data().claim(ev.mapId);
        plugin.data().addFound(p.getUniqueId());

        Location loc = new Location(ev.world, ev.x + 0.5, ev.y + 1, ev.z + 0.5);
        Firework fw = ev.world.spawn(loc, Firework.class);
        FireworkMeta fm = fw.getFireworkMeta();
        fm.addEffect(FireworkEffect.builder().with(FireworkEffect.Type.STAR).withColor(Color.ORANGE, Color.YELLOW)
                .withFade(Color.WHITE).flicker(true).build());
        fm.setPower(0);
        fw.setFireworkMeta(fm);
        fw.detonate();
        ev.world.playSound(loc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 2f, 1f);
        ev.world.spawnParticle(Particle.END_ROD, loc, 60, 0.6, 0.8, 0.6, 0.05);

        Component c = plugin.msg("claimed-global", "player", p.getName(),
                "tier", ev.custom != null ? ev.custom.display : ev.tier.display, "num", ev.num);
        if (ev.owner == null) Bukkit.broadcast(c);
        else p.sendMessage(c);
        p.sendMessage(plugin.msg("rewards", "list", got.isEmpty() ? "-" : String.join("<gray>, <white>", got)));
        end(ev, 60);
    }

    private List<String> giveRewards(Player p, TreasureEvent ev) {
        Tier t = ev.tier;
        List<ItemStack> out = new ArrayList<>();
        List<String> got = new ArrayList<>();
        if (ev.custom != null) {
            for (ItemStack s : ev.custom.loot) out.add(s.clone());
        } else {
            for (Tier.ItemRoll r : t.items) {
                if (rnd.nextDouble() * 100 < r.chance()) {
                    out.add(new ItemStack(r.material(), r.min() + rnd.nextInt(r.max() - r.min() + 1)));
                }
            }
        }
        for (ItemStack s : out) {
            got.add(s.getAmount() + "x " + s.getType().name().toLowerCase(Locale.ROOT).replace('_', ' '));
            for (ItemStack left : p.getInventory().addItem(s).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
        }
        if (t.moneyMax > 0 && plugin.economy().hasEconomy()) {
            int amt = t.moneyMin + rnd.nextInt(t.moneyMax - t.moneyMin + 1);
            plugin.economy().deposit(p, amt);
            got.add(plugin.economy().format(amt));
        }
        if (t.xp > 0) {
            p.giveExp(t.xp);
            got.add(t.xp + " XP");
        }
        for (String cmd : t.commands) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("%player%", p.getName()));
        return got;
    }

    private void expire(TreasureEvent ev) {
        if (ev.owner == null) {
            Bukkit.broadcast(plugin.msg("expired", "tier", ev.custom != null ? ev.custom.display : ev.tier.display, "num", ev.num));
        }
        end(ev, 0);
    }

    private void end(TreasureEvent ev, long delay) {
        events.remove(ev.num);
        blocks.values().removeIf(e -> e == ev);
        tracking.values().removeIf(n -> n == ev.num);
        for (Player p : Bukkit.getOnlinePlayers()) p.hideBossBar(ev.bar);
        for (UUID id : ev.mobs) {
            Entity e = Bukkit.getEntity(id);
            if (e != null) e.remove();
        }
        ev.mobs.clear();
        ev.world.getBlockAt(ev.x, ev.y, ev.z).setType(Material.AIR, false);
        Runnable restore = () -> {
            for (BlockState s : ev.saved) s.update(true, false);
        };
        if (delay > 0) {
            pending.add(restore);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (pending.remove(restore)) restore.run();
            }, delay);
        } else {
            restore.run();
        }
    }

    public int stopAll() {
        int n = events.size();
        for (TreasureEvent ev : new ArrayList<>(events.values())) end(ev, 0);
        return n;
    }

    public boolean stop(int num) {
        TreasureEvent ev = events.get(num);
        if (ev == null) return false;
        end(ev, 0);
        return true;
    }

    // ───────────── mobs ─────────────

    public boolean onMobDeath(LivingEntity e) {
        String tag = e.getPersistentDataContainer().get(mobKey, PersistentDataType.STRING);
        if (tag == null) return false;
        for (TreasureEvent ev : events.values()) {
            if (ev.uuid.toString().equals(tag)) {
                ev.mobs.remove(e.getUniqueId());
                if (ev.mobs.isEmpty() && ev.tier.lockUntilCleared) {
                    Location c = new Location(ev.world, ev.x, ev.y, ev.z);
                    for (Player p : ev.world.getPlayers()) {
                        if (p.getLocation().distanceSquared(c) < 2500) {
                            p.sendMessage(plugin.msg("unlocked", "num", ev.num));
                            p.playSound(p.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1f, 1f);
                        }
                    }
                }
                break;
            }
        }
        return true;
    }

    public void cleanOrphan(Entity en) {
        String tag = en.getPersistentDataContainer().get(mobKey, PersistentDataType.STRING);
        if (tag == null) return;
        for (TreasureEvent ev : events.values()) if (ev.uuid.toString().equals(tag)) return;
        Bukkit.getScheduler().runTask(plugin, en::remove);
    }

    // ───────────── consulta / rastreo ─────────────

    private String key(Block b) {
        return b.getWorld().getUID() + ":" + b.getX() + ":" + b.getY() + ":" + b.getZ();
    }

    public TreasureEvent at(Block b) {
        return blocks.get(key(b));
    }

    public TreasureEvent get(int num) {
        return events.get(num);
    }

    public boolean canSee(TreasureEvent ev, Player p) {
        return ev.owner == null || ev.owner.equals(p.getUniqueId());
    }

    public List<TreasureEvent> visibleTo(Player p) {
        List<TreasureEvent> l = new ArrayList<>();
        for (TreasureEvent ev : events.values()) if (canSee(ev, p)) l.add(ev);
        return l;
    }

    public Collection<TreasureEvent> all() {
        return events.values();
    }

    public String timeLeft(TreasureEvent ev) {
        long s = Math.max(0, (ev.endAt - System.currentTimeMillis()) / 1000);
        return String.format("%d:%02d", s / 60, s % 60);
    }

    public TreasureEvent nearest(Player p) {
        TreasureEvent best = null;
        double bd = Double.MAX_VALUE;
        for (TreasureEvent ev : visibleTo(p)) {
            double d = ev.world.equals(p.getWorld())
                    ? p.getLocation().distanceSquared(new Location(ev.world, ev.x, ev.y, ev.z)) : Double.MAX_VALUE / 2;
            if (best == null || d < bd) {
                best = ev;
                bd = d;
            }
        }
        return best;
    }

    public void track(Player p, int num) {
        TreasureEvent ev = events.get(num);
        if (ev == null || !canSee(ev, p)) {
            p.sendMessage(plugin.msg("no-events"));
            return;
        }
        tracking.put(p.getUniqueId(), num);
        p.sendMessage(plugin.msg("track-on", "num", num));
    }

    public void untrack(Player p) {
        if (tracking.remove(p.getUniqueId()) != null) {
            p.setCompassTarget(p.getWorld().getSpawnLocation());
            p.sendMessage(plugin.msg("track-off"));
        }
    }

    public void forget(Player p) {
        tracking.remove(p.getUniqueId());
    }

    // ───────────── tareas periódicas ─────────────

    private void tickSecond() {
        long now = System.currentTimeMillis();
        String barFmt = plugin.getConfig().getString("messages.bossbar", "%tier% %time%");
        for (TreasureEvent ev : new ArrayList<>(events.values())) {
            long left = ev.endAt - now;
            if (left <= 0) {
                expire(ev);
                continue;
            }
            ev.bar.progress(Math.max(0f, Math.min(1f, (float) left / (ev.endAt - ev.startedAt))));
            ev.bar.name(Msg.c(plugin.fmt(barFmt, "tier", ev.custom != null ? ev.custom.display : ev.tier.display,
                    "num", ev.num, "time", timeLeft(ev), "mobs", ev.mobs.size())));
            for (Player p : Bukkit.getOnlinePlayers()) {
                boolean show = canSee(ev, p) && (ev.owner != null || p.getWorld().equals(ev.world));
                if (show) p.showBossBar(ev.bar);
                else p.hideBossBar(ev.bar);
            }
            if (ev.world.isChunkLoaded(ev.x >> 4, ev.z >> 4)) {
                for (int i = 0; i < 6; i++) {
                    Location l = new Location(ev.world, ev.x + 0.5, ev.y + 1 + i * 8, ev.z + 0.5);
                    ev.world.spawnParticle(Particle.END_ROD, l, 4, 0.15, 3, 0.15, 0.0, null, true);
                }
            }
        }
    }

    private void tickTrack() {
        FileConfiguration c = plugin.getConfig();
        for (Iterator<Map.Entry<UUID, Integer>> it = tracking.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Integer> en = it.next();
            Player p = Bukkit.getPlayer(en.getKey());
            TreasureEvent ev = events.get(en.getValue());
            if (p == null || ev == null) {
                it.remove();
                continue;
            }
            if (!p.getWorld().equals(ev.world)) {
                p.sendActionBar(plugin.msg("track-otherworld", "world", ev.world.getName(), "num", ev.num));
                continue;
            }
            Location l = p.getLocation();
            double dx = ev.x + 0.5 - l.getX(), dz = ev.z + 0.5 - l.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            double target = Math.toDegrees(Math.atan2(-dx, dz));
            double rel = ((target - l.getYaw()) % 360 + 540) % 360 - 180;
            int idx = ((int) Math.round(rel / 45.0) % 8 + 8) % 8;
            String heat = dist < 20 ? plugin.fmt(c.getString("messages.heat-hot", ""))
                    : dist < 100 ? plugin.fmt(c.getString("messages.heat-warm", ""))
                    : plugin.fmt(c.getString("messages.heat-cold", ""));
            p.setCompassTarget(new Location(ev.world, ev.x, ev.y, ev.z));
            p.sendActionBar(plugin.msg("track", "arrow", ARROWS[idx], "dist", (int) dist,
                    "tier", ev.custom != null ? ev.custom.display : ev.tier.display, "num", ev.num, "heat", heat));
        }
    }
}
