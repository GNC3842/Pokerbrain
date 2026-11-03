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
 *   3-Bet : 5-10%
 *   AF    : 2.5-3.5
 *   WTSD  : 25-32%
 *   WSD   : 50-55%
 *
 * TAILLE D'ÉCHANTILLON
 *   Sous MIN_HANDS_ADVICE mains, aucune statistique n'est exploitable : on ne renvoie
 *   que l'avertissement d'échantillon. Entre MIN_HANDS_ADVICE et MIN_HANDS, les fuites
 *   sont signalées mais leur sévérité est abaissée d'un cran, car le bruit domine encore.
 *   C'est indispensable : sans ce garde-fou, un joueur à 0 main se voyait diagnostiquer
 *   « PFR beaucoup trop bas » et « VPIP trop bas » sur des statistiques toutes à zéro.
 *
 *   Chaque famille de fuite a en plus son propre dénominateur : l'AF n'a de sens qu'avec
 *   assez d'actions volontaires, le 3-bet qu'avec assez d'occasions, le W$SD qu'avec
 *   assez de showdowns.
 */

public class LeakDetector {
	private static final int MIN_HANDS = 50;
	//En dessous de ce seuil aucune statistique n'est interprétable
	private static final int MIN_HANDS_ADVICE = 10;
	//Nombre minimal d'actions volontaires (bets/raises/calls) pour juger l'agressivité
	private static final int MIN_ACTIONS_FOR_AF = 20;
	//Nombre minimal d'occasions de 3-bet pour juger la fréquence de 3-bet
	private static final int MIN_THREE_BET_SPOTS = 15;
	//Nombre minimal de showdowns pour juger le W$SD
	private static final int MIN_SHOWDOWNS = 10;

	 // Seuils TAG optimal
    private static final double VPIP_MIN = 20.0;
    private static final double VPIP_MAX = 25.0;
    private static final double PFR_MIN = 16.0;
    private static final double PFR_MAX = 22.0;
    private static final double THREE_BET_MIN = 5.0;
    private static final double THREE_BET_MAX = 10.0;
    private static final double AF_MIN = 2.5;
    private static final double AF_MAX = 3.5;
    private static final double WTSD_MIN = 25.0;
    private static final double WTSD_MAX = 32.0;
    private static final double WSD_MIN = 50.0;
    private static final double WSD_MAX = 60.0;

    //analyser les stats et retourner les fuites detectées par sévérité
    public static List<Leak> detect(PlayerStatistics stats){
    	List<Leak> leaks = new ArrayList<>();
    	int hands = stats.getHandsPlayed();

    	if (hands < MIN_HANDS) {
            leaks.add(new Leak(LeakSeverity.INFO, "Échantillon",
                hands, MIN_HANDS,
                "Seulement " + hands + " mains analysées",
                "Jouez au moins " + MIN_HANDS + " mains pour une analyse fiable."
            ));
        }

    	// Sous ce seuil les pourcentages ne veulent rien dire : on s'arrête là plutôt
    	// que d'accuser le joueur de fuites calculées sur trois mains (ou sur zéro).
    	if (hands < MIN_HANDS_ADVICE) {
    		return leaks;
    	}

    	// Entre MIN_HANDS_ADVICE et MIN_HANDS, on tempère chaque diagnostic d'un cran.
    	boolean noisy = hands < MIN_HANDS;

    	//Analyse VPIP
    	double vpip = stats.getVPIP();
        if (vpip > 40) {
        	add(leaks, noisy, LeakSeverity.CRITICAL, "VPIP", vpip, VPIP_MAX,
                 "VPIP beaucoup trop élevé - vous jouez trop de mains",
                 "Resserrez votre range preflop. Foldez les mains faibles (ex: K5o, J3o).");
        } else if (vpip > VPIP_MAX) {
            add(leaks, noisy, LeakSeverity.WARNING, "VPIP", vpip, VPIP_MAX,
                 "VPIP légèrement trop élevé",
                 "Éliminez les mains marginales (c'est a dire moyenne) en early position (quand vous parlez en 1er)");
        } else if (vpip < 10) {
            add(leaks, noisy, LeakSeverity.CRITICAL, "VPIP", vpip, VPIP_MIN,
                 "VPIP beaucoup trop bas - vous ne jouez presque aucune main",
                 "À ce rythme les blindes vous grignotent plus vite que vos bonnes mains ne rapportent. Ouvrez large au bouton et au cut-off.");
        } else if (vpip < 15) {
            add(leaks, noisy, LeakSeverity.WARNING, "VPIP", vpip, VPIP_MIN,
                 "VPIP trop bas - vous jouez trop serré (nit)",
                 "N'ayez pas peur de tenter votre chance avec des cartes qui se suivent (comme 8 et 9 de la même couleur) quand vous parlez dans les derniers autour de la table. C'est le meilleur moment pour voler le pot !");
        }

        //PFR
        double pfr = stats.getPFR();
        if (pfr < 8) {
            add(leaks, noisy, LeakSeverity.CRITICAL, "PFR", pfr, PFR_MIN,
                 "PFR beaucoup trop bas - vous vous contentez de suivre la mise de départ.",
                 "Si vous décidez de jouer une main, faites-le en relançant plutôt qu'en payant simplement.");
        } else if (pfr < PFR_MIN) {
            add(leaks, noisy, LeakSeverity.WARNING, "PFR", pfr, PFR_MIN,
                 "PFR un peu bas - pas assez d'initiative preflop",
                 "Relancez plus souvent, surtout en position (BTN, CO).");
        } else if (pfr > 30) {
            add(leaks, noisy, LeakSeverity.CRITICAL, "PFR", pfr, PFR_MAX,
                 "PFR beaucoup trop élevé - vous relancez presque toutes vos mains",
                 "Vous serez systématiquement dominé quand on vous paie. Gardez la relance pour le haut de votre range.");
        } else if (pfr > PFR_MAX) {
            add(leaks, noisy, LeakSeverity.WARNING, "PFR", pfr, PFR_MAX,
                 "PFR trop élevé - trop de raises preflop",
                 "Resserrez vos ranges d'open en early position.");
        }

        //Gap VPIP-PFR
        double gap = vpip - pfr;
        if (gap > 20) {
            add(leaks, noisy, LeakSeverity.CRITICAL, "VPIP-PFR Gap", gap, 5.0,
                 "Écart VPIP-PFR énorme - votre jeu preflop est presque entièrement passif",
                 "Vous entrez dans le coup en payant, donc sans jamais l'initiative. Choisissez : relancer ou se coucher.");
        } else if (gap > 10) {
            add(leaks, noisy, LeakSeverity.WARNING, "VPIP-PFR Gap", gap, 5.0,
                 "Écart VPIP-PFR trop grand - trop de limps/calls passifs",
                 "Transformez vos flat calls en raises ou en folds. C'est à dire plutot que de simplement payer à chaque fois, prenez une décision plus impactante comme relancer ou bien se coucher");
        }

        //3-Bet : uniquement si le joueur a eu assez d'occasions de le faire
        if (stats.getThreeBetOpportunities() >= MIN_THREE_BET_SPOTS) {
            double threeBet = stats.getThreeBet();
            if (threeBet < 2.0) {
                add(leaks, noisy, LeakSeverity.WARNING, "3-Bet", threeBet, THREE_BET_MIN,
                     "3-bet quasi inexistant - vous ne relancez jamais les relanceurs",
                     "Vous devenez lisible : quand vous relancez enfin, tout le monde se couche. 3-bettez vos meilleures mains ET quelques mains de bluff (A5s, KJs).");
            } else if (threeBet < THREE_BET_MIN) {
                add(leaks, noisy, LeakSeverity.INFO, "3-Bet", threeBet, THREE_BET_MIN,
                     "3-bet un peu rare",
                     "Relancez plus souvent face aux opens du bouton et du cut-off, qui sont larges.");
            } else if (threeBet > THREE_BET_MAX) {
                add(leaks, noisy, LeakSeverity.WARNING, "3-Bet", threeBet, THREE_BET_MAX,
                     "3-bet trop fréquent - vous surrelancez trop souvent",
                     "Vous gonflez le pot hors de position avec des mains dominées. Réservez le 3-bet aux paires servies, aux gros As et à quelques suited.");
            }
        }

        //AF : demande assez d'actions volontaires pour être interprétable
        double af = stats.getAF();
        if (stats.getActionCount() >= MIN_ACTIONS_FOR_AF) {
            if (Double.isInfinite(af)) {
                // Que des mises et des relances, jamais un call : hyper-agressif, pas passif
                add(leaks, noisy, LeakSeverity.WARNING, "AF", 99.9, AF_MAX,
                     "Vous ne suivez jamais - toutes vos actions sont des mises ou des relances",
                     "Certaines mains moyennes se jouent en payant. Tout miser ou tout coucher rend votre jeu prévisible et coûteux.");
            } else if (af < 1.0) {
                add(leaks, noisy, LeakSeverity.CRITICAL, "AF", af, AF_MIN,
                     "Aggression Factor très bas - jeu trop passif postflop",
                     "Misez et relancez plus souvent avec vos bonnes mains et semi-bluffs.");
            } else if (af < AF_MIN) {
                add(leaks, noisy, LeakSeverity.WARNING, "AF", af, AF_MIN,
                     "Aggression Factor un peu bas",
                     "Augmentez vos c-bets et value-bets.");
            } else if (af > AF_MAX) {
                add(leaks, noisy, LeakSeverity.WARNING, "AF", af, AF_MAX,
                     "Aggression Factor trop élevé - trop de bluffs",
                     "Réduisez la fréquence de bluff, surtout sur river.");
            }
        }

        //WTSD : le dénominateur est le nombre de mains jouées, pas le nombre de mains vues
        if (stats.getVpipCount() >= MIN_HANDS_ADVICE) {
            double wtsd = stats.getWTSD();
            if (wtsd > 40) {
                add(leaks, noisy, LeakSeverity.WARNING, "WTSD", wtsd, WTSD_MAX,
                     "WTSD trop élevé - vous allez trop souvent au showdown",
                     "Foldez plus sur river face à des mises fortes avec des mains marginales.");
            } else if (wtsd < 20) {
                add(leaks, noisy, LeakSeverity.INFO, "WTSD", wtsd, WTSD_MIN,
                     "WTSD bas - vous foldez beaucoup avant le showdown",
                     "Vérifiez que vous ne foldez pas trop face à des bluffs.");
            }
        }

        //WSD
        if (stats.getWentToShowDown() >= MIN_SHOWDOWNS) {
            double wsd = stats.getWSD();
            if (wsd < 45) {
                add(leaks, noisy, LeakSeverity.WARNING, "WSD", wsd, WSD_MIN,
                     "WSD bas - vous perdez souvent au showdown",
                     "Améliorez votre sélection de mains pour le showdown.");
            } else if (wsd > 70) {
                add(leaks, noisy, LeakSeverity.INFO, "WSD", wsd, WSD_MAX,
                     "WSD très élevé - vous n'allez au showdown qu'avec les nuts",
                     "Gagner presque tous vos showdowns veut dire que vous vous couchez trop tôt avec des mains encore gagnantes. Payez un peu plus large sur river.");
            }
        }

        //Profil de calling station : combinaison de stats, invisible stat par stat
        if (!Double.isInfinite(af) && vpip > 35 && pfr < 12
                && stats.getActionCount() >= MIN_ACTIONS_FOR_AF && af < 1.0) {
            add(leaks, noisy, LeakSeverity.CRITICAL, "Profil", vpip, VPIP_MAX,
                 "Profil calling station - beaucoup de mains jouées, presque aucune agression",
                 "C'est le profil le plus exploitable du poker : vos adversaires misent leurs bonnes mains en sachant que vous paierez. Jouez moins de mains, et jouez-les en relançant.");
        }

        //Winrate : la seule statistique qui compte vraiment
        if (hands >= MIN_HANDS) {
            double bb100 = stats.getBBPer100();
            if (bb100 < -20) {
                add(leaks, noisy, LeakSeverity.CRITICAL, "Winrate", bb100, 0.0,
                     String.format("Winrate très négatif (%.1f BB/100)", bb100),
                     "Reprenez les fuites ci-dessus dans l'ordre : preflop d'abord, c'est là que se corrigent les pertes les plus régulières.");
            } else if (bb100 < -5) {
                add(leaks, noisy, LeakSeverity.WARNING, "Winrate", bb100, 0.0,
                     String.format("Winrate négatif (%.1f BB/100)", bb100),
                     "Vous perdez lentement mais sûrement. Regardez le replay de vos mains les plus coûteuses.");
            }
        }

        // Trier par sévérité décroissante
        Collections.sort(leaks);
        return leaks;
    }

    /**
     * Ajoute une fuite en abaissant sa sévérité d'un cran si l'échantillon est petit.
     * Sur 15 mains une tendance est réelle mais pas encore une certitude.
     */
    private static void add(List<Leak> leaks, boolean noisy, LeakSeverity severity,
                            String category, double actual, double target,
                            String message, String suggestion) {
        LeakSeverity effective = severity;
        if (noisy) {
            effective = downgrade(severity);
        }
        leaks.add(new Leak(effective, category, actual, target, message, suggestion));
    }

    private static LeakSeverity downgrade(LeakSeverity severity) {
        switch (severity) {
            case CRITICAL: return LeakSeverity.WARNING;
            case WARNING:  return LeakSeverity.INFO;
            default:       return LeakSeverity.INFO;
        }
    }
}
