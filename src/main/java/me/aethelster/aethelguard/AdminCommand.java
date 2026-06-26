package me.aethelster.aethelguard;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.mindrot.jbcrypt.BCrypt;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class AdminCommand implements CommandExecutor, TabCompleter {

    private final AethelGuard plugin;

    public AdminCommand(AethelGuard plugin) {
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

        if (subCommand.equals("diagnostics")) {
            handleDiagnostics(sender, args);
            return true;
        }

        if (subCommand.equals("pingui")) {
            if (args.length < 2) {
                plugin.sendMessage(sender, "messages.admin-pingui-preview-usage", true);
                return true;
            }

            if (args[1].equalsIgnoreCase("themes")) {
                plugin.sendMessage(sender, "messages.admin-pingui-themes", true, Map.of(
                        "themes", String.join(", ", plugin.getPinGuiThemes()),
                        "current", plugin.getConfig().getString("auth-settings.pin.gui.theme", "quartz")
                ));
                return true;
            }

            if (!args[1].equalsIgnoreCase("preview")) {
                plugin.sendMessage(sender, "messages.admin-pingui-preview-usage", true);
                return true;
            }

            if (!(sender instanceof Player player)) {
                plugin.sendMessage(sender, "messages.only-players", true);
                return true;
            }

            String theme = args.length >= 3
                    ? args[2].toLowerCase(Locale.ROOT)
                    : plugin.getConfig().getString("auth-settings.pin.gui.theme", "quartz").toLowerCase(Locale.ROOT);
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

            AethelGuard.PasswordPolicyResult passwordPolicy = plugin.validatePasswordPolicy(args[2], status.username());
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

    private void handleDiagnostics(CommandSender sender, String[] args) {
        if (!plugin.getConfig().getBoolean("diagnostics.enabled", true)) {
            plugin.sendMessage(sender, "messages.admin-diagnostics-disabled", true);
            return;
        }

        if (args.length == 1) {
            sendDiagnosticsOverview(sender);
            return;
        }

        String mode = args[1].toLowerCase(Locale.ROOT);
        if (mode.equals("player")) {
            if (args.length < 3) {
                plugin.sendMessage(sender, "messages.admin-diagnostics-player-usage", true);
                return;
            }
            sendDiagnosticsPlayer(sender, args[2]);
            return;
        }

        if (mode.equals("config")) {
            sendDiagnosticsConfig(sender);
            return;
        }

        if (mode.equals("dump")) {
            writeDiagnosticsDump(sender);
            return;
        }

        plugin.sendMessage(sender, "messages.admin-diagnostics-usage", true);
    }

    private void sendDiagnosticsOverview(CommandSender sender) {
        sendDiagnosticsHeader(sender, "messages.admin-diagnostics-title");
        sendDiagnosticEntry(sender, diagnosticLabel("plugin-version"), plugin.getDescription().getVersion());
        sendDiagnosticEntry(sender, diagnosticLabel("storage-mode"), plugin.getConfig().getBoolean("database.enabled", false) ? "MySQL" : "Local YAML");
        sendDiagnosticEntry(sender, diagnosticLabel("database"), databaseHealth());
        sendDiagnosticEntry(sender, diagnosticLabel("default-language"), plugin.getConfig().getString("default-language", "TR"));
        sendDiagnosticEntry(sender, diagnosticLabel("console-language"), plugin.getConfig().getString("console-language", "en") + " / " + plugin.getConfig().getString("console-text-mode", "ascii"));
        sendDiagnosticEntry(sender, diagnosticLabel("pin"), diagnosticText("pin-summary", Map.of(
                "pin", enabledState("auth-settings.pin.enabled"),
                "gui", enabledState("auth-settings.pin.gui.enabled"),
                "theme", plugin.getConfig().getString("auth-settings.pin.gui.theme", "quartz")
        )));
        sendDiagnosticEntry(sender, diagnosticLabel("default-auth-mode"), plugin.defaultAuthMode());
        sendDiagnosticEntry(sender, diagnosticLabel("captcha"), diagnosticText("captcha-summary", Map.of(
                "state", enabledState("auth-settings.captcha.enabled"),
                "types", plugin.getConfig().getStringList("auth-settings.captcha.types").toString()
        )));
        sendDiagnosticEntry(sender, diagnosticLabel("two-factor"), enabledState("auth-settings.two-factor.enabled"));
        sendDiagnosticEntry(sender, diagnosticLabel("recovery"), enabledState("recovery.enabled"));
        sendDiagnosticEntry(sender, diagnosticLabel("adaptive-security"), enabledState("adaptive-security.enabled"));
        sendDiagnosticEntry(sender, diagnosticLabel("vpn-check"), diagnosticText("vpn-summary", Map.of(
                "state", enabledState("adaptive-security.suspicious-ip-extra-captcha.vpn-check.enabled"),
                "providers", plugin.getConfig().getStringList("adaptive-security.suspicious-ip-extra-captcha.vpn-check.providers").toString()
        )));
        sendDiagnosticEntry(sender, diagnosticLabel("sessions"), diagnosticText("session-summary", Map.of(
                "state", enabledState("auth-settings.sessions.enabled"),
                "duration", String.valueOf(plugin.getConfig().getLong("auth-settings.sessions.duration-minutes", 10L))
        )));
        sendDiagnosticEntry(sender, diagnosticLabel("status-system"), enabledState("status.enabled"));
        sendDiagnosticEntry(sender, diagnosticLabel("local-users-folder"), localUsersSummary());
        sendDiagnosticEntry(sender, diagnosticLabel("user-index"), userIndexSummary());
        sendDiagnosticEntry(sender, diagnosticLabel("active-auth-sessions"), String.valueOf(plugin.getAuthSessionSummaries().size()));
        sendDiagnosticEntry(sender, diagnosticLabel("waiting-auth-players"), String.valueOf(plugin.getUnauthenticatedPlayers().size()));
        sendDiagnosticEntry(sender, diagnosticLabel("captcha-challenges"), String.valueOf(plugin.getCaptchaChallengeCount()));
        sendDiagnosticEntry(sender, diagnosticLabel("pending-2fa-players"), String.valueOf(plugin.getPendingTwoFactorCount()));
        sendDiagnosticsFooter(sender);
    }

    private void sendDiagnosticsPlayer(CommandSender sender, String username) {
        AccountStatus status = plugin.getAccountStatus(username);
        if (!status.found()) {
            sendPlayerMessage(sender, "messages.admin-account-not-found", username);
            return;
        }

        UUID uuid = status.uuid();
        Player online = uuid == null ? null : plugin.getServer().getPlayer(uuid);
        sendDiagnosticsHeader(sender, "messages.admin-diagnostics-player-title");
        sendDiagnosticEntry(sender, diagnosticLabel("player"), status.username());
        sendDiagnosticEntry(sender, diagnosticLabel("uuid"), String.valueOf(uuid));
        sendDiagnosticEntry(sender, diagnosticLabel("storage"), status.storage());
        sendDiagnosticEntry(sender, diagnosticLabel("online"), plainYesNo(status.online()));
        sendDiagnosticEntry(sender, diagnosticLabel("authenticated"), plainYesNo(status.authenticated()));
        sendDiagnosticEntry(sender, diagnosticLabel("waiting-auth"), plainYesNo(status.waitingAuth()));
        sendDiagnosticEntry(sender, diagnosticLabel("auth-mode"), uuid == null ? "-" : plugin.getAuthMode(uuid));
        sendDiagnosticEntry(sender, diagnosticLabel("password-usable"), uuid == null ? "-" : plainYesNo(plugin.isPasswordUsable(uuid)));
        sendDiagnosticEntry(sender, diagnosticLabel("pin-set"), uuid == null ? "-" : plainYesNo(plugin.getPinHash(uuid) != null));
        sendDiagnosticEntry(sender, diagnosticLabel("two-factor"), uuid == null ? "-" : plainYesNo(plugin.hasTwoFactorEnabled(uuid)));
        sendDiagnosticEntry(sender, diagnosticLabel("captcha-required"), online == null ? "-" : plainYesNo(plugin.isCaptchaRequired(online)));
        sendDiagnosticEntry(sender, diagnosticLabel("wrong-password-attempts"), String.valueOf(status.currentWrongAttempts()));
        sendDiagnosticEntry(sender, diagnosticLabel("wrong-pin-attempts"), uuid == null ? "-" : String.valueOf(plugin.getWrongPinAttempts().getOrDefault(uuid, 0)));
        sendDiagnosticEntry(sender, diagnosticLabel("recovery-method"), uuid == null ? "-" : plugin.getRecoveryMethod(uuid));
        sendDiagnosticEntry(sender, diagnosticLabel("security-question"), uuid == null ? "-" : plainYesNo(plugin.getStoredSecurityQuestion(uuid) != null));
        sendDiagnosticEntry(sender, diagnosticLabel("backup-codes"), uuid == null ? "-" : String.valueOf(plugin.getBackupCodeCount(uuid)));
        sendDiagnosticEntry(sender, diagnosticLabel("last-login"), status.lastLogin());
        sendDiagnosticEntry(sender, diagnosticLabel("last-ip"), formatIp(status.lastIp()));
        sendDiagnosticEntry(sender, diagnosticLabel("last-world"), status.lastWorld());
        sendDiagnosticEntry(sender, diagnosticLabel("last-location"), status.lastLocation());
        sendDiagnosticsFooter(sender);
    }

    private void sendDiagnosticsConfig(CommandSender sender) {
        sendDiagnosticsHeader(sender, "messages.admin-diagnostics-config-title");
        List<String> warnings = collectConfigWarnings();
        if (warnings.isEmpty()) {
            plugin.sendMessage(sender, "messages.admin-diagnostics-config-ok", true);
        } else {
            for (String warning : warnings) {
                plugin.sendMessage(sender, "messages.admin-diagnostics-warning", true, Map.of("warning", warning));
            }
        }
        sendDiagnosticsFooter(sender);
    }

    private void writeDiagnosticsDump(CommandSender sender) {
        if (!plugin.getConfig().getBoolean("diagnostics.dump.enabled", true)) {
            plugin.sendMessage(sender, "messages.admin-diagnostics-dump-disabled", true);
            return;
        }

        File folder = new File(plugin.getDataFolder(), safeFolder(plugin.getConfig().getString("diagnostics.dump.folder", "diagnostics")));
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.sendMessage(sender, "messages.admin-diagnostics-dump-failed", true);
            return;
        }

        String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
        File file = new File(folder, "diagnostics-" + timestamp + ".txt");
        try {
            Files.write(file.toPath(), buildDiagnosticsDumpLines(), StandardCharsets.UTF_8);
            cleanupOldDumps(folder);
            plugin.sendMessage(sender, "messages.admin-diagnostics-dump-success", true, Map.of("file", file.getPath()));
        } catch (IOException e) {
            plugin.sendMessage(sender, "messages.admin-diagnostics-dump-failed", true);
        }
    }

    private List<String> buildDiagnosticsDumpLines() {
        List<String> lines = new ArrayList<>();
        lines.add("AethelGuard Diagnostics");
        lines.add("Generated at: " + plugin.formatDate(new Date()));
        lines.add("");
        lines.add("[Overview]");
        lines.add("Plugin version: " + plugin.getDescription().getVersion());
        lines.add("Storage mode: " + (plugin.getConfig().getBoolean("database.enabled", false) ? "MySQL" : "Local YAML"));
        lines.add("Database: " + databaseHealth());
        lines.add("Default language: " + plugin.getConfig().getString("default-language", "TR"));
        lines.add("Console language: " + plugin.getConfig().getString("console-language", "en") + " / " + plugin.getConfig().getString("console-text-mode", "ascii"));
        lines.add("PIN: " + enabledState("auth-settings.pin.enabled") + ", GUI " + enabledState("auth-settings.pin.gui.enabled") + ", theme " + plugin.getConfig().getString("auth-settings.pin.gui.theme", "quartz"));
        lines.add("Captcha: " + enabledState("auth-settings.captcha.enabled") + ", types " + plugin.getConfig().getStringList("auth-settings.captcha.types"));
        lines.add("Two-factor: " + enabledState("auth-settings.two-factor.enabled"));
        lines.add("Recovery: " + enabledState("recovery.enabled"));
        lines.add("Adaptive security: " + enabledState("adaptive-security.enabled"));
        lines.add("VPN check: " + enabledState("adaptive-security.suspicious-ip-extra-captcha.vpn-check.enabled"));
        lines.add("Sessions: " + enabledState("auth-settings.sessions.enabled") + ", " + plugin.getConfig().getLong("auth-settings.sessions.duration-minutes", 10L) + " minutes");
        lines.add("Local users folder: " + localUsersSummary());
        lines.add("Active auth sessions: " + plugin.getAuthSessionSummaries().size());
        lines.add("Waiting auth players: " + plugin.getUnauthenticatedPlayers().size());
        lines.add("Captcha challenges: " + plugin.getCaptchaChallengeCount());
        lines.add("Pending 2FA players: " + plugin.getPendingTwoFactorCount());
        lines.add("");
        lines.add("[Config warnings]");
        List<String> warnings = collectConfigWarnings();
        if (warnings.isEmpty()) {
            lines.add("No warnings found.");
        } else {
            warnings.forEach(warning -> lines.add("- " + warning));
        }
        return lines;
    }

    private List<String> collectConfigWarnings() {
        List<String> warnings = new ArrayList<>();
        String theme = plugin.getConfig().getString("auth-settings.pin.gui.theme", "quartz");
        if (theme == null || !plugin.getPinGuiThemes().contains(theme.toLowerCase(Locale.ROOT))) {
            warnings.add(configWarning("unknown-pin-theme", Map.of("theme", String.valueOf(theme))));
        }
        if (plugin.getConfig().getBoolean("auth-settings.pin.enabled", false)
                && plugin.defaultAuthMode().equals("PIN")
                && !plugin.getConfig().getBoolean("auth-settings.pin.numeric-only", true)) {
            warnings.add(configWarning("pin-gui-numeric-only", Map.of()));
        }
        String tableName = plugin.getConfig().getString("database.table-name", "aethelguard_auth");
        if (tableName == null || !tableName.matches("[A-Za-z0-9_]+")) {
            warnings.add(configWarning("invalid-table-name", Map.of()));
        }
        String localFolder = plugin.getConfig().getString("storage.local-users-folder", "users");
        if (localFolder == null || localFolder.isBlank() || localFolder.contains("..") || localFolder.contains("/") || localFolder.contains("\\")) {
            warnings.add(configWarning("invalid-local-users-folder", Map.of()));
        }
        if (plugin.getConfig().getInt("diagnostics.max-dumps-to-keep", 10) < 1) {
            warnings.add(configWarning("invalid-dump-retention", Map.of()));
        }

        org.bukkit.configuration.ConfigurationSection sounds = plugin.getConfig().getConfigurationSection("auth-settings.sounds");
        if (sounds != null) {
            for (String key : sounds.getKeys(false)) {
                if (key.equals("enabled")) continue;
                String path = "auth-settings.sounds." + key + ".sound";
                String soundName = plugin.getConfig().getString(path, "");
                if (soundName != null && !soundName.isBlank() && !isValidSound(soundName)) {
                    warnings.add(configWarning("invalid-sound", Map.of("path", path, "sound", soundName)));
                }
            }
        }
        return warnings;
    }

    private String diagnosticLabel(String key) {
        return plugin.getFormattedMessageString("messages.admin-diagnostics-label-" + key, false);
    }

    private String configWarning(String key, Map<String, String> placeholders) {
        String message = plugin.getFormattedMessageString("messages.admin-diagnostics-config-warning-" + key, false);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            message = message.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return message;
    }

    private String diagnosticValue(String key) {
        return plugin.getFormattedMessageString("messages.admin-diagnostics-value-" + key, false);
    }

    private String diagnosticText(String key, Map<String, String> placeholders) {
        String message = plugin.getFormattedMessageString("messages.admin-diagnostics-text-" + key, false);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            message = message.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return message;
    }

    private boolean isValidSound(String soundName) {
        String normalized = soundName.toLowerCase(Locale.ROOT).replace('_', '.');
        NamespacedKey key = normalized.contains(":")
                ? NamespacedKey.fromString(normalized)
                : NamespacedKey.minecraft(normalized);
        if (key != null && Registry.SOUNDS.get(key) != null) {
            return true;
        }
        try {
            Sound.valueOf(soundName.toUpperCase(Locale.ROOT));
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private String databaseHealth() {
        if (!plugin.getConfig().getBoolean("database.enabled", false)) {
            return diagnosticValue("disabled");
        }
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            return conn == null ? diagnosticValue("not-connected") : diagnosticValue("connected");
        } catch (SQLException e) {
            return diagnosticText("error", Map.of("error", e.getMessage()));
        }
    }

    private String localUsersSummary() {
        File folder = plugin.getLocalUsersFolder();
        File[] users = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        return diagnosticText("local-users-summary", Map.of(
                "state", folder.exists() ? diagnosticValue("exists") : diagnosticValue("missing"),
                "count", String.valueOf(users == null ? 0 : users.length)
        ));
    }

    private String userIndexSummary() {
        File index = new File(plugin.getLocalUsersFolder(), plugin.getConfig().getString("status.user-index-file", "user-index.txt"));
        return index.exists() ? diagnosticValue("exists") : diagnosticValue("missing");
    }

    private String enabledState(String path) {
        return plugin.getConfig().getBoolean(path, true) ? diagnosticValue("enabled") : diagnosticValue("disabled");
    }

    private String plainYesNo(boolean value) {
        return value ? diagnosticValue("yes") : diagnosticValue("no");
    }

    private String formatIp(String ip) {
        if (!plugin.getConfig().getBoolean("diagnostics.include-player-ip", false)) {
            return diagnosticValue("hidden");
        }
        if (!plugin.getConfig().getBoolean("diagnostics.mask-sensitive-data", true)) {
            return ip == null || ip.isBlank() ? "-" : ip;
        }
        return maskIp(ip);
    }

    private String maskIp(String ip) {
        if (ip == null || ip.isBlank() || ip.equals("-") || ip.equalsIgnoreCase("UNKNOWN")) return "-";
        if (ip.contains(":")) {
            int split = ip.indexOf(':');
            return split <= 0 ? diagnosticValue("masked") : ip.substring(0, split) + ":xxxx";
        }
        String[] parts = ip.split("\\.");
        if (parts.length != 4) return diagnosticValue("masked");
        return parts[0] + "." + parts[1] + "." + parts[2] + ".xxx";
    }

    private String safeFolder(String folder) {
        if (folder == null || folder.isBlank() || folder.contains("..") || folder.contains("/") || folder.contains("\\")) {
            return "diagnostics";
        }
        return folder;
    }

    private void cleanupOldDumps(File folder) {
        File[] dumps = folder.listFiles((dir, name) -> name.startsWith("diagnostics-") && name.endsWith(".txt"));
        if (dumps == null) return;
        int max = Math.max(1, plugin.getConfig().getInt("diagnostics.max-dumps-to-keep", 10));
        if (dumps.length <= max) return;
        List<File> files = new ArrayList<>(List.of(dumps));
        files.sort(Comparator.comparingLong(File::lastModified));
        for (int i = 0; i < files.size() - max; i++) {
            files.get(i).delete();
        }
    }

    private void sendDiagnosticsHeader(CommandSender sender, String titlePath) {
        plugin.sendMessage(sender, "messages.admin-diagnostics-line", false);
        plugin.sendMessage(sender, titlePath, false);
        plugin.sendMessage(sender, "messages.admin-diagnostics-line", false);
    }

    private void sendDiagnosticsFooter(CommandSender sender) {
        plugin.sendMessage(sender, "messages.admin-diagnostics-line", false);
    }

    private void sendDiagnosticEntry(CommandSender sender, String key, String value) {
        plugin.sendMessage(sender, "messages.admin-diagnostics-entry", false,
                Map.of("key", key, "value", value == null || value.isBlank() ? "-" : value));
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
        config.set("password.hash", hash);
        config.set("password.usable", true);
        config.set("security.last-password-change", plugin.formatDate(new Date()));
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
                Map.entry("active_session", yesNo(status.activeSession())),
                Map.entry("temporary_lockout", yesNo(status.temporaryLockout())),
                Map.entry("temporary_lockout_remaining", status.temporaryLockoutRemaining()),
                Map.entry("auth_mode", status.authMode()),
                Map.entry("password_usable", yesNo(status.passwordUsable())),
                Map.entry("pin_set", yesNo(status.pinSet())),
                Map.entry("two_factor", yesNo(status.twoFactorEnabled())),
                Map.entry("recovery_method", status.recoveryMethod()),
                Map.entry("security_question", yesNo(status.securityQuestionSet())),
                Map.entry("backup_codes", String.valueOf(status.backupCodeCount())),
                Map.entry("wrong_current", String.valueOf(status.currentWrongAttempts())),
                Map.entry("wrong_pin_current", String.valueOf(status.currentPinAttempts())),
                Map.entry("wrong_total", String.valueOf(status.totalWrongAttempts())),
                Map.entry("login_count", String.valueOf(status.loginCount())),
                Map.entry("created_at", status.createdAt()),
                Map.entry("last_login", status.lastLogin()),
                Map.entry("last_wrong_attempt", status.lastWrongAttempt()),
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
                "messages.admin-status-active-session",
                "messages.admin-status-temporary-lockout",
                "messages.admin-status-auth-mode",
                "messages.admin-status-security",
                "messages.admin-status-wrong-attempts",
                "messages.admin-status-login-count",
                "messages.admin-status-created-at",
                "messages.admin-status-last-login",
                "messages.admin-status-last-wrong-attempt",
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
        List<AethelGuard.AccountIpEntry> accounts = plugin.getAccountsByIp(ip);

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

        List<AethelGuard.AccountIpEntry> accounts = plugin.getAccountsByIp(ip);
        plugin.sendMessage(sender, "messages.admin-accounts-header", true, Map.of(
                "ip", ip,
                "count", String.valueOf(accounts.size())
        ));

        if (accounts.isEmpty()) {
            plugin.sendMessage(sender, "messages.admin-accounts-empty", true, Map.of("ip", ip));
            return;
        }

        for (AethelGuard.AccountIpEntry account : accounts) {
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
                    "diagnostics",
                    "pingui",
                    "unregister",
                    "changepassword",
                    "unlogin",
                    "logout"
            ));
        }

        if (args.length == 2 && List.of("status", "session", "clearsession", "unregister", "changepassword", "unlogin", "logout").contains(args[0].toLowerCase(Locale.ROOT))) {
            return accountAndOnlineCompletions(args[1]);
        }

        if (args.length == 2 && List.of("ipinfo", "accounts").contains(args[0].toLowerCase(Locale.ROOT))) {
            return accountIpAndOnlineCompletions(args[1]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("diagnostics")) {
            return CommandCompletions.filter(args[1], List.of("player", "config", "dump"));
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("diagnostics") && args[1].equalsIgnoreCase("player")) {
            return CommandCompletions.onlinePlayers(plugin.getServer(), args[2]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("pingui")) {
            return CommandCompletions.filter(args[1], List.of("preview", "themes"));
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("pingui") && args[1].equalsIgnoreCase("preview")) {
            return CommandCompletions.filter(args[2], plugin.getPinGuiThemes());
        }

        return List.of();
    }

    private List<String> accountAndOnlineCompletions(String current) {
        Set<String> options = new LinkedHashSet<>();
        options.addAll(plugin.getKnownAccountNames());
        options.addAll(CommandCompletions.onlinePlayers(plugin.getServer(), ""));
        return CommandCompletions.filter(current, options);
    }

    private List<String> accountIpAndOnlineCompletions(String current) {
        Set<String> options = new LinkedHashSet<>();
        options.addAll(plugin.getKnownAccountNames());
        options.addAll(plugin.getKnownAccountIps());
        options.addAll(CommandCompletions.onlinePlayers(plugin.getServer(), ""));
        return CommandCompletions.filter(current, options);
    }
}
