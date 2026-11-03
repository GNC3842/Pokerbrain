package pokerbrain.ui;

import javafx.scene.layout.Pane;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Polyline;
import javafx.scene.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Courbe d'équité du héro le long de la main, avec bande d'incertitude et marqueur sur
 * l'étape courante.
 *
 * Remplace l'`EquityChart` à deux barres, qui ne montrait que l'instant courant : on ne
 * voyait pas que l'équité s'était effondrée à la turn. Les points sont les équités
 * Monte-Carlo calculées par l'analyseur (une par action du héro) ; les actions sans
 * équité (-1) interrompent la ligne au lieu d'être tracées à zéro.
 *
 * La bande d'incertitude est l'erreur type d'un tirage binomial sur le nombre
 * d'itérations Monte-Carlo de l'analyseur : ±1,96·√(p(1−p)/n), soit un intervalle à 95 %.
 */
public class EquityCurve extends Pane {

    private static final double MARGE = 26;
    private static final int ITERATIONS_MC = 10_000; // HandAnalyser.MC_ITERATIONS

    private final Polygon bande     = new Polygon();
    private final Polyline ligne    = new Polyline();
    private final Line     mediane  = new Line();
    private final Circle   marqueur = new Circle(4.5);
    private final Text     etiquette = new Text();

    private List<Double> points = new ArrayList<>();
    private int courant = -1;

    public EquityCurve() {
        getStyleClass().add("curve");
        setMinHeight(130);
        setPrefHeight(150);

        bande.getStyleClass().add("curve__band");
        ligne.getStyleClass().add("curve__line");
        mediane.getStyleClass().add("curve__axis");
        marqueur.getStyleClass().add("curve__marker");
        etiquette.getStyleClass().add("curve__label");

        getChildren().addAll(bande, mediane, ligne, marqueur, etiquette);
        widthProperty().addListener((o, a, b) -> redessiner());
        heightProperty().addListener((o, a, b) -> redessiner());
    }

    /** Une valeur par action de la main : équité du héro (0..1), ou -1 si non calculée. */
    public void setPoints(List<Double> equites) {
        this.points = (equites == null) ? new ArrayList<>() : new ArrayList<>(equites);
        redessiner();
    }

    public void setCurrent(int index) {
        this.courant = index;
        redessiner();
    }

    public void clear() {
        points = new ArrayList<>();
        courant = -1;
        redessiner();
    }

    private void redessiner() {
        ligne.getPoints().clear();
        bande.getPoints().clear();
        marqueur.setVisible(false);
        etiquette.setVisible(false);

        double l = getWidth(), h = getHeight();
        if (l <= 2 * MARGE || h <= 2 * MARGE) return;

        // Axe des 50 % : repère de lecture, une équité sous la ligne est perdante
        mediane.setStartX(MARGE);
        mediane.setEndX(l - MARGE);
        mediane.setStartY(y(0.5, h));
        mediane.setEndY(y(0.5, h));

        List<Integer> connus = new ArrayList<>();
        for (int i = 0; i < points.size(); i++) {
            if (points.get(i) != null && points.get(i) >= 0) connus.add(i);
        }
        if (connus.isEmpty()) return;

        // La bande est un polygone : bord haut à l'aller, bord bas au retour
        for (int i : connus) {
            double p = points.get(i);
            ligne.getPoints().addAll(x(i, l), y(p, h));
            bande.getPoints().addAll(x(i, l), y(Math.min(1, p + demiIntervalle(p)), h));
        }
        for (int k = connus.size() - 1; k >= 0; k--) {
            int i = connus.get(k);
            double p = points.get(i);
            bande.getPoints().addAll(x(i, l), y(Math.max(0, p - demiIntervalle(p)), h));
        }

        if (courant >= 0 && courant < points.size()
                && points.get(courant) != null && points.get(courant) >= 0) {
            double p = points.get(courant);
            marqueur.setCenterX(x(courant, l));
            marqueur.setCenterY(y(p, h));
            marqueur.setVisible(true);
            etiquette.setText(String.format("%.0f %%", p * 100));
            etiquette.setX(Math.min(l - MARGE, x(courant, l) + 8));
            etiquette.setY(y(p, h) - 8);
            etiquette.setVisible(true);
        }
    }

    //Demi-intervalle de confiance à 95 % d'un tirage binomial sur ITERATIONS_MC tirages
    private static double demiIntervalle(double p) {
        return 1.96 * Math.sqrt(Math.max(1e-9, p * (1 - p)) / ITERATIONS_MC);
    }

    private double x(int index, double largeur) {
        int dernier = Math.max(1, points.size() - 1);
        return MARGE + (largeur - 2 * MARGE) * index / dernier;
    }

    private double y(double equite, double hauteur) {
        return hauteur - MARGE - (hauteur - 2 * MARGE) * equite;
    }
}
