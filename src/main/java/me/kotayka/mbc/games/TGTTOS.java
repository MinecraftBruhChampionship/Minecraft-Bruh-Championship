package me.kotayka.mbc.games;

import me.kotayka.mbc.Game;
import me.kotayka.mbc.GameState;
import me.kotayka.mbc.MBC;
import me.kotayka.mbc.Participant;
import me.kotayka.mbc.gameMaps.tgttosMap.TGTTOSMap;
import me.kotayka.mbc.gameMaps.tgttosMap.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

public class TGTTOS extends Game {
    private int roundNum = 0;
    private static final int MAX_ROUNDS = 6;
    private TGTTOSMap map = null;
    private List<TGTTOSMap> maps = new ArrayList<>(
            Arrays.asList(new Pit(), new Meatball(), new Walls(),
                          new Cliffs(), new Glide(), new Skydive(),
                          new Boats()
            ));

    private List<Participant> finishedParticipants;
    private String[] deathMessages = new String[39];
    private List<Location> placedBlocks = new ArrayList<Location>(20);
    private boolean teamBonus = false;  // determine whether or not a full team has completed yet

    // Scoring
    public static int PLACEMENT_POINTS = 1; // awarded multiplied by the amount of players who havent finished yet
    public static int COMPLETION_POINTS = 1; // awarded for completing the course
    public static int TEAM_BONUS = 5; // awarded per player on team

    public TGTTOS() {
        super(2, "TGTTOS");
    }

    public void createScoreboard(Participant p) {
        createLine(23, ChatColor.AQUA + "" + ChatColor.BOLD + "Game: "+ MBC.getInstance().gameNum+"/6:" + ChatColor.WHITE + " TGTTOS", p);

        createLine(19, ChatColor.RESET.toString(), p);
        createLine(15, ChatColor.AQUA + "Game Coins:", p);
        createLine(3, ChatColor.RESET.toString() + ChatColor.RESET.toString(), p);

        updateInGameTeamScoreboard();
    }

    /**
     * Update scoreboard display on how many players have finished the round
     */
    public void updateFinishedPlayers() {
        for (Participant p : MBC.getInstance().getPlayers()){
            createLine(2, ChatColor.YELLOW + "Finished: " + ChatColor.WHITE + finishedParticipants.size()+"/"+MBC.MAX_PLAYERS, p);
        }
    }

    public void events() {
        if (getState().equals(GameState.STARTING)) {
           if (timeRemaining == 0) {
               for (Participant p : MBC.getInstance().getPlayers()) {
                    p.getPlayer().setGameMode(GameMode.SURVIVAL);
               }
               map.Barriers(false);
               setGameState(GameState.ACTIVE);
               timeRemaining = 120;
           } else {
               Countdown();
           }
        } else if (getState().equals(GameState.ACTIVE)) {
            if (timeRemaining == 0) {
                for (Participant p : MBC.getInstance().getPlayers()) {
                    if (!finishedParticipants.contains(p)) {
                        winEffects(p); // just for the flying
                        p.getPlayer().sendMessage(ChatColor.RED+"You didn't finish in time!");
                    }
                }

                if (roundNum == MAX_ROUNDS) {
                    setGameState(GameState.END_GAME);
                    timeRemaining = 37;
                } else {
                    setGameState(GameState.END_ROUND);
                    timeRemaining = 5;
                }
            }
        } else if (getState().equals(GameState.END_ROUND)) {
            if (timeRemaining == 0) {
                startRound();
            } else if (timeRemaining == 5) {
                roundOverGraphics();
            }
        } else if (getState().equals(GameState.END_GAME)) {
            if (timeRemaining == 0) {
                removePlacedBlocks();
            }
            gameEndEvents();
        }
    }

    public void start() {
        super.start();

        setDeathMessages();
        //setGameState(GameState.TUTORIAL);
        setGameState(GameState.STARTING);

        for (Participant p : MBC.getInstance().getPlayers()) {
            p.getPlayer().addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 100000, 10, false, false));
        }

        startRound();
    }

    /**
     * Moved from startRound()
     * repurpose loadPlayers() however is best needed for tgttos
     */
    public void loadPlayers() {
        if (map == null) {
            return;
        }
        for (Participant p : MBC.getInstance().getPlayers()) {
            p.getInventory().clear();
            p.getPlayer().setGameMode(GameMode.ADVENTURE);
            p.getPlayer().setVelocity(new Vector(0,0,0));
            p.getPlayer().teleport(map.getSpawnLocation());

            if (p.getPlayer().getAllowFlight()) {
                removeWinEffect(p);
            }

            if (p.getPlayer().hasPotionEffect(PotionEffectType.NIGHT_VISION)) {
                p.getPlayer().removePotionEffect(PotionEffectType.NIGHT_VISION);
            }

            if (map instanceof Meatball) {
                p.getPlayer().addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 100000, 10, false, false));
            }

            if (map.getItems() == null) continue;

            for (ItemStack i : map.getItems()) {
                if (i.getType().equals(Material.WHITE_WOOL)) {
                    ItemStack wool = p.getTeam().getColoredWool();
                    wool.setAmount(64);
                    p.getInventory().addItem(wool);
                } else if (i.getType().equals(Material.SHEARS)) {
                    ItemMeta meta = i.getItemMeta();
                    meta.setUnbreakable(true);
                    i.setItemMeta(meta);
                    p.getInventory().addItem(i);
                } else if (i.getType().equals(Material.LEATHER_BOOTS)) {
                    p.getInventory().setBoots(p.getTeam().getColoredLeatherArmor(i));
                } else {
                    p.getInventory().addItem(i);
                }
            }
        }
    }

    /**
     * Resets variables and map for next round
     * If at maximum rounds, ends the game
     */
    public void startRound() {
        if (roundNum == MAX_ROUNDS) {
            setGameState(GameState.END_GAME);
            timeRemaining = 37;
            return;
        } else {
            roundNum++;
        }

        teamBonus = false;

        finishedParticipants = new ArrayList<>(MBC.getInstance().players.size());
        TGTTOSMap newMap = maps.get((int) (Math.random()*maps.size()));
        map = newMap;
        maps.remove(newMap);
        map.Barriers(true);

        createLine(22, ChatColor.AQUA+""+ChatColor.BOLD+"Map: "+map.getName());
        createLine(21, ChatColor.GREEN +  "Round: "+ ChatColor.RESET+roundNum+"/6");
        updateFinishedPlayers();

        if (map != null) {
            loadPlayers();
        }
        map.getWorld().spawnEntity(map.getEndLocation(), EntityType.CHICKEN);

        setGameState(GameState.STARTING);
        setTimer(20);
    }

    private void setDeathMessages() {
        try {
            FileReader fr = new FileReader("tgttos_death_messages.txt");
            BufferedReader br = new BufferedReader(fr);
            int i = 0;
            String line = null;
            while ((line = br.readLine()) != null) {
                deathMessages[i] = line;
                i++;
            }
            br.close();
            fr.close();
        } catch (IOException e) {
            Bukkit.broadcastMessage(ChatColor.RED+"Error: " + e.getMessage());
        }
    }

    private void printDeathMessage(Participant p) {
        int rand = (int) (Math.random() * deathMessages.length);
        Bukkit.broadcastMessage(ChatColor.GRAY+deathMessages[rand].replace("{player}", p.getFormattedName() + ChatColor.GRAY));
    }

    private void Countdown() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (timeRemaining <= 10 && timeRemaining > 3) {
                p.sendTitle(ChatColor.AQUA + "Chaos begins in:", ChatColor.BOLD + ">"+timeRemaining+"<", 0,20,0);
            } else if (timeRemaining == 3) {
                p.sendTitle(ChatColor.AQUA + "Chaos begins in:", ChatColor.BOLD + ">"+ChatColor.RED+""+ChatColor.BOLD+ timeRemaining+ChatColor.WHITE+""+ChatColor.BOLD+"<", 0,20,0);
            } else if (timeRemaining == 2) {
                p.sendTitle(ChatColor.AQUA + "Chaos begins in:", ChatColor.BOLD + ">"+ChatColor.YELLOW+""+ChatColor.BOLD + timeRemaining+ChatColor.WHITE+""+ChatColor.BOLD+"<", 0,20,0);
            } else if (timeRemaining == 1) {
                p.sendTitle(ChatColor.AQUA + "Chaos begins in:", ChatColor.BOLD + ">"+ChatColor.GREEN+""+ChatColor.BOLD + timeRemaining+ChatColor.WHITE+""+ChatColor.BOLD+"<", 0,20,0);
            }
        }
    }

    private void removePlacedBlocks() {
        for (Location l : placedBlocks) {
            if (!l.getBlock().getType().equals(Material.AIR)) {
                l.getBlock().setType(Material.AIR);
            }
        }
    }

    @EventHandler
    public void PlayerMoveEvent(PlayerMoveEvent e) {
        if (!isGameActive()) return;
        if (map == null) return;

        if (e.getPlayer().getLocation().getY() < map.getDeathY()) {
            e.getPlayer().setVelocity(new Vector(0,0,0));
            e.getPlayer().teleport(map.getSpawnLocation());
            printDeathMessage(Participant.getParticipant(e.getPlayer()));
        }
    }

    @EventHandler
    public void blockBreak(BlockBreakEvent event) {
        if (!isGameActive()) return;

        if (!(event.getBlock().getType().toString().endsWith("WOOL"))) {
            event.setCancelled(true);
            return;
        }

        event.setDropItems(false);

        if (placedBlocks.contains(event.getBlock().getLocation())) placedBlocks.remove(event.getBlock().getLocation());
    }

    private void checkTeamFinish(Participant p) {
        int count = 0;
        for (Participant teammate : p.getTeam().getPlayers()) {
            if (teammate.getPlayer().getGameMode().equals(GameMode.SPECTATOR))
                count++;
        }
        if (count == p.getTeam().getPlayers().size()) {
            Bukkit.broadcastMessage(ChatColor.BOLD + p.getTeam().teamNameFormat() + ChatColor.GREEN+" was the first full team to complete!");
            for (Participant teammate : p.getTeam().getPlayers()) {
                teammate.addCurrentScore(5);
                teammate.getPlayer().sendMessage(ChatColor.GREEN+"Your team got the team bonus!");
            }
            teamBonus = true;
        }
    }

    public void chickenClick(Participant p, Entity chicken) {
        finishedParticipants.add(p);
        p.addCurrentScore(PLACEMENT_POINTS*(MBC.getInstance().getPlayers().size()-finishedParticipants.size())+COMPLETION_POINTS);
        String place = getPlace(finishedParticipants.size());
        chicken.remove();
        Bukkit.broadcastMessage(p.getFormattedName()+ChatColor.WHITE+" finished in "+ChatColor.AQUA+place+"!");
        MBC.spawnFirework(p);
        p.getPlayer().setGameMode(GameMode.SPECTATOR);
        p.getPlayer().sendMessage(ChatColor.GREEN+"You finished in "+ ChatColor.AQUA+place+ChatColor.GREEN+" place!");

        // check if all players on a team finished
        if (!teamBonus)
            checkTeamFinish(p);

        updateFinishedPlayers();

        if (finishedParticipants.size() == MBC.getInstance().getPlayers().size()) {
            setGameState(GameState.END_ROUND);
            timeRemaining = 5;
        }
    }
    @EventHandler
    public void chickenLeftClick(EntityDamageByEntityEvent event) {
        if (!isGameActive()) return;

        if (event.getEntity() instanceof Chicken && event.getDamager() instanceof Player) {
            if (((Player) event.getDamager()).getGameMode() != GameMode.SURVIVAL)
                event.setCancelled(true);
            else
                chickenClick(Participant.getParticipant((Player) event.getDamager()), event.getEntity());
        }
    }

    @EventHandler
    public void chickenRightClick(PlayerInteractEntityEvent event) {
        if (!isGameActive()) return;

        if (event.getRightClicked() instanceof Chicken) {
            chickenClick(Participant.getParticipant(event.getPlayer()), event.getRightClicked());
        }
    }

    @EventHandler
    public void boatExit(VehicleExitEvent event) {
        if (!isGameActive()) return;

        if (event.getVehicle() instanceof Boat && event.getExited() instanceof Player) {
            Boat boat = (Boat) event.getVehicle();
            boat.remove();

            ItemStack boatItem = new ItemStack(Material.OAK_BOAT);
            Player p = (Player) event.getExited();
            p.getInventory().addItem(boatItem);
        }
    }

    @EventHandler
    public void blockPlaceEvent(BlockPlaceEvent e) {
        if (!isGameActive()) return;

        Player p = e.getPlayer();

        if (e.getBlock().getType().toString().endsWith("WOOL")) {
            // add to placed blocks
            placedBlocks.add(e.getBlock().getLocation());
            // if block was wool, give appropriate amount back
            String wool = e.getBlock().getType().toString();
            // check item slot
            assert wool != null;
            int index = p.getInventory().getHeldItemSlot();
            if (Objects.requireNonNull(p.getInventory().getItem(index)).getType().toString().equals(wool)) {
                int amt = Objects.requireNonNull(p.getInventory().getItem(index)).getAmount();
                p.getInventory().setItem(index, new ItemStack(Objects.requireNonNull(Material.getMaterial(wool)), amt));
                return;
            }
            if (p.getInventory().getItem(40) != null) {
                if (Objects.requireNonNull(p.getInventory().getItem(40)).getType().toString().equals(wool)) {
                    int amt;
                    // "some wacky bullshit prevention" - me several months ago
                    if (Objects.requireNonNull(p.getInventory().getItem(40)).getAmount() + 63 > 100) {
                        amt = 64;
                    } else {
                        amt = Objects.requireNonNull(p.getInventory().getItem(40)).getAmount() + 63;
                    }
                    p.getInventory().setItem(40, new ItemStack(Objects.requireNonNull(Material.getMaterial(wool)), amt));
                }
            }
        }
    }
}
