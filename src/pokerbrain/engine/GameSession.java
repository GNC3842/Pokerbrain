package pokerbrain.engine;

import pokerbrain.bot.Bot;
import pokerbrain.model.game.*;
import pokerbrain.model.history.Hand;
import pokerbrain.model.history.HandRecord;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Encapsule l'état d'une partie en cours : PokerGame, HandRecord et bots.
 * GameController délègue toute la logique métier à cette classe.
 */
public final class GameSession {

    private final PokerGame game;
    private final Map<String, Bot> botsByPlayerId;
    private final int heroPlayerIndex;
    private final int bigBlind;
    private HandRecord currentRecord;

    public GameSession(List<Player> players, int heroIndex, List<Bot> bots, int sb, int bb) {
        this.game = new PokerGame(players, sb, bb);
        this.heroPlayerIndex = heroIndex;
        this.bigBlind = bb;
        this.botsByPlayerId = new HashMap<>();
        for (Bot b : bots) botsByPlayerId.put(b.getPlayerId(), b);
    }

    public void startNewHand() {
        game.startNewHand();
        // L'id du héro vient du siège choisi, jamais de la constante "hero" : avec un autre
        // id, la main enregistrée portait un heroId introuvable et HandAnalyser rendait une
        // liste de conseils vide, sans erreur.
        currentRecord = new HandRecord(game, game.getPlayers().get(heroPlayerIndex).getId());
    }

    public Action applyAction(ActionType type, int amount) {
        Action result = game.applyAction(type, amount);
        if (currentRecord != null) currentRecord.recordAction(result);
        return result;
    }

    public Action applyAction(ActionType type) {
        return applyAction(type, 0);
    }

    public Hand buildAndCloseHand(Map<Player, Integer> winnings) {
        if (currentRecord == null) return null;
        Hand hand = currentRecord.buildHand(winnings);
        currentRecord = null;
        return hand;
    }

    /**
     * Clôt la main en reprenant les gains que le moteur a réellement distribués.
     * Appelée sur les fins anticipées (fold général, tous à tapis), où l'appelant n'a
     * pas de map à fournir : passer `null` enregistrait la main sans aucun gain.
     */
    public Hand buildAndCloseHand() {
        return buildAndCloseHand(game.getWinnings());
    }

    public Action decideBotAction(Bot bot, Player current) {
        int activeOpp = (int) game.getPlayers().stream()
            .filter(p -> !p.isFolded() && !p.isOut() && !p.getId().equals(current.getId()))
            .count();
        Action decision = bot.decide(game.getCurrentRound(), current,
            new ArrayList<>(game.getBoard()), game.getTotalPot(), game.getCurrentBetLevel(),
            bigBlind, game.getValidActions(), activeOpp);
        return clampBetAmount(decision, current);
    }

    /**
     * Ramène le montant d'une mise de bot dans l'intervalle légal
     * [`getMinRaiseTo()`, mise engagée + tapis] — un total à atteindre sur la street.
     *
     * Le bot dimensionne au pot (`totalPot * 0.55 + currentBetLevel`) et passe sous le
     * minimum de relance dès qu'un adversaire a surmisé. `BettingRoundManager` levait,
     * l'exception était avalée par `GameController.applyBotAction` qui rappelait
     * `processTurn()` sans que le bot ait agi : même joueur, même décision déterministe,
     * la partie tournait en rond. Un seul point de bornage ici couvre tous les profils.
     */
    private Action clampBetAmount(Action decision, Player bot) {
        if (decision.getType() != ActionType.BET && decision.getType() != ActionType.RAISE)
            return decision;
        int min = game.getMinRaiseTo();
        int max = bot.getCurrentBet() + bot.getStack();
        int bounded = Math.max(min, Math.min(decision.getAmount(), max));
        if (bounded == decision.getAmount()) return decision;
        return new Action(bot, decision.getType(), bounded, decision.getRound());
    }

    public int countActiveOpponents() {
        String heroId = game.getPlayers().get(heroPlayerIndex).getId();
        return (int) game.getPlayers().stream()
            .filter(p -> !p.isFolded() && !p.isOut() && !p.getId().equals(heroId))
            .count();
    }

    public PokerGame getGame()                      { return game; }
    public int getHeroPlayerIndex()                 { return heroPlayerIndex; }
    public boolean isBot(String playerId)           { return botsByPlayerId.containsKey(playerId); }
    public Optional<Bot> getBot(String playerId)    { return Optional.ofNullable(botsByPlayerId.get(playerId)); }
}
