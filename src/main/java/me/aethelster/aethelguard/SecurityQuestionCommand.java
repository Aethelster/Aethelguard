package me.aethelster.aethelguard;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;

public class SecurityQuestionCommand implements CommandExecutor {
    private final Aethelguard plugin;

    public SecurityQuestionCommand(Aethelguard plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getRawStringMessage("messages.only-players", false));
            return true;
        }

        if (!plugin.isAuthenticated(player)) {
            plugin.sendMessage(player, "messages.security-question-auth-required", true);
            return true;
        }

        if (!plugin.getConfig().getBoolean("recovery.security-questions.enabled", true)) {
            plugin.sendMessage(player, "messages.security-question-disabled", true);
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            Aethelguard.SecurityQuestion question = plugin.getStoredSecurityQuestion(player.getUniqueId());
            plugin.sendMessage(player, question == null
                    ? "messages.security-question-status-missing"
                    : "messages.security-question-status-set", true);
            return true;
        }

        if (args[0].equalsIgnoreCase("setup") || args[0].equalsIgnoreCase("change")) {
            long remaining = plugin.getSecurityCooldownRemainingMillis(player, "security-question-change");
            if (remaining > 0L) {
                plugin.sendMessage(player, "messages.security-cooldown-active", true,
                        Map.of("time", plugin.formatDuration(remaining)));
                return true;
            }

            Aethelguard.SecurityQuestion question = plugin.createPendingSecurityQuestion(player);
            plugin.sendMessage(player, "messages.security-question-setup-start", true,
                    Map.of("question", question.text()));
            return true;
        }

        if (args[0].equalsIgnoreCase("answer")) {
            if (args.length < 2) {
                plugin.sendMessage(player, "messages.security-question-answer-usage", true);
                return true;
            }

            String answer = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
            if (plugin.confirmSecurityQuestion(player, answer)) {
                plugin.markSecurityCooldown(player, "security-question-change");
                plugin.sendMessage(player, "messages.security-question-saved", true);
            } else {
                plugin.sendMessage(player, "messages.security-question-no-pending", true);
            }
            return true;
        }

        plugin.sendMessage(player, "messages.security-question-usage", true);
        return true;
    }
}
