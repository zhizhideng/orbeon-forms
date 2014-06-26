<!--
  Copyright (C) 2011 Orbeon, Inc.

  This program is free software; you can redistribute it and/or modify it under the terms of the
  GNU Lesser General Public License as published by the Free Software Foundation; either version
  2.1 of the License, or (at your option) any later version.

  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  See the GNU Lesser General Public License for more details.

  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  -->
<xsl:transform version="2.0"
        xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
        xmlns:xh="http://www.w3.org/1999/xhtml"
        xmlns:xs="http://www.w3.org/2001/XMLSchema"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xmlns:oxf="http://www.orbeon.com/oxf/processors"
        xmlns:p="http://www.orbeon.com/oxf/pipeline"
        xmlns:xi="http://www.w3.org/2001/XInclude"
        xmlns:xf="http://www.w3.org/2002/xforms"
        xmlns:xbl="http://www.w3.org/ns/xbl"
        xmlns:ev="http://www.w3.org/2001/xml-events"
        xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
        xmlns:fb="http://orbeon.org/oxf/xml/form-builder"
        xmlns:saxon="http://saxon.sf.net/"
        xmlns:xpl="java:org.orbeon.oxf.pipeline.api.FunctionLibrary"
        xmlns:map="java:java.util.Map"
        xmlns:frf="java:org.orbeon.oxf.fr.FormRunner">

    <xsl:variable name="data" select="/*" as="element()"/>
    <xsl:variable name="parameters" select="doc('input:parameters')/*" as="element()"/>

    <xsl:variable name="is-not-a-control-classes" select="('xforms-trigger', 'xforms-disabled', 'xforms-group', 'xforms-case', 'xforms-switch', 'xforms-repeat')" as="xs:string*"/>
    <xsl:variable name="control-classes"          select="('xforms-input', 'xforms-textarea', 'xforms-select', 'xforms-select1', 'fr-attachment', 'xforms-output')" as="xs:string*"/>
    <xsl:variable name="image-attachment-classes" select="('xbl-fr-image-attachment')" as="xs:string*"/>
    <xsl:variable name="attachment-classes"       select="('fr-attachment', $image-attachment-classes)" as="xs:string*"/>

    <xsl:variable name="ids" select="//*"/>

    <xsl:template match="/">
        <config>
            <!-- Template -->
            <template href="input:template" show-grid="false"/>

            <!-- Barcode -->
            <!-- NOTE: Code 39 only take uppercase letters, hence we upper-case(…) -->
            <xsl:variable name="barcode-value" select="upper-case($parameters/document)" as="xs:string?"/>
            <xsl:if test="normalize-space($barcode-value) != '' and xpl:property(string-join(('oxf.fr.detail.pdf.barcode', $parameters/app, $parameters/form), '.'))">
                <group ref="/*" font-pitch="15.9" font-family="Courier" font-size="12">
                    <barcode left="80" top="800" height="15" value="'{$barcode-value}'"/>
                </group>
            </xsl:if>

            <xsl:variable name="pdfFormats" select="frf:getPDFFormats()"/>

            <xsl:for-each select="p:split(xpl:property('oxf.fr.pdf.template.font.paths'))">
                <substitution-font font-family="{.}" embed="true"/>
            </xsl:for-each>

            <xsl:for-each select="//*[@id = 'fr-form-group']//*[p:classes(.) = ($control-classes, 'xbl-component') and not(p:classes(.) = $is-not-a-control-classes)]">

                <xsl:variable name="control" select="."/>

                <xsl:variable
                    name="pdf-field-name"
                    select="frf:buildPDFFieldNameFromHTML($control)"/>

                <xsl:variable name="classes" select="p:classes(.)"/>

                <!-- Get the expression to evaluate on the control from the configuration properties -->
                <xsl:variable name="expression" as="xs:string?">
                    <xsl:choose>
                        <xsl:when test="$classes = 'xbl-component'">
                            <!-- XBL component -->
                            <xsl:variable name="component-name" select="for $c in $classes return if (starts-with($c, 'xbl-') and $c != 'xbl-component') then substring-after($c, 'xbl-') else ()"/>
                            <xsl:variable name="type" select="(for $c in $classes return if (starts-with($c, 'xforms-type-')) then substring-after($c, 'xforms-type-') else (), 'string')[1]"/>
                            <xsl:copy-of select="frf:getPDFFormatExpression($pdfFormats, $parameters/app, $parameters/form, $component-name, $type)"/>
                        </xsl:when>
                        <xsl:when test="$classes = $control-classes">
                            <!-- Built-in controls -->
                            <xsl:variable name="component-name" select="$control-classes[. = $classes][1]"/>
                            <xsl:variable name="type" select="(for $c in $classes return if (starts-with($c, 'xforms-type-')) then substring-after($c, 'xforms-type-') else (), 'string')[1]"/>
                            <xsl:copy-of select="frf:getPDFFormatExpression($pdfFormats, $parameters/app, $parameters/form, $component-name, $type)"/>
                        </xsl:when>
                    </xsl:choose>
                </xsl:variable>

                <!-- If an expression was found, evaluate it to produce the value of the field -->
                <xsl:if test="$expression">
                    <xsl:variable name="value" select="$control/saxon:evaluate(string($expression))"/>
                    <xsl:if test="$value">
                        <xsl:choose>
                            <xsl:when test="$classes = $image-attachment-classes">
                                <!-- Handle URL rewriting for image attachments -->
                                <image acro-field-name="'{$pdf-field-name}'" href="{xpl:rewriteResourceURI($value, true())}"/>
                            </xsl:when>
                            <xsl:otherwise>
                                <field acro-field-name="'{$pdf-field-name}'" value="'{replace($value, '''', '''''')}'"/>
                            </xsl:otherwise>
                        </xsl:choose>
                    </xsl:if>
                </xsl:if>

                <!-- Also provide a mapping for select where disjoint values are output as separate acrobat fields -->
                <xsl:if test="$classes = 'xforms-select'">
                    <xsl:variable name="expression" select="map:get($pdfFormats, 'select-values')"/>
                    <xsl:if test="$expression">
                        <xsl:for-each select="$control/saxon:evaluate(string($expression))">
                            <xsl:variable name="item-value" as="xs:string" select="."/>
                            <xsl:if test="$item-value">
                                <field acro-field-name="'{$pdf-field-name}${$item-value}'" value="'true'"/>
                            </xsl:if>
                        </xsl:for-each>
                    </xsl:if>
                </xsl:if>
            </xsl:for-each>
        </config>
    </xsl:template>


</xsl:transform>
