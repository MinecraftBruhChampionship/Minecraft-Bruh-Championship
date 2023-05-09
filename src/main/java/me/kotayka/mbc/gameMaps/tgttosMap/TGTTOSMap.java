package me.kotayka.mbc.gameMaps.tgttosMap;

import me.kotayka.mbc.gameMaps.Map;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class TGTTOSMap extends Map {

    private Location[] spawn;
    private Location[] end;
    private int deathY;
    private final String name;
    private List<ItemStack> items;

    public TGTTOSMap(String name, ItemStack[] i) {
        super(Bukkit.getWorld("TGTTOS"));
        this.name = name;
        items = new ArrayList<>(Arrays.asList(i));
    }

    public void loadMap(Location[] spawn, Location[] end, int spawnDeath) {
        this.spawn = spawn;
        this.end = end;
        this.deathY = spawnDeath;
    }

    public Location getSpawnLocation() {
        double x1 = Math.min(spawn[0].getX(), spawn[1].getX());
        double y1 = Math.min(spawn[0].getY(), spawn[1].getY());
        double z1 = Math.min(spawn[0].getZ(), spawn[1].getZ());

        double x2 = Math.max(spawn[0].getX(), spawn[1].getX());
        double y2 = Math.max(spawn[0].getY(), spawn[1].getY());
        double z2 = Math.max(spawn[0].getZ(), spawn[1].getZ());

        return new Location(getWorld(), Math.random()*(x2-x1)+x1, Math.random()*(y2-y1)+y1, Math.random()*(z2-z1)+z1);
    }

    public Location getEndLocation() {
        double x1 = Math.min(end[0].getX(), end[1].getX());
        double y1 = Math.min(end[0].getY(), end[1].getY());
        double z1 = Math.min(end[0].getZ(), end[1].getZ());

        double x2 = Math.max(end[0].getX(), end[1].getX());
        double y2 = Math.max(end[0].getY(), end[1].getY());
        double z2 = Math.max(end[0].getZ(), end[1].getZ());

        return new Location(getWorld(), Math.random()*(x2-x1)+x1, Math.random()*(y2-y1)+y1, Math.random()*(z2-z1)+z1);
    }

    public List<ItemStack> getItems() {
        return items;
    }

    public int getDeathY() {
        return deathY;
    }

    public String getName() {
        return name;
    }

}