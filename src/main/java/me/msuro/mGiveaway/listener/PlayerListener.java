package me.msuro.mGiveaway.listener;

import me.msuro.mGiveaway.Giveaway;
import me.msuro.mGiveaway.MGiveaway;
import me.msuro.mGiveaway.utils.ConfigUtil;
import me.msuro.mGiveaway.utils.GiveawayManager;
import me.msuro.mGiveaway.utils.TextUtil;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;

public class PlayerListener implements Listener {

    private final MGiveaway instance;

    public PlayerListener() {
        instance = MGiveaway.getInstance();
        instance.getServer().getPluginManager().registerEvents(this, instance);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        instance.getServer().getScheduler().runTaskLater(instance, new Runnable() {
            @Override
            public void run() {
                GiveawayManager manager = instance.getGiveawayManager();
                for (Giveaway giveaway : manager.listGiveaways().values()) {
                    if (giveaway.state() == Giveaway.State.STARTED) {
                        String message = TextUtil.process(TextUtil.replacePlaceholders(ConfigUtil.getAndValidate(ConfigUtil.GIVEAWAY_INFO_PERSONAL_ON_JOIN), Map.of(
                                "%name%", giveaway.name(),
                                "%prize%", giveaway.prize(),
                                "%time_left%", giveaway.getTimeLeft(),
                                "%win_count%", String.valueOf(giveaway.winCount())
                        )));
                        event.getPlayer().sendMessage(message);
                    }
                }
            }
        }, 20L);
    }
}
