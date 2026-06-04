package me.aethelster.aethelguard;

import org.bukkit.Server;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

public final class CommandCompletions {

    private CommandCompletions() {
    }

    public static List<String> filter(String current, Collection<String> options) {
        String normalized = current == null ? "" : current.toLowerCase(Locale.ROOT);
        List<String> completions = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(normalized)) {
                completions.add(option);
            }
        }
        return completions;
    }

    public static List<String> onlinePlayers(Server server, String current) {
        List<String> names = new ArrayList<>();
        for (Player player : server.getOnlinePlayers()) {
            names.add(player.getName());
        }
        return filter(current, names);
    }

    public static TabCompleter firstArgument(Collection<String> options) {
        return (sender, command, alias, args) -> args.length == 1
                ? filter(args[0], options)
                : List.of();
    }
}
