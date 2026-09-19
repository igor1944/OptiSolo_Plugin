package ru.optisolo.data;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.optisolo.OptiSoloPlugin;
import ru.optisolo.util.ItemSerializer;

import java.io.File;
import java.util.UUID;

/**
 * Раздельные данные игрока: секция "main" (обычная игра) и "solo" (соло-миры).
 * Сохраняет инвентарь, броню, эндер-сундук, опыт, здоровье, еду и режим игры.
 */
public class PlayerStore {

    private final OptiSoloPlugin plugin;
    private final File folder;

    public PlayerStore(OptiSoloPlugin plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "players");
        if (!folder.exists()) folder.mkdirs();
    }

    private File file(UUID uuid) {
        return new File(folder, uuid + ".yml");
    }

    private YamlConfiguration load(UUID uuid) {
        return YamlConfiguration.loadConfiguration(file(uuid));
    }

    private void save(UUID uuid, YamlConfiguration cfg) {
        try {
            cfg.save(file(uuid));
        } catch (Exception e) {
            plugin.getLogger().warning("Cannot save player data " + uuid + ": " + e.getMessage());
        }
    }

    /** Сохранить текущее состояние игрока в секцию (main/solo). Вызывать в главном потоке. */
    public void saveSection(Player player, String section) {
        try {
            YamlConfiguration cfg = load(player.getUniqueId());
            String p = section + ".";
            cfg.set(p + "inventory", ItemSerializer.toBase64(player.getInventory().getContents()));
            cfg.set(p + "armor", ItemSerializer.toBase64(player.getInventory().getArmorContents()));
            cfg.set(p + "ender", ItemSerializer.toBase64(player.getEnderChest().getContents()));
            cfg.set(p + "level", player.getLevel());
            cfg.set(p + "exp", (double) player.getExp());
            cfg.set(p + "health", player.getHealth());
            cfg.set(p + "food", player.getFoodLevel());
            cfg.set(p + "saturation", (double) player.getSaturation());
            cfg.set(p + "gamemode", player.getGameMode().name());
            save(player.getUniqueId(), cfg);
        } catch (Exception e) {
            plugin.getLogger().warning("Cannot save " + section + " section for " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Загрузить секцию. Если данных нет: для solo — выдать чистое состояние,
     * для main — ничего не трогать (никогда не вайпаем обычную игру).
     */
    public void loadSection(Player player, String section) {
        try {
            YamlConfiguration cfg = load(player.getUniqueId());
            ConfigurationSection sec = cfg.getConfigurationSection(section);
            if (sec == null || !sec.contains("inventory")) {
                if (section.equals("solo")) {
                    clearToSoloDefault(player);
                }
                return;
            }
            applySection(player, sec, section.equals("solo"));
        } catch (Exception e) {
            plugin.getLogger().warning("Cannot load " + section + " section for " + player.getName() + ": " + e.getMessage());
            // При битой solo-секции чистим (там лежат чужие main-предметы), main не трогаем.
            if (section.equals("solo")) {
                clearToSoloDefault(player);
            }
        }
    }

    private void applySection(Player player, ConfigurationSection sec, boolean isSolo) throws Exception {
        ItemStack[] contents = ItemSerializer.fromBase64(sec.getString("inventory", ""));
        ItemStack[] armor = ItemSerializer.fromBase64(sec.getString("armor", ""));
        ItemStack[] ender = ItemSerializer.fromBase64(sec.getString("ender", ""));

        player.getInventory().clear();
        for (int i = 0; i < contents.length && i < player.getInventory().getSize(); i++) {
            if (contents[i] != null) player.getInventory().setItem(i, contents[i]);
        }
        ItemStack[] fixedArmor = new ItemStack[4];
        for (int i = 0; i < armor.length && i < 4; i++) fixedArmor[i] = armor[i];
        player.getInventory().setArmorContents(fixedArmor);

        player.getEnderChest().clear();
        for (int i = 0; i < ender.length && i < player.getEnderChest().getSize(); i++) {
            if (ender[i] != null) player.getEnderChest().setItem(i, ender[i]);
        }

        player.setLevel(Math.max(0, sec.getInt("level", 0)));
        player.setExp((float) Math.min(1.0, Math.max(0.0, sec.getDouble("exp", 0.0))));

        double health = sec.getDouble("health", 20.0);
        if (health <= 0 || health > 20) health = 20;
        try {
            player.setHealth(health);
        } catch (Exception ignored) {
        }
        player.setFoodLevel(Math.min(20, Math.max(0, sec.getInt("food", 20))));
        try {
            player.setSaturation((float) Math.max(0, sec.getDouble("saturation", 5.0)));
        } catch (Exception ignored) {
        }
        String gm = sec.getString("gamemode", isSolo ? plugin.getSettings().getDefaultGamemodeSolo() : "SURVIVAL");
        try {
            player.setGameMode(GameMode.valueOf(gm.toUpperCase()));
        } catch (IllegalArgumentException e) {
            player.setGameMode(GameMode.SURVIVAL);
        }
        player.setFireTicks(0);
        player.setFallDistance(0);
    }

    /** Чистое состояние для первого входа в соло-миры. */
    public void clearToSoloDefault(Player player) {
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        player.getEnderChest().clear();
        player.setLevel(0);
        player.setExp(0);
        try {
            player.setHealth(20);
        } catch (Exception ignored) {
        }
        player.setFoodLevel(20);
        try {
            player.setSaturation(5f);
        } catch (Exception ignored) {
        }
        try {
            player.setGameMode(GameMode.valueOf(plugin.getSettings().getDefaultGamemodeSolo()));
        } catch (IllegalArgumentException e) {
            player.setGameMode(GameMode.SURVIVAL);
        }
        player.setFireTicks(0);
        player.setFallDistance(0);
        player.getActivePotionEffects().forEach(e -> player.removePotionEffect(e.getType()));
    }

    /** Сохранить текущую секцию в зависимости от того, где игрок. */
    public void saveCurrent(Player player, boolean inSolo) {
        saveSection(player, inSolo ? "solo" : "main");
        setLastLocation(player);
    }

    // ---------- последняя позиция ----------

    public void setLastLocation(Player player) {
        try {
            YamlConfiguration cfg = load(player.getUniqueId());
            Location loc = player.getLocation();
            cfg.set("last.world", loc.getWorld() == null ? "" : loc.getWorld().getName());
            cfg.set("last.x", loc.getX());
            cfg.set("last.y", loc.getY());
            cfg.set("last.z", loc.getZ());
            cfg.set("last.yaw", (double) loc.getYaw());
            cfg.set("last.pitch", (double) loc.getPitch());
            cfg.set("clean-quit", true);
            save(player.getUniqueId(), cfg);
        } catch (Exception e) {
            plugin.getLogger().warning("Cannot save last location for " + player.getName());
        }
    }

    public String getLastWorldName(UUID uuid) {
        YamlConfiguration cfg = load(uuid);
        return cfg.getString("last.world", "");
    }

    public Location getLastLocation(UUID uuid) {
        YamlConfiguration cfg = load(uuid);
        String worldName = cfg.getString("last.world", "");
        if (worldName.isEmpty()) return null;
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        return new Location(world,
                cfg.getDouble("last.x", 0.5),
                cfg.getDouble("last.y", 64),
                cfg.getDouble("last.z", 0.5),
                (float) cfg.getDouble("last.yaw", 0),
                (float) cfg.getDouble("last.pitch", 0));
    }

    public boolean wasCleanQuit(UUID uuid) {
        return load(uuid).getBoolean("clean-quit", false);
    }

    public void setCleanQuit(UUID uuid, boolean clean) {
        YamlConfiguration cfg = load(uuid);
        cfg.set("clean-quit", clean);
        save(uuid, cfg);
    }

    // ---------- кулдаун кита ----------

    public long getKitLastClaim(UUID uuid) {
        return load(uuid).getLong("kit-last-claim", 0L);
    }

    public void setKitLastClaim(UUID uuid, long time) {
        YamlConfiguration cfg = load(uuid);
        cfg.set("kit-last-claim", time);
        save(uuid, cfg);
    }
}
