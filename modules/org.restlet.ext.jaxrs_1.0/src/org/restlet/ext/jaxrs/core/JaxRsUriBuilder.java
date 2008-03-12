/*
 * Copyright 2005-2008 Noelios Consulting.
 * 
 * The contents of this file are subject to the terms of the Common Development
 * and Distribution License (the "License"). You may not use this file except in
 * compliance with the License.
 * 
 * You can obtain a copy of the license at
 * http://www.opensource.org/licenses/cddl1.txt See the License for the specific
 * language governing permissions and limitations under the License.
 * 
 * When distributing Covered Code, include this CDDL HEADER in each file and
 * include the License file at http://www.opensource.org/licenses/cddl1.txt If
 * applicable, add the following below this CDDL HEADER, with the fields
 * enclosed by brackets "[]" replaced with your own identifying information:
 * Portions Copyright [yyyy] [name of copyright owner]
 */
package org.restlet.ext.jaxrs.core;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriBuilderException;

import org.restlet.data.Reference;
import org.restlet.ext.jaxrs.exceptions.IllegalPathException;
import org.restlet.ext.jaxrs.exceptions.IllegalPathOnClassException;
import org.restlet.ext.jaxrs.exceptions.IllegalPathOnMethodException;
import org.restlet.ext.jaxrs.exceptions.MissingAnnotationException;
import org.restlet.ext.jaxrs.util.EncodeOrCheck;
import org.restlet.ext.jaxrs.util.Util;
import org.restlet.ext.jaxrs.wrappers.AbstractMethodWrapper;
import org.restlet.ext.jaxrs.wrappers.ResourceClass;
import org.restlet.util.Resolver;
import org.restlet.util.Template;

/**
 * Implementation of interface {@link UriBuilder}.
 * 
 * @author Stephan Koops
 * @see UriBuilder
 */
public class JaxRsUriBuilder extends UriBuilder {

    /**
     * This resolver is used, if no variable is allowed in the template. It
     * throws an {@link UriBuilderException}, if a variable is requested.
     */
    private static final Resolver<String> NO_VAR_RESOLVER = new Resolver<String>() {
        public String resolve(String variableName) {
            throw new UriBuilderException(
                    "The UriBuilder must not contain any template parameter");
        }
    };

    private class IteratorVariableResolver implements Resolver<String> {
        private int i = 0;

        private Map<String, String> retrievedValues = new HashMap<String, String>();

        private String[] values;

        IteratorVariableResolver(String[] values) {
            this.values = values;
        }

        public String resolve(String variableName) {
            String varValue = retrievedValues.get(variableName);
            if (varValue == null) {
                if (i >= values.length)
                    throw new IllegalArgumentException(
                            "The value given array contains not enough elements");
                varValue = values[i];
                i++;
                retrievedValues.put(variableName, varValue);
            }
            return varValue;
        }
    }

    private boolean encode = true;

    /**
     * {@link String} or {@link StringBuilder}. fleible to avoid unnecessary
     * converting.
     */
    private CharSequence fragment;

    /**
     * {@link String} or {@link StringBuilder}. fleible to avoid unnecessary
     * converting.
     */
    private CharSequence host;

    private LinkedList<JaxRsPathSegment> pathSegments = new LinkedList<JaxRsPathSegment>();

    private int port = -1;

    /**
     * {@link String} or {@link StringBuilder}. fleible to avoid unnecessary
     * converting.
     */
    private CharSequence query;

    /**
     * {@link String} or {@link StringBuilder}. fleible to avoid unnecessary
     * converting.
     */
    private CharSequence scheme;

    /**
     * {@link String} or {@link StringBuilder}. fleible to avoid unnecessary
     * converting.
     */
    private CharSequence userInfo;

    /**
     * Creates a JaxRsUriBuilder
     */
    public JaxRsUriBuilder() {
    }

    /**
     * Adds the given path to the current path. This method should only be
     * called once.
     * 
     * @param path
     *                may contain "/". The path will be splitted here,
     *                independent of {@link #encode}.
     */
    private void addUnencodedPathSegments(String path) {
        if (path.startsWith("/"))
            path = path.substring(1);
        if (path.length() > 0) {
            // first check preconditions
            List<JaxRsPathSegment> pathSegments = convertToJaxRsPathSegments(path
                    .split("/"));
            // than add
            this.pathSegments.addAll(pathSegments);
        }
    }

    /**
     * Converts the given segments to {@link JaxRsPathSegment}s. Encodes the
     * segments (if wished) xor check, if the chars were valid.
     * 
     * @param segments
     * @return
     * @throws IllegalArgumentException
     */
    private List<JaxRsPathSegment> convertToJaxRsPathSegments(String[] segments)
            throws IllegalArgumentException {
        List<JaxRsPathSegment> pathSegments = new ArrayList<JaxRsPathSegment>(
                segments.length);
        addToJaxRsPathSegments(segments, pathSegments);
        return pathSegments;
    }

    /**
     * Converts the given segments to {@link JaxRsPathSegment}s and add them to
     * the given {@link List}. Encodes also the segments (if wished) xor check,
     * if the chars were valid.
     * 
     * @param segments
     *                the segments to convert
     * @param pathSegments
     *                the List to add the converted {@link JaxRsPathSegment}s.
     * @throws IllegalArgumentException
     */
    private void addToJaxRsPathSegments(String[] segments,
            List<JaxRsPathSegment> pathSegments)
            throws IllegalArgumentException {
        int l = segments.length;
        for (int i = 0; i < l; i++)
            pathSegments.add(new JaxRsPathSegment(segments[i], false, false,
                    encode, true, i));
    }

    /**
     * Build a URI, any URI template parameters will be replaced by the empty
     * string. The <code>build</code> method does not change the state of the
     * <code>UriBuilder</code> and it may be called multiple times on the same
     * builder instance.
     * 
     * @return the URI built from the UriBuilder
     * @throws UriBuilderException
     *                 if there are any URI template parameters, or if a URI
     *                 cannot be constructed based on the current state of the
     *                 builder.
     * @see javax.ws.rs.core.UriBuilder#build()
     */
    @Override
    public URI build() throws UriBuilderException {
        Template template = new Template(toStringWithCheck(true));
        return buildUri(template.format(NO_VAR_RESOLVER));
    }

    /**
     * Build a URI, any URI template parameters will be replaced by the value in
     * the supplied map. The <code>build</code> method does not change the
     * state of the <code>UriBuilder</code> and it may be called multiple
     * times on the same builder instance.
     * 
     * @param values
     *                a map of URI template parameter names and values
     * @return the URI built from the UriBuilder
     * @throws IllegalArgumentException
     *                 if automatic encoding is disabled and a supplied value
     *                 contains illegal characters, or if there are any URI
     *                 template parameters without a supplied value
     * @throws UriBuilderException
     *                 if a URI cannot be constructed based on the current state
     *                 of the builder.
     * @see javax.ws.rs.core.UriBuilder#build(java.util.Map)
     */
    @Override
    @SuppressWarnings("unchecked")
    public URI build(final Map<String, String> values)
            throws IllegalArgumentException, UriBuilderException {
        Template template = new Template(toStringWithCheck(false));
        return buildUri(template.format(new Resolver() {
            public String resolve(String variableName) {
                String varValue = values.get(variableName);
                if (varValue == null)
                    throw new IllegalArgumentException(
                            "The value Map must contain a value for all given Templet variables. The value for variable "
                                    + variableName + " is missing");
                return varValue;
            }
        }));
    }

    /**
     * Build a URI, using the supplied values in order to replace any URI
     * template parameters. The <code>build</code> method does not change the
     * state of the <code>UriBuilder</code> and it may be called multiple
     * times on the same builder instance.
     * <p>
     * All instances of the same template parameter will be replaced by the same
     * value that corresponds to the position of the first instance of the
     * template parameter. e.g. the template "{a}/{b}/{a}" with values {"x",
     * "y", "z"} will result in the the URI "x/y/x", <i>not</i> "x/y/z".
     * 
     * @param values
     *                a list of URI template parameter values
     * @return the URI built from the UriBuilder
     * @throws IllegalArgumentException
     *                 if automatic encoding is disabled and a supplied value
     *                 contains illegal characters, or if there are any URI
     *                 template parameters without a supplied value
     * @throws UriBuilderException
     *                 if a URI cannot be constructed based on the current state
     *                 of the builder.
     * @see javax.ws.rs.core.UriBuilder#build(java.lang.String[])
     */
    @Override
    public URI build(String... values) throws IllegalArgumentException,
            UriBuilderException {
        Template template = new Template(toStringWithCheck(false));
        return buildUri(template.format(new IteratorVariableResolver(values)));
    }

    /**
     * @param refAsString
     * @return
     * @throws UriBuilderException
     */
    private URI buildUri(String refAsString) throws UriBuilderException {
        try {
            return new URI(refAsString);
        } catch (URISyntaxException e) {
            throw new UriBuilderException(
                    "Could not build the URI from String " + refAsString, e);
        }
    }

    /**
     * Create a copy of the UriBuilder preserving its state. This is a more
     * efficient means of creating a copy than constructing a new UriBuilder
     * from a URI returned by the {@link #build} method.
     * 
     * @return a copy of the UriBuilder
     * @see javax.ws.rs.core.UriBuilder#clone()
     */
    @Override
    public UriBuilder clone() {
        JaxRsUriBuilder uriBuilder = new JaxRsUriBuilder();
        uriBuilder.encode = this.encode;
        uriBuilder.fragment = this.fragment;
        uriBuilder.host = this.host;
        uriBuilder.port = this.port;
        uriBuilder.scheme = this.scheme;
        uriBuilder.userInfo = this.userInfo;
        uriBuilder.pathSegments = new LinkedList<JaxRsPathSegment>();
        for (JaxRsPathSegment pathSegment : pathSegments)
            uriBuilder.pathSegments.add(pathSegment.clone());
        if (this.query != null) {
            uriBuilder.query = this.query;
            this.query = this.query.toString();
            // copy this.query to new query, because typically the clone will
            // be changed and not the orignal.
        }
        return uriBuilder;
    }

    /**
     * Controls whether the UriBuilder will automatically encode URI components
     * added by subsequent operations or not. Defaut value is true
     * 
     * @param enable
     *                automatic encoding (true) or disable it (false). If false,
     *                subsequent components added must be valid with all illegal
     *                characters already escaped.
     * @return the updated UriBuilder
     * @see javax.ws.rs.core.UriBuilder#encode(boolean)
     * @see UriBuilder
     */
    @Override
    public UriBuilder encode(boolean enable) {
        this.encode = enable;
        return this;
    }

    /**
     * Set the URI fragment using an unencoded value.
     * 
     * @param fragment
     *                the URI fragment, may contain URI template parameters
     * @return the updated UriBuilder
     * @throws IllegalArgumentException
     *                 if fragment is null, or if automatic encoding is disabled
     *                 and fragment contains illegal characters
     * @see javax.ws.rs.core.UriBuilder#fragment(java.lang.String)
     */
    @Override
    public UriBuilder fragment(String fragment) throws IllegalArgumentException {
        this.fragment = EncodeOrCheck.fragment(fragment, this.encode);
        return this;
    }

    /**
     * Set the URI host.
     * 
     * @return the updated UriBuilder
     * @param host
     *                the URI host, may contain URI template parameters
     * @throws IllegalArgumentException
     *                 if host is invalid or is null
     * @see javax.ws.rs.core.UriBuilder#host(java.lang.String)
     */
    @Override
    public UriBuilder host(String host) throws IllegalArgumentException {
        this.host = EncodeOrCheck.host(host);
        return this;
    }

    /**
     * Get the current state of automatic encoding.
     * 
     * @return true if automatic encoding is enable, false otherwise
     * @see UriBuilder#isEncode()
     */
    @Override
    public boolean isEncode() {
        return this.encode;
    }

    /**
     * Append a matrix parameter to the existing set of matrix parameters of the
     * current final segment of the URI path. Note that the matrix parameters
     * are tied to a particular path segment; subsequent addition of path
     * segments will not affect their position in the URI path.
     * 
     * @param name
     *                the matrix parameter name, may contain URI template
     *                parameters
     * @param value
     *                the matrix parameter value, may contain URI template
     *                parameters
     * @return the updated UriBuilder
     * @throws IllegalArgumentException
     *                 if name or value is null, or if automatic encoding is
     *                 disabled and name or value contains illegal characters
     * @see javax.ws.rs.core.UriBuilder#matrixParam(java.lang.String,
     *      java.lang.String)
     */
    @Override
    public UriBuilder matrixParam(String name, String value)
            throws IllegalArgumentException {
        name = EncodeOrCheck.nameOrValue(name, encode, "matrix parameter name");
        value = EncodeOrCheck.nameOrValue(value, encode,
                "matrix parameter value");
        this.pathSegments.getLast().getMatrixParameters().add(name, value);
        return this;
    }

    /**
     * Append path segments from a Path-annotated class to the existing list of
     * segments. When constructing the final path, each segment will be
     * separated by '/' if necessary. The value of the encode property of the
     * Path annotation will be used when processing the value of the
     * 
     * @Path but it will not be used to modify the state of automaic encoding
     *       for the builder.
     * 
     * @param resource
     *                a resource whose &#64;Path value will be used to obtain
     *                the path segment.
     * @return the updated UriBuilder
     * @throws IllegalArgumentException
     *                 if resource is null, or if resource.encode is false and
     *                 resource.value contains illegal characters, or if
     *                 resource is not annotated with UrPath
     * @see javax.ws.rs.core.UriBuilder#path(java.lang.Class)
     */
    @Override
    @SuppressWarnings("unchecked")
    public UriBuilder path(Class resource) throws IllegalArgumentException {
        if (resource == null)
            throw new IllegalArgumentException(
                    "The root resource class must not be null");
        try {
            addUnencodedPathSegments(ResourceClass.getPathTemplate(resource));
        } catch (IllegalPathOnClassException e) {
            throw e.getCause();
        } catch (MissingAnnotationException e) {
            throw new IllegalArgumentException("The resource class "
                    + resource.getName() + " requires an annotation @Path");
        }
        return this;
    }

    /**
     * Append path segments from a Path-annotated method to the existing list of
     * segments. When constructing the final path, each segment will be
     * separated by '/' if necessary. This method is a convenience shortcut to
     * <code>path(Method)</code>, it can only be used in cases where there is
     * a single method with the specified name that is annotated with
     * 
     * @Path.
     * 
     * @param resource
     *                the resource containing the method
     * @param method
     *                the name of the method whose
     * @UPathvalue will be used to obtain the path segment
     * @return the updated UriBuilder
     * @throws IllegalArgumentException
     *                 if resource or method is null, or if the specified method
     *                 does not exist, or there is more than or less than one
     *                 variant of the method annotated with UriPath
     * @see javax.ws.rs.core.UriBuilder#path(java.lang.Class, java.lang.String)
     */
    @Override
    @SuppressWarnings("unchecked")
    public UriBuilder path(Class resource, String methodName)
            throws IllegalArgumentException {
        if (methodName == null)
            throw new IllegalArgumentException(
                    "The method name must not be null");
        String resMethodPath = null;
        for (Method method : resource.getMethods()) {
            if (!method.getName().equals(methodName))
                continue;
            String path;
            try {
                path = AbstractMethodWrapper.getPathTemplate(method);
            } catch (IllegalPathOnMethodException e) {
                throw e.getCause();
            } catch (MissingAnnotationException e) {
                throw new IllegalArgumentException(e);
            }
            if (path == null)
                continue;
            if (resMethodPath != null && !resMethodPath.equals(path))
                throw new IllegalArgumentException("The class " + resource
                        + " has more than one methods with the name "
                        + methodName + " annotated with @Path");
            resMethodPath = path;
        }
        if (resMethodPath == null)
            throw new IllegalArgumentException("The class " + resource
                    + " has no method with the name " + methodName
                    + " annotated with @Path");
        path(resource);
        addUnencodedPathSegments(resMethodPath);
        return this;
    }

    /**
     * Append path segments from a list of Path-annotated methods to the
     * existing list of segments. When constructing the final path, each segment
     * will be separated by '/' if necessary. The value of the encode property
     * of the Path annotation will be used when processing the value of the
     * 
     * @Path but it will not be used to modify the state of automaic encoding
     *       for the builder.
     * 
     * @param methods
     *                a list of methods whose
     * @Path values will be used to obtain the path segments
     * @return the updated UriBuilder
     * @throws IllegalArgumentException
     *                 if any element of methods is null or is not annotated
     *                 with a UrPath
     * @see javax.ws.rs.core.UriBuilder#path(java.lang.reflect.Method[])
     */
    @Override
    public UriBuilder path(Method... methods) throws IllegalArgumentException {
        if (methods == null || methods.length == 0)
            return this;
        List<JaxRsPathSegment> newPathSegments = new ArrayList<JaxRsPathSegment>();
        // first check all preconditions
        for (Method method : methods) {
            String segment;
            try {
                segment = AbstractMethodWrapper.getPathTemplate(method);
            } catch (MissingAnnotationException e) {
                throw new IllegalArgumentException(e);
            } catch (IllegalPathException e) {
                throw e.getCause();
            }
            if (segment.startsWith("/"))
                segment = segment.substring(1);
            if (segment.length() > 0) {
                String[] segments = segment.split("/");
                addToJaxRsPathSegments(segments, newPathSegments);
            }
        }
        // than add
        this.pathSegments.addAll(newPathSegments);
        return this;
    }

    /**
     * Append path segments to the existing list of segments. When constructing
     * the final path, each segment will be separated by '/' if necessary.
     * Existing '/' characters are preserved thus a single segment value can
     * represent multiple URI path segments.
     * 
     * @param segments
     *                the path segments, may contain URI template parameters
     * @return the updated UriBuilder
     * @throws IllegalArgumentException
     *                 if any element of segments is null, or if automatic
     *                 encoding is disabled and any element of segments contains
     *                 illegal characters
     * @see javax.ws.rs.core.UriBuilder#path(java.lang.String[])
     */
    @Override
    public UriBuilder path(String... segments) throws IllegalArgumentException {
        if (segments == null)
            throw new IllegalArgumentException("The segments must not be null");
        // first check preconditions
        List<JaxRsPathSegment> pathSegments = convertToJaxRsPathSegments(segments);
        // than add
        this.pathSegments.addAll(pathSegments);
        return this;
    }

    /**
     * Set the URI port.
     * 
     * @param port
     *                the URI port, a value of -1 will unset an explicit port.
     * @return the updated UriBuilder
     * @throws IllegalArgumentException
     *                 if port is invalid
     * @see javax.ws.rs.core.UriBuilder#port(int)
     */
    @Override
    public UriBuilder port(int port) throws IllegalArgumentException {
        this.port = port;
        return this;
    }

    /**
     * Append a query parameter to the existing set of query parameters.
     * 
     * @param name
     *                the query parameter name, may contain URI template
     *                parameters
     * @param value
     *                the query parameter value, may contain URI template
     *                parameters
     * @return the updated UriBuilder
     * @throws IllegalArgumentException
     *                 if name or value is null, or if automatic encoding is
     *                 disabled and name or value contains illegal characters
     * @see javax.ws.rs.core.UriBuilder#queryParam(java.lang.String,
     *      java.lang.String)
     */
    @Override
    public UriBuilder queryParam(String name, String value)
            throws IllegalArgumentException {
        name = EncodeOrCheck.nameOrValue(name, encode, "query parameter name");
        value = EncodeOrCheck.nameOrValue(value, encode,
                "query parameter value");
        StringBuilder query;
        if (this.query == null) {
            query = new StringBuilder();
            this.query = query;
        } else if (this.query instanceof StringBuilder) {
            query = (StringBuilder) this.query;
            query.append('&');
        } else {
            query = new StringBuilder(this.query.toString());
            query.append('&');
        }
        query.append(name);
        query.append('=');
        query.append(value);
        return this;
    }

    /**
     * Set the matrix parameters of the current final segment of the current URI
     * path. This method will overwrite any existing matrix parameters on the
     * current final segment of the current URI path. Note that the matrix
     * parameters are tied to a particular path segment; subsequent addition of
     * path segments will not affect their position in the URI path.
     * 
     * @param matrix
     *                the matrix parameters, may contain URI template
     *                parameters. A null value will remove all matrix parameters
     *                of the current final segment of the current URI path.
     * @return the updated UriBuilder
     * @throws IllegalArgumentException
     *                 if matrix cannot be parsed, or if automatic encoding is
     *                 disabled and any matrix parameter name or value contains
     *                 illegal characters
     * @see javax.ws.rs.core.UriBuilder#replaceMatrixParams(java.lang.String)
     */
    @Override
    public UriBuilder replaceMatrixParams(String matrixParams)
            throws IllegalArgumentException {
        JaxRsPathSegment lastPathSegment = this.pathSegments.getLast();
        if (matrixParams == null || matrixParams.length() == 0)
            lastPathSegment.getMatrixParameters().clear();
        else
            lastPathSegment.setMatrixParameters(JaxRsPathSegment
                    .parseMatrixParams(matrixParams, false, true));
        return this;
    }

    /**
     * Set the URI path. This method will overwrite any existing path segments
     * and associated matrix parameters. When constructing the final path, each
     * segment will be separated by '/' if necessary. Existing '/' characters
     * are preserved thus a single segment value can represent multiple URI path
     * segments.
     * 
     * @param segments
     *                the path segments, may contain URI template parameters. A
     *                null value will unset the path component of the URI.
     * @return the updated UriBuilder
     * @throws IllegalArgumentException
     *                 if any element of segments is null, or if automatic
     *                 encoding is disabled and any element of segments contains
     *                 illegal characters
     * @see javax.ws.rs.core.UriBuilder#replacePath(java.lang.String)
     */
    @Override
    public UriBuilder replacePath(String... path)
            throws IllegalArgumentException {
        this.pathSegments.clear();
        if (path == null || path.length == 0) // empty array
            return this;
        if (path.length == 1 && (path[0] == null || path[0].length() == 0))
            return this; // only 1 element, which is empty
        // first check all preconditions
        List<JaxRsPathSegment> newPathSegments = new ArrayList<JaxRsPathSegment>();
        for (String segment : path) {
            if (segment.startsWith("/"))
                segment = segment.substring(1);
            if (segment.length() > 0) {
                String[] segments = segment.split("/");
                addToJaxRsPathSegments(segments, newPathSegments);
            }
        }
        // than add
        this.pathSegments.addAll(newPathSegments);
        return this;
    }

    /**
     * Set the URI query string. This method will overwrite any existing query
     * parameters.
     * 
     * @param query
     *                the URI query string, may contain URI template parameters.
     *                A null value will remove all query parameters.
     * @return the updated UriBuilder
     * @throws IllegalArgumentException
     *                 if query cannot be parsed or is null, or if automatic
     *                 encoding is disabled and any query parameter name or
     *                 value contains illegal characters
     * @see javax.ws.rs.core.UriBuilder#replaceQueryParams(java.lang.String)
     */
    @Override
    public UriBuilder replaceQueryParams(String query)
            throws IllegalArgumentException {
        if (query == null || query.length() == 0)
            this.query = null;
        else
            this.query = EncodeOrCheck.fullQuery(query, this.encode);
        return this;
    }

    /**
     * Set the URI scheme.
     * 
     * @param scheme
     *                the URI scheme, may contain URI template parameters
     * @return the updated UriBuilder
     * @throws IllegalArgumentException
     *                 if scheme is invalid or is null
     * @see javax.ws.rs.core.UriBuilder#scheme(java.lang.String)
     */
    @Override
    public UriBuilder scheme(String scheme) throws IllegalArgumentException {
        EncodeOrCheck.checkValidScheme(scheme);
        this.scheme = scheme;
        return this;
    }

    /**
     * Set the URI scheme-specific-part (see {@link java.net.URI}). This method
     * will overwrite any existing values for authority, user-info, host, port
     * and path.
     * 
     * @param ssp
     *                the URI scheme-specific-part, may contain URI template
     *                parameters
     * @return the updated UriBuilder
     * @throws IllegalArgumentException
     *                 if ssp cannot be parsed or is null
     * @see javax.ws.rs.core.UriBuilder#schemeSpecificPart(java.lang.String)
     */
    @Override
    public UriBuilder schemeSpecificPart(String ssp)
            throws IllegalArgumentException {
        if (ssp == null)
            throw new IllegalArgumentException(
                    "The scheme specific part must not be null");
        Reference reference = new Reference(ssp, null);
        this.userInfo = reference.getUserInfo();
        this.host = reference.getHostDomain();
        this.port = reference.getHostPort();
        String path = reference.getPath();
        this.pathSegments.clear();
        if (path != null)
            this.addUnencodedPathSegments(path);
        this.query = reference.getQuery();
        this.fragment = reference.getFragment();
        return this;
    }

    /**
     * Returns the actual URI as String.
     * 
     * @return the actual URI as String.
     * @see #toStringWithCheck()
     */
    @Override
    public String toString() {
        return this.toString(false);
    }

    /**
     * Returns the actual URI as String.
     * 
     * @param convertBraces
     *                if true, all braces are converted, if false then not.
     * 
     * @return the actual URI as String.
     * @see #toStringWithCheck()
     */
    private String toString(boolean convertBraces) {
        try {
            StringBuilder stb = new StringBuilder();
            if (scheme != null) {
                Util.append(stb, scheme, convertBraces);
                stb.append("://");
            }
            if (userInfo != null) {
                Util.append(stb, userInfo, convertBraces);
                stb.append('@');
            }
            if (host != null)
                Util.append(stb, host, convertBraces);
            if (port > 0) {
                stb.append(':');
                stb.append(port);
            }
            boolean relativeUri = stb.length() == 0;
            for (JaxRsPathSegment pathSegment : pathSegments) {
                stb.append('/');
                pathSegment.toStringBuilder(stb, convertBraces);
            }
            if (relativeUri)
                stb.deleteCharAt(0);
            if (query != null) {
                stb.append('?');
                Util.append(stb, query, convertBraces);
            }
            if (fragment != null) {
                stb.append('#');
                Util.append(stb, fragment, convertBraces);
            }
            return stb.toString();
        } catch (IOException e) {
            throw new RuntimeException(
                    "Could not write the UriBuilder to a String; but this Exception could not occur normally",
                    e);
        }
    }

    /**
     * Returns the actual URI as String. Check for valid scheme and host before
     * 
     * @param convertBraces
     *                if true, all braces are converted, if false then not.
     * 
     * @return the actual URI as String.
     * @see #toString()
     */
    private String toStringWithCheck(boolean convertBraces) {
        if (this.host == null) {
            if (this.port >= 0)
                throw new UriBuilderException(
                        "You must set a host, if you set a port");
            if (this.userInfo != null && this.userInfo.length() >= 0)
                throw new UriBuilderException(
                        "You must set a host, if you set a userInfo");
        }
        return toString(convertBraces);
    }

    /**
     * Copies the non-null components of the supplied URI to the UriBuilder
     * replacing any existing values for those components.
     * 
     * @param uri
     *                the URI to copy components from
     * @return the updated UriBuilder
     * @throws IllegalArgumentException
     *                 if uri is null
     * @see javax.ws.rs.core.UriBuilder#uri(java.net.URI)
     */
    @Override
    public UriBuilder uri(URI uri) throws IllegalArgumentException {
        if (this.encode) {
            if (uri.getScheme() != null)
                this.scheme = uri.getScheme();
            if (uri.getHost() != null)
                this.host = uri.getHost();
            this.port = uri.getPort();
            if (uri.getUserInfo() != null)
                this.userInfo = uri.getUserInfo();
            if (uri.getPath() != null)
                this.replacePath(uri.getPath());
            if (uri.getQuery() != null)
                this.query = uri.getQuery();
            if (uri.getFragment() != null)
                this.fragment = uri.getFragment();
        } else {
            if (uri.getScheme() != null)
                this.scheme = uri.getScheme();
            if (uri.getHost() != null)
                this.host = uri.getHost();
            this.port = uri.getPort();
            if (uri.getRawUserInfo() != null)
                this.userInfo = uri.getRawUserInfo();
            if (uri.getRawPath() != null)
                this.replacePath(uri.getRawPath());
            if (uri.getRawQuery() != null)
                this.query = uri.getRawQuery();
            if (uri.getRawFragment() != null)
                this.fragment = uri.getRawFragment();
        }
        return this;
    }

    /**
     * Set the URI user-info.
     * 
     * @param userInfo
     *                the URI user-info, may contain URI template parameters
     * @return the updated UriBuilder
     * @throws IllegalArgumentException
     *                 if automatic encoding is disabled and the userInfo
     *                 contains illegal characters, or if the userInfo is null.
     * @see javax.ws.rs.core.UriBuilder#userInfo(java.lang.String)
     */
    @Override
    public UriBuilder userInfo(String userInfo) throws IllegalArgumentException {
        this.userInfo = EncodeOrCheck.userInfo(userInfo, this.encode);
        return this;
    }
}