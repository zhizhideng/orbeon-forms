/**
 * Copyright (C) 2010 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.state

import org.orbeon.oxf.cache._
import org.orbeon.oxf.xforms.XFormsStaticState

object XFormsStaticStateCache {

    trait CacheTracer {
        def digestAndTemplateStatus(digestIfFound: Option[String])
        def staticStateStatus(found: Boolean, digest: String)
    }

    def storeDocument(staticState: XFormsStaticState): Unit =
        cache.add(createCacheKey(staticState.digest), ConstantValidity, staticState)

    def getDocumentJava(digest: String) =
        findDocument(digest).orNull

    def findDocument(digest: String) =
        Option(cache.findValid(createCacheKey(digest), ConstantValidity).asInstanceOf[XFormsStaticState])

    private def createCacheKey(digest: String) =
        new InternalCacheKey(ContainingDocumentKeyType, digest ensuring (_ ne null))

    private val XFormsDocumentCache = "xforms.cache.static-state"
    private val XFormsDocumentCacheDefaultSize = 50
    private val ConstantValidity = 0L
    private val ContainingDocumentKeyType = XFormsDocumentCache

    private val cache = ObjectCache.instance(XFormsDocumentCache, XFormsDocumentCacheDefaultSize)
}
