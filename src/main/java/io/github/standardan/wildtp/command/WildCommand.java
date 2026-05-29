package io.github.standardan.wildtp.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * /wild - teleport to a random safe spot. The whole search is asynchronous:
 * each candidate chunk is loaded with getChunkAtAsync (off the main thread),
 * the safety check runs on the main thread (block reads must), and the
 * teleport uses teleportAsync. The server never stalls, even if it takes
 * many tries to find solid ground.
 */
public final class WildCommand implements CommandExecutor {

    private final JavaPlugin plugin;
    private final Set<Material> unsafeGround;
    private final Map<UUID, Long> cooldownUntil = new HashMap<>();
    private final Set<UUID> searching = ConcurrentHashMap.newKeySet();

    public WildCommand(JavaPlugin plugin) {
        this.plugin = plugin;
        this.unsafeGround = EnumSet.noneOf(Material.class);
        for (String name : plugin.getConfig().getStringList("unsafe-blocks")) {
            Material m = Material.matchMaterial(name);
            if (m != null) {
                unsafeGround.add(m);
            }
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use /wild.");
            return true;
        }
        UUID id = player.getUniqueId();
        if (searching.contains(id)) {
            player.sendMessage(Component.text("Already finding you a spot...", NamedTextColor.GRAY));
            return true;
        }
        if (!player.hasPermission("wildtp.cooldown.bypass")) {
            long until = cooldownUntil.getOrDefault(id, 0L);
            long now = System.currentTimeMillis();
            if (now < until) {
                player.sendMessage(Component.text("Wait " + ((until - now) / 1000 + 1)
                        + "s before using /wild again.", NamedTextColor.RED));
                return true;
            }
        }

        String worldName = plugin.getConfig().getString("world", "");
        World world = (worldName == null || worldName.isEmpty())
                ? player.getWorld() : Bukkit.getWorld(worldName);
        if (world == null) {
            player.sendMessage(Component.text("Configured world doesn't exist.", NamedTextColor.RED));
            return true;
        }

        int minR = plugin.getConfig().getInt("min-radius", 200);
        int maxR = Math.max(minR + 1, plugin.getConfig().getInt("max-radius", 5000));
        int attempts = plugin.getConfig().getInt("max-attempts", 25);

        searching.add(id);
        player.sendMessage(Component.text("Searching for a safe spot in the wild...", NamedTextColor.GRAY));
        attempt(player, world, minR, maxR, attempts);
        return true;
    }

    private void attempt(Player player, World world, int minR, int maxR, int attemptsLeft) {
        UUID id = player.getUniqueId();
        if (!player.isOnline()) {
            searching.remove(id);
            return;
        }
        if (attemptsLeft <= 0) {
            searching.remove(id);
            player.sendMessage(Component.text("Couldn't find a safe spot - try again.", NamedTextColor.RED));
            return;
        }

        Location center = world.getSpawnLocation();
        double angle = ThreadLocalRandom.current().nextDouble() * Math.PI * 2;
        double dist = minR + ThreadLocalRandom.current().nextDouble() * (maxR - minR);
        int x = center.getBlockX() + (int) (Math.cos(angle) * dist);
        int z = center.getBlockZ() + (int) (Math.sin(angle) * dist);

        // Load the candidate chunk off-thread, then inspect it on the main thread.
        world.getChunkAtAsync(x >> 4, z >> 4).thenAccept(chunk ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Location safe = findSafeSpot(world, x, z);
                    if (safe == null) {
                        attempt(player, world, minR, maxR, attemptsLeft - 1);
                        return;
                    }
                    if (!player.isOnline()) {
                        searching.remove(id);
                        return;
                    }
                    player.teleportAsync(safe).thenAccept(success -> {
                        searching.remove(id);
                        if (success) {
                            applyCooldown(player);
                            player.sendMessage(Component.text("Whoosh! Teleported to " + safe.getBlockX()
                                    + ", " + safe.getBlockY() + ", " + safe.getBlockZ() + ".", NamedTextColor.GREEN));
                        } else {
                            player.sendMessage(Component.text("Teleport failed - try again.", NamedTextColor.RED));
                        }
                    });
                }));
    }

    /** Returns a standable location at (x, z), or null if the surface isn't safe. */
    private Location findSafeSpot(World world, int x, int z) {
        int y = world.getHighestBlockYAt(x, z);
        if (y <= world.getMinHeight() + 1) {
            return null; // void / no terrain
        }
        Block ground = world.getBlockAt(x, y, z);
        Block feet = world.getBlockAt(x, y + 1, z);
        Block head = world.getBlockAt(x, y + 2, z);

        if (!ground.getType().isSolid() || unsafeGround.contains(ground.getType())) {
            return null;
        }
        if (!feet.isPassable() || !head.isPassable()) {
            return null;
        }
        return new Location(world, x + 0.5, y + 1, z + 0.5);
    }

    private void applyCooldown(Player player) {
        if (!player.hasPermission("wildtp.cooldown.bypass")) {
            int cd = plugin.getConfig().getInt("cooldown-seconds", 60);
            cooldownUntil.put(player.getUniqueId(), System.currentTimeMillis() + cd * 1000L);
        }
    }
}
