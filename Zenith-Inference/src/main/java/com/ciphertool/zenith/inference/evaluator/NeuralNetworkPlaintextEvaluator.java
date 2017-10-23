/**
 * Copyright 2017 George Belden
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

package com.ciphertool.zenith.inference.evaluator;

import java.math.BigDecimal;
import java.math.RoundingMode;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.ciphertool.zenith.inference.dto.EvaluationResults;
import com.ciphertool.zenith.inference.entities.CipherSolution;
import com.ciphertool.zenith.math.MathCache;
import com.ciphertool.zenith.math.MathConstants;
import com.ciphertool.zenith.neural.io.NetworkMapper;
import com.ciphertool.zenith.neural.model.NeuralNetwork;
import com.ciphertool.zenith.neural.predict.Predictor;

@Component
public class NeuralNetworkPlaintextEvaluator {
	private Logger					log						= LoggerFactory.getLogger(getClass());

	private static final BigDecimal	ALPHABET_SIZE			= BigDecimal.valueOf(26);
	private static final int		CHAR_TO_NUMERIC_OFFSET	= 9;

	@Value("${network.input.fileName}")
	private String					inputFileName;

	@Autowired
	private MathCache				bigDecimalFunctions;

	@Autowired
	private Predictor				predictor;

	private NeuralNetwork			network;

	@PostConstruct
	public void init() {
		network = NetworkMapper.loadFromFile(inputFileName);
	}

	public EvaluationResults evaluate(CipherSolution solution, String ciphertextKey) {
		String solutionString = solution.asSingleLineString();

		BigDecimal[] inputs = new BigDecimal[solutionString.length()];

		for (int i = 0; i < solutionString.length(); i++) {
			inputs[i] = charToBigDecimal(solutionString.charAt(i));
		}

		long startFeedForward = System.currentTimeMillis();

		predictor.feedForward(network, inputs);

		log.debug("Feed forward took {}ms.", (System.currentTimeMillis() - startFeedForward));

		BigDecimal interpolatedProbability = network.getOutputLayer().getNeurons()[0].getActivationValue();
		BigDecimal interpolatedLogProbability = bigDecimalFunctions.log(interpolatedProbability);

		return new EvaluationResults(interpolatedProbability, interpolatedLogProbability);
	}

	protected BigDecimal charToBigDecimal(char c) {
		int numericValue = Character.getNumericValue(c) - CHAR_TO_NUMERIC_OFFSET;

		return BigDecimal.valueOf(numericValue).divide(ALPHABET_SIZE, MathConstants.PREC_10_HALF_UP).setScale(10, RoundingMode.UP);
	}
}