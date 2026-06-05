package me.aethelster.aethelguard;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.scheduler.BukkitTask;

import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PinGui implements Listener {
    private static final int SIZE = 45;
    private static final int[] DISPLAY_SLOTS = {13, 14, 15, 16};
    private static final int ATTEMPT_LABEL_SLOT = 36;
    private static final int ATTEMPT_COLON_SLOT = 37;
    private static final int ATTEMPT_VALUE_START_SLOT = 38;
    private static final int TIME_VALUE_END_SLOT = 44;
    private static final Map<Integer, Integer> DIGIT_SLOTS = Map.of(
            0, 28,
            1, 0,
            2, 1,
            3, 2,
            4, 9,
            5, 10,
            6, 11,
            7, 18,
            8, 19,
            9, 20
    );
    private static final int CONFIRM_SLOT = 22;
    private static final int BACKSPACE_SLOT = 23;
    private static final int CLEAR_SLOT = 24;
    private static final int EXIT_SLOT = 25;
    private static final Pattern TEXTURE_URL_PATTERN = Pattern.compile("https?://textures\\.minecraft\\.net/texture/[A-Za-z0-9]+");

    private final Aethelguard plugin;
    private final PinCommand pinCommand;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Map<UUID, BukkitTask> updateTasks = new HashMap<>();
    private final Map<UUID, Integer> closeAttempts = new HashMap<>();
    private final Set<UUID> intentionalClose = new java.util.HashSet<>();

    public PinGui(Aethelguard plugin, PinCommand pinCommand) {
        this.plugin = plugin;
        this.pinCommand = pinCommand;
    }

    public boolean openForCurrentState(Player player) {
        if (!plugin.getConfig().getBoolean("auth-settings.pin.gui.enabled", false)) return false;
        if (!plugin.getConfig().getBoolean("auth-settings.pin.enabled", false)) return false;
        if (plugin.isAuthenticated(player)) return false;
        if (plugin.isCaptchaRequired(player)) return false;
        if (plugin.isWaitingTwoFactor(player) || plugin.shouldSkipPasswordLoginForTwoFactor(player)) return false;

        UUID uuid = player.getUniqueId();
        if (plugin.isAccountRegistered(uuid)) {
            if (!plugin.getAuthMode(uuid).equals("PIN")) return false;
            open(player, Mode.LOGIN, null);
            return true;
        }

        if (!plugin.defaultAuthMode().equals("PIN")) return false;
        open(player, Mode.SETUP, null);
        return true;
    }

    public boolean isOpen(Player player) {
        UUID uuid = player.getUniqueId();
        return sessions.containsKey(uuid)
                && player.getOpenInventory().getTopInventory().getHolder() instanceof PinGuiHolder holder
                && holder.uuid().equals(uuid);
    }

    public void openPreview(Player player, String themeName) {
        open(player, Mode.PREVIEW, themeName);
    }

    public boolean showError(Player player) {
        UUID uuid = player.getUniqueId();
        Session session = sessions.get(uuid);
        if (session == null) return false;

        session.fail();
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof PinGuiHolder holder
                && holder.uuid().equals(uuid)) {
            render(player.getOpenInventory().getTopInventory(), session, player);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Session current = sessions.get(uuid);
            if (current == null) return;
            current.clearError();
            current.clear();
            current.setSubmitting(false);
            if (player.isOnline()
                    && player.getOpenInventory().getTopInventory().getHolder() instanceof PinGuiHolder holder
                    && holder.uuid().equals(uuid)) {
                render(player.getOpenInventory().getTopInventory(), current, player);
            }
        }, 100L);
        return true;
    }

    public void closeForSuccess(Player player) {
        intentionalClose.add(player.getUniqueId());
        closeSession(player.getUniqueId());
        closeAttempts.remove(player.getUniqueId());
        player.closeInventory();
    }

    private void open(Player player, Mode mode, String themeOverride) {
        Session session = new Session(mode, themeOverride, createDigitSlots());
        sessions.put(player.getUniqueId(), session);
        Inventory inventory = Bukkit.createInventory(new PinGuiHolder(player.getUniqueId()), SIZE,
                plugin.getRawStringMessage(titlePath(mode), false));
        render(inventory, session, player);
        player.openInventory(inventory);
        startUpdater(player);
        plugin.updateAuthBossBar(player);
        plugin.playConfiguredSound(player, "auth-settings.sounds.pin-gui-open");
    }

    private String titlePath(Mode mode) {
        return switch (mode) {
            case LOGIN -> "messages.pin-gui-title-login";
            case SETUP -> "messages.pin-gui-title-setup";
            case PREVIEW -> "messages.pin-gui-title-preview";
        };
    }

    private void render(Inventory inventory, Session session, Player player) {
        Theme theme = Theme.resolve(plugin, session.themeOverride());
        ItemStack filler = session.hasError()
                ? namedItem(Material.RED_STAINED_GLASS_PANE, "messages.pin-gui-error-digit")
                : namedItem(Material.BLACK_STAINED_GLASS_PANE, "messages.pin-gui-filler");
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, filler);
        }

        for (Map.Entry<Integer, Integer> entry : session.digitSlots().entrySet()) {
            inventory.setItem(entry.getValue(), headItem(theme.digits().get(entry.getKey()), "messages.pin-gui-digit", Map.of("digit", String.valueOf(entry.getKey()))));
        }

        int maxLength = DISPLAY_SLOTS.length;
        for (int i = 0; i < DISPLAY_SLOTS.length; i++) {
            boolean filled = i < session.input().length();
            if (session.hasError()) {
                inventory.setItem(DISPLAY_SLOTS[i], namedItem(Material.RED_STAINED_GLASS_PANE, "messages.pin-gui-error-digit"));
            } else if (!filled) {
                inventory.setItem(DISPLAY_SLOTS[i], namedItem(Material.GRAY_STAINED_GLASS_PANE, "messages.pin-gui-empty-digit"));
            } else if (plugin.getConfig().getBoolean("auth-settings.pin.gui.hide-input", true)) {
                inventory.setItem(DISPLAY_SLOTS[i], headItem(theme.secret(), "messages.pin-gui-filled-digit", Map.of()));
            } else {
                int digit = Character.digit(session.input().charAt(i), 10);
                inventory.setItem(DISPLAY_SLOTS[i], headItem(theme.digits().get(digit), "messages.pin-gui-visible-digit", Map.of("digit", String.valueOf(digit))));
            }
        }

        boolean confirmActive = session.input().length() >= minPinLength() && !session.isSubmitting() && !session.hasError();
        if (confirmActive) {
            inventory.setItem(CONFIRM_SLOT, headItem(theme.confirm(), "messages.pin-gui-confirm", Map.of()));
        } else if (session.hasError()) {
            inventory.setItem(CONFIRM_SLOT, namedItem(Material.RED_STAINED_GLASS_PANE, "messages.pin-gui-confirm-error"));
        } else {
            inventory.setItem(CONFIRM_SLOT, namedItem(Material.GRAY_DYE, "messages.pin-gui-confirm-disabled"));
        }
        inventory.setItem(BACKSPACE_SLOT, headItem(theme.backspace(), "messages.pin-gui-backspace", Map.of()));
        inventory.setItem(CLEAR_SLOT, headItem(theme.clear(), "messages.pin-gui-clear", Map.of()));
        inventory.setItem(EXIT_SLOT, headItem(theme.exit(), "messages.pin-gui-exit", Map.of()));
        renderStatusRow(inventory, theme, session, player);

        if (session.input().length() >= maxLength) {
            session.trim(maxLength);
        }
    }

    private int minPinLength() {
        return DISPLAY_SLOTS.length;
    }

    private Map<Integer, Integer> createDigitSlots() {
        Map<Integer, Integer> slots = new HashMap<>(DIGIT_SLOTS);
        if (!plugin.getConfig().getBoolean("auth-settings.pin.gui.randomize-numbers", false)) {
            return slots;
        }

        List<Integer> slotValues = new ArrayList<>(DIGIT_SLOTS.values());
        Collections.shuffle(slotValues);
        Map<Integer, Integer> randomized = new HashMap<>();
        for (int digit = 0; digit <= 9; digit++) {
            randomized.put(digit, slotValues.get(digit));
        }
        return randomized;
    }

    private void renderStatusRow(Inventory inventory, Theme theme, Session session, Player player) {
        inventory.setItem(ATTEMPT_LABEL_SLOT, headItem(theme.attemptLabel(), "messages.pin-gui-attempt-label", Map.of()));
        inventory.setItem(ATTEMPT_COLON_SLOT, headItem(theme.colon(), "messages.pin-gui-colon", Map.of()));

        int maxAttempts = Math.min(99, Math.max(1, plugin.getConfig().getInt("auth-settings.pin.wrong-pin.max-attempts", 3)));
        int usedAttempts = session.mode() == Mode.LOGIN
                ? plugin.getWrongPinAttempts().getOrDefault(player.getUniqueId(), 0)
                : 0;
        int attempts = Math.max(0, Math.min(99, maxAttempts - usedAttempts));
        renderLeftNumber(inventory, theme, attempts, ATTEMPT_VALUE_START_SLOT, 2, "messages.pin-gui-attempt-value");

        int seconds = (int) Math.max(0, Math.min(999, plugin.getAuthTimeoutRemainingSeconds(player)));
        renderRightNumber(inventory, theme, seconds, TIME_VALUE_END_SLOT, 3, "messages.pin-gui-time-value");
    }

    private void renderLeftNumber(Inventory inventory, Theme theme, int value, int startSlot, int maxDigits, String messagePath) {
        String text = String.valueOf(Math.max(0, Math.min(value, maxDigits == 2 ? 99 : 999)));
        for (int i = 0; i < text.length(); i++) {
            int digit = Character.digit(text.charAt(i), 10);
            inventory.setItem(startSlot + i, headItem(theme.statusDigits().get(digit), messagePath, Map.of("value", text, "digit", String.valueOf(digit))));
        }
    }

    private void renderRightNumber(Inventory inventory, Theme theme, int value, int endSlot, int maxDigits, String messagePath) {
        String text = String.valueOf(Math.max(0, Math.min(value, maxDigits == 3 ? 999 : 99)));
        int startSlot = endSlot - text.length() + 1;
        for (int i = 0; i < text.length(); i++) {
            int digit = Character.digit(text.charAt(i), 10);
            inventory.setItem(startSlot + i, headItem(theme.statusDigits().get(digit), messagePath, Map.of("value", text, "digit", String.valueOf(digit))));
        }
    }

    private void startUpdater(Player player) {
        UUID uuid = player.getUniqueId();
        BukkitTask previous = updateTasks.remove(uuid);
        if (previous != null) {
            previous.cancel();
        }

        updateTasks.put(uuid, Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline() || plugin.isAuthenticated(player) || !sessions.containsKey(uuid)) {
                closeSession(uuid);
                return;
            }
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof PinGuiHolder holder
                    && holder.uuid().equals(uuid)) {
                render(player.getOpenInventory().getTopInventory(), sessions.get(uuid), player);
            }
        }, 20L, 20L));
    }

    private void closeSession(UUID uuid) {
        sessions.remove(uuid);
        BukkitTask task = updateTasks.remove(uuid);
        if (task != null) {
            task.cancel();
        }
    }

    private ItemStack namedItem(Material material, String messagePath) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(plugin.getMiniMessage().deserialize(plugin.getFormattedMessageString(messagePath, false)));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack headItem(String value, String messagePath, Map<String, String> placeholders) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof SkullMeta skullMeta) {
            applyTexture(skullMeta, value);
            skullMeta.displayName(plugin.getMiniMessage().deserialize(
                    applyPlaceholders(plugin.getFormattedMessageString(messagePath, false), placeholders)
            ));
            item.setItemMeta(skullMeta);
            return item;
        }
        return namedItem(Material.STONE_BUTTON, messagePath);
    }

    private void applyTexture(SkullMeta meta, String value) {
        String textureUrl = textureUrl(value);
        if (textureUrl.isBlank()) return;

        try {
            PlayerProfile profile = Bukkit.createPlayerProfile(UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8)));
            URL url = URI.create(textureUrl).toURL();
            profile.getTextures().setSkin(url);
            meta.setOwnerProfile(profile);
        } catch (Exception ignored) {
        }
    }

    private String textureUrl(String base64) {
        try {
            String decoded = new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
            Matcher matcher = TEXTURE_URL_PATTERN.matcher(decoded);
            return matcher.find() ? matcher.group() : "";
        } catch (IllegalArgumentException ignored) {
            return "";
        }
    }

    private String applyPlaceholders(String text, Map<String, String> placeholders) {
        String result = text;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getView().getTopInventory().getHolder() instanceof PinGuiHolder holder)) return;
        if (!holder.uuid().equals(player.getUniqueId())) return;

        event.setCancelled(true);
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getView().getTopInventory())) return;

        Session session = sessions.get(player.getUniqueId());
        if (session == null) return;

        int slot = event.getRawSlot();
        for (Map.Entry<Integer, Integer> entry : session.digitSlots().entrySet()) {
            if (entry.getValue() == slot) {
                appendDigit(player, event.getView().getTopInventory(), session, entry.getKey());
                return;
            }
        }

        if (slot == BACKSPACE_SLOT) {
            session.backspace();
            render(event.getView().getTopInventory(), session, player);
            return;
        }
        if (slot == CLEAR_SLOT) {
            session.clear();
            render(event.getView().getTopInventory(), session, player);
            return;
        }
        if (slot == EXIT_SLOT) {
            if (session.mode() == Mode.PREVIEW) {
                intentionalClose.add(player.getUniqueId());
                closeSession(player.getUniqueId());
                closeAttempts.remove(player.getUniqueId());
                player.closeInventory();
                plugin.playConfiguredSound(player, "auth-settings.sounds.pin-gui-close");
                return;
            }
            kickFromGui(player);
            return;
        }
        if (slot == CONFIRM_SLOT) {
            confirm(player, event.getView().getTopInventory(), session);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof PinGuiHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!(event.getInventory().getHolder() instanceof PinGuiHolder)) return;

        UUID uuid = player.getUniqueId();
        if (intentionalClose.remove(uuid)) return;
        Session session = sessions.get(uuid);
        if ((session != null && session.mode() == Mode.PREVIEW)
                || plugin.isAuthenticated(player)
                || !plugin.getUnauthenticatedPlayers().contains(uuid)) {
            closeSession(uuid);
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && !plugin.isAuthenticated(player) && plugin.getUnauthenticatedPlayers().contains(uuid)) {
                int attempts = closeAttempts.getOrDefault(uuid, 0) + 1;
                closeAttempts.put(uuid, attempts);
                if (attempts >= 5) {
                    intentionalClose.add(uuid);
                    closeSession(uuid);
                    closeAttempts.remove(uuid);
                    plugin.restorePreviousLocation(player);
                    plugin.restoreAuthInventory(player);
                    plugin.hideAuthBossBar(player);
                    player.kickPlayer(plugin.getRawStringMessage("messages.pin-gui-close-spam-kick", true));
                    return;
                }
                openForCurrentState(player);
            }
        });
    }

    private void appendDigit(Player player, Inventory inventory, Session session, int digit) {
        int maxLength = DISPLAY_SLOTS.length;
        if (session.isSubmitting() || session.hasError()) return;
        if (session.input().length() >= maxLength) return;
        session.append(digit);
        render(inventory, session, player);
        plugin.playConfiguredSound(player, "auth-settings.sounds.pin-gui-click");
    }

    private void confirm(Player player, Inventory inventory, Session session) {
        if (session.isSubmitting() || session.hasError()) return;
        if (session.input().length() < minPinLength()) {
            plugin.playConfiguredSound(player, "auth-settings.sounds.pin-gui-disabled-confirm");
            render(inventory, session, player);
            return;
        }

        String pin = session.input();
        if (session.mode() != Mode.LOGIN) {
            Aethelguard.PinPolicyResult policy = plugin.validatePinPolicy(pin);
            if (!policy.valid()) {
                plugin.sendMessage(player, policy.messagePath(), true, policy.placeholders());
                plugin.playConfiguredSound(player, "auth-settings.sounds.pin-gui-disabled-confirm");
                return;
            }
        }

        if (session.mode() == Mode.PREVIEW) {
            session.clear();
            render(inventory, session, player);
            plugin.playConfiguredSound(player, "auth-settings.sounds.pin-gui-confirm");
            return;
        }

        session.setSubmitting(true);
        render(inventory, session, player);
        plugin.playConfiguredSound(player, "auth-settings.sounds.pin-gui-confirm");

        if (session.mode() == Mode.LOGIN) {
            pinCommand.submitPinLogin(player, pin, true);
        } else {
            pinCommand.submitSetPin(player, pin, true);
        }
    }

    private void kickFromGui(Player player) {
        UUID uuid = player.getUniqueId();
        intentionalClose.add(uuid);
        closeSession(uuid);
        closeAttempts.remove(uuid);
        plugin.playConfiguredSound(player, "auth-settings.sounds.pin-gui-close");
        plugin.restorePreviousLocation(player);
        plugin.restoreAuthInventory(player);
        plugin.hideAuthBossBar(player);
        player.kickPlayer(plugin.getRawStringMessage("messages.pin-gui-exit-kick", true));
    }

    private enum Mode {
        LOGIN,
        SETUP,
        PREVIEW
    }

    private static final class Session {
        private final Mode mode;
        private final String themeOverride;
        private final Map<Integer, Integer> digitSlots;
        private final StringBuilder input = new StringBuilder();
        private boolean submitting;
        private long errorUntil;

        private Session(Mode mode, String themeOverride, Map<Integer, Integer> digitSlots) {
            this.mode = mode;
            this.themeOverride = themeOverride;
            this.digitSlots = digitSlots;
        }

        private Mode mode() {
            return mode;
        }

        private String themeOverride() {
            return themeOverride;
        }

        private Map<Integer, Integer> digitSlots() {
            return digitSlots;
        }

        private String input() {
            return input.toString();
        }

        private boolean isSubmitting() {
            return submitting;
        }

        private void setSubmitting(boolean submitting) {
            this.submitting = submitting;
        }

        private boolean hasError() {
            return errorUntil > System.currentTimeMillis();
        }

        private void fail() {
            this.errorUntil = System.currentTimeMillis() + 5000L;
            this.submitting = false;
        }

        private void clearError() {
            this.errorUntil = 0L;
        }

        private void append(int digit) {
            input.append(digit);
        }

        private void backspace() {
            if (!input.isEmpty()) {
                input.deleteCharAt(input.length() - 1);
            }
        }

        private void clear() {
            input.setLength(0);
        }

        private void trim(int maxLength) {
            if (input.length() > maxLength) {
                input.setLength(maxLength);
            }
        }
    }

    private record PinGuiHolder(UUID uuid) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private record Theme(Map<Integer, String> digits, Map<Integer, String> statusDigits, String secret, String confirm,
                         String backspace, String clear, String exit, String attemptLabel, String colon) {
        private static Theme resolve(Aethelguard plugin, String override) {
            String name = override == null || override.isBlank()
                    ? plugin.getConfig().getString("auth-settings.pin.gui.theme", "quartz")
                    : override;
            String theme = name == null ? "quartz" : name.toLowerCase(Locale.ROOT);
            boolean special = plugin.getConfig().getBoolean("auth-settings.pin.gui.use-special-buttons", false) || theme.equals("netherite");

            Map<Integer, String> digits = switch (theme) {
                case "quartz" -> numbers("quartz");
                case "pumpkin" -> numbers("pumpkin");
                case "netherite" -> numbers("netherite");
                case "monitor-green" -> monitorNumbers("g");
                case "monitor-red" -> monitorNumbers("r");
                default -> numbers("forest-green");
            };

            String buttonSet = special || theme.startsWith("monitor-") || theme.equals("netherite") ? "special" : theme;
            String secretSet = theme.startsWith("monitor-") ? "monitor" : theme;
            return new Theme(
                    digits,
                    numbers("numbers"),
                    value(secretSet, "secret"),
                    value(buttonSet, "confirm"),
                    value(buttonSet, "backspace"),
                    value(buttonSet, "clear"),
                    value(buttonSet, "exit"),
                    value("attempts", "a"),
                    value("attempts", "colon")
            );
        }

        private static Map<Integer, String> numbers(String theme) {
            Map<Integer, String> result = new HashMap<>();
            for (int i = 0; i <= 9; i++) {
                result.put(i, value(theme, String.valueOf(i)));
            }
            return result;
        }

        private static Map<Integer, String> monitorNumbers(String colorPrefix) {
            Map<Integer, String> result = new HashMap<>();
            for (int i = 0; i <= 9; i++) {
                result.put(i, value("monitor", colorPrefix + i));
            }
            return result;
        }

        private static String value(String theme, String key) {
            return HEADS.getOrDefault(theme + "." + key, HEADS.getOrDefault("forest-green." + key, ""));
        }

        private static final Map<String, String> HEADS = createHeads();

        private static Map<String, String> createHeads() {
            Map<String, String> heads = new HashMap<>();
            load(heads, "forest-green", List.of(
                    "1=eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvN2U2OWI1Y2ZlYmUwNWRkNDMwNTAyNzdlNWJlODNjODA3YjQ1YWRlMzBlMDgyOTFmNjg2N2M3MjNlODU0YjQ4MiJ9fX0=",
                    "2=eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNDNkYmNkMzJiNTVlOGJiYzM2M2E3NDk3ZjA3NTc2MzhhN2JiMDc4YmUwN2YzNjJkMTExMjhjZjQyODhhNmJjYyJ9fX0=",
                    "3=eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvM2MyOWU0Nzc0MDE4ODljOWUyNjNjZDQ5ZWM0Yzk4ODA1NDAxNDk5YjI3Yzk1NDYxNDIwMTAxOGI5MTcwMDc2ZCJ9fX0=",
                    "4=eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNGQ4NmYwYTA1ZGM2ZmE3NmJjNDZjNGUzYzEyMWI0ZTBiOTUxNzgyMjgwODQ3NDdjYWQ2N2U1MWQ5YzVlNmJhYiJ9fX0=",
                    "5=eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTNmMWMzNTI0MGQ0ZGY4YTJlNWY4MTU5MzQ3YTFkNDJmYjdiZGQ1MGM5MGRlOTVhZjMwNjg0MTgyNWQ3MmJiZCJ9fX0=",
                    "6=eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNGYxYmE1NTk5NzIyZGJhNjFkMTZmNzU5NjFmZTA5NGRlNDUxZDUxYjdkZGYwODdiZGNlNDFlZjkzMTk2ZDgxNiJ9fX0=",
                    "7=eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMTA4Yjg4ZWZhMGZiMWZkMjMwMTNhYTEzNmFjYzFhZjQ0YmRlZDVmM2FlNDE3ZWY3ZjZkNDdiNGYyYzQ1NTBhMyJ9fX0=",
                    "8=eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTE1ZmQxYjdjNjM3NzNmOTMyNDUzNmU0OGNiZDBhYzEwMWUyYzE4Y2FiYWE1NTk5M2M1NWRiMDJhODZlMTk2YSJ9fX0=",
                    "9=eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvODE0M2YyMmMwOWM4OGM0MWZiMGM0ZDZmZjI0ZjQ5N2Q4YzI4NjM0NWEwZGY2OTk4YTA1MGNmOTc1OWE3MmQxYSJ9fX0=",
                    "0=eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYmRiMDFhMzA3NzU2Mjg0MzA5MGUwMzBlZTdiNGE4NjM0ZTZmYzJkNTNlNDYwM2EzNGYyOGY1ZjJhZGMzMzcxZCJ9fX0=",
                    "confirm=eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZWVmZDRjZmE5MTQzMzk4ZTExZWJjZTNmYjJlY2JiZjU2YTA3M2UzNTYzZjZlZjZhY2FlNGNiNzM5N2U1ODhiMiJ9fX0=",
                    "clear=eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYmQxMDZlZjFhY2RkMDk5OGU2ZGJiYjUzMmUzMGFmNTU0YjNlMGVhOTE2MjFlNGViMGQxYTkzMTlkZWJmNjU0ZSJ9fX0=",
                    "backspace=eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvN2YzZTdiY2E1YzY1MWJiYTI5Y2QzNTlkNWNkNDc0NDAyY2MyM2NhN2IzMDlkYzQ4NzM2NDM2YjlmMDU1YjkwNSJ9fX0=",
                    "exit=eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZDZhZTIxMWI0NDAzYjg5NjNjNjc0NWYzYzk4ZWJlNzNhMmI0ZTk3YzQwYTc4YjJmZDQwM2EwOWMwZmNhZDZkIn19fQ==",
                    "secret=eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZTY4MzEyY2UzNzg4YjA0M2EzMGJmZTlkZTVkMDI1OTk4ZmFhNTNmYmExYzU2YzI2MGIzZTBkMThkNzg4NjNjOSJ9fX0="
            ));
            load(heads, "quartz", List.of(
                    "1=eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYTBhMTllMjNkMjFmMmRiMDYzY2M1NWU5OWFlODc0ZGM4YjIzYmU3NzliZTM0ZTUyZTdjN2I5YTI1In19fQ==",
                    "2=eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvY2M1OTZhNDFkYWVhNTFiZTJlOWZlYzdkZTJkODkwNjhlMmZhNjFjOWQ1N2E4YmRkZTQ0YjU1OTM3YjYwMzcifX19",
                    "3=eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYjg1ZDRmZGE1NmJmZWI4NTEyNDQ2MGZmNzJiMjUxZGNhOGQxZGViNjU3ODA3MGQ2MTJiMmQzYWRiZjVhOCJ9fX0=",
                    "4=eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMzg1MmEyNWZlNjljYTg2ZmI5ODJmYjNjYzdhYzk3OTNmNzM1NmI1MGI5MmNiMGUxOTNkNmI0NjMyYTliZDYyOSJ9fX0=",
                    "5=eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzRlZTdkOTU0ZWIxNGE1Y2NkMzQ2MjY2MjMxYmY5YTY3MTY1MjdiNTliYmNkNzk1NmNlZjA0YTlkOWIifX19",
                    "6=eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMjY4MmEzYWU5NDgzNzRlMDM3ZTNkN2RkNjg3ZDU5ZDE4NWRkMmNjOGZjMDlkZmViNDJmOThmOGQyNTllNWMzIn19fQ==",
                    "7=eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNGVhMzBjMjRjNjBiM2JjMWFmNjU4ZWY2NjFiNzcxYzQ4ZDViOWM5ZTI4MTg4Y2Y5ZGU5ZjgzMjQyMmU1MTAifX19",
                    "8=eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNjZhYmFmZDAyM2YyMzBlNDQ4NWFhZjI2ZTE5MzY4ZjU5ODBkNGYxNGE1OWZjYzZkMTFhNDQ2Njk5NDg5MiJ9fX0=",
                    "9=eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOGQ3OTEwZTEwMzM0Zjg5MGE2MjU0ODNhYzBjODI0YjVlNGExYTRiMTVhOTU2MzI3YTNlM2FlNDU4ZDllYTQifX19",
                    "0=eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMWY4ODZkOWM0MGVmN2Y1MGMyMzg4MjQ3OTJjNDFmYmZiNTRmNjY1ZjE1OWJmMWJjYjBiMjdiM2VhZDM3M2IifX19",
                    "confirm=eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMzdiNjJkMjc1ZDg3YzA5Y2UxMGFjYmNjZjM0YzRiYTBiNWYxMzVkNjQzZGM1MzdkYTFmMWRmMzU1YTIyNWU4MiJ9fX0=",
                    "clear=eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNGMzMDFhMTdjOTU1ODA3ZDg5ZjljNzJhMTkyMDdkMTM5M2I4YzU4YzRlNmU0MjBmNzE0ZjY5NmE4N2ZkZCJ9fX0=",
                    "backspace=eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNDgzNDhhYTc3ZjlmYjJiOTFlZWY2NjJiNWM4MWI1Y2EzMzVkZGVlMWI5MDVmM2E4YjkyMDk1ZDBhMWYxNDEifX19",
                    "exit=eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMzkxZDZlZGE4M2VkMmMyNGRjZGNjYjFlMzNkZjM2OTRlZWUzOTdhNTcwMTIyNTViZmM1NmEzYzI0NGJjYzQ3NCJ9fX0=",
                    "secret=eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMDlhNTJjYjUwOTkyZDgzYzU1OTlmZDZlNDFhNmNlOTljZjdmMWU2MjAzNjExOTYzZGMyYzJmZGEwYjU1NTgzIn19fQ=="
            ));
            load(heads, "special", List.of(
                    "confirm=eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZDk5ODBjMWQyMTE4MDlhOWI2NTY1MDg4ZjU2YTM4ZjJlZjQ5MTE1YzEwNTRmYTY2MjQ1MTIyZTllZWVkZWNjMiJ9fX0=",
                    "clear=eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZWZjZTQ3NWE0Mzg0ZTA5ZTMyY2Q4ZTkxZDA5Yzc5NDdhZGY3ODI3MzA3ZmRhZGRiYWYyOTk4NTE0OTQ4ZmI2ZSJ9fX0=",
                    "backspace=eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZmM5OWFhNmZjMmVjY2UzNTY2NWQ5NDhhMDEzMjUxNTNmZTUzZmMxNzcxZmIyNzg0ZjU3OTY3ZjEwZTJkZGNmOCJ9fX0=",
                    "exit=eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZWQwYTE0MjA4NDRjZTIzN2E0NWQyZTdlNTQ0ZDEzNTg0MWU5ZjgyZDA5ZTIwMzI2N2NmODg5NmM4NTE1ZTM2MCJ9fX0="
            ));
            load(heads, "attempts", List.of(
                    "a=eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMTdkZDM0OTI0ZDJiNmEyMTNhNWVkNDZhZTU3ODNmOTUzNzNhOWVmNWNlNWM4OGY5ZDczNjcwNTk4M2I5NyJ9fX0=",
                    "colon=eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYWM0YzI2OTYzZjg1MzhjMTFlYWM2YThlNDM3ZDI3NjgyZGZmZWE0YmNjMGYwNGFmZDVjZGE2ZDFkNTY3In19fQ=="
            ));
            load(heads, "numbers", List.of(
                    "1=eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvODZiYzE4NzcyYWNkMzQzZTZhYjIwMTAzYzRhOWU0MjExNWYyMjQ4NDI3ZWNiZDIwNjcxZDZjZDI0ZWU3ZWY4YyJ9fX0=",
                    "2=eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNWI3ODFjOTUzZjg1ZGY3NzVmOTdjNjRmZWYyZjUwYjczMTM2NjI1NjgyN2UwZjg4YzIyOTcwYzIzMzE3YzY5NSJ9fX0=",
                    "3=eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYTk0ZGM4ZWU0ODJiMTkxN2JkZWQ2Yjk1MjM5ZDAwODY2MzZkNWI2YjA3MGQ3MWRkNWViNWY0YjI3NWQ2OTNiMCJ9fX0=",
                    "4=eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzc2NTc5YTQxZDU3OWExZmUxN2RkZTg4Zjg5ZmE4Mzc3NmRlNzE2YmM5ZjI0ZGEyN2YyMTFiZTlmZTIxOWFmZSJ9fX0=",
                    "5=eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMTEwYTZmY2MyMjRkYzg1YjNlNzI1YWUyYjg2ZmQ1ZmRhNGUyNGMwMDQ3NTUzMDdjZDNmZGNlNGFlNmRhYTFmMSJ9fX0=",
                    "6=eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZmExZDgxNGExMTZiNGI1MGQ0ODYyZTVjZTUzZDFhNDExYjhiYTJhNmYxOTUzMWIzYWUyMmNlZDdmNzA5ZTJhZCJ9fX0=",
                    "7=eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTY5YTdmNTZlMWJmZWIwZDU5ZjU4OWUzNzc0MDZlYTViMWQxYzFkYzFjNDA4Nzk0ZDAzNWY3M2NmODhiNjdmNSJ9fX0=",
                    "8=eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZjU4NjlmNjJhYmM1ZGM3M2NlNzZiMGQ3OTAxMzZlNzQ3MTNhYTIxNWRmYWNmMWRmNzMxNzVjMTkxMDJlNzYzYiJ9fX0=",
                    "9=eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzVlOWRlNDBmNjdiM2YyN2Y4ZGE5Y2I0NDYwMGIyM2U4NTBkNWVkYTYxZDVjMzgyNTU1NGM1ZWQ1ODk5MGU0MSJ9fX0=",
                    "0=eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYjA3MjNjMWVmM2U2ZTUxZWM0ZmQzOTZiNmJhMzg5NGE3Njc0YmU5MzVmNmVkY2E1ODNmYjU4Y2M5NGRmODE4NCJ9fX0="
            ));
            load(heads, "monitor", List.of(
                    "r1=eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzMwNDFjMmVhODI3YmY2NjFiNjQ3MmE4ZTMxMGY2NTFiMjk0ODNhNWZkN2U3NDEzOTdmOTZjMTUxNWIxNjgifX19",
                    "r2=eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZTE3MzgxYWE0Y2M3M2Q5ZDc5ODk0MTc0NTc2ZWJmMzRmN2ZlNDAzNDZhYmI4YzcxYWRjNTczZWE4YzdlYzUifX19",
                    "r3=eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYmJlNTk3M2U0ZTc5MTYyMzk5M2U2N2MyNDliNzJmM2M1MTYwZjkxZDY5MjZhM2E4NzU2YmM1NDNmMzgzNTIzMiJ9fX0=",
                    "r4=eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMTgzYzZiYmRiYTZhODc3Zjk5NmQ4YzNkZDFkOWI4NDUwODViZTFiZGIyZjUzNWUyYjZmOGZjM2M2NTg5YjEifX19",
                    "r5=eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvODJlODViOTMxYjY4ZDZjZDk1YmFjZGExOGRkOGMwODZjZmJlNmIzZjc5ZTY0ZTNiZDNjOGVlMGRhNmE5ZDMyIn19fQ==",
                    "r6=eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYzM0NWQxNjM3ODg0NjhiNTQ3OTUxNzg3ZWUyOTQzMzFjZmRkOWJkMTUxNGMyNDI0YzRkNDU5YzVhMDY5NjljMCJ9fX0=",
                    "r7=eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNDM5MDk0OTY3Y2E2NmQ4OTJjNmVmMTU4Y2U4MjllOTA0YzllMjRjODY0ZWRmMjc3MTFkNDEwZWY2YzQyMmI3ZCJ9fX0=",
                    "r8=eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYmNkZmYzNjVhZTdlMzhhZTI3YjFkNGNmZWUxNTg0OGNhYmY3NDZhMTc4NWVlZDE4MTUzNmE3ZmY1YzM4ZSJ9fX0=",
                    "r9=eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYjQxNmVlZDc5MjU5NTA1NzliMzkxZWQxZTEzMDQyNmQ1NjE5NzZkZDYxNzE1ZDBhMmQzNWNlOTA0Y2Y0In19fQ==",
                    "r0=eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNGMyNDJhNTkzOTI4YTgyYTI0ZWZkZDdhZjA5NGNiZDA2MjVmMzA5MWE1YmU4ZGY1MmIzNGYyYjU4ZWQyNWIifX19",
                    "g1=eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMjFkODU5ZThiMTRmNjI2NDY4NTljZjM4MDRhNjRmMTA2MGQ2ODc5MzQxYjRjMzM4NWI0NmEwZWM0MGZhZjczYyJ9fX0=",
                    "g2=eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYjNkOTNlOGI1ZmIwYjVkNTBhYmQ0ZWY4ODUzMmY0Njg3NGI5OTI0ZjY2OGRkYjAxMDkxNDY4ZTRlNjFiOWM4MyJ9fX0=",
                    "g3=eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZWE3OWRhYTkyMDhhMDU2NTAwYzgzY2QyMDkwZmFlYzkxZWFlNTQwNTc5MmU0ZjU0NzI2OTdiMGU3ZGFjMzIzYSJ9fX0=",
                    "g4=eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMjFiZDg5OGNjYjFlNWFmNzFkNjQ0MTFhMTU5YzBkNDY5YWY2MzkxMDYyZTgwMmJmZTgxOTk1NjVlOGQ3YmU2OCJ9fX0=",
                    "g5=eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMzNjZDkzNGYxMWYwNzY2ZjU0MTBlYmE5ZTdiNWYwY2ViNjZmNmIzMTdlODQ1Y2I2YTUwMWYzNzI1ODU1NmE0MyJ9fX0==",
                    "g6=eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzk1NTQ0YTQwODBmNmE0YWYxOWUwODAxZGE0OTI4MzhmYjA0YmM1MzRmYzg0NjAwZTA0Yzg4MTVlMTMxZTI5ZCJ9fX0=",
                    "g7=eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZjQzZjhiMTA1ZWMzYjQ4OTYzZTk4MWFmZjgyNWM1YWI5NGRmZmVlNWQ4NGE4NjlhZDA3MmFmYThmZDIxNGU3ZSJ9fX0=",
                    "g8=eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvY2VmODNkMDI3OWEyZTUxZTdlNTMyZDIwMmRmNWVjN2RiODNmOTZkM2ZiYjI0NWRhMWI2MjhjMWYwYjFlZWNiZCJ9fX0=",
                    "g9=eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvN2U0ZWFiNWM4YzgyZTBhOTE4MjhmMGE1ZGJkNDNkMjYwNzZiYzRiNTdjZTFkMTM1ZWNhMmQ3YmQwYjFkZTMwIn19fQ==",
                    "g0=eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTBmYmRmMjYwZTJjZjIwMjViZmJjMzA0ZmE3YjgxNTkzMDE2ZDA3MjliMDBkYjE2ZjA5ZWQwMWY5YmQzZTY5OSJ9fX0=",
                    "secret=eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNTIzMTU4ZTVhNmQ5NjNjNTVhODZiOWQ0NDgzZTVhNjVhM2NmYTY5NGUyYWU3YzZjMjdiYzgyNmIyY2M3ZjcxZCJ9fX0="
            ));
            load(heads, "netherite", List.of(
                    "1=eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYTg0MzJhNTc1NmEwNGViZjA2MmQ3MmE2ZjMxYmQ2MmU4ZjRkODJhOTIxMjAzMzZhZTE5NzJmZTE4ZDM4NzBiYSJ9fX0=",
                    "2=eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvN2U1MGM3MDk3OTk0MzEzZDk0MzIxNDJkYTc2NTFkYzZkZDYzMzU4N2UyZTFkZDlhNTYyYWJiYzc4NzhlZmI2NSJ9fX0=",
                    "3=eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNWRkMjJkYjhjNmUyMzhmYjhjYzA4MTlkMDJhNjU0MDMyOTdkNjNiNjdjNmM3Y2U2YjQzYmM4MjkxODk4MzdmNCJ9fX0=",
                    "4=eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvODU0YzFkZWQ5MjMxOWJkODM1NzNmMGYwMDQxZTczMDMzOGViN2JiNzk5N2ViNzFmZjU4M2MyOTA4MzIzODg4ZSJ9fX0=",
                    "5=eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNTRkYWM3Y2YyMDE3YTJhZWZjZGYyOWRjMzgzMmQ0MDdjYmQ5YzhiNmJhMGU1MWEwYTMxNjlmNmZmYjYyYzAxNSJ9fX0=",
                    "6=eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZGVkOWJlZGNjMWQxYzQ4Y2FlZTU3MjhlMWVmOWIwMDhkNWE1ZDMwZDJlMTRjMWM3Yjg0OWU4ZDg1NTNiNTI1NyJ9fX0=",
                    "7=eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTBiYmE4ZDU2YmI2YjkwNjY4M2IxOGZiYTQ4MWQ1OGZhMGJiN2QzYjNiMThjNjQ1MmU5MjU3ZGY1NDJmNTNhYSJ9fX0=",
                    "8=eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvODY5Y2Q3NGMwYThhMmYwMTA1NTg4NmY4MTVmOGUxZWQ2ZDdkZTZlNzg4YWFlOWY4NmJkMzdlMmQ0ZTQ2MjFjNiJ9fX0=",
                    "9=eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTljZDcyNjlhMzE0MDQ4YmM3Zjc5Mjk0NDhhYWJmNGJiYzlkNjk4MTdhOTJmZjUwNTZiMDY1YWRkOTQwODU4OSJ9fX0=",
                    "0=eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZTBjZjRlNGMzNDVmZTYxNmIwYTljMDRjZGQyZmVlZjYxZGI0YjFjYjE0YjAwMzVhMjgwMjBkZmU3M2MyZDVkMSJ9fX0=",
                    "secret=eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZjVjOTZjZjNlNWM2OTgwYzcxNGYyNzkxN2I2NDM5YjA1OTY1MmY0Y2MyMTRhZGQ3MGRjNzQwYzZjMWZlNzBmMSJ9fX0="
            ));
            load(heads, "pumpkin", List.of(
                    "1=eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMzBjNDg3M2U0YTIxOTE0NWViNGUzZjY2YzA5ZTJjZThiMjdkZTJmZDgxZGViNDhlM2M3ZTA0ZTk3NGU1ZWFhIn19fQ==",
                    "2=eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYTM1ZjhmNTZlMWEyMzNmY2Q5Nzc3Mjg1ZTRiNWFiMTNmYWEwYzZkODJmYTYzNTQyNWMxNzQ1ZDQxMWU4NTcxZCJ9fX0=",
                    "3=eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMTQ0NjM1ZDZiYjhmOWFhNDZkYjYwODc1YmI1ZGIzOTQwYWIwYzRmMGViNjg0YzZiODg4NjQ3YmIzMzkyN2UifX19",
                    "4=eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMjlhOWYzZDc5YTJkOGRiOGQ2NmI1OTc1MjU0MmFkNmUxMzQ5ODUzMjU4ZmFkOTg0ODc4OTI3NzM2YmQ1YjkxIn19fQ==",
                    "5=eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZjllOGQyMjhlNTMwNzlkMDBiMmRmYzU3OTkzZDUyZmY3OGU2YTdlYTI3NDJiYmRmYjNjNGY0ZTkzY2QxOWI2In19fQ==",
                    "6=eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMmM1ZjU3N2ZiNWM0YjQ4OGQ5ODBlZjczM2I1ZWQ1ZDU0NWE4ZTA3MDFhYjI4NDFkODE3ZTgzODgzYTU2MWYifX19",
                    "7=eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzRiYmI3ZWJkNWQ5ZGViZjcyYTQyY2FiZjMwZDI1NTc0MzI0Y2VkYWM4ZjdmOGE1NTAxY2Q4MWU3MTZmOSJ9fX0=",
                    "8=eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNDhlZmI2Y2QxZjhlNTkxZWU1M2MxOTJmNjQ3NmU4M2UyNWI5NWJiZDI3ZWVjMWM2NDRjMTE1MjVmMDc2MjIifX19",
                    "9=eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYzAyZjYwMjNiNWI5MGY2ZTJiMGIyZTNhNzIyOTc2MzQ3MTlkNTc0YmQ5ZmY2NDc2N2Y1NGQxOWJlZDNmYSJ9fX0=",
                    "0=eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNWIyMmI3OWI3MmFjZWY4NTkxMGRiOWU5YWU4YTdiYWU4ZWJjZTgzNDJjMThlMzQ3MjUyNzNlNzdjMWFhNCJ9fX0=",
                    "confirm=eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYjg5ZjE1OGFkYjQzMTZiMTM2NTJmNDNkYWYzNzViZTgzYjQ1YWQ0YzlmMjZiZWE1YWFkYjhlYmI3NzlmZGVlMSJ9fX0=",
                    "clear=eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvN2VhYmY2ZGY2NTgxODQzOGVhMGM1Zjg4NmRlY2IzOTMzOTNmMTVhYmYyNzg3YTVlYzRlODM2ODIxOTJiYSJ9fX0=",
                    "backspace=eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzE1OTdhMzY5NmIxOWY1Y2ZiYTlmOGJlNzU3OTA4NDgxYzg2YmU1YmIzZTc2YTg5M2I0MmZlNTYxNzJlYyJ9fX0=",
                    "exit=eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMTZmOTFkNTZhY2FjZGRmZTdiNjQ1NjZhZDNkM2JhMWQxOGZmOWZiOTQxYjRkZGQ4M2NiNTY3MjYyMTA4OSJ9fX0=",
                    "secret=eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMThhNzZmZmEyYTJmMmMwY2Q2NjlkNGY4OTI1OTQ2MDRkY2Q5ZGFkNGM4NWFlYjJlNWFhYmYyNTcyZDZjYjEifX19"
            ));
            return heads;
        }

        private static void load(Map<String, String> heads, String theme, List<String> entries) {
            for (String entry : entries) {
                int split = entry.indexOf('=');
                if (split <= 0) continue;
                heads.put(theme + "." + entry.substring(0, split), entry.substring(split + 1));
            }
        }
    }
}
