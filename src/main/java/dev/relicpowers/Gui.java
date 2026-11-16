package dev.relicpowers;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** /powers - a browsable list of every relic. Admins can click one to take it. */
public final class Gui {
    private Gui() {}

    public static final class Holder implements InventoryHolder {
        private Inventory inv;
        @Override public Inventory getInventory() { return inv; }
        void set(Inventory i) { this.inv = i; }
    }

    public static void open(Player p) {
        RelicPowers pl = RelicPowers.get();
        Holder h = new Holder();
        Inventory inv = Bukkit.createInventory(h, 54, Component.text("Relic Powers"));
        h.set(inv);
        List<Power> all = Powers.all();
        int slot = 0;
        for (Power pw : all) {
            inv.setItem(slot++, pl.mgr.createItem(pw, 1));
        }
        slot = 45;
        inv.setItem(slot, pl.mgr.createTool("upgrader"));
        inv.setItem(slot + 1, pl.mgr.createTool("reroller"));
        p.openInventory(inv);
    }

    /** Admin click = become that relic / receive that tool. */
    public static void click(Player p, ItemStack clicked) {
        RelicPowers pl = RelicPowers.get();
        if (clicked == null || clicked.getType().isAir()) return;
        if (!p.hasPermission("relicpowers.admin")) return;
        Power pw = pl.mgr.powerOf(clicked);
        if (pw != null) {
            pl.mgr.setPower(p, pw, 1);
            Util.msg(p, "§aYou are now " + pw.color + pw.name() + "§a.");
            return;
        }
        String tool = pl.mgr.toolOf(clicked);
        if (tool != null) {
            pl.mgr.giveItem(p, pl.mgr.createTool(tool));
            Util.msg(p, "§aGiven a " + tool + ".");
        }
    }
}
