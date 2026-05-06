package pokerbrain.model.history;

import pokerbrain.model.core.Card;
import pokerbrain.model.game.Action;
import pokerbrain.model.game.BettingRound;
import pokerbrain.model.game.SidePot;


/**
 * Snapshot immuable de l'état complet du jeu après une action.
 *
 * Contient : round courant, board, pot total, side pots, état de chaque joueur,joueur courant, niveau de mise, et l'action qui a produit cet état.
 * Construit via me builder comme pour Hand.
 */