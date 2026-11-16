package dev.relicpowers;

import dev.relicpowers.Power.Rarity;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.ThrownExpBottle;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.List;

/** Erebus, Hermes, Artimes, Archanic, Despair, Greed, Lust */
final class PowersA {
    private PowersA() {}

    static void register(List<Power> out, RelicPowers pl) {

        // ------------------------------------------------------------ EREBUS
        out.add(new Power(pl, "erebus", "Erebus", "§5", Material.BOOK, Rarity.COMMON,
                "Chance to blind enemies you hit",
                "Create a sphere of darkness that blinds everyone inside", 45, null, 0) {
            @Override public void onAttack(Player p, LivingEntity v, EntityDamageByEntityEvent e, int tier) {
                if (Util.chance(tier >= 2 ? 0.25 : 0.15)) {
                    Util.eff(v, PotionEffectType.BLINDNESS, tier >= 2 ? 5 : 3, 0);
                    Util.p(Particle.SMOKE, v.getEyeLocation(), 12, 0.25, 0.25, 0.25, 0.02);
                }
            }
            @Override public boolean ability(Player p, int slot, int tier) {
                final Location c = p.getLocation().clone().add(0, 1, 0);
                final double r = tier >= 2 ? 8 : 6;
                final int dur = Util.ticks(tier >= 2 ? 12 : 8);
                Util.sound(c, "entity.wither.spawn", 0.5f, 1.8f);
                new BukkitRunnable() {
                    int t = 0;
                    @Override public void run() {
                        if (t >= dur) { cancel(); return; }
                        if (t % 2 == 0) Util.sphere(c, r, 80, l -> Util.dust(l, Color.fromRGB(45, 0, 70), 1.7f, 1));
                        if (t % 10 == 0) {
                            for (LivingEntity le : Util.livingNear(c, r, p)) Util.eff(le, PotionEffectType.BLINDNESS, 2.5, 0);
                        }
                        t++;
                    }
                }.runTaskTimer(pl, 0L, 1L);
                return true;
            }
        });

        // ------------------------------------------------------------ HERMES
        out.add(new Power(pl, "hermes", "Hermes", "§b", Material.BOOK, Rarity.COMMON,
                "No fall damage + chance to gain Speed II in combat",
                "Boost yourself high into the air", 20, null, 0) {
            private void boost(Player p, int tier) {
                if (Util.chance(tier >= 2 ? 0.35 : 0.2)) {
                    Util.eff(p, PotionEffectType.SPEED, tier >= 2 ? 8 : 5, 1);
                    Util.p(Particle.CLOUD, p.getLocation(), 8, 0.3, 0.1, 0.3, 0.02);
                }
            }
            @Override public void onAttack(Player p, LivingEntity v, EntityDamageByEntityEvent e, int tier) { boost(p, tier); }
            @Override public void onDamaged(Player p, EntityDamageEvent e, int tier) {
                if (e.getCause() == EntityDamageEvent.DamageCause.FALL) { e.setCancelled(true); return; }
                boost(p, tier);
            }
            @Override public boolean ability(Player p, int slot, int tier) {
                Vector v = p.getLocation().getDirection().setY(0);
                if (v.lengthSquared() > 0.001) v.normalize().multiply(0.35);
                v.setY(tier >= 2 ? 1.9 : 1.5);
                p.setVelocity(v);
                Util.sound(p.getLocation(), "entity.firework_rocket.launch", 0.8f, 1.2f);
                Util.repeat(2, 15, i -> Util.p(Particle.CLOUD, p.getLocation(), 6, 0.25, 0.1, 0.25, 0.01), null);
                return true;
            }
        });

        // ------------------------------------------------------------ ARTIMES
        out.add(new Power(pl, "artimes", "Artimes", "§f", Material.BOOK, Rarity.COMMON,
                "While you are airborne, nearby players glow",
                "Teleport a few blocks forward", 8, null, 0) {
            @Override public void tick(Player p, int tier) {
                if (p.isOnGround() || p.isInWater()) return;
                double r = tier >= 2 ? 30 : 20;
                for (Player o : Util.playersNear(p.getLocation(), r, p)) Util.passive(o, PotionEffectType.GLOWING, 0);
            }
            @Override public boolean ability(Player p, int slot, int tier) {
                Vector dir = p.getEyeLocation().getDirection().normalize();
                double max = tier >= 2 ? 14 : 9;
                Location start = p.getLocation().clone();
                Location last = start.clone();
                for (double d = 0.5; d <= max; d += 0.5) {
                    Location test = start.clone().add(dir.clone().multiply(d));
                    if (!Util.passable(test)) break;
                    last = test;
                }
                if (last.distanceSquared(start) < 1.0) {
                    Util.bar(p, "§cSomething is in the way!");
                    return false;
                }
                Util.line(start.clone().add(0, 1, 0), last.clone().add(0, 1, 0), 0.4, l -> Util.p(Particle.END_ROD, l, 1, 0, 0, 0, 0));
                last.setYaw(start.getYaw());
                last.setPitch(start.getPitch());
                p.teleport(last);
                Util.p(Particle.PORTAL, last.clone().add(0, 1, 0), 40, 0.3, 0.6, 0.3, 0.4);
                Util.sound(last, "entity.enderman.teleport", 0.8f, 1.5f);
                return true;
            }
        });

        // ------------------------------------------------------------ ARCHANIC
        out.add(new Power(pl, "archanic", "Archanic", "§9", Material.BOOK, Rarity.COMMON,
                "Chance to gain Haste on hit",
                "Surge of arcane speed (Speed II)", 30,
                "Arcane ward (Resistance II)", 35) {
            @Override public void onAttack(Player p, LivingEntity v, EntityDamageByEntityEvent e, int tier) {
                if (Util.chance(tier >= 2 ? 0.35 : 0.2)) {
                    Util.eff(p, PotionEffectType.HASTE, tier >= 2 ? 8 : 6, tier >= 2 ? 2 : 1);
                    Util.p(Particle.ENCHANT, p.getLocation().add(0, 1, 0), 25, 0.4, 0.6, 0.4, 0.5);
                }
            }
            @Override public boolean ability(Player p, int slot, int tier) {
                if (slot == 1) {
                    Util.eff(p, PotionEffectType.SPEED, tier >= 2 ? 12 : 10, tier >= 2 ? 2 : 1);
                    Util.sound(p.getLocation(), "entity.evoker.cast_spell", 0.8f, 1.4f);
                } else {
                    Util.eff(p, PotionEffectType.RESISTANCE, tier >= 2 ? 10 : 8, tier >= 2 ? 2 : 1);
                    Util.sound(p.getLocation(), "block.beacon.activate", 0.8f, 1.6f);
                }
                Util.repeat(2, 10, i -> Util.ring(p.getLocation().add(0, 0.2 + i * 0.2, 0), 0.9, 14,
                        l -> Util.dust(l, Color.fromRGB(80, 140, 255), 1.2f, 1)), null);
                return true;
            }
        });

        // ------------------------------------------------------------ DESPAIR
        out.add(new Power(pl, "despair", "Despair", "§8", Material.BOOK, Rarity.COMMON,
                "Permanent Strength and 2 extra hearts",
                "Grow huge and gain Haste V (your axe swings at sword speed)", 50, null, 0) {
            @Override public void tick(Player p, int tier) {
                Util.passive(p, PotionEffectType.STRENGTH, tier >= 2 ? 1 : 0);
                Util.ensureMod(p, Attribute.MAX_HEALTH, "despair_hp", tier >= 2 ? 8 : 4, AttributeModifier.Operation.ADD_NUMBER);
                if (pl.mgr.timer(p.getUniqueId(), "despair_big")) {
                    Util.ensureMod(p, Attribute.SCALE, "despair_scale", tier >= 2 ? 0.8 : 0.5, AttributeModifier.Operation.ADD_SCALAR);
                    if (p.getInventory().getItemInMainHand().getType().name().endsWith("_AXE")) {
                        Util.ensureMod(p, Attribute.ATTACK_SPEED, "despair_axe", 0.7, AttributeModifier.Operation.ADD_NUMBER);
                    } else {
                        Util.clearMod(p, Attribute.ATTACK_SPEED, "despair_axe");
                    }
                } else {
                    Util.clearMod(p, Attribute.SCALE, "despair_scale");
                    Util.clearMod(p, Attribute.ATTACK_SPEED, "despair_axe");
                }
            }
            @Override public void onRemove(Player p) {
                Util.clearMod(p, Attribute.MAX_HEALTH, "despair_hp");
                Util.clearMod(p, Attribute.SCALE, "despair_scale");
                Util.clearMod(p, Attribute.ATTACK_SPEED, "despair_axe");
            }
            @Override public boolean ability(Player p, int slot, int tier) {
                double sec = tier >= 2 ? 20 : 15;
                pl.mgr.setTimer(p.getUniqueId(), "despair_big", sec);
                Util.eff(p, PotionEffectType.HASTE, sec, 4);
                tick(p, tier);
                Util.sound(p.getLocation(), "entity.ravager.roar", 0.9f, 0.7f);
                Util.p(Particle.LARGE_SMOKE, p.getLocation().add(0, 1, 0), 40, 0.6, 0.9, 0.6, 0.03);
                return true;
            }
        });

        // ------------------------------------------------------------ GREED
        out.add(new Power(pl, "greed", "Greed", "§6", Material.BOOK, Rarity.COMMON,
                "Chance not to consume XP bottles, golden apples or cobwebs + Hero of the Village",
                "Speed III and invisibility", 40, null, 0) {
            private double odds(int tier) { return tier >= 2 ? 0.4 : 0.25; }
            @Override public void tick(Player p, int tier) {
                Util.passive(p, PotionEffectType.HERO_OF_THE_VILLAGE, tier >= 2 ? 1 : 0);
            }
            @Override public void onConsume(Player p, PlayerItemConsumeEvent e, int tier) {
                Material m = e.getItem().getType();
                if ((m == Material.GOLDEN_APPLE || m == Material.ENCHANTED_GOLDEN_APPLE) && Util.chance(odds(tier))) {
                    Util.giveBack(p, m);
                    Util.p(Particle.HAPPY_VILLAGER, p.getLocation().add(0, 1.5, 0), 10, 0.3, 0.3, 0.3, 0);
                }
            }
            @Override public void onLaunch(Player p, ProjectileLaunchEvent e, int tier) {
                if (e.getEntity() instanceof ThrownExpBottle && Util.chance(odds(tier))) {
                    Util.giveBack(p, Material.EXPERIENCE_BOTTLE);
                    Util.p(Particle.HAPPY_VILLAGER, p.getLocation().add(0, 1.5, 0), 10, 0.3, 0.3, 0.3, 0);
                }
            }
            @Override public void onPlace(Player p, BlockPlaceEvent e, int tier) {
                if (e.getBlockPlaced().getType() == Material.COBWEB && Util.chance(odds(tier))) {
                    Util.giveBack(p, Material.COBWEB);
                }
            }
            @Override public boolean ability(Player p, int slot, int tier) {
                double sec = tier >= 2 ? 12 : 8;
                Util.eff(p, PotionEffectType.SPEED, sec, 2);
                Util.eff(p, PotionEffectType.INVISIBILITY, sec, 0);
                Util.sound(p.getLocation(), "entity.experience_orb.pickup", 1f, 0.6f);
                Util.p(Particle.HAPPY_VILLAGER, p.getLocation().add(0, 1, 0), 30, 0.5, 0.8, 0.5, 0);
                return true;
            }
        });

        // ------------------------------------------------------------ LUST
        out.add(new Power(pl, "lust", "Lust", "§d", Material.BOOK, Rarity.COMMON,
                "Chance to gain Regeneration and heal when you hit",
                "Pull every nearby player towards you", 30, null, 0) {
            @Override public void onAttack(Player p, LivingEntity v, EntityDamageByEntityEvent e, int tier) {
                if (Util.chance(tier >= 2 ? 0.25 : 0.15)) {
                    Util.eff(p, PotionEffectType.REGENERATION, 4, 1);
                    Util.heal(p, tier >= 2 ? 4 : 3);
                    Util.p(Particle.HEART, p.getLocation().add(0, 2, 0), 6, 0.4, 0.3, 0.4, 0);
                }
            }
            @Override public boolean ability(Player p, int slot, int tier) {
                final double r = tier >= 2 ? 16 : 12;
                Util.sound(p.getLocation(), "entity.evoker.prepare_summon", 1f, 1.5f);
                Util.repeat(5, 4, i -> {
                    Location me = p.getLocation().add(0, 1, 0);
                    for (Player o : Util.playersNear(me, r, p)) {
                        Vector pull = me.toVector().subtract(o.getLocation().toVector());
                        if (pull.length() < 2.0) continue;
                        o.setVelocity(pull.normalize().multiply(tier >= 2 ? 1.5 : 1.2).setY(0.25));
                        Util.line(o.getLocation().add(0, 1, 0), me, 0.7, l -> Util.p(Particle.HEART, l, 1, 0, 0, 0, 0));
                    }
                    Util.ring(p.getLocation().add(0, 0.2, 0), 1.5 + i, 24, l -> Util.dust(l, Color.fromRGB(255, 60, 160), 1.3f, 1));
                }, null);
                return true;
            }
        });
    }
}
