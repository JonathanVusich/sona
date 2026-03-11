package org.sona.utils;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedEpochGenerator;
import lombok.experimental.UtilityClass;

import java.util.UUID;

@UtilityClass
public final class IDGenerator {

    private static final TimeBasedEpochGenerator UUIDV7 = Generators.timeBasedEpochGenerator();

    public static UUID uuidv7() {
        return UUIDV7.generate();
    }
}
