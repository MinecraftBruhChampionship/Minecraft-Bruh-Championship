package me.kotayka.mbc.gameMaps.aceRaceMap;

import me.kotayka.mbc.Participant;
import me.kotayka.mbc.gamePlayers.AceRacePlayer;
import me.kotayka.mbc.gamePlayers.GamePlayer;
import me.kotayka.mbc.games.AceRace;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;

public class Biomes extends AceRaceMap {
    Location[] respawns = {
            new Location(world, 3,26, 150, 90, 0),  // default
            new Location(world, -59, 26, 140, 125, 0),
            new Location(world, -119, 27, 77, 135, 0),
            new Location(world, -138, 26, -58, -160, 0),
            new Location(world, -49, 33, -144, -90, 0),
            new Location(world, 57, 26, -137, -90, 0),
            new Location(world, 103, 26, -109, -44, 0),
            new Location(world, 138, 26, -62, -10, 0),
            new Location(world, 136, 26, 67, 35, 0),
            new Location(world, 95, 25, 119, 65, 0)
    };

    Location[] checkpoints = {
            new Location(world, 3, 26, 150),    // default
            new Location(world, -22, 28, 150),
            new Location(world, -109, 32, 103),
            new Location(world, -150, 28, -25),
            new Location(world, -77, 54, -129),
            new Location(world, 7, 24, -144),
            new Location(world, 102, 30, -110),
            new Location(world, 119, 34, -93),
            new Location(world, 148, 25, 27),
            new Location(world, 125, 26, 84)
    };

    public Biomes() {
        super(0, "Lava");
        mapName = "Biomes";
        loadCheckpoints(respawns, checkpoints);
    }
}
