package net.muxigame.core.client.tasks;

import java.nio.file.Files;
import java.nio.file.Path;

public final class TaskHudSettingsSelfTest {
    private TaskHudSettingsSelfTest() {}

    public static int run() {
        Path root=null;
        try {
            root=Files.createTempDirectory("muxi-task-hud-settings-");
            TaskHudSettings first=new TaskHudSettings(root);
            if(!first.dailyExpanded || !first.mainlineExpanded) throw new AssertionError("HUD sections default expanded");
            first.dailyExpanded=false; first.mainlineExpanded=false; first.save();
            TaskHudSettings restored=new TaskHudSettings(root);
            if(restored.dailyExpanded || restored.mainlineExpanded) throw new AssertionError("HUD section state persisted");
            return 2;
        } catch(Exception e) {
            throw new AssertionError("HUD settings round trip",e);
        } finally {
            if(root!=null) try {
                Files.deleteIfExists(root.resolve("config/muxi-daily-tasks-client.json"));
                Files.deleteIfExists(root.resolve("config"));
                Files.deleteIfExists(root);
            } catch(Exception ignored) {}
        }
    }
}
