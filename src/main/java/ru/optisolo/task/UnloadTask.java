package ru.optisolo.task;

import org.bukkit.scheduler.BukkitRunnable;
import ru.optisolo.OptiSoloPlugin;

/** Раз в минуту выгружает пустые соло-миры из памяти. */
public class UnloadTask extends BukkitRunnable {

    private final OptiSoloPlugin plugin;

    public UnloadTask(OptiSoloPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        try {
            plugin.getWorldManager().checkUnloads();
        } catch (Exception e) {
            plugin.getLogger().warning("Unload task error: " + e.getMessage());
        }
    }
}
