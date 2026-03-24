package pokerbrain.ui;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import pokerbrain.model.core.Card;
import pokerbrain.model.game.Player;

import java.util.ArrayList;
import java.util.List;

/*
 * Répartition des sièges autour de la table (héro toujours en bas) :
 *
 *   2j :        [top: 1]
 *   3j :        [top: 2]
 *   4j : [L:1]  [top: 2]
 *   5j : [L:1]  [top: 2]  [R:1]
 *   6j : [L:1]  [top: 3]  [R:1]
 *   7j : [L:1]  [top: 3]  [R:1]  [bot:1]
 *   8j : [L:1]  [top: 3]  [R:1]  [bot:2]
 */
public class GameController {

    @FXML private Label  roundLabel;
    @FXML private Label  potLabel;

    @FXML private HBox   topRow;
    @FXML private VBox   leftSlot;
    @FXML private HBox   communityRow;
    @FXML private VBox   potContainer;
    @FXML private VBox   rightSlot;
    @FXML private HBox   bottomRow;
    @FXML private VBox   heroSlot;
    @FXML private VBox   timelineContainer;

    @FXML private Button foldButton;
    @FXML private Button checkCallButton;
    @FXML private Button raiseButton;
    @FXML private Slider raiseSlider;
    @FXML private Label  raiseAmountLabel;

    private PlayerView     hero;
    private PotView        potView;
    private CardView[]     communityCards;
    private ActionTimeline timeline;

    @FXML
    public void initialize() {
        // Les 5 cartes communes sont créées ici et mises à jour via revealCommunityCards()
        communityCards = new CardView[5];
        for (int i = 0; i < 5; i++) {
            communityCards[i] = new CardView();
            communityCards[i].showEmpty();
            communityRow.getChildren().add(communityCards[i]);
        }

        potView = new PotView();
        potContainer.getChildren().add(potView);

        timeline = new ActionTimeline();
        timelineContainer.getChildren().add(timeline);

        // NaN possible si min == max == 0 lors du drag (voir configureRaiseSlider)
        raiseSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            double v = newVal.doubleValue();
            raiseAmountLabel.setText(Double.isNaN(v) ? "0 BB" : String.format("%.1f BB", v));
        });
    }

    // Appel principal depuis le moteur de jeu : place le héro en bas et distribue les adversaires autour de la table
    public void loadPlayers(List<Player> players, int heroIndex) {
        if (players.size() < 2 || players.size() > 8)
            throw new IllegalArgumentException("Le nombre de joueurs doit être entre 2 et 8.");

        clearAllSlots();

        hero = new PlayerView();
        hero.setPlayer(players.get(heroIndex));
        heroSlot.getChildren().add(hero);
        configureRaiseSlider(players.get(heroIndex).stack());

        List<Player> opponents = new ArrayList<>(players);
        opponents.remove(heroIndex);
        distributeOpponents(opponents);
    }

    private void distributeOpponents(List<Player> opp) {
        List<PlayerView> top    = new ArrayList<>();
        List<PlayerView> left   = new ArrayList<>();
        List<PlayerView> right  = new ArrayList<>();
        List<PlayerView> bottom = new ArrayList<>();

        switch (opp.size()) {
            case 1 -> top.add(makeView(opp.get(0)));
            case 2 -> { top.add(makeView(opp.get(0))); top.add(makeView(opp.get(1))); }
            case 3 -> { left.add(makeView(opp.get(0))); top.add(makeView(opp.get(1))); top.add(makeView(opp.get(2))); }
            case 4 -> { left.add(makeView(opp.get(0))); top.add(makeView(opp.get(1))); top.add(makeView(opp.get(2))); right.add(makeView(opp.get(3))); }
            case 5 -> { left.add(makeView(opp.get(0))); top.add(makeView(opp.get(1))); top.add(makeView(opp.get(2))); top.add(makeView(opp.get(3))); right.add(makeView(opp.get(4))); }
            case 6 -> { left.add(makeView(opp.get(0))); top.add(makeView(opp.get(1))); top.add(makeView(opp.get(2))); top.add(makeView(opp.get(3))); right.add(makeView(opp.get(4))); bottom.add(makeView(opp.get(5))); }
            case 7 -> { left.add(makeView(opp.get(0))); top.add(makeView(opp.get(1))); top.add(makeView(opp.get(2))); top.add(makeView(opp.get(3))); right.add(makeView(opp.get(4))); bottom.add(makeView(opp.get(5))); bottom.add(makeView(opp.get(6))); }
        }

        top.forEach(v    -> topRow.getChildren().add(v));
        left.forEach(v   -> leftSlot.getChildren().add(v));
        right.forEach(v  -> rightSlot.getChildren().add(v));
        bottom.forEach(v -> bottomRow.getChildren().add(v));
    }

    private PlayerView makeView(Player player) {
        PlayerView view = new PlayerView();
        view.setPlayer(player);
        return view;
    }

    private void clearAllSlots() {
        topRow.getChildren().clear();
        leftSlot.getChildren().clear();
        rightSlot.getChildren().clear();
        bottomRow.getChildren().clear();
        heroSlot.getChildren().clear();
    }

    @FXML
    private void onFold() {
        foldButton.setDisable(true);
        checkCallButton.setDisable(true);
        raiseButton.setDisable(true);
        raiseSlider.setDisable(true);
        hero.setFolded(true);
    }

    @FXML private void onCall()  { /* TODO : logique de jeu */ }
    @FXML private void onRaise() { /* TODO : logique de jeu */ }

    @FXML
    private void onBackToMenu() {
        MainController.goToMenu();
    }

    public void revealCommunityCards(Card... cards) {
        for (int i = 0; i < communityCards.length; i++) {
            if (i < cards.length) communityCards[i].showCard(cards[i]);
            else                  communityCards[i].showEmpty();
        }
    }

    public void updatePot(double amount) {
        potView.setPot(amount);
        potLabel.setText(String.format("%.1f BB", amount));
    }

    // Max = all-in ; évite NaN si stack == 0
    public void configureRaiseSlider(double heroStack) {
        raiseSlider.setDisable(heroStack <= 0);
        raiseSlider.setMin(0);
        raiseSlider.setMax(heroStack > 0 ? heroStack : 1);
        raiseSlider.setValue(0);
        raiseAmountLabel.setText("0 BB");
    }

    public void setRound(String round)                              { roundLabel.setText(round); }
    public void addActionToHistory(String round, String p, String a){ timeline.addAction(round, p, a); }
    public void clearHistory()                                      { timeline.clear(); }
}
