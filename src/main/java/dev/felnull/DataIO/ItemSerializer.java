package dev.felnull.DataIO;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

public class ItemSerializer {
    public static String serializeToBase64(ItemStack item) {
        if (item == null) {
            return null; // ← nullのまま返す（DB保存時にNULLとして扱える）
        }
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             BukkitObjectOutputStream oos = new BukkitObjectOutputStream(baos)) {
            oos.writeObject(item);
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException("ItemStack serialization failed", e);
        }
    }

    public static ItemStack deserializeFromBase64(String base64) {
        if (base64 == null) {
            return null; // ← ここで防ぐ
        }
        byte[] data = Base64.getDecoder().decode(base64);
        try (BukkitObjectInputStream ois = new BukkitObjectInputStream(new ByteArrayInputStream(data))) {
            return (ItemStack) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException("ItemStack deserialization failed", e);
        }
    }
}
