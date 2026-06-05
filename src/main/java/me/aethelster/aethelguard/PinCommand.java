package me.aethelster.aethelguard;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.mindrot.jbcrypt.BCrypt;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

public class PinCommand implements CommandExecutor {

    private final Aethelguard plugin;

    public PinCommand(Aethelguard plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getRawStringMessage("messages.only-players", false));
            return true;
        }

        String name = command.getName().toLowerCase();
        return switch (name) {
            case "pin" -> handlePinLogin(player, args);
            case "setpin" -> handleSetPin(player, args);
            case "changepin" -> handleChangePin(player, args);
            case "authmode" -> handleAuthMode(player, args);
            default -> false;
        };
    }

    private boolean handlePinLogin(Player player, String[] args) {
        UUID uuid = player.getUniqueId();

        if (plugin.isTemporarilyAuthLocked(uuid)) {
            plugin.kickTemporaryAuthLocked(player, "messages.auth-lockout-active-kick");
            return true;
        }

        if (!plugin.getConfig().getBoolean("auth-settings.pin.enabled", true)) {
            plugin.sendMessage(player, "messages.pin-disabled", true);
            return true;
        }
        if (!plugin.isAccountRegistered(uuid)) {
            plugin.sendMessage(player, "messages.not-registered", true);
            return true;
        }
        if (!plugin.getAuthMode(uuid).equals("PIN")) {
            plugin.sendMessage(player, "messages.login-required", true);
            return true;
        }
        if (args.length < 1) {
            if (plugin.openPinGuiIfNeeded(player)) {
                return true;
            }
            plugin.sendMessage(player, "messages.pin-usage", true);
            return true;
        }

        submitPinLogin(player, args[0]);
        return true;
    }

    public void submitPinLogin(Player player, String pin) {
        submitPinLogin(player, pin, false);
    }

    public void submitPinLogin(Player player, String pin, boolean fromGui) {
        UUID uuid = player.getUniqueId();

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            String pinHash = plugin.getPinHash(uuid);
            if (pinHash == null || pinHash.isBlank()) {
                plugin.getServer().getScheduler().runTask(plugin, () ->
                        sendIfOnline(player, "messages.pin-not-set")
                );
                return;
            }

            if (BCrypt.checkpw(pin, pinHash)) {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) return;
                    if (plugin.beginMissingRegistrationSecurityQuestion(player, null)) {
                        plugin.closePinGui(player);
                        return;
                    }
                    plugin.getUnauthenticatedPlayers().remove(uuid);
                    plugin.getWrongPinAttempts().remove(uuid);
                    plugin.closePinGui(player);
                    plugin.playConfiguredSound(player, fromGui ? "auth-settings.sounds.pin-gui-success" : "auth-settings.sounds.login-success");
                    plugin.completeLogin(player, true);
                    plugin.rememberAuthSession(player);
                    plugin.updateAccountSnapshot(player, true);
                    logPinSuccess(player);
                });
                return;
            }

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    handleWrongPin(player);
                }
            });
        });
    }

    private boolean handleSetPin(Player player, String[] args) {
        UUID uuid = player.getUniqueId();

        if (args.length < 2) {
            if (plugin.openPinGuiIfNeeded(player)) {
                return true;
            }
            plugin.sendMessage(player, "messages.setpin-usage", true);
            return true;
        }
        if (!args[0].equals(args[1])) {
            plugin.sendMessage(player, "messages.pins-dont-match", true);
            return true;
        }

        Aethelguard.PinPolicyResult policy = plugin.validatePinPolicy(args[0]);
        if (!policy.valid()) {
            plugin.sendMessage(player, policy.messagePath(), true, policy.placeholders());
            return true;
        }

        submitSetPin(player, args[0]);
        return true;
    }

    public void submitSetPin(Player player, String pin) {
        submitSetPin(player, pin, false);
    }

    public void submitSetPin(Player player, String pin, boolean fromGui) {
        UUID uuid = player.getUniqueId();
        String playerName = player.getName();
        String ipAddress = player.getAddress() != null ? player.getAddress().getAddress().getHostAddress() : "UNKNOWN";
        Location registrationLocation = player.getLocation().clone();
        boolean bypassIpLimit = hasIpLimitBypass(player);
        boolean authenticatedAtSubmit = plugin.isAuthenticated(player);

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            if (!plugin.isAccountRegistered(uuid)) {
                registerWithPin(player, playerName, uuid, registrationLocation, ipAddress, bypassIpLimit, pin, fromGui);
                return;
            }

            if (!authenticatedAtSubmit) {
                plugin.getServer().getScheduler().runTask(plugin, () ->
                        sendIfOnline(player, "messages.setpin-auth-required")
                );
                return;
            }

            String existingHash = plugin.getPinHash(uuid);
            if (existingHash != null && !existingHash.isBlank()) {
                plugin.getServer().getScheduler().runTask(plugin, () ->
                        sendIfOnline(player, "messages.pin-already-set")
                );
                return;
            }

            if (plugin.updateAccountPin(uuid, BCrypt.hashpw(pin, BCrypt.gensalt()))) {
                plugin.getServer().getScheduler().runTask(plugin, () ->
                        sendIfOnline(player, "messages.pin-created")
                );
            } else {
                plugin.getServer().getScheduler().runTask(plugin, () ->
                        sendIfOnline(player, "messages.pin-error")
                );
            }
        });
    }

    private boolean handleChangePin(Player player, String[] args) {
        UUID uuid = player.getUniqueId();

        if (!plugin.isAuthenticated(player)) {
            plugin.sendMessage(player, "messages.changepin-auth-required", true);
            return true;
        }
        if (args.length < 3) {
            plugin.sendMessage(player, "messages.changepin-usage", true);
            return true;
        }
        if (!args[1].equals(args[2])) {
            plugin.sendMessage(player, "messages.pins-dont-match", true);
            return true;
        }

        Aethelguard.PinPolicyResult policy = plugin.validatePinPolicy(args[1]);
        if (!policy.valid()) {
            plugin.sendMessage(player, policy.messagePath(), true, policy.placeholders());
            return true;
        }

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            String pinHash = plugin.getPinHash(uuid);
            if (pinHash == null || pinHash.isBlank()) {
                plugin.getServer().getScheduler().runTask(plugin, () ->
                        plugin.sendMessage(player, "messages.pin-not-set", true)
                );
                return;
            }
            if (!BCrypt.checkpw(args[0], pinHash)) {
                plugin.getServer().getScheduler().runTask(plugin, () ->
                        sendIfOnline(player, "messages.pin-invalid")
                );
                return;
            }
            if (plugin.updateAccountPin(uuid, BCrypt.hashpw(args[1], BCrypt.gensalt()))) {
                plugin.getServer().getScheduler().runTask(plugin, () ->
                        sendIfOnline(player, "messages.pin-changed")
                );
            } else {
                plugin.getServer().getScheduler().runTask(plugin, () ->
                        sendIfOnline(player, "messages.pin-error")
                );
            }
        });
        return true;
    }

    private boolean handleAuthMode(Player player, String[] args) {
        UUID uuid = player.getUniqueId();

        if (!plugin.isAuthenticated(player)) {
            plugin.sendMessage(player, "messages.auth-mode-auth-required", true);
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            plugin.sendMessage(player, "messages.auth-mode-current", true,
                    Map.of("mode", plugin.getAuthMode(uuid)));
            return true;
        }
        if (!plugin.getConfig().getBoolean("auth-settings.pin.allow-player-auth-mode-change", true)) {
            plugin.sendMessage(player, "messages.auth-mode-change-disabled", true);
            return true;
        }

        String requested = args[0].equalsIgnoreCase("pin") ? "PIN"
                : args[0].equalsIgnoreCase("password") ? "PASSWORD" : "";
        if (requested.isBlank()) {
            plugin.sendMessage(player, "messages.auth-mode-usage", true);
            return true;
        }
        if (requested.equals("PIN")) {
            String pinHash = plugin.getPinHash(uuid);
            if (pinHash == null || pinHash.isBlank()) {
                plugin.sendMessage(player, "messages.auth-mode-pin-missing", true);
                return true;
            }
        }
        if (requested.equals("PASSWORD") && !plugin.isPasswordUsable(uuid)) {
            plugin.sendMessage(player, "messages.auth-mode-password-missing", true);
            return true;
        }

        if (plugin.setAuthMode(uuid, requested)) {
            plugin.sendMessage(player, "messages.auth-mode-changed", true, Map.of("mode", requested));
        } else {
            plugin.sendMessage(player, "messages.auth-mode-error", true);
        }
        return true;
    }

    private void registerWithPin(Player player, String playerName, UUID playerUuid, Location location,
                                 String ipAddress, boolean bypassIpLimit, String pin, boolean fromGui) {
        int maxAccounts = plugin.getConfig().getInt("auth-settings.registration.ip-limit.max-accounts", 2);
        if (isIpRegistrationLimitReached(ipAddress, maxAccounts, bypassIpLimit)) {
            plugin.getServer().getScheduler().runTask(plugin, () ->
                    sendIfOnline(player, "messages.ip-registration-limit-reached", Map.of("limit", String.valueOf(maxAccounts)))
            );
            return;
        }

        String pinHash = BCrypt.hashpw(pin, BCrypt.gensalt());
        String disabledPasswordHash = BCrypt.hashpw(UUID.randomUUID().toString(), BCrypt.gensalt());
        boolean registered = plugin.getConfig().getBoolean("database.enabled", false)
                ? registerDatabasePinPlayer(playerUuid, playerName, location, disabledPasswordHash, pinHash, ipAddress)
                : registerLocalPinPlayer(playerUuid, playerName, location, disabledPasswordHash, pinHash, ipAddress);

        if (!registered) {
            plugin.getServer().getScheduler().runTask(plugin, () ->
                    sendIfOnline(player, "messages.register-error")
            );
            return;
        }

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            plugin.closePinGui(player);
            if (plugin.beginRegistrationSecurityQuestion(player)) {
                return;
            }
            plugin.getUnauthenticatedPlayers().remove(player.getUniqueId());
            plugin.playConfiguredSound(player, fromGui ? "auth-settings.sounds.pin-gui-success" : "auth-settings.sounds.register-success");
            plugin.completeLogin(player, false);
            plugin.rememberAuthSession(player);
            player.teleport(player.getWorld().getSpawnLocation());
            plugin.updateAccountSnapshot(player, true);
            logPinRegister(player);
            plugin.sendMessage(player, "messages.pin-register-success", true);
        });
    }

    private boolean registerLocalPinPlayer(UUID playerUuid, String playerName, Location location, String passwordHash, String pinHash, String ipAddress) {
        String uuid = playerUuid.toString();
        String now = currentDate();
        File userFile = new File(plugin.getLocalUsersFolder(), uuid + ".yml");
        userFile.getParentFile().mkdirs();

        FileConfiguration config = YamlConfiguration.loadConfiguration(userFile);
        config.set("uuid", uuid);
        config.set("username", playerName);
        config.set("auth-mode", "PIN");
        config.set("password.hash", passwordHash);
        config.set("password.usable", false);
        config.set("pin.hash", pinHash);
        config.set("created-at", now);
        config.set("registration-ip", ipAddress);
        config.set("last-login", now);
        config.set("last-ip", ipAddress);
        config.set("last-world", location.getWorld() == null ? "UNKNOWN" : location.getWorld().getName());
        config.set("last-location.x", location.getX());
        config.set("last-location.y", location.getY());
        config.set("last-location.z", location.getZ());
        config.set("stats.login-count", 0);
        config.set("security.wrong-attempts-total", 0);
        config.set("security.last-wrong-attempt", null);
        config.set("security-question.id", null);
        config.set("security-question.text", null);
        config.set("security-question.answer-hash", null);
        config.set("security.cooldowns", null);
        config.set("recovery.method", "question");
        config.set("backup-codes.hashes", null);
        config.set("two-factor.enabled", false);
        config.set("two-factor.secret", null);
        try {
            config.save(userFile);
            plugin.rebuildUserIndex();
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    private boolean registerDatabasePinPlayer(UUID playerUuid, String playerName, Location location, String passwordHash, String pinHash, String ipAddress) {
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            if (conn == null) return false;
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO " + plugin.getAuthTableName() +
                            " (uuid, username, password, password_usable, auth_mode, pin_hash, registration_ip, last_ip, last_world, last_x, last_y, last_z, login_count) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);"
            )) {
                ps.setString(1, playerUuid.toString());
                ps.setString(2, playerName);
                ps.setString(3, passwordHash);
                ps.setBoolean(4, false);
                ps.setString(5, "PIN");
                ps.setString(6, pinHash);
                ps.setString(7, ipAddress);
                ps.setString(8, ipAddress);
                ps.setString(9, location.getWorld() == null ? "UNKNOWN" : location.getWorld().getName());
                ps.setDouble(10, location.getX());
                ps.setDouble(11, location.getY());
                ps.setDouble(12, location.getZ());
                ps.setInt(13, 0);
                ps.executeUpdate();
                return true;
            }
        } catch (SQLException ignored) {
            return false;
        }
    }

    private void handleWrongPin(Player player) {
        UUID uuid = player.getUniqueId();
        int attempts = plugin.getWrongPinAttempts().getOrDefault(uuid, 0) + 1;
        plugin.getWrongPinAttempts().put(uuid, attempts);
        recordWrongPinAttempt(player);

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (plugin.showPinGuiError(player)) {
                plugin.playConfiguredSound(player, "auth-settings.sounds.pin-gui-wrong");
            } else {
                plugin.playConfiguredSound(player, "auth-settings.sounds.wrong-password");
            }
        });

        int maxAttempts = plugin.getConfig().getInt("auth-settings.pin.wrong-pin.max-attempts", 3);
        boolean kickEnabled = plugin.getConfig().getBoolean("auth-settings.pin.wrong-pin.kick-enabled", true);
        if (kickEnabled && attempts >= maxAttempts) {
            boolean locked = plugin.registerFailedAuthKick(uuid);
            plugin.getWrongPinAttempts().remove(uuid);
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                plugin.restorePreviousLocation(player);
                plugin.restoreAuthInventory(player);
                plugin.hideAuthBossBar(player);
                if (locked) {
                    player.kickPlayer(plugin.getRawStringMessage("messages.auth-lockout-triggered-kick", true,
                            Map.of("time", plugin.formatDuration(plugin.getTemporaryAuthLockoutRemainingMillis(uuid)))));
                } else {
                    player.kickPlayer(plugin.getRawStringMessage("messages.wrong-pin-kick", true,
                            Map.of("max", String.valueOf(maxAttempts))));
                }
            });
            return;
        }

        plugin.getServer().getScheduler().runTask(plugin, () ->
                plugin.sendMessage(player, "messages.pin-invalid", true,
                        Map.of("remaining", String.valueOf(Math.max(0, maxAttempts - attempts)),
                                "max", String.valueOf(maxAttempts)))
        );
    }

    private void recordWrongPinAttempt(Player player) {
        String uuid = player.getUniqueId().toString();
        String now = currentDate();

        if (!plugin.getConfig().getBoolean("database.enabled", false)) {
            File userFile = new File(plugin.getLocalUsersFolder(), uuid + ".yml");
            if (!userFile.exists()) return;
            FileConfiguration config = YamlConfiguration.loadConfiguration(userFile);
            config.set("security.wrong-attempts-total", config.getInt("security.wrong-attempts-total", 0) + 1);
            config.set("security.last-wrong-attempt", now);
            try {
                config.save(userFile);
            } catch (IOException ignored) {
            }
            return;
        }

        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            if (conn == null) return;
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE " + plugin.getAuthTableName() +
                            " SET wrong_attempts_total = wrong_attempts_total + 1, last_wrong_attempt = ? WHERE uuid = ?;"
            )) {
                ps.setString(1, now);
                ps.setString(2, uuid);
                ps.executeUpdate();
            }
        } catch (SQLException ignored) {
        }
    }

    private boolean isIpRegistrationLimitReached(String ipAddress, int maxAccounts, boolean bypassIpLimit) {
        if (!plugin.getConfig().getBoolean("auth-settings.registration.ip-limit.enabled", true)) return false;
        if (maxAccounts <= 0) return false;
        if (bypassIpLimit) return false;
        return plugin.countAccountsByIp(ipAddress) >= maxAccounts;
    }

    private boolean hasIpLimitBypass(Player player) {
        String bypassPermission = plugin.getConfig().getString("auth-settings.registration.ip-limit.bypass-permission", "aethelguard.bypass.iplimit");
        return bypassPermission != null && !bypassPermission.isBlank() && player.hasPermission(bypassPermission);
    }

    private void logPinRegister(Player player) {
        if (!plugin.getConfig().getBoolean("console-logging.log-auth-success", true)) return;
        plugin.logInfo(
                player.getName() + " PIN ile başarıyla kayıt oldu.",
                player.getName() + " registered successfully with PIN."
        );
    }

    private void logPinSuccess(Player player) {
        if (!plugin.getConfig().getBoolean("console-logging.log-auth-success", true)) return;
        plugin.logInfo(
                player.getName() + " PIN ile başarıyla giriş yaptı.",
                player.getName() + " logged in successfully with PIN."
        );
    }

    private void sendIfOnline(Player player, String messagePath) {
        if (player.isOnline()) {
            plugin.sendMessage(player, messagePath, true);
        }
    }

    private void sendIfOnline(Player player, String messagePath, Map<String, String> placeholders) {
        if (player.isOnline()) {
            plugin.sendMessage(player, messagePath, true, placeholders);
        }
    }

    private String currentDate() {
        return plugin.formatDate(new Date());
    }
}
