package dev.relicpowers;

import dev.relicpowers.Power.Rarity;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.UUID;

/** Bloodhound, Kensei, Spellbound, Bonebreaker, Nullifier, Dial, Thunder */
final class PowersC {
    private PowersC() {}

    static void register(List<Power> out, RelicPowers pl) {

        // ------------------------------------------------------------ BLOODHOUND
        out.add(new Power(pl, "bloodhound", "Bloodhound", "§4", Material.BOOK, Rarity.COMMON,
                "Permanent invisibility",
                "No knockback + Regeneration II", 40, null, 0) {
            @Override public void tick(Player p, int tier) {
                Util.passive(p, PotionEffectType.INVISIBILITY, 0);
                if (pl.mgr.timer(p.getUniqueId(), "blood_kb")) {
                    Util.ensureMod(p, Attribute.KNOCKBACK_RESISTANCE, "blood_kb", 1.0, AttributeModifier.Operation.ADD_NUMBER);
                } else {
                    Util.clearMod(p, Attribute.KNOCKBACK_RESISTANCE, "blood_kb");
                }
            }
            @Override public void onRemove(Player p) { Util.clearMod(p, Attribute.KNOCKBACK_RESISTANCE, "blood_kb"); }
            @Override public boolean ability(Player p, int slot, int tier) {
                double sec = tier >= 2 ? 15 : 10;
                pl.mgr.setTimer(p.getUniqueId(), "blood_kb", sec);
                Util.ensureMod(p, Attribute.KNOCKBACK_RESISTANCE, "blood_kb", 1.0, AttributeModifier.Operation.ADD_NUMBER);
                Util.eff(p, PotionEffectType.REGENERATION, sec, 1);
                Util.sound(p.getLocation(), "entity.wolf.growl", 1f, 0.7f);
                Util.ring(p.getLocation().add(0, 0.3, 0), 1.0, 18, l -> Util.dust(l, Color.fromRGB(150, 0, 0), 1.3f, 2));
                return true;
            }
        });

        // ------------------------------------------------------------ KENSEI
        out.add(new Power(pl, "kensei", "Kensei", "§f", Material.BOOK, Rarity.COMMON,
                "Chance to disable shields on hit",
                "Deal more damage and call lightning down on your enemies", 40, null, 0) {
            @Override public void onAttack(Player p, LivingEntity v, EntityDamageByEntityEvent e, int tier) {
                UUID id = p.getUniqueId();
                if (v instanceof Player && Util.chance(tier >= 2 ? 0.35 : 0.2)) {
                    Player vp = (Player) v;
                    vp.setCooldown(Material.SHIELD, Util.ticks(tier >= 2 ? 8 : 5));
                    if (vp.isBlocking()) vp.clearActiveItem();
                    Util.sound(vp.getLocation(), "item.shield.break", 1f, 1f);
                    Util.bar(vp, "§cYour shield was disabled!");
                }
                if (pl.mgr.timer(id, "kensei_charge")) {
                    e.setDamage(e.getDamage() * (tier >= 2 ? 1.5 : 1.3));
                    if (!pl.mgr.timer(id, "kensei_bolt")) {
                        pl.mgr.setTimer(id, "kensei_bolt", 1.5);
                        final Location l = v.getLocation();
                        l.getWorld().strikeLightningEffect(l);
                        final double bolt = tier >= 2 ? 4.0 : 3.0;
                        Util.later(1, () -> Util.trueDamage(v, bolt, p));
                    }
                }
            }
            @Override public boolean ability(Player p, int slot, int tier) {
                pl.mgr.setTimer(p.getUniqueId(), "kensei_charge", tier >= 2 ? 10 : 8);
                Util.sound(p.getLocation(), "item.trident.thunder", 1f, 1.4f);
                Util.ring(p.getLocation().add(0, 1, 0), 1.0, 20, l -> Util.p(Particle.ELECTRIC_SPARK, l, 3, 0.05, 0.3, 0.05, 0.05));
                Util.msg(p, "§fKensei: your blade crackles with lightning.");
                return true;
            }
        });

        // ------------------------------------------------------------ SPELLBOUND
        out.add(new Power(pl, "spellbound", "Spellbound", "§d", Material.BOOK, Rarity.COMMON,
                "Chance to repair your armor when you take damage",
                "Raise your held weapon's Sharpness for 30 seconds", 50,
                "Raise your armor's Protection for 30 seconds", 50) {
            @Override public void onDamaged(Player p, EntityDamageEvent e, int tier) {
                if (!Util.chance(tier >= 2 ? 0.2 : 0.12)) return;
                int amt = tier >= 2 ? 80 : 40;
                boolean any = false;
                ItemStack[] armor = p.getInventory().getArmorContents();
                for (ItemStack a : armor) {
                    if (a == null || !a.hasItemMeta()) continue;
                    ItemMeta m = a.getItemMeta();
                    if (m instanceof org.bukkit.inventory.meta.Damageable) {
                        org.bukkit.inventory.meta.Damageable d = (org.bukkit.inventory.meta.Damageable) m;
                        if (d.getDamage() > 0) {
                            d.setDamage(Math.max(0, d.getDamage() - amt));
                            a.setItemMeta(m);
                            any = true;
                        }
                    }
                }
                if (any) {
                    p.getInventory().setArmorContents(armor);
                    Util.p(Particle.ENCHANT, p.getLocation().add(0, 1, 0), 20, 0.4, 0.6, 0.4, 0.4);
                    Util.sound(p.getLocation(), "block.anvil.use", 0.3f, 1.8f);
                }
            }
            @Override public boolean ability(Player p, int slot, int tier) {
                long expiry = System.currentTimeMillis() + (tier >= 2 ? 45000L : 30000L);
                int add = tier >= 2 ? 2 : 1;
                if (slot == 1) {
                    ItemStack hand = p.getInventory().getItemInMainHand();
                    String n = hand.getType().name();
                    if (!(n.endsWith("_SWORD") || n.endsWith("_AXE"))) {
                        Util.bar(p, "§cHold a sword or axe to raise its Sharpness!");
                        return false;
                    }
                    SpellUtil.boost(hand, Enchantment.SHARPNESS, "sharp", add, expiry);
                    p.getInventory().setItemInMainHand(hand);
                } else {
                    ItemStack[] armor = p.getInventory().getArmorContents();
                    boolean any = false;
                    for (ItemStack a : armor) {
                        if (a == null || a.getType().isAir()) continue;
                        SpellUtil.boost(a, Enchantment.PROTECTION, "prot", add, expiry);
                        any = true;
                    }
                    if (!any) {
                        Util.bar(p, "§cWear some armor first!");
                        return false;
                    }
                    p.getInventory().setArmorContents(armor);
                }
                Util.p(Particle.ENCHANT, p.getLocation().add(0, 1.2, 0), 60, 0.5, 0.8, 0.5, 0.8);
                Util.sound(p.getLocation(), "block.enchantment_table.use", 1f, 1.2f);
                Util.msg(p, "§dSpellbound: " + (slot == 1 ? "Sharpness" : "Protection") + " +" + add + " for " + (tier >= 2 ? 45 : 30) + "s");
                return true;
            }
        });

        // ------------------------------------------------------------ BONEBREAKER
        out.add(new Power(pl, "bonebreaker", "Bonebreaker", "§7", Material.BOOK, Rarity.COMMON,
                "Chance to stun enemies for 1 second on hit",
                "Slam the nearest player into the ground for 3 hearts of true damage", 35, null, 0) {
            private void stun(LivingEntity v, double sec) {
                if (v instanceof Player) pl.mgr.setTimer(v.getUniqueId(), "stun", sec);
                Util.eff(v, PotionEffectType.SLOWNESS, sec, 6);
                Util.eff(v, PotionEffectType.JUMP_BOOST, sec, 200);
                Util.eff(v, PotionEffectType.MINING_FATIGUE, sec, 3);
                Vector vel = v.getVelocity();
                v.setVelocity(new Vector(0, Math.min(0, vel.getY()), 0));
            }
            @Override public void onAttack(Player p, LivingEntity v, EntityDamageByEntityEvent e, int tier) {
                if (Util.chance(tier >= 2 ? 0.25 : 0.15)) {
                    stun(v, tier >= 2 ? 1.5 : 1.0);
                    Util.p(Particle.CRIT, v.getLocation().add(0, 1, 0), 25, 0.3, 0.5, 0.3, 0.2);
                    Util.sound(v.getLocation(), "entity.skeleton.hurt", 1f, 0.6f);
                }
            }
            @Override public boolean ability(Player p, int slot, int tier) {
                Player t = Util.nearestPlayer(p, 10);
                if (t == null) {
                    Util.bar(p, "§cNo player nearby!");
                    return false;
                }
                stun(t, tier >= 2 ? 1.5 : 1.0);
                t.setVelocity(new Vector(0, -2.5, 0));
                Util.trueDamage(t, tier >= 2 ? 8.0 : 6.0, p);
                Block under = t.getLocation().subtract(0, 1, 0).getBlock();
                BlockData bd = under.getType().isAir() ? Material.STONE.createBlockData() : under.getBlockData();
                t.getWorld().spawnParticle(Particle.BLOCK, t.getLocation(), 50, 0.6, 0.1, 0.6, 0.0, bd);
                Util.sound(t.getLocation(), "entity.iron_golem.damage", 1f, 0.5f);
                Util.sound(t.getLocation(), "entity.skeleton.hurt", 1f, 0.5f);
                Util.msg(t, "§7" + p.getName() + " slammed you into the ground!");
                return true;
            }
        });

        // ------------------------------------------------------------ NULLIFIER
        out.add(new Power(pl, "nullifier", "Nullifier", "§5", Material.BOOK, Rarity.COMMON,
                "Chance to take no damage from a hit",
                "Disable the abilities of every player near you for 30 seconds", 60, null, 0) {
            @Override public void onDamaged(Player p, EntityDamageEvent e, int tier) {
                if (e instanceof EntityDamageByEntityEvent && Util.chance(tier >= 2 ? 0.3 : 0.2)) {
                    e.setCancelled(true);
                    Util.bar(p, "§5Nullified!");
                    Util.p(Particle.ENCHANT, p.getLocation().add(0, 1, 0), 15, 0.4, 0.6, 0.4, 0.4);
                    Util.sound(p.getLocation(), "block.respawn_anchor.deplete", 0.6f, 1.5f);
                }
            }
            @Override public boolean ability(Player p, int slot, int tier) {
                double r = tier >= 2 ? 18 : 12;
                double sec = tier >= 2 ? 45 : 30;
                List<Player> near = Util.playersNear(p.getLocation(), r, p);
                if (near.isEmpty()) {
                    Util.bar(p, "§cNo players nearby!");
                    return false;
                }
                for (Player o : near) {
                    pl.mgr.setTimer(o.getUniqueId(), "nullified", sec);
                    Util.msg(o, "§5" + p.getName() + " nullified your abilities for " + (int) sec + "s!");
                    Util.p(Particle.SOUL, o.getLocation().add(0, 1, 0), 20, 0.4, 0.6, 0.4, 0.05);
                }
                Util.sound(p.getLocation(), "entity.elder_guardian.curse", 1f, 0.8f);
                Util.ring(p.getLocation().add(0, 0.3, 0), r, 60, l -> Util.dust(l, Color.fromRGB(120, 0, 180), 1.4f, 1));
                return true;
            }
        });

        // ------------------------------------------------------------ DIAL (legendary)
        out.add(new Power(pl, "dial", "Dial", "§b", Material.NETHER_STAR, Rarity.LEGENDARY,
                null,
                "Freeze every player near you in time", 45,
                "Gain incredible speed", 30) {
            @Override public boolean ability(Player p, int slot, int tier) {
                if (slot == 2) {
                    double sec = tier >= 2 ? 12 : 8;
                    Util.eff(p, PotionEffectType.SPEED, sec, tier >= 2 ? 5 : 4);
                    Util.sound(p.getLocation(), "block.amethyst_block.resonate", 1f, 1.6f);
                    Util.repeat(2, 12, i -> Util.p(Particle.END_ROD, p.getLocation().add(0, 1, 0), 6, 0.3, 0.6, 0.3, 0.05), null);
                    return true;
                }
                double r = tier >= 2 ? 12 : 8;
                final double sec = tier >= 2 ? 6 : 4;
                List<Player> near = Util.playersNear(p.getLocation(), r, p);
                if (near.isEmpty()) {
                    Util.bar(p, "§cNo players nearby!");
                    return false;
                }
                for (final Player o : near) {
                    pl.mgr.setTimer(o.getUniqueId(), "freeze", sec);
                    Util.eff(o, PotionEffectType.SLOWNESS, sec, 7);
                    Util.eff(o, PotionEffectType.JUMP_BOOST, sec, 200);
                    Util.repeat(5, (int) (sec * 4), i -> {
                        if (!o.isOnline()) return;
                        o.setFreezeTicks(o.getMaxFreezeTicks());
                        Util.p(Particle.SNOWFLAKE, o.getLocation().add(0, 1, 0), 6, 0.3, 0.6, 0.3, 0.01);
                    }, null);
                    Util.msg(o, "§b" + p.getName() + " froze you in time!");
                }
                Util.sound(p.getLocation(), "block.amethyst_block.resonate", 1f, 0.5f);
                Util.ring(p.getLocation().add(0, 0.2, 0), r, 60, l -> Util.p(Particle.SNOWFLAKE, l, 2, 0.1, 0.1, 0.1, 0.01));
                return true;
            }
        });

        // ------------------------------------------------------------ THUNDER (legendary)
        out.add(new Power(pl, "thunder", "Thunder", "§e", Material.NETHER_STAR, Rarity.LEGENDARY,
                "Speed II and Haste IV",
                "Spawn fake players of yourself around you", 40, null, 0) {
            @Override public void tick(Player p, int tier) {
                Util.passive(p, PotionEffectType.SPEED, tier >= 2 ? 2 : 1);
                Util.passive(p, PotionEffectType.HASTE, tier >= 2 ? 4 : 3);
            }
            @Override public boolean ability(Player p, int slot, int tier) {
                Decoys.spawn(p, tier >= 2 ? 6 : 4, Util.ticks(tier >= 2 ? 12 : 8));
                p.getWorld().strikeLightningEffect(p.getLocation());
                Util.ring(p.getLocation().add(0, 0.5, 0), 2.5, 24, l -> Util.p(Particle.ELECTRIC_SPARK, l, 4, 0.1, 0.5, 0.1, 0.1));
                return true;
            }
        });
    }
}
