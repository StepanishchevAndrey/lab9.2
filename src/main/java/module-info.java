module com.lab9 {
    requires javafx.controls;
    requires javafx.fxml;


    opens com.lab9 to javafx.fxml;
    exports com.lab9;
}