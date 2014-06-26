<!--
    Copyright (C) 2007 Orbeon, Inc.

    This program is free software; you can redistribute it and/or modify it under the terms of the
    GNU Lesser General Public License as published by the Free Software Foundation; either version
    2.1 of the License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
    without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
    See the GNU Lesser General Public License for more details.

    The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
-->
<p:config xmlns:p="http://www.orbeon.com/oxf/pipeline"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:fo="http://www.w3.org/1999/XSL/Format"
    xmlns:xh="http://www.w3.org/1999/xhtml"
    xmlns:oxf="http://www.orbeon.com/oxf/processors"
    xmlns:xf="http://www.w3.org/2002/xforms"
    xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">

    <!-- Generate resource based on path -->
    <p:processor name="oxf:java">
        <p:input name="config">
            <config sourcepath="oxf:/" class="config.utils.GenerateResource"/>
        </p:input>
        <p:output name="data" id="xhtml-xforms"/>
    </p:processor>
    <!-- Native XForms Initialization -->
    <p:processor name="oxf:xforms-to-xhtml">
        <p:input name="annotated-document" href="#xhtml-xforms"/>
        <p:input name="data"><null xsi:nil="true"/></p:input>
        <p:input name="instance"><null xsi:nil="true"/></p:input>
        <p:output name="document" id="xformed-data"/>
    </p:processor>
    <!-- Rewrite all URLs in HTML and XHTML documents -->
    <p:processor name="oxf:xhtml-rewrite">
        <p:input name="data" href="#xformed-data"/>
        <p:output name="data" id="rewritten-data"/>
    </p:processor>
    <!-- Move from XHTML namespace to no namespace -->
    <p:processor name="oxf:plain-html-converter">
        <p:input name="data" href="#rewritten-data"/>
        <p:output name="data" id="html-data"/>
    </p:processor>
    <!-- Convert to plain HTML -->
    <p:processor name="oxf:html-converter">
        <p:input name="config">
            <config>
                <version>5.0</version>
                <encoding>utf-8</encoding>
            </config>
        </p:input>
        <p:input name="data" href="#html-data"/>
        <p:output name="data" id="converted"/>
    </p:processor>
    <!-- Serialize to HTTP -->
    <p:processor name="oxf:http-serializer">
        <p:input name="config">
            <config>
                <header>
                    <name>Cache-Control</name>
                    <value>post-check=0, pre-check=0</value>
                </header>
                <!-- NOTE: HTML converter specifies text/html content-type -->
            </config>
        </p:input>
        <p:input name="data" href="#converted"/>
    </p:processor>

</p:config>
