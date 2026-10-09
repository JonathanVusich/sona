package org.sona.utils;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.NameBasedGenerator;
import com.fasterxml.uuid.impl.TimeBasedEpochGenerator;
import lombok.experimental.UtilityClass;

import java.util.UUID;

@UtilityClass
public final class IDGenerator {

    private static final TimeBasedEpochGenerator UUIDV7 = Generators.timeBasedEpochGenerator();

    // Sona's namespace for name-based IDs. Changing it would change every ID made from a name.
    private static final NameBasedGenerator UUIDV5 =
            Generators.nameBasedGenerator(UUID.fromString("0198c1f4-6d0e-7c8a-9b3e-5f2a1d4c7e60"));

    /**
     * For rows that are ordered by ID.
     */
    public static UUID uuidv7() {
        return UUIDV7.generate();
    }

    /**
     * @return the same ID every time for the same name
     */
    public static UUID uuidv5(String name) {
        return UUIDV5.generate(name);
    }
}
