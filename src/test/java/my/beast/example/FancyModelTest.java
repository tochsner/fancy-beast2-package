package my.beast.example;

import beast.base.evolution.datatype.Aminoacid;
import beast.base.evolution.datatype.Nucleotide;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.evolution.substitutionmodel.Frequencies;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.spec.inference.parameter.SimplexParam;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class FancyModelTest {

    // reference values below (t=1e-4 and t=1.0 blocks) were computed independently, outside
    // this codebase, via scaling-and-squaring matrix exponentiation of the full 5x5 rate
    // matrix (A, C, G, T, X) for baseRate=1, so they exercise FancyModel end to end rather
    // than re-deriving its own formulas

    private static FancyModel newModel(double baseRate) {
        RealScalarParam<PositiveReal> rate = new RealScalarParam<>(baseRate, PositiveReal.INSTANCE);
        rate.setID("baseRate");
        FancyModel model = new FancyModel();
        model.initByName("baseRate", rate);
        return model;
    }

    @Test
    void hasFourObservableStates() {
        assertThat(newModel(1.0).getStateCount()).isEqualTo(4);
    }

    @Test
    void frequenciesAreUniform() {
        assertThat(newModel(1.0).getFrequencies()).containsExactly(0.25, 0.25, 0.25, 0.25);
    }

    @Test
    void rejectsExplicitFrequencies() {
        RealScalarParam<PositiveReal> rate = new RealScalarParam<>(1.0, PositiveReal.INSTANCE);
        rate.setID("baseRate");
        Frequencies freqs = new Frequencies(new SimplexParam(new double[]{0.25, 0.25, 0.25, 0.25}));

        FancyModel model = new FancyModel();
        assertThatThrownBy(() -> model.initByName("baseRate", rate, "frequencies", freqs))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void handlesOnlyNucleotideData() {
        FancyModel model = newModel(1.0);
        assertThat(model.canHandleDataType(new Nucleotide())).isTrue();
        assertThat(model.canHandleDataType(new Aminoacid())).isFalse();
    }

    @Test
    void rateMatrixMatchesAlphabetDistanceToX() {
        // direct nucleotide-to-nucleotide rates are always zero: every substitution must
        // detour through the hidden nucleotide X
        double[] rate = newModel(1.0).getRateMatrix(null);
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                if (i != j) {
                    assertThat(rate[i * 4 + j]).isEqualTo(0.0);
                }
            }
        }

        // diagonal equals minus the rate to X (the only exit route). Alphabet positions:
        // A=1, C=3, G=7, T=20, X=24, so e.g. T<->X = baseRate * 3, matching the task's
        // worked example (U, V, W lie between T and X)
        assertThat(rate[0 * 4 + 0]).isEqualTo(-22.0); // A<->X: 22 letters between (B..W)
        assertThat(rate[1 * 4 + 1]).isEqualTo(-20.0); // C<->X: 20 letters between (D..W)
        assertThat(rate[2 * 4 + 2]).isEqualTo(-16.0); // G<->X: 16 letters between (H..W)
        assertThat(rate[3 * 4 + 3]).isEqualTo(-3.0);  // T<->X: 3 letters between (U,V,W)
    }

    @Test
    void rateMatrixScalesLinearlyWithBaseRate() {
        double[] rate = newModel(2.5).getRateMatrix(null);
        assertThat(rate[3 * 4 + 3]).isEqualTo(-7.5); // T<->X: 2.5 * 3
    }

    @Test
    void transitionProbabilitiesAreIdentityAtZeroDistance() {
        FancyModel model = newModel(1.0);
        double[] matrix = new double[16];
        model.getTransitionProbabilities(null, 0.0, 0.0, 1.0, matrix);

        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                assertThat(matrix[i * 4 + j]).isCloseTo(i == j ? 1.0 : 0.0, within(1e-9));
            }
        }
    }

    @Test
    void shortBranchLengthMatchesIndependentReference() {
        // for a very short branch, escaping to X dominates (a direct nucleotide-to-nucleotide
        // move needs two hops through X, so it is a much smaller, second-order effect)
        FancyModel model = newModel(1.0);
        double[] matrix = new double[16];
        model.getTransitionProbabilities(null, 1e-4, 0.0, 1.0, matrix);

        assertThat(matrix[0 * 4 + 0]).isCloseTo(0.99780483, within(1e-6)); // A stays A
        assertThat(matrix[1 * 4 + 1]).isCloseTo(0.99800399, within(1e-6)); // C stays C
        assertThat(matrix[2 * 4 + 2]).isCloseTo(0.99840256, within(1e-6)); // G stays G
        assertThat(matrix[3 * 4 + 3]).isCloseTo(0.99970009, within(1e-6)); // T stays T

        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                if (i != j) {
                    assertThat(matrix[i * 4 + j]).isLessThan(1e-5);
                }
            }
        }
    }

    @Test
    void matchesIndependentReferenceAtUnitDistance() {
        FancyModel model = newModel(1.0);
        double[] matrix = new double[16];
        model.getTransitionProbabilities(null, 1.0, 0.0, 1.0, matrix);

        assertThat(matrix[0 * 4 + 0]).isCloseTo(0.20135889, within(1e-6)); // A->A
        assertThat(matrix[0 * 4 + 1]).isCloseTo(0.20138634, within(1e-6)); // A->C
        assertThat(matrix[0 * 4 + 2]).isCloseTo(0.20146788, within(1e-6)); // A->G
        assertThat(matrix[0 * 4 + 3]).isCloseTo(0.19465263, within(1e-6)); // A->T
        assertThat(matrix[3 * 4 + 3]).isCloseTo(0.22104259, within(1e-6)); // T->T
    }

    @Test
    void transitionProbabilitiesAreSymmetric() {
        // Q is symmetric (rate(N,X) == rate(X,N) for every nucleotide N), so the resulting
        // transition probability matrix is symmetric too
        FancyModel model = newModel(1.0);
        double[] matrix = new double[16];
        model.getTransitionProbabilities(null, 1.0, 0.0, 1.0, matrix);

        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                assertThat(matrix[i * 4 + j]).isCloseTo(matrix[j * 4 + i], within(1e-9));
            }
        }
    }

    @Test
    void probabilityLeaksIntoTheHiddenState() {
        // since X is never observed, the four visible transition probabilities from any
        // state never sum to 1 for t>0: part of the probability mass sits in state X
        FancyModel model = newModel(1.0);
        double[] matrix = new double[16];
        model.getTransitionProbabilities(null, 1.0, 0.0, 1.0, matrix);

        for (int i = 0; i < 4; i++) {
            double rowSum = matrix[i * 4] + matrix[i * 4 + 1] + matrix[i * 4 + 2] + matrix[i * 4 + 3];
            assertThat(rowSum).isLessThan(1.0);
        }
    }

    @Test
    void transitionProbabilitiesConvergeToStationaryDistribution() {
        // the full 5-state chain is reversible with a uniform stationary distribution
        // (1/5 per state), so every visible entry converges to 0.2, not 0.25 -- the
        // remaining 0.2 sits in the never-observed state X
        FancyModel model = newModel(1.0);
        double[] matrix = new double[16];
        model.getTransitionProbabilities(null, 100.0, 0.0, 1.0, matrix);

        for (double p : matrix) {
            assertThat(p).isCloseTo(0.2, within(1e-6));
        }
    }

    @Test
    void baseRateAndDistanceAreInterchangeable() {
        // Q scales linearly with baseRate, so baseRate=2.5 over distance 0.4 should match
        // baseRate=1 over distance 1.0 (2.5 * 0.4 == 1.0)
        double[] reference = new double[16];
        newModel(1.0).getTransitionProbabilities(null, 1.0, 0.0, 1.0, reference);

        double[] scaled = new double[16];
        newModel(2.5).getTransitionProbabilities(null, 0.4, 0.0, 1.0, scaled);

        for (int i = 0; i < 16; i++) {
            assertThat(scaled[i]).isCloseTo(reference[i], within(1e-9));
        }
    }
}
