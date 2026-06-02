package me.aethelster.aethelguard;

import org.bukkit.Location;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class AuthListener implements Listener {
    private static final List<String> DEFAULT_ALLOWED_COMMANDS = List.of(
            "/login",
            "/giris",
            "/giriş",
            "/register",
            "/kayitol",
            "/kayıtol"
    );

    private final Aethelguard plugin;

    public AuthListener(Aethelguard plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (plugin.getConfig().getBoolean("console-logging.suppress-server-connection-logs", true)) {
            event.setJoinMessage(null);
        }

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        plugin.getUnauthenticatedPlayers().add(uuid);
        plugin.rememberPreviousLocation(player);

        if (plugin.getConfig().getBoolean("auth-settings.teleport-to-void", true)) {
            player.teleport(plugin.getVoidLocation(player));
            plugin.sendMessage(player, "messages.secure-void-zone", true);
        } else {
            plugin.sendMessage(player, "messages.welcome-message", true);
        }

        plugin.applyAuthEffects(player);
        startAuthPrompts(player);
        startAuthTimeout(player);
    }

    private void startAuthPrompts(Player player) {
        if (!plugin.getConfig().getBoolean("auth-settings.prompts.enabled", true)) return;

        long initialDelay = plugin.getConfig().getLong("auth-settings.prompts.initial-delay-ticks", 20L);
        long interval = plugin.getConfig().getLong("auth-settings.prompts.interval-ticks", 100L);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    this.cancel();
                    return;
                }

                if (plugin.isAuthenticated(player)) {
                    this.cancel();
                    return;
                }

                boolean isRegistered = plugin.isAccountRegistered(player.getUniqueId());
                plugin.sendMessage(player, isRegistered ? "messages.login-prompt" : "messages.register-prompt", true);
            }
        }.runTaskTimer(plugin, initialDelay, interval);
    }

    private void startAuthTimeout(Player player) {
        if (!plugin.getConfig().getBoolean("auth-settings.timeout.enabled", true)) return;

        long timeoutTicks = plugin.getConfig().getLong("auth-settings.timeout.ticks", 1200L);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || plugin.isAuthenticated(player)) return;

            plugin.logInfo("§e" + player.getName() + " giriş yapmadığı için zaman aşımından dolayı sunucudan atıldı.",
                    "§e" + player.getName() + " was kicked due to authentication timeout.");

            if (plugin.getConfig().getBoolean("auth-settings.timeout.kick", true)) {
                plugin.restorePreviousLocation(player);
                player.kickPlayer(plugin.getRawStringMessage("messages.timeout-kick", true));
            }
        }, timeoutTicks);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (plugin.getConfig().getBoolean("console-logging.suppress-server-connection-logs", true)) {
            event.setQuitMessage(null);
        }

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (!plugin.isAuthenticated(player)) {
            plugin.restorePreviousLocation(player);
        }

        plugin.getLoggedInPlayers().remove(uuid);
        plugin.getUnauthenticatedPlayers().remove(uuid);
        plugin.getPreviousLocations().remove(uuid);
        plugin.getWrongPasswordAttempts().remove(uuid);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (plugin.isAuthenticated(player)
                || !plugin.getConfig().getBoolean("auth-settings.restrictions.prevent-movement", true)) {
            return;
        }

        Location from = event.getFrom();
        Location to = event.getTo();

        if (to != null && (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ())) {
            Location lockLoc = from.clone();
            lockLoc.setYaw(to.getYaw());
            lockLoc.setPitch(to.getPitch());
            event.setTo(lockLoc);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (plugin.isAuthenticated(player)) return;

        String message = event.getMessage().toLowerCase(Locale.ROOT);

        if (isAllowedCommand(message)) {
            handleAllowedAuthCommand(event);
            return;
        }

        event.setCancelled(true);
        plugin.sendMessage(player, "messages.command-denied", true);

        if (plugin.getConfig().getBoolean("auth-settings.log-unauthenticated-command-attempts", true)) {
            plugin.logWarning(
                    "Giriş yapmamış oyuncu (" + player.getName() + ") komut denedi: " + event.getMessage(),
                    "Unauthenticated player (" + player.getName() + ") executed command: " + event.getMessage()
            );
        }
    }

    private void handleAllowedAuthCommand(PlayerCommandPreprocessEvent event) {
        String[] parts = event.getMessage().substring(1).split("\\s+");
        if (parts.length == 0) return;

        String label = parts[0].toLowerCase(Locale.ROOT);
        String commandName = switch (label) {
            case "login", "giris", "giriş" -> "login";
            case "register", "kayitol", "kayıtol" -> "register";
            default -> null;
        };

        if (commandName == null) return;

        PluginCommand command = plugin.getCommand(commandName);
        if (command == null) return;

        String[] args = new String[Math.max(0, parts.length - 1)];
        if (parts.length > 1) {
            System.arraycopy(parts, 1, args, 0, args.length);
        }

        event.setCancelled(true);
        new AuthCommand(plugin).onCommand(event.getPlayer(), command, label, args);
    }

    private boolean isAllowedCommand(String message) {
        List<String> allowedCommands = plugin.getConfig().getStringList("auth-settings.commands.allowed");
        if (allowedCommands.isEmpty()) {
            allowedCommands = DEFAULT_ALLOWED_COMMANDS;
        }

        for (String command : allowedCommands) {
            String normalizedCommand = command.toLowerCase(Locale.ROOT);
            if (message.equals(normalizedCommand) || message.startsWith(normalizedCommand + " ")) {
                return true;
            }
        }

        return false;
    }

    @EventHandler
    public void onPlayerDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player
                && !plugin.isAuthenticated(player)
                && plugin.getConfig().getBoolean("auth-settings.restrictions.prevent-damage", true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (!plugin.isAuthenticated(event.getPlayer())
                && plugin.getConfig().getBoolean("auth-settings.restrictions.prevent-block-break", true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!plugin.isAuthenticated(event.getPlayer())
                && plugin.getConfig().getBoolean("auth-settings.restrictions.prevent-block-place", true)) {
            event.setCancelled(true);
        }
    }
}
