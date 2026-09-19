package ru.optisolo.data;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Данные одного персонального мира: владелец, доступы, приглашения, точки телепорта. */
public class SoloWorldData {

    private final UUID ownerUuid;
    private String ownerName;
    private final String worldName;
    private final String worldType;
    private long createdAt;
    private boolean isPublic;
    private String difficulty;
    private boolean pvp;

    private final Set<UUID> trusted = new LinkedHashSet<>();
    private final Map<UUID, String> trustedNames = new LinkedHashMap<>();
    private final Map<UUID, Long> invites = new LinkedHashMap<>(); // uuid -> expiry millis
    private final Map<String, HomePoint> homes = new LinkedHashMap<>(); // lower-name -> point

    public SoloWorldData(UUID ownerUuid, String ownerName, String worldName, String worldType) {
        this.ownerUuid = ownerUuid;
        this.ownerName = ownerName;
        this.worldName = worldName;
        this.worldType = worldType;
        this.createdAt = System.currentTimeMillis();
        this.isPublic = false;
        this.difficulty = "NORMAL";
        this.pvp = true;
    }

    // ---------- доступ ----------

    public boolean isOwner(UUID uuid) {
        return ownerUuid.equals(uuid);
    }

    public boolean isTrusted(UUID uuid) {
        return trusted.contains(uuid);
    }

    public boolean hasBuildAccess(UUID uuid) {
        return isOwner(uuid) || isTrusted(uuid);
    }

    public void addTrusted(UUID uuid, String name) {
        trusted.add(uuid);
        if (name != null) trustedNames.put(uuid, name);
    }

    public boolean removeTrusted(UUID uuid) {
        trustedNames.remove(uuid);
        return trusted.remove(uuid);
    }

    // ---------- приглашения ----------

    public void addInvite(UUID uuid, long durationMillis) {
        invites.put(uuid, System.currentTimeMillis() + durationMillis);
    }

    public boolean revokeInvite(UUID uuid) {
        return invites.remove(uuid) != null;
    }

    public boolean hasInvite(UUID uuid) {
        Long exp = invites.get(uuid);
        if (exp == null) return false;
        if (exp < System.currentTimeMillis()) {
            invites.remove(uuid);
            return false;
        }
        return true;
    }

    public void cleanupInvites() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Long>> it = invites.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue() < now) it.remove();
        }
    }

    // ---------- точки телепорта ----------

    public void setHome(String name, HomePoint point) {
        homes.put(name.toLowerCase(), point);
    }

    public HomePoint getHome(String name) {
        if (name == null) return null;
        return homes.get(name.toLowerCase());
    }

    public boolean removeHome(String name) {
        if (name == null) return false;
        return homes.remove(name.toLowerCase()) != null;
    }

    // ---------- сериализация ----------

    public void saveTo(YamlConfiguration cfg) {
        cfg.set("owner-uuid", ownerUuid.toString());
        cfg.set("owner-name", ownerName == null ? "unknown" : ownerName);
        cfg.set("world-name", worldName);
        cfg.set("world-type", worldType);
        cfg.set("created-at", createdAt);
        cfg.set("public", isPublic);
        cfg.set("difficulty", difficulty);
        cfg.set("pvp", pvp);

        List<String> trustedList = new ArrayList<>();
        for (UUID id : trusted) trustedList.add(id.toString());
        cfg.set("trusted", trustedList);

        for (Map.Entry<UUID, String> e : trustedNames.entrySet()) {
            cfg.set("trusted-names." + e.getKey(), e.getValue());
        }
        for (Map.Entry<UUID, Long> e : invites.entrySet()) {
            cfg.set("invites." + e.getKey(), e.getValue());
        }
        for (Map.Entry<String, HomePoint> e : homes.entrySet()) {
            String path = "homes." + e.getKey() + ".";
            HomePoint h = e.getValue();
            cfg.set(path + "x", h.x);
            cfg.set(path + "y", h.y);
            cfg.set(path + "z", h.z);
            cfg.set(path + "yaw", h.yaw);
            cfg.set(path + "pitch", h.pitch);
        }
    }

    public static SoloWorldData loadFrom(YamlConfiguration cfg) {
        try {
            UUID owner = UUID.fromString(cfg.getString("owner-uuid", ""));
            String worldName = cfg.getString("world-name", "");
            if (worldName.isEmpty()) return null;
            SoloWorldData data = new SoloWorldData(
                    owner,
                    cfg.getString("owner-name", "unknown"),
                    worldName,
                    cfg.getString("world-type", "NORMAL")
            );
            data.createdAt = cfg.getLong("created-at", System.currentTimeMillis());
            data.isPublic = cfg.getBoolean("public", false);
            data.difficulty = cfg.getString("difficulty", "NORMAL");
            data.pvp = cfg.getBoolean("pvp", true);

            for (String s : cfg.getStringList("trusted")) {
                try {
                    data.trusted.add(UUID.fromString(s));
                } catch (IllegalArgumentException ignored) {
                }
            }
            ConfigurationSection names = cfg.getConfigurationSection("trusted-names");
            if (names != null) {
                for (String key : names.getKeys(false)) {
                    try {
                        data.trustedNames.put(UUID.fromString(key), names.getString(key, key));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
            ConfigurationSection inv = cfg.getConfigurationSection("invites");
            if (inv != null) {
                for (String key : inv.getKeys(false)) {
                    try {
                        data.invites.put(UUID.fromString(key), inv.getLong(key, 0L));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
            ConfigurationSection homes = cfg.getConfigurationSection("homes");
            if (homes != null) {
                for (String key : homes.getKeys(false)) {
                    ConfigurationSection h = homes.getConfigurationSection(key);
                    if (h == null) continue;
                    data.homes.put(key.toLowerCase(), new HomePoint(
                            key,
                            h.getDouble("x", 0.5),
                            h.getDouble("y", 64),
                            h.getDouble("z", 0.5),
                            (float) h.getDouble("yaw", 0),
                            (float) h.getDouble("pitch", 0)
                    ));
                }
            }
            return data;
        } catch (Exception e) {
            return null;
        }
    }

    // ---------- геттеры/сеттеры ----------

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public String getWorldName() {
        return worldName;
    }

    public String getWorldType() {
        return worldType;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public boolean isPublic() {
        return isPublic;
    }

    public void setPublic(boolean isPublic) {
        this.isPublic = isPublic;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }

    public boolean isPvp() {
        return pvp;
    }

    public void setPvp(boolean pvp) {
        this.pvp = pvp;
    }

    public Set<UUID> getTrusted() {
        return trusted;
    }

    public String getTrustedName(UUID uuid) {
        String n = trustedNames.get(uuid);
        return n == null ? uuid.toString().substring(0, 8) : n;
    }

    public Map<UUID, Long> getInvites() {
        return invites;
    }

    public Map<String, HomePoint> getHomes() {
        return homes;
    }

    /** Точка телепорта внутри соло-мира. */
    public static class HomePoint {
        public final String name;
        public final double x, y, z;
        public final float yaw, pitch;

        public HomePoint(String name, double x, double y, double z, float yaw, float pitch) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }
}
