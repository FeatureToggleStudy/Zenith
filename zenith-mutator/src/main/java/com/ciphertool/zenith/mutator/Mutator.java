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

package com.ciphertool.zenith.mutator;

import com.ciphertool.zenith.inference.dao.CipherDao;
import com.ciphertool.zenith.inference.entities.Cipher;
import com.ciphertool.zenith.inference.transformer.ciphertext.TranspositionCipherTransformer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class Mutator {
    @Autowired
    private Cipher cipher;

    @Autowired
    private CipherDao cipherDao;

    @Autowired
    private TranspositionCipherTransformer transpositionCipherTransformer;

    public void mutate() {
        cipher.setName(cipher.getName() + "-mutated");
        cipher.clearKnownSolutionKey();
        cipherDao.writeToFile(transpositionCipherTransformer.transform(cipher));
    }
}
