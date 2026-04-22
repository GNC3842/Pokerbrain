package pokerbrain.bot;

/**
 * Profiles de bot prédéfinis chargés depuis les HeatMap CSV.
 *
 * Les fichiers CSV sont dans le package pokerbrain/bot/ranges/ sur le classpath.
 *
 * USAGE :
 *   BotProfile tag = BotProfileFactory.createTightAggressive();
 *   Bot bot = new Bot("bot-0", tag, AppContext.getEngine());
 */
public final class BotProfileFactory {

    private static final String BASE = "pokerbrain/bot/ranges/";

    private BotProfileFactory() {}

    //TAG - L'agressif
    public static BotProfile createTightAggressive() {
        return RangeMapLoader.loadProfileFromClasspath(
            "L'agressif (TAG)",
            BASE + "tag_btn.csv",
            BASE + "tag_co.csv",
            BASE + "tag_utg.csv",
            BotProfile.PostflopParams.tag()
        );
    }

    //Fish - Le passif
    public static BotProfile createCallingStation() {
        return RangeMapLoader.loadProfileFromClasspath(
            "Le passif",
            BASE + "fish_all.csv",
            BASE + "fish_all.csv",
            BASE + "fish_all.csv",
            BotProfile.PostflopParams.fish()
        );
    }

    //LAG - Le perdant 
    public static BotProfile createLooseAggressive() {
        return RangeMapLoader.loadProfileFromClasspath(
            "Le perdant",
            BASE + "lag_all.csv",
            BASE + "lag_all.csv",
            BASE + "lag_all.csv",
            BotProfile.PostflopParams.lag()
        );
    }
}
