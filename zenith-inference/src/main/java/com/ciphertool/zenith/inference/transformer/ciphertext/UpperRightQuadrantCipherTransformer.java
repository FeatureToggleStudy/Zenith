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

package com.ciphertool.zenith.inference.transformer.ciphertext;

import com.ciphertool.zenith.inference.entities.Cipher;
import com.ciphertool.zenith.inference.entities.Ciphertext;
import org.springframework.stereotype.Component;

@Component
public class UpperRightQuadrantCipherTransformer implements CipherTransformer {
    @Override
    public Cipher transform(Cipher cipher) {
        int halfOfRows = cipher.getRows() / 2;
        int halfOfColumns = cipher.getColumns() / 2;

        Cipher quadrant = new Cipher(cipher.getName() + "_upperRightQuadrant", halfOfRows, halfOfColumns);

        int id = 0;
        for (int i = 0; i < halfOfRows; i++) {
            for (int j = halfOfColumns + 1; j < cipher.getColumns(); j++) {
                Ciphertext toAdd = cipher.getCiphertextCharacters().get((i * cipher.getColumns()) + j).clone();
                toAdd.setCiphertextId(id);
                quadrant.addCiphertextCharacter(toAdd);
                id++;
            }
        }

        return quadrant;
    }
}
