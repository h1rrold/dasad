package dev.relicpowers;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Items, per-player power data, cooldowns, timers and ability dispatch. */
public final class PowerManager {
    /** True while a relic is dealing damage itself, so on-hit passives don't chain. */
    public static boolean internal = false;

    public record State(Power power, int tier) {}

    /** key -> expiry map per player. */
    public static final class Timers {
        private final Map<UUID, Map<String, Long>> map = new HashMap<>();
        public void set(UUID id, String key, double sec) {
            map.computeIfAbsent(id, k -> new HashMap<>()).put(key, System.currentTimeMillis() + (long) (sec * 1000.0));
        }
        public boolean active(UUID id, String key) {
            Map<String, Long> m = map.get(id);
            if (m == null) return false;
            Long t = m.get(key);
            if (t == null) return false;
            if (t < System.currentTimeMillis()) { m.remove(key); return false; }
            return true;
        }
        public double left(UUID id, String key) {
            Map<String, Long> m = map.get(id);
            if (m == null) return 0;
            Long t = m.get(key);
            if (t == null) return 0;
            return Math.max(0, (t - System.currentTimeMillis()) / 1000.0);
        }
        public void clear(UUID id, String key) {
            Map<String, Long> m = map.get(id);
            if (m != null) m.remove(key);
        }
        public boolean any() { return !map.isEmpty(); }
    }

    private final RelicPowers pl;
    public final NamespacedKey K_POWER, K_TIER, K_UID, K_TOOL, K_PROJ, K_GIVEN;
    private final Map<UUID, State> cache = new HashMap<>();
    public final Timers timers = new Timers();
    public final Timers cooldowns = new Timers();
    private UUID maceHolder;
    private File dataFile;
    private YamlConfiguration data;
    private BukkitTask tickTask;
    private int counter = 0;

    public PowerManager(RelicPowers pl) {
        this.pl = pl;
        K_POWER = new NamespacedKey(pl, "power");
        K_TIER = new NamespacedKey(pl, "tier");
        K_UID = new NamespacedKey(pl, "uid");
        K_TOOL = new NamespacedKey(pl, "tool");
        K_PROJ = new NamespacedKey(pl, "proj");
        K_GIVEN = new NamespacedKey(pl, "given");
    }

    // ------------------------------------------------------------------ data file
    public void loadData() {
        dataFile = new File(pl.getDataFolder(), "data.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);
        String s = data.getString("mace-holder");
        if (s != null) {
            try { maceHolder = UUID.fromString(s); } catch (IllegalArgumentException ignored) { maceHolder = null; }
        }
    }
    private void saveData() {
        try { data.save(dataFile); } catch (IOException ex) { pl.getLogger().warning("Could not save data.yml: " + ex.getMessage()); }
    }
    public UUID getMaceHolder() { return maceHolder; }

    // ------------------------------------------------------------------ timers (shortcuts)
    public void setTimer(UUID id, String key, double sec) { timers.set(id, key, sec); }
    public boolean timer(UUID id, String key) { return timers.active(id, key); }
    public void clearTimer(UUID id, String key) { timers.clear(id, key); }
    public double timerLeft(UUID id, String key) { return timers.left(id, key); }

    // ------------------------------------------------------------------ item building
    private static String cdText(double s) { return (int) Math.round(s) + "s"; }

    public ItemStack createItem(Power pw, int tier) {
        ItemStack it = new ItemStack(pw.mat);
        ItemMeta m = it.getItemMeta();
        m.displayName(Util.c(pw.color + "§l" + pw.name() + (tier >= 2 ? " §d§l[II]" : "")));

        List<Component> lore = new ArrayList<>();
        lore.add(Util.c("§8" + (pw.rarity == Power.Rarity.LEGENDARY ? "Legendary relic"
                : pw.rarity == Power.Rarity.UNIQUE ? "Unique relic - only one exists" : "Relic")));
        lore.add(Util.c(""));
        lore.add(Util.c("§7Passive: §f" + (pw.passive == null ? "none" : pw.passive)));
        lore.add(Util.c("§7Ability 1: §f" + pw.a1 + " §8(" + cdText(pw.cooldown(1, tier)) + ")"));
        if (pw.a2 != null) lore.add(Util.c("§7Ability 2: §f" + pw.a2 + " §8(" + cdText(pw.cooldown(2, tier)) + ")"));
        lore.add(Util.c(""));
        lore.add(Util.c("§8/ability 1 §7or right-click"));
        if (pw.a2 != null) lore.add(Util.c("§8/ability 2 §7or sneak + right-click"));
        lore.add(Util.c(pw.rarity == Power.Rarity.UNIQUE ? "§4Kill the holder to take it"
                : tier >= 2 ? "§d✦ Tier II - upgraded" : "§eTier I §8(use an Upgrader)"));
        m.lore(lore);

        m.setItemModel(new NamespacedKey("relicpowers", pw.id));
        m.setMaxStackSize(1);
        m.setEnchantmentGlintOverride(tier >= 2 || pw.rarity != Power.Rarity.COMMON);
        m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

        PersistentDataContainer pdc = m.getPersistentDataContainer();
        pdc.set(K_POWER, PersistentDataType.STRING, pw.id);
        pdc.set(K_TIER, PersistentDataType.INTEGER, tier);
        pdc.set(K_UID, PersistentDataType.STRING, UUID.randomUUID().toString());
        it.setItemMeta(m);
        return it;
    }

    public ItemStack createTool(String tool) {
        boolean up = tool.equals("upgrader");
        ItemStack it = new ItemStack(up ? Material.AMETHYST_SHARD : Material.ECHO_SHARD);
        ItemMeta m = it.getItemMeta();
        m.displayName(Util.c(up ? "§6§lRelic Upgrader" : "§b§lRelic Reroller"));
        List<Component> lore = new ArrayList<>();
        if (up) {
            lore.add(Util.c("§7Right-click to upgrade your relic"));
            lore.add(Util.c("§7to §dTier II§7: stronger passive,"));
            lore.add(Util.c("§7stronger ability, shorter cooldowns."));
        } else {
            lore.add(Util.c("§7Right-click to swap your relic for"));
            lore.add(Util.c("§7a different random one."));
            lore.add(Util.c("§7Resets you to Tier I."));
        }
        lore.add(Util.c(""));
        lore.add(Util.c("§8Consumed on use"));
        m.lore(lore);
        m.setItemModel(new NamespacedKey("relicpowers", tool));
        m.setEnchantmentGlintOverride(true);
        m.getPersistentDataContainer().set(K_TOOL, PersistentDataType.STRING, tool);
        it.setItemMeta(m);
        return it;
    }

    public ItemStack createCoin() {
        ItemStack it = new ItemStack(Material.GOLD_NUGGET);
        ItemMeta m = it.getItemMeta();
        m.setItemModel(new NamespacedKey("relicpowers", "coin"));
        it.setItemMeta(m);
        return it;
    }

    // ------------------------------------------------------------------ item identification
    public Power powerOf(ItemStack it) {
        if (it == null || it.getType().isAir() || !it.hasItemMeta()) return null;
        String id = it.getItemMeta().getPersistentDataContainer().get(K_POWER, PersistentDataType.STRING);
        return id == null ? null : Powers.get(id);
    }
    public int tierOf(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return 1;
        Integer t = it.getItemMeta().getPersistentDataContainer().get(K_TIER, PersistentDataType.INTEGER);
        return t == null ? 1 : Math.max(1, Math.min(2, t));
    }
    public String toolOf(ItemStack it) {
        if (it == null || it.getType().isAir() || !it.hasItemMeta()) return null;
        return it.getItemMeta().getPersistentDataContainer().get(K_TOOL, PersistentDataType.STRING);
    }
    public boolean isRelicItem(ItemStack it) { return powerOf(it) != null; }

    public void tagProjectile(Projectile proj, Power pw, int tier) {
        proj.getPersistentDataContainer().set(K_PROJ, PersistentDataType.STRING, pw.id + ":" + tier);
    }

    // ------------------------------------------------------------------ per-player data
    public Power getPower(Player p) {
        String id = p.getPersistentDataContainer().get(K_POWER, PersistentDataType.STRING);
        return id == null ? null : Powers.get(id);
    }
    public int getTier(Player p) {
        Integer t = p.getPersistentDataContainer().get(K_TIER, PersistentDataType.INTEGER);
        return t == null ? 1 : Math.max(1, Math.min(2, t));
    }
    public State state(Player p) { return cache.get(p.getUniqueId()); }

    public boolean hasPowerItem(Player p, Power pw) {
        for (ItemStack i : p.getInventory().getContents()) {
            if (i != null && powerOf(i) == pw) return true;
        }
        return false;
    }

    private boolean passivesEnabled(Player p, Power pw) {
        String mode = pl.getConfig().getString("passive-mode", "INVENTORY").toUpperCase();
        if (mode.equals("ALWAYS")) return true;
        if (mode.equals("HAND")) {
            return powerOf(p.getInventory().getItemInMainHand()) == pw || powerOf(p.getInventory().getItemInOffHand()) == pw;
        }
        return hasPowerItem(p, pw);
    }

    /** Recompute whether the player's passives are active; cleans up when they stop. */
    public void refresh(Player p) {
        UUID id = p.getUniqueId();
        Power pw = getPower(p);
        State old = cache.get(id);
        State neu = (pw != null && passivesEnabled(p, pw)) ? new State(pw, getTier(p)) : null;
        if (old != null && (neu == null || old.power() != neu.power())) old.power().onRemove(p);
        if (neu == null) cache.remove(id); else cache.put(id, neu);
    }

    // ------------------------------------------------------------------ giving / removing
    public void giveItem(Player p, ItemStack item) {
        Map<Integer, ItemStack> left = p.getInventory().addItem(item);
        for (ItemStack i : left.values()) p.getWorld().dropItem(p.getLocation(), i);
    }

    /** Removes every (non-unique) power item from the inventory. */
    public void removePowerItems(Player p) {
        PlayerInventory inv = p.getInventory();
        ItemStack[] c = inv.getContents();
        for (int i = 0; i < c.length; i++) {
            Power x = powerOf(c[i]);
            if (x != null && x.rarity != Power.Rarity.UNIQUE) inv.setItem(i, null);
        }
    }

    private void swapPowerItem(Player p, ItemStack replacement) {
        PlayerInventory inv = p.getInventory();
        ItemStack[] c = inv.getContents();
        int first = -1;
        for (int i = 0; i < c.length; i++) {
            Power x = powerOf(c[i]);
            if (x == null || x.rarity == Power.Rarity.UNIQUE) continue;
            if (first == -1) first = i; else inv.setItem(i, null);
        }
        if (first >= 0) inv.setItem(first, replacement); else giveItem(p, replacement);
    }

    public void setPower(Player p, Power pw, int tier) {
        if (pw.rarity == Power.Rarity.UNIQUE) { giveMace(p); return; }
        UUID id = p.getUniqueId();
        Power old = getPower(p);
        removePowerItems(p);
        if (old != null && old != pw) {
            old.onRemove(p);
            old.cleanup(id);
            cache.remove(id);
        }
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(K_POWER, PersistentDataType.STRING, pw.id);
        pdc.set(K_TIER, PersistentDataType.INTEGER, tier);
        pdc.set(K_GIVEN, PersistentDataType.BYTE, (byte) 1);
        giveItem(p, createItem(pw, tier));
        refresh(p);
    }

    public void removePower(Player p) {
        UUID id = p.getUniqueId();
        Power old = getPower(p);
        removePowerItems(p);
        if (old != null) { old.onRemove(p); old.cleanup(id); }
        cache.remove(id);
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.remove(K_POWER);
        pdc.remove(K_TIER);
        pdc.set(K_GIVEN, PersistentDataType.BYTE, (byte) 1);
    }

    public void setTier(Player p, int tier) {
        Power cur = getPower(p);
        if (cur == null) return;
        p.getPersistentDataContainer().set(K_TIER, PersistentDataType.INTEGER, tier);
        swapPowerItem(p, createItem(cur, tier));
        refresh(p);
    }

    public Power randomPower(Power exclude, boolean allowLegendary) {
        List<Power> commons = new ArrayList<>();
        List<Power> legends = new ArrayList<>();
        for (Power x : Powers.all()) {
            if (x == exclude) continue;
            if (x.rarity == Power.Rarity.COMMON) commons.add(x);
            else if (x.rarity == Power.Rarity.LEGENDARY) legends.add(x);
        }
        if (allowLegendary && !legends.isEmpty() && Util.chance(pl.getConfig().getDouble("legendary-reroll-chance", 0.06))) {
            return legends.get(Util.rndInt(legends.size()));
        }
        if (commons.isEmpty()) return null;
        return commons.get(Util.rndInt(commons.size()));
    }

    // ------------------------------------------------------------------ upgrader / reroller
    public boolean upgrade(Player p) {
        Power cur = getPower(p);
        if (cur == null) { Util.msg(p, "§cYou don't have a relic power to upgrade."); return false; }
        if (getTier(p) >= 2) { Util.msg(p, "§cYour " + cur.name() + " is already Tier II."); return false; }
        setTier(p, 2);
        Util.msg(p, "§d✦ " + cur.name() + " ascended to §lTier II§d!");
        Util.sound(p.getLocation(), "block.beacon.power_select", 1f, 1.4f);
        Util.p(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 60, 0.4, 0.8, 0.4, 0.3);
        return true;
    }

    public boolean reroll(Player p) {
        Power cur = getPower(p);
        Power pick = randomPower(cur, true);
        if (pick == null) { Util.msg(p, "§cNo other relic is available."); return false; }
        setPower(p, pick, 1);
        Util.msg(p, "§bYour relic was rerolled into " + pick.color + "§l" + pick.name() + "§b!");
        Util.sound(p.getLocation(), "entity.player.levelup", 1f, 0.8f);
        Util.p(Particle.ENCHANT, p.getLocation().add(0, 1.2, 0), 80, 0.5, 0.8, 0.5, 1.0);
        return true;
    }

    /** Right-click with an upgrader/reroller in hand. */
    public void useTool(Player p, String tool) {
        boolean ok = tool.equals("upgrader") ? upgrade(p) : reroll(p);
        if (!ok) return;
        PlayerInventory inv = p.getInventory();
        ItemStack h = inv.getItemInMainHand();
        h.setAmount(h.getAmount() - 1);
        inv.setItemInMainHand(h.getAmount() <= 0 ? null : h);
    }

    // ------------------------------------------------------------------ the unique Mace
    public void giveMace(Player p) {
        Power mace = Powers.get("mace");
        if (mace == null) return;
        for (Player o : Bukkit.getOnlinePlayers()) {
            if (!o.equals(p)) removeMace(o);
        }
        maceHolder = p.getUniqueId();
        data.set("mace-holder", maceHolder.toString());
        saveData();
        if (!hasPowerItem(p, mace)) giveItem(p, createItem(mace, 1));
        Util.msg(p, "§4§lYou now hold the one and only Mace!");
    }
    public void removeMace(Player p) {
        Power mace = Powers.get("mace");
        PlayerInventory inv = p.getInventory();
        ItemStack[] c = inv.getContents();
        for (int i = 0; i < c.length; i++) {
            if (powerOf(c[i]) == mace) inv.setItem(i, null);
        }
    }
    public void clearMace() {
        for (Player o : Bukkit.getOnlinePlayers()) removeMace(o);
        maceHolder = null;
        data.set("mace-holder", null);
        saveData();
    }

    /** Deletes relic items that don't belong to this player (stolen/duplicated) and extra copies of their own. */
    public void cleanForeign(Player p) {
        Power own = getPower(p);
        PlayerInventory inv = p.getInventory();
        ItemStack[] c = inv.getContents();
        boolean seenOwn = false;
        for (int i = 0; i < c.length; i++) {
            Power x = powerOf(c[i]);
            if (x == null) continue;
            if (x.rarity == Power.Rarity.UNIQUE) {
                if (!p.getUniqueId().equals(maceHolder)) inv.setItem(i, null);
                continue;
            }
            if (x != own || seenOwn) inv.setItem(i, null); else seenOwn = true;
        }
    }

    /** Re-give anything the player should own but is missing (join, respawn). */
    public void ensureItems(Player p) {
        Power cur = getPower(p);
        if (cur != null && !hasPowerItem(p, cur)) giveItem(p, createItem(cur, getTier(p)));
        Power mace = Powers.get("mace");
        if (mace == null) return;
        boolean has = hasPowerItem(p, mace);
        if (p.getUniqueId().equals(maceHolder)) {
            if (!has) giveItem(p, createItem(mace, 1));
        } else if (has) {
            removeMace(p);
        }
    }

    public void onJoin(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        if (!pdc.has(K_GIVEN, PersistentDataType.BYTE)) {
            pdc.set(K_GIVEN, PersistentDataType.BYTE, (byte) 1);
            if (pl.getConfig().getBoolean("give-on-first-join", true) && getPower(p) == null) {
                Power pick = randomPower(null, false);
                if (pick != null) {
                    setPower(p, pick, 1);
                    Util.msg(p, "§6You were chosen by " + pick.color + "§l" + pick.name() + "§6! Type §f/powers§6 to see every relic.");
                }
            }
        }
        ensureItems(p);
        p.discoverRecipe(new NamespacedKey(pl, "upgrader"));
        p.discoverRecipe(new NamespacedKey(pl, "reroller"));
        refresh(p);
    }

    public void onQuit(Player p) {
        UUID id = p.getUniqueId();
        for (Power pw : Powers.all()) {
            try { pw.onRemove(p); } catch (Throwable ignored) { }
            pw.cleanup(id);
        }
        cache.remove(id);
        SpellUtil.sweep(p, true);
        pl.realm.onQuit(p);
    }

    // ------------------------------------------------------------------ tick loop
    public void startTasks() {
        tickTask = Bukkit.getScheduler().runTaskTimer(pl, () -> {
            counter++;
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.isDead()) continue;
                try {
                    if (counter % 2 == 1) cleanForeign(p);
                    refresh(p);
                    State s = cache.get(p.getUniqueId());
                    if (s != null) s.power().tick(p, s.tier());
                    if (counter % 2 == 0) SpellUtil.sweep(p, false);
                } catch (Throwable t) {
                    pl.getLogger().warning("Tick error: " + t);
                }
            }
        }, 10L, 10L);
    }

    public void shutdown() {
        if (tickTask != null) tickTask.cancel();
        for (Player p : Bukkit.getOnlinePlayers()) {
            for (Power pw : Powers.all()) {
                try { pw.onRemove(p); } catch (Throwable ignored) { }
            }
            SpellUtil.sweep(p, true);
        }
        cache.clear();
    }

    // ------------------------------------------------------------------ ability dispatch
    /** Uses ability `slot` of the relic in the main hand, or else the player's own relic. */
    public void useAbility(Player p, int slot) {
        UUID id = p.getUniqueId();
        if (p.isDead() || p.getGameMode() == GameMode.SPECTATOR) return;
        if (timers.active(id, "freeze") || timers.active(id, "stun")) {
            Util.bar(p, "§cYou can't use abilities right now!");
            return;
        }
        if (timers.active(id, "nullified")) {
            Util.bar(p, "§5Your abilities are nullified for §f" + (int) Math.ceil(timers.left(id, "nullified")) + "s");
            return;
        }
        ItemStack hand = p.getInventory().getItemInMainHand();
        Power pw = powerOf(hand);
        int tier = pw == null ? 1 : tierOf(hand);
        if (pw != null && pw.rarity != Power.Rarity.UNIQUE && pw != getPower(p)) pw = null; // someone else's relic
        if (pw != null && pw.rarity == Power.Rarity.UNIQUE && !p.getUniqueId().equals(maceHolder)) pw = null;
        if (pw == null) {
            pw = getPower(p);
            tier = getTier(p);
        }
        if (pw == null) {
            Util.msg(p, "§cYou don't have a relic power. Craft a Reroller or ask an admin.");
            return;
        }
        if (slot < 1 || slot > pw.abilityCount()) {
            Util.bar(p, pw.color + pw.name() + " §chas no ability " + slot);
            return;
        }
        String ck = pw.id + ":" + slot;
        if (cooldowns.active(id, ck)) {
            Util.bar(p, "§c" + pw.name() + " ability " + slot + " is on cooldown: §f" + (int) Math.ceil(cooldowns.left(id, ck)) + "s");
            return;
        }
        boolean used;
        try {
            used = pw.ability(p, slot, tier);
        } catch (Throwable t) {
            pl.getLogger().warning("Ability error in " + pw.id + ": " + t);
            return;
        }
        if (used) {
            cooldowns.set(id, ck, pw.cooldown(slot, tier));
            Util.bar(p, pw.color + "§l" + pw.name() + " §7- ability " + slot + " used");
        }
    }
}
