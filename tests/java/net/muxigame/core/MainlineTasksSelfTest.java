package net.muxigame.core;

import net.muxigame.core.client.tasks.MainlineTasks;

public final class MainlineTasksSelfTest {
    private MainlineTasksSelfTest() {}

    public static int run() {
        if (MainlineTasks.CURRENT.size() != 1) throw new AssertionError("mainline task count");
        MainlineTasks.Entry entry=MainlineTasks.CURRENT.get(0);
        if (!entry.title().equals("请联系服务器管理员联系夏意")) throw new AssertionError("mainline title");
        if (!entry.description().equals("请联系服务器管理员联系夏意")) throw new AssertionError("mainline description");
        return 3;
    }
}
