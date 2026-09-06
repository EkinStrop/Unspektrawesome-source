package com.unspektrawesome.camera;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Pure route expansion for public, logical/physical, and successfully probed hidden IDs. */
public final class CameraRoutePlanner {
    private CameraRoutePlanner() {}

    public static List<String> numericProbeCandidates(Collection<String> publicIds, int exclusiveLimit) {
        if (exclusiveLimit < 0) throw new IllegalArgumentException("Probe limit cannot be negative");
        List<String> result = new ArrayList<>();
        for (int value = 0; value < exclusiveLimit; value++) {
            String id = Integer.toString(value);
            if (!publicIds.contains(id)) result.add(id);
        }
        return result;
    }

    public static List<CameraRoute> plan(Collection<String> publicIds,
                                         Map<String, ? extends Set<String>> physicalIds,
                                         Collection<String> hiddenIds) {
        Map<String, CameraRoute> routes = new LinkedHashMap<>();
        List<String> visible = sorted(publicIds);
        for (String id : visible) {
            Set<String> physical = physicalIds.get(id);
            CameraRoute.Kind kind = physical == null || physical.isEmpty()
                    ? CameraRoute.Kind.DIRECT : CameraRoute.Kind.LOGICAL;
            add(routes, new CameraRoute(id, id, id, null, kind));
            if (physical != null) {
                for (String child : sorted(physical)) {
                    add(routes, new CameraRoute(id + "/" + child, id, child, child,
                            CameraRoute.Kind.PHYSICAL));
                }
            }
        }
        for (String id : sorted(hiddenIds)) {
            if (!publicIds.contains(id)) {
                Set<String> physical = physicalIds.get(id);
                CameraRoute.Kind kind = physical == null || physical.isEmpty()
                        ? CameraRoute.Kind.HIDDEN_DIRECT : CameraRoute.Kind.HIDDEN_LOGICAL;
                add(routes, new CameraRoute(id, id, id, null, kind));
                if (physical != null) {
                    for (String child : sorted(physical)) {
                        add(routes, new CameraRoute(id + "/" + child, id, child, child,
                                CameraRoute.Kind.PHYSICAL));
                    }
                }
            }
        }
        return Collections.unmodifiableList(new ArrayList<>(routes.values()));
    }

    private static void add(Map<String, CameraRoute> routes, CameraRoute route) {
        routes.putIfAbsent(route.routeId, route);
    }

    private static List<String> sorted(Collection<String> ids) {
        List<String> result = new ArrayList<>();
        for (String id : ids) if (id != null && !id.trim().isEmpty()) result.add(id);
        result.sort(CameraRoutePlanner::compareIds);
        return result;
    }

    private static int compareIds(String left, String right) {
        Integer leftNumber = parseInteger(left);
        Integer rightNumber = parseInteger(right);
        if (leftNumber != null && rightNumber != null) return Integer.compare(leftNumber, rightNumber);
        if (leftNumber != null) return -1;
        if (rightNumber != null) return 1;
        return Comparator.<String>naturalOrder().compare(left, right);
    }

    private static Integer parseInteger(String value) {
        try { return Integer.valueOf(value); }
        catch (NumberFormatException ignored) { return null; }
    }
}
