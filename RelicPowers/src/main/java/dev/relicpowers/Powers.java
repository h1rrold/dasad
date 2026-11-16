package dev.relicpowers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Registry of every relic. */
public final class Powers {
    private Powers() {}

    private static final Map<String, Power> BY_ID = new LinkedHashMap<>();

    public static void init(RelicPowers pl) {
        BY_ID.clear();
        List<Power> list = new ArrayList<>();
        PowersA.register(list, pl);
        PowersB.register(list, pl);
        PowersC.register(list, pl);
        PowersD.register(list, pl);
        PowersE.register(list, pl);
        for (Power p : list) BY_ID.put(p.id, p);
    }

    public static Power get(String id) { return id == null ? null : BY_ID.get(id.toLowerCase()); }

    public static List<Power> all() { return Collections.unmodifiableList(new ArrayList<>(BY_ID.values())); }

    public static List<String> ids() { return new ArrayList<>(BY_ID.keySet()); }
}
