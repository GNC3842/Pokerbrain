package pokerbrain.ui;

import pokerbrain.MainApp;

import java.io.IOException;

// Point de navigation centralisé : tous les contrôleurs passent par ici pour changer de scène.
public class MainController {

    private MainController() {}

    public static GameController goToGame() {
        try {
            return MainApp.switchScene("fxml/GameScreen.fxml");
        } catch (IOException e) {
            System.err.println("Erreur navigation : " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    public static void goToMenu()       { navigate("fxml/MainMenu.fxml"); }
    public static void goToReplay()     { navigate("fxml/ReplayScreen.fxml"); }
    public static void goToAnalysis()   { navigate("fxml/AnalysisPanel.fxml"); }
    public static void goToStatistics() { navigate("fxml/StatisticsView.fxml"); }

    private static void navigate(String fxmlPath) {
        try {
            MainApp.switchScene(fxmlPath);
        } catch (IOException e) {
            System.err.println("Erreur navigation : " + e.getMessage());
            e.printStackTrace();
        }
    }
}
