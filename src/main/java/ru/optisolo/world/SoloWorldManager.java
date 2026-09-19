package ru.optisolo.world;

import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import ru.optisolo.OptiSoloPlugin;
import ru.optisolo.config.Messages;
import ru.optisolo.config.Settings;
import ru.optisolo.data.DataManager;
import ru.optisolo.data.SoloWorldData;
import ru.optisolo.task.UnloadTask;
import ru.optisolo.util.Text;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Создание, загрузка/выгрузка и доступы к соло-мирам.
 * Инвентари НЕ трогает: обмен секций делает WorldLifecycleListener по смене мира.
 */
public class SoloWorldManager {

    private final OptiSoloPlugin plugin;
    private final Settings settings;
    private final Messages msg;
    private final DataManager dataManager;

    /** Когда мир стал пустым (имя в нижнем регистре -> время). */
    private final Map<String, Long> emptySince = new HashMap<>();
    /** Защита от двойного создания. */
    private final Set<UUID> creating = new HashSet<>();

    public SoloWorldManager(OptiSoloPlugin plugin) {
        this.plugin = plugin;
        this.settings = plugin.getSettings();
        this.msg = plugin.getMessages();
        this.dataManager = plugin.getDataManager();
    }

    // ================= имена и проверки =================

    public static String soloWorldName(UUID owner) {
        return "solo_" + owner.toString();
    }

    /** Строгая проверка: мир наш, только если есть запись в данных. Обычную игру не трогаем. */
    public boolean isSoloWorld(World world) {
        return world != null && dataManager.findByWorld(world.getName()) != null;
    }

    public boolean isSoloWorldName(String name) {
        return name != null && dataManager.findByWorld(name) != null;
    }

    public SoloWorldData getData(World world) {
        return world == null ? null : dataManager.findByWorld(world.getName());
    }

    public boolean hasWorld(UUID owner) {
        return dataManager.findByOwner(owner) != null;
    }

    public boolean isAdmin(Player player) {
        return player.hasPermission("optisolo.admin");
    }

    /** Полный доступ к строительству: владелец, доверенный или админ с bypass. */
    public boolean hasBuildAccess(Player player, SoloWorldData data) {
        if (data == null) return true; // не наш мир — не мешаем
        if (data.hasBuildAccess(player.getUniqueId())) return true;
        return settings.isAdminBypass() && isAdmin(player);
    }

    /** Может ли игрок находиться в мире (владелец/доверенный/админ/открытый/приглашённый). */
    public boolean canEnter(Player player, SoloWorldData data) {
        if (data == null) return false;
        if (data.hasBuildAccess(player.getUniqueId())) return true;
        if (isAdmin(player)) return true;
        if (data.isPublic()) return true;
        return data.hasInvite(player.getUniqueId());
    }

    // ================= создание =================

    public void createWorld(Player owner, String typeInput) {
        if (dataManager.findByOwner(owner.getUniqueId()) != null) {
            msg.send(owner, "world-exists");
            return;
        }
        if (!creating.add(owner.getUniqueId())) {
            msg.send(owner, "world-create-busy");
            return;
        }
        try {
            String type = typeInput == null ? settings.getDefaultWorldType() : typeInput.toUpperCase();
            if (!Text.validWorldType(type)) {
                msg.send(owner, "bad-type");
                return;
            }
            String worldName = soloWorldName(owner.getUniqueId());
            if (Bukkit.getWorld(worldName) != null) {
                // Папка уже есть (остаток после удаления данных) — просто привязываем.
                SoloWorldData data = dataManager.create(owner.getUniqueId(), owner.getName(), worldName, type);
                applyRuntimeSettings(Bukkit.getWorld(worldName), data);
                enterSoloWorld(owner, data);
                plugin.getKitManager().giveStarterKit(owner);
                msg.send(owner, "world-created");
                return;
            }

            SoloWorldData data = dataManager.create(owner.getUniqueId(), owner.getName(), worldName, type);

            WorldCreator creator = WorldCreator.name(worldName);
            creator.environment(World.Environment.NORMAL);
            if (type.equals("FLAT")) {
                creator.type(WorldType.FLAT);
            } else if (type.equals("VOID")) {
                creator.generator(new VoidGenerator());
            } else {
                creator.type(WorldType.NORMAL);
            }
            if (settings.getSeed() != 0) {
                creator.seed(settings.getSeed());
            }

            World world = Bukkit.createWorld(creator);
            if (world == null) {
                dataManager.delete(data);
                msg.send(owner, "world-create-fail");
                return;
            }
            applyRuntimeSettings(world, data);
            setupSpawn(world, type);
            dataManager.save(data);

            enterSoloWorld(owner, data);
            plugin.getKitManager().giveStarterKit(owner);
            msg.send(owner, "world-created");
            plugin.getLogger().info("Solo world created: " + worldName + " (" + type + ")");
        } finally {
            creating.remove(owner.getUniqueId());
        }
    }

    /** Загрузить мир по требованию (ленивая загрузка — основа оптимизации). */
    public World loadWorld(SoloWorldData data) {
        if (data == null) return null;
        World world = Bukkit.getWorld(data.getWorldName());
        if (world != null) {
            applyRuntimeSettings(world, data);
            return world;
        }
        WorldCreator creator = WorldCreator.name(data.getWorldName());
        creator.environment(World.Environment.NORMAL);
        if (data.getWorldType().equalsIgnoreCase("VOID")) {
            // Генератор нужен при каждой загрузке, иначе новые чанки будут обычными.
            creator.generator(new VoidGenerator());
        }
        try {
            world = Bukkit.createWorld(creator);
        } catch (Exception e) {
            plugin.getLogger().warning("Cannot load " + data.getWorldName() + ": " + e.getMessage());
            return null;
        }
        if (world != null) {
            applyRuntimeSettings(world, data);
            emptySince.remove(data.getWorldName().toLowerCase());
        }
        return world;
    }

    /** Настройки производительности и границы при каждой загрузке. */
    public void applyRuntimeSettings(World world, SoloWorldData data) {
        if (world == null) return;
        try {
            world.getWorldBorder().setCenter(0, 0);
            world.getWorldBorder().setSize(settings.getBorderSize());
            world.getWorldBorder().setWarningDistance(5);
        } catch (Exception ignored) {
        }
        try {
            world.setKeepSpawnInMemory(settings.isKeepSpawnInMemory());
        } catch (Exception ignored) {
        }
        try {
            world.setAutoSave(settings.isAutosave());
        } catch (Exception ignored) {
        }
        try {
            world.setViewDistance(settings.getViewDistance());
        } catch (Exception ignored) {
        }
        try {
            world.setSimulationDistance(settings.getSimulationDistance());
        } catch (Exception ignored) {
        }
        if (data != null) {
            try {
                world.setDifficulty(Difficulty.valueOf(data.getDifficulty().toUpperCase()));
            } catch (IllegalArgumentException ignored) {
            }
        }
        applyGamerules(world);
    }

    private void applyGamerules(World world) {
        for (Map.Entry<String, String> e : settings.getGamerules().entrySet()) {
            String key = e.getKey();
            String val = e.getValue();
            try {
                if (key.equalsIgnoreCase("randomTickSpeed")) {
                    world.setGameRule(GameRule.RANDOM_TICK_SPEED, Integer.parseInt(val));
                } else if (key.equalsIgnoreCase("spawnRadius")) {
                    world.setGameRule(GameRule.SPAWN_RADIUS, Integer.parseInt(val));
                } else {
                    setBooleanRule(world, key, val.equalsIgnoreCase("true"));
                }
            } catch (Exception ex) {
                plugin.getLogger().warning("Bad gamerule " + key + "=" + val + ": " + ex.getMessage());
            }
        }
    }

    private void setBooleanRule(World world, String key, boolean value) {
        if (key.equalsIgnoreCase("doMobSpawning")) world.setGameRule(GameRule.DO_MOB_SPAWNING, value);
        else if (key.equalsIgnoreCase("mobGriefing")) world.setGameRule(GameRule.MOB_GRIEFING, value);
        else if (key.equalsIgnoreCase("doDaylightCycle")) world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, value);
        else if (key.equalsIgnoreCase("doFireTick")) world.setGameRule(GameRule.DO_FIRE_TICK, value);
        else if (key.equalsIgnoreCase("keepInventory")) world.setGameRule(GameRule.KEEP_INVENTORY, value);
        else if (key.equalsIgnoreCase("doTileDrops")) world.setGameRule(GameRule.DO_TILE_DROPS, value);
        else if (key.equalsIgnoreCase("doEntityDrops")) world.setGameRule(GameRule.DO_ENTITY_DROPS, value);
        else if (key.equalsIgnoreCase("announceAdvancements")) world.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, value);
        else if (key.equalsIgnoreCase("doWeatherCycle")) world.setGameRule(GameRule.DO_WEATHER_CYCLE, value);
        else if (key.equalsIgnoreCase("doImmediateRespawn")) world.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, value);
        else plugin.getLogger().warning("Unknown gamerule: " + key);
    }

    private void setupSpawn(World world, String type) {
        try {
            if (type.equalsIgnoreCase("VOID")) {
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        world.getBlockAt(dx, 64, dz).setType(Material.STONE, false);
                    }
                }
                world.setSpawnLocation(0, 65, 0);
            } else {
                int y = world.getHighestBlockYAt(0, 0);
                world.setSpawnLocation(0, y + 1, 0);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Cannot setup spawn for " + world.getName() + ": " + e.getMessage());
        }
    }

    // ================= телепорты =================

    public Location getMainSpawn() {
        String mainName = settings.getMainWorldName();
        World main = null;
        if (mainName != null && !mainName.isEmpty()) {
            main = Bukkit.getWorld(mainName);
        }
        if (main == null) {
            List<World> worlds = Bukkit.getWorlds();
            main = worlds.isEmpty() ? null : worlds.get(0);
        }
        if (main == null) return null;
        return main.getSpawnLocation();
    }

    public void enterSoloWorld(Player player, SoloWorldData data) {
        World world = loadWorld(data);
        if (world == null) {
            msg.send(player, "teleport-fail");
            return;
        }
        Location spawn = world.getSpawnLocation();
        player.teleport(spawn, PlayerTeleportEvent.TeleportCause.PLUGIN);
        emptySince.remove(world.getName().toLowerCase());
    }

    public void teleportOwn(Player player) {
        SoloWorldData data = dataManager.findByOwner(player.getUniqueId());
        if (data == null) {
            msg.send(player, "no-world");
            return;
        }
        enterSoloWorld(player, data);
        msg.send(player, "teleport-own");
    }

    public void visit(Player player, String ownerName) {
        SoloWorldData data = dataManager.findByOwnerName(ownerName);
        if (data == null) {
            // Может ввели ник, который сменился: пробуем найти онлайн-игрока и его мир.
            Player online = Bukkit.getPlayerExact(ownerName);
            if (online != null) data = dataManager.findByOwner(online.getUniqueId());
        }
        if (data == null) {
            msg.send(player, "world-not-found", "target", ownerName);
            return;
        }
        if (!canEnter(player, data)) {
            msg.send(player, "visit-no-access", "player", player.getName());
            return;
        }
        OfflinePlayer owner = Bukkit.getOfflinePlayer(data.getOwnerUuid());
        boolean ownerOnline = owner != null && owner.isOnline();
        if (!ownerOnline && !settings.isAllowOfflineVisit()
                && !data.isOwner(player.getUniqueId()) && !data.isTrusted(player.getUniqueId())
                && !isAdmin(player)) {
            msg.send(player, "visit-owner-offline");
            return;
        }
        enterSoloWorld(player, data);
        msg.send(player, "teleport-visit", "owner", data.getOwnerName());
    }

    public void acceptInvite(Player player, String ownerName) {
        SoloWorldData data = dataManager.findByOwnerName(ownerName);
        if (data == null) {
            Player online = Bukkit.getPlayerExact(ownerName);
            if (online != null) data = dataManager.findByOwner(online.getUniqueId());
        }
        if (data == null) {
            msg.send(player, "world-not-found", "target", ownerName);
            return;
        }
        if (data.isOwner(player.getUniqueId()) || data.isTrusted(player.getUniqueId())
                || data.isPublic() || isAdmin(player)) {
            enterSoloWorld(player, data);
            msg.send(player, "teleport-visit", "owner", data.getOwnerName());
            return;
        }
        if (!data.hasInvite(player.getUniqueId())) {
            msg.send(player, "invite-no-pending", "owner", data.getOwnerName());
            return;
        }
        enterSoloWorld(player, data);
        msg.send(player, "teleport-visit", "owner", data.getOwnerName());
    }

    public void leaveToMain(Player player) {
        if (!isSoloWorld(player.getWorld())) {
            msg.send(player, "not-in-solo");
        }
        Location spawn = getMainSpawn();
        if (spawn == null) {
            msg.send(player, "teleport-fail");
            return;
        }
        player.teleport(spawn, PlayerTeleportEvent.TeleportCause.PLUGIN);
        msg.send(player, "left-world");
    }

    // ================= приглашения и доверенные =================

    @SuppressWarnings("deprecation")
    public OfflinePlayer findPlayer(String name) {
        if (name == null || name.isEmpty()) return null;
        Player exact = Bukkit.getPlayerExact(name);
        if (exact != null) return exact;
        Player partial = Bukkit.getPlayer(name);
        if (partial != null) return partial;
        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        if (offline != null && (offline.hasPlayedBefore() || offline.isOnline())) return offline;
        return null;
    }

    public void invitePlayer(Player owner, String targetName) {
        SoloWorldData data = dataManager.findByOwner(owner.getUniqueId());
        if (data == null) {
            msg.send(owner, "no-world");
            return;
        }
        OfflinePlayer target = findPlayer(targetName);
        if (target == null || target.getUniqueId() == null) {
            msg.send(owner, "player-not-found", "target", targetName);
            return;
        }
        if (target.getUniqueId().equals(owner.getUniqueId())) {
            msg.send(owner, "cannot-self");
            return;
        }
        if (data.hasInvite(target.getUniqueId())) {
            msg.send(owner, "already-invited", "target", safeName(target, targetName));
            return;
        }
        data.addInvite(target.getUniqueId(), settings.getInviteExpireMinutes() * 60L * 1000L);
        dataManager.save(data);
        msg.send(owner, "invite-sent", "target", safeName(target, targetName),
                "time", msg.formatDuration(settings.getInviteExpireMinutes() * 60L * 1000L));
        if (target.isOnline() && target.getPlayer() != null) {
            msg.send(target.getPlayer(), "invite-received", "owner", owner.getName());
        }
    }

    public void uninvitePlayer(Player owner, String targetName) {
        SoloWorldData data = dataManager.findByOwner(owner.getUniqueId());
        if (data == null) {
            msg.send(owner, "no-world");
            return;
        }
        OfflinePlayer target = findPlayer(targetName);
        if (target == null || target.getUniqueId() == null) {
            msg.send(owner, "player-not-found", "target", targetName);
            return;
        }
        if (!data.revokeInvite(target.getUniqueId())) {
            msg.send(owner, "invite-not-found", "target", safeName(target, targetName));
            return;
        }
        dataManager.save(data);
        msg.send(owner, "invite-revoked", "target", safeName(target, targetName));
    }

    public void trustPlayer(Player owner, String targetName) {
        SoloWorldData data = dataManager.findByOwner(owner.getUniqueId());
        if (data == null) {
            msg.send(owner, "no-world");
            return;
        }
        OfflinePlayer target = findPlayer(targetName);
        if (target == null || target.getUniqueId() == null) {
            msg.send(owner, "player-not-found", "target", targetName);
            return;
        }
        if (target.getUniqueId().equals(owner.getUniqueId())) {
            msg.send(owner, "cannot-self");
            return;
        }
        if (data.isTrusted(target.getUniqueId())) {
            msg.send(owner, "already-trusted", "target", safeName(target, targetName));
            return;
        }
        if (data.getTrusted().size() >= settings.getMaxTrusted()) {
            msg.send(owner, "trust-limit", "amount", settings.getMaxTrusted());
            return;
        }
        data.addTrusted(target.getUniqueId(), safeName(target, targetName));
        data.revokeInvite(target.getUniqueId());
        dataManager.save(data);
        msg.send(owner, "trust-added", "target", safeName(target, targetName));
        if (target.isOnline() && target.getPlayer() != null) {
            msg.send(target.getPlayer(), "trust-added-target", "owner", owner.getName());
        }
    }

    public void untrustPlayer(Player owner, String targetName) {
        SoloWorldData data = dataManager.findByOwner(owner.getUniqueId());
        if (data == null) {
            msg.send(owner, "no-world");
            return;
        }
        OfflinePlayer target = findPlayer(targetName);
        if (target == null || target.getUniqueId() == null) {
            msg.send(owner, "player-not-found", "target", targetName);
            return;
        }
        if (!data.removeTrusted(target.getUniqueId())) {
            msg.send(owner, "not-trusted", "target", safeName(target, targetName));
            return;
        }
        dataManager.save(data);
        msg.send(owner, "trust-removed", "target", safeName(target, targetName));
        if (target.isOnline() && target.getPlayer() != null) {
            msg.send(target.getPlayer(), "trust-removed-target", "owner", owner.getName());
        }
    }

    private String safeName(OfflinePlayer p, String fallback) {
        return p.getName() == null ? fallback : p.getName();
    }

    // ================= точки телепорта =================

    /** Мир для точек: если стоим в соло-мире — он, иначе свой. */
    private SoloWorldData resolveHomeWorld(Player player, boolean needBuildAccess) {
        SoloWorldData current = getData(player.getWorld());
        if (current != null) {
            if (needBuildAccess && !hasBuildAccess(player, current)) return null;
            if (!needBuildAccess && !canEnter(player, current)) return null;
            return current;
        }
        return dataManager.findByOwner(player.getUniqueId());
    }

    public void createHomePoint(Player player, String name) {
        if (!Text.validHomeName(name)) {
            msg.send(player, "home-invalid-name");
            return;
        }
        SoloWorldData data = resolveHomeWorld(player, true);
        if (data == null) {
            SoloWorldData current = getData(player.getWorld());
            if (current != null) msg.send(player, "visitor-denied");
            else msg.send(player, "no-world");
            return;
        }
        Location loc = player.getLocation();
        data.setHome(name, new SoloWorldData.HomePoint(name, loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch()));
        dataManager.save(data);
        msg.send(player, "home-saved", "name", name);
    }

    public void deleteHomePoint(Player player, String name) {
        SoloWorldData current = getData(player.getWorld());
        SoloWorldData data;
        if (current != null) {
            // В чужом мире удалять точки может только владелец.
            if (!current.isOwner(player.getUniqueId()) && !isAdmin(player)) {
                msg.send(player, "only-owner");
                return;
            }
            data = current;
        } else {
            data = dataManager.findByOwner(player.getUniqueId());
            if (data == null) {
                msg.send(player, "no-world");
                return;
            }
        }
        if (!data.removeHome(name)) {
            msg.send(player, "home-not-found", "name", name);
            return;
        }
        dataManager.save(data);
        msg.send(player, "home-deleted", "name", name);
    }

    public void teleportHome(Player player, String name) {
        SoloWorldData data = resolveHomeWorld(player, false);
        if (data == null) {
            SoloWorldData current = getData(player.getWorld());
            if (current != null) msg.send(player, "visit-no-access", "player", player.getName());
            else msg.send(player, "no-world");
            return;
        }
        SoloWorldData.HomePoint home = data.getHome(name);
        if (home == null) {
            msg.send(player, "home-not-found", "name", name);
            return;
        }
        World world = loadWorld(data);
        if (world == null) {
            msg.send(player, "teleport-fail");
            return;
        }
        player.teleport(new Location(world, home.x, home.y, home.z, home.yaw, home.pitch),
                PlayerTeleportEvent.TeleportCause.PLUGIN);
        msg.send(player, "home-teleported", "name", home.name);
    }

    // ================= настройки мира =================

    private SoloWorldData ownData(Player player) {
        SoloWorldData data = dataManager.findByOwner(player.getUniqueId());
        if (data == null) msg.send(player, "no-world");
        return data;
    }

    public void setTime(Player player, boolean day) {
        SoloWorldData data = ownData(player);
        if (data == null) return;
        World world = loadWorld(data);
        if (world == null) {
            msg.send(player, "teleport-fail");
            return;
        }
        world.setTime(day ? 1000 : 13000);
        msg.send(player, "time-set", "value", day ? "день" : "ночь");
    }

    public void setWeather(Player player, boolean clear) {
        SoloWorldData data = ownData(player);
        if (data == null) return;
        World world = loadWorld(data);
        if (world == null) {
            msg.send(player, "teleport-fail");
            return;
        }
        world.setStorm(!clear);
        world.setThundering(false);
        world.setWeatherDuration(6000);
        msg.send(player, "weather-set", "value", clear ? "ясно" : "дождь");
    }

    public void setDifficulty(Player player, String diff) {
        SoloWorldData data = ownData(player);
        if (data == null) return;
        Difficulty d;
        try {
            d = Difficulty.valueOf(diff.toUpperCase());
        } catch (IllegalArgumentException e) {
            msg.send(player, "bad-difficulty");
            return;
        }
        data.setDifficulty(d.name());
        dataManager.save(data);
        World world = Bukkit.getWorld(data.getWorldName());
        if (world != null) world.setDifficulty(d);
        msg.send(player, "difficulty-set", "value", d.name());
    }

    public void cycleDifficulty(Player player) {
        SoloWorldData data = ownData(player);
        if (data == null) return;
        Difficulty[] order = {Difficulty.PEACEFUL, Difficulty.EASY, Difficulty.NORMAL, Difficulty.HARD};
        Difficulty cur;
        try {
            cur = Difficulty.valueOf(data.getDifficulty().toUpperCase());
        } catch (IllegalArgumentException e) {
            cur = Difficulty.NORMAL;
        }
        setDifficulty(player, order[(cur.ordinal() + 1) % order.length].name());
    }

    public void togglePvp(Player player) {
        SoloWorldData data = ownData(player);
        if (data == null) return;
        data.setPvp(!data.isPvp());
        dataManager.save(data);
        msg.send(player, data.isPvp() ? "pvp-on" : "pvp-off");
    }

    public void setPublic(Player player, boolean isPublic) {
        SoloWorldData data = ownData(player);
        if (data == null) return;
        data.setPublic(isPublic);
        dataManager.save(data);
        msg.send(player, isPublic ? "public-on" : "public-off", "player", player.getName());
    }

    // ================= удаление =================

    public void deleteOwnWorld(Player player) {
        SoloWorldData data = dataManager.findByOwner(player.getUniqueId());
        if (data == null) {
            msg.send(player, "no-world");
            return;
        }
        deleteWorldData(player, data);
    }

    public void deleteWorldData(CommandSender deleter, SoloWorldData data) {
        String worldName = data.getWorldName();
        World world = Bukkit.getWorld(worldName);

        // Выводим всех игроков в обычный мир (инвентари поменяет слушатель смены мира).
        Location mainSpawn = getMainSpawn();
        if (world != null && mainSpawn != null) {
            for (Player occupant : new ArrayList<>(world.getPlayers())) {
                occupant.teleport(mainSpawn, PlayerTeleportEvent.TeleportCause.PLUGIN);
                msg.send(occupant, "world-deleted-tp");
            }
        }
        if (world != null) {
            Bukkit.unloadWorld(world, true);
        }
        emptySince.remove(worldName.toLowerCase());
        dataManager.delete(data);

        boolean ok = deleteWorldFolder(worldName);
        if (ok) {
            plugin.getLogger().info("Solo world deleted: " + worldName);
            if (deleter instanceof Player && data.isOwner(((Player) deleter).getUniqueId())) {
                msg.send(deleter, "delete-done");
            } else {
                msg.send(deleter, "admin-delete-done", "target", data.getOwnerName());
            }
        } else {
            msg.send(deleter, "delete-fail");
            plugin.getLogger().warning("Could not fully delete folder of " + worldName);
        }
    }

    private boolean deleteWorldFolder(String worldName) {
        boolean ok = true;
        File container = Bukkit.getWorldContainer();
        String[] folders = {worldName, worldName + "_nether", worldName + "_the_end"};
        for (String name : folders) {
            File dir = new File(container, name);
            if (dir.exists()) {
                ok &= deleteRecursive(dir);
            }
        }
        return ok;
    }

    private boolean deleteRecursive(File file) {
        boolean ok = true;
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                ok &= deleteRecursive(child);
            }
        }
        return file.delete() && ok;
    }

    // ================= выгрузка пустых (оптимизация) =================

    public void startUnloadTask() {
        new UnloadTask(plugin).runTaskTimer(plugin, 20L * 60, 20L * 60);
    }

    public void checkUnloads() {
        long now = System.currentTimeMillis();
        long timeout = settings.getUnloadMinutes() * 60L * 1000L;

        List<World> loaded = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            if (isSoloWorld(world)) loaded.add(world);
        }

        for (World world : loaded) {
            String key = world.getName().toLowerCase();
            if (!world.getPlayers().isEmpty()) {
                emptySince.remove(key);
                continue;
            }
            long since = emptySince.computeIfAbsent(key, k -> now);
            if (timeout > 0 && now - since >= timeout) {
                if (Bukkit.unloadWorld(world, true)) {
                    plugin.getLogger().info("Unloaded empty solo world: " + world.getName());
                }
                emptySince.remove(key);
            }
        }

        // Лимит одновременно загруженных: выгружаем давно пустые.
        List<World> stillLoaded = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            if (isSoloWorld(world)) stillLoaded.add(world);
        }
        if (stillLoaded.size() > settings.getMaxLoaded()) {
            stillLoaded.sort(Comparator.comparingLong(w -> emptySince.getOrDefault(w.getName().toLowerCase(), Long.MAX_VALUE)));
            for (World world : stillLoaded) {
                if (countLoadedSolo() <= settings.getMaxLoaded()) break;
                if (world.getPlayers().isEmpty() && Bukkit.unloadWorld(world, true)) {
                    plugin.getLogger().info("Unloaded solo world (limit): " + world.getName());
                    emptySince.remove(world.getName().toLowerCase());
                }
            }
        }
    }

    private int countLoadedSolo() {
        int n = 0;
        for (World world : Bukkit.getWorlds()) {
            if (isSoloWorld(world)) n++;
        }
        return n;
    }
}
