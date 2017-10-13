package org.sagebionetworks.bridge;

import static com.google.common.base.Preconditions.checkArgument;

import java.security.SecureRandom;
import java.util.Objects;
import java.util.Random;

/**
 * This code came from Stack Exchange, reformatted to our standards. Unfortunately I then lost 
 * the reference to the page I took it from. Cleaned up to our formatting standards.
 */
class StringIdGenerator {

    private static final String ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    private final Random random;
    private final char[] characters;
    private final int length;

    StringIdGenerator(int length, Random random, String characters) {
        checkArgument(length > 1);
        checkArgument(characters != null && characters.length() >= 2);

        this.length = length;
        this.random = Objects.requireNonNull(random);
        this.characters = characters.toCharArray();
    }

    /**
     * Create session identifiers. This is 4.36e+37 unique values, which is enough
     * for a good session key.
     */
    StringIdGenerator() {
        this(21, new SecureRandom(), ALPHANUMERIC);
    }
    
    String nextString() {
        final char[] buffer = new char[length];
        for (int i = 0; i < buffer.length; ++i) {
            buffer[i] = characters[random.nextInt(characters.length)];
        }
        return new String(buffer);
    }
}
