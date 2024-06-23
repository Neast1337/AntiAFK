package org.neast.antiafk;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class Antiafk extends JavaPlugin implements Listener {

    private Map<UUID, BukkitRunnable> afkTasks = new ConcurrentHashMap<>();
    private Map<UUID, Boolean> isAfk = new ConcurrentHashMap<>();
    private Map<UUID, Boolean> afkByJoin = new ConcurrentHashMap<>();
    private FileConfiguration config;

    private static final String PREFIX_KEY = "prefix";
    private static final String MESSAGE_AFK = "messages.afk";
    private static final String MESSAGE_NOT_AFK = "messages.notAfk";
    private static final String MESSAGE_KICK_REASON = "messages.kickReason";
    private static final String MESSAGE_AFK_NOTIFY = "messages.afkNotify";
    private static final String TITLE_AFK = "titles.afkTitle";
    private static final String SUBTITLE_AFK = "titles.afkSubtitle";

    private String prefix;
    private String messageAfk;
    private String messageNotAfk;
    private String messageKickReason;
    private String messageAfkNotify;
    private String titleAfk;
    private String subtitleAfk;

    @Override
    public void onEnable() {
        // Load configuration
        saveDefaultConfig();
        config = getConfig();
        prefix = ChatColor.translateAlternateColorCodes('&', config.getString(PREFIX_KEY));
        messageAfk = ChatColor.translateAlternateColorCodes('&', config.getString(MESSAGE_AFK));
        messageNotAfk = ChatColor.translateAlternateColorCodes('&', config.getString(MESSAGE_NOT_AFK));
        messageKickReason = ChatColor.translateAlternateColorCodes('&', config.getString(MESSAGE_KICK_REASON));
        messageAfkNotify = ChatColor.translateAlternateColorCodes('&', config.getString(MESSAGE_AFK_NOTIFY));
        titleAfk = ChatColor.translateAlternateColorCodes('&', config.getString(TITLE_AFK));
        subtitleAfk = ChatColor.translateAlternateColorCodes('&', config.getString(SUBTITLE_AFK));

        // Register events
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        // Cancel ongoing AFK tasks
        afkTasks.values().forEach(BukkitRunnable::cancel);
        afkTasks.clear();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (label.equalsIgnoreCase("antiafk")) {
            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                if (!sender.hasPermission("antiafk.reload")) {
                    sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
                    return true;
                }
                reloadConfig();
                config = getConfig();
                prefix = ChatColor.translateAlternateColorCodes('&', config.getString(PREFIX_KEY));
                messageAfk = ChatColor.translateAlternateColorCodes('&', config.getString(MESSAGE_AFK));
                messageNotAfk = ChatColor.translateAlternateColorCodes('&', config.getString(MESSAGE_NOT_AFK));
                messageKickReason = ChatColor.translateAlternateColorCodes('&', config.getString(MESSAGE_KICK_REASON));
                messageAfkNotify = ChatColor.translateAlternateColorCodes('&', config.getString(MESSAGE_AFK_NOTIFY));
                titleAfk = ChatColor.translateAlternateColorCodes('&', config.getString(TITLE_AFK));
                subtitleAfk = ChatColor.translateAlternateColorCodes('&', config.getString(SUBTITLE_AFK));
                sender.sendMessage(ChatColor.GREEN + "Configuration successfully reloaded!");
                return true;
            }
        }
        return false;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        if (player.hasPermission("antiafk.bypass")) {
            return;
        }

        if (event.getFrom().distanceSquared(event.getTo()) > 0.01) {
            cancelAfkTask(playerId);
            if (isAfk.getOrDefault(playerId, false)) {
                if (afkByJoin.getOrDefault(playerId, false)) {
                    afkByJoin.remove(playerId);
                } else {
                    player.sendMessage(prefix + messageNotAfk);
                }
                isAfk.put(playerId, false);
            }
        } else {
            if (!afkTasks.containsKey(playerId)) {
                BukkitRunnable task = new BukkitRunnable() {
                    @Override
                    public void run() {
                        player.sendMessage(prefix + messageAfk);
                        player.sendTitle(titleAfk, subtitleAfk, 10, 70, 20);
                        String soundName = config.getString("sounds.afkSound", "ENTITY_PLAYER_LEVELUP");
                        Sound sound = Sound.valueOf(soundName);
                        player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
                        isAfk.put(playerId, true);
                        notifyAdmins(player);
                        kickPlayerIfAfk(player);
                    }
                };
                afkTasks.put(playerId, task);
                task.runTaskLaterAsynchronously(this, config.getInt("afk.afkCheckDelayTicks", 200));
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        if (!player.hasPermission("antiafk.bypass")) {
            BukkitRunnable task = new BukkitRunnable() {
                @Override
                public void run() {
                    player.sendMessage(prefix + messageAfk);
                    player.sendTitle(titleAfk, subtitleAfk, 10, 70, 20);
                    String soundName = config.getString("sounds.afkSound", "ENTITY_PLAYER_LEVELUP");
                    Sound sound = Sound.valueOf(soundName);
                    player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
                    isAfk.put(playerId, true);
                    afkByJoin.put(playerId, true);
                    notifyAdmins(player);
                    kickPlayerIfAfk(player);
                }
            };
            afkTasks.put(playerId, task);
            task.runTaskLaterAsynchronously(this, config.getInt("afk.afkCheckDelayTicks", 200));
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        cancelAfkTask(playerId);
        isAfk.remove(playerId);
        afkByJoin.remove(playerId);
    }

    private void cancelAfkTask(UUID playerId) {
        BukkitRunnable task = afkTasks.remove(playerId);
        if (task != null) {
            task.cancel();
        }
    }

    private void kickPlayerIfAfk(Player player) {
        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline() && !player.isDead() && afkTasks.containsKey(player.getUniqueId()) && isAfk.getOrDefault(player.getUniqueId(), false)) {
                    player.kickPlayer(messageKickReason);
                    notifyOps(player);
                    cancelAfkTask(player.getUniqueId());
                }
            }
        };
        task.runTaskLater(this, config.getInt("afk.kickDelayTicks", 100));
    }

    private void notifyAdmins(Player afkPlayer) {
        String notifyMessage = messageAfkNotify.replace("{player}", afkPlayer.getName());
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.isOp()) {
                player.sendMessage(prefix + notifyMessage);
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
            }
        }
    }

    private void notifyOps(Player kickedPlayer) {
        String kickMessage = ChatColor.RED + "Player " + kickedPlayer.getName() + " has been kicked for being AFK.";
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.isOp()) {
                player.sendMessage(prefix + kickMessage);
            }
        }
    }
}
