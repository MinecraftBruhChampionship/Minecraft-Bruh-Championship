package me.kotayka.mbc.partygames;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.function.mask.BlockStateMask;
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.transform.AffineTransform;
import com.sk89q.worldedit.math.transform.CombinedTransform;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.world.World;
import me.kotayka.mbc.GameState;
import me.kotayka.mbc.MBC;
import me.kotayka.mbc.Participant;
import me.kotayka.mbc.PartyGame;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

public class BeepTest extends PartyGame {
    // Maps of levels by name
    private List<BeepTestLevel> easyLevels = null;
    private List<BeepTestLevel> regularLevels = null;
    private List<BeepTestLevel> mediumLevels = null;
    private List<BeepTestLevel> hardLevels = null;
    private int rounds = 0;
    private boolean oppositeSide = false;

    // arena regions
    private final Location copyArenaPrimary = new Location(Bukkit.getWorld("Party"), -65, -63, 60);
    private final Location copyArenaSecondary = new Location(Bukkit.getWorld("Party"), -109, -37, 112);
    private final Location arenaPrimary = new Location(Bukkit.getWorld("Party"),-505, -63, -492);
    private final Location arenaSecondary = new Location(Bukkit.getWorld("Party"),-536, -56, -492);
    private final Location courseToPrimary = new Location(Bukkit.getWorld("Party"), -534, -59, -462);
    private final Location courseToSecondary = new Location(Bukkit.getWorld("Party"), -510, -59, -486);
    private final BlockVector3 arenaFrom = new BlockVector3(-65, -63, 60);
    private final BlockVector3 arenaTo = new BlockVector3(-500, -63, -500);
    private final World WorldEditWorld = BukkitAdapter.adapt(Bukkit.getWorld("Party"));

    // game instance
    private static BeepTest instance = null;

    public static PartyGame getInstance() {
        if (instance == null) {
            instance = new BeepTest();
            return new BeepTest();
        } else {
            return instance;
        }
    }

    private BeepTest() {
        super("BeepTest", new Location(Bukkit.getWorld("Party"), -522, -55, -458), new String[] {
                "text 1",
                "text 2",
                "text 3",
                "scoring"
        });

        loadCourses();

    }

    @Override
    public void start() {
        super.start();

        world().setTime(18000);

        loadInitialArena();
        Barriers(true);

        setGameState(GameState.TUTORIAL);

        setTimer(30);
    }

    @Override
    public void loadPlayers() {
        //MBC.getInstance().hideAllPlayers();
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.teleport(SPAWN);
            p.addPotionEffect(MBC.SATURATION);
            p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, -1, 255, false, false));
        }
    }

    @Override
    public void events() {
        switch (getState()) {
            case TUTORIAL:
                if (timeRemaining == 0) {
                    setGameState(GameState.STARTING);
                    setTimer(15);
                } else if (timeRemaining % 7 == 0) {
                    Introduction();
                }
                break;
            case STARTING:
                startingCountdown();
                if (timeRemaining == 0) {
                    //MBC.getInstance().hideAllPlayers();
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.playSound(p, Sound.MUSIC_DISC_13, SoundCategory.RECORDS,1,1); // temp?
                    }
                    Barriers(false);
                    nextRound();
                    rounds++;
                    setGameState(GameState.ACTIVE);
                    setTimer(16);
                }
                break;
            case ACTIVE:
                if (timeRemaining == 0) {
                    if (rounds == 15) {
                        // there are no more rounds. game is over
                        Bukkit.broadcastMessage("The game is over!");
                    } else {
                        // clear level, move onto next level
                        nextRound();
                        rounds++;
                    }
                    setTimer(16);
                }
                break;
            case END_GAME:
        }

    }

    @Override
    public void createScoreboard(Participant p) {
        createLine(19, ChatColor.RESET.toString(), p);
        createLine(4, ChatColor.RESET.toString() + ChatColor.RESET, p);

        updateInGameTeamScoreboard();
    }

    private void loadCourses() {
        easyLevels = BeepTestLevelLoader.loadEasyLevels();
        regularLevels = BeepTestLevelLoader.loadRegularLevels();
        mediumLevels = BeepTestLevelLoader.loadMediumLevels();
        hardLevels = BeepTestLevelLoader.loadHardLevels();
    }

    /**
     * Spawns the course for the next round on the appropriate side.
     */
    private void nextRound() {
        BeepTestLevel lvl = chooseLevel();
        if (oppositeSide) {
            oppositeSide = false;
            EditSession editSession = WorldEdit.getInstance().newEditSession(WorldEditWorld);
            ForwardExtentCopy copy = new ForwardExtentCopy(WorldEditWorld, lvl.getReversedRegion(), BukkitAdapter.asBlockVector(lvl.getPasteReversed()), editSession, BukkitAdapter.asBlockVector(courseToPrimary));
            try {
                Operations.complete(copy);
                editSession.close();
            } catch (WorldEditException e) {
                e.printStackTrace();
            }
        } else {
            oppositeSide = true;
            EditSession editSession = WorldEdit.getInstance().newEditSession(WorldEditWorld);
            ForwardExtentCopy copy = new ForwardExtentCopy(WorldEditWorld, lvl.getRegion(), BukkitAdapter.asBlockVector(lvl.getPasteFrom()), editSession, BukkitAdapter.asBlockVector(courseToPrimary));
            try {
                Operations.complete(copy);
                editSession.close();
            } catch (WorldEditException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Randomly chooses a level from the appropriate category based on rounds.
     *
     * @return The randomly selected level.
     */
    private BeepTestLevel chooseLevel() {
        List<BeepTestLevel> chooseFrom = null;
        if (rounds < 4) {
            chooseFrom = easyLevels;
        } else if (rounds < 8) {
            chooseFrom = regularLevels;
        } else if (rounds < 12) {
            chooseFrom = mediumLevels;
        } else {
            chooseFrom = hardLevels;
        }

        // select random level
        int rand = (int) (Math.random() * chooseFrom.size());
        BeepTestLevel lvl = chooseFrom.get(rand);
        chooseFrom.remove(rand);
        return lvl;
    }


    /**
     * Put or remove barriers on the first side of the course
     *
     * @param b Boolean for state of barriers: true = Barriers, false = Air
     */
    private void Barriers(boolean b) {
        Material m = b ? Material.BARRIER : Material.AIR;

        for (int y = -55; y <= -52; y++) {
            for (int x = -536; x <= -508; x++) {
                world().getBlockAt(x, y, -461).setType(m);
            }
        }
    }

    /**
     * Loads the default arena using WorldEditAPI.
     */
    private void loadInitialArena() {
        CuboidRegion arena = new CuboidRegion(BukkitAdapter.asBlockVector(copyArenaPrimary), BukkitAdapter.asBlockVector(copyArenaSecondary));
        EditSession editSession = WorldEdit.getInstance().newEditSession(WorldEditWorld);
        ForwardExtentCopy copy = new ForwardExtentCopy(WorldEditWorld, arena, arenaFrom, editSession, arenaTo);
        try {
            Operations.complete(copy);
            editSession.close();
        } catch (WorldEditException e) {
            e.printStackTrace();
        }
    }
}