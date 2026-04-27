package pokerbrain.bot;

import pokerbrain.model.core.Rank;
import pokerbrain.model.game.ActionType;

import java.io.*;
import java.net.URL;
import java.nio.file.*;
import java.util.*;

/**
 * Charge une RangeMap depuis un fichier CSV.
 *
 * FORMAT CSV :
 *   RANG_HAUT,RANG_BAS,TYPE,ACTION
 *
 *   RANG_HAUT / RANG_BAS : A K Q J T 9 8 7 6 5 4 3 2
 *   TYPE    : s=suited  o=offsuit  p=paire (RANG_BAS ignore pour p)
 *   ACTION  : R=raise   C=call     F=fold
 *
 *   Les lignes commencant par # sont des commentaires.
 *   Les lignes vides sont ignorees.
 *
 * EXEMPLE :
 *   A,K,s,R    -> AKs = RAISE
 *   A,K,o,R    -> AKo = RAISE
 *   A,A,p,R    -> AA  = RAISE (paire — RANG_BAS = A aussi par convention)
 *   9,8,s,C    -> 98s = CALL
 *   7,2,o,F    -> 72o = FOLD (ou absent = FOLD par defaut)
 *
 * USAGE :
 *   // Depuis le classpath (src/main/resources/ranges/)
 *   RangeMap map = RangeMapLoader.fromClasspath("ranges/tag_btn.csv");
 *
 *   // Depuis un chemin absolu (fichier utilisateur)
 *   RangeMap map = RangeMapLoader.fromFile(Paths.get("/mes/ranges/custom.csv"));
 *
 *   // Construire un BotProfile avec des fichiers differents par position
 *   BotProfile profil = RangeMapLoader.loadProfile("ranges/tag", ...);
 */
public final class RangeMapLoader {

    private RangeMapLoader() {}

    //Charge une RangeMap depuis le classpath.
    //Le fichier doit etre dans src/main/resources/.
    public static RangeMap fromClasspath(String resourcePath) throws IOException {
    	InputStream is = RangeMapLoader.class.getResourceAsStream("/" + resourcePath);
    	if (is == null) {
    		is = RangeMapLoader.class.getResourceAsStream(resourcePath);    	
    	}
    	if (is == null) {
    		URL url = RangeMapLoader.class.getClassLoader().getResource(resourcePath);
    		if (url != null) {
    			is = url.openStream();
    		}
    	}
        
    	if (is == null) {
            throw new FileNotFoundException("Ressource introuvable : " + resourcePath);
        }
    	try (BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
            return parse(br, resourcePath);
        }
    }

    //Chargement depuis le systeme de fichiers 
    //Charge une RangeMap depuis un chemin absolue ou relatif

    public static RangeMap fromFile(Path path) throws IOException {
        try (BufferedReader br = Files.newBufferedReader(path)) {
            return parse(br, path.toString());
        }
    }

    public static RangeMap fromFile(String path) throws IOException {
        return fromFile(Paths.get(path));
    }

    //Chargement silencieux (avec fallback)
    public static RangeMap fromClasspathOrEmpty(String resourcePath) {
        try {
            return fromClasspath(resourcePath);
        } catch (IOException e) {
            System.err.println("[RangeMapLoader] Fichier absent, RangeMap vide : " + resourcePath);
            return new RangeMap();
        }
    }

    //Parser CSV 

    private static RangeMap parse(BufferedReader br, String source) throws IOException {
        RangeMap map = new RangeMap();
        int lineNum = 0;

        String line;
        while ((line = br.readLine()) != null) {
            lineNum++;
            line = line.trim();

            // Ignorer commentaires et lignes vides
            if (line.isEmpty() || line.startsWith("#")) {
            	continue;
            }

            String[] parts = line.split(",");
            if (parts.length < 4) {
                throw new IOException(
                    "Ligne " + lineNum + " invalide (attendu 4 colonnes) dans " + source
                    + " : [" + line + "]");
            }

            try {
                Rank high = parseRank(parts[0].trim());
                Rank low = parseRank(parts[1].trim());
                String type = parts[2].trim().toLowerCase();
                ActionType act= parseAction(parts[3].trim());

                switch (type) {
                    case "p": 
                    	map.setPair(high, act);         
                    	break;
                    case "s": 
                    	map.setSuited(high, low, act);  
                    	break;
                    case "o": 
                    	map.setOffsuit(high, low, act); 
                    	break;
                    default:
                        throw new IOException(
                            "Type inconnu '" + type + "' ligne " + lineNum + " dans " + source);
                }
            } catch (IOException ioe) {
                throw ioe;
            } catch (Exception e) {
                throw new IOException(
                    "Erreur ligne " + lineNum + " dans " + source + " : " + e.getMessage(), e);
            }
        }
        return map;
    }

    // LISTe Helpers

    private static Rank parseRank(String s) {
        switch (s.toUpperCase()) {
            case "A":  
            	return Rank.ACE;
            case "K":  
            	return Rank.KING;
            case "Q":  
            	return Rank.QUEEN;
            case "J":  
            	return Rank.JACK;
            case "T":  
            	return Rank.TEN;
            case "9":  
            	return Rank.NINE;
            case "8":  
            	return Rank.EIGHT;
            case "7":  
            	return Rank.SEVEN;
            case "6":  
            	return Rank.SIX;
            case "5":  
            	return Rank.FIVE;
            case "4":  
            	return Rank.FOUR;
            case "3":  
            	return Rank.THREE;
            case "2":  
            	return Rank.TWO;
            default:
                throw new IllegalArgumentException("Rang inconnu : " + s);
        }
    }

    private static ActionType parseAction(String s) {
        switch (s.toUpperCase()) {
            case "R": 
            	return ActionType.RAISE;
            case "RAISE": 
            	return ActionType.RAISE;
            case "C": 
            	return ActionType.CALL;
            case "CALL":  
            	return ActionType.CALL;
            case "F": 
            	return ActionType.FOLD;
            case "FOLD":  
            	return ActionType.FOLD;
            default:
                throw new IllegalArgumentException("Action inconnue : " + s);
        }
    }

    //Construction de profil complet depuis des fichiers 

    /**
     * Charge un BotProfile complet en associant une RangeMap par position.
     * Utilise fromClasspathOrEmpty — les positions sans fichier jouent FOLD.
     */
    public static BotProfile loadProfileFromClasspath(
            String name,
            String btnPath, String coPath, String utgPath,
            BotProfile.PostflopParams params) {

        RangeMap btn = fromClasspathOrEmpty(btnPath);
        RangeMap co  = fromClasspathOrEmpty(coPath);
        RangeMap utg = fromClasspathOrEmpty(utgPath);

        return new BotProfile.Builder()
            .name(name)
            // LATE POSITIONS
            .range(pokerbrain.model.game.Position.BTN,  btn)
            // CORRECTION CRITIQUE : Fallback pour que le bot joue dans les blindes
            .range(pokerbrain.model.game.Position.SB,   btn) 
            .range(pokerbrain.model.game.Position.BB,   co)  
            // MIDDLE POSITIONS
            .range(pokerbrain.model.game.Position.CO,   co)
            .range(pokerbrain.model.game.Position.HJ,   co)
            // EARLY POSITIONS
            .range(pokerbrain.model.game.Position.LJ,   utg)
            .range(pokerbrain.model.game.Position.MP,   utg)
            .range(pokerbrain.model.game.Position.UTG,  utg)
            .range(pokerbrain.model.game.Position.UTG1, utg)
            .range(pokerbrain.model.game.Position.UTG2, utg)
            
            // CORRECTION ARCHITECTURALE : Utilisation de l'objet de transfert
            .applyPostflopParams(params)
            .build();
    }
}
