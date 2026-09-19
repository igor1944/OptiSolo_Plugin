package ru.optisolo.data;

import org.bukkit.configuration.file.YamlConfiguration;
import ru.optisolo.OptiSoloPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Хранилище метаданных миров. Сами миры грузятся лениво, только метаданные в памяти. */
public class DataManager {

    private final OptiSoloPlugin plugin;
    private final File folder;
    private final Map<UUID, SoloWorldData> byOwner = new LinkedHashMap<>();
    private final Map<String, UUID> byWorldName = new LinkedHashMap<>();

    public DataManager(OptiSoloPlugin plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "worlds");
        if (!folder.exists()) folder.mkdirs();
    }

    public void loadAll() {
        byOwner.clear();
        byWorldName.clear();
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return;
        for (File f : files) {
            try {
                SoloWorldData data = SoloWorldData.loadFrom(YamlConfiguration.loadConfiguration(f));
                if (data != null) {
                    byOwner.put(data.getOwnerUuid(), data);
                    byWorldName.put(data.getWorldName().toLowerCase(), data.getOwnerUuid());
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Cannot load " + f.getName() + ": " + e.getMessage());
            }
        }
    }

    public void save(SoloWorldData data) {
        try {
            YamlConfiguration cfg = new YamlConfiguration();
            data.saveTo(cfg);
            cfg.save(new File(folder, data.getOwnerUuid() + ".yml"));
        } catch (Exception e) {
            plugin.getLogger().warning("Cannot save world data " + data.getWorldName() + ": " + e.getMessage());
        }
    }

    public void saveAll() {
        for (SoloWorldData data : byOwner.values()) {
            save(data);
        }
    }

    public SoloWorldData create(UUID owner, String ownerName, String worldName, String worldType) {
        SoloWorldData data = new SoloWorldData(owner, ownerName, worldName, worldType);
        data.setDifficulty(plugin.getSettings().getDefaultDifficulty());
        byOwner.put(owner, data);
        byWorldName.put(worldName.toLowerCase(), owner);
        save(data);
        return data;
    }

    public void delete(SoloWorldData data) {
        byOwner.remove(data.getOwnerUuid());
        byWorldName.remove(data.getWorldName().toLowerCase());
        File f = new File(folder, data.getOwnerUuid() + ".yml");
        if (f.exists()) f.delete();
    }

    public SoloWorldData findByOwner(UUID owner) {
        return byOwner.get(owner);
    }

    public SoloWorldData findByWorld(String worldName) {
        if (worldName == null) return null;
        UUID owner = byWorldName.get(worldName.toLowerCase());
        return owner == null ? null : byOwner.get(owner);
    }

    public SoloWorldData findByOwnerName(String name) {
        if (name == null) return null;
        for (SoloWorldData data : byOwner.values()) {
            if (data.getOwnerName() != null && data.getOwnerName().equalsIgnoreCase(name)) {
                return data;
            }
        }
        return null;
    }

    /** Все миры, куда у игрока есть активное приглашение. */
    public List<SoloWorldData> findInvitesFor(UUID uuid) {
        List<SoloWorldData> out = new ArrayList<>();
        for (SoloWorldData data : byOwner.values()) {
            if (data.hasInvite(uuid)) out.add(data);
        }
        return out;
    }

    public Collection<SoloWorldData> getAll() {
        return byOwner.values();
    }
}
