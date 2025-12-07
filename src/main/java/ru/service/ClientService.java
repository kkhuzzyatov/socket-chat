package ru.service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.function.Consumer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ClientService {
    private final Logger logger = LogManager.getLogger(ClientService.class);

    private Selector selector;
    private SocketChannel channel;
    private final String clientUuid;

    public ClientService(String clientUuid) {
        this.clientUuid = clientUuid;
    }

    public SocketChannel getChannel() {
        return channel;
    }

    public void connect(String host, int port) {
        try {
            selector = Selector.open();
            channel = SocketChannel.open();
            channel.configureBlocking(false);

            while (true) {
                try {
                    channel.connect(new InetSocketAddress(host, port));
                    channel.register(selector, SelectionKey.OP_CONNECT);
                    logger.info("(clientUuid = {}) Подключение к {}:{}", clientUuid, host, port);
                    break;
                } catch (Exception e) {
                    logger.error("(clientUuid = {}) Не удалось подключиться: {}", clientUuid, e.getMessage());
                    Thread.sleep(500); // небольшая пауза перед повтором
                }
            }
        } catch (Exception e) {
            logger.error("(clientUuid = {}) Ошибка подключения: {}", clientUuid, e.getMessage());
        }
    }

    public void sendAction(String action) {
        if (action != null && !action.isEmpty()) sendRaw(action);
    }

    public void sendChatName(String chat) {
        if (chat != null && !chat.isEmpty()) sendRaw(chat);
    }

    public void sendMessage(String message) {
        if (message != null && !message.isEmpty()) sendRaw(message);
    }

    private void sendRaw(String text) {
        if (channel == null || !channel.isConnected()) {
            logger.warn("(clientUuid = {}) Канал не подключен, сообщение не отправлено: {}", clientUuid, text);
            return;
        }
        try {
            ByteBuffer buffer = ByteBuffer.wrap(text.getBytes());
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            logger.info("(clientUuid = {}) Отправлено: {}", clientUuid, text);
        } catch (Exception e) {
            logger.error("(clientUuid = {}) Ошибка отправки '{}': {}", clientUuid, text, e.getMessage());
        }
    }

    public void listen(Consumer<String> messageConsumer) {
        try {
            while (true) {
                if (selector.select() == 0) continue;
                Iterator<SelectionKey> keys = selector.selectedKeys().iterator();

                while (keys.hasNext()) {
                    SelectionKey key = keys.next();
                    keys.remove();

                    if (key.isConnectable()) {
                        SocketChannel sc = (SocketChannel) key.channel();
                        if (sc.isConnectionPending()) sc.finishConnect();
                        sc.register(selector, SelectionKey.OP_READ);
                        String logMsg = "Успешно подключено.";
                        logger.info("(clientUuid = {}) {}", clientUuid, logMsg);
                    }

                    if (key.isReadable()) {
                        SocketChannel sc = (SocketChannel) key.channel();
                        ByteBuffer buffer = ByteBuffer.allocate(1024); // увеличим буфер для длинных сообщений
                        int bytes = sc.read(buffer);

                        if (bytes == -1) {
                            String logMsg = "Сервер закрыл соединение.";
                            logger.info("(clientUuid = {}) {}", clientUuid, logMsg);
                            if (messageConsumer != null) messageConsumer.accept(logMsg);
                            sc.close();
                            return;
                        }

                        buffer.flip();
                        String message = new String(buffer.array(), 0, buffer.limit()).trim();
                        if (!message.isEmpty()) {
                            logger.info("(clientUuid = {}) Получено: {}", clientUuid, message);
                            if (messageConsumer != null) messageConsumer.accept(message);
                        }
                    }
                }
            }
        } catch (Exception e) {
            String errorMsg = "Ошибка получения данных: " + e.getMessage();
            logger.error("(clientUuid = {}) {}", clientUuid, errorMsg, e);
            if (messageConsumer != null) messageConsumer.accept(errorMsg);
        }
    }

    public void run(InputStreamReader inputReader, Consumer<String> messageConsumer, String host, Integer port, String action, String chat, String username) {
        logger.info(
                "(clientUuid = {}) Host={}, Port={}, User={}, Chat={}, Action={}",
                clientUuid, host, port, username, chat, action
        );

        connect(host, port);

        new Thread(() -> {
            try {
                Thread.sleep(200);

                sendAction(action);
                sendChatName(chat);

                BufferedReader console = new BufferedReader(inputReader);
                while (true) {
                    String line = console.readLine();
                    if (line == null || line.isEmpty()) continue;

                    if (line.length() > 255) {
                        messageConsumer.accept("Сообщение слишком длинное.");
                        continue;
                    }

                    sendMessage("[" + username + "] " + line);
                }
            } catch (Exception ignored) {
            }
        }).start();

        listen(messageConsumer);
    }
}