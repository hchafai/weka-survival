# WeibullSurvival for Weka

`WeibullSurvival` is a standalone Weka extension for estimating a
two-parameter Weibull lifetime distribution in the presence of right-censored
observations. Its motivating applications are software obsolescence and
technical-debt survival, but the estimator is domain independent.

## Mathematical model

Let `T` be a positive lifetime, `beta > 0` the shape parameter and `eta > 0`
the scale parameter. The two-parameter Weibull model is defined by:

```text
Probability density:       f(t) = (beta / eta) (t / eta)^(beta - 1)
                                   exp(-(t / eta)^beta)
Cumulative probability:    F(t) = 1 - exp(-(t / eta)^beta)
Survival probability:      S(t) = exp(-(t / eta)^beta)
Cumulative hazard:         H(t) = (t / eta)^beta
Instantaneous hazard:      h(t) = (beta / eta) (t / eta)^(beta - 1)
```

The shape parameter describes how risk changes with age:

- `beta < 1`: decreasing hazard; surviving items become less likely to fail or
  be removed as they age;
- `beta = 1`: constant hazard, which reduces Weibull to the exponential law;
- `beta > 1`: increasing hazard, often interpreted as aging, wear or growing
  obsolescence pressure.

`eta` is the characteristic lifetime for which `F(eta) = 1 - exp(-1)`, meaning
approximately 63.2% of events have occurred. Useful summaries are:

```text
Median:       t_0.5 = eta (ln 2)^(1 / beta)
Mean:         E[T]  = eta Gamma(1 + 1 / beta)
Quantile:     t_p   = eta [-ln(1 - p)]^(1 / beta)
```

### Right-censored maximum likelihood

For observation `i`, `t_i` is the known duration and `delta_i` equals `1` when
the event is observed or `0` when the item is still alive at the last
observation. An observed event contributes the density `f(t_i)`; a censored
record contributes only the probability of surviving at least until `t_i`,
namely `S(t_i)`. Therefore:

```text
L(beta, eta) = product_i f(t_i)^delta_i S(t_i)^(1 - delta_i)

log L = sum_i delta_i [log(beta) - beta log(eta)
                       + (beta - 1) log(t_i)]
        - sum_i (t_i / eta)^beta
```

For fixed `beta`, the scale has the analytical profile estimate below, where
`w_i` is the Weka instance weight:

```text
eta(beta) = [sum_i w_i t_i^beta / sum_i w_i delta_i]^(1 / beta)
```

The implementation substitutes this expression into the likelihood and solves
the resulting one-dimensional score equation for `beta` by bracketed bisection
with log-sum-exp arithmetic. It reports `beta`, `eta`, log-likelihood, AIC, BIC,
survival and hazard functions, quantiles, mean, median, observed-information
standard errors and 95% confidence intervals.

## Intended use cases

### Software obsolescence and replacement

- `duration`: time from deployment or a selected baseline until replacement;
- `event = 1`: the software was retired, replaced or migrated;
- `event = 0`: the software was still operating when observation ended.

The fitted survival curve estimates the fraction of solutions expected to
remain in service beyond a horizon. Hazard evolution helps determine whether
replacement pressure decreases, stays constant or increases with age. These
results can support replacement planning, but cost, security and business
criticality must still be modeled separately.

### Technical debt and software smells

- `duration`: lifetime of a debt item, code smell or test smell;
- `event = 1`: the item was repaid, refactored or removed;
- `event = 0`: it was still present at the final analyzed revision.

Here, `S(t)` estimates persistence beyond time `t`, while `h(t)` describes the
instantaneous removal rate among items that have survived until `t`. Comparing
projects or smell types requires separate fits, stratification, or a future
Weibull regression model rather than interpreting one pooled curve causally.

### Reliability and maintenance

The same data contract applies to component failures, incident resolution,
service retirement and maintenance intervals whenever durations are positive,
events are binary and incomplete follow-up produces right censoring.

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
