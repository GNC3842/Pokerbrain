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
        currentRecord = new HandRecord(game, "hero");
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

    public Hand buildAndCloseHand() {
        return buildAndCloseHand(null);
    }

    public Action decideBotAction(Bot bot, Player current) {
        int activeOpp = (int) game.getPlayers().stream()
            .filter(p -> !p.isFolded() && !p.isOut() && !p.getId().equals(current.getId()))
            .count();
        return bot.decide(game.getCurrentRound(), current, new ArrayList<>(game.getBoard()),
            game.getTotalPot(), game.getCurrentBetLevel(), bigBlind, game.getValidActions(), activeOpp);
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
