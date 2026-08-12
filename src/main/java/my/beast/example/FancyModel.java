package my.beast.example;

import beast.base.core.Description;
import beast.base.core.Input;
import beast.base.core.Input.Validate;
import beast.base.evolution.datatype.DataType;
import beast.base.evolution.datatype.Nucleotide;
import beast.base.evolution.substitutionmodel.DefaultEigenSystem;
import beast.base.evolution.substitutionmodel.EigenDecomposition;
import beast.base.evolution.substitutionmodel.EigenSystem;
import beast.base.evolution.tree.Node;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.evolution.substitutionmodel.Base;
import beast.base.spec.type.RealScalar;

/**
 * Nucleotide substitution model with a secret, never-observed nucleotide X. Every
 * substitution between conventional nucleotides must pass through X, so the true
 * process is a 5-state (A, C, G, T, X) star-topology chain with X as the hub.
 * The rate between any two of the five nucleotides equals baseRate times the number
 * of letters that lie between them in the English alphabet (A..Z), e.g. T&lt;-&gt;X has
 * rate baseRate*3, since U, V and W lie between T and X.
 */
@Description("Nucleotide substitution model in which every substitution passes through " +
        "a secret, never-observed nucleotide X. The rate between two nucleotides equals " +
        "baseRate times the number of letters between them in the alphabet.")
public class FancyModel extends Base {

    final public Input<RealScalar<PositiveReal>> baseRateInput = new Input<>(
            "baseRate", "rate multiplier applied to the alphabet distance between two nucleotides",
            Validate.REQUIRED);

    // the four conventional, observable nucleotides, matching the Nucleotide datatype
    public static final int STATE_COUNT = 4;

    // the true process also includes the hidden nucleotide X as a 5th state
    private static final int HIDDEN_STATE_COUNT = 5;

    // position of A, C, G, T, X in the alphabet A..Z (1-indexed), used to count the
    // number of letters between two nucleotides
    private static final int[] ALPHABET_POSITION = {1, 3, 7, 20, 24};

    private static final int X = HIDDEN_STATE_COUNT - 1;

    private final EigenSystem eigenSystem = new DefaultEigenSystem(HIDDEN_STATE_COUNT);
    private EigenDecomposition eigenDecomposition;
    private EigenDecomposition storedEigenDecomposition;
    private boolean updateMatrix = true;

    private double[] frequencies;

    public FancyModel() {
        // frequencies are fixed (see initAndValidate), not user-specifiable
        frequenciesInput.setRule(Validate.OPTIONAL);
    }

    @Override
    public void initAndValidate() {
        if (frequenciesInput.get() != null) {
            throw new RuntimeException("Frequencies must not be specified in FancyModel. " +
                    "The rate symmetry between each nucleotide and X forces a uniform " +
                    "equilibrium distribution.");
        }

        nrOfStates = STATE_COUNT;
        frequencies = new double[]{0.25, 0.25, 0.25, 0.25};
        updateMatrix = true;
    }

    @Override
    public double[] getFrequencies() {
        return frequencies;
    }

    @Override
    public void getTransitionProbabilities(Node node, double startTime, double endTime, double rate, double[] matrix) {
        double distance = (startTime - endTime) * rate;

        synchronized (this) {
            if (updateMatrix) {
                eigenDecomposition = eigenSystem.decomposeMatrix(buildRateMatrix());
                updateMatrix = false;
            }
        }

        double[] evec = eigenDecomposition.getEigenVectors();
        double[] ievc = eigenDecomposition.getInverseEigenVectors();
        double[] eval = eigenDecomposition.getEigenValues();

        double[] iexp = new double[HIDDEN_STATE_COUNT * HIDDEN_STATE_COUNT];
        for (int i = 0; i < HIDDEN_STATE_COUNT; i++) {
            double temp = Math.exp(distance * eval[i]);
            for (int j = 0; j < HIDDEN_STATE_COUNT; j++) {
                iexp[i * HIDDEN_STATE_COUNT + j] = ievc[i * HIDDEN_STATE_COUNT + j] * temp;
            }
        }

        // only the four conventional nucleotides are ever observed, so only the
        // top-left 4x4 block of the 5x5 transition probability matrix is needed
        for (int i = 0; i < STATE_COUNT; i++) {
            for (int j = 0; j < STATE_COUNT; j++) {
                double temp = 0.0;
                for (int k = 0; k < HIDDEN_STATE_COUNT; k++) {
                    temp += evec[i * HIDDEN_STATE_COUNT + k] * iexp[k * HIDDEN_STATE_COUNT + j];
                }
                matrix[i * STATE_COUNT + j] = Math.abs(temp);
            }
        }
    }

    @Override
    public EigenDecomposition getEigenDecomposition(Node node) {
        synchronized (this) {
            if (updateMatrix) {
                eigenDecomposition = eigenSystem.decomposeMatrix(buildRateMatrix());
                updateMatrix = false;
            }
        }
        return eigenDecomposition;
    }

    @Override
    public double[] getRateMatrix(Node node) {
        // instantaneous rate matrix restricted to the four conventional nucleotides:
        // direct nucleotide-to-nucleotide rates are always zero, since every
        // substitution must pass through the hidden nucleotide X
        double[][] q = buildRateMatrix();
        double[] rateMatrix = new double[STATE_COUNT * STATE_COUNT];
        for (int i = 0; i < STATE_COUNT; i++) {
            System.arraycopy(q[i], 0, rateMatrix, STATE_COUNT * i, STATE_COUNT);
        }
        return rateMatrix;
    }

    /**
     * Builds the instantaneous rate matrix for the full 5-state process (A, C, G, T,
     * X). Direct rates between two conventional nucleotides are zero; the rate
     * between a nucleotide and X is baseRate times the number of letters between
     * them in the alphabet. A fresh matrix is returned on every call, since
     * DefaultEigenSystem.decomposeMatrix mutates its argument in place.
     */
    private double[][] buildRateMatrix() {
        double baseRate = baseRateInput.get().get();

        double[][] q = new double[HIDDEN_STATE_COUNT][HIDDEN_STATE_COUNT];
        for (int i = 0; i < STATE_COUNT; i++) {
            int lettersBetween = Math.abs(ALPHABET_POSITION[i] - ALPHABET_POSITION[X]) - 1;
            double r = baseRate * lettersBetween;
            q[i][X] = r;
            q[X][i] = r;
        }

        for (int i = 0; i < HIDDEN_STATE_COUNT; i++) {
            double sum = 0.0;
            for (int j = 0; j < HIDDEN_STATE_COUNT; j++) {
                if (i != j) {
                    sum += q[i][j];
                }
            }
            q[i][i] = -sum;
        }
        return q;
    }

    @Override
    public boolean canHandleDataType(DataType dataType) {
        return dataType instanceof Nucleotide;
    }

    /**
     * CalculationNode implementations
     */
    @Override
    protected boolean requiresRecalculation() {
        updateMatrix = true;
        return true;
    }

    @Override
    protected void store() {
        if (eigenDecomposition != null) {
            storedEigenDecomposition = eigenDecomposition.copy();
        }
        super.store();
    }

    @Override
    protected void restore() {
        updateMatrix = true;
        if (storedEigenDecomposition != null) {
            eigenDecomposition = storedEigenDecomposition;
        }
        super.restore();
    }
}
