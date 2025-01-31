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

package com.ciphertool.zenith.inference.configuration;

import com.ciphertool.zenith.inference.evaluator.PlaintextEvaluator;
import com.ciphertool.zenith.inference.transformer.plaintext.PlaintextTransformer;
import com.ciphertool.zenith.model.dao.LetterNGramDao;
import com.ciphertool.zenith.model.entities.TreeNGram;
import com.ciphertool.zenith.model.markov.ArrayMarkovModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import javax.validation.constraints.Min;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Configuration
@ComponentScan(basePackages = {
        "com.ciphertool.zenith.genetic.algorithms.crossover",
        "com.ciphertool.zenith.genetic.algorithms.mutation",
        "com.ciphertool.zenith.genetic.algorithms",
        "com.ciphertool.zenith.genetic.algorithms.selection",
        "com.ciphertool.zenith.genetic.population",
        "com.ciphertool.zenith.model.dao",
        "com.ciphertool.zenith.model.archive"
})
public class InferenceConfiguration {
    private final static String PLAINTEXT_TRANSFORMER_SUFFIX = PlaintextTransformer.class.getSimpleName();

    private Logger log = LoggerFactory.getLogger(getClass());

    @Value("${markov.letter.order}")
    private int markovOrder;

    @Min(1)
    @Value("${language-model.max-ngrams-to-keep}")
    private int maxNGramsToKeep;

    @Value("${decipherment.transformers.plaintext}")
    private List<String> plaintextTransformersToUse;

    @Value("${decipherment.evaluator.plaintext}")
    private String plaintextEvaluatorName;

    @Bean
    public ArrayMarkovModel letterMarkovModel(LetterNGramDao letterNGramDao) {
        long startFindAll = System.currentTimeMillis();
        log.info("Beginning retrieval of all n-grams.");

        /*
         * Begin setting up letter n-gram model
         */
        List<TreeNGram> nGramNodes = letterNGramDao.findAll();

        log.info("Finished retrieving {} n-grams in {}ms.", nGramNodes.size(), (System.currentTimeMillis() - startFindAll));

        ArrayMarkovModel letterMarkovModel = new ArrayMarkovModel(markovOrder);

        long startAdding = System.currentTimeMillis();
        log.info("Adding nodes to the model.");

        nGramNodes.stream()
                .filter(node -> node.getOrder() == 1)
                .forEach(letterMarkovModel::addNode);

        nGramNodes.stream()
                .filter(node -> node.getOrder() == markovOrder)
                .sorted(Comparator.comparing(TreeNGram::getCount).reversed())
                .limit(maxNGramsToKeep)
                .forEach(letterMarkovModel::addNode);

        log.info("Finished adding {} nodes to the letter n-gram model in {}ms.", letterMarkovModel.getMapSize(), (System.currentTimeMillis() - startAdding));

        float unknownLetterNGramProbability = 1f / (float) letterMarkovModel.getTotalNGramCount();
        letterMarkovModel.setUnknownLetterNGramProbability(unknownLetterNGramProbability);
        letterMarkovModel.setUnknownLetterNGramLogProbability((float) Math.log(unknownLetterNGramProbability));

        return letterMarkovModel;
    }

    @Bean
    public List<PlaintextTransformer> activePlaintextTransformers(List<PlaintextTransformer> plaintextTransformers) {
        if (plaintextTransformers == null || plaintextTransformers.isEmpty()) {
            return Collections.EMPTY_LIST;
        }

        List<PlaintextTransformer> toUse = new ArrayList<>(plaintextTransformersToUse.size());
        List<String> existentPlaintextTransformers = plaintextTransformers.stream()
                .map(transformer -> transformer.getClass().getSimpleName().replace(PLAINTEXT_TRANSFORMER_SUFFIX, ""))
                .collect(Collectors.toList());

        for (String transformerName : plaintextTransformersToUse) {
            if (!existentPlaintextTransformers.contains(transformerName)) {
                log.error("The PlaintextTransformer with name {} does not exist.  Please use a name from the following: {}", transformerName, existentPlaintextTransformers);
                throw new IllegalArgumentException("The PlaintextTransformer with name " + transformerName + " does not exist.");
            }

            for (PlaintextTransformer plaintextTransformer : plaintextTransformers) {
                if (plaintextTransformer.getClass().getSimpleName().replace(PLAINTEXT_TRANSFORMER_SUFFIX, "").equals(transformerName)) {
                    toUse.add(plaintextTransformer);

                    break;
                }
            }
        }

        return toUse;
    }

    @Bean
    public PlaintextEvaluator plaintextEvaluator(List<PlaintextEvaluator> plaintextEvaluators) {
        for (PlaintextEvaluator evaluator : plaintextEvaluators) {
            if (evaluator.getClass().getSimpleName().equals(plaintextEvaluatorName)) {
                return evaluator;
            }
        }

        List<String> existentPlaintextEvaluators = plaintextEvaluators.stream()
                .map(evaluator -> evaluator.getClass().getSimpleName())
                .collect(Collectors.toList());

        log.error("The PlaintextEvaluator with name {} does not exist.  Please use a name from the following: {}", plaintextEvaluatorName, existentPlaintextEvaluators);
        throw new IllegalArgumentException("The PlaintextEvaluator with name " + plaintextEvaluatorName + " does not exist.");
    }
}
