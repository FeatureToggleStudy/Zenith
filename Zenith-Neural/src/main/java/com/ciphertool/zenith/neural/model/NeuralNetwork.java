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

package com.ciphertool.zenith.neural.model;

import com.ciphertool.zenith.neural.activation.ActivationFunctionType;

import java.util.concurrent.ThreadLocalRandom;

public class NeuralNetwork {
	private Float biasWeight;

	private ProblemType	problemType;

	private Layer[]		layers;

	private long samplesTrained = 0;

	protected void init() {
		problemType = this.getOutputLayer().getNeurons().length == 1 ? ProblemType.REGRESSION : ProblemType.CLASSIFICATION;

		Layer fromLayer;
		Layer toLayer;

		for (int i = 0; i < layers.length - 1; i++) {
			fromLayer = layers[i];
			toLayer = layers[i + 1];

			for (int j = 0; j < fromLayer.getNeurons().length; j++) {
				Neuron nextInputNeuron = fromLayer.getNeurons()[j];
				nextInputNeuron.setOutgoingSynapses(new Synapse[toLayer.getNeurons().length
						- (toLayer.hasBias() ? 1 : 0)]);

				if (nextInputNeuron.isBias()) {
					// The bias activation value is static and should never change
					nextInputNeuron.setActivationValue(biasWeight);
				}

				for (int k = 0; k < toLayer.getNeurons().length; k++) {
					Neuron nextOutputNeuron = toLayer.getNeurons()[k];

					if (nextOutputNeuron.isBias()) {
						// We don't want to create a synapse going into a bias neuron
						continue;
					}

					Float initialWeight = ThreadLocalRandom.current().nextFloat() - 0.5f;

					nextInputNeuron.getOutgoingSynapses()[k] = new Synapse(nextOutputNeuron, initialWeight);
				}
			}
		}
	}

	@SuppressWarnings("unused")
	private NeuralNetwork() {
		// Exists purely for Jackson deserialization
	}

	public NeuralNetwork(int inputLayerNeurons, String[] hiddenLayers, int outputLayerNeurons, Float biasWeight) {
		this.biasWeight = biasWeight;
		boolean addBias = biasWeight != null ? true : false;

		layers = new Layer[hiddenLayers.length + 2];

		layers[0] = new Layer(inputLayerNeurons, addBias);

		for (int i = 1; i <= hiddenLayers.length; i++) {
			int separatorIndex = hiddenLayers[i - 1].indexOf(':');

			if (separatorIndex < 0) {
				throw new IllegalArgumentException(
						"The hidden layers must be represented as a comma-separated list of numberOfNeurons:activationFunctionType pairs.");
			}

			int numberOfNeurons = Integer.parseInt(hiddenLayers[i - 1].substring(0, separatorIndex));

			ActivationFunctionType activationFunctionType = ActivationFunctionType.valueOf(hiddenLayers[i
					- 1].substring(separatorIndex + 1));

			layers[i] = new Layer(numberOfNeurons, activationFunctionType, addBias);
		}

		ActivationFunctionType activationFunctionType = outputLayerNeurons == 1 ? ActivationFunctionType.LEAKY_RELU : ActivationFunctionType.SOFTMAX;

		layers[layers.length - 1] = new Layer(outputLayerNeurons, activationFunctionType, false);

		init();
	}

	public NeuralNetwork(NeuralNetwork network) {
		this.layers = network.getLayers();
		this.biasWeight = network.getBiasWeight();
		this.problemType = network.getProblemType();
	}

	public long applyAccumulatedDeltas(Float learningRate, Float weightDecayPercent) {
		long start = System.currentTimeMillis();

		for (int i = 0; i < layers.length - 1; i++) {
			Layer fromLayer = layers[i];

			for (int j = 0; j < fromLayer.getNeurons().length; j++) {
				Neuron nextNeuron = fromLayer.getNeurons()[j];

				for (int k = 0; k < nextNeuron.getOutgoingSynapses().length; k++) {
					Synapse nextSynapse = nextNeuron.getOutgoingSynapses()[k];

					Float averageDelta = nextSynapse.getAverageAccumulatedDeltas();

					if (learningRate != null) {
						averageDelta = averageDelta * learningRate;
					}

					Float regularization = 0.0f;

					if (weightDecayPercent != null && weightDecayPercent != 0.0f && !nextNeuron.isBias()) {
						regularization = nextSynapse.getWeight() * weightDecayPercent;

						if (learningRate != null) {
							regularization = regularization * learningRate;
						}
					}

					nextSynapse.setWeight(nextSynapse.getWeight() - averageDelta - regularization);

					nextSynapse.clearAccumulatedDeltas();
				}
			}
		}

		return System.currentTimeMillis() - start;
	}

	/**
	 * @return the problemType
	 */
	public ProblemType getProblemType() {
		return problemType;
	}

	/**
	 * @return the inputLayer
	 */
	public Layer getInputLayer() {
		return layers[0];
	}

	/**
	 * @return the layers
	 */
	public Layer[] getLayers() {
		return layers;
	}

	/**
	 * @return the outputLayer
	 */
	public Layer getOutputLayer() {
		return layers[layers.length - 1];
	}

	/**
	 * @return the biasWeight
	 */
	public Float getBiasWeight() {
		return biasWeight;
	}

	/**
	 * @param biasWeight
	 *            the biasWeight to set
	 */
	public void setBiasWeight(Float biasWeight) {
		this.biasWeight = biasWeight;
	}

	/**
	 * @param problemType
	 *            the problemType to set
	 */
	public void setProblemType(ProblemType problemType) {
		this.problemType = problemType;
	}

	/**
	 * @param layers
	 *            the layers to set
	 */
	public void setLayers(Layer[] layers) {
		this.layers = layers;
	}

	/**
	 * @return the number of samples trained
	 */
	public long getSamplesTrained() {
		return samplesTrained;
	}

	/**
	 * @param samplesTrained the samplesTrained to set
	 */
	public void setSamplesTrained(long samplesTrained) {
		this.samplesTrained = samplesTrained;
	}

	public void incrementSamplesTrained() {
		this.samplesTrained ++;
	}
}
