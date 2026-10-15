package io.github.hchafai.weibull;

import weka.classifiers.functions.WeibullSurvival;
import weka.core.Instances;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Command-line helper that regenerates the full-dataset validation table. */
public final class ValidationRunner {
    private ValidationRunner() { }

    /** Arguments: output CSV followed by one or more ARFF files. */
    public static void main(String[] arguments) throws Exception {
        if (arguments.length < 2) throw new IllegalArgumentException("Expected output CSV and ARFF inputs");
        try (PrintWriter output = new PrintWriter(arguments[0], StandardCharsets.UTF_8)) {
            output.println("dataset,observations,events,censored,beta,eta,log_likelihood,aic,bic,median");
            for (int index = 1; index < arguments.length; index++) {
                Instances data;
                try (BufferedReader reader = new BufferedReader(new FileReader(arguments[index], StandardCharsets.UTF_8))) {
                    data = new Instances(reader);
                }
                data.setClassIndex(data.numAttributes() - 1);
                WeibullSurvival estimator = new WeibullSurvival();
                estimator.setEventAttribute("event");
                estimator.buildClassifier(data);
                WeibullModel model = estimator.getModel();
                String filename = new java.io.File(arguments[index]).getName().replaceFirst("\\.arff$", "");
                output.printf(Locale.ROOT, "%s,%d,%.0f,%.0f,%.15g,%.15g,%.15g,%.15g,%.15g,%.15g%n",
                        filename, model.observations(), model.eventWeight(), model.censoredWeight(),
                        model.shape(), model.scale(), model.logLikelihood(), model.aic(), model.bic(), model.median());
            }
        }
    }
}
