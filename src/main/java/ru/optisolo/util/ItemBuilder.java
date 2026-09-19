package ru.optisolo.util;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;

/** Маленький билдер предметов для меню. */
public class ItemBuilder {

    private final ItemStack item;
    private final ItemMeta meta;

    public ItemBuilder(Material material) {
        this(material, 1);
    }

    public ItemBuilder(Material material, int amount) {
        this.item = new ItemStack(material, Math.max(1, amount));
        this.meta = item.getItemMeta();
    }

    public ItemBuilder name(String name) {
        if (meta != null && name != null) {
            meta.setDisplayName(name);
        }
        return this;
    }

    public ItemBuilder lore(List<String> lore) {
        if (meta != null && lore != null) {
            meta.setLore(new ArrayList<>(lore));
        }
        return this;
    }

    public ItemBuilder lore(String... lore) {
        if (meta != null) {
            List<String> list = new ArrayList<>();
            for (String s : lore) {
                if (s != null) list.add(s);
            }
            meta.setLore(list);
        }
        return this;
    }

    public ItemBuilder glow(boolean glow) {
        if (glow && meta != null) {
            meta.addEnchant(Enchantment.DURABILITY, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        return this;
    }

    public ItemBuilder flags(ItemFlag... flags) {
        if (meta != null) {
            meta.addItemFlags(flags);
        }
        return this;
    }

    public ItemStack build() {
        if (meta != null) {
            item.setItemMeta(meta);
        }
        return item;
    }

    /** Голова игрока для списков. */
    public static ItemStack skull(org.bukkit.OfflinePlayer owner, String name, List<String> lore) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta sm = (SkullMeta) head.getItemMeta();
        if (sm != null) {
            try {
                sm.setOwningPlayer(owner);
            } catch (Exception ignored) {
            }
            if (name != null) sm.setDisplayName(name);
            if (lore != null) sm.setLore(new ArrayList<>(lore));
            head.setItemMeta(sm);
        }
        return head;
    }
}
