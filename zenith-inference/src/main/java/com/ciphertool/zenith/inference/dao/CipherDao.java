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

package com.ciphertool.zenith.inference.dao;

import com.ciphertool.zenith.inference.entities.Cipher;
import com.ciphertool.zenith.inference.entities.CipherJson;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Component
public class CipherDao {
    private static Logger log = LoggerFactory.getLogger(CipherDao.class);

    private static final String CIPHERS_DIRECTORY = "ciphers";
    private static final String JSON_EXTENSION = ".json";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private List<Cipher> ciphers = new ArrayList<>();

    public Cipher findByCipherName(String name) {
        if (name == null || name.isEmpty()) {
            log.warn("Attempted to find cipher with null or empty name.  Returning null.");

            return null;
        }

        return findAll().stream()
                .filter(cipher -> name.equalsIgnoreCase(cipher.getName()))
                .findAny()
                .orElse(null);
    }

    public List<Cipher> findAll() {
        if (!ciphers.isEmpty()) {
            return ciphers;
        }

        // First read ciphers from the classpath
        ClassLoader cl = this.getClass().getClassLoader();
        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(cl);
        Resource[] resources;
        try {
            resources = resolver.getResources("classpath*:/" + CIPHERS_DIRECTORY + "/*" + JSON_EXTENSION);
        } catch (IOException ioe) {
            throw new IllegalStateException("Unable to read ciphers from classpath directory=" + CIPHERS_DIRECTORY, ioe);
        }

        for (Resource resource : resources){
            try (InputStream inputStream = resource.getInputStream()) {
                Cipher nextCipher = new Cipher(OBJECT_MAPPER.readValue(inputStream, CipherJson.class));

                if (containsCipher(nextCipher)) {
                    throw new IllegalArgumentException("Cipher with name " + nextCipher.getName() + " already imported.  Cannot import duplicate ciphers.");
                }

                ciphers.add(nextCipher);
            } catch (IOException e) {
                log.error("Unable to read Ciphers from file: {}.", resource.getFilename(), e);
                throw new IllegalStateException(e);
            }
        }

        // Secondly, attempt to read ciphers from the local directory on the filesystem
        File localCiphersDirectory = new File(Paths.get(CIPHERS_DIRECTORY).toAbsolutePath().toString());

        if (!localCiphersDirectory.exists() || !localCiphersDirectory.isDirectory()) {
            return ciphers;
        }

        for (File file : localCiphersDirectory.listFiles()) {
            if (!file.getName().endsWith(JSON_EXTENSION)) {
                log.info("Skipping file in ciphers directory due to invalid file extension.  File={}", file.getName());
                continue;
            }

            try {
                Cipher nextCipher = new Cipher(OBJECT_MAPPER.readValue(file, CipherJson.class));

                if (containsCipher(nextCipher)) {
                    throw new IllegalArgumentException("Cipher with name " + nextCipher.getName() + " already imported.  Cannot import duplicate ciphers.");
                }

                ciphers.add(nextCipher);
            } catch (IOException e) {
                log.error("Unable to read Ciphers from file: {}.", file.getPath(), e);
                throw new IllegalStateException(e);
            }
        }

        return ciphers;
    }

    public void writeToFile(Cipher cipher) {
        String dateText = LocalDateTime.now().format(DATE_TIME_FORMATTER);

        // Write the file to the current working directory
        String outputFilename = "." + File.separator + cipher.getName() + "-" + dateText + JSON_EXTENSION;

        try {
            OBJECT_MAPPER.writeValue(new File(outputFilename), new CipherJson(cipher));
        } catch (IOException e) {
            throw new IllegalStateException("Unable to write Cipher with name=" + cipher.getName() + " to file=" + outputFilename + ".");
        }
    }

    private boolean containsCipher(Cipher newCipher) {
        return ciphers.stream().anyMatch(cipher -> cipher.getName().equals(newCipher.getName()));
    }
}
