package com.orbitaleternal;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public final class OrbitalEternal extends JavaPlugin implements Listener, TabExecutor {
    private NamespacedKey modeKey;
    private int activeStrikes;

    @Override public void onEnable() {
        saveDefaultConfig();
        modeKey = new NamespacedKey(this, "orbital_mode");
        getServer().getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("orbitalEternal")).setExecutor(this);
        Objects.requireNonNull(getCommand("orbitalEternal")).setTabCompleter(this);
        getLogger().info("Orbital Eternal enabled.");
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Run this command in-game."); return true; }
        if (!player.isOp()) { player.sendMessage("§cOnly server operators can use Orbital Eternal."); return true; }
        if (args.length != 1 || !(args[0].equalsIgnoreCase("nuke") || args[0].equalsIgnoreCase("stab"))) {
            player.sendMessage("§eUsage: /orbitalEternal <nuke|stab>"); return true;
        }
        String mode = args[0].toLowerCase(Locale.ROOT);
        if (!getConfig().getBoolean(mode.equals("nuke") ? "orbital.enabled" : "stab.enabled", true)) {
            player.sendMessage("§cThat firing mode is disabled in config.yml."); return true;
        }
        ItemStack rod = new ItemStack(Material.FISHING_ROD);
        ItemMeta meta = rod.getItemMeta();
        meta.setDisplayName("§6☄ §lOrbital Eternal §7[" + mode.toUpperCase(Locale.ROOT) + "]");
        meta.setLore(List.of("§7One-use cannon trigger", "§eRight-click to fire"));
        meta.getPersistentDataContainer().set(modeKey, PersistentDataType.STRING, mode);
        if (meta instanceof Damageable d) d.setDamage(rod.getType().getMaxDurability() - 1);
        rod.setItemMeta(meta);
        player.getInventory().addItem(rod).values().forEach(it -> player.getWorld().dropItemNaturally(player.getLocation(), it));
        player.sendMessage("§6☄ " + mode.toUpperCase(Locale.ROOT) + " rod received. Right-click while aiming at a block.");
        return true;
    }

    @EventHandler public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.FISHING_ROD || !item.hasItemMeta()) return;
        String mode = item.getItemMeta().getPersistentDataContainer().get(modeKey, PersistentDataType.STRING);
        if (mode == null) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!player.isOp()) { player.sendMessage("§cOnly operators can fire this item."); return; }
        if (activeStrikes >= getConfig().getInt("performance.max-active-strikes", 1)) {
            player.sendMessage("§cA strike is already running."); return;
        }
        Block aimed = player.getTargetBlockExact(200);
        if (aimed == null || aimed.getType().isAir()) { player.sendMessage("§cLook at a block within 200 blocks."); return; }
        player.getInventory().setItemInMainHand(null);
        Location target = aimed.getLocation().add(0.5, 1, 0.5);
        if (mode.equals("nuke")) launchNuke(target, player); else launchStab(target, player);
    }

    private void launchNuke(Location target, Player owner) {
        World world = target.getWorld();
        int height = getConfig().getInt("orbital.height", 72);
        int fuse = getConfig().getInt("orbital.fuse-ticks", 80);
        int batch = Math.max(1, getConfig().getInt("orbital.spawn-per-tick", 20));
        int spreadTicks = Math.max(1, getConfig().getInt("orbital.outward-spread-ticks", 80));
        double scatter = getConfig().getDouble("orbital.scatter", 1.8);
        List<Payload> payload = new ArrayList<>();
        payload.add(new Payload(0, 0, 0, 0));
        List<Integer> radii = getConfig().getIntegerList("orbital.ring-radii");
        List<Integer> counts = getConfig().getIntegerList("orbital.ring-counts");
        for (int r = 0; r < Math.min(radii.size(), counts.size()); r++) {
            for (int i = 0; i < counts.get(r); i++) {
                double angle = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
                double initialR = ThreadLocalRandom.current().nextDouble(0, scatter);
                double drag = 0.98, distance = Math.max(0, radii.get(r) - initialR);
                double velocity = distance * (1 - drag) / (1 - Math.pow(drag, spreadTicks));
                payload.add(new Payload(Math.cos(angle) * initialR, Math.sin(angle) * initialR,
                        Math.min(1.2, velocity), angle));
            }
        }
        int total = getConfig().getInt("orbital.total-charges", 760);
        if (payload.size() > total) payload = new ArrayList<>(payload.subList(0, total));
        owner.sendMessage("§6☄ Orbital Nuke launched. Payload descending!");
        world.playSound(target, Sound.BLOCK_BEACON_ACTIVATE, 3f, 0.6f);
        activeStrikes++;
        List<Payload> shots = payload;
        new BukkitRunnable() {
            int index;
            @Override public void run() {
                int end = Math.min(index + batch, shots.size());
                for (; index < end; index++) {
                    Payload p = shots.get(index);
                    Location spawn = target.clone().add(p.x, height, p.z);
                    if (!world.isChunkLoaded(spawn.getBlockX() >> 4, spawn.getBlockZ() >> 4))
                        world.getChunkAt(spawn).load();
                    world.spawn(spawn, TNTPrimed.class, tnt -> {
                        tnt.setFuseTicks(fuse);
                        tnt.setSource(owner);
                        tnt.setVelocity(new Vector(Math.cos(p.angle) * p.speed, -0.05, Math.sin(p.angle) * p.speed));
                    });
                }
                if (index >= shots.size()) { activeStrikes--; cancel(); }
            }
        }.runTaskTimer(this, 0L, 1L);
    }

    private void launchStab(Location target, Player owner) {
        World world = target.getWorld();
        int max = Math.min(192, getConfig().getInt("stab.max-charges", 192));
        int spacing = Math.max(1, getConfig().getInt("stab.spacing", 2));
        int fuse = Math.max(1, getConfig().getInt("stab.fuse-ticks", 1));
        double resistance = getConfig().getDouble("stab.blast-resistance-stop", 1200.0);
        List<Location> positions = new ArrayList<>();
        int count = 0;
        for (int y = target.getBlockY(); y > world.getMinHeight() && count < max; y -= spacing) {
            Block block = world.getBlockAt(target.getBlockX(), y, target.getBlockZ());
            if (block.getType().getBlastResistance() >= resistance) break;
            positions.add(new Location(world, target.getX(), y + 0.5, target.getZ()));
            count++;
        }
        if (positions.isEmpty()) { owner.sendMessage("§cStab shot stopped: no valid drill path."); return; }
        owner.sendMessage("§c☄ Stab Shot launched: " + positions.size() + " charges.");
        activeStrikes++;
        new BukkitRunnable() {
            int index;
            @Override public void run() {
                if (index >= positions.size()) { activeStrikes--; cancel(); return; }
                Location spawn = positions.get(index++);
                if (!world.isChunkLoaded(spawn.getBlockX() >> 4, spawn.getBlockZ() >> 4)) {
                    activeStrikes--; owner.sendMessage("§cStab shot stopped: chunk unloaded."); cancel(); return;
                }
                world.spawn(spawn, TNTPrimed.class, tnt -> {
                    tnt.setFuseTicks(fuse); tnt.setSource(owner); tnt.setVelocity(new Vector(0, -0.1, 0));
                });
            }
        }.runTaskTimer(this, 0L, 1L);
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player p) || !p.isOp()) return List.of();
        if (args.length == 1) return List.of("nuke", "stab").stream()
                .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        return List.of();
    }
    private record Payload(double x, double z, double speed, double angle) {}
}
