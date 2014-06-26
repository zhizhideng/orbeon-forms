/**
 *  Copyright (C) 2004 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.processor;

import org.orbeon.oxf.pipeline.api.PipelineContext;

/**
 * This class is called back from ProcessorImpl.readCacheInputAsObject().
 */
public abstract class CacheableInputReader<T> {
    /**
     * Method called back when it is necessary to read the input. It may not be called when the input data is already
     * in cache.
     *
     * @param pipelineContext   current PipelineContext
     * @param processorInput    input being read
     * @return                  object created from the input
     */
    public abstract T read(PipelineContext pipelineContext, ProcessorInput input);

    /**
     * Method called back when the input data is already in cache. If this is called, read() won't be called.
     */
    public void foundInCache() {}

    /**
     * Method called back after read() to check whether the result can be stored in cache.
     */
    public boolean allowCaching() { return true; }

    /**
     * Method called back when the input data has been cached after a call to read().
     */
    public void storedInCache() {}
}
