package ru.optisolo.listener;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import ru.optisolo.OptiSoloPlugin;
import ru.optisolo.gui.ChatInputManager;

/** Перехват чата для ввода после кликов в меню. */
public class ChatListener implements Listener {

    private final OptiSoloPlugin plugin;

    public ChatListener(OptiSoloPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent e) {
        ChatInputManager.PendingInput input = plugin.getChatInputManager().take(e.getPlayer().getUniqueId());
        if (input == null) return;
        e.setCancelled(true);
        final Player player = e.getPlayer();
        final String text = e.getMessage().trim();
        // Миры и телепорты — только в главном потоке.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                plugin.getChatInputManager().processSync(player, input, text);
            }
        });
    }
}
