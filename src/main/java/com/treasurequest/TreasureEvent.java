package com.treasurequest;

import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class TreasureEvent {
    public final int num;
    public final UUID uuid = UUID.randomUUID();
    public final Tier tier;
    public final CustomTreasure custom;
    public final World world;
    public final int x, y, z;
    public final UUID mapId, owner;
    public final long startedAt, endAt;
    public final BossBar bar;
    public final Set<UUID> mobs = new HashSet<>();
    public final List<BlockState> saved = new ArrayList<>();
    public boolean claimed;

    public TreasureEvent(int num, Tier tier, CustomTreasure custom, World world, int x, int y, int z,
                         UUID mapId, UUID owner, long durationMs, BossBar bar) {
        this.num = num;
        this.tier = tier;
        this.custom = custom;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.mapId = mapId;
        this.owner = owner;
        this.startedAt = System.currentTimeMillis();
        this.endAt = startedAt + durationMs;
        this.bar = bar;
    }

    public boolean isChest(Block b) {
        return b.getWorld().equals(world) && b.getX() == x && b.getY() == y && b.getZ() == z;
    }
}
