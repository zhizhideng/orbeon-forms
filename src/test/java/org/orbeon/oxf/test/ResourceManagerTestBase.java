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
package org.orbeon.oxf.test;

import org.apache.log4j.Logger;
import org.dom4j.Document;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.ProcessorUtils;
import org.orbeon.oxf.processor.XMLProcessorRegistry;
import org.orbeon.oxf.processor.generator.DOMGenerator;
import org.orbeon.oxf.processor.test.TestExternalContext;
import org.orbeon.oxf.resources.ResourceManagerWrapper;
import org.orbeon.oxf.util.IndentedLogger;
import org.orbeon.oxf.util.LoggerFactory;
import org.orbeon.oxf.util.PipelineUtils;
import org.orbeon.oxf.xml.XMLUtils;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;


public abstract class ResourceManagerTestBase {

    public static final Logger logger = LoggerFactory.createLogger(ResourceManagerTestBase.class);

    public static IndentedLogger newIndentedLogger() {
        return new IndentedLogger(logger, true, "test");
    }

    public ResourceManagerTestBase() {}

    private static boolean staticSetupDone;

    @BeforeClass
    public static void staticSetup() throws Exception {
        if (!staticSetupDone) {

            // Avoid Log4j warning telling us no appender could be found
            LoggerFactory.initBasicLogger();

            // Setup resource manager
            final Map<String, Object> props = new HashMap<String, Object>();
            final java.util.Properties properties = System.getProperties();
            for (Enumeration e = properties.propertyNames(); e.hasMoreElements();) {
                final String name = (String) e.nextElement();
                if (name.startsWith("oxf.resources."))
                    props.put(name, properties.getProperty(name));
            }
            ResourceManagerWrapper.init(props);
            // Initialize properties
            org.orbeon.oxf.properties.Properties.init("oxf:/ops/unit-tests/properties.xml");

            // Initialize logger
            LoggerFactory.initLogger();

            // Run processor registry so we can use XPL
            final XMLProcessorRegistry registry = new XMLProcessorRegistry();
            final String processorsXML = "processors.xml";
            final Document doc = ResourceManagerWrapper.instance().getContentAsDOM4J(processorsXML, XMLUtils.ParserConfiguration.XINCLUDE_ONLY, true);
            final DOMGenerator config = PipelineUtils.createDOMGenerator(doc, processorsXML, DOMGenerator.ZeroValidity, processorsXML);
            PipelineUtils.connect(config, "data", registry, "config");
            registry.start(new PipelineContext());

            staticSetupDone  = true;
        }
	}

    private PipelineContext pipelineContext;

    @Before
    public void setUp() {
        this.pipelineContext = createPipelineContextWithExternalContext();
    }

    @After
    public void tearDown() {
        if (pipelineContext != null)
            pipelineContext.destroy(true);
    }

    protected PipelineContext createPipelineContextWithExternalContext() {
        return createPipelineContextWithExternalContext("oxf:/org/orbeon/oxf/default-request.xml");
    }

    protected PipelineContext createPipelineContextWithExternalContext(String requestURL) {
        final PipelineContext pipelineContext = new PipelineContext();
        final Document requestDocument = ProcessorUtils.createDocumentFromURL(requestURL, null);
        final ExternalContext externalContext = new TestExternalContext(pipelineContext, requestDocument);
        pipelineContext.setAttribute(PipelineContext.EXTERNAL_CONTEXT, externalContext);

        return pipelineContext;
    }
}
