package org.encog.ml.ea.species;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.encog.Encog;
import org.encog.ml.ea.genome.Genome;
import org.encog.ml.ea.population.Population;
import org.encog.ml.ea.sort.SortGenomesForSpecies;
import org.encog.ml.ea.sort.SpeciesComparator;
import org.encog.ml.ea.train.EvolutionaryAlgorithm;
import org.encog.ml.genetic.GeneticError;

public abstract class ThresholdSpeciation implements Speciation, Serializable {
	/**
	 * The NEAT training being used.
	 */
	private EvolutionaryAlgorithm owner;

	/**
	 * THe minimum compatibility that two genes must have to be in the same
	 * species.
	 */
	private double compatibilityThreshold = 1.0;

	/**
	 * The maximum number of generations allows with no improvement. After this
	 * the genomes in this species are not allowed to reproduce or continue.
	 * This does not apply to top species.
	 */
	private int numGensAllowedNoImprovement = 15;

	/**
	 * The maximum number of species. This is just a target. If the number of
	 * species goes over this number then the compatibilityThreshold is
	 * increased to decrease the number of species.
	 * 
	 */
	private int maxNumberOfSpecies = 40;

	/**
	 * The method used to sort the genomes in the species. More desirable
	 * genomes should come first for later selection.
	 */
	private SortGenomesForSpecies sortGenomes;
	
	private Population neatPopulation;

	/**
	 * Add a genome.
	 * 
	 * @param species
	 *            The species to add to.
	 * @param genome
	 *            The genome to add.
	 */
	public void addSpeciesMember(final Species species,
			final Genome genome) {

		if (this.owner.isValidationMode()) {
			if (species.getMembers().contains(genome)) {
				throw new GeneticError("Species already contains genome: "
						+ genome.toString());
			}
		}

		if (this.owner.getSelectionComparator().compare(genome,
				species.getLeader()) < 0) {
			species.setBestScore(genome.getScore());
			species.setGensNoImprovement(0);
			species.setLeader(genome);
		}

		species.add(genome);
	}
	
	/**
	 * Adjust the species compatibility threshold. This prevents us from having
	 * too many species. Dynamically increase or decrease the
	 * compatibilityThreshold.
	 */
	private void adjustCompatibilityThreshold() {

		// has this been disabled (unlimited species)
		if (this.maxNumberOfSpecies < 1) {
			return;
		}

		final double thresholdIncrement = 0.01;

		if (this.neatPopulation.getSpecies().size() > this.maxNumberOfSpecies) {
			this.compatibilityThreshold += thresholdIncrement;
		}

		else if (this.neatPopulation.getSpecies().size() < 2) {
			this.compatibilityThreshold -= thresholdIncrement;
		}
	}

	/**
	 * Divide up the potential offspring by the most fit species. To do this we
	 * look at the total species score, vs each individual species percent
	 * contribution to that score.
	 * 
	 * @param speciesCollection
	 *            The current species list.
	 * @param totalSpeciesScore
	 *            The total score over all species.
	 */
	private void divideByFittestSpecies(
			final List<Species> speciesCollection,
			final double totalSpeciesScore) {
		Species bestSpecies = null;

		// determine the best species.
		if (this.owner.getBestGenome() != null) {
			bestSpecies = ((Genome)this.owner.getBestGenome()).getSpecies();
		}

		// loop over all species and calculate its share
		final Object[] speciesArray = speciesCollection.toArray();
		for (final Object element : speciesArray) {
			final Species species = (Species) element;
			// calculate the species share based on the percent of the total
			// species score
			int share = (int) Math
					.round((species.getOffspringShare() / totalSpeciesScore)
							* this.owner.getPopulation().getPopulationSize());

			// do not give the best species a zero-share
			if ((species == bestSpecies) && (share == 0)) {
				share = 1;
			}

			// if the share is zero, then remove the species
			if ((species.getMembers().size() == 0) || (share == 0)) {
				speciesCollection.remove(species);
			}
			// if the species has not improved over the specified number of
			// generations, then remove it.
			else if ((species.getGensNoImprovement() > this.numGensAllowedNoImprovement)
					&& (species != bestSpecies)) {
				speciesCollection.remove(species);
			} else {
				// otherwise assign a share and sort the members.
				species.setOffspringCount(share);
				Collections.sort(species.getMembers(), this.sortGenomes);
			}
		}
	}

	/**
	 * If no species has a good score then divide the potential offspring amount
	 * all species evenly.
	 * 
	 * @param speciesCollection
	 *            The current set of species.
	 */
	private void divideEven(final List<Species> speciesCollection) {
		final double ratio = 1.0 / speciesCollection.size();
		for (final Species species : speciesCollection) {
			final int share = (int) Math.round(ratio
					* this.owner.getPopulation().getPopulationSize());
			species.setOffspringCount(share);
		}
	}
	
	/**
	 * @return the compatibilityThreshold
	 */
	public double getCompatibilityThreshold() {
		return this.compatibilityThreshold;
	}
	
	/**
	 * @return the maxNumberOfSpecies
	 */
	public int getMaxNumberOfSpecies() {
		return this.maxNumberOfSpecies;
	}

	/**
	 * @return the numGensAllowedNoImprovement
	 */
	public int getNumGensAllowedNoImprovement() {
		return this.numGensAllowedNoImprovement;
	}

	/**
	 * @return the owner
	 */
	public EvolutionaryAlgorithm getOwner() {
		return this.owner;
	}

	/**
	 * @return the sortGenomes
	 */
	public SortGenomesForSpecies getSortGenomes() {
		return this.sortGenomes;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void init(final EvolutionaryAlgorithm theOwner) {
		this.owner = theOwner;
		this.neatPopulation = theOwner.getPopulation();
		this.sortGenomes = new SortGenomesForSpecies(this.owner);
	}

	/**
	 * Level off all of the species shares so that they add up to the desired
	 * population size. If they do not add up to the desired species size, this
	 * was a result of rounding the floating point share amounts to integers.
	 */
	private void levelOff() {
		int total = 0;
		final List<Species> list = this.neatPopulation
				.getSpecies();
		Collections.sort(list, new SpeciesComparator(this.owner));

		// best species gets at least one offspring
		if (list.get(0).getOffspringCount() == 0) {
			list.get(0).setOffspringCount(1);
		}

		// total up offspring
		for (final Species species : list) {
			total += species.getOffspringCount();
		}

		// how does the total offspring count match the target
		int diff = this.neatPopulation.getPopulationSize() - total;

		if (diff < 0) {
			// need less offspring
			int index = list.size() - 1;
			while ((diff != 0) && (index > 0)) {
				final Species species = list.get(index);
				final int t = Math.min(species.getOffspringCount(),
						Math.abs(diff));
				species.setOffspringCount(species.getOffspringCount() - t);
				if (species.getOffspringCount() == 0) {
					list.remove(index);
				}
				diff += t;
				index--;
			}
		} else {
			// need more offspring
			list.get(0).setOffspringCount(
					list.get(0).getOffspringCount() + diff);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void performSpeciation(List<Genome> genomeList) {
		final List<Genome> newGenomeList = resetSpecies(genomeList);
		speciateAndCalculateSpawnLevels(newGenomeList);
	}

	/**
	 * Reset for an iteration.
	 * 
	 * @return
	 */
	private List<Genome> resetSpecies(List<Genome> inputGenomes) {
		final List<Genome> result = new ArrayList<Genome>();
		final Object[] speciesArray = this.neatPopulation
				.getSpecies().toArray();

		// Add the NEAT genomes
		for (final Genome genome : inputGenomes) {
			result.add(genome);
		}

		for (final Object element : speciesArray) {
			final BasicSpecies s = (BasicSpecies) element;
			s.purge();

			// did the leader die? If so, disband the species. (but don't kill
			// the genomes)
			if (!inputGenomes.contains(s.getLeader())) {
				this.neatPopulation.getSpecies().remove(s);
			} else if ((s.getGensNoImprovement() > this.numGensAllowedNoImprovement)
					&& this.owner.getSelectionComparator().isBetterThan(
							this.owner.getError(), s.getBestScore())) {
				this.neatPopulation.getSpecies().remove(s);
			}

			// remove the leader from the list we return. the leader already has
			// a species
			result.remove(s.getLeader());
		}

		return result;
	}

	/**
	 * @param compatibilityThreshold
	 *            the compatibilityThreshold to set
	 */
	public void setCompatibilityThreshold(final double compatibilityThreshold) {
		this.compatibilityThreshold = compatibilityThreshold;
	}
	
	/**
	 * @param maxNumberOfSpecies
	 *            the maxNumberOfSpecies to set
	 */
	public void setMaxNumberOfSpecies(final int maxNumberOfSpecies) {
		this.maxNumberOfSpecies = maxNumberOfSpecies;
	}

	/**
	 * @param numGensAllowedNoImprovement
	 *            the numGensAllowedNoImprovement to set
	 */
	public void setNumGensAllowedNoImprovement(
			final int numGensAllowedNoImprovement) {
		this.numGensAllowedNoImprovement = numGensAllowedNoImprovement;
	}

	/**
	 * @param sortGenomes
	 *            the sortGenomes to set
	 */
	public void setSortGenomes(final SortGenomesForSpecies sortGenomes) {
		this.sortGenomes = sortGenomes;
	}

	/**
	 * Determine the species.
	 * 
	 * @param genomes
	 *            The genomes to speciate.
	 */
	private void speciateAndCalculateSpawnLevels(final List<Genome> genomes) {
		double maxScore = 0;

		final List<Species> speciesCollection = this.neatPopulation.getSpecies();

		// calculate compatibility between genomes and species
		adjustCompatibilityThreshold();

		// assign genomes to species (if any exist)
		for (final Genome g : genomes) {
			Species currentSpecies = null;
			final Genome genome = (Genome) g;

			if (!Double.isNaN(genome.getScore())
					&& !Double.isInfinite(genome.getScore())) {
				maxScore = Math.max(genome.getScore(), maxScore);
			}

			for (final Species s : speciesCollection) {
				final double compatibility = getCompatibilityScore(genome,
						(Genome)s.getLeader());

				if (compatibility <= this.compatibilityThreshold) {
					currentSpecies = s;
					addSpeciesMember(s, genome);
					genome.setSpecies(s);
					break;
				}
			}

			// if this genome did not fall into any existing species, create a
			// new species
			if (currentSpecies == null) {
				currentSpecies = new BasicSpecies(
						this.neatPopulation, genome);
				this.neatPopulation.getSpecies().add(currentSpecies);
			}
		}

		//
		double totalSpeciesScore = 0;
		for (final Species species : speciesCollection) {
			totalSpeciesScore += species.calculateShare(this.owner
					.getScoreFunction().shouldMinimize(), maxScore);
		}

		if (totalSpeciesScore < Encog.DEFAULT_DOUBLE_EQUAL) {
			// This should not happen much, or if it does, only in the
			// beginning.
			// All species scored zero. So they are all equally bad. Just divide
			// up the right to produce offspring evenly.
			divideEven(speciesCollection);
		} else {
			// Divide up the number of offspring produced to the most fit
			// species.
			divideByFittestSpecies(speciesCollection, totalSpeciesScore);
		}

		levelOff();

	}
	
	@Override
	public boolean isIterationBased() {
		return true;
	}

	public abstract double getCompatibilityScore(Genome genome1, Genome genome2);
}