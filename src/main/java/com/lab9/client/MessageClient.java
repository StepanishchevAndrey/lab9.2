package com.lab9.client;

import com.lab9.common.MessageProtocol;
import javafx.application.Application;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import java.io.*;
import java.net.*;

public class MessageClient extends Application {
    private Socket socket;
    private InputStream in;
    private OutputStream out;
    private String currentUser;
    private int currentLevel = 0;

    private static final String SERVER_HOST = "localhost";
    private static final int TEXT_PORT = 12345;
    private static final int PROTOBUF_PORT = 12346;
    private static final int PROXY_PORT = 8888;

    @FXML private TextArea logArea;
    @FXML private TextField usernameField;
    @FXML private TextField targetField;
    @FXML private TextArea messageArea;
    @FXML private TextField likeTargetField;
    @FXML private ComboBox<String> levelCombo;
    @FXML private Label statusLabel;

    @Override
    public void start(Stage stage) throws Exception {
        // Загрузка интерфейса из FXML
        FXMLLoader loader = new FXMLLoader(getClass().getResource("message.fxml"));
        Parent root = loader.load();

        stage.setTitle("Анонимные сообщения + Лайки");
        stage.setScene(new Scene(root, 750, 650));
        stage.show();
    }

    @FXML
    private void connect() {
        String selected = levelCombo.getValue();
        if (selected == null) return;

        if (selected.startsWith("Уровень 0")) {
            currentLevel = 0;
            connectTo(TEXT_PORT, false, false);
        } else if (selected.startsWith("Уровень 1")) {
            currentLevel = 1;
            connectTo(TEXT_PORT, true, false);
        } else if (selected.startsWith("Уровень 2")) {
            currentLevel = 2;
            connectTo(PROTOBUF_PORT, false, true);
        } else {
            currentLevel = 3;
            connectTo(PROTOBUF_PORT, true, true);
        }
    }

    private void connectTo(int targetPort, boolean useProxy, boolean useProtobuf) {
        try {
            if (useProxy) {
                socket = new Socket(SERVER_HOST, PROXY_PORT);
                log("Подключено через прокси (порт " + PROXY_PORT + ")");
            } else {
                socket = new Socket(SERVER_HOST, targetPort);
                log("Подключено напрямую к порту " + targetPort);
            }

            in = socket.getInputStream();
            out = socket.getOutputStream();

            String desc = switch (currentLevel) {
                case 0 -> "Уровень 0: текст, IP виден";
                case 1 -> "Уровень 1: прокси, текст, IP скрыт";
                case 2 -> "Уровень 2: Protobuf, содержимое скрыто";
                case 3 -> "Уровень 3: прокси + Protobuf, полное скрытие";
                default -> "";
            };

            statusLabel.setText("Подключено: " + desc);
            statusLabel.setStyle("-fx-text-fill: green;");
            log("=== " + desc + " ===");
        } catch (IOException e) {
            statusLabel.setText("Ошибка: " + e.getMessage());
            statusLabel.setStyle("-fx-text-fill: red;");
            log("Ошибка подключения: " + e.getMessage());
        }
    }

    @FXML
    private void register() {
        if (usernameField.getText().isEmpty()) {
            log("Введите имя");
            return;
        }
        currentUser = usernameField.getText();
        sendCommand("REGISTER", currentUser, null, null);
    }

    @FXML
    private void sendMessage() {
        if (currentUser == null) { log("Сначала регистрация"); return; }
        if (targetField.getText().isEmpty()) { log("Введите получателя"); return; }
        if (messageArea.getText().isEmpty()) { log("Введите текст"); return; }
        sendCommand("SEND", currentUser, targetField.getText(), messageArea.getText());
        messageArea.clear();
    }

    @FXML
    private void getInbox() {
        if (currentUser == null) { log("Сначала регистрация"); return; }
        sendCommand("INBOX", currentUser, null, null);
    }

    @FXML
    private void sendLike() {
        if (currentUser == null) { log("Сначала регистрация"); return; }
        if (likeTargetField.getText().isEmpty()) { log("Введите получателя лайка"); return; }
        sendCommand("LIKE", currentUser, likeTargetField.getText(), null);
    }

    @FXML
    private void showTop() {
        if (currentUser == null) { log("Сначала регистрация"); return; }
        sendCommand("TOP", currentUser, null, null);
    }

    @FXML
    private void hideUser() {
        if (currentUser == null) { log("Сначала регистрация"); return; }
        if (likeTargetField.getText().isEmpty()) { log("Введите имя"); return; }
        sendCommand("HIDE", currentUser, likeTargetField.getText(), null);
    }

    private void sendCommand(String cmd, String user, String target, String content) {
        if (socket == null || socket.isClosed()) {
            log("Ошибка: нет подключения к серверу");
            return;
        }
        try {
            boolean useProtobuf = (currentLevel >= 2);
            if (useProtobuf) {
                MessageProtocol.Message.Builder builder = MessageProtocol.Message.newBuilder()
                        .setCommand(cmd).setUsername(user);
                if (target != null) builder.setTarget(target);
                if (content != null) builder.setContent(content);
                builder.build().writeDelimitedTo(out);
                out.flush();

                MessageProtocol.Message response = MessageProtocol.Message.parseDelimitedFrom(in);
                if (response != null) handleResponse(response);
            } else {
                String msg = cmd + "|" + user;
                if (target != null) msg += "|" + target;
                if (content != null) msg += "|" + content;
                out.write((msg + "\n").getBytes());
                out.flush();

                BufferedReader reader = new BufferedReader(new InputStreamReader(in));
                String line = reader.readLine();
                handleTextResponse(line);
            }
        } catch (IOException e) {
            log("Ошибка: " + e.getMessage());
        }
    }

    private void handleResponse(MessageProtocol.Message response) {
        switch (response.getCommand()) {
            case "OK" -> log("[OK] " + response.getResponse());
            case "ERROR" -> log("[ERROR] " + response.getResponse());
            case "INBOX_RESULT" -> {
                log("СООБЩЕНИЯ:");
                for (String s : response.getItemsList()) log("  " + s);
            }
            case "INBOX_EMPTY" -> log("Нет сообщений");
            case "TOP_RESULT" -> {
                log("=== ТОП ЛАЙКОВ ===");
                for (String s : response.getItemsList()) log("  " + s);
            }
        }
    }

    private void handleTextResponse(String line) {
        if (line == null) return;
        if (line.startsWith("OK|")) log("[OK] " + line.substring(3));
        else if (line.startsWith("ERROR|")) log("[ERROR] " + line.substring(6));
        else if (line.startsWith("INBOX_RESULT")) {
            log("СООБЩЕНИЯ:");
            String[] parts = line.split("\\|\\|");
            for (int i = 1; i < parts.length; i++) log("  " + parts[i]);
        } else if (line.startsWith("INBOX_EMPTY")) log("Нет сообщений");
        else if (line.startsWith("TOP_RESULT")) {
            log("=== ТОП ЛАЙКОВ ===");
            String[] parts = line.split("\\|");
            for (int i = 1; i < parts.length; i++) log("  " + parts[i]);
        } else log(line);
    }

    private void log(String msg) {
        logArea.appendText(msg + "\n");
    }

    @Override
    public void stop() {
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
    }

    public static void main(String[] args) {
        launch(args);
    }
}
