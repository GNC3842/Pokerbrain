module pokerbrain {
    requires javafx.controls;
    requires javafx.fxml;

    opens pokerbrain to javafx.fxml;
    opens pokerbrain.ui to javafx.fxml;

    exports pokerbrain;
    exports pokerbrain.model.core;
    exports pokerbrain.model.game;
    exports pokerbrain.ui;
}
