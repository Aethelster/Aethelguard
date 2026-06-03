package me.aethelster.aethelguard;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.mindrot.jbcrypt.BCrypt;

import java.util.Arrays;
import java.util.Map;

public class RecoverCommand implements CommandExecutor {
    private final Aethelguard plugin;

    public RecoverCommand(Aethelguard plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getRawStringMessage("messages.only-players", false));
            return true;
        }

        if (plugin.isAuthenticated(player)) {
            plugin.sendMessage(player, "messages.recover-already-authenticated", true);
            return true;
        }

        if (!plugin.getConfig().getBoolean("recovery.enabled", true)) {
            plugin.sendMessage(player, "messages.recover-disabled", true);
            return true;
        }

        if (!plugin.isAccountRegistered(player.getUniqueId())) {
            plugin.sendMessage(player, "messages.not-registered", true);
            return true;
        }

        if (args.length < 3) {
            sendUsage(player);
            return true;
        }

        long remaining = plugin.getSecurityCooldownRemainingMillis(player, "recover");
        if (remaining > 0L) {
            plugin.sendMessage(player, "messages.security-cooldown-active", true,
                    Map.of("time", plugin.formatDuration(remaining)));
            return true;
        }

        String method = args[0].toLowerCase();
        String newPassword = args[args.length - 1];
        String proof = String.join(" ", Arrays.copyOfRange(args, 1, args.length - 1));

        Aethelguard.PasswordPolicyResult passwordPolicy = plugin.validatePasswordPolicy(newPassword, player.getName());
        if (!passwordPolicy.valid()) {
            plugin.sendMessage(player, passwordPolicy.messagePath(), true, passwordPolicy.placeholders());
            return true;
        }

        boolean verified;
        if (method.equals("question")) {
            if (!plugin.getConfig().getBoolean("recovery.security-questions.enabled", true)) {
                plugin.sendMessage(player, "messages.security-question-disabled", true);
                return true;
            }
            if (!plugin.getRecoveryMethod(player.getUniqueId()).equals("question")) {
                plugin.sendMessage(player, "messages.recover-method-mismatch", true,
                        Map.of("method", plugin.getRecoveryMethod(player.getUniqueId())));
                return true;
            }
            verified = plugin.verifySecurityQuestionAnswer(player.getUniqueId(), proof);
        } else if (method.equals("code") || method.equals("backup") || method.equals("backup-code")) {
            if (!plugin.getConfig().getBoolean("recovery.backup-codes.enabled", true)) {
                plugin.sendMessage(player, "messages.backup-codes-disabled", true);
                return true;
            }
            if (!plugin.getRecoveryMethod(player.getUniqueId()).equals("backup-code")) {
                plugin.sendMessage(player, "messages.recover-method-mismatch", true,
                        Map.of("method", plugin.getRecoveryMethod(player.getUniqueId())));
                return true;
            }
            verified = plugin.consumeBackupCode(player.getUniqueId(), proof);
        } else {
            sendUsage(player);
            return true;
        }

        if (!verified) {
            plugin.sendMessage(player, "messages.recover-invalid-proof", true);
            return true;
        }

        if (plugin.updateAccountPassword(player.getUniqueId(), BCrypt.hashpw(newPassword, BCrypt.gensalt()))) {
            plugin.markSecurityCooldown(player, "recover");
            plugin.getWrongPasswordAttempts().remove(player.getUniqueId());
            plugin.sendMessage(player, "messages.recover-success", true);
        } else {
            plugin.sendMessage(player, "messages.recover-error", true);
        }
        return true;
    }

    private void sendUsage(Player player) {
        Aethelguard.SecurityQuestion question = plugin.getStoredSecurityQuestion(player.getUniqueId());
        if (question != null) {
            plugin.sendMessage(player, "messages.recover-question-hint", true,
                    Map.of("question", question.text()));
        }
        plugin.sendMessage(player, "messages.recover-usage", true);
    }
}
