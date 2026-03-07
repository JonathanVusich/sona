package org.sona.utils;

import com.fasterxml.uuid.Generators;
import lombok.experimental.UtilityClass;

import java.util.UUID;

@UtilityClass
public final class IDGenerator {

    public static UUID uuidv7() {
        return Generators.timeBasedEpochGenerator().generate();
    }
}
