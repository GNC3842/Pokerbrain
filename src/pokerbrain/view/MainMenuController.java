package pokerbrain.view;

import javafx.fxml.FXML;
import pokerbrain.MainApp;

import java.io.IOException;

public class MainMenuController {

    @FXML
    private void onPlayGame() throws IOException {
        // TODO : naviguer vers l'écran de jeu
        System.out.println("Jouer une partie");
    }

    @FXML
    private void onReplay() throws IOException {
        // TODO : naviguer vers l'écran de replay
        System.out.println("Replay");
    }

    @FXML
    private void onLeakAnalyzer() throws IOException {
        // TODO : naviguer vers l'analyseur de leaks
        System.out.println("Leaks");
    }

    @FXML
    private void onQuit() {
        MainApp.getPrimaryStage().close();
    }
}
