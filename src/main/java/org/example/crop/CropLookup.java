package org.example.crop;

import java.util.Optional;

/** Finds a crop's profile by name. The role monitoring code depends on. */
public interface CropLookup {
    Optional<CropProfile> find(String cropName);
}
