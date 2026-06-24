package pokerbrain.analyser;

import pokerbrain.bot.PotOddsCalculator;
import pokerbrain.bot.RangeMap;
import pokerbrain.bot.BotProfile;
import pokerbrain.bot.BotProfileFactory;
import pokerbrain.model.core.Card;
import pokerbrain.model.game.*;
import pokerbrain.model.history.*;
import pokerbrain.simulator.MonteCarloEngine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/*
 * Analyse une main complete et produit un ActionAdvice en fonction des actions du hero
 * 
 * Analyse en preflop: compare l'action jouée à ce que recommenderai un profil TAG pour cette position du joueur
 * 		EXEMPLE: Heatmap TAG dit RAISE, joueur a CALL -> "Tu aurais dû raise"
 * 				 Heatmap TAG dit FOLD, joueur a CALL -> "Main trop faible ici"
 * 
 * Analyse en postflop: calcule l'équité MC à ce moment précis puis compare
 * 		CAS 1: Face à une mise :                                     │
 *  	 		equity >= breakEvenEquity -> CALL ou RAISE profitable        │
 *   			equity <  breakEvenEquity -> FOLD correct en EV              │
 *   			equity >= 65% -> RAISE pour value
 *   	CAS 2: Pas de mise (check/bet)
 *   			equity >=65% -> BET pour value
 *   			equity >=45% -> CHECK ok
 *   			equity <= 30 -> CHECK 
 *   
 * USAGE :
 *   HandAnalyser analyser = new HandAnalyser(lookupTable);
 *   List<ActionAdvice> advices = analyser.analyse(hand);
 */

public class HandAnalyser {
	private static final int MC_ITERATIONS = 10000;
	private static final double VALUE_THRESHOLD = 0.65;
    private static final double MEDIUM_THRESHOLD = 0.45;
    private static final double BLUFF_THRESHOLD = 0.30;
    
    private final MonteCarloEngine engine;
    private final RangeMap tagBtnRange;
    private final RangeMap tagCoRange;
    private final RangeMap tagUtgRange;
    
    //CONSTRUCTEUR
    public HandAnalyser() {
        this.engine = new MonteCarloEngine();

        // Charger les ranges TAG comme référence optimale
        // On récupère les ranges via BotProfileFactory pour ne pas dupliquer le code
        BotProfile tagProfile = BotProfileFactory.createTightAggressive();
        this.tagBtnRange = tagProfile.getRangeFor(Position.BTN);
        this.tagCoRange = tagProfile.getRangeFor(Position.CO);
        this.tagUtgRange = tagProfile.getRangeFor(Position.UTG);
    }
    
    //ANalyse toutes les actions du héro dune main
    public List<ActionAdvice> analyse(Hand hand) {
        if (hand == null || hand.getActions().isEmpty()) {
            return Collections.emptyList();
        }

        String heroId = hand.getHeroId();
        if (heroId == null) return Collections.emptyList();

        List<ActionAdvice> advices = new ArrayList<>();
        List<Action> actions = hand.getActions();
        List<GameState> states = hand.getGameStates();

        for (int i = 0; i < actions.size(); i++) {
            Action action = actions.get(i);

            // On n'analyse que les actions du héro
            if (!action.getPlayer().getId().equals(heroId)) continue;

            // Récupérer l'état du jeu AVANT cette action
            // (le GameState à l'index i est l'état APRÈS l'action i,
            //  donc l'état avant l'action i est le GameState i-1,
            //  ou l'état initial si i == 0)
            GameState stateBefore = (i > 0 && i - 1 < states.size())
                ? states.get(i - 1) : null;

            ActionAdvice advice;
            if (action.getRound() == BettingRound.PREFLOP) {
                advice = analysePreflopAction(i, action, hand);
            } else {
                advice = analysePostflopAction(i, action, hand, stateBefore);
            }

            if (advice != null) {
                advices.add(advice);
            }
        }

        return advices;
    }
    
    
    //Analyse preflop en comparant avec profil TAG
    private ActionAdvice analysePreflopAction(int index, Action action, Hand hand) {
    	Player hero = action.getPlayer();
        List<Card> hole = hero.getHoleCards();
        if (hole.size() != 2){
            throw new IllegalStateException("Le joueur " + hero.getName() + " n'a pas 2 cartes lors de l'analyse preflop (taille=" 
        + hole.size() + "). Main corrompue ou HandRecord mal construit.");
        };

        Card c1 = hole.get(0);
        Card c2 = hole.get(1);
        boolean suited = c1.getSuit() == c2.getSuit();
        Position pos = hero.getPosition();

        // Déterminer la range TAG de référence selon la position
        RangeMap refRange = getTagRange(pos);
        ActionType tagRecommendation = refRange.getAction(c1.getRank(), c2.getRank(), suited);

        ActionType played = action.getType();
        String handName = formatHand(c1, c2, suited);

        // Normaliser : CHECK est équivalent à un FOLD gratuit en BB
        ActionType normalizedPlayed = played;
        if (played == ActionType.CHECK) {
        	normalizedPlayed = ActionType.FOLD;
        }
        if (played == ActionType.ALL_IN) {
        	normalizedPlayed = ActionType.RAISE;
        }
        if (played == ActionType.BET) {
        	normalizedPlayed = ActionType.RAISE;
        }
        
        ActionAdvice.Builder builder = new ActionAdvice.Builder()
                .actionIndex(index)
                .playerName(hero.getName())
                .round(BettingRound.PREFLOP)
                .played(played)
                .playedAmount(action.getAmount())
                .recommended(tagRecommendation);
        
        if (normalizedPlayed == tagRecommendation ) {
        	// Action conforme au TAG
            builder.score(100)
                   .reasoning(handName + " en " + pos + " - action conforme au jeu TAG optimal.");
        } else if (tagRecommendation == ActionType.RAISE && normalizedPlayed == ActionType.CALL ) {
        	builder.score(60)
            	.reasoning(handName + " en " + pos
                + " — un RAISE était recommandé. Le CALL est passif : "
                + "vous ne prenez pas l'initiative et laissez les adversaires voir le flop à bon prix.");
        } else if (tagRecommendation == ActionType.RAISE && normalizedPlayed == ActionType.FOLD) {
        	// TAG dit raise, héro a fold
            builder.score(25)
                   .reasoning(handName + " en " + pos
                       + " — cette main est assez forte pour être relancée en position "
                       + pos + ". Le FOLD fait perdre de la valeur attendue.");
        } else if (tagRecommendation == ActionType.CALL && normalizedPlayed == ActionType.FOLD) {
        	 // TAG dit call, héro a fold
            builder.score(50)
                   .reasoning(handName + " en " + pos
                       + " — cette main méritait un CALL. "
                       + "Même si elle n'est pas assez forte pour relancer, elle a suffisamment de potentiel.");
        }else if (tagRecommendation == ActionType.FOLD && normalizedPlayed == ActionType.CALL) {
            // TAG dit fold, héro a call
            builder.score(35)
                   .reasoning(handName + " en " + pos
                       + " — cette main est trop faible pour être jouée profitablement ici. "
                       + "Le CALL preflop avec des mains marginales coûte cher sur le long terme.");
        } else if (tagRecommendation == ActionType.FOLD && normalizedPlayed == ActionType.RAISE) {
            // TAG dit fold, héro a raise — erreur
            builder.score(15)
                   .reasoning(handName + " en " + pos
                       + " -> main hors range pour cette position. "
                       + "Relancer ici vous met dans des spots difficiles postflop "
                       + "car vous serez souvent dominé par les mains adverses qui suivent.");
        } else if (tagRecommendation == ActionType.CALL && normalizedPlayed == ActionType.RAISE) {
            // TAG dit call, héro a raise — parfois OK
            builder.score(70)
                   .reasoning(handName + " en " + pos
                       + " — le CALL suffisait selon le jeu TAG standard. "
                       + "Le RAISE n'est pas une erreur si vous avez un read, "
                       + "mais en général cette main n'a pas assez de force pour 3-bet.");
        } else {
            builder.score(80)
                   .reasoning(handName + " en " + pos + " — action acceptable.");
        }

        return builder.build();
    }
    
    //Analyse postfflop avec montecarlo et pot odds
    /**
     * Le raisonnement suit la logique EV :
     *   - Calculer l'équité MC exacte à ce moment
     *   - Si face à une mise : comparer equity vs break-even equity (pot odds)
     *   - Sinon : évaluer si un value-bet ou un check était correct
     */
    private ActionAdvice analysePostflopAction(int index, Action action, Hand hand,
                                                GameState stateBefore) {
        Player hero = action.getPlayer();
        List<Card> hole = hero.getHoleCards();
        if (hole.size() != 2) {
            throw new IllegalStateException("Le joueur " + hero.getName() + " n'a pas 2 cartes lors de l'analyse postflop (taille="
        + hole.size() + "). Main corrompue ou HandRecord mal construit.");
        };

        // Board au moment de l'action
        List<Card> board;
        if (stateBefore != null) {
            board = stateBefore.getBoard();
        } else {
            board = hand.getBoard();
        }
        if (board == null || board.isEmpty()) return null;

        // Calculer l'équité MC
        int nbOpponents = countActiveOpponents(stateBefore, hero.getId());
        if (nbOpponents < 1) {
        	nbOpponents = 1;
        }

        double equity;
        try {
            MonteCarloEngine.EquityResult result = engine.calculateEquity(hole, board, MC_ITERATIONS, nbOpponents);
            equity = result.equity() / 100.0; // convertir % -> ratio
        } catch (Exception e) {
            equity = 0.5; // fallback
        }

        // Informations du pot
        int totalPot;
        int currentBetLevel;
        if (stateBefore != null) {
        	totalPot = stateBefore.getTotalPot();
        	currentBetLevel = stateBefore.getCurrentBetLevel();
        } else {
        	totalPot = 0;
        	currentBetLevel = 0;
        }

        ActionType played = action.getType();
        BettingRound round = action.getRound();

        ActionAdvice.Builder builder = new ActionAdvice.Builder()
            .actionIndex(index)
            .playerName(hero.getName())
            .round(round)
            .played(played)
            .playedAmount(action.getAmount())
            .equity(equity);

        boolean facingBet = currentBetLevel > 0
            && (played == ActionType.CALL || played == ActionType.FOLD
                || played == ActionType.RAISE || played == ActionType.ALL_IN);

        if (facingBet) {
            return analyseFacingBet(builder, played, action.getAmount(),
                                     equity, totalPot, currentBetLevel, hero, round);
        } else {
            return analyseNobet(builder, played, action.getAmount(),
                                 equity, totalPot, hero, round);
        }
    }
    
    /**
     * Analyse quand le héro fait face à une mise adverse
     * Compare l'equity à la cote du pot pour déterminer l'action EV+.
     */
    private ActionAdvice analyseFacingBet(ActionAdvice.Builder builder,ActionType played, int playedAmount,
                                           double equity, int pot, int betLevel,
                                           Player hero, BettingRound round) {
        int callAmount = Math.max(1, betLevel - hero.getCurrentBet());
        double breakEven = PotOddsCalculator.breakEvenEquity(pot, callAmount);
        double callEV = PotOddsCalculator.callEV(equity, pot, callAmount);

        builder.potOdds(breakEven);

        String eqStr = String.format("%.0f%%", equity * 100);
        String beStr = String.format("%.0f%%", breakEven * 100);
        String evStr = String.format("%+.1f BB", callEV);

        if (equity >= VALUE_THRESHOLD) {
            // Main très forte -> raise pour value
            ActionType recommended = ActionType.RAISE;
            builder.recommended(recommended);

            if (played == ActionType.RAISE || played == ActionType.ALL_IN) {
                builder.score(100)
                       .reasoning("Équité " + eqStr + " - RAISE pour value. "
                           + "Avec une main aussi forte, maximiser la valeur est optimal. Bien joué!");
            } else if (played == ActionType.CALL) {
                builder.score(65)
                       .reasoning("Équité " + eqStr + " - un RAISE pour value était meilleur qu'un simple CALL. "
                           + "Vous avez laissé de l'argent sur la table. "
                           + "La cote du pot (" + beStr + ") est largement en votre faveur (EV du call : " + evStr + ").");
            } else {
                // FOLD avec 65%+ d'equity...
                builder.score(5)
                       .reasoning("Équité " + eqStr + " - FOLD avec une main forte est une erreur majeure. "
                           + "La cote du pot demandait seulement " + beStr + " d'équité pour être profitable. "
                           + "EV perdue : " + evStr + ".");
            }
        } else if (equity >= breakEven) {
            // Equity suffisante pour call
            ActionType recommended = ActionType.CALL;
            builder.recommended(recommended);

            if (played == ActionType.CALL) {
                builder.score(100)
                       .reasoning("Équité " + eqStr + " vs cote requise " + beStr
                           + " - CALL correct et profitable (EV : " + evStr + ").");
            } else if (played == ActionType.RAISE) {
                // Raise avec equity moyenne - parfois OK
                builder.score(70)
                       .reasoning("Équité " + eqStr + " — le CALL suffisait (EV : " + evStr + "). "
                           + "Le RAISE augmente la variance sans avantage clair, "
                           + "sauf si vous avez un read spécifique sur l'adversaire.");
            } else {
                // FOLD alors que le call était profitable
                builder.score(20)
                       .reasoning("Équité " + eqStr + " vs cote requise " + beStr
                           + " - le CALL était profitable (EV : " + evStr + "). "
                           + "Le FOLD ici fait perdre de la valeur attendue sur le long terme.");
            }
        } else {
            // Equity insuffisante -> fold correct
            ActionType recommended = ActionType.FOLD;
            builder.recommended(recommended);

            if (played == ActionType.FOLD) {
                builder.score(100)
                       .reasoning("Équité " + eqStr + " vs cote requise " + beStr
                           + " - FOLD correct. Le call aurait une EV négative (" + evStr + ").");
            } else if (played == ActionType.CALL) {
                builder.score(30)
                       .reasoning("Équité " + eqStr + " vs cote requise " + beStr
                           + " - le CALL n'est pas profitable ici (EV : " + evStr + "). "
                           + "Même si c'est tentant, le FOLD préserve vos jetons pour de meilleurs spots.");
            } else {
                // RAISE en bluff - possible mais risqué
                builder.score(25)
                       .reasoning("Équité " + eqStr + " - RAISE en bluff. "
                           + "Peut fonctionner si l'adversaire fold souvent, "
                           + "mais avec cette equity, le risque dépasse le gain attendu.");
            }
        }

        return builder.build();
    }

    /**
     * Analyse quand personne n'a misé (cad check ou bet possible)
     */
    private ActionAdvice analyseNobet(ActionAdvice.Builder builder,
                                       ActionType played, int playedAmount,
                                       double equity, int pot,
                                       Player hero, BettingRound round) {
        String eqStr = String.format("%.0f%%", equity * 100);

        if (equity >= VALUE_THRESHOLD) {
            // Main forte → bet pour value
            builder.recommended(ActionType.BET);

            if (played == ActionType.BET || played == ActionType.RAISE) {
                builder.score(100)
                       .reasoning("Équité " + eqStr + " - BET pour value, bien joué. "
                           + "Avec cette force de main, miser extrait de la valeur des mains plus faibles.");
            } else {
                builder.score(40)
                       .reasoning("Équité " + eqStr + " - un BET pour value était recommandé. "
                           + "Le CHECK laisse l'adversaire voir la carte suivante gratuitement "
                           + "et vous empêche de construire le pot avec votre main forte.");
            }
        } else if (equity >= MEDIUM_THRESHOLD) {
            // Main moyenne -> check est raisonnable (pot control)
            builder.recommended(ActionType.CHECK);

            if (played == ActionType.CHECK) {
                builder.score(90)
                       .reasoning("Équité " + eqStr + " - CHECK pour contrôler la taille du pot. "
                           + "Avec une main moyenne, éviter de gonfler le pot est prudent.");
            } else if (played == ActionType.BET) {
                builder.score(70)
                       .reasoning("Équité " + eqStr + " - le BET est agressif mais défendable. "
                           + "Un CHECK aurait mieux contrôlé le pot avec une main de force moyenne. "
                           + "Le risque est de se faire relancer et devoir abandonner.");
            } else {
                builder.score(100)
                       .reasoning("Équité " + eqStr + " - action acceptable.");
            }
        } else {
            // Main faible -> check, pas de bluff recommandé par défaut
            builder.recommended(ActionType.CHECK);

            if (played == ActionType.CHECK) {
                builder.score(100)
                       .reasoning("Équité " + eqStr + " - CHECK correct avec une main faible. "
                           + "Pas de raison de miser sans équité suffisante.");
            } else if (played == ActionType.BET) {
                // Bluff
                builder.score(50)
                       .reasoning("Équité " + eqStr + " - bluff. "
                           + "Peut fonctionner occasionnellement, mais avec cette main, "
                           + "vous ne pouvez pas supporter un call ou un raise. "
                           + "Bluffer est acceptable si c'est fait avec modération et sur les bons boards.");
            } else {
                builder.score(90)
                       .reasoning("Équité " + eqStr + " - action correcte.");
            }
        }

        return builder.build();
    }
    
    //HELPERS
    
    
    // Retourne la range TAG de référence selon la position
    private RangeMap getTagRange(Position pos) {
        if (pos == null) return tagCoRange;
        switch (pos) {
            case BTN: case SB:
                return tagBtnRange;
            case CO: case HJ: case BB:
                return tagCoRange;
            default: // UTG, UTG1, UTG2, MP, LJ
                return tagUtgRange;
        }
    }
    
    // Compte les adversaires actifs (non foldés, non out) autres que le héro
    private int countActiveOpponents(GameState state, String heroId) {
        if (state == null) return 1;
        int count = 0;
        for (PlayerState ps : state.getPlayers()) {
            if (ps.getId().equals(heroId)) continue;
            if (ps.getStatus() != PlayerStatus.FOLDED && ps.getStatus() != PlayerStatus.OUT) {
                count++;
            }
        }
        return Math.max(1, count);
    }

    //Formate une main pour l'affichage : "AKs", "QJo", "TT"
    private String formatHand(Card c1, Card c2, boolean suited) {
        char r1 = c1.getRank().getSymbol();
        char r2 = c2.getRank().getSymbol();
        // Toujours le plus gros rang en premier
        char hi, lo;
        if (c1.getRank().getValue() >= c2.getRank().getValue()) {
            hi = r1; lo = r2;
        } else {
            hi = r2; lo = r1;
        }
        if (hi == lo) return "" + hi + lo; // paire
        return "" + hi + lo + (suited ? "s" : "o");
    }


}
