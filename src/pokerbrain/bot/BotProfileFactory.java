package pokerbrain.bot;

/*
 * Profile de bot prédéfini charger depuis les HeatMap CSV
 * 
 * BotProfile Agressif  = BotProfileFactory.createTightAggressive();
 * Bot bot1 = new Bot("bot-1", Agressif);
 * 
 * 
 * Pour creer un bot avec une heatmap personnalisee :
 * RangeMap maRange = RangeMapLoader.fromFile("mes_ranges/custom_btn.csv");
 * BotProfile custom = new BotProfile.Builder()
 * 		.name("Mon Bot")
 * 		.range(Position.BTN, maRange)
 * 		.aggressionFactor(2.5)
 * 		...
 * 		.build();
 */
public class BotProfileFactory {
	
	//COnstructeur vide
	private BotProfileFactory() {}
	
	//Profile AGRESSIF (TAG)
	//Heatmaps : ranges/tag_btn.csv, ranges/tag_co.csv, ranges/tag_utg.csv
	//VPIP=18%  PFR=15%  AF = 3.0
	public static BotProfile createTightAggressive() {
        return RangeMapLoader.loadProfileFromClasspath(
            "L'agressif (TAG)",
            "ranges/tag_btn.csv",
            "ranges/tag_co.csv",
            "ranges/tag_utg.csv",
            BotProfile.PostflopParams.tag()
        );
    }
	
	/**
     * Le passif (fish)
     * Heatmap : ranges/fish_all.csv (meme range toutes positions)
     * VPIP=55%  PFR=8%   AF=0.5
     */
    public static BotProfile createCallingStation() {
        return RangeMapLoader.loadProfileFromClasspath(
            "Le passif",
            "ranges/fish_all.csv",
            "ranges/fish_all.csv",
            "ranges/fish_all.csv",
            BotProfile.PostflopParams.fish()
        );
    }
    
    /**
     * LE perdant (LAG)
     * Heatmap : ranges/lag_all.csv (meme range toutes positions)
     * VPIP=38%  PFR=30%  AF=4.0
     */
    public static BotProfile createLooseAggressive() {
        return RangeMapLoader.loadProfileFromClasspath(
            "Le perdant",
            "ranges/lag_all.csv",
            "ranges/lag_all.csv",
            "ranges/lag_all.csv",
            BotProfile.PostflopParams.lag()
        );
    }


}
