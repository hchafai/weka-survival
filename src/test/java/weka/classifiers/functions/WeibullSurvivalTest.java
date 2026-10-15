package weka.classifiers.functions;

import org.junit.jupiter.api.Test;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instances;
import weka.core.SerializationHelper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class WeibullSurvivalTest {
    @Test
    void worksAsAConfigurableSerializableWekaModel() throws Exception {
        Instances data = data();
        WeibullSurvival estimator = new WeibullSurvival();
        estimator.setEventAttribute("event");
        estimator.setEventValue("yes");
        estimator.buildClassifier(data);

        assertThat(estimator.getModel().observations()).isEqualTo(8);
        assertThat(estimator.getModel().eventWeight()).isEqualTo(5.0);
        assertThat(estimator.classifyInstance(data.instance(0)))
                .isEqualTo(estimator.getModel().median());
        assertThat(estimator.survivalProbability(estimator.quantile(0.75)))
                .isCloseTo(0.25, org.assertj.core.data.Offset.offset(1.0e-12));

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        SerializationHelper.write(output, estimator);
        WeibullSurvival restored = (WeibullSurvival) SerializationHelper.read(
                new ByteArrayInputStream(output.toByteArray()));
        assertThat(restored.getModel().shape()).isEqualTo(estimator.getModel().shape());
    }

    @Test
    void optionsRoundTrip() throws Exception {
        WeibullSurvival estimator = new WeibullSurvival();
        estimator.setOptions(new String[]{"-event", "1", "-event-value", "yes",
                "-tolerance", "1e-8", "-max-iterations", "80"});
        assertThat(estimator.getEventAttribute()).isEqualTo("1");
        assertThat(estimator.getEventValue()).isEqualTo("yes");
        assertThat(estimator.getTolerance()).isEqualTo(1.0e-8);
        assertThat(estimator.getMaximumIterations()).isEqualTo(80);
    }

    private Instances data() {
        ArrayList<String> states = new ArrayList<>();
        states.add("no");
        states.add("yes");
        ArrayList<Attribute> attributes = new ArrayList<>();
        attributes.add(new Attribute("event", states));
        attributes.add(new Attribute("duration"));
        Instances data = new Instances("censored", attributes, 8);
        data.setClassIndex(1);
        double[] times = {1, 2, 3, 4, 5, 7, 9, 12};
        boolean[] events = {true, true, false, true, false, true, false, true};
        for (int index = 0; index < times.length; index++) {
            data.add(new DenseInstance(1.0, new double[]{events[index] ? 1 : 0, times[index]}));
        }
        return data;
    }
}
