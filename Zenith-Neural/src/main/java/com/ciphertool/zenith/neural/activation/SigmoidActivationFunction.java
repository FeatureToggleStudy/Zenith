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

package com.ciphertool.zenith.neural.activation;

import com.ciphertool.zenith.math.MathConstants;

public class SigmoidActivationFunction implements ActivationFunction {
	@Override
	public Float transformInputSignal(Float sum, Float[] allSums) {
		Float denominator = 1.0f + (float) Math.pow(MathConstants.EULERS_CONSTANT, sum * -1.0);

		return 1.0f / denominator;
	}

	@Override
	public Float calculateDerivative(Float sum, Float[] allSums) {
		Float sigmoid = transformInputSignal(sum, null);

		return sigmoid * (1.0f - sigmoid);
	}
}
