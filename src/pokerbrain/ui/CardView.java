package pokerbrain.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Line;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import pokerbrain.model.core.Card;
import pokerbrain.model.core.Suit;

/**
 * Carte à jouer dessinée : rang et enseigne dans le coin, grande enseigne au centre,
 * coins arrondis et ombre portée. Trois états — face visible, dos, emplacement vide.
 *
 * Tout l'habillage est en CSS (`card`, `card--red`, `card--back`, `card--empty` dans
 * components.css) : cette classe ne fait que choisir les classes et le texte. L'ancienne
 * version empilait des `setStyle("-fx-background-color: white…")` et affichait « A♠ » dans
 * un Label — une étiquette, pas une carte.
 *
 * La taille est pilotée par {@link Taille} plutôt que par deux constantes : le replay et la
 * table n'ont pas les mêmes besoins, et les cartes du héro méritent d'être plus grandes
 * que celles des adversaires.
 */
public class CardView extends StackPane {

    public enum Taille {
        SM(32, 46, 10, 17),
        MD(46, 64, 13, 25),
        LG(60, 84, 16, 33);

        private final double largeur, hauteur, policeCoin, policeCentre;

        Taille(double largeur, double hauteur, double policeCoin, double policeCentre) {
            this.largeur = largeur;
            this.hauteur = hauteur;
            this.policeCoin = policeCoin;
            this.policeCentre = policeCentre;
        }
    }

    private final Text coin   = new Text();
    private final Text centre = new Text();
    private final StackPane dos = new StackPane();

    private Taille taille;

    public CardView() {
        this(Taille.MD);
    }

    public CardView(Taille taille) {
        coin.getStyleClass().add("card__rank");
        centre.getStyleClass().add("card__pip");

        StackPane.setAlignment(coin, Pos.TOP_LEFT);
        StackPane.setAlignment(centre, Pos.CENTER);

        construireDos();
        getChildren().addAll(dos, centre, coin);
        setTaille(taille);
        showEmpty();
    }

    //Dos dessiné : quadrillage diagonal, pas un rectangle plat
    private void construireDos() {
        dos.setMouseTransparent(true);
        for (int i = -2; i <= 2; i++) {
            Line ligne = new Line(0, i * 9, 0, i * 9);
            ligne.getStyleClass().add("card__pattern");
            dos.getChildren().add(ligne);
        }
    }

    public void setTaille(Taille nouvelle) {
        this.taille = nouvelle;
        setMinSize(nouvelle.largeur, nouvelle.hauteur);
        setPrefSize(nouvelle.largeur, nouvelle.hauteur);
        setMaxSize(nouvelle.largeur, nouvelle.hauteur);
        setPadding(new Insets(nouvelle.hauteur * 0.06, nouvelle.largeur * 0.1,
                              nouvelle.hauteur * 0.06, nouvelle.largeur * 0.1));
        coin.setFont(Font.font(null, javafx.scene.text.FontWeight.BOLD, nouvelle.policeCoin));
        centre.setFont(Font.font(nouvelle.policeCentre));
        redessinerDos();
    }

    private void redessinerDos() {
        double l = taille.largeur * 0.62;
        int i = 0;
        for (var noeud : dos.getChildren()) {
            if (noeud instanceof Line ligne) {
                double decalage = (i - 2) * (taille.hauteur * 0.17);
                ligne.setStartX(-l / 2);
                ligne.setEndX(l / 2);
                ligne.setStartY(decalage - l / 2);
                ligne.setEndY(decalage + l / 2);
                i++;
            }
        }
    }

    // Face visible : cœurs et carreaux en rouge via la classe card--red
    public void showCard(Card card) {
        if (card == null) { showBack(); return; }
        etat("card");
        boolean rouge = card.getSuit() == Suit.HEARTS || card.getSuit() == Suit.DIAMONDS;
        if (rouge) getStyleClass().add("card--red");

        coin.setText(String.valueOf(card.getRank().getSymbol()));
        centre.setText(String.valueOf(card.getSuit().getSymbol()));
        coin.setVisible(true);
        centre.setVisible(true);
        dos.setVisible(false);
    }

    // Carte distribuée mais non visible
    public void showBack() {
        etat("card", "card--back");
        coin.setVisible(false);
        centre.setVisible(false);
        dos.setVisible(true);
    }

    // Emplacement vide : carte pas encore distribuée
    public void showEmpty() {
        etat("card", "card--empty");
        coin.setVisible(false);
        centre.setVisible(false);
        dos.setVisible(false);
    }

    private void etat(String... classes) {
        getStyleClass().setAll(classes);
    }
}
