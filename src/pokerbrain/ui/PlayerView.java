package pokerbrain.ui;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import pokerbrain.model.core.Card;
import pokerbrain.model.game.Player;

/**
 * Siège d'un joueur : nom, position, cartes, tapis et mise en cours.
 *
 * L'apparence est entièrement dans components.css (`seat`, `seat--active`, `seat__*`) :
 * les deux constantes de style inline qui se recopiaient mutuellement ont disparu.
 */
public class PlayerView extends VBox {

    private final Label    nameLabel     = new Label();
    private final Label    positionLabel = new Label();
    private final CardView card1         = new CardView(CardView.Taille.SM);
    private final CardView card2         = new CardView(CardView.Taille.SM);
    private final Label    stackLabel    = new Label();
    private final Label    betLabel      = new Label();

    public PlayerView() {
        getStyleClass().add("seat");
        setAlignment(Pos.CENTER);
        setSpacing(4);

        nameLabel.getStyleClass().add("seat__name");
        positionLabel.getStyleClass().add("seat__position");
        stackLabel.getStyleClass().addAll("seat__stack", "num");
        betLabel.getStyleClass().addAll("seat__bet", "num");

        HBox cardsBox = new HBox(4, card1, card2);
        cardsBox.setAlignment(Pos.CENTER);

        getChildren().addAll(nameLabel, positionLabel, cardsBox, stackLabel, betLabel);
    }

    public void setPlayer(Player player) {
        nameLabel.setText(player.getName());
        positionLabel.setText(player.getPosition() != null ? player.getPosition().name() : "");
        positionLabel.setVisible(player.getPosition() != null);
        stackLabel.setText(player.getStack() + " jetons");
        setBet(player.getCurrentBet());

        var cards = player.getHoleCards();
        if (cards.size() >= 2) { card1.showCard(cards.get(0)); card2.showCard(cards.get(1)); }
        else                   { card1.showBack(); card2.showBack(); }
    }

    public void setName(String name)         { nameLabel.setText(name); }
    public void setPosition(String position) { positionLabel.setText(position); }
    public void setStack(double stack)       { stackLabel.setText((int) stack + " jetons"); }
    public void setBet(double bet)           { betLabel.setText(bet > 0 ? "mise " + (int) bet : ""); }
    public void showCards(Card c1, Card c2)  { card1.showCard(c1); card2.showCard(c2); }
    public void hideCards()                  { card1.showBack(); card2.showBack(); }

    // C'est à ce joueur de parler : liseré ambre, porté par la classe seat--active
    public void setActive(boolean active) {
        getStyleClass().remove("seat--active");
        if (active) getStyleClass().add("seat--active");
    }

    // Joueur couché : opacité réduite plutôt que masqué, pour garder la table lisible
    public void setFolded(boolean folded) { setOpacity(folded ? 0.35 : 1.0); }
}
