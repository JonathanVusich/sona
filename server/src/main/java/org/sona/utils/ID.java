package org.sona.utils;

import com.fasterxml.uuid.Generators;
import lombok.experimental.UtilityClass;

import java.util.UUID;

@UtilityClass
public final class ID {

    public static UUID v7() {
        return Generators.timeBasedEpochGenerator().generate();
    }
}
