package pokerbrain.ui;

import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import pokerbrain.AppContext;
import pokerbrain.MainApp;
import pokerbrain.bot.Bot;
import pokerbrain.bot.BotProfile;
import pokerbrain.bot.BotProfileFactory;
import pokerbrain.model.game.Player;

import java.util.List;
import java.util.ArrayList;

// Contrôleur du menu principal : délègue simplement la navigation à MainController.
public class MenuController {

    private static final int START_STACK = 1000; // 100 BB à 5/10
    private static final int SMALL_BLIND = 5;
    private static final int BIG_BLIND   = 10;

    @FXML private ComboBox<String> numberOfBots;
    
    @FXML
    public void initialize() {
        for (int i = 1; i <= 7; i++)
            numberOfBots.getItems().add(i + (i == 1 ? " bot" : " bots"));
        numberOfBots.getSelectionModel().select(2); // 3 bots par défaut
    }
    
    @FXML
    private void onPlayGame() {
    	int numBots = numberOfBots.getSelectionModel().getSelectedIndex() + 1;
        
    	BotProfile[] profiles = {
                BotProfileFactory.createTightAggressive(),
                BotProfileFactory.createCallingStation(),
                BotProfileFactory.createLooseAggressive()
            };
        String[] names = {"Marcel", "Pédro", "Yamato", "Archibald","Leonard","Mehdi","Ivan"};
        
        //Joueur
        List<Player> players = new ArrayList<>();
        players.add(new Player("hero", "Vous", START_STACK));
        
        //Bots
        List<Bot> bots = new ArrayList<>();
        for (int i = 0; i < numBots; i++) {
            String id   = "bot-" + (i + 1);
            String name = names[i % names.length] + (i >= 3 ? " " + (i/3 + 1) : "");
            BotProfile prof = profiles[i % profiles.length];
            players.add(new Player(id, name, START_STACK));
            bots.add(new Bot(id, prof, AppContext.getEngine()));
        }

        GameController gc = MainController.goToGame();
        if (gc != null) {
            gc.initGame(List.copyOf(players), 0, bots, SMALL_BLIND, BIG_BLIND);
        }
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
