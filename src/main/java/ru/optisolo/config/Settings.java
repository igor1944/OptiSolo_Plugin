package ru.optisolo.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import ru.optisolo.OptiSoloPlugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Все настройки из config.yml с безопасными значениями по умолчанию. */
public class Settings {

    private final OptiSoloPlugin plugin;

    private double borderSize;
    private String defaultWorldType;
    private long seed;
    private boolean keepSpawnInMemory;
    private boolean autosave;
    private int viewDistance;
    private int simulationDistance;
    private int unloadMinutes;
    private int maxLoaded;
    private boolean allowNetherEnd;
    private String defaultDifficulty;
    private Map<String, String> gamerules;

    private String mainWorldName;
    private boolean inventorySeparation;
    private String defaultGamemodeSolo;

    private boolean kitOnce;
    private int kitCooldownMinutes;

    private int inviteExpireMinutes;
    private boolean allowOfflineVisit;
    private int maxTrusted;

    private boolean visitorsCanPickup;
    private boolean visitorsCanDrop;
    private boolean visitorsCanPve;
    private boolean visitorsCanPvp;
    private boolean visitorsCanRide;
    private List<String> allowedInteractContains;

    private boolean reportsEnabled;
    private boolean reportsNotifyAdmins;

    private boolean adminBypass;
    private int denyCooldownSeconds;

    public Settings(OptiSoloPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        FileConfiguration c = plugin.getConfig();

        borderSize = c.getDouble("world.border-size", 10000.0);
        if (borderSize < 100) borderSize = 100;
        if (borderSize > 29999984) borderSize = 29999984;
        defaultWorldType = c.getString("world.default-type", "NORMAL").toUpperCase();
        seed = c.getLong("world.seed", 0L);
        keepSpawnInMemory = c.getBoolean("world.keep-spawn-in-memory", false);
        autosave = c.getBoolean("world.autosave", true);
        viewDistance = clamp(c.getInt("world.view-distance", 6), 2, 32);
        simulationDistance = clamp(c.getInt("world.simulation-distance", 5), 2, 32);
        unloadMinutes = Math.max(0, c.getInt("world.unload-after-minutes-empty", 5));
        maxLoaded = Math.max(1, c.getInt("world.max-loaded-worlds", 20));
        allowNetherEnd = c.getBoolean("world.allow-nether-end", false);
        defaultDifficulty = c.getString("world.default-difficulty", "NORMAL").toUpperCase();

        gamerules = new LinkedHashMap<>();
        ConfigurationSection gr = c.getConfigurationSection("world.gamerules");
        if (gr != null) {
            for (String key : gr.getKeys(false)) {
                gamerules.put(key, gr.getString(key, "true"));
            }
        }

        mainWorldName = c.getString("main-world-name", "");
        inventorySeparation = c.getBoolean("inventory-separation", true);
        defaultGamemodeSolo = c.getString("default-gamemode-solo", "SURVIVAL").toUpperCase();

        kitOnce = c.getBoolean("kit-once", false);
        kitCooldownMinutes = Math.max(0, c.getInt("kit-cooldown-minutes", 1440));

        inviteExpireMinutes = Math.max(1, c.getInt("invites.expire-minutes", 10));
        allowOfflineVisit = c.getBoolean("invites.allow-offline-owner-visit", true);
        maxTrusted = Math.max(1, c.getInt("invites.max-trusted", 10));

        visitorsCanPickup = c.getBoolean("visitors.can-pickup", false);
        visitorsCanDrop = c.getBoolean("visitors.can-drop", false);
        visitorsCanPve = c.getBoolean("visitors.can-pve", true);
        visitorsCanPvp = c.getBoolean("visitors.can-pvp", false);
        visitorsCanRide = c.getBoolean("visitors.can-ride", false);
        allowedInteractContains = new ArrayList<>();
        for (String s : c.getStringList("visitors.allowed-interact-contains")) {
            allowedInteractContains.add(s.toUpperCase());
        }
        if (allowedInteractContains.isEmpty()) {
            allowedInteractContains.add("_DOOR");
            allowedInteractContains.add("_TRAPDOOR");
            allowedInteractContains.add("_FENCE_GATE");
            allowedInteractContains.add("_BUTTON");
            allowedInteractContains.add("PRESSURE_PLATE");
        }

        reportsEnabled = c.getBoolean("reports.enabled", true);
        reportsNotifyAdmins = c.getBoolean("reports.notify-admins", true);

        adminBypass = c.getBoolean("admin-bypass", true);
        denyCooldownSeconds = Math.max(0, c.getInt("deny-cooldown-seconds", 3));
    }

    private int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    public double getBorderSize() {
        return borderSize;
    }

    public String getDefaultWorldType() {
        return defaultWorldType;
    }

    public long getSeed() {
        return seed;
    }

    public boolean isKeepSpawnInMemory() {
        return keepSpawnInMemory;
    }

    public boolean isAutosave() {
        return autosave;
    }

    public int getViewDistance() {
        return viewDistance;
    }

    public int getSimulationDistance() {
        return simulationDistance;
    }

    public int getUnloadMinutes() {
        return unloadMinutes;
    }

    public int getMaxLoaded() {
        return maxLoaded;
    }

    public boolean isAllowNetherEnd() {
        return allowNetherEnd;
    }

    public String getDefaultDifficulty() {
        return defaultDifficulty;
    }

    public Map<String, String> getGamerules() {
        return gamerules;
    }

    public String getMainWorldName() {
        return mainWorldName;
    }

    public boolean isInventorySeparation() {
        return inventorySeparation;
    }

    public String getDefaultGamemodeSolo() {
        return defaultGamemodeSolo;
    }

    public boolean isKitOnce() {
        return kitOnce;
    }

    public int getKitCooldownMinutes() {
        return kitCooldownMinutes;
    }

    public int getInviteExpireMinutes() {
        return inviteExpireMinutes;
    }

    public boolean isAllowOfflineVisit() {
        return allowOfflineVisit;
    }

    public int getMaxTrusted() {
        return maxTrusted;
    }

    public boolean isVisitorsCanPickup() {
        return visitorsCanPickup;
    }

    public boolean isVisitorsCanDrop() {
        return visitorsCanDrop;
    }

    public boolean isVisitorsCanPve() {
        return visitorsCanPve;
    }

    public boolean isVisitorsCanPvp() {
        return visitorsCanPvp;
    }

    public boolean isVisitorsCanRide() {
        return visitorsCanRide;
    }

    public List<String> getAllowedInteractContains() {
        return allowedInteractContains;
    }

    public boolean isReportsEnabled() {
        return reportsEnabled;
    }

    public boolean isReportsNotifyAdmins() {
        return reportsNotifyAdmins;
    }

    public boolean isAdminBypass() {
        return adminBypass;
    }

    public int getDenyCooldownSeconds() {
        return denyCooldownSeconds;
    }
}
