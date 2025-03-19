package me.msuro.mGiveaway.commands;

import me.msuro.mGiveaway.MGiveaway;
import me.msuro.mGiveaway.utils.ConfigUtil;
import me.msuro.mGiveaway.utils.TextUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Objects;

public class Reload implements CommandExecutor {

    private final MGiveaway instance;

    public Reload() {
        this.instance = MGiveaway.getInstance();
        Objects.requireNonNull(this.instance.getCommand("mgwreload")).setExecutor(this);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if(!command.getName().equalsIgnoreCase("mgwreload")) return true;
        if(!sender.hasPermission("mgiveaway.reload")) {
            sender.sendMessage(TextUtil.process(ConfigUtil.getAndValidate(ConfigUtil.MESSAGES_IN_GAME_NO_PERMISSION)));
            return true;
        }
        long now = System.currentTimeMillis();
        sender.sendMessage(TextUtil.process(TextUtil.replacePlaceholders("%prefix% &7Reloading plugin...", Map.of("%prefix%", TextUtil.prefix))));
        instance.reloadPlugin();
        sender.sendMessage(TextUtil.process(TextUtil.replacePlaceholders("%prefix% &7Plugin reloaded! " + "&8(&a" + (System.currentTimeMillis() - now) + "ms&8)", Map.of("%prefix%", TextUtil.prefix))));

        return true;
    }
}
