package my.beast.example;

import beast.base.core.Description;
import beast.base.core.Input;
import beast.base.inference.Operator;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.util.Randomizer;

/**
 * Example MCMC operator for a BEAST 3 package.
 * Proposes a new value by multiplying the current value by a random scale factor.
 *
 * <p>This class demonstrates how to write an operator that works with
 * strongly-typed {@link RealScalarParam} parameters.
 */
@Description("Scales a single real scalar parameter by a random factor. " +
        "The scale factor is drawn uniformly from [1/s, s] where s is the scaleFactor input.")
public class MyScaleOperator extends Operator {

    final public Input<RealScalarParam<?>> parameterInput = new Input<>(
            "parameter",
            "the real scalar parameter to operate on",
            Input.Validate.REQUIRED);

    final public Input<Double> scaleFactorInput = new Input<>(
            "scaleFactor",
            "magnitude of the scaling proposal (between 0 and 1, default 0.5)",
            0.5);

    private double scaleFactor;

    @Override
    public void initAndValidate() {
        scaleFactor = scaleFactorInput.get();
    }

    @Override
    public double proposal() {
        final RealScalarParam<?> param = parameterInput.get();

        // Draw scale uniformly from [scaleFactor, 1/scaleFactor]
        final double scale = scaleFactor + (Randomizer.nextDouble() * ((1.0 / scaleFactor) - scaleFactor));

        final double oldValue = param.get();
        if (oldValue == 0) {
            return Double.NEGATIVE_INFINITY;
        }

        final double newValue = oldValue * scale;

        // Reject if outside domain bounds
        if (!param.isValid(newValue)) {
            return Double.NEGATIVE_INFINITY;
        }

        param.set(newValue);

        // Log of the Hastings ratio for a scale proposal
        return -Math.log(scale);
    }
}
