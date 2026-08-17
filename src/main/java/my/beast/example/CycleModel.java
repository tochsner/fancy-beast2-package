package my.beast.example;

import beast.base.core.Description;
import beast.base.core.Input;
import beast.base.core.Input.Validate;
import beast.base.evolution.datatype.DataType;
import beast.base.evolution.datatype.Nucleotide;
import beast.base.evolution.tree.Node;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.evolution.substitutionmodel.BasicComplexSubstitutionModel;
import beast.base.spec.type.RealVector;

/**
 * Cyclic nucleotide substitution model: A -> C -> G -> T -> A. rates[i] is the rate of leaving
 * state i. Non-reversible.
 */
@Description("Cyclic nucleotide substitution model: A -> C -> G -> T -> A. "
        + "rates[i] is the rate of leaving state i. Non-reversible.")
public class CycleModel extends BasicComplexSubstitutionModel {

    /** rates[i] = rate of the (only) substitution leaving state i. Length must be 4. */
    final public Input<RealVector<PositiveReal>> ratesInput =
        new Input<>("rates", "rate of the substitution leaving each state, "
                 + "in the order A, C, G, T; length 4", Validate.REQUIRED);

    public CycleModel() {
        // CycleModel derives its frequencies from the rates, so no frequencies input is needed
        // (and Base.frequenciesInput is Validate.REQUIRED by default).
        frequenciesInput.setRule(Validate.OPTIONAL);
    }

    @Override
    public void initAndValidate() {
        // Deliberately do not call super.initAndValidate(): the inherited implementation
        // dereferences frequenciesInput.get(), which we don't have.
        if (frequenciesInput.get() != null) {
            throw new IllegalArgumentException(
                "CycleModel does not accept a frequencies input: "
                + "equilibrium frequencies are derived from the rates.");
        }

        int nrRates = ratesInput.get().size();
        if (nrRates != 4) {
            throw new IllegalArgumentException(
                "CycleModel requires exactly 4 rates (one per state, in order A, C, G, T), "
                + "but got " + nrRates + ".");
        }

        updateMatrix = true;
        nrOfStates = 4;

        try {
            eigenSystem = createEigenSystem();
        } catch (SecurityException e) {
            throw new IllegalArgumentException(e.getMessage());
        }

        rateMatrix = new double[4][4];
        relativeRates = new double[12];
        storedRelativeRates = new double[12];
    }

    /**
     * @return sum of the reciprocals of the rates, Σ_k (1 / r_k)
     */
    private double sumOfReciprocals() {
        RealVector<PositiveReal> rates = ratesInput.get();
        double sum = 0.0;
        for (int i = 0; i < 4; i++) {
            sum += 1.0 / rates.get(i);
        }
        return sum;
    }

    @Override
    public double[] getFrequencies() {
        double sumInv = sumOfReciprocals();
        RealVector<PositiveReal> rates = ratesInput.get();
        double[] freqs = new double[4];
        for (int i = 0; i < 4; i++) {
            freqs[i] = (1.0 / rates.get(i)) / sumInv;
        }
        return freqs;
    }

    @Override
    public void setupRelativeRates() {
        RealVector<PositiveReal> rates = ratesInput.get();
        double mu = 4.0 / sumOfReciprocals();

        java.util.Arrays.fill(relativeRates, 0.0);
        relativeRates[0] = rates.get(0) / mu; // A->C, (i,j)=(0,1)
        relativeRates[4] = rates.get(1) / mu; // C->G, (i,j)=(1,2)
        relativeRates[8] = rates.get(2) / mu; // G->T, (i,j)=(2,3)
        relativeRates[9] = rates.get(3) / mu; // T->A, (i,j)=(3,0)
    }

    @Override
    public void setupRateMatrix() {
        RealVector<PositiveReal> rates = ratesInput.get();
        double mu = 4.0 / sumOfReciprocals();

        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                rateMatrix[i][j] = 0.0;
            }
        }
        for (int i = 0; i < 4; i++) {
            double r = rates.get(i) / mu;
            rateMatrix[i][(i + 1) % 4] = r;
            rateMatrix[i][i] = -r;
        }
    }

    @Override
    public double[] getRateMatrix(Node node) {
        synchronized (this) {
            if (updateMatrix) {
                setupRelativeRates();
                setupRateMatrix();
            }
        }
        return super.getRateMatrix(node);
    }

    @Override
    public boolean canHandleDataType(DataType dataType) {
        return dataType instanceof Nucleotide;
    }
}
