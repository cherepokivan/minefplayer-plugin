package com.example.minef;

/*
 * ВАЖНО ОБ ЭТОМ ФАЙЛЕ:
 *
 * Точные имена классов/пакетов MCProtocolLib МЕНЯЛИСЬ между версиями
 * (старые релизы от Steveice10, новые - от GeyserMC, org.geysermc.mcprotocollib.*).
 * Ниже - рабочая схема на основе актуальной (на момент написания) структуры
 * проекта GeyserMC/MCProtocolLib. Перед компиляцией:
 *
 *   1. Откройте https://github.com/GeyserMC/MCProtocolLib и посмотрите
 *      папку src/main/java/.../protocol/packet/ingame/serverbound/
 *      чтобы найти актуальный класс для отправки чата/команды
 *      (обычно что-то вроде ServerboundChatPacket).
 *   2. При необходимости поправьте импорты ниже под реальную версию,
 *      которую вы прописали в pom.xml.
 *
 * Это нормальная часть работы с такими библиотеками - API у них не всегда
 * стабилен между мажорными версиями.
 */

import org.bukkit.Bukkit;
import org.geysermc.mcprotocollib.protocol.MinecraftProtocol;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatPacket;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.tcp.TcpClientSession;
import org.geysermc.mcprotocollib.network.event.session.SessionAdapter;
import org.geysermc.mcprotocollib.network.event.session.DisconnectedEvent;
import org.geysermc.mcprotocollib.network.event.session.ConnectedEvent;

public class BotSession {

    private final MinefPlugin plugin;
    private Session session;
    private volatile boolean shouldRun = false;

    // Имя виртуального игрока - должно быть свободно на сервере
    private static final String BOT_USERNAME = "VoiceBridgeBot";

    public BotSession(MinefPlugin plugin) {
        this.plugin = plugin;
    }

    public synchronized void start() {
        if (session != null && session.isConnected()) {
            plugin.getLogger().info("[Minef] Уже подключён.");
            return;
        }

        shouldRun = true;
        connect();
    }

    private void connect() {
        String host = "127.0.0.1"; // подключаемся сами к себе (loopback)
        int port = Bukkit.getPort();

        MinecraftProtocol protocol = new MinecraftProtocol(BOT_USERNAME);
        session = new TcpClientSession(host, port, protocol);

        session.addListener(new SessionAdapter() {
            @Override
            public void connected(ConnectedEvent event) {
                plugin.getLogger().info("[Minef] Виртуальный игрок подключился.");
            }

            @Override
            public void disconnected(DisconnectedEvent event) {
                plugin.getLogger().info("[Minef] Отключился: " + event.getReason());
                if (shouldRun) {
                    // реконнект через 10 секунд, в основном потоке планировщика Bukkit
                    Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, BotSession.this::connect, 200L);
                }
            }
        });

        session.connect();
    }

    public synchronized void stop() {
        shouldRun = false;
        if (session != null) {
            session.disconnect("Остановлено вручную");
            session = null;
        }
    }

    /**
     * Отправляет текст от имени виртуального игрока.
     * Если text начинается с "/", сервер обработает это как команду -
     * так работает обычный чат-пакет ванильного протокола, отдельного
     * пакета для команд не требуется.
     */
    public synchronized boolean say(String text) {
        if (session == null || !session.isConnected()) {
            return false;
        }
        session.send(new ServerboundChatPacket(text));
        return true;
    }

    public synchronized boolean isConnected() {
        return session != null && session.isConnected();
    }
}
