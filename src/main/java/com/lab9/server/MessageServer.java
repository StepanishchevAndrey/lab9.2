package com.lab9.server;

import com.lab9.common.MessageProtocol;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class MessageServer {
    private static final int TEXT_PORT = 12345;
    private static final int PROTOBUF_PORT = 12346;

    private final Map<String, Queue<String>> inbox = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> likes = new ConcurrentHashMap<>();
    private final Set<String> hidden = ConcurrentHashMap.newKeySet();

    public static void main(String[] args) {
        new MessageServer().start();
    }

    public void start() {
        new Thread(() -> startTextServer()).start();
        new Thread(() -> startProtobufServer()).start();
    }

    private void startTextServer() {
        try (ServerSocket ss = new ServerSocket(TEXT_PORT)) {
            System.out.println("[TEXT] Сервер на порту " + TEXT_PORT);
            while (true) {
                Socket s = ss.accept();
                String clientIP = s.getInetAddress().getHostAddress();
                System.out.println("[TEXT] Клиент подключен. IP: " + clientIP);
                new Thread(() -> handleText(s, clientIP)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void startProtobufServer() {
        try (ServerSocket ss = new ServerSocket(PROTOBUF_PORT)) {
            System.out.println("[PROTOBUF] Сервер на порту " + PROTOBUF_PORT);
            while (true) {
                Socket s = ss.accept();
                String clientIP = s.getInetAddress().getHostAddress();
                System.out.println("[PROTOBUF] Клиент подключен. IP: " + clientIP);
                new Thread(() -> handleProtobuf(s, clientIP)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ==================== ТЕКСТОВЫЕ КЛИЕНТЫ ====================
    private void handleText(Socket socket, String clientIP) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            String line;
            while ((line = in.readLine()) != null) {
                String[] parts = line.split("\\|", 4);
                String cmd = parts[0];
                String user = parts.length > 1 ? parts[1] : "";
                String target = parts.length > 2 ? parts[2] : "";
                String content = parts.length > 3 ? parts[3] : "";

                // ВЫВОД IP + КОМАНДА
                System.out.println("[TEXT] IP: " + clientIP + " | CMD: " + cmd + " | USER: " + user);

                String response = processCommandText(cmd, user, target, content);
                out.println(response);
            }
        } catch (IOException e) {
            System.out.println("[TEXT] Клиент " + clientIP + " отключен");
        }
    }

    // ==================== PROTOBUF КЛИЕНТЫ ====================
    private void handleProtobuf(Socket socket, String clientIP) {
        try (InputStream in = socket.getInputStream();
             OutputStream out = socket.getOutputStream()) {

            while (true) {
                MessageProtocol.Message msg = MessageProtocol.Message.parseDelimitedFrom(in);
                if (msg == null) break;

                String cmd = msg.getCommand();
                String user = msg.getUsername();
                String target = msg.getTarget();
                String content = msg.getContent();

                // ВЫВОД IP + КОМАНДА
                System.out.println("[PROTOBUF] IP: " + clientIP + " | CMD: " + cmd + " | USER: " + user);

                MessageProtocol.Message response = processCommandProtobuf(cmd, user, target, content);
                response.writeDelimitedTo(out);
                out.flush();
            }
        } catch (IOException e) {
            System.out.println("[PROTOBUF] Клиент " + clientIP + " отключен");
        }
    }

    // ==================== ЛОГИКА (текст) ====================
    private String processCommandText(String cmd, String user, String target, String content) {
        switch (cmd) {
            case "REGISTER":
                inbox.putIfAbsent(user, new ConcurrentLinkedQueue<>());
                likes.putIfAbsent(user, ConcurrentHashMap.newKeySet());
                return "OK|" + user + " зарегистрирован";
            case "SEND":
                if (!inbox.containsKey(target)) return "ERROR|Пользователь " + target + " не найден";
                inbox.get(target).add("[Аноним] " + content);
                return "OK|Сообщение отправлено";
            case "INBOX":
                Queue<String> q = inbox.get(user);
                if (q == null || q.isEmpty()) return "INBOX_EMPTY|Нет сообщений";
                StringBuilder sb = new StringBuilder("INBOX_RESULT");
                while (!q.isEmpty()) sb.append("||").append(q.poll());
                return sb.toString();
            case "LIKE":
                if (!likes.containsKey(target)) return "ERROR|Пользователь " + target + " не найден";
                likes.get(target).add(user);
                return "OK|Лайк отправлен";
            case "TOP":
                List<String> top = new ArrayList<>();
                likes.entrySet().stream()
                        .filter(e -> !hidden.contains(e.getKey()))
                        .sorted((a, b) -> Integer.compare(b.getValue().size(), a.getValue().size()))
                        .limit(10)
                        .forEach(e -> top.add(e.getKey() + "(" + e.getValue().size() + ")"));
                if (top.isEmpty()) top.add("Пока нет лайков");
                return "TOP_RESULT|" + String.join("|", top);
            case "HIDE":
                hidden.add(target);
                return "OK|" + target + " скрыт";
            default:
                return "ERROR|Неизвестная команда";
        }
    }

    // ==================== ЛОГИКА (protobuf) ====================
    private MessageProtocol.Message processCommandProtobuf(String cmd, String user, String target, String content) {
        switch (cmd) {
            case "REGISTER":
                inbox.putIfAbsent(user, new ConcurrentLinkedQueue<>());
                likes.putIfAbsent(user, ConcurrentHashMap.newKeySet());
                return ok(user + " зарегистрирован");
            case "SEND":
                if (!inbox.containsKey(target)) return error("Пользователь " + target + " не найден");
                inbox.get(target).add("[Аноним] " + content);
                return ok("Сообщение отправлено");
            case "INBOX":
                Queue<String> q = inbox.get(user);
                if (q == null || q.isEmpty()) return empty();
                List<String> msgs = new ArrayList<>();
                while (!q.isEmpty()) msgs.add(q.poll());
                return inboxResult(msgs);
            case "LIKE":
                if (!likes.containsKey(target)) return error("Пользователь " + target + " не найден");
                likes.get(target).add(user);
                return ok("Лайк отправлен");
            case "TOP":
                List<String> top = new ArrayList<>();
                likes.entrySet().stream()
                        .filter(e -> !hidden.contains(e.getKey()))
                        .sorted((a, b) -> Integer.compare(b.getValue().size(), a.getValue().size()))
                        .limit(10)
                        .forEach(e -> top.add(e.getKey() + "(" + e.getValue().size() + ")"));
                if (top.isEmpty()) top.add("Пока нет лайков");
                return topResult(top);
            case "HIDE":
                hidden.add(target);
                return ok(target + " скрыт");
            default:
                return error("Неизвестная команда");
        }
    }

    private MessageProtocol.Message ok(String text) {
        return MessageProtocol.Message.newBuilder().setCommand("OK").setResponse(text).build();
    }

    private MessageProtocol.Message error(String text) {
        return MessageProtocol.Message.newBuilder().setCommand("ERROR").setResponse(text).build();
    }

    private MessageProtocol.Message empty() {
        return MessageProtocol.Message.newBuilder().setCommand("INBOX_EMPTY").setResponse("Нет сообщений").build();
    }

    private MessageProtocol.Message inboxResult(List<String> msgs) {
        return MessageProtocol.Message.newBuilder().setCommand("INBOX_RESULT").addAllItems(msgs).build();
    }

    private MessageProtocol.Message topResult(List<String> top) {
        return MessageProtocol.Message.newBuilder().setCommand("TOP_RESULT").addAllItems(top).build();
    }
}