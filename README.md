# PokerBrain

  

Application de poker Texas Hold'em avec bots configurables, simulateur Monte Carlo, et analyseur de leaks.

  

## Fonctionnalités



### Jouer une partie

Affrontez 1 à 7 bots aux profils variés (TAG, Fish, LAG) autour d'une table de poker. 
Note: il faut choisir le nombre de bots avant de lancer (celui-ci ne s'affiche pas dans la case mais une fois sélectionné c'est bon).  

### Replay avec conseils

Rejouez une main pas-à-pas avec des conseils détaillés à chaque action : le HandAnalyser évalue chaque coup joué par rapport à la stratégie TAG optimale (preflop) et aux pot odds + équité MC (postflop). Chaque action reçoit un score sur 100 et une explication en français.

  

### Statistiques et détection de leaks

Consultez vos statistiques (VPIP, PFR, AF, WTSD, WSD, BB/100) et recevez des alertes automatiques sur les fuites détectées : VPIP trop élevé, PFR trop bas, jeu trop passif, etc.

### Calculateur d'équité

Saisissez votre main, celle de l'adversaire et éventuellement un board (flop, turn ou
river) : l'écran rend les probabilités de gagner, de partager et de perdre.

Le calcul est une **énumération exacte** de tous les boards restants (`EquityCalculator`),
pas une estimation : les chiffres affichés sont les vrais. L'évaluateur est celui du
showdown (`FastHandEvaluator`, classe canonique CactusKev 1–7462), donc l'équité annoncée
et le gagnant désigné en partie ne peuvent pas se contredire. Ordre de grandeur :
AA vs KK préflop, soit 1 712 304 boards, en moins d'une seconde.

Le Monte-Carlo (`MonteCarloEngine`) sert là où l'énumération est hors de portée : équité du
héro en cours de partie, décisions des bots, analyse du replay.

Le mode « contre une range » existe dans le moteur
(`MonteCarloEngine.calculateEquity(..., VillainRange)`, tirage par rejet dans une heatmap
13×13) mais n'est pas encore branché dans l'écran : voir `docs/TODO.md`.


## Prérequis


-  **Java 21** ou supérieur (OpenJDK ou Oracle JDK)

-  **JavaFX 21** (inclus dans le JDK ou ajouté via Maven/Gradle)

-  **Maven 3.8+** pour la compilation

  

## Installation et lancement

  

### Avec Maven

```bash

# Cloner le projet

git  clone git@gitlabense.imtbs-tsp.eu:projetdev/pokerbrain.git

cd  pokerbrain

# Compiler

mvn  clean  compile

 
# Lancer

mvn  javafx:run

```

  

### Avec IntelliJ IDEA

  

1. Importer le projet comme projet Maven

2. Configurer le JDK 21 dans Project Structure

3. Classe principale : `pokerbrain.MainApp`

4. Ajouter les VM options si nécessaire :

```

--module-path /path/to/javafx/lib --add-modules javafx.controls,javafx.fxml

```

  

### Avec Eclipse

  

1. Importer comme projet Maven existant

2. Clic droit → Run As → Java Application → `pokerbrain.MainApp`

3. Si erreur JavaFX, ajouter dans Run Configuration → Arguments → VM :

```

--module-path /path/to/javafx/lib --add-modules javafx.controls,javafx.fxml
```

 
  

