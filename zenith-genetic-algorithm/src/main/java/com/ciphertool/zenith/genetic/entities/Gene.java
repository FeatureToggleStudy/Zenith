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

package com.ciphertool.zenith.genetic.entities;

public interface Gene extends Cloneable {

	Gene clone();

	/**
	 * Sets the Chromosome that this Gene is a part of.
	 * 
	 * @param chromosome
	 */
	void setChromosome(Chromosome chromosome);

	/**
	 * @return the Chromosome that this Gene is a part of
	 */
	Chromosome getChromosome();

	/**
	 * @return whether this Gene matches that of a known solution
	 */
	boolean hasMatch();

	/**
	 * @param hasMatch
	 *            the hasMatch to set
	 */
	void setHasMatch(boolean hasMatch);
}