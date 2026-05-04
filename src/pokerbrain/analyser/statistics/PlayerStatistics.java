package pokerbrain.analyser.statistics;

/**
 * Statistiques calculées d'un joueur, immuable
 *
 * VPIP = Voluntarily Put money In Pot (hors blindes forcées)
 * PFR = Pre-Flop Raise percentage
 * AF = Aggression Factor = (bets + raises) / calls
 * WTSD = Went To ShowDown percentage (parmi les mains jouées postflop)
 * WSD= Won at ShowDown percentage
 * BB/100= Big Blinds gagnées par 100 mains (winrate)
 *
 * PROFILS DÉTECTÉS :
 *   TAG  : VPIP < 25  et PFR > 15  -> Tight-Aggressive (optimal)
 *   LAG  : VPIP > 30  et PFR > 20  -> Loose-Aggressive
 *   FISH : VPIP > 40  et PFR < 10  -> Calling station
 *   NIT  : VPIP < 15  et PFR < 12  -> Selectif mais passif
 *   ROCK : VPIP < 20  et AF > 2    -> Selectif et agressif
 */


public class PlayerStatistics {

}
