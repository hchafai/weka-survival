package io.github.hchafai.weibull;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WeibullFitterTest {
    @Test
    void reproducesIndependentCensoredReference() {
        double[] times = {1, 2, 3, 4, 5, 7, 9, 12};
        boolean[] events = {true, true, false, true, false, true, false, true};
        WeibullModel model = WeibullFitter.fit(times, events);

        assertThat(model.shape()).isCloseTo(1.4000316639, within(1.0e-8));
        assertThat(model.scale()).isCloseTo(8.1377907539, within(1.0e-8));
        assertThat(model.logLikelihood()).isCloseTo(-15.3891793354, within(1.0e-8));
        assertThat(model.converged()).isTrue();
        assertThat(model.shapeStandardError()).isPositive();
        assertThat(model.scaleStandardError()).isPositive();
    }

    @Test
    void distributionFunctionsAreCoherent() {
        WeibullModel model = WeibullFitter.fit(
                new double[]{2, 3, 5, 8, 13, 21},
                new boolean[]{true, false, true, true, false, true});
        double t = model.median();
        assertThat(model.survival(t)).isCloseTo(0.5, within(1.0e-12));
        assertThat(model.cumulativeProbability(t)).isCloseTo(0.5, within(1.0e-12));
        assertThat(model.cumulativeHazard(t)).isCloseTo(Math.log(2.0), within(1.0e-12));
        assertThat(model.hazard(t)).isPositive();
        assertThat(model.mean()).isPositive();
    }

    @Test
    void rejectsInvalidSurvivalRows() {
        assertThatThrownBy(() -> WeibullFitter.fit(
                new double[]{0, 2}, new boolean[]{true, false}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("strictly positive");
        assertThatThrownBy(() -> WeibullFitter.fit(
                new double[]{1, 2}, new boolean[]{false, false}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("observed event");
    }

    private static org.assertj.core.data.Offset<Double> within(double tolerance) {
        return org.assertj.core.data.Offset.offset(tolerance);
    }
}
