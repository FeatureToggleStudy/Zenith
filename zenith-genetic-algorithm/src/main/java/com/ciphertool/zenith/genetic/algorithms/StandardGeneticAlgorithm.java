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

package com.ciphertool.zenith.genetic.algorithms;

import com.ciphertool.zenith.genetic.GeneticAlgorithmStrategy;
import com.ciphertool.zenith.genetic.entities.Chromosome;
import com.ciphertool.zenith.genetic.population.Population;
import com.ciphertool.zenith.genetic.population.StandardPopulation;
import com.ciphertool.zenith.genetic.statistics.ExecutionStatistics;
import com.ciphertool.zenith.genetic.statistics.GenerationStatistics;
import com.ciphertool.zenith.genetic.statistics.PerformanceStatistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class StandardGeneticAlgorithm {
    private Logger log = LoggerFactory.getLogger(getClass());

    @Autowired
    private TaskExecutor taskExecutor;

    @Autowired
    private Population population;

    private GeneticAlgorithmStrategy strategy;
    private Integer generationCount = 0;
    private ExecutionStatistics executionStatistics;
    private AtomicInteger mutations = new AtomicInteger(0);

    public void initialize() {
        validateParameters();

        this.generationCount = 0;

        this.executionStatistics = new ExecutionStatistics(LocalDateTime.now(), this.strategy);

        this.spawnInitialPopulation();
    }

    public void spawnInitialPopulation() {
        GenerationStatistics generationStatistics = new GenerationStatistics(this.generationCount);

        long start = System.currentTimeMillis();

        this.population.clearIndividuals();

        this.population.breed();

        long startEntropyCalculation = System.currentTimeMillis();
        BigDecimal entropy = this.population.calculateEntropy();
        generationStatistics.setEntropy(entropy);
        generationStatistics.getPerformanceStatistics().setEntropyMillis(System.currentTimeMillis() - startEntropyCalculation);

        long startEvaluation = System.currentTimeMillis();
        this.population.evaluateFitness(generationStatistics);
        generationStatistics.getPerformanceStatistics().setEvaluationMillis(System.currentTimeMillis() - startEvaluation);

        long executionTime = System.currentTimeMillis() - start;
        generationStatistics.getPerformanceStatistics().setTotalMillis(executionTime);

        log.info("Took {}ms to spawn initial population of size {}", executionTime, this.population.size());

        log.info(generationStatistics.toString());
    }

    public void evolve() {
        initialize();

        do {
            proceedWithNextGeneration();
        } while ((this.strategy.getMaxGenerations() < 0 || this.generationCount < this.strategy.getMaxGenerations()));

        finish();
    }

    protected void validateParameters() {
        List<String> validationErrors = new ArrayList<>();

        if (strategy.getPopulationSize() == null || strategy.getPopulationSize() <= 0) {
            validationErrors.add("Parameter 'populationSize' must be greater than zero.");
        }

        if (strategy.getMutationRate() == null || strategy.getMutationRate() < 0) {
            validationErrors.add("Parameter 'mutationRate' must be greater than or equal to zero.");
        }

        if (strategy.getMaxMutationsPerIndividual() == null || strategy.getMaxMutationsPerIndividual() < 0) {
            validationErrors.add("Parameter 'maxMutationsPerIndividual' must be greater than or equal to zero.");
        }

        if (strategy.getMaxGenerations() == null || strategy.getMaxGenerations() == 0) {
            validationErrors.add("Parameter 'maxGenerations' cannot be null and must not equal zero.");
        }

        if (strategy.getCrossoverAlgorithm() == null) {
            validationErrors.add("Parameter 'crossoverAlgorithm' cannot be null.");
        }

        if (strategy.getFitnessEvaluator() == null) {
            validationErrors.add("Parameter 'fitnessEvaluator' cannot be null.");
        }

        if (strategy.getMutationAlgorithm() == null) {
            validationErrors.add("Parameter 'mutationAlgorithm' cannot be null.");
        }

        if (strategy.getSelector() == null) {
            validationErrors.add("Parameter 'selectorMethod' cannot be null.");
        }

        if (validationErrors.size() > 0) {
            StringBuilder sb = new StringBuilder();
            sb.append("Unable to execute genetic algorithm because one or more of the required parameters are missing.  The validation errors are:");

            for (String validationError : validationErrors) {
                sb.append("\n\t-" + validationError);
            }

            throw new IllegalStateException(sb.toString());
        }
    }

    public void proceedWithNextGeneration() {
        this.generationCount++;

        GenerationStatistics generationStatistics = new GenerationStatistics(this.generationCount);

        long generationStart = System.currentTimeMillis();

        int populationSizeBeforeGeneration = this.population.size();

        PerformanceStatistics performanceStats = new PerformanceStatistics();

        List<Chromosome> moms = new ArrayList<>();
        List<Chromosome> dads = new ArrayList<>();

        long startSelection = System.currentTimeMillis();
        this.population.reIndexSelector();
        select(populationSizeBeforeGeneration, moms, dads);
        performanceStats.setSelectionMillis(System.currentTimeMillis() - startSelection);

        long startCrossover = System.currentTimeMillis();
        generationStatistics.setNumberOfCrossovers(crossover(populationSizeBeforeGeneration, moms, dads));
        performanceStats.setCrossoverMillis(System.currentTimeMillis() - startCrossover);

        long startMutation = System.currentTimeMillis();
        generationStatistics.setNumberOfMutations(mutate());
        performanceStats.setMutationMillis(System.currentTimeMillis() - startMutation);

        long startEntropyCalculation = System.currentTimeMillis();
        BigDecimal entropy = this.population.calculateEntropy();
        generationStatistics.setEntropy(entropy);
        performanceStats.setEntropyMillis(System.currentTimeMillis() - startEntropyCalculation);

        long startEvaluation = System.currentTimeMillis();
        this.population.evaluateFitness(generationStatistics);
        performanceStats.setEvaluationMillis(System.currentTimeMillis() - startEvaluation);

        performanceStats.setTotalMillis(System.currentTimeMillis() - generationStart);
        generationStatistics.setPerformanceStatistics(performanceStats);

        log.info(generationStatistics.toString());

        this.executionStatistics.addGenerationStatistics(generationStatistics);
    }

    public void select(int initialPopulationSize, List<Chromosome> moms, List<Chromosome> dads) {
        long pairsToCrossover = (initialPopulationSize - strategy.getElitism());

        List<FutureTask<SelectionResult>> futureTasks = new ArrayList<>();
        FutureTask<SelectionResult> futureTask;

        /*
         * Execute each selection concurrently. Each should produce two children, but this is not necessarily always
         * guaranteed.
         */
        for (int i = 0; i < Math.max(0, pairsToCrossover); i++) {
            futureTask = new FutureTask<>(new SelectionTask());
            futureTasks.add(futureTask);
            this.taskExecutor.execute(futureTask);
        }

        // Add the result of each FutureTask to the Lists of Chromosomes selected for subsequent crossover
        for (FutureTask<SelectionResult> future : futureTasks) {
            try {
                SelectionResult result = future.get();
                moms.add(result.getMom());
                dads.add(result.getDad());
            } catch (InterruptedException ie) {
                log.error("Caught InterruptedException while waiting for SelectionTask ", ie);
            } catch (ExecutionException ee) {
                log.error("Caught ExecutionException while waiting for SelectionTask ", ee);
            }
        }
    }

    public int crossover(int pairsToCrossover, List<Chromosome> moms, List<Chromosome> dads) {
        if (this.population.size() < 2) {
            log.info("Unable to perform crossover because there is only 1 individual in the population. Returning.");

            return 0;
        }

        log.debug("Pairs to crossover: {}", pairsToCrossover);

        List<Chromosome> crossoverResults = doConcurrentCrossovers(moms, dads);
        List<Chromosome> childrenToAdd = new ArrayList<>();

        if (crossoverResults != null && !crossoverResults.isEmpty()) {
            childrenToAdd.addAll(crossoverResults);
        }

        if (childrenToAdd == null || (childrenToAdd.size() + strategy.getElitism()) < pairsToCrossover) {
            log.error("{} children produced from concurrent crossover execution.  Expected {} children.", ((null == childrenToAdd) ? "No" : childrenToAdd.size()), pairsToCrossover);

            return ((null == childrenToAdd) ? 0 : childrenToAdd.size());
        }

        List<Chromosome> eliteIndividuals = new ArrayList<>();

        if (strategy.getElitism() > 0) {
            this.population.sortIndividuals();

            for (int i = this.population.size() - 1; i >= this.population.size() - strategy.getElitism(); i--) {
                eliteIndividuals.add(this.population.getIndividuals().get(i));
            }
        }

        this.population.clearIndividuals();

        for (Chromosome elite : eliteIndividuals) {
            this.population.addIndividual(elite);
        }

        for (Chromosome child : childrenToAdd) {
            this.population.addIndividual(child);
        }

        return childrenToAdd.size();
    }

    protected List<Chromosome> doConcurrentCrossovers(List<Chromosome> moms, List<Chromosome> dads) {
        if (moms.size() != dads.size()) {
            throw new IllegalStateException(
                    "Attempted to perform crossover on the population, but there are not an equal number of moms and dads.  Something is wrong.  Moms: "
                            + moms.size() + ", Dads:  " + dads.size());
        }

        List<FutureTask<List<Chromosome>>> futureTasks = new ArrayList<>();
        FutureTask<List<Chromosome>> futureTask;

        Chromosome mom;
        Chromosome dad;

        /*
         * Execute each crossover concurrently. Parents should produce two children, but this is not necessarily always
         * guaranteed.
         */
        for (int i = 0; i < moms.size(); i++) {
            mom = moms.get(i);
            dad = dads.get(i);

            futureTask = new FutureTask<>(new CrossoverTask(mom, dad));
            futureTasks.add(futureTask);
            this.taskExecutor.execute(futureTask);
        }

        List<Chromosome> childrenToAdd = new ArrayList<>();

        // Add the result of each FutureTask to the population since it represents a new child Chromosome.
        for (FutureTask<List<Chromosome>> future : futureTasks) {
            try {
                /*
                 * Add children after all crossover operations are completed so that children are not inadvertently
                 * breeding immediately after birth.
                 */
                childrenToAdd.addAll(future.get());
            } catch (InterruptedException ie) {
                log.error("Caught InterruptedException while waiting for CrossoverTask ", ie);
            } catch (ExecutionException ee) {
                log.error("Caught ExecutionException while waiting for CrossoverTask ", ee);
            }
        }

        return childrenToAdd;
    }

    public int mutate() {
        StandardPopulation standardPopulation = (StandardPopulation) this.population;

        List<FutureTask<Void>> futureTasks = new ArrayList<>();
        FutureTask<Void> futureTask;

        mutations.set(0);

        standardPopulation.sortIndividuals();

        /*
         * Execute each mutation concurrently.
         */
        for (int i = this.population.size() - strategy.getElitism() - 1; i >= 0; i--) {
            futureTask = new FutureTask<>(new MutationTask(this.population.getIndividuals().get(i)));
            futureTasks.add(futureTask);
            this.taskExecutor.execute(futureTask);
        }

        for (FutureTask<Void> future : futureTasks) {
            try {
                future.get();
            } catch (InterruptedException ie) {
                log.error("Caught InterruptedException while waiting for MutationTask ", ie);
            } catch (ExecutionException ee) {
                log.error("Caught ExecutionException while waiting for MutationTask ", ee);
            }
        }

        return mutations.get();
    }

    public void finish() {
        long totalExecutionTime = 0;

        for (GenerationStatistics generationStatistics : this.executionStatistics.getGenerationStatisticsList()) {
            if (generationStatistics.getGeneration() == 0) {
                // This is the initial spawning of the population, which will potentially skew the average
                continue;
            }

            totalExecutionTime += generationStatistics.getPerformanceStatistics().getTotalMillis();
        }

        long averageExecutionTime = 0;

        if (this.generationCount > 1) {
            /*
             * We subtract 1 from the generation count because the zeroth generation is just the initial spawning of the
             * population. And, we add one to the result because the remainder from division is truncated due to use of
             * primitive type long, and we want to round up.
             */
            averageExecutionTime = (totalExecutionTime / (this.generationCount - 1)) + 1;
        } else {
            averageExecutionTime = totalExecutionTime;
        }

        log.info("Average generation time is {}ms.", averageExecutionTime);

        this.executionStatistics.setEndDateTime(LocalDateTime.now());

        // This needs to be reset to null in case the algorithm is re-run
        this.executionStatistics = null;
    }

    public Population getPopulation() {
        return population;
    }

    public void setStrategy(GeneticAlgorithmStrategy geneticAlgorithmStrategy) {
        this.population.setFitnessEvaluator(geneticAlgorithmStrategy.getFitnessEvaluator());
        this.population.setKnownSolutionFitnessEvaluator(geneticAlgorithmStrategy.getKnownSolutionFitnessEvaluator());
        this.population.setCompareToKnownSolution(geneticAlgorithmStrategy.getCompareToKnownSolution());
        this.population.setTargetSize(geneticAlgorithmStrategy.getPopulationSize());
        this.population.setSelector(geneticAlgorithmStrategy.getSelector());
        this.population.setBreeder(geneticAlgorithmStrategy.getBreeder());

        this.strategy = geneticAlgorithmStrategy;
    }

    /**
     * A concurrent task for performing a crossover of two parent Chromosomes, producing one child Chromosome.
     */
    protected class CrossoverTask implements Callable<List<Chromosome>> {
        private Chromosome mom;
        private Chromosome dad;

        public CrossoverTask(Chromosome mom, Chromosome dad) {
            this.mom = mom;
            this.dad = dad;
        }

        @SuppressWarnings("unchecked")
        @Override
        public List<Chromosome> call() {
            return strategy.getCrossoverAlgorithm().crossover(mom, dad);
        }
    }

    /**
     * A concurrent task for performing a crossover of two parent Chromosomes, producing one child Chromosome.
     */
    protected class MutationTask implements Callable<Void> {
        private Chromosome chromosome;

        public MutationTask(Chromosome chromosome) {
            this.chromosome = chromosome;
        }

        @SuppressWarnings("unchecked")
        @Override
        public Void call() {
            /*
             * Mutate a gene within the Chromosome. The original Chromosome has been cloned.
             */
            if (strategy.getMutationAlgorithm().mutateChromosome(chromosome)) {
                mutations.incrementAndGet();
            }

            return null;
        }
    }

    protected class SelectionTask implements Callable<SelectionResult> {
        public SelectionTask() {
        }

        @Override
        public SelectionResult call() {
            StandardPopulation standardPopulation = (StandardPopulation) population;

            int momIndex = standardPopulation.selectIndex();
            Chromosome mom = standardPopulation.getIndividuals().get(momIndex);

            int dadIndex = standardPopulation.selectIndex();
            // Ensure that dadIndex is different from momIndex
            dadIndex += (dadIndex == momIndex) ? ((dadIndex == 0) ? 1 : -1) : 0;
            Chromosome dad = standardPopulation.getIndividuals().get(dadIndex);

            return new SelectionResult(mom, dad);
        }
    }

    protected class SelectionResult {
        private Chromosome mom;
        private Chromosome dad;

        /**
         * @param mom the mom Chromosome to set
         * @param dad the dad Chromosome to set
         */
        public SelectionResult(Chromosome mom, Chromosome dad) {
            this.mom = mom;
            this.dad = dad;
        }

        /**
         * @return the mom Chromosome
         */
        public Chromosome getMom() {
            return mom;
        }

        /**
         * @return the dad Chromosome
         */
        public Chromosome getDad() {
            return dad;
        }
    }
}
