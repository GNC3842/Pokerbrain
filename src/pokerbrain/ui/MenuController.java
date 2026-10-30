package pokerbrain.ui;

import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import pokerbrain.MainApp;
import pokerbrain.bot.Bot;
import pokerbrain.bot.BotProfile;
import pokerbrain.bot.BotProfileFactory;
import pokerbrain.model.game.Player;
import javafx.scene.control.Label;
import pokerbrain.model.history.Hand;
import pokerbrain.simulator.MonteCarloEngine;

import java.util.List;
import java.util.ArrayList;

// Contrôleur du menu principal : délègue simplement la navigation à MainController.
public class MenuController {

    private static final int START_STACK = 1000; // 100 BB à 5/10
    private static final int SMALL_BLIND = 5;
    private static final int BIG_BLIND   = 10;

    @FXML private ComboBox<String> numberOfBots;
    @FXML private ComboBox<String> replayCombo;
    @FXML private Label handsCountLabel;

    // Mains chargées depuis le disque pour le replay
    private List<Hand> availableHands;
    
    @FXML
    public void initialize() {
        for (int i = 1; i <= 7; i++)
            numberOfBots.getItems().add(i + (i == 1 ? " bot" : " bots"));
        numberOfBots.getSelectionModel().select(2); // 3 bots par défaut
        refreshReplayList();
    }

    private void refreshReplayList() {
        availableHands = SessionData.getAllHands();
        int count = availableHands.size();
        if (count > 1){
            handsCountLabel.setText(count + " main" + "s" + " en historique");
        } else {
            handsCountLabel.setText(count + " main" + " en historique");
        }

        replayCombo.getItems().clear();
        if (availableHands.isEmpty()) {
            replayCombo.getItems().add("Aucune main disponible");
            replayCombo.getSelectionModel().select(0);
            replayCombo.setDisable(true);
        } else {
            for (int i = availableHands.size() - 1; i >= Math.max(0, availableHands.size() - 50); i--) {
                Hand h = availableHands.get(i);
                String dateStr;
                if (h.getDate() != null){
                    dateStr = h.getDate().toString().substring(0, 10);
                } else {
                    dateStr = "?";
                }
                String label = "#" + (i + 1) + " [" + dateStr + "] "+ h.getActions().size() + " actions";
                int w = h.getHeroWinnings();
                if (w > 0) {
                    label += " (+" + w + ")";
                }
                replayCombo.getItems().add(label);
            }
            replayCombo.getSelectionModel().select(0);
            replayCombo.setDisable(false);
        }
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
            // Un moteur par bot : MonteCarloEngine porte des buffers d'instance
            bots.add(new Bot(id, prof, new MonteCarloEngine()));
        }

        GameController gc = MainController.goToGame();
        if (gc != null) {
            gc.initGame(List.copyOf(players), 0, bots, SMALL_BLIND, BIG_BLIND);
        }
    }

    @FXML
    private void onReplay() {
        if (availableHands == null || availableHands.isEmpty()) return;
        int comboIdx = replayCombo.getSelectionModel().getSelectedIndex();
        int realIdx = availableHands.size() - 1 - comboIdx;
        if (realIdx >= 0 && realIdx < availableHands.size()) {
            SessionData.setSelectedHand(availableHands.get(realIdx));
            MainController.goToAnalysis();
        }
    }

    //Le calculateur d'équité est le mode « spot libre » de l'écran d'analyse
    @FXML
    private void onLeakAnalyzer() {
        SessionData.setSelectedHand(null);
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
