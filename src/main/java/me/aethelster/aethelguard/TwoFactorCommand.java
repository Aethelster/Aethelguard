package me.aethelster.aethelguard;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class TwoFactorCommand implements CommandExecutor {

    private final AethelGuard plugin;

    public TwoFactorCommand(AethelGuard plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getRawStringMessage("messages.only-players", false));
            return true;
        }

        if (plugin.isWaitingTwoFactor(player)) {
            if (args.length < 1) {
                plugin.sendMessage(player, "messages.two-factor-login-usage", true);
                return true;
            }
            plugin.completeTwoFactorLogin(player, args[0]);
            return true;
        }

        if (!plugin.isAuthenticated(player)) {
            plugin.sendMessage(player, "messages.two-factor-auth-required", true);
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            plugin.sendMessage(player, plugin.isTwoFactorEnabled(player)
                    ? "messages.two-factor-status-enabled"
                    : "messages.two-factor-status-disabled", true);
            return true;
        }

        if (args[0].equalsIgnoreCase("setup")) {
            long remaining = plugin.getSecurityCooldownRemainingMillis(player, "two-factor-setup");
            if (remaining > 0L) {
                plugin.sendMessage(player, "messages.security-cooldown-active", true,
                        java.util.Map.of("time", plugin.formatDuration(remaining)));
                return true;
            }
            String secret = plugin.createPendingTwoFactorSetup(player);
            plugin.sendMessage(player, "messages.two-factor-setup-start", true,
                    java.util.Map.of(
                            "secret", secret,
                            "qr_url", plugin.createTwoFactorQrUrl(player, secret)
                    ));
            return true;
        }

        if (args[0].equalsIgnoreCase("confirm")) {
            if (args.length < 2) {
                plugin.sendMessage(player, "messages.two-factor-confirm-usage", true);
                return true;
            }
            plugin.confirmTwoFactorSetup(player, args[1]);
            return true;
        }

        if (args[0].equalsIgnoreCase("disable")) {
            long remaining = plugin.getSecurityCooldownRemainingMillis(player, "two-factor-disable");
            if (remaining > 0L) {
                plugin.sendMessage(player, "messages.security-cooldown-active", true,
                        java.util.Map.of("time", plugin.formatDuration(remaining)));
                return true;
            }
            if (args.length < 2) {
                plugin.sendMessage(player, "messages.two-factor-disable-usage", true);
                return true;
            }
            plugin.disableTwoFactor(player, args[1]);
            return true;
        }

        plugin.sendMessage(player, "messages.two-factor-usage", true);
        return true;
    }
}
