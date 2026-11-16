package dev.relicpowers;

import dev.relicpowers.Power.Rarity;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Heavens Eye, Ace, Manifestation (legendary) and the unique Mace */
final class PowersD {
    private PowersD() {}

    private static double armorReduction(double armor, double tough, double dmg) {
        double v = Math.min(20.0, Math.max(armor / 5.0, armor - dmg / (2.0 + tough / 4.0)));
        return v / 25.0;
    }

    /** Multiplier on base damage so that the target behaves as if `ignore` (0..1) of its armor were gone. */
    private static double armorMult(LivingEntity v, double base, double ignore) {
        AttributeInstance ar = v.getAttribute(Attribute.ARMOR);
        AttributeInstance tg = v.getAttribute(Attribute.ARMOR_TOUGHNESS);
        double armor = ar == null ? 0 : ar.getValue();
        double tough = tg == null ? 0 : tg.getValue();
        double r1 = armorReduction(armor, tough, base);
        double r2 = armorReduction(armor * (1.0 - ignore), tough, base);
        return (1.0 - r2) / Math.max(0.0001, 1.0 - r1);
    }

    static void register(List<Power> out, RelicPowers pl) {

        // ------------------------------------------------------------ HEAVENS EYE (legendary)
        out.add(new Power(pl, "heavens_eye", "Heavens Eye", "§d", Material.NETHER_STAR, Rarity.LEGENDARY,
                "Immune to drowning and lava damage",
                "Pull yourself and the nearest player into another dimension for 20 seconds - when it ends, players who were nearby take 3 hearts", 90,
                null, 0) {
            @Override public void onDamaged(Player p, EntityDamageEvent e, int tier) {
                EntityDamageEvent.DamageCause c = e.getCause();
                if (c == EntityDamageEvent.DamageCause.DROWNING) {
                    e.setCancelled(true);
                } else if (c == EntityDamageEvent.DamageCause.LAVA) {
                    e.setCancelled(true);
                    p.setFireTicks(0);
                }
            }
            @Override public boolean ability(Player p, int slot, int tier) {
                Player t = Util.nearestPlayer(p, 30);
                if (t == null) {
                    Util.bar(p, "§cNo player nearby!");
                    return false;
                }
                return pl.realm.start(p, t, tier >= 2 ? 30 : 20, tier >= 2 ? 8.0 : 6.0, 12);
            }
        });

        // ------------------------------------------------------------ ACE (legendary)
        out.add(new Power(pl, "ace", "Ace", "§f", Material.NETHER_STAR, Rarity.LEGENDARY,
                "Immune to mobs. Every 3rd hit steals 1.5 hearts and heals you 1 heart",
                "Your hits ignore 20% of armor and you gain Resistance II for 10 seconds", 45, null, 0) {
            private final Map<UUID, Integer> hits = new HashMap<>();
            @Override public boolean mobImmune() { return true; }
            @Override public void onDamaged(Player p, EntityDamageEvent e, int tier) {
                if (!(e instanceof EntityDamageByEntityEvent)) return;
                Entity d = ((EntityDamageByEntityEvent) e).getDamager();
                if (d instanceof Projectile && ((Projectile) d).getShooter() instanceof Mob) { e.setCancelled(true); return; }
                if (d instanceof Mob) e.setCancelled(true);
            }
            @Override public void onAttack(Player p, LivingEntity v, EntityDamageByEntityEvent e, int tier) {
                UUID id = p.getUniqueId();
                if (pl.mgr.timer(id, "ace_pierce")) {
                    e.setDamage(e.getDamage() * armorMult(v, e.getDamage(), tier >= 2 ? 0.35 : 0.2));
                }
                int c = hits.merge(id, 1, Integer::sum);
                if (c >= 3) {
                    hits.put(id, 0);
                    final double steal = tier >= 2 ? 4.0 : 3.0;
                    final double heal = tier >= 2 ? 3.0 : 2.0;
                    Util.later(1, () -> {
                        if (v.isDead() || !p.isOnline()) return;
                        Util.trueDamage(v, steal, p);
                        Util.heal(p, heal);
                        Util.p(Particle.HEART, p.getLocation().add(0, 2, 0), 4, 0.3, 0.2, 0.3, 0);
                        Util.p(Particle.CRIT, v.getLocation().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.2);
                    });
                }
            }
            @Override public void cleanup(UUID id) { hits.remove(id); }
            @Override public boolean ability(Player p, int slot, int tier) {
                double sec = tier >= 2 ? 14 : 10;
                pl.mgr.setTimer(p.getUniqueId(), "ace_pierce", sec);
                Util.eff(p, PotionEffectType.RESISTANCE, sec, 1);
                Util.sound(p.getLocation(), "item.trident.return", 1f, 1.5f);
                Util.p(Particle.CRIT, p.getLocation().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0.3);
                return true;
            }
        });

        // ------------------------------------------------------------ MANIFESTATION (legendary)
        out.add(new Power(pl, "manifestation", "Manifestation", "§b", Material.NETHER_STAR, Rarity.LEGENDARY,
                "Permanent Strength I, Speed I, Regeneration I, 4 extra hearts and 10% less knockback",
                "Strength II, Speed III, Resistance II, Regeneration II and teleport behind the nearest player", 60, null, 0) {
            @Override public void tick(Player p, int tier) {
                Util.passive(p, PotionEffectType.STRENGTH, 0);
                Util.passive(p, PotionEffectType.SPEED, 0);
                Util.passive(p, PotionEffectType.REGENERATION, tier >= 2 ? 1 : 0);
                Util.ensureMod(p, Attribute.MAX_HEALTH, "manif_hp", tier >= 2 ? 12 : 8, AttributeModifier.Operation.ADD_NUMBER);
                Util.ensureMod(p, Attribute.KNOCKBACK_RESISTANCE, "manif_kb", tier >= 2 ? 0.2 : 0.1, AttributeModifier.Operation.ADD_NUMBER);
            }
            @Override public void onRemove(Player p) {
                Util.clearMod(p, Attribute.MAX_HEALTH, "manif_hp");
                Util.clearMod(p, Attribute.KNOCKBACK_RESISTANCE, "manif_kb");
            }
            @Override public boolean ability(Player p, int slot, int tier) {
                double sec = tier >= 2 ? 16 : 12;
                Util.eff(p, PotionEffectType.STRENGTH, sec, 1);
                Util.eff(p, PotionEffectType.SPEED, sec, 2);
                Util.eff(p, PotionEffectType.RESISTANCE, sec, 1);
                Util.eff(p, PotionEffectType.REGENERATION, sec, 1);
                Player t = Util.nearestPlayer(p, 25);
                if (t != null) {
                    double yaw = Math.toRadians(t.getLocation().getYaw());
                    Vector face = new Vector(-Math.sin(yaw), 0, Math.cos(yaw));
                    Location dest = t.getLocation().clone().subtract(face.multiply(1.6));
                    dest.setYaw(t.getLocation().getYaw());
                    dest.setPitch(0f);
                    if (Util.passable(dest)) {
                        Util.p(Particle.PORTAL, p.getLocation().add(0, 1, 0), 40, 0.3, 0.6, 0.3, 0.5);
                        p.teleport(dest);
                        Util.p(Particle.REVERSE_PORTAL, dest.clone().add(0, 1, 0), 40, 0.3, 0.6, 0.3, 0.3);
                    } else {
                        Util.bar(p, "§7No room to appear behind them.");
                    }
                }
                Util.sound(p.getLocation(), "entity.enderman.teleport", 1f, 0.8f);
                Util.sound(p.getLocation(), "block.beacon.activate", 0.8f, 1.2f);
                return true;
            }
        });

        // ------------------------------------------------------------ THE MACE (unique)
        out.add(new Power(pl, "mace", "The Mace", "§4", Material.MACE, Rarity.UNIQUE,
                "The only Mace on the server. Kill the holder to take it.",
                "Double-tap sneak to dash forward", 6,
                "Launch skyward, then slam down and send out a shockwave", 25) {
            private final Map<UUID, Long> lastSneak = new HashMap<>();
            @Override public void onSneak(Player p, PlayerToggleSneakEvent e, int tier) {
                long now = System.currentTimeMillis();
                Long prev = lastSneak.put(p.getUniqueId(), now);
                if (prev != null && now - prev < 400L) {
                    lastSneak.remove(p.getUniqueId());
                    pl.mgr.useAbility(p, 1);
                }
            }
            @Override public void cleanup(UUID id) { lastSneak.remove(id); }
            @Override public boolean ability(Player p, int slot, int tier) {
                UUID id = p.getUniqueId();
                if (slot == 1) {
                    double yaw = Math.toRadians(p.getLocation().getYaw());
                    Vector d = new Vector(-Math.sin(yaw), 0.2, Math.cos(yaw));
                    d.setX(d.getX() * 2.0);
                    d.setZ(d.getZ() * 2.0);
                    p.setVelocity(d);
                    pl.mgr.setTimer(id, "nofall", 2);
                    Util.sound(p.getLocation(), "entity.breeze.wind_burst", 1f, 1.4f);
                    Util.repeat(1, 8, i -> Util.p(Particle.CLOUD, p.getLocation().add(0, 0.3, 0), 6, 0.2, 0.1, 0.2, 0.02), null);
                    return true;
                }
                p.setVelocity(new Vector(0, 1.6, 0));
                pl.mgr.setTimer(id, "nofall", 10);
                Util.sound(p.getLocation(), "entity.breeze.wind_burst", 1f, 0.7f);
                new BukkitRunnable() {
                    int t = 0;
                    boolean slammed = false;
                    @Override public void run() {
                        t++;
                        if (!p.isOnline() || p.isDead() || t > 140) { cancel(); return; }
                        if (!slammed) {
                            if (t >= 14) {
                                slammed = true;
                                p.setVelocity(new Vector(0, -3.0, 0));
                                Util.sound(p.getLocation(), "entity.iron_golem.attack", 1f, 0.6f);
                            } else {
                                Util.p(Particle.CLOUD, p.getLocation(), 4, 0.2, 0.1, 0.2, 0.01);
                            }
                        } else if (p.isOnGround() || t > 110) {
                            Location l = p.getLocation();
                            for (LivingEntity le : Util.livingNear(l, 5, p)) {
                                Util.dmg(le, 6.0, p);
                                Vector away = le.getLocation().toVector().subtract(l.toVector());
                                if (away.lengthSquared() < 0.01) away = new Vector(0, 0, 0);
                                else away.normalize();
                                le.setVelocity(away.multiply(1.1).setY(0.9));
                            }
                            Util.ring(l.clone().add(0, 0.2, 0), 2.0, 24, x -> Util.p(Particle.CLOUD, x, 3, 0.1, 0.1, 0.1, 0.1));
                            Util.ring(l.clone().add(0, 0.2, 0), 4.0, 40, x -> Util.p(Particle.CLOUD, x, 3, 0.1, 0.1, 0.1, 0.1));
                            Util.p(Particle.EXPLOSION, l, 1, 0, 0, 0, 0);
                            Util.sound(l, "entity.generic.explode", 1f, 0.8f);
                            Util.sound(l, "item.mace.smash_ground", 1f, 1f);
                            cancel();
                        }
                    }
                }.runTaskTimer(pl, 1L, 1L);
                return true;
            }
        });
    }
}
