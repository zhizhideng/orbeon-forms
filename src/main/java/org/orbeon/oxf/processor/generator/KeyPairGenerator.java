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
package org.orbeon.oxf.processor.generator;

import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.xml.XMLReceiver;
import org.orbeon.oxf.processor.*;
import org.orbeon.oxf.util.Base64;
import org.orbeon.oxf.xml.XMLUtils;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

public class KeyPairGenerator extends ProcessorImpl {

    public KeyPairGenerator() {
        addOutputInfo(new org.orbeon.oxf.processor.ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    @Override
    public ProcessorOutput createOutput(String name) {
        final ProcessorOutput output = new ProcessorOutputImpl(KeyPairGenerator.this, name) {
            public void readImpl(PipelineContext context, XMLReceiver xmlReceiver) {

                try {
                    final java.security.KeyPairGenerator keyGen = java.security.KeyPairGenerator.getInstance("DSA", "SUN");
                    final SecureRandom random = SecureRandom.getInstance("SHA1PRNG", "SUN");
                    keyGen.initialize(1024, random);

                    final KeyPair pair = keyGen.generateKeyPair();
                    final PrivateKey priv = pair.getPrivate();
                    final PublicKey pub = pair.getPublic();

                    final String pubKey = Base64.encode(new X509EncodedKeySpec(pub.getEncoded()).getEncoded(), true);
                    final String privKey = Base64.encode(new PKCS8EncodedKeySpec(priv.getEncoded()).getEncoded(), true);

                    xmlReceiver.startDocument();

                    xmlReceiver.startElement("", "key-pair", "key-pair", XMLUtils.EMPTY_ATTRIBUTES);

                    xmlReceiver.startElement("", "private-key", "private-key", XMLUtils.EMPTY_ATTRIBUTES);
                    final char[] privKeyChar = new char[privKey.length()];
                    privKey.getChars(0, privKey.length(), privKeyChar, 0);
                    xmlReceiver.characters(privKeyChar, 0, privKeyChar.length);
                    xmlReceiver.endElement("", "private-key", "private-key");

                    xmlReceiver.startElement("", "public-key", "public-key", XMLUtils.EMPTY_ATTRIBUTES);
                    final char[] pubKeyChar = new char[pubKey.length()];
                    pubKey.getChars(0, pubKey.length(), pubKeyChar, 0);
                    xmlReceiver.characters(pubKeyChar, 0, pubKeyChar.length);
                    xmlReceiver.endElement("", "public-key", "public-key");

                    xmlReceiver.endElement("", "key-pair", "key-pair");

                    xmlReceiver.endDocument();
                } catch (Exception e) {
                    throw new OXFException(e);
                }
            }
        };

        addOutput(name, output);
        return output;
    }
}
