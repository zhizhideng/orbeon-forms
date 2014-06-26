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
package org.orbeon.oxf.xforms.submission;

import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.externalcontext.LocalRequest;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.util.Connection;
import org.orbeon.oxf.util.ConnectionResult;
import org.orbeon.oxf.util.IndentedLogger;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.XFormsProperties;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xforms.event.events.XFormsSubmitErrorEvent;
import org.orbeon.oxf.xml.XMLUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.concurrent.Callable;

public abstract class BaseSubmission implements Submission {

    protected final XFormsModelSubmission submission;
    protected final XFormsContainingDocument containingDocument;

    protected BaseSubmission(XFormsModelSubmission submission) {
        this.submission = submission;
        this.containingDocument = submission.containingDocument();
    }

    protected String getAbsoluteSubmissionURL(String resolvedActionOrResource, String queryString, boolean isNorewrite) {

        if ("resource".equals(submission.getUrlType())) {
            // In case, for some reason, author forces a resource URL

            // NOTE: Before 2009-10-08, there was some code performing custom rewriting in portlet mode. That code was
            // very unclear and was removed as it seemed like resolveResourceURL() should handle all cases.

            return XFormsUtils.resolveResourceURL(containingDocument, submission.getSubmissionElement(),
                    NetUtils.appendQueryString(resolvedActionOrResource, queryString),
                    isNorewrite ? ExternalContext.Response.REWRITE_MODE_ABSOLUTE_NO_CONTEXT : ExternalContext.Response.REWRITE_MODE_ABSOLUTE);
        } else {
            // Regular case of service URL

            // NOTE: If the resource or service URL does not start with a protocol or with '/', the URL is resolved against
            // the request path, then against the service base. Example in servlet environment:
            //
            // - action path: my/service
            // - request URL: http://orbeon.com/orbeon/myapp/mypage
            // - request path: /myapp/mypage
            // - service base: http://services.com/myservices/
            // - resulting service URL: http://services.com/myservices/myapp/my/service

            return XFormsUtils.resolveServiceURL(containingDocument, submission.getSubmissionElement(),
                    NetUtils.appendQueryString(resolvedActionOrResource, queryString),
                    isNorewrite ? ExternalContext.Response.REWRITE_MODE_ABSOLUTE_NO_CONTEXT : ExternalContext.Response.REWRITE_MODE_ABSOLUTE);
        }
    }

    /**
     * Submit the Callable for synchronous or asynchronous execution.
     *
     * @param p                 parameters
     * @param p2                parameters
     * @param callable          callable performing the submission
     * @return ConnectionResult or null if asynchronous
     * @throws Exception
     */
    protected SubmissionResult submitCallable(final XFormsModelSubmission.SubmissionParameters p,
                                              final XFormsModelSubmission.SecondPassParameters p2,
                                              final Callable<SubmissionResult> callable) throws Exception {
        if (p2.isAsynchronous) {

            // Tell XFCD that we have one more async submission
            containingDocument.getAsynchronousSubmissionManager(true).addAsynchronousSubmission(callable);

            // Tell caller he doesn't need to do anything
            return null;
        } else if (p.isDeferredSubmissionSecondPass) {
            // Tell XFCD that we have a submission replace="all" ready for a second pass
            containingDocument.setReplaceAllCallable(callable);
            // Tell caller he doesn't need to do anything
            return null;
        } else {
            // Just run it now
            return callable.call();
        }
    }

    protected IndentedLogger getDetailsLogger(final XFormsModelSubmission.SubmissionParameters p, final XFormsModelSubmission.SecondPassParameters p2) {
        return submission.getDetailsLogger(p, p2);
    }

    protected IndentedLogger getTimingLogger(final XFormsModelSubmission.SubmissionParameters p, final XFormsModelSubmission.SecondPassParameters p2) {
        return submission.getTimingLogger(p, p2);
    }

    public static boolean isLogBody() {
        return XFormsProperties.getDebugLogging().contains("submission-body");
    }

    protected interface SubmissionProcess {
        void process(ExternalContext.Request request, ExternalContext.Response response);
    }

    /**
     * Perform a local (request dispatcher or portlet) submission.
     */
    protected ConnectionResult openLocalConnection(ExternalContext externalContext,
                                                   ExternalContext.Response response,
                                                   final IndentedLogger indentedLogger,
                                                   final String resource,
                                                   XFormsModelSubmission.SubmissionParameters p,
                                                   String actualRequestMediatype,
                                                   String encoding,
                                                   byte[] messageBody,
                                                   String queryString,
                                                   String headerNames,
                                                   scala.collection.immutable.Map<String, String[]> customHeaderNameValues,
                                                   SubmissionProcess submissionProcess,
                                                   boolean isContextRelative,
                                                   boolean isDefaultContext) {

        // Action must be an absolute path
        if (!resource.startsWith("/"))
            throw new OXFException("Action does not start with a '/': " + resource);

        final String httpMethod = p.actualHttpMethod;

        // Case of empty body
        if (messageBody == null)
            messageBody = new byte[0];

        // Destination context path is the context path of the current request, or the context path implied by the new URI
        final String destinationContextPath = isDefaultContext ? "" : isContextRelative ? externalContext.getRequest().getContextPath() : NetUtils.getFirstPathElement(resource);

        // Determine headers
        final scala.collection.immutable.Map<String, String[]> headers =
            Connection.buildConnectionHeadersWithSOAP(httpMethod, null, actualRequestMediatype, encoding, customHeaderNameValues, headerNames, indentedLogger);

        // Create requestAdapter depending on method
        final LocalRequest requestAdapter;
        final String effectiveResourceURI;
        final String rootAdjustedResourceURI;
        {
            if (Connection.requiresRequestBody(httpMethod)) {
                // Simulate a POST or PUT
                effectiveResourceURI = resource;

                // Log request body
                if (indentedLogger.isDebugEnabled() && isLogBody())
                    Connection.logRequestBody(indentedLogger, actualRequestMediatype, messageBody);

                rootAdjustedResourceURI = isDefaultContext || isContextRelative ? effectiveResourceURI : NetUtils.removeFirstPathElement(effectiveResourceURI);
                if (rootAdjustedResourceURI == null)
                    throw new OXFException("Action must start with a servlet context path: " + resource);

                requestAdapter = new LocalRequest(
                    externalContext, indentedLogger, destinationContextPath, rootAdjustedResourceURI, httpMethod,
                    messageBody, headers);
            } else {
                // Simulate a GET or DELETE
                {
                    final StringBuilder updatedActionStringBuilder = new StringBuilder(resource);
                    if (queryString != null) {
                        if (resource.indexOf('?') == -1)
                            updatedActionStringBuilder.append('?');
                        else
                            updatedActionStringBuilder.append('&');
                        updatedActionStringBuilder.append(queryString);
                    }
                    effectiveResourceURI = updatedActionStringBuilder.toString();
                }

                rootAdjustedResourceURI = isDefaultContext || isContextRelative ? effectiveResourceURI : NetUtils.removeFirstPathElement(effectiveResourceURI);
                if (rootAdjustedResourceURI == null)
                    throw new OXFException("Action must start with a servlet context path: " + resource);

                requestAdapter = new LocalRequest(
                    externalContext, indentedLogger, destinationContextPath, rootAdjustedResourceURI, httpMethod, headers);
            }
        }

        if (indentedLogger.isDebugEnabled())
            indentedLogger.logDebug("", "dispatching request",
                        "method", httpMethod,
                        "mediatype", actualRequestMediatype,
                        "context path", destinationContextPath,
                        "effective resource URI (original)", effectiveResourceURI,
                        "effective resource URI (relative to servlet root)", rootAdjustedResourceURI);

        // Reason we use a Response passed is for the case of replace="all" when XFormsContainingDocument provides a Response
        final ExternalContext.Response effectiveResponse = !p.isReplaceAll ? null : response;

        final ConnectionResult connectionResult = new ConnectionResult(effectiveResourceURI) {
            @Override
            public void close() {
                final boolean nullInputStream = getResponseInputStream() == null;

                // Try to close input stream
                super.close();

                // Case of isReplaceAll where forwarded resource writes to the response directly
                if (nullInputStream) {
                    // Try to obtain, flush and close the stream to work around WebSphere issue
                    try {
                        if (effectiveResponse != null) {
                            final OutputStream os = effectiveResponse.getOutputStream();
                            os.flush();
                            os.close();
                        }
                    } catch (IllegalStateException e) {
                        indentedLogger.logDebug("", "IllegalStateException caught while closing OutputStream after forward");
                        try {
                            if (effectiveResponse != null) {
                                final PrintWriter writer = effectiveResponse.getWriter();
                                writer.flush();
                                writer.close();
                            }
                        } catch (IllegalStateException f) {
                            indentedLogger.logDebug("", "IllegalStateException caught while closing Writer after forward");
                        } catch (IOException f) {
                            indentedLogger.logDebug("", "IOException caught while closing Writer after forward");
                        }
                    } catch (IOException e) {
                        indentedLogger.logDebug("", "IOException caught while closing OutputStream after forward");
                    }
                }
            }
        };
        if (p.isReplaceAll) {
            final AllReplacer.ReplaceAllResponse replaceAllResponse = new AllReplacer.ReplaceAllResponse(effectiveResponse);
            submissionProcess.process(requestAdapter, replaceAllResponse);
            if (replaceAllResponse.getStatus() > 0)
                connectionResult.setStatusCodeJava(replaceAllResponse.getStatus());
            connectionResult.setDontHandleResponseJava();

            // Here we cause dispatch xforms-submit-error upon getting a non-success error code, even though the
            // response has already been written out. This gives the form author a chance to do something in cases
            // the response is buffered, for example do a sendError().
            // HOWEVER: We don't do this
            if (! p.isDeferredSubmissionSecondPass) {
                if (! NetUtils.isSuccessCode(connectionResult.statusCode()) && ! p.isDeferredSubmissionSecondPass)
                    throw new XFormsSubmissionException(submission, "xf:submission for submission id: " + submission.getId() + ", error code received when submitting instance: " + connectionResult.statusCode(), "processing submission response",
                            new XFormsSubmitErrorEvent(submission, XFormsSubmitErrorEvent.RESOURCE_ERROR(), connectionResult));
            } else {
                // Two reasons: 1. We don't want to modify the document state 2. This can be called outside of the document
                // lock, see XFormsServer.
            }
        } else {
            // We must intercept the reply
            final ResponseAdapter responseAdapter = new ResponseAdapter(response);
            submissionProcess.process(requestAdapter, responseAdapter);

            // Get response information that needs to be forwarded

            // NOTE: Here, the resultCode is not propagated from the included resource
            // when including Servlets. Similarly, it is not possible to obtain the
            // included resource's content type or headers. Because of this we should not
            // use an optimized submission from within a servlet.
            connectionResult.setStatusCodeJava(responseAdapter.getResponseCode());
            connectionResult.setResponseContentType(XMLUtils.XML_CONTENT_TYPE);
            connectionResult.setResponseInputStream(responseAdapter.getInputStream());
        }

        return connectionResult;
    }
}
