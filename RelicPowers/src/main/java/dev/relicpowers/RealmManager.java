package dev.relicpowers;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/** The private void "dimension" used by Heavens Eye. */
public final class RealmManager {
    /** Empty chunks = void world. */
    public static final class VoidGen extends ChunkGenerator {
        @Override public Location getFixedSpawnLocation(World world, Random random) {
            return new Location(world, 0.5, 101, 0.5);
        }
    }

    private static final class Session {
        Player caster;
        Player target;
        Location casterOrigin;
        Location targetOrigin;
        Set<UUID> victims = new HashSet<>();
        double damage;
        boolean ended;
        BukkitTask task;
    }

    private final RelicPowers pl;
    private World world;
    private final Map<UUID, Session> sessions = new HashMap<>();

    public RealmManager(RelicPowers pl) { this.pl = pl; }

    private World world() {
        if (world != null) return world;
        World w = Bukkit.getWorld("relics_realm");
        if (w == null) {
            WorldCreator wc = new WorldCreator("relics_realm");
            wc.generator(new VoidGen());
            wc.generateStructures(false);
            w = wc.createWorld();
            if (w != null) buildArena(w);
        }
        if (w != null) {
            w.setSpawnFlags(false, false);
            w.setTime(18000L);
        }
        world = w;
        return w;
    }

    private void buildArena(World w) {
        int y = 100;
        int r = 13;
        for (int x = -r - 2; x <= r + 2; x++) {
            for (int z = -r - 2; z <= r + 2; z++) {
                double d = Math.sqrt((double) (x * x + z * z));
                if (d <= r) {
                    Material m = ((x + z) & 1) == 0 ? Material.POLISHED_BLACKSTONE : Material.CRYING_OBSIDIAN;
                    if (d < 2) m = Material.SEA_LANTERN;
                    w.getBlockAt(x, y, z).setType(m, false);
                } else if (d <= r + 1.6) {
                    for (int h = 1; h <= 10; h++) w.getBlockAt(x, y + h, z).setType(Material.BARRIER, false);
                }
                if (d <= r + 1.6) w.getBlockAt(x, y + 11, z).setType(Material.BARRIER, false);
            }
        }
    }

    public boolean inRealm(Player p) { return sessions.containsKey(p.getUniqueId()); }

    /** Sends the caster and target to the realm for `seconds`, then back; nearby players take `dmg` at the end. */
    public boolean start(Player caster, Player target, int seconds, double dmg, double nearbyRadius) {
        World w = world();
        if (w == null) {
            Util.msg(caster, "§cThe realm world could not be created.");
            return false;
        }
        if (sessions.containsKey(caster.getUniqueId()) || sessions.containsKey(target.getUniqueId())) return false;

        final Session s = new Session();
        s.caster = caster;
        s.target = target;
        s.casterOrigin = caster.getLocation().clone();
        s.targetOrigin = target.getLocation().clone();
        s.damage = dmg;
        List<Player> near = Util.playersNear(caster.getLocation(), nearbyRadius, caster);
        for (Player o : near) s.victims.add(o.getUniqueId());
        s.victims.add(target.getUniqueId());

        sessions.put(caster.getUniqueId(), s);
        sessions.put(target.getUniqueId(), s);

        caster.teleport(new Location(w, 8.5, 101, 0.5, 90f, 0f));
        target.teleport(new Location(w, -7.5, 101, 0.5, -90f, 0f));
        Util.sound(caster.getLocation(), "block.portal.travel", 0.7f, 1.4f);
        Util.sound(target.getLocation(), "block.portal.travel", 0.7f, 1.4f);
        Util.msg(caster, "§5Heavens Eye pulls you and §f" + target.getName() + "§5 into another dimension...");
        Util.msg(target, "§5" + caster.getName() + "'s Heavens Eye pulled you into another dimension!");

        s.task = new BukkitRunnable() {
            int left = seconds;
            @Override public void run() {
                if (s.ended) { cancel(); return; }
                if (left <= 0) { end(s, true); cancel(); return; }
                Util.bar(s.caster, "§5Heavens Eye §7- §f" + left + "s");
                Util.bar(s.target, "§5Heavens Eye §7- §f" + left + "s");
                left--;
            }
        }.runTaskTimer(pl, 0L, 20L);
        return true;
    }

    private void end(Session s, boolean damage) {
        if (s.ended) return;
        s.ended = true;
        sessions.remove(s.caster.getUniqueId());
        sessions.remove(s.target.getUniqueId());
        if (s.task != null) s.task.cancel();
        if (s.caster.isOnline() && !s.caster.isDead()) s.caster.teleport(s.casterOrigin);
        if (s.target.isOnline() && !s.target.isDead()) s.target.teleport(s.targetOrigin);
        if (!damage) return;
        List<UUID> ids = new ArrayList<>(s.victims);
        for (UUID id : ids) {
            Player o = Bukkit.getPlayer(id);
            if (o == null || !o.isOnline() || o.isDead()) continue;
            Util.trueDamage(o, s.damage, s.caster);
            Util.p(Particle.EXPLOSION, o.getLocation().add(0, 1, 0), 1, 0, 0, 0, 0);
            Util.sound(o.getLocation(), "entity.generic.explode", 0.8f, 1.2f);
        }
    }

    public void onQuit(Player p) {
        Session s = sessions.get(p.getUniqueId());
        if (s != null) end(s, false);
    }

    public void onDeath(Player p) {
        Session s = sessions.get(p.getUniqueId());
        if (s != null) end(s, false);
    }

    public void shutdown() {
        for (Session s : new ArrayList<>(new HashSet<>(sessions.values()))) end(s, false);
    }
}
