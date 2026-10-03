package net.muxigame.core.client.input;

import java.util.*;

/** Key-level migration only; never rewrites unrelated options or user bindings. */
public final class GlobalKeyBindingPlan {
    private GlobalKeyBindingPlan() {}
    public static final String SWAP="key.swapOffhand", LIST="key.playerlist",
        INTERACT="key.tacz.interact.desc", STORAGE="key.toms_storage.open_terminal",
        WHEEL="key.muxi_game_core.challenge_wheel";
    public record Change(String name,String legacy,String desired) {}
    public static final List<Change> CHANGES=List.of(
        new Change(SWAP,"key.keyboard.f","key.keyboard.tab"),
        new Change(LIST,"key.keyboard.tab","key.keyboard.tab:CONTROL"),
        new Change(INTERACT,"key.keyboard.o","key.keyboard.f"),
        new Change(STORAGE,"key.keyboard.b:ALT","key.keyboard.t:ALT"),
        new Change(WHEEL,"key.keyboard.b","key.keyboard.b:ALT"));
    public static String defaultBinding(String name){
        for(var change:CHANGES)if(change.name.equals(name))return change.desired;
        return null;
    }
    /** Only legacy defaults migrate; distinct user choices are preserved. */
    public static Map<String,String> changes(Map<String,String> current,Map<String,String> applied){
        var result=new LinkedHashMap<String,String>();
        for(var change:CHANGES)if(!change.desired.equals(applied.get(change.name)) &&
            (change.legacy.equals(current.get(change.name)) ||
             change.name.equals(WHEEL) && "key.keyboard.unknown".equals(current.get(change.name))))result.put(change.name,change.desired);
        return result;
    }
    public static String updateOptions(String text,Map<String,String> changes){
        if(changes.isEmpty())return text;
        var pending=new LinkedHashMap<>(changes);var out=new StringBuilder();
        // Keep all original lines/newlines, including unknown keys and comments.
        var lines=java.util.regex.Pattern.compile(".*(?:\\r\\n|\\n|\\r|$)").matcher(text);
        while(lines.find()){
            var raw=lines.group();if(raw.isEmpty())continue;
            int ending=raw.endsWith("\r\n")?2:(raw.endsWith("\n")||raw.endsWith("\r"))?1:0;
            var line=raw.substring(0,raw.length()-ending);int colon=line.indexOf(':');
            if(colon>0 && line.startsWith("key_")){
                var name=line.substring(4,colon);var value=changes.get(name);
                if(value!=null){line=line.substring(0,colon+1)+value;pending.remove(name);}
            }
            out.append(line).append(raw.substring(raw.length()-ending));
        }
        if(!pending.isEmpty()){
            var newline=text.contains("\r\n")?"\r\n":"\n";
            if(out.length()>0 && out.charAt(out.length()-1)!='\n' && out.charAt(out.length()-1)!='\r')out.append(newline);
            pending.forEach((name,value)->out.append("key_").append(name).append(':').append(value).append(newline));
        }
        return out.toString();
    }
}
