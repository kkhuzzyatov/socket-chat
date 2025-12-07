package ru.presentation;

import javax.swing.*;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.UUID;
import java.util.function.Consumer;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import ru.service.ClientService;

public class ClientViewSwingImpl {
    private final Logger logger = LogManager.getLogger(ClientViewSwingImpl.class);

    private ClientService clientService;
    private String clientUuid;

    private String host;
    private Integer port;
    private String action;
    private String chat;
    private String username;

    public static void main(String[] args) {
        ClientViewSwingImpl view = new ClientViewSwingImpl();
        view.parseArgs(args);
        view.start();
    }

    private void parseArgs(String[] args) {
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-h":
                case "--host":
                    if (i + 1 < args.length) {
                        host = args[++i];
                    }
                    break;
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
                case "-a":
                case "--action":
                    if (i + 1 < args.length) {
                        action = args[++i];
                    }
                    break;
                case "-c":
                case "--chat":
                    if (i + 1 < args.length) {
                        chat = args[++i];
                    }
                    break;
                case "-u":
                case "--username":
                    if (i + 1 < args.length) {
                        username = args[++i];
                    }
                    break;
                default:
                    logger.warn("Неизвестный аргумент: {}", args[i]);
            }
        }
    }

    private void start() {
        clientUuid = UUID.randomUUID().toString();
        clientService = new ClientService(clientUuid);
        SwingUtilities.invokeLater(() -> {
            try {
                DualPanelWindow dualPanelWindow = new DualPanelWindow();
                dualPanelWindow.setVisible(true);
                Consumer<String> writeConsumer = dualPanelWindow.getWriteConsumer();
                InputStreamReader readInput = dualPanelWindow.getReadInputStreamReader();
                new Thread(() -> {
                    clientService.run(readInput, writeConsumer, host, port, action, chat, username);
                }).start();
                logger.info("Клиент с uuid - {} подключился с помощью swing интерфейса.", clientUuid);
            } catch (IOException e) {
                logger.error("Ошибка при создании окна", e);
            }
        });
    }
}