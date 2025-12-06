package ru;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;
import org.apache.logging.log4j.*;
import picocli.CommandLine;
import picocli.CommandLine.Option;

public class ChatServer implements Runnable {
    private static final Logger logger = LogManager.getLogger(ChatServer.class);
    private static final Set<SocketChannel> clients = new HashSet<>();

    @Option(names = {"-p", "--port"}, description = "Port to start the server")
    private Integer port;

    public static void main(String[] args) {
        ChatServer server = new ChatServer();
        CommandLine cmd = new CommandLine(server);
        cmd.parseArgs(args);
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
                        client.register(selector, SelectionKey.OP_READ);
                        clients.add(client);
                        logger.info("Клиент с адресом {} подключился.", client.getRemoteAddress());
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
                            clients.remove(client);
                            client.close();
                            continue;
                        }

                        buffer.flip();
                        String message = new String(buffer.array(), 0, buffer.limit());
                        sendNewMessageToSubs(message);
                        logger.info("Клиент с адресом {} отправил сообщение: {}", client.getRemoteAddress(), message.trim());
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Ошибка сервера", e);
        }
    }

    private static void sendNewMessageToSubs(String msg) {
        ByteBuffer buffer = ByteBuffer.wrap(msg.getBytes());
        synchronized (clients) {
            Iterator<SocketChannel> it = clients.iterator();
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