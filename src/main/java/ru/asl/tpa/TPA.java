package ru.asl.tpa;

import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static ru.asl.tpa.TPA.Action.giveInvoice;
import static ru.asl.tpa.TPA.PlayerData.*;
import static org.bukkit.ChatColor.translateAlternateColorCodes;

public final class TPA extends JavaPlugin {

    static TPA instance;

    static YAML mainConfig;
    static YAML playerData;

    static ItemStack coin;
    static long invoiceTimer;
    static String invoiceMessage;

    @Override
    public void onEnable() {
        instance = this;

        mainConfig = new YAML(getDataFolder() + "/config.yml", this);
        playerData = new YAML(getDataFolder() + "/data.yml", this);

        loadConfig();

        Objects.requireNonNull(getCommand("ta")).setExecutor(new CMDHandler());

        getServer().getPluginManager().registerEvents(new PlayerListener(), this);

        InvoiceTypes.load();
        init();
    }

    void onReload() {
        for (Player p : Bukkit.getOnlinePlayers())
            unload(p);
        flush();

        mainConfig.reload();
        playerData.reload();

        loadConfig();

        InvoiceTypes.load();
        init();
    }

    void log(String msg) {
        Bukkit.getConsoleSender().sendMessage(ChatColor.YELLOW + msg);
    }

    void error(String msg) {
        Bukkit.getConsoleSender().sendMessage(ChatColor.RED + msg);
    }

    @NotNull static String c(String msg) { return translateAlternateColorCodes('&', msg); }

    void loadConfig() {
        String matName = mainConfig.getString("coin-item.material");
        Material mat = Material.matchMaterial(matName);

        Objects.requireNonNull(mat, "Incorrect material provided for coin item: " + matName);

        coin = new ItemStack(mat);

        String displayName = null;
        List<String> lore = null;
        int modelData = -1;

        if (mainConfig.contains("coin-item.display-name"))
            displayName = mainConfig.getString("coin-item.display-name");
        if (mainConfig.contains("coin-item.lore"))
            lore = mainConfig.getStringList("coin-item.lore");
        if (mainConfig.contains("coin-item.modeldata"))
            modelData = mainConfig.getInt("coin-item.modeldata");

        ItemMeta meta = coin.getItemMeta();
        if (meta != null) {
            if (displayName != null)
                meta.setDisplayName(c(displayName));

            if (lore != null) {
                List<String> colored = new ArrayList<>();
                for (String str : lore)
                    colored.add(c(str));
                meta.setLore(colored);
            }
            if (modelData > 0)
                meta.setCustomModelData(modelData);
        }
        coin.setItemMeta(meta);

        invoiceTimer = mainConfig.getLong("invoice-timer", 3600, true) * 20;

        invoiceMessage = mainConfig.getString("invoice-message","You received {invoice} coins for playing!", true);
    }

    @Override public void onDisable() {
        for (Player p : Bukkit.getOnlinePlayers())
            unload(p);
        flush();
    }

    static Random rnd = new Random();

    static Item addItem(Player p, int amount) {
        ItemStack clone = coin.clone();

        if (amount > 0)
            clone.setAmount(amount);

        final Item item = p.getWorld().dropItem(p.getLocation().add(rnd.nextDouble(), rnd.nextDouble(), rnd.nextDouble()), clone);
        item.setPickupDelay(25);
        p.spawnParticle(Particle.REVERSE_PORTAL, item.getLocation(),128,1,1,1);
        return item;
    }

    static class TimedAction extends BukkitRunnable {
        static final long CHECK_PERIOD = 100L;

        static final ConcurrentMap<UUID, TimedAction> timers = new ConcurrentHashMap<>();

        Player p;
        long savedTime;
        int taskId;
        boolean executed;

        TimedAction(Player p, long savedTime) {
            this.p = p;
            this.savedTime = savedTime;
            execute();
            timers.put(p.getUniqueId(), this);
        }

        void execute() {
            if (executed) return;

            BukkitTask task = this.runTaskTimer(instance, CHECK_PERIOD, CHECK_PERIOD);
            this.taskId = task.getTaskId();
            executed = true;
        }

        void shutdown() {
            Bukkit.getScheduler().cancelTask(taskId);
        }

        @Override public void run() {
            if (savedTime - CHECK_PERIOD <= 0) {
                savedTime = invoiceTimer;
                giveInvoice(p);
            } else
                savedTime -= CHECK_PERIOD;
        }

    }

    static final class Action {

        static void giveInvoice(Player p) {
            for (InvoiceTypes.Invoice invoice : InvoiceTypes.types)
                if (p.hasPermission(invoice.permission)) {
                    Item item = addItem(p, invoice.coinAmount);

                    if (!invoice.commands.isEmpty())
                        for (String command : invoice.commands)
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), format(command, item));

                    p.sendMessage(c(invoiceMessage.replace("{invoice}", String.valueOf(invoice.coinAmount))));
                    return;
                }
        }

        static String format(String in, Item it) {
            return in.replace("{item:pos.x}", String.valueOf(it.getLocation().getX()))
                    .replace("{item:pos.y}", String.valueOf(it.getLocation().getY()))
                    .replace("{item:pos.z}", String.valueOf(it.getLocation().getZ()));
        }

    }

    static final class InvoiceTypes {
        static final List<Invoice> types = new ArrayList<>();

        record Invoice(String permission, int coinAmount, List<String> commands,int priority) {

        }

        static void load() {
            int priority = 0;
            for (String key : mainConfig.getSection("invoice").getKeys(false)) {
                String permission = mainConfig.getString("invoice." + key + ".permission", "invoice.some-permission", true);
                int coinAmount = mainConfig.getInt("invoice." + key + ".coin-amount", 1, true);
                List<String> commands = mainConfig.getStringList("invoice." + key + ".commands");
                int pr = 0;

                if (mainConfig.contains("invoice." + key + ".priority"))
                    pr = mainConfig.getInt("invoice." + key + ".priority", priority, true);

                types.add(new Invoice(permission, coinAmount, commands, pr));

                types.sort(Comparator.comparingInt(Invoice::priority));
                priority++;
            }
        }
    }

    static class PlayerListener implements Listener {
        @EventHandler(priority = EventPriority.HIGHEST)
        public void onPlayerJoin(PlayerJoinEvent e) {
            load(e.getPlayer());
        }

        @EventHandler(priority = EventPriority.HIGHEST)
        public void onPlayerQuit(PlayerQuitEvent e) {
            unload(e.getPlayer());
        }
    }

    static class PlayerData {

        static final ConcurrentMap<UUID, Long> invoiceTime = new ConcurrentHashMap<>();

        static void init() {
            List<String> pdata = playerData.getStringList("player-data");
            if (pdata.isEmpty()) return;

            for (String param : pdata) {
                String[] params = param.split(":");

                if (params.length <= 1) continue;

                invoiceTime.put(UUID.fromString(params[0]), Long.parseLong(params[1]));
            }
        }

        static void load(Player p) {
            long savedTime = invoiceTimer;
            if (invoiceTime.containsKey(p.getUniqueId()))
                savedTime = invoiceTime.get(p.getUniqueId());

            new TimedAction(p, savedTime);
        }

        static void unload(Player p) {
            TimedAction action = TimedAction.timers.get(p.getUniqueId());

            if (action == null) return;

            action.shutdown();

            invoiceTime.put(p.getUniqueId(), action.savedTime);
        }

        static void flush() {
            List<String> pdata = new ArrayList<>();
            for (Map.Entry<UUID, Long> entry : invoiceTime.entrySet())
                pdata.add(entry.getKey().toString() + ":" + entry.getValue());

            playerData.set("player-data", pdata);
        }

    }

    static class CMDHandler implements CommandExecutor, TabCompleter {

        @Override public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
            if (args.length <= 0)
                sender.sendMessage(ChatColor.RED + "Command usage: /ta reload");
            else if (args[0].equalsIgnoreCase("reload")) {
                TPA.instance.onReload();
                sender.sendMessage(ChatColor.GREEN + "[TimedPlayerActions] Successfully reloaded!");
            }

            return true;
        }

        @Override public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
            if (command.getName().equalsIgnoreCase("ta"))
                return List.of("reload");

            return null;
        }

    }

}
