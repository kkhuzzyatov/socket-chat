package ru.presentation;

import java.io.InputStreamReader;
import java.util.UUID;
import java.util.function.Consumer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import picocli.CommandLine;
import picocli.CommandLine.Option;
import ru.service.ClientService;

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
        Consumer<String> consumer = System.out::println;
        InputStreamReader isr = new InputStreamReader(System.in);
        clientService.run(isr, consumer, host, port, action, chat, username);
        logger.info("Клиент с uuid - {} подключился с помощью консольного интерфейса.", clientUuid);
    }
}
