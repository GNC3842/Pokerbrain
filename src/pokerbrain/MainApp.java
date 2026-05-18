package pokerbrain;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class MainApp extends Application {

    private static Stage primaryStage;

    @Override
    public void start(Stage stage) throws IOException {
    	
    	// Lancer la génération MC en background (thread daemon)
        AppContext.initAsync();
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
        primaryStage.setScene(new Scene(root, 1200, 800));
        return loader.getController();
    }

    public static Stage getPrimaryStage() {
        return primaryStage;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
