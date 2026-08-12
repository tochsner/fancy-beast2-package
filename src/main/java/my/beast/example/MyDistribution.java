package my.beast.example;

import beast.base.core.Description;
import beast.base.core.Input;
import beast.base.spec.domain.Real;
import beast.base.spec.inference.distribution.ScalarDistribution;
import beast.base.spec.type.RealScalar;
import org.apache.commons.statistics.distribution.ContinuousDistribution;
import org.apache.commons.statistics.distribution.NormalDistribution;

import java.util.List;

/**
 * Example scalar distribution for a BEAST 3 package.
 * Wraps a Normal(mean, 1) distribution where the mean is an estimated parameter.
 *
 * <p>This class demonstrates the strongly-typed {@link ScalarDistribution} pattern.
 * The distribution can be used directly as a prior on a {@link RealScalar} parameter
 * (no separate Prior wrapper needed).
 */
@Description("Normal distribution with estimated mean and fixed sigma=1. " +
        "Useful as a prior on a real-valued parameter.")
public class MyDistribution extends ScalarDistribution<RealScalar<Real>, Double> {

    final public Input<RealScalar<Real>> meanInput = new Input<>(
            "mean",
            "mean of the normal distribution (default 0.0)");

    private NormalDistribution dist = NormalDistribution.of(0, 1);
    private ContinuousDistribution.Sampler sampler;

    public MyDistribution() {
    }

    @Override
    public void initAndValidate() {
        refresh();
        super.initAndValidate();
    }

    @Override
    public void refresh() {
        double mean = (meanInput.get() != null) ? meanInput.get().get() : 0.0;
        if (isNotEqual(dist.getMean(), mean)) {
            dist = NormalDistribution.of(mean, 1.0);
            sampler = null;
        }
    }

    @Override
    public double calculateLogP() {
        logP = getApacheDistribution().logDensity(param.get());
        return logP;
    }

    @Override
    public List<Double> sample() {
        if (sampler == null) {
            sampler = dist.createSampler(rng);
        }
        return List.of(sampler.sample());
    }

    @Override
    protected NormalDistribution getApacheDistribution() {
        refresh();
        return dist;
    }
}
