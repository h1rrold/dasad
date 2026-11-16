package dev.relicpowers;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class RelicPowers extends JavaPlugin {
    private static RelicPowers instance;

    public PowerManager mgr;
    public RealmManager realm;

    public static RelicPowers get() { return instance; }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        mgr = new PowerManager(this);
        realm = new RealmManager(this);
        Powers.init(this);
        mgr.loadData();
        Recipes.register(this);

        Bukkit.getPluginManager().registerEvents(new RelicListener(this), this);

        Cmds cmds = new Cmds(this);
        for (String name : new String[] {"ability", "relic", "powers"}) {
            PluginCommand pc = getCommand(name);
            if (pc != null) {
                pc.setExecutor(cmds);
                pc.setTabCompleter(cmds);
            }
        }

        mgr.startTasks();
        for (Player p : Bukkit.getOnlinePlayers()) mgr.onJoin(p);
        getLogger().info("RelicPowers enabled with " + Powers.all().size() + " relics.");
    }

    @Override
    public void onDisable() {
        if (mgr != null) mgr.shutdown();
        if (realm != null) realm.shutdown();
        Decoys.removeAll();
        Recipes.unregister(this);
    }
}
