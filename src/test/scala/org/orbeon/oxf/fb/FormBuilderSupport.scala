/**
 * Copyright (C) 2013 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.fb

import org.orbeon.oxf.test.{XFormsSupport, DocumentTestBase}
import org.orbeon.saxon.dom4j.DocumentWrapper
import org.orbeon.scaxon.XML._
import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xml.dom4j.Dom4jUtils
import org.orbeon.oxf.xml.TransformerUtils
import org.orbeon.saxon.om.NodeInfo

trait FormBuilderSupport extends XFormsSupport {

    self: DocumentTestBase ⇒

    val TemplateDoc = "oxf:/forms/orbeon/builder/form/template.xml"

    // Run the body in the action context of a form which simulates the main Form Builder model
    def withActionAndFBDoc[T](url: String)(body: DocumentWrapper ⇒ T): T =
        withActionAndFBDoc(formBuilderContainingDocument(url))(body)

    private def formBuilderContainingDocument(url: String) =
        setupDocument(formBuilderDoc(url))

    def withActionAndFBDoc[T](doc: XFormsContainingDocument)(body: DocumentWrapper ⇒ T): T = {
        withActionAndDoc(doc) {
            body(
                doc.models
                find    (_.getId == "fr-form-model")
                flatMap (m ⇒ Option(m.getInstance("fb-form-instance")))
                map     (_.documentInfo.asInstanceOf[DocumentWrapper])
                orNull
            )
        }
    }

    def prettyPrintElem(elem: NodeInfo): Unit =
        println(Dom4jUtils.domToPrettyString(TransformerUtils.tinyTreeToDom4j(elem)))

    private def formBuilderDoc(url: String) =
        elemToDom4j(
            <xh:html xmlns:xh="http://www.w3.org/1999/xhtml"
                     xmlns:xf="http://www.w3.org/2002/xforms"
                     xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
                     xmlns:ev="http://www.w3.org/2001/xml-events"
                     xmlns:xbl="http://www.w3.org/ns/xbl">
                <xh:head>
                    <xf:model id="fr-form-model">
                        <xf:instance id="fb-form-instance"  xxf:index="id"><dummy/></xf:instance>
                        <xf:instance id="fr-form-instance"  src={url}/>
                        <xf:instance id="fr-form-resources" src="oxf:/forms/orbeon/builder/form/resources.xml"
                                     xxf:readonly="true" xxf:cache="true"/>

                        <xf:var name="model"             value="xh:head/xf:model[@id = 'fr-form-model']"/>
                        <xf:var name="metadata-instance" value="$model/xf:instance[@id = 'fr-form-metadata']/*"/>
                        <xf:var name="resources"         value="$model/xf:instance[@id = 'fr-form-resources']/*"/>
                        <xf:var name="current-resources" value="$resources/resource[1]"/>

                        <xf:instance id="fb-variables">
                            <variables>
                                <selected-cell/>
                            </variables>
                        </xf:instance>

                        <xf:var name="variables"     value="instance('fb-variables')"/>
                        <xf:var name="selected-cell" value="$variables/selected-cell"/>

                        <xf:instance id="fb-components-instance">
                            <components/>
                        </xf:instance>

                        <xf:var name="component-bindings" value="instance('fb-components-instance')//xbl:binding"/>

                        <xf:action ev:event="xforms-model-construct-done">
                            <!-- Load components -->
                            <xf:insert context="instance('fb-components-instance')"
                                       origin="xxf:call-xpl('oxf:/org/orbeon/oxf/fb/simple-toolbox.xpl', (), (), 'data')"/>

                            <!-- First store into a temporary document so that multiple inserts won't cause repeat processing until we are done -->
                            <xf:var name="temp" value="xxf:create-document()"/>
                            <xf:insert context="$temp"
                                       origin="xxf:call-xpl('oxf:/forms/orbeon/builder/form/annotate.xpl',
                                                                ('data', 'bindings'),
                                                                (instance('fr-form-instance'), instance('fb-components-instance')),
                                                                'data')"/>

                            <xf:action type="xpath" xmlns:fbf="java:org.orbeon.oxf.fb.FormBuilder">
                                fbf:initializeGrids($temp)
                            </xf:action>

                            <xf:insert ref="instance('fb-form-instance')" origin="$temp"/>
                        </xf:action>
                    </xf:model>
                    <xf:model id="fr-resources-model">
                        <xf:var name="fr-form-resources" value="xxf:instance('fr-form-resources')/resource[@xml:lang = 'en']"/>
                    </xf:model>
                </xh:head>
                <xh:body>
                </xh:body>
            </xh:html>)
}
