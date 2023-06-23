package me.kotayka.mbc;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Objects;

public class Participant implements Comparable<Participant> {

    // Player's un-multiplied individual score; updates between games
    private int rawTotalScore = 0;
    private int multipliedTotalScore = 0;
    // Player's current score in game; used for display
    private int rawCurrentScore = 0;
    private int multipliedCurrentScore = 0;
    private Team team;
    private final Player player;

    public final Scoreboard board = MBC.getInstance().manager.getNewScoreboard();
    public Objective objective;
    public String gameObjective;

    public HashMap<Integer, String> lines = new HashMap<>();

    public Participant(Player p) {
        player=p;
        p.setScoreboard(board);
        changeTeam(MBC.getInstance().spectator);
    }

    public void changeTeam(Team t) {
        if (t==null) {return;}
        if (team != null) {
            team.removePlayer(this);
        }
        team = t;
        team.addPlayer(this);
        Bukkit.broadcastMessage(getFormattedName()+ChatColor.WHITE+" has joined the "+team.getChatColor()+team.getTeamFullName());
        if (MBC.getInstance().gameID == 0 && MBC.getInstance().currentGame != null) {
            MBC.getInstance().lobby.changeTeam(this);
        }
    }
    public Player getPlayer() {
        return player;
    }

    /* Return unmultiplied total score */
    public int getRawTotalScore() {
        return rawTotalScore;
    }

    /* Returns the multiplied total score*/
    public int getMultipliedTotalScore() {
        return multipliedTotalScore;
    }

    /**
     * @return player's username
     */
    public String getPlayerName() {
        return getPlayer().getName();
    }

    /**
     * for string formatting; no hanging space
     * @return team icon + player's username with color
     */
    public String getFormattedName() {
        return (ChatColor.WHITE + "" + getTeam().getIcon() + " " + getTeam().getChatColor() + getPlayer().getName()) + ChatColor.WHITE;
    }

    public int getRawCurrentScore() {
        return rawCurrentScore;
    }
    public int getMultipliedCurrentScore() { return multipliedCurrentScore; }


    /**
     * Takes each current (game) scores and adds to Participant's stat totals.
     * Adds score to team, and resets the round variables for the next game.
     * @see Team addCurrentScoreToTotal()
     * @see Game gameEndEvents()
     */
    public void addCurrentScoreToTotal() {
        int amount = getRawCurrentScore();
        team.addCurrentScoreToTotal();
        rawTotalScore += amount;
        multipliedTotalScore += amount * MBC.getInstance().multiplier;
        resetCurrentScores();
    }

    /**
     * Called inbetween games to reset scores for each game to 0
     * Does not check whether or not game scores have been added to total event score.
     */
    public void resetCurrentScores() {
        multipliedCurrentScore = 0;
        rawCurrentScore = 0;
    }

    public void addCurrentScore(int amount) {
        multipliedCurrentScore += amount;
        rawCurrentScore += amount*MBC.getInstance().multiplier;
        team.addCurrentTeamScore(amount);

        MBC.getInstance().currentGame.updatePlayerCurrentScoreDisplay(this);
    }

    public Team getTeam() {
        return team;
    }

    public static boolean contains(Participant p) {
        return MBC.getInstance().players.contains(p);
    }

    public static boolean contains(Player p) {
        for (Participant x : MBC.getInstance().players) {
            if (Objects.equals(x.getPlayer(), p)) {
                return true;
            }
        }

        return false;
    }

    public static Participant getParticipant(Player p) {
        for (Participant x : MBC.getInstance().players) {
            if (Objects.equals(x.getPlayer(), p)) {
                return x;
            }
        }

        return null;
    }

    public static Participant getParticipant(String p) {
        for (Participant x : MBC.getInstance().players) {
            if (Objects.equals(x.getPlayer().getName(), p)) {
                return x;
            }
        }

        return null;
    }

    public PlayerInventory getInventory() {
        return getPlayer().getInventory();
    }

    /**
     * Comparison of players is based off unmultiplied total score.
     */
    @Override
    public int compareTo(@NotNull Participant o) {
        return (this.rawTotalScore - o.rawTotalScore);
    }
}
