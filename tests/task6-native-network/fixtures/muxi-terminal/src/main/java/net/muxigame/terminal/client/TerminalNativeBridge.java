package net.muxigame.terminal.client;

import com.cinemamod.mcef.MCEF;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.browser.CefMessageRouter;
import org.cef.callback.CefQueryCallback;
import org.cef.handler.CefMessageRouterHandlerAdapter;
import java.util.Base64;
import com.google.gson.Gson;
import com.google.gson.JsonParser;
import org.cef.CefClient;

/** A deliberately tiny capability bridge. Web apps do not get arbitrary Java access. */
public final class TerminalNativeBridge {
    private static final Gson JSON=new Gson();

    private TerminalNativeBridge() {}

    public static void installWhenReady() {
        // Routers are attached only to independently owned trusted clients by TerminalViewClient.
    }

    static void attach(CefClient client) {
        // Keep the private trusted client; WebDisplays owns the default query name.
        var config=new CefMessageRouter.CefMessageRouterConfig("muxiTerminalQuery","muxiTerminalCancel");
        client.addMessageRouter(CefMessageRouter.create(config,new Handler()));
    }

    private static final class Handler extends CefMessageRouterHandlerAdapter {
        @Override
        public boolean onQuery(CefBrowser browser, CefFrame frame, long queryId, String request,
                               boolean persistent, CefQueryCallback callback) {
            Minecraft mc = Minecraft.getInstance();
            if(!TerminalBrowserSession.trusted(browser,frame)) {
                callback.failure(403,"Native bridge is available only to the owned local terminal main frame");
                return true;
            }
            if(persistent || request==null || request.length()>8192){callback.failure(400,"Invalid terminal request");return true;}
            long epoch=TerminalBrowserSession.generation();String document=frame.getURL();
            CefQueryCallback guarded=new CefQueryCallback(){
                private boolean valid(){return TerminalBrowserSession.generation()==epoch && TerminalBrowserSession.trusted(browser,frame) && document.equals(frame.getURL());}
                @Override public void success(String response){if(valid())callback.success(response);}
                @Override public void failure(int code,String message){if(valid())callback.failure(code,message);}
            };
            Object musicScreen=mc.screen, musicConnection=mc.getConnection();
            boolean musicOwner=browser==TerminalBrowserSession.content()
                && TerminalBrowserSession.state().kind()==TerminalBrowserSession.Kind.BUILTIN
                && musicScreen instanceof TerminalScreen;
            java.util.function.BooleanSupplier musicValid=()->mc.screen==musicScreen
                && mc.getConnection()==musicConnection && mc.player!=null && mc.player.isAlive()
                && TerminalBrowserSession.generation()==epoch && TerminalBrowserSession.trusted(browser,frame)
                && document.equals(frame.getURL()) && TerminalBrowserSession.activeBrowser()==browser
                && TerminalBrowserSession.contentVisible();
            if(net.muxigame.terminal.client.music.TerminalMusicBridge.handle(browser,frame,musicOwner,
                    request,guarded,musicValid))return true;
            mc.execute(()->{
                if(TerminalBrowserSession.generation()!=epoch || !TerminalBrowserSession.trusted(browser,frame) || !document.equals(frame.getURL()))return;
                dispatch(mc,browser,frame,request,guarded);
            });
            return true;
        }

        private void dispatch(Minecraft mc,CefBrowser browser,CefFrame frame,String request,CefQueryCallback callback){
            if(TerminalNativeMapBridge.dispatch(browser,frame,request,callback))return;
            if(TerminalFriendsBridge.dispatch(browser,frame,request,callback))return;
            if(TerminalAlbumBridge.dispatch(browser,frame,request,callback))return;
            if(TerminalCameraBridge.dispatch(browser,frame,request,callback))return;
            if(request.startsWith("terminal.launch:") || request.startsWith("terminal.reveal:") || request.startsWith("terminal.cancel-launch:")){
                if(!TerminalBrowserSession.isShell(browser)){callback.failure(403,"Only the trusted shell controls views");return;}
                try{
                    if(request.startsWith("terminal.cancel-launch:")){callback.success("{\"ok\":true}");TerminalBrowserSession.cancelLaunch(request.substring(23));return;}
                    var data=JsonParser.parseString(request.substring(request.indexOf(':')+1)).getAsJsonObject();
                    String token=data.get("token").getAsString();if(!token.matches("[0-9]{1,16}"))throw new IllegalArgumentException("Invalid launch token");
                    if(request.startsWith("terminal.reveal:")){
                        boolean ok=TerminalBrowserSession.revealView(data.get("viewId").getAsLong(),token,data.get("visible").getAsBoolean(),data.has("reducedMotion") && data.get("reducedMotion").getAsBoolean());
                        callback.success("{\"ok\":"+ok+"}");return;
                    }
                    String kind=data.get("kind").getAsString(),id=data.get("id").getAsString();
                    if(kind.equals("builtin") && (id.equals("guide") || id.equals("tasks") || id.equals("games") || id.equals("camera") || id.equals("friends") || id.equals("album") || id.equals("settings") || id.equals("music"))){
                        callback.success("{\"ok\":true}");TerminalBrowserSession.beginLaunch(token,TerminalBrowserSession.Kind.BUILTIN,id);TerminalBrowserSession.openApp(id);return;
                    }
                    if(kind.equals("web")){
                        var app=TerminalBrowserSession.personalApps().get(id);callback.success("{\"ok\":true}");
                        TerminalBrowserSession.beginLaunch(token,TerminalBrowserSession.Kind.WEB,app.name());TerminalBrowserSession.openWebApp(app.url(),app.name());return;
                    }
                    if(kind.equals("account")){
                        callback.success("{\"ok\":true}");TerminalBrowserSession.beginLaunch(token,TerminalBrowserSession.Kind.ACCOUNT,"木夕账户");TerminalPassportNavigation.open();return;
                    }
                    throw new IllegalArgumentException("Unknown application");
                }catch(Exception e){callback.failure(400,e.getMessage()==null?"Application could not open":e.getMessage());}
                return;
            }
            if(request.startsWith("apps.")){
                if(!TerminalBrowserSession.isShell(browser)){callback.failure(403,"Personal apps are managed by the shell only");return;}
                try {
                    var store=TerminalBrowserSession.personalApps();
                    if(request.equals("apps.list")){callback.success(JSON.toJson(store.list()));return;}
                    if(request.startsWith("apps.save:")){
                        var data=JsonParser.parseString(request.substring(10)).getAsJsonObject();
                        String id=data.has("id")?data.get("id").getAsString():"";
                        callback.success(JSON.toJson(store.save(id,data.get("name").getAsString(),data.get("url").getAsString())));return;
                    }
                    if(request.startsWith("apps.delete:")){store.delete(request.substring(12));callback.success("{\"ok\":true}");return;}
                    if(request.startsWith("apps.open:")){
                        var app=store.get(request.substring(10));callback.success("{\"ok\":true}");
                        TerminalBrowserSession.openWebApp(app.url(),app.name());return;
                    }
                    callback.failure(404,"Unknown personal app command");
                }catch(Exception e){callback.failure(400,e.getMessage()==null?"网页应用操作失败":e.getMessage());}
                return;
            }
            if(request.equals("terminal.state")){callback.success(JSON.toJson(TerminalBrowserSession.state()));return;}
            if(request.startsWith("terminal.app:")){
                callback.success("{\"ok\":true}");TerminalBrowserSession.openApp(request.substring(13));return;
            }
            if(request.equals("terminal.back")){callback.success("{\"ok\":true}");TerminalBrowserSession.back();return;}
            if(request.equals("passport.open")) {
                var current=TerminalBrowserSession.current();
                if(current==null || browser.getIdentifier()!=current.getIdentifier() || !frame.isMain()) {
                    callback.failure(403,"Passport is available only in the terminal main frame");return;
                }
                callback.success("{\"ok\":true}");mc.execute(TerminalPassportNavigation::open);return;
            }
            if (request.equals("terminal.home") || request.equals("terminal.home:reduced")) {
                callback.success("{\"ok\":true}");
                mc.execute(()->TerminalBrowserSession.animateHome(request.endsWith(":reduced")));
                return;
            }
            if (request.equals("terminal.close")) {
                net.muxigame.terminal.client.music.TerminalMusicService.closeLocal();
                mc.execute(() -> mc.setScreen(null));
                callback.success("{\"ok\":true}");
                return;
            }
            if(request.startsWith("terminal.external:")) {
                String url=request.substring("terminal.external:".length());
                if(TerminalWebPolicy.account(url)){callback.success("{\"ok\":true}");TerminalBrowserSession.openExternal(url);}
                else callback.failure(403,"External URL is not allowed");
                return;
            }
            if(request.startsWith("resource.data:")) {
                resourceData(mc,request.substring("resource.data:".length()),callback);
                return;
            }
            if(request.equals("tasks.snapshot")) {
                mc.execute(() -> callback.success(invokeTaskString("snapshotJson")));
                return;
            }
            if(request.equals("tasks.request")) {
                mc.execute(()->invokeTaskVoid("request",null)); callback.success("{\"ok\":true}"); return;
            }
            if(request.startsWith("tasks.claim:")) {
                String id=request.substring("tasks.claim:".length());
                mc.execute(()->invokeTaskVoid("claim",id)); callback.success("{\"ok\":true}"); return;
            }
            if(request.startsWith("tasks.reroll:")) {
                String id=request.substring("tasks.reroll:".length());
                mc.execute(()->invokeTaskVoid("reroll",id)); callback.success("{\"ok\":true}"); return;
            }
            if(request.startsWith("tasks.track:")) {
                String id=request.substring("tasks.track:".length());
                mc.execute(()->invokeTaskVoid("toggleTracked",id)); callback.success("{\"ok\":true}"); return;
            }
            if(request.equals("challenge.open")) {
                mc.execute(Handler::openChallenge); callback.success("{\"ok\":true}"); return;
            }
            if(TerminalGameAppsBridge.dispatch(browser,frame,request,callback))return;
            if(request.startsWith("manual.open:")) {
                String manual=request.substring("manual.open:".length());
                if(manual.startsWith("patchouli:")) {
                    String value=manual.substring("patchouli:".length());
                    ResourceLocation id=ResourceLocation.tryParse(value);
                    if(id==null){callback.failure(400,"Invalid Patchouli book id");return;}
                    if(!ModList.get().isLoaded("patchouli")){callback.failure(404,"Patchouli is not installed");return;}
                    mc.execute(()->openPatchouliBook(id)); callback.success("{\"ok\":true}"); return;
                }
                if("starcatcher".equals(manual)) {
                    mc.execute(Handler::openStarcatcherGuide); callback.success("{\"ok\":true}"); return;
                }
                if("alexsmobs".equals(manual)) {
                    mc.execute(Handler::openAlexDictionary); callback.success("{\"ok\":true}"); return;
                }
                if("iceandfire".equals(manual)) {
                    mc.execute(Handler::openIceAndFireBestiary); callback.success("{\"ok\":true}"); return;
                }
                if("create".equals(manual)) {
                    mc.execute(Handler::openCreatePonderIndex); callback.success("{\"ok\":true}"); return;
                }
            }
            if (request.startsWith("patchouli.open:")) {
                String value = request.substring("patchouli.open:".length());
                ResourceLocation id = ResourceLocation.tryParse(value);
                if (id == null) {
                    callback.failure(400, "Invalid book id");
                    return;
                }
                if (!ModList.get().isLoaded("patchouli")) {
                    callback.failure(404, "Patchouli is not installed");
                    return;
                }
                mc.execute(() -> openPatchouliBook(id));
                callback.success("{\"ok\":true}");
                return;
            }
            callback.failure(404, "Unknown terminal command");
            return;
        }

        private static void resourceData(Minecraft mc,String value,CefQueryCallback callback) {
            ResourceLocation id=ResourceLocation.tryParse(value);
            if(id==null){callback.failure(400,"Invalid resource id");return;}
            try {
                var resource=mc.getResourceManager().getResource(id).orElseThrow();
                byte[] data;
                try(var stream=resource.open()){data=stream.readAllBytes();}
                String mime=id.getPath().endsWith(".png")?"image/png":"application/octet-stream";
                callback.success("data:"+mime+";base64,"+Base64.getEncoder().encodeToString(data));
            } catch(Exception error){callback.failure(404,"Resource not found: "+id);}
        }

        private static String invokeTaskString(String method) {
            try {
                Class<?> api=Class.forName("net.muxigame.core.client.tasks.TerminalTasksApi");
                return (String)api.getMethod(method).invoke(null);
            } catch(ReflectiveOperationException | LinkageError error) {
                return "{\"supported\":false,\"loading\":false,\"rows\":[],\"error\":\"Game Core task API unavailable\"}";
            }
        }

        private static void invokeTaskVoid(String method,String value) {
            try {
                Class<?> api=Class.forName("net.muxigame.core.client.tasks.TerminalTasksApi");
                if(value==null) api.getMethod(method).invoke(null);
                else api.getMethod(method,String.class).invoke(null,value);
            } catch(ReflectiveOperationException | LinkageError ignored) {}
        }

        private static void openStarcatcherGuide() {
            try {
                Class<?> screen=Class.forName("com.wdiscute.starcatcher.guide.FishingGuideScreen");
                Class<?> signed=Class.forName("com.wdiscute.starcatcher.data.SignedGuide");
                screen.getMethod("open",BlockPos.class,signed).invoke(null,BlockPos.ZERO,null);
            } catch(ReflectiveOperationException error) {
                throw new IllegalStateException("Could not open Starcatcher fishing guide",error);
            }
        }

        private static void openAlexDictionary() {
            try {
                ItemStack stack=new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("alexsmobs","animal_dictionary")));
                Class<?> alex=Class.forName("com.github.alexthe666.alexsmobs.AlexsMobs");
                Object proxy=alex.getField("PROXY").get(null);
                proxy.getClass().getMethod("openBookGUI",ItemStack.class).invoke(proxy,stack);
            } catch(ReflectiveOperationException error) {
                throw new IllegalStateException("Could not open Alex's Mobs animal dictionary",error);
            }
        }

        private static void openIceAndFireBestiary() {
            Minecraft mc=Minecraft.getInstance();
            if(mc.player==null)return;
            ResourceLocation id=ResourceLocation.fromNamespaceAndPath("iceandfire","bestiary");
            var item=BuiltInRegistries.ITEM.get(id);
            ItemStack book=ItemStack.EMPTY;
            for(ItemStack candidate:mc.player.getInventory().items) if(candidate.is(item)){book=candidate.copy();break;}
            if(book.isEmpty() && mc.player.getOffhandItem().is(item))book=mc.player.getOffhandItem().copy();
            if(book.isEmpty()){
                mc.player.displayClientMessage(Component.literal("需要先获得《怪物图鉴》，终端不会跳过图鉴页解锁。"),false);
                return;
            }
            try {
                Class<?> menuClass=Class.forName("com.iafenvoy.iceandfire.screen.menu.BestiaryMenu");
                Object menu=menuClass.getConstructor(int.class,net.minecraft.world.entity.player.Inventory.class)
                    .newInstance(0,mc.player.getInventory());
                var field=menuClass.getDeclaredField("bookStack");field.setAccessible(true);field.set(menu,book);
                Class<?> screenClass=Class.forName("com.iafenvoy.iceandfire.screen.gui.bestiary.BestiaryScreen");
                Object screen=screenClass.getConstructor(menuClass,net.minecraft.world.entity.player.Inventory.class,Component.class)
                    .newInstance(menu,mc.player.getInventory(),Component.translatable("bestiary_gui"));
                mc.setScreen((net.minecraft.client.gui.screens.Screen)screen);
            } catch(ReflectiveOperationException error) {
                throw new IllegalStateException("Could not open Ice and Fire bestiary",error);
            }
        }

        private static void openCreatePonderIndex() {
            try {
                Class<?> type=Class.forName("net.createmod.ponder.foundation.ui.PonderIndexScreen");
                Minecraft.getInstance().setScreen((net.minecraft.client.gui.screens.Screen)type.getConstructor().newInstance());
            } catch(ReflectiveOperationException error) {
                throw new IllegalStateException("Could not open Create Ponder index",error);
            }
        }

        private static void openChallenge() {
            try {
                Class<?> challenge=Class.forName("net.muxigame.minigames.client.TerminalGamesApi");
                challenge.getMethod("open",String.class).invoke(null,"zombie-challenge");
            } catch(ReflectiveOperationException | LinkageError error) {
                throw new IllegalStateException("Challenge launcher is unavailable",error);
            }
        }

        private static void openPatchouliBook(ResourceLocation id) {
            try {
                Class<?> apiClass = Class.forName("vazkii.patchouli.api.PatchouliAPI");
                Object api = apiClass.getMethod("get").invoke(null);
                api.getClass().getMethod("openBookGUI", ResourceLocation.class).invoke(api, id);
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("Patchouli API is present but could not open book " + id, error);
            }
        }
    }
}
