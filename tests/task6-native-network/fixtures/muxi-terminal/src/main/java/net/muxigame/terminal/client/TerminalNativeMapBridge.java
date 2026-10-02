package net.muxigame.terminal.client;

import net.minecraft.client.Minecraft;
import net.muxigame.terminal.client.map.NativeMapLauncher;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.callback.CefQueryCallback;

/** Read-only map launch, distinct from server-authorized waypoint transmission. */
final class TerminalNativeMapBridge {
    private TerminalNativeMapBridge(){}
    static boolean dispatch(CefBrowser browser,CefFrame frame,String request,CefQueryCallback callback){
        if(!request.startsWith("map."))return false;
        var mc=Minecraft.getInstance();
        boolean owner=TerminalBrowserSession.isShell(browser)
            || (browser==TerminalBrowserSession.content() && TerminalBrowserSession.state().kind()==TerminalBrowserSession.Kind.BUILTIN);
        if(!owner || !TerminalBrowserSession.trusted(browser,frame) || !(mc.screen instanceof TerminalScreen)
            || !mc.isWindowActive() || mc.player==null || !mc.player.isAlive() || mc.level==null || mc.getConnection()==null){
            callback.failure(403,"地图入口仅限当前终端的本地页面");return true;
        }
        if(!request.equals("map.open")){callback.failure(404,"未知地图操作");return true;}
        long generation=TerminalBrowserSession.generation();String document=frame.getURL();Object connection=mc.getConnection();
        try{
            NativeMapLauncher.open(()->mc.getConnection()==connection && TerminalBrowserSession.generation()==generation
                && TerminalBrowserSession.trusted(browser,frame) && document.equals(frame.getURL()));
            callback.success("{\"ok\":true,\"nativeMap\":true,\"teleportRequested\":false}");
        }catch(ReflectiveOperationException|LinkageError unavailable){callback.failure(503,"当前 Xaero 地图版本不可用，请检查客户端模组");}
        catch(RuntimeException unavailable){callback.failure(409,"地图未就绪或终端页面已变化，请稍后重试");}
        return true;
    }
}
