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
package org.orbeon.oxf.servlet;

import org.apache.log4j.Logger;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.InitUtils;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.pipeline.api.ProcessorDefinition;
import org.orbeon.oxf.util.AttributesToMap;
import org.orbeon.oxf.util.LoggerFactory;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Enumeration;

public class OXFServlet extends HttpServlet {

    private static Logger logger = LoggerFactory.createLogger(OXFServlet.class);

    public static final String DEFAULT_FORM_CHARSET = "utf-8";
    public static final String DEFAULT_FORM_CHARSET_PROPERTY = "oxf.servlet.default-form-charset";

    private ProcessorService processorService;

    // Web application context instance
    private WebAppContext webAppContext;

    public static boolean supportsServlet23;

    // We need to know if Servlet 2.3 API is present
    static {
        try {
            Method method = HttpServletRequest.class.getMethod("setCharacterEncoding", new Class[]{String.class});
            supportsServlet23  = method != null;
        } catch (NoSuchMethodException e) {
            supportsServlet23 = false;
        }
        if (!supportsServlet23)
            logger.warn("Servlet 2.3 API is not dectected. Some feature won't be available.");
    }

    public void init() throws ServletException {
        try {
            // Make sure the Web app context is initialized
            ServletContext servletContext = getServletContext();
            webAppContext = WebAppContext.instance(servletContext);

            // Try to obtain a local processor definition
            ProcessorDefinition mainProcessorDefinition
                = InitUtils.getDefinitionFromMap(new ServletInitMap(), ProcessorService.MAIN_PROCESSOR_PROPERTY_PREFIX,
                        ProcessorService.MAIN_PROCESSOR_INPUT_PROPERTY_PREFIX);
            // Try to obtain a processor definition from the properties
            if (mainProcessorDefinition == null)
                mainProcessorDefinition = InitUtils.getDefinitionFromProperties(ProcessorService.MAIN_PROCESSOR_PROPERTY_PREFIX,
                    ProcessorService.MAIN_PROCESSOR_INPUT_PROPERTY_PREFIX);
            // Try to obtain a processor definition from the context
            if (mainProcessorDefinition == null)
                mainProcessorDefinition = InitUtils.getDefinitionFromServletContext(servletContext, ProcessorService.MAIN_PROCESSOR_PROPERTY_PREFIX,
                    ProcessorService.MAIN_PROCESSOR_INPUT_PROPERTY_PREFIX);

            // Create and initialize service
            processorService = new ProcessorService();
            processorService.init(mainProcessorDefinition);
        } catch (Exception e) {
            throw new ServletException(OXFException.getRootThrowable(e));
        }
    }

    public void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        try {
            // Filter on supported methods
            String httpMethod = request.getMethod();
            if (!("post".equalsIgnoreCase(httpMethod) || "get".equalsIgnoreCase(httpMethod)))
                throw new OXFException("Unsupported HTTP method: " + httpMethod);

            // Run service
            PipelineContext pipelineContext = new PipelineContext();
            ExternalContext externalContext = new ServletExternalContext(getServletContext(), pipelineContext, webAppContext.getServletInitParametersMap(), request, response);
            processorService.service(true, externalContext, pipelineContext);
        } catch (Exception e) {
            throw new ServletException(OXFException.getRootThrowable(e));
        }
    }

    public void destroy() {
        processorService.destroy();
        processorService = null;
        webAppContext = null;
    }

    /**
     * Present a read-only view of the Servlet initialization parameters as a Map.
     */
    public class ServletInitMap extends AttributesToMap {
        public ServletInitMap() {
            super(new AttributesToMap.Attributeable() {
                public Object getAttribute(String s) {
                    return OXFServlet.this.getInitParameter(s);
                }

                public Enumeration getAttributeNames() {
                    return OXFServlet.this.getInitParameterNames();
                }

                public void removeAttribute(String s) {
                    throw new UnsupportedOperationException();
                }

                public void setAttribute(String s, Object o) {
                    throw new UnsupportedOperationException();
                }
            });
        }
    }

/*
    {
        if (config.waitPageProcessorDefinition != null) {
                // Create and schedule the task
                Task task = new Task() {
                    public String getStatus() {
                        return null;
                    }

                    public void run() {
                        // Scenarios:
                        // 1. GET -> redirect
                        // 2. GET -> content (*)
                        // 3. POST -> redirect (*)
                        // 4. POST -> content

                        // Check (synchronized on output) whether the response was committed

                        // If it was, return immediately, there is nothing we can do

                        // If it was not, bufferize regular output and run pipeline

                        // When processing instruction is found,
                    }
                };
                task.setSchedule(System.currentTimeMillis() + config.waitPageDelay, 0);
            }
    }

    {
        InitUtils.ProcessorDefinition waitPageProcessorDefinition;
        long waitPageDelay;
    }
*/
}
