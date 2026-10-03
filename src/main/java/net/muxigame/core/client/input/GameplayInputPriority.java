package net.muxigame.core.client.input;

import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.Screen;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Client-only arbitration; gameplay owners retain their original actions and packets. */
public final class GameplayInputPriority {
    private GameplayInputPriority() {}
    private static final Map<String,BooleanSupplier> F_CLAIMS=new LinkedHashMap<>();
    private static final Map<String,Runnable> F_ACTIONS=new HashMap<>();
    private static final Set<String> WHEELS=new HashSet<>(Set.of(GlobalKeyBindingPlan.WHEEL));
    private static boolean fHeldClaim,bHeldReserved;
    private static Frame frame;
    private record Frame(int key,int action,int modifiers,boolean gameplay,boolean fClaim,Runnable dispatch) {}
    /** Outbreak registers a pure current-context predicate, not an action callback. */
    public static void registerPlainFClaim(String owner,BooleanSupplier claims){
        if(owner==null||owner.isBlank())throw new IllegalArgumentException("Input owner required");
        F_CLAIMS.put(owner,Objects.requireNonNull(claims));
    }
    public static void registerPlainFAction(String owner,BooleanSupplier claims,Runnable action){
        registerPlainFClaim(owner,claims);F_ACTIONS.put(owner,Objects.requireNonNull(action));
    }
    /** Zombie registers its native Alt+B KeyMapping name; raw event opening remains its job. */
    public static void registerWeaponWheelBinding(String name){
        if(name==null||name.isBlank())throw new IllegalArgumentException("Wheel binding required");
        WHEELS.add(name);
    }
    public static boolean begin(long window,int key,int action,int modifiers){
        var mc=Minecraft.getInstance();
        if(mc==null||mc.getWindow()==null||window!=mc.getWindow().getWindow())return false;
        boolean gameplay=mc.player!=null&&mc.screen==null;
        boolean claim=fHeldClaim;
        Runnable dispatch=null;
        if(gameplay && key==70 && action==1 && (modifiers&12)==0){
            claim=false;
            for(var entry:F_CLAIMS.entrySet()){
                try{if(entry.getValue().getAsBoolean()){claim=true;dispatch=F_ACTIONS.get(entry.getKey());break;}}
                catch(RuntimeException failure){claim=true;break;}
            }
            fHeldClaim=claim;
        }
        if(gameplay && key==66 && action!=0 && (modifiers&4)!=0)bHeldReserved=true;
        frame=new Frame(key,action,modifiers,gameplay,claim,dispatch);
        return true;
    }
    /** Called once after all claims are captured, before vanilla/mod key handlers. */
    public static void dispatchPlainF(){
        var current=frame;if(current==null||current.dispatch==null)return;
        frame=new Frame(current.key,current.action,current.modifiers,current.gameplay,current.fClaim,null);
        try{current.dispatch.run();}
        catch(RuntimeException failure){org.slf4j.LoggerFactory.getLogger("muxi-game-core/input").warn("Contextual F request failed; same-event gun input remains blocked",failure);}
    }
    public static void end(int key,int action){
        // A matches() listener can open a Screen midway through this same event.
        // Drain blocked click queues while the original input snapshot is retained.
        var mc=Minecraft.getInstance();
        if(frame!=null&&frame.gameplay&&mc!=null&&mc.options!=null){
            for(var mapping:mc.options.keyMappings)if(mapping.getKey().getValue()==key&&!allowed(mapping,key)){
                mapping.setDown(false);while(mapping.consumeClick()){}
            }
            if(key==66&&action!=0&&(frame.modifiers&4)!=0)bHeldReserved=true;
            if(key==70&&action!=0&&frame.fClaim)fHeldClaim=true;
        }
        if(action==0){if(key==70)fHeldClaim=false;if(key==66)bHeldReserved=false;}
        frame=null;
    }
    public static void releaseAll(){fHeldClaim=false;bHeldReserved=false;}
    public static boolean allowed(KeyMapping mapping,int key){
        var mc=Minecraft.getInstance();
        if(mc==null||mc.getWindow()==null)return true;
        boolean gameplay=frame!=null?frame.gameplay:mc.player!=null&&mc.screen==null;
        int modifiers=frame!=null?frame.modifiers:(Screen.hasShiftDown()?1:0)|(Screen.hasControlDown()?2:0)|(Screen.hasAltDown()?4:0);
        boolean claimed=frame!=null?frame.fClaim:fHeldClaim;
        var name=mapping.getName();
        // Preserve explicit related modifier customizations as well as their saved values.
        int expected=switch(mapping.getKeyModifier()){case NONE->0;case SHIFT->1;case CONTROL->2;case ALT->4;};
        if(gameplay&&key==258&&(name.equals(GlobalKeyBindingPlan.SWAP)||name.equals(GlobalKeyBindingPlan.LIST)))return (modifiers&15)==expected;
        if(gameplay&&key==70&&name.equals(GlobalKeyBindingPlan.INTERACT))return (expected==0?(modifiers&12)==0:(modifiers&15)==expected)&&!(claimed&&(modifiers&12)==0);
        return allowed(mapping.getName(),key,modifiers,gameplay,claimed,bHeldReserved,WHEELS.contains(mapping.getName()));
    }
    /** Pure decision shared by native mapping hooks and focused regression tests. */
    static boolean allowed(String name,int key,int mods,boolean gameplay,boolean fClaim,boolean bReserved,boolean wheel){
        if(!gameplay&&!bReserved)return true;
        if(key==66){
            if(wheel)return (mods&15)==4;
            if((mods&4)!=0||bReserved)return false;
        }
        if(key==70 && name.equals(GlobalKeyBindingPlan.INTERACT) && (fClaim||(mods&12)!=0))return false;
        if(key==258 && name.equals(GlobalKeyBindingPlan.SWAP) && (mods&15)!=0)return false;
        if(key==258 && name.equals(GlobalKeyBindingPlan.LIST) && (mods&15)!=2)return false;
        return true;
    }
}
