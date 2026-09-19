package ru.optisolo.util;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

/** Сериализация инвентарей в Base64 для раздельного хранения. */
public final class ItemSerializer {

    private ItemSerializer() {
    }

    public static String toBase64(ItemStack[] items) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        BukkitObjectOutputStream out = new BukkitObjectOutputStream(bos);
        out.writeInt(items.length);
        for (ItemStack item : items) {
            out.writeObject(item);
        }
        out.close();
        return Base64.getEncoder().encodeToString(bos.toByteArray());
    }

    public static ItemStack[] fromBase64(String data) throws IOException, ClassNotFoundException {
        byte[] bytes = Base64.getDecoder().decode(data);
        ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
        BukkitObjectInputStream in = new BukkitObjectInputStream(bis);
        int len = in.readInt();
        if (len < 0 || len > 512) {
            in.close();
            throw new IOException("Bad item array length: " + len);
        }
        ItemStack[] items = new ItemStack[len];
        for (int i = 0; i < len; i++) {
            Object o = in.readObject();
            items[i] = (o instanceof ItemStack) ? (ItemStack) o : null;
        }
        in.close();
        return items;
    }
}
