package com.kotayka.mcc.Skybattle;

import com.kotayka.mcc.Scoreboards.ScoreboardPlayer;
import com.kotayka.mcc.mainGame.MCC;
import com.kotayka.mcc.mainGame.manager.Participant;
import com.kotayka.mcc.mainGame.manager.Players;
import com.kotayka.mcc.mainGame.manager.teamManager;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BrewingStand;
import org.bukkit.block.Chest;
import org.bukkit.block.Container;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Team;

import java.util.*;

import static org.bukkit.GameMode.SURVIVAL;

public class Skybattle {
    // (PLANNED) STATES: INACTIVE, PLAYING, PAUSED, STARTING, ENDING (?)
    private String state = "INACTIVE";

    public final Players players;
    public final Plugin plugin;
    public boolean stage = false;
    public boolean finalShrink = false;
    public List<UUID> playersDeadList = new ArrayList<UUID>();

    public Map<Entity, Player> lastDamage; // <Player, Damager>
    public Map<Entity, Player> creepersAndSpawned = new HashMap<>(16); // <Creeper, Spawner>
    public Map<Entity, Player> whoPlacedThatTNT = new HashMap<>(5);
    public int roundNum = 1;
    public double borderHeight = 17.0;
    public List<Location> spawnPoints = new ArrayList<>(6);
    public List<ItemStack> spawnItems = new ArrayList<>(5);
    public World world;
    public WorldBorder border;
    private final Location CENTER;
    public MCC mcc;

    // for events that don't repeat for each player


    public Skybattle(Players players, Plugin plugin, MCC mcc) {
        this.players = players;
        this.plugin = plugin;
        this.mcc = mcc;

        lastDamage = new HashMap<>(players.players.size());

        if (Bukkit.getWorld("Skybattle") == null) {
            world = Bukkit.getWorld("world");
        }
        else {
            world = Bukkit.getWorld("Skybattle");
        }

        CENTER = new Location(world, -157, 0, -266);
    }

    public void loadMap() {
        resetMap();

        // Spawn items
        ItemStack pick = new ItemStack(Material.IRON_PICKAXE);
        pick.addEnchantment(Enchantment.DIG_SPEED, 3);

        spawnItems = Arrays.asList(
                new ItemStack(Material.STONE_SWORD), pick,
                new ItemStack(Material.WHITE_CONCRETE, 64),
                new ItemStack(Material.COOKED_BEEF, 7),
                new ItemStack(Material.IRON_CHESTPLATE)
        );

        Location spawnOne = new Location(world, -220, -9, -266);
        Location spawnTwo = new Location(world, -190, -9, -212);
        Location spawnThree = new Location(world, -124, -9, -212);
        Location spawnFour = new Location(world, -94, -9, -266);
        Location spawnFive = new Location(world, -124, -9, -320);
        Location spawnSix = new Location(world, -190, -9, -320);

        spawnPoints = Arrays.asList(spawnOne, spawnTwo, spawnThree, spawnFour, spawnFive, spawnSix);

        for (Participant p : Participant.participantsOnATeam) {
            p.player.getInventory().clear();
            p.player.setHealth(20);
            p.player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 10, 4, false, false));
            p.player.setGameMode(SURVIVAL);
            p.player.setAllowFlight(false);
            p.player.setFlying(false);
            p.player.setInvulnerable(false);

            lastDamage.put(p.player, null);

            for (ItemStack i : spawnItems) {
                if (i.getType() == Material.WHITE_CONCRETE) {
                    ItemStack concrete = new ItemStack(Material.WHITE_CONCRETE);
                    concrete.setAmount(64);
                    switch (p.team) {
                        case "RedRabbits":
                            concrete.setType(Material.RED_CONCRETE);
                            break;
                        case "YellowYaks":
                            concrete.setType(Material.YELLOW_CONCRETE);
                            break;
                        case "GreenGuardians":
                            concrete.setType(Material.GREEN_CONCRETE);
                            break;
                        case "BlueBats":
                            concrete.setType(Material.BLUE_CONCRETE);
                            break;
                        case "PurplePandas":
                            concrete.setType(Material.PURPLE_CONCRETE);
                            break;
                        case "PinkPiglets":
                            concrete.setType(Material.PINK_CONCRETE);
                            break;
                        default:
                            p.player.sendMessage("You're not on a team");
                    }
                    p.player.getInventory().addItem(concrete);
                }
                else if (i.getType() == Material.IRON_CHESTPLATE) {
                    p.player.getInventory().setChestplate(i);
                }
                else {
                    p.player.getInventory().addItem(i);
                }
            }
        }

        // Border
        assert world != null;
        border = world.getWorldBorder();
        border.setCenter(CENTER);
        border.setSize(150);
        border.setDamageAmount(0.5);
        border.setDamageBuffer(0);
        border.setWarningDistance(5);

        borderHeight = 17;
    }

    public void start() {
        stage = true;

        for (ScoreboardPlayer player : mcc.scoreboardManager.playerList) {
            mcc.scoreboardManager.createSkybattleBoard(player);
        }

        loadMap();

        startRound();
    }

    /*
     * Reset Map
     */
    public void resetMap() {
        lastDamage.clear();
        creepersAndSpawned.clear();
        whoPlacedThatTNT.clear();

        int x = 225;
        int y = -16;
        int z = 322;
        for (int mapX = -225; mapX <= -87; mapX++) {
            for (int mapY = -17; mapY <= 17; mapY++) {
                for (int mapZ = -325; mapZ <= -207; mapZ++) {
                    assert world != null;
                    Block originalBlock = world.getBlockAt(x, y, z);
                    Block possiblyChangedBlock = world.getBlockAt(mapX, mapY, mapZ);
                    if (!(originalBlock.getType().name().equals(possiblyChangedBlock.getType().name()))) {
                        possiblyChangedBlock.setType(originalBlock.getType());
                        possiblyChangedBlock.setBlockData(originalBlock.getBlockData());
                    }
                    if (possiblyChangedBlock.getState() instanceof Chest && originalBlock.getState() instanceof Chest) {
                        Container container = (Chest) originalBlock.getState();
                        ItemStack[] itemsForChest = container.getInventory().getContents();
                        ((Chest) possiblyChangedBlock.getState()).getInventory().setContents(itemsForChest);
                    }
                    if (possiblyChangedBlock.getState() instanceof BrewingStand && originalBlock.getState() instanceof BrewingStand) {
                        Container container = (BrewingStand) originalBlock.getState();
                        ItemStack[] potions = container.getInventory().getContents();
                        ((BrewingStand) possiblyChangedBlock.getState()).getInventory().setContents(potions);
                    }
                    z++;
                }
                z = 322;
                y++;
            }
            z = 322;
            y = -16;
            x++;
        }

        // Clear all floor items, primed tnt, and creepers
        for (Item item : world.getEntitiesByClass(Item.class)) {
            item.remove();
        }
        for (Entity tnt : world.getEntitiesByClass(TNTPrimed.class)) {
            tnt.remove();
        }
        for (Entity creeper : world.getEntitiesByClass(Creeper.class)) {
            creeper.remove();
        }
    }

    public void spawnParticles() {
        for (int mapX = -172; mapX <= -143; mapX++) {
            for (int mapZ = -281; mapZ <= -253; mapZ++) {
                world.spawnParticle(Particle.ASH , mapX, borderHeight, mapZ, 1);
            }
        }
    }

    public void nextRound() {
        if (roundNum == 3) return;

        loadMap();
        players.spectators.clear();
        if (roundNum < 3) {
            roundNum++;
            startRound();
        }
    }

    public void startRound() {
        playersDeadList.clear();
        mcc.scoreboardManager.startTimerForGame(10, "Skybattle");
        String roundValue = ChatColor.BOLD+""+ChatColor.GREEN + "Round: "+ ChatColor.WHITE+ roundNum + "/3";
        mcc.scoreboardManager.changeLine(22, roundValue);
        setState("STARTING");

        // Randomly place each team at a different spawn
        List<Location> tempSpawns = new ArrayList<>(spawnPoints);

        for (int i = 0; i < mcc.teamList.size(); i++) {
            int randomNum = (int) (Math.random() * tempSpawns.size());
            for (int j = 0; j < mcc.teamList.get(i).size(); j++) {
                mcc.teamList.get(i).get(j).player.teleport(tempSpawns.get(randomNum));
            }
            tempSpawns.remove(randomNum);
        }
    }

    public void removeBarriers() {
        for (int x = -222; x <= -92; x++) {
            for (int y = -9; y <= -7; y++) {
                for (int z = -322; z <= -210; z++) {
                    if (world.getBlockAt(x, y, z).getType().equals(Material.BARRIER)) {
                        world.getBlockAt(x, y, z).setType(Material.AIR);
                    }
                }
            }
        }
    }

    public void timeEndEvents() {
        switch (mcc.skybattle.getState()) {
            case "STARTING":
                mcc.skybattle.setState("PLAYING");
                mcc.scoreboardManager.startTimerForGame(240, "Skybattle");
                mcc.skybattle.removeBarriers();
                break;
            case "PLAYING":
                mcc.skybattle.setState("END_ROUND");
                mcc.scoreboardManager.startTimerForGame(10, "Skybattle");
                break;
            case "END_ROUND":
                if (mcc.skybattle.roundNum < 3) {
                    mcc.skybattle.nextRound();
                } else {
                    mcc.skybattle.resetMap();
                    mcc.skybattle.resetBorder();
                    mcc.skybattle.setState("INACTIVE");
                    mcc.setGameOver(true);
                }
                break;
        }
        Bukkit.broadcastMessage("Skybattle State: " + getState());
        finalShrink = false;
        // return to lobby
    }

    public void timedEventsHandler(int time, ScoreboardPlayer p) {
        // WHILE GAME IS PLAYING
        switch (this.getState()) {
            case "PLAYING":
                if (time % 40 == 0 && time != 240 && time >= 60 && !finalShrink) {
                    mcc.skybattle.border.setSize(mcc.skybattle.border.getSize() * 0.75, 15);
                    p.player.player.sendMessage(ChatColor.DARK_RED + "> Border is Shrinking!");
                    p.player.player.sendTitle(" ", ChatColor.RED + "Border shrinking!", 0, 20, 10);
                } else if (((time - 10)) % 40 == 0 && !finalShrink) {
                    p.player.player.sendMessage(ChatColor.RED + "> Border shrinking in 10 seconds!");
                } else if (time == 60) {
                    mcc.skybattle.border.setSize(5, 60);
                    p.player.player.sendMessage(ChatColor.DARK_RED + "> Border will continue shrinking!");
                    finalShrink = true;
                } else if (time == 70) {
                    p.player.player.sendMessage(ChatColor.RED + "> Final shrink in 10 seconds!");
                }
                break;
            // DURING STARTING
            case "STARTING":
                p.player.player.sendTitle("Starting in:", "> " + time + " <", 0, 20, 0);
                break;
            // DURING ROUND END
            case "END_ROUND":
                if (time == 9) {
                    p.player.player.sendTitle(ChatColor.BOLD + "" + ChatColor.RED + "Round Over!", null, 0, 20, 0);
                    p.player.player.sendMessage(ChatColor.BOLD+""+ChatColor.RED+"Round Over!");
                }
                break;
        }
    }

    public void specialEvents(int time) {
        switch (this.getState()) {
            case "PLAYING":
                if (time <= 75) {
                    spawnParticles();
                    if (borderHeight >= 0)
                        borderHeight -= 0.22666667;
                }
                break;
            case "END_ROUND":
                if (time == 10)
                    rewardLastPlayers();
                break;
        }
    }

    public void kill(Participant p) {
        mcc.scoreboardManager.addScore(mcc.scoreboardManager.players.get(p.player.getUniqueId()), 15);
    }

    public void kill(Player p) {
        mcc.scoreboardManager.addScore(mcc.scoreboardManager.players.get(p.getUniqueId()), 15);
    }

    public void outLivePlayer() {
        for (Participant p : Participant.participantsOnATeam) {
            if (!playersDeadList.contains(p.player.getUniqueId())) {
                mcc.scoreboardManager.addScore(mcc.scoreboardManager.players.get(p.player.getUniqueId()), 1);
            }
        }
    }

    public void rewardLastPlayers() {
        for (Participant p : Participant.participantsOnATeam) {
            if (!playersDeadList.contains(p.player.getUniqueId())) {
                mcc.scoreboardManager.addScore(mcc.scoreboardManager.players.get(p.player.getUniqueId()), 15);
                p.player.setAllowFlight(true);
                p.player.sendMessage(ChatColor.GREEN+"You survived the round!");
                p.player.setFlying(true);
                p.player.setInvulnerable(true);
            }
        }
    }

    public void resetBorder() {
        world.getWorldBorder().reset();
    }

    public Location getCenter() { return CENTER; }

    public void setState(String state) {
        this.state = state;
    }
    public String getState() {
        return state;
    }
    public boolean enabled() {
        return stage;
    }
}