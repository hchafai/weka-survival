package io.github.hchafai.weibull;

import java.util.Arrays;

/** Maximum-likelihood estimator for a two-parameter Weibull model with right censoring. */
public final class WeibullFitter {
    private WeibullFitter() { }

    /** Fits unweighted observations with a default numerical configuration. */
    public static WeibullModel fit(double[] durations, boolean[] events) {
        double[] weights = new double[durations.length];
        Arrays.fill(weights, 1.0);
        return fit(durations, events, weights, 1.0e-10, 300);
    }

    /**
     * Fits a Weibull model by profiling eta and solving the strictly decreasing
     * score equation for beta.
     */
    public static WeibullModel fit(double[] durations, boolean[] events, double[] weights,
                                   double tolerance, int maximumIterations) {
        validateInputs(durations, events, weights, tolerance, maximumIterations);
        double[] logs = new double[durations.length];
        double eventWeight = 0.0;
        double totalWeight = 0.0;
        double eventLogSum = 0.0;
        for (int index = 0; index < durations.length; index++) {
            logs[index] = Math.log(durations[index]);
            totalWeight += weights[index];
            if (events[index]) {
                eventWeight += weights[index];
                eventLogSum += weights[index] * logs[index];
            }
        }
        if (eventWeight <= 0.0) {
            throw new IllegalArgumentException("At least one observed event with positive weight is required");
        }

        double low = 1.0e-6;
        double high = 1.0;
        double scoreLow = score(low, logs, weights, eventWeight, eventLogSum);
        while (scoreLow <= 0.0 && low > 1.0e-14) {
            low *= 0.1;
            scoreLow = score(low, logs, weights, eventWeight, eventLogSum);
        }
        double scoreHigh = score(high, logs, weights, eventWeight, eventLogSum);
        while (scoreHigh > 0.0 && high < 1.0e6) {
            high *= 2.0;
            scoreHigh = score(high, logs, weights, eventWeight, eventLogSum);
        }
        if (!(scoreLow > 0.0 && scoreHigh < 0.0)) {
            throw new IllegalStateException("Could not bracket the Weibull shape score root");
        }

        double beta = Double.NaN;
        boolean converged = false;
        int iteration = 0;
        for (; iteration < maximumIterations; iteration++) {
            beta = 0.5 * (low + high);
            double current = score(beta, logs, weights, eventWeight, eventLogSum);
            if (Math.abs(current) <= tolerance
                    || (high - low) <= tolerance * Math.max(1.0, beta)) {
                converged = true;
                iteration++;
                break;
            }
            if (current > 0.0) low = beta;
            else high = beta;
        }
        if (!Double.isFinite(beta)) beta = 0.5 * (low + high);

        Moments moments = moments(beta, logs, weights);
        double logEta = (moments.logPowerSum - Math.log(eventWeight)) / beta;
        double eta = Math.exp(logEta);
        if (!Double.isFinite(eta) || eta <= 0.0) {
            throw new IllegalStateException("Estimated Weibull scale is not finite and positive");
        }

        double logLikelihood = eventWeight * Math.log(beta)
                - eventWeight * beta * logEta
                + (beta - 1.0) * eventLogSum;
        double sumQ = 0.0;
        double sumQX = 0.0;
        double sumQX2 = 0.0;
        double eventX = 0.0;
        for (int index = 0; index < logs.length; index++) {
            double x = logs[index] - logEta;
            double q = weights[index] * Math.exp(beta * x);
            sumQ += q;
            sumQX += q * x;
            sumQX2 += q * x * x;
            if (events[index]) eventX += weights[index] * x;
        }
        logLikelihood -= sumQ;

        double informationAA = -beta * eventX + beta * sumQX + beta * beta * sumQX2;
        double informationAB = beta * eventWeight - beta * beta * sumQX - beta * sumQ;
        double informationBB = beta * beta * sumQ;
        double determinant = informationAA * informationBB - informationAB * informationAB;
        double shapeSe = Double.NaN;
        double scaleSe = Double.NaN;
        if (determinant > 0.0 && informationAA > 0.0 && informationBB > 0.0) {
            double varianceLogBeta = informationBB / determinant;
            double varianceLogEta = informationAA / determinant;
            shapeSe = beta * Math.sqrt(varianceLogBeta);
            scaleSe = eta * Math.sqrt(varianceLogEta);
        }

        return new WeibullModel(beta, eta, shapeSe, scaleSe, logLikelihood,
                durations.length, eventWeight, totalWeight - eventWeight, iteration, converged);
    }

    private static double score(double beta, double[] logs, double[] weights,
                                double eventWeight, double eventLogSum) {
        Moments moments = moments(beta, logs, weights);
        return 1.0 / beta + eventLogSum / eventWeight - moments.weightedLogMean;
    }

    private static Moments moments(double beta, double[] logs, double[] weights) {
        double maximum = Double.NEGATIVE_INFINITY;
        for (double log : logs) maximum = Math.max(maximum, beta * log);
        double sum = 0.0;
        double sumLog = 0.0;
        for (int index = 0; index < logs.length; index++) {
            double term = weights[index] * Math.exp(beta * logs[index] - maximum);
            sum += term;
            sumLog += term * logs[index];
        }
        return new Moments(maximum + Math.log(sum), sumLog / sum);
    }

    private static void validateInputs(double[] durations, boolean[] events, double[] weights,
                                       double tolerance, int maximumIterations) {
        if (durations == null || events == null || weights == null
                || durations.length != events.length || durations.length != weights.length) {
            throw new IllegalArgumentException("Duration, event and weight arrays must be non-null and equally sized");
        }
        if (durations.length < 2) throw new IllegalArgumentException("At least two observations are required");
        if (!Double.isFinite(tolerance) || tolerance <= 0.0) {
            throw new IllegalArgumentException("Tolerance must be finite and strictly positive");
        }
        if (maximumIterations < 1) throw new IllegalArgumentException("Maximum iterations must be positive");
        for (int index = 0; index < durations.length; index++) {
            if (!Double.isFinite(durations[index]) || durations[index] <= 0.0) {
                throw new IllegalArgumentException("Every duration must be finite and strictly positive; invalid row " + index);
            }
            if (!Double.isFinite(weights[index]) || weights[index] <= 0.0) {
                throw new IllegalArgumentException("Every weight must be finite and strictly positive; invalid row " + index);
            }
        }
    }

    private record Moments(double logPowerSum, double weightedLogMean) { }
}
