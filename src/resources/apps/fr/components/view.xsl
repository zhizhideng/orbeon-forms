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
<xsl:stylesheet
        version="2.0"
        xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
        xmlns:xs="http://www.w3.org/2001/XMLSchema"
        xmlns:xf="http://www.w3.org/2002/xforms"
        xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
        xmlns:exf="http://www.exforms.org/exf/1-0"
        xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
        xmlns:xh="http://www.w3.org/1999/xhtml"
        xmlns:xi="http://www.w3.org/2001/XInclude"
        xmlns:xxi="http://orbeon.org/oxf/xml/xinclude"
        xmlns:ev="http://www.w3.org/2001/xml-events"
        xmlns:xbl="http://www.w3.org/ns/xbl"
        xmlns:frf="java:org.orbeon.oxf.fr.FormRunner">

    <xsl:variable name="view"           select="(/xh:html/xh:body/fr:view)[1]" as="element(fr:view)?"/>
    <xsl:variable name="fluid"          select="$view/@fluid = 'true'"/>
    <xsl:variable name="body"           select="($view/fr:body, $view)[1]"           as="element()?"/>
    <xsl:variable name="custom-buttons" select="$view/fr:buttons"                    as="element()*"/>

    <!-- Template for the default layout of a form -->
    <xsl:variable name="default-page-template" as="element(*)*">
        <fr:navbar/>

        <fr:description/>

        <!-- Error summary (if at top) -->
        <xsl:if test="$error-summary-top">
            <fr:error-summary position="top"/>
        </xsl:if>

        <fr:row>
            <fr:toc/>
        </fr:row>
        <fr:row>
            <fr:body/>
        </fr:row>
        <fr:row>
            <fr:captcha/>
        </fr:row>

        <!-- Error summary (if at bottom) -->
        <!-- If we configuration tells us the bottom error summary should not be shown, still include it but hide it with 'display: none'.
             This is necessary because the persistence model relies on the error summary to know if the data is valid. -->
        <xh:div>
            <xsl:if test="not($error-summary-bottom)">
                <xsl:attribute name="style">display: none</xsl:attribute>
            </xsl:if>
            <fr:error-summary position="bottom"/>
        </xh:div>

        <fr:row>
            <fr:noscript-help/>
        </fr:row>
        <fr:row>
            <fr:messages/>
        </fr:row>
        <fr:row>
            <fr:buttons-bar/>
        </fr:row>
        <fr:row>
            <fr:version/>
        </fr:row>
    </xsl:variable>

    <xsl:template match="fr:row">
        <xh:div class="row{if ($fluid) then '-fluid' else ''}">
            <xh:div class="span12">
                <xsl:apply-templates select="@* | node()"/>
            </xh:div>
        </xh:div>
    </xsl:template>

    <xsl:template match="fr:body">
        <!-- Form content. Set context on form instance and define this group as #fr-form-group as observers will refer to it. -->
        <xf:group id="fr-form-group" class="fr-body{if ($is-detail) then ' fr-border' else ''}" model="fr-form-model" ref="instance('fr-form-instance')">
            <xh:a name="fr-form"/>
            <xsl:choose>
                <xsl:when test="not($mode = ('edit', 'new', 'test')) or $is-form-builder or $view-appearance = ('full', '')">
                    <!-- Just place the content as is -->
                    <xsl:apply-templates select="if ($body) then $body/(node() except fr:buttons) else node()"/>
                </xsl:when>
                <xsl:otherwise>
                    <!-- Insert appropriate XBL component -->
                    <!-- NOTE: Once we support XBL matching on @appearance, use instead
                         <fr:view appearance="{$view-appearance}">. -->
                    <xsl:element name="fr:{$view-appearance}">
                        <xsl:attribute name="id" select="concat('fr-view-', $view-appearance)"/>
                        <xsl:apply-templates select="if ($body) then $body/(node() except fr:buttons) else node()"/>
                        <!-- Optional inner buttons -->
                        <xsl:if test="exists($inner-buttons)">
                            <xsl:call-template name="fr-buttons-bar">
                                <xsl:with-param name="buttons-property"  select="'oxf.fr.detail.buttons.inner'" tunnel="yes"/>
                                <xsl:with-param name="highlight-primary" select="false()"        tunnel="yes"/>
                                <xsl:with-param name="inverse"           select="false()"        tunnel="yes"/>
                            </xsl:call-template>
                        </xsl:if>
                    </xsl:element>
                </xsl:otherwise>
            </xsl:choose>
            <!--<fr:xforms-inspector/>-->
        </xf:group>
    </xsl:template>

    <!-- Main entry point -->
    <xsl:template match="xh:body">
        <xsl:copy>
            <!-- .orbeon is here to scope all Orbeon CSS rules -->
            <xsl:attribute name="class" select="string-join(('orbeon', concat('xforms-', if ($is-inline-hints) then 'disable' else 'enable', '-hint-as-tooltip'), 'xforms-disable-alert-as-tooltip', @class), ' ')"/>
            <xsl:apply-templates select="@* except @class"/>
            <xf:group model="fr-form-model" id="fr-view" class="container{if ($fluid) then '-fluid' else ''} fr-view {{concat('fr-mode-', $fr-mode)}}" xxf:element="div">
                <xh:div class="popover-container-right"/>
                <xh:div class="popover-container-left"/>
                <xsl:apply-templates select="if ($is-detail and not($is-form-builder)) then $default-page-template else node()"/>
                <xsl:call-template name="fr-hidden-controls"/>
                <xsl:call-template name="fr-dialogs"/>
            </xf:group>
        </xsl:copy>
    </xsl:template>

    <xsl:template match="fr:navbar" name="fr-navbar">
        <xf:group
                xxf:element="div"
                model="fr-form-model"
                ref=".[not(xxf:property(string-join(('oxf.fr.detail.hide-header', $fr-app, $fr-form), '.')))]"
                class="navbar navbar-inverse">
            <xh:div class="navbar-inner">
                <xh:div class="container">
                    <xsl:variable name="default-objects" as="element()+">
                        <!-- These are typically to the right, but to help with IE7 we put them first. See:
                             https://github.com/orbeon/orbeon-forms/issues/721 -->
                        <fr:language-selector/>
                        <fr:noscript-selector/>
                        <fr:status-icons/>
                        <fr:goto-content/>
                        <!-- These are typically to the left -->
                        <fr:logo/>
                        <fr:title/>
                    </xsl:variable>

                    <xsl:apply-templates select="$default-objects"/>
                </xh:div>
            </xh:div>
        </xf:group>
    </xsl:template>

    <xsl:template match="fr:hidden-controls" name="fr-hidden-controls">
        <xh:span class="xforms-hidden">
            <!-- Hidden field to communicate to the client whether the data is safe -->
            <xf:input
                    model="fr-persistence-model"
                    ref="instance('fr-persistence-instance')/data-safe"
                    id="fr-data-safe-input"
                    class="xforms-hidden"/>

            <!-- Expose document id to JavaScript -->
            <xf:output id="fr-parameters-instance-document" ref="xxf:instance('fr-parameters-instance')/document" style="display: none"/>

            <!-- When the mode changes to "edit" after a save from /new, attempt to change the URL -->
            <xf:var name="mode-for-save" value="xxf:instance('fr-parameters-instance')/mode/string()">
                <!-- If URI is /new (it should be), change it to /edit/id -->
                <!-- If browser supporting the HTML5 history API (http://goo.gl/Ootqu) -->
                <xxf:script ev:event="xforms-value-changed" if="$mode-for-save = 'edit'">
                    <![CDATA[
                        if (history && history.replaceState) {
                            var NewSuffix = '/new';
                            var endsWithNew = location.pathname.indexOf(NewSuffix, location.pathname.length - NewSuffix.length) != -1;
                            if (endsWithNew) {
                                var documentId = ORBEON.xforms.Document.getValue("fr-parameters-instance-document");
                                var editSuffix = "edit/" +
                                    documentId +
                                    location.search +   // E.g. ?form-version=42
                                    location.hash;      // For now not used by Form Runner, but it is safer to keep it
                                history.replaceState(null, "", editSuffix);
                            }
                        }
                    ]]>
                </xxf:script>
            </xf:var>

            <!-- This is a HACK for Form Builder only: place non-relevant instances of all toolbox controls so that
                 xxf:dynamic will have all the JavaScript and CSS resources available on the client.
                 See: https://github.com/orbeon/orbeon-forms/issues/31 -->
            <xsl:if test="$is-form-builder and $is-detail" xmlns:p="http://www.orbeon.com/oxf/pipeline" xmlns:fb="http://orbeon.org/oxf/xml/form-builder">

                <xsl:variable name="property-names"
                              select="p:properties-start-with('oxf.fb.toolbox.group')" as="xs:string*" />
                <xsl:variable name="resources-names"
                              select="distinct-values(for $n in $property-names return p:split(p:property($n)))" as="xs:string*"/>

                <xsl:variable name="resources"
                              select="for $uri in $resources-names return doc($uri)" as="document-node()*"/>

                <xsl:if test="$resources">
                    <!-- Non-relevant group -->
                    <xf:group ref="()">
                        <xsl:apply-templates select="$resources/*/xbl:binding/fb:metadata/(fb:template | fb:templates/fb:view)/*" mode="filter-fb-template"/>
                        <!-- So that the xxbl:global for repeated sections is included -->
                        <fr:repeater/>
                    </xf:group>
                </xsl:if>
            </xsl:if>
        </xh:span>
    </xsl:template>

    <xsl:template match="fr:captcha" name="fr-captcha">
        <xsl:if test="$has-captcha">
            <xf:group id="fr-captcha-group" model="fr-persistence-model" ref=".[frf:showCaptcha()]" class="fr-captcha">
                <xf:var name="captcha" value="instance('fr-persistence-instance')/captcha"/>
                <!-- Success: remember the captcha passed, which also influences validity -->
                <xf:action ev:event="fr-verify-done">
                    <xf:setvalue ref="$captcha">true</xf:setvalue>
                    <xf:revalidate model="fr-persistence-model"/>
                    <xf:refresh/>
                </xf:action>
                <!-- Failure: load another challenge (supported by reCAPTCHA; SimpleCaptcha won't do anything) -->
                <xf:dispatch ev:event="fr-verify-error" if="event('fr-error-code') != 'empty'" targetid="captcha" name="fr-reload"/>
                <!-- Captcha component: either reCAPTCHA or SimpleCaptcha -->
                <xsl:if test="$captcha-type = 'reCAPTCHA'">
                    <fr:recaptcha id="captcha" theme="clean" ref="$captcha">
                        <xf:label ref="$fr-resources/detail/labels/captcha-label"/>
                        <xf:alert ref="$fr-resources/detail/labels/captcha-alert"/>
                    </fr:recaptcha>
                </xsl:if>
                <xsl:if test="$captcha-type = 'SimpleCaptcha'">
                    <fr:simple-captcha id="captcha" ref="$captcha">
                        <xf:label ref="$fr-resources/detail/labels/captcha-label"/>
                        <xf:alert ref="$fr-resources/detail/labels/captcha-alert"/>
                    </fr:simple-captcha>
                </xsl:if>
            </xf:group>
        </xsl:if>
    </xsl:template>

    <xsl:template match="fr:noscript-help" name="fr-noscript-help">
        <!-- Noscript help section (shown only in edit mode) -->
        <xsl:if test="$is-noscript and $mode = ('edit', 'new')">
            <!-- Only display this section if there is at least one non-empty nested help text -->
            <xf:group
                ref=".[normalize-space(string-join(({string-join(($body//(fr:section | xf:*)[@id]/xf:help/@ref), ',')}), ''))]">
                <xh:div class="xforms-help-panel">
                    <xh:h2>
                        <xf:output value="$fr-resources/summary/titles/help"/>
                    </xh:h2>
                    <xh:ul>
                        <xsl:apply-templates select="$body/*" mode="noscript-help"/>
                    </xh:ul>
                </xh:div>
            </xf:group>
        </xsl:if>
    </xsl:template>

    <!-- Remove id elements on Form Builder templates -->
    <xsl:template match="@id | @bind" mode="filter-fb-template"/>
    <xsl:template match="@ref[not(normalize-space())] | @nodeset[not(normalize-space())]" mode="filter-fb-template">
        <xsl:attribute name="{name()}" select="'()'"/>
    </xsl:template>

    <xsl:template match="fr:title" name="fr-title">
        <!-- Q: Why do we need @ref here? -->
        <xh:h1><xf:output value="{if (exists(@ref)) then @ref else '$title'}"/></xh:h1>
    </xsl:template>

    <!-- Description in chosen language or first one if not found -->
    <xsl:template match="fr:description" name="fr-description">
        <xf:var
            name="description"
            value="({if (@paths) then concat(@paths, ', ') else ''}xxf:instance('fr-form-metadata')/description[@xml:lang = xxf:instance('fr-language-instance')],
                        xxf:instance('fr-form-metadata')/description)[normalize-space()][1]"/>

        <xf:group xxf:element="div" ref=".[normalize-space($description)]" class="alert fr-form-description">
            <!-- Don't allow closing as that removes the markup and the XForms engine might attempt to update the nested
                 xf:output, which will cause an error. -->
            <xf:output value="$description"/>
        </xf:group>
    </xsl:template>

    <xsl:template match="fr:logo">
        <xsl:if test="not($hide-logo) and normalize-space($default-logo-uri)">
            <xh:img src="{$default-logo-uri}" alt=""/>
        </xsl:if>
    </xsl:template>

    <xsl:template match="fr:language-selector">
        <!-- Switch language -->
        <xf:group xxf:element="div" model="fr-form-model" ref=".[not($fr-mode = ('view', 'pdf', 'email'))]" class="fr-language-choice">
            <!-- Put default language first, then other languages -->
            <xf:var
                name="available-languages"
                model="fr-resources-model"
                value="frf:getFormLangSelection($app, $form, $fr-selector-resources/resource/@xml:lang/string())"/>

            <!-- Don't display language selector if there is only one language -->
            <!-- NOTE: Resolve model here, as for now model within XBL component won't resolve -->
            <xf:group id="fr-language-selector" model="fr-resources-model" ref=".[count($available-languages) gt 1 and normalize-space(xxf:get-request-header('orbeon-liferay-language')) = '']">
                <fr:link-select1 ref="$fr-selector-lang">
                    <xf:itemset ref="$available-languages">
                        <xf:label ref="(xxf:instance('fr-languages-instance')/language[@code = context()]/@native-name, context())[1]"/>
                        <xf:value ref="context()"/>
                    </xf:itemset>
                </fr:link-select1>
            </xf:group>
        </xf:group>
    </xsl:template>

    <xsl:template match="fr:noscript-selector">
        <!-- Switch script/noscript -->

        <!-- NOTE: without xml:space="preserve", XSLT strip spaces. This causes a CSS  bug with IE 7:
             https://github.com/orbeon/orbeon-forms/issues/733 -->
        <xf:group
            xxf:element="div"
            model="fr-form-model"
            ref=".[$fr-mode = ('new', 'edit') and not(xxf:property(string-join(('oxf.fr.noscript-link', $fr-app, $fr-form), '.')) = false()) and property('xxf:noscript-support')]"
            class="fr-noscript-choice"
            xml:space="preserve">

            <xf:group ref=".[not(property('xxf:noscript'))]">
                <xf:trigger appearance="minimal">
                    <xf:label xml:space="preserve">
                        <xf:output value="$fr-resources/summary/labels/noscript"/>
                    </xf:label>
                </xf:trigger>
            </xf:group>
            <xf:group ref=".[property('xxf:noscript')]">
                <xf:trigger appearance="minimal">
                    <xf:label xml:space="preserve">
                        <xf:output value="$fr-resources/summary/labels/script"/>
                    </xf:label>
                </xf:trigger>
            </xf:group>
            <!-- React to activation of both triggers -->
            <xf:action ev:event="DOMActivate" type="xpath" xmlns:process="java:org.orbeon.oxf.fr.process.SimpleProcess">
                process:runProcess('oxf.fr.detail.process', 'toggle-noscript')
            </xf:action>
        </xf:group>
    </xsl:template>

    <xsl:template match="fr:goto-content">
        <xf:group model="fr-form-model" ref=".[property('xxf:noscript')]">
            <!-- Group to scope variables -->
            <xf:group appearance="xxf:internal" model="fr-error-summary-model">
                <!-- Link to form content or to errors if any -->
                <xh:a class="fr-goto-content" href="#{{if (counts/@error gt 0) then 'fr-errors' else 'fr-form'}}">
                    <xf:output value="$fr-resources/summary/labels/goto-content"/>
                </xh:a>
            </xf:group>
        </xf:group>
    </xsl:template>

    <xsl:template match="fr:version">
        <xsl:if test="not($has-version = false())">
            <fr:row>
                <xsl:variable xmlns:version="java:org.orbeon.oxf.common.Version"
                    name="orbeon-forms-version"
                    select="version:getVersionString()"
                    as="xs:string"/>
                <xh:div class="fr-orbeon-version">
                    <xsl:value-of select="$orbeon-forms-version"/>
                </xh:div>
            </fr:row>
        </xsl:if>
    </xsl:template>

    <!-- Content handled separately -->
    <xsl:template match="fr:dialogs"/>

    <xsl:template name="fr-dialogs">
        <!-- Copy custom dialogs under fr:dialogs only (other dialogs will be left in place) -->
        <xsl:apply-templates select=".//fr:dialogs//xxf:dialog"/>

        <!-- Misc standard dialogs -->
        <xi:include href="oxf:/apps/fr/includes/clear-dialog.xhtml"      xxi:omit-xml-base="true"/>
        <xi:include href="oxf:/apps/fr/includes/draft-dialogs.xhtml"     xxi:omit-xml-base="true"/>
        <xi:include href="oxf:/apps/fr/includes/submission-dialog.xhtml" xxi:omit-xml-base="true"/>
        <xi:include href="oxf:/apps/fr/includes/validation-dialog.xhtml" xxi:omit-xml-base="true"/>

        <!-- Error dialog -->
        <fr:alert-dialog id="fr-error-dialog" close="true">
            <fr:label ref="$fr-resources/detail/messages/error-dialog-title"/>
            <fr:neutral-choice/>
        </fr:alert-dialog>

        <!-- Generic confirmation dialog (message must be passed dynamically) -->
        <fr:alert-dialog id="fr-confirmation-dialog" close="true">
            <fr:label ref="$fr-resources/detail/messages/confirmation-dialog-title"/>
            <fr:negative-choice>
                <xf:action event="DOMActivate" type="xpath" xmlns:process="java:org.orbeon.oxf.fr.process.SimpleProcess">
                    process:runProcess('oxf.fr.detail.process', 'abort')
                </xf:action>
            </fr:negative-choice>
            <fr:positive-choice>
                <xf:action event="DOMActivate" type="xpath" xmlns:process="java:org.orbeon.oxf.fr.process.SimpleProcess">
                    process:runProcess('oxf.fr.detail.process', 'resume')
                </xf:action>
            </fr:positive-choice>
        </fr:alert-dialog>

        <!-- Listen for upload events -->
        <xf:action
            ev:event="xxforms-upload-error"
            ev:observer="fr-form-group"
            ev:defaultAction="cancel"
            xxf:phantom="true">
            <xf:action type="xpath">
                frf:errorMessage(if (event('error-type') = 'size-error')
                                 then xxf:format-message(xxf:r('detail.messages.upload-error-size', 'fr-fr-resources'), (event('permitted'), event('actual')))
                                 else xxf:r(concat('detail.messages.', substring-after(event('xxf:type'), 'xxforms-')), 'fr-fr-resources'))
            </xf:action>
        </xf:action>

        <xf:action
            ev:event="xxforms-upload-done"
            ev:observer="fr-form-group"
            ev:defaultAction="cancel"
            xxf:phantom="true"
            type="xpath">
            frf:successMessage(xxf:r(concat('detail.messages.', substring-after(event('xxf:type'), 'xxforms-')), 'fr-fr-resources'))
        </xf:action>

    </xsl:template>

    <!-- Noscript section help entry -->
    <xsl:template match="fr:section[@id]" mode="noscript-help">
        <!-- Assumption: help referred to by xf:help/@ref and XPath expression is independent on the control's context (case of Form Builder-generated forms) -->

        <!-- Only display this <li> if there is at least one non-empty nested help text -->
        <xf:group
            ref=".[normalize-space(string-join(({string-join(((descendant-or-self::fr:section | .//xf:*)[@id]/xf:help/@ref), ',')}), ''))]">
            <xh:li class="xforms-help-group">
                <xf:var name="section-has-help" value="normalize-space({xf:help/@ref}) != ''" as="xs:boolean"/>
                <!-- Case where current section has help -->
                <xf:group ref=".[$section-has-help]">
                    <!-- Linked reachable from help icon -->
                    <xh:a name="{@id}$$p"/>
                    <!-- Label and help text -->
                    <xf:output class="xforms-label" value="{xf:label/@ref}"/>: <xf:output value="{xf:help/@ref}"/>
                    <!-- Link back to control -->
                    <xh:a href="#{@id}" class="fr-help-back"><xf:output value="$fr-resources/summary/labels/help-back"/></xh:a>
                </xf:group>
                <!-- Case where current section doesn't have help -->
                <xf:group ref=".[not($section-has-help)]">
                    <!-- Label -->
                    <xf:output class="xforms-label" value="{xf:label/@ref}"/>
                </xf:group>
                <!-- Recurse into nested controls if there is at least one non-empty nested help text -->
                <xf:group ref=".[normalize-space(string-join(({string-join((.//(fr:section | xf:*)[@id]/xf:help/@ref), ',')}), ''))]">
                    <xh:ul>
                        <xsl:apply-templates mode="#current"/>
                    </xh:ul>
                </xf:group>
            </xh:li>
        </xf:group>
    </xsl:template>

    <!-- Noscript control help entry -->
    <xsl:template match="xf:*[@id and xf:help]" mode="noscript-help">
        <xf:group ref=".[normalize-space(({xf:help/@ref})[1])]">
            <!-- (...)[1] protects against incorrect form where more than one node is returned -->
            <xh:li class="xforms-help-group">
                <!-- Linked reachable from help icon -->
                <xh:a name="{@id}$$p"/>
                <!-- Label and help text -->
                <xf:output class="xforms-label" value="{xf:label/@ref}"/>: <xf:output value="{xf:help/@ref}"/>
                <!-- Link back to the control -->
                <xh:a href="#{@id}" class="fr-help-back"><xf:output value="$fr-resources/summary/labels/help-back"/></xh:a>
            </xh:li>
        </xf:group>
    </xsl:template>

    <xsl:template match="node()" mode="noscript-help">
        <xsl:apply-templates mode="#current"/>
    </xsl:template>

    <!-- Error summary UI -->
    <xsl:template match="fr:error-summary" name="fr-error-summary">
        <xsl:param name="position" select="@position" as="xs:string"/>

        <!-- NOTE: We used to only handle events coming from controls bound to "fr-form-instance" instance, but this
             doesn't work with "section templates". We now use the observer mechanism of fr:error-summary -->

        <!-- For form builder we disable the error summary and say that the form is always valid -->
        <xsl:if test="not($is-form-builder)">
            <fr:row>
                <fr:error-summary
                        id="error-summary-control-{$position}"
                        observer="fr-form-group fr-captcha-group"
                        model="fr-error-summary-model"
                        alerts-count-ref="counts/@alert"
                        errors-count-ref="counts/@error"
                        warnings-count-ref="counts/@warning"
                        infos-count-ref="counts/@info"
                        visible-alerts-count-ref="visible-counts/@alert"
                        visible-errors-count-ref="visible-counts/@error"
                        visible-warnings-count-ref="visible-counts/@warning"
                        visible-infos-count-ref="visible-counts/@info"
                        valid-ref="valid">
                    <fr:label>
                        <!-- If there are e.g. some errors AND warnings, the formatter will display a generic word such as "message" -->
                        <xf:output
                            value="xxf:format-message(
                                       $fr-resources/errors/summary-title,
                                       (
                                           xxf:instance('fr-error-summary-instance')/visible-counts/(if (count((@error, @warning, @info)[. gt 0]) gt 1) then 3 else if (@error gt 0) then 0 else if (@warning gt 0) then 1 else if (@info gt 0) then 2 else 4),
                                           xxf:instance('fr-error-summary-instance')/visible-counts/xs:integer(@alert)
                                       )
                                   )"/>
                    </fr:label>
                    <xsl:if test="$position = 'bottom'">
                        <fr:header/>
                    </xsl:if>
                    <xsl:if test="$position = 'top'">
                        <fr:footer/>
                    </xsl:if>
                </fr:error-summary>
            </fr:row>
        </xsl:if>

    </xsl:template>

    <!-- Optional standard explanation message for view mode -->
    <xsl:template name="fr-explanation">
        <xf:group
                xxf:element="div"
                model="fr-form-model"
                ref=".[$fr-mode = 'view' and xxf:property(string-join(('oxf.fr.detail.view.show-explanation', $fr-app, $fr-form), '.')) = true()]"
                class="fr-explanation">
            <xf:output value="$fr-resources/detail/view/explanation"/>
        </xf:group>
    </xsl:template>

    <xsl:template match="fr:status-icons" name="fr-status-icons">
        <!-- Status icons for detail page -->

        <xf:group
            model="fr-form-model"
            ref=".[not($fr-mode = ('summary', 'home'))]"
            class="fr-status-icons">

            <xf:group model="fr-error-summary-model" ref=".[visible-counts/@alert gt 0]">
                <!-- Form has error or warning messages -->
                <xf:repeat ref="visible-counts/(@error, @warning, @info)[. gt 0]">
                    <xh:span class="badge badge-{{if (name() = 'error') then 'important' else if (name() = 'warning') then 'warning' else 'info'}}">
                        <xf:output value="."/>
                    </xh:span>
                </xf:repeat>
            </xf:group>
            <xf:group model="fr-error-summary-model" ref=".[visible-counts/@alert = 0]" class="fr-validity-icon">
                <!-- Form has no error or warning messages -->
                <xh:i class="icon-ok" title="{{$fr-resources/errors/none}}"/>
            </xf:group>
            <xf:group model="fr-persistence-model" ref="instance('fr-persistence-instance')[data-status = 'dirty']" class="fr-data-icon">
                <!-- Data is dirty -->
                <xh:i class="icon-hdd" title="{{$fr-resources/errors/unsaved}}"/>
            </xf:group>
        </xf:group>
    </xsl:template>

    <!-- Success messages -->
    <xsl:template match="fr:messages" name="fr-messages">
        <xf:switch class="fr-messages" model="fr-persistence-model" ref=".[instance('fr-persistence-instance')/message != '']">
            <xf:case id="fr-message-none">
                <xh:span/>
            </xf:case>
            <xf:case id="fr-message-success">
                <xf:output value="instance('fr-persistence-instance')/message" class="fr-message-success alert alert-success"/>
            </xf:case>
        </xf:switch>
    </xsl:template>

    <xsl:template match="fr:buttons-bar" name="fr-buttons-bar">

        <xsl:param name="buttons-property"  tunnel="yes" as="xs:string*"  select="()"/>
        <xsl:param name="highlight-primary" tunnel="yes" as="xs:boolean?" select="()"/>
        <xsl:param name="inverse"           tunnel="yes" as="xs:boolean?" select="()"/>

        <!-- Nothing below must statically depend on the mode -->
        <xsl:choose>
            <xsl:when test="exists($custom-buttons)">
                <xh:span class="fr-buttons">
                    <xsl:apply-templates select="$custom-buttons/node()"/>
                </xh:span>
            </xsl:when>
            <xsl:when test="not($hide-buttons-bar)">
                <xf:group model="fr-form-model" class="fr-buttons">

                    <xf:var name="buttons-property-override"  value="'{$buttons-property}'"/>
                    <xf:var name="highlight-primary-override" value="'{$highlight-primary}'"/>
                    <xf:var name="inverse-override"           value="'{$inverse}'"/>

                    <xf:var
                        name="highlight-primary"
                        value="if ($highlight-primary-override = '') then $fr-mode != 'test' else xs:boolean($highlight-primary-override)"/>
                    <xf:var
                        name="inverse"
                        value="if ($inverse-override = '')           then $fr-mode  = 'test' else xs:boolean($inverse-override)"/>

                    <xf:var
                        name="buttons-property"
                        value="if (normalize-space($buttons-property-override))
                               then $buttons-property-override
                               else if ($fr-mode = 'view')
                               then 'oxf.fr.detail.buttons.view'
                               else 'oxf.fr.detail.buttons'"/>

                    <xf:var
                        name="buttons-names"
                        value="if ($fr-mode = 'pdf')
                               then ()
                               else if ($fr-mode = 'test')
                               then 'validate'
                               else xxf:split(xxf:property(string-join(($buttons-property, $fr-app, $fr-form), '.')))"/>

                    <!-- Message shown next to the buttons (empty by default) -->
                    <xh:span class="fr-buttons-message">
                        <xf:output mediatype="text/html" ref="$fr-resources/detail/messages/buttons-message"/>
                    </xh:span>

                    <xf:repeat ref="$buttons-names">
                        <xf:var name="button-name" value="."/>
                        <xf:var name="pdf"         value="$button-name = 'pdf'"/>
                        <xf:var name="primary"     value="$highlight-primary and position() = last()"/>
                        <xf:var name="class"       value="concat('xforms-trigger-appearance-xxforms-',
                                                                 if ($primary) then 'primary'
                                                                 else if ($inverse) then 'inverse'
                                                                 else 'default')"/>
                        <xf:var name="ref"
                                value="if ($button-name = 'wizard-prev')
                                       then xxf:binding('fr-wizard-prev')
                                       else if ($button-name = 'wizard-next')
                                       then xxf:binding('fr-wizard-next')
                                       else xxf:instance('fr-triggers-instance')/*[name() = (
                                            if ($button-name = ('edit', 'workflow-edit'))
                                            then 'can-update'
                                            else if ($button-name = 'summary')
                                            then 'can-access-summary'
                                            else if ($button-name = 'refresh')
                                            then 'noscript'
                                            else if ($pdf)
                                            then 'pdf'
                                            else 'other')]"/>

                        <!-- Because @appearance is static, use a CSS class instead for primary/inverse. This requires
                             changes to form-runner-bootstrap-override.less, which is not the best solution. Ideally,
                             we could find a dynamic way to set that class on the nested <button> so that standard
                             Bootstrap rules apply. -->
                        <fr:process-button name="{{$button-name}}" ref="$ref[not($pdf)]" class="{{$class}}"/>
                        <xsl:variable name="pdf-button" as="element()">
                            <fr:pdf-button ref="$ref[$pdf]"  class="{{$class}}"/>
                        </xsl:variable>
                        <xsl:apply-templates select="$pdf-button"/>

                    </xf:repeat>

                </xf:group>
            </xsl:when>
            <xsl:otherwise/>
        </xsl:choose>
    </xsl:template>

    <!-- TOC: Top-level -->
    <xsl:template match="fr:toc" name="fr-toc">
        <!-- This is statically built in XSLT instead of using XForms -->
        <xsl:if test="$has-toc and $is-detail and not($is-form-builder) and count($body//fr:section) ge $min-toc">
            <xh:div class="fr-toc well sidebar-nav">
                <xh:ul class="nav nav-list">
                    <xh:li class="nav-header"><xf:output ref="$fr-resources/summary/titles/toc"/></xh:li>
                    <xsl:apply-templates select="$body" mode="fr-toc-sections"/>
                </xh:ul>
            </xh:div>
        </xsl:if>
    </xsl:template>

    <!-- TOC: Swallow unneeded nodes -->
    <xsl:template match="text()" mode="fr-toc-sections"/>

    <xsl:template match="*" mode="fr-toc-sections">
        <xsl:apply-templates mode="fr-toc-sections"/>
    </xsl:template>

    <!-- TOC: handle section -->
    <xsl:template match="fr:section" mode="fr-toc-sections">
        <xh:li xxf:control="xf:group">
            <!-- Propagate binding so that entry for section disappears if the section is non-relevant -->
            <xsl:copy-of select="@model | @context | @bind | @ref"/>
            <!-- Clicking sets the focus -->
            <xf:trigger appearance="minimal">
                <xf:label value="xxf:label('{@id}')"/>
                <xf:setfocus ev:event="DOMActivate" control="{@id}" input-only="true"/>
            </xf:trigger>
            <!-- Sub-sections if any -->
            <xsl:if test="exists(fr:section)">
                <xh:ol>
                    <xsl:apply-templates mode="fr-toc-sections"/>
                </xh:ol>
            </xsl:if>
        </xh:li>
    </xsl:template>

    <!-- Add a default xf:alert for those fields which don't have one. Only do this within grids and dialogs. -->
    <!-- Q: Do we really need this? -->
    <xsl:template
        match="xh:body//fr:grid//xf:*[local-name() = ('input', 'textarea', 'select', 'select1', 'upload', 'secret') and not(xf:alert)]
                       | xh:body//xxf:dialog//xf:*[local-name() = ('input', 'textarea', 'select', 'select1', 'upload', 'secret') and not(xf:alert)]">
        <xsl:copy>
            <xsl:apply-templates select="@* | node()"/>
            <xf:alert ref="$fr-resources/detail/labels/alert"/>
        </xsl:copy>
    </xsl:template>

</xsl:stylesheet>
