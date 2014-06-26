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
package org.orbeon.oxf.processor;

import org.apache.commons.lang3.StringUtils;
import org.dom4j.Document;
import org.dom4j.Node;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.xml.XMLReceiver;
import org.orbeon.oxf.util.Base64;
import org.orbeon.oxf.xml.XPathUtils;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.LocationSAXWriter;
import org.orbeon.oxf.xml.dom4j.NonLazyUserDataDocument;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.X509EncodedKeySpec;

public class SignatureVerifierProcessor extends ProcessorImpl {

    public static final String SIGNATURE_DATA_URI = "http://www/orbeon.com/oxf/signature";
    public static final String SIGNATURE_PUBLIC_KEY_URI = "http://www/orbeon.com/oxf/signature/public-key";

    public static final String INPUT_PUBLIC_KEY = "public-key";

    public SignatureVerifierProcessor() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_DATA, SIGNATURE_DATA_URI));
        addInputInfo(new ProcessorInputOutputInfo(INPUT_PUBLIC_KEY, SIGNATURE_PUBLIC_KEY_URI));
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA));
    }


    public ProcessorOutput createOutput(String name) {
        final ProcessorOutput output = new ProcessorOutputImpl(SignatureVerifierProcessor.this, name) {
            public void readImpl(PipelineContext context, final XMLReceiver xmlReceiver) {
                try {
                    final Document pubDoc = readCacheInputAsDOM4J(context, INPUT_PUBLIC_KEY);
                    final String pubString = XPathUtils.selectStringValueNormalize(pubDoc, "/public-key");
                    final byte[] pubBytes = Base64.decode(pubString);
                    final X509EncodedKeySpec pubKeySpec = new X509EncodedKeySpec(pubBytes);
                    final KeyFactory keyFactory = KeyFactory.getInstance("DSA");
                    final PublicKey pubKey = keyFactory.generatePublic(pubKeySpec);

                    final Signature dsa = Signature.getInstance("SHA1withDSA");
                    dsa.initVerify(pubKey);

                    final Document data = readInputAsDOM4J(context, INPUT_DATA);
                    final Node sigDataNode = data.selectSingleNode("/signed-data/data/*");
                    final String sig = StringUtils.trimToEmpty(XPathUtils.selectStringValue(data, "/signed-data/signature"));

                    sigDataNode.detach();
                    final Document sigData = new NonLazyUserDataDocument();
                    sigData.add(sigDataNode);

                    dsa.update(Dom4jUtils.domToString(sigData).getBytes("utf-8"));

                    // Verify signature and throw in case of failure
                    try {
                        if (! dsa.verify(Base64.decode(sig)))
                            throw new OXFException("Signature verification failed");
                    } catch (SignatureException e) {
                        throw e;
                    } catch (Exception e) {
                        // A number of things can fail above, including Base64 decoding
                        // NOTE: We don't pas the cause so that we can match on SignatureException as root Exception
                        throw new SignatureException("Signature verification failed");
                    }

                    // Signature verification passed
                    final LocationSAXWriter saw = new LocationSAXWriter();
                    saw.setContentHandler(xmlReceiver);
                    saw.write(sigData);
                } catch (Exception e) {
                    throw new OXFException(e);
                }
            }
        };
        addOutput(name, output);
        return output;
    }
}