package ru.optisolo.report;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import ru.optisolo.OptiSoloPlugin;
import ru.optisolo.config.Messages;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Отдельная система жалоб (репортов) с GUI для игроков и админов. */
public class ReportManager {

    private final OptiSoloPlugin plugin;
    private final File file;
    private final Map<Integer, Report> reports = new LinkedHashMap<>();
    private int nextId = 1;

    public ReportManager(OptiSoloPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "reports.yml");
    }

    public void load() {
        reports.clear();
        if (!file.exists()) {
            nextId = 1;
            return;
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        nextId = Math.max(1, cfg.getInt("next-id", 1));
        ConfigurationSection sec = cfg.getConfigurationSection("reports");
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            try {
                int id = Integer.parseInt(key);
                ConfigurationSection r = sec.getConfigurationSection(key);
                if (r == null) continue;
                reports.put(id, new Report(
                        id,
                        UUID.fromString(r.getString("reporter-uuid", "")),
                        r.getString("reporter-name", "?"),
                        r.getString("reported", "?"),
                        r.getString("world", "?"),
                        r.getDouble("x", 0), r.getDouble("y", 64), r.getDouble("z", 0),
                        r.getString("reason", ""),
                        r.getLong("time", 0L),
                        r.getString("status", "OPEN")
                ));
            } catch (Exception ignored) {
            }
        }
    }

    public void save() {
        try {
            YamlConfiguration cfg = new YamlConfiguration();
            cfg.set("next-id", nextId);
            for (Report r : reports.values()) {
                String p = "reports." + r.id + ".";
                cfg.set(p + "reporter-uuid", r.reporterUuid.toString());
                cfg.set(p + "reporter-name", r.reporterName);
                cfg.set(p + "reported", r.reported);
                cfg.set(p + "world", r.world);
                cfg.set(p + "x", r.x);
                cfg.set(p + "y", r.y);
                cfg.set(p + "z", r.z);
                cfg.set(p + "reason", r.reason);
                cfg.set(p + "time", r.time);
                cfg.set(p + "status", r.status);
            }
            cfg.save(file);
        } catch (Exception e) {
            plugin.getLogger().warning("Cannot save reports: " + e.getMessage());
        }
    }

    public int createReport(Player reporter, String reported, String reason) {
        Location loc = reporter.getLocation();
        int id = nextId++;
        reports.put(id, new Report(
                id,
                reporter.getUniqueId(),
                reporter.getName(),
                reported,
                loc.getWorld() == null ? "?" : loc.getWorld().getName(),
                loc.getX(), loc.getY(), loc.getZ(),
                reason,
                System.currentTimeMillis(),
                "OPEN"
        ));
        save();

        Messages msg = plugin.getMessages();
        msg.send(reporter, "report-created", "id", id);
        if (plugin.getSettings().isReportsNotifyAdmins()) {
            for (Player admin : Bukkit.getOnlinePlayers()) {
                if (admin.hasPermission("optisolo.admin")) {
                    msg.send(admin, "report-admin-notify",
                            "id", id,
                            "reporter", reporter.getName(),
                            "reported", reported,
                            "reason", reason);
                }
            }
        }
        return id;
    }

    public boolean closeReport(int id) {
        Report r = reports.get(id);
        if (r == null) return false;
        r.status = "CLOSED";
        save();
        return true;
    }

    public boolean deleteReport(int id) {
        boolean removed = reports.remove(id) != null;
        if (removed) save();
        return removed;
    }

    public List<Report> getAllSorted() {
        List<Report> list = new ArrayList<>(reports.values());
        list.sort(Comparator.comparingInt(r -> r.id));
        Collections.reverse(list);
        return list;
    }

    public List<Report> getByReporter(UUID uuid) {
        List<Report> list = new ArrayList<>();
        for (Report r : reports.values()) {
            if (r.reporterUuid.equals(uuid)) list.add(r);
        }
        list.sort(Comparator.comparingInt(r -> r.id));
        Collections.reverse(list);
        return list;
    }

    public int countOpen() {
        int n = 0;
        for (Report r : reports.values()) {
            if (r.status.equals("OPEN")) n++;
        }
        return n;
    }

    public static class Report {
        public final int id;
        public final UUID reporterUuid;
        public final String reporterName;
        public final String reported;
        public final String world;
        public final double x, y, z;
        public final String reason;
        public final long time;
        public String status;

        public Report(int id, UUID reporterUuid, String reporterName, String reported,
                      String world, double x, double y, double z, String reason, long time, String status) {
            this.id = id;
            this.reporterUuid = reporterUuid;
            this.reporterName = reporterName;
            this.reported = reported;
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.reason = reason;
            this.time = time;
            this.status = status;
        }

        public String dateString() {
            return new SimpleDateFormat("dd.MM HH:mm").format(new Date(time));
        }
    }
}
