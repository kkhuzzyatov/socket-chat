package ru;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class ChatServer {
    private static final List<Socket> clients = Collections.synchronizedList(new ArrayList<>());

    public static void main(String[] args) throws Exception {
        Scanner in = new Scanner(System.in);
        ServerSocket serverSocket = null;
        while (serverSocket == null) {
            System.out.print("Введите порт для запуска сервера: ");
            while (!in.hasNextInt()) {
                System.out.print("Введите число: ");
                in.next();
            }
            int port = in.nextInt();
            try {
                serverSocket = new ServerSocket(port);
            } catch (Exception e) {
                System.out.println("Порт занят. Укажите другой.");
            }
        }
        System.out.println("Сервер запущен.");

        while (true) {
            Socket client = serverSocket.accept();
            clients.add(client);
            new Thread(() -> handleClient(client)).start();
        }
    }

    private static void handleClient(Socket client) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream()))) {
            String msg;
            while ((msg = reader.readLine()) != null) {
                if (msg.length() > 256) {
                    broadcast(msg.split("]")[0] + "] Отправил слишком длинное сообщение.");
                } else {
                    broadcast(msg);
                }
            }
        } catch (Exception ignored) {
        } finally {
            try { client.close(); } catch (Exception ignored) {}
            clients.remove(client);
        }
    }

    private static void broadcast(String msg) {
        synchronized (clients) {
            Iterator<Socket> it = clients.iterator();
            while (it.hasNext()) {
                Socket s = it.next();
                try {
                    new PrintWriter(s.getOutputStream(), true).println(msg);
                } catch (Exception e) {
                    try { s.close(); } catch (Exception ignored) {}
                    it.remove();
                }
            }
        }
    }
}