package pokerbrain.analyser.statistics;

import pokerbrain.model.game.*;
import pokerbrain.model.history.Hand;

import java.util.*;

/**
 * Calcule les statistiques d'un joueur à partir d'une liste de Hand
 *
 * RÈGLES DE COMPTAGE :
 *
 * VPIP (Voluntarily Put money In Pot) :
 *   On compte une main comme VPIP si le joueur a mis de l'argent preflop
 *   VOLONTAIREMENT, c'est-à-dire hors blindes forcées
 *   - CALL preflop -> VPIP (même si c'est juste compléter la BB)
 *   - RAISE preflop -> VPIP
 *   - ALL_IN preflop -> VPIP
 *   - CHECK (BB qui check) -> PAS VPIP
 *   - FOLD -> PAS VPIP
 *
 * PFR (Pre-Flop Raise) :
 *   Toute action RAISE ou BET ou ALL_IN en preflop
 *
 * AF (Aggression Factor) :
 *   (nombre total de BET + RAISE) / (nombre total de CALL)
 *   Sur TOUTES les streets (pas juste preflop)
 *
 * WTSD (Went To ShowDown) :
 *   Pourcentage de mains où le joueur est allé au showdown
 *   parmi celles où il a joué postflop (VPIP)
 *
 * WSD (Won at ShowDown) :
 *   Pourcentage de showdowns gagnés
 */

public class PlayerStatisticsCalculator {

}
