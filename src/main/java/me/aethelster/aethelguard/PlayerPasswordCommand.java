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
import java.util.Date;
import java.util.UUID;

public class PlayerPasswordCommand implements CommandExecutor {

    private final Aethelguard plugin;

    public PlayerPasswordCommand(Aethelguard plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getRawStringMessage("messages.only-players", false));
            return true;
        }

        if (!plugin.isAuthenticated(player)) {
            plugin.sendMessage(player, "messages.change-password-auth-required", true);
            return true;
        }

        if (args.length < 3) {
            plugin.sendMessage(player, "messages.change-password-usage", true);
            return true;
        }

        long remaining = plugin.getSecurityCooldownRemainingMillis(player, "changepassword");
        if (remaining > 0L) {
            plugin.sendMessage(player, "messages.security-cooldown-active", true,
                    java.util.Map.of("time", plugin.formatDuration(remaining)));
            return true;
        }

        if (!args[1].equals(args[2])) {
            plugin.sendMessage(player, "messages.passwords-dont-match", true);
            return true;
        }

        Aethelguard.PasswordPolicyResult passwordPolicy = plugin.validatePasswordPolicy(args[1], player.getName());
        if (!passwordPolicy.valid()) {
            plugin.sendMessage(player, passwordPolicy.messagePath(), true, passwordPolicy.placeholders());
            return true;
        }

        UUID uuid = player.getUniqueId();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            String storedHash = getStoredPassword(uuid);
            if (storedHash == null) {
                plugin.getServer().getScheduler().runTask(plugin, () -> sendIfOnline(player, "messages.not-registered"));
                return;
            }

            if (!BCrypt.checkpw(args[0], storedHash)) {
                plugin.getServer().getScheduler().runTask(plugin, () -> sendIfOnline(player, "messages.change-password-wrong-current"));
                return;
            }

            String newHash = BCrypt.hashpw(args[1], BCrypt.gensalt());
            if (updatePassword(uuid, newHash)) {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) return;
                    plugin.markSecurityCooldown(player, "changepassword");
                    plugin.sendMessage(player, "messages.change-password-success", true);

                    if (plugin.getConfig().getBoolean("console-logging.log-auth-state-changes", true)) {
                        plugin.logInfo(
                                player.getName() + " kendi şifresini değiştirdi.",
                                player.getName() + " changed their own password."
                        );
                    }
                });
            } else {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        plugin.sendMessage(player, "messages.change-password-error", true);
                    }
                });
            }
        });

        return true;
    }

    private String getStoredPassword(UUID playerUuid) {
        String uuid = playerUuid.toString();
        if (!plugin.getConfig().getBoolean("database.enabled", false)) {
            File userFile = new File(plugin.getLocalUsersFolder(), uuid + ".yml");
            if (!userFile.exists()) return null;
            FileConfiguration config = YamlConfiguration.loadConfiguration(userFile);
            String hash = config.getString("password.hash");
            if (hash != null && !hash.isBlank()) {
                return hash;
            }
            if (config.isString("password")) {
                String legacyHash = config.getString("password");
                if (legacyHash != null && !legacyHash.isBlank()) {
                    config.set("password.hash", legacyHash);
                    try {
                        config.save(userFile);
                    } catch (IOException ignored) {
                    }
                }
                return legacyHash;
            }
            return null;
        }

        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            if (conn == null) return null;
            try (PreparedStatement ps = conn.prepareStatement("SELECT password FROM " + plugin.getAuthTableName() + " WHERE uuid = ?;")) {
                ps.setString(1, uuid);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getString("password");
                }
            }
        } catch (SQLException ignored) {
        }
        return null;
    }

    private boolean updatePassword(UUID playerUuid, String hash) {
        String uuid = playerUuid.toString();
        if (!plugin.getConfig().getBoolean("database.enabled", false)) {
            File userFile = new File(plugin.getLocalUsersFolder(), uuid + ".yml");
            if (!userFile.exists()) return false;

            FileConfiguration config = YamlConfiguration.loadConfiguration(userFile);
            config.set("password.hash", hash);
            config.set("password.usable", true);
            config.set("security.last-password-change", plugin.formatDate(new Date()));
            try {
                config.save(userFile);
                return true;
            } catch (IOException ignored) {
                return false;
            }
        }

        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            if (conn == null) return false;
            try (PreparedStatement ps = conn.prepareStatement("UPDATE " + plugin.getAuthTableName() + " SET password = ?, password_usable = TRUE WHERE uuid = ?;")) {
                ps.setString(1, hash);
                ps.setString(2, uuid);
                return ps.executeUpdate() > 0;
            }
        } catch (SQLException ignored) {
            return false;
        }
    }

    private void sendIfOnline(Player player, String messagePath) {
        if (player.isOnline()) {
            plugin.sendMessage(player, messagePath, true);
        }
    }
}
