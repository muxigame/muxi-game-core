package net.muxigame.core.client.tasks;

import java.util.List;

/** Static mainline-task catalog for the first release of the mainline tab. */
public final class MainlineTasks {
    private MainlineTasks() {}

    public record Entry(String title, String description) {}

    public static final List<Entry> CURRENT = List.of(
        new Entry("请联系服务器管理员联系夏意", "请联系服务器管理员联系夏意")
    );
}
