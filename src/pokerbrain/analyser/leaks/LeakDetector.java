package pokerbrain.analyser.leaks;

import pokerbrain.analyser.statistics.PlayerStatistics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Détecte les fuites de jeu en comparant les statistiques d'un joueur aux seuils d'un profil TAG optimal
 *
 * SEUILS TAG OPTIMAL (référence 6-max) :
 *   VPIP  : 20-25%
 *   PFR   : 16-22%
 *   AF    : 2.5-3.5
 *   WTSD  : 25-32%
 *   WSD   : 50-55%
 *  
 *  50 mains minimum pour une analyse fiable sinon less résultats sont trop bruités et un avertissement est ajouté
 */

public class LeakDetector {
	private static final int MIN_HANDS = 50;
	 // Seuils TAG optimal
    private static final double VPIP_MIN = 20.0;
    private static final double VPIP_MAX = 25.0;
    private static final double PFR_MIN = 16.0;
    private static final double PFR_MAX = 22.0;
    private static final double AF_MIN = 2.5;
    private static final double AF_MAX = 3.5;
    private static final double WTSD_MIN = 25.0;
    private static final double WTSD_MAX = 32.0;
    private static final double WSD_MIN = 50.0;
    
    //analyser les stats et retourner les fuites detectées par sévérité
    public static List<Leak> detect(PlayerStatistics stats){
    	List<Leak> leaks = new ArrayList<>();
    	if (stats.getHandsPlayed() < MIN_HANDS) {
            leaks.add(new Leak(LeakSeverity.INFO, "Échantillon",
                stats.getHandsPlayed(), MIN_HANDS,
                "Seulement " + stats.getHandsPlayed() + " mains analysées",
                "Jouez au moins " + MIN_HANDS + " mains pour une analyse fiable."
            ));
        }
    	
    	//Analyse VPIP
    	double vpip = stats.getVPIP();
        if (vpip > 40) {
        	leaks.add(new Leak(LeakSeverity.CRITICAL, "VPIP", vpip, VPIP_MAX,
                 "VPIP beaucoup trop élevé - vous jouez trop de mains",
                 "Resserrez votre range preflop. Foldez les mains faibles (ex: K5o, J3o)."));
        } else if (vpip > VPIP_MAX) {
            leaks.add(new Leak(LeakSeverity.WARNING, "VPIP", vpip, VPIP_MAX,
                 "VPIP légèrement trop élevé",
                 "Éliminez les mains marginales (c'est a dire moyenne) en early position (quand vous parlez en 1er)"));
        } else if (vpip < 15) {
            leaks.add(new Leak(LeakSeverity.WARNING, "VPIP", vpip, VPIP_MIN,
                 "VPIP trop bas - vous jouez trop serré (nit)",
                 "N'ayez pas peur de tenter votre chance avec des cartes qui se suivent (comme 8 et 9 de la même couleur) quand vous parlez dans les derniers autour de la table. C'est le meilleur moment pour voler le pot !"));
        }

        //PFR 
        double pfr = stats.getPFR();
        if (pfr < 8) {
            leaks.add(new Leak(LeakSeverity.CRITICAL, "PFR", pfr, PFR_MIN,
                 "PFR beaucoup trop bas - vous vous contentez de suivre la mise de départ.",
                 "Si vous décidez de jouer une main, faites-le en relançant plutôt qu'en payant simplement."));
        } else if (pfr < PFR_MIN) {
            leaks.add(new Leak(LeakSeverity.WARNING, "PFR", pfr, PFR_MIN,
                 "PFR un peu bas - pas assez d'initiative preflop",
                 "Relancez plus souvent, surtout en position (BTN, CO)."));
        } else if (pfr > 30) {
            leaks.add(new Leak(LeakSeverity.WARNING, "PFR", pfr, PFR_MAX,
                 "PFR trop élevé - trop de raises preflop",
                 "Resserrez vos ranges d'open en early position."));
        }

        //Gap VPIP-PFR
        double gap = vpip - pfr;
        if (gap > 10) {
            leaks.add(new Leak(LeakSeverity.WARNING, "VPIP-PFR Gap",
                 gap, 5.0,
                 "Écart VPIP-PFR trop grand - trop de limps/calls passifs",
                 "Transformez vos flat calls en raises ou en folds. C'est à dire plutot que de simplement payer à chaque fois, prenez une décision plus impactante comme relancer ou bien se coucher"));
        }

        //AF 
        double af = stats.getAF();
        if (af < 1.0) {
            leaks.add(new Leak(LeakSeverity.CRITICAL, "AF", af, AF_MIN,
                 "Aggression Factor très bas - jeu trop passif postflop",
                 "Misez et relancez plus souvent avec vos bonnes mains et semi-bluffs."));
        } else if (af < AF_MIN) {
            leaks.add(new Leak(LeakSeverity.WARNING, "AF", af, AF_MIN,
                 "Aggression Factor un peu bas",
                 "Augmentez vos c-bets et value-bets."));
        } else if (af > 5.0) {
            leaks.add(new Leak(LeakSeverity.WARNING, "AF", af, AF_MAX,
                 "Aggression Factor trop élevé - trop de bluffs",
                 "Réduisez la fréquence de bluff, surtout sur river."));
        }

        //WTSD
        double wtsd = stats.getWTSD();
        if (wtsd > 40 && stats.getHandsPlayed() >= MIN_HANDS) {
            leaks.add(new Leak(LeakSeverity.WARNING, "WTSD", wtsd, WTSD_MAX,
                 "WTSD trop élevé - vous allez trop souvent au showdown",
                 "Foldez plus sur river face à des mises fortes avec des mains marginales."));
        } else if (wtsd < 20 && stats.getHandsPlayed() >= MIN_HANDS) {
            leaks.add(new Leak(LeakSeverity.INFO, "WTSD", wtsd, WTSD_MIN,
                 "WTSD bas - vous foldez beaucoup avant le showdown",
                 "Vérifiez que vous ne foldez pas trop face à des bluffs."));
        }

        //WSD
        double wsd = stats.getWSD();
        if (wsd < 45 && stats.getWentToShowdown() >= 10) {
            leaks.add(new Leak(LeakSeverity.WARNING, "WSD", wsd, WSD_MIN,
                 "WSD bas - vous perdez souvent au showdown",
                 "Améliorez votre sélection de mains pour le showdown."));
        }

        // Trier par sévérité décroissante
        Collections.sort(leaks);
        return leaks;
    }
}
