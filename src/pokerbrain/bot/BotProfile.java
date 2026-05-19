package pokerbrain.bot;

import pokerbrain.model.game.Position;
import java.util.*;

/**
 * Profil de comportement d'un bot.
 * Contient les paramètres qui définissent le style de jeu.
 *
 * Immuable une fois construit via Builder.
 */

public final class BotProfile {
	private final String name;
	
	// Ranges preflop par position (une matrice 13×13 par position)
    private final Map<Position, RangeMap> preflopRanges;
    
    //Paramètres postflop
    private final double aggressionFactor;   // 0.5 (passif) -> 4.0 (agressif)
    private final double cBetFrequency;      // 0.0 -> 1.0
    private final double bluffFrequency;     // 0.0 -> 0.3
    private final double foldToRaisePercent; // 0.0 (jamais fold) -> 1.0 (tjs fold)
    
    //Taille
    private final double openRaiseMultiplier; // ex: 2.5 → open à 2.5×BB
    private final double cBetSizingFraction;  // fraction du pot (ex: 0.6 = 60% pot)
    
    //Constructeur du profile
    public BotProfile(Builder b) {
    	this.name               = b.name;
        this.preflopRanges      = Collections.unmodifiableMap(new EnumMap<>(b.preflopRanges));
        this.aggressionFactor   = b.aggressionFactor;
        this.cBetFrequency      = b.cBetFrequency;
        this.bluffFrequency     = b.bluffFrequency;
        this.foldToRaisePercent = b.foldToRaisePercent;
        this.openRaiseMultiplier= b.openRaiseMultiplier;
        this.cBetSizingFraction = b.cBetSizingFraction;
    }
    
    //Liste des getters
    public String getName() {
    	return this.name;
    }
    public double getAggressionFactor() {
    	return this.aggressionFactor;
    }
    public double getCBetFrequency() {
    	return this.cBetFrequency;
    }
    public double getBluffFrequency() {
    	return this.bluffFrequency;
    }
    public double getFoldToRaisePercent() {
    	return this.foldToRaisePercent;
    }
    public double getOpenRaiseMultiplier() {
    	return this.openRaiseMultiplier;
    }
    public double getCBetSizingFraction() {
    	return this.cBetSizingFraction;
    }
    
    // ── PostflopParams — regroupement pour le chargement depuis fichier ───────

    /**
     * Paramètres postflop regroupes pour faciliter le chargement par RangeMapLoader.
     * Permet de specifier les parametres comportementaux independamment des heatmaps.
     */
    public static final class PostflopParams {
        public final double aggressionFactor;
        public final double cBetFrequency;
        public final double bluffFrequency;
        public final double foldToRaisePercent;
        public final double openRaiseMultiplier;
        public final double cBetSizingFraction;

        public PostflopParams(double aggressionFactor, double cBetFrequency,
                              double bluffFrequency, double foldToRaisePercent,
                              double openRaiseMultiplier, double cBetSizingFraction) {
            this.aggressionFactor = aggressionFactor;
            this.cBetFrequency = cBetFrequency;
            this.bluffFrequency = bluffFrequency;
            this.foldToRaisePercent = foldToRaisePercent;
            this.openRaiseMultiplier= openRaiseMultiplier;
            this.cBetSizingFraction = cBetSizingFraction;
        }

        /** Paramètres agression standard. */
        public static PostflopParams tag() {
            return new PostflopParams(3.0, 0.65, 0.08, 0.60, 2.5, 0.55);
        }
        /** Paramètres passif standard. */
        public static PostflopParams fish() {
            return new PostflopParams(0.5, 0.20, 0.02, 0.15, 3.0, 0.50);
        }
        /** Paramètres Loose standard. */
        public static PostflopParams lag() {
        	return new PostflopParams(3.0, 0.60, 0.15, 0.40, 2.2, 0.55);
        }
    }
    
    //Renvoie la rnage preflop pour une certaine position
    public RangeMap getRangeFor(Position position) {
        return preflopRanges.getOrDefault(position, RangeMap.empty());
    }

    @Override
    public String toString() {
        return "BotProfile[" + name
             + " AF=" + aggressionFactor
             + " cBet=" + String.format("%.0f%%", cBetFrequency * 100)
             + "]";
    }
    
    //BUilder
    public static final class Builder {
        private String name = "Bot";
        private Map<Position, RangeMap> preflopRanges = new EnumMap<>(Position.class);
        private double aggressionFactor = 2.0;
        private double cBetFrequency = 0.6;
        private double bluffFrequency = 0.1;
        private double foldToRaisePercent = 0.5;
        private double openRaiseMultiplier= 2.5;
        private double cBetSizingFraction = 0.6;

        public Builder name(String v){ 
        	this.name = v; 
        	return this; 
        }
        public Builder aggressionFactor(double v){ 
        	this.aggressionFactor = v; 
        	return this; 
        }
        public Builder cBetFrequency(double v){ 
        	this.cBetFrequency = v; 
        	return this; 
        }
        public Builder bluffFrequency(double v){ 
        	this.bluffFrequency = v; 
        	return this; 
        }
        public Builder foldToRaisePercent(double v){ 
        	this.foldToRaisePercent = v; 
        return this; }
        public Builder openRaiseMultiplier(double v){ 
        	this.openRaiseMultiplier= v; 
        	return this; 
        }
        public Builder cBetSizingFraction(double v) { 
        	this.cBetSizingFraction = v; 
        	return this; 
        }
        public Builder range(Position pos, RangeMap map) {
            this.preflopRanges.put(pos, map); 
            return this;
        }
        
        public Builder applyPostflopParams(PostflopParams params) {
            this.aggressionFactor   = params.aggressionFactor;
            this.cBetFrequency      = params.cBetFrequency;
            this.bluffFrequency     = params.bluffFrequency;
            this.foldToRaisePercent = params.foldToRaisePercent;
            this.openRaiseMultiplier= params.openRaiseMultiplier;
            this.cBetSizingFraction = params.cBetSizingFraction;
            return this;
        }

        public BotProfile build(){ 
        	return new BotProfile(this); 
        }
    }
    
    
}