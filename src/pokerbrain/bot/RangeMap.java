package pokerbrain.bot;

import pokerbrain.model.core.Rank;
import pokerbrain.model.game.ActionType;

import java.io.*;
import java.net.URL;
import java.nio.file.*;
import java.util.*;

/**
 * Heatmap 13x13 representant la strategie preflop d'un bot.
 *
 * STRUCTURE :
 *   Chaque cellule [rankHigh][rankLow] contient l'ActionType a jouer
 *   (FOLD, CALL ou RAISE) pour cette combinaison de rangs.
 *
 *   Indexation : rankIndex = Rank.getValue() - 2  (TWO=0 ... ACE=12)
 *   Diagonale  : paires de poche (AA, KK, QQ...)
 *   Triangle superieur droit : suited  (AKs, KQs...)
 *   Triangle inferieur gauche: offsuit (AKo, KQo...)
 *
 *   Convention standard poker :
 *     - [i][j] avec i > j : ligne=rang haut, colonne=rang bas
 *     - i == j : paire
 *     - i > j  dans le triangle superieur = suited (Ks)
 *               dans le triangle inferieur = offsuit (Ko)
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
 * SUITED vs OFFSUIT :
 *   La meme cellule logique (ex: A-K) peut avoir des actions differentes
 *   selon si les cartes sont de la meme couleur. On utilise deux matrices.
 */
public class RangeMap{
	private static final int SIZE = 13; // TWO(0) ... ACE(12)
	
	// Actions par type de main
    // suited[high][low]   : high > low, cartes meme couleur
    // offsuit[high][low]  : high > low, couleurs differentes
    // pairs[rank][rank]   : paire de poche (diagonale)
    private final ActionType[][] suited  = new ActionType[SIZE][SIZE];
    private final ActionType[][] offsuit = new ActionType[SIZE][SIZE];
    private final ActionType[]   pairs   = new ActionType[SIZE];
    
    //Constructeur
    //Cree une heatmap avec toutes les mains a FOLD par defaut
    public RangeMap() {
        for (int i = 0; i < SIZE; i++) {
            pairs[i] = ActionType.FOLD;
            for (int j = 0; j < SIZE; j++) {
                suited[i][j]  = ActionType.FOLD;
                offsuit[i][j] = ActionType.FOLD;
            }
        }
    }
    
    //Liste setters
    
    //défini l'action pour une paire de poche
    public RangeMap setPair(Rank rank, ActionType action) {
        pairs[idx(rank)] = action;
        return this;
    }
    //Definit l'action pour toutes les paires >= minRank
    public RangeMap setPairsFrom(Rank minRank, ActionType action) {
        for (int i = idx(minRank); i < SIZE; i++) pairs[i] = action;
        return this;
    }
	//Renvoie une RangeMap vide
	public static RangeMap empty() { 
		return new RangeMap(); 
	}
	
	//Retourne une representation ASCII de la heatmap (13x13
    public String toAscii() {
    	
    }
    
    private static int idx(Rank r) { 
    	return r.getValue() - 2; 
    }
}