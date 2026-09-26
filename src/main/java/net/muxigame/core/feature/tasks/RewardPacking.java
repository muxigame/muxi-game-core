package net.muxigame.core.feature.tasks;

import java.util.*;

/** All-or-nothing inventory simulation. K represents an item AND its complete component set. */
public final class RewardPacking {
    private RewardPacking() {}
    public record Slot<K>(K kind, int count, int limit) {
        public Slot { if (count < 0 || limit < 1) throw new IllegalArgumentException("Invalid slot"); }
    }
    public static <K> Optional<List<Slot<K>>> plan(List<Slot<K>> inventory, List<Slot<K>> rewards) {
        List<Slot<K>> result = new ArrayList<>(inventory);
        for (Slot<K> reward : rewards) {
            int left = reward.count();
            for (int i=0; i<result.size() && left>0; i++) {
                Slot<K> slot = result.get(i);
                if (slot.count() == 0 || !Objects.equals(slot.kind(), reward.kind())) continue;
                int add = Math.min(left, Math.max(0, Math.min(slot.limit(), reward.limit())-slot.count()));
                result.set(i, new Slot<>(slot.kind(), slot.count()+add, slot.limit())); left-=add;
            }
            for (int i=0; i<result.size() && left>0; i++) if (result.get(i).count() == 0) {
                int limit = Math.min(result.get(i).limit(), reward.limit()); int add = Math.min(left, limit);
                result.set(i, new Slot<>(reward.kind(), add, limit)); left-=add;
            }
            if (left>0) return Optional.empty();
        }
        return Optional.of(List.copyOf(result));
    }
}
