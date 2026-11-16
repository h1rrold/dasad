package dev.relicpowers;

import dev.relicpowers.Power.Rarity;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Soul Harvester, Magma, Gaia, Bulwark, Chronos, Coinstorm */
final class PowersE {
    private PowersE() {}

    private static final class Snap {
        final Location loc;
        final double hp;
        final long t;
        Snap(Location loc, double hp, long t) { this.loc = loc; this.hp = hp; this.t = t; }
    }

    static void register(List<Power> out, RelicPowers pl) {

        // ------------------------------------------------------------ SOUL HARVESTER
        out.add(new Power(pl, "soul_harvester", "Soul Harvester", "§3", Material.BOOK, Rarity.COMMON,
                "Heal 3 hearts whenever you kill a mob",
                "Plant a voodoo doll. Use it again to activate it and hurt the nearest player to the doll", 45, null, 0) {
            private final Map<UUID, ArmorStand> dolls = new HashMap<>();

            @Override public void onKill(Player p, LivingEntity dead, int tier) {
                if (dead instanceof Player) return;
                Util.heal(p, tier >= 2 ? 10.0 : 6.0);
                Util.p(Particle.SOUL, p.getLocation().add(0, 1, 0), 15, 0.4, 0.6, 0.4, 0.05);
            }
            @Override public void cleanup(UUID id) {
                ArmorStand d = dolls.remove(id);
                if (d != null) d.remove();
            }
            @Override public void onRemove(Player p) { cleanup(p.getUniqueId()); }

            @Override public boolean ability(Player p, int slot, int tier) {
                UUID id = p.getUniqueId();
                ArmorStand doll = dolls.get(id);
                if (doll != null && doll.isValid()) {
                    // activate
                    Player t = null;
                    double best = 30 * 30;
                    for (Player o : Util.playersNear(doll.getLocation(), 30, p)) {
                        double d = o.getLocation().distanceSquared(doll.getLocation());
                        if (d < best) { best = d; t = o; }
                    }
                    if (t == null) {
                        Util.bar(p, "§cNobody is near your voodoo doll.");
                        return false;
                    }
                    final Player target = t;
                    Util.line(doll.getLocation().add(0, 1, 0), target.getLocation().add(0, 1, 0), 0.4,
                            l -> Util.p(Particle.SOUL_FIRE_FLAME, l, 2, 0.05, 0.05, 0.05, 0.01));
                    Util.trueDamage(target, tier >= 2 ? 8.0 : 6.0, p);
                    Util.sound(target.getLocation(), "entity.wither.hurt", 1f, 1.4f);
                    Util.p(Particle.SOUL, target.getLocation().add(0, 1, 0), 30, 0.4, 0.8, 0.4, 0.05);
                    Util.msg(target, "§3A voodoo doll cursed you!");
                    Decoys.pop(doll);
                    dolls.remove(id);
                    return true;
                }
                // plant
                final ArmorStand as = p.getWorld().spawn(p.getLocation(), ArmorStand.class, a -> {
                    a.setSmall(true);
                    a.setArms(true);
                    a.setBasePlate(false);
                    a.setGravity(false);
                    a.setSilent(true);
                    a.setPersistent(false);
                    EntityEquipment eq = a.getEquipment();
                    eq.setHelmet(new ItemStack(Material.WITHER_SKELETON_SKULL));
                    eq.setChestplate(new ItemStack(Material.LEATHER_CHESTPLATE));
                    Decoys.mark(a);
                });
                dolls.put(id, as);
                Util.p(Particle.SOUL, as.getLocation().add(0, 0.5, 0), 20, 0.2, 0.3, 0.2, 0.03);
                Util.sound(as.getLocation(), "entity.zombie_villager.converted", 1f, 0.7f);
                Util.bar(p, "§3Voodoo doll planted. Use the ability again to activate it (30s).");
                new BukkitRunnable() {
                    int t = 0;
                    @Override public void run() {
                        if (!as.isValid()) { cancel(); return; }
                        if (t++ >= 30 * 4) {
                            Decoys.pop(as);
                            dolls.remove(id);
                            cancel();
                            return;
                        }
                        Util.p(Particle.SOUL, as.getLocation().add(0, 0.8, 0), 1, 0.1, 0.1, 0.1, 0.01);
                    }
                }.runTaskTimer(pl, 0L, 5L);
                return false;
            }
        });

        // ------------------------------------------------------------ MAGMA
        out.add(new Power(pl, "magma", "Magma", "§6", Material.BOOK, Rarity.COMMON,
                "Infinite fire resistance, swim fast in lava, and deal more damage while on fire",
                "Clear water and cobwebs around you", 20, null, 0) {
            @Override public void tick(Player p, int tier) {
                Util.passive(p, PotionEffectType.FIRE_RESISTANCE, 0);
            }
            @Override public void onMove(Player p, PlayerMoveEvent e, int tier) {
                if (p.getLocation().getBlock().getType() != Material.LAVA) return;
                Vector dir = e.getTo().toVector().subtract(e.getFrom().toVector());
                dir.setY(0);
                if (dir.lengthSquared() < 0.0004) return;
                dir.normalize().multiply(tier >= 2 ? 0.55 : 0.4);
                Vector cur = p.getVelocity();
                p.setVelocity(new Vector(dir.getX(), cur.getY(), dir.getZ()));
                Util.p(Particle.LAVA, p.getLocation(), 1, 0.2, 0.1, 0.2, 0);
            }
            @Override public void onAttack(Player p, LivingEntity v, EntityDamageByEntityEvent e, int tier) {
                if (p.getFireTicks() > 0) {
                    e.setDamage(e.getDamage() * (tier >= 2 ? 1.5 : 1.3));
                    Util.p(Particle.FLAME, v.getLocation().add(0, 1, 0), 8, 0.2, 0.3, 0.2, 0.02);
                }
            }
            @Override public boolean ability(Player p, int slot, int tier) {
                int r = tier >= 2 ? 10 : 7;
                Location c = p.getLocation();
                World w = c.getWorld();
                int cleared = 0;
                for (int x = -r; x <= r; x++) {
                    for (int y = -r; y <= r; y++) {
                        for (int z = -r; z <= r; z++) {
                            if (x * x + y * y + z * z > r * r) continue;
                            Block b = w.getBlockAt(c.getBlockX() + x, c.getBlockY() + y, c.getBlockZ() + z);
                            Material m = b.getType();
                            if (m == Material.WATER || m == Material.COBWEB || m == Material.KELP || m == Material.KELP_PLANT
                                    || m == Material.SEAGRASS || m == Material.TALL_SEAGRASS || m == Material.BUBBLE_COLUMN) {
                                b.setType(Material.AIR, false);
                                cleared++;
                            } else {
                                BlockData bd = b.getBlockData();
                                if (bd instanceof Waterlogged && ((Waterlogged) bd).isWaterlogged()) {
                                    ((Waterlogged) bd).setWaterlogged(false);
                                    b.setBlockData(bd, false);
                                    cleared++;
                                }
                            }
                        }
                    }
                }
                Util.sound(c, "block.fire.extinguish", 1f, 0.7f);
                Util.sound(c, "entity.blaze.shoot", 0.6f, 0.6f);
                Util.sphere(c.clone().add(0, 1, 0), r, 120, l -> Util.p(Particle.CAMPFIRE_COSY_SMOKE, l, 1, 0.05, 0.05, 0.05, 0.01));
                Util.p(Particle.FLAME, c.clone().add(0, 1, 0), 60, 1.2, 0.8, 1.2, 0.05);
                if (cleared == 0) Util.bar(p, "§7Nothing to clear nearby - but the heat rolls out anyway.");
                return true;
            }
        });

        // ------------------------------------------------------------ GAIA
        out.add(new Power(pl, "gaia", "Gaia", "§2", Material.BOOK, Rarity.COMMON,
                "Resistance while standing on stone, Regeneration while standing on grass",
                "Overworld: tsunami. Nether: burn everyone and wither them. End: levitate them and deal damage", 45, null, 0) {
            @Override public void tick(Player p, int tier) {
                Material under = p.getLocation().clone().subtract(0, 0.2, 0).getBlock().getType();
                if (Util.isStoneish(under)) {
                    Util.passive(p, PotionEffectType.RESISTANCE, tier >= 2 ? 1 : 0);
                } else if (under == Material.GRASS_BLOCK || under == Material.MOSS_BLOCK || under == Material.PODZOL) {
                    Util.passive(p, PotionEffectType.REGENERATION, tier >= 2 ? 1 : 0);
                }
            }
            @Override public boolean ability(Player p, int slot, int tier) {
                World.Environment env = p.getWorld().getEnvironment();
                if (env == World.Environment.NETHER) {
                    Util.sound(p.getLocation(), "entity.ghast.shoot", 1f, 0.6f);
                    for (LivingEntity le : Util.livingNear(p.getLocation(), tier >= 2 ? 14 : 10, p)) {
                        le.setFireTicks(120);
                        Util.eff(le, PotionEffectType.WITHER, tier >= 2 ? 8 : 6, tier >= 2 ? 1 : 0);
                    }
                    Util.ring(p.getLocation().add(0, 0.3, 0), tier >= 2 ? 14 : 10, 70, l -> Util.p(Particle.FLAME, l, 2, 0.1, 0.4, 0.1, 0.02));
                    Util.p(Particle.LAVA, p.getLocation().add(0, 1, 0), 40, 2, 1, 2, 0);
                } else if (env == World.Environment.THE_END) {
                    Util.sound(p.getLocation(), "entity.shulker.shoot", 1f, 0.6f);
                    for (LivingEntity le : Util.livingNear(p.getLocation(), tier >= 2 ? 14 : 10, p)) {
                        Util.eff(le, PotionEffectType.LEVITATION, tier >= 2 ? 4 : 3, 2);
                        Util.dmg(le, tier >= 2 ? 6.0 : 4.0, p);
                    }
                    Util.ring(p.getLocation().add(0, 0.3, 0), tier >= 2 ? 14 : 10, 70, l -> Util.p(Particle.PORTAL, l, 3, 0.1, 0.6, 0.1, 0.3));
                } else {
                    tsunami(p, tier);
                }
                return true;
            }
            private void tsunami(Player p, int tier) {
                final Location start = p.getEyeLocation().clone();
                final Vector dir = start.getDirection().setY(0);
                if (dir.lengthSquared() < 0.001) dir.setX(1);
                dir.normalize();
                final Vector right = new Vector(-dir.getZ(), 0, dir.getX());
                final double dmg = tier >= 2 ? 8.0 : 6.0;
                final Set<UUID> hit = new HashSet<>();
                Util.sound(p.getLocation(), "entity.dolphin.splash", 1f, 0.5f);
                Util.sound(p.getLocation(), "ambient.underwater.enter", 1f, 0.6f);
                new BukkitRunnable() {
                    int t = 0;
                    @Override public void run() {
                        if (t > (tier >= 2 ? 30 : 24) || !p.isOnline()) { cancel(); return; }
                        Location pos = start.clone().add(dir.clone().multiply(2 + t * 1.0));
                        for (double w = -4; w <= 4; w += 0.8) {
                            for (double h = 0; h <= 3.5; h += 0.7) {
                                Location l = pos.clone().add(right.clone().multiply(w)).add(0, h - 1.2, 0);
                                Util.p(Particle.SPLASH, l, 3, 0.2, 0.1, 0.2, 0.1);
                                if (h > 2.8) Util.p(Particle.CLOUD, l, 1, 0.1, 0.05, 0.1, 0.01);
                            }
                        }
                        for (LivingEntity le : Util.livingNear(pos, 3.5, p)) {
                            if (!hit.add(le.getUniqueId())) continue;
                            Util.dmg(le, dmg, p);
                            le.setVelocity(dir.clone().multiply(1.5).setY(0.45));
                            Util.eff(le, PotionEffectType.SLOWNESS, 3, 1);
                            le.setFireTicks(0);
                        }
                        t++;
                    }
                }.runTaskTimer(pl, 0L, 1L);
            }
        });

        // ------------------------------------------------------------ BULWARK
        out.add(new Power(pl, "bulwark", "Bulwark", "§7", Material.BOOK, Rarity.COMMON,
                "Below 4 hearts you gain Resistance II",
                "Raise a small barrier that absorbs damage", 40, null, 0) {
            private final Map<UUID, Double> pool = new HashMap<>();
            @Override public void tick(Player p, int tier) {
                if (p.getHealth() <= (tier >= 2 ? 10.0 : 8.0)) {
                    Util.passive(p, PotionEffectType.RESISTANCE, tier >= 2 ? 2 : 1);
                }
                UUID id = p.getUniqueId();
                if (pool.containsKey(id) && pl.mgr.timer(id, "bulwark")) {
                    Location c = p.getLocation().add(0, 1, 0);
                    Util.sphere(c, 2.4, 40, l -> Util.dust(l, Color.fromRGB(120, 200, 255), 1.1f, 1));
                } else if (pool.containsKey(id)) {
                    pool.remove(id);
                }
            }
            @Override public void onDamaged(Player p, EntityDamageEvent e, int tier) {
                UUID id = p.getUniqueId();
                Double left = pool.get(id);
                if (left == null || !pl.mgr.timer(id, "bulwark")) return;
                double dmg = e.getFinalDamage();
                e.setCancelled(true);
                Util.sound(p.getLocation(), "item.shield.block", 1f, 1.2f);
                Util.p(Particle.CRIT, p.getLocation().add(0, 1, 0), 15, 0.6, 0.8, 0.6, 0.2);
                double rem = left - dmg;
                if (rem <= 0) {
                    pool.remove(id);
                    pl.mgr.clearTimer(id, "bulwark");
                    Util.sound(p.getLocation(), "item.shield.break", 1f, 0.8f);
                    Util.bar(p, "§cYour barrier shattered!");
                } else {
                    pool.put(id, rem);
                    Util.bar(p, "§bBarrier: " + (int) Math.ceil(rem / 2.0) + " hearts left");
                }
            }
            @Override public void cleanup(UUID id) { pool.remove(id); }
            @Override public boolean ability(Player p, int slot, int tier) {
                UUID id = p.getUniqueId();
                pool.put(id, tier >= 2 ? 30.0 : 20.0);
                pl.mgr.setTimer(id, "bulwark", tier >= 2 ? 9 : 6);
                Util.sound(p.getLocation(), "block.beacon.activate", 1f, 1.8f);
                Util.sound(p.getLocation(), "item.armor.equip_iron", 1f, 0.6f);
                Util.sphere(p.getLocation().add(0, 1, 0), 2.4, 80, l -> Util.dust(l, Color.fromRGB(120, 200, 255), 1.3f, 2));
                return true;
            }
        });

        // ------------------------------------------------------------ CHRONOS
        out.add(new Power(pl, "chronos", "Chronos", "§e", Material.BOOK, Rarity.COMMON,
                "Chance to take 50% less damage from a hit",
                "Teleport back to where you stood 10 seconds ago", 50, null, 0) {
            private final Map<UUID, ArrayDeque<Snap>> hist = new HashMap<>();
            @Override public void onDamaged(Player p, EntityDamageEvent e, int tier) {
                if (Util.chance(tier >= 2 ? 0.2 : 0.12)) {
                    e.setDamage(e.getDamage() * 0.5);
                    Util.p(Particle.END_ROD, p.getLocation().add(0, 1, 0), 15, 0.4, 0.6, 0.4, 0.02);
                    Util.sound(p.getLocation(), "block.amethyst_block.chime", 1f, 1.4f);
                    Util.bar(p, "§eTime slows - damage halved!");
                }
            }
            @Override public void tick(Player p, int tier) {
                ArrayDeque<Snap> q = hist.computeIfAbsent(p.getUniqueId(), k -> new ArrayDeque<>());
                long now = System.currentTimeMillis();
                if (q.isEmpty() || now - q.peekLast().t >= 1000L) {
                    q.addLast(new Snap(p.getLocation().clone(), p.getHealth(), now));
                }
                while (!q.isEmpty() && now - q.peekFirst().t > 14000L) q.pollFirst();
            }
            @Override public void cleanup(UUID id) { hist.remove(id); }
            @Override public boolean ability(Player p, int slot, int tier) {
                ArrayDeque<Snap> q = hist.get(p.getUniqueId());
                if (q == null || q.isEmpty()) {
                    Util.bar(p, "§cNo time to rewind yet.");
                    return false;
                }
                long now = System.currentTimeMillis();
                Snap target = null;
                for (Snap s : q) {
                    if (now - s.t >= 9500L) target = s;
                }
                if (target == null) target = q.peekFirst();
                if (now - target.t < 3000L) {
                    Util.bar(p, "§cNot enough history yet - wait a few seconds.");
                    return false;
                }
                Util.p(Particle.REVERSE_PORTAL, p.getLocation().add(0, 1, 0), 50, 0.3, 0.7, 0.3, 0.4);
                Location dest = target.loc.clone();
                dest.setYaw(p.getLocation().getYaw());
                dest.setPitch(p.getLocation().getPitch());
                p.teleport(dest);
                if (tier >= 2 && target.hp > p.getHealth()) p.setHealth(Math.min(Util.maxHealth(p), target.hp));
                p.setFallDistance(0f);
                Util.p(Particle.END_ROD, dest.clone().add(0, 1, 0), 40, 0.3, 0.7, 0.3, 0.1);
                Util.sound(dest, "block.beacon.deactivate", 1f, 1.6f);
                return true;
            }
        });

        // ------------------------------------------------------------ COINSTORM
        out.add(new Power(pl, "coinstorm", "Coinstorm", "§6", Material.BOOK, Rarity.COMMON,
                "Your arrows fly straight, and every 5th arrow homes in",
                "Fire a burst of coins that each deal 2 hearts", 25, null, 0) {
            private final Map<UUID, Integer> shots = new HashMap<>();
            @Override public void cleanup(UUID id) { shots.remove(id); }

            @Override public void onShoot(Player p, EntityShootBowEvent e, int tier) {
                Entity pr = e.getProjectile();
                if (!(pr instanceof AbstractArrow)) return;
                final AbstractArrow arrow = (AbstractArrow) pr;
                arrow.setGravity(false);
                Util.later(Util.ticks(tier >= 2 ? 5 : 3), () -> { if (arrow.isValid()) arrow.setGravity(true); });
                int n = shots.merge(p.getUniqueId(), 1, Integer::sum);
                if (n % 5 != 0) return;
                Util.sound(p.getLocation(), "entity.experience_orb.pickup", 0.8f, 1.8f);
                new BukkitRunnable() {
                    int t = 0;
                    @Override public void run() {
                        if (!arrow.isValid() || arrow.isInBlock() || t++ > 100) { cancel(); return; }
                        LivingEntity best = null;
                        double bd = 30 * 30;
                        Vector vel = arrow.getVelocity();
                        if (vel.lengthSquared() < 0.0001) { cancel(); return; }
                        for (LivingEntity le : Util.livingNear(arrow.getLocation(), 30, p)) {
                            if (!(le instanceof Player) && !(le instanceof Monster)) continue;
                            Vector to = le.getEyeLocation().toVector().subtract(arrow.getLocation().toVector());
                            if (to.lengthSquared() < 0.01 || to.angle(vel) > 1.2) continue;
                            double d = to.lengthSquared();
                            if (d < bd) { bd = d; best = le; }
                        }
                        if (best != null) {
                            Vector to = best.getEyeLocation().toVector().subtract(arrow.getLocation().toVector()).normalize();
                            Vector nv = vel.clone().normalize().multiply(0.7).add(to.multiply(0.3)).normalize().multiply(vel.length());
                            arrow.setVelocity(nv);
                        }
                        Util.p(Particle.END_ROD, arrow.getLocation(), 1, 0, 0, 0, 0);
                    }
                }.runTaskTimer(pl, 1L, 1L);
            }

            @Override public boolean ability(Player p, int slot, int tier) {
                final Power self = this;
                final int count = tier >= 2 ? 8 : 5;
                Util.repeat(4, count, i -> {
                    if (!p.isOnline() || p.isDead()) return;
                    Vector aim = p.getEyeLocation().getDirection().multiply(2.4);
                    final Snowball s = p.launchProjectile(Snowball.class, aim);
                    s.setItem(pl.mgr.createCoin());
                    s.setGravity(false);
                    pl.mgr.tagProjectile(s, self, tier);
                    Util.sound(p.getLocation(), "entity.experience_orb.pickup", 0.7f, 1.0f + i * 0.08f);
                    Util.later(60, s::remove);
                }, null);
                return true;
            }
            @Override public void onProjectileDamage(Player shooter, Projectile proj, LivingEntity victim, EntityDamageByEntityEvent e, int tier) {
                e.setCancelled(true);
            }
            @Override public void onProjectileHit(Player shooter, Projectile proj, ProjectileHitEvent e, int tier) {
                if (e.getHitEntity() instanceof LivingEntity && e.getHitEntity() != shooter) {
                    LivingEntity le = (LivingEntity) e.getHitEntity();
                    Util.dmg(le, tier >= 2 ? 5.0 : 4.0, shooter);
                    le.setNoDamageTicks(0);
                    Util.p(Particle.CRIT, le.getLocation().add(0, 1, 0), 10, 0.2, 0.4, 0.2, 0.2);
                    Util.sound(le.getLocation(), "block.chain.hit", 0.8f, 1.6f);
                    proj.remove();
                } else if (e.getHitBlock() != null) {
                    proj.remove();
                }
            }
        });
    }
}
