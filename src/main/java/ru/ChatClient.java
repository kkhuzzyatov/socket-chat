package ru;

import java.io.*;
import java.net.*;
import java.util.Scanner;

public class ChatClient {
    private static String username;
    private static Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) throws Exception {
        Socket socket = null;
        String host = null;
        int port = 0;

        while (socket == null) {
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
                socket = new Socket(host, port);
            } catch (Exception e) {
                System.out.println("Подключиться невозможно. Попробуйте снова.");
            }
        }

        System.out.print("Введите имя: ");
        username = scanner.nextLine();

        Socket finalSocket = socket;

        new Thread(() -> {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(finalSocket.getInputStream()))) {
                String msg;
                while ((msg = in.readLine()) != null) {
                    System.out.println(msg);
                }
            } catch (Exception e) {
                System.out.println("Соединение с сервером потеряно.");
                System.exit(0);
            }
        }).start();

        PrintWriter serverWriter = new PrintWriter(socket.getOutputStream(), true);

        while (scanner.hasNextLine()) {
            String text = scanner.nextLine();
            String userMessage = "[" + username + "] " + text;
            if (text.length() > 256) {
                System.out.println("Ваше сообщение слишком длинное.");
                continue;
            }
            serverWriter.println(userMessage);
        }
    }
}
