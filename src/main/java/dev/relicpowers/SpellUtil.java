package dev.relicpowers;

import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/** Temporary enchantment boosts for Spellbound. Markers live on the item so they survive moving it around. */
public final class SpellUtil {
    private SpellUtil() {}

    private static NamespacedKey orig(String tag) { return new NamespacedKey(RelicPowers.get(), "sb_" + tag + "_orig"); }
    private static NamespacedKey exp(String tag) { return new NamespacedKey(RelicPowers.get(), "sb_" + tag + "_exp"); }

    /** Raises `ench` on the item by `add` levels until `expiryMillis`. */
    public static void boost(ItemStack it, Enchantment ench, String tag, int add, long expiryMillis) {
        ItemMeta m = it.getItemMeta();
        if (m == null) return;
        PersistentDataContainer pdc = m.getPersistentDataContainer();
        if (!pdc.has(orig(tag), PersistentDataType.INTEGER)) {
            pdc.set(orig(tag), PersistentDataType.INTEGER, m.getEnchantLevel(ench));
        }
        pdc.set(exp(tag), PersistentDataType.LONG, expiryMillis);
        int base = pdc.get(orig(tag), PersistentDataType.INTEGER);
        m.addEnchant(ench, base + add, true);
        it.setItemMeta(m);
    }

    private static void restore(ItemStack it, Enchantment ench, String tag, boolean force) {
        if (it == null || !it.hasItemMeta()) return;
        ItemMeta m = it.getItemMeta();
        PersistentDataContainer pdc = m.getPersistentDataContainer();
        Long e = pdc.get(exp(tag), PersistentDataType.LONG);
        if (e == null) return;
        if (!force && e > System.currentTimeMillis()) return;
        Integer o = pdc.get(orig(tag), PersistentDataType.INTEGER);
        if (o == null || o <= 0) m.removeEnchant(ench); else m.addEnchant(ench, o, true);
        pdc.remove(exp(tag));
        pdc.remove(orig(tag));
        it.setItemMeta(m);
    }

    /** Reverts any expired (or all, if force) Spellbound boosts in the player's inventory. */
    public static void sweep(Player p, boolean force) {
        PlayerInventory inv = p.getInventory();
        for (ItemStack i : inv.getContents()) {
            if (i == null || !i.hasItemMeta()) continue;
            restore(i, Enchantment.SHARPNESS, "sharp", force);
            restore(i, Enchantment.PROTECTION, "prot", force);
        }
    }
}
