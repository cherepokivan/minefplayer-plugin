package com.example.minef;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MinefCommand implements CommandExecutor, TabCompleter {

    private final MinefPlugin plugin;
    private final BotSession session;

    public MinefCommand(MinefPlugin plugin, BotSession session) {
        this.plugin = plugin;
        this.session = session;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("Использование: /minef <start|stop|say> [текст]");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "start":
                session.start();
                sender.sendMessage("[Minef] Виртуальный игрок запускается...");
                return true;

            case "stop":
                session.stop();
                sender.sendMessage("[Minef] Виртуальный игрок отключён.");
                return true;

            case "status":
                sender.sendMessage(session.isConnected()
                        ? "[Minef] Виртуальный игрок подключён."
                        : "[Minef] Виртуальный игрок не подключён.");
                return true;

            case "say":
                if (args.length < 2) {
                    sender.sendMessage("[Minef] Укажите текст (без \"/\") или команду (с \"/\") после 'say'");
                    return true;
                }
                String payload = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                boolean ok = session.say(payload);
                if (!ok) {
                    sender.sendMessage("[Minef] Не удалось отправить - виртуальный игрок не подключён. Сначала /minef start");
                }
                return true;

            default:
                sender.sendMessage("[Minef] Неизвестная подкоманда: " + args[0]);
                return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("start", "stop", "status", "say");
        }
        return Collections.emptyList();
    }
}
