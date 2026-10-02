package net.muxigame.terminal.client.map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import java.lang.ref.WeakReference;
import java.util.function.BooleanSupplier;

/** Opens the installed native Xaero screen. No key registration, network mutation or map settings. */
public final class NativeMapLauncher {
    private NativeMapLauncher() {}

    public static void open(BooleanSupplier returnContext) throws ReflectiveOperationException {
        var mc=Minecraft.getInstance();
        if(mc.player==null || !mc.player.isAlive() || mc.level==null || mc.getConnection()==null || mc.screen==null)
            throw new IllegalStateException("当前游戏地图不可用");
        var sessionClass=Class.forName("xaero.map.WorldMapSession");
        var session=sessionClass.getMethod("getCurrentSession").invoke(null);
        if(session==null || !Boolean.TRUE.equals(sessionClass.getMethod("isUsable").invoke(session)))
            throw new IllegalStateException("Xaero 地图尚未就绪，请稍后重试");
        var processor=sessionClass.getMethod("getMapProcessor").invoke(session);
        if(processor==null)throw new IllegalStateException("Xaero 地图尚未就绪");
        Screen previous=mc.screen;
        var connection=new WeakReference<Object>(mc.getConnection());
        Screen back=new Screen(Component.literal("返回终端")) {
            @Override protected void init() {
                if(connection.get()!=null && connection.get()==mc.getConnection()
                    && mc.player!=null && mc.player.isAlive() && mc.level!=null && returnContext.getAsBoolean())mc.setScreen(previous);
                else mc.setScreen(null);
            }
            @Override public boolean isPauseScreen(){return false;}
        };
        var mapClass=Class.forName("xaero.map.gui.GuiMap");
        var processorClass=Class.forName("xaero.map.MapProcessor");
        var map=mapClass.getConstructor(Screen.class,Screen.class,processorClass,Entity.class)
            .newInstance(back,back,processor,mc.player);
        if(!returnContext.getAsBoolean())throw new IllegalStateException("终端页面已变化，请重新打开地图");
        if(!(map instanceof Screen screen))throw new IllegalStateException("地图版本不匹配");
        mc.setScreen(screen);
    }
}
