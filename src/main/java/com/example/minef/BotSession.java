package com.example.minef;

/*
 * Актуально под GeyserMC/MCProtocolLib, ветка feature/26.2 (проверено
 * по исходникам напрямую).
 *
 * ВАЖНОЕ ДОПОЛНЕНИЕ (после диагностики зависания в фазе CONFIGURATION):
 * ClientListener библиотеки сам отвечает на все служебные пакеты сервера
 * (FinishConfiguration, SelectKnownPacks, KeepAlive...), но НЕ отправляет
 * то, что реальный клиент шлёт по своей инициативе сразу при входе в
 * конфигурацию - "Client Information" (локаль/настройки) и "brand"
 * (какой это клиент - "vanilla", "fabric" и т.д.). Некоторые серверные
 * плагины ждут именно эти данные, прежде чем завершить конфигурацию для
 * игрока - без них голый бот молча зависает, даже когда сама библиотека
 * работает правильно. Поэтому здесь мы отправляем их сами, один раз,
 * сразу как только выходное состояние сессии переключилось в CONFIGURATION.
 */

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.geysermc.mcprotocollib.network.ClientSession;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.factory.ClientNetworkSessionFactory;
import org.geysermc.mcprotocollib.network.event.session.SessionAdapter;
import org.geysermc.mcprotocollib.network.event.session.DisconnectedEvent;
import org.geysermc.mcprotocollib.network.event.session.ConnectedEvent;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.MinecraftProtocol;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftTypes;
import org.geysermc.mcprotocollib.protocol.data.ProtocolState;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.HandPreference;
import org.geysermc.mcprotocollib.protocol.data.game.setting.ChatVisibility;
import org.geysermc.mcprotocollib.protocol.data.game.setting.ParticleStatus;
import org.geysermc.mcprotocollib.protocol.data.game.setting.SkinPart;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundClientInformationPacket;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundCustomPayloadPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatCommandPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatPacket;

import java.util.Arrays;
import java.util.BitSet;

public class BotSession {

    private final MinefPlugin plugin;
    private ClientSession session;
    private volatile boolean shouldRun = false;
    private volatile boolean sentClientInfo = false;

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

        sentClientInfo = false;
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
            public void packetReceived(Session session, Packet packet) {
                // Как только сессия перешла в CONFIGURATION - шлём то, что
                // обычно шлёт реальный клиент сам, без запроса сервера.
                if (!sentClientInfo && session.getPacketProtocol().getOutboundState() == ProtocolState.CONFIGURATION) {
                    sentClientInfo = true;
                    sendClientInfoAndBrand(session);
                }
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

    private void sendClientInfoAndBrand(Session session) {
        session.send(new ServerboundClientInformationPacket(
            "en_us",
            10,
            ChatVisibility.FULL,
            true,
            Arrays.asList(SkinPart.VALUES),
            HandPreference.RIGHT_HAND,
            false,
            true,
            ParticleStatus.ALL
        ));

        ByteBuf brandBuf = Unpooled.buffer();
        MinecraftTypes.writeString(brandBuf, "vanilla");
        byte[] brandData = new byte[brandBuf.readableBytes()];
        brandBuf.readBytes(brandData);
        session.send(new ServerboundCustomPayloadPacket(Key.key("minecraft:brand"), brandData));
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
