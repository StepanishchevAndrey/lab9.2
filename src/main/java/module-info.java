module AnonymousLab9 {
    // Необходимые модули
    requires javafx.controls;
    requires javafx.fxml;
    //requires com.google.protobuf;
    requires java.sql; // Если используется

    // Это самая важная часть: открываем пакет для FXML и Graphics
    opens com.lab9.client to javafx.fxml, javafx.graphics;

    // Также открываем остальные пакеты, если в них будет FXML или рефлексия
    opens com.lab9.server to javafx.fxml, javafx.graphics;
    opens com.lab9.proxy to javafx.fxml, javafx.graphics;
    opens com.lab9.common to javafx.fxml, javafx.graphics;

    // Экспортируем пакеты
    exports com.lab9.client;
    exports com.lab9.server;
    exports com.lab9.proxy;
    exports com.lab9.common;
}
