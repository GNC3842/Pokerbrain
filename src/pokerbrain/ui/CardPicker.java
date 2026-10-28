package pokerbrain.ui;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import pokerbrain.model.core.Card;
import pokerbrain.model.core.Rank;
import pokerbrain.model.core.Suit;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sélecteur de cartes : grille 4 × 13 cliquable.
 *
 * Remplace les `TextField` « ex: As », qui demandaient de connaître la notation, et
 * surtout la validation manuelle de doublons du calculateur (trois boucles imbriquées
 * comparant chaque carte saisie à toutes les autres) : une carte déjà prise ailleurs est
 * simplement désactivée, le doublon devient impossible à saisir.
 */
public class CardPicker extends VBox {

    private final Map<Card, Button> boutons = new LinkedHashMap<>();
    private final List<Card> selection = new ArrayList<>();
    private final int maximum;
    private Runnable onChange = () -> { };

    public CardPicker(String titre, int maximum) {
        this.maximum = maximum;
        getStyleClass().add("picker");
        setSpacing(6);

        Label entete = new Label(titre);
        entete.getStyleClass().add("section-title");

        Button vider = new Button("Vider");
        vider.getStyleClass().addAll("btn", "btn--ghost");
        vider.setOnAction(e -> clear());

        HBox barre = new HBox(10, entete, vider);
        barre.setAlignment(Pos.CENTER_LEFT);

        GridPane grille = new GridPane();
        grille.setHgap(3);
        grille.setVgap(3);
        int ligne = 0;
        for (Suit suit : Suit.values()) {
            int colonne = 0;
            for (Rank rank : rangsDecroissants()) {
                Card carte = new Card(rank, suit);
                Button bouton = new Button(carte.toSymbolString());
                bouton.getStyleClass().addAll("picker__cell",
                    suit == Suit.HEARTS || suit == Suit.DIAMONDS ? "picker__cell--red" : "picker__cell--black");
                bouton.setOnAction(e -> basculer(carte));
                boutons.put(carte, bouton);
                grille.add(bouton, colonne++, ligne);
            }
            ligne++;
        }

        getChildren().addAll(barre, grille);
    }

    public void setOnChange(Runnable onChange) {
        this.onChange = (onChange == null) ? () -> { } : onChange;
    }

    public List<Card> getSelection() {
        return new ArrayList<>(selection);
    }

    /**
     * Cartes prises par les autres sélecteurs : grisées ici. Ne déclenche pas onChange,
     * sinon les sélecteurs se rappelleraient mutuellement sans fin.
     */
    public void setDeadCards(Collection<Card> mortes) {
        for (var entree : boutons.entrySet()) {
            boolean prise = mortes.contains(entree.getKey()) && !selection.contains(entree.getKey());
            entree.getValue().setDisable(prise);
        }
    }

    public void clear() {
        selection.clear();
        boutons.values().forEach(b -> b.getStyleClass().remove("picker__cell--selected"));
        onChange.run();
    }

    private void basculer(Card carte) {
        if (selection.contains(carte)) {
            selection.remove(carte);
            boutons.get(carte).getStyleClass().remove("picker__cell--selected");
        } else {
            // Au-delà du maximum, la plus ancienne carte sort : plus pratique que
            // d'exiger une désélection avant de corriger une carte
            if (selection.size() >= maximum) {
                Card retiree = selection.remove(0);
                boutons.get(retiree).getStyleClass().remove("picker__cell--selected");
            }
            selection.add(carte);
            boutons.get(carte).getStyleClass().add("picker__cell--selected");
        }
        onChange.run();
    }

    private static List<Rank> rangsDecroissants() {
        List<Rank> rangs = new ArrayList<>(List.of(Rank.values()));
        rangs.sort((a, b) -> b.getValue() - a.getValue());
        return rangs;
    }
}
