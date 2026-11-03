package pokerbrain;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class MainApp extends Application {

    private static final String[] STYLESHEETS = {
        "/pokerbrain/ui/css/theme.css",
        "/pokerbrain/ui/css/components.css",
        "/pokerbrain/ui/css/screens.css"
    };

    private static Stage primaryStage;

    /**
     * Scène unique de l'application.
     *
     * Chaque navigation créait auparavant une `new Scene(root, 1200, 800)` : la fenêtre
     * sautait à sa taille d'origine à chaque écran, la taille choisie par l'utilisateur
     * était perdue, et les feuilles de style étaient rechargées à chaque fois. On garde
     * donc la scène et on remplace seulement sa racine.
     */
    private static Scene scene;

    @Override
    public void start(Stage stage) throws IOException {
    	//Charger l'historique
        pokerbrain.ui.SessionData.initHistory();
        
        primaryStage = stage;
        switchScene("fxml/MainMenu.fxml");
        stage.setTitle("PokerBrain");
        stage.setMinWidth(900);
        stage.setMinHeight(600);
        stage.show();
    }

    public static <T> T switchScene(String fxmlPath) throws IOException {
        FXMLLoader loader = new FXMLLoader(
            MainApp.class.getResource("/pokerbrain/ui/" + fxmlPath)
        );
        Parent root = loader.load();

        if (scene == null) {
            scene = new Scene(root, 1200, 800);
            for (String feuille : STYLESHEETS) {
                scene.getStylesheets().add(MainApp.class.getResource(feuille).toExternalForm());
            }
            primaryStage.setScene(scene);
        } else {
            scene.setRoot(root);
        }
        return loader.getController();
    }

    public static Stage getPrimaryStage() {
        return primaryStage;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
