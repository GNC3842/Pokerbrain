module pokerbrain {
    requires javafx.controls;
    requires javafx.fxml;

    opens pokerbrain to javafx.fxml;
    opens pokerbrain.ui to javafx.fxml;
    opens pokerbrain.bot;

    exports pokerbrain;
    exports pokerbrain.model.core;
    exports pokerbrain.model.game;
    exports pokerbrain.model.history;
    exports pokerbrain.ui;
    exports pokerbrain.bot;
    exports pokerbrain.engine;
    exports pokerbrain.parser;
    exports pokerbrain.simulator;
    exports pokerbrain.analyser;
    exports pokerbrain.analyser.leaks;
    exports pokerbrain.analyser.statistics;
}
