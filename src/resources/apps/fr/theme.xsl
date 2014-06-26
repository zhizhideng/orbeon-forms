<!--
    Copyright (C) 2009 Orbeon, Inc.

    This program is free software; you can redistribute it and/or modify it under the terms of the
    GNU Lesser General Public License as published by the Free Software Foundation; either version
    2.1 of the License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
    without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
    See the GNU Lesser General Public License for more details.

    The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
-->
<!--
    Regular theme for Form Runner.
-->
<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:xh="http://www.w3.org/1999/xhtml"
                xmlns:xi="http://www.w3.org/2001/XInclude"
                xmlns:p="http://www.orbeon.com/oxf/pipeline">

    <!-- Just use the plain theme -->
    <xsl:import href="../../config/theme-plain.xsl"/>

    <xsl:template match="xh:head" priority="10">
        <xsl:copy>
            <xsl:call-template name="head"/>
            <!-- Favicon -->
            <xh:link rel="shortcut icon" href="/ops/images/orbeon-icon-16.ico"/>
            <xh:link rel="icon" href="/ops/images/orbeon-icon-16.png" type="image/png"/>
        </xsl:copy>
    </xsl:template>

    <xsl:template match="xh:body">
        <xsl:copy>
            <xsl:apply-templates select="@*|node()"/>
            <xsl:if test="p:property('oxf.epilogue.show-feedback')">
                <xsl:copy-of select="doc('oxf:/config/feedback.xhtml')"/>
            </xsl:if>
        </xsl:copy>
    </xsl:template>

</xsl:stylesheet>
