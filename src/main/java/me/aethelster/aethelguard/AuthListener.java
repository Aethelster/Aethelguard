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

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class AuthListener implements Listener {
    private static final List<String> DEFAULT_ALLOWED_COMMANDS = List.of(
            "/login",
            "/giris",
            "/giriş",
            "/register",
            "/kayitol",
            "/kayit",
            "/pin",
            "/setpin",
            "/pinayarla",
            "/kayıtol",
            "/captcha",
            "/dogrula",
            "/doğrula",
            "/twofactor",
            "/2fa",
            "/authenticator",
            "/authy",
            "/recover",
            "/sifresifirla",
            "/şifresıfırla",
            "/kurtar"
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

        plugin.rememberPreviousLocation(player);

        if (plugin.consumeValidAuthSession(player)) {
            return;
        }

        plugin.getUnauthenticatedPlayers().add(uuid);
        plugin.hideInventoryForAuth(player);

        if (plugin.getConfig().getBoolean("auth-settings.teleport-to-void", true)) {
            player.teleport(plugin.getVoidLocation(player));
            plugin.sendMessage(player, "messages.secure-void-zone", true);
        } else {
            plugin.sendMessage(player, "messages.welcome-message", true);
        }

        plugin.applyAuthEffects(player);
        plugin.startCaptcha(player);
        startAuthPrompts(player);
        startAuthTimeout(player);
    }

    private void startAuthPrompts(Player player) {
        if (!plugin.getConfig().getBoolean("auth-settings.prompts.enabled", true)) return;

        long initialDelay = plugin.getConfig().getLong("auth-settings.prompts.initial-delay-ticks", 20L);

        new BukkitRunnable() {
            private long nextPromptAt = 0L;
            private String lastState = "";
            private final Set<String> sentOnceStates = new HashSet<>();

            @Override
            public void run() {
                if (!player.isOnline()) {
                    this.cancel();
                    return;
                }

                if (plugin.isAuthenticated(player)) {
                    plugin.hideAuthBossBar(player);
                    this.cancel();
                    return;
                }

                plugin.updateAuthBossBar(player);

                String state = getPromptState(player);
                long now = System.currentTimeMillis();
                if (!state.equals(lastState)) {
                    lastState = state;
                    nextPromptAt = now;
                }

                if (now < nextPromptAt) {
                    return;
                }

                boolean repeatEnabled = plugin.getConfig().getBoolean("auth-settings.prompts." + state + "-repeat-enabled", true);
                if (!repeatEnabled && sentOnceStates.contains(state)) {
                    return;
                }

                sendPromptForState(player, state);
                sentOnceStates.add(state);

                long interval = getPromptIntervalTicks(state);
                nextPromptAt = now + ticksToMillis(interval);
            }
        }.runTaskTimer(plugin, initialDelay, 20L);
    }

    private String getPromptState(Player player) {
        if (plugin.isCaptchaRequired(player)) {
            return "captcha";
        }
        if (plugin.isWaitingTwoFactor(player) || plugin.shouldSkipPasswordLoginForTwoFactor(player)) {
            return "two-factor";
        }
        if (plugin.isAccountRegistered(player.getUniqueId())) {
            return plugin.getAuthMode(player.getUniqueId()).equals("PIN") ? "pin" : "login";
        }
        return plugin.defaultAuthMode().equals("PIN") ? "setpin" : "register";
    }

    private void sendPromptForState(Player player, String state) {
        switch (state) {
            case "captcha" -> plugin.sendCaptchaPrompt(player);
            case "login" -> plugin.sendMessage(player, "messages.login-prompt", true);
            case "register" -> plugin.sendMessage(player, "messages.register-prompt", true);
            case "pin" -> plugin.sendMessage(player, "messages.pin-prompt", true);
            case "setpin" -> plugin.sendMessage(player, "messages.setpin-prompt", true);
            case "two-factor" -> {
                if (plugin.shouldSkipPasswordLoginForTwoFactor(player)) {
                    plugin.startTwoFactorLogin(player);
                } else {
                    plugin.sendMessage(player, "messages.two-factor-login-required", true);
                }
            }
            default -> {
            }
        }
    }

    private long getPromptIntervalTicks(String state) {
        String path = "auth-settings.prompts." + state + "-interval-ticks";
        if (plugin.getConfig().contains(path)) {
            return Math.max(20L, plugin.getConfig().getLong(path, 200L));
        }
        return Math.max(20L, plugin.getConfig().getLong("auth-settings.prompts.interval-ticks", 200L));
    }

    private long ticksToMillis(long ticks) {
        return Math.max(1L, ticks) * 50L;
    }

    private void startAuthTimeout(Player player) {
        if (!plugin.getConfig().getBoolean("auth-settings.timeout.enabled", true)) return;

        long timeoutTicks = plugin.getConfig().getLong("auth-settings.timeout.ticks", 1200L);
        plugin.markAuthTimeout(player, timeoutTicks);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || plugin.isAuthenticated(player)) return;

            plugin.logInfo(
                    player.getName() + " giriş yapmadığı için zaman aşımından dolayı sunucudan atıldı.",
                    player.getName() + " was kicked due to authentication timeout."
            );

            if (plugin.getConfig().getBoolean("auth-settings.timeout.kick", true)) {
                plugin.restorePreviousLocation(player);
                plugin.restoreAuthInventory(player);
                plugin.hideAuthBossBar(player);
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
        boolean authenticated = plugin.isAuthenticated(player);
        if (authenticated) {
            plugin.updateAccountSnapshot(player, false);
            if (plugin.getConfig().getBoolean("console-logging.log-auth-state-changes", true)) {
                plugin.logInfo(
                        player.getName() + " giriş yapmış durumdayken oyundan çıktı.",
                        player.getName() + " left the game while authenticated."
                );
            }
        } else {
            plugin.restorePreviousLocation(player);
            plugin.restoreAuthInventory(player);
            if (plugin.getConfig().getBoolean("console-logging.log-auth-state-changes", true)) {
                plugin.logInfo(
                        player.getName() + " giriş yapmadan oyundan çıktı.",
                        player.getName() + " left the game before authentication."
                );
            }
        }

        plugin.getLoggedInPlayers().remove(uuid);
        plugin.getUnauthenticatedPlayers().remove(uuid);
        plugin.getPreviousLocations().remove(uuid);
        plugin.getWrongPasswordAttempts().remove(uuid);
        plugin.getWrongPinAttempts().remove(uuid);
        plugin.clearAuthTimeout(uuid);
        plugin.clearCaptchaState(uuid);
        plugin.clearTwoFactorState(uuid);
        plugin.hideAuthBossBar(player);
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
        if (plugin.isAuthenticated(player)) {
            if (plugin.isCustomCommandOnCooldown(player, event.getMessage())) {
                event.setCancelled(true);
                plugin.sendMessage(player, "messages.security-cooldown-active", true,
                        java.util.Map.of("time", plugin.formatDuration(plugin.getCustomCommandCooldownRemainingMillis(player, event.getMessage()))));
                return;
            }
            plugin.markCustomCommandCooldown(player, event.getMessage());
            return;
        }

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
            case "register", "kayitol", "kayit", "kayıtol" -> "register";
            case "pin" -> "pin";
            case "setpin", "pinayarla" -> "setpin";
            case "captcha", "dogrula", "doğrula" -> "captcha";
            case "twofactor", "2fa", "authenticator", "authy" -> "twofactor";
            case "recover", "sifresifirla", "şifresıfırla", "kurtar" -> "recover";
            default -> null;
        };

        if (commandName == null) return;

        if (List.of("login", "register", "pin", "setpin").contains(commandName) && plugin.isCaptchaRequired(event.getPlayer())) {
            event.setCancelled(true);
            plugin.sendMessage(event.getPlayer(), "messages.captcha-required-before-auth", true);
            plugin.sendCaptchaPrompt(event.getPlayer());
            return;
        }

        if (List.of("login", "register", "pin", "setpin").contains(commandName)
                && (plugin.isWaitingTwoFactor(event.getPlayer()) || plugin.shouldSkipPasswordLoginForTwoFactor(event.getPlayer()))) {
            event.setCancelled(true);
            if (plugin.shouldSkipPasswordLoginForTwoFactor(event.getPlayer())) {
                plugin.startTwoFactorLogin(event.getPlayer());
            } else {
                plugin.sendMessage(event.getPlayer(), "messages.two-factor-login-required", true);
            }
            return;
        }

        PluginCommand command = plugin.getCommand(commandName);
        if (command == null) return;

        String[] args = new String[Math.max(0, parts.length - 1)];
        if (parts.length > 1) {
            System.arraycopy(parts, 1, args, 0, args.length);
        }

        event.setCancelled(true);
        if (commandName.equals("captcha")) {
            new CaptchaCommand(plugin).onCommand(event.getPlayer(), command, label, args);
        } else if (commandName.equals("twofactor")) {
            new TwoFactorCommand(plugin).onCommand(event.getPlayer(), command, label, args);
        } else if (commandName.equals("recover")) {
            new RecoverCommand(plugin).onCommand(event.getPlayer(), command, label, args);
        } else if (commandName.equals("pin") || commandName.equals("setpin")) {
            new PinCommand(plugin).onCommand(event.getPlayer(), command, label, args);
        } else {
            new AuthCommand(plugin).onCommand(event.getPlayer(), command, label, args);
        }
    }

    private boolean isAllowedCommand(String message) {
        for (String captchaCommand : List.of("/captcha", "/dogrula", "/doğrula")) {
            if (message.equals(captchaCommand) || message.startsWith(captchaCommand + " ")) {
                return true;
            }
        }

        for (String twoFactorCommand : List.of("/twofactor", "/2fa", "/authenticator", "/authy")) {
            if (message.equals(twoFactorCommand) || message.startsWith(twoFactorCommand + " ")) {
                return true;
            }
        }

        for (String recoverCommand : List.of("/recover", "/sifresifirla", "/şifresıfırla", "/kurtar")) {
            if (message.equals(recoverCommand) || message.startsWith(recoverCommand + " ")) {
                return true;
            }
        }

        for (String pinCommand : List.of("/pin", "/setpin", "/pinayarla")) {
            if (message.equals(pinCommand) || message.startsWith(pinCommand + " ")) {
                return true;
            }
        }

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
