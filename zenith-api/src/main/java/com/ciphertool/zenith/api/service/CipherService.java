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

package com.ciphertool.zenith.api.service;

import com.ciphertool.zenith.api.model.CipherResponse;
import com.ciphertool.zenith.api.model.CipherResponseItem;
import com.ciphertool.zenith.inference.dao.CipherDao;
import com.ciphertool.zenith.inference.entities.Cipher;
import com.ciphertool.zenith.inference.statistics.CiphertextCycleCountEvaluator;
import com.ciphertool.zenith.inference.statistics.CiphertextMultiplicityEvaluator;
import com.ciphertool.zenith.inference.statistics.CiphertextRepeatingBigramEvaluator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@CrossOrigin(origins = "http://localhost:4200")
@RequestMapping(value = "/api/ciphers", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
public class CipherService {
    @Autowired
    private CipherDao cipherDao;

    @Autowired
    private CiphertextMultiplicityEvaluator multiplicityEvaluator;

    @Autowired
    private CiphertextRepeatingBigramEvaluator bigramEvaluator;

    @Autowired
    private CiphertextCycleCountEvaluator cycleCountEvaluator;

    @GetMapping
    @ResponseBody
    public CipherResponse findCiphers() {
        CipherResponse cipherResponse = new CipherResponse();

        List<Cipher> ciphers = cipherDao.findAll();

        for (Cipher cipher : ciphers) {
            CipherResponseItem cipherResponseItem = new CipherResponseItem(cipher.getName(), cipher.getRows(), cipher.getColumns(), cipher.asSingleLineString());

            cipherResponseItem.setMultiplicity(multiplicityEvaluator.evaluate(cipher));
            cipherResponseItem.setBigramRepeats(bigramEvaluator.evaluate(cipher));
            cipherResponseItem.setCycleScore(cycleCountEvaluator.evaluate(cipher));

            cipherResponse.getCiphers().add(cipherResponseItem);
        }

        return cipherResponse;
    }
}
