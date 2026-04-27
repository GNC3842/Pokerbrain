package pokerbrain.ui;

import javafx.fxml.FXML;
import pokerbrain.MainApp;
import pokerbrain.bot.Bot;
import pokerbrain.bot.BotProfileFactory;
import pokerbrain.model.game.Player;

import java.util.List;

// Contrôleur du menu principal : délègue simplement la navigation à MainController.
public class MenuController {

    private static final int START_STACK = 1000; // 100 BB à 5/10
    private static final int SMALL_BLIND = 5;
    private static final int BIG_BLIND   = 10;

    @FXML
    private void onPlayGame() {
        // Héro (index 0) + 3 bots avec des styles différents
        List<Player> players = List.of(
            new Player("hero",  "Vous",     START_STACK),
            new Player("bot-1", "TAG",      START_STACK),
            new Player("bot-2", "Passif",   START_STACK),
            new Player("bot-3", "Agressif", START_STACK)
        );
        List<Bot> bots = List.of(
            new Bot("bot-1", BotProfileFactory.createTightAggressive()),
            new Bot("bot-2", BotProfileFactory.createCallingStation()),
            new Bot("bot-3", BotProfileFactory.createLooseAggressive())
        );

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
