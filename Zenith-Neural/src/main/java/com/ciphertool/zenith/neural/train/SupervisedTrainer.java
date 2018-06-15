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

package com.ciphertool.zenith.neural.train;

import com.ciphertool.zenith.neural.generate.SampleGenerator;
import com.ciphertool.zenith.neural.model.DataSet;
import com.ciphertool.zenith.neural.model.Layer;
import com.ciphertool.zenith.neural.model.NeuralNetwork;
import com.ciphertool.zenith.neural.model.ProblemType;
import com.ciphertool.zenith.neural.predict.Predictor;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.NDArrayIndex;
import org.nd4j.linalg.ops.transforms.Transforms;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import javax.annotation.PostConstruct;
import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Min;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
@Validated
@ConfigurationProperties(prefix = "training")
public class SupervisedTrainer {
	private static Logger					log						= LoggerFactory.getLogger(SupervisedTrainer.class);

	private static final boolean			COMPUTE_SUM_OF_ERRORS	= false;

	@DecimalMin("0.0")
	private Float learningRate;

	@DecimalMin("0.0")
	@DecimalMax("1.0")
	private Float weightDecayPercent;

	private int trainingSampleCount;

	@Min(0)
	private int iterationsBetweenSaves = 0;

	private String outputFileNameWithDate;

	@Value("${network.outputFileName}")
	private String outputFileName;

	@Autowired
	private SampleGenerator generator;

	@Autowired
	private Predictor predictor;

	@PostConstruct
	public void init() {
		if (outputFileName == null) {
			if (iterationsBetweenSaves > 0) {
				throw new IllegalArgumentException("Property network.iterationsBetweenSaves was set, but property " +
						"network.outputFileName was null.  Please set network.outputFileName if saving of the network is " +
						"desired.");
			}

			return;
		}

		LocalDateTime now = LocalDateTime.now();
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
		String dateText = now.format(formatter);

		String extension = outputFileName.substring(outputFileName.indexOf('.'));
		String beforeExtension = outputFileName.replace(extension, "");

		outputFileNameWithDate = beforeExtension + "-" + dateText + extension;
	}

	public void train(NeuralNetwork network, int batchSize) {
		int currentBatchSize = 0;

		int i;
		long batchStart = System.currentTimeMillis();
		for (i = 0; i < trainingSampleCount; i++) {
			long start = System.currentTimeMillis();

			DataSet nextSample = generator.generateTrainingSample();

			for (int j = 0; j < nextSample.getInputs().size(0); j ++) {
				predictor.feedForward(network, nextSample.getInputs().getRow(j));

				log.debug("Finished feed-forward in: {}ms", (System.currentTimeMillis() - start));

				start = System.currentTimeMillis();

				backPropagate(network, nextSample.getOutputs().getRow(j));

				log.debug("Finished back-propagation in: {}ms", (System.currentTimeMillis() - start));
			}

			currentBatchSize++;

			if (currentBatchSize == batchSize) {
				long applyAccumulatedDeltasTime = network.applyAccumulatedDeltas(learningRate, weightDecayPercent, currentBatchSize);
				log.debug("Finished applying accumulated deltas in {}ms", applyAccumulatedDeltasTime);

				log.info("Finished training batch {} in {}ms.", (int) ((i + 1) / batchSize), (System.currentTimeMillis()
						- batchStart));

				currentBatchSize = 0;

				batchStart = System.currentTimeMillis();
			}

			network.incrementSamplesTrained();

			if (outputFileNameWithDate != null && iterationsBetweenSaves > 0 && ((i + 1) % iterationsBetweenSaves) == 0) {
				// FIXME: re-implement saving of network to file
				//NetworkMapper.saveToFile(network, outputFileNameWithDate);
			}
		}

		if (outputFileNameWithDate != null && (iterationsBetweenSaves == 0 || (i % iterationsBetweenSaves) != 0)) {
			// FIXME: re-implement saving of network to file
			//NetworkMapper.saveToFile(network, outputFileNameWithDate);
		}

		if (currentBatchSize > 0) {
			log.info("Finished training batch {} in {}ms.", (int) ((i + 1) / batchSize), (System.currentTimeMillis()
					- batchStart));

			long applyAccumulatedDeltasTime = network.applyAccumulatedDeltas(learningRate, weightDecayPercent, currentBatchSize);
			log.debug("Finished applying accumulated deltas in {}ms", applyAccumulatedDeltasTime);
		}
	}

	protected void backPropagate(NeuralNetwork network, INDArray expectedOutputs) {
		Layer outputLayer = network.getOutputLayer();

		if (expectedOutputs.size(1) != outputLayer.getNeurons()) {
			throw new IllegalArgumentException("The expected output size of " + expectedOutputs.size(1)
					+ " does not match the actual output size of " + outputLayer.getNeurons()
					+ ".  Unable to continue with back propagation step.");
		}

		/*
		 * The sum of errors is not actually used by the backpropagation algorithm, but it may be useful for debugging
		 * purposes
		 */
		if (COMPUTE_SUM_OF_ERRORS) {
			computeSumOfErrors(network, expectedOutputs);
		}

		// START - PROCESS OUTPUT LAYER
		INDArray errorDerivatives;
		INDArray activationDerivatives;

		INDArray actualOutputs = network.getOutputLayer().getActivations().dup();

		// Compute deltas for output layer using chain rule and subtract them from current weights
		if (network.getProblemType() == ProblemType.REGRESSION) {
			derivativeOfCostFunctionRegression(expectedOutputs, actualOutputs);
		} else {
			derivativeOfCostFunctionClassification(expectedOutputs, actualOutputs);
		}

		errorDerivatives = actualOutputs;

		INDArray outputSums = network.getOutputLayer().getOutputSums().dup();

		if (network.getProblemType() == ProblemType.REGRESSION) {
			outputLayer.getActivationFunctionType().getActivationFunction().calculateDerivative(outputSums);

			activationDerivatives = outputSums;
		} else {
			// For CLASSIFICATION, including softmax/cross entropy loss, the activationDerivative is accounted for in the errorDerivative
			activationDerivatives = Nd4j.ones(outputSums.shape());
		}

		Layer secondToLast = network.getLayers()[network.getLayers().length - 2];

		INDArray outputWeights = secondToLast.getOutgoingWeights();
		INDArray deltas = Nd4j.ones(outputWeights.shape());
		INDArray outputSumDerivatives = secondToLast.getActivations();
		deltas.muliColumnVector(outputSumDerivatives.transpose());
		deltas.muliRowVector(errorDerivatives);
		deltas.muliRowVector(activationDerivatives);

		INDArray deltaLayer = secondToLast.getAccumulatedDeltas();
		deltaLayer.addi(deltas);
		// END - PROCESS OUTPUT LAYER

		Layer toLayer;
		INDArray oldErrorDerivatives;
		INDArray oldActivationDerivatives;

		// Compute deltas for hidden layers using chain rule and subtract them from current weights
		for (int i = network.getLayers().length - 2; i > 0; i--) {
			outputSumDerivatives = network.getLayers()[i - 1].getActivations();
			toLayer = network.getLayers()[i];

			oldErrorDerivatives = errorDerivatives;
			oldActivationDerivatives = activationDerivatives;

			INDArray outputSumLayer = network.getLayers()[i].getOutputSums();
			// Get a subset of the outputSumLayer so as to skip the bias neuron
			outputSums = outputSumLayer.get(NDArrayIndex.all(), NDArrayIndex.interval(0, outputSumLayer.size(1) - (toLayer.hasBias() ? 1 : 0))).dup();
			toLayer.getActivationFunctionType().getActivationFunction().calculateDerivative(outputSums);

			activationDerivatives = outputSums;

			INDArray partialErrorDerivatives = oldErrorDerivatives.mul(oldActivationDerivatives);
			INDArray toWeightLayer = network.getLayers()[i].getOutgoingWeights();
			// Get a subset of the toWeightLayer so as to skip the bias neuron
			errorDerivatives = toWeightLayer.get(NDArrayIndex.interval(0, toWeightLayer.size(0) - (toLayer.hasBias() ? 1 : 0)), NDArrayIndex.all()).mmul(partialErrorDerivatives.transpose()).transpose();

			outputWeights = network.getLayers()[i - 1].getOutgoingWeights();
			deltas = Nd4j.ones(outputWeights.shape());
			deltas.muliColumnVector(outputSumDerivatives.transpose());
			deltas.muliRowVector(errorDerivatives);
			deltas.muliRowVector(activationDerivatives);

			deltaLayer = network.getLayers()[i - 1].getAccumulatedDeltas();
			deltaLayer.addi(deltas);
		}
	}

	protected static Float computeSumOfErrors(NeuralNetwork network, INDArray expectedOutputs) {
		INDArray actualOutputs = network.getOutputLayer().getActivations().dup();

		if (network.getProblemType() == ProblemType.REGRESSION) {
			costFunctionRegression(expectedOutputs, actualOutputs);
		} else {
			costFunctionClassification(expectedOutputs, actualOutputs);
		}

		return actualOutputs.sumNumber().floatValue();
	}

	protected static void costFunctionRegression(INDArray expectedOutputs, INDArray actualOutputs) {
		actualOutputs.rsubi(expectedOutputs);
		Transforms.pow(actualOutputs, 2, false);
		actualOutputs.divi(2.0f);
	}

	protected static void costFunctionClassification(INDArray expectedOutputs, INDArray actualOutputs) {
		Transforms.log(actualOutputs, false);
		actualOutputs.muli(expectedOutputs);
	}

	protected static void derivativeOfCostFunctionRegression(INDArray expectedOutputs, INDArray actualOutputs) {
		actualOutputs.rsubi(expectedOutputs);
		actualOutputs.negi();
	}

	protected static void derivativeOfCostFunctionClassification(INDArray expectedOutputs, INDArray actualOutputs) {
		actualOutputs.subi(expectedOutputs);
	}

	public void setLearningRate(Float learningRate) {
		this.learningRate = learningRate;
	}

	public void setWeightDecayPercent(Float weightDecayPercent) {
		this.weightDecayPercent = weightDecayPercent;
	}

	public void setTrainingSampleCount(int trainingSampleCount) {
		this.trainingSampleCount = trainingSampleCount;
	}

	public void setIterationsBetweenSaves(int iterationsBetweenSaves) {
		this.iterationsBetweenSaves = iterationsBetweenSaves;
	}
}
