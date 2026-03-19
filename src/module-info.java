module pokerbrain {
    requires javafx.controls;
    requires javafx.fxml;

    opens pokerbrain to javafx.fxml;
    opens pokerbrain.view to javafx.fxml;

    exports pokerbrain;
    exports pokerbrain.model.core;
}
