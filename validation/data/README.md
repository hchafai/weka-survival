# Validation data

The full datasets are intentionally not committed. Run
`scripts/run-scientific-validation.sh`; it downloads the original archives,
checks their SHA-256 digests and creates:

- `php-code-smells.arff` from Zenodo record `7626150`;
- `java-code-test-smells.arff` from Zenodo record `16230542`.

Both generated files contain only `event` and `duration`. In the PHP source,
`censored=1` means the smell was removed, so it is an observed event despite
the misleading source-column name. Rows with `diff=0` are excluded because a
continuous Weibull likelihood requires a strictly positive duration.

The included `weibull-demo.arff` is a small technical fixture, not scientific
evidence.
