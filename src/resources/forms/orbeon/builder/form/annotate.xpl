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
<p:config xmlns:p="http://www.orbeon.com/oxf/pipeline"
          xmlns:oxf="http://www.orbeon.com/oxf/processors">

    <p:param type="input" name="data"/>
    <p:param type="input" name="bindings"/>
    <p:param type="output" name="data"/>

    <p:processor name="oxf:unsafe-xslt">
        <p:input name="data" href="#data"/>
        <p:input name="config">
            <xsl:stylesheet version="2.0"
                            xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                            xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
                            xmlns:fb="http://orbeon.org/oxf/xml/form-builder"
                            xmlns:xh="http://www.w3.org/1999/xhtml"
                            xmlns:xf="http://www.w3.org/2002/xforms"
                            xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
                            xmlns:ev="http://www.w3.org/2001/xml-events">

                <xsl:import href="oxf:/oxf/xslt/utils/copy-modes.xsl"/>

                <xsl:variable name="model" select="/*/xh:head/xf:model[@id = 'fr-form-model']"/>

                <!-- Whether we have "many" controls -->
                <xsl:variable name="many-controls"
                              select="count(/*/xh:body//*:td[exists(*)]) ge p:property('oxf.fb.section.close')"/>

                <!-- All unneeded help elements -->
                <xsl:variable xmlns:fbf="java:org.orbeon.oxf.fb.FormBuilder"
                              name="unneeded-elements"
                              select="fbf:findBlankLHHAHoldersAndElements(/, 'help')/generate-id()"/>

                <xsl:template match="xf:help[generate-id() = $unneeded-elements]"/>

                <!-- Temporarily mark read-only instances as read-write -->
                <xsl:template match="xf:instance[@xxf:readonly = 'true']">
                    <xsl:copy>
                        <xsl:attribute name="fb:readonly" select="'true'"/><!-- so we remember to set the value back -->
                        <xsl:apply-templates select="@* except @xxf:readonly | node()"/>
                    </xsl:copy>
                </xsl:template>

                <!-- Update namespace on actions and services so that they don't run at design time -->
                <xsl:template match="xf:model/xf:*[p:classes() = ('fr-service', 'fr-database-service')] | xf:model/xf:action[ends-with(@id, '-binding')]">
                    <xsl:element name="fb:{local-name()}">
                        <xsl:apply-templates select="@* | node()"/>
                    </xsl:element>
                </xsl:template>

                <!-- Disable all event handlers at design time -->
                <xsl:template match="@ev:event | @event">
                    <xsl:attribute name="fb:{local-name()}" select="."/>
                </xsl:template>

                <!--
                    fr:view:
                    - copied over along with nested fr:buttons
                    - the XForms engine must ignore foreign elements such as fr:view in the XForms view
                    - we annotate fr:body below
                -->

                <!-- fr:body → xf:group -->
                <xsl:template match="xh:body//fr:body[not(parent::fr:repeat) and not (parent::fr:grid)]">
                    <xf:group class="fb-body">
                        <xsl:copy-of select="namespace::*"/>
                        <!-- Scope $lang which is the language of the form being edited -->
                        <xf:var name="lang" value="xxf:get-variable('fr-form-model', 'fb-lang')" as="element()" class="fb-annotation"/>
                        <!-- Scope $form-resources: resources of the form being edited.
                             Use the same logic as in resources-model. In the builder, we don't have a resources-model running
                             for the form being edited, so we duplicate this here. -->
                        <xf:var name="form-resources" value="instance('fr-form-resources')/(resource[@xml:lang = $lang], resource[1])[1]" as="element(resource)?" class="fb-annotation"/>
                        <!-- Scope $fr-resources for Form Runner resources -->
                        <xf:var name="fr-resources" value="xxf:get-variable('fr-resources-model', 'fr-fr-resources')" as="element(resource)?" class="fb-annotation"/>
                        <!-- Scope $fb-resources for Form Builder resources -->
                        <xf:var name="fb-resources" value="xxf:get-variable('fr-resources-model', 'fr-form-resources')" as="element(resource)?" class="fb-annotation"/>

                        <!-- Apply all the content -->
                        <xsl:apply-templates select="node()"/>

                        <!-- Listen to activations on grid cells -->
                        <xf:action ev:event="DOMActivate" xxf:phantom="true" class="fb-annotation">
                            <xf:var name="control-element" value="xxf:control-element(event('xxf:absolute-targetid'))"/>
                            <xf:action if="xxf:split($control-element/@class) = 'xforms-activable'">
                                <xf:var name="th-column" value="count($control-element/preceding-sibling::*[@xxf:element = 'xh:th']) + 1"/>
                                <xf:var name="new-selected-cell" value="if ($control-element/@xxf:element = 'xh:th') then
                                    ($control-element/following-sibling::xf:repeat//*[@xxf:element = 'xh:td'])[$th-column]/@id
                                    else $control-element/@id"/>
                                <xf:setvalue ref="xxf:get-variable('fr-form-model', 'selected-cell')" value="$new-selected-cell"/>
                            </xf:action>
                        </xf:action>

                    </xf:group>
                </xsl:template>

                <!-- fr:section → fr:section/(@edit-ref, @xxf:update) -->
                <xsl:template match="xh:body//fr:section">
                    <xsl:copy>
                        <xsl:attribute name="edit-ref"/>
                        <xsl:attribute name="xxf:update" select="'full'"/>
                        <!-- Save current value of @open as @fb:open -->
                        <xsl:if test="@open"><xsl:attribute name="fb:open" select="@open"/></xsl:if>
                        <!-- If "many" controls close all sections but the first -->
                        <xsl:if test="$many-controls and preceding::fr:section">
                            <xsl:attribute name="open" select="'false'"/>
                        </xsl:if>
                        <xsl:apply-templates select="@* | node()"/>
                    </xsl:copy>
                </xsl:template>

                <!-- fr:grid → fr:grid/@edit-ref -->
                <xsl:template match="xh:body//fr:grid">
                    <xsl:copy>
                        <xsl:attribute name="edit-ref"/>
                        <xsl:apply-templates select="@* | node()"/>
                    </xsl:copy>
                </xsl:template>

                <!-- Convert MIP names (attributes and nested elements) -->
                <xsl:template match="xf:bind/@relevant | xf:bind/@readonly | xf:bind/@required | xf:bind/@constraint | xf:bind/@calculate | xf:bind/@xxf:default">
                    <!-- Below we only allow fb:required to be interpreted as a custom MIP -->
                    <xsl:attribute name="fb:{local-name()}" select="."/>
                </xsl:template>
                <xsl:template match="xf:bind/xf:relevant | xf:bind/xf:readonly | xf:bind/xf:required | xf:bind/xf:constraint | xf:bind/xf:calculate | xf:bind/xxf:default">
                    <xsl:element name="fb:{local-name()}">
                        <xsl:apply-templates select="@* | node()"/>
                    </xsl:element>
                </xsl:template>

                <!-- Add model actions -->
                <xsl:template match="xf:model[generate-id() = generate-id($model)]">
                    <xsl:copy>
                        <!-- Namespace for fb:required -->
                        <xsl:namespace name="fb" select="'http://orbeon.org/oxf/xml/form-builder'"/>
                        <xsl:apply-templates select="@* except @xxf:custom-mips"/>
                        <!-- Only let fb:required act as a custom MIP. All other non-standard bind attributes, including
                             @fb:relevant, are ignored. We don't need the result of these rewritten MIPs at design time,
                             and this helps with performance in case there is a large number of binds. -->
                        <xsl:attribute name="xxf:custom-mips" select="string-join((@xxf:custom-mips, 'fb:required'), ' ')"/>

                        <xsl:apply-templates select="node()"/>

                        <!-- Upon model creation, recalculation and revalidation, notify Form Builder -->
                        <xsl:for-each select="('xforms-model-construct', 'xforms-recalculate', 'xforms-revalidate', 'xxforms-xpath-error')">
                            <xf:action ev:event="{.}" ev:target="#observer" class="fb-annotation">
                                <!-- Upon MIP XPath error cancel the default error behavior (which otherwise can open an
                                     error dialog at inopportune times.) -->
                                <xsl:if test=". = 'xxforms-xpath-error'">
                                    <xsl:attribute name="ev:defaultAction">cancel</xsl:attribute>
                                </xsl:if>
                                <!-- Dispatch custom event to FB model -->
                                <!-- USE OF ABSOLUTE ID -->
                                <xf:dispatch name="fb-{.}" targetid="|fr-form-model|"/>
                            </xf:action>
                        </xsl:for-each>

                        <!-- Dummy variable values so that user XPath expressions find them -->
                        <xf:var name="fr-roles" value="''" class="fb-annotation"/>
                        <xf:var name="fr-mode" value="'edit'" class="fb-annotation"/>

                    </xsl:copy>
                </xsl:template>

                <!-- Prevent fr:buttons from showing/running -->
                <xsl:template match="fr:buttons">
                    <xf:group class="fr-buttons" ref="()">
                        <xsl:apply-templates select="node()"/>
                    </xf:group>
                </xsl:template>

                <!--
                    Remove actions implementations as they are unneeded. See also:

                    - actions.xsl
                    - https://github.com/orbeon/orbeon-forms/issues/1019
                -->
                <xsl:template match="xf:model[generate-id() = generate-id($model)]/xf:action[ends-with(@id, '-binding')]//xf:action[p:has-class('fr-set-service-value-action')]">
                    <xsl:copy>
                        <xsl:apply-templates select="@*"/>
                        <xsl:apply-templates select="(*:variable | *:var)[@name = ('control-name', 'path')]"/>
                    </xsl:copy>
                </xsl:template>

                <xsl:template match="xf:model[generate-id() = generate-id($model)]/xf:action[ends-with(@id, '-binding')]//xf:action[p:has-class('fr-set-database-service-value-action')]">
                    <xsl:copy>
                        <xsl:apply-templates select="@*"/>
                        <xsl:apply-templates select="(*:variable | *:var)[@name = ('control-name', 'parameter')]"/>
                    </xsl:copy>
                </xsl:template>

                <xsl:template match="xf:model[generate-id() = generate-id($model)]/xf:action[ends-with(@id, '-binding')]//xf:action[p:has-class('fr-set-control-value-action')]">
                    <xsl:copy>
                        <xsl:apply-templates select="@*"/>
                        <xsl:apply-templates select="(*:variable | *:var)[@name = ('control-name', 'control-value')]"/>
                    </xsl:copy>
                </xsl:template>

                <xsl:template match="xf:model[generate-id() = generate-id($model)]/xf:action[ends-with(@id, '-binding')]//xf:action[p:has-class('fr-itemset-action')]">
                    <xsl:copy>
                        <xsl:apply-templates select="@*"/>
                        <xsl:apply-templates select="(*:variable | *:var)[@name = ('control-name', 'response-items')]"/>
                        <!-- These two variables may be nested for forms generated with the inline implementation of the action -->
                        <xsl:apply-templates select=".//(*:variable | *:var)[@name = ('item-label', 'item-value')]"/>
                    </xsl:copy>
                </xsl:template>

                <!-- Saxon serialization adds an extra meta element, make sure to remove it -->
                <xsl:template match="xh:head/meta[@http-equiv = 'Content-Type']"/>

                <!-- ======== Upgrading form ========-->

                <!-- Use ref instead of nodeset -->
                <xsl:template match="xf:*/@nodeset">
                    <xsl:attribute name="ref" select="."/>
                </xsl:template>

                <!-- Convert minimal xf:select1 to fr:dropdown-select1 -->
                <xsl:template match="xf:select1[@appearance = 'minimal']">
                    <fr:dropdown-select1>
                        <xsl:apply-templates select="@* except @appearance | node() except xf:item[xf:value = '']"/>
                    </fr:dropdown-select1>
                </xsl:template>

            </xsl:stylesheet>
        </p:input>
        <p:output name="data" id="annotated"/>
    </p:processor>

    <!-- Make sure XBL bindings for section templates are present -->
    <p:processor name="oxf:pipeline">
        <p:input name="config" href="add-template-bindings.xpl"/>
        <p:input name="data" href="#annotated"/>
        <p:input name="bindings" href="#bindings"/>
        <p:output name="data" ref="data"/>
    </p:processor>

</p:config>