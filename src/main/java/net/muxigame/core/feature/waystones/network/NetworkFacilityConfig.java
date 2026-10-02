package net.muxigame.core.feature.waystones.network;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;

/** Operator-owned local configuration. Reading never creates a file or a world facility. */
public record NetworkFacilityConfig(int associationRadius, int associationVertical,
                                    int entranceRadius, int entranceVertical, List<Facility> facilities) {
    public record Facility(String dimension, int x, int y, int z, UUID stone) {}
    public NetworkFacilityConfig { facilities=List.copyOf(facilities); }
    public static NetworkFacilityConfig empty() { return new NetworkFacilityConfig(4,3,4,3,List.of()); }
    public static NetworkFacilityConfig read(Path path) {
        try {
            if(!Files.isRegularFile(path) || Files.size(path)>65536) return empty();
            return parse(Files.readString(path));
        } catch(Exception invalid) { return empty(); }
    }
    public static NetworkFacilityConfig parse(String json) {
        JsonObject root=JsonParser.parseString(json).getAsJsonObject();
        if(integer(root.get("version"))!=1) throw new IllegalArgumentException("Unsupported facility config");
        int ar=number(root,"associationRadius",4,1,8), av=number(root,"associationVertical",3,0,8);
        int er=number(root,"entranceRadius",4,1,8), ev=number(root,"entranceVertical",3,0,8);
        JsonArray entries=root.getAsJsonArray("facilities");
        if(entries==null || entries.size()>128) throw new IllegalArgumentException("Invalid facilities");
        List<Facility> facilities=new ArrayList<>(); Set<String> keys=new HashSet<>();
        for(JsonElement item:entries) {
            JsonObject entry=item.getAsJsonObject(); String dim=entry.get("dimension").getAsString();
            if(dim.length()>128 || dim.contains("://") || !dim.matches("[a-z0-9_.-]+:[a-z0-9_/.-]+")) throw new IllegalArgumentException("Invalid dimension");
            JsonArray pos=entry.getAsJsonArray("portalPos");
            if(pos.size()!=3) throw new IllegalArgumentException("Invalid portal position");
            int x=integer(pos.get(0)),y=integer(pos.get(1)),z=integer(pos.get(2));
            if(Math.abs((long)x)>30000000 || Math.abs((long)z)>30000000 || Math.abs((long)y)>4096) throw new IllegalArgumentException("Invalid position");
            String rawUid=entry.get("waystoneUid").getAsString();UUID uid=UUID.fromString(rawUid);
            if(!uid.toString().equalsIgnoreCase(rawUid))throw new IllegalArgumentException("Noncanonical stone UID");
            if(!keys.add(dim+"/"+x+"/"+y+"/"+z)) throw new IllegalArgumentException("Duplicate facility");
            facilities.add(new Facility(dim,x,y,z,uid));
        }
        return new NetworkFacilityConfig(ar,av,er,ev,facilities);
    }
    private static int number(JsonObject root,String key,int fallback,int min,int max) {
        int value=root.has(key)?integer(root.get(key)):fallback;
        if(value<min || value>max) throw new IllegalArgumentException("Invalid range: "+key);
        return value;
    }
    private static int integer(JsonElement value) {
        if(value==null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("Expected integer");
        return value.getAsBigDecimal().toBigIntegerExact().intValueExact();
    }
}
