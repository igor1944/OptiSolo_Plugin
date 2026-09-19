package ru.optisolo.config;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.optisolo.OptiSoloPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Сообщения из messages.yml. Плейсхолдеры вида {name}. */
public class Messages {

    private final OptiSoloPlugin plugin;
    private YamlConfiguration config;

    public Messages(OptiSoloPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(file);
    }

    public String prefix() {
        return color(config.getString("prefix", "&8[&aOptiSolo&8] &r"));
    }

    /** Получить сообщение с подстановкой плейсхолдеров: tr("key", "name", value, ...). */
    public String tr(String key, Object... kv) {
        String raw = config.getString(key, key);
        raw = raw.replace("{prefix}", config.getString("prefix", ""));
        if (kv != null) {
            for (int i = 0; i + 1 < kv.length; i += 2) {
                String ph = "{" + kv[i] + "}";
                String val = kv[i + 1] == null ? "" : String.valueOf(kv[i + 1]);
                raw = raw.replace(ph, val);
            }
        }
        return color(raw);
    }

    public List<String> trList(String key, Object... kv) {
        List<String> out = new ArrayList<>();
        for (String line : config.getStringList(key)) {
            String s = line.replace("{prefix}", config.getString("prefix", ""));
            if (kv != null) {
                for (int i = 0; i + 1 < kv.length; i += 2) {
                    s = s.replace("{" + kv[i] + "}", kv[i + 1] == null ? "" : String.valueOf(kv[i + 1]));
                }
            }
            out.add(color(s));
        }
        return out;
    }

    public void send(CommandSender sender, String key, Object... kv) {
        sender.sendMessage(tr(key, kv));
    }

    public void sendList(CommandSender sender, String key, Object... kv) {
        for (String line : trList(key, kv)) {
            sender.sendMessage(line);
        }
    }

    public String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    /** "5 ч 12 мин" / "3 мин" / "10 сек" */
    public String formatDuration(long millis) {
        if (millis <= 0) return "0 сек";
        long totalSec = millis / 1000;
        long h = totalSec / 3600;
        long m = (totalSec % 3600) / 60;
        long s = totalSec % 60;
        if (h > 0) return h + " ч " + m + " мин";
        if (m > 0) return m + " мин " + s + " сек";
        return s + " сек";
    }
}
