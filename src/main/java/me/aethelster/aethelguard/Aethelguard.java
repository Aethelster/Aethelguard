package me.aethelster.aethelguard;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.util.*;
import java.util.logging.Filter;
import java.util.logging.Handler;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public final class Aethelguard extends JavaPlugin {

    private DatabaseManager databaseManager;
    private final Map<UUID, Location> previousLocations = new HashMap<>();
    private final Set<UUID> unauthenticatedPlayers = new HashSet<>();
    private final Map<UUID, Integer> wrongPasswordAttempts = new HashMap<>();
    private final Set<UUID> loggedInPlayers = new HashSet<>();
    private static final Map<String, String> COLOR_MAP = Map.ofEntries(
            Map.entry("&0", "<black>"),
            Map.entry("&1", "<dark_blue>"),
            Map.entry("&2", "<dark_green>"),
            Map.entry("&3", "<dark_aqua>"),
            Map.entry("&4", "<dark_red>"),
            Map.entry("&5", "<dark_purple>"),
            Map.entry("&6", "<gold>"),
            Map.entry("&7", "<gray>"),
            Map.entry("&8", "<dark_gray>"),
            Map.entry("&9", "<blue>"),
            Map.entry("&a", "<green>"),
            Map.entry("&b", "<aqua>"),
            Map.entry("&c", "<red>"),
            Map.entry("&d", "<light_purple>"),
            Map.entry("&e", "<yellow>"),
            Map.entry("&f", "<white>"),
            Map.entry("&l", "<bold>"),
            Map.entry("&n", "<underlined>"),
            Map.entry("&o", "<italic>"),
            Map.entry("&k", "<obfuscated>"),
            Map.entry("&r", "<reset>")
    );

    private FileConfiguration langConfig = null;
    private File langFile = null;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final String consolePrefix = "§b[Aethelguard] §r";


    public Set<UUID> getLoggedInPlayers() {
        return this.loggedInPlayers;
    }

    private boolean isTurkishConsole() {
        return getConfig().getString("console-language", "en")
                .equalsIgnoreCase("tr");
    }

    public Location getVoidLocation(Player player) {
        double x = getConfig().getDouble("auth-settings.void-zone.x", 0.0);
        double y = getConfig().getDouble("auth-settings.void-zone.y", 1000.0);
        double z = getConfig().getDouble("auth-settings.void-zone.z", 0.0);
        World world = player.getWorld();

        if (!getConfig().getBoolean("auth-settings.void-zone.use-player-world", true)) {
            String worldName = getConfig().getString("auth-settings.void-zone.world", "");
            if (worldName != null && !worldName.isBlank()) {
                World configuredWorld = getServer().getWorld(worldName);
                if (configuredWorld != null) {
                    world = configuredWorld;
                }
            }
        }

        return new Location(world, x, y, z);
    }

    public void applyAuthEffects(Player player) {
        if (getConfig().getBoolean("auth-settings.apply-blindness", true)) {
            int duration = getConfig().getInt("auth-settings.effects.blindness-duration-ticks", 999999);
            int amplifier = getConfig().getInt("auth-settings.effects.blindness-amplifier", 1);
            player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, duration, amplifier, false, false));
        }

        if (getConfig().getBoolean("auth-settings.effects.lock-player-time", true)) {
            long lockedTime = getConfig().getLong("auth-settings.effects.locked-time", 18000L);
            player.setPlayerTime(lockedTime, false);
        }
    }

    public void clearAuthEffects(Player player) {
        player.removePotionEffect(PotionEffectType.BLINDNESS);
        if (getConfig().getBoolean("auth-settings.effects.lock-player-time", true)) {
            player.resetPlayerTime();
        }
    }

    public void playConfiguredSound(Player player, String path) {
        if (!getConfig().getBoolean("auth-settings.sounds.enabled", true)) return;
        if (!getConfig().getBoolean(path + ".enabled", true)) return;

        String soundName = getConfig().getString(path + ".sound", "");
        if (soundName == null || soundName.isBlank()) return;

        try {
            Sound sound = findSound(soundName);
            if (sound == null) {
                logWarning("Invalid sound config: " + soundName, "Invalid sound config: " + soundName);
                return;
            }
            float volume = (float) getConfig().getDouble(path + ".volume", 1.0);
            float pitch = (float) getConfig().getDouble(path + ".pitch", 1.0);
            int repeatTimes = Math.max(1, getConfig().getInt(path + ".repeat-times", 1));
            long repeatInterval = Math.max(1L, getConfig().getLong(path + ".repeat-interval-ticks", 4L));

            for (int i = 0; i < repeatTimes; i++) {
                long delay = i * repeatInterval;
                getServer().getScheduler().runTaskLater(this, () -> {
                    if (player.isOnline()) {
                        player.playSound(player.getLocation(), sound, volume, pitch);
                    }
                }, delay);
            }
        } catch (IllegalArgumentException ignored) {
            logWarning("Geçersiz ses ayarı: " + soundName, "Invalid sound config: " + soundName);
        }
    }

    private Sound findSound(String soundName) {
        soundName = switch (soundName.toUpperCase(Locale.ROOT)) {
            case "ENTITY_ENDER_DRAGON_GROWL" -> "entity.ender_dragon.growl";
            case "ENTITY_PLAYER_LEVELUP" -> "entity.player.levelup";
            case "ENTITY_ITEM_BREAK" -> "entity.item.break";
            default -> soundName;
        };

        String normalized = soundName.toLowerCase(Locale.ROOT).replace('_', '.');
        NamespacedKey key = normalized.contains(":")
                ? NamespacedKey.fromString(normalized)
                : NamespacedKey.minecraft(normalized);

        if (key != null) {
            Sound sound = Registry.SOUNDS.get(key);
            if (sound != null) return sound;
        }

        try {
            return Sound.valueOf(soundName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public String getAuthTableName() {
        String tableName = getConfig().getString("database.table-name", "aethelguard_auth");
        if (tableName == null || !tableName.matches("[A-Za-z0-9_]+")) {
            logWarning("Geçersiz tablo adı ayarı. Varsayılan tablo kullanılıyor.",
                    "Invalid database table name config. Using default table.");
            return "aethelguard_auth";
        }
        return tableName;
    }

    public File getLocalUsersFolder() {
        return new File(getDataFolder(), getSafeFolderName("storage.local-users-folder", "users"));
    }

    private String getSafeFolderName(String path, String fallback) {
        String folderName = getConfig().getString(path, fallback);
        if (folderName == null || folderName.isBlank() || folderName.contains("..") || folderName.contains("/") || folderName.contains("\\")) {
            logWarning("Geçersiz klasör ayarı: " + path + ". Varsayılan klasör kullanılıyor.",
                    "Invalid folder config: " + path + ". Using default folder.");
            return fallback;
        }
        return folderName;
    }

    private boolean sameBlock(Location a, Location b) {
        return a.getBlockX() == b.getBlockX()
                && a.getBlockY() == b.getBlockY()
                && a.getBlockZ() == b.getBlockZ();
    }

    public boolean isVoidLocation(Player player, Location location) {
        if (location == null) return false;
        Location voidLoc = getVoidLocation(player);
        return location.getWorld() != null
                && voidLoc.getWorld() != null
                && location.getWorld().equals(voidLoc.getWorld())
                && sameBlock(location, voidLoc);
    }

    public void rememberPreviousLocation(Player player) {
        Location currentLocation = player.getLocation();
        if (!isVoidLocation(player, currentLocation)) {
            previousLocations.put(player.getUniqueId(), currentLocation);
        }
    }

    public Location getRestoreLocation(Player player) {
        Location prevLoc = previousLocations.get(player.getUniqueId());
        if (prevLoc == null || isVoidLocation(player, prevLoc)) {
            return player.getWorld().getSpawnLocation();
        }
        return prevLoc;
    }

    public void restorePreviousLocation(Player player) {
        player.teleport(getRestoreLocation(player));
    }

    public void clearPlayerChat(Player player) {
        if (!getConfig().getBoolean("auth-settings.clear-chat-on-auth", true)) return;

        for (int i = 0; i < 100; i++) {
            player.sendMessage("");
        }
    }

    public void completeLogin(Player player,  boolean sendMessage) {
        unauthenticatedPlayers.remove(player.getUniqueId());
        loggedInPlayers.add(player.getUniqueId());

        clearAuthEffects(player);

        restorePreviousLocation(player);
        previousLocations.remove(player.getUniqueId());
        clearPlayerChat(player);

        if (sendMessage) {
            sendMessage(player, "messages.login-success", true);
        }
    }

    private void installConsoleLogFilter() {
        Filter connectionLogFilter = new Filter() {
            @Override
            public boolean isLoggable(LogRecord record) {
                if (!getConfig().getBoolean("console-logging.suppress-server-connection-logs", true)) {
                    return true;
                }

                String msg = getLogText(record).toLowerCase(Locale.ROOT);
                return !isSuppressedConnectionLog(msg);
            }
        };

        Logger rootLogger = Logger.getLogger("");
        rootLogger.setFilter(connectionLogFilter);
        for (Handler handler : rootLogger.getHandlers()) {
            handler.setFilter(connectionLogFilter);
        }

        Enumeration<String> loggerNames = LogManager.getLogManager().getLoggerNames();
        while (loggerNames.hasMoreElements()) {
            Logger logger = LogManager.getLogManager().getLogger(loggerNames.nextElement());
            if (logger != null) {
                logger.setFilter(connectionLogFilter);
            }
        }

        installLog4jConnectionFilter();
    }

    private void installLog4jConnectionFilter() {
        try {
            org.apache.logging.log4j.core.Logger rootLogger =
                    (org.apache.logging.log4j.core.Logger) org.apache.logging.log4j.LogManager.getRootLogger();

            rootLogger.addFilter(new org.apache.logging.log4j.core.filter.AbstractFilter() {
                @Override
                public Result filter(org.apache.logging.log4j.core.LogEvent event) {
                    if (!getConfig().getBoolean("console-logging.suppress-server-connection-logs", true)) {
                        return Result.NEUTRAL;
                    }

                    String message = event.getMessage() == null
                            ? ""
                            : event.getMessage().getFormattedMessage().toLowerCase(Locale.ROOT);

                    return isSuppressedConnectionLog(message) ? Result.DENY : Result.NEUTRAL;
                }
            });
        } catch (Throwable ignored) {
            logWarning("Log4j bağlantı log filtresi kurulamadı.",
                    "Could not install Log4j connection log filter.");
        }
    }

    private String getLogText(LogRecord record) {
        StringBuilder text = new StringBuilder();
        if (record.getMessage() != null) {
            text.append(record.getMessage());
        }

        Object[] parameters = record.getParameters();
        if (parameters != null) {
            for (Object parameter : parameters) {
                text.append(' ').append(parameter);
            }
        }

        return text.toString();
    }

    private boolean isSuppressedConnectionLog(String message) {
        return message.contains("logged in with entity id")
                || message.contains("uuid of player")
                || message.contains("lost connection")
                || message.contains("joined the game")
                || message.contains("left the game")
                || message.contains("issued server command");
    }



    @Override
    public void onEnable() {
        installConsoleLogFilter();

        boolean isFirstRun = !getDataFolder().exists();
        saveDefaultConfig();
        if (!getDataFolder().exists()) getDataFolder().mkdirs();

        validateConfig();

        if (isFirstRun) {
            if (isTurkishConsole()) {
                getServer().getConsoleSender().sendMessage(consolePrefix + "§b[!] Aethelguard ilk kez kuruldu! Lütfen config.yml ayarlarını kontrol edip sunucuyu tekrar başlatın.");
            } else {
                getServer().getConsoleSender().sendMessage(consolePrefix + "§b[!] Aethelguard installed for the first time! Please check config.yml and restart.");
            }
        }

        logInfo("Aethelguard çekirdek altyapısı kuruluyor...", "Initializing Aethelguard core infrastructure...");

        saveDefaultLangFiles();
        reloadLangConfig();
        checkInternalLogStatus();

        this.databaseManager = new DatabaseManager(this);

        if (getConfig().getBoolean("database.enabled", false)) {
            logInfo("Veritabanı modu aktif, bağlantı kuruluyor...", "Database mode enabled, connecting...");
            String host = getConfig().getString("database.host");
            int port = getConfig().getInt("database.port");
            String db = getConfig().getString("database.database");
            String user = getConfig().getString("database.username");
            String pass = getConfig().getString("database.password");
            this.databaseManager.connect(host, port, db, user, pass);
        } else {
            logInfo("Veritabanı kapalı, dosya tabanlı (local) modda çalışılıyor.", "Database disabled, running in local file mode.");
        }

        boolean tpVoid = getConfig().getBoolean("auth-settings.teleport-to-void", true);
        for (Player onlinePlayer : getServer().getOnlinePlayers()) {
            UUID uuid = onlinePlayer.getUniqueId();
            unauthenticatedPlayers.add(uuid);
            rememberPreviousLocation(onlinePlayer);

            if (tpVoid) {
                Location voidLoc = getVoidLocation(onlinePlayer);
                onlinePlayer.teleport(voidLoc);
            }
            applyAuthEffects(onlinePlayer);
            sendMessage(onlinePlayer, "messages.plugin-reloaded-auth", true);
        }

        getCommand("register").setExecutor(new AuthCommand(this));
        getCommand("login").setExecutor(new AuthCommand(this));
        getServer().getPluginManager().registerEvents(new AuthListener(this), this);
        getServer().getPluginManager().registerEvents(new ChatListener(this), this);

        logInfo("Aethelguard başarıyla aktif edildi.", "Aethelguard successfully enabled.");
    }

    @Override
    public void onDisable() {
        logInfo("Aethelguard sistemleri kapatılıyor...", "Safely shutting down Aethelguard systems...");
        for (UUID uuid : unauthenticatedPlayers) {
            Player player = getServer().getPlayer(uuid);
            if (player != null) {
                clearAuthEffects(player);
                restorePreviousLocation(player);
            }
        }
        if (databaseManager != null) databaseManager.close();
    }

    public void validateConfig() {
        boolean changed = false;
        Map<String, Object> defaults = Map.ofEntries(
                Map.entry("console-language", "en"),
                Map.entry("default-language", "TR"),
                Map.entry("prefix", "<yellow>[Aethelguard] </yellow>"),
                Map.entry("local-logging.enabled", true),
                Map.entry("local-logging.folder", "internal"),
                Map.entry("database.enabled", false),
                Map.entry("database.host", "localhost"),
                Map.entry("database.port", 3306),
                Map.entry("database.database", "aethelguard"),
                Map.entry("database.username", "root"),
                Map.entry("database.password", "password"),
                Map.entry("database.table-name", "aethelguard_auth"),
                Map.entry("database.use-ssl", false),
                Map.entry("database.allow-public-key-retrieval", true),
                Map.entry("database.server-timezone", "UTC"),
                Map.entry("database.pool.maximum-size", 10),
                Map.entry("database.pool.minimum-idle", 2),
                Map.entry("database.pool.connection-timeout-ms", 10000),
                Map.entry("database.pool.idle-timeout-ms", 600000),
                Map.entry("database.pool.max-lifetime-ms", 1800000),
                Map.entry("auth-settings.teleport-to-void", true),
                Map.entry("auth-settings.apply-blindness", true),
                Map.entry("auth-settings.clear-chat-on-auth", true),
                Map.entry("auth-settings.log-unauthenticated-command-attempts", true),
                Map.entry("auth-settings.void-zone.x", 0.0),
                Map.entry("auth-settings.void-zone.y", 1000.0),
                Map.entry("auth-settings.void-zone.z", 0.0),
                Map.entry("auth-settings.void-zone.use-player-world", true),
                Map.entry("auth-settings.void-zone.world", ""),
                Map.entry("auth-settings.effects.blindness-duration-ticks", 999999),
                Map.entry("auth-settings.effects.blindness-amplifier", 1),
                Map.entry("auth-settings.effects.lock-player-time", true),
                Map.entry("auth-settings.effects.locked-time", 18000),
                Map.entry("auth-settings.prompts.enabled", true),
                Map.entry("auth-settings.prompts.initial-delay-ticks", 20),
                Map.entry("auth-settings.prompts.interval-ticks", 100),
                Map.entry("auth-settings.timeout.enabled", true),
                Map.entry("auth-settings.timeout.ticks", 1200),
                Map.entry("auth-settings.timeout.kick", true),
                Map.entry("auth-settings.restrictions.prevent-movement", true),
                Map.entry("auth-settings.restrictions.prevent-damage", true),
                Map.entry("auth-settings.restrictions.prevent-block-break", true),
                Map.entry("auth-settings.restrictions.prevent-block-place", true),
                Map.entry("auth-settings.restrictions.prevent-chat", true),
                Map.entry("auth-settings.restrictions.hide-chat-from-unauthenticated", true),
                Map.entry("auth-settings.commands.allowed", List.of("/login", "/giris", "/giriş", "/register", "/kayitol", "/kayıtol")),
                Map.entry("auth-settings.wrong-password.max-attempts", 3),
                Map.entry("auth-settings.wrong-password.kick-enabled", true),
                Map.entry("auth-settings.sounds.enabled", true),
                Map.entry("auth-settings.sounds.register-success.enabled", true),
                Map.entry("auth-settings.sounds.register-success.sound", "entity.ender_dragon.growl"),
                Map.entry("auth-settings.sounds.register-success.volume", 1.0),
                Map.entry("auth-settings.sounds.register-success.pitch", 1.0),
                Map.entry("auth-settings.sounds.register-success.repeat-times", 2),
                Map.entry("auth-settings.sounds.register-success.repeat-interval-ticks", 6),
                Map.entry("auth-settings.sounds.login-success.enabled", true),
                Map.entry("auth-settings.sounds.login-success.sound", "entity.player.levelup"),
                Map.entry("auth-settings.sounds.login-success.volume", 1.0),
                Map.entry("auth-settings.sounds.login-success.pitch", 1.0),
                Map.entry("auth-settings.sounds.login-success.repeat-times", 2),
                Map.entry("auth-settings.sounds.login-success.repeat-interval-ticks", 6),
                Map.entry("auth-settings.sounds.wrong-password.enabled", true),
                Map.entry("auth-settings.sounds.wrong-password.sound", "entity.item.break"),
                Map.entry("auth-settings.sounds.wrong-password.volume", 1.0),
                Map.entry("auth-settings.sounds.wrong-password.pitch", 1.0),
                Map.entry("auth-settings.sounds.wrong-password.repeat-times", 1),
                Map.entry("auth-settings.sounds.wrong-password.repeat-interval-ticks", 4),
                Map.entry("local-logging.file-name", "users"),
                Map.entry("local-logging.separator", "--------------------------------------------------"),
                Map.entry("console-logging.suppress-server-connection-logs", true),
                Map.entry("console-logging.log-auth-success", true),
                Map.entry("console-logging.log-blocked-chat-attempts", true),
                Map.entry("storage.local-users-folder", "users")
        );

        for (Map.Entry<String, Object> entry : defaults.entrySet()) {
            if (!getConfig().contains(entry.getKey())) {
                logWarning("Ayarlarda eksik bulundu: " + entry.getKey() + ". Varsayılan değer atanıyor.",
                        "Missing config key: " + entry.getKey() + ". Setting default value.");
                getConfig().set(entry.getKey(), entry.getValue());
                changed = true;
            }
        }

        List<String> allowedCommands = new ArrayList<>(getConfig().getStringList("auth-settings.commands.allowed"));
        if (!allowedCommands.contains("/giriş")) {
            allowedCommands.add("/giriş");
            changed = true;
        }
        if (!allowedCommands.contains("/kayıtol")) {
            allowedCommands.add("/kayıtol");
            changed = true;
        }
        if (changed) {
            getConfig().set("auth-settings.commands.allowed", allowedCommands);
        }

        if (changed) saveConfig();
    }

    public void checkInternalLogStatus() {
        File internalDir = new File(getDataFolder(), getSafeFolderName("local-logging.folder", "internal"));
        boolean isLocalLoggingEnabled = getConfig().getBoolean("local-logging.enabled", true);

        if (!internalDir.exists()) internalDir.mkdirs();

        if (!isLocalLoggingEnabled) {
            File[] files = internalDir.listFiles();
            if (files != null) for (File file : files) file.delete();

            String infoFileName = isTurkishConsole()
                    ? "tüm kayıtlar databasede.txt"
                    : "all records in database.txt";
            try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(new File(internalDir, infoFileName)), StandardCharsets.UTF_8)) {
                writer.write(isTurkishConsole()
                        ? "Yerel loglama kapalı. Kayıtlar veritabanındadır."
                        : "Local logging is disabled. Records are in the database.");
            } catch (IOException e) { e.printStackTrace(); }
        }
    }

    public void writeToInternalLog(String fileName, String logData) {
        if (!getConfig().getBoolean("local-logging.enabled", true)) return;

        String separator = getConfig().getString("local-logging.separator", "--------------------------------------------------");
        String finalEntry = separator + "\n" + logData + "\n" + separator;

        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            File folder = new File(getDataFolder(), getSafeFolderName("local-logging.folder", "internal"));
            if (!folder.exists()) folder.mkdirs();
            File logFile = new File(folder, fileName + ".txt");

            try (PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(logFile, true), StandardCharsets.UTF_8)))) {
                out.println(finalEntry);
            } catch (IOException e) {
                logWarning("Log yazma hatası: " + fileName, "Error writing log: " + fileName);
            }
        });
    }

    public void logInfo(String tr, String en) {
        getServer().getConsoleSender().sendMessage(
                consolePrefix + "§r" + (isTurkishConsole() ? tr : en)
        );
    }

    public void logWarning(String tr, String en) {
        getServer().getConsoleSender().sendMessage(
                consolePrefix + "§e[WARNING] " + (isTurkishConsole() ? tr : en)
        );
    }

    public void reloadLangConfig() {
        String langCode = getConfig().getString("default-language", "TR").toLowerCase();
        langFile = new File(new File(getDataFolder(), "messages"), "messages_" + langCode + ".yml");
        langConfig = YamlConfiguration.loadConfiguration(langFile);
        try (InputStream is = getResource("messages_" + langCode + ".yml")) {
            if (is != null) langConfig.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(is, StandardCharsets.UTF_8)));
        } catch (IOException e) { e.printStackTrace(); }
    }

    public FileConfiguration getLangConfig() {
        if (langConfig == null) reloadLangConfig();
        return langConfig;
    }

    private void saveDefaultLangFiles() {
        File dir = new File(getDataFolder(), "messages");
        if (!dir.exists()) dir.mkdirs();

        for (String f : new String[]{"messages_tr.yml", "messages_en.yml"}) {
            File outFile = new File(dir, f);
            if (!outFile.exists()) {
                saveResource(f, false);
                File tempFile = new File(getDataFolder(), f);
                if (tempFile.exists())
                    tempFile.renameTo(outFile);
            }
        }
    }

    public void sendMessage(CommandSender sender, String path, boolean prefix) {

        String msg = getFormattedMessageString(path, prefix);



        String cleanMsg = msg.replace("§", "&");


        String processed = cleanMsg;

        for (Map.Entry<String, String> entry : COLOR_MAP.entrySet()) {
            processed = processed.replace(entry.getKey(), entry.getValue());
        }


        sender.sendMessage(miniMessage.deserialize(processed));
    }

    public String getRawStringMessage(String path, boolean prefix) {
        return LegacyComponentSerializer.legacySection().serialize(
                LegacyComponentSerializer.legacyAmpersand().deserialize(getFormattedMessageString(path, prefix).replace("§", "&"))
        );
    }

    public String getFormattedMessageString(String path, boolean prefix) {
        String msg = getLangConfig().getString(path);
        if (msg == null) return "<red>[Missing node: " + path + "]";
        return prefix ? (getConfig().getString("prefix", "<yellow>[Aethelguard] </yellow>") + msg) : msg;
    }

    public boolean isAccountRegistered(java.util.UUID uuid) {
        String uuidStr = uuid.toString();


        if (getConfig().getBoolean("database.enabled", false)) {
            try (java.sql.Connection conn = getDatabaseManager().getConnection()) {
                if (conn == null) return false;

                try (java.sql.PreparedStatement ps = conn.prepareStatement("SELECT id FROM " + getAuthTableName() + " WHERE uuid = ?;")) {
                    ps.setString(1, uuidStr);
                    try (ResultSet rs = ps.executeQuery()) {
                        return rs.next();
                    }
                }
            } catch (java.sql.SQLException e) {
                e.printStackTrace();
                return false;
            }
        }


        return new java.io.File(getLocalUsersFolder(), uuidStr + ".yml").exists();
    }

    public boolean isAuthenticated(Player p) {
        return loggedInPlayers.contains(p.getUniqueId());
    }

    public MiniMessage getMiniMessage() { return miniMessage; }
    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public Set<UUID> getUnauthenticatedPlayers() { return unauthenticatedPlayers; }
    public Map<UUID, Location> getPreviousLocations() { return previousLocations; }
    public Map<UUID, Integer> getWrongPasswordAttempts() { return wrongPasswordAttempts; }
}
