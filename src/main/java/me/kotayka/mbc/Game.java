package me.kotayka.mbc;

import me.kotayka.mbc.gamePlayers.GamePlayer;
import me.kotayka.mbc.games.Lobby;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

/**
 * @see Minigame for non-point scoring games
 */
public abstract class Game extends Minigame {
    // TRUE = pvp is on ; FALSE = pvp is off
    private boolean PVP_ENABLED = false;

    public SortedMap<Participant, Integer> gameIndividual = new TreeMap<>(Collections.reverseOrder());

    public Game(String gameName) {
        super(gameName);
    }

    public List<GamePlayer> gamePlayers = new ArrayList<GamePlayer>();

    public List<Participant> playersAlive = new ArrayList<>();
    public List<MBCTeam> teamsAlive = new ArrayList<>();


    public void createScoreboard() {
        for (Participant p : MBC.getInstance().players) {
            newObjective(p);
            createScoreboard(p);
        }
    }


    /**
     * Provide game explanation
     */
    //public abstract void Introduction();


    public void addPlayerScore(Participant p, int score) {

    }

    public void addTeamScore(Participant p, int score) {

    }

    public void addTeamScore(int score) {

    }

    /**
     * Handles kill graphics for both killed player and killer
     * Does not handle void kills (death messages will not be descriptive).
     * Separate functions should be made to handle void kills:
     * and as such, Skybattle has a completely separate implementation.
     * Should work for any other PVP game not including the void.
     * Checks if one team remains, and adjusts timeRemaining if necessary.
     * Removes team from teamsAlive.
     * Does not handle scoring.
     * @param e Event thrown when a player dies
     */
    public void playerDeathEffects(PlayerDeathEvent e) {
        Participant victim = Participant.getParticipant(e.getPlayer());
        Participant killer = Participant.getParticipant(e.getPlayer().getKiller());
        String deathMessage = e.getDeathMessage();

        if (victim == null) return;

        victim.getPlayer().sendMessage(ChatColor.RED+"You died!");
        victim.getPlayer().sendTitle(" ", ChatColor.RED+"You died!", 0, 60, 30);
        MBC.spawnFirework(victim);
        deathMessage = deathMessage.replace(victim.getPlayerName(), victim.getFormattedName());

        if (killer != null) {
            killer.getPlayer().sendMessage(ChatColor.GREEN+"You killed " + victim.getPlayerName() + "!");
            killer.getPlayer().sendTitle(" ", "[" + ChatColor.BLUE + "x" + ChatColor.RESET + "] " + victim.getFormattedName(), 0, 60, 20);
            deathMessage = deathMessage.replace(killer.getPlayerName(), killer.getFormattedName());
        }

        e.setDeathMessage(deathMessage);

        // Check if only one team remains
        updatePlayersAlive(victim);
    }

    /**
     * Variant of playerDeathEffects() but where the death message is displayed as:
     * {Player A} was slain by {Player B} [♥ Player 2 Health]
     * This is to resemble Minecraft Monday.
     */
    public void deathEffectsWithHealth(PlayerDeathEvent e) {
        Participant victim = Participant.getParticipant(e.getPlayer());
        Participant killer = Participant.getParticipant(e.getPlayer().getKiller());
        String deathMessage = e.getDeathMessage();

        if (victim == null) return;

        victim.getPlayer().sendMessage(ChatColor.RED+"You died!");
        victim.getPlayer().sendTitle(" ", ChatColor.RED+"You died!", 0, 60, 30);
        deathMessage = deathMessage.replace(victim.getPlayerName(), victim.getFormattedName());

        if (killer != null) {
            killer.getPlayer().sendMessage(ChatColor.GREEN+"You killed " + victim.getPlayerName() + "!");
            killer.getPlayer().sendTitle(" ", "[" + ChatColor.BLUE + "x" + ChatColor.RESET + "] " + victim.getFormattedName(), 0, 60, 20);
            String health = String.format("["+ChatColor.RED+"♥ %.2f"+ChatColor.RESET+"]", killer.getPlayer().getHealth());
            deathMessage = deathMessage.replace(killer.getPlayerName(), killer.getFormattedName()+health);
        }

        e.setDeathMessage(deathMessage);

        // Check if only one team remains
        updatePlayersAlive(victim);
    }

    /**
     * Removes player from playersAlive list
     * WIP: likely needs more docs, this is kind of important
     * Optional: Additional restructure TBD
     * @param p Participant to be removed
     */
    public void updatePlayersAlive(Participant p) {
        playersAlive.remove(p);

        checkLastTeam(p.getTeam());
    }

    /**
     * If only one team remains,
     * @param t
     */
    private void checkLastTeam(MBCTeam t) {
        if (checkTeamEliminated(t)) {
            teamsAlive.remove(t);
            t.announceTeamDeath();

            if (teamsAlive.size() <= 1) {
                timeRemaining = 1;
            }
        }
    }

    public boolean checkIfDead(Participant p) {
        return !playersAlive.contains(p);
    }

    public boolean checkIfAlive(Participant p) {
        return playersAlive.contains(p);
    }

    /**
     * Resets both playersAlive and teamsAlive;
     * Best used for multi-round survival-type games
     * Might need a better name
     */
    public void resetAliveLists() {
        playersAlive.clear();
        teamsAlive.clear();

        playersAlive.addAll(MBC.getInstance().getPlayers());
        teamsAlive.addAll(getValidTeams());
    }

    /**
     * Makes use of teamsAlive and playersAlive lists.
     * @return true if the team has been fully eliminated (there are no players on that team alive).
     * @see Game checkIfDead
     */
    public boolean checkTeamEliminated(MBCTeam team) {
        int deadPlayers = 0;
        for (Participant p : team.teamPlayers) {
            if (checkIfDead(p)) {
                deadPlayers++;
            }
        }

        return deadPlayers == team.teamPlayers.size();
    }

    /**
     * Updates each player's scoreboards to match that of playersAlive and teamsAlive.
     * Does not do any computation and assumes playersAlive and teamsAlive has been properly
     * computed by the function call.
     *
     * Standardizes the following:
     *  Players Remaining is on line 2
     *  Teams Remaining is on line 1
     *
     *  Loops for all players by default.
     */
    public void updatePlayersAliveScoreboard() {
        createLine(2, ChatColor.GREEN+""+ChatColor.BOLD+"Players Remaining: " + ChatColor.RESET+playersAlive.size()+"/"+MBC.MAX_PLAYERS);
        createLine(1, ChatColor.GREEN+""+ChatColor.BOLD+"Teams Remaining: " + ChatColor.RESET+teamsAlive.size()+"/"+MBC.MAX_TEAMS);
    }

    /**
     * Updates each player's scoreboards to match that of playersAlive and teamsAlive.
     * Does not do any computation and assumes playersAlive and teamsAlive has been properly
     * computed by the function call.
     *
     * Standardizes the following:
     *  Players Remaining is on line 2
     *  Teams Remaining is on line 1
     *
     * @param p Specific participant to update scoreboard
     */
    public void updatePlayersAliveScoreboard(Participant p) {
        createLine(2, ChatColor.GREEN+""+ChatColor.BOLD+"Players Remaining: " + ChatColor.RESET+playersAlive.size()+"/"+MBC.MAX_PLAYERS, p);
        createLine(1, ChatColor.GREEN+""+ChatColor.BOLD+"Teams Remaining: " + ChatColor.RESET+teamsAlive.size()+"/"+MBC.MAX_TEAMS, p);
    }

    /**
     * Updates the player's current coin count in-game on the scoreboard
     * Your Coins: {COIN_AMOUNT}
     * @param p Participant whose scoreboard to update
     */
    public void updatePlayerCurrentScoreDisplay(Participant p) {
        createLine(1, ChatColor.YELLOW+"Your Coins: "+ChatColor.WHITE+p.getMultipliedCurrentScore(), p);
    }

    public String getPlace(int place) {
        int temp = place;
        if (temp > 10) {
            temp %= 10;
        }

        switch (temp) {
            case 1:
                return "1st";
            case 2:
                return "2nd";
            case 3:
                return "3rd";
            default:
                return place+"th";
        }
    }

    /**
     * General formatting function to get winners of a round and broadcast text.
     * Does not handle scoring.
     */
    public void roundWinners() {
        if (playersAlive.size() > 1) {
            StringBuilder survivors = new StringBuilder("The winners of this round are: ");
            for (int i = 0; i < playersAlive.size(); i++) {
                winEffects(playersAlive.get(i));
                playersAlive.get(i).getPlayer().sendMessage(ChatColor.GREEN+"You survived the round!");

                if (i == playersAlive.size()-1) {
                    survivors.append("and ").append(playersAlive.get(i).getFormattedName());
                } else {
                    survivors.append(playersAlive.get(i).getFormattedName()).append(", ");
                }
            }
            Bukkit.broadcastMessage(survivors.toString()+ChatColor.WHITE+"!");
        } else if (playersAlive.size() == 1) {
            playersAlive.get(0).getPlayer().sendMessage(ChatColor.GREEN+"You survived the round!");
            winEffects(playersAlive.get(0));
            Bukkit.broadcastMessage("The winner of this round is " + playersAlive.get(0).getFormattedName()+"!");
        } else {
            Bukkit.broadcastMessage("Nobody survived the round.");
        }
    }

    public ChatColor getPlacementColor(int place) {
        ChatColor placementColor;
        placementColor = switch (place) {
            case 1 -> ChatColor.GOLD;
            case 2 -> ChatColor.GRAY;
            case 3 -> ChatColor.getByChar("#CD7F32"); // idk if this works yet tbh
            default -> ChatColor.YELLOW;
        };
        return placementColor;
    }

   public void start() {
        super.start();
   }

   /**
    * Called at the end of games
    * Handles formatting and reveals of team and individual scores
    *
    * NOTE: games with additional information, (ex: Ace Race and lap times)
    * must implement a separate function for those stats.
    */
    public void gameEndEvents() {
        // GAME_END should have 35 seconds by default
        switch(timeRemaining) {
            case 30:
                Bukkit.broadcastMessage(ChatColor.BOLD+"Each team scored this game:");
                break;
            case 28:
                printRoundScores();
                break;
            case 22:
                Bukkit.broadcastMessage(ChatColor.BOLD+"Top 5 players this game:");
                break;
            case 20:
                getScores();
                break;
            case 14:
                Bukkit.broadcastMessage(ChatColor.BOLD+"Current event standings:");
                break;
            case 12:
                printEventScores();
                break;
            case 1:
                Bukkit.broadcastMessage(ChatColor.RED + "Returning to lobby...");
                break;
            case 0:
                returnToLobby();
                break;
        }
    }

    /**
     * Prints out top 5 players (or more if there is a tie).
     * Calls addRoundScoreToGame() to load stats from this game to overall MBC.
     * @see Participant addRoundScoreToGame()
     */
    public void getScores() {
        int num = 0; // separate var incase there is an absurd amount of ties
        StringBuilder topFive = new StringBuilder();
        int lastScore = -1;
        int counter = 0;

        for (Participant p : MBC.getInstance().getPlayers()) {
            gameIndividual.put(p, p.getRawCurrentScore());
        }

        for (Participant p : gameIndividual.keySet()) {
            if (p.getRawCurrentScore() != lastScore) {
                num++;
                if (counter == 4) {
                    counter--; // keep going until no ties
                }
            }

            if (counter < 5) {
                topFive.append(String.format(
                        (num) + ". %-18s %5d (%d x %.2f)\n", p.getFormattedName(), p.getRawCurrentScore(), p.getMultipliedCurrentScore(), MBC.getInstance().multiplier)
                );
                lastScore = p.getRawCurrentScore();
                counter++;
            }
            p.addCurrentScoreToTotal();
        }
        Bukkit.broadcastMessage(topFive.toString());
    }

    public void printEventScores() {
        List<MBCTeam> gameScores = new ArrayList<>(getValidTeams());
        gameScores.sort(new TeamScoreSorter());
        Collections.reverse(gameScores);
        StringBuilder str = new StringBuilder();

        for (int i = 0; i < gameScores.size(); i++) {
            str.append(String.format(
                    ChatColor.BOLD + "" + (i+1) + ChatColor.RESET + ". %-18s %5d\n",
                    gameScores.get(i).teamNameFormat(), gameScores.get(i).getMultipliedTotalScore())
            );
        }
        Bukkit.broadcastMessage(str.toString());
    }

    public void printRoundScores() {
        List<MBCTeam> teamRoundsScores = new ArrayList<>(getValidTeams());
        teamRoundsScores.sort(new TeamRoundSorter());
        Collections.reverse(teamRoundsScores);
        StringBuilder teamString = new StringBuilder();

        for (int i = 0; i < teamRoundsScores.size(); i++) {
            teamString.append(String.format(
                    ChatColor.BOLD + "" + (i+1) + ChatColor.RESET + ". %-18s %5d\n",
                    teamRoundsScores.get(i).teamNameFormat(), teamRoundsScores.get(i).getMultipliedCurrentScore())
            );
        }

        Bukkit.broadcastMessage(teamString.toString());
    }

    public void returnToLobby() {
        HandlerList.unregisterAll(this);    // game specific listeners are only active when game is
        MBC.getInstance().plugin.getServer().getPluginManager().registerEvents(MBC.getInstance().lobby, MBC.getInstance().plugin);
        for (Participant p : MBC.getInstance().getPlayers()) {
            if (p.getPlayer().getAllowFlight()) {
                removeWinEffect(p);
            }
            p.getPlayer().removePotionEffect(PotionEffectType.DAMAGE_RESISTANCE);
            p.getPlayer().addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 100000, 10, false, false));
            p.getPlayer().getInventory().clear();
            p.getPlayer().setExp(0);
            p.getPlayer().setLevel(0);

            updatePlayerCurrentScoreDisplay(p);
            p.resetCurrentScores();
            p.getPlayer().teleport(Lobby.LOBBY);
        }
        MBC.getInstance().lobby.start();
    }

    /**
     * Graphics for counting down when a game is about to start.
     * Should only be called when gameState() is GameState.STARTING
     * since it directly uses timeRemaining
     * Does not handle events for when timer hits 0 (countdown finishes).
     */
    public void startingCountdown() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (timeRemaining <= 10 && timeRemaining > 3) {
                p.sendTitle(ChatColor.AQUA + "Starting in:", ChatColor.BOLD + ">"+timeRemaining+"<", 0,20,0);
            } else if (timeRemaining == 3) {
                p.sendTitle(ChatColor.AQUA + "Starting in:", ChatColor.BOLD + ">"+ChatColor.RED+""+ChatColor.BOLD+ timeRemaining+ChatColor.WHITE+""+ChatColor.BOLD+"<", 0,20,0);
            } else if (timeRemaining == 2) {
                p.sendTitle(ChatColor.AQUA + "Starting in:", ChatColor.BOLD + ">"+ChatColor.YELLOW+""+ChatColor.BOLD + timeRemaining+ChatColor.WHITE+""+ChatColor.BOLD+"<", 0,20,0);
            } else if (timeRemaining == 1) {
                p.sendTitle(ChatColor.AQUA + "Starting in:", ChatColor.BOLD + ">"+ChatColor.GREEN+""+ChatColor.BOLD + timeRemaining+ChatColor.WHITE+""+ChatColor.BOLD+"<", 0,20,0);
            }
        }
    }

    /**
     * Apply win effects to player
     * Does NOT check if player won, logic must be implemented before call
     * @param p GamePlayer that has won a round/game.
     */
    public void winEffects(Participant p) {
        p.getPlayer().setGameMode(GameMode.ADVENTURE);
        p.getPlayer().setAllowFlight(true);
        p.getPlayer().setFlying(true);
        p.getPlayer().setInvulnerable(true);
    }

    /**
     * Removes win effects from player that has won a round/game/is in god mode.
     * @param p GamePlayer that has won a round, game, or is invulnerable for some reason that needs to be reset.
     */
    public void removeWinEffect(Participant p) {
        p.getPlayer().setGameMode(GameMode.ADVENTURE);
        p.getPlayer().setAllowFlight(false);
        p.getPlayer().setFlying(false);
        p.getPlayer().setInvulnerable(false);
    }

    /**
     * Display red text "Game Over!" and send chat message
     * Does not proceed to game scoring or call anything else.
     * (gonna see if this is used at all in the future before deleting)
     */
    public void gameOverGraphics() {
        Bukkit.broadcastMessage(ChatColor.BOLD + "" + ChatColor.RED + "Game Over!");
        for (Participant p : MBC.getInstance().getPlayers()) {
            p.getPlayer().sendTitle(ChatColor.BOLD + "" + ChatColor.RED + "Game Over!","",0, 60, 20);
        }
    }

    /**
     * Custom version of above function if other player-specific events are to be triggered
     * within loops not handled by raw forEach loop
     */
    public void gameOverGraphics(GamePlayer p) {
        p.getPlayer().sendMessage(ChatColor.BOLD + "" + ChatColor.RED + "Game Over!");
        p.getPlayer().sendTitle(ChatColor.BOLD + "" + ChatColor.RED + "Game Over!","",0, 60, 20);
    }

    /**
     * Display red text "Round Over!"
     */
    public void roundOverGraphics() {
        Bukkit.broadcastMessage(ChatColor.BOLD + "" + ChatColor.RED + "Round Over!");
        for (Participant p : MBC.getInstance().getPlayers()) {
            p.getPlayer().sendTitle(ChatColor.BOLD + "" + ChatColor.RED + "Round Over!", "", 0, 60, 20);
        }
    }

    /**
     * Custom version of above function if other player-specific events are to be triggered
     * within loops not handled by raw forEach loop
     */
    public void roundOverGraphics(GamePlayer p) {
        p.getPlayer().sendMessage(ChatColor.BOLD + "" + ChatColor.RED + "Round Over!");
        p.getPlayer().sendTitle(ChatColor.BOLD + "" + ChatColor.RED + "Round Over!","",0, 60, 20);
    }

    public void setPVP(boolean b) {
        String s = b ? "[Debug] PVP now enabled. " : "[Debug] PVP now disabled.";
        Bukkit.broadcastMessage(s);
        PVP_ENABLED = b;
    }
    public boolean PVP() { return PVP_ENABLED; }
}
