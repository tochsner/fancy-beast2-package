package my.beast.example;

import beast.base.evolution.datatype.Aminoacid;
import beast.base.evolution.datatype.Nucleotide;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.evolution.substitutionmodel.Frequencies;
import beast.base.spec.inference.parameter.RealVectorParam;
import beast.base.spec.inference.parameter.SimplexParam;
import beast.base.spec.type.Simplex;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behaviour tests for {@link CycleModel}, against the frozen spec at plan/SPEC.md.
 *
 * <p>Every expected value asserted here is derived independently from SPEC.md section 1
 * (the rate-matrix / equilibrium-frequency / normalisation maths) — never copied from the
 * implementation and never read back from a first run of the code. Where the spec supplies
 * a genuine closed form (Examples A and B, section 3), that closed form is implemented
 * directly in this test and evaluated at the required t's, rather than pasting the
 * tabulated decimals. Where the spec gives only eigenvalues without a full closed form
 * (Example C), the tabulated decimals are used, but they were independently re-derived
 * before writing this file: from Qn = Q/mu built straight from section 1's formulas, run
 * through a scaling-and-squaring Taylor matrix exponential implemented from scratch in
 * Python (not via the Java implementation under test). That independent computation agreed
 * with every number in SPEC.md section 3 to 12 decimal places for all three examples
 * (A, B and C, at t=0.1 and t=1.0) — no discrepancy was found, so all tabulated values are
 * used as-is below.
 */
class CycleModelTest {

    private static final double TOL = 1e-10;

    // ---- construction helpers -------------------------------------------------

    private static CycleModel model(double... rates) {
        CycleModel m = new CycleModel();
        m.initByName("rates", new RealVectorParam<>(rates, PositiveReal.INSTANCE));
        return m;
    }

    /** P(t) as a flat row-major 4x4, using distance = (startTime - endTime) * rate = t. */
    private static double[] P(CycleModel m, double t) {
        double[] mat = new double[16];
        m.getTransitionProbabilities(null, t, 0.0, 1.0, mat);
        return mat;
    }

    private static double[] flatten(double[][] a) {
        double[] flat = new double[16];
        for (int i = 0; i < 4; i++) {
            System.arraycopy(a[i], 0, flat, i * 4, 4);
        }
        return flat;
    }

    /** 4x4 flat row-major matrix product A*B. */
    private static double[] matMul(double[] a, double[] b) {
        double[] r = new double[16];
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                double s = 0.0;
                for (int k = 0; k < 4; k++) {
                    s += a[i * 4 + k] * b[k * 4 + j];
                }
                r[i * 4 + j] = s;
            }
        }
        return r;
    }

    /**
     * Independent re-derivation of pi from section 1.2: pi_i proportional to 1/r_i.
     * Computed straight from the rates, never read from the model.
     */
    private static double[] piOf(double[] rates) {
        double[] w = new double[4];
        double sum = 0.0;
        for (int i = 0; i < 4; i++) {
            w[i] = 1.0 / rates[i];
            sum += w[i];
        }
        double[] pi = new double[4];
        for (int i = 0; i < 4; i++) pi[i] = w[i] / sum;
        return pi;
    }

    /** Independent re-derivation of mu from section 1.3: mu = 4 / sum(1/r_k). */
    private static double muOf(double[] rates) {
        double sum = 0.0;
        for (double r : rates) sum += 1.0 / r;
        return 4.0 / sum;
    }

    /** Independent re-derivation of the normalised Qn from section 1.1/1.3, as a flat 4x4. */
    private static double[] qnOf(double[] rates) {
        double mu = muOf(rates);
        double[][] q = new double[4][4];
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) % 4;
            q[i][j] = rates[i] / mu;
            q[i][i] = -rates[i] / mu;
        }
        return flatten(q);
    }

    // ---- Example A: rates = (1,1,1,1) — closed form, section 3 ---------------

    /**
     * Closed form from SPEC.md section 3, Example A, re-implemented here directly
     * (not copied decimals): with d = (j-i) mod 4,
     *   d=0: 1/4 [1 + e^-2t + 2 e^-t cos t]
     *   d=1: 1/4 [1 - e^-2t + 2 e^-t sin t]
     *   d=2: 1/4 [1 + e^-2t - 2 e^-t cos t]
     *   d=3: 1/4 [1 - e^-2t - 2 e^-t sin t]
     */
    private static double exampleAClosedForm(int i, int j, double t) {
        int d = ((j - i) % 4 + 4) % 4;
        double e2 = Math.exp(-2 * t);
        double e1 = Math.exp(-t);
        switch (d) {
            case 0: return 0.25 * (1 + e2 + 2 * e1 * Math.cos(t));
            case 1: return 0.25 * (1 - e2 + 2 * e1 * Math.sin(t));
            case 2: return 0.25 * (1 + e2 - 2 * e1 * Math.cos(t));
            case 3: return 0.25 * (1 - e2 - 2 * e1 * Math.sin(t));
            default: throw new IllegalStateException();
        }
    }

    @Test
    void exampleA_matchesIndependentClosedForm() {
        CycleModel m = model(1, 1, 1, 1);
        for (double t : new double[]{0.1, 0.5, 1.0}) {
            double[] p = P(m, t);
            for (int i = 0; i < 4; i++) {
                for (int j = 0; j < 4; j++) {
                    assertEquals(exampleAClosedForm(i, j, t), p[i * 4 + j], TOL,
                            "t=" + t + " i=" + i + " j=" + j);
                }
            }
        }
    }

    @Test
    void exampleA_matchesTabulatedValuesAtT01() {
        // Sanity cross-check against SPEC section 3's tabulated P(0.1); the closed-form
        // test above is the independent derivation, this just pins the exact numbers.
        CycleModel m = model(1, 1, 1, 1);
        double[] p = P(m, 0.1);
        double[] expected = {
                0.904841188192, 0.090483817207, 0.004524188347, 0.000150806254,
                0.000150806254, 0.904841188192, 0.090483817207, 0.004524188347,
                0.004524188347, 0.000150806254, 0.904841188192, 0.090483817207,
                0.090483817207, 0.004524188347, 0.000150806254, 0.904841188192
        };
        for (int k = 0; k < 16; k++) {
            assertEquals(expected[k], p[k], TOL, "index " + k);
        }
    }

    // ---- Example B: rates = (1,6,1,6) — exact rational closed form, section 3 -

    /**
     * Exact closed form from SPEC.md section 3, Example B, re-implemented here from the
     * rational eigenvalues/eigenvectors given in the spec (not the tabulated decimals):
     * a = e^(-7t/4), b = e^(-7t/3), c = e^(-49t/12).
     *   P[0][0] = 3/7  + (3/2)a -      b + (1/14)c
     *   P[0][1] = 1/14 + (1/2)a - (1/2)b - (1/14)c
     *   P[0][2] = 3/7  - (3/2)a +      b + (1/14)c
     *   P[0][3] = 1/14 - (1/2)a + (1/2)b - (1/14)c
     *   P[1][0] = 3/7  -   3a   +   3b   - (3/7)c
     *   P[1][1] = 1/14 -    a   + (3/2)b + (3/7)c
     *   P[1][2] = 3/7  +   3a   -   3b   - (3/7)c
     *   P[1][3] = 1/14 +    a   - (3/2)b + (3/7)c
     *   P[2][j] = P[0][(j+2) mod 4], P[3][j] = P[1][(j+2) mod 4]
     */
    private static double[] exampleBClosedForm(double t) {
        double a = Math.exp(-7 * t / 4);
        double b = Math.exp(-7 * t / 3);
        double c = Math.exp(-49 * t / 12);
        double[] row0 = {
                3.0 / 7 + 1.5 * a - b + c / 14,
                1.0 / 14 + 0.5 * a - 0.5 * b - c / 14,
                3.0 / 7 - 1.5 * a + b + c / 14,
                1.0 / 14 - 0.5 * a + 0.5 * b - c / 14
        };
        double[] row1 = {
                3.0 / 7 - 3 * a + 3 * b - 3.0 / 7 * c,
                1.0 / 14 - a + 1.5 * b + 3.0 / 7 * c,
                3.0 / 7 + 3 * a - 3 * b - 3.0 / 7 * c,
                1.0 / 14 + a - 1.5 * b + 3.0 / 7 * c
        };
        double[] p = new double[16];
        System.arraycopy(row0, 0, p, 0, 4);
        System.arraycopy(row1, 0, p, 4, 4);
        for (int j = 0; j < 4; j++) {
            p[8 + j] = row0[(j + 2) % 4];
            p[12 + j] = row1[(j + 2) % 4];
        }
        return p;
    }

    @Test
    void exampleB_matchesIndependentClosedForm() {
        CycleModel m = model(1, 6, 1, 6);
        for (double t : new double[]{0.1, 1.0}) {
            double[] expected = exampleBClosedForm(t);
            double[] p = P(m, t);
            for (int k = 0; k < 16; k++) {
                assertEquals(expected[k], p[k], TOL, "t=" + t + " index " + k);
            }
        }
    }

    @Test
    void exampleB_equilibriumMatchesRationalDerivation() {
        // pi_i proportional to 1/r_i: 1/1, 1/6, 1/1, 1/6 -> normalise by sum = 7/3.
        CycleModel m = model(1, 6, 1, 6);
        double[] pi = m.getFrequencies();
        assertEquals(3.0 / 7, pi[0], TOL);
        assertEquals(1.0 / 14, pi[1], TOL);
        assertEquals(3.0 / 7, pi[2], TOL);
        assertEquals(1.0 / 14, pi[3], TOL);
    }

    // ---- Example C: rates = (1,2,3,4) — genuinely complex, tabulated ----------

    @Test
    void exampleC_equilibriumMatchesExactDerivation() {
        // 1/r = (1, 1/2, 1/3, 1/4), sum = 25/12.
        // pi_0 = 1/(25/12) = 12/25 = 0.48, pi_1 = 0.5/(25/12) = 6/25 = 0.24,
        // pi_2 = (1/3)/(25/12) = 4/25 = 0.16, pi_3 = 0.25/(25/12) = 3/25 = 0.12.
        CycleModel m = model(1, 2, 3, 4);
        double[] pi = m.getFrequencies();
        assertEquals(0.48, pi[0], 1e-12);
        assertEquals(0.24, pi[1], 1e-12);
        assertEquals(0.16, pi[2], 1e-12);
        assertEquals(0.12, pi[3], 1e-12);
    }

    @Test
    void exampleC_matchesIndependentlyVerifiedValuesAtT01() {
        CycleModel m = model(1, 2, 3, 4);
        double[] p = P(m, 0.1);
        double[] expected = {
                0.949256323353, 0.048174722166, 0.002444876447, 0.000124078034,
                0.000496312136, 0.901081601187, 0.091459691439, 0.006962395238,
                0.014172946544, 0.000248156068, 0.855351755467, 0.130227141921,
                0.183084820257, 0.004807034204, 0.000165437379, 0.811942708160
        };
        for (int k = 0; k < 16; k++) {
            assertEquals(expected[k], p[k], TOL, "index " + k);
        }
    }

    @Test
    void exampleC_matchesIndependentlyVerifiedValuesAtT1() {
        CycleModel m = model(1, 2, 3, 4);
        double[] p = P(m, 1.0);
        double[] expected = {
                0.618159702179, 0.243694568512, 0.098316623792, 0.039829105518,
                0.159316422071, 0.374465133667, 0.290755889439, 0.175462554823,
                0.430583320681, 0.079658211036, 0.229087188948, 0.260671279336,
                0.634617252902, 0.170080510572, 0.053105474024, 0.142196762502
        };
        for (int k = 0; k < 16; k++) {
            assertEquals(expected[k], p[k], TOL, "index " + k);
        }
    }

    // ---- Section 4 invariants --------------------------------------------------
    // Exercised over Examples A, B, C plus one further arbitrary positive rate vector.

    private static final double[][] RATE_SETS = {
            {1, 1, 1, 1},
            {1, 6, 1, 6},
            {1, 2, 3, 4},
            {2.3, 0.7, 5.1, 1.9}
    };

    @Test
    void modelMatchesIndependentPiAndQDerivation() {
        // Uses piOf/qnOf (independent re-derivations from section 1) directly against the
        // model's own getFrequencies()/getRateMatrix(), rather than only checking internal
        // consistency of the model's own numbers against each other.
        for (double[] rates : RATE_SETS) {
            CycleModel m = model(rates);
            double[] expectedPi = piOf(rates);
            double[] actualPi = m.getFrequencies();
            for (int i = 0; i < 4; i++) {
                assertEquals(expectedPi[i], actualPi[i], 1e-12, "pi[" + i + "]");
            }
            double[] expectedQ = qnOf(rates);
            double[] actualQ = m.getRateMatrix(null);
            for (int k = 0; k < 16; k++) {
                assertEquals(expectedQ[k], actualQ[k], 1e-12, "Q index " + k);
            }
        }
    }

    @Test
    void invariant1_qStructure() {
        for (double[] rates : RATE_SETS) {
            CycleModel m = model(rates);
            double[] q = m.getRateMatrix(null);
            for (int i = 0; i < 4; i++) {
                double rowSum = 0.0;
                for (int j = 0; j < 4; j++) {
                    double v = q[i * 4 + j];
                    rowSum += v;
                    if (j == (i + 1) % 4) {
                        assertTrue(v > 0, "Q[" + i + "][" + j + "] should be > 0");
                    } else if (j == i) {
                        assertTrue(v < 0, "Q[" + i + "][" + j + "] should be < 0");
                    } else {
                        assertEquals(0.0, v, 0.0, "Q[" + i + "][" + j + "] should be exactly 0");
                    }
                }
                assertEquals(0.0, rowSum, 1e-12, "row " + i + " should sum to 0");
            }
        }
    }

    @Test
    void invariant2_qIsNormalised() {
        for (double[] rates : RATE_SETS) {
            CycleModel m = model(rates);
            double[] q = m.getRateMatrix(null);
            double[] pi = m.getFrequencies();
            double subst = 0.0;
            for (int i = 0; i < 4; i++) {
                subst += pi[i] * (-q[i * 4 + i]);
            }
            assertEquals(1.0, subst, 1e-12);
        }
    }

    @Test
    void invariant3_fluxBalance() {
        // Derivation: pi_i * r_i = (1/r_i / S) * r_i = 1/S, and mu = 4/S, so
        // pi_i * (r_i/mu) = (1/S) / (4/S) = 1/4 for every i, independent of the rates.
        for (double[] rates : RATE_SETS) {
            CycleModel m = model(rates);
            double[] q = m.getRateMatrix(null);
            double[] pi = m.getFrequencies();
            for (int i = 0; i < 4; i++) {
                assertEquals(0.25, pi[i] * (-q[i * 4 + i]), 1e-12, "state " + i);
            }
        }
    }

    @Test
    void invariant4_stationarity() {
        for (double[] rates : RATE_SETS) {
            CycleModel m = model(rates);
            double[] pi = m.getFrequencies();
            for (double t : new double[]{0.01, 0.5, 5}) {
                double[] p = P(m, t);
                double[] piP = new double[4];
                for (int j = 0; j < 4; j++) {
                    double s = 0.0;
                    for (int i = 0; i < 4; i++) s += pi[i] * p[i * 4 + j];
                    piP[j] = s;
                }
                for (int j = 0; j < 4; j++) {
                    assertEquals(pi[j], piP[j], 1e-10, "t=" + t + " j=" + j);
                }
            }
        }
    }

    @Test
    void invariant5_rowsAreDistributions() {
        for (double[] rates : RATE_SETS) {
            CycleModel m = model(rates);
            for (double t : new double[]{0.01, 0.3, 2.0}) {
                double[] p = P(m, t);
                for (int i = 0; i < 4; i++) {
                    double rowSum = 0.0;
                    for (int j = 0; j < 4; j++) {
                        double v = p[i * 4 + j];
                        assertTrue(v > 0, "P(t)[" + i + "][" + j + "] should be > 0 for t>0");
                        rowSum += v;
                    }
                    assertEquals(1.0, rowSum, 1e-12, "row " + i + " at t=" + t);
                }
            }
        }
    }

    @Test
    void invariant6_pAtZeroIsIdentity() {
        for (double[] rates : RATE_SETS) {
            CycleModel m = model(rates);
            double[] p = P(m, 0.0);
            for (int i = 0; i < 4; i++) {
                for (int j = 0; j < 4; j++) {
                    assertEquals(i == j ? 1.0 : 0.0, p[i * 4 + j], 1e-12);
                }
            }
        }
    }

    @Test
    void invariant7_chapmanKolmogorov() {
        double s = 0.3, t = 0.7;
        for (double[] rates : RATE_SETS) {
            CycleModel m = model(rates);
            double[] ps = P(m, s);
            double[] pt = P(m, t);
            double[] pst = P(m, s + t);
            double[] product = matMul(ps, pt);
            for (int k = 0; k < 16; k++) {
                assertEquals(pst[k], product[k], 1e-10, "index " + k);
            }
        }
    }

    @Test
    void invariant8_convergesToStationary() {
        for (double[] rates : RATE_SETS) {
            CycleModel m = model(rates);
            double[] pi = m.getFrequencies();
            double[] p = P(m, 200.0);
            for (int i = 0; i < 4; i++) {
                for (int j = 0; j < 4; j++) {
                    assertEquals(pi[j], p[i * 4 + j], 1e-8, "i=" + i + " j=" + j);
                }
            }
        }
    }

    @Test
    void invariant9_shortTimeLimitMatchesQ() {
        double t = 1e-6;
        for (double[] rates : RATE_SETS) {
            CycleModel m = model(rates);
            double[] q = m.getRateMatrix(null);
            double[] p = P(m, t);
            for (int i = 0; i < 4; i++) {
                for (int j = 0; j < 4; j++) {
                    double delta = (i == j) ? 1.0 : 0.0;
                    double rate = (p[i * 4 + j] - delta) / t;
                    assertEquals(q[i * 4 + j], rate, 1e-4, "i=" + i + " j=" + j);
                }
            }
        }
    }

    @Test
    void invariant10_scaleInvariance() {
        CycleModel m1 = model(1, 2, 3, 4);
        CycleModel m2 = model(10, 20, 30, 40);
        double[] pi1 = m1.getFrequencies();
        double[] pi2 = m2.getFrequencies();
        for (int i = 0; i < 4; i++) {
            assertEquals(pi1[i], pi2[i], 1e-12);
        }
        double[] q1 = m1.getRateMatrix(null);
        double[] q2 = m2.getRateMatrix(null);
        for (int k = 0; k < 16; k++) {
            assertEquals(q1[k], q2[k], 1e-12);
        }
        for (double t : new double[]{0.1, 1.0, 5.0}) {
            double[] p1 = P(m1, t);
            double[] p2 = P(m2, t);
            for (int k = 0; k < 16; k++) {
                assertEquals(p1[k], p2[k], 1e-12, "t=" + t + " index " + k);
            }
        }
    }

    @Test
    void invariant11_nonReversibility() {
        for (double[] rates : RATE_SETS) {
            CycleModel m = model(rates);
            double[] q = m.getRateMatrix(null);
            double[] pi = m.getFrequencies();
            // pi_0 * Q[0][1] > 0 (A->C flux is real) while pi_1 * Q[1][0] == 0
            // (C->A is not a permitted edge, Q[1][0] is exactly zero).
            assertTrue(pi[0] * q[0 * 4 + 1] > 0);
            assertEquals(0.0, pi[1] * q[1 * 4 + 0], 0.0);
        }
    }

    @Test
    void invariant12_complexDiagonalizationAndDataType() {
        CycleModel m = model(1, 2, 3, 4);
        assertTrue(m.canReturnComplexDiagonalization());
        assertTrue(m.canHandleDataType(new Nucleotide()));
        assertFalse(m.canHandleDataType(new Aminoacid()));
    }

    // ---- Section 4 error cases --------------------------------------------------

    /**
     * BEASTInterface.initByName(...) catches every Exception thrown by initAndValidate()
     * and rethrows a bare RuntimeException ("initAndValidate() failed! " + e.getMessage()),
     * without chaining the cause (BEASTInterface.java:108-113, per SPEC.md section 4 "How
     * these surface"). So an IllegalArgumentException can only be observed by calling
     * initAndValidate() directly (bypassing initByName's wrapping via setInputValue); the
     * RuntimeException is what a caller going through initByName actually sees, with the
     * original message text still present as a substring of the wrapper's message.
     */
    private static CycleModel modelWithoutInit(double... rates) {
        CycleModel m = new CycleModel();
        m.setInputValue("rates", new RealVectorParam<>(rates, PositiveReal.INSTANCE));
        return m;
    }

    @Test
    void invariant13_wrongLengthRatesRejected_specdContract() {
        // Direct call to initAndValidate(), bypassing initByName's wrapping.
        IllegalArgumentException ex3 = assertThrows(IllegalArgumentException.class,
                () -> modelWithoutInit(1, 2, 3).initAndValidate());
        assertTrue(ex3.getMessage().contains("4"), "message should state required length 4: " + ex3.getMessage());
        assertTrue(ex3.getMessage().contains("3"), "message should state length seen (3): " + ex3.getMessage());

        IllegalArgumentException ex5 = assertThrows(IllegalArgumentException.class,
                () -> modelWithoutInit(1, 2, 3, 4, 5).initAndValidate());
        assertTrue(ex5.getMessage().contains("4"), "message should state required length 4: " + ex5.getMessage());
        assertTrue(ex5.getMessage().contains("5"), "message should state length seen (5): " + ex5.getMessage());
    }

    @Test
    void invariant13_wrongLengthRatesRejected_userVisibleBehaviour() {
        // Through initByName, the same failure surfaces as a bare RuntimeException.
        RuntimeException ex3 = assertThrows(RuntimeException.class,
                () -> model(1, 2, 3));
        assertTrue(ex3.getMessage().contains("4"), "message should state required length 4: " + ex3.getMessage());
        assertTrue(ex3.getMessage().contains("3"), "message should state length seen (3): " + ex3.getMessage());

        RuntimeException ex5 = assertThrows(RuntimeException.class,
                () -> model(1, 2, 3, 4, 5));
        assertTrue(ex5.getMessage().contains("4"), "message should state required length 4: " + ex5.getMessage());
        assertTrue(ex5.getMessage().contains("5"), "message should state length seen (5): " + ex5.getMessage());
    }

    @Test
    void invariant14_suppliedFrequenciesRejected_specdContract() {
        Simplex f = new SimplexParam(new double[]{0.25, 0.25, 0.25, 0.25});
        Frequencies freqs = new Frequencies();
        freqs.initByName("frequencies", f, "estimate", false);

        CycleModel m = modelWithoutInit(1, 2, 3, 4);
        m.setInputValue("frequencies", freqs);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, m::initAndValidate);
        assertTrue(ex.getMessage().contains("CycleModel"), "message should name CycleModel: " + ex.getMessage());
    }

    @Test
    void invariant14_suppliedFrequenciesRejected_userVisibleBehaviour() {
        Simplex f = new SimplexParam(new double[]{0.25, 0.25, 0.25, 0.25});
        Frequencies freqs = new Frequencies();
        freqs.initByName("frequencies", f, "estimate", false);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> {
                    CycleModel m = new CycleModel();
                    m.initByName("rates", new RealVectorParam<>(new double[]{1, 2, 3, 4}, PositiveReal.INSTANCE),
                            "frequencies", freqs);
                });
        assertTrue(ex.getMessage().contains("CycleModel"), "message should name CycleModel: " + ex.getMessage());
    }
}
