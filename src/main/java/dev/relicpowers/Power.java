package dev.relicpowers;

import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

import java.util.UUID;

/** One relic power. Subclass it (anonymously) and override only the hooks you need. */
public abstract class Power {
    public enum Rarity { COMMON, LEGENDARY, UNIQUE }

    public final RelicPowers pl;
    public final String id;
    public final String defName;
    public final String color;
    public final Material mat;
    public final Rarity rarity;
    public final String passive;   // may be null
    public final String a1;        // ability 1 description
    public final String a2;        // ability 2 description, may be null
    public final int cd1;
    public final int cd2;

    protected Power(RelicPowers pl, String id, String name, String color, Material mat, Rarity rarity,
                    String passive, String a1, int cd1, String a2, int cd2) {
        this.pl = pl;
        this.id = id;
        this.defName = name;
        this.color = color;
        this.mat = mat;
        this.rarity = rarity;
        this.passive = passive;
        this.a1 = a1;
        this.cd1 = cd1;
        this.a2 = a2;
        this.cd2 = cd2;
    }

    public String name() { return pl.getConfig().getString("names." + id, defName); }
    public int abilityCount() { return a2 == null ? 1 : 2; }

    /** Cooldown in seconds for the given ability slot (1/2) at the given tier. */
    public double cooldown(int slot, int tier) {
        double base = pl.getConfig().getDouble("cooldowns." + id + "." + slot, slot == 1 ? cd1 : cd2);
        return tier >= 2 ? base * pl.getConfig().getDouble("tier2-cooldown-multiplier", 0.75) : base;
    }

    // ------------- hooks (all optional) -------------
    /** Runs every 10 ticks while this power is active for the player. */
    public void tick(Player p, int tier) {}
    /** Player melee-hit something. Damage can be edited on the event. */
    public void onAttack(Player p, LivingEntity victim, EntityDamageByEntityEvent e, int tier) {}
    /** Player is about to take damage (any cause). */
    public void onDamaged(Player p, EntityDamageEvent e, int tier) {}
    /** Player is about to take damage that would kill them (runs last). */
    public void onLethal(Player p, EntityDamageEvent e, int tier) {}
    public void onConsume(Player p, PlayerItemConsumeEvent e, int tier) {}
    public void onKill(Player p, LivingEntity dead, int tier) {}
    public void onMove(Player p, PlayerMoveEvent e, int tier) {}
    public void onShoot(Player p, EntityShootBowEvent e, int tier) {}
    public void onLaunch(Player p, ProjectileLaunchEvent e, int tier) {}
    public void onPlace(Player p, BlockPlaceEvent e, int tier) {}
    public void onSneak(Player p, PlayerToggleSneakEvent e, int tier) {}
    /** One of this power's tagged projectiles damaged something. */
    public void onProjectileDamage(Player shooter, Projectile proj, LivingEntity victim, EntityDamageByEntityEvent e, int tier) {}
    /** One of this power's tagged projectiles landed. */
    public void onProjectileHit(Player shooter, Projectile proj, ProjectileHitEvent e, int tier) {}
    /** The power stopped being active for this player: undo attributes etc. */
    public void onRemove(Player p) {}
    /** Forget any per-player memory. */
    public void cleanup(UUID id) {}
    /** True if mobs can't hurt or target the holder. */
    public boolean mobImmune() { return false; }

    /** Use ability `slot` (1 or 2). Return true if it was used (starts the cooldown). */
    public boolean ability(Player p, int slot, int tier) { return false; }
}
