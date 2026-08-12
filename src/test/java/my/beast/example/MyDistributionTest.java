package my.beast.example;

import beast.base.spec.domain.PositiveReal;
import beast.base.spec.domain.Real;
import beast.base.spec.inference.parameter.RealScalarParam;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class MyDistributionTest {

    @Test
    void logDensityAtMean() {
        // Normal(0, 1) log-density at x=0 is -0.5 * ln(2*pi)
        RealScalarParam<Real> x = new RealScalarParam<>(0.0, Real.INSTANCE);
        x.setID("x");

        MyDistribution dist = new MyDistribution();
        dist.initByName("param", x);

        double expected = -0.5 * Math.log(2 * Math.PI);
        assertThat(dist.calculateLogP()).isCloseTo(expected, within(1e-10));
    }

    @Test
    void logDensityWithMean() {
        // Normal(2, 1) log-density at x=2 should equal Normal(0,1) at x=0
        RealScalarParam<Real> x = new RealScalarParam<>(2.0, Real.INSTANCE);
        x.setID("x");

        RealScalarParam<Real> mean = new RealScalarParam<>(2.0, Real.INSTANCE);
        mean.setID("mean");

        MyDistribution dist = new MyDistribution();
        dist.initByName("param", x, "mean", mean);

        double expected = -0.5 * Math.log(2 * Math.PI);
        assertThat(dist.calculateLogP()).isCloseTo(expected, within(1e-10));
    }

    @Test
    void logDensityAwayFromMean() {
        // Normal(0, 1) log-density at x=1 is -0.5 * ln(2*pi) - 0.5
        RealScalarParam<Real> x = new RealScalarParam<>(1.0, Real.INSTANCE);
        x.setID("x");

        MyDistribution dist = new MyDistribution();
        dist.initByName("param", x);

        double expected = -0.5 * Math.log(2 * Math.PI) - 0.5;
        assertThat(dist.calculateLogP()).isCloseTo(expected, within(1e-10));
    }

    @Test
    void densityMethodWorks() {
        // Verify the density() convenience method inherited from ScalarDistribution
        MyDistribution dist = new MyDistribution();
        dist.initAndValidate();

        double expected = 1.0 / Math.sqrt(2 * Math.PI);
        assertThat(dist.density(0.0)).isCloseTo(expected, within(1e-10));
    }
}
