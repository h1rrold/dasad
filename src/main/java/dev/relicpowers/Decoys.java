package dev.relicpowers;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;

/** "Fake players": armor stands wearing the owner's head + gear that run around, plus voodoo dolls. */
public final class Decoys {
    private Decoys() {}

    private static NamespacedKey key() { return new NamespacedKey(RelicPowers.get(), "decoy"); }

    public static boolean isDecoy(Entity e) {
        return e instanceof ArmorStand && e.getPersistentDataContainer().has(key(), PersistentDataType.BYTE);
    }

    public static void mark(Entity e) {
        e.getPersistentDataContainer().set(key(), PersistentDataType.BYTE, (byte) 1);
    }

    public static void pop(Entity e) {
        Util.p(Particle.POOF, e.getLocation().add(0, 1, 0), 20, 0.3, 0.6, 0.3, 0.05);
        Util.sound(e.getLocation(), "entity.illusioner.mirror_move", 0.8f, 1.2f);
        e.remove();
    }

    public static void removeAll() {
        for (World w : Bukkit.getWorlds()) {
            for (ArmorStand a : w.getEntitiesByClass(ArmorStand.class)) {
                if (isDecoy(a)) a.remove();
            }
        }
    }

    public static void spawn(Player owner, int count, int lifeTicks) {
        for (int i = 0; i < count; i++) one(owner, lifeTicks);
    }

    private static void one(Player owner, int life) {
        Location base = owner.getLocation();
        Location l = base.clone().add(Util.rnd(-3, 3), 0, Util.rnd(-3, 3));
        if (!Util.passable(l)) l = base.clone();
        final ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta sm = (SkullMeta) head.getItemMeta();
        sm.setOwningPlayer(owner);
        head.setItemMeta(sm);
        final ItemStack chest = owner.getInventory().getChestplate();
        final ItemStack legs = owner.getInventory().getLeggings();
        final ItemStack boots = owner.getInventory().getBoots();
        final ItemStack hand = owner.getInventory().getItemInMainHand();
        final String name = owner.getName();

        final ArmorStand as = base.getWorld().spawn(l, ArmorStand.class, a -> {
            a.setArms(true);
            a.setBasePlate(false);
            a.setGravity(false);
            a.setSilent(true);
            a.setCollidable(false);
            a.setPersistent(false);
            a.setCustomNameVisible(true);
            a.customName(Component.text(name));
            EntityEquipment eq = a.getEquipment();
            eq.setHelmet(head);
            if (chest != null) eq.setChestplate(chest.clone());
            if (legs != null) eq.setLeggings(legs.clone());
            if (boots != null) eq.setBoots(boots.clone());
            if (hand != null && !hand.getType().isAir() && !pl().mgr.isRelicItem(hand)) eq.setItemInMainHand(hand.clone());
            mark(a);
        });
        Util.p(Particle.POOF, l.clone().add(0, 1, 0), 12, 0.3, 0.5, 0.3, 0.03);

        new BukkitRunnable() {
            int t = 0;
            double ang = Util.rnd(0, Math.PI * 2);
            @Override public void run() {
                if (!as.isValid()) { cancel(); return; }
                if (t >= life) { pop(as); cancel(); return; }
                if (t % 20 == 0) ang += Util.rnd(-0.9, 0.9);
                Vector step = new Vector(Math.cos(ang), 0, Math.sin(ang)).multiply(0.24);
                Location n = as.getLocation().add(step);
                if (!n.getBlock().isPassable()) {
                    n.add(0, 1, 0);
                    if (!n.getBlock().isPassable()) { ang += Math.PI; t++; return; }
                } else if (n.clone().add(0, -1, 0).getBlock().isPassable()) {
                    n.add(0, -1, 0);
                }
                n.setYaw((float) Math.toDegrees(Math.atan2(-step.getX(), step.getZ())));
                as.teleport(n);
                double sw = Math.sin(t * 0.7) * 0.9;
                as.setRightArmPose(new EulerAngle(sw, 0, 0));
                as.setLeftArmPose(new EulerAngle(-sw, 0, 0));
                as.setRightLegPose(new EulerAngle(-sw, 0, 0));
                as.setLeftLegPose(new EulerAngle(sw, 0, 0));
                t++;
            }
        }.runTaskTimer(RelicPowers.get(), 1L, 1L);
    }

    private static RelicPowers pl() { return RelicPowers.get(); }
}
