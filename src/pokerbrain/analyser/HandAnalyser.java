package pokerbrain.analyser;

import pokerbrain.bot.PotOddsCalculator;
import pokerbrain.bot.RangeMap;
import pokerbrain.bot.RangeMapLoader;
import pokerbrain.bot.BotProfile;
import pokerbrain.bot.BotProfileFactory;
import pokerbrain.model.core.Card;
import pokerbrain.model.core.Rank;
import pokerbrain.model.game.*;
import pokerbrain.model.history.*;
import pokerbrain.simulator.MonteCarloEngine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Analyse une main complète et produit un ActionAdvice par action du héro.
 *
 * PREFLOP — l'action jouée est comparée à une référence TAG qui dépend du niveau de
 * relance déjà atteint :
 *   - pot non ouvert  : range d'ouverture de la position (RFI)
 *   - face à un open  : range de défense (call / 3-bet) — dédiée en SB et en BB
 *   - face à un 3-bet : seules les mains premium continuent
 *   - face à un 4-bet+: seules AA/KK relancent
 * Le nombre de limpeurs, la profondeur de tapis et la taille jouée entrent dans le
 * jugement. CHECK (option de la BB) et ALL_IN ont leur propre traitement : ce ne sont
 * ni des folds ni des relances ordinaires.
 *
 * POSTFLOP — l'équité Monte-Carlo est calculée sur le board RÉELLEMENT visible à cet
 * instant, puis comparée à la cote du pot. Les seuils sont exprimés en multiples de la
 * part équitable 1/(n+1), ce qui les rend justes en multiway. Les tirages (outs, fold
 * equity requise), le SPR, le sizing et le rôle du coup (c-bet, donk bet, check-raise)
 * complètent le diagnostic.
 *
 * ROBUSTESSE — une action inanalysable produit un ActionAdvice marqué non analysable
 * portant le motif, jamais une exception : une main abîmée ne doit pas faire perdre
 * les conseils de toutes les autres actions.
 *
 * USAGE :
 *   HandAnalyser analyser = new HandAnalyser();   // instance réutilisable
 *   List&lt;ActionAdvice&gt; advices = analyser.analyse(hand);
 */
public class HandAnalyser {

	private static final int MC_ITERATIONS = 10000;
	private static final int MC_ITERATIONS_PREFLOP = 4000;

	/**
	 * Seuils exprimés en multiples de la part équitable 1/(nbAdversaires + 1).
	 * En heads-up la part équitable vaut 50 %, on retrouve donc les seuils classiques
	 * 65 % / 45 % / 30 %. En multiway ils s'abaissent automatiquement : 45 % d'équité
	 * contre trois joueurs est une main très forte, pas une main moyenne.
	 */
	private static final double VALUE_RATIO  = 1.30;
	private static final double MEDIUM_RATIO = 0.90;

	//Sizing de référence
	private static final double OPEN_SIZE_BB = 2.5;      // open standard, + 1 BB par limpeur
	private static final double THREE_BET_IP_MULT = 3.0; // 3-bet en position
	private static final double THREE_BET_OOP_MULT = 4.0;// 3-bet hors de position
	private static final double CBET_FRACTION = 0.6;     // fraction de pot pour une mise postflop
	private static final double SIZING_TOLERANCE = 0.40; // écart relatif toléré avant reproche
	private static final double SHOVE_MAX_BB = 20.0;     // au-delà, le tapis n'est plus standard

	private static final String RANGE_DIR = "pokerbrain/bot/ranges/";

	/**
	 * Les ranges sont immuables une fois chargées : on les lit une seule fois pour
	 * toute l'application. Le MonteCarloEngine, lui, porte des buffers d'instance et
	 * n'est pas partageable entre threads — il reste donc propre à chaque analyseur.
	 * Construire un HandAnalyser est ainsi bon marché, et l'analyse peut tourner sur
	 * un thread de fond sans rien partager de mutable.
	 */
	private static final class Ranges {
		//Ranges d'ouverture (RFI)
		static final RangeMap OPEN_BTN;
		static final RangeMap OPEN_CO;
		static final RangeMap OPEN_UTG;
		static final RangeMap OPEN_SB;
		//Ranges de défense face à un open — distinctes des ranges d'ouverture
		static final RangeMap DEFENCE_SB;
		static final RangeMap DEFENCE_BB;

		static {
			// Ranges TAG comme référence optimale, via BotProfileFactory pour ne pas dupliquer le code
			BotProfile tagProfile = BotProfileFactory.createTightAggressive();
			OPEN_BTN = tagProfile.getRangeFor(Position.BTN);
			OPEN_CO = tagProfile.getRangeFor(Position.CO);
			OPEN_UTG = tagProfile.getRangeFor(Position.UTG);
			// Les blindes ont leurs propres fichiers : la SB ouvre large mais se défend
			// serré, la BB se défend très large grâce à la cote qu'elle a déjà.
			OPEN_SB = loadOrFallback("tag_sb.csv", OPEN_BTN);
			DEFENCE_SB = loadOrFallback("tag_sb_vs_open.csv", OPEN_UTG);
			DEFENCE_BB = loadOrFallback("tag_bb_vs_open.csv", OPEN_CO);
		}
	}

	private final MonteCarloEngine engine;

	//CONSTRUCTEUR
	public HandAnalyser() {
		this.engine = new MonteCarloEngine();
	}

	private static RangeMap loadOrFallback(String file, RangeMap fallback) {
		RangeMap loaded = RangeMapLoader.fromClasspathOrEmpty(RANGE_DIR + file);
		if (loaded.isEmpty()) {
			System.err.println("[HandAnalyser] Range " + file + " absente ou vide, repli sur une range générique.");
			return fallback;
		}
		return loaded;
	}

	//Analyse toutes les actions du héro d'une main
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

			// Le GameState d'index i est l'état APRÈS l'action i : l'état avant
			// l'action i est donc le GameState i-1 (aucun si i == 0).
			GameState stateBefore = (i > 0 && i - 1 < states.size()) ? states.get(i - 1) : null;

			ActionAdvice advice;
			try {
				ActionContext context = ActionContext.build(hand, action, stateBefore);
				if (action.getRound() == BettingRound.PREFLOP) {
					advice = analysePreflopAction(i, action, hand, context);
				} else {
					advice = analysePostflopAction(i, action, hand, context);
				}
			} catch (RuntimeException e) {
				// Une action abîmée ne doit pas faire perdre l'analyse de toute la main
				advice = unanalysable(i, action, "erreur interne : " + e);
			}

			if (advice != null) {
				advices.add(advice);
			}
		}

		return advices;
	}

	// ─────────────────────────────── PREFLOP ───────────────────────────────

	private ActionAdvice analysePreflopAction(int index, Action action, Hand hand, ActionContext ctx) {
		Player hero = action.getPlayer();
		List<Card> hole = holeCardsOf(hand, hero);
		if (hole.size() != 2) {
			return unanalysable(index, action,
				"cartes fermées introuvables pour " + hero.getName() + " (" + hole.size() + " carte(s) connue(s)).");
		}

		Card c1 = hole.get(0);
		Card c2 = hole.get(1);
		boolean suited = c1.getSuit() == c2.getSuit();
		Position pos = ctx.heroPosition;
		String handName = formatHand(c1, c2, suited);

		int raiseLevel = countPreflopRaisesBefore(hand, index);
		int limpers = countPreflopLimpersBefore(hand, index);
		int tier = premiumTier(c1, c2, suited);

		ActionType played = action.getType();
		// Montant total atteint par le coup : getAmount() est un delta, il faut y
		// rajouter ce que le héro avait déjà engagé sur la street (blinde comprise).
		int raiseTo = ctx.heroBet + action.getAmount();

		ActionType reference;
		int recommendedAmount;
		if (raiseLevel == 0) {
			reference = openRange(pos).getAction(c1.getRank(), c2.getRank(), suited);
			recommendedAmount = (int) Math.round((OPEN_SIZE_BB + limpers) * ctx.bigBlind);
		} else if (raiseLevel == 1) {
			reference = defenceRange(pos).getAction(c1.getRank(), c2.getRank(), suited);
			double mult = inPosition(pos) ? THREE_BET_IP_MULT : THREE_BET_OOP_MULT;
			recommendedAmount = (int) Math.round(ctx.betLevel * mult);
		} else if (raiseLevel == 2) {
			// Face à un 3-bet : seules les mains premium continuent
			reference = (tier >= 4) ? ActionType.RAISE : (tier >= 2) ? ActionType.CALL : ActionType.FOLD;
			recommendedAmount = (int) Math.round(ctx.betLevel * 2.3);
		} else {
			// Face à un 4-bet ou plus : AA/KK relancent, QQ/AK suivent, le reste passe
			reference = (tier >= 4) ? ActionType.RAISE : (tier >= 3) ? ActionType.CALL : ActionType.FOLD;
			recommendedAmount = ctx.heroStack + ctx.heroBet; // 5-bet = tapis
		}

		String context = preflopContext(ctx, raiseLevel, limpers);

		ActionAdvice.Builder builder = new ActionAdvice.Builder()
			.actionIndex(index)
			.playerName(hero.getName())
			.round(BettingRound.PREFLOP)
			.played(played)
			.playedAmount(action.getAmount())
			.recommended(reference)
			.recommendedAmount(recommendedAmount)
			.context(context)
			.equity(preflopEquity(hole, ctx, raiseLevel));

		String head = handName + " en " + pos + " (" + context + ") — ";

		// CHECK : l'option gratuite de la BB. Ce n'est pas un fold : le héro voit le
		// flop sans payer un jeton de plus, il ne renonce à rien d'autre qu'à l'initiative.
		if (played == ActionType.CHECK) {
			if (reference == ActionType.RAISE) {
				return builder.score(55)
					.reasoning(head + "vous prenez votre option gratuitement, ce qui est correct, "
						+ "mais cette main était assez forte pour relancer et isoler les limpeurs. "
						+ "Checker l'option laisse partir de la valeur.")
					.build();
			}
			return builder.score(100)
				.reasoning(head + "option prise gratuitement. Rien à payer, rien à risquer : "
					+ "c'est le bon réflexe avec une main qui ne mérite pas de relance.")
				.build();
		}

		// ALL_IN : jugé sur la profondeur de tapis, pas assimilé à une relance ordinaire
		if (played == ActionType.ALL_IN) {
			return analysePreflopShove(builder, head, ctx, reference, tier, raiseLevel);
		}

		ActionType normalized = (played == ActionType.BET) ? ActionType.RAISE : played;

		if (normalized == reference) {
			// Bonne décision : reste à juger le montant
			if (normalized == ActionType.RAISE) {
				return judgePreflopSizing(builder, head, raiseTo, recommendedAmount, ctx, raiseLevel);
			}
			return builder.score(100)
				.reasoning(head + "action conforme au jeu TAG optimal.")
				.build();
		}

		if (reference == ActionType.RAISE && normalized == ActionType.CALL) {
			String extra = (raiseLevel == 0)
				? "Le CALL est passif : vous ne prenez pas l'initiative et laissez les adversaires voir le flop à bon prix."
				: "Un 3-bet était préférable : suivre laisse entrer les blindes derrière vous et vous prive de l'initiative.";
			return builder.score(60).reasoning(head + "un RAISE était recommandé. " + extra).build();
		}
		if (reference == ActionType.RAISE && normalized == ActionType.FOLD) {
			return builder.score(25)
				.reasoning(head + "cette main est assez forte pour être relancée ici. "
					+ "Le FOLD fait perdre de la valeur attendue.")
				.build();
		}
		if (reference == ActionType.CALL && normalized == ActionType.FOLD) {
			return builder.score(50)
				.reasoning(head + "cette main méritait un CALL. "
					+ "Même si elle n'est pas assez forte pour relancer, elle a suffisamment de potentiel.")
				.build();
		}
		if (reference == ActionType.FOLD && normalized == ActionType.CALL) {
			String extra = (raiseLevel >= 2)
				? "Payer un 3-bet avec une main dominée est l'une des fuites les plus coûteuses du preflop."
				: "Le CALL preflop avec des mains marginales coûte cher sur le long terme.";
			return builder.score(35)
				.reasoning(head + "cette main est trop faible pour être jouée profitablement ici. " + extra)
				.build();
		}
		if (reference == ActionType.FOLD && normalized == ActionType.RAISE) {
			return builder.score(15)
				.reasoning(head + "main hors range dans ce spot. "
					+ "Relancer ici vous met dans des situations difficiles postflop, "
					+ "car vous serez souvent dominé par les mains adverses qui suivent.")
				.build();
		}
		if (reference == ActionType.CALL && normalized == ActionType.RAISE) {
			return builder.score(70)
				.reasoning(head + "le CALL suffisait selon le jeu TAG standard. "
					+ "Le RAISE n'est pas une erreur si vous avez un read, "
					+ "mais en général cette main n'a pas assez de force pour relancer ici.")
				.build();
		}
		return builder.score(80).reasoning(head + "action acceptable.").build();
	}

	//Juge le montant d'un open ou d'un 3-bet, une fois la décision reconnue correcte
	private ActionAdvice judgePreflopSizing(ActionAdvice.Builder builder, String head,
											int raiseTo, int recommended, ActionContext ctx, int raiseLevel) {
		if (recommended <= 0 || raiseTo <= 0) {
			return builder.score(100).reasoning(head + "action conforme au jeu TAG optimal.").build();
		}
		double ecart = (raiseTo - recommended) / (double) recommended;
		String taille = String.format("%.1f BB", raiseTo / (double) ctx.bigBlind);
		String cible = String.format("%.1f BB", recommended / (double) ctx.bigBlind);

		if (ecart < -SIZING_TOLERANCE) {
			return builder.score(80)
				.reasoning(head + "bonne décision, mais la relance est trop petite (" + taille
					+ " au lieu de " + cible + "). Une relance minimale donne une cote imbattable "
					+ "à tout le monde et n'élimine personne : vous jouez un gros pot multiway avec l'initiative pour seul avantage.")
				.build();
		}
		if (ecart > SIZING_TOLERANCE) {
			return builder.score(85)
				.reasoning(head + "bonne décision, mais la relance est surdimensionnée (" + taille
					+ " au lieu de " + cible + "). Vous n'êtes payé que par les mains qui vous battent "
					+ "et vous risquez trop pour un pot que personne ne conteste.")
				.build();
		}
		String suffixe = (raiseLevel == 0) ? "open" : "relance";
		return builder.score(100)
			.reasoning(head + "action conforme au jeu TAG optimal, " + suffixe + " bien dimensionné (" + taille + ").")
			.build();
	}

	//Un tapis preflop se juge à la profondeur : correct à moins de 20 BB, discutable au-delà
	private ActionAdvice analysePreflopShove(ActionAdvice.Builder builder, String head,
											 ActionContext ctx, ActionType reference, int tier, int raiseLevel) {
		double stackBB = ctx.stackInBigBlinds();
		String profondeur = String.format("%.0f BB", stackBB);

		if (reference == ActionType.FOLD) {
			return builder.score(10)
				.reasoning(head + "tapis avec une main hors range. "
					+ "Mettre " + profondeur + " en jeu sur une main que le jeu standard couche "
					+ "est la façon la plus rapide de perdre son tapis.")
				.build();
		}
		if (stackBB > 0 && stackBB <= SHOVE_MAX_BB) {
			return builder.score(95)
				.reasoning(head + "tapis à " + profondeur + " : à cette profondeur le tapis est "
					+ "la relance standard, il n'y a plus de place pour jouer postflop. Bien joué.")
				.build();
		}
		if (raiseLevel >= 3 && tier >= 4) {
			return builder.score(95)
				.reasoning(head + "tapis face à un 4-bet avec une main premium : "
					+ "c'est exactement le spot où mettre " + profondeur + " en jeu.")
				.build();
		}
		return builder.score(45)
			.reasoning(head + "tapis à " + profondeur + ". Avec un tapis aussi profond, "
				+ "le tapis n'est presque jamais nécessaire : vous ne serez payé que par mieux, "
				+ "et vous perdez toute la valeur des trois streets suivantes. Une relance normale suffisait.")
			.build();
	}

	// ─────────────────────────────── POSTFLOP ───────────────────────────────

	private ActionAdvice analysePostflopAction(int index, Action action, Hand hand, ActionContext ctx) {
		Player hero = action.getPlayer();
		List<Card> hole = holeCardsOf(hand, hero);
		if (hole.size() != 2) {
			return unanalysable(index, action,
				"cartes fermées introuvables pour " + hero.getName() + " (" + hole.size() + " carte(s) connue(s)).");
		}
		if (ctx.board.isEmpty()) {
			return unanalysable(index, action,
				"board inconnu au " + action.getRound() + " : la main enregistrée ne contient pas les cartes communes.");
		}

		double equity;
		try {
			MonteCarloEngine.EquityResult result =
				engine.calculateEquity(hole, ctx.board, MC_ITERATIONS, ctx.opponents);
			equity = result.equity() / 100.0;
		} catch (RuntimeException e) {
			return unanalysable(index, action, "équité incalculable : " + e.getMessage());
		}

		DrawAnalyser.Draws draws = DrawAnalyser.analyse(hole, ctx.board);
		ActionType played = action.getType();
		String role = streetRole(hand, index, action);
		String context = postflopContext(ctx, draws, role);

		ActionAdvice.Builder builder = new ActionAdvice.Builder()
			.actionIndex(index)
			.playerName(hero.getName())
			.round(action.getRound())
			.played(played)
			.playedAmount(action.getAmount())
			.equity(equity)
			.outs(draws.total())
			.spr(ctx.spr())
			.context(context);

		if (ctx.facingBet()) {
			return analyseFacingBet(builder, action, ctx, equity, draws, context);
		}
		return analyseNoBet(builder, action, ctx, equity, draws, context);
	}

	/**
	 * Face à une mise adverse : l'équité est comparée à la cote du pot.
	 * Un tirage change la lecture — payer 30 % d'équité brute est une erreur,
	 * payer 30 % avec neuf outs et de la profondeur derrière n'en est pas une.
	 */
	private ActionAdvice analyseFacingBet(ActionAdvice.Builder builder, Action action, ActionContext ctx,
										  double equity, DrawAnalyser.Draws draws, String context) {
		ActionType played = action.getType();
		int pot = ctx.totalPot;
		int callAmount = Math.max(1, ctx.callAmount());
		double breakEven = PotOddsCalculator.breakEvenEquity(pot, callAmount);
		double callEV = PotOddsCalculator.callEV(equity, pot, callAmount);
		double valueThreshold = valueThreshold(ctx.opponents);

		builder.potOdds(breakEven);

		String eqStr = pct(equity);
		String beStr = pct(breakEven);
		String evStr = String.format("%+.1f BB", callEV / ctx.bigBlind);
		String head = context + " — ";

		if (equity >= valueThreshold) {
			builder.recommended(ActionType.RAISE)
				   .recommendedAmount((int) Math.round((pot + callAmount) * 0.7));

			if (played == ActionType.RAISE || played == ActionType.ALL_IN) {
				return builder.score(100)
					.reasoning(head + "équité " + eqStr + " — RAISE pour value. "
						+ "Avec une main aussi forte, maximiser la valeur est optimal. Bien joué !")
					.build();
			}
			if (played == ActionType.CALL) {
				return builder.score(65)
					.reasoning(head + "équité " + eqStr + " — un RAISE pour value était meilleur qu'un simple CALL. "
						+ "Vous avez laissé de l'argent sur la table. "
						+ "La cote du pot (" + beStr + ") est largement en votre faveur (EV du call : " + evStr + ").")
					.build();
			}
			return builder.score(5)
				.evDelta(callEV / ctx.bigBlind)
				.reasoning(head + "équité " + eqStr + " — FOLD avec une main forte est une erreur majeure. "
					+ "La cote du pot demandait seulement " + beStr + " d'équité pour être profitable. "
					+ "EV perdue : " + evStr + ".")
				.build();
		}

		if (equity >= breakEven) {
			builder.recommended(ActionType.CALL);

			if (played == ActionType.CALL) {
				String tirage = draws.hasFlushDraw() || draws.hasOpenEnded()
					? " Votre " + draws.describe() + " (" + draws.total() + " outs) justifie de payer."
					: "";
				return builder.score(100)
					.reasoning(head + "équité " + eqStr + " vs cote requise " + beStr
						+ " — CALL correct et profitable (EV : " + evStr + ")." + tirage)
					.build();
			}
			if (played == ActionType.RAISE || played == ActionType.ALL_IN) {
				if (draws.total() >= 8) {
					double foldEquityNeeded = PotOddsCalculator.bluffBreakEvenFoldEquity(
						(ctx.heroBet + action.getAmount()) / (double) Math.max(1, pot));
					return builder.score(85)
						.reasoning(head + "équité " + eqStr + " — semi-bluff sur " + draws.describe()
							+ " (" + draws.total() + " outs). Relancer ajoute la fold equity à l'équité du tirage : "
							+ "il suffit que l'adversaire se couche " + pct(foldEquityNeeded)
							+ " du temps pour que la relance soit gagnante, même sans toucher.")
						.build();
				}
				return builder.score(70)
					.reasoning(head + "équité " + eqStr + " — le CALL suffisait (EV : " + evStr + "). "
						+ "Le RAISE augmente la variance sans avantage clair, "
						+ "sauf si vous avez un read spécifique sur l'adversaire.")
					.build();
			}
			return builder.score(20)
				.evDelta(callEV / ctx.bigBlind)
				.reasoning(head + "équité " + eqStr + " vs cote requise " + beStr
					+ " — le CALL était profitable (EV : " + evStr + "). "
					+ "Le FOLD ici fait perdre de la valeur attendue sur le long terme.")
				.build();
		}

		// Équité insuffisante pour payer
		builder.recommended(ActionType.FOLD);

		if (played == ActionType.FOLD) {
			return builder.score(100)
				.reasoning(head + "équité " + eqStr + " vs cote requise " + beStr
					+ " — FOLD correct. Le call aurait une EV négative (" + evStr + ").")
				.build();
		}
		if (played == ActionType.CALL) {
			String tirage = draws.total() >= 8
				? " Votre " + draws.describe() + " (" + draws.total() + " outs) ne suffit pas tout à fait à la cote offerte."
				: "";
			return builder.score(30)
				.evDelta(-callEV / ctx.bigBlind)
				.reasoning(head + "équité " + eqStr + " vs cote requise " + beStr
					+ " — le CALL n'est pas profitable ici (EV : " + evStr + ")." + tirage
					+ " Le FOLD préserve vos jetons pour de meilleurs spots.")
				.build();
		}
		// Relance sans équité : bluff sec ou semi-bluff selon les outs
		if (draws.total() >= 8) {
			double foldEquityNeeded = PotOddsCalculator.bluffBreakEvenFoldEquity(
				(ctx.heroBet + action.getAmount()) / (double) Math.max(1, pot));
			return builder.score(70)
				.reasoning(head + "équité " + eqStr + " — semi-bluff sur " + draws.describe()
					+ " (" + draws.total() + " outs). Ce n'est pas un bluff sec : même payé, "
					+ "vous gardez de quoi gagner. Il faut " + pct(foldEquityNeeded)
					+ " de folds pour que la relance soit rentable immédiatement.")
				.build();
		}
		return builder.score(25)
			.reasoning(head + "équité " + eqStr + " — RAISE en bluff sec, sans tirage derrière. "
				+ "Peut fonctionner si l'adversaire se couche souvent, "
				+ "mais avec cette équité le risque dépasse le gain attendu.")
			.build();
	}

	/**
	 * Personne n'a misé : le héro peut checker ou miser.
	 * Le sizing et le rôle du coup (c-bet, donk bet) font partie du jugement.
	 */
	private ActionAdvice analyseNoBet(ActionAdvice.Builder builder, Action action, ActionContext ctx,
									  double equity, DrawAnalyser.Draws draws, String context) {
		ActionType played = action.getType();
		int pot = Math.max(1, ctx.totalPot);
		double valueThreshold = valueThreshold(ctx.opponents);
		double mediumThreshold = mediumThreshold(ctx.opponents);
		int recommendedBet = (int) Math.round(pot * CBET_FRACTION);

		String eqStr = pct(equity);
		String head = context + " — ";
		boolean aggressive = played.isAgressive();
		double betFraction = action.getAmount() / (double) pot;

		if (equity >= valueThreshold) {
			builder.recommended(ActionType.BET).recommendedAmount(recommendedBet);

			if (aggressive) {
				return judgeBetSizing(builder, head, eqStr, recommendedBet, betFraction);
			}
			return builder.score(40)
				.reasoning(head + "équité " + eqStr + " — un BET pour value était recommandé. "
					+ "Le CHECK laisse l'adversaire voir la carte suivante gratuitement "
					+ "et vous empêche de construire le pot avec votre main forte.")
				.build();
		}

		if (equity >= mediumThreshold) {
			builder.recommended(ActionType.CHECK);

			if (played == ActionType.CHECK) {
				String sprNote = (ctx.spr() >= 0 && ctx.spr() < 3)
					? " Avec un SPR de " + String.format("%.1f", ctx.spr())
					  + ", le pot est déjà gros par rapport aux tapis : checker garde la main jouable."
					: "";
				return builder.score(90)
					.reasoning(head + "équité " + eqStr + " — CHECK pour contrôler la taille du pot. "
						+ "Avec une main moyenne, éviter de gonfler le pot est prudent." + sprNote)
					.build();
			}
			if (aggressive) {
				return builder.score(70)
					.reasoning(head + "équité " + eqStr + " — le BET est agressif mais défendable. "
						+ "Un CHECK aurait mieux contrôlé le pot avec une main de force moyenne. "
						+ "Le risque est de se faire relancer et de devoir abandonner.")
					.build();
			}
			return builder.score(100).reasoning(head + "équité " + eqStr + " — action acceptable.").build();
		}

		// Main faible
		builder.recommended(ActionType.CHECK);

		if (played == ActionType.CHECK) {
			return builder.score(100)
				.reasoning(head + "équité " + eqStr + " — CHECK correct avec une main faible. "
					+ "Pas de raison de miser sans équité suffisante.")
				.build();
		}
		if (aggressive) {
			if (draws.total() >= 8) {
				double foldEquityNeeded = PotOddsCalculator.bluffBreakEvenFoldEquity(betFraction);
				builder.recommended(ActionType.BET).recommendedAmount(recommendedBet);
				return builder.score(80)
					.reasoning(head + "équité " + eqStr + " — semi-bluff sur " + draws.describe()
						+ " (" + draws.total() + " outs). C'est le bon moment pour miser : vous gagnez "
						+ "soit tout de suite si l'adversaire se couche (il faut " + pct(foldEquityNeeded)
						+ " de folds), soit plus tard en touchant votre tirage.")
					.build();
			}
			double foldEquityNeeded = PotOddsCalculator.bluffBreakEvenFoldEquity(betFraction);
			return builder.score(50)
				.reasoning(head + "équité " + eqStr + " — bluff sec, sans tirage. "
					+ "Il faut que l'adversaire se couche " + pct(foldEquityNeeded)
					+ " du temps pour que cette mise soit rentable, et vous ne pouvez supporter "
					+ "ni un call ni un raise. À réserver aux boards qui favorisent votre range.")
				.build();
		}
		return builder.score(90).reasoning(head + "équité " + eqStr + " — action correcte.").build();
	}

	//Juge le montant d'une mise de value
	private ActionAdvice judgeBetSizing(ActionAdvice.Builder builder, String head, String eqStr,
										int recommended, double betFraction) {
		String taille = String.format("%.0f%% du pot", betFraction * 100);

		if (betFraction < 0.25) {
			return builder.score(75)
				.reasoning(head + "équité " + eqStr + " — bonne décision de miser, mais " + taille
					+ " est trop peu. Une mise aussi faible offre une cote imbattable aux tirages "
					+ "adverses et n'extrait presque rien de vos mains battues.")
				.build();
		}
		if (betFraction > 1.2) {
			return builder.score(80)
				.reasoning(head + "équité " + eqStr + " — overbet à " + taille + ". "
					+ "Ce sizing est justifié sur certains boards, mais il ne sera payé "
					+ "que par les mains qui vous battent : le gain moyen y est souvent inférieur "
					+ "à une mise de " + recommended + " jetons.")
				.build();
		}
		return builder.score(100)
			.reasoning(head + "équité " + eqStr + " — BET pour value à " + taille + ", bien joué. "
				+ "Avec cette force de main, miser extrait de la valeur des mains plus faibles.")
			.build();
	}

	// ─────────────────────────────── HELPERS ───────────────────────────────

	//Conseil marqué non analysable, avec le motif : ne fait jamais échouer l'analyse
	private static ActionAdvice unanalysable(int index, Action action, String motif) {
		return new ActionAdvice.Builder()
			.actionIndex(index)
			.playerName(action.getPlayer().getName())
			.round(action.getRound())
			.played(action.getType())
			.playedAmount(action.getAmount())
			.analysable(false)
			.score(0)
			.reasoning("Action non analysable — " + motif)
			.build();
	}

	//Équité minimale pour un value bet, relative à la part équitable 1/(n+1)
	private static double valueThreshold(int opponents) {
		return Math.min(0.95, fairShare(opponents) * VALUE_RATIO);
	}

	private static double mediumThreshold(int opponents) {
		return fairShare(opponents) * MEDIUM_RATIO;
	}

	private static double fairShare(int opponents) {
		return 1.0 / (Math.max(1, opponents) + 1.0);
	}

	/**
	 * Équité preflop. Face à une relance, l'adversaire n'a plus une main au hasard :
	 * on tire ses cartes dans une range TAG, ce qui donne un chiffre exploitable
	 * (AJo vaut 65 % contre une main aléatoire, mais bien moins contre un open range).
	 */
	private double preflopEquity(List<Card> hole, ActionContext ctx, int raiseLevel) {
		try {
			if (raiseLevel == 0) {
				return engine.calculateEquity(hole, Collections.emptyList(),
					MC_ITERATIONS_PREFLOP, ctx.opponents).equity() / 100.0;
			}
			RangeMap villainRange = (raiseLevel == 1) ? Ranges.OPEN_CO : Ranges.OPEN_UTG;
			return engine.calculateEquity(hole, Collections.emptyList(), MC_ITERATIONS_PREFLOP,
				ctx.opponents, villainRange.asVillainRange()).equity() / 100.0;
		} catch (RuntimeException e) {
			return -1;
		}
	}

	//Range d'ouverture (RFI) de la position
	private static RangeMap openRange(Position pos) {
		if (pos == null) return Ranges.OPEN_CO;
		switch (pos) {
			case BTN: return Ranges.OPEN_BTN;
			case SB:  return Ranges.OPEN_SB;
			case CO: case HJ: return Ranges.OPEN_CO;
			// La BB n'ouvre jamais : si l'action lui revient sans relance, la main est finie.
			// On lui donne la range du CO, la moins mauvaise référence pour un check d'option.
			case BB:  return Ranges.OPEN_CO;
			default:  return Ranges.OPEN_UTG; // UTG, UTG1, UTG2, MP, LJ
		}
	}

	//Range de défense face à un open — différente de la range d'ouverture
	private static RangeMap defenceRange(Position pos) {
		if (pos == null) return Ranges.OPEN_UTG;
		switch (pos) {
			case SB: return Ranges.DEFENCE_SB;
			case BB: return Ranges.DEFENCE_BB;
			// Ailleurs, se défendre face à un open demande au moins la range la plus
			// serrée : on utilise la range UTG comme référence de continuation.
			default: return Ranges.OPEN_UTG;
		}
	}

	private static boolean inPosition(Position pos) {
		return pos == Position.BTN || pos == Position.CO || pos == Position.HJ;
	}

	/**
	 * Force relative de la main de départ, pour les spots où aucune heatmap n'existe
	 * (face à un 3-bet ou à un 4-bet).
	 *   4 = AA, KK        3 = QQ, AK        2 = JJ, TT, AQs
	 *   1 = 99, AQo, AJs, KQs                0 = le reste
	 */
	private static int premiumTier(Card c1, Card c2, boolean suited) {
		int hi = Math.max(c1.getRank().getValue(), c2.getRank().getValue());
		int lo = Math.min(c1.getRank().getValue(), c2.getRank().getValue());
		boolean pair = hi == lo;

		if (pair) {
			if (hi >= Rank.KING.getValue()) return 4;
			if (hi == Rank.QUEEN.getValue()) return 3;
			if (hi >= Rank.TEN.getValue()) return 2;
			if (hi >= Rank.NINE.getValue()) return 1;
			return 0;
		}
		if (hi == Rank.ACE.getValue()) {
			if (lo == Rank.KING.getValue()) return 3;
			if (lo == Rank.QUEEN.getValue()) return suited ? 2 : 1;
			if (lo == Rank.JACK.getValue() && suited) return 1;
			return 0;
		}
		if (hi == Rank.KING.getValue() && lo == Rank.QUEEN.getValue() && suited) return 1;
		return 0;
	}

	//Nombre de relances preflop déjà portées avant cette action
	private static int countPreflopRaisesBefore(Hand hand, int index) {
		int raises = 0;
		List<Action> actions = hand.getActions();
		for (int i = 0; i < index && i < actions.size(); i++) {
			Action a = actions.get(i);
			if (a.getRound() == BettingRound.PREFLOP && a.getType().isAgressive()) {
				raises++;
			}
		}
		return raises;
	}

	//Nombre de limpeurs : joueurs ayant suivi la big blind avant toute relance
	private static int countPreflopLimpersBefore(Hand hand, int index) {
		int limpers = 0;
		List<Action> actions = hand.getActions();
		for (int i = 0; i < index && i < actions.size(); i++) {
			Action a = actions.get(i);
			if (a.getRound() != BettingRound.PREFLOP) continue;
			if (a.getType().isAgressive()) return limpers; // une relance clôt le limp
			if (a.getType() == ActionType.CALL) limpers++;
		}
		return limpers;
	}

	/**
	 * Rôle du coup dans la street : c-bet, donk bet, check-raise, ou rien de particulier.
	 * Ces trois situations se jugent différemment d'une mise ordinaire, et n'étaient
	 * jusqu'ici pas distinguées du tout.
	 */
	private static String streetRole(Hand hand, int index, Action action) {
		List<Action> actions = hand.getActions();
		BettingRound round = action.getRound();
		String heroId = action.getPlayer().getId();

		boolean firstAggression = true;
		boolean heroChecked = false;
		for (int i = 0; i < index && i < actions.size(); i++) {
			Action a = actions.get(i);
			if (a.getRound() != round) continue;
			if (a.getType().isAgressive()) firstAggression = false;
			if (a.getPlayer().getId().equals(heroId) && a.getType() == ActionType.CHECK) heroChecked = true;
		}

		String previousAggressor = lastAggressorBefore(hand, index, round);
		boolean heroWasAggressor = heroId.equals(previousAggressor);

		if (action.getType().isAgressive()) {
			if (heroChecked) return "check-raise";
			if (firstAggression && heroWasAggressor) return "c-bet";
			if (firstAggression && previousAggressor != null) return "donk bet";
			return null;
		}
		if (action.getType() == ActionType.CHECK && heroWasAggressor && firstAggression) {
			return "c-bet abandonné";
		}
		return null;
	}

	//Dernier joueur agressif d'une street ANTÉRIEURE à `round`
	private static String lastAggressorBefore(Hand hand, int index, BettingRound round) {
		String aggressor = null;
		List<Action> actions = hand.getActions();
		for (int i = 0; i < index && i < actions.size(); i++) {
			Action a = actions.get(i);
			if (a.getRound() == round) continue;
			if (a.getType().isAgressive()) aggressor = a.getPlayer().getId();
		}
		return aggressor;
	}

	private static String preflopContext(ActionContext ctx, int raiseLevel, int limpers) {
		StringBuilder sb = new StringBuilder();
		if (raiseLevel == 0) {
			sb.append(limpers == 0 ? "pot non ouvert"
				: limpers + (limpers > 1 ? " limpeurs" : " limpeur"));
		} else if (raiseLevel == 1) {
			sb.append(String.format("face à un open de %.1f BB", ctx.betLevel / (double) ctx.bigBlind));
		} else if (raiseLevel == 2) {
			sb.append("face à un 3-bet");
		} else {
			sb.append("face à un ").append(raiseLevel + 1).append("-bet");
		}
		if (ctx.opponents >= 2) {
			sb.append(", ").append(ctx.opponents).append(" adversaires");
		}
		if (ctx.stackInBigBlinds() > 0) {
			sb.append(String.format(", %.0f BB de tapis", ctx.stackInBigBlinds()));
		}
		return sb.toString();
	}

	private static String postflopContext(ActionContext ctx, DrawAnalyser.Draws draws, String role) {
		StringBuilder sb = new StringBuilder();
		sb.append(ctx.round);
		if (ctx.opponents >= 2) {
			sb.append(" multiway (").append(ctx.opponents).append(" adversaires)");
		}
		if (ctx.spr() >= 0) {
			sb.append(String.format(", SPR %.1f", ctx.spr()));
		}
		if (draws.total() > 0) {
			sb.append(", ").append(draws.describe());
		}
		if (role != null) {
			sb.append(", ").append(role);
		}
		return sb.toString();
	}

	/**
	 * Cartes fermées du héro AU MOMENT DE LA DISTRIBUTION.
	 * L'objet Player est muté d'une main à l'autre : on lit d'abord le snapshot initial,
	 * qui est le seul état fiable une fois la main terminée.
	 */
	private static List<Card> holeCardsOf(Hand hand, Player hero) {
		for (PlayerState ps : hand.getInitialStates()) {
			if (ps.getId().equals(hero.getId()) && ps.getHoleCards().size() == 2) {
				return ps.getHoleCards();
			}
		}
		return hero.getHoleCards();
	}

	private static String pct(double ratio) {
		return String.format("%.0f%%", ratio * 100);
	}

	//Formate une main pour l'affichage : "AKs", "QJo", "TT"
	private static String formatHand(Card c1, Card c2, boolean suited) {
		char r1 = c1.getRank().getSymbol();
		char r2 = c2.getRank().getSymbol();
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
