/**
 * Copyright (C) 2011 Orbeon, Inc.
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
package org.orbeon.oxf.fr

import FormRunner._
import collection.JavaConverters._
import org.dom4j.{Document ⇒ JDocument}
import org.junit.Test
import org.orbeon.oxf.test.DocumentTestBase
import org.orbeon.oxf.util.NetUtils
import org.orbeon.oxf.xforms.XFormsUtils._
import org.orbeon.oxf.xforms.action.XFormsAPI._
import org.orbeon.oxf.xml.TransformerUtils
import org.scalatest.junit.AssertionsForJUnit
import org.orbeon.scaxon.XML._
import org.orbeon.oxf.xml.Dom4j.elemToDocument

class FormRunnerFunctionsTest extends DocumentTestBase with AssertionsForJUnit {

    @Test def persistenceHeaders(): Unit = {

        val obf = getPersistenceHeadersAsXML("cities", "form1", "form")
        assert(TransformerUtils.tinyTreeToString(obf) ===
            """<headers><header><name>Orbeon-City-Uri</name><value>http://en.wikipedia.org/wiki/Mexico_City</value></header><header><name>Orbeon-City-Name</name><value>Mexico City</value></header><header><name>Orbeon-Population</name><value>8851080</value></header></headers>""")

        val obd = getPersistenceHeadersAsXML("cities", "form1", "data")
        assert(TransformerUtils.tinyTreeToString(obd) ===
            """<headers><header><name>Orbeon-City-Uri</name><value>http://en.wikipedia.org/wiki/S%C3%A3o_Paulo</value></header><header><name>Orbeon-City-Name</name><value>São Paulo</value></header><header><name>Orbeon-Population</name><value>11244369</value></header></headers>""")
    }

    @Test def language(): Unit = {

        val app  = "acme"
        val form = "order"

        // oxf.fr.default-language not set so "en" is the default
        assert("en" === getDefaultLang(getAppForm(app, form)))

        // oxf.fr.available-languages not set so all languages are allowed
        assert(isAllowedLang(getAppForm(app, form))("en"))
        assert(isAllowedLang(getAppForm(app, form))("foo"))

        // Requested language
        assert(Some("en") === findRequestedLang(getAppForm(app, form), null))
        assert(Some("en") === findRequestedLang(getAppForm(app, form), "   "))

        assert(Some("es") === findRequestedLang(getAppForm(app, form), "es"))
        assert(Some("en") === findRequestedLang(getAppForm(app, form), "en"))

        NetUtils.getExternalContext.getRequest.getSession(true).getAttributesMap.put("fr-language", "fr")

        assert(Some("fr") === findRequestedLang(getAppForm(app, form), null))
        assert(Some("it") === findRequestedLang(getAppForm(app, form), "it"))

        // Language selector
        assert(Seq("en", "fr", "it") === getFormLangSelection(app, form, Seq("fr", "it", "en").asJava).asScala)
        assert(Seq("fr", "it", "es") === getFormLangSelection(app, form, Seq("fr", "it", "es").asJava).asScala)
        assert(Seq.empty[String]     === getFormLangSelection(app, form, Seq.empty[String].asJava).asScala)

        // Select form language
        assert("it" === selectFormLang(app, form, "it", Seq("fr", "it", "en").asJava))
        assert("en" === selectFormLang(app, form, "zh", Seq("fr", "it", "en").asJava))
        assert("fr" === selectFormLang(app, form, "zh", Seq("fr", "it", "es").asJava))
        assert(null eq  selectFormLang(app, form, "fr", Seq.empty[String].asJava))

        // Select Form Runner language
        assert("it" === selectFormRunnerLang(app, form, "it", Seq("fr", "it", "en").asJava))
        assert("en" === selectFormRunnerLang(app, form, "zh", Seq("fr", "it", "en").asJava))
        assert("fr" === selectFormRunnerLang(app, form, "zh", Seq("fr", "it", "es").asJava))
    }


    @Test def errorSummarySortString(): Unit = {

        def source: JDocument =
            <xh:html xmlns:xh="http://www.w3.org/1999/xhtml" xmlns:xf="http://www.w3.org/2002/xforms">
                <xh:head>
                    <xf:model>
                        <xf:instance>
                            <data>
                                <c1/>
                                <c2/>
                                <c3>
                                    <c4/>
                                    <c5/>
                                    <c6>
                                        <c7/>
                                        <c8/>
                                    </c6>
                                    <c6>
                                        <c7/>
                                        <c8/>
                                    </c6>
                                    <c9/>
                                    <c10/>
                                </c3>
                                <c3>
                                    <c4/>
                                    <c5/>
                                    <c6>
                                        <c7/>
                                        <c8/>
                                    </c6>
                                    <c6>
                                        <c7/>
                                        <c8/>
                                    </c6>
                                    <c9/>
                                    <c10/>
                                </c3>
                                <c11/>
                                <c12/>
                            </data>
                        </xf:instance>
                    </xf:model>
                </xh:head>
                <xh:body>
                    <xf:input id="c1" ref="c1"/>
                    <xf:input id="c2" ref="c2"/>
                    <xf:repeat id="c3" ref="c3">
                        <xf:input id="c4" ref="c4"/>
                        <xf:input id="c5" ref="c5"/>
                        <xf:repeat id="c6" ref="c6">
                            <xf:input id="c7" ref="c7"/>
                            <xf:input id="c8" ref="c8"/>
                        </xf:repeat>
                        <xf:input id="c9" ref="c9"/>
                        <xf:input id="c10" ref="c10"/>
                    </xf:repeat>
                    <xf:input id="c11" ref="c11"/>
                    <xf:input id="c12" ref="c12"/>
                </xh:body>
            </xh:html>

        withActionAndDoc(setupDocument(source)) {

            val doc = containingDocument

            val controlIds     = 1 to 12 map ("c" +)
            val controlIndexes = controlIds map doc.getStaticOps.getControlPosition

            // Static control position follows source document order
            assert(controlIndexes.sorted === controlIndexes)

            val effectiveAbsoluteIds =
                doc.getControls.getCurrentControlTree.getEffectiveIdsToControls.asScala map
                { case (id, _) ⇒ effectiveIdToAbsoluteId(id) } toList

            val sortStrings =
                effectiveAbsoluteIds map (controlSortString(_, 3))

            // Effective sort strings follow document order
            assert(sortStrings.sorted === sortStrings)
        }
    }
}