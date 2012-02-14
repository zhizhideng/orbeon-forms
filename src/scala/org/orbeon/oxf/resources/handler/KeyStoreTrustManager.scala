/**
 * Copyright (C) 2012 Orbeon, Inc.
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
package org.orbeon.oxf.resources.handler


import java.security.cert.X509Certificate
import java.security.KeyStore
import javax.net.ssl.{TrustManagerFactory, X509TrustManager}
import org.orbeon.oxf.common.OXFException
import java.io.InputStream

// Our own trust manager based on the specified key store
class KeyStoreTrustManager(is: InputStream, password: String) extends X509TrustManager {

    private val trustManager =
        {
            val keyStore = KeyStore.getInstance("JKS")
            keyStore.load(is, password.toCharArray)

            val trustManagerFactory = TrustManagerFactory.getInstance("PKIX")
            trustManagerFactory.init(keyStore)

            val x509TrustManagerOption =
                trustManagerFactory.getTrustManagers collectFirst
                    { case tm: X509TrustManager ⇒ tm }

            x509TrustManagerOption getOrElse
                (throw new OXFException("Couldn't initialize trust manager"))
        }

    // Delegate
    def checkClientTrusted(chain: Array[X509Certificate], authType: String) = trustManager.checkClientTrusted _
    def checkServerTrusted(chain: Array[X509Certificate], authType: String) = trustManager.checkServerTrusted _
    def getAcceptedIssuers = trustManager.getAcceptedIssuers
}