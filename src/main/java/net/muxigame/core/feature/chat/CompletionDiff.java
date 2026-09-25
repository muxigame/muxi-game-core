package net.muxigame.core.feature.chat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/** 聊天补全表的记账：在线玩家的昵称集合，以及两次之间要增、要删的词。纯逻辑，方便单测。 */
public final class CompletionDiff {
    private CompletionDiff() {}

    /**
     * 在线玩家的昵称，去重。按集合记而不是按人记：昵称允许重名，两个人同叫"张三"时走了一个，
     * 客户端补全表里的"张三"还得留着。
     */
    public static Set<String> nicknames(Iterable<String> logins, Function<String, String> nicknameOf) {
        Set<String> result = new LinkedHashSet<>();
        for (String login : logins) {
            String nickname = login == null ? null : nicknameOf.apply(login);
            if (nickname != null && !nickname.isBlank()) result.add(nickname);
        }
        return Collections.unmodifiableSet(result);
    }

    public static List<String> added(Set<String> before, Set<String> after) {
        return minus(after, before);
    }

    public static List<String> removed(Set<String> before, Set<String> after) {
        return minus(before, after);
    }

    private static List<String> minus(Set<String> from, Set<String> other) {
        List<String> result = new ArrayList<>();
        for (String value : from) if (!other.contains(value)) result.add(value);
        return result;
    }
}
