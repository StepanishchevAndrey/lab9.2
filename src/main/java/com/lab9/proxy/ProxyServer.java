package com.lab9.proxy;

import java.io.*;
import java.net.*;

public class ProxyServer {
    private static final int PROXY_PORT = 8888;
    private static final String SERVER_HOST = "localhost";
    private static final int TEXT_PORT = 12345;
    private static final int PROTOBUF_PORT = 12346;

    public static void main(String[] args) {
        try (ServerSocket ss = new ServerSocket(PROXY_PORT)) {
            System.out.println("======================================");
            System.out.println("ПРОКСИ СЕРВЕР ЗАПУЩЕН");
            System.out.println("Порт: " + PROXY_PORT);
            System.out.println("Текст -> порт " + TEXT_PORT);
            System.out.println("Protobuf -> порт " + PROTOBUF_PORT);
            System.out.println("======================================");

            while (true) {
                Socket client = ss.accept();
                System.out.println("[+] Клиент подключен к прокси");
                new Thread(() -> handleClient(client)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void handleClient(Socket client) {
        try {
            // Буферизация для чтения первого байта
            BufferedInputStream bufferedIn = new BufferedInputStream(client.getInputStream());
            bufferedIn.mark(1);
            int firstByte = bufferedIn.read();
            bufferedIn.reset();

            // Определяем тип трафика
            boolean isText = (firstByte == 'R' || firstByte == 'S' || firstByte == 'I' ||
                    firstByte == 'L' || firstByte == 'T' || firstByte == 'H');

            int targetPort = isText ? TEXT_PORT : PROTOBUF_PORT;
            String type = isText ? "TEXT" : "PROTOBUF";

            System.out.println("[ПРОКСИ] " + type + " -> порт " + targetPort);

            // Подключаемся к серверу
            Socket server = new Socket(SERVER_HOST, targetPort);

            // Получаем потоки
            InputStream clientIn = bufferedIn;
            OutputStream clientOut = client.getOutputStream();
            InputStream serverIn = server.getInputStream();
            OutputStream serverOut = server.getOutputStream();

            // Пересылка в обе стороны
            Thread proxyToServer = new Thread(() -> {
                try {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = clientIn.read(buffer)) != -1) {
                        serverOut.write(buffer, 0, bytesRead);
                        serverOut.flush();
                    }
                } catch (IOException e) {
                    // Клиент закрыл соединение
                } finally {
                    try { server.close(); } catch (IOException e) {}
                    try { client.close(); } catch (IOException e) {}
                }
            });

            Thread serverToProxy = new Thread(() -> {
                try {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = serverIn.read(buffer)) != -1) {
                        clientOut.write(buffer, 0, bytesRead);
                        clientOut.flush();
                    }
                } catch (IOException e) {
                    // Сервер закрыл соединение
                } finally {
                    try { client.close(); } catch (IOException e) {}
                    try { server.close(); } catch (IOException e) {}
                }
            });

            proxyToServer.start();
            serverToProxy.start();

            proxyToServer.join();
            serverToProxy.join();

        } catch (Exception e) {
            System.out.println("[ПРОКСИ] Ошибка: " + e.getMessage());
        }
    }
}