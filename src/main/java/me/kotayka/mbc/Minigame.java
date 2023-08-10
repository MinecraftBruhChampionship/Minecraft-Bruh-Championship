package me.kotayka.mbc;

import me.kotayka.mbc.games.Lobby;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;

import java.util.*;

/**
 * This class is for lobby-related minigames that do not necessitate the full package of a point-scoring game.
 * Examples:
 *      Lobby
 *      Decision Dome / Voting gimmick
 *      Any hub minigame put in place, i.e. trick-or-treat/presents/milk the cow
 */
public abstract class Minigame implements Scoreboard, Listener {
    public String gameName;
    private GameState gameState = GameState.INACTIVE;
    public int timeRemaining = -1;
    public static int taskID = -1;

    // GLOBAL STRING STORAGE FOR STORING STRINGS TO PRINT WHILE PERFORMING TASKS (ie sorting through game scores)
    protected String TO_PRINT = "";

    public Minigame(String name) {
        gameName = name;
    }

    /**
     * @see Game start()
     * all games should call a super.start()
     * minigames may have their own implementation
     * since they require less
     */
    public abstract void start();

    public boolean isGameActive() {
        if (!(MBC.getInstance().getMinigame() instanceof Game)) {
            return false;
        }

        return (this.getState().equals(GameState.ACTIVE) || this.getState().equals(GameState.OVERTIME));
        // return (this.getState().equals(GameState.ACTIVE) || this.getState().equals(GameState.PAUSED)); <- might be dependent on event? gonna hold off
    }

    /**
     * Should load players into appropriate spots after/during introduction
     * Could/should handle stuff like: clearing inventory, applying potion effects, etc
     */
    public abstract void loadPlayers();

    public abstract void events();

    public void setTimer(int time) {
        if (timeRemaining != -1) {
            // if the time hasn't run out yet, stop the time and start it again
            stopTimer();
        }

        timeRemaining = time;

        taskID = Bukkit.getScheduler().scheduleSyncRepeatingTask(MBC.getInstance().plugin, () -> {
            if (MBC.getInstance().getMinigame() instanceof Game) {
                if (!gameState.equals(GameState.OVERTIME)) {
                    createLineAll(20, ChatColor.RED + "" + ChatColor.BOLD + "Time left: " + ChatColor.WHITE + getFormattedTime(--timeRemaining));
                } else {
                    createLineAll(20, ChatColor.RED + "" + ChatColor.BOLD + "Overtime: " + ChatColor.WHITE + getFormattedTime(--timeRemaining));
                }
            } else {
                createLineAll(20, getFormattedTime(--timeRemaining));
            }
            if (timeRemaining < 0) {
                stopTimer();
            }
            MBC.getInstance().getMinigame().events();
        }, 20, 20);
    }

    public void Pause() {
        if (!(gameState == GameState.STARTING) || this instanceof DecisionDome || this instanceof Lobby) {
            // don't pause if game has started or minigame
            return;
        }

        Bukkit.broadcastMessage("Event Paused!");
        gameState = GameState.PAUSED;
        stopTimer();
        createLineAll(20, "EVENT PAUSED");
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendTitle("PAUSED", "", 20, 60, 20);
        }
    }

    public void Unpause() {
        Bukkit.broadcastMessage("Starting!");
        if (timeRemaining < 5) timeRemaining = 5;
        gameState = GameState.STARTING;
        setTimer(timeRemaining);
    }

    public void stopTimer() {
        MBC.getInstance().cancelEvent(taskID);
    }

    public String getFormattedTime(int seconds) {
        return String.format("%02d", seconds / 60) + ":" + String.format("%02d", seconds % 60);
    }

    public void createScoreboard() {
        for (Participant p : MBC.getInstance().getPlayersAndSpectators()) {
            newObjective(p);
            createScoreboard(p);
        }
    }

    public void createLine(int score, String line, Participant p) {
        if (p.objective == null || !Objects.equals(p.gameObjective, gameName)) {
            Bukkit.broadcastMessage("[Debug] p.objective == " + p.objective);
            Bukkit.broadcastMessage("[Debug] gameObjective == " + p.gameObjective);
            p.gameObjective = gameName;
            MBC.getInstance().getMinigame().createScoreboard(p);
        }

        resetLine(p, score);

        p.objective.getScore(line).setScore(score);
        p.lines.put(score, line);
    }

    public void createLineAll(int score, String line) {
        for (Participant p : MBC.getInstance().players) {
            createLine(score, line, p);
        }
    }

    /**
     * Updates display of team's current score on bottom of player's scoreboard;
     * Note: may be redundant
     * @param t Team for which to update for each player
     */
    public void displayTeamCurrentScore(MBCTeam t) {
        for (Participant p : t.teamPlayers) {
            createLine(1, ChatColor.GREEN + "Team Coins: " + ChatColor.WHITE + t.getMultipliedCurrentScore(), p);
        }
    }

    /**
     * Sorts teams by their current round score to place onto scoreboard.
     */
    public void updateInGameTeamScoreboard() {
        List<MBCTeam> teamRoundsScores = new ArrayList<>(getValidTeams());
        teamRoundsScores.sort(new TeamRoundSorter());

        for (int i = 14; i > 14-teamRoundsScores.size(); i--) {
            MBCTeam t = teamRoundsScores.get(14-i);
            createLineAll(i,String.format("%s: %.1f", t.teamNameFormat(), t.getMultipliedCurrentScore()));
        }
    }

    /**
     * Sorts teams by their overall score to place onto scoreboard during lobby/after games
     */
    public void updateTeamStandings() {
        //MBCTeam lastTeam = null;
        //int ties = 0;
        int line = 14;
        int place = 1;
        for (MBCTeam t : MBC.getInstance().teamScores) {
            createLineAll(line--, String.format("%s: %.1f", t.teamNameFormat(), t.getMultipliedTotalScore()));

            // we will handle ties in the future
            t.setPlace(place++);

            // use past scores to account for ties
            /*
            if (lastTeam != null && lastTeam.getMultipliedTotalScore() == t.getMultipliedTotalScore()) {
                if (lastTeam.getPreviousPlace() > t.getPreviousPlace()) {
                    lastTeam.setPlace()
                }
                t.setPlace(place);
                ties++;
            } else {
                t.setPlace(place+ties);
                //ties = 0;
                place++;
            }
            //lastScore = t.getMultipliedTotalScore();
            line--;
            lastTeam = t;
             */
        }
        MBC.getInstance().lobby.colorPodiums();
    }

    /**
     * Displays team's total score in lobby
     * Team Coins: {COIN_AMOUNT}
     * Note: Since team scoreboard is always active in lobby, this may be redundant.
     * @param t Team whose coin count to display
     */
    protected void displayTeamTotalScore(MBCTeam t) {
        for (Participant p : t.teamPlayers) {
            createLine(1, ChatColor.GREEN + "Team Coins: " + ChatColor.WHITE + t.getMultipliedTotalScore(), p);
        }
    }

    public void newObjective() {
        for (Participant p : MBC.getInstance().players) {
            newObjective(p);
        }
    }

    public void resetLine(Participant p, int line) {
        if (p.lines.containsKey(line)) {
            p.objective.getScoreboard().resetScores(p.lines.get(line));
        }
    }

    public List<MBCTeam> getValidTeams() {
        List<MBCTeam> newTeams = new ArrayList<>();
        for (int i = 0; i < MBC.teamNames.size(); i++) {
            if (!Objects.equals(MBC.getInstance().teams.get(i).fullName, "Spectator") && MBC.getInstance().teams.get(i).teamPlayers.size() > 0) {
                newTeams.add(MBC.getInstance().teams.get(i));
            }
        }

        return newTeams;
    }

    /**
     * Accessor for current game's state
     *
     * @return Enum GameState representing the current state of the game
     * @see GameState
     */
    public GameState getState() {
        return gameState;
    }

    /**
     * Mutator for current game's state
     *
     * @param gameState the state which the game will change to
     * @see GameState
     */
    public void setGameState(GameState gameState) {
        if (gameState == GameState.TUTORIAL && this instanceof Game) {
            if (((Game) this).disconnect) {
                this.gameState = GameState.STARTING;
                Pause();
                ((Game) this).disconnect = false;
            } else {
                this.gameState = gameState;
            }
        } else {
            this.gameState = gameState;
        }
    }

    public void newObjective(Participant p) {
        if (p.objective != null) {
            p.objective.unregister();
        }

        p.gameObjective = gameName;
        Objective obj;

        if (p.board.getObjective("Objective") == null) {
            obj = p.board.registerNewObjective("Objective", "dummy", ChatColor.YELLOW + "" + ChatColor.BOLD + "MCC");
        } else {
            obj = p.board.getObjective("Objective");
        }
        if (obj == null) {
            obj = p.board.registerNewObjective("Objective", "dummy", ChatColor.YELLOW + "" +ChatColor.BOLD +"MCC");
        }

        p.lines = new HashMap<>();

        p.objective = obj;
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
    }
}