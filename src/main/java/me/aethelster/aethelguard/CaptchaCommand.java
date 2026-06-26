package me.aethelster.aethelguard;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class CaptchaCommand implements CommandExecutor {

    private final AethelGuard plugin;

    public CaptchaCommand(AethelGuard plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getRawStringMessage("messages.only-players", false));
            return true;
        }

        if (plugin.isAuthenticated(player)) {
            plugin.sendMessage(player, "messages.captcha-already-authenticated", true);
            return true;
        }

        if (!plugin.isCaptchaRequired(player)) {
            plugin.sendMessage(player, "messages.captcha-not-required", true);
            return true;
        }

        if (args.length < 1) {
            plugin.sendMessage(player, "messages.captcha-usage", true);
            plugin.sendCaptchaPrompt(player);
            return true;
        }

        long remainingCooldown = plugin.getCaptchaCooldownRemainingSeconds(player);
        if (remainingCooldown > 0L) {
            plugin.sendMessage(player, "messages.captcha-cooldown", true,
                    java.util.Map.of("seconds", String.valueOf(remainingCooldown)));
            return true;
        }

        plugin.markCaptchaCooldown(player);
        plugin.verifyCaptcha(player, args[0]);
        return true;
    }
}
