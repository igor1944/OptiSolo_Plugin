package ru.optisolo.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import ru.optisolo.OptiSoloPlugin;
import ru.optisolo.gui.MenuManager;

/** Клики по GUI-меню. */
public class MenuListener implements Listener {

    private final MenuManager menus;

    public MenuListener(OptiSoloPlugin plugin) {
        this.menus = plugin.getMenuManager();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof MenuManager.SoloMenuHolder)) return;
        e.setCancelled(true);
        if (e.getRawSlot() < 0 || e.getRawSlot() >= top.getSize()) return;
        menus.handleClick((Player) e.getWhoClicked(),
                (MenuManager.SoloMenuHolder) top.getHolder(),
                e.getRawSlot(), e.getClick(), e.getCurrentItem());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (top.getHolder() instanceof MenuManager.SoloMenuHolder) {
            e.setCancelled(true);
        }
    }
}
