<!--
    Copyright (C) 2012 Orbeon, Inc.
  
    This program is free software; you can redistribute it and/or modify it under the terms of the
    GNU Lesser General Public License as published by the Free Software Foundation; either version
    2.1 of the License, or (at your option) any later version.
  
    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
    without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
    See the GNU Lesser General Public License for more details.
  
    The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
-->
<p:config xmlns:p="http://www.orbeon.com/oxf/pipeline"
          xmlns:oxf="http://www.orbeon.com/oxf/processors">

    <!-- The inputs and output of this pipeline must be connected using -i and -o command line options -->

    <p:param name="transformation" type="input"/>
    <p:param name="data" type="input"/>
    <p:param name="data" type="output"/>

    <!-- Execute the XSLT transformation -->
    <p:processor name="oxf:xslt">
        <p:input name="data" href="#data"/>
        <p:input name="config" href="#transformation"/>
        <p:output name="data" ref="data"/>
    </p:processor>

</p:config>
