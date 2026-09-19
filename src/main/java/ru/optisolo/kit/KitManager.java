package ru.optisolo.kit;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.optisolo.OptiSoloPlugin;
import ru.optisolo.config.Messages;
import ru.optisolo.config.Settings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Минимальный стартовый набор при создании мира + повторная выдача по кулдауну. */
public class KitManager {

    private final OptiSoloPlugin plugin;
    private final List<ItemStack> kit = new ArrayList<>();

    public KitManager(OptiSoloPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        kit.clear();
        for (String line : plugin.getConfig().getStringList("starter-kit")) {
            try {
                String[] parts = line.split(":");
                Material mat = Material.matchMaterial(parts[0].trim().toUpperCase());
                if (mat == null || !mat.isItem()) {
                    plugin.getLogger().warning("Bad kit item: " + line);
                    continue;
                }
                int amount = 1;
                if (parts.length > 1) {
                    amount = Math.max(1, Math.min(64, Integer.parseInt(parts[1].trim())));
                }
                kit.add(new ItemStack(mat, amount));
            } catch (Exception e) {
                plugin.getLogger().warning("Bad kit item: " + line);
            }
        }
    }

    /** Выдача при создании мира (без кулдауна, фиксируем время). */
    public void giveStarterKit(Player player) {
        give(player);
        plugin.getPlayerStore().setKitLastClaim(player.getUniqueId(), System.currentTimeMillis());
    }

    /** Повторная выдача командой /solo kit (только в соло-мирах, чтобы не влиять на выживание). */
    public void claimKit(Player player) {
        Settings settings = plugin.getSettings();
        Messages msg = plugin.getMessages();
        if (settings.isInventorySeparation() && plugin.getWorldManager().getData(player.getWorld()) == null) {
            msg.send(player, "kit-only-solo");
            return;
        }
        long last = plugin.getPlayerStore().getKitLastClaim(player.getUniqueId());

        if (settings.isKitOnce() && last > 0) {
            msg.send(player, "kit-once");
            return;
        }
        long cooldown = settings.getKitCooldownMinutes() * 60L * 1000L;
        long left = (last + cooldown) - System.currentTimeMillis();
        if (last > 0 && left > 0) {
            msg.send(player, "kit-cooldown", "time", msg.formatDuration(left));
            return;
        }
        if (kit.isEmpty()) {
            msg.send(player, "kit-empty");
            return;
        }
        give(player);
        plugin.getPlayerStore().setKitLastClaim(player.getUniqueId(), System.currentTimeMillis());
        msg.send(player, "kit-given");
    }

    private void give(Player player) {
        if (kit.isEmpty()) {
            plugin.getMessages().send(player, "kit-empty");
            return;
        }
        List<ItemStack> copy = new ArrayList<>();
        for (ItemStack item : kit) copy.add(item.clone());
        Map<Integer, ItemStack> leftover = new HashMap<>();
        for (ItemStack item : copy) {
            leftover.putAll(player.getInventory().addItem(item));
        }
        for (ItemStack drop : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), drop);
        }
    }
}
