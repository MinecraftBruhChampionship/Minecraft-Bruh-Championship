package me.kotayka.mbc.games;

import me.kotayka.mbc.Game;
import me.kotayka.mbc.GameState;
import me.kotayka.mbc.MBC;
import me.kotayka.mbc.Participant;
import me.kotayka.mbc.gameMaps.tgttosMap.*;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.*;

public class TGTTOS extends Game {
    private int roundNum = 0;
    private static final int MAX_ROUNDS = 6;
    private TGTTOSMap map = null;
    private List<TGTTOSMap> maps = new ArrayList<>(
            Arrays.asList(new Pit(), new Meatball(), new Walls(),
                    new Cliffs(), new Elytra(), new Skydive(),
                    new Boats(), new Glide()
            ));

    private List<Participant> finishedParticipants;
    private ArrayList<String> deathMessages = new ArrayList<>();
    private List<Location> placedBlocks = new ArrayList<Location>(20);
    private boolean firstTeamBonus = false;  // determine whether or not a full team has completed yet
    private boolean secondTeamBonus = false;

    // Meatball cooldowns from Dragons
    private Map<UUID, Boolean> canJump = new HashMap<>(); // this is only for meatball currently
    private Map<Player, Long> cooldowns = new HashMap<>();
    private DecimalFormat df = new DecimalFormat("#.#");
    private int cooldownID = -1;

    // Scoring
    public static int PLACEMENT_POINTS = 1; // awarded multiplied by the amount of players who havent finished yet
    public static int COMPLETION_POINTS = 1; // awarded for completing the course
    public static int FIRST_TEAM_BONUS = 4; // awarded per player on team
    public static int SECOND_TEAM_BONUS = 2; // awarded per player on team
    public static int TOP_THREE_BONUS = 3; // bonus for placing top 3

    public TGTTOS() {
        super("TGTTOS", new String[] {
                "⑪ Why did the MBC Player cross the road? To get to the other side!!!\n\n" +
                "⑪ Complete a series of obstacle courses fast to get as many points as possible!",
                "⑪ Make sure to " + ChatColor.BOLD + "punch the chicken" + ChatColor.RESET + " to complete the level!\n\n" +
                "⑪ You'll get more points for finishing ahead of other players!",
                "⑪ There are also bonuses for the first two teams to fully complete a level, and for being one of the first 3 players to finish a level!\n\n" + 
                "⑪ You can also punch people, I guess...",
                ChatColor.BOLD + "Scoring: \n" + ChatColor.RESET +
                        "⑪ +1 point for completing the course\n" +
                        "⑪ +1 point for every player outplaced\n" +
                        "⑪ +4 points for each player on the first full team to finish a course\n" +
                        "⑪ +2 points for each player on the second full team to finish a course\n" +
                        "⑪ +3 bonus points for placing Top 3 in a course\n"
        });
    }

    /**
     * Called when the game is loaded.
     * Starts the game.
     */
    public void start() {
        super.start();

        setDeathMessages();

        startFirstRound();

        setGameState(GameState.TUTORIAL);

        setTimer(30);
    }

    public void createScoreboard(Participant p) {
        createLine(19, ChatColor.RESET.toString(), p);
        createLine(4, ChatColor.RESET.toString() + ChatColor.RESET, p);

        updateInGameTeamScoreboard();
    }

    /**
     * Update scoreboard display on how many players have finished the round
     */
    public void updateFinishedPlayers() {
        for (Participant p : MBC.getInstance().getPlayers()) {
            createLine(2, ChatColor.YELLOW + "Finished: " + ChatColor.WHITE + finishedParticipants.size() + "/" + MBC.getInstance().getPlayers().size(), p);
        }
    }

    public void events() {
        if (getState().equals(GameState.TUTORIAL)) {
            if (timeRemaining == 0) {
                MBC.getInstance().sendMutedMessages();
                Bukkit.broadcastMessage("\n" + MBC.MBC_STRING_PREFIX + "The game is starting!\n");
                for (Participant p : MBC.getInstance().getPlayers()) {
                    p.getPlayer().addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, PotionEffect.INFINITE_DURATION, 10, false, false));
                }
                setGameState(GameState.STARTING);
                timeRemaining = 15;
            } else if (timeRemaining % 7 == 0) {
                Introduction();
            }
        } else if (getState().equals(GameState.STARTING)) {
            if (timeRemaining == 0) {
                for (Participant p : MBC.getInstance().getPlayers()) {
                    p.getPlayer().setGameMode(GameMode.SURVIVAL);
                }
                map.Barriers(false);
                setPVP(true);
                setGameState(GameState.ACTIVE);
                timeRemaining = 120;
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.playSound(p, Sound.MUSIC_DISC_OTHERSIDE, SoundCategory.RECORDS, 1, 1);
                }
            } else {
                Countdown();
            }
        } else if (getState().equals(GameState.ACTIVE)) {
            if (timeRemaining == 0) {
                for (Participant p : MBC.getInstance().getPlayers()) {
                    if (!finishedParticipants.contains(p)) {
                        flightEffects(p);
                        p.getPlayer().sendMessage(ChatColor.RED + "You didn't finish in time!");
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
            } else if (timeRemaining == 4) {
                if (cooldownID != -1) {
                    Bukkit.getScheduler().cancelTask(cooldownID);
                }
                roundOverGraphics();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.stopSound(Sound.MUSIC_DISC_OTHERSIDE, SoundCategory.RECORDS);
                }
            }
        } else if (getState().equals(GameState.END_GAME)) {
            if (timeRemaining == 36) {
                if (cooldownID != -1) {
                    Bukkit.getScheduler().cancelTask(cooldownID);
                }
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.stopSound(Sound.MUSIC_DISC_OTHERSIDE, SoundCategory.RECORDS);
                }
                gameOverGraphics();
            } else if (timeRemaining == 0) {
                removePlacedBlocks();
            }
            gameEndEvents();
        }
    }

    @Override
    public void onRestart() {
        roundNum = 0;
        maps = new ArrayList<>(
                Arrays.asList(new Pit(), new Meatball(), new Walls(),
                        new Cliffs(), new Glide(), new Skydive(), new Boats()
                )
        );
        removePlacedBlocks();
    }

    /**
     * Moved from startRound()
     * repurpose loadPlayers() however is best needed for tgttos
     */
    public void loadPlayers() {
        setPVP(false);
        if (map == null) {
            return;
        }

        getLogger().log(ChatColor.AQUA.toString() + ChatColor.BOLD + "New Map: " + ChatColor.WHITE + map.getName());

        for (Participant p : MBC.getInstance().getPlayersAndSpectators()) {
            p.getPlayer().setVelocity(new Vector(0, 0, 0));
            p.getPlayer().teleport(map.getSpawnLocation());
            p.getPlayer().removePotionEffect(PotionEffectType.WEAKNESS);
            if (p.getTeam().equals(MBC.getInstance().spectator)) {
                p.getPlayer().setGameMode(GameMode.SPECTATOR);
            }
            if (p.getPlayer().hasPotionEffect(PotionEffectType.NIGHT_VISION)) {
                p.getPlayer().removePotionEffect(PotionEffectType.NIGHT_VISION);
            }
            if (map instanceof Meatball) {
                p.getPlayer().addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, PotionEffect.INFINITE_DURATION, 10, false, false));
                canJump.put(p.getPlayer().getUniqueId(), true);
            }
        }
    }

    private void startFirstRound() {
        roundNum++;

        finishedParticipants = new ArrayList<>(MBC.getInstance().players.size());
        map = maps.get((int) (Math.random() * maps.size()));
        maps.remove(map);
        map.Barriers(true);

        createLineAll(22, ChatColor.AQUA + "" + ChatColor.BOLD + "Map: " + ChatColor.RESET + map.getName());
        createLineAll(21, ChatColor.GREEN + "Round: " + ChatColor.RESET + roundNum + "/6");
        updateFinishedPlayers();

        if (map != null) {
            loadPlayers();
        }

        for (Participant p : MBC.getInstance().getPlayers()) {
            p.getPlayer().getInventory().clear();
            p.getPlayer().setGameMode(GameMode.ADVENTURE);
            map.getWorld().spawnEntity(map.getEndLocation(), EntityType.CHICKEN);

            if (p.getPlayer().getAllowFlight()) {
                removeWinEffect(p);
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

        firstTeamBonus = false;
        secondTeamBonus = false;

        finishedParticipants = new ArrayList<>(MBC.getInstance().players.size());
        map = maps.get((int) (Math.random() * maps.size()));
        maps.remove(map);
        map.Barriers(true);

        createLineAll(22, ChatColor.AQUA + "" + ChatColor.BOLD + "Map: " + ChatColor.RESET + map.getName());
        createLineAll(21, ChatColor.GREEN + "Round: " + ChatColor.RESET + roundNum + "/6");
        updateFinishedPlayers();

        if (map != null) {
            loadPlayers();
        }


        for (Participant p : MBC.getInstance().getPlayers()) {
            p.getPlayer().getInventory().clear();
            p.getPlayer().setGameMode(GameMode.ADVENTURE);
            map.getWorld().spawnEntity(map.getEndLocation(), EntityType.CHICKEN);

            if (p.getPlayer().getAllowFlight()) {
                removeWinEffect(p);
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

        if (map instanceof Meatball) {
            cooldownID = Bukkit.getScheduler().scheduleSyncRepeatingTask(MBC.getInstance().plugin, () -> {

                Iterator<Map.Entry<Player, Long>> iterator = cooldowns.entrySet().iterator();
                while (iterator.hasNext()) {
                    Map.Entry<Player, Long> entry = iterator.next();
                    long storedTime = entry.getValue();

                    if (System.currentTimeMillis() - storedTime >= 10000) {
                        entry.getKey().spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(""));
                        canJump.put(entry.getKey().getUniqueId(), true);
                        iterator.remove();
                        continue;
                    }

                    long timeLeft = 10000 - (System.currentTimeMillis() - storedTime);
                    double secondsLeft = timeLeft / 1000.0;
                    int iCooldown = (int) secondsLeft;

                    String seconds = df.format(secondsLeft);

                    if (seconds.length() == 1) {
                        seconds+=".0";
                    }

                    String actionBarMessage = seconds + " seconds left §c" + "▐".repeat(iCooldown) + "§a" + "▐".repeat(10 - iCooldown);
                    entry.getKey().spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(actionBarMessage));
                }
            }, 20, 1);
        }

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
                deathMessages.add(line);
                i++;
            }
            br.close();
            fr.close();
        } catch (IOException e) {
            Bukkit.broadcastMessage(ChatColor.RED + "Error: " + e.getMessage());
        }
    }

    private void printDeathMessage(Participant p) {
        int rand = (int) (Math.random() * deathMessages.size());
        Bukkit.broadcastMessage(ChatColor.GRAY + deathMessages.get(rand).replace("{player}", p.getFormattedName() + ChatColor.GRAY));
    }

    private void Countdown() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (timeRemaining <= 10 && timeRemaining > 3) {
                p.sendTitle(ChatColor.AQUA + "Chaos begins in:", ChatColor.BOLD + ">" + timeRemaining + "<", 0, 20, 0);
            } else if (timeRemaining == 3) {
                p.sendTitle(ChatColor.AQUA + "Chaos begins in:", ChatColor.BOLD + ">" + ChatColor.RED + "" + ChatColor.BOLD + timeRemaining + ChatColor.WHITE + "" + ChatColor.BOLD + "<", 0, 20, 0);
            } else if (timeRemaining == 2) {
                p.sendTitle(ChatColor.AQUA + "Chaos begins in:", ChatColor.BOLD + ">" + ChatColor.YELLOW + "" + ChatColor.BOLD + timeRemaining + ChatColor.WHITE + "" + ChatColor.BOLD + "<", 0, 20, 0);
            } else if (timeRemaining == 1) {
                p.sendTitle(ChatColor.AQUA + "Chaos begins in:", ChatColor.BOLD + ">" + ChatColor.GREEN + "" + ChatColor.BOLD + timeRemaining + ChatColor.WHITE + "" + ChatColor.BOLD + "<", 0, 20, 0);
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
            if (e.getPlayer().getGameMode() != GameMode.SURVIVAL) {
                e.getPlayer().teleport(map.getSpawnLocation());
                return;
            }
            if (map instanceof Boats) {
                e.getPlayer().getInventory().addItem(new ItemStack(Material.OAK_BOAT));
            }
            e.getPlayer().setVelocity(new Vector(0, 0, 0));
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
            if (!firstTeamBonus) {
                Bukkit.broadcastMessage(p.getTeam().teamNameFormat() + ChatColor.GREEN + ChatColor.BOLD + " was the first full team to finish!");
                logger.log(p.getTeam().teamNameFormat() + ChatColor.GREEN + ChatColor.BOLD + " was the first full team to finish!");
                for (Participant teammate : p.getTeam().getPlayers()) {
                    teammate.addCurrentScore(FIRST_TEAM_BONUS);
                    teammate.getPlayer().sendMessage(ChatColor.GREEN+"Your team finished first and earned a " + (FIRST_TEAM_BONUS*MBC.getInstance().multiplier*p.getTeam().getPlayers().size()) + " point bonus!");
                }
                firstTeamBonus = true;
            } else {
                Bukkit.broadcastMessage(p.getTeam().teamNameFormat() + ChatColor.GREEN + "" + ChatColor.BOLD + " was the second full team to finish!");
                logger.log(p.getTeam().teamNameFormat() + ChatColor.GREEN + ChatColor.BOLD + " was the first full team to finish!");
                for (Participant teammate : p.getTeam().getPlayers()) {
                    teammate.addCurrentScore(SECOND_TEAM_BONUS);
                    teammate.getPlayer().sendMessage(ChatColor.GREEN+"Your team finished second and earned a " + (SECOND_TEAM_BONUS*MBC.getInstance().multiplier*p.getTeam().getPlayers().size()) + " point bonus!");
                }
                secondTeamBonus = true;
            }
        }
    }

    public void chickenClick(Participant p, Entity chicken) {
        finishedParticipants.add(p);
        int placement = finishedParticipants.size();
        p.addCurrentScore(PLACEMENT_POINTS * (MBC.getInstance().getPlayers().size() - placement) + COMPLETION_POINTS);
        String place = getPlace(placement);
        if (placement < 4) {
            p.addCurrentScore(TOP_THREE_BONUS);
        }
        chicken.remove();
        String finish = p.getFormattedName() + ChatColor.WHITE + " finished in " + ChatColor.AQUA + place + "!";

        getLogger().log(finish);

        Bukkit.broadcastMessage(finish);

        MBC.spawnFirework(p);
        p.getPlayer().setGameMode(GameMode.SPECTATOR);
        p.getPlayer().sendMessage(ChatColor.GREEN + "You finished in " + ChatColor.AQUA + place + ChatColor.GREEN + " place!");

        // check if all players on a team finished
        if (!firstTeamBonus || !secondTeamBonus)
            checkTeamFinish(p);

        updateFinishedPlayers();

        if (finishedParticipants.size() == MBC.getInstance().getPlayers().size()) {
            if (roundNum != MAX_ROUNDS) {
                setGameState(GameState.END_ROUND);
                timeRemaining = 5;
            } else {
                setGameState(GameState.END_GAME);
                timeRemaining = 37;
            }
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

        if (event.getRightClicked() instanceof Chicken && event.getPlayer().getGameMode() == GameMode.SURVIVAL) {
            chickenClick(Participant.getParticipant(event.getPlayer()), event.getRightClicked());
        }
    }

    @EventHandler
    public void boatExit(VehicleExitEvent e) {
        if (!isGameActive()) return;
        if (!(e.getVehicle() instanceof Boat)) return;
        if (!(e.getExited() instanceof Player)) return;

        Boat boat = (Boat) e.getVehicle();
        if (boat.getPassengers().size() > 1) {
            for (Entity en : boat.getPassengers()) {
                en.setVelocity(new Vector(0, 0, 0));
                Location l = en.getLocation().add(0, 1, 0);
                en.teleport(l);

                // give boat to all players
                if (en instanceof Player) {
                    ((Player) en).getInventory().addItem(new ItemStack(Material.OAK_BOAT));
                }
            }
        } else {
            Player entity = (Player) e.getExited();
            entity.setVelocity(new Vector(0, 0, 0));
            entity.getInventory().addItem(new ItemStack(Material.OAK_BOAT));
        }
        boat.remove();
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        e.setCancelled(true);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Check if the interaction is placing a boat or throwing meatball
        if (event.getItem() != null && (event.getItem().getType() == Material.OAK_BOAT || event.getItem().getType() == Material.SNOWBALL) && !getState().equals(GameState.ACTIVE)) {
            event.setCancelled(true);
            return;
        }

        if (!(map instanceof Meatball)) return;

        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.SPECTATOR) return;
        if (event.getAction().isRightClick() && getState().equals(GameState.ACTIVE) &&
           (player.getInventory().getItemInMainHand().getType() == Material.FEATHER ||
            player.getInventory().getItemInOffHand().getType() == Material.FEATHER)) {

            if (canJump.containsKey(player.getUniqueId()) && canJump.get(player.getUniqueId())) {
                player.setVelocity(player.getLocation().getDirection().multiply(1.25));
                map.getWorld().playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1, 2);
                canJump.put(player.getUniqueId(), false);
                cooldowns.put(player, System.currentTimeMillis());
            }
            else {
                long storedTime = cooldowns.get(player);

                long timeLeft = 10000 - (System.currentTimeMillis() - storedTime);
                double secondsLeft = timeLeft / 1000.0;

                String seconds = df.format(secondsLeft);

                if (seconds.length() == 1) {
                    seconds+=".0";
                }

                player.sendMessage(ChatColor.RED+seconds+" seconds left until you can use this");
            }
        }
    }

    @EventHandler
    public void blockPlaceEvent(BlockPlaceEvent e) {
        if (!isGameActive()) {
            e.setCancelled(true);
        }

        Player p = e.getPlayer();

        if (e.getBlock().getType().toString().endsWith("WOOL")) {
            // if block was placed too close to spawn, don't place it (only for Meatball+Skydive for now)
            if ((map instanceof Meatball || map instanceof Skydive) && e.getBlock().getLocation().distanceSquared(map.getSpawnLocation()) < 9) {
                p.sendMessage(ChatColor.RED+"Move further away from spawn before building!");
                e.setCancelled(true);
                return;
            }

            // add to placed blocks
            placedBlocks.add(e.getBlock().getLocation());
            // if block was wool, give appropriate amount back
            String wool = e.getBlock().getType().toString();
            // check item slot
            if (e.getHand() == EquipmentSlot.HAND) {
                int index = p.getInventory().getHeldItemSlot();
                int amt = Objects.requireNonNull(p.getInventory().getItem(index)).getAmount();
                p.getInventory().setItem(index, new ItemStack(Objects.requireNonNull(Material.getMaterial(wool)), amt));
                return;
            }
            if (e.getHand() == EquipmentSlot.OFF_HAND) {
                int amt = Objects.requireNonNull(p.getInventory().getItem(40)).getAmount();
                p.getInventory().setItem(40, new ItemStack(Objects.requireNonNull(Material.getMaterial(wool)), amt));
            }
        }
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent e) {
        if (!(e.getEntity() instanceof Snowball)) return;
        if (!(e.getEntity().getShooter() instanceof Player)) return;

        // deal slight knockback to other players
        if (e.getHitEntity() != null && e.getHitEntity() instanceof Player) {
            Player p = (Player) e.getHitEntity();
            snowballHit((Snowball) e.getEntity(), p);
        }
    }
}
