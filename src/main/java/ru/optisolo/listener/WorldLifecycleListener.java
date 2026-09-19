package ru.optisolo.listener;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import ru.optisolo.OptiSoloPlugin;
import ru.optisolo.config.Messages;
import ru.optisolo.config.Settings;
import ru.optisolo.data.DataManager;
import ru.optisolo.data.PlayerStore;
import ru.optisolo.data.SoloWorldData;
import ru.optisolo.world.SoloWorldManager;

import java.util.List;
import java.util.StringJoiner;

/**
 * Жизненный цикл: обмен инвентарей при смене мира, возврат игрока
 * в свой мир после рестарта, респаун и блокировка порталов.
 */
public class WorldLifecycleListener implements Listener {

    private final OptiSoloPlugin plugin;
    private final SoloWorldManager worlds;
    private final DataManager dataManager;
    private final PlayerStore playerStore;
    private final Settings settings;
    private final Messages msg;

    public WorldLifecycleListener(OptiSoloPlugin plugin) {
        this.plugin = plugin;
        this.worlds = plugin.getWorldManager();
        this.dataManager = plugin.getDataManager();
        this.playerStore = plugin.getPlayerStore();
        this.settings = plugin.getSettings();
        this.msg = plugin.getMessages();
    }

    // ---------- обмен инвентарей (единственное место!) ----------

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onWorldChange(PlayerChangedWorldEvent e) {
        if (!settings.isInventorySeparation()) return;
        Player player = e.getPlayer();
        boolean fromSolo = worlds.isSoloWorld(e.getFrom());
        boolean toSolo = worlds.isSoloWorld(player.getWorld());
        try {
            if (fromSolo && !toSolo) {
                // Вышел из соло: сохранить solo, вернуть main.
                playerStore.saveSection(player, "solo");
                playerStore.loadSection(player, "main");
            } else if (!fromSolo && toSolo) {
                // Вошёл в соло: сохранить main, вернуть solo.
                playerStore.saveSection(player, "main");
                playerStore.loadSection(player, "solo");
            }
            // solo -> solo и main -> main: ничего не делаем.
        } catch (Exception ex) {
            plugin.getLogger().warning("Inventory swap failed for " + player.getName() + ": " + ex.getMessage());
        }
    }

    // ---------- вход/выход ----------

    @EventHandler(priority = EventPriority.HIGH)
    public void onQuit(PlayerQuitEvent e) {
        Player player = e.getPlayer();
        if (!settings.isInventorySeparation()) {
            playerStore.setLastLocation(player);
            return;
        }
        try {
            if (worlds.isSoloWorld(player.getWorld())) {
                // Сохраняем solo и сразу возвращаем main,
                // чтобы player.dat остался с обычными вещами.
                playerStore.saveSection(player, "solo");
                playerStore.loadSection(player, "main");
            } else {
                playerStore.saveSection(player, "main");
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("Quit save failed for " + player.getName() + ": " + ex.getMessage());
        }
        playerStore.setLastLocation(player);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onJoin(PlayerJoinEvent e) {
        Player player = e.getPlayer();

        // Обновляем ник владельца (мог смениться).
        SoloWorldData own = dataManager.findByOwner(player.getUniqueId());
        if (own != null && !player.getName().equals(own.getOwnerName())) {
            own.setOwnerName(player.getName());
            dataManager.save(own);
        }

        // Показываем активные приглашения.
        List<SoloWorldData> invites = dataManager.findInvitesFor(player.getUniqueId());
        if (!invites.isEmpty()) {
            StringJoiner joiner = new StringJoiner(", ");
            for (SoloWorldData d : invites) joiner.add(d.getOwnerName());
            msg.send(player, "pending-invites", "value", joiner.toString());
        }

        if (!settings.isInventorySeparation()) return;

        // Возврат в соло-мир после рестарта (мир мог быть выгружен).
        String lastWorld = playerStore.getLastWorldName(player.getUniqueId());
        if (lastWorld == null || lastWorld.isEmpty()) return;
        final SoloWorldData lastData = dataManager.findByWorld(lastWorld);
        if (lastData == null) return;

        boolean cleanQuit = playerStore.wasCleanQuit(player.getUniqueId());
        playerStore.setCleanQuit(player.getUniqueId(), false);

        World loaded = worlds.loadWorld(lastData);
        if (loaded == null) return;

        if (worlds.isSoloWorld(player.getWorld())) {
            // Игрок сразу оказался в соло-мире, но вещи — main (мы вернули их при выходе).
            if (cleanQuit) {
                try {
                    playerStore.saveSection(player, "main");
                    playerStore.loadSection(player, "solo");
                } catch (Exception ex) {
                    plugin.getLogger().warning("Join fix failed for " + player.getName());
                }
            } else {
                // Крах сервера: вещи в руках — solo, просто пересохраняем.
                try {
                    playerStore.saveSection(player, "solo");
                } catch (Exception ignored) {
                }
            }
            return;
        }

        // Сервер выкинул игрока на спавн (мира не было при входе) — возвращаем.
        if (!cleanQuit) {
            // Крах: в руках solo-вещи, сначала нормализуем.
            try {
                playerStore.saveSection(player, "solo");
                playerStore.loadSection(player, "main");
            } catch (Exception ignored) {
            }
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            if (worlds.isSoloWorld(player.getWorld())) return;
            Location last = playerStore.getLastLocation(player.getUniqueId());
            if (last == null || last.getWorld() == null) {
                World w = Bukkit.getWorld(lastData.getWorldName());
                if (w == null) return;
                last = w.getSpawnLocation();
            }
            player.teleport(last);
        }, 10L);
    }

    // ---------- респаун в своём мире ----------

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent e) {
        Player player = e.getPlayer();
        SoloWorldData data = worlds.getData(player.getWorld());
        if (data == null) return;
        if (e.isBedSpawn() || e.isAnchorSpawn()) return;
        World world = Bukkit.getWorld(data.getWorldName());
        if (world != null) {
            e.setRespawnLocation(world.getSpawnLocation());
        }
    }

    // ---------- блокировка порталов ----------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent e) {
        if (!settings.isAllowNetherEnd() && worlds.isSoloWorld(e.getFrom().getWorld())) {
            e.setCancelled(true);
            msg.send(e.getPlayer(), "nether-blocked");
        }
    }
}
