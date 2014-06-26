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

import org.orbeon.oxf.test.DocumentTestBase
import org.scalatest.junit.AssertionsForJUnit
import org.junit.Test
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.oxf.fb.FormBuilder._
import org.orbeon.scaxon.XML._

class PermissionsTest extends DocumentTestBase with AssertionsForJUnit {

    @Test def fbPermissions(): Unit = {

        val frRoles: NodeInfo =
            <roles>
                <role name="*"                  app="app-always" form="form-always"/>
                <role name="all-forms-role"     app="*"          form="*"/>
                <role name="all-foo-forms-role" app="foo"        form="*"/>
                <role name="foo-baz-role"       app="foo"        form="baz"/>
                <role name="bar-baz-role"       app="bar"        form="baz"/>
                <role name="bar-baz2-role"      app="bar"        form="baz2"/>
            </roles>

        // Test inclusion of form that is always permitted
        val always = Map("app-always" → Set("form-always"))

        assert(formBuilderPermissions(frRoles, Set("some", "other"))                         === always)
        assert(formBuilderPermissions(frRoles, Set("all-foo-forms-role"))                    === always + ("foo" → Set("*")))
        assert(formBuilderPermissions(frRoles, Set("bar-baz-role"))                          === always + ("bar" → Set("baz")))
        assert(formBuilderPermissions(frRoles, Set("all-foo-forms-role", "bar-baz-role"))    === always + ("foo" → Set("*")) + ("bar" → Set("baz")))

        // Test match for all roles
        val all = Map("*" → Set("*"))

        assert(formBuilderPermissions(frRoles, Set("all-forms-role"))                        === all)
        assert(formBuilderPermissions(frRoles, Set("all-forms-role", "some", "other"))       === all)
        assert(formBuilderPermissions(frRoles, Set("all-forms-role", "all-foo-forms-role"))  === all)
        assert(formBuilderPermissions(frRoles, Set("all-forms-role", "bar-baz-role"))        === all)

        // Combine roles with wildcard and specific app
        assert(formBuilderPermissions(frRoles, Set("all-foo-forms-role", "foo-baz-role"))    === always + ("foo" → Set("*")))

        // Different baz forms
        assert(formBuilderPermissions(frRoles, Set("foo-baz-role", "bar-baz-role"))          === always + ("foo" → Set("baz")) + ("bar" → Set("baz")))

        // Multiple forms per app
        assert(formBuilderPermissions(frRoles, Set("bar-baz-role", "bar-baz2-role"))         === always + ("bar" → Set("baz", "baz2")))

        // Empty roles
        val emptyRoles: NodeInfo = <roles/>
        assert(formBuilderPermissions(emptyRoles, Set("some")) === Map())
    }
}
