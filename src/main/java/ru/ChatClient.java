package ru;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.Scanner;
import org.apache.logging.log4j.*;

public class ChatClient {
    private static final Logger logger = LogManager.getLogger(ChatClient.class);
    private static String username;

    public static void main(String[] args) throws Exception {
        Selector selector = Selector.open();
        SocketChannel clientChannel = SocketChannel.open();
        clientChannel.configureBlocking(false);

        Scanner scanner = new Scanner(System.in);
        String host;
        int port;

        while (true) {
            System.out.print("Введите адрес сервера: ");
            host = scanner.nextLine();

            System.out.print("Введите порт сервера: ");
            while (!scanner.hasNextInt()) {
                System.out.print("Порт должен быть числом. Введите снова: ");
                scanner.next();
            }
            port = scanner.nextInt();
            scanner.nextLine();

            try {
                clientChannel.connect(new InetSocketAddress(host, port));
                clientChannel.register(selector, SelectionKey.OP_CONNECT);
                logger.info("Попытка подключения к {}:{}", host, port);
                break;
            } catch (Exception e) {
                System.out.println("Подключиться невозможно. Попробуйте снова.");
                logger.error("Ошибка подключения", e);
            }
        }

        System.out.print("Введите имя: ");
        username = scanner.nextLine();
        while (username.length() > 50) {
            System.out.print("Имя должно иметь длину не более 50. Введите другое имя: ");
            username = scanner.nextLine();
        }
        logger.info("Имя установлено: {}", username);

        BufferedReader console = new BufferedReader(new InputStreamReader(System.in));

        new Thread(() -> {
            try {
                while (true) {
                    String line = console.readLine();
                    if (line == null || line.isEmpty()) continue;

                    if (line.length() > 200) {
                        System.out.println("Сообщение слишком длинное.");
                        logger.error("Слишком длинное сообщение ({} символов)", line.length());
                        continue;
                    }

                    String msg = "[" + username + "] " + line;
                    ByteBuffer buffer = ByteBuffer.wrap(msg.getBytes());
                    clientChannel.write(buffer);
                    logger.info("Отправлено: {}", msg);
                }
            } catch (Exception e) {
                logger.error("Ошибка в потоке отправки", e);
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
                        if (sc.isConnectionPending()) {
                            sc.finishConnect();
                        }
                        sc.register(selector, SelectionKey.OP_READ);
                        logger.info("Клиент подключен к серверу.");
                    }

                    if (key.isReadable()) {
                        SocketChannel sc = (SocketChannel) key.channel();
                        ByteBuffer buffer = ByteBuffer.allocate(1024);

                        int read = sc.read(buffer);
                        if (read == -1) {
                            logger.error("Соединение закрыто сервером.");
                            sc.close();
                            return;
                        }

                        buffer.flip();
                        String msg = new String(buffer.array(), 0, buffer.limit());
                        logger.info("Получено: {}", msg.trim());
                        System.out.println(msg.trim());
                    }
                } catch (Exception e) {
                    logger.error("Ошибка обработки ключа", e);
                    key.cancel();
                    key.channel().close();
                }
            }
        }
    }
}