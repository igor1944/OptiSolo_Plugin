package ru.optisolo.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import ru.optisolo.OptiSoloPlugin;
import ru.optisolo.config.Messages;
import ru.optisolo.data.DataManager;
import ru.optisolo.data.SoloWorldData;
import ru.optisolo.report.ReportManager;
import ru.optisolo.util.ItemBuilder;
import ru.optisolo.world.SoloWorldManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Все GUI-меню плагина. */
public class MenuManager {

    private final OptiSoloPlugin plugin;
    private final Messages msg;
    private final DataManager dataManager;
    private SoloWorldManager worlds;

    public MenuManager(OptiSoloPlugin plugin) {
        this.plugin = plugin;
        this.msg = plugin.getMessages();
        this.dataManager = plugin.getDataManager();
    }

    private SoloWorldManager wm() {
        if (worlds == null) worlds = plugin.getWorldManager();
        return worlds;
    }

    public enum MenuType {
        MAIN, MAIN_NO_WORLD, MEMBERS, HOMES, SETTINGS, REPORTS, CONFIRM_DELETE
    }

    public static class SoloMenuHolder implements InventoryHolder {
        private final MenuType type;
        private final int page;
        private Inventory inventory;

        public SoloMenuHolder(MenuType type, int page) {
            this.type = type;
            this.page = page;
        }

        public MenuType getType() {
            return type;
        }

        public int getPage() {
            return page;
        }

        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    // ================= открытие =================

    public void openMain(Player player) {
        SoloWorldData data = dataManager.findByOwner(player.getUniqueId());
        if (data == null) openNoWorld(player);
        else {
            SoloMenuHolder holder = new SoloMenuHolder(MenuType.MAIN, 0);
            Inventory inv = Bukkit.createInventory(holder, 27, msg.tr("menu-title-main"));
            holder.setInventory(inv);
            fill(inv);

            inv.setItem(10, new ItemBuilder(Material.ENDER_PEARL).name("§aТелепорт в свой мир")
                    .lore("§7Нажмите, чтобы попасть домой").build());
            inv.setItem(11, new ItemBuilder(Material.COMPASS).name("§bВ мир друга")
                    .lore("§7Ввести ник владельца в чате").build());
            inv.setItem(12, new ItemBuilder(Material.PLAYER_HEAD).name("§eПригласить друга")
                    .lore("§7Ввести ник друга в чате").build());
            inv.setItem(13, new ItemBuilder(Material.BOOK).name("§6Доступы")
                    .lore("§7Доверенных: §f" + data.getTrusted().size(),
                            "§7Приглашений: §f" + data.getInvites().size()).build());
            inv.setItem(14, new ItemBuilder(Material.OAK_SIGN).name("§dТочки телепорта")
                    .lore("§7Сохранено: §f" + data.getHomes().size()).build());
            inv.setItem(15, new ItemBuilder(Material.COMPARATOR).name("§6Настройки мира")
                    .lore("§7Время, погода, сложность, PvP").build());
            inv.setItem(16, new ItemBuilder(Material.CHEST).name("§aСтартовый набор")
                    .lore("§7Получить набор (кулдаун)").build());

            inv.setItem(19, new ItemBuilder(Material.OAK_DOOR).name("§7Покинуть мир")
                    .lore("§7Вернуться в обычный мир").build());
            ReportManager rm = plugin.getReportManager();
            inv.setItem(21, new ItemBuilder(Material.PAPER).name("§cЖалобы")
                    .lore("§7Открытых: §f" + rm.countOpen()).build());
            inv.setItem(22, new ItemBuilder(Material.MAP).name("§eПомощь")
                    .lore("§7Список команд в чат").build());
            inv.setItem(23, new ItemBuilder(Material.TNT).name("§4Удалить мир")
                    .lore("§cОсторожно! Безвозвратно!").build());

            player.openInventory(inv);
        }
    }

    public void openNoWorld(Player player) {
        SoloMenuHolder holder = new SoloMenuHolder(MenuType.MAIN_NO_WORLD, 0);
        Inventory inv = Bukkit.createInventory(holder, 27, msg.tr("menu-title-no-world"));
        holder.setInventory(inv);
        fill(inv);
        inv.setItem(11, new ItemBuilder(Material.GRASS_BLOCK).name("§aСоздать обычный мир")
                .lore("§7Стандартная генерация", "§7Размер: §f10000 x 10000").build());
        inv.setItem(13, new ItemBuilder(Material.SANDSTONE).name("§eСоздать плоский мир")
                .lore("§7Идеально для построек", "§7Размер: §f10000 x 10000").build());
        inv.setItem(15, new ItemBuilder(Material.GLASS).name("§bСоздать пустой мир")
                .lore("§7Только пустота и платформа", "§7Размер: §f10000 x 10000").build());
        inv.setItem(22, new ItemBuilder(Material.MAP).name("§eПомощь")
                .lore("§7Список команд в чат").build());
        player.openInventory(inv);
    }

    public void openMembers(Player player, int page) {
        SoloWorldData data = dataManager.findByOwner(player.getUniqueId());
        if (data == null) {
            msg.send(player, "no-world");
            return;
        }
        SoloMenuHolder holder = new SoloMenuHolder(MenuType.MEMBERS, page);
        Inventory inv = Bukkit.createInventory(holder, 54, msg.tr("menu-title-members"));
        holder.setInventory(inv);

        List<ItemStack> items = new ArrayList<>();
        items.add(ItemBuilder.skull(Bukkit.getOfflinePlayer(data.getOwnerUuid()),
                "§6Владелец: §e" + data.getOwnerName(),
                Arrays.asList("§7Это вы", "§7Мир: §f" + (data.isPublic() ? "открыт" : "закрыт"))));
        for (UUID id : data.getTrusted()) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(id);
            items.add(ItemBuilder.skull(op, "§a" + data.getTrustedName(id),
                    Arrays.asList("§7Статус: §aдоверенный",
                            "§7Онлайн: §f" + (op.isOnline() ? "да" : "нет"),
                            "",
                            "§cНажмите, чтобы убрать из доверенных")));
        }
        data.cleanupInvites();
        for (Map.Entry<UUID, Long> e : data.getInvites().entrySet()) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(e.getKey());
            String name = op.getName() == null ? e.getKey().toString().substring(0, 8) : op.getName();
            long left = e.getValue() - System.currentTimeMillis();
            items.add(ItemBuilder.skull(op, "§e" + name,
                    Arrays.asList("§7Статус: §eприглашён",
                            "§7Истекает через: §f" + msg.formatDuration(Math.max(0, left)),
                            "",
                            "§cНажмите, чтобы отозвать")));
        }

        int perPage = 45;
        int totalPages = Math.max(0, (items.size() - 1) / perPage);
        page = Math.max(0, Math.min(page, totalPages));
        for (int i = 0; i < perPage && page * perPage + i < items.size(); i++) {
            inv.setItem(i, items.get(page * perPage + i));
        }
        inv.setItem(45, page > 0 ? back_page("§a« Стр. " + page) : filler());
        inv.setItem(47, new ItemBuilder(Material.PLAYER_HEAD).name("§eПригласить")
                .lore("§7Ввести ник в чате").build());
        inv.setItem(48, new ItemBuilder(Material.EMERALD).name("§aВ доверенные")
                .lore("§7Ввести ник в чате").build());
        inv.setItem(49, backButton());
        inv.setItem(53, page < totalPages ? back_page("§aСтр. " + (page + 2) + " »") : filler());
        for (int i = 45; i < 54; i++) {
            if (inv.getItem(i) == null) inv.setItem(i, filler());
        }
        player.openInventory(inv);
    }

    public void openHomes(Player player, int page) {
        SoloWorldData data = dataManager.findByOwner(player.getUniqueId());
        if (data == null) {
            msg.send(player, "no-world");
            return;
        }
        SoloMenuHolder holder = new SoloMenuHolder(MenuType.HOMES, page);
        Inventory inv = Bukkit.createInventory(holder, 54, msg.tr("menu-title-homes"));
        holder.setInventory(inv);

        List<SoloWorldData.HomePoint> homes = new ArrayList<>(data.getHomes().values());
        int perPage = 45;
        int totalPages = Math.max(0, (homes.size() - 1) / perPage);
        page = Math.max(0, Math.min(page, totalPages));
        for (int i = 0; i < perPage && page * perPage + i < homes.size(); i++) {
            SoloWorldData.HomePoint h = homes.get(page * perPage + i);
            inv.setItem(i, new ItemBuilder(Material.ENDER_PEARL).name("§b" + h.name)
                    .lore("§7Координаты: §f" + (int) h.x + " " + (int) h.y + " " + (int) h.z,
                            "",
                            "§aЛКМ — телепорт",
                            "§cShift+ЛКМ — удалить").build());
        }
        inv.setItem(45, page > 0 ? back_page("§a« Стр. " + page) : filler());
        inv.setItem(47, new ItemBuilder(Material.NAME_TAG).name("§aСоздать точку")
                .lore("§7Ввести название в чате").build());
        inv.setItem(49, backButton());
        inv.setItem(53, page < totalPages ? back_page("§aСтр. " + (page + 2) + " »") : filler());
        for (int i = 45; i < 54; i++) {
            if (inv.getItem(i) == null) inv.setItem(i, filler());
        }
        player.openInventory(inv);
    }

    public void openSettings(Player player) {
        SoloWorldData data = dataManager.findByOwner(player.getUniqueId());
        if (data == null) {
            msg.send(player, "no-world");
            return;
        }
        SoloMenuHolder holder = new SoloMenuHolder(MenuType.SETTINGS, 0);
        Inventory inv = Bukkit.createInventory(holder, 27, msg.tr("menu-title-settings"));
        holder.setInventory(inv);
        fill(inv);

        inv.setItem(10, new ItemBuilder(Material.ZOMBIE_HEAD).name("§eСложность: §f" + data.getDifficulty())
                .lore("§7Нажмите, чтобы сменить").build());
        inv.setItem(11, new ItemBuilder(Material.SUNFLOWER).name("§eДень")
                .lore("§7Установить день").build());
        inv.setItem(12, new ItemBuilder(Material.INK_SAC).name("§8Ночь")
                .lore("§7Установить ночь").build());
        inv.setItem(13, new ItemBuilder(Material.GLASS).name("§bЯсная погода")
                .lore("§7Убрать дождь").build());
        inv.setItem(14, new ItemBuilder(Material.WATER_BUCKET).name("§9Дождь")
                .lore("§7Включить дождь").build());
        inv.setItem(15, new ItemBuilder(Material.DIAMOND_SWORD).name("§cPvP: " + (data.isPvp() ? "§aвкл" : "§4выкл"))
                .lore("§7Нажмите, чтобы переключить").build());
        inv.setItem(16, new ItemBuilder(data.isPublic() ? Material.OAK_FENCE_GATE : Material.IRON_DOOR)
                .name("§eМир: " + (data.isPublic() ? "§aоткрыт" : "§cзакрыт"))
                .lore("§7Нажмите, чтобы переключить").build());
        inv.setItem(22, new ItemBuilder(Material.MAP).name("§7Граница мира")
                .lore("§7Размер: §f" + (int) plugin.getSettings().getBorderSize() + " x " + (int) plugin.getSettings().getBorderSize(),
                        "§7Центр: §f0, 0").build());
        inv.setItem(26, backButton());
        player.openInventory(inv);
    }

    public void openReports(Player player, int page) {
        boolean admin = player.hasPermission("optisolo.admin");
        List<ReportManager.Report> list = admin
                ? plugin.getReportManager().getAllSorted()
                : plugin.getReportManager().getByReporter(player.getUniqueId());

        SoloMenuHolder holder = new SoloMenuHolder(MenuType.REPORTS, page);
        Inventory inv = Bukkit.createInventory(holder, 54, msg.tr("menu-title-reports"));
        holder.setInventory(inv);

        int perPage = 45;
        int totalPages = Math.max(0, (list.size() - 1) / perPage);
        page = Math.max(0, Math.min(page, totalPages));
        for (int i = 0; i < perPage && page * perPage + i < list.size(); i++) {
            ReportManager.Report r = list.get(page * perPage + i);
            String status = r.status.equals("OPEN") ? "§aоткрыта" : "§7закрыта";
            List<String> lore = new ArrayList<>();
            lore.add("§7От: §f" + r.reporterName + " §7на: §c" + r.reported);
            lore.add("§7Мир: §f" + r.world);
            lore.add("§7Дата: §f" + r.dateString());
            lore.add("§7Причина: §f" + r.reason);
            lore.add("§7Статус: " + status);
            if (admin) {
                lore.add("");
                lore.add("§aЛКМ — закрыть");
                lore.add("§cShift+ЛКМ — удалить");
            }
            inv.setItem(i, new ItemBuilder(r.status.equals("OPEN") ? Material.PAPER : Material.BOOK)
                    .name("§eЖалоба #" + r.id).lore(lore).build());
        }
        inv.setItem(45, page > 0 ? back_page("§a« Стр. " + page) : filler());
        inv.setItem(47, new ItemBuilder(Material.WRITABLE_BOOK).name("§cСоздать жалобу")
                .lore("§7Ввести ника и причину в чате").build());
        inv.setItem(49, backButton());
        inv.setItem(53, page < totalPages ? back_page("§aСтр. " + (page + 2) + " »") : filler());
        for (int i = 45; i < 54; i++) {
            if (inv.getItem(i) == null) inv.setItem(i, filler());
        }
        player.openInventory(inv);
    }

    public void openConfirmDelete(Player player) {
        SoloMenuHolder holder = new SoloMenuHolder(MenuType.CONFIRM_DELETE, 0);
        Inventory inv = Bukkit.createInventory(holder, 9, msg.tr("menu-title-confirm"));
        holder.setInventory(inv);
        fill(inv);
        inv.setItem(3, new ItemBuilder(Material.LIME_DYE).name("§a§lПОДТВЕРДИТЬ УДАЛЕНИЕ")
                .lore("§7Мир будет удалён навсегда!").build());
        inv.setItem(5, new ItemBuilder(Material.RED_DYE).name("§c§lОТМЕНА")
                .lore("§7Вернуться в меню").build());
        player.openInventory(inv);
    }

    // ================= клики =================

    public void handleClick(Player player, SoloMenuHolder holder, int slot, ClickType click, ItemStack current) {
        Sound clickSound = Sound.UI_BUTTON_CLICK;
        try {
            player.playSound(player.getLocation(), clickSound, 0.6f, 1f);
        } catch (Exception ignored) {
        }

        switch (holder.getType()) {
            case MAIN_NO_WORLD:
                if (slot == 11) {
                    player.closeInventory();
                    wm().createWorld(player, "NORMAL");
                } else if (slot == 13) {
                    player.closeInventory();
                    wm().createWorld(player, "FLAT");
                } else if (slot == 15) {
                    player.closeInventory();
                    wm().createWorld(player, "VOID");
                } else if (slot == 22) {
                    player.closeInventory();
                    sendHelp(player);
                }
                break;

            case MAIN:
                if (slot == 10) {
                    player.closeInventory();
                    wm().teleportOwn(player);
                } else if (slot == 11) {
                    player.closeInventory();
                    plugin.getChatInputManager().request(player, ChatInputManager.InputType.VISIT, null);
                } else if (slot == 12) {
                    player.closeInventory();
                    plugin.getChatInputManager().request(player, ChatInputManager.InputType.INVITE, null);
                } else if (slot == 13) {
                    openMembers(player, 0);
                } else if (slot == 14) {
                    openHomes(player, 0);
                } else if (slot == 15) {
                    openSettings(player);
                } else if (slot == 16) {
                    player.closeInventory();
                    plugin.getKitManager().claimKit(player);
                } else if (slot == 19) {
                    player.closeInventory();
                    wm().leaveToMain(player);
                } else if (slot == 21) {
                    openReports(player, 0);
                } else if (slot == 22) {
                    player.closeInventory();
                    sendHelp(player);
                } else if (slot == 23) {
                    openConfirmDelete(player);
                }
                break;

            case MEMBERS:
                handleMembersClick(player, holder, slot);
                break;

            case HOMES:
                handleHomesClick(player, holder, slot, click);
                break;

            case SETTINGS:
                if (slot == 10) {
                    wm().cycleDifficulty(player);
                    openSettings(player);
                } else if (slot == 11) {
                    wm().setTime(player, true);
                    openSettings(player);
                } else if (slot == 12) {
                    wm().setTime(player, false);
                    openSettings(player);
                } else if (slot == 13) {
                    wm().setWeather(player, true);
                    openSettings(player);
                } else if (slot == 14) {
                    wm().setWeather(player, false);
                    openSettings(player);
                } else if (slot == 15) {
                    wm().togglePvp(player);
                    openSettings(player);
                } else if (slot == 16) {
                    SoloWorldData data = dataManager.findByOwner(player.getUniqueId());
                    if (data != null) wm().setPublic(player, !data.isPublic());
                    openSettings(player);
                } else if (slot == 26) {
                    openMain(player);
                }
                break;

            case REPORTS:
                handleReportsClick(player, holder, slot, click);
                break;

            case CONFIRM_DELETE:
                if (slot == 3) {
                    player.closeInventory();
                    wm().deleteOwnWorld(player);
                } else if (slot == 5) {
                    openMain(player);
                }
                break;
        }
    }

    private void handleMembersClick(Player player, SoloMenuHolder holder, int slot) {
        SoloWorldData data = dataManager.findByOwner(player.getUniqueId());
        if (data == null) {
            player.closeInventory();
            msg.send(player, "no-world");
            return;
        }
        if (slot == 45 && holder.getPage() > 0) {
            openMembers(player, holder.getPage() - 1);
            return;
        }
        if (slot == 53) {
            openMembers(player, holder.getPage() + 1);
            return;
        }
        if (slot == 47) {
            player.closeInventory();
            plugin.getChatInputManager().request(player, ChatInputManager.InputType.INVITE, null);
            return;
        }
        if (slot == 48) {
            player.closeInventory();
            plugin.getChatInputManager().request(player, ChatInputManager.InputType.TRUST, null);
            return;
        }
        if (slot == 49) {
            openMain(player);
            return;
        }
        if (slot < 0 || slot >= 45) return;

        // Индекс в общем списке: 0 — владелец, далее доверенные, далее приглашённые.
        int index = holder.getPage() * 45 + slot;
        if (index == 0) return;
        List<UUID> trusted = new ArrayList<>(data.getTrusted());
        if (index - 1 < trusted.size()) {
            UUID id = trusted.get(index - 1);
            String name = data.getTrustedName(id);
            data.removeTrusted(id);
            dataManager.save(data);
            msg.send(player, "trust-removed", "target", name);
            Player target = Bukkit.getPlayer(id);
            if (target != null) msg.send(target, "trust-removed-target", "owner", player.getName());
            openMembers(player, holder.getPage());
            return;
        }
        List<UUID> invited = new ArrayList<>(data.getInvites().keySet());
        int invIndex = index - 1 - trusted.size();
        if (invIndex >= 0 && invIndex < invited.size()) {
            UUID id = invited.get(invIndex);
            data.revokeInvite(id);
            dataManager.save(data);
            OfflinePlayer op = Bukkit.getOfflinePlayer(id);
            msg.send(player, "invite-revoked", "target", op.getName() == null ? "?" : op.getName());
            openMembers(player, holder.getPage());
        }
    }

    private void handleHomesClick(Player player, SoloMenuHolder holder, int slot, ClickType click) {
        SoloWorldData data = dataManager.findByOwner(player.getUniqueId());
        if (data == null) {
            player.closeInventory();
            msg.send(player, "no-world");
            return;
        }
        if (slot == 45 && holder.getPage() > 0) {
            openHomes(player, holder.getPage() - 1);
            return;
        }
        if (slot == 53) {
            openHomes(player, holder.getPage() + 1);
            return;
        }
        if (slot == 47) {
            player.closeInventory();
            plugin.getChatInputManager().request(player, ChatInputManager.InputType.HOME_CREATE, null);
            return;
        }
        if (slot == 49) {
            openMain(player);
            return;
        }
        if (slot < 0 || slot >= 45) return;
        List<SoloWorldData.HomePoint> homes = new ArrayList<>(data.getHomes().values());
        int index = holder.getPage() * 45 + slot;
        if (index < 0 || index >= homes.size()) return;
        SoloWorldData.HomePoint home = homes.get(index);
        if (click == ClickType.SHIFT_LEFT || click == ClickType.SHIFT_RIGHT) {
            wm().deleteHomePoint(player, home.name);
            openHomes(player, holder.getPage());
        } else {
            player.closeInventory();
            wm().teleportHome(player, home.name);
        }
    }

    private void handleReportsClick(Player player, SoloMenuHolder holder, int slot, ClickType click) {
        boolean admin = player.hasPermission("optisolo.admin");
        if (slot == 45 && holder.getPage() > 0) {
            openReports(player, holder.getPage() - 1);
            return;
        }
        if (slot == 53) {
            openReports(player, holder.getPage() + 1);
            return;
        }
        if (slot == 47) {
            player.closeInventory();
            plugin.getChatInputManager().request(player, ChatInputManager.InputType.REPORT_PLAYER, null);
            return;
        }
        if (slot == 49) {
            openMain(player);
            return;
        }
        if (!admin || slot < 0 || slot >= 45) return;
        List<ReportManager.Report> list = plugin.getReportManager().getAllSorted();
        int index = holder.getPage() * 45 + slot;
        if (index < 0 || index >= list.size()) return;
        ReportManager.Report r = list.get(index);
        if (click == ClickType.SHIFT_LEFT || click == ClickType.SHIFT_RIGHT) {
            plugin.getReportManager().deleteReport(r.id);
            msg.send(player, "report-deleted", "id", r.id);
        } else {
            plugin.getReportManager().closeReport(r.id);
            msg.send(player, "report-closed", "id", r.id);
        }
        openReports(player, holder.getPage());
    }

    public void sendHelp(Player player) {
        msg.sendList(player, "help-user");
        if (player.hasPermission("optisolo.admin")) {
            msg.sendList(player, "help-admin");
        }
    }

    // ================= оформление =================

    private void fill(Inventory inv) {
        ItemStack filler = filler();
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null) inv.setItem(i, filler);
        }
    }

    private ItemStack filler() {
        return new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(msg.tr("filler-name")).build();
    }

    private ItemStack backButton() {
        return new ItemBuilder(Material.ARROW).name(msg.tr("btn-back")).build();
    }

    private ItemStack back_page(String name) {
        return new ItemBuilder(Material.ARROW).name(name).build();
    }
}
