package pokerbrain.engine;

import java.util.*;
import pokerbrain.model.game.*;

/*
 * Gere un tour d'enchere complet: PREFLOP, FLOP, TURN ou RIVER
 * 
 * Regles:
 * 		- un joueur ne peut agir que si isActive() et !hasActed() sauf relance
 * 		- call partiel = all_in si stack insuffisant
 * 		- relance reouvre l'action aux joueurs qui ont déja parlé
 * 		- round fini quand tous les joeurus ont parlés et tous les mises égales/ALL_IN
 */

public class BettingRoundManager {
	private final BettingRound round;
	private final PotManager potManager;
	private final int bigBlind;
	
	
}
