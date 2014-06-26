/**
 * Copyright (C) 2014 Orbeon, Inc.
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
package org.orbeon.oxf.fr.relational.crud

import org.junit.Test
import org.orbeon.oxf.test.{TestSupport, ResourceManagerTestBase}
import org.scalatest.junit.AssertionsForJUnit
import org.orbeon.oxf.resources.ResourceManagerWrapper
import org.orbeon.oxf.xml.Dom4j._
import org.orbeon.oxf.xml.dom4j.Dom4jUtils
import org.dom4j.Document

class MetadataTest extends ResourceManagerTestBase with AssertionsForJUnit with TestSupport {

    @Test def extractMetadata(): Unit = {

        val is = ResourceManagerWrapper.instance.getContentAsStream("/org/orbeon/oxf/fb/form-with-metadata.xhtml")

        val (_, metadataOpt) = RequestReader.dataAndMetadataAsString(is, metadata = true)

        val expected: Document =
            <metadata>
                <application-name>acme</application-name>
                <form-name>order</form-name>
                <title xml:lang="en">ACME Order Form</title>
                <description xml:lang="en">This is a form to order new stuff from ACME, Inc.</description>
                <title xml:lang="fr">Formulaire de commande ACME</title>
                <description xml:lang="fr">Ceci est un formulaire de commande pour ACME, Inc.</description>
                <permissions>
                    <permission operations="read update delete">
                        <group-member/>
                    </permission>
                    <permission operations="read update delete">
                        <owner/>
                    </permission>
                    <permission operations="create"/>
                </permissions>
            </metadata>

        assertXMLDocumentsIgnoreNamespacesInScope(expected, metadataOpt map Dom4jUtils.readDom4j get)
    }
}
