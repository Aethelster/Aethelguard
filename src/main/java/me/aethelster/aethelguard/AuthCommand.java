package me.aethelster.aethelguard;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.Location;
import org.mindrot.jbcrypt.BCrypt;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

public class AuthCommand implements CommandExecutor {

    private final Aethelguard plugin;

    public AuthCommand(Aethelguard plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getRawStringMessage("messages.only-players", false));
            return true;
        }

        UUID playerUUID = player.getUniqueId();
        String uuidStr = playerUUID.toString();
        String ipAddress = player.getAddress() != null ? player.getAddress().getAddress().getHostAddress() : "UNKNOWN";

        if (command.getName().equalsIgnoreCase("register")) {
            handleRegister(player, playerUUID, uuidStr, ipAddress, args);
            return true;
        }

        if (command.getName().equalsIgnoreCase("login")) {
            handleLogin(player, playerUUID, uuidStr, ipAddress, args);
            return true;
        }

        return false;
    }

    private void handleRegister(Player player, UUID playerUUID, String uuidStr, String ipAddress, String[] args) {
        if (args.length < 2) {
            plugin.getServer().getScheduler().runTask(plugin, () ->
                    plugin.sendMessage(player, "messages.register-usage", true)
            );
            return;
        }

        if (!args[0].equals(args[1])) {
            plugin.getServer().getScheduler().runTask(plugin, () ->
                    plugin.sendMessage(player, "messages.passwords-dont-match", true)
            );
            return;
        }

        Aethelguard.PasswordPolicyResult passwordPolicy = plugin.validatePasswordPolicy(args[0], player.getName());
        if (!passwordPolicy.valid()) {
            plugin.sendMessage(player, passwordPolicy.messagePath(), true, passwordPolicy.placeholders());
            return;
        }

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            if (plugin.isAccountRegistered(player.getUniqueId())) {
                plugin.getServer().getScheduler().runTask(plugin, () ->
                        plugin.sendMessage(player, "messages.already-registered", true)
                );
                return;
            }

            int maxAccounts = plugin.getConfig().getInt("auth-settings.registration.ip-limit.max-accounts", 2);
            if (isIpRegistrationLimitReached(player, ipAddress, maxAccounts)) {
                plugin.getServer().getScheduler().runTask(plugin, () ->
                        plugin.sendMessage(player, "messages.ip-registration-limit-reached", true,
                                java.util.Map.of("limit", String.valueOf(maxAccounts)))
                );
                return;
            }

            String hashedPassword = BCrypt.hashpw(args[0], BCrypt.gensalt());

            if (registerPlayer(uuidStr, player.getName(), hashedPassword, player, ipAddress)) {
                plugin.getUnauthenticatedPlayers().remove(playerUUID);

                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    plugin.playConfiguredSound(player, "auth-settings.sounds.register-success");
                    plugin.completeLogin(player, false);
                    plugin.rememberAuthSession(player);
                    player.teleport(player.getWorld().getSpawnLocation());
                    plugin.updateAccountSnapshot(player, true);
                    logAuthSuccess(player, true);
                    plugin.sendMessage(player, "messages.register-success", true);
                });
            } else {
                plugin.getServer().getScheduler().runTask(plugin, () ->
                        plugin.sendMessage(player, "messages.register-error", true)
                );
            }
        });
    }

    private void handleLogin(Player player, UUID playerUUID, String uuidStr, String ipAddress, String[] args) {
        if (args.length < 1) {
            plugin.sendMessage(player, "messages.login-usage", true);
            return;
        }

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            String storedHash = getStoredPassword(uuidStr);

            if (storedHash == null) {
                plugin.getServer().getScheduler().runTask(plugin, () ->
                        plugin.sendMessage(player, "messages.not-registered", true)
                );
                return;
            }

            if (BCrypt.checkpw(args[0], storedHash)) {
                if (plugin.isTwoFactorEnabled(player)) {
                    plugin.getServer().getScheduler().runTask(plugin, () ->
                            plugin.startTwoFactorLogin(player)
                    );
                    return;
                }

                plugin.getUnauthenticatedPlayers().remove(playerUUID);
                plugin.getWrongPasswordAttempts().remove(playerUUID);

                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    plugin.playConfiguredSound(player, "auth-settings.sounds.login-success");
                    plugin.completeLogin(player, true);
                    plugin.rememberAuthSession(player);
                    plugin.updateAccountSnapshot(player, true);
                    logAuthSuccess(player, false);
                });
                return;
            }

            handleWrongPassword(player, playerUUID);
        });
    }

    private void handleWrongPassword(Player player, UUID playerUUID) {
        int attempts = plugin.getWrongPasswordAttempts().getOrDefault(playerUUID, 0) + 1;
        plugin.getWrongPasswordAttempts().put(playerUUID, attempts);
        recordWrongPasswordAttempt(player);

        plugin.getServer().getScheduler().runTask(plugin, () ->
                plugin.playConfiguredSound(player, "auth-settings.sounds.wrong-password")
        );

        int maxAttempts = plugin.getConfig().getInt("auth-settings.wrong-password.max-attempts", 3);
        boolean kickEnabled = plugin.getConfig().getBoolean("auth-settings.wrong-password.kick-enabled", true);

        if (kickEnabled && attempts >= maxAttempts) {
            if (plugin.getConfig().getBoolean("recovery.enabled", true)
                    && plugin.getConfig().getBoolean("recovery.offer-before-kick", true)) {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    Aethelguard.SecurityQuestion question = plugin.getStoredSecurityQuestion(playerUUID);
                    if (question != null) {
                        plugin.sendMessage(player, "messages.recover-question-hint", true, Map.of("question", question.text()));
                    }
                    plugin.sendMessage(player, "messages.recover-before-kick", true);
                });
                plugin.getWrongPasswordAttempts().remove(playerUUID);
                return;
            }

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                    plugin.restorePreviousLocation(player);
                    plugin.restoreAuthInventory(player);
                    plugin.hideAuthBossBar(player);
                    player.kickPlayer(plugin.getRawStringMessage("messages.wrong-password-kick", true, Map.of(
                            "max", String.valueOf(maxAttempts)
                    )));
            });
            plugin.getWrongPasswordAttempts().remove(playerUUID);
            return;
        }

        String wrongMsg = plugin.getFormattedMessageString("messages.wrong-password", true)
                .replace("{remaining}", String.valueOf(Math.max(0, maxAttempts - attempts)))
                .replace("{max}", String.valueOf(maxAttempts));

        plugin.getServer().getScheduler().runTask(plugin, () ->
                player.sendMessage(
                        plugin.getMiniMessage().deserialize(wrongMsg.replace("&", "§"))
                )
        );
    }

    private void logAuthSuccess(Player player, boolean registered) {
        if (!plugin.getConfig().getBoolean("console-logging.log-auth-success", true)) return;

        if (registered) {
            plugin.logInfo(
                    player.getName() + " başarıyla kayıt oldu.",
                    player.getName() + " registered successfully."
            );
        } else {
            plugin.logInfo(
                    player.getName() + " başarıyla giriş yaptı.",
                    player.getName() + " logged in successfully."
            );
        }
    }

    private boolean registerPlayer(String uuid, String username, String hashedPassword, Player player, String ipAddress) {
        String now = currentDate();
        Location location = player.getLocation();

        if (!plugin.getConfig().getBoolean("database.enabled", false)) {
            File userFile = new File(plugin.getLocalUsersFolder(), uuid + ".yml");
            userFile.getParentFile().mkdirs();

            FileConfiguration config = YamlConfiguration.loadConfiguration(userFile);
            config.set("uuid", uuid);
            config.set("username", username);
            config.set("password", hashedPassword);
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
            } catch (IOException e) {
                return false;
            }
        }

        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            if (conn == null) return false;
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO " + plugin.getAuthTableName() +
                            " (uuid, username, password, registration_ip, last_ip, last_world, last_x, last_y, last_z, login_count) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?);"
            )) {
                ps.setString(1, uuid);
                ps.setString(2, username);
                ps.setString(3, hashedPassword);
                ps.setString(4, ipAddress);
                ps.setString(5, ipAddress);
                ps.setString(6, location.getWorld() == null ? "UNKNOWN" : location.getWorld().getName());
                ps.setDouble(7, location.getX());
                ps.setDouble(8, location.getY());
                ps.setDouble(9, location.getZ());
                ps.setInt(10, 0);
                ps.executeUpdate();
                return true;
            }
        } catch (SQLException e) {
            return false;
        }
    }

    private void recordWrongPasswordAttempt(Player player) {
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

    private boolean isIpRegistrationLimitReached(Player player, String ipAddress, int maxAccounts) {
        if (!plugin.getConfig().getBoolean("auth-settings.registration.ip-limit.enabled", true)) return false;
        if (maxAccounts <= 0) return false;

        String bypassPermission = plugin.getConfig().getString("auth-settings.registration.ip-limit.bypass-permission", "aethelguard.bypass.iplimit");
        if (bypassPermission != null && !bypassPermission.isBlank() && player.hasPermission(bypassPermission)) return false;

        return countAccountsByIp(ipAddress) >= maxAccounts;
    }

    private int countAccountsByIp(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank() || ipAddress.equalsIgnoreCase("UNKNOWN")) return 0;

        if (!plugin.getConfig().getBoolean("database.enabled", false)) {
            File[] files = plugin.getLocalUsersFolder().listFiles((dir, name) -> name.endsWith(".yml"));
            if (files == null) return 0;

            int count = 0;
            for (File file : files) {
                FileConfiguration config = YamlConfiguration.loadConfiguration(file);
                String registrationIp = config.getString("registration-ip", config.getString("last-ip", ""));
                if (ipAddress.equals(registrationIp)) count++;
            }
            return count;
        }

        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            if (conn == null) return 0;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM " + plugin.getAuthTableName() + " WHERE COALESCE(registration_ip, last_ip) = ?;"
            )) {
                ps.setString(1, ipAddress);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getInt(1);
                }
            }
        } catch (SQLException ignored) {
        }
        return 0;
    }

    private String currentDate() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
    }

    private String getStoredPassword(String uuid) {
        if (!plugin.getConfig().getBoolean("database.enabled", false)) {
            File userFile = new File(plugin.getLocalUsersFolder(), uuid + ".yml");
            if (!userFile.exists()) return null;
            return YamlConfiguration.loadConfiguration(userFile).getString("password");
        }

        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            if (conn == null) return null;
            try (PreparedStatement ps = conn.prepareStatement("SELECT password FROM " + plugin.getAuthTableName() + " WHERE uuid = ?;")) {
                ps.setString(1, uuid);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getString("password");
                }
            }
        } catch (SQLException e) {
            return null;
        }
        return null;
    }
}
