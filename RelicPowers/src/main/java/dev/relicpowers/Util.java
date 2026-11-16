package dev.relicpowers;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/** Small helpers used everywhere. */
public final class Util {
    private Util() {}

    private static RelicPowers plugin() { return RelicPowers.get(); }

    // ---------- text ----------
    public static Component c(String legacy) {
        return LegacyComponentSerializer.legacySection().deserialize(legacy).decoration(TextDecoration.ITALIC, false);
    }
    public static void msg(Player p, String s) { p.sendMessage(c(s)); }
    public static void bar(Player p, String s) { p.sendActionBar(c(s)); }

    // ---------- sound / particles ----------
    public static void sound(Location l, String key, float vol, float pitch) {
        if (l.getWorld() != null) l.getWorld().playSound(l, key, SoundCategory.PLAYERS, vol, pitch);
    }
    public static void p(Particle part, Location l, int n, double dx, double dy, double dz, double speed) {
        if (l.getWorld() != null) l.getWorld().spawnParticle(part, l, n, dx, dy, dz, speed);
    }
    public static void dust(Location l, Color col, float size, int n) {
        if (l.getWorld() != null) l.getWorld().spawnParticle(Particle.DUST, l, n, 0.05, 0.05, 0.05, 0, new Particle.DustOptions(col, size));
    }

    // ---------- random ----------
    public static boolean chance(double p) { return ThreadLocalRandom.current().nextDouble() < p; }
    public static double rnd(double a, double b) { return a + ThreadLocalRandom.current().nextDouble() * (b - a); }
    public static int rndInt(int bound) { return ThreadLocalRandom.current().nextInt(bound); }
    public static int ticks(double sec) { return (int) Math.round(sec * 20.0); }

    // ---------- potion effects ----------
    /** Visible effect (shows the icon). */
    public static void eff(LivingEntity e, PotionEffectType t, double sec, int amp) {
        e.addPotionEffect(new PotionEffect(t, ticks(sec), amp, false, true, true));
    }
    /** Hidden, self-refreshing effect for passives (call every tick-loop). */
    public static void passive(LivingEntity e, PotionEffectType t, int amp) {
        e.addPotionEffect(new PotionEffect(t, 40, amp, true, false, false));
    }

    // ---------- scheduling ----------
    public static void later(int ticks, Runnable r) {
        Bukkit.getScheduler().runTaskLater(plugin(), r, Math.max(1, ticks));
    }
    /** Runs step(0..times-1) every `period` ticks, then done (may be null). */
    public static void repeat(int period, int times, IntConsumer step, Runnable done) {
        new BukkitRunnable() {
            int i = 0;
            @Override public void run() {
                if (i >= times) {
                    cancel();
                    if (done != null) done.run();
                    return;
                }
                step.accept(i++);
            }
        }.runTaskTimer(plugin(), 0L, Math.max(1, period));
    }

    // ---------- health / damage ----------
    public static double maxHealth(LivingEntity e) {
        AttributeInstance a = e.getAttribute(Attribute.MAX_HEALTH);
        return a == null ? 20.0 : a.getValue();
    }
    public static void heal(LivingEntity e, double amt) {
        e.setHealth(Math.max(0.5, Math.min(maxHealth(e), e.getHealth() + amt)));
    }
    /** Normal (armor-reduced) damage that will not trigger relic on-hit passives. */
    public static void dmg(LivingEntity t, double amt, Entity src) {
        boolean old = PowerManager.internal;
        PowerManager.internal = true;
        try {
            if (src instanceof LivingEntity) t.damage(amt, src); else t.damage(amt);
        } finally {
            PowerManager.internal = old;
        }
    }
    /** Damage that ignores armor (still respects totems when it would kill). */
    public static void trueDamage(LivingEntity t, double amt, Entity src) {
        if (t.isDead() || !t.isValid()) return;
        if (t instanceof Player) {
            GameMode gm = ((Player) t).getGameMode();
            if (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR) return;
        }
        double left = t.getHealth() - amt;
        if (left > 0.0) {
            t.setHealth(left);
            t.playHurtAnimation(0f);
        } else {
            dmg(t, 10000.0, src);
        }
    }

    // ---------- finding things ----------
    public static List<Player> playersNear(Location c, double r, Entity exclude) {
        List<Player> out = new ArrayList<>();
        if (c.getWorld() == null) return out;
        double r2 = r * r;
        for (Player o : c.getWorld().getPlayers()) {
            if (o == exclude || o.isDead() || o.getGameMode() == GameMode.SPECTATOR) continue;
            if (o.getLocation().distanceSquared(c) <= r2) out.add(o);
        }
        return out;
    }
    public static List<LivingEntity> livingNear(Location c, double r, Entity exclude) {
        List<LivingEntity> out = new ArrayList<>();
        if (c.getWorld() == null) return out;
        double r2 = r * r;
        for (Entity e : c.getWorld().getNearbyEntities(c, r, r, r)) {
            if (e == exclude) continue;
            if (!(e instanceof LivingEntity)) continue;
            if (e instanceof ArmorStand) continue;
            LivingEntity le = (LivingEntity) e;
            if (le instanceof Player && ((Player) le).getGameMode() == GameMode.SPECTATOR) continue;
            if (le.getLocation().distanceSquared(c) <= r2 + 4.0) out.add(le);
        }
        return out;
    }
    public static Player nearestPlayer(Player self, double r) {
        Player best = null;
        double bd = Double.MAX_VALUE;
        for (Player o : playersNear(self.getLocation(), r, self)) {
            double d = o.getLocation().distanceSquared(self.getLocation());
            if (d < bd) { bd = d; best = o; }
        }
        return best;
    }

    // ---------- shapes ----------
    public static void sphere(Location c, double r, int n, Consumer<Location> fn) {
        double ga = Math.PI * (3.0 - Math.sqrt(5.0));
        for (int i = 0; i < n; i++) {
            double y = 1.0 - (i / (double) Math.max(1, n - 1)) * 2.0;
            double rad = Math.sqrt(Math.max(0.0, 1.0 - y * y));
            double th = ga * i;
            fn.accept(c.clone().add(Math.cos(th) * rad * r, y * r, Math.sin(th) * rad * r));
        }
    }
    public static void ring(Location c, double r, int n, Consumer<Location> fn) {
        for (int i = 0; i < n; i++) {
            double a = (Math.PI * 2.0 * i) / n;
            fn.accept(c.clone().add(Math.cos(a) * r, 0, Math.sin(a) * r));
        }
    }
    public static void line(Location a, Location b, double step, Consumer<Location> fn) {
        Vector d = b.toVector().subtract(a.toVector());
        double len = d.length();
        if (len < 0.01) return;
        d.normalize().multiply(step);
        Location cur = a.clone();
        for (double t = 0; t < len; t += step) {
            fn.accept(cur.clone());
            cur.add(d);
        }
    }

    // ---------- world checks ----------
    /** Feet + head space free and not lava. */
    public static boolean passable(Location l) {
        Block b = l.getBlock();
        Block h = l.clone().add(0, 1, 0).getBlock();
        return b.isPassable() && h.isPassable() && b.getType() != Material.LAVA && h.getType() != Material.LAVA;
    }
    public static boolean isStoneish(Material m) {
        String n = m.name();
        if (n.contains("SANDSTONE") || n.contains("GLOWSTONE") || n.contains("REDSTONE")) return false;
        return n.equals("STONE") || n.contains("COBBLESTONE") || n.contains("DEEPSLATE") || n.equals("ANDESITE")
                || n.equals("DIORITE") || n.equals("GRANITE") || n.equals("TUFF") || n.contains("STONE_BRICKS")
                || n.equals("BLACKSTONE") || n.equals("BASALT") || n.equals("CALCITE");
    }

    // ---------- attributes (transient, so they never stick if the plugin is removed) ----------
    public static void ensureMod(LivingEntity e, Attribute a, String key, double amt, AttributeModifier.Operation op) {
        AttributeInstance inst = e.getAttribute(a);
        if (inst == null) return;
        NamespacedKey nk = new NamespacedKey(plugin(), key);
        boolean wasFull = a == Attribute.MAX_HEALTH && e.getHealth() >= inst.getValue() - 0.01;
        for (AttributeModifier m : new ArrayList<>(inst.getModifiers())) {
            if (m.getKey().equals(nk)) {
                if (m.getAmount() == amt && m.getOperation() == op) return;
                inst.removeModifier(m);
            }
        }
        inst.addTransientModifier(new AttributeModifier(nk, amt, op));
        // players at full health get the new hearts filled in (so relogging can't be used to heal)
        if (wasFull) e.setHealth(inst.getValue());
    }
    public static void clearMod(LivingEntity e, Attribute a, String key) {
        AttributeInstance inst = e.getAttribute(a);
        if (inst == null) return;
        NamespacedKey nk = new NamespacedKey(plugin(), key);
        boolean removed = false;
        for (AttributeModifier m : new ArrayList<>(inst.getModifiers())) {
            if (m.getKey().equals(nk)) { inst.removeModifier(m); removed = true; }
        }
        if (removed && a == Attribute.MAX_HEALTH && e.getHealth() > inst.getValue()) e.setHealth(inst.getValue());
    }

    // ---------- items ----------
    /** Gives one item back a tick later (used by "chance not to consume" passives). */
    public static void giveBack(Player p, Material m) {
        if (p.getGameMode() == GameMode.CREATIVE) return;
        later(1, () -> {
            Map<Integer, ItemStack> left = p.getInventory().addItem(new ItemStack(m));
            for (ItemStack i : left.values()) p.getWorld().dropItemNaturally(p.getLocation(), i);
        });
    }
}
