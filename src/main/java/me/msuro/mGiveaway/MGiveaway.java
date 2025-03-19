package me.msuro.mGiveaway;

import me.msuro.mGiveaway.commands.Reload;
import me.msuro.mGiveaway.discord.DiscordListener;
import me.msuro.mGiveaway.listener.PlayerListener;
import me.msuro.mGiveaway.utils.*;
import net.dv8tion.jda.api.JDA;
import net.md_5.bungee.api.chat.*;
import net.milkbowl.vault.permission.Permission;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SingleLineChart;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
@SuppressWarnings("deprecation")
public final class MGiveaway extends JavaPlugin {

    private static MGiveaway instance;
    private DiscordUtil discordUtil;
    private DBUtils dbUtils;
    private GiveawayManager giveawayManager; // Use GiveawayManager
    private static boolean pausePlugin = false;
    private Metrics metrics;
    private BukkitTask updateGiveaways;
    private BukkitTask updateCheck;
    private BukkitTask saveEntries;
    private Permission perms = null;

    @Override
    public void onEnable() {
        instance = this;
        getLogger().info("Enabling plugin...");

        try {
            Class.forName("com.destroystokyo.paper.PaperConfig");
        } catch (ClassNotFoundException e) {
            getLogger().warning("This plugin runs better on Paper. Consider switching to Paper for best performance.");
        }

        getLogger().info("Loading bStats...");
        metrics = new Metrics(this, 24362);

        // --- Check for PlaceholderAPI and Vault ---
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null || !Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            getLogger().severe("PlaceholderAPI not found or not enabled! Disabling plugin...");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        if (Bukkit.getPluginManager().getPlugin("Vault") == null || !Bukkit.getPluginManager().isPluginEnabled("Vault")) {
            getLogger().severe("Vault not found or not enabled! Disabling plugin...");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        getLogger().info("PlaceholderAPI and Vault found and enabled!");

        if (!setupPermissions()) {
            getLogger().severe("Vault permissions not found or not enabled! Disabling plugin...");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        // --- Initialize GiveawayManager and other components ---
        giveawayManager = new GiveawayManager();
        new PlayerListener();
        new Reload();
        new ConfigUtil();
        TextUtil.setInstance(this);
        TextUtil.prefix = ConfigUtil.getOrDefault(ConfigUtil.PREFIX);
        discordUtil = new DiscordUtil();
        dbUtils = new DBUtils();

        if (isPaused())
            return;

        discordUtil.build();

        if (isPaused())
            return;

        new DiscordListener();

        if (isPaused())
            return;


        getLogger().info("Plugin enabled!");

        metrics.addCustomChart(new SingleLineChart("active-giveaways", new Callable<Integer>() {
            @Override
            public Integer call() throws Exception {
                // (This is useless as there is already a player chart by default.)
                return giveawayManager.listGiveaways().size();
            }
        }));

        // --- Asynchronous Tasks (updateGiveaways and updateCheck) ---
        saveEntries = saveEntries();
        updateGiveaways = resetUpdateGiveaways();
        updateCheck = getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            UpdateChecker.init(this, 122302).requestUpdateCheck().whenComplete((result, e) -> {
                        this.getLogger().info("Checking for updates...");
                        if (result.requiresUpdate()) {
                            BaseComponent[] message = TextComponent.fromLegacyText(
                                    TextUtil.toMinecraftHex(
                                            TextUtil.process(ConfigUtil.getAndValidate(ConfigUtil.UPDATE_AVAILABLE)))
                                            .replace("%current_version%", instance.getDescription().getVersion())
                                            .replace("%new_version%", result.getNewestVersion()));

                            // Attach the click and hover events to each component.
                            for (BaseComponent component : message) {
                                component.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://modrinth.com/plugin/mGiveaway"));
                                component.setHoverEvent(new HoverEvent(
                                        HoverEvent.Action.SHOW_TEXT,
                                        TextComponent.fromLegacyText(
                                                TextUtil.toMinecraftHex(
                                                        TextUtil.process(ConfigUtil.getAndValidate(ConfigUtil.UPDATE_AVAILABLE_HOVER)))
                                                        .replace("%new_version%", result.getNewestVersion())
                                                        .replace("%current_version%", instance.getDescription().getVersion()))));

                            }

                            this.getServer().getConsoleSender().spigot().sendMessage(message);
                            for (Player p : Bukkit.getOnlinePlayers()) {
                                Bukkit.getScheduler().runTask(this, () -> {
                                    if (p.isOp()) {
                                        p.spigot().sendMessage(message);
                                    }
                                });
                            }
                            return;
                        }

                        UpdateChecker.UpdateReason reason = result.getReason();
                        if (reason == UpdateChecker.UpdateReason.UP_TO_DATE) {
                            this.getLogger().info(String.format("Your version of mGiveaway (%s) is up to date!", result.getNewestVersion()));
                        } else if (reason == UpdateChecker.UpdateReason.UNRELEASED_VERSION) {
                            this.getLogger().info(String.format("Your version of mGiveaway (%s) is more recent than the one publicly available. Are you on a development build?", result.getNewestVersion()));
                        } else {
                            this.getLogger().warning("Could not check for a new version of mGiveaway. Reason: " + reason);
                        }
                    }
            );
        }, 120, 20 * 60 * 60 * 6);

    }


    @Override
    public void onDisable() {
        if (updateGiveaways != null && !updateGiveaways.isCancelled()) {
            updateGiveaways.cancel();
        }

        if (updateCheck != null && !updateCheck.isCancelled()) {
            updateCheck.cancel();
        }

        // Save entries for all giveaways (using GiveawayManager)
        if (giveawayManager != null) {
            for (Giveaway giveaway : giveawayManager.listGiveaways().values()) {
                dbUtils.saveEntries(giveaway);
            }
        }

        try {
            if (discordUtil == null) return;
            JDA jda = discordUtil.getJDA();
            if (jda == null) return;
            getLogger().info("Shutting down Discord bot...");
            jda.shutdown();
            if (!jda.awaitShutdown(Duration.ofSeconds(10))) {
                jda.shutdownNow();
                jda.awaitShutdown();
            }
            getLogger().info("Discord bot shut down successfully!");
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public static MGiveaway getInstance() {
        return instance;
    }

    public DiscordUtil getDiscordUtil() {
        return discordUtil;
    }


    private boolean setupPermissions() {
        RegisteredServiceProvider<Permission> rsp = getServer().getServicesManager().getRegistration(Permission.class);
        assert rsp != null;
        perms = rsp.getProvider();
        return true;
    }

    public Permission getPerms() {
        return perms;
    }

    public DBUtils getDBUtil() {
        return dbUtils;
    }

    // --- Access Giveaways through GiveawayManager ---
    public Giveaway getGiveaway(String name) {
        return giveawayManager.listGiveaways().get(name); // Use GiveawayManager
    }

    public static boolean isPaused() {
        return pausePlugin;
    }

    public static void setPaused(boolean paused) {
        pausePlugin = paused;
    }

    public void reloadPlugin() {
        onDisable();

        for (Giveaway giveaway : giveawayManager.listGiveaways().values()) {
            dbUtils.saveEntries(giveaway);
        }
        giveawayManager.clearGiveaways();

        setPaused(false);

        getLogger().info("Reloading plugin...");

        getLogger().info("Resetting bStats...");
        metrics.shutdown();
        metrics = new Metrics(this, 24362);

        getLogger().info("Reloading config...");
        new ConfigUtil();
        TextUtil.setInstance(this);
        TextUtil.prefix = ConfigUtil.getOrDefault(ConfigUtil.PREFIX);

        getLogger().info("Reloading Discord bot...");
        if (discordUtil != null && discordUtil.getJDA() != null && discordUtil.getJDA().getStatus() == JDA.Status.CONNECTED) {
            discordUtil.getJDA().shutdown();
        }
        discordUtil = new DiscordUtil();
        discordUtil.build();
        new DiscordListener();
        dbUtils = new DBUtils();
        giveawayManager.fetchGiveaways();
        if (updateGiveaways != null && !updateGiveaways.isCancelled()) {
            updateGiveaways.cancel();
        }
        resetUpdateGiveaways();
        if (saveEntries != null && !saveEntries.isCancelled()) {
            saveEntries.cancel();
        }
        saveEntries();

        getLogger().info("Reloading plugin complete!");
    }

    public void clearGiveaways() {
        giveawayManager.clearGiveaways();
    }

    public GiveawayManager getGiveawayManager() {
        return giveawayManager;
    }

    private BukkitTask saveEntries() {
        if (saveEntries != null && !saveEntries.isCancelled()) {
            saveEntries.cancel();
        }
        saveEntries = getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            if (!isPaused()) {
                for (Giveaway giveaway : new ArrayList<>(giveawayManager.listGiveaways().values())) {
                    if (giveaway.state() == Giveaway.State.STARTED)
                        dbUtils.saveEntries(giveaway);
                }
            }
        }, 120, 20 * 60 * 10);
        return saveEntries;
    }

    private BukkitTask resetUpdateGiveaways() {
        if (updateGiveaways != null && !updateGiveaways.isCancelled()) {
            updateGiveaways.cancel();
        }
        int interval = ConfigUtil.getInt(ConfigUtil.BROADCAST_INTERVAL) / 60 == 0 ? 1 : ConfigUtil.getInt(ConfigUtil.BROADCAST_INTERVAL) / 60;
        final int[] n = {interval - 1};
        updateGiveaways = getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            if (!isPaused()) {
                String message = ConfigUtil.getAndValidate(ConfigUtil.BROADCAST_MESSAGE);
                n[0]++;
                ConfigUtil.reloadConfig();
                TextUtil.prefix = ConfigUtil.getOrDefault(ConfigUtil.PREFIX);
                HashMap<String, Giveaway> newGiveaways = giveawayManager.fetchGiveaways();

                for (String name : newGiveaways.keySet()) {
                    if (giveawayManager.listGiveaways().get(name) == null) {
                        getLogger().info("New giveaway found: " + newGiveaways.get(name).name());
                        giveawayManager.putGiveaway(newGiveaways.get(name));
                    }
                }

                for (Giveaway giveaway : giveawayManager.listGiveaways().values()) {
                    if (giveaway.shouldStart()) {
                        giveawayManager.startGiveaway(giveaway);
                    }
                    if (giveaway.shouldEnd()) {
                        giveawayManager.endGiveaway(giveaway);
                    } else if (!giveaway.state().equals(Giveaway.State.ENDED)
                            && giveaway.state().equals(Giveaway.State.STARTED)
                            && n[0] % interval == 0) {
                        Bukkit.broadcastMessage(TextUtil.process(TextUtil.replacePlaceholders(message, Map.of(
                                "%win_count%", String.valueOf(giveaway.winCount()),
                                "%prize%", giveaway.minecraftPrize(),
                                "%time_left%", giveaway.getTimeLeft()
                        ))));
                        n[0] = 0;
                    }
                }
            }
        }, 120, 20 * 60);
        return updateGiveaways;
    }

}
