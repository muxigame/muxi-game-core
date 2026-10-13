package net.muxigame.core.feature.ysmassets;

import com.google.gson.*;
import java.util.*;

/** Trusted machine export only. Never parse client-supplied JSON as authority. */
public record YsmAssetSnapshot(long revision, Map<String,Content> contents,
                               Map<String,Reference> references, Map<String,String> selections) {
    public record Reference(String modelId,String ownerUid,String visibility,String contentId) {}
    public record Content(String contentId,String sha256,long size,String validationProfile,
                          String validationReceiptId,boolean published,List<Reference> references) {}
    public YsmAssetSnapshot {
        contents=Map.copyOf(contents);references=Map.copyOf(references);selections=Map.copyOf(selections);
    }
    static YsmAssetSnapshot empty(){return new YsmAssetSnapshot(-1,Map.of(),Map.of(),Map.of());}
    public static String uid(String value){
        if(value==null || !value.matches("[1-9][0-9]{4,15}"))throw bad();return value;
    }
    static String id(String value,String prefix){
        if(value==null || !value.matches(prefix+"[a-f0-9]{32}"))throw bad();return value;
    }
    static IllegalArgumentException bad(){return new IllegalArgumentException("Invalid trusted YSM asset snapshot");}
    static String text(JsonObject o,String key){
        var v=o.get(key);if(v==null||!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isString())throw bad();return v.getAsString();
    }
    static boolean flag(JsonObject o,String key){
        var v=o.get(key);if(v==null||!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isBoolean())throw bad();return v.getAsBoolean();
    }
    static long number(JsonObject o,String key){
        var v=o.get(key);if(v==null||!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isNumber()||!v.getAsString().matches("0|[1-9][0-9]*"))throw bad();
        try{return Long.parseLong(v.getAsString());}catch(NumberFormatException e){throw bad();}
    }
    static JsonArray array(JsonObject o,String key){var v=o.get(key);if(v==null||!v.isJsonArray())throw bad();return v.getAsJsonArray();}
    static Set<String> strings(JsonArray a,boolean owner){
        var result=new HashSet<String>();for(var v:a){if(!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isString())throw bad();var s=v.getAsString();if(owner)uid(s);else id(s,"m_");if(!result.add(s))throw bad();}return result;
    }
    /** Verified receipts are bound metadata, not proof that this Java class validated YSM bytes. */
    public static YsmAssetSnapshot parseTrusted(String json){
        if(json==null||json.length()>2_097_152)throw bad();
        try{
            var root=JsonParser.parseString(json).getAsJsonObject();long revision=number(root,"revision");
            if(root.has("active")&&flag(root,"active") || root.has("selectEnabled")&&flag(root,"selectEnabled"))throw bad();
            var models=array(root,"models");if(models.size()>10_000)throw bad();
            var contents=new TreeMap<String,Content>();var references=new TreeMap<String,Reference>();var digests=new HashSet<String>();
            for(var item:models){
                var o=item.getAsJsonObject();String contentId=id(text(o,"contentId"),"p_"),sha=text(o,"sha256");long size=number(o,"size");
                if(!sha.matches("[a-f0-9]{64}")||!digests.add(sha)||size<1||size>64L*1024*1024||!text(o,"storageKey").equals("verified/"+sha+".bin")||!text(o,"validationStatus").equals("verified"))throw bad();
                String profile=text(o,"validationProfile"),receipt=text(o,"validationReceiptId");if(profile.isBlank()||profile.length()>128||!receipt.matches("[A-Za-z0-9._:-]{8,128}"))throw bad();
                var owners=strings(array(o,"ownerUids"),true);var ids=strings(array(o,"modelIds"),false);var refs=new ArrayList<Reference>();
                for(var value:array(o,"references")){
                    var q=value.getAsJsonObject();String modelId=id(text(q,"modelId"),"m_"),owner=uid(text(q,"ownerUid")),visibility=text(q,"visibility");
                    if(!visibility.equals("public")&&!visibility.equals("private"))throw bad();var ref=new Reference(modelId,owner,visibility,contentId);
                    if(references.putIfAbsent(modelId,ref)!=null)throw bad();refs.add(ref);
                }
                if(refs.isEmpty()||refs.size()>10_000||!owners.equals(new HashSet<>(refs.stream().map(Reference::ownerUid).toList()))||!ids.equals(new HashSet<>(refs.stream().map(Reference::modelId).toList())))throw bad();
                boolean published=flag(o,"public");if(published!=refs.stream().anyMatch(q->q.visibility().equals("public")))throw bad();
                refs.sort(Comparator.comparing(Reference::modelId));var content=new Content(contentId,sha,size,profile,receipt,published,List.copyOf(refs));if(contents.putIfAbsent(contentId,content)!=null)throw bad();
            }
            var selections=new TreeMap<String,String>();
            for(var item:array(root,"selections")){
                var o=item.getAsJsonObject();String owner=uid(text(o,"uid")),model=text(o,"modelId"),content=id(text(o,"contentId"),"p_");
                var c=contents.get(content);var ref=references.get(model);
                if(c==null || !(c.published()&&model.equals(content) || ref!=null&&ref.ownerUid().equals(owner)&&ref.contentId().equals(content)) || selections.putIfAbsent(owner,model)!=null)throw bad();
            }
            return new YsmAssetSnapshot(revision,contents,references,selections);
        }catch(RuntimeException failure){throw bad();}
    }
    /** Private m_ handles are owner-only even when another reference publishes the same bytes. */
    Content permitted(String uid,String modelId){
        uid(uid);if(modelId==null)return null;
        if(modelId.startsWith("p_")){var c=contents.get(modelId);return c!=null&&c.published()?c:null;}
        var ref=references.get(modelId);return ref!=null&&ref.ownerUid().equals(uid)?contents.get(ref.contentId()):null;
    }
    public List<String> visibleHandles(String uid){
        uid(uid);var out=new TreeSet<String>();for(var c:contents.values())if(c.published())out.add(c.contentId());for(var ref:references.values())if(ref.ownerUid().equals(uid))out.add(ref.modelId());return List.copyOf(out);
    }
}
