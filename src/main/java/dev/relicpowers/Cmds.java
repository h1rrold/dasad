package dev.relicpowers;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class Cmds implements CommandExecutor, TabCompleter {
    private final RelicPowers pl;

    public Cmds(RelicPowers pl) { this.pl = pl; }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        String n = cmd.getName().toLowerCase(Locale.ROOT);
        if (n.equals("ability")) return ability(s, a);
        if (n.equals("powers")) {
            if (!(s instanceof Player)) { s.sendMessage("Players only."); return true; }
            Gui.open((Player) s);
            return true;
        }
        return relic(s, a);
    }

    private boolean ability(CommandSender s, String[] a) {
        if (!(s instanceof Player)) { s.sendMessage("Players only."); return true; }
        Player p = (Player) s;
        if (a.length < 1) {
            Util.msg(p, "§7Usage: §f/ability 1 §7or §f/ability 2");
            return true;
        }
        int slot;
        try { slot = Integer.parseInt(a[0]); } catch (NumberFormatException ex) { Util.msg(p, "§cUse /ability 1 or /ability 2"); return true; }
        pl.mgr.useAbility(p, slot);
        return true;
    }

    private void help(CommandSender s) {
        s.sendMessage("§6/relic give <player> <power|upgrader|reroller>");
        s.sendMessage("§6/relic set <player> <power> [tier 1|2]");
        s.sendMessage("§6/relic tier <player> <1|2>   §7/relic remove <player>");
        s.sendMessage("§6/relic reroll <player>   §7/relic upgrade <player>");
        s.sendMessage("§6/relic info [player]   §7/relic reload   §7/relic clearmace");
    }

    private boolean relic(CommandSender s, String[] a) {
        if (!s.hasPermission("relicpowers.admin")) { s.sendMessage("§cNo permission."); return true; }
        if (a.length == 0) { help(s); return true; }
        String sub = a[0].toLowerCase(Locale.ROOT);

        if (sub.equals("reload")) {
            pl.reloadConfig();
            s.sendMessage("§aConfig reloaded.");
            return true;
        }
        if (sub.equals("clearmace")) {
            pl.mgr.clearMace();
            s.sendMessage("§aThe Mace was removed from everyone.");
            return true;
        }
        if (sub.equals("info")) {
            Player t = a.length > 1 ? Bukkit.getPlayerExact(a[1]) : (s instanceof Player ? (Player) s : null);
            if (t == null) { s.sendMessage("§cPlayer not found."); return true; }
            Power pw = pl.mgr.getPower(t);
            s.sendMessage("§6" + t.getName() + ": §f" + (pw == null ? "no relic" : pw.name() + " (tier " + pl.mgr.getTier(t) + ")"));
            return true;
        }

        if (a.length < 2) { help(s); return true; }
        Player t = Bukkit.getPlayerExact(a[1]);
        if (t == null) { s.sendMessage("§cPlayer not found."); return true; }

        switch (sub) {
            case "give": {
                if (a.length < 3) { help(s); return true; }
                String what = a[2].toLowerCase(Locale.ROOT);
                if (what.equals("upgrader") || what.equals("reroller")) {
                    pl.mgr.giveItem(t, pl.mgr.createTool(what));
                    s.sendMessage("§aGave " + t.getName() + " a " + what + ".");
                    return true;
                }
                Power pw = Powers.get(what);
                if (pw == null) { s.sendMessage("§cUnknown relic. Use tab-complete."); return true; }
                pl.mgr.setPower(t, pw, 1);
                s.sendMessage("§aGave " + t.getName() + " " + pw.name() + ".");
                return true;
            }
            case "set": {
                if (a.length < 3) { help(s); return true; }
                Power pw = Powers.get(a[2]);
                if (pw == null) { s.sendMessage("§cUnknown relic."); return true; }
                int tier = a.length > 3 && a[3].equals("2") ? 2 : 1;
                pl.mgr.setPower(t, pw, tier);
                s.sendMessage("§aSet " + t.getName() + " to " + pw.name() + " (tier " + tier + ").");
                return true;
            }
            case "tier": {
                if (a.length < 3) { help(s); return true; }
                if (pl.mgr.getPower(t) == null) { s.sendMessage("§c" + t.getName() + " has no relic."); return true; }
                pl.mgr.setTier(t, a[2].equals("2") ? 2 : 1);
                s.sendMessage("§aTier updated.");
                return true;
            }
            case "remove": {
                pl.mgr.removePower(t);
                s.sendMessage("§aRemoved " + t.getName() + "'s relic.");
                return true;
            }
            case "reroll": {
                pl.mgr.reroll(t);
                return true;
            }
            case "upgrade": {
                pl.mgr.upgrade(t);
                return true;
            }
            default:
                help(s);
                return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> out = new ArrayList<>();
        if (!cmd.getName().equalsIgnoreCase("relic")) {
            if (cmd.getName().equalsIgnoreCase("ability") && a.length == 1) { out.add("1"); out.add("2"); }
            return out;
        }
        if (a.length == 1) {
            for (String x : new String[] {"give", "set", "tier", "remove", "reroll", "upgrade", "info", "reload", "clearmace"}) {
                if (x.startsWith(a[0].toLowerCase(Locale.ROOT))) out.add(x);
            }
        } else if (a.length == 2) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(a[1].toLowerCase(Locale.ROOT))) out.add(p.getName());
            }
        } else if (a.length == 3 && (a[0].equalsIgnoreCase("give") || a[0].equalsIgnoreCase("set"))) {
            List<String> ids = Powers.ids();
            if (a[0].equalsIgnoreCase("give")) { ids.add("upgrader"); ids.add("reroller"); }
            for (String id : ids) if (id.startsWith(a[2].toLowerCase(Locale.ROOT))) out.add(id);
        } else if (a.length == 3 && a[0].equalsIgnoreCase("tier")) {
            out.add("1");
            out.add("2");
        } else if (a.length == 4 && a[0].equalsIgnoreCase("set")) {
            out.add("1");
            out.add("2");
        }
        return out;
    }
}
