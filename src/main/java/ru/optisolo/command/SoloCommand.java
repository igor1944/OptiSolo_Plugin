package ru.optisolo.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import ru.optisolo.OptiSoloPlugin;
import ru.optisolo.config.Messages;
import ru.optisolo.data.DataManager;
import ru.optisolo.data.SoloWorldData;
import ru.optisolo.world.SoloWorldManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** /solo — все подкоманды. */
public class SoloCommand implements TabExecutor {

    private final OptiSoloPlugin plugin;
    private final SoloWorldManager worlds;
    private final DataManager dataManager;
    private final Messages msg;

    private static final List<String> SUBS = Arrays.asList(
            "menu", "help", "create", "go", "tp", "home", "sethome", "delhome", "homes",
            "visit", "accept", "leave", "spawn", "invite", "uninvite", "trust", "untrust",
            "members", "kit", "time", "weather", "difficulty", "public", "private",
            "report", "reports", "delete", "admin", "reload", "reportclose", "reportdelete");

    public SoloCommand(OptiSoloPlugin plugin) {
        this.plugin = plugin;
        this.worlds = plugin.getWorldManager();
        this.dataManager = plugin.getDataManager();
        this.msg = plugin.getMessages();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("optisolo.use")) {
            msg.send(sender, "no-permission");
            return true;
        }

        // Консоль: только админ-команды.
        if (!(sender instanceof Player)) {
            return onConsole(sender, args);
        }
        Player player = (Player) sender;

        if (args.length == 0 || args[0].equalsIgnoreCase("menu")) {
            plugin.getMenuManager().openMain(player);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "help":
                plugin.getMenuManager().sendHelp(player);
                return true;
            case "create":
                worlds.createWorld(player, args.length > 1 ? args[1] : null);
                return true;
            case "go":
            case "tp":
                worlds.teleportOwn(player);
                return true;
            case "home":
                if (args.length > 1) worlds.teleportHome(player, args[1]);
                else worlds.teleportOwn(player);
                return true;
            case "sethome":
                if (args.length < 2) {
                    msg.send(player, "home-invalid-name");
                    return true;
                }
                worlds.createHomePoint(player, args[1]);
                return true;
            case "delhome":
                if (args.length < 2) {
                    msg.send(player, "home-not-found", "name", "?");
                    return true;
                }
                worlds.deleteHomePoint(player, args[1]);
                return true;
            case "homes":
                plugin.getMenuManager().openHomes(player, 0);
                return true;
            case "visit":
                if (args.length < 2) {
                    plugin.getChatInputManager().request(player,
                            ru.optisolo.gui.ChatInputManager.InputType.VISIT, null);
                    return true;
                }
                worlds.visit(player, args[1]);
                return true;
            case "accept":
                if (args.length < 2) {
                    msg.send(player, "invite-no-pending", "owner", "?");
                    return true;
                }
                worlds.acceptInvite(player, args[1]);
                return true;
            case "leave":
            case "spawn":
                worlds.leaveToMain(player);
                return true;
            case "invite":
                if (args.length < 2) {
                    plugin.getChatInputManager().request(player,
                            ru.optisolo.gui.ChatInputManager.InputType.INVITE, null);
                    return true;
                }
                worlds.invitePlayer(player, args[1]);
                return true;
            case "uninvite":
                if (args.length < 2) {
                    msg.send(player, "invite-not-found", "target", "?");
                    return true;
                }
                worlds.uninvitePlayer(player, args[1]);
                return true;
            case "trust":
                if (args.length < 2) {
                    plugin.getChatInputManager().request(player,
                            ru.optisolo.gui.ChatInputManager.InputType.TRUST, null);
                    return true;
                }
                worlds.trustPlayer(player, args[1]);
                return true;
            case "untrust":
                if (args.length < 2) {
                    msg.send(player, "not-trusted", "target", "?");
                    return true;
                }
                worlds.untrustPlayer(player, args[1]);
                return true;
            case "members":
                plugin.getMenuManager().openMembers(player, 0);
                return true;
            case "kit":
                plugin.getKitManager().claimKit(player);
                return true;
            case "time":
                if (args.length < 2 || (!args[1].equalsIgnoreCase("day") && !args[1].equalsIgnoreCase("night"))) {
                    msg.send(player, "time-set", "value", "day|night");
                    return true;
                }
                worlds.setTime(player, args[1].equalsIgnoreCase("day"));
                return true;
            case "weather":
                if (args.length < 2 || (!args[1].equalsIgnoreCase("clear") && !args[1].equalsIgnoreCase("rain"))) {
                    msg.send(player, "weather-set", "value", "clear|rain");
                    return true;
                }
                worlds.setWeather(player, args[1].equalsIgnoreCase("clear"));
                return true;
            case "difficulty":
                if (args.length < 2) {
                    msg.send(player, "bad-difficulty");
                    return true;
                }
                worlds.setDifficulty(player, args[1]);
                return true;
            case "public":
                worlds.setPublic(player, true);
                return true;
            case "private":
                worlds.setPublic(player, false);
                return true;
            case "report":
                if (!plugin.getSettings().isReportsEnabled()) {
                    msg.send(player, "report-disabled");
                    return true;
                }
                if (args.length < 3) {
                    msg.send(player, "report-usage");
                    return true;
                }
                StringBuilder reason = new StringBuilder();
                for (int i = 2; i < args.length; i++) {
                    if (i > 2) reason.append(' ');
                    reason.append(args[i]);
                }
                plugin.getReportManager().createReport(player, args[1], reason.toString());
                return true;
            case "reports":
                plugin.getMenuManager().openReports(player, 0);
                return true;
            case "reportclose":
                if (!player.hasPermission("optisolo.admin")) {
                    msg.send(player, "admin-only");
                    return true;
                }
                if (args.length < 2) return true;
                try {
                    int id = Integer.parseInt(args[1]);
                    if (plugin.getReportManager().closeReport(id)) msg.send(player, "report-closed", "id", id);
                    else msg.send(player, "report-not-found", "id", id);
                } catch (NumberFormatException e) {
                    msg.send(player, "report-not-found", "id", args[1]);
                }
                return true;
            case "reportdelete":
                if (!player.hasPermission("optisolo.admin")) {
                    msg.send(player, "admin-only");
                    return true;
                }
                if (args.length < 2) return true;
                try {
                    int id = Integer.parseInt(args[1]);
                    if (plugin.getReportManager().deleteReport(id)) msg.send(player, "report-deleted", "id", id);
                    else msg.send(player, "report-not-found", "id", id);
                } catch (NumberFormatException e) {
                    msg.send(player, "report-not-found", "id", args[1]);
                }
                return true;
            case "delete":
                if (args.length > 1 && args[1].equalsIgnoreCase("confirm")) {
                    worlds.deleteOwnWorld(player);
                } else {
                    msg.send(player, "delete-warn");
                }
                return true;
            case "reload":
                if (!player.hasPermission("optisolo.admin")) {
                    msg.send(player, "admin-only");
                    return true;
                }
                plugin.reloadAll();
                msg.send(player, "reloaded");
                return true;
            case "admin":
                return onAdmin(player, args);
            default:
                plugin.getMenuManager().sendHelp(player);
                return true;
        }
    }

    // ================= админка =================

    private boolean onAdmin(Player player, String[] args) {
        if (!player.hasPermission("optisolo.admin")) {
            msg.send(player, "admin-only");
            return true;
        }
        if (args.length < 2) {
            msg.sendList(player, "help-admin");
            return true;
        }
        String action = args[1].toLowerCase();
        switch (action) {
            case "list":
                msg.send(player, "admin-list-header", "amount", dataManager.getAll().size());
                for (SoloWorldData data : dataManager.getAll()) {
                    boolean loaded = Bukkit.getWorld(data.getWorldName()) != null;
                    msg.send(player, "admin-list-entry",
                            "owner", data.getOwnerName(),
                            "world", data.getWorldName(),
                            "amount", data.getTrusted().size(),
                            "value", loaded ? "да" : "нет");
                }
                return true;
            case "info":
                if (args.length < 3) return true;
                SoloWorldData info = dataManager.findByOwnerName(args[2]);
                if (info == null) {
                    msg.send(player, "world-not-found", "target", args[2]);
                    return true;
                }
                msg.send(player, "admin-info",
                        "owner", info.getOwnerName(),
                        "world", info.getWorldName(),
                        "value", info.isPublic() ? "да" : "нет",
                        "amount", info.getHomes().size());
                return true;
            case "tp":
                if (args.length < 3) return true;
                SoloWorldData tp = dataManager.findByOwnerName(args[2]);
                if (tp == null) {
                    msg.send(player, "world-not-found", "target", args[2]);
                    return true;
                }
                worlds.enterSoloWorld(player, tp);
                msg.send(player, "teleport-visit", "owner", tp.getOwnerName());
                return true;
            case "delete":
                if (args.length < 3) return true;
                SoloWorldData del = dataManager.findByOwnerName(args[2]);
                if (del == null) {
                    msg.send(player, "world-not-found", "target", args[2]);
                    return true;
                }
                worlds.deleteWorldData(player, del);
                return true;
            default:
                msg.sendList(player, "help-admin");
                return true;
        }
    }

    private boolean onConsole(CommandSender sender, String[] args) {
        if (args.length == 0) return true;
        String sub = args[0].toLowerCase();
        if (sub.equals("reload")) {
            plugin.reloadAll();
            msg.send(sender, "reloaded");
            return true;
        }
        if (sub.equals("admin") && args.length > 1) {
            String action = args[1].toLowerCase();
            if (action.equals("list")) {
                msg.send(sender, "admin-list-header", "amount", dataManager.getAll().size());
                for (SoloWorldData data : dataManager.getAll()) {
                    boolean loaded = Bukkit.getWorld(data.getWorldName()) != null;
                    msg.send(sender, "admin-list-entry",
                            "owner", data.getOwnerName(),
                            "world", data.getWorldName(),
                            "amount", data.getTrusted().size(),
                            "value", loaded ? "да" : "нет");
                }
                return true;
            }
            if (action.equals("delete") && args.length > 2) {
                SoloWorldData del = dataManager.findByOwnerName(args[2]);
                if (del == null) {
                    msg.send(sender, "world-not-found", "target", args[2]);
                    return true;
                }
                worlds.deleteWorldData(sender, del);
                return true;
            }
        }
        sender.sendMessage("Use: solo <reload|admin list|admin delete <owner>>");
        return true;
    }

    // ================= таб-комплит =================

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return partial(args[0], SUBS);
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            switch (sub) {
                case "create":
                    return partial(args[1], Arrays.asList("normal", "flat", "void"));
                case "visit":
                case "accept":
                    return partial(args[1], ownerNames());
                case "invite":
                case "uninvite":
                case "trust":
                case "untrust":
                case "report":
                    return partial(args[1], onlineNames());
                case "home":
                case "delhome":
                    if (sender instanceof Player) {
                        return partial(args[1], homeNames((Player) sender));
                    }
                    return Collections.emptyList();
                case "time":
                    return partial(args[1], Arrays.asList("day", "night"));
                case "weather":
                    return partial(args[1], Arrays.asList("clear", "rain"));
                case "difficulty":
                    return partial(args[1], Arrays.asList("peaceful", "easy", "normal", "hard"));
                case "delete":
                    return partial(args[1], Collections.singletonList("confirm"));
                case "admin":
                    return partial(args[1], Arrays.asList("list", "info", "tp", "delete"));
                default:
                    return Collections.emptyList();
            }
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("admin")) {
            String action = args[1].toLowerCase();
            if (action.equals("info") || action.equals("tp") || action.equals("delete")) {
                return partial(args[2], ownerNames());
            }
        }
        return Collections.emptyList();
    }

    private List<String> partial(String token, List<String> options) {
        List<String> out = new ArrayList<>();
        StringUtil.copyPartialMatches(token, options, out);
        Collections.sort(out);
        return out;
    }

    private List<String> onlineNames() {
        List<String> out = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
        return out;
    }

    private List<String> ownerNames() {
        List<String> out = new ArrayList<>();
        for (SoloWorldData data : dataManager.getAll()) out.add(data.getOwnerName());
        return out;
    }

    private List<String> homeNames(Player player) {
        List<String> out = new ArrayList<>();
        SoloWorldData data = dataManager.findByOwner(player.getUniqueId());
        if (data != null) {
            for (SoloWorldData.HomePoint h : data.getHomes().values()) out.add(h.name);
        }
        return out;
    }
}
