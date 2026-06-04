package me.aethelster.aethelguard;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.mindrot.jbcrypt.BCrypt;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
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
    private final Map<UUID, Integer> wrongPinAttempts = new HashMap<>();
    private final Set<UUID> loggedInPlayers = new HashSet<>();
    private final Map<UUID, AuthSession> authSessions = new HashMap<>();
    private final Map<UUID, CaptchaChallenge> captchaChallenges = new HashMap<>();
    private final Set<UUID> captchaVerifiedPlayers = new HashSet<>();
    private final Map<UUID, Long> captchaCooldowns = new HashMap<>();
    private final Set<UUID> pendingTwoFactorPlayers = new HashSet<>();
    private final Map<UUID, String> pendingTwoFactorSetups = new HashMap<>();
    private final Map<UUID, SecurityQuestion> pendingSecurityQuestions = new HashMap<>();
    private final Map<UUID, AuthInventorySnapshot> authInventories = new HashMap<>();
    private final Map<UUID, BossBar> authBossBars = new HashMap<>();
    private final Map<UUID, Long> authTimeoutDeadlines = new HashMap<>();
    private final Map<UUID, Boolean> extraCaptchaPlayers = new HashMap<>();
    private final Map<String, Deque<Long>> ipSuccessLogins = new HashMap<>();
    private final Map<String, VpnCheckResult> vpnCheckCache = new HashMap<>();
    private final Set<String> pendingVpnChecks = new HashSet<>();
    private final HttpClient vpnHttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final SecureRandom random = new SecureRandom();
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
    private PinGui pinGui;
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

    public void hideInventoryForAuth(Player player) {
        UUID uuid = player.getUniqueId();
        if (authInventories.containsKey(uuid)) return;

        PlayerInventory inventory = player.getInventory();
        authInventories.put(uuid, new AuthInventorySnapshot(
                cloneItems(inventory.getStorageContents()),
                cloneItems(inventory.getArmorContents()),
                cloneItems(inventory.getExtraContents())
        ));

        inventory.clear();
        inventory.setArmorContents(new ItemStack[4]);
        inventory.setExtraContents(new ItemStack[inventory.getExtraContents().length]);
        player.updateInventory();
    }

    public void restoreAuthInventory(Player player) {
        AuthInventorySnapshot snapshot = authInventories.remove(player.getUniqueId());
        if (snapshot == null) return;

        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        inventory.setStorageContents(cloneItems(snapshot.storage()));
        inventory.setArmorContents(cloneItems(snapshot.armor()));
        inventory.setExtraContents(cloneItems(snapshot.extra()));
        player.updateInventory();
    }

    public void discardAuthInventory(Player player) {
        authInventories.remove(player.getUniqueId());
    }

    private ItemStack[] cloneItems(ItemStack[] items) {
        ItemStack[] cloned = new ItemStack[items.length];
        for (int i = 0; i < items.length; i++) {
            cloned[i] = items[i] == null ? null : items[i].clone();
        }
        return cloned;
    }

    public void updateAuthBossBar(Player player) {
        if (isAuthenticated(player) || !unauthenticatedPlayers.contains(player.getUniqueId())) {
            hideAuthBossBar(player);
            return;
        }

        if (!getConfig().getBoolean("auth-settings.bossbar.enabled", true)) {
            hideAuthBossBar(player);
            return;
        }

        BossBar bossBar = authBossBars.computeIfAbsent(player.getUniqueId(), uuid -> {
            BossBar created = getServer().createBossBar("", getBossBarColor(), getBossBarStyle());
            created.addPlayer(player);
            return created;
        });

        if (!bossBar.getPlayers().contains(player)) {
            bossBar.addPlayer(player);
        }

        bossBar.setColor(getBossBarColor());
        bossBar.setStyle(getBossBarStyle());
        bossBar.setProgress(Math.max(0.0, Math.min(1.0, getConfig().getDouble("auth-settings.bossbar.progress", 1.0))));
        bossBar.setTitle(getRawStringMessage(getAuthBossBarMessagePath(player), false));
        bossBar.setVisible(true);
    }

    public void hideAuthBossBar(Player player) {
        BossBar bossBar = authBossBars.remove(player.getUniqueId());
        if (bossBar == null) return;
        bossBar.removePlayer(player);
        bossBar.setVisible(false);
    }

    private String getAuthBossBarMessagePath(Player player) {
        if (isCaptchaRequired(player)) {
            return "messages.bossbar-captcha";
        }
        if (isWaitingTwoFactor(player)) {
            return "messages.bossbar-two-factor";
        }
        if (isAccountRegistered(player.getUniqueId())) {
            return getAuthMode(player.getUniqueId()).equals("PIN")
                    ? "messages.bossbar-pin"
                    : "messages.bossbar-login";
        }
        return defaultAuthMode().equals("PIN")
                ? "messages.bossbar-setpin"
                : "messages.bossbar-register";
    }

    private BarColor getBossBarColor() {
        try {
            return BarColor.valueOf(getConfig().getString("auth-settings.bossbar.color", "YELLOW").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return BarColor.YELLOW;
        }
    }

    private BarStyle getBossBarStyle() {
        try {
            return BarStyle.valueOf(getConfig().getString("auth-settings.bossbar.style", "SOLID").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return BarStyle.SOLID;
        }
    }

    public void completeLogin(Player player,  boolean sendMessage) {
        unauthenticatedPlayers.remove(player.getUniqueId());
        loggedInPlayers.add(player.getUniqueId());
        wrongPinAttempts.remove(player.getUniqueId());
        clearAuthTimeout(player.getUniqueId());
        recordAdaptiveSuccessfulAuth(player);
        clearCaptchaState(player.getUniqueId());
        clearTwoFactorState(player.getUniqueId());
        hideAuthBossBar(player);

        clearAuthEffects(player);

        restorePreviousLocation(player);
        previousLocations.remove(player.getUniqueId());
        restoreAuthInventory(player);
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
            logConfigInfo(
                    "[!] Aethelguard ilk kez kuruldu! Lutfen config.yml ayarlarini kontrol edip sunucuyu tekrar baslatin.",
                    "[!] Aethelguard installed for the first time! Please check config.yml and restart."
            );
        }

        logInfo("Aethelguard çekirdek altyapısı kuruluyor...", "Initializing Aethelguard core infrastructure...");

        saveDefaultLangFiles();
        saveDefaultSecurityQuestionFiles();
        reloadLangConfig();
        ensureStatusStorage();

        reconnectDatabase();

        boolean tpVoid = getConfig().getBoolean("auth-settings.teleport-to-void", true);
        for (Player onlinePlayer : getServer().getOnlinePlayers()) {
            UUID uuid = onlinePlayer.getUniqueId();
            unauthenticatedPlayers.add(uuid);
            rememberPreviousLocation(onlinePlayer);
            hideInventoryForAuth(onlinePlayer);

            if (tpVoid) {
                Location voidLoc = getVoidLocation(onlinePlayer);
                onlinePlayer.teleport(voidLoc);
            }
            applyAuthEffects(onlinePlayer);
            sendMessage(onlinePlayer, "messages.plugin-reloaded-auth", true);
        }

        AuthCommand authCommand = new AuthCommand(this);
        AdminCommand adminCommand = new AdminCommand(this);
        PinCommand pinCommand = new PinCommand(this);
        pinGui = new PinGui(this, pinCommand);

        getCommand("register").setExecutor(authCommand);
        getCommand("login").setExecutor(authCommand);
        getCommand("pin").setExecutor(pinCommand);
        getCommand("setpin").setExecutor(pinCommand);
        getCommand("changepin").setExecutor(pinCommand);
        getCommand("authmode").setExecutor(pinCommand);
        getCommand("authmode").setTabCompleter(CommandCompletions.firstArgument(List.of("status", "password", "pin")));
        getCommand("changepassword").setExecutor(new PlayerPasswordCommand(this));
        getCommand("captcha").setExecutor(new CaptchaCommand(this));
        getCommand("twofactor").setExecutor(new TwoFactorCommand(this));
        getCommand("twofactor").setTabCompleter(CommandCompletions.firstArgument(List.of("status", "setup", "confirm", "disable")));
        getCommand("securityquestion").setExecutor(new SecurityQuestionCommand(this));
        getCommand("securityquestion").setTabCompleter(CommandCompletions.firstArgument(List.of("status", "setup", "change", "answer")));
        getCommand("backupcodes").setExecutor(new BackupCodesCommand(this));
        getCommand("backupcodes").setTabCompleter(CommandCompletions.firstArgument(List.of("generate")));
        getCommand("recoverymethod").setExecutor(new RecoveryMethodCommand(this));
        getCommand("recoverymethod").setTabCompleter(CommandCompletions.firstArgument(List.of("status", "question", "backup-code")));
        getCommand("recover").setExecutor(new RecoverCommand(this));
        getCommand("recover").setTabCompleter(CommandCompletions.firstArgument(List.of("question", "code", "backup-code")));
        getCommand("aethelguard").setExecutor(adminCommand);
        getCommand("aethelguard").setTabCompleter(adminCommand);
        getServer().getPluginManager().registerEvents(new AuthListener(this), this);
        getServer().getPluginManager().registerEvents(new ChatListener(this), this);
        getServer().getPluginManager().registerEvents(pinGui, this);

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
                restoreAuthInventory(player);
                hideAuthBossBar(player);
            }
        }
        if (databaseManager != null) databaseManager.close();
    }

    public boolean reloadPluginSettings() {
        try {
            reloadConfig();
            validateConfig();
            saveDefaultLangFiles();
            saveDefaultSecurityQuestionFiles();
            reloadLangConfig();
            ensureStatusStorage();
            reconnectDatabase();
            refreshOnlineAccountSnapshots();

            logInfo("Aethelguard ayarları yeniden yüklendi.", "Aethelguard settings reloaded.");
            return true;
        } catch (Exception e) {
            logWarning("Ayarlar yeniden yüklenirken hata oluştu.", "An error occurred while reloading settings.");
            e.printStackTrace();
            return false;
        }
    }

    private void reconnectDatabase() {
        if (databaseManager != null) {
            databaseManager.close();
        }

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
    }

    private void refreshOnlineAccountSnapshots() {
        for (Player player : getServer().getOnlinePlayers()) {
            if (isAuthenticated(player)) {
                updateAccountSnapshot(player, false);
            }
        }
    }

    public void validateConfig() {
        boolean changed = false;
        List<String> addedConfigKeys = new ArrayList<>();

        if (!getConfig().isSet("auth-settings.captcha.kick-enabled")
                && getConfig().isSet("auth-settings.captcha.kick-on-fail")) {
            getConfig().set("auth-settings.captcha.kick-enabled",
                    getConfig().getBoolean("auth-settings.captcha.kick-on-fail", true));
            addedConfigKeys.add("auth-settings.captcha.kick-enabled");
            changed = true;
        }

        if (getConfig().isSet("auth-settings.prompts.interval-ticks")) {
            long legacyPromptInterval = getConfig().getLong("auth-settings.prompts.interval-ticks", 200L);
            if (!getConfig().isSet("auth-settings.prompts.captcha-interval-ticks")) {
                getConfig().set("auth-settings.prompts.captcha-interval-ticks", legacyPromptInterval);
                addedConfigKeys.add("auth-settings.prompts.captcha-interval-ticks");
                changed = true;
            }
            if (!getConfig().isSet("auth-settings.prompts.login-interval-ticks")) {
                getConfig().set("auth-settings.prompts.login-interval-ticks", legacyPromptInterval);
                addedConfigKeys.add("auth-settings.prompts.login-interval-ticks");
                changed = true;
            }
            if (!getConfig().isSet("auth-settings.prompts.register-interval-ticks")) {
                getConfig().set("auth-settings.prompts.register-interval-ticks", legacyPromptInterval);
                addedConfigKeys.add("auth-settings.prompts.register-interval-ticks");
                changed = true;
            }
        }

        Map<String, Object> defaults = Map.ofEntries(
                Map.entry("console-language", "en"),
                Map.entry("console-text-mode", "ascii"),
                Map.entry("default-language", "TR"),
                Map.entry("prefix", "<yellow>[Aethelguard] </yellow>"),
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
                Map.entry("auth-settings.registration.ip-limit.enabled", true),
                Map.entry("auth-settings.registration.ip-limit.max-accounts", 2),
                Map.entry("auth-settings.registration.ip-limit.bypass-permission", "aethelguard.bypass.iplimit"),
                Map.entry("auth-settings.password-policy.enabled", true),
                Map.entry("auth-settings.password-policy.min-length", 4),
                Map.entry("auth-settings.password-policy.max-length", 64),
                Map.entry("auth-settings.password-policy.require-letter", false),
                Map.entry("auth-settings.password-policy.require-number", false),
                Map.entry("auth-settings.password-policy.block-username", true),
                Map.entry("auth-settings.password-policy.allow-turkish-characters", true),
                Map.entry("auth-settings.password-policy.allow-punctuation", true),
                Map.entry("auth-settings.password-policy.allow-non-alphabet-symbols", false),
                Map.entry("auth-settings.password-policy.blocked-words", List.of("admin", "password", "sifre", "şifre", "123456", "qwerty", "fuck", "shit", "bitch", "asshole", "bastard", "dick", "pussy", "cunt", "orospu", "amk", "aq", "siktir", "sik", "pic", "piç", "yarrak", "göt", "got")),
                Map.entry("auth-settings.pin.enabled", false),
                Map.entry("auth-settings.pin.default-auth-mode", "PASSWORD"),
                Map.entry("auth-settings.pin.allow-player-auth-mode-change", true),
                Map.entry("auth-settings.pin.min-length", 4),
                Map.entry("auth-settings.pin.max-length", 8),
                Map.entry("auth-settings.pin.numeric-only", true),
                Map.entry("auth-settings.pin.allow-repeated-digits", false),
                Map.entry("auth-settings.pin.allow-sequential-digits", false),
                Map.entry("auth-settings.pin.block-common-pins", true),
                Map.entry("auth-settings.pin.common-pins", List.of("0000", "1111", "1234", "123456", "1212", "7777", "2580", "0852")),
                Map.entry("auth-settings.pin.wrong-pin.max-attempts", 3),
                Map.entry("auth-settings.pin.wrong-pin.kick-enabled", true),
                Map.entry("auth-settings.pin.gui.enabled", false),
                Map.entry("auth-settings.pin.gui.theme", "quartz"),
                Map.entry("auth-settings.pin.gui.use-special-buttons", false),
                Map.entry("auth-settings.pin.gui.hide-input", true),
                Map.entry("auth-settings.pin.gui.randomize-numbers", false),
                Map.entry("auth-settings.pin.gui.max-digits", 4),
                Map.entry("adaptive-security.enabled", true),
                Map.entry("adaptive-security.trusted-ip-captcha-bypass.enabled", true),
                Map.entry("adaptive-security.trusted-ip-captcha-bypass.window-minutes", 30),
                Map.entry("adaptive-security.trusted-ip-captcha-bypass.required-successful-logins", 3),
                Map.entry("adaptive-security.suspicious-ip-extra-captcha.enabled", true),
                Map.entry("adaptive-security.suspicious-ip-extra-captcha.manual-suspicious-ips", List.of()),
                Map.entry("adaptive-security.suspicious-ip-extra-captcha.reasons.max-accounts-per-ip", 3),
                Map.entry("adaptive-security.suspicious-ip-extra-captcha.reasons.max-current-wrong-attempts", 2),
                Map.entry("adaptive-security.suspicious-ip-extra-captcha.vpn-check.enabled", true),
                Map.entry("adaptive-security.suspicious-ip-extra-captcha.vpn-check.providers", List.of("IPWHOIS", "IPAPI")),
                Map.entry("adaptive-security.suspicious-ip-extra-captcha.vpn-check.min-detections", 1),
                Map.entry("adaptive-security.suspicious-ip-extra-captcha.vpn-check.timeout-ms", 2500),
                Map.entry("adaptive-security.suspicious-ip-extra-captcha.vpn-check.cache-minutes", 360),
                Map.entry("adaptive-security.suspicious-ip-extra-captcha.vpn-check.fail-open", true),
                Map.entry("adaptive-security.suspicious-ip-extra-captcha.vpn-check.check-private-ips", false),
                Map.entry("adaptive-security.suspicious-ip-extra-captcha.vpn-check.log-results", true),
                Map.entry("adaptive-security.suspicious-ip-extra-captcha.captcha-mode", "RANDOM_EXCEPT_NORMAL"),
                Map.entry("adaptive-security.suspicious-ip-extra-captcha.fixed-type", "MATH"),
                Map.entry("adaptive-security.suspicious-ip-extra-captcha.types", List.of("TEXT", "NUMERIC", "ALPHANUMERIC", "MATH")),
                Map.entry("recovery.enabled", true),
                Map.entry("recovery.offer-before-kick", true),
                Map.entry("recovery.security-questions.enabled", true),
                Map.entry("recovery.backup-codes.enabled", true),
                Map.entry("recovery.backup-codes.count", 6),
                Map.entry("recovery.backup-codes.length", 10),
                Map.entry("security-cooldowns.enabled", true),
                Map.entry("security-cooldowns.commands.changepassword-hours", 72),
                Map.entry("security-cooldowns.commands.two-factor-setup-hours", 24),
                Map.entry("security-cooldowns.commands.two-factor-disable-hours", 72),
                Map.entry("security-cooldowns.commands.security-question-change-hours", 72),
                Map.entry("security-cooldowns.commands.recovery-method-change-hours", 72),
                Map.entry("security-cooldowns.commands.backup-code-generate-hours", 24),
                Map.entry("security-cooldowns.commands.recover-hours", 24),
                Map.entry("security-cooldowns.custom.enabled", true),
                Map.entry("security-cooldowns.custom.hours", 24),
                Map.entry("security-cooldowns.custom.commands", List.of()),
                Map.entry("auth-settings.captcha.enabled", true),
                Map.entry("auth-settings.captcha.types", List.of("MAP")),
                Map.entry("auth-settings.captcha.length", 5),
                Map.entry("auth-settings.captcha.case-sensitive", false),
                Map.entry("auth-settings.captcha.timeout-seconds", 60),
                Map.entry("auth-settings.captcha.cooldown-seconds", 2),
                Map.entry("auth-settings.captcha.max-attempts", 5),
                Map.entry("auth-settings.captcha.kick-enabled", true),
                Map.entry("auth-settings.captcha.map.give-item", true),
                Map.entry("auth-settings.captcha.map.item-name", "<yellow>Captcha"),
                Map.entry("auth-settings.captcha.success-sound.enabled", true),
                Map.entry("auth-settings.captcha.success-sound.sound", "entity.experience_orb.pickup"),
                Map.entry("auth-settings.captcha.success-sound.volume", 1.0),
                Map.entry("auth-settings.captcha.success-sound.pitch", 1.4),
                Map.entry("auth-settings.captcha.success-sound.repeat-times", 1),
                Map.entry("auth-settings.captcha.success-sound.repeat-interval-ticks", 1),
                Map.entry("auth-settings.two-factor.enabled", true),
                Map.entry("auth-settings.two-factor.issuer", "Aethelguard"),
                Map.entry("auth-settings.two-factor.code-window", 1),
                Map.entry("auth-settings.sessions.enabled", true),
                Map.entry("auth-settings.sessions.duration-minutes", 10),
                Map.entry("auth-settings.sessions.match-ip", true),
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
                Map.entry("auth-settings.prompts.captcha-repeat-enabled", true),
                Map.entry("auth-settings.prompts.captcha-interval-ticks", 200),
                Map.entry("auth-settings.prompts.login-repeat-enabled", true),
                Map.entry("auth-settings.prompts.login-interval-ticks", 200),
                Map.entry("auth-settings.prompts.register-repeat-enabled", true),
                Map.entry("auth-settings.prompts.register-interval-ticks", 200),
                Map.entry("auth-settings.prompts.pin-repeat-enabled", true),
                Map.entry("auth-settings.prompts.pin-interval-ticks", 200),
                Map.entry("auth-settings.prompts.setpin-repeat-enabled", true),
                Map.entry("auth-settings.prompts.setpin-interval-ticks", 200),
                Map.entry("auth-settings.prompts.two-factor-repeat-enabled", true),
                Map.entry("auth-settings.prompts.two-factor-interval-ticks", 200),
                Map.entry("auth-settings.bossbar.enabled", true),
                Map.entry("auth-settings.bossbar.color", "YELLOW"),
                Map.entry("auth-settings.bossbar.style", "SOLID"),
                Map.entry("auth-settings.bossbar.progress", 1.0),
                Map.entry("auth-settings.timeout.enabled", true),
                Map.entry("auth-settings.timeout.ticks", 1200),
                Map.entry("auth-settings.timeout.kick", true),
                Map.entry("auth-settings.restrictions.prevent-movement", true),
                Map.entry("auth-settings.restrictions.prevent-damage", true),
                Map.entry("auth-settings.restrictions.prevent-block-break", true),
                Map.entry("auth-settings.restrictions.prevent-block-place", true),
                Map.entry("auth-settings.restrictions.prevent-chat", true),
                Map.entry("auth-settings.restrictions.hide-chat-from-unauthenticated", true),
                Map.entry("auth-settings.commands.allowed", List.of("/login", "/giris", "/giriş", "/register", "/kayitol", "/kayit", "/kayıtol", "/pin", "/setpin", "/pinayarla", "/captcha", "/dogrula", "/doğrula", "/twofactor", "/2fa", "/authenticator", "/authy", "/recover", "/sifresifirla", "/şifresıfırla", "/kurtar")),
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
                Map.entry("auth-settings.sounds.pin-gui-click.enabled", true),
                Map.entry("auth-settings.sounds.pin-gui-click.sound", "ui.button.click"),
                Map.entry("auth-settings.sounds.pin-gui-click.volume", 0.6),
                Map.entry("auth-settings.sounds.pin-gui-click.pitch", 1.2),
                Map.entry("auth-settings.sounds.pin-gui-click.repeat-times", 1),
                Map.entry("auth-settings.sounds.pin-gui-click.repeat-interval-ticks", 1),
                Map.entry("auth-settings.sounds.pin-gui-confirm.enabled", true),
                Map.entry("auth-settings.sounds.pin-gui-confirm.sound", "entity.experience_orb.pickup"),
                Map.entry("auth-settings.sounds.pin-gui-confirm.volume", 0.8),
                Map.entry("auth-settings.sounds.pin-gui-confirm.pitch", 1.4),
                Map.entry("auth-settings.sounds.pin-gui-confirm.repeat-times", 1),
                Map.entry("auth-settings.sounds.pin-gui-confirm.repeat-interval-ticks", 1),
                Map.entry("auth-settings.sounds.pin-gui-open.enabled", true),
                Map.entry("auth-settings.sounds.pin-gui-open.sound", "block.note_block.pling"),
                Map.entry("auth-settings.sounds.pin-gui-open.volume", 0.5),
                Map.entry("auth-settings.sounds.pin-gui-open.pitch", 1.4),
                Map.entry("auth-settings.sounds.pin-gui-open.repeat-times", 1),
                Map.entry("auth-settings.sounds.pin-gui-open.repeat-interval-ticks", 1),
                Map.entry("auth-settings.sounds.pin-gui-close.enabled", true),
                Map.entry("auth-settings.sounds.pin-gui-close.sound", "block.chest.close"),
                Map.entry("auth-settings.sounds.pin-gui-close.volume", 0.5),
                Map.entry("auth-settings.sounds.pin-gui-close.pitch", 1.0),
                Map.entry("auth-settings.sounds.pin-gui-close.repeat-times", 1),
                Map.entry("auth-settings.sounds.pin-gui-close.repeat-interval-ticks", 1),
                Map.entry("auth-settings.sounds.pin-gui-wrong.enabled", true),
                Map.entry("auth-settings.sounds.pin-gui-wrong.sound", "entity.villager.no"),
                Map.entry("auth-settings.sounds.pin-gui-wrong.volume", 1.0),
                Map.entry("auth-settings.sounds.pin-gui-wrong.pitch", 0.9),
                Map.entry("auth-settings.sounds.pin-gui-wrong.repeat-times", 1),
                Map.entry("auth-settings.sounds.pin-gui-wrong.repeat-interval-ticks", 1),
                Map.entry("auth-settings.sounds.pin-gui-disabled-confirm.enabled", true),
                Map.entry("auth-settings.sounds.pin-gui-disabled-confirm.sound", "entity.villager.no"),
                Map.entry("auth-settings.sounds.pin-gui-disabled-confirm.volume", 0.8),
                Map.entry("auth-settings.sounds.pin-gui-disabled-confirm.pitch", 0.7),
                Map.entry("auth-settings.sounds.pin-gui-disabled-confirm.repeat-times", 1),
                Map.entry("auth-settings.sounds.pin-gui-disabled-confirm.repeat-interval-ticks", 1),
                Map.entry("auth-settings.sounds.pin-gui-success.enabled", true),
                Map.entry("auth-settings.sounds.pin-gui-success.sound", "entity.villager.trade"),
                Map.entry("auth-settings.sounds.pin-gui-success.volume", 1.0),
                Map.entry("auth-settings.sounds.pin-gui-success.pitch", 1.1),
                Map.entry("auth-settings.sounds.pin-gui-success.repeat-times", 1),
                Map.entry("auth-settings.sounds.pin-gui-success.repeat-interval-ticks", 1),
                Map.entry("console-logging.suppress-server-connection-logs", true),
                Map.entry("console-logging.log-auth-success", true),
                Map.entry("console-logging.log-auth-state-changes", true),
                Map.entry("console-logging.log-blocked-chat-attempts", true),
                Map.entry("storage.local-users-folder", "users"),
                Map.entry("status.enabled", true),
                Map.entry("status.create-local-users-folder", true),
                Map.entry("status.user-index-file", "user-index.txt")
        );

        for (Map.Entry<String, Object> entry : defaults.entrySet()) {
            if (!getConfig().isSet(entry.getKey())) {
                logWarning("Ayarlarda eksik bulundu: " + entry.getKey() + ". Varsayılan değer atanıyor.",
                        "Missing config key: " + entry.getKey() + ". Setting default value.");
                getConfig().set(entry.getKey(), entry.getValue());
                addedConfigKeys.add(entry.getKey());
                changed = true;
            }
        }

        if (getConfig().isSet("local-logging")) {
            getConfig().set("local-logging", null);
            changed = true;
        }
        if (getConfig().isSet("auth-settings.pin.gui.filler-material")) {
            getConfig().set("auth-settings.pin.gui.filler-material", null);
            changed = true;
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
        if (!allowedCommands.contains("/kayit")) {
            allowedCommands.add("/kayit");
            changed = true;
        }
        for (String command : List.of("/pin", "/setpin", "/pinayarla")) {
            if (!allowedCommands.contains(command)) {
                allowedCommands.add(command);
                changed = true;
            }
        }
        if (changed) {
            getConfig().set("auth-settings.commands.allowed", allowedCommands);
        }

        String pinGuiTheme = getConfig().getString("auth-settings.pin.gui.theme", "quartz");
        if (pinGuiTheme != null
                && pinGuiTheme.equalsIgnoreCase("netherite")
                && !getConfig().getBoolean("auth-settings.pin.gui.use-special-buttons", false)) {
            getConfig().set("auth-settings.pin.gui.use-special-buttons", true);
            logWarning(
                    "Netherite PIN GUI temasinda special tuslar zorunludur. use-special-buttons otomatik true yapildi.",
                    "Special buttons are required for the netherite PIN GUI theme. use-special-buttons was forced to true."
            );
            changed = true;
        }

        if (changed) {
            saveConfig();
        }

        boolean layoutChanged = syncConfigFileLayout();

        if (changed) {
            if (!addedConfigKeys.isEmpty()) {
                logConfigInfo(
                        "Config kontrol edildi, eksik " + addedConfigKeys.size() + " ayar eklendi: " + String.join(", ", addedConfigKeys),
                        "Config checked, added " + addedConfigKeys.size() + " missing setting(s): " + String.join(", ", addedConfigKeys)
                );
            } else {
                logConfigInfo(
                        "Config kontrol edildi ve gerekli temizlikler uygulandı.",
                        "Config checked and required cleanup was applied."
                );
            }
        } else if (!layoutChanged) {
            logConfigInfo(
                    "Config kontrol edildi, eksik ayar bulunmadı.",
                    "Config checked, no missing settings found."
            );
        }
    }

    private boolean syncConfigFileLayout() {
        File configFile = new File(getDataFolder(), "config.yml");
        if (!configFile.exists()) return false;

        try (InputStream inputStream = getResource("config.yml")) {
            if (inputStream == null) return false;

            List<String> templateLines = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))
                    .lines()
                    .toList();
            List<String> currentLines = java.nio.file.Files.readAllLines(configFile.toPath(), StandardCharsets.UTF_8);
            Set<String> movedKnownKeys = findMovedKnownConfigKeys(currentLines, templateLines);
            List<String> syncedLines = renderConfigFromTemplate(templateLines);
            String currentText = String.join(System.lineSeparator(), currentLines).trim();
            String syncedText = String.join(System.lineSeparator(), syncedLines).trim();

            if (currentText.equals(syncedText)) {
                return false;
            }

            java.nio.file.Files.write(configFile.toPath(), syncedLines, StandardCharsets.UTF_8);
            reloadConfig();

            if (!movedKnownKeys.isEmpty()) {
                logConfigInfo(
                        "Config duzeni duzeltildi, " + movedKnownKeys.size() + " ayar dogru bolumune tasindi: " + String.join(", ", movedKnownKeys),
                        "Config layout corrected, moved " + movedKnownKeys.size() + " setting(s) back to the correct section. "
                );
            } else {
                logConfigInfo(
                        "Config duzeni duzeltildi, yeni ayarlar yorumlariyla dogru bolume yerlestirildi.",
                        "Config layout corrected, new settings were placed with comments in the correct section."
                );
            }
            return true;
        } catch (IOException e) {
            logWarning("Config duzeni senkronize edilemedi.", "Could not synchronize config layout.");
            e.printStackTrace();
            return false;
        }
    }

    private List<String> renderConfigFromTemplate(List<String> templateLines) {
        List<String> rendered = new ArrayList<>();
        Deque<ConfigPathPart> stack = new ArrayDeque<>();

        for (int i = 0; i < templateLines.size(); i++) {
            String line = templateLines.get(i);
            ConfigLine configLine = parseConfigLine(line, stack);

            if (configLine == null || configLine.path().isBlank()) {
                rendered.add(line);
                continue;
            }

            Object value = getConfig().get(configLine.path());
            if (value instanceof org.bukkit.configuration.ConfigurationSection) {
                rendered.add(line);
                continue;
            }

            if (value instanceof List<?> list) {
                rendered.add(configLine.indentText() + configLine.key() + ":");
                for (Object item : list) {
                    rendered.add(configLine.indentText() + "  - " + formatYamlValue(item));
                }
                i = skipTemplateChildBlock(templateLines, i, configLine.indent());
                continue;
            }

            rendered.add(configLine.indentText() + configLine.key() + ": " + formatYamlValue(value));
        }

        if (!rendered.isEmpty()) {
            rendered.add("");
        }
        return rendered;
    }

    private int skipTemplateChildBlock(List<String> lines, int index, int parentIndent) {
        int next = index + 1;
        while (next < lines.size()) {
            String line = lines.get(next);
            if (line.isBlank() || line.trim().startsWith("#")) {
                break;
            }
            int indent = countIndent(line);
            if (indent <= parentIndent) {
                break;
            }
            next++;
        }
        return next - 1;
    }

    private Set<String> findMovedKnownConfigKeys(List<String> currentLines, List<String> templateLines) {
        List<String> templateOrder = extractConfigPathOrder(templateLines);
        List<String> currentOrder = extractConfigPathOrder(currentLines);
        Set<String> templatePaths = new LinkedHashSet<>(templateOrder);
        List<String> currentKnownOrder = currentOrder.stream()
                .filter(templatePaths::contains)
                .toList();
        List<String> expectedKnownOrder = templateOrder.stream()
                .filter(currentKnownOrder::contains)
                .toList();

        Set<String> moved = new LinkedHashSet<>();
        int count = Math.min(currentKnownOrder.size(), expectedKnownOrder.size());
        for (int i = 0; i < count; i++) {
            if (!currentKnownOrder.get(i).equals(expectedKnownOrder.get(i))) {
                moved.add(currentKnownOrder.get(i));
            }
        }
        return moved;
    }

    private List<String> extractConfigPathOrder(List<String> lines) {
        List<String> order = new ArrayList<>();
        Deque<ConfigPathPart> stack = new ArrayDeque<>();
        for (String line : lines) {
            ConfigLine configLine = parseConfigLine(line, stack);
            if (configLine != null && !configLine.path().isBlank()) {
                order.add(configLine.path());
            }
        }
        return order;
    }

    private ConfigLine parseConfigLine(String line, Deque<ConfigPathPart> stack) {
        if (line.isBlank() || line.trim().startsWith("#") || line.trim().startsWith("-")) {
            return null;
        }

        int colonIndex = line.indexOf(':');
        if (colonIndex < 0) return null;

        String key = line.substring(0, colonIndex).trim();
        if (key.isBlank() || key.contains(" ")) return null;

        int indent = countIndent(line);
        while (!stack.isEmpty() && stack.peekLast().indent() >= indent) {
            stack.removeLast();
        }
        stack.addLast(new ConfigPathPart(indent, key));

        String path = stack.stream()
                .map(ConfigPathPart::key)
                .reduce((left, right) -> left + "." + right)
                .orElse("");
        return new ConfigLine(indent, line.substring(0, indent), key, path);
    }

    private int countIndent(String line) {
        int indent = 0;
        while (indent < line.length() && line.charAt(indent) == ' ') {
            indent++;
        }
        return indent;
    }

    private String formatYamlValue(Object value) {
        if (value == null) return "null";
        if (value instanceof Boolean || value instanceof Number) {
            return String.valueOf(value);
        }
        return "\"" + String.valueOf(value)
                .replace("\\", "\\\\")
                .replace("\"", "\\\"") + "\"";
    }

    private record ConfigPathPart(int indent, String key) {
    }

    private record ConfigLine(int indent, String indentText, String key, String path) {
    }

    public void ensureStatusStorage() {
        if (getConfig().getBoolean("status.enabled", true)) {
            if (!getConfig().getBoolean("database.enabled", false)
                    && getConfig().getBoolean("status.create-local-users-folder", true)) {
                File usersFolder = getLocalUsersFolder();
                if (!usersFolder.exists()) usersFolder.mkdirs();
                rebuildUserIndex();
            }
        }
    }

    public void rebuildUserIndex() {
        if (!getConfig().getBoolean("status.enabled", true)) return;
        if (getConfig().getBoolean("database.enabled", false)) return;

        File usersFolder = getLocalUsersFolder();
        if (!usersFolder.exists()) {
            if (!getConfig().getBoolean("status.create-local-users-folder", true)) return;
            usersFolder.mkdirs();
        }

        File[] files = usersFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        List<String> lines = new ArrayList<>();
        lines.add("# Aethelguard user index");
        lines.add("# username: uuid");

        if (files != null) {
            Arrays.sort(files, Comparator.comparing(File::getName));
            for (File file : files) {
                FileConfiguration userConfig = YamlConfiguration.loadConfiguration(file);
                String username = userConfig.getString("username", "");
                String uuid = userConfig.getString("uuid", file.getName().replace(".yml", ""));
                if (!username.isBlank() && !uuid.isBlank()) {
                    lines.add(username + ": " + uuid);
                }
            }
        }

        File indexFile = new File(usersFolder, getSafeFileName("status.user-index-file", "user-index.txt"));
        try {
            java.nio.file.Files.write(indexFile.toPath(), lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            logWarning("Kullanıcı index dosyası yazılamadı.", "Could not write user index file.");
        }
    }

    private String getSafeFileName(String path, String fallback) {
        String fileName = getConfig().getString(path, fallback);
        if (fileName == null || fileName.isBlank() || fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            logWarning("Geçersiz dosya ayarı: " + path + ". Varsayılan dosya kullanılıyor.",
                    "Invalid file config: " + path + ". Using default file.");
            return fallback;
        }
        return fileName;
    }

    public void logInfo(String tr, String en) {
        getServer().getConsoleSender().sendMessage(
                consolePrefix + "§r" + getConsoleMessage(tr, en)
        );
    }

    public void logWarning(String tr, String en) {
        getServer().getConsoleSender().sendMessage(
                consolePrefix + "§e" + getConsoleMessage(tr, en)
        );
    }

    public void logConfigInfo(String tr, String en) {
        getServer().getConsoleSender().sendMessage(
                consolePrefix + "§b" + getConsoleMessage(tr, en)
        );
    }

    private String getConsoleMessage(String tr, String en) {
        if (!isTurkishConsole()) {
            return en;
        }
        return getConfig().getString("console-text-mode", "ascii").equalsIgnoreCase("native")
                ? tr
                : toConsoleAscii(tr);
    }

    private String toConsoleAscii(String message) {
        return message
                .replace('ç', 'c').replace('Ç', 'C')
                .replace('ğ', 'g').replace('Ğ', 'G')
                .replace('ı', 'i').replace('İ', 'I')
                .replace('ö', 'o').replace('Ö', 'O')
                .replace('ş', 's').replace('Ş', 'S')
                .replace('ü', 'u').replace('Ü', 'U');
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

    private void saveDefaultSecurityQuestionFiles() {
        File dir = new File(getDataFolder(), "security_questions");
        if (!dir.exists()) dir.mkdirs();

        for (String f : new String[]{"security_questions_tr.yml", "security_questions_en.yml"}) {
            File outFile = new File(dir, f);
            if (!outFile.exists()) {
                saveResource(f, false);
                File tempFile = new File(getDataFolder(), f);
                if (tempFile.exists()) {
                    tempFile.renameTo(outFile);
                }
            }
        }
    }

    public void sendMessage(CommandSender sender, String path, boolean prefix) {
        sendMessage(sender, path, prefix, Map.of());
    }

    public void sendMessage(CommandSender sender, String path, boolean prefix, Map<String, String> placeholders) {

        String msg = applyPlaceholders(getFormattedMessageString(path, prefix), placeholders);



        String cleanMsg = msg.replace("§", "&");


        String processed = cleanMsg;

        for (Map.Entry<String, String> entry : COLOR_MAP.entrySet()) {
            processed = processed.replace(entry.getKey(), entry.getValue());
        }


        sender.sendMessage(miniMessage.deserialize(processed));
    }

    public String getRawStringMessage(String path, boolean prefix) {
        return getRawStringMessage(path, prefix, Map.of());
    }

    public String getRawStringMessage(String path, boolean prefix, Map<String, String> placeholders) {
        return LegacyComponentSerializer.legacySection().serialize(
                LegacyComponentSerializer.legacyAmpersand().deserialize(applyPlaceholders(getFormattedMessageString(path, prefix), placeholders).replace("§", "&"))
        );
    }

    public String getFormattedMessageString(String path, boolean prefix) {
        String msg = getLangConfig().getString(path);
        if (msg == null) return "<red>[Missing node: " + path + "]";
        return prefix ? (getConfig().getString("prefix", "<yellow>[Aethelguard] </yellow>") + msg) : msg;
    }

    private String applyPlaceholders(String message, Map<String, String> placeholders) {
        String processed = message;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            processed = processed.replace("{" + entry.getKey() + "}", entry.getValue() == null ? "" : entry.getValue());
        }
        return processed;
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

    public AccountStatus getAccountStatus(String username) {
        return getConfig().getBoolean("database.enabled", false)
                ? getDatabaseAccountStatus(username)
                : getLocalAccountStatus(username);
    }

    public void updateAccountSnapshot(Player player, boolean incrementLoginCount) {
        String uuid = player.getUniqueId().toString();
        String now = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        String ipAddress = player.getAddress() == null
                ? "UNKNOWN"
                : player.getAddress().getAddress().getHostAddress();
        Location location = player.getLocation();
        String worldName = location.getWorld() == null ? "UNKNOWN" : location.getWorld().getName();

        if (!getConfig().getBoolean("database.enabled", false)) {
            File userFile = new File(getLocalUsersFolder(), uuid + ".yml");
            if (!userFile.exists()) return;

            FileConfiguration config = YamlConfiguration.loadConfiguration(userFile);
            config.set("uuid", uuid);
            config.set("username", player.getName());
            if (!config.contains("created-at")) {
                config.set("created-at", now);
            }
            config.set("last-login", now);
            config.set("last-ip", ipAddress);
            config.set("last-world", worldName);
            config.set("last-location.x", location.getX());
            config.set("last-location.y", location.getY());
            config.set("last-location.z", location.getZ());
            if (incrementLoginCount) {
                config.set("stats.login-count", config.getInt("stats.login-count", 0) + 1);
            }

            try {
                config.save(userFile);
                rebuildUserIndex();
            } catch (IOException e) {
                logWarning("Kullanıcı snapshot kaydı yazılamadı: " + player.getName(), "Could not write user snapshot: " + player.getName());
            }
            return;
        }

        try (java.sql.Connection conn = getDatabaseManager().getConnection()) {
            if (conn == null) return;

            String loginCountSql = incrementLoginCount ? ", login_count = login_count + 1" : "";
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE " + getAuthTableName() +
                            " SET username = ?, last_ip = ?, last_world = ?, last_x = ?, last_y = ?, last_z = ?" + loginCountSql +
                            " WHERE uuid = ?;"
            )) {
                ps.setString(1, player.getName());
                ps.setString(2, ipAddress);
                ps.setString(3, worldName);
                ps.setDouble(4, location.getX());
                ps.setDouble(5, location.getY());
                ps.setDouble(6, location.getZ());
                ps.setString(7, uuid);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            logWarning("Kullanıcı snapshot kaydı veritabanına yazılamadı: " + player.getName(), "Could not write user snapshot to database: " + player.getName());
        }
    }

    private AccountStatus getLocalAccountStatus(String username) {
        File folder = getLocalUsersFolder();
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) {
            return AccountStatus.notFound(username, "Local");
        }

        for (File file : files) {
            FileConfiguration userConfig = YamlConfiguration.loadConfiguration(file);
            String storedName = userConfig.getString("username", "");
            if (!storedName.equalsIgnoreCase(username)) {
                continue;
            }

            UUID uuid = parseUuid(userConfig.getString("uuid", file.getName().replace(".yml", "")));
            return buildAccountStatus(
                    storedName,
                    uuid,
                    "Local",
                    userConfig.getString("created-at", "-"),
                    userConfig.getString("last-login", "-"),
                    userConfig.getString("last-ip", "-"),
                    userConfig.getString("last-world", "-"),
                    formatStoredLocation(
                            userConfig.getDouble("last-location.x", 0.0),
                            userConfig.getDouble("last-location.y", 0.0),
                            userConfig.getDouble("last-location.z", 0.0),
                            userConfig.contains("last-location.x")
                    ),
                    userConfig.getInt("security.wrong-attempts-total", 0)
            );
        }

        return AccountStatus.notFound(username, "Local");
    }

    private AccountStatus getDatabaseAccountStatus(String username) {
        try (java.sql.Connection conn = getDatabaseManager().getConnection()) {
            if (conn == null) return AccountStatus.notFound(username, "MySQL");

            try (java.sql.PreparedStatement ps = conn.prepareStatement(
                    "SELECT uuid, username, created_at, last_login, last_ip, last_world, last_x, last_y, last_z, wrong_attempts_total " +
                            "FROM " + getAuthTableName() + " WHERE LOWER(username) = LOWER(?) LIMIT 1;"
            )) {
                ps.setString(1, username);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return AccountStatus.notFound(username, "MySQL");
                    }

                    return buildAccountStatus(
                            rs.getString("username"),
                            parseUuid(rs.getString("uuid")),
                            "MySQL",
                            stringOrDash(rs.getString("created_at")),
                            stringOrDash(rs.getString("last_login")),
                            stringOrDash(rs.getString("last_ip")),
                            stringOrDash(rs.getString("last_world")),
                            formatStoredLocation(rs.getDouble("last_x"), rs.getDouble("last_y"), rs.getDouble("last_z"), rs.getObject("last_x") != null),
                            rs.getInt("wrong_attempts_total")
                    );
                }
            }
        } catch (Exception e) {
            logWarning("Status bilgisi okunurken hata oluştu.", "Error while reading account status.");
            e.printStackTrace();
            return AccountStatus.notFound(username, "MySQL");
        }
    }

    private AccountStatus buildAccountStatus(String username, UUID uuid, String storage, String createdAt, String lastLogin, String lastIp, String lastWorld, String lastLocation, int totalWrongAttempts) {
        Player onlinePlayer = uuid == null ? getServer().getPlayerExact(username) : getServer().getPlayer(uuid);
        boolean online = onlinePlayer != null && onlinePlayer.isOnline();
        boolean authenticated = uuid != null && loggedInPlayers.contains(uuid);
        boolean waitingAuth = uuid != null && unauthenticatedPlayers.contains(uuid);
        int currentWrongAttempts = uuid == null ? 0 : wrongPasswordAttempts.getOrDefault(uuid, 0);

        return new AccountStatus(
                true,
                username,
                uuid,
                storage,
                online,
                authenticated,
                waitingAuth,
                currentWrongAttempts,
                totalWrongAttempts,
                stringOrDash(createdAt),
                stringOrDash(lastLogin),
                stringOrDash(lastIp),
                stringOrDash(lastWorld),
                stringOrDash(lastLocation)
        );
    }

    private UUID parseUuid(String value) {
        try {
            return value == null || value.isBlank() ? null : UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private String formatStoredLocation(double x, double y, double z, boolean hasLocation) {
        if (!hasLocation) return "-";
        return String.format(Locale.US, "%.2f, %.2f, %.2f", x, y, z);
    }

    private String stringOrDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    public boolean isAuthenticated(Player p) {
        return loggedInPlayers.contains(p.getUniqueId());
    }

    public boolean isCaptchaRequired(Player player) {
        if (shouldBypassCaptchaForTrustedIp(player)) {
            return false;
        }
        return getConfig().getBoolean("auth-settings.captcha.enabled", true)
                && unauthenticatedPlayers.contains(player.getUniqueId())
                && !captchaVerifiedPlayers.contains(player.getUniqueId());
    }

    public void prepareAdaptiveSecurity(Player player) {
        if (!getConfig().getBoolean("adaptive-security.enabled", true)) {
            extraCaptchaPlayers.remove(player.getUniqueId());
            return;
        }
        boolean suspicious = isSuspiciousIp(player);
        if (suspicious) {
            extraCaptchaPlayers.put(player.getUniqueId(), true);
            if (getConfig().getBoolean("console-logging.log-auth-state-changes", true)) {
                logInfo(
                        player.getName() + " supheli IP nedeniyle ekstra captcha alacak.",
                        player.getName() + " will receive extra captcha due to suspicious IP."
                );
            }
        } else {
            extraCaptchaPlayers.remove(player.getUniqueId());
        }
    }

    private boolean ensureVpnCheckReady(Player player) {
        if (!isVpnCheckEnabled()) return true;

        String ip = getPlayerIp(player);
        if (ip.equals("UNKNOWN") || shouldSkipVpnCheck(ip)) return true;

        VpnCheckResult cached = getCachedVpnCheck(ip);
        if (cached != null) return true;

        if (pendingVpnChecks.add(ip)) {
            runVpnCheckAsync(ip, player.getName(), player.getUniqueId());
        }
        return false;
    }

    private boolean shouldBypassCaptchaForTrustedIp(Player player) {
        if (!getConfig().getBoolean("adaptive-security.enabled", true)) return false;
        if (!getConfig().getBoolean("adaptive-security.trusted-ip-captcha-bypass.enabled", true)) return false;
        if (isSuspiciousIp(player)) return false;

        String ip = getPlayerIp(player);
        if (ip.equals("UNKNOWN")) return false;

        long windowMillis = Math.max(1L, getConfig().getLong("adaptive-security.trusted-ip-captcha-bypass.window-minutes", 30L)) * 60_000L;
        int required = Math.max(1, getConfig().getInt("adaptive-security.trusted-ip-captcha-bypass.required-successful-logins", 3));
        Deque<Long> logins = ipSuccessLogins.get(ip);
        if (logins == null) return false;
        pruneIpLoginWindow(logins, windowMillis);
        return logins.size() >= required;
    }

    private boolean isSuspiciousIp(Player player) {
        if (!getConfig().getBoolean("adaptive-security.suspicious-ip-extra-captcha.enabled", true)) return false;

        String ip = getPlayerIp(player);
        if (getConfig().getStringList("adaptive-security.suspicious-ip-extra-captcha.manual-suspicious-ips").contains(ip)) {
            return true;
        }

        VpnCheckResult vpnResult = getCachedVpnCheck(ip);
        if (vpnResult != null && vpnResult.suspicious()) {
            return true;
        }

        int maxAccounts = getConfig().getInt("adaptive-security.suspicious-ip-extra-captcha.reasons.max-accounts-per-ip", 3);
        if (maxAccounts > 0 && countAccountsByIp(ip) >= maxAccounts) {
            return true;
        }

        int maxWrong = getConfig().getInt("adaptive-security.suspicious-ip-extra-captcha.reasons.max-current-wrong-attempts", 2);
        return maxWrong > 0 && wrongPasswordAttempts.getOrDefault(player.getUniqueId(), 0) >= maxWrong;
    }

    public void recordAdaptiveSuccessfulAuth(Player player) {
        if (!getConfig().getBoolean("adaptive-security.enabled", true)) return;
        String ip = getPlayerIp(player);
        if (ip.equals("UNKNOWN")) return;
        long windowMillis = Math.max(1L, getConfig().getLong("adaptive-security.trusted-ip-captcha-bypass.window-minutes", 30L)) * 60_000L;
        Deque<Long> logins = ipSuccessLogins.computeIfAbsent(ip, ignored -> new ArrayDeque<>());
        logins.addLast(System.currentTimeMillis());
        pruneIpLoginWindow(logins, windowMillis);
    }

    private void pruneIpLoginWindow(Deque<Long> logins, long windowMillis) {
        long cutoff = System.currentTimeMillis() - windowMillis;
        while (!logins.isEmpty() && logins.peekFirst() < cutoff) {
            logins.removeFirst();
        }
    }

    private String getPlayerIp(Player player) {
        return player.getAddress() == null ? "UNKNOWN" : player.getAddress().getAddress().getHostAddress();
    }

    public PasswordPolicyResult validatePasswordPolicy(String password, String username) {
        if (!getConfig().getBoolean("auth-settings.password-policy.enabled", true)) {
            return PasswordPolicyResult.ok();
        }

        String safePassword = password == null ? "" : password;
        int minLength = Math.max(0, getConfig().getInt("auth-settings.password-policy.min-length", 4));
        int maxLength = Math.max(minLength, getConfig().getInt("auth-settings.password-policy.max-length", 64));

        if (safePassword.length() < minLength) {
            return PasswordPolicyResult.error("messages.password-policy-too-short", Map.of("min", String.valueOf(minLength)));
        }
        if (safePassword.length() > maxLength) {
            return PasswordPolicyResult.error("messages.password-policy-too-long", Map.of("max", String.valueOf(maxLength)));
        }
        if (getConfig().getBoolean("auth-settings.password-policy.require-letter", false)
                && safePassword.chars().noneMatch(Character::isLetter)) {
            return PasswordPolicyResult.error("messages.password-policy-require-letter", Map.of());
        }
        if (getConfig().getBoolean("auth-settings.password-policy.require-number", false)
                && safePassword.chars().noneMatch(Character::isDigit)) {
            return PasswordPolicyResult.error("messages.password-policy-require-number", Map.of());
        }
        if (!getConfig().getBoolean("auth-settings.password-policy.allow-turkish-characters", true)
                && containsTurkishCharacter(safePassword)) {
            return PasswordPolicyResult.error("messages.password-policy-turkish-characters", Map.of());
        }
        if (!getConfig().getBoolean("auth-settings.password-policy.allow-punctuation", true)
                && containsPunctuation(safePassword)) {
            return PasswordPolicyResult.error("messages.password-policy-punctuation", Map.of());
        }
        if (!getConfig().getBoolean("auth-settings.password-policy.allow-non-alphabet-symbols", false)
                && containsNonAlphabetSymbol(safePassword)) {
            return PasswordPolicyResult.error("messages.password-policy-non-alphabet-symbols", Map.of());
        }
        if (getConfig().getBoolean("auth-settings.password-policy.block-username", true)
                && username != null
                && !username.isBlank()
                && safePassword.toLowerCase(Locale.ROOT).contains(username.toLowerCase(Locale.ROOT))) {
            return PasswordPolicyResult.error("messages.password-policy-contains-name", Map.of());
        }
        String normalizedPassword = normalizePolicyText(safePassword);
        for (String blockedWord : getConfig().getStringList("auth-settings.password-policy.blocked-words")) {
            String normalizedBlockedWord = normalizePolicyText(blockedWord);
            if (!normalizedBlockedWord.isBlank() && normalizedPassword.contains(normalizedBlockedWord)) {
                return PasswordPolicyResult.error("messages.password-policy-blocked-word", Map.of());
            }
        }

        return PasswordPolicyResult.ok();
    }

    private boolean containsTurkishCharacter(String value) {
        return value != null && value.matches(".*[çÇğĞıİöÖşŞüÜ].*");
    }

    private boolean containsPunctuation(String value) {
        return value != null && value.matches(".*\\p{Punct}.*");
    }

    private boolean containsNonAlphabetSymbol(String value) {
        if (value == null) return false;
        for (int i = 0; i < value.length(); ) {
            int codePoint = value.codePointAt(i);
            i += Character.charCount(codePoint);
            if (Character.isLetterOrDigit(codePoint) || Character.isWhitespace(codePoint)) {
                continue;
            }
            char[] chars = Character.toChars(codePoint);
            String character = new String(chars);
            if (character.matches("\\p{Punct}")) {
                continue;
            }
            return true;
        }
        return false;
    }

    private String normalizePolicyText(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.ROOT)
                .replace('ç', 'c')
                .replace('ğ', 'g')
                .replace('ı', 'i')
                .replace('ö', 'o')
                .replace('ş', 's')
                .replace('ü', 'u');
    }

    private boolean isVpnCheckEnabled() {
        return getConfig().getBoolean("adaptive-security.suspicious-ip-extra-captcha.enabled", true)
                && getConfig().getBoolean("adaptive-security.suspicious-ip-extra-captcha.vpn-check.enabled", true);
    }

    private boolean shouldSkipVpnCheck(String ip) {
        if (getConfig().getBoolean("adaptive-security.suspicious-ip-extra-captcha.vpn-check.check-private-ips", false)) {
            return false;
        }
        try {
            InetAddress address = InetAddress.getByName(ip);
            return address.isAnyLocalAddress()
                    || address.isLoopbackAddress()
                    || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress()
                    || address.isMulticastAddress();
        } catch (IOException ignored) {
            return true;
        }
    }

    private VpnCheckResult getCachedVpnCheck(String ip) {
        VpnCheckResult result = vpnCheckCache.get(ip);
        if (result == null) return null;
        long ttlMillis = Math.max(1L, getConfig().getLong("adaptive-security.suspicious-ip-extra-captcha.vpn-check.cache-minutes", 360L)) * 60_000L;
        if (System.currentTimeMillis() - result.checkedAt() > ttlMillis) {
            vpnCheckCache.remove(ip);
            return null;
        }
        return result;
    }

    private void runVpnCheckAsync(String ip, String playerName, UUID playerUuid) {
        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            VpnCheckResult result = checkVpnProviders(ip);
            vpnCheckCache.put(ip, result);
            pendingVpnChecks.remove(ip);

            if (getConfig().getBoolean("adaptive-security.suspicious-ip-extra-captcha.vpn-check.log-results", true)) {
                logInfo(
                        playerName + " icin VPN kontrolu: " + result.reason(),
                        "VPN check for " + playerName + ": " + result.reason()
                );
            }

            getServer().getScheduler().runTask(this, () -> {
                Player player = getServer().getPlayer(playerUuid);
                if (player != null && player.isOnline() && unauthenticatedPlayers.contains(playerUuid) && !captchaChallenges.containsKey(playerUuid)) {
                    startCaptcha(player);
                }
            });
        });
    }

    private VpnCheckResult checkVpnProviders(String ip) {
        List<String> providers = getConfig().getStringList("adaptive-security.suspicious-ip-extra-captcha.vpn-check.providers");
        if (providers.isEmpty()) providers = List.of("IPWHOIS", "IPAPI");

        int detections = 0;
        int checked = 0;
        List<String> reasons = new ArrayList<>();
        for (String provider : providers) {
            VpnProviderResult result = queryVpnProvider(provider, ip);
            if (!result.success()) {
                reasons.add(provider.toUpperCase(Locale.ROOT) + ":error");
                continue;
            }
            checked++;
            if (result.suspicious()) detections++;
            reasons.add(provider.toUpperCase(Locale.ROOT) + ":" + result.reason());
        }

        int minDetections = Math.max(1, getConfig().getInt("adaptive-security.suspicious-ip-extra-captcha.vpn-check.min-detections", 1));
        boolean failOpen = getConfig().getBoolean("adaptive-security.suspicious-ip-extra-captcha.vpn-check.fail-open", true);
        boolean suspicious = checked == 0 ? !failOpen : detections >= minDetections;
        return new VpnCheckResult(suspicious, System.currentTimeMillis(), String.join(", ", reasons));
    }

    private VpnProviderResult queryVpnProvider(String provider, String ip) {
        String normalized = provider == null ? "" : provider.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "IPAPI" -> queryIpApi(ip);
            case "IPWHOIS" -> queryIpWhoIs(ip);
            default -> new VpnProviderResult(false, false, "unknown-provider");
        };
    }

    private VpnProviderResult queryIpWhoIs(String ip) {
        String body = httpGet("https://ipwho.is/" + ip + "?security=1");
        if (body == null || body.isBlank() || body.contains("\"success\":false")) {
            return new VpnProviderResult(false, false, "no-response");
        }
        boolean vpn = jsonBoolean(body, "vpn");
        boolean proxy = jsonBoolean(body, "proxy");
        boolean tor = jsonBoolean(body, "tor");
        boolean hosting = jsonBoolean(body, "hosting");
        boolean suspicious = vpn || proxy || tor || hosting;
        return new VpnProviderResult(true, suspicious, suspicious ? enabledFlags(vpn, proxy, tor, hosting) : "clean");
    }

    private VpnProviderResult queryIpApi(String ip) {
        String body = httpGet("http://ip-api.com/json/" + ip + "?fields=status,message,proxy,hosting,query");
        if (body == null || body.isBlank() || !body.contains("\"status\":\"success\"")) {
            return new VpnProviderResult(false, false, "no-response");
        }
        boolean proxy = jsonBoolean(body, "proxy");
        boolean hosting = jsonBoolean(body, "hosting");
        boolean suspicious = proxy || hosting;
        return new VpnProviderResult(true, suspicious, suspicious ? enabledFlags(false, proxy, false, hosting) : "clean");
    }

    private String httpGet(String url) {
        try {
            int timeoutMs = Math.max(500, getConfig().getInt("adaptive-security.suspicious-ip-extra-captcha.vpn-check.timeout-ms", 2500));
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("User-Agent", "Aethelguard/" + getDescription().getVersion())
                    .GET()
                    .build();
            HttpResponse<String> response = vpnHttpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) return null;
            return response.body();
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean jsonBoolean(String body, String key) {
        return body.contains("\"" + key + "\":true");
    }

    private String enabledFlags(boolean vpn, boolean proxy, boolean tor, boolean hosting) {
        List<String> flags = new ArrayList<>();
        if (vpn) flags.add("vpn");
        if (proxy) flags.add("proxy");
        if (tor) flags.add("tor");
        if (hosting) flags.add("hosting");
        return String.join("+", flags);
    }

    public int countAccountsByIp(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank() || ipAddress.equalsIgnoreCase("UNKNOWN")) return 0;

        if (!getConfig().getBoolean("database.enabled", false)) {
            File[] files = getLocalUsersFolder().listFiles((dir, name) -> name.endsWith(".yml"));
            if (files == null) return 0;

            int count = 0;
            for (File file : files) {
                FileConfiguration config = YamlConfiguration.loadConfiguration(file);
                String registrationIp = config.getString("registration-ip", config.getString("last-ip", ""));
                if (ipAddress.equals(registrationIp)) count++;
            }
            return count;
        }

        try (java.sql.Connection conn = getDatabaseManager().getConnection()) {
            if (conn == null) return 0;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM " + getAuthTableName() + " WHERE COALESCE(registration_ip, last_ip) = ?;"
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

    public List<AccountIpEntry> getAccountsByIp(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank() || ipAddress.equalsIgnoreCase("UNKNOWN")) return List.of();

        if (!getConfig().getBoolean("database.enabled", false)) {
            File[] files = getLocalUsersFolder().listFiles((dir, name) -> name.endsWith(".yml"));
            if (files == null) return List.of();

            List<AccountIpEntry> accounts = new ArrayList<>();
            for (File file : files) {
                FileConfiguration config = YamlConfiguration.loadConfiguration(file);
                String registrationIp = config.getString("registration-ip", config.getString("last-ip", ""));
                String lastIp = config.getString("last-ip", "");
                if (!ipAddress.equals(registrationIp) && !ipAddress.equals(lastIp)) {
                    continue;
                }

                accounts.add(new AccountIpEntry(
                        config.getString("username", file.getName().replace(".yml", "")),
                        config.getString("uuid", file.getName().replace(".yml", "")),
                        stringOrDash(registrationIp),
                        stringOrDash(lastIp),
                        config.getString("created-at", "-"),
                        config.getString("last-login", "-")
                ));
            }
            return accounts;
        }

        try (java.sql.Connection conn = getDatabaseManager().getConnection()) {
            if (conn == null) return List.of();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT uuid, username, registration_ip, last_ip, created_at, last_login " +
                            "FROM " + getAuthTableName() + " WHERE registration_ip = ? OR last_ip = ? ORDER BY username;"
            )) {
                ps.setString(1, ipAddress);
                ps.setString(2, ipAddress);
                try (ResultSet rs = ps.executeQuery()) {
                    List<AccountIpEntry> accounts = new ArrayList<>();
                    while (rs.next()) {
                        accounts.add(new AccountIpEntry(
                                rs.getString("username"),
                                rs.getString("uuid"),
                                stringOrDash(rs.getString("registration_ip")),
                                stringOrDash(rs.getString("last_ip")),
                                stringOrDash(rs.getString("created_at")),
                                stringOrDash(rs.getString("last_login"))
                        ));
                    }
                    return accounts;
                }
            }
        } catch (SQLException ignored) {
        }
        return List.of();
    }

    public void startCaptcha(Player player) {
        if (!ensureVpnCheckReady(player)) {
            sendMessage(player, "messages.vpn-check-wait", true);
            updateAuthBossBar(player);
            return;
        }

        prepareAdaptiveSecurity(player);
        if (shouldBypassCaptchaForTrustedIp(player)) {
            captchaVerifiedPlayers.add(player.getUniqueId());
            extraCaptchaPlayers.remove(player.getUniqueId());
            continueAfterCaptcha(player);
            updateAuthBossBar(player);
            return;
        }

        if (!getConfig().getBoolean("auth-settings.captcha.enabled", true)) {
            captchaVerifiedPlayers.add(player.getUniqueId());
            continueAfterCaptcha(player);
            updateAuthBossBar(player);
            return;
        }

        CaptchaChallenge challenge = createCaptchaChallenge(player);
        captchaChallenges.put(player.getUniqueId(), challenge);
        if (challenge.type().equals("MAP") && getConfig().getBoolean("auth-settings.captcha.map.give-item", true)) {
            giveCaptchaMap(player, challenge.display());
        }
        updateAuthBossBar(player);
        sendCaptchaPrompt(player);
    }

    public void sendCaptchaPrompt(Player player) {
        CaptchaChallenge challenge = captchaChallenges.get(player.getUniqueId());
        if (challenge == null || challenge.isExpired()) {
            startCaptcha(player);
            return;
        }

        sendMessage(player, challenge.type().equals("MAP") ? "messages.captcha-map-prompt" : "messages.captcha-prompt", true, Map.of(
                "type", challenge.type(),
                "challenge", challenge.display()
        ));
    }

    public boolean verifyCaptcha(Player player, String input) {
        CaptchaChallenge challenge = captchaChallenges.get(player.getUniqueId());
        if (challenge == null || challenge.isExpired()) {
            removeCaptchaMaps(player);
            startCaptcha(player);
            sendMessage(player, "messages.captcha-expired", true);
            return false;
        }

        boolean caseSensitive = getConfig().getBoolean("auth-settings.captcha.case-sensitive", false);
        boolean valid = caseSensitive
                ? challenge.answer().equals(input)
                : challenge.answer().equalsIgnoreCase(input);

        if (valid) {
            captchaChallenges.remove(player.getUniqueId());
            captchaVerifiedPlayers.add(player.getUniqueId());
            removeCaptchaMaps(player);
            playConfiguredSound(player, "auth-settings.captcha.success-sound");
            showCaptchaSuccessPopup(player);
            continueAfterCaptcha(player);
            updateAuthBossBar(player);
            return true;
        }

        CaptchaChallenge updated = challenge.failedAttempt();
        captchaChallenges.put(player.getUniqueId(), updated);

        int maxAttempts = Math.max(1, getConfig().getInt("auth-settings.captcha.max-attempts", 5));
        if (updated.attempts() >= maxAttempts) {
            captchaChallenges.remove(player.getUniqueId());
            removeCaptchaMaps(player);
            if (isCaptchaKickEnabled()) {
                restorePreviousLocation(player);
                restoreAuthInventory(player);
                hideAuthBossBar(player);
                player.kickPlayer(getRawStringMessage("messages.captcha-kick", true, Map.of(
                        "max", String.valueOf(maxAttempts)
                )));
            } else {
                startCaptcha(player);
            }
            return false;
        }

        sendMessage(player, "messages.captcha-wrong", true, Map.of(
                "remaining", String.valueOf(maxAttempts - updated.attempts())
        ));
        return false;
    }

    private boolean isCaptchaKickEnabled() {
        if (getConfig().isSet("auth-settings.captcha.kick-enabled")) {
            return getConfig().getBoolean("auth-settings.captcha.kick-enabled", true);
        }
        return getConfig().getBoolean("auth-settings.captcha.kick-on-fail", true);
    }

    public long getCaptchaCooldownRemainingSeconds(Player player) {
        long cooldownSeconds = Math.max(0L, getConfig().getLong("auth-settings.captcha.cooldown-seconds", 2L));
        if (cooldownSeconds <= 0L) return 0L;

        long now = System.currentTimeMillis();
        long nextAllowed = captchaCooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (nextAllowed <= now) return 0L;
        return (long) Math.ceil((nextAllowed - now) / 1000.0);
    }

    public void markCaptchaCooldown(Player player) {
        long cooldownSeconds = Math.max(0L, getConfig().getLong("auth-settings.captcha.cooldown-seconds", 2L));
        if (cooldownSeconds <= 0L) return;
        captchaCooldowns.put(player.getUniqueId(), System.currentTimeMillis() + cooldownSeconds * 1000L);
    }

    private void showCaptchaSuccessPopup(Player player) {
        String subtitlePath = getPostCaptchaMessagePath(player);
        String title = getFormattedMessageString("messages.captcha-success-title", false);
        String subtitle = getFormattedMessageString(subtitlePath, false);
        player.showTitle(Title.title(
                miniMessage.deserialize(title),
                miniMessage.deserialize(subtitle),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(700))
        ));
    }

    private String getPostCaptchaMessagePath(Player player) {
        if (shouldSkipPasswordLoginForTwoFactor(player)) {
            return "messages.captcha-two-factor-popup";
        }
        if (isAccountRegistered(player.getUniqueId())) {
            return getAuthMode(player.getUniqueId()).equals("PIN")
                    ? "messages.captcha-pin-popup"
                    : "messages.captcha-login-popup";
        }
        return defaultAuthMode().equals("PIN")
                ? "messages.captcha-setpin-popup"
                : "messages.captcha-register-popup";
    }

    private void continueAfterCaptcha(Player player) {
        if (shouldSkipPasswordLoginForTwoFactor(player)) {
            startTwoFactorLogin(player);
            return;
        }
        openPinGuiIfNeeded(player);
    }

    public boolean shouldSkipPasswordLoginForTwoFactor(Player player) {
        return unauthenticatedPlayers.contains(player.getUniqueId())
                && isAccountRegistered(player.getUniqueId())
                && isTwoFactorEnabled(player)
                && !isWaitingTwoFactor(player);
    }

    public boolean isWaitingTwoFactor(Player player) {
        return pendingTwoFactorPlayers.contains(player.getUniqueId());
    }

    public boolean isTwoFactorEnabled(Player player) {
        if (!getConfig().getBoolean("auth-settings.two-factor.enabled", true)) return false;
        String secret = getTwoFactorSecret(player.getUniqueId());
        return secret != null && !secret.isBlank();
    }

    public void startTwoFactorLogin(Player player) {
        pendingTwoFactorPlayers.add(player.getUniqueId());
        updateAuthBossBar(player);
        sendMessage(player, "messages.two-factor-login-required", true);
    }

    public void completeTwoFactorLogin(Player player, String code) {
        String secret = getTwoFactorSecret(player.getUniqueId());
        if (secret == null || !verifyTotp(secret, code)) {
            sendMessage(player, "messages.two-factor-code-invalid", true);
            return;
        }

        pendingTwoFactorPlayers.remove(player.getUniqueId());
        wrongPasswordAttempts.remove(player.getUniqueId());
        completeLogin(player, true);
        rememberAuthSession(player);
        updateAccountSnapshot(player, true);

        if (getConfig().getBoolean("console-logging.log-auth-success", true)) {
            logInfo(
                    player.getName() + " iki aşamalı doğrulama ile giriş yaptı.",
                    player.getName() + " completed login with two-factor authentication."
            );
        }
    }

    public String createPendingTwoFactorSetup(Player player) {
        String secret = randomBase32(32);
        pendingTwoFactorSetups.put(player.getUniqueId(), secret);
        return secret;
    }

    public void confirmTwoFactorSetup(Player player, String code) {
        String secret = pendingTwoFactorSetups.get(player.getUniqueId());
        if (secret == null) {
            sendMessage(player, "messages.two-factor-setup-missing", true);
            return;
        }

        if (!verifyTotp(secret, code)) {
            sendMessage(player, "messages.two-factor-code-invalid", true);
            return;
        }

        if (setTwoFactorSecret(player.getUniqueId(), secret)) {
            pendingTwoFactorSetups.remove(player.getUniqueId());
            markSecurityCooldown(player, "two-factor-setup");
            sendMessage(player, "messages.two-factor-enabled", true);
        } else {
            sendMessage(player, "messages.two-factor-error", true);
        }
    }

    public void disableTwoFactor(Player player, String code) {
        String secret = getTwoFactorSecret(player.getUniqueId());
        if (secret == null || secret.isBlank()) {
            sendMessage(player, "messages.two-factor-status-disabled", true);
            return;
        }

        if (!verifyTotp(secret, code)) {
            sendMessage(player, "messages.two-factor-code-invalid", true);
            return;
        }

        if (setTwoFactorSecret(player.getUniqueId(), null)) {
            pendingTwoFactorSetups.remove(player.getUniqueId());
            markSecurityCooldown(player, "two-factor-disable");
            sendMessage(player, "messages.two-factor-disabled", true);
        } else {
            sendMessage(player, "messages.two-factor-error", true);
        }
    }

    public SecurityQuestion createPendingSecurityQuestion(Player player) {
        Map<String, String> questions = loadSecurityQuestions();
        if (questions.isEmpty()) {
            SecurityQuestion fallback = new SecurityQuestion("fallback", "What is your favorite color?");
            pendingSecurityQuestions.put(player.getUniqueId(), fallback);
            return fallback;
        }

        List<Map.Entry<String, String>> entries = new ArrayList<>(questions.entrySet());
        Map.Entry<String, String> selected = entries.get(random.nextInt(entries.size()));
        SecurityQuestion question = new SecurityQuestion(selected.getKey(), selected.getValue());
        pendingSecurityQuestions.put(player.getUniqueId(), question);
        return question;
    }

    public boolean confirmSecurityQuestion(Player player, String answer) {
        SecurityQuestion question = pendingSecurityQuestions.get(player.getUniqueId());
        if (question == null) return false;
        boolean saved = setSecurityQuestion(player.getUniqueId(), question, answer);
        if (saved) {
            pendingSecurityQuestions.remove(player.getUniqueId());
        }
        return saved;
    }

    public SecurityQuestion getStoredSecurityQuestion(UUID uuid) {
        if (!getConfig().getBoolean("database.enabled", false)) {
            File userFile = new File(getLocalUsersFolder(), uuid + ".yml");
            if (!userFile.exists()) return null;
            FileConfiguration config = YamlConfiguration.loadConfiguration(userFile);
            String id = config.getString("security-question.id");
            String text = config.getString("security-question.text");
            String hash = config.getString("security-question.answer-hash");
            return id == null || text == null || hash == null ? null : new SecurityQuestion(id, text);
        }

        try (java.sql.Connection conn = getDatabaseManager().getConnection()) {
            if (conn == null) return null;
            try (PreparedStatement ps = conn.prepareStatement("SELECT security_question_id, security_question_text, security_question_hash FROM " + getAuthTableName() + " WHERE uuid = ?;")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next() && rs.getString("security_question_hash") != null) {
                        return new SecurityQuestion(rs.getString("security_question_id"), rs.getString("security_question_text"));
                    }
                }
            }
        } catch (SQLException ignored) {
        }
        return null;
    }

    public boolean verifySecurityQuestionAnswer(UUID uuid, String answer) {
        String hash = getSecurityQuestionHash(uuid);
        return hash != null && BCrypt.checkpw(normalizeRecoveryAnswer(answer), hash);
    }

    private boolean setSecurityQuestion(UUID uuid, SecurityQuestion question, String answer) {
        String hash = BCrypt.hashpw(normalizeRecoveryAnswer(answer), BCrypt.gensalt());
        if (!getConfig().getBoolean("database.enabled", false)) {
            File userFile = new File(getLocalUsersFolder(), uuid + ".yml");
            if (!userFile.exists()) return false;
            FileConfiguration config = YamlConfiguration.loadConfiguration(userFile);
            config.set("security-question.id", question.id());
            config.set("security-question.text", question.text());
            config.set("security-question.answer-hash", hash);
            try {
                config.save(userFile);
                return true;
            } catch (IOException ignored) {
                return false;
            }
        }

        try (java.sql.Connection conn = getDatabaseManager().getConnection()) {
            if (conn == null) return false;
            try (PreparedStatement ps = conn.prepareStatement("UPDATE " + getAuthTableName() + " SET security_question_id = ?, security_question_text = ?, security_question_hash = ? WHERE uuid = ?;")) {
                ps.setString(1, question.id());
                ps.setString(2, question.text());
                ps.setString(3, hash);
                ps.setString(4, uuid.toString());
                return ps.executeUpdate() > 0;
            }
        } catch (SQLException ignored) {
            return false;
        }
    }

    private String getSecurityQuestionHash(UUID uuid) {
        if (!getConfig().getBoolean("database.enabled", false)) {
            File userFile = new File(getLocalUsersFolder(), uuid + ".yml");
            if (!userFile.exists()) return null;
            return YamlConfiguration.loadConfiguration(userFile).getString("security-question.answer-hash");
        }

        try (java.sql.Connection conn = getDatabaseManager().getConnection()) {
            if (conn == null) return null;
            try (PreparedStatement ps = conn.prepareStatement("SELECT security_question_hash FROM " + getAuthTableName() + " WHERE uuid = ?;")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getString("security_question_hash");
                }
            }
        } catch (SQLException ignored) {
        }
        return null;
    }

    private Map<String, String> loadSecurityQuestions() {
        String langCode = getConfig().getString("default-language", "TR").toLowerCase(Locale.ROOT);
        File dir = new File(getDataFolder(), "security_questions");
        File file = new File(dir, "security_questions_" + langCode + ".yml");
        if (!file.exists()) {
            file = new File(dir, "security_questions_en.yml");
        }
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        Map<String, String> questions = new LinkedHashMap<>();
        org.bukkit.configuration.ConfigurationSection section = config.getConfigurationSection("questions");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                questions.put(key, section.getString(key, key));
            }
        }
        return questions;
    }

    private String normalizeRecoveryAnswer(String answer) {
        return answer == null ? "" : answer.trim().toLowerCase(Locale.ROOT);
    }

    public List<String> generateBackupCodes(Player player) {
        List<String> codes = new ArrayList<>();
        List<String> hashes = new ArrayList<>();
        int count = Math.max(1, getConfig().getInt("recovery.backup-codes.count", 6));
        int length = Math.max(6, getConfig().getInt("recovery.backup-codes.length", 10));
        for (int i = 0; i < count; i++) {
            String code = randomString("ABCDEFGHJKLMNPQRSTUVWXYZ23456789", length);
            codes.add(code);
            hashes.add(BCrypt.hashpw(code, BCrypt.gensalt()));
        }
        return setBackupCodeHashes(player.getUniqueId(), hashes) ? codes : List.of();
    }

    public boolean consumeBackupCode(UUID uuid, String code) {
        List<String> hashes = getBackupCodeHashes(uuid);
        for (int i = 0; i < hashes.size(); i++) {
            if (BCrypt.checkpw(code, hashes.get(i))) {
                hashes.remove(i);
                return setBackupCodeHashes(uuid, hashes);
            }
        }
        return false;
    }

    private List<String> getBackupCodeHashes(UUID uuid) {
        String joined = getAccountString(uuid, "backup-codes.hashes", "backup_code_hashes");
        if (joined == null || joined.isBlank()) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(joined.split("\\|")));
    }

    private boolean setBackupCodeHashes(UUID uuid, List<String> hashes) {
        return setAccountString(uuid, "backup-codes.hashes", String.join("|", hashes), "backup_code_hashes");
    }

    public String getRecoveryMethod(UUID uuid) {
        String method = getAccountString(uuid, "recovery.method", "recovery_method");
        return method == null || method.isBlank() ? "question" : method;
    }

    public boolean setRecoveryMethod(UUID uuid, String method) {
        return setAccountString(uuid, "recovery.method", method, "recovery_method");
    }

    public boolean updateAccountPassword(UUID uuid, String hash) {
        boolean updated = setAccountString(uuid, "password", hash, "password");
        if (updated) {
            setPasswordUsable(uuid, true);
        }
        return updated;
    }

    public boolean isPasswordUsable(UUID uuid) {
        String usable = getAccountString(uuid, "password.usable", "password_usable");
        return usable == null || usable.isBlank() || usable.equalsIgnoreCase("true") || usable.equals("1");
    }

    public boolean setPasswordUsable(UUID uuid, boolean usable) {
        return setAccountString(uuid, "password.usable", String.valueOf(usable), "password_usable");
    }

    public String defaultAuthMode() {
        String configured = getConfig().getString("auth-settings.pin.default-auth-mode", "PASSWORD");
        return configured != null && configured.equalsIgnoreCase("PIN") ? "PIN" : "PASSWORD";
    }

    public String getAuthMode(UUID uuid) {
        String mode = getAccountString(uuid, "auth-mode", "auth_mode");
        if (mode == null || mode.isBlank()) {
            mode = "PASSWORD";
        }
        return mode.equalsIgnoreCase("PIN") ? "PIN" : "PASSWORD";
    }

    public boolean setAuthMode(UUID uuid, String mode) {
        String normalized = mode != null && mode.equalsIgnoreCase("PIN") ? "PIN" : "PASSWORD";
        return setAccountString(uuid, "auth-mode", normalized, "auth_mode");
    }

    public String getPinHash(UUID uuid) {
        return getAccountString(uuid, "pin.hash", "pin_hash");
    }

    public boolean updateAccountPin(UUID uuid, String hash) {
        return setAccountString(uuid, "pin.hash", hash, "pin_hash");
    }

    public PinPolicyResult validatePinPolicy(String pin) {
        if (!getConfig().getBoolean("auth-settings.pin.enabled", true)) {
            return PinPolicyResult.error("messages.pin-disabled", Map.of());
        }

        String safePin = pin == null ? "" : pin;
        int minLength = Math.max(1, getConfig().getInt("auth-settings.pin.min-length", 4));
        int maxLength = Math.max(minLength, getConfig().getInt("auth-settings.pin.max-length", 8));

        if (safePin.length() < minLength) {
            return PinPolicyResult.error("messages.pin-too-short", Map.of("min", String.valueOf(minLength)));
        }
        if (safePin.length() > maxLength) {
            return PinPolicyResult.error("messages.pin-too-long", Map.of("max", String.valueOf(maxLength)));
        }
        if (getConfig().getBoolean("auth-settings.pin.numeric-only", true) && !safePin.matches("\\d+")) {
            return PinPolicyResult.error("messages.pin-numeric-only", Map.of());
        }
        if (!getConfig().getBoolean("auth-settings.pin.allow-repeated-digits", false) && isRepeatedPin(safePin)) {
            return PinPolicyResult.error("messages.pin-repeated-blocked", Map.of());
        }
        if (!getConfig().getBoolean("auth-settings.pin.allow-sequential-digits", false) && isSequentialPin(safePin)) {
            return PinPolicyResult.error("messages.pin-sequential-blocked", Map.of());
        }
        if (getConfig().getBoolean("auth-settings.pin.block-common-pins", true)
                && getConfig().getStringList("auth-settings.pin.common-pins").contains(safePin)) {
            return PinPolicyResult.error("messages.pin-common-blocked", Map.of());
        }

        return PinPolicyResult.ok();
    }

    private boolean isRepeatedPin(String pin) {
        if (pin.isEmpty()) return false;
        for (int i = 1; i < pin.length(); i++) {
            if (pin.charAt(i) != pin.charAt(0)) return false;
        }
        return true;
    }

    private boolean isSequentialPin(String pin) {
        if (!pin.matches("\\d+") || pin.length() < 3) return false;
        boolean ascending = true;
        boolean descending = true;
        for (int i = 1; i < pin.length(); i++) {
            int previous = pin.charAt(i - 1) - '0';
            int current = pin.charAt(i) - '0';
            ascending &= current == previous + 1;
            descending &= current == previous - 1;
        }
        return ascending || descending;
    }

    public long getSecurityCooldownRemainingMillis(Player player, String key) {
        if (!getConfig().getBoolean("security-cooldowns.enabled", true)) return 0L;
        long hours = getConfig().getLong("security-cooldowns.commands." + key + "-hours", 0L);
        if (hours <= 0L) return 0L;
        return getCooldownRemainingMillis(player.getUniqueId(), key, hours);
    }

    public void markSecurityCooldown(Player player, String key) {
        if (!getConfig().getBoolean("security-cooldowns.enabled", true)) return;
        setCooldownTimestamp(player.getUniqueId(), key, System.currentTimeMillis());
    }

    public boolean isCustomCommandOnCooldown(Player player, String commandLine) {
        if (!getConfig().getBoolean("security-cooldowns.enabled", true)) return false;
        if (!getConfig().getBoolean("security-cooldowns.custom.enabled", true)) return false;
        String lower = commandLine.toLowerCase(Locale.ROOT);
        for (String command : getConfig().getStringList("security-cooldowns.custom.commands")) {
            String normalized = command.toLowerCase(Locale.ROOT);
            if (lower.equals(normalized) || lower.startsWith(normalized + " ")) {
                return getCooldownRemainingMillis(player.getUniqueId(), "custom-" + normalized.replace("/", ""), getConfig().getLong("security-cooldowns.custom.hours", 24L)) > 0L;
            }
        }
        return false;
    }

    public long getCustomCommandCooldownRemainingMillis(Player player, String commandLine) {
        String lower = commandLine.toLowerCase(Locale.ROOT);
        for (String command : getConfig().getStringList("security-cooldowns.custom.commands")) {
            String normalized = command.toLowerCase(Locale.ROOT);
            if (lower.equals(normalized) || lower.startsWith(normalized + " ")) {
                return getCooldownRemainingMillis(player.getUniqueId(), "custom-" + normalized.replace("/", ""), getConfig().getLong("security-cooldowns.custom.hours", 24L));
            }
        }
        return 0L;
    }

    public void markCustomCommandCooldown(Player player, String commandLine) {
        if (!getConfig().getBoolean("security-cooldowns.custom.enabled", true)) return;
        String lower = commandLine.toLowerCase(Locale.ROOT);
        for (String command : getConfig().getStringList("security-cooldowns.custom.commands")) {
            String normalized = command.toLowerCase(Locale.ROOT);
            if (lower.equals(normalized) || lower.startsWith(normalized + " ")) {
                setCooldownTimestamp(player.getUniqueId(), "custom-" + normalized.replace("/", ""), System.currentTimeMillis());
                return;
            }
        }
    }

    public String formatDuration(long millis) {
        long seconds = Math.max(1L, (long) Math.ceil(millis / 1000.0));
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long remainingSeconds = seconds % 60;
        if (hours > 0) return hours + "h " + minutes + "m";
        if (minutes > 0) return minutes + "m " + remainingSeconds + "s";
        return remainingSeconds + "s";
    }

    private long getCooldownRemainingMillis(UUID uuid, String key, long hours) {
        long lastUsed = getCooldownTimestamp(uuid, key);
        if (lastUsed <= 0L) return 0L;
        long expiresAt = lastUsed + hours * 60L * 60L * 1000L;
        return Math.max(0L, expiresAt - System.currentTimeMillis());
    }

    private long getCooldownTimestamp(UUID uuid, String key) {
        String raw = getAccountString(uuid, "security.cooldowns." + key, "security_cooldowns");
        if (raw == null) return 0L;
        if (getConfig().getBoolean("database.enabled", false)) {
            for (String part : raw.split(";")) {
                String[] pieces = part.split("=", 2);
                if (pieces.length == 2 && pieces[0].equals(key)) {
                    try {
                        return Long.parseLong(pieces[1]);
                    } catch (NumberFormatException ignored) {
                        return 0L;
                    }
                }
            }
            return 0L;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private void setCooldownTimestamp(UUID uuid, String key, long timestamp) {
        if (!getConfig().getBoolean("database.enabled", false)) {
            setAccountString(uuid, "security.cooldowns." + key, String.valueOf(timestamp), "security_cooldowns");
            return;
        }

        String raw = getAccountString(uuid, "security.cooldowns." + key, "security_cooldowns");
        Map<String, String> values = new LinkedHashMap<>();
        if (raw != null && !raw.isBlank()) {
            for (String part : raw.split(";")) {
                String[] pieces = part.split("=", 2);
                if (pieces.length == 2) values.put(pieces[0], pieces[1]);
            }
        }
        values.put(key, String.valueOf(timestamp));
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            parts.add(entry.getKey() + "=" + entry.getValue());
        }
        setAccountString(uuid, "security.cooldowns." + key, String.join(";", parts), "security_cooldowns");
    }

    private String getAccountString(UUID uuid, String localPath, String dbColumn) {
        if (!getConfig().getBoolean("database.enabled", false)) {
            File userFile = new File(getLocalUsersFolder(), uuid + ".yml");
            if (!userFile.exists()) return null;
            return YamlConfiguration.loadConfiguration(userFile).getString(localPath);
        }

        try (java.sql.Connection conn = getDatabaseManager().getConnection()) {
            if (conn == null) return null;
            try (PreparedStatement ps = conn.prepareStatement("SELECT " + dbColumn + " FROM " + getAuthTableName() + " WHERE uuid = ?;")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getString(dbColumn);
                }
            }
        } catch (SQLException ignored) {
        }
        return null;
    }

    private boolean setAccountString(UUID uuid, String localPath, String value, String dbColumn) {
        if (!getConfig().getBoolean("database.enabled", false)) {
            File userFile = new File(getLocalUsersFolder(), uuid + ".yml");
            if (!userFile.exists()) return false;
            FileConfiguration config = YamlConfiguration.loadConfiguration(userFile);
            config.set(localPath, value);
            try {
                config.save(userFile);
                return true;
            } catch (IOException ignored) {
                return false;
            }
        }

        try (java.sql.Connection conn = getDatabaseManager().getConnection()) {
            if (conn == null) return false;
            try (PreparedStatement ps = conn.prepareStatement("UPDATE " + getAuthTableName() + " SET " + dbColumn + " = ? WHERE uuid = ?;")) {
                ps.setString(1, value);
                ps.setString(2, uuid.toString());
                return ps.executeUpdate() > 0;
            }
        } catch (SQLException ignored) {
            return false;
        }
    }

    private String getTwoFactorSecret(UUID uuid) {
        if (!getConfig().getBoolean("database.enabled", false)) {
            File userFile = new File(getLocalUsersFolder(), uuid + ".yml");
            if (!userFile.exists()) return null;
            FileConfiguration config = YamlConfiguration.loadConfiguration(userFile);
            return config.getBoolean("two-factor.enabled", false) ? config.getString("two-factor.secret") : null;
        }

        try (java.sql.Connection conn = getDatabaseManager().getConnection()) {
            if (conn == null) return null;
            try (PreparedStatement ps = conn.prepareStatement("SELECT auth_type, totp_secret FROM " + getAuthTableName() + " WHERE uuid = ?;")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next() && "TOTP".equalsIgnoreCase(rs.getString("auth_type"))) {
                        return rs.getString("totp_secret");
                    }
                }
            }
        } catch (SQLException ignored) {
        }
        return null;
    }

    private boolean setTwoFactorSecret(UUID uuid, String secret) {
        if (!getConfig().getBoolean("database.enabled", false)) {
            File userFile = new File(getLocalUsersFolder(), uuid + ".yml");
            if (!userFile.exists()) return false;
            FileConfiguration config = YamlConfiguration.loadConfiguration(userFile);
            config.set("two-factor.enabled", secret != null);
            config.set("two-factor.secret", secret);
            try {
                config.save(userFile);
                return true;
            } catch (IOException ignored) {
                return false;
            }
        }

        try (java.sql.Connection conn = getDatabaseManager().getConnection()) {
            if (conn == null) return false;
            try (PreparedStatement ps = conn.prepareStatement("UPDATE " + getAuthTableName() + " SET auth_type = ?, totp_secret = ? WHERE uuid = ?;")) {
                ps.setString(1, secret == null ? "TEXT" : "TOTP");
                ps.setString(2, secret);
                ps.setString(3, uuid.toString());
                return ps.executeUpdate() > 0;
            }
        } catch (SQLException ignored) {
            return false;
        }
    }

    private boolean verifyTotp(String secret, String code) {
        if (code == null || !code.matches("\\d{6}")) return false;
        long currentStep = System.currentTimeMillis() / 1000L / 30L;
        int window = Math.max(0, getConfig().getInt("auth-settings.two-factor.code-window", 1));
        for (long step = currentStep - window; step <= currentStep + window; step++) {
            if (generateTotp(secret, step).equals(code)) return true;
        }
        return false;
    }

    private String generateTotp(String secret, long timeStep) {
        try {
            byte[] key = decodeBase32(secret);
            byte[] data = new byte[8];
            long value = timeStep;
            for (int i = 7; i >= 0; i--) {
                data[i] = (byte) (value & 0xFF);
                value >>= 8;
            }

            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(data);
            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);
            return String.format(Locale.US, "%06d", binary % 1_000_000);
        } catch (Exception ignored) {
            return "000000";
        }
    }

    private String randomBase32(int length) {
        return randomString("ABCDEFGHIJKLMNOPQRSTUVWXYZ234567", length);
    }

    private byte[] decodeBase32(String value) {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int buffer = 0;
        int bitsLeft = 0;
        for (char raw : value.replace("=", "").toUpperCase(Locale.ROOT).toCharArray()) {
            int val = alphabet.indexOf(raw);
            if (val < 0) continue;
            buffer = (buffer << 5) | val;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                output.write((buffer >> (bitsLeft - 8)) & 0xFF);
                bitsLeft -= 8;
            }
        }
        return output.toByteArray();
    }

    public void clearCaptchaState(UUID uuid) {
        captchaChallenges.remove(uuid);
        captchaVerifiedPlayers.remove(uuid);
        captchaCooldowns.remove(uuid);
        extraCaptchaPlayers.remove(uuid);
    }

    public void clearTwoFactorState(UUID uuid) {
        pendingTwoFactorPlayers.remove(uuid);
        pendingTwoFactorSetups.remove(uuid);
    }

    private CaptchaChallenge createCaptchaChallenge(Player player) {
        List<String> types = getCaptchaTypesForPlayer(player);
        if (types.isEmpty()) {
            types = List.of("MAP");
        }

        String type = types.get(random.nextInt(types.size())).toUpperCase(Locale.ROOT);
        int length = Math.max(3, getConfig().getInt("auth-settings.captcha.length", 5));
        long timeoutMs = Math.max(10L, getConfig().getLong("auth-settings.captcha.timeout-seconds", 60L)) * 1000L;
        long expiresAt = System.currentTimeMillis() + timeoutMs;

        return switch (type) {
            case "NUMERIC" -> {
                String code = randomString("0123456789", length);
                yield new CaptchaChallenge("NUMERIC", code, code, 0, expiresAt);
            }
            case "ALPHANUMERIC" -> {
                String code = randomString("ABCDEFGHJKLMNPQRSTUVWXYZ23456789", length);
                yield new CaptchaChallenge("ALPHANUMERIC", code, code, 0, expiresAt);
            }
            case "MATH" -> {
                int left = random.nextInt(9) + 1;
                int right = random.nextInt(9) + 1;
                boolean plus = random.nextBoolean();
                String question = left + (plus ? " + " : " - ") + right;
                String answer = String.valueOf(plus ? left + right : left - right);
                yield new CaptchaChallenge("MATH", question, answer, 0, expiresAt);
            }
            case "MAP" -> {
                String code = randomString("ABCDEFGHJKLMNPQRSTUVWXYZ23456789", length);
                yield new CaptchaChallenge("MAP", code, code, 0, expiresAt);
            }
            default -> {
                String code = randomString("ABCDEFGHJKLMNPQRSTUVWXYZ", length);
                yield new CaptchaChallenge("TEXT", code, code, 0, expiresAt);
            }
        };
    }

    private List<String> getCaptchaTypesForPlayer(Player player) {
        if (!extraCaptchaPlayers.getOrDefault(player.getUniqueId(), false)) {
            return getConfig().getStringList("auth-settings.captcha.types");
        }

        String mode = getConfig().getString("adaptive-security.suspicious-ip-extra-captcha.captcha-mode", "RANDOM_EXCEPT_NORMAL").toUpperCase(Locale.ROOT);
        if (mode.equals("FIXED")) {
            return List.of(getConfig().getString("adaptive-security.suspicious-ip-extra-captcha.fixed-type", "MATH"));
        }

        List<String> extraTypes = new ArrayList<>(getConfig().getStringList("adaptive-security.suspicious-ip-extra-captcha.types"));
        if (extraTypes.isEmpty()) {
            extraTypes = new ArrayList<>(List.of("TEXT", "NUMERIC", "ALPHANUMERIC", "MATH"));
        }

        if (mode.equals("RANDOM_EXCEPT_NORMAL")) {
            Set<String> normalTypes = new HashSet<>();
            for (String type : getConfig().getStringList("auth-settings.captcha.types")) {
                normalTypes.add(type.toUpperCase(Locale.ROOT));
            }
            List<String> filtered = new ArrayList<>();
            for (String type : extraTypes) {
                if (!normalTypes.contains(type.toUpperCase(Locale.ROOT))) {
                    filtered.add(type);
                }
            }
            if (!filtered.isEmpty()) {
                return filtered;
            }
        }

        return extraTypes;
    }

    private String randomString(String alphabet, int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return builder.toString();
    }

    private void giveCaptchaMap(Player player, String code) {
        removeCaptchaMaps(player);

        MapView mapView = getServer().createMap(player.getWorld());
        for (MapRenderer renderer : mapView.getRenderers()) {
            mapView.removeRenderer(renderer);
        }
        mapView.addRenderer(new CaptchaMapRenderer(code));

        ItemStack mapItem = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta) mapItem.getItemMeta();
        if (meta != null) {
            meta.setMapView(mapView);
            meta.displayName(miniMessage.deserialize(getConfig().getString("auth-settings.captcha.map.item-name", "<yellow>Captcha")));
            meta.getPersistentDataContainer().set(captchaMapKey(), PersistentDataType.BYTE, (byte) 1);
            mapItem.setItemMeta(meta);
        }
        player.getInventory().addItem(mapItem);
    }

    private void removeCaptchaMaps(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        boolean changed = false;

        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (isCaptchaMap(item)) {
                contents[i] = null;
                changed = true;
            }
        }

        if (changed) {
            player.getInventory().setContents(contents);
        }
    }

    private boolean isCaptchaMap(ItemStack item) {
        if (item == null || item.getType() != Material.FILLED_MAP || !(item.getItemMeta() instanceof MapMeta meta)) {
            return false;
        }
        Byte marker = meta.getPersistentDataContainer().get(captchaMapKey(), PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1;
    }

    private NamespacedKey captchaMapKey() {
        return new NamespacedKey(this, "captcha_map");
    }

    public void rememberAuthSession(Player player) {
        if (!getConfig().getBoolean("auth-settings.sessions.enabled", true)) return;

        String ipAddress = player.getAddress() == null
                ? "UNKNOWN"
                : player.getAddress().getAddress().getHostAddress();
        long durationMinutes = Math.max(1L, getConfig().getLong("auth-settings.sessions.duration-minutes", 10L));
        long expiresAt = System.currentTimeMillis() + (durationMinutes * 60_000L);
        authSessions.put(player.getUniqueId(), new AuthSession(ipAddress, expiresAt));
    }

    public Map<UUID, String[]> getAuthSessionSummaries() {
        removeExpiredAuthSessions();

        Map<UUID, String[]> summaries = new LinkedHashMap<>();
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        for (Map.Entry<UUID, AuthSession> entry : authSessions.entrySet()) {
            AuthSession session = entry.getValue();
            summaries.put(entry.getKey(), new String[]{
                    session.ipAddress(),
                    format.format(new Date(session.expiresAt()))
            });
        }
        return summaries;
    }

    public boolean clearAuthSession(UUID uuid) {
        return authSessions.remove(uuid) != null;
    }

    public int clearAllAuthSessions() {
        int count = authSessions.size();
        authSessions.clear();
        return count;
    }

    private void removeExpiredAuthSessions() {
        long now = System.currentTimeMillis();
        authSessions.entrySet().removeIf(entry -> entry.getValue().expiresAt() < now);
    }

    public boolean consumeValidAuthSession(Player player) {
        if (!getConfig().getBoolean("auth-settings.sessions.enabled", true)) return false;

        AuthSession session = authSessions.get(player.getUniqueId());
        if (session == null) return false;

        if (session.expiresAt() < System.currentTimeMillis()) {
            authSessions.remove(player.getUniqueId());
            return false;
        }

        if (getConfig().getBoolean("auth-settings.sessions.match-ip", true)) {
            String currentIp = player.getAddress() == null
                    ? "UNKNOWN"
                    : player.getAddress().getAddress().getHostAddress();
            if (!session.ipAddress().equals(currentIp)) {
                authSessions.remove(player.getUniqueId());
                return false;
            }
        }

        loggedInPlayers.add(player.getUniqueId());
        unauthenticatedPlayers.remove(player.getUniqueId());
        wrongPasswordAttempts.remove(player.getUniqueId());
        clearAuthEffects(player);
        restorePreviousLocation(player);
        previousLocations.remove(player.getUniqueId());
        restoreAuthInventory(player);
        hideAuthBossBar(player);
        recordAdaptiveSuccessfulAuth(player);
        updateAccountSnapshot(player, true);
        sendMessage(player, "messages.session-login-success", true);

        if (getConfig().getBoolean("console-logging.log-auth-success", true)) {
            logInfo(
                    player.getName() + " aktif oturum ile otomatik giriş yaptı.",
                    player.getName() + " logged in automatically with an active session."
            );
        }
        return true;
    }

    public void forceUnlogin(Player player) {
        loggedInPlayers.remove(player.getUniqueId());
        unauthenticatedPlayers.add(player.getUniqueId());
        wrongPasswordAttempts.remove(player.getUniqueId());
        authSessions.remove(player.getUniqueId());
        rememberPreviousLocation(player);
        hideInventoryForAuth(player);

        if (getConfig().getBoolean("auth-settings.teleport-to-void", true)) {
            player.teleport(getVoidLocation(player));
            sendMessage(player, "messages.secure-void-zone", true);
        }

        applyAuthEffects(player);
        startCaptcha(player);
    }

    private record AuthInventorySnapshot(ItemStack[] storage, ItemStack[] armor, ItemStack[] extra) {
    }

    public record SecurityQuestion(String id, String text) {
    }

    private record CaptchaChallenge(String type, String display, String answer, int attempts, long expiresAt) {
        private boolean isExpired() {
            return expiresAt < System.currentTimeMillis();
        }

        private CaptchaChallenge failedAttempt() {
            return new CaptchaChallenge(type, display, answer, attempts + 1, expiresAt);
        }
    }

    private static class CaptchaMapRenderer extends MapRenderer {
        private final String code;
        private boolean rendered;

        private CaptchaMapRenderer(String code) {
            this.code = code;
        }

        @Override
        public void render(MapView map, MapCanvas canvas, Player player) {
            if (rendered) return;

            BufferedImage image = new BufferedImage(128, 128, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = image.createGraphics();
            Random seededRandom = new Random(code.hashCode() * 31L + 17L);

            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(new Color(238, 241, 228));
            graphics.fillRect(0, 0, 128, 128);

            for (int i = 0; i < 250; i++) {
                graphics.setColor(new Color(
                        120 + seededRandom.nextInt(120),
                        120 + seededRandom.nextInt(120),
                        120 + seededRandom.nextInt(120),
                        100 + seededRandom.nextInt(120)
                ));
                int x = seededRandom.nextInt(128);
                int y = seededRandom.nextInt(128);
                graphics.fillRect(x, y, 1 + seededRandom.nextInt(2), 1 + seededRandom.nextInt(2));
            }

            for (int i = 0; i < 11; i++) {
                graphics.setColor(new Color(
                        30 + seededRandom.nextInt(180),
                        30 + seededRandom.nextInt(180),
                        30 + seededRandom.nextInt(180)
                ));
                graphics.drawLine(
                        seededRandom.nextInt(128),
                        seededRandom.nextInt(128),
                        seededRandom.nextInt(128),
                        seededRandom.nextInt(128)
                );
            }

            graphics.setFont(new Font("Serif", Font.BOLD | Font.ITALIC, 30));
            FontMetrics metrics = graphics.getFontMetrics();
            int totalWidth = metrics.stringWidth(code);
            int startX = Math.max(8, (128 - totalWidth) / 2);

            for (int i = 0; i < code.length(); i++) {
                char character = code.charAt(i);
                int charX = startX + i * Math.max(18, totalWidth / Math.max(1, code.length()));
                int charY = 72 + seededRandom.nextInt(14) - 7;
                double angle = Math.toRadians(seededRandom.nextInt(35) - 17);

                graphics.rotate(angle, charX, charY);
                graphics.setColor(new Color(
                        20 + seededRandom.nextInt(120),
                        20 + seededRandom.nextInt(120),
                        20 + seededRandom.nextInt(120)
                ));
                graphics.drawString(String.valueOf(character), charX, charY);
                graphics.rotate(-angle, charX, charY);
            }

            graphics.dispose();

            canvas.drawImage(0, 0, image);
            rendered = true;
        }
    }

    private record AuthSession(String ipAddress, long expiresAt) {
    }

    public record AccountIpEntry(String username, String uuid, String registrationIp, String lastIp, String createdAt, String lastLogin) {
    }

    private record VpnCheckResult(boolean suspicious, long checkedAt, String reason) {
    }

    private record VpnProviderResult(boolean success, boolean suspicious, String reason) {
    }

    public record PasswordPolicyResult(boolean valid, String messagePath, Map<String, String> placeholders) {
        private static PasswordPolicyResult ok() {
            return new PasswordPolicyResult(true, "", Map.of());
        }

        private static PasswordPolicyResult error(String messagePath, Map<String, String> placeholders) {
            return new PasswordPolicyResult(false, messagePath, placeholders);
        }
    }

    public record PinPolicyResult(boolean valid, String messagePath, Map<String, String> placeholders) {
        private static PinPolicyResult ok() {
            return new PinPolicyResult(true, "", Map.of());
        }

        private static PinPolicyResult error(String messagePath, Map<String, String> placeholders) {
            return new PinPolicyResult(false, messagePath, placeholders);
        }
    }

    public MiniMessage getMiniMessage() { return miniMessage; }
    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public Set<UUID> getUnauthenticatedPlayers() { return unauthenticatedPlayers; }
    public Map<UUID, Location> getPreviousLocations() { return previousLocations; }
    public Map<UUID, Integer> getWrongPasswordAttempts() { return wrongPasswordAttempts; }
    public Map<UUID, Integer> getWrongPinAttempts() { return wrongPinAttempts; }

    public void markAuthTimeout(Player player, long timeoutTicks) {
        long timeoutMillis = Math.max(1L, timeoutTicks) * 50L;
        authTimeoutDeadlines.put(player.getUniqueId(), System.currentTimeMillis() + timeoutMillis);
    }

    public long getAuthTimeoutRemainingSeconds(Player player) {
        Long deadline = authTimeoutDeadlines.get(player.getUniqueId());
        if (deadline == null) {
            long configuredTicks = getConfig().getLong("auth-settings.timeout.ticks", 1200L);
            return Math.max(0L, configuredTicks / 20L);
        }
        return Math.max(0L, (long) Math.ceil((deadline - System.currentTimeMillis()) / 1000.0));
    }

    public void clearAuthTimeout(UUID uuid) {
        authTimeoutDeadlines.remove(uuid);
    }

    public boolean openPinGuiIfNeeded(Player player) {
        return pinGui != null && pinGui.openForCurrentState(player);
    }

    public void closePinGui(Player player) {
        if (pinGui != null) {
            pinGui.closeForSuccess(player);
        }
    }

    public boolean showPinGuiError(Player player) {
        return pinGui != null && pinGui.showError(player);
    }

    public void openPinGuiPreview(Player player, String theme) {
        if (pinGui != null) {
            pinGui.openPreview(player, theme);
        }
    }

    public List<String> getPinGuiThemes() {
        return List.of("forest-green", "quartz", "pumpkin", "netherite", "monitor-green", "monitor-red");
    }
}
