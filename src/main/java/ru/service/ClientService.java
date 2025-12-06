package ru.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Iterator;

public class ClientService {
    private final Logger logger = LogManager.getLogger(ClientService.class);

    private Selector selector;
    private SocketChannel channel;
    private final String clientUuid;

    public ClientService(String clientUuid) {
        this.clientUuid = clientUuid;
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
                }
            }
        } catch (Exception e) {
            logger.error("(clientUuid = {}) Ошибка подключения: {}", clientUuid, e.getMessage());
        }
    }

    public void sendAction(String action) {
        sendRaw(action);
    }

    public void sendChatName(String chat) {
        sendRaw(chat);
    }

    public void sendMessage(String message) {
        sendRaw(message);
    }

    private void sendRaw(String text) {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(text.getBytes());
            channel.write(buffer);
            logger.info("(clientUuid = {}) Отправлено: {}", clientUuid, text);
        } catch (Exception e) {
            logger.error("(clientUuid = {}) Ошибка отправки {}", clientUuid, e.getMessage());
        }
    }

    public void listen() {
        try {
            while (true) {
                selector.select();
                Iterator<SelectionKey> keys = selector.selectedKeys().iterator();

                while (keys.hasNext()) {
                    SelectionKey key = keys.next();
                    keys.remove();

                    if (key.isConnectable()) {
                        SocketChannel sc = (SocketChannel) key.channel();
                        if (sc.isConnectionPending()) sc.finishConnect();
                        sc.register(selector, SelectionKey.OP_READ);
                        logger.info("(clientUuid = {}) Успешно подключено.", clientUuid);
                    }

                    if (key.isReadable()) {
                        SocketChannel sc = (SocketChannel) key.channel();
                        ByteBuffer buffer = ByteBuffer.allocate(256);
                        int bytes = sc.read(buffer);

                        if (bytes == -1) {
                            logger.info("(clientUuid = {}) Сервер закрыл соединение.", clientUuid);
                            sc.close();
                            return;
                        }

                        buffer.flip();
                        String message = new String(buffer.array(), 0, buffer.limit());
                        System.out.println(message.trim());
                        logger.info("(clientUuid = {}) Получено: {}", clientUuid, message.trim());
                    }
                }
            }
        } catch (Exception e) {
            logger.error("(clientUuid = {}) Ошибка получения данных: {}", clientUuid, e.getMessage());
        }
    }
}