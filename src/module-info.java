module pokerbrain {
    requires javafx.controls;
    requires javafx.fxml;

    opens pokerbrain to javafx.fxml;
    opens pokerbrain.ui to javafx.fxml;
    opens pokerbrain.bot;

    exports pokerbrain;
    exports pokerbrain.model.core;
    exports pokerbrain.model.game;
    exports pokerbrain.ui;
    exports pokerbrain.bot;
    exports pokerbrain.engine;
    exports pokerbrain.simulator;
}
