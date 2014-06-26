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

import org.orbeon.oxf.cache.OutputCacheKey;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.xml.XMLReceiver;

public interface ProcessorOutput extends ProcessorReader, ProcessorInputOutput {

    String getId();
    Processor getProcessor(PipelineContext pipelineContext);

    void setInput(ProcessorInput processorInput);
    ProcessorInput getInput();

    void read(PipelineContext pipelineContext, XMLReceiver xmlReceiver);
    OutputCacheKey getKey(PipelineContext pipelineContext);
    Object getValidity(PipelineContext pipelineContext);
}
