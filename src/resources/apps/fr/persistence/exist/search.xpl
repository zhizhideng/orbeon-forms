<!--
  Copyright (C) 2010 Orbeon, Inc.

  This program is free software; you can redistribute it and/or modify it under the terms of the
  GNU Lesser General Public License as published by the Free Software Foundation; either version
  2.1 of the License, or (at your option) any later version.

  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  See the GNU Lesser General Public License for more details.

  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  -->
<p:config xmlns:p="http://www.orbeon.com/oxf/pipeline"
        xmlns:sql="http://orbeon.org/oxf/xml/sql"
        xmlns:odt="http://orbeon.org/oxf/xml/datatypes"
        xmlns:xs="http://www.w3.org/2001/XMLSchema"
        xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
        xmlns:saxon="http://saxon.sf.net/"
        xmlns:oxf="http://www.orbeon.com/oxf/processors"
        xmlns:xi="http://www.w3.org/2001/XInclude"
        xmlns:xf="http://www.w3.org/2002/xforms"
        xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
        xmlns:ev="http://www.w3.org/2001/xml-events"
        xmlns:frf="java:org.orbeon.oxf.fr.FormRunner">

    <!-- Search instance -->
    <p:param name="instance" type="input"/>

    <!-- Search result -->
    <p:param name="data" type="output"/>

    <p:processor name="oxf:pipeline">
        <p:input name="config" href="../common-search.xpl"/>
        <p:input name="search" href="#instance"/>
        <p:output name="search" id="search-input"/>
    </p:processor>

    <!-- Check whether users can do a search based on their roles -->
    <p:processor name="oxf:xforms-submission">
        <p:input name="request"><dummy/></p:input>
        <p:input name="submission" transform="oxf:xslt" href="#search-input">
            <xf:submission xsl:version="2.0" method="get" replace="instance"
                               resource="/fr/service/persistence/form/{encode-for-uri(/search/app)}/{encode-for-uri(/search/form)}"/>
        </p:input>
        <p:output name="response" id="form-metadata"/>
    </p:processor>
    <p:processor name="oxf:unsafe-xslt">
        <p:input name="data" href="#form-metadata"/>
        <p:input name="config">
            <root xsl:version="2.0">
                <xsl:variable name="permissions"                select="/forms/form/permissions"/>
                <xsl:variable name="operations-from-role"       select="frf:javaAuthorizedOperationsBasedOnRoles($permissions)"/>
                <xsl:variable name="search-operations"          select="('*', 'read', 'update', 'delete')"/>
                <xsl:variable name="authorized-based-on-role"   select="$operations-from-role = $search-operations"/>
                <xsl:if test="not($authorized-based-on-role)">
                    <xsl:value-of select="frf:sendError(403)"/>
                </xsl:if>
            </root>
        </p:input>
        <p:output name="data" id="check-authorized"/>
    </p:processor>
    <p:processor name="oxf:null-serializer">
        <p:input name="data" href="#check-authorized"/>
    </p:processor>

    <p:processor name="oxf:request">
        <p:input name="config">
            <config stream-type="xs:anyURI">
                <include>/request/headers/header[name = 'orbeon-exist-uri']</include>
            </config>
        </p:input>
        <p:output name="data" id="request"/>
    </p:processor>

    <!-- Prepare submission -->
    <p:processor name="oxf:xslt">
        <p:input name="data" href="#search-input"/>
        <p:input name="request" href="#request"/>
        <p:input name="config">
            <xf:submission xsl:version="2.0" method="post"
                               resource="{doc('input:request')/request/headers/header[name = 'orbeon-exist-uri']/value}/{/*/app}/{/*/form
                                            }/data/?page-size={/*/page-size
                                            }&amp;page-number={/*/page-number
                                            }&amp;query={
                                                concat(/*/query[empty(@path)],
                                                       string-join(for $query in /*/query[@path and normalize-space() != '']
                                                         return concat('&amp;path=', encode-for-uri($query/@path), '&amp;value=', $query), ''))
                                            }&amp;lang={/*/lang}" replace="instance">
                <!-- Move resulting <document> element as root element -->
                <xf:insert ev:event="xforms-submit-done" if="event('response-status-code') = 200" ref="/*" origin="/*/*[1]"/>
                <xi:include href="propagate-exist-error.xml" xpointer="xpath(/root/*)"/>
            </xf:submission>
        </p:input>
        <p:output name="data" id="submission"/>
    </p:processor>

    <!-- Prepare query -->
    <p:processor name="oxf:xslt">
        <p:input name="data" href="#search-input"/>
        <p:input name="query" href="search.xml"/>
        <p:input name="config">
            <xsl:stylesheet version="2.0">
                <xsl:import href="oxf:/oxf/xslt/utils/copy.xsl"/>
                <!-- Get query and apply templates -->
                <xsl:variable name="query" select="doc('input:query')"/>
                <xsl:variable name="instance" select="/*" as="element(search)"/>
                <xsl:template match="/">
                    <xsl:apply-templates select="$query/*"/>
                </xsl:template>
                <!-- Dynamically list of namespaces -->
                <xsl:template match="namespaces">
                    <!-- All namespaces on all query elements, which will contain duplicate namespaces -->
                    <!-- Note: we need here to exclude the declaration for the XML namespace -->
                    <xsl:variable name="namespaces" select="$instance/query/namespace::*[local-name() != 'xml']"/>
                    <xsl:variable name="prefixes" as="xs:string*" select="distinct-values($namespaces/local-name())"/>
                    <xsl:for-each select="$prefixes">
                        <xsl:variable name="prefix" select="."/>
                        <xsl:variable name="namespace" select="$namespaces[local-name() = $prefix][1]"/>
                        <xsl:value-of select="concat('declare namespace ', $prefix, '=&quot;', $namespace, '&quot;;')"/>
                    </xsl:for-each>
                </xsl:template>
                <!-- Dynamically build where clause -->
                <xsl:template match="where">
                    <!-- New lucene index -->
                    <xsl:for-each select="$instance/query[@path and normalize-space() != '']">
                        <xsl:variable name="position" select="position()" as="xs:integer"/>
                        <!-- NOTE: We should probably use ft:query() below as well -->
                        <xsl:choose>
                            <xsl:when test="@match = 'exact'">
                                <!-- Exact match -->
                                and $d/*/<xsl:value-of select="@path"/>[. = $value[<xsl:value-of select="$position"/>]]
                            </xsl:when>
                            <xsl:otherwise>
                                <!-- Substring, case-insensitive match -->
                                and $d/*/<xsl:value-of select="@path"/>[contains(lower-case(.), lower-case($value[<xsl:value-of select="$position"/>]))]
                            </xsl:otherwise>
                        </xsl:choose>
                    </xsl:for-each>
                </xsl:template>
                <!-- Dynamically build detail result -->
                <xsl:template match="details">
                    <xsl:for-each select="$instance/query[@path]">
                        &lt;detail>
                            {string-join($d/*/<xsl:value-of select="@path"/>, ', ')}
                        &lt;/detail>
                    </xsl:for-each>
                </xsl:template>
            </xsl:stylesheet>
        </p:input>
        <p:output name="data" id="query"/>
    </p:processor>

    <!-- Execute REST submission -->
    <p:processor name="oxf:xforms-submission">
        <p:input name="submission" href="#submission"/>
        <p:input name="request" href="#query"/>
        <p:output name="response" ref="data"/>
    </p:processor>

</p:config>
