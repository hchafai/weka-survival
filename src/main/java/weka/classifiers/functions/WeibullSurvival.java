package weka.classifiers.functions;

import io.github.hchafai.weibull.WeibullFitter;
import io.github.hchafai.weibull.WeibullModel;
import weka.classifiers.AbstractClassifier;
import weka.core.Attribute;
import weka.core.Capabilities;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.Option;
import weka.core.TechnicalInformation;
import weka.core.TechnicalInformationHandler;
import weka.core.Utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Weka Explorer and command-line entry point for a baseline two-parameter
 * Weibull survival model with right censoring.
 *
 * <p>The numeric class is the observed duration. A separate binary attribute,
 * selected with {@code -event}, indicates whether the event was observed.
 * Other attributes are accepted but intentionally ignored by this baseline
 * distribution model.</p>
 */
public class WeibullSurvival extends AbstractClassifier implements TechnicalInformationHandler {
    private static final long serialVersionUID = 1L;

    private String eventAttribute = "event";
    private String eventValue = "1";
    private double tolerance = 1.0e-10;
    private int maximumIterations = 300;
    private WeibullModel model;
    private int skippedMissingRows;

    /** Human-readable description displayed by Weka Explorer. */
    public String globalInfo() {
        return "Two-parameter Weibull baseline survival model fitted by maximum likelihood with "
                + "right-censored observations. The numeric class is duration; -event selects "
                + "the binary event-status attribute. Predictions are the fitted median because "
                + "this baseline model has no covariates.\n\n" + getTechnicalInformation();
    }

    @Override
    public void buildClassifier(Instances source) throws Exception {
        getCapabilities().testWithFail(source);
        if (source.classIndex() < 0) throw new IllegalArgumentException("Set the numeric duration class first");
        int eventIndex = resolveEventIndex(source);
        if (eventIndex == source.classIndex()) {
            throw new IllegalArgumentException("Event attribute must differ from the duration class");
        }
        Attribute event = source.attribute(eventIndex);
        if (!(event.isNumeric() || event.isNominal())) {
            throw new IllegalArgumentException("Event attribute must be numeric or nominal");
        }

        List<Double> durationList = new ArrayList<>();
        List<Boolean> eventList = new ArrayList<>();
        List<Double> weightList = new ArrayList<>();
        Set<String> observedStates = new LinkedHashSet<>();
        skippedMissingRows = 0;
        for (Instance instance : source) {
            if (instance.classIsMissing() || instance.isMissing(eventIndex)) {
                skippedMissingRows++;
                continue;
            }
            double duration = instance.classValue();
            if (!Double.isFinite(duration) || duration <= 0.0) {
                throw new IllegalArgumentException("Duration must be finite and strictly positive at row "
                        + (durationList.size() + skippedMissingRows));
            }
            boolean observed = isEvent(instance, eventIndex, event);
            observedStates.add(event.isNominal() ? instance.stringValue(eventIndex)
                    : Double.toString(instance.value(eventIndex)));
            if (observedStates.size() > 2) {
                throw new IllegalArgumentException("Event attribute must contain no more than two observed states");
            }
            durationList.add(duration);
            eventList.add(observed);
            weightList.add(instance.weight());
        }
        if (durationList.size() < 2) throw new IllegalArgumentException("At least two complete rows are required");

        double[] durations = new double[durationList.size()];
        boolean[] events = new boolean[eventList.size()];
        double[] weights = new double[weightList.size()];
        for (int index = 0; index < durations.length; index++) {
            durations[index] = durationList.get(index);
            events[index] = eventList.get(index);
            weights[index] = weightList.get(index);
        }
        model = WeibullFitter.fit(durations, events, weights, tolerance, maximumIterations);
    }

    @Override
    public double classifyInstance(Instance instance) {
        ensureBuilt();
        return model.median();
    }

    /** Returns S(t) from the fitted baseline distribution. */
    public double survivalProbability(double time) { ensureBuilt(); return model.survival(time); }
    /** Returns h(t) from the fitted baseline distribution. */
    public double hazard(double time) { ensureBuilt(); return model.hazard(time); }
    /** Returns a requested fitted lifetime quantile. */
    public double quantile(double probability) { ensureBuilt(); return model.quantile(probability); }
    /** Returns the immutable fitted result. */
    public WeibullModel getModel() { return model; }
    /** Number of rows skipped because duration or event status was missing. */
    public int getSkippedMissingRows() { return skippedMissingRows; }

    @Override
    public Capabilities getCapabilities() {
        Capabilities capabilities = super.getCapabilities();
        capabilities.disableAll();
        capabilities.enable(Capabilities.Capability.NUMERIC_ATTRIBUTES);
        capabilities.enable(Capabilities.Capability.NOMINAL_ATTRIBUTES);
        capabilities.enable(Capabilities.Capability.DATE_ATTRIBUTES);
        capabilities.enable(Capabilities.Capability.MISSING_VALUES);
        capabilities.enable(Capabilities.Capability.NUMERIC_CLASS);
        capabilities.enable(Capabilities.Capability.MISSING_CLASS_VALUES);
        capabilities.setMinimumNumberInstances(2);
        return capabilities;
    }

    @Override
    public Enumeration<Option> listOptions() {
        List<Option> options = new ArrayList<>();
        options.add(new Option("Binary event attribute name or one-based index (default: event).",
                "event", 1, "-event <name|index>"));
        options.add(new Option("Value denoting an observed event (default: 1).",
                "event-value", 1, "-event-value <value>"));
        options.add(new Option("Root-finding tolerance (default: 1.0E-10).",
                "tolerance", 1, "-tolerance <double>"));
        options.add(new Option("Maximum root-finding iterations (default: 300).",
                "max-iterations", 1, "-max-iterations <integer>"));
        return Collections.enumeration(options);
    }

    @Override
    public void setOptions(String[] options) throws Exception {
        String value = Utils.getOption("event", options);
        if (!value.isBlank()) setEventAttribute(value);
        value = Utils.getOption("event-value", options);
        if (!value.isBlank()) setEventValue(value);
        value = Utils.getOption("tolerance", options);
        if (!value.isBlank()) setTolerance(Double.parseDouble(value));
        value = Utils.getOption("max-iterations", options);
        if (!value.isBlank()) setMaximumIterations(Integer.parseInt(value));
        Utils.checkForRemainingOptions(options);
    }

    @Override
    public String[] getOptions() {
        return new String[]{"-event", eventAttribute, "-event-value", eventValue,
                "-tolerance", Double.toString(tolerance),
                "-max-iterations", Integer.toString(maximumIterations)};
    }

    /** Event attribute shown in the Explorer property editor. */
    public String getEventAttribute() { return eventAttribute; }
    /** Sets the event attribute by name or one-based index. */
    public void setEventAttribute(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Event attribute cannot be blank");
        eventAttribute = value.trim();
    }
    /** Weka Explorer help for the event attribute. */
    public String eventAttributeTipText() { return "Binary status attribute; its event value means failure/removal observed."; }
    /** Event-state label shown in the Explorer property editor. */
    public String getEventValue() { return eventValue; }
    /** Sets the numeric value or nominal label that denotes an observed event. */
    public void setEventValue(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Event value cannot be blank");
        eventValue = value.trim();
    }
    /** Weka Explorer help for the event-state label. */
    public String eventValueTipText() { return "Numeric value or nominal label denoting an observed event."; }
    /** Numerical root tolerance. */
    public double getTolerance() { return tolerance; }
    /** Sets the numerical root tolerance. */
    public void setTolerance(double value) {
        if (!Double.isFinite(value) || value <= 0.0) throw new IllegalArgumentException("Tolerance must be positive");
        tolerance = value;
    }
    /** Weka Explorer help for numerical tolerance. */
    public String toleranceTipText() { return "Convergence tolerance for the Weibull shape score equation."; }
    /** Maximum number of root iterations. */
    public int getMaximumIterations() { return maximumIterations; }
    /** Sets the maximum number of root iterations. */
    public void setMaximumIterations(int value) {
        if (value < 1) throw new IllegalArgumentException("Maximum iterations must be positive");
        maximumIterations = value;
    }
    /** Weka Explorer help for maximum iterations. */
    public String maximumIterationsTipText() { return "Maximum bisection iterations."; }

    @Override
    public TechnicalInformation getTechnicalInformation() {
        TechnicalInformation information = new TechnicalInformation(TechnicalInformation.Type.BOOK);
        information.setValue(TechnicalInformation.Field.AUTHOR, "Klein, J. P. and Moeschberger, M. L.");
        information.setValue(TechnicalInformation.Field.YEAR, "2003");
        information.setValue(TechnicalInformation.Field.TITLE,
                "Survival Analysis: Techniques for Censored and Truncated Data");
        information.setValue(TechnicalInformation.Field.PUBLISHER, "Springer");
        information.setValue(TechnicalInformation.Field.ISBN, "978-0-387-95399-1");
        return information;
    }

    private int resolveEventIndex(Instances source) {
        if (eventAttribute.equalsIgnoreCase("first")) return 0;
        if (eventAttribute.equalsIgnoreCase("last")) return source.numAttributes() - 1;
        try {
            int index = Integer.parseInt(eventAttribute) - 1;
            if (index < 0 || index >= source.numAttributes()) throw new NumberFormatException();
            return index;
        } catch (NumberFormatException ignored) {
            Attribute attribute = source.attribute(eventAttribute);
            if (attribute == null) throw new IllegalArgumentException("Unknown event attribute: " + eventAttribute);
            return attribute.index();
        }
    }

    private boolean isEvent(Instance instance, int eventIndex, Attribute event) {
        if (event.isNominal()) return eventValue.equals(instance.stringValue(eventIndex));
        final double expected;
        try {
            expected = Double.parseDouble(eventValue);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Numeric event attribute requires a numeric -event-value", exception);
        }
        return Double.compare(instance.value(eventIndex), expected) == 0;
    }

    private void ensureBuilt() {
        if (model == null) throw new IllegalStateException("WeibullSurvival has not been built yet");
    }

    @Override
    public String toString() {
        if (model == null) return "WeibullSurvival: no model built yet.";
        return model + System.lineSeparator() + "Rows skipped for missing values: " + skippedMissingRows;
    }

    @Override
    public String getRevision() { return "1.0"; }

    /** Runs the classifier through Weka's standard command-line evaluation. */
    public static void main(String[] args) { runClassifier(new WeibullSurvival(), args); }
}
