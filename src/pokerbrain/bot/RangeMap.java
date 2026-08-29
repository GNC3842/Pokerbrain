package pokerbrain.bot;

import pokerbrain.model.core.Rank;
import pokerbrain.model.game.ActionType;

/**
 * Heatmap 13x13 representant la strategie preflop d'un bot.
 *
 * STRUCTURE :
 *   Chaque cellule [rankHigh][rankLow] contient l'ActionType a jouer
 *   (FOLD, CALL ou RAISE) pour cette combinaison de rangs.
 *
 *   Indexation : rankIndex = Rank.getValue() - 2  (TWO=0 ... ACE=12)
 *
 *   Visuellement la grille ressemble a :
 *        A    K    Q    J    T    9 ...
 *   A  [AA] [AKs] [AQs] ...
 *   K  [AKo] [KK] [KQs] ...
 *   Q  [AQo] [KQo] [QQ] ...
 *   ...
 *
 * ACTIONS :
 *   FOLD  : ne joue pas la main
 *   CALL  : suit sans relancer (limping ou flat call)
 *   RAISE : relance / open raise
 * 
 * Attention en pratique on va découpé ce tableau en 3 catégorie suited,offsuit et pairs pour éviter les problèmes
 * de comparaisons dessus/dessous la daigonale
 */

public class RangeMap {
    private static final int SIZE = 13; // TWO(0) ... ACE(12)

    // Actions par type de main
    private final ActionType[][] suited = new ActionType[SIZE][SIZE];
    private final ActionType[][] offsuit = new ActionType[SIZE][SIZE];
    private final ActionType[] pairs = new ActionType[SIZE];

    //Constructeur: crée une heatmap où tout est à FOLD par défaut
    public RangeMap() {
        for (int i = 0; i < SIZE; i++) {
            pairs[i] = ActionType.FOLD;
            for (int j = 0; j < SIZE; j++) {
                suited[i][j]  = ActionType.FOLD;
                offsuit[i][j] = ActionType.FOLD;
            }
        }
    }

    //Renvoie une RangeMap vide (tout FOLD)
    public static RangeMap empty() {
        return new RangeMap();
    }

    //Setters pour paires 

    //Definit l'action pour une paire de poche specifique
    public RangeMap setPair(Rank rank, ActionType action) {
        pairs[idx(rank)] = action;
        return this;
    }

    //Definit l'action pour toutes les paires >= minRank
    public RangeMap setPairsFrom(Rank minRank, ActionType action) {
        for (int i = idx(minRank); i < SIZE; i++) pairs[i] = action;
        return this;
    }

    // Setters pour suited

    //Definit l'action pour une main suited specifique (ex: AKs)
    public RangeMap setSuited(Rank high, Rank low, ActionType action) {
        int h = idx(high);
        int l = idx(low);
        if (h == l) { 
            pairs[h] = action; 
            return this; 
        }
        // Toujours stocker [max][min] pour cohérence + logique avec séparation en 3
        suited[Math.max(h, l)][Math.min(h, l)] = action;
        return this;
    }

    //Definit l'action pour toutes les mains suited high-X avec X >= minLow
    public RangeMap setSuitedFrom(Rank high, Rank minLow, ActionType action) {
        int h = idx(high);
        for (int l = idx(minLow); l < h; l++) {
            suited[h][l] = action;
        }
        return this;
    }

    //Setters pour offsuit

    //Definit l'action pour une main offsuit specifique (ex: AKo)
    public RangeMap setOffsuit(Rank high, Rank low, ActionType action) {
        int h = idx(high);
        int l = idx(low);
        if (h == l) { pairs[h] = action; return this; }
        offsuit[Math.max(h, l)][Math.min(h, l)] = action;
        return this;
    }

    // Definit l'action pour toutes les mains offsuit high-X avec X >= minLow
    public RangeMap setOffsuitFrom(Rank high, Rank minLow, ActionType action) {
        int h = idx(high);
        for (int l = idx(minLow); l < h; l++) {
            offsuit[h][l] = action;
        }
        return this;
    }

    //Consultation

    //Retourne l'action a jouer pour une combinaison donnee et gere automatiquement l'ordre des rangs
    public ActionType getAction(Rank rank1, Rank rank2, boolean isSuited) {
        int r1 = idx(rank1);
        int r2 = idx(rank2);

        if (r1 == r2) {
            return pairs[r1];
        }

        int high = Math.max(r1, r2);
        int low = Math.min(r1, r2);
        if (isSuited){
            return suited[high][low];
        } else {
            return offsuit[high][low];
        }
    }

    //Vrai si aucune main n'est jouée (toutes les cellules à FOLD).
    //Sert à détecter un fichier de range absent ou vide, plutôt que de conseiller
    //silencieusement « fold » sur les 169 mains.
    public boolean isEmpty() {
        for (int i = 0; i < SIZE; i++) {
            if (pairs[i] != ActionType.FOLD) return false;
            for (int j = 0; j < SIZE; j++) {
                if (suited[i][j] != ActionType.FOLD) return false;
                if (offsuit[i][j] != ActionType.FOLD) return false;
            }
        }
        return true;
    }

    /**
     * Adapte cette range en filtre pour le Monte-Carlo, qui ne manipule que des
     * index de cartes primitifs (index = rangIndex*4 + couleurIndex, cf. CardPrimitiveBridge).
     * Une main adverse est acceptée si la range ne dit pas FOLD dessus.
     */
    public pokerbrain.simulator.MonteCarloEngine.VillainRange asVillainRange() {
        return (indexA, indexB) -> {
            int rankA = indexA / 4;
            int rankB = indexB / 4;
            boolean isSuited = (indexA % 4) == (indexB % 4);
            if (rankA == rankB) {
                return pairs[rankA] != ActionType.FOLD;
            }
            int high = Math.max(rankA, rankB);
            int low = Math.min(rankA, rankB);
            if (isSuited) {
                return suited[high][low] != ActionType.FOLD;
            }
            return offsuit[high][low] != ActionType.FOLD;
        };
    }

    // Helper: renvoie juste le rank normalisé
    private static int idx(Rank r) {
        return r.getValue() - 2;
    }

    //Statistiques de la heatmap (à implémenter)

    //Calcul VPIP théorique

    //meme chose pour PFR théorique
   

    //Affichage de la heatmap en Ascii si besoin (a faire pour le fun)
}
/*
    }

    private static int idx(Rank r) {
    	return r.getValue() - 2;
    }
*/