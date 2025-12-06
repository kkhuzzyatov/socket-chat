package ru.presentation;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import picocli.CommandLine;
import picocli.CommandLine.Option;
import ru.service.ClientService;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Scanner;
import java.util.UUID;

public class ClientViewConsoleImpl implements Runnable {

    private final Logger logger = LogManager.getLogger(ClientViewConsoleImpl.class);

    private ClientService clientService;
    private String clientUuid;

    @Option(names = {"-h", "--host"}, description = "Server host")
    private String host;

    @Option(names = {"-p", "--port"}, description = "Server port")
    private Integer port;

    @Option(names = {"-a", "--action"}, description = "Action (create/connect)")
    private String action;

    @Option(names = {"-c", "--chat"}, description = "Chat name")
    private String chat;

    @Option(names = {"-u", "--username"}, description = "Username")
    private String username;

    public static void main(String[] args) {
        ClientViewConsoleImpl view = new ClientViewConsoleImpl();
        new CommandLine(view).parseArgs(args);
        view.run();
    }

    @Override
    public void run() {
        clientUuid = UUID.randomUUID().toString();
        clientService = new ClientService(clientUuid);

        Scanner scanner = new Scanner(System.in);

        if (host == null) {
            System.out.print("Введите адрес сервера: ");
            host = scanner.nextLine();
        }

        if (port == null) {
            System.out.print("Введите порт сервера: ");
            port = Integer.parseInt(scanner.nextLine());
        }

        if (action == null) {
            System.out.print("Введите действие (create/connect): ");
            action = scanner.nextLine();
        }

        if (chat == null) {
            System.out.print("Введите название чата: ");
            chat = scanner.nextLine();
        }

        if (username == null) {
            System.out.print("Введите имя пользователя: ");
            username = scanner.nextLine();
        }

        logger.info(
                "(clientUuid = {}) Host={}, Port={}, User={}, Chat={}, Action={}",
                clientUuid, host, port, username, chat, action
        );

        clientService.connect(host, port);

        new Thread(() -> {
            try {
                Thread.sleep(200);

                clientService.sendAction(action);
                clientService.sendChatName(chat);

                BufferedReader console = new BufferedReader(new InputStreamReader(System.in));
                while (true) {
                    String line = console.readLine();
                    if (line == null || line.isEmpty()) continue;

                    if (line.length() > 255) {
                        System.out.println("Сообщение слишком длинное.");
                        continue;
                    }

                    clientService.sendMessage("[" + username + "] " + line);
                }
            } catch (Exception ignored) {
            }
        }).start();

        clientService.listen();
    }
}