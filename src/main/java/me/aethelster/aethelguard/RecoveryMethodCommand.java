package me.aethelster.aethelguard;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;

public class RecoveryMethodCommand implements CommandExecutor {
    private final AethelGuard plugin;

    public RecoveryMethodCommand(AethelGuard plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getRawStringMessage("messages.only-players", false));
            return true;
        }

        if (!plugin.isAuthenticated(player)) {
            plugin.sendMessage(player, "messages.recovery-method-auth-required", true);
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            plugin.sendMessage(player, "messages.recovery-method-status", true,
                    Map.of("method", plugin.getRecoveryMethod(player.getUniqueId())));
            return true;
        }

        String method = args[0].equalsIgnoreCase("backup")
                || args[0].equalsIgnoreCase("backup-code")
                || args[0].equalsIgnoreCase("code")
                ? "backup-code"
                : args[0].equalsIgnoreCase("question") ? "question" : "";

        if (method.isBlank()) {
            plugin.sendMessage(player, "messages.recovery-method-usage", true);
            return true;
        }

        if (method.equals("question") && !plugin.getConfig().getBoolean("recovery.security-questions.enabled", true)) {
            plugin.sendMessage(player, "messages.security-question-disabled", true);
            return true;
        }
        if (method.equals("backup-code") && !plugin.getConfig().getBoolean("recovery.backup-codes.enabled", true)) {
            plugin.sendMessage(player, "messages.backup-codes-disabled", true);
            return true;
        }

        long remaining = plugin.getSecurityCooldownRemainingMillis(player, "recovery-method-change");
        if (remaining > 0L) {
            plugin.sendMessage(player, "messages.security-cooldown-active", true,
                    Map.of("time", plugin.formatDuration(remaining)));
            return true;
        }

        if (plugin.setRecoveryMethod(player.getUniqueId(), method)) {
            plugin.markSecurityCooldown(player, "recovery-method-change");
            plugin.sendMessage(player, "messages.recovery-method-set", true,
                    Map.of("method", method));
        } else {
            plugin.sendMessage(player, "messages.recovery-method-error", true);
        }
        return true;
    }
}
