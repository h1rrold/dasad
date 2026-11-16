package dev.relicpowers;

import dev.relicpowers.Power.Rarity;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.SmallFireball;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Stormseye, Wrathborn, Eclipse, Hephaestus, Gambler, Guardian, Tempest */
final class PowersB {
    private PowersB() {}

    static void register(List<Power> out, RelicPowers pl) {

        // ------------------------------------------------------------ STORMSEYE
        out.add(new Power(pl, "stormseye", "Stormseye", "§3", Material.BOOK, Rarity.COMMON,
                "Speed and Dolphin's Grace",
                "Speed IV and a fake player of you spawns every few seconds", 45, null, 0) {
            @Override public void tick(Player p, int tier) {
                Util.passive(p, PotionEffectType.SPEED, tier >= 2 ? 1 : 0);
                Util.passive(p, PotionEffectType.DOLPHINS_GRACE, 0);
            }
            @Override public boolean ability(Player p, int slot, int tier) {
                double sec = tier >= 2 ? 12 : 8;
                Util.eff(p, PotionEffectType.SPEED, sec, 3);
                Util.sound(p.getLocation(), "entity.lightning_bolt.thunder", 0.6f, 1.6f);
                Util.repeat(40, tier >= 2 ? 6 : 4, i -> {
                    if (p.isOnline()) Decoys.spawn(p, 1, Util.ticks(4));
                }, null);
                return true;
            }
        });

        // ------------------------------------------------------------ WRATHBORN
        out.add(new Power(pl, "wrathborn", "Wrathborn", "§c", Material.BOOK, Rarity.COMMON,
                "Eating a golden apple gives you 4 absorption hearts",
                "Gain extra reach", 40, null, 0) {
            @Override public void onConsume(Player p, PlayerItemConsumeEvent e, int tier) {
                Material m = e.getItem().getType();
                if (m != Material.GOLDEN_APPLE && m != Material.ENCHANTED_GOLDEN_APPLE) return;
                final double hp = tier >= 2 ? 12.0 : 8.0;
                Util.later(2, () -> {
                    if (!p.isOnline()) return;
                    p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, Util.ticks(120), (int) (hp / 4.0) - 1, false, true, true));
                    p.setAbsorptionAmount(Math.max(p.getAbsorptionAmount(), hp));
                    Util.p(Particle.HEART, p.getLocation().add(0, 2, 0), 8, 0.4, 0.3, 0.4, 0);
                });
            }
            @Override public void tick(Player p, int tier) {
                if (pl.mgr.timer(p.getUniqueId(), "wrath_reach")) {
                    Util.ensureMod(p, Attribute.ENTITY_INTERACTION_RANGE, "wrath_reach", tier >= 2 ? 4 : 3, AttributeModifier.Operation.ADD_NUMBER);
                } else {
                    Util.clearMod(p, Attribute.ENTITY_INTERACTION_RANGE, "wrath_reach");
                }
            }
            @Override public void onRemove(Player p) {
                Util.clearMod(p, Attribute.ENTITY_INTERACTION_RANGE, "wrath_reach");
            }
            @Override public boolean ability(Player p, int slot, int tier) {
                pl.mgr.setTimer(p.getUniqueId(), "wrath_reach", tier >= 2 ? 15 : 10);
                Util.ensureMod(p, Attribute.ENTITY_INTERACTION_RANGE, "wrath_reach", tier >= 2 ? 4 : 3, AttributeModifier.Operation.ADD_NUMBER);
                Util.sound(p.getLocation(), "entity.ravager.roar", 0.7f, 1.3f);
                Util.ring(p.getLocation().add(0, 1, 0), 1.2, 20, l -> Util.dust(l, Color.fromRGB(220, 20, 20), 1.4f, 2));
                return true;
            }
        });

        // ------------------------------------------------------------ ECLIPSE
        out.add(new Power(pl, "eclipse", "Eclipse", "§e", Material.BOOK, Rarity.COMMON,
                "Every 3-hit combo gives a damage buff until you get hit",
                "Slip into spectator mode for a few seconds", 60, null, 0) {
            private final Map<UUID, Integer> combo = new HashMap<>();
            private final Set<UUID> buffed = new HashSet<>();
            private final Map<UUID, GameMode> prevMode = new HashMap<>();
            private final Map<UUID, Location> startLoc = new HashMap<>();

            @Override public void onAttack(Player p, LivingEntity v, EntityDamageByEntityEvent e, int tier) {
                UUID id = p.getUniqueId();
                if (buffed.contains(id)) {
                    e.setDamage(e.getDamage() * (tier >= 2 ? 1.4 : 1.25));
                    Util.p(Particle.FLAME, v.getLocation().add(0, 1, 0), 6, 0.2, 0.3, 0.2, 0.01);
                }
                int c = combo.merge(id, 1, Integer::sum);
                if (c >= 3 && buffed.add(id)) {
                    Util.bar(p, "§6Eclipse empowered! Don't get hit.");
                    Util.sound(p.getLocation(), "block.beacon.activate", 0.6f, 1.7f);
                }
            }
            @Override public void onDamaged(Player p, EntityDamageEvent e, int tier) {
                UUID id = p.getUniqueId();
                combo.remove(id);
                if (buffed.remove(id)) Util.bar(p, "§7Eclipse buff lost");
            }
            @Override public void tick(Player p, int tier) {
                if (buffed.contains(p.getUniqueId())) {
                    Util.p(Particle.FLAME, p.getLocation().add(0, 2.2, 0), 2, 0.2, 0.1, 0.2, 0.01);
                }
            }
            private void end(Player p) {
                UUID id = p.getUniqueId();
                GameMode g = prevMode.remove(id);
                Location s = startLoc.remove(id);
                if (g == null) return;
                pl.mgr.clearTimer(id, "eclipse");
                if (!p.isOnline()) return;
                p.setGameMode(g == GameMode.SPECTATOR ? GameMode.SURVIVAL : g);
                if (!Util.passable(p.getLocation()) && s != null) p.teleport(s);
                Util.p(Particle.SMOKE, p.getLocation().add(0, 1, 0), 30, 0.4, 0.8, 0.4, 0.03);
            }
            @Override public void onRemove(Player p) { end(p); }
            @Override public void cleanup(UUID id) { combo.remove(id); buffed.remove(id); }
            @Override public boolean ability(Player p, int slot, int tier) {
                UUID id = p.getUniqueId();
                if (prevMode.containsKey(id)) return false;
                double sec = tier >= 2 ? 8 : 5;
                prevMode.put(id, p.getGameMode());
                startLoc.put(id, p.getLocation().clone());
                pl.mgr.setTimer(id, "eclipse", sec);
                p.setGameMode(GameMode.SPECTATOR);
                Util.p(Particle.SMOKE, p.getLocation().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0.04);
                Util.sound(p.getLocation(), "entity.phantom.flap", 1f, 0.6f);
                Util.later(Util.ticks(sec), () -> end(p));
                return true;
            }
        });

        // ------------------------------------------------------------ HEPHAESTUS
        out.add(new Power(pl, "hephaestus", "Hephaestus", "§6", Material.BOOK, Rarity.COMMON,
                "Gain Weaving and Fire Resistance when you hit",
                "Shoot a fireball that deals 3 hearts", 15, null, 0) {
            @Override public void onAttack(Player p, LivingEntity v, EntityDamageByEntityEvent e, int tier) {
                double sec = tier >= 2 ? 8 : 5;
                Util.eff(p, PotionEffectType.WEAVING, sec, 0);
                Util.eff(p, PotionEffectType.FIRE_RESISTANCE, sec, 0);
            }
            @Override public boolean ability(Player p, int slot, int tier) {
                final SmallFireball fb = p.launchProjectile(SmallFireball.class, p.getEyeLocation().getDirection().multiply(1.7));
                fb.setIsIncendiary(false);
                fb.setYield(0f);
                pl.mgr.tagProjectile(fb, this, tier);
                Util.sound(p.getLocation(), "entity.blaze.shoot", 1f, 0.8f);
                new BukkitRunnable() {
                    int t = 0;
                    @Override public void run() {
                        if (!fb.isValid() || fb.isDead() || t++ > 100) { cancel(); return; }
                        Util.p(Particle.FLAME, fb.getLocation(), 4, 0.1, 0.1, 0.1, 0.01);
                    }
                }.runTaskTimer(pl, 0L, 1L);
                return true;
            }
            @Override public void onProjectileDamage(Player shooter, Projectile proj, LivingEntity victim, EntityDamageByEntityEvent e, int tier) {
                e.setDamage(tier >= 2 ? 8.0 : 6.0);
                victim.setFireTicks(60);
            }
            @Override public void onProjectileHit(Player shooter, Projectile proj, ProjectileHitEvent e, int tier) {
                Util.p(Particle.LAVA, proj.getLocation(), 12, 0.2, 0.2, 0.2, 0);
                Util.sound(proj.getLocation(), "block.fire.extinguish", 0.6f, 1.2f);
            }
        });

        // ------------------------------------------------------------ GAMBLER
        out.add(new Power(pl, "gambler", "Gambler", "§a", Material.BOOK, Rarity.COMMON,
                "Chance for a random buff or debuff while fighting",
                "Gamble: something great... or something awful", 35, null, 0) {
            private void small(Player p, int tier) {
                boolean good = Util.chance(tier >= 2 ? 0.75 : 0.6);
                if (good) {
                    switch (Util.rndInt(5)) {
                        case 0: Util.eff(p, PotionEffectType.SPEED, 7, 1); Util.bar(p, "§aGambler: Speed II!"); break;
                        case 1: Util.eff(p, PotionEffectType.STRENGTH, 7, 0); Util.bar(p, "§aGambler: Strength!"); break;
                        case 2: Util.eff(p, PotionEffectType.REGENERATION, 7, 1); Util.bar(p, "§aGambler: Regeneration!"); break;
                        case 3: Util.eff(p, PotionEffectType.RESISTANCE, 7, 0); Util.bar(p, "§aGambler: Resistance!"); break;
                        default: Util.eff(p, PotionEffectType.ABSORPTION, 20, 1); Util.bar(p, "§aGambler: Absorption!"); break;
                    }
                    Util.p(Particle.HAPPY_VILLAGER, p.getLocation().add(0, 1.5, 0), 8, 0.3, 0.3, 0.3, 0);
                } else {
                    switch (Util.rndInt(4)) {
                        case 0: Util.eff(p, PotionEffectType.SLOWNESS, 6, 0); Util.bar(p, "§cGambler: Slowness..."); break;
                        case 1: Util.eff(p, PotionEffectType.WEAKNESS, 6, 0); Util.bar(p, "§cGambler: Weakness..."); break;
                        case 2: Util.eff(p, PotionEffectType.MINING_FATIGUE, 6, 0); Util.bar(p, "§cGambler: Mining Fatigue..."); break;
                        default: Util.eff(p, PotionEffectType.HUNGER, 8, 1); Util.bar(p, "§cGambler: Hunger..."); break;
                    }
                    Util.p(Particle.ANGRY_VILLAGER, p.getLocation().add(0, 1.5, 0), 4, 0.3, 0.3, 0.3, 0);
                }
            }
            @Override public void onAttack(Player p, LivingEntity v, EntityDamageByEntityEvent e, int tier) {
                if (Util.chance(0.12)) small(p, tier);
            }
            @Override public void onDamaged(Player p, EntityDamageEvent e, int tier) {
                if (Util.chance(0.08)) small(p, tier);
            }
            @Override public boolean ability(Player p, int slot, int tier) {
                boolean good = Util.chance(tier >= 2 ? 0.65 : 0.5);
                Util.sound(p.getLocation(), "block.note_block.chime", 1f, 1.2f);
                if (good) {
                    switch (Util.rndInt(3)) {
                        case 0:
                            Util.eff(p, PotionEffectType.STRENGTH, 12, 1);
                            Util.eff(p, PotionEffectType.SPEED, 12, 2);
                            Util.eff(p, PotionEffectType.RESISTANCE, 12, 1);
                            Util.msg(p, "§a§lJACKPOT! §7Strength II, Speed III, Resistance II");
                            break;
                        case 1:
                            p.setHealth(Util.maxHealth(p));
                            Util.eff(p, PotionEffectType.ABSORPTION, 15, 3);
                            Util.msg(p, "§a§lLUCKY! §7Fully healed + absorption");
                            break;
                        default:
                            Util.eff(p, PotionEffectType.REGENERATION, 8, 2);
                            Util.eff(p, PotionEffectType.HASTE, 15, 2);
                            Util.eff(p, PotionEffectType.SPEED, 15, 1);
                            Util.msg(p, "§a§lFORTUNE! §7Regeneration III, Haste III, Speed II");
                            break;
                    }
                    Util.sound(p.getLocation(), "entity.villager.yes", 1f, 1.2f);
                    Util.p(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0.3);
                } else {
                    switch (Util.rndInt(3)) {
                        case 0:
                            Util.eff(p, PotionEffectType.SLOWNESS, 8, 1);
                            Util.eff(p, PotionEffectType.WEAKNESS, 8, 1);
                            Util.msg(p, "§c§lSNAKE EYES! §7Slowness II + Weakness II");
                            break;
                        case 1:
                            Util.eff(p, PotionEffectType.MINING_FATIGUE, 10, 2);
                            Util.eff(p, PotionEffectType.HUNGER, 10, 2);
                            Util.eff(p, PotionEffectType.NAUSEA, 5, 0);
                            Util.msg(p, "§c§lBUST! §7Fatigue, Hunger and Nausea");
                            break;
                        default:
                            p.setHealth(Math.max(1.0, p.getHealth() - 8.0));
                            p.playHurtAnimation(0f);
                            Util.msg(p, "§c§lPICKPOCKETED! §7You lost 4 hearts");
                            break;
                    }
                    Util.sound(p.getLocation(), "entity.villager.no", 1f, 0.8f);
                    Util.p(Particle.ANGRY_VILLAGER, p.getLocation().add(0, 1.5, 0), 10, 0.4, 0.4, 0.4, 0);
                }
                return true;
            }
        });

        // ------------------------------------------------------------ GUARDIAN
        out.add(new Power(pl, "guardian", "Guardian", "§9", Material.BOOK, Rarity.COMMON,
                "16 extra hearts",
                "A totem protects you for 10 seconds - it pops instead of you dying", 90, null, 0) {
            @Override public void tick(Player p, int tier) {
                Util.ensureMod(p, Attribute.MAX_HEALTH, "guardian_hp", tier >= 2 ? 40 : 32, AttributeModifier.Operation.ADD_NUMBER);
                if (pl.mgr.timer(p.getUniqueId(), "guardian_totem")) {
                    Util.ring(p.getLocation().add(0, 2.3, 0), 0.5, 10, l -> Util.dust(l, Color.fromRGB(255, 215, 0), 1.0f, 1));
                }
            }
            @Override public void onRemove(Player p) { Util.clearMod(p, Attribute.MAX_HEALTH, "guardian_hp"); }
            @Override public void onLethal(Player p, EntityDamageEvent e, int tier) {
                UUID id = p.getUniqueId();
                if (!pl.mgr.timer(id, "guardian_totem")) return;
                e.setCancelled(true);
                pl.mgr.clearTimer(id, "guardian_totem");
                p.setHealth(Math.min(Util.maxHealth(p), 14.0));
                p.setFireTicks(0);
                Util.eff(p, PotionEffectType.REGENERATION, 40, 1);
                Util.eff(p, PotionEffectType.ABSORPTION, 5, 1);
                Util.eff(p, PotionEffectType.FIRE_RESISTANCE, 40, 0);
                Util.p(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 80, 0.4, 0.8, 0.4, 0.5);
                Util.sound(p.getLocation(), "item.totem.use", 1f, 1f);
                Util.msg(p, "§6Your Guardian totem saved you!");
            }
            @Override public boolean ability(Player p, int slot, int tier) {
                pl.mgr.setTimer(p.getUniqueId(), "guardian_totem", tier >= 2 ? 15 : 10);
                Util.sound(p.getLocation(), "item.totem.use", 0.6f, 1.6f);
                Util.p(Particle.END_ROD, p.getLocation().add(0, 1, 0), 30, 0.4, 0.8, 0.4, 0.05);
                Util.msg(p, "§6A totem now watches over you for " + (tier >= 2 ? 15 : 10) + " seconds.");
                return true;
            }
        });

        // ------------------------------------------------------------ TEMPEST
        out.add(new Power(pl, "tempest", "Tempest", "§b", Material.BOOK, Rarity.COMMON,
                "Chance to launch enemies into the air",
                "Launch yourself up and rain arrows like a machine gun", 50, null, 0) {
            @Override public void onAttack(Player p, LivingEntity v, EntityDamageByEntityEvent e, int tier) {
                if (Util.chance(tier >= 2 ? 0.25 : 0.15)) {
                    Vector vel = v.getVelocity();
                    vel.setY(tier >= 2 ? 1.2 : 0.9);
                    v.setVelocity(vel);
                    Util.p(Particle.CLOUD, v.getLocation(), 15, 0.3, 0.1, 0.3, 0.05);
                    Util.sound(v.getLocation(), "entity.breeze.wind_burst", 0.8f, 1.2f);
                }
            }
            @Override public boolean ability(Player p, int slot, int tier) {
                final int shots = tier >= 2 ? 60 : 40;
                final Power self = this;
                final double dmg = tier >= 2 ? 0.9 : 0.7;
                p.setVelocity(new Vector(0, tier >= 2 ? 1.6 : 1.4, 0));
                Util.eff(p, PotionEffectType.SLOW_FALLING, shots * 2 / 20.0 + 3, 0);
                pl.mgr.setTimer(p.getUniqueId(), "nofall", shots * 2 / 20.0 + 6);
                Util.sound(p.getLocation(), "entity.breeze.wind_burst", 1f, 0.8f);
                Util.later(8, () -> Util.repeat(2, shots, i -> {
                    if (!p.isOnline() || p.isDead()) return;
                    Vector aim = p.getEyeLocation().getDirection().add(
                            new Vector(Util.rnd(-0.04, 0.04), Util.rnd(-0.04, 0.04), Util.rnd(-0.04, 0.04))).normalize().multiply(2.6);
                    Arrow a = p.launchProjectile(Arrow.class, aim);
                    a.setDamage(dmg);
                    a.setCritical(false);
                    a.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
                    pl.mgr.tagProjectile(a, self, tier);
                    if (i % 3 == 0) Util.sound(p.getLocation(), "entity.arrow.shoot", 0.5f, 1.6f);
                }, null));
                return true;
            }
            @Override public void onProjectileHit(Player shooter, Projectile proj, ProjectileHitEvent e, int tier) {
                if (e.getHitEntity() instanceof LivingEntity) {
                    final LivingEntity le = (LivingEntity) e.getHitEntity();
                    Util.later(1, () -> le.setNoDamageTicks(0));
                }
                Util.later(1, proj::remove);
            }
        });
    }
}
