/**
 * Copyright (C) 2011 Orbeon, Inc.
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
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.util.ConnectionResult;
import org.orbeon.oxf.util.IndentedLogger;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.XFormsUtils;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.Callable;

/**
 * Submission which doesn't issue HTTP requests but goes through a Servlet or Portlet API's RequestDispatcher.
 */
public class RequestDispatcherSubmission extends BaseSubmission {
    
    private static final String SKIPPING_SUBMISSION_DEBUG_MESSAGE = "skipping request dispatcher servlet submission";

    public RequestDispatcherSubmission(XFormsModelSubmission submission) {
        super(submission);
    }

    public String getType() {
        return "request dispatcher";
    }

    /**
     * Check whether submission is allowed.
     */
    public boolean isMatch(XFormsModelSubmission.SubmissionParameters p,
                           XFormsModelSubmission.SecondPassParameters p2, XFormsModelSubmission.SerializationParameters sp) {

        final ExternalContext.Request request = NetUtils.getExternalContext().getRequest();
        final IndentedLogger indentedLogger = getDetailsLogger(p, p2);

        // Log a lot of stuff for development, as it is not always obvious why we pick this type of submission.

        final boolean isDebugEnabled = indentedLogger.isDebugEnabled();
        if (isDebugEnabled) {
            indentedLogger.logDebug("", "checking whether " + getType() + " submission is allowed",
                "resource", p2.actionOrResource, "noscript", Boolean.toString(p.isNoscript),
                "is asynchronous", Boolean.toString(p2.isAsynchronous),
                "container type", request.getContainerType(),
                "norewrite", Boolean.toString(submission.isURLNorewrite()),
                "url type", submission.getUrlType(),
                "local-submission-forward", Boolean.toString(containingDocument.isLocalSubmissionForward()),
                "local-submission-include", Boolean.toString(containingDocument.isLocalSubmissionInclude())
            );
        }

        // Only for servlet for now
        if (!request.getContainerType().equals("servlet")) {
            if (isDebugEnabled)
                indentedLogger.logDebug("", SKIPPING_SUBMISSION_DEBUG_MESSAGE,
                        "reason", "container type is not servlet");
            return false;
        }

        // Absolute URL implies a regular submission
        if (NetUtils.urlHasProtocol(p2.actionOrResource)) {
            if (isDebugEnabled)
                indentedLogger.logDebug("", SKIPPING_SUBMISSION_DEBUG_MESSAGE,
                        "reason", "resource URL has protocol", "resource", p2.actionOrResource);
            return false;
        }

        // TODO: why is this condition here?
        if (p.isNoscript) {
            if (isDebugEnabled)
                indentedLogger.logDebug("", SKIPPING_SUBMISSION_DEBUG_MESSAGE,
                        "reason", "noscript mode enabled");
            return false;
        }

        // For now, we don't handle async (could be handled in the future)
        if (p2.isAsynchronous) {
            if (isDebugEnabled)
                indentedLogger.logDebug("", SKIPPING_SUBMISSION_DEBUG_MESSAGE,
                        "reason", "asynchronous mode is not supported yet");
            return false;
        }

        if (p.isReplaceAll) {
            // replace="all"
            if (! containingDocument.isLocalSubmissionForward()) {
                if (isDebugEnabled)
                    indentedLogger.logDebug("", SKIPPING_SUBMISSION_DEBUG_MESSAGE,
                            "reason", "forward submissions are disallowed in properties");
                return false;
            }
        } else {
            // replace="instance|text|none"
            if (! containingDocument.isLocalSubmissionInclude()) {
                if (isDebugEnabled)
                    indentedLogger.logDebug("", SKIPPING_SUBMISSION_DEBUG_MESSAGE,
                            "reason", "include submissions are disallowed in properties");
                return false;
            }
        }

        if (isDebugEnabled)
            indentedLogger.logDebug("", "enabling " + getType() + " submission");

        return true;
    }

    public SubmissionResult connect(final XFormsModelSubmission.SubmissionParameters p,
                                    final XFormsModelSubmission.SecondPassParameters p2, final XFormsModelSubmission.SerializationParameters sp) throws Exception {

        final IndentedLogger timingLogger = getTimingLogger(p, p2);
        final IndentedLogger detailsLogger = getDetailsLogger(p, p2);

        // NOTE: Using include() for servlets doesn't allow detecting errors caused by the
        // included resource. [As of 2009-02-13, not sure if this is the case.]

        // f:url-norewrite="true" with an absolute path allows accessing other servlet contexts.

        // URI with xml:base resolution
        final URI resolvedURI = XFormsUtils.resolveXMLBase(containingDocument, submission.getSubmissionElement(), p2.actionOrResource);

        // NOTE: We don't want any changes to happen to the document upon xxforms-submit when producing
        // a new document so we don't dispatch xforms-submit-done and pass a null XFormsModelSubmission
        // in that case

        // Headers
        final scala.collection.immutable.Map<String, String[]> customHeaderNameValues = SubmissionUtils.evaluateHeaders(submission, p.isReplaceAll);
        final String headersToForward = containingDocument.getForwardSubmissionHeaders();

        final String submissionEffectiveId = submission.getEffectiveId();

        // Pack external call into a Runnable so it can be run:
        // - now and synchronously
        // - now and asynchronously
        // - later as a "foreground" asynchronous submission
        final Callable<SubmissionResult> callable = new Callable<SubmissionResult>() {
            public SubmissionResult call() throws Exception {

                // TODO: This refers to PropertyContext, XFormsContainingDocument, and Submission. Ok because async disabled for now.

                // Open the connection
                final boolean[] status = { false , false};
                ConnectionResult connectionResult = null;
                try {
                    connectionResult = openRequestDispatcherConnection(NetUtils.getExternalContext(),
                        containingDocument, detailsLogger, resolvedURI.toString(), p,
                        submission.isURLNorewrite(), sp.actualRequestMediatype, p2.encoding, sp.messageBody,
                        sp.queryString, headersToForward, customHeaderNameValues);

                    // Update status
                    status[0] = true;

                    // TODO: can we put this in the Replacer?
                    if (connectionResult.dontHandleResponse())
                        containingDocument.setGotSubmissionReplaceAll();

                    // Obtain replacer, deserialize and update status
                    final Replacer replacer = submission.getReplacer(connectionResult, p);
                    replacer.deserialize(connectionResult, p, p2);
                    status[1] = true;

                    // Return result
                    return new SubmissionResult(submissionEffectiveId, replacer, connectionResult);
                } catch (Throwable throwable) {
                    // Exceptions are handled further down
                    return new SubmissionResult(submissionEffectiveId, throwable, connectionResult);
                } finally {
                    if (p2.isAsynchronous && timingLogger.isDebugEnabled())
                        timingLogger.endHandleOperation("id", submissionEffectiveId, "asynchronous", Boolean.toString(p2.isAsynchronous),
                                "connected", Boolean.toString(status[0]), "deserialized", Boolean.toString(status[1]));
                }
            }
        };

        // Submit the callable
        // This returns null if the execution is deferred
        return submitCallable(p, p2, callable);
    }

    /**
     * Perform a local connection using the Servlet API.
     */
    public ConnectionResult openRequestDispatcherConnection(ExternalContext externalContext,
                                                            XFormsContainingDocument containingDocument,
                                                            IndentedLogger indentedLogger,
                                                            final String resource,
                                                            final XFormsModelSubmission.SubmissionParameters p,
                                                            boolean isNorewrite,
                                                            String actualRequestMediatype,
                                                            String encoding,
                                                            byte[] messageBody,
                                                            String queryString,
                                                            String headerNames,
                                                            scala.collection.immutable.Map<String, String[]> customHeaderNameValues) {

        // NOTE: This code does custom rewriting of the path on the action, taking into account whether
        // the page was produced through a filter in separate deployment or not.
        final boolean isContextRelative;
        final String effectiveResource;
        if (!isNorewrite) {
            // Must rewrite
            if (containingDocument.getDeploymentType() != XFormsConstants.DeploymentType.separate) {
                // We are not in separate deployment, so keep path relative to the current servlet context
                isContextRelative = true;
                effectiveResource = resource;
            } else {
                // We are in separate deployment, so prepend request context path and mark path as not relative to the current context`
                final String contextPath = containingDocument.getRequestContextPath();
                isContextRelative = false;
                effectiveResource = contextPath + resource;
            }
        } else {
            // Must not rewrite anyway, so mark path as not relative to the current context
            isContextRelative = false;
            effectiveResource = resource;
        }

        final ExternalContext.RequestDispatcher requestDispatcher = externalContext.getRequestDispatcher(effectiveResource, isContextRelative);
        final boolean isDefaultContext = requestDispatcher.isDefaultContext();

        final ExternalContext.Response response = containingDocument.getResponse() != null ? containingDocument.getResponse() : externalContext.getResponse();
        return openLocalConnection(externalContext, response, indentedLogger,
           effectiveResource, p, actualRequestMediatype, encoding,
           messageBody, queryString, headerNames, customHeaderNameValues, new SubmissionProcess() {
               public void process(ExternalContext.Request request, ExternalContext.Response response) {
                  try {
                      if (p.isReplaceAll)
                          requestDispatcher.forward(request, response);
                      else
                          requestDispatcher.include(request, response);
                  } catch (IOException e) {
                      throw new OXFException(e);
                  }
               }
           }, isContextRelative, isDefaultContext);
    }
}
