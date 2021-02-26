# Optimized CDA-CH-EMED Schematron

This project provides pre-compiled XSLT files for the CDA-CH-EMED Schematron definitions.

The official CDA-CH-EMED Schematron definitions can be downloaded from
[Art-Decor](http://ehealthsuisse.art-decor.org/index.php?prefix=cdachemed-) or
[github.com/hl7ch/hl7ch-cda](https://github.com/hl7ch/hl7ch-cda/tree/master/schematrons/eHealthSuisse/eMedikation).

The two following version of XSLT files are created:
- `cdachemed-[type]-all.sch` contain all original rules;
- `cdachemed-[type]-error.sch` contain only rules that have an 'error' role (this de facto excludes all 'reports').

## Usage

Here is a usage example with the [ph-schematron](https://github.com/phax/ph-schematron) library.
```java
final SchematronResourceXSLT mtpValidator = SchematronResourceXSLT.fromFile("cdachemed-MTP-all.xslt");

// The content to validate, whether from a file or a string
final Source fileSource = new StreamSource(new BufferedInputStream(new FileInputStream(new File("mtp.xml"))));
final Source stringSource = new StreamSource(new StringReader("<?xml version="1.0" encoding="utf-8"?>"));

final SchematronOutputType report = mtpValidator.applySchematronValidationToSVRL(fileSource);

// Retrieve all error failed asserts in the report
final List<FailedAssert> failedAsserts = report.getActivePatternAndFiredRuleAndFailedAssert()
    .stream()
    .filter(object -> object instanceof FailedAssert)
    .map(object -> (FailedAssert) object)
    .collect(Collectors.toList());
// Whether the validation is a success or a failure is yours to decide, depending on triggered reports, failed
    asserts and their roles
```
