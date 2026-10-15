# Validation report — WeibullSurvival 0.1.0

Validation date: 2026-10-15.

## Protocol

1. Download the two original Zenodo archives and verify their SHA-256 digests.
2. Retain complete binary event/duration rows with strictly positive duration.
3. Fit the right-censored two-parameter Weibull likelihood in Java/Weka.
4. Refit it with an independent Python standard-library implementation.
5. Require relative agreement within `1e-8` on counts, parameters and criteria.
6. Compare the fitted survival curve with the non-parametric Kaplan–Meier
   estimate and compare AIC with the nested exponential model (`beta = 1`).

## Data audit and estimates

| Dataset | Used | Events | Censored | Excluded | beta | eta (days) | Median (days) |
|---|---:|---:|---:|---:|---:|---:|---:|
| PHP code smells | 67,583 | 41,283 | 26,300 | 52 | 0.950610 | 1,982.586 | 1,348.303 |
| Java code/test smells | 41,650 | 15,235 | 26,415 | 1,191 | 0.460648 | 8,387.558 | 3,785.219 |

All exclusions were made before fitting because the source duration was zero or
otherwise invalid for a continuous Weibull likelihood. No positive complete
duration was removed.

## Numerical validation

The Java/Weka and Python estimates agree within the required relative tolerance
of `1e-8`. The exact machine-readable values are in `weka-results.csv` and
`python-reference.csv`.

## Fit diagnostics

| Dataset | KM event-weighted RMSE | Maximum KM error | Weibull-plot R² | AIC gain vs exponential |
|---|---:|---:|---:|---:|
| PHP code smells | 0.02835 | 0.06619 | 0.95715 | 166.94 |
| Java code/test smells | 0.01947 | 0.05275 | 0.97321 | 17,746.59 |

The positive AIC differences favor the two-parameter Weibull model over the
one-parameter exponential baseline on both pooled datasets. `beta < 1` means
the fitted hazard decreases with age; it does not by itself prove a causal
aging mechanism.

## Interpretation boundary

This validation establishes reproducibility, agreement with the stated
likelihood, and useful pooled descriptive fit. It does not establish external
validity for every software system. The records mix projects, smell types and
calendar periods, and censoring may depend on project history. Scientific use
should therefore add stratification or a regression model and report
sensitivity analyses rather than treating the pooled `beta` and `eta` as
universal constants.

## Sources

- PHP dataset: <https://doi.org/10.5281/zenodo.7626150>, CC BY 4.0.
- Java dataset: <https://doi.org/10.5281/zenodo.16230542>, CC BY 4.0.
