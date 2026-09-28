package net.muxigame.core.feature.challenge;

import net.minecraft.nbt.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.muxigame.core.mixin.PlayerListSaveInvoker;
import net.neoforged.fml.ModList;

/** Original vanilla inventory and return coordinates are saved before the temporary kit is installed. */
public final class ChallengeInventory {
    public static final String KEY="muxi_challenge_return";
    private ChallengeInventory() {}
    public static void save(ServerPlayer p) { ((PlayerListSaveInvoker)p.server.getPlayerList()).muxi$savePlayer(p); }
    public static boolean pending(ServerPlayer p) { return p.getPersistentData().contains(KEY,Tag.TAG_COMPOUND); }
    public static void enter(ServerPlayer p) {
        enter(p,ChallengeLoadout.defaults());
    }
    public static void enter(ServerPlayer p,ChallengeLoadout loadout) {
        loadout.validate(p);
        if(pending(p)) throw new IllegalStateException("Return snapshot already exists");
        if(!p.containerMenu.getCarried().isEmpty()) throw new IllegalArgumentException("请先放下鼠标上拿着的物品");
        if(!ModList.get().isLoaded("tacz"))throw new IllegalArgumentException("挑战需要 TaCZ 枪械模组");
        ItemStack primary=loadout.primary()>=0?loadout.first().copyWithCount(1):ChallengeGuns.gun(p,"tacz:hk_mp5a5");
        ItemStack secondary=loadout.secondary()>=0?loadout.second().copyWithCount(1):ChallengeGuns.gun(p,"tacz:glock_17");
        if(!ChallengeGuns.isGun(primary)||!ChallengeGuns.isGun(secondary))throw new IllegalArgumentException("挑战武器模型不可用");
        p.closeContainer();
        CompoundTag t=new CompoundTag(); t.put("inventory",p.getInventory().save(new ListTag()));
        t.putString("dimension",p.level().dimension().location().toString());
        t.putDouble("x",p.getX());t.putDouble("y",p.getY());t.putDouble("z",p.getZ());
        t.putFloat("yaw",p.getYRot());t.putFloat("pitch",p.getXRot());
        t.putFloat("health",p.getHealth()); t.putInt("food",p.getFoodData().getFoodLevel());t.putFloat("saturation",p.getFoodData().getSaturationLevel());
        t.putInt("mode",p.gameMode.getGameModeForPlayer().getId()); t.putInt("selected",p.getInventory().selected);
        p.getPersistentData().put(KEY,t); save(p);
        p.getInventory().clearContent();
        p.getInventory().setItem(0,primary);
        p.getInventory().setItem(1,secondary);
        p.getInventory().setItem(2,new ItemStack(Items.COOKED_BEEF,16));
        p.getInventory().setItem(36,new ItemStack(Items.IRON_BOOTS));
        p.getInventory().setItem(37,new ItemStack(Items.IRON_LEGGINGS));
        p.getInventory().setItem(38,new ItemStack(Items.IRON_CHESTPLATE));
        p.getInventory().setItem(39,new ItemStack(Items.IRON_HELMET));
        p.getInventory().setItem(40,new ItemStack(Items.SHIELD));
        ChallengeGuns.refill(p);
        p.getInventory().selected=0;p.setGameMode(GameType.ADVENTURE);p.setHealth(p.getMaxHealth());
        p.getFoodData().setFoodLevel(20);p.getFoodData().setSaturation(10);p.inventoryMenu.broadcastChanges();
    }
    public static void restore(ServerPlayer p) {
        if(!pending(p)) return;
        CompoundTag t=p.getPersistentData().getCompound(KEY);
        var level=p.server.getLevel(ResourceKey.create(Registries.DIMENSION,ResourceLocation.parse(t.getString("dimension"))));
        if(level==null) level=p.server.overworld();
        p.closeContainer();p.getInventory().load(t.getList("inventory",Tag.TAG_COMPOUND));
        p.getInventory().selected=Math.max(0,Math.min(8,t.getInt("selected")));
        p.setGameMode(GameType.byId(t.getInt("mode")));p.setHealth(Math.min(p.getMaxHealth(),Math.max(1,t.getFloat("health"))));
        p.getFoodData().setFoodLevel(t.getInt("food"));p.getFoodData().setSaturation(t.getFloat("saturation"));
        p.clearFire();p.fallDistance=0;
        p.teleportTo(level,t.getDouble("x"),t.getDouble("y"),t.getDouble("z"),t.getFloat("yaw"),t.getFloat("pitch"));
        p.getPersistentData().remove(KEY);p.inventoryMenu.broadcastChanges();save(p);
    }
}
