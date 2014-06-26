<p:config xmlns:p="http://www.orbeon.com/oxf/pipeline"
    xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
    xmlns:xf="http://www.w3.org/2002/xforms"
    xmlns:oxf="http://www.orbeon.com/oxf/processors"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xh="http://www.w3.org/1999/xhtml">

    <p:param type="input" name="instance"/>
    <p:param type="output" name="result"/>

    <p:processor name="oxf:identity">
        <p:input name="data" href="#instance"/>
        <p:output name="data" ref="result"/>
    </p:processor>

</p:config>
