package dev.relicpowers;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ShapedRecipe;

/** Crafting recipes for the Upgrader and Reroller. */
public final class Recipes {
    private Recipes() {}

    public static void register(RelicPowers pl) {
        if (pl.getConfig().getBoolean("recipes.upgrader", true)) {
            ShapedRecipe up = new ShapedRecipe(new NamespacedKey(pl, "upgrader"), pl.mgr.createTool("upgrader"));
            up.shape("DND", "NSN", "DND");
            up.setIngredient('D', Material.DIAMOND_BLOCK);
            up.setIngredient('N', Material.NETHERITE_INGOT);
            up.setIngredient('S', Material.NETHER_STAR);
            Bukkit.addRecipe(up);
        }
        if (pl.getConfig().getBoolean("recipes.reroller", true)) {
            // columns: blaze rods | ender pearl, eye of ender, ender pearl | breeze rods
            ShapedRecipe rr = new ShapedRecipe(new NamespacedKey(pl, "reroller"), pl.mgr.createTool("reroller"));
            rr.shape("BEZ", "BIZ", "BEZ");
            rr.setIngredient('B', Material.BLAZE_ROD);
            rr.setIngredient('E', Material.ENDER_PEARL);
            rr.setIngredient('I', Material.ENDER_EYE);
            rr.setIngredient('Z', Material.BREEZE_ROD);
            Bukkit.addRecipe(rr);
        }
    }

    public static void unregister(RelicPowers pl) {
        Bukkit.removeRecipe(new NamespacedKey(pl, "upgrader"));
        Bukkit.removeRecipe(new NamespacedKey(pl, "reroller"));
    }
}
