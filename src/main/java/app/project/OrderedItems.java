package app.project;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.ObjIntConsumer;
import java.util.function.ToIntFunction;

/** Shares positional mechanics without making persisted track types inherit UI/container behavior. Callers retain
 * their own missing-target policy: insertion appends, whereas a reorder can reject a missing target. Author order
 * is normalized independently of queue shuffle order and sorting remains stable for equal persisted positions. */
final class OrderedItems {
    private OrderedItems() { }

    static <T> List<T> sorted(List<T> items, ToIntFunction<T> order) {
        List<T> result = new ArrayList<>(items);
        result.sort(Comparator.comparingInt(order));
        return result;
    }

    static <T> int insertionIndex(List<T> sorted, Function<T, UUID> id, UUID target, boolean after) {
        int index = indexOf(sorted, id, target);
        return index < 0 ? sorted.size() : index + (after ? 1 : 0);
    }

    static <T> int indexOf(List<T> items, Function<T, UUID> id, UUID target) {
        for (int index = 0; index < items.size(); index++) {
            if (id.apply(items.get(index)).equals(target)) return index;
        }
        return -1;
    }

    static <T> void renumber(List<T> items, ObjIntConsumer<T> setOrder) {
        for (int index = 0; index < items.size(); index++) setOrder.accept(items.get(index), index);
    }
}
