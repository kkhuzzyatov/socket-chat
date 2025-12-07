package ru.service;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;
import org.apache.logging.log4j.*;

public class ServerService implements Runnable {
    private static final Logger logger = LogManager.getLogger(ServerService.class);
    // key = chatName, val = chatSubs
    private static final Map<String, Set<SocketChannel>> chats = new HashMap<>();
    // key = client, val = action
    private static final Map<SocketChannel, String> clientAction = new HashMap<>();
    // key = client, val = chatName
    private static final Map<SocketChannel, String> clientChats = new HashMap<>();

    private static Integer port;

    public static void main(String[] args) {
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-p":
                case "--port":
                    if (i + 1 < args.length) {
                        try {
                            port = Integer.parseInt(args[++i]);
                        } catch (NumberFormatException e) {
                            logger.error("Ошибка: порт должен быть числом");
                        }
                    }
                    break;
                default:
                    logger.warn("Неизвестный аргумент: {}", args[i]);
            }
        }
        ServerService server = new ServerService();
        server.run();
    }

    @Override
    public void run() {
        try {
            Scanner in = new Scanner(System.in);
            if (port == null) {
                System.out.print("Введите порт для запуска сервера: ");
                while (!in.hasNextInt()) {
                    System.out.print("Введите число: ");
                    in.next();
                }
                port = in.nextInt();
            }

            logger.info("Используемый порт: {}", port);

            Selector selector = Selector.open();
            ServerSocketChannel serverChannel = ServerSocketChannel.open();
            while (true) {
                try {
                    serverChannel.bind(new InetSocketAddress(port));
                    logger.info("Канал сервера связан с портом: {}", port);
                    break;
                } catch (Exception e) {
                    System.out.println("Порт занят. Укажите другой.");
                    logger.error("Ошибка связывания порта {}", port, e);
                    System.out.print("Введите порт для запуска сервера: ");
                    while (!in.hasNextInt()) {
                        System.out.print("Введите число: ");
                        in.next();
                    }
                    port = in.nextInt();
                }
            }

            serverChannel.configureBlocking(false);
            serverChannel.register(selector, SelectionKey.OP_ACCEPT);
            logger.info("Сервер запущен.");

            while (true) {
                selector.select();
                Iterator<SelectionKey> keyIterator = selector.selectedKeys().iterator();
                while (keyIterator.hasNext()) {
                    SelectionKey key = keyIterator.next();
                    keyIterator.remove();

                    if (key.isAcceptable()) {
                        ServerSocketChannel srv = (ServerSocketChannel) key.channel();
                        SocketChannel client = srv.accept();
                        client.configureBlocking(false);
                        client.register(selector, SelectionKey.OP_READ, "chat");
                        logger.info("Клиент с адресом {} подключился.", client.getRemoteAddress());
                        client.register(selector, SelectionKey.OP_READ, "action");
                    } else if (key.isReadable()) {
                        SocketChannel client = (SocketChannel) key.channel();
                        ByteBuffer buffer = ByteBuffer.allocate(256);
                        int read;
                        try {
                            read = client.read(buffer);
                        } catch (IOException e) {
                            read = -1;
                        }
                        if (read == -1) {
                            logger.info("Клиент с адресом {} отключён.", client.getRemoteAddress());
                            key.cancel();
                            chats.get("main").remove(client);
                            client.close();
                            continue;
                        }

                        buffer.flip();
                        String input = new String(buffer.array(), 0, buffer.limit());

                        String state = (String) key.attachment();
                        if (state.equals("action")) {
                            clientAction.put(client, input);
                            key.attach("chat");
                            logger.info("Клиент с адресом {} собирается исполнить действие {}", client.getRemoteAddress(), input);
                        } else if (state.equals("chat")) {
                            if (clientAction.get(client).equals("create")) {
                                Set<SocketChannel> clientsOfChat = new HashSet<>();
                                clientsOfChat.add(client);
                                chats.put(input, clientsOfChat);
                                logger.info("Клиент с адресом {} создал чат {}", client.getRemoteAddress(), input);
                            } else {
                                chats.get(input).add(client);
                                logger.info("Клиент с адресом {} присоединился к чату {}", client.getRemoteAddress(), input);
                            }
                            clientChats.put(client, input);
                            key.attach("null");
                        } else {
                            String chatName = clientChats.get(client);
                            sendNewMessageToSubs(input, chatName);
                            logger.info("Клиент с адресом {} отправил в чат {} сообщение: {}", client.getRemoteAddress(), input, chatName);
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Ошибка сервера", e);
        }
    }

    private static void sendNewMessageToSubs(String msg, String chatName) {
        ByteBuffer buffer = ByteBuffer.wrap(msg.getBytes());
        Set<SocketChannel> clientsOfChat = chats.get(chatName);
        synchronized (clientsOfChat) {
            Iterator<SocketChannel> it = clientsOfChat.iterator();
            while (it.hasNext()) {
                SocketChannel client = it.next();
                try {
                    buffer.rewind();
                    client.write(buffer);
                } catch (IOException e) {
                    logger.error("Ошибка отправки сообщения клиенту (IO): {}", client, e);
                    closeClient(it, client);
                } catch (Exception e) {
                    logger.error("Неизвестная ошибка при отправке сообщения клиенту: {}", client, e);
                    closeClient(it, client);
                }
            }
        }
    }

    private static void closeClient(Iterator<SocketChannel> it, SocketChannel client) {
        try {
            client.close();
        } catch (IOException closeError) {
            logger.error("Ошибка закрытия канала клиента: {}", client, closeError);
        }
        it.remove();
    }
}