package com.example.minef;

/*
 * Актуально под GeyserMC/MCProtocolLib, ветка feature/26.2 (проверено
 * по исходникам напрямую - см. историю переписки для деталей).
 *
 * Ключевые отличия от более старых версий библиотеки:
 * - Класса TcpClientSession больше нет, вместо него ClientNetworkSession,
 *   создаваемая через ClientNetworkSessionFactory.
 * - connect()/isConnected() - на интерфейсе ClientSession (наследует Session).
 * - Команды (текст с "/") и обычные сообщения - РАЗНЫЕ пакеты:
 *   ServerboundChatCommandPacket для команд (просто строка без "/"),
 *   ServerboundChatPacket для обычного чата - и он требует полный набор
 *   полей подписи сообщения (timestamp/salt/signature/...), т.к. в этой
 *   версии протокола это часть механизма защищённого чата.
 *   Ниже сообщения отправляются БЕЗ подписи (signature = null) - это
 *   может не сработать на серверах, где включена принудительная
 *   secure chat проверка. Команды (наш основной сценарий - /dvc ...)
 *   этой проблемы не имеют вообще.
 */

import org.bukkit.Bukkit;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.geysermc.mcprotocollib.network.ClientSession;
import org.geysermc.mcprotocollib.network.factory.ClientNetworkSessionFactory;
import org.geysermc.mcprotocollib.network.event.session.SessionAdapter;
import org.geysermc.mcprotocollib.network.event.session.DisconnectedEvent;
import org.geysermc.mcprotocollib.network.event.session.ConnectedEvent;
import org.geysermc.mcprotocollib.protocol.MinecraftProtocol;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatCommandPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatPacket;

import java.util.BitSet;

public class BotSession {

    private final MinefPlugin plugin;
    private ClientSession session;
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

        session = ClientNetworkSessionFactory.factory()
            .setAddress(host, port)
            .setProtocol(protocol)
            .create();

        session.addListener(new SessionAdapter() {
            @Override
            public void connected(ConnectedEvent event) {
                plugin.getLogger().info("[Minef] Виртуальный игрок подключился.");
            }

            @Override
            public void disconnected(DisconnectedEvent event) {
                String reason = event.getReason() != null
                    ? PlainTextComponentSerializer.plainText().serialize(event.getReason())
                    : "неизвестна";
                plugin.getLogger().info("[Minef] Отключился: " + reason);
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
     * Если text начинается с "/" - отправляется как команда
     * (ServerboundChatCommandPacket, без "/"). Иначе - как обычное
     * сообщение чата (ServerboundChatPacket, без подписи - см.
     * предупреждение в шапке файла).
     */
    public synchronized boolean say(String text) {
        if (session == null || !session.isConnected()) {
            return false;
        }

        if (text.startsWith("/")) {
            session.send(new ServerboundChatCommandPacket(text.substring(1)));
        } else {
            session.send(new ServerboundChatPacket(
                text,
                System.currentTimeMillis(),
                0L,
                null,
                0,
                new BitSet(20),
                0
            ));
        }
        return true;
    }

    public synchronized boolean isConnected() {
        return session != null && session.isConnected();
    }
}
