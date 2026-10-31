package org.example.crop;

import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** The crops the facility knows about. Crops can be added or removed at runtime. */
public final class CropRegistry implements CropLookup {
    private final ConcurrentMap<String, CropProfile> profiles = new ConcurrentHashMap<>();

    public void register(CropProfile profile) {
        profiles.put(profile.name(), profile);
    }

    public void remove(String cropName) {
        profiles.remove(cropName);
    }

    @Override
    public Optional<CropProfile> find(String cropName) {
        return Optional.ofNullable(profiles.get(cropName));
    }

    public Set<String> names() {
        return new TreeSet<>(profiles.keySet());
    }
}
