package ru.optisolo;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import ru.optisolo.command.SoloCommand;
import ru.optisolo.config.Messages;
import ru.optisolo.config.Settings;
import ru.optisolo.data.DataManager;
import ru.optisolo.data.PlayerStore;
import ru.optisolo.gui.ChatInputManager;
import ru.optisolo.gui.MenuManager;
import ru.optisolo.kit.KitManager;
import ru.optisolo.listener.ChatListener;
import ru.optisolo.listener.MenuListener;
import ru.optisolo.listener.ProtectionListener;
import ru.optisolo.listener.WorldLifecycleListener;
import ru.optisolo.report.ReportManager;
import ru.optisolo.world.SoloWorldManager;

/**
 * OptiSolo — персональные миры 10k x 10k для Paper 1.18.2.
 * Максимально изолирован от обычной игры: трогает только свои миры.
 */
public class OptiSoloPlugin extends JavaPlugin {

    private static OptiSoloPlugin instance;

    private Settings settings;
    private Messages messages;
    private DataManager dataManager;
    private PlayerStore playerStore;
    private SoloWorldManager worldManager;
    private KitManager kitManager;
    private ReportManager reportManager;
    private MenuManager menuManager;
    private ChatInputManager chatInputManager;

    public static OptiSoloPlugin getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();

        messages = new Messages(this);
        messages.load();

        settings = new Settings(this);
        settings.load();

        dataManager = new DataManager(this);
        dataManager.loadAll();

        playerStore = new PlayerStore(this);
        kitManager = new KitManager(this);
        kitManager.load();
        reportManager = new ReportManager(this);
        reportManager.load();

        chatInputManager = new ChatInputManager(this);
        worldManager = new SoloWorldManager(this);
        menuManager = new MenuManager(this);

        SoloCommand command = new SoloCommand(this);
        if (getCommand("solo") != null) {
            getCommand("solo").setExecutor(command);
            getCommand("solo").setTabCompleter(command);
        }

        Bukkit.getPluginManager().registerEvents(new ProtectionListener(this), this);
        Bukkit.getPluginManager().registerEvents(new MenuListener(this), this);
        Bukkit.getPluginManager().registerEvents(new WorldLifecycleListener(this), this);
        Bukkit.getPluginManager().registerEvents(new ChatListener(this), this);

        worldManager.startUnloadTask();

        getLogger().info("OptiSolo enabled. Worlds data: " + dataManager.getAll().size());
    }

    @Override
    public void onDisable() {
        try {
            // Тихо сохраняем данные игроков, которые сейчас в соло-мирах.
            if (playerStore != null && worldManager != null && settings != null && settings.isInventorySeparation()) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    try {
                        boolean inSolo = worldManager.isSoloWorld(player.getWorld());
                        playerStore.saveCurrent(player, inSolo);
                    } catch (Exception ignored) {
                    }
                }
            }
            if (dataManager != null) {
                dataManager.saveAll();
            }
            if (reportManager != null) {
                reportManager.save();
            }
        } catch (Exception e) {
            getLogger().warning("Error while disabling: " + e.getMessage());
        }
        getLogger().info("OptiSolo disabled.");
    }

    /** Перезагрузка конфигов без рестарта сервера. */
    public void reloadAll() {
        reloadConfig();
        settings.load();
        messages.load();
        kitManager.load();
    }

    public Settings getSettings() {
        return settings;
    }

    public Messages getMessages() {
        return messages;
    }

    public DataManager getDataManager() {
        return dataManager;
    }

    public PlayerStore getPlayerStore() {
        return playerStore;
    }

    public SoloWorldManager getWorldManager() {
        return worldManager;
    }

    public KitManager getKitManager() {
        return kitManager;
    }

    public ReportManager getReportManager() {
        return reportManager;
    }

    public MenuManager getMenuManager() {
        return menuManager;
    }

    public ChatInputManager getChatInputManager() {
        return chatInputManager;
    }
}
