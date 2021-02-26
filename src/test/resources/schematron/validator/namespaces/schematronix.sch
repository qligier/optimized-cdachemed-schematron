<?xml version="1.0" encoding="UTF-8"?>
<schema xmlns="http://purl.oclc.org/dsdl/schematron" queryBinding="xslt2">
    <title>Schematron sample file for namespaces</title>
    <ns uri="urn:example:schematron:book" prefix="book"/>
   	<ns uri="urn:example:schematron:isbn" prefix="isbn"/>

    <pattern id="pattern1">
        <rule context="//book:book" id="rule1">
            <assert test="not(@isbn:isbn)">Found ISBN with value '<value-of select="@isbn:isbn"/>'</assert>
        </rule>
    </pattern>
</schema>
