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
    public HandAnalyser(int[] lookupTable) {
        this.engine = new MonteCarloEngine(lookupTable);

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
