package pokerbrain.ui;

import javafx.fxml.FXML;
import pokerbrain.MainApp;

// Contrôleur du menu principal : délègue simplement la navigation à MainController.
public class MenuController {

    @FXML
    private void onPlayGame() {
        MainController.goToGame();
    }

    @FXML
    private void onLeakAnalyzer() {
        MainController.goToAnalysis();
    }

    @FXML
    private void onStatistics() {
        MainController.goToStatistics();
    }

    @FXML
    private void onQuit() {
        MainApp.getPrimaryStage().close();
    }
}
