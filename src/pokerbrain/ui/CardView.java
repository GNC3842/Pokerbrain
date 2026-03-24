package pokerbrain.ui;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import pokerbrain.model.core.Card;
import pokerbrain.model.core.Suit;

// Widget réutilisable représentant une carte à jouer (50×70 px).
// Trois états : face visible, dos caché, ou emplacement vide.
public class CardView extends VBox {

    private static final double W = 50;
    private static final double H = 70;

    private final Label cardLabel = new Label();

    public CardView() {
        setAlignment(Pos.CENTER);
        setMinSize(W, H);
        setMaxSize(W, H);
        getChildren().add(cardLabel);
        showEmpty();
    }

    // Affiche la carte face visible ; cœurs/carreaux en rouge, piques/trèfles en noir
    public void showCard(Card card) {
        if (card == null) { showBack(); return; }
        boolean red = card.getSuit() == Suit.HEARTS || card.getSuit() == Suit.DIAMONDS;
        cardLabel.setText(card.toSymbolString());
        cardLabel.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: " + (red ? "#cc0000" : "black") + ";");
        setStyle("-fx-background-color: white; -fx-background-radius: 6px;");
    }

    // Carte distribuée mais non visible (dos bleu foncé)
    public void showBack() {
        cardLabel.setText("");
        setStyle("-fx-background-color: #2c3e50; -fx-background-radius: 6px;");
    }

    // Emplacement vide (carte pas encore distribuée)
    public void showEmpty() {
        cardLabel.setText("");
        setStyle("-fx-background-color: #0a4020; -fx-background-radius: 6px; -fx-border-color: #2a4430; -fx-border-radius: 6px;");
    }
}
