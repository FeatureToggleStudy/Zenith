package com.ciphertool.zenith.inference.util;

import com.ciphertool.zenith.inference.entities.Cipher;
import com.ciphertool.zenith.model.LanguageConstants;
import com.ciphertool.zenith.model.entities.TreeNGram;
import com.ciphertool.zenith.model.markov.ArrayMarkovModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ChiSquaredEvaluator {
    private Map<String, Long> englishLetterCounts = new HashMap<>(LetterUtils.NUMBER_OF_LETTERS);

    @Autowired
    private ArrayMarkovModel letterMarkovModel;

    private Cipher initialized = null;

    public void init(Cipher cipher) {
        for (TreeNGram node : letterMarkovModel.getFirstOrderNodes()) {
            double letterProbability = (double) node.getCount() / (double) letterMarkovModel.getTotalNGramCount();
            englishLetterCounts.put(node.getCumulativeString(), Math.round(letterProbability * cipher.length()));
        }

        initialized = cipher;
    }

    public double evaluate(Cipher cipher, String solutionString) {
        if (initialized == null || initialized != cipher) {
            init(cipher);
        }

        Map<String, Long> solutionLetterCounts = new HashMap<>(LetterUtils.NUMBER_OF_LETTERS);

        for (int i = 0; i < LanguageConstants.LOWERCASE_LETTERS.length; i ++) {
            solutionLetterCounts.put(String.valueOf(LanguageConstants.LOWERCASE_LETTERS[i]), 0L);
        }

        for (int i = 0; i < solutionString.length(); i ++) {
            String letter = String.valueOf(solutionString.charAt(i));

            solutionLetterCounts.put(letter, solutionLetterCounts.get(letter) + 1L);
        }

        List<Double> chiSquaredPerLetter = new ArrayList<>(LetterUtils.NUMBER_OF_LETTERS);

        for (String letter : englishLetterCounts.keySet()) {
            long actualCount = solutionLetterCounts.get(letter);
            long expectedCount = englishLetterCounts.get(letter);
            double numerator = Math.pow((double) (actualCount - expectedCount), 2.0);
            double denominator = Math.max(1d, expectedCount); // Prevent division by zero
            chiSquaredPerLetter.add(numerator / denominator);
        }

        return chiSquaredPerLetter.stream()
                .mapToDouble(perLetter -> perLetter.doubleValue())
                .sum();
    }
}
