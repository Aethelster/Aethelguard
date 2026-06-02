package me.aethelster.aethelguard;

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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Date;
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

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            if (plugin.isAccountRegistered(player.getUniqueId())) {
                plugin.getServer().getScheduler().runTask(plugin, () ->
                        plugin.sendMessage(player, "messages.already-registered", true)
                );
                return;
            }

            String hashedPassword = BCrypt.hashpw(args[0], BCrypt.gensalt());

            if (registerPlayer(uuidStr, player.getName(), hashedPassword)) {
                plugin.getUnauthenticatedPlayers().remove(playerUUID);
                logPlayerAction(player, ipAddress, hashedPassword);

                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    plugin.playConfiguredSound(player, "auth-settings.sounds.register-success");
                    plugin.completeLogin(player, false);
                    player.teleport(player.getWorld().getSpawnLocation());
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
                plugin.getUnauthenticatedPlayers().remove(playerUUID);
                plugin.getWrongPasswordAttempts().remove(playerUUID);
                logPlayerAction(player, ipAddress, storedHash);

                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    plugin.playConfiguredSound(player, "auth-settings.sounds.login-success");
                    plugin.completeLogin(player, true);
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

        plugin.getServer().getScheduler().runTask(plugin, () ->
                plugin.playConfiguredSound(player, "auth-settings.sounds.wrong-password")
        );

        int maxAttempts = plugin.getConfig().getInt("auth-settings.wrong-password.max-attempts", 3);
        boolean kickEnabled = plugin.getConfig().getBoolean("auth-settings.wrong-password.kick-enabled", true);

        if (kickEnabled && attempts >= maxAttempts) {
            plugin.getServer().getScheduler().runTask(plugin, () ->
                    player.kickPlayer(plugin.getRawStringMessage("messages.wrong-password-kick", true))
            );
            plugin.getWrongPasswordAttempts().remove(playerUUID);
            return;
        }

        String wrongMsg = plugin.getFormattedMessageString("messages.wrong-password", true)
                .replace("{remaining}", String.valueOf(Math.max(0, maxAttempts - attempts)));

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

    private void logPlayerAction(Player player, String ipAddress, String hash) {
        String lastLoginDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        String lang = plugin.getConfig().getString("console-language", "en");
        String logData = (lang.equalsIgnoreCase("tr"))
                ? String.format("[%s]\n   UUID: %s\n   IP: %s\n   Şifre Hash: %s\n   Son Giriş Tarihi: %s",
                player.getName(), player.getUniqueId(), ipAddress, hash, lastLoginDate)
                : String.format("[%s]\n   UUID: %s\n   IP: %s\n   Password Hash: %s\n   Last LogIn Date: %s",
                player.getName(), player.getUniqueId(), ipAddress, hash, lastLoginDate);

        String fileName = plugin.getConfig().getString("local-logging.file-name", "users");
        plugin.writeToInternalLog(fileName, logData);
    }

    private boolean registerPlayer(String uuid, String username, String hashedPassword) {
        if (!plugin.getConfig().getBoolean("database.enabled", false)) {
            File userFile = new File(plugin.getLocalUsersFolder(), uuid + ".yml");
            userFile.getParentFile().mkdirs();

            FileConfiguration config = YamlConfiguration.loadConfiguration(userFile);
            config.set("username", username);
            config.set("password", hashedPassword);
            try {
                config.save(userFile);
                return true;
            } catch (IOException e) {
                return false;
            }
        }

        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            if (conn == null) return false;
            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO " + plugin.getAuthTableName() + " (uuid, username, password) VALUES (?, ?, ?);")) {
                ps.setString(1, uuid);
                ps.setString(2, username);
                ps.setString(3, hashedPassword);
                ps.executeUpdate();
                return true;
            }
        } catch (SQLException e) {
            return false;
        }
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
