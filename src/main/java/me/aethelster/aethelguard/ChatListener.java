package me.aethelster.aethelguard;

import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class ChatListener implements Listener {

    private final Aethelguard plugin;

    public ChatListener(Aethelguard plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player sender = event.getPlayer();

        if (!plugin.isAuthenticated(sender)
                && plugin.getConfig().getBoolean("auth-settings.restrictions.prevent-chat", true)) {
            event.setCancelled(true);

            plugin.getServer().getScheduler().runTask(plugin, () ->
                    plugin.sendMessage(sender, "messages.chat-denied", true)
            );

            if (plugin.getConfig().getBoolean("console-logging.log-blocked-chat-attempts", true)) {
                plugin.logInfo(
                        "§7Giriş yapmamış oyuncu (" + sender.getName() + ") mesaj göndermeyi denedi ve engellendi.",
                        "§7Unauthenticated player (" + sender.getName() + ") tried to send a chat message and was blocked."
                );
            }
            return;
        }

        if (plugin.getConfig().getBoolean("auth-settings.restrictions.hide-chat-from-unauthenticated", true)) {
            event.viewers().removeIf(viewer ->
                    viewer instanceof Player targetPlayer && !plugin.isAuthenticated(targetPlayer)
            );
        }
    }
}
