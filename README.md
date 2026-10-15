# WeibullSurvival for Weka

`WeibullSurvival` is a standalone Weka extension for estimating a
two-parameter Weibull lifetime distribution in the presence of right-censored
observations. Its motivating applications are software obsolescence and
technical-debt survival, but the estimator is domain independent.

## What the extension estimates

For shape `beta > 0` and scale `eta > 0`:

```text
S(t) = exp(-(t / eta)^beta)
h(t) = (beta / eta) (t / eta)^(beta - 1)
```

With event indicator `delta_i` (`1` observed, `0` right-censored), the fitted
log-likelihood is:

```text
log L = sum_i delta_i [log(beta) - beta log(eta)
                       + (beta - 1) log(t_i)]
        - sum_i (t_i / eta)^beta
```

The implementation profiles `eta` analytically and solves the one-dimensional
score equation for `beta` by bracketed bisection with log-sum-exp arithmetic.
It reports `beta`, `eta`, log-likelihood, AIC, BIC, survival/hazard functions,
quantiles, mean, median, observed-information standard errors and 95% CIs.

## Weka data contract

- The class must be the numeric, strictly positive duration.
- `-event` selects a numeric or nominal binary status attribute.
- `-event-value` identifies the observed-event state; every other binary state
  is treated as right-censored.
- Rows missing duration or status are skipped and counted.
- Weka instance weights are honored.
- Other attributes are accepted but ignored in version 0.1.0.

Because this release is a baseline lifetime distribution without covariates,
`classifyInstance()` returns the same fitted median for every row. Use `-no-cv`
when the goal is parameter estimation rather than evaluating that constant
prediction as a conventional regression model.

## Build and run

Java 17 and Maven 3.6.3 or later are required.

```bash
mvn verify
scripts/build-package.sh
scripts/test-package.sh
```

CLI example:

```bash
java -cp 'weka.jar:WeibullSurvival.jar' \
  weka.classifiers.functions.WeibullSurvival \
  -t examples/weibull-demo.arff -c last \
  -event event -event-value yes -no-cv
```

The Weka-installable archive is written to
`output/WeibullSurvival-0.1.0.zip`.

In the Weka GUI, install that ZIP with the Package Manager, open Explorer,
load an ARFF file, set the duration attribute as the class, then choose
`functions > WeibullSurvival`. Configure `eventAttribute` and `eventValue` in
the classifier property dialog.

## Reproducible scientific validation

```bash
scripts/run-scientific-validation.sh
```

The validation downloads and checksum-verifies two open datasets:

1. PHP code-smell histories, Zenodo DOI
   [10.5281/zenodo.7626150](https://doi.org/10.5281/zenodo.7626150),
   CC BY 4.0. The source field named `censored` is interpreted according to the
   data documentation: `1` means the smell was removed (event observed).
2. Java code- and test-smell survival data, Zenodo DOI
   [10.5281/zenodo.16230542](https://doi.org/10.5281/zenodo.16230542),
   CC BY 4.0. The source provides `duration` and `event` directly.

The script transforms the datasets to ARFF, estimates them with Java/Weka,
re-estimates the same censored likelihood with an independent Python
standard-library implementation, and requires relative agreement within
`1e-8`. Generated source data remain ignored; concise evidence is retained in
`validation/results`. The interpreted results and fit diagnostics are in
[`validation/REPORT.md`](validation/REPORT.md).

The pooled fits validate numerical behavior, not a causal or universally valid
law of software decay. Project, smell-type and calendar heterogeneity require a
stratified model or a Weibull AFT/proportional-hazards extension before making
substantive comparisons.

## Scope and limitations

Version 0.1.0 supports one baseline Weibull distribution and right censoring.
It does not yet support left/interval censoring, time-varying covariates,
competing risks, frailty, Weibull AFT regression or proportional hazards.

License: GPL-3.0-or-later.
