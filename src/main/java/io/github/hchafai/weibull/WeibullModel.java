package io.github.hchafai.weibull;

import java.io.Serial;
import java.io.Serializable;
import java.util.Locale;

/** Immutable result of a two-parameter Weibull maximum-likelihood fit. */
public final class WeibullModel implements Serializable {
    @Serial private static final long serialVersionUID = 1L;

    private final double shape;
    private final double scale;
    private final double shapeStandardError;
    private final double scaleStandardError;
    private final double logLikelihood;
    private final int observations;
    private final double eventWeight;
    private final double censoredWeight;
    private final int iterations;
    private final boolean converged;

    WeibullModel(double shape, double scale, double shapeStandardError,
                 double scaleStandardError, double logLikelihood, int observations,
                 double eventWeight, double censoredWeight, int iterations,
                 boolean converged) {
        this.shape = shape;
        this.scale = scale;
        this.shapeStandardError = shapeStandardError;
        this.scaleStandardError = scaleStandardError;
        this.logLikelihood = logLikelihood;
        this.observations = observations;
        this.eventWeight = eventWeight;
        this.censoredWeight = censoredWeight;
        this.iterations = iterations;
        this.converged = converged;
    }

    /** Weibull shape parameter, also denoted beta or k. */
    public double shape() { return shape; }
    /** Weibull scale parameter, also denoted eta or lambda. */
    public double scale() { return scale; }
    /** Delta-method standard error of the shape estimate. */
    public double shapeStandardError() { return shapeStandardError; }
    /** Delta-method standard error of the scale estimate. */
    public double scaleStandardError() { return scaleStandardError; }
    /** Maximized right-censored log-likelihood. */
    public double logLikelihood() { return logLikelihood; }
    /** Number of complete duration/event rows used. */
    public int observations() { return observations; }
    /** Sum of Weka instance weights for observed events. */
    public double eventWeight() { return eventWeight; }
    /** Sum of Weka instance weights for right-censored observations. */
    public double censoredWeight() { return censoredWeight; }
    /** Number of root-finding iterations. */
    public int iterations() { return iterations; }
    /** Whether the numerical stopping rule was reached. */
    public boolean converged() { return converged; }

    /** Survival probability S(t). */
    public double survival(double time) {
        requireNonNegativeTime(time);
        return Math.exp(-cumulativeHazard(time));
    }

    /** Cumulative distribution F(t). */
    public double cumulativeProbability(double time) {
        return -Math.expm1(-cumulativeHazardChecked(time));
    }

    /** Cumulative hazard H(t). */
    public double cumulativeHazard(double time) {
        requireNonNegativeTime(time);
        if (time == 0.0) return 0.0;
        return Math.exp(shape * (Math.log(time) - Math.log(scale)));
    }

    /** Instantaneous hazard h(t), defined for t greater than zero. */
    public double hazard(double time) {
        if (!Double.isFinite(time) || time <= 0.0) {
            throw new IllegalArgumentException("Time must be finite and strictly positive");
        }
        return (shape / scale) * Math.exp((shape - 1.0) * (Math.log(time) - Math.log(scale)));
    }

    /** Quantile t_p such that P(T less than or equal to t_p) = p. */
    public double quantile(double probability) {
        if (!Double.isFinite(probability) || probability < 0.0 || probability >= 1.0) {
            throw new IllegalArgumentException("Probability must be in [0, 1)");
        }
        if (probability == 0.0) return 0.0;
        return scale * Math.pow(-Math.log1p(-probability), 1.0 / shape);
    }

    /** Median survival time. */
    public double median() { return quantile(0.5); }

    /** Mean survival time. */
    public double mean() { return scale * Math.exp(logGamma(1.0 + 1.0 / shape)); }

    /** Akaike information criterion for two estimated parameters. */
    public double aic() { return 4.0 - 2.0 * logLikelihood; }

    /** Bayesian information criterion for two estimated parameters. */
    public double bic() { return 2.0 * Math.log(observations) - 2.0 * logLikelihood; }

    /** Lower endpoint of the 95 percent log-scale confidence interval for beta. */
    public double shapeCiLower() { return logScaleCi(shape, shapeStandardError, -1.959963984540054); }
    /** Upper endpoint of the 95 percent log-scale confidence interval for beta. */
    public double shapeCiUpper() { return logScaleCi(shape, shapeStandardError, 1.959963984540054); }
    /** Lower endpoint of the 95 percent log-scale confidence interval for eta. */
    public double scaleCiLower() { return logScaleCi(scale, scaleStandardError, -1.959963984540054); }
    /** Upper endpoint of the 95 percent log-scale confidence interval for eta. */
    public double scaleCiUpper() { return logScaleCi(scale, scaleStandardError, 1.959963984540054); }

    private double cumulativeHazardChecked(double time) {
        return cumulativeHazard(time);
    }

    private static void requireNonNegativeTime(double time) {
        if (!Double.isFinite(time) || time < 0.0) {
            throw new IllegalArgumentException("Time must be finite and non-negative");
        }
    }

    private static double logScaleCi(double estimate, double standardError, double z) {
        if (!Double.isFinite(standardError) || standardError <= 0.0) return Double.NaN;
        return Math.exp(Math.log(estimate) + z * standardError / estimate);
    }

    // Lanczos approximation, sufficient for reporting the Weibull mean.
    private static double logGamma(double value) {
        double[] coefficients = {
                676.5203681218851, -1259.1392167224028, 771.32342877765313,
                -176.61502916214059, 12.507343278686905, -0.13857109526572012,
                9.9843695780195716e-6, 1.5056327351493116e-7
        };
        if (value < 0.5) {
            return Math.log(Math.PI) - Math.log(Math.sin(Math.PI * value)) - logGamma(1.0 - value);
        }
        double shifted = value - 1.0;
        double sum = 0.99999999999980993;
        for (int index = 0; index < coefficients.length; index++) {
            sum += coefficients[index] / (shifted + index + 1.0);
        }
        double t = shifted + coefficients.length - 0.5;
        return 0.5 * Math.log(2.0 * Math.PI) + (shifted + 0.5) * Math.log(t) - t + Math.log(sum);
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT,
                "Weibull survival model (right-censored maximum likelihood)%n"
                        + "Observations: %d%nObserved event weight: %.6f%nRight-censored weight: %.6f%n"
                        + "Shape beta: %.10f (SE %.10f; 95%% CI %.10f to %.10f)%n"
                        + "Scale eta: %.10f (SE %.10f; 95%% CI %.10f to %.10f)%n"
                        + "Median: %.10f%nMean: %.10f%nLog-likelihood: %.10f%n"
                        + "AIC: %.10f%nBIC: %.10f%nIterations: %d%nConverged: %s",
                observations, eventWeight, censoredWeight,
                shape, shapeStandardError, shapeCiLower(), shapeCiUpper(),
                scale, scaleStandardError, scaleCiLower(), scaleCiUpper(),
                median(), mean(), logLikelihood, aic(), bic(), iterations, converged);
    }
}
