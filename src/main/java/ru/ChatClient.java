package ru;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.Scanner;
import java.util.UUID;

import org.apache.logging.log4j.*;
import picocli.CommandLine;
import picocli.CommandLine.Option;

public class ChatClient implements Runnable {
    private final Logger logger = LogManager.getLogger(ChatClient.class);
    private String clientUuid ;

    @Option(names = {"-h", "--host"}, description = "Server host")
    private String host;

    @Option(names = {"-p", "--port"}, description = "Server port")
    private Integer port;

    @Option(names = {"-a", "--action"}, description = "Action")
    private String action;

    @Option(names = {"-c", "--chat"}, description = "Chat")
    private String chat;

    @Option(names = {"-u", "--username"}, description = "Username")
    private String username;

    public static void main(String[] args) {
        ChatClient client = new ChatClient();
        CommandLine cmd = new CommandLine(client);
        cmd.parseArgs(args);
        client.run();
    }

    @Override
    public void run() {
        clientUuid = UUID.randomUUID().toString();
        try {
            Scanner scanner = new Scanner(System.in);
            if (host == null) {
                System.out.print("Введите адрес сервера: ");
                host = scanner.nextLine();
            }
            // TODO: host validation
            if (port == null) {
                System.out.print("Введите порт сервера: ");
                port = scanner.nextInt();
                scanner.nextLine();
            }
            // TODO: port validation
            if (action == null) {
                System.out.print("Введите действие с чатом: ");
                action = scanner.nextLine();
            }
            // TODO: action validation (action.equals("create") || action.equals("connect"))
            if (chat == null) {
                System.out.print("Введите название чата: ");
                chat = scanner.nextLine();
            }
            // TODO: chat validation (not empty and shorter then 50)
            if (username == null) {
                System.out.print("Введите имя: ");
                username = scanner.nextLine();
            }
            // TODO: username validation (username.length() <= 50)

            logger.info("(clientUuid = {}) Используемые параметры - Host: {}, Port: {}, Username: {}, Chat: {}, Action: {}", clientUuid, host, port, username, chat, action);

            Selector selector = Selector.open();
            SocketChannel clientChannel = SocketChannel.open();
            clientChannel.configureBlocking(false);

            while (true) {
                try {
                    clientChannel.connect(new InetSocketAddress(host, port));
                    clientChannel.register(selector, SelectionKey.OP_CONNECT);
                    logger.info("(clientUuid = {}) Попытка подключения к {}:{}", clientUuid, host, port);
                    break;
                } catch (Exception e) {
                    System.out.println("Подключиться невозможно. Попробуйте снова.");
                    logger.error("(clientUuid = {}) Ошибка подключения {}", clientUuid, e.getMessage());
                }
            }

            BufferedReader console = new BufferedReader(new InputStreamReader(System.in));

            new Thread(() -> {
                try {
                    while (!clientChannel.isConnected()) {
                        Thread.sleep(50);
                    }

                    clientChannel.write(ByteBuffer.wrap(action.getBytes()));
                    logger.info("(clientUuid = {}) Действие для чата отправлено: {}", clientUuid, action);

                    clientChannel.write(ByteBuffer.wrap(chat.getBytes()));
                    logger.info("(clientUuid = {}) Имя чата отправлено: {}", clientUuid, chat);

                    while (true) {
                        String line = console.readLine();
                        if (line == null || line.isEmpty()) continue;

                        if (line.length() > 255) {
                            System.out.println("Сообщение слишком длинное.");
                            logger.error("(clientUuid = {}) Слишком длинное сообщение ({} символов)", clientUuid, line.length());
                            continue;
                        }

                        String message = "[" + username + "] " + line;
                        ByteBuffer buffer = ByteBuffer.wrap(message.getBytes());
                        clientChannel.write(buffer);
                        logger.info("(clientUuid = {}) Отправлено: {}", clientUuid, message);
                    }
                } catch (Exception e) {
                    logger.error("(clientUuid = {}) Ошибка в потоке отправки {}", clientUuid, e.getMessage());
                }
            }).start();

            while (true) {
                selector.select();
                Iterator<SelectionKey> it = selector.selectedKeys().iterator();
                while (it.hasNext()) {
                    SelectionKey key = it.next();
                    it.remove();

                    try {
                        if (key.isConnectable()) {
                            SocketChannel sc = (SocketChannel) key.channel();
                            if (sc.isConnectionPending()) sc.finishConnect();
                            sc.register(selector, SelectionKey.OP_READ);
                            logger.info("(clientUuid = {}) Клиент подключен к серверу.", clientUuid);
                        }

                        if (key.isReadable()) {
                            SocketChannel sc = (SocketChannel) key.channel();
                            ByteBuffer buffer = ByteBuffer.allocate(256);
                            int read = sc.read(buffer);
                            if (read == -1) {
                                logger.error("(clientUuid = {}) Соединение закрыто сервером.", clientUuid);
                                sc.close();
                                return;
                            }
                            buffer.flip();
                            String msg = new String(buffer.array(), 0, buffer.limit());
                            logger.info("(clientUuid = {}) Получено: {}", clientUuid, msg.trim());
                            System.out.println(msg.trim());
                        }
                    } catch (Exception e) {
                        logger.error("(clientUuid = {}) Ошибка обработки ключа {}", clientUuid, e.getMessage());
                        key.cancel();
                        key.channel().close();
                    }
                }
            }

        } catch (Exception e) {
            logger.error("(clientUuid = {}) Ошибка клиента {}", clientUuid, e.getMessage());
        }
    }
}