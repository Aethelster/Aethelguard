package me.aethelster.aethelguard;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

public class BackupCodesCommand implements CommandExecutor {
    private final Aethelguard plugin;

    public BackupCodesCommand(Aethelguard plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getRawStringMessage("messages.only-players", false));
            return true;
        }

        if (!plugin.isAuthenticated(player)) {
            plugin.sendMessage(player, "messages.backup-codes-auth-required", true);
            return true;
        }

        if (!plugin.getConfig().getBoolean("recovery.backup-codes.enabled", true)) {
            plugin.sendMessage(player, "messages.backup-codes-disabled", true);
            return true;
        }

        if (args.length == 0 || !args[0].equalsIgnoreCase("generate")) {
            plugin.sendMessage(player, "messages.backup-codes-usage", true);
            return true;
        }

        long remaining = plugin.getSecurityCooldownRemainingMillis(player, "backup-code-generate");
        if (remaining > 0L) {
            plugin.sendMessage(player, "messages.security-cooldown-active", true,
                    Map.of("time", plugin.formatDuration(remaining)));
            return true;
        }

        List<String> codes = plugin.generateBackupCodes(player);
        if (codes.isEmpty()) {
            plugin.sendMessage(player, "messages.backup-codes-error", true);
            return true;
        }

        plugin.markSecurityCooldown(player, "backup-code-generate");
        plugin.sendMessage(player, "messages.backup-codes-generated", true,
                Map.of("codes", String.join(", ", codes)));
        return true;
    }
}
