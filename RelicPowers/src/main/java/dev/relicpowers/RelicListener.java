package dev.relicpowers;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Allay;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

public final class RelicListener implements Listener {
    private final RelicPowers pl;
    private final PowerManager mgr;

    public RelicListener(RelicPowers pl) {
        this.pl = pl;
        this.mgr = pl.mgr;
    }

    // ================================================================= join / quit / respawn / death
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        final Player p = e.getPlayer();
        Util.later(2, () -> { if (p.isOnline()) mgr.onJoin(p); });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        mgr.onQuit(e.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        final Player p = e.getPlayer();
        Util.later(2, () -> { if (p.isOnline()) { mgr.ensureItems(p); mgr.refresh(p); } });
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        pl.realm.onDeath(p);
        // the mace goes to the killer
        Power mace = Powers.get("mace");
        boolean hadMace = mace != null && mgr.hasPowerItem(p, mace);
        if (pl.getConfig().getBoolean("soulbound", true)) {
            e.getDrops().removeIf(mgr::isRelicItem);
        } else if (hadMace) {
            e.getDrops().removeIf(i -> mgr.powerOf(i) == mace);
        }
        if (hadMace) {
            Player killer = p.getKiller();
            if (killer != null && !killer.equals(p)) {
                mgr.giveMace(killer);
            } else if (!pl.getConfig().getBoolean("soulbound", true)) {
                mgr.clearMace();
            }
        }
        if (pl.getConfig().getBoolean("death-resets-tier", false) && mgr.getPower(p) != null) {
            p.getPersistentDataContainer().set(mgr.K_TIER, PersistentDataType.INTEGER, 1);
        }
    }

    // ================================================================= soulbound
    private boolean soulbound() { return pl.getConfig().getBoolean("soulbound", true); }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (soulbound() && mgr.isRelicItem(e.getItemDrop().getItemStack())) {
            e.setCancelled(true);
        }
    }

    private boolean allowedTop(Inventory top) {
        InventoryType t = top.getType();
        return t == InventoryType.CRAFTING || t == InventoryType.PLAYER || t == InventoryType.ANVIL
                || t == InventoryType.ENCHANTING || t == InventoryType.GRINDSTONE || t == InventoryType.SMITHING;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        // relic browser
        if (e.getView().getTopInventory().getHolder() instanceof Gui.Holder) {
            e.setCancelled(true);
            if (e.getWhoClicked() instanceof Player && e.getClickedInventory() == e.getView().getTopInventory()) {
                Gui.click((Player) e.getWhoClicked(), e.getCurrentItem());
            }
            return;
        }
        if (!soulbound()) return;
        Inventory top = e.getView().getTopInventory();
        if (allowedTop(top)) return;
        boolean clickedTop = e.getClickedInventory() != null && e.getClickedInventory().equals(top);
        ItemStack cursor = e.getCursor();
        ItemStack current = e.getCurrentItem();
        if (clickedTop && mgr.isRelicItem(cursor)) { e.setCancelled(true); return; }
        if (e.isShiftClick() && !clickedTop && mgr.isRelicItem(current)) { e.setCancelled(true); return; }
        if (clickedTop && e.getClick().name().equals("NUMBER_KEY")) {
            ItemStack hot = e.getWhoClicked().getInventory().getItem(e.getHotbarButton());
            if (mgr.isRelicItem(hot)) e.setCancelled(true);
        }
        if (clickedTop && e.getClick().name().equals("SWAP_OFFHAND")
                && mgr.isRelicItem(e.getWhoClicked().getInventory().getItemInOffHand())) {
            e.setCancelled(true);
        }
    }

    /** No putting relics into item frames, armor stands or allays. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        if (!soulbound()) return;
        Entity target = e.getRightClicked();
        if (!(target instanceof ItemFrame || target instanceof ArmorStand || target instanceof Allay)) return;
        ItemStack held = e.getPlayer().getInventory().getItem(e.getHand());
        if (mgr.isRelicItem(held)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Gui.Holder) { e.setCancelled(true); return; }
        if (!soulbound()) return;
        Inventory top = e.getView().getTopInventory();
        if (allowedTop(top)) return;
        if (!mgr.isRelicItem(e.getOldCursor())) return;
        int topSize = top.getSize();
        for (int slot : e.getRawSlots()) {
            if (slot < topSize) { e.setCancelled(true); return; }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCraftPrepare(PrepareItemCraftEvent e) {
        for (ItemStack i : e.getInventory().getMatrix()) {
            if (i != null && (mgr.isRelicItem(i) || mgr.toolOf(i) != null)) {
                e.getInventory().setResult(null);
                return;
            }
        }
    }

    // ================================================================= using items
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack it = e.getItem();
        if (it == null) return;
        String tool = mgr.toolOf(it);
        Power pw = mgr.powerOf(it);
        if (tool == null && pw == null) return;

        Block cb = e.getClickedBlock();
        if (cb != null) {
            if (cb.getType() == Material.LECTERN) { e.setCancelled(true); return; }
            if (cb.getType().isInteractable() && !e.getPlayer().isSneaking()) return;
        }
        e.setUseItemInHand(Event.Result.DENY);
        Player p = e.getPlayer();
        if (tool != null) {
            if (mgr.timer(p.getUniqueId(), "toolcd")) return;
            mgr.setTimer(p.getUniqueId(), "toolcd", 0.6);
            mgr.useTool(p, tool);
            return;
        }
        if (!pl.getConfig().getBoolean("right-click-abilities", true)) return;
        int slot = p.isSneaking() && pw.abilityCount() >= 2 ? 2 : 1;
        mgr.useAbility(p, slot);
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent e) {
        if (!e.isSneaking()) return;
        Player p = e.getPlayer();
        Power pw = mgr.powerOf(p.getInventory().getItemInMainHand());
        if (pw != null) pw.onSneak(p, e, mgr.tierOf(p.getInventory().getItemInMainHand()));
    }

    // ================================================================= combat
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        Entity ent = e.getEntity();

        if (Decoys.isDecoy(ent)) {
            e.setCancelled(true);
            if (e instanceof EntityDamageByEntityEvent) Decoys.pop(ent);
            return;
        }

        // victim side
        if (ent instanceof Player) {
            Player victim = (Player) ent;
            UUID vid = victim.getUniqueId();
            if (e.getCause() == EntityDamageEvent.DamageCause.FALL && mgr.timer(vid, "nofall")) {
                e.setCancelled(true);
                return;
            }
            PowerManager.State s = mgr.state(victim);
            if (s != null) {
                s.power().onDamaged(victim, e, s.tier());
                if (e.isCancelled()) return;
            }
        }

        // attacker side
        if (!(e instanceof EntityDamageByEntityEvent)) return;
        EntityDamageByEntityEvent ed = (EntityDamageByEntityEvent) e;
        Entity damager = ed.getDamager();

        if (damager instanceof Projectile) {
            Projectile proj = (Projectile) damager;
            String tag = proj.getPersistentDataContainer().get(mgr.K_PROJ, PersistentDataType.STRING);
            if (tag != null && proj.getShooter() instanceof Player && ent instanceof LivingEntity) {
                String[] parts = tag.split(":");
                Power pw = Powers.get(parts[0]);
                int tier = parts.length > 1 ? parseInt(parts[1]) : 1;
                if (pw != null) pw.onProjectileDamage((Player) proj.getShooter(), proj, (LivingEntity) ent, ed, tier);
            }
            return;
        }

        if (damager instanceof Player && ent instanceof LivingEntity) {
            Player att = (Player) damager;
            UUID aid = att.getUniqueId();
            if (mgr.timer(aid, "freeze") || mgr.timer(aid, "stun")) {
                e.setCancelled(true);
                return;
            }
            if (PowerManager.internal) return;
            PowerManager.State s = mgr.state(att);
            if (s != null) s.power().onAttack(att, (LivingEntity) ent, ed, s.tier());
        }
    }

    private static int parseInt(String s) {
        try { return Integer.parseInt(s); } catch (NumberFormatException ex) { return 1; }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLethal(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        Player p = (Player) e.getEntity();
        PowerManager.State s = mgr.state(p);
        if (s == null) return;
        if (p.getHealth() - e.getFinalDamage() <= 0.0) s.power().onLethal(p, e, s.tier());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent e) {
        if (!(e.getTarget() instanceof Player)) return;
        PowerManager.State s = mgr.state((Player) e.getTarget());
        if (s != null && s.power().mobImmune()) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent e) {
        Projectile proj = e.getEntity();
        String tag = proj.getPersistentDataContainer().get(mgr.K_PROJ, PersistentDataType.STRING);
        if (tag == null || !(proj.getShooter() instanceof Player)) return;
        String[] parts = tag.split(":");
        Power pw = Powers.get(parts[0]);
        if (pw != null) pw.onProjectileHit((Player) proj.getShooter(), proj, e, parts.length > 1 ? parseInt(parts[1]) : 1);
    }

    @EventHandler(ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent e) {
        Player p = e.getPlayer();
        PowerManager.State s = mgr.state(p);
        if (s != null) s.power().onConsume(p, e, s.tier());
    }

    @EventHandler(ignoreCancelled = true)
    public void onMobDeath(EntityDeathEvent e) {
        Player k = e.getEntity().getKiller();
        if (k == null) return;
        PowerManager.State s = mgr.state(k);
        if (s != null) s.power().onKill(k, e.getEntity(), s.tier());
    }

    @EventHandler(ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        Player p = (Player) e.getEntity();
        PowerManager.State s = mgr.state(p);
        if (s != null) s.power().onShoot(p, e, s.tier());
    }

    @EventHandler(ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent e) {
        if (!(e.getEntity().getShooter() instanceof Player)) return;
        Player p = (Player) e.getEntity().getShooter();
        PowerManager.State s = mgr.state(p);
        if (s != null) s.power().onLaunch(p, e, s.tier());
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        PowerManager.State s = mgr.state(p);
        if (s != null) s.power().onPlace(p, e, s.tier());
    }

    // ================================================================= movement locks + move hooks
    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();
        Location from = e.getFrom();
        Location to = e.getTo();
        if (to == null) return;

        if (mgr.timers.any()) {
            if (mgr.timer(id, "freeze")) {
                Location f = from.clone();
                f.setYaw(to.getYaw());
                f.setPitch(to.getPitch());
                e.setTo(f);
                return;
            }
            if (mgr.timer(id, "stun")) {
                if (from.getX() != to.getX() || from.getZ() != to.getZ()) {
                    Location t = to.clone();
                    t.setX(from.getX());
                    t.setZ(from.getZ());
                    e.setTo(t);
                }
                return;
            }
        }
        PowerManager.State s = mgr.state(p);
        if (s != null && (from.getX() != to.getX() || from.getZ() != to.getZ())) s.power().onMove(p, e, s.tier());
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        if (e.getCause() == PlayerTeleportEvent.TeleportCause.SPECTATE && mgr.timer(e.getPlayer().getUniqueId(), "eclipse")) {
            e.setCancelled(true);
        }
    }

    // ================================================================= decoys
    @EventHandler(ignoreCancelled = true)
    public void onStandManipulate(PlayerArmorStandManipulateEvent e) {
        if (Decoys.isDecoy(e.getRightClicked())) e.setCancelled(true);
    }
}
