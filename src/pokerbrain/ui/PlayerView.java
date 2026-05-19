package pokerbrain.ui;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import pokerbrain.model.core.Card;
import pokerbrain.model.game.Player;

// Widget représentant un joueur à la table : nom, position, cartes, stack et mise en cours.
public class PlayerView extends VBox {

    // Bordure dorée quand c'est au tour du joueur d'agir
    private static final String STYLE_BASE =
        "-fx-background-color: #163d24; -fx-background-radius: 10px; -fx-padding: 10 16 10 16;";
    private static final String STYLE_ACTIVE =
        "-fx-background-color: #1e6e38; -fx-background-radius: 10px; -fx-padding: 10 16 10 16;" +
        "-fx-border-color: #d4af37; -fx-border-radius: 10px; -fx-border-width: 2px;";

    private final Label    nameLabel     = new Label();
    private final Label    positionLabel = new Label();
    private final CardView card1         = new CardView();
    private final CardView card2         = new CardView();
    private final Label    stackLabel    = new Label();
    private final Label    betLabel      = new Label();

    public PlayerView() {
        setAlignment(Pos.CENTER);
        setSpacing(5);

        nameLabel.setStyle("-fx-text-fill: white; -fx-font-size: 13px; -fx-font-weight: bold;");
        positionLabel.setStyle("-fx-text-fill: #a09070; -fx-font-size: 11px;");
        stackLabel.setStyle("-fx-text-fill: #d4af37; -fx-font-size: 11px;");
        betLabel.setStyle("-fx-text-fill: #7fcf9f; -fx-font-size: 11px;");

        HBox cardsBox = new HBox(4, card1, card2);
        cardsBox.setAlignment(Pos.CENTER);

        setStyle(STYLE_BASE);
        getChildren().addAll(nameLabel, positionLabel, cardsBox, stackLabel, betLabel);
    }

    public void setPlayer(Player player) {
        nameLabel.setText(player.getName());
        if (player.getPosition() != null)
            positionLabel.setText(player.getPosition().name());
        stackLabel.setText(player.getStack() + " jetons.");
        setBet(player.getCurrentBet());

        var cards = player.getHoleCards();
        if (cards.size() >= 2) { card1.showCard(cards.get(0)); card2.showCard(cards.get(1)); }
        else                   { card1.showBack(); card2.showBack(); }
    }

    public void setName(String name)         { nameLabel.setText(name); }
    public void setPosition(String position) { positionLabel.setText(position); }
    public void setStack(double stack)       { stackLabel.setText((int) stack + " jetons"); }
    public void setBet(double bet)           { betLabel.setText(bet > 0 ? "+ " + (int) bet + " jetons" : "");  }
    public void showCards(Card c1, Card c2)  { card1.showCard(c1); card2.showCard(c2); }
    public void hideCards()                  { card1.showBack(); card2.showBack(); }

    // Joueur couché : on réduit l'opacité plutôt que de le masquer pour garder la lisibilité de la table
    public void setActive(boolean active) { setStyle(active ? STYLE_ACTIVE : STYLE_BASE); }
    public void setFolded(boolean folded) { setOpacity(folded ? 0.35 : 1.0); }
}
