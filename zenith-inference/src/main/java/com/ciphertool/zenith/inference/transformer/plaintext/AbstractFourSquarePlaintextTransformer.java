/**
 * Copyright 2017-2019 George Belden
 *
 * This file is part of Zenith.
 *
 * Zenith is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * Zenith is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * Zenith. If not, see <http://www.gnu.org/licenses/>.
 */

package com.ciphertool.zenith.inference.transformer.plaintext;

import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

public abstract class AbstractFourSquarePlaintextTransformer implements PlaintextTransformer {
    protected Logger log = LoggerFactory.getLogger(getClass());

    protected static final int KEY_LENGTH = 25;
    protected static final int SQUARE_SIZE = 5;

    @Value("${four-square-transformer.key.top-left}")
    protected String keyTopLeft;

    @Value("${four-square-transformer.key.top-right}")
    protected String keyTopRight;

    @Value("${four-square-transformer.key.bottom-left}")
    protected String keyBottomLeft;

    @Value("${four-square-transformer.key.bottom-right}")
    protected String keyBottomRight;

    protected Map<Character, FourSquarePlaintextTransformer.Coordinates> keyTopLeftMap;
    protected Map<Character, FourSquarePlaintextTransformer.Coordinates> keyTopRightMap;
    protected Map<Character, FourSquarePlaintextTransformer.Coordinates> keyBottomLeftMap;
    protected Map<Character, FourSquarePlaintextTransformer.Coordinates> keyBottomRightMap;

    @PostConstruct
    public void init() {
        if (keyTopLeft.length() != KEY_LENGTH) {
            throw new IllegalArgumentException("Key specified by four-square-transformer.key.top-left must be of length " + KEY_LENGTH + ".");
        }

        if (keyTopRight.length() != KEY_LENGTH) {
            throw new IllegalArgumentException("Key specified by four-square-transformer.key.top-right must be of length " + KEY_LENGTH + ".");
        }

        if (keyBottomLeft.length() != KEY_LENGTH) {
            throw new IllegalArgumentException("Key specified by four-square-transformer.key.bottom-left must be of length " + KEY_LENGTH + ".");
        }

        if (keyBottomRight.length() != KEY_LENGTH) {
            throw new IllegalArgumentException("Key specified by four-square-transformer.key.bottom-right must be of length " + KEY_LENGTH + ".");
        }

        keyTopLeftMap = getKeyMapFromKeyString(keyTopLeft);
        keyTopRightMap = getKeyMapFromKeyString(keyTopRight);
        keyBottomLeftMap = getKeyMapFromKeyString(keyBottomLeft);
        keyBottomRightMap = getKeyMapFromKeyString(keyBottomRight);
    }

    @AllArgsConstructor
    protected class Coordinates {
        protected int row;
        protected int column;
    }

    protected Map<Character, Coordinates> getKeyMapFromKeyString(String key) {
        Map<Character, Coordinates> keyMap = new HashMap<>();

        for (int i = 0; i < SQUARE_SIZE; i++) {
            for (int j = 0; j < SQUARE_SIZE; j ++) {
                keyMap.put(key.charAt((i * SQUARE_SIZE) + j), new Coordinates(i, j));
            }
        }

        return keyMap;
    }

    protected Character getCharacterAtCoordinates(String key, int row, int column) {
        return key.charAt((row * SQUARE_SIZE) + column);
    }

    protected Character ifJThenI(Character c) {
        return c == 'j' ? 'i' : c;
    }
}
