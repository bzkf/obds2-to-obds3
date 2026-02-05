# obds2-to-obds3

[![OpenSSF Scorecard](https://img.shields.io/ossf-scorecard/github.com/bzkf/obds2-to-obds3?label=openssf%20scorecard&style=flat)](https://scorecard.dev/viewer/?uri=github.com/bzkf/obds2-to-obds3)

Library to map oBDS v2 (ADT_GEKID) into oBDS v3 messages

## Mapping Caveats

### Multiple Histologie to single Histologie

In v2, it was possible to report multiple Diagnose.Histologie elements, while v3 only allows setting a single one.
To handle this, we first sort alle Histologie elements by date, descending. We then iterate thorugh them and use the
first element's `SentinelLKUntersucht`, `SentinelLKBefallen` where both are set in the same element. Ditto for
`LKUntersucht`, `LKBefallen`. We take the most recent Histologie_ID available. Morphologie-Codes are set to the v3
ICDO list. The `MorphologieFreitext` is set to the first available text. The Histologie-Datum is set to the first
available value if the histologie_id is still unset, take the first matching one, otherwise always use the one
corresponding to the set Histologie_ID. We finally take the most severe Grading value.


## Related application

Besides the library, an application can be used to map a set of messages into oBDS v3 messages.

```console
> java -jar obds2-to-obds3-app.jar

usage: java -jar obds2-to-obds3-app.jar --input <input file> --output
            <output file>
    --fix-missing-id      Fix missing IDs by generating hash values
 -i,--input <input>       Input file
    --ignore-unmappable   Ignore unmappable messages and patients
 -o,--output <output>     Output file
 -v                       Show errors
 -vv                      Show exceptions and stack traces
```
