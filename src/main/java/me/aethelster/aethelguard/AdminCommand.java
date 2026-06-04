package me.aethelster.aethelguard;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.OfflinePlayer;
import org.mindrot.jbcrypt.BCrypt;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class AdminCommand implements CommandExecutor, TabCompleter {

    private final Aethelguard plugin;

    public AdminCommand(Aethelguard plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("aethelguard.admin")) {
            plugin.sendMessage(sender, "messages.admin-no-permission", true);
            return true;
        }

        if (args.length == 0) {
            plugin.sendMessage(sender, "messages.admin-usage", true);
            return true;
        }

        String subCommand = args[0].toLowerCase(Locale.ROOT);

        if (subCommand.equals("reload")) {
            if (plugin.reloadPluginSettings()) {
                plugin.sendMessage(sender, "messages.admin-reload-success", true);
            } else {
                plugin.sendMessage(sender, "messages.admin-reload-failed", true);
            }
            return true;
        }

        if (subCommand.equals("status")) {
            if (!plugin.getConfig().getBoolean("status.enabled", true)) {
                plugin.sendMessage(sender, "messages.admin-status-disabled", true);
                return true;
            }

            if (args.length < 2) {
                plugin.sendMessage(sender, "messages.admin-status-usage", true);
                return true;
            }

            sendStatus(sender, plugin.getAccountStatus(args[1]));
            return true;
        }

        if (subCommand.equals("ipinfo")) {
            if (args.length < 2) {
                plugin.sendMessage(sender, "messages.admin-ipinfo-usage", true);
                return true;
            }

            sendIpInfo(sender, args[1]);
            return true;
        }

        if (subCommand.equals("accounts")) {
            if (args.length < 2) {
                plugin.sendMessage(sender, "messages.admin-accounts-usage", true);
                return true;
            }

            sendAccounts(sender, args[1]);
            return true;
        }

        if (subCommand.equals("pingui")) {
            if (!(sender instanceof Player player)) {
                plugin.sendMessage(sender, "messages.only-players", true);
                return true;
            }
            if (args.length < 3 || !args[1].equalsIgnoreCase("preview")) {
                plugin.sendMessage(sender, "messages.admin-pingui-preview-usage", true);
                return true;
            }
            String theme = args[2].toLowerCase(Locale.ROOT);
            if (!plugin.getPinGuiThemes().contains(theme)) {
                plugin.sendMessage(sender, "messages.admin-pingui-preview-usage", true);
                return true;
            }
            plugin.openPinGuiPreview(player, theme);
            plugin.sendMessage(sender, "messages.admin-pingui-preview-opened", true, Map.of("theme", theme));
            return true;
        }

        if (subCommand.equals("sessions")) {
            sendSessions(sender);
            return true;
        }

        if (subCommand.equals("session")) {
            if (args.length < 2) {
                plugin.sendMessage(sender, "messages.admin-session-usage", true);
                return true;
            }

            UUID uuid = resolveSessionUuid(args[1]);
            if (uuid == null) {
                sendPlayerMessage(sender, "messages.admin-account-not-found", args[1]);
                return true;
            }

            sendSingleSession(sender, args[1], uuid);
            return true;
        }

        if (subCommand.equals("clearsession")) {
            if (args.length < 2) {
                plugin.sendMessage(sender, "messages.admin-clear-session-usage", true);
                return true;
            }

            UUID uuid = resolveSessionUuid(args[1]);
            if (uuid == null) {
                sendPlayerMessage(sender, "messages.admin-account-not-found", args[1]);
                return true;
            }

            if (plugin.clearAuthSession(uuid)) {
                sendPlayerMessage(sender, "messages.admin-clear-session-success", args[1]);
            } else {
                sendPlayerMessage(sender, "messages.admin-session-not-found", args[1]);
            }
            return true;
        }

        if (subCommand.equals("clearsessions")) {
            int count = plugin.clearAllAuthSessions();
            plugin.sendMessage(sender, "messages.admin-clear-sessions-success", true, Map.of("count", String.valueOf(count)));
            return true;
        }

        if (subCommand.equals("unregister")) {
            if (args.length < 2) {
                plugin.sendMessage(sender, "messages.admin-unregister-usage", true);
                return true;
            }

            AccountStatus status = plugin.getAccountStatus(args[1]);
            if (!status.found()) {
                sendPlayerMessage(sender, "messages.admin-account-not-found", args[1]);
                return true;
            }

            if (unregisterAccount(status.username())) {
                clearOnlineState(status);
                sendPlayerMessage(sender, "messages.admin-unregister-success", status.username());
                plugin.logInfo(
                        sender.getName() + ", " + status.username() + " hesabını sildi.",
                        sender.getName() + " removed account " + status.username() + "."
                );
            } else {
                sendPlayerMessage(sender, "messages.admin-unregister-failed", status.username());
            }
            return true;
        }

        if (subCommand.equals("changepassword")) {
            if (args.length < 3) {
                plugin.sendMessage(sender, "messages.admin-change-password-usage", true);
                return true;
            }

            AccountStatus status = plugin.getAccountStatus(args[1]);
            if (!status.found()) {
                sendPlayerMessage(sender, "messages.admin-account-not-found", args[1]);
                return true;
            }

            Aethelguard.PasswordPolicyResult passwordPolicy = plugin.validatePasswordPolicy(args[2], status.username());
            if (!passwordPolicy.valid()) {
                plugin.sendMessage(sender, passwordPolicy.messagePath(), true, passwordPolicy.placeholders());
                return true;
            }

            if (changePassword(status.username(), args[2])) {
                plugin.getWrongPasswordAttempts().remove(status.uuid());
                sendPlayerMessage(sender, "messages.admin-change-password-success", status.username());
                plugin.logInfo(
                        sender.getName() + ", " + status.username() + " hesabının şifresini değiştirdi.",
                        sender.getName() + " changed password for " + status.username() + "."
                );
            } else {
                sendPlayerMessage(sender, "messages.admin-change-password-failed", status.username());
            }
            return true;
        }

        if (subCommand.equals("unlogin") || subCommand.equals("logout")) {
            if (args.length < 2) {
                plugin.sendMessage(sender, "messages.admin-unlogin-usage", true);
                return true;
            }

            Player target = plugin.getServer().getPlayerExact(args[1]);
            if (target == null || !target.isOnline()) {
                plugin.sendMessage(sender, "messages.admin-player-not-online", true);
                return true;
            }

            if (!plugin.isAuthenticated(target)) {
                plugin.sendMessage(sender, "messages.admin-player-not-authenticated", true);
                return true;
            }

            plugin.forceUnlogin(target);
            sendPlayerMessage(sender, "messages.admin-unlogin-success", target.getName());

            if (plugin.getConfig().getBoolean("console-logging.log-auth-state-changes", true)) {
                plugin.logInfo(
                        sender.getName() + ", " + target.getName() + " oyuncusunu tekrar giriş ekranına gönderdi.",
                        sender.getName() + " forced " + target.getName() + " back to authentication."
                );
            }
            return true;
        }

        plugin.sendMessage(sender, "messages.admin-usage", true);
        return true;
    }

    private boolean unregisterAccount(String username) {
        return plugin.getConfig().getBoolean("database.enabled", false)
                ? unregisterDatabaseAccount(username)
                : unregisterLocalAccount(username);
    }

    private boolean unregisterLocalAccount(String username) {
        File file = findLocalAccountFile(username);
        boolean deleted = file != null && file.delete();
        if (deleted) {
            plugin.rebuildUserIndex();
        }
        return deleted;
    }

    private boolean unregisterDatabaseAccount(String username) {
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            if (conn == null) return false;
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM " + plugin.getAuthTableName() + " WHERE LOWER(username) = LOWER(?);")) {
                ps.setString(1, username);
                return ps.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            return false;
        }
    }

    private boolean changePassword(String username, String password) {
        String hash = BCrypt.hashpw(password, BCrypt.gensalt());
        return plugin.getConfig().getBoolean("database.enabled", false)
                ? changeDatabasePassword(username, hash)
                : changeLocalPassword(username, hash);
    }

    private boolean changeLocalPassword(String username, String hash) {
        File file = findLocalAccountFile(username);
        if (file == null) return false;

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        config.set("password", hash);
        config.set("password.usable", true);
        config.set("security.last-password-change", java.time.LocalDateTime.now().toString());
        try {
            config.save(file);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private boolean changeDatabasePassword(String username, String hash) {
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            if (conn == null) return false;
            try (PreparedStatement ps = conn.prepareStatement("UPDATE " + plugin.getAuthTableName() + " SET password = ?, password_usable = TRUE WHERE LOWER(username) = LOWER(?);")) {
                ps.setString(1, hash);
                ps.setString(2, username);
                return ps.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            return false;
        }
    }

    private File findLocalAccountFile(String username) {
        File[] files = plugin.getLocalUsersFolder().listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return null;

        for (File file : files) {
            FileConfiguration config = YamlConfiguration.loadConfiguration(file);
            if (config.getString("username", "").equalsIgnoreCase(username)) {
                return file;
            }
        }
        return null;
    }

    private void clearOnlineState(AccountStatus status) {
        UUID uuid = status.uuid();
        if (uuid == null) return;

        plugin.getLoggedInPlayers().remove(uuid);
        plugin.getUnauthenticatedPlayers().remove(uuid);
        plugin.getWrongPasswordAttempts().remove(uuid);

        Player player = plugin.getServer().getPlayer(uuid);
        if (player != null) {
            player.kickPlayer(plugin.getRawStringMessage("messages.admin-unregister-kick", true));
        }
    }

    private void sendStatus(CommandSender sender, AccountStatus status) {
        if (!status.found()) {
            sendPlayerMessage(sender, "messages.admin-account-not-found", status.username());
            return;
        }

        Map<String, String> placeholders = Map.ofEntries(
                Map.entry("player", status.username()),
                Map.entry("uuid", String.valueOf(status.uuid())),
                Map.entry("storage", status.storage()),
                Map.entry("online", yesNo(status.online())),
                Map.entry("authenticated", yesNo(status.authenticated())),
                Map.entry("waiting_auth", yesNo(status.waitingAuth())),
                Map.entry("wrong_current", String.valueOf(status.currentWrongAttempts())),
                Map.entry("wrong_total", String.valueOf(status.totalWrongAttempts())),
                Map.entry("created_at", status.createdAt()),
                Map.entry("last_login", status.lastLogin()),
                Map.entry("last_ip", status.lastIp()),
                Map.entry("last_world", status.lastWorld()),
                Map.entry("last_location", status.lastLocation())
        );

        for (String path : List.of(
                "messages.admin-status-line",
                "messages.admin-status-title",
                "messages.admin-status-uuid",
                "messages.admin-status-storage",
                "messages.admin-status-online",
                "messages.admin-status-authenticated",
                "messages.admin-status-waiting-auth",
                "messages.admin-status-wrong-attempts",
                "messages.admin-status-created-at",
                "messages.admin-status-last-login",
                "messages.admin-status-last-ip",
                "messages.admin-status-last-world",
                "messages.admin-status-last-location",
                "messages.admin-status-line"
        )) {
            plugin.sendMessage(sender, path, false, placeholders);
        }
    }

    private void sendIpInfo(CommandSender sender, String input) {
        String ip = resolveIpInput(input);
        if (ip == null || ip.equals("-") || ip.equalsIgnoreCase("UNKNOWN")) {
            plugin.sendMessage(sender, "messages.admin-ipinfo-not-found", true, Map.of("input", input));
            return;
        }

        int registrationLimit = plugin.getConfig().getInt("auth-settings.registration.ip-limit.max-accounts", 2);
        int suspiciousLimit = plugin.getConfig().getInt("adaptive-security.suspicious-ip-extra-captcha.reasons.max-accounts-per-ip", 3);
        List<Aethelguard.AccountIpEntry> accounts = plugin.getAccountsByIp(ip);

        for (String path : List.of(
                "messages.admin-ipinfo-line",
                "messages.admin-ipinfo-title",
                "messages.admin-ipinfo-ip",
                "messages.admin-ipinfo-account-count",
                "messages.admin-ipinfo-registration-limit",
                "messages.admin-ipinfo-suspicious-limit",
                "messages.admin-ipinfo-line"
        )) {
            plugin.sendMessage(sender, path, false, Map.of(
                    "input", input,
                    "ip", ip,
                    "count", String.valueOf(accounts.size()),
                    "registration_limit", String.valueOf(registrationLimit),
                    "suspicious_limit", String.valueOf(suspiciousLimit)
            ));
        }
    }

    private void sendAccounts(CommandSender sender, String input) {
        String ip = resolveIpInput(input);
        if (ip == null || ip.equals("-") || ip.equalsIgnoreCase("UNKNOWN")) {
            plugin.sendMessage(sender, "messages.admin-ipinfo-not-found", true, Map.of("input", input));
            return;
        }

        List<Aethelguard.AccountIpEntry> accounts = plugin.getAccountsByIp(ip);
        plugin.sendMessage(sender, "messages.admin-accounts-header", true, Map.of(
                "ip", ip,
                "count", String.valueOf(accounts.size())
        ));

        if (accounts.isEmpty()) {
            plugin.sendMessage(sender, "messages.admin-accounts-empty", true, Map.of("ip", ip));
            return;
        }

        for (Aethelguard.AccountIpEntry account : accounts) {
            plugin.sendMessage(sender, "messages.admin-accounts-entry", false, Map.of(
                    "player", account.username(),
                    "uuid", account.uuid(),
                    "registration_ip", account.registrationIp(),
                    "last_ip", account.lastIp(),
                    "created_at", account.createdAt(),
                    "last_login", account.lastLogin()
            ));
        }
    }

    private String resolveIpInput(String input) {
        AccountStatus status = plugin.getAccountStatus(input);
        if (status.found()) {
            return status.lastIp();
        }
        return input;
    }

    private String yesNo(boolean value) {
        return value ? plugin.getFormattedMessageString("messages.admin-status-yes", false)
                : plugin.getFormattedMessageString("messages.admin-status-no", false);
    }

    private void sendSessions(CommandSender sender) {
        Map<UUID, String[]> sessions = plugin.getAuthSessionSummaries();
        plugin.sendMessage(sender, "messages.admin-sessions-header", true, Map.of("count", String.valueOf(sessions.size())));

        if (sessions.isEmpty()) {
            plugin.sendMessage(sender, "messages.admin-sessions-empty", true);
            return;
        }

        for (Map.Entry<UUID, String[]> entry : sessions.entrySet()) {
            String[] data = entry.getValue();
            plugin.sendMessage(sender, "messages.admin-sessions-entry", false, Map.of(
                    "player", getOfflineName(entry.getKey()),
                    "uuid", entry.getKey().toString(),
                    "ip", data[0],
                    "expires", data[1]
            ));
        }
    }

    private void sendSingleSession(CommandSender sender, String requestedName, UUID uuid) {
        Map<UUID, String[]> sessions = plugin.getAuthSessionSummaries();
        String[] data = sessions.get(uuid);
        if (data == null) {
            sendPlayerMessage(sender, "messages.admin-session-not-found", requestedName);
            return;
        }

        plugin.sendMessage(sender, "messages.admin-session-detail", true, Map.of(
                "player", getOfflineName(uuid),
                "uuid", uuid.toString(),
                "ip", data[0],
                "expires", data[1]
        ));
    }

    private UUID resolveSessionUuid(String username) {
        Player online = plugin.getServer().getPlayerExact(username);
        if (online != null) return online.getUniqueId();

        AccountStatus status = plugin.getAccountStatus(username);
        return status.found() ? status.uuid() : null;
    }

    private String getOfflineName(UUID uuid) {
        OfflinePlayer player = plugin.getServer().getOfflinePlayer(uuid);
        return player.getName() == null ? uuid.toString() : player.getName();
    }

    private void sendPlayerMessage(CommandSender sender, String path, String playerName) {
        plugin.sendMessage(sender, path, true, Map.of("player", playerName));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("aethelguard.admin")) {
            return List.of();
        }

        if (args.length == 1) {
            return CommandCompletions.filter(args[0], List.of(
                    "reload",
                    "status",
                    "ipinfo",
                    "accounts",
                    "sessions",
                    "session",
                    "clearsession",
                    "clearsessions",
                    "pingui",
                    "unregister",
                    "changepassword",
                    "unlogin",
                    "logout"
            ));
        }

        if (args.length == 2 && List.of("status", "ipinfo", "accounts", "session", "clearsession", "unregister", "changepassword", "unlogin", "logout").contains(args[0].toLowerCase(Locale.ROOT))) {
            return CommandCompletions.onlinePlayers(plugin.getServer(), args[1]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("pingui")) {
            return CommandCompletions.filter(args[1], List.of("preview"));
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("pingui") && args[1].equalsIgnoreCase("preview")) {
            return CommandCompletions.filter(args[2], plugin.getPinGuiThemes());
        }

        return List.of();
    }
}
