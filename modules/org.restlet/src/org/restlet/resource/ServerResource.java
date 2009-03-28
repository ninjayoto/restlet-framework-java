/**
 * Copyright 2005-2009 Noelios Technologies.
 * 
 * The contents of this file are subject to the terms of one of the following
 * open source licenses: LGPL 3.0 or LGPL 2.1 or CDDL 1.0 or EPL 1.0 (the
 * "Licenses"). You can select the license that you prefer but you may not use
 * this file except in compliance with one of these Licenses.
 * 
 * You can obtain a copy of the LGPL 3.0 license at
 * http://www.opensource.org/licenses/lgpl-3.0.html
 * 
 * You can obtain a copy of the LGPL 2.1 license at
 * http://www.opensource.org/licenses/lgpl-2.1.php
 * 
 * You can obtain a copy of the CDDL 1.0 license at
 * http://www.opensource.org/licenses/cddl1.php
 * 
 * You can obtain a copy of the EPL 1.0 license at
 * http://www.opensource.org/licenses/eclipse-1.0.php
 * 
 * See the Licenses for the specific language governing permissions and
 * limitations under the Licenses.
 * 
 * Alternatively, you can obtain a royalty free commercial license with less
 * limitations, transferable or non-transferable, directly at
 * http://www.noelios.com/products/restlet-engine
 * 
 * Restlet is a registered trademark of Noelios Technologies.
 */

package org.restlet.resource;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;

import org.restlet.Application;
import org.restlet.Context;
import org.restlet.Restlet;
import org.restlet.data.ChallengeRequest;
import org.restlet.data.CookieSetting;
import org.restlet.data.Dimension;
import org.restlet.data.Language;
import org.restlet.data.Method;
import org.restlet.data.Reference;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.data.ServerInfo;
import org.restlet.data.Status;
import org.restlet.data.Tag;
import org.restlet.representation.Representation;
import org.restlet.representation.RepresentationInfo;
import org.restlet.representation.Variant;
import org.restlet.util.Series;

/**
 * Server-side resource. It is a full replacement for the {@link Resource} class
 * that will be deprecated in Restlet 1.2.<br>
 * <br>
 * Concurrency note: contrary to the {@link org.restlet.Uniform} class and its
 * main {@link Restlet} subclass where a single instance can handle several
 * calls concurrently, one instance of {@link ServerResource} is created for
 * each call handled and accessed by only one thread at a time.
 * 
 * @author Jerome Louvel
 */
public class ServerResource extends UniformResource {

    /** Indicates if annotations are supported. */
    private boolean annotated;

    /** Indicates if the identified resource exists. */
    private boolean exists;

    /** Indicates if conditional handling is enabled. */
    private boolean conditional;

    /** Indicates if content negotiation of response entities is enabled. */
    private boolean negotiated;

    /** The modifiable list of variants. */
    private volatile Map<Method, Object> variants;

    /** The modifiable list of annotation descriptors. */
    private volatile List<AnnotationInfo> annotations;

    private class AnnotationInfo {
        Method restletMethod;

        java.lang.reflect.Method javaMethod;
    }

    /**
     * Initializer block to ensure that the basic properties are initialized
     * consistently across constructors.
     */
    {
        this.annotated = true;
        this.conditional = true;
        this.exists = true;
        this.negotiated = true;
        this.variants = null;
    }

    /**
     * Default constructor. Note that the
     * {@link #init(Context, Request, Response)}() method will be invoked right
     * after the creation of the resource.
     */
    public ServerResource() {
    }

    /**
     * Deletes the resource and all its representations.<br>
     * <br>
     * This method is only invoked if content negotiation has been disabled as
     * indicated by the {@link #isNegotiated()}, otherwise the
     * {@link #delete(Variant)} method is invoked.<br>
     * <br>
     * The default behavior is to set the response status to
     * {@link Status#CLIENT_ERROR_METHOD_NOT_ALLOWED}.
     * 
     * @return The optional response entity.
     * @throws ResourceException
     * @see <a
     *      href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec9.html#sec9.7">HTTP
     *      DELETE method</a>
     */
    public Representation delete() throws ResourceException {
        setStatus(Status.CLIENT_ERROR_METHOD_NOT_ALLOWED);
        return null;
    }

    /**
     * Deletes the resource and all its representations. A variant parameter is
     * passed to indicate which representation should be returned if any.<br>
     * <br>
     * This method is only invoked if content negotiation has been enabled as
     * indicated by the {@link #isNegotiated()}, otherwise the {@link #delete()}
     * method is invoked.<br>
     * <br>
     * The default behavior is to set the response status to
     * {@link Status#CLIENT_ERROR_METHOD_NOT_ALLOWED}.
     * 
     * @param variant
     *            The variant of the response entity.
     * @return The optional response entity.
     * @throws ResourceException
     * @see <a
     *      href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec9.html#sec9.7">HTTP
     *      DELETE method</a>
     */
    public Representation delete(Variant variant) throws ResourceException {
        setStatus(Status.CLIENT_ERROR_METHOD_NOT_ALLOWED);
        return null;
    }

    /**
     * Describes the available variants to help client-side content negotiation.
     * Return null by default.
     * 
     * @return The description of available variants.
     */
    protected Representation describeVariants() {
        Representation result = null;

        // The list of all variants is transmitted to the client
        // final ReferenceList refs = new ReferenceList(variants.size());
        // for (final Variant variant : variants) {
        // if (variant.getIdentifier() != null) {
        // refs.add(variant.getIdentifier());
        // }
        // }
        //
        // result = refs.getTextRepresentation();
        return result;
    }

    /**
     * Verifies the optional request conditions and continue the processing if
     * possible. Note that in order to evaluate those conditions,
     * {@link #getInfo()} or {@link #getInfo(Variant)} methods might be invoked.
     * 
     * @param method
     *            The method to handle.
     * @return The response entity.
     * @throws ResourceException
     */
    protected Representation doConditionalHandle(Method method)
            throws ResourceException {
        Representation result = null;

        if (!isExists() && getConditions().hasSome()
                && getConditions().getMatch().contains(Tag.ALL)) {
            setStatus(Status.CLIENT_ERROR_PRECONDITION_FAILED,
                    "A non existing resource can't match any tag.");
        } else {
            RepresentationInfo representationInfo = null;

            if (isNegotiated()) {
                representationInfo = doNegotiatedHandle(Method.GET);
            } else {
                representationInfo = doHandle(Method.GET);
            }

            if (representationInfo == null) {
                if ((getStatus() == null)
                        || (getStatus().isSuccess() && !Status.SUCCESS_NO_CONTENT
                                .equals(getStatus()))) {
                    setStatus(Status.CLIENT_ERROR_NOT_FOUND);
                } else {
                    // Keep the current status as the developer might prefer a
                    // special status like 'method not authorized'.
                }
            } else if (getRequest().getConditions().hasSome()) {
                Status status = getConditions().getStatus(getMethod(),
                        representationInfo);

                if (status != null) {
                    setStatus(status);
                }
            }

            if ((getStatus() != null) && getStatus().isSuccess()) {
                // Conditions where passed successfully.
                // Continue the normal processing
                if (representationInfo instanceof Representation) {
                    result = (Representation) representationInfo;
                } else {
                    if (isNegotiated()) {
                        result = doNegotiatedHandle(method);
                    } else {
                        result = doHandle(method);
                    }
                }
            }
        }

        return result;
    }

    /**
     * Handles a call without content negotiation of the response entity. The
     * default behavior is to dispatch the call to one of the {@link #get()},
     * {@link #post(Representation)}, {@link #put(Representation)},
     * {@link #delete()}, {@link #head()} or {@link #options()} methods.
     * 
     * @param method
     *            The method to handle.
     * @return The response entity.
     * @throws ResourceException
     */
    protected Representation doHandle(Method method) throws ResourceException {
        Representation result = null;

        if (method == null) {
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "No method specified");
        } else {
            if (method.equals(Method.GET)) {
                result = get();
            } else if (method.equals(Method.POST)) {
                result = post(getRequest().getEntity());
            } else if (method.equals(Method.PUT)) {
                result = put(getRequest().getEntity());
            } else if (method.equals(Method.DELETE)) {
                result = delete();
            } else if (method.equals(Method.HEAD)) {
                result = head();
            } else if (method.equals(Method.OPTIONS)) {
                result = options();
            } else {
                setStatus(Status.CLIENT_ERROR_METHOD_NOT_ALLOWED);
            }
        }

        return result;
    }

    /**
     * Handles a call with content negotiation of the response entity. The
     * default behavior is to dispatch the call to one of the
     * {@link #get(Variant)}, {@link #post(Representation,Variant)},
     * {@link #put(Representation,Variant)}, {@link #delete(Variant)},
     * {@link #head(Variant)} or {@link #options(Variant)} methods.
     * 
     * @param method
     *            The method to handle.
     * @return The response entity.
     * @throws ResourceException
     */
    protected Representation doHandle(Method method, Variant variant)
            throws ResourceException {
        Representation result = null;

        if (method == null) {
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "No method specified");
        } else {
            if (method.equals(Method.GET)) {
                result = get(variant);
            } else if (method.equals(Method.POST)) {
                result = post(getRequest().getEntity(), variant);
            } else if (method.equals(Method.PUT)) {
                result = put(getRequest().getEntity(), variant);
            } else if (method.equals(Method.DELETE)) {
                result = delete(variant);
            } else if (method.equals(Method.HEAD)) {
                result = head(variant);
            } else if (method.equals(Method.OPTIONS)) {
                result = options(variant);
            } else {
                setStatus(Status.CLIENT_ERROR_METHOD_NOT_ALLOWED);
            }
        }

        return result;
    }

    /**
     * Handles the content negotiation of a call.
     * 
     * @param method
     *            The method to handle.
     * @return The response entity.
     * @throws ResourceException
     */
    protected Representation doNegotiatedHandle(Method method)
            throws ResourceException {
        Representation result = null;
        Variant preferredVariant = getPreferredVariant();

        if (preferredVariant == null) {
            // No variant was found matching the client preferences
            setStatus(Status.CLIENT_ERROR_NOT_ACCEPTABLE);
            result = describeVariants();
        } else {
            // Update the variant dimensions used for content negotiation
            updateDimensions();
            result = doHandle(method, preferredVariant);
        }

        return result;
    }

    /**
     * Returns a full representation.<br>
     * <br>
     * This method is only invoked if content negotiation has been disabled as
     * indicated by the {@link #isNegotiated()}, otherwise the
     * {@link #get(Variant)} method is invoked.<br>
     * <br>
     * The default behavior is to set the response status to
     * {@link Status#CLIENT_ERROR_METHOD_NOT_ALLOWED}.
     * 
     * @return The resource's representation.
     * @throws ResourceException
     * @see <a
     *      href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec9.html#sec9.3">HTTP
     *      GET method</a>
     */
    public Representation get() throws ResourceException {
        setStatus(Status.CLIENT_ERROR_METHOD_NOT_ALLOWED);
        return null;
    }

    /**
     * Returns a full representation for a given variant. The default
     * implementation directly returns the variant if it is already an instance
     * of {@link Representation}. In other cases, you need to override this
     * method in order to provide your own implementation.<br>
     * <br>
     * This method is only invoked if content negotiation has been enabled as
     * indicated by the {@link #isNegotiated()}, otherwise the {@link #get()}
     * method is invoked.<br>
     * <br>
     * The default behavior is to set the response status to
     * {@link Status#CLIENT_ERROR_METHOD_NOT_ALLOWED}.
     * 
     * @param variant
     *            The variant whose full representation must be returned.
     * @return The resource's representation.
     * @see #getVariants()
     * @throws ResourceException
     */
    public Representation get(Variant variant) throws ResourceException {
        Representation result = null;

        if (variant instanceof Representation) {
            result = (Representation) variant;
        }

        return result;
    }

    /**
     * Returns information about the resource's representation. Those metadata
     * are important for conditional method processing. The advantage over the
     * complete {@link Representation} class is that it is much lighter to
     * create.<br>
     * <br>
     * By default, the {@link #get()} method is invoked.
     * 
     * @return Information about the resource's representation.
     * @throws ResourceException
     */
    public RepresentationInfo getInfo() throws ResourceException {
        return get();
    }

    /**
     * Returns information about the resource's representation. Those metadata
     * are important for conditional method processing. The advantage over the
     * complete {@link Representation} class is that it is much lighter to
     * create.<br>
     * <br>
     * By default, the {@link #get(Variant)} method is invoked.
     * 
     * @param variant
     *            The variant whose representation information must be returned.
     * @return Information about the resource's representation.
     * @throws ResourceException
     */
    public RepresentationInfo getInfo(Variant variant) throws ResourceException {
        return get(variant);
    }

    /**
     * Returns the preferred variant according to the client preferences
     * specified in the request.
     * 
     * @return The preferred variant.
     */
    public Variant getPreferredVariant() {
        Variant result = null;
        List<Variant> variants = (List<Variant>) getVariants().get(getMethod());

        if ((variants != null) && (!variants.isEmpty())) {
            Language language = null;
            // Compute the preferred variant. Get the default language
            // preference from the Application (if any).
            final Application app = Application.getCurrent();

            if (app != null) {
                language = app.getMetadataService().getDefaultLanguage();
            }

            result = getClientInfo().getPreferredVariant(variants, language);

        }

        return result;
    }

    /**
     * Returns the modifiable map of variant declarations. Creates a new
     * instance if no one has been set. A variant can be a purely descriptive
     * representation, with no actual content that can be served. It can also be
     * a full representation in case a resource has only one variant or if the
     * initialization cost is very low.<br>
     * <br>
     * Note that the order in which the variants are inserted in the list
     * matters. For example, if the client has no preference defined, or if the
     * acceptable variants have the same quality level for the client, the first
     * acceptable variant in the list will be returned.<br>
     * <br>
     * It is recommended to not override this method and to simply use it at
     * construction time to initialize the list of available variants.
     * Overriding it may reconstruct the list for each call which can be
     * expensive.
     * 
     * @return The list of variants.
     * @see #getRepresentation(Variant)
     */
    public Map<Method, Object> getVariants() {
        // Lazy initialization with double-check.
        Map<Method, Object> v = this.variants;
        if (v == null) {
            synchronized (this) {
                v = this.variants;
                if (v == null) {
                    this.variants = v = new TreeMap<Method, Object>();
                }
            }
        }
        return v;
    }

    /**
     * 
     */
    @SuppressWarnings("unchecked")
    private void discoverAnnotations() {
        String attributeName = "org.restlet.resource.ServerResource.annotations."
                + getClass().getCanonicalName();

        if (getContext() != null) {
            this.annotations = (List<AnnotationInfo>) getContext()
                    .getAttributes().get(attributeName);
        }

        if (this.annotations == null) {
            this.annotations = new ArrayList<AnnotationInfo>();

            if (getContext() != null) {
                getContext().getAttributes().put(attributeName,
                        this.annotations);
            }

            for (java.lang.reflect.Method method : getClass().getMethods()) {
                for (Annotation annotation : method.getAnnotations()) {

                    Annotation methodAnnotation = annotation.annotationType()
                            .getAnnotation(org.restlet.engine.Method.class);

                    if (methodAnnotation != null) {
                        Method restletMethod = Method
                                .valueOf(((org.restlet.engine.Method) methodAnnotation)
                                        .value());

                        String toString = annotation.toString();
                        int startIndex = annotation.annotationType()
                                .getCanonicalName().length() + 8;
                        int endIndex = toString.length() - 1;
                        String value = toString.substring(startIndex, endIndex);
                        System.out.println(value);
                    }
                }
            }

        }
    }

    /**
     * Handles any call to this resource. The default implementation check the
     * {@link #isConditional()} and {@link #isNegotiated()} method to determine
     * which one of the {@link #doConditionalHandle()},
     * {@link #doNegotiatedHandle()} and {@link #doHandle()} methods should be
     * invoked. It also catches any {@link ResourceException} thrown and updates
     * the response status using the
     * {@link #setStatus(Status, Throwable, String)} method.<br>
     * <br>
     * After handling, if the status is set to
     * {@link Status#CLIENT_ERROR_METHOD_NOT_ALLOWED}, then
     * {@link #updateAllowedMethods()} is invoked to give the resource a chance
     * to inform the client about the allowed methods.
     * 
     * @return The response entity.
     */
    @Override
    public Representation handle() {
        Representation result = null;

        try {
            if (isAnnotated()) {
                discoverAnnotations();
            }

            if (isConditional()) {
                result = doConditionalHandle(getMethod());
            } else if (isNegotiated()) {
                result = doNegotiatedHandle(getMethod());
            } else {
                result = doHandle(getMethod());
            }

            getResponse().setEntity(result);

            if (Status.CLIENT_ERROR_METHOD_NOT_ALLOWED.equals(getStatus())) {
                updateAllowedMethods();
            }
        } catch (ResourceException re) {
            setStatus(re.getStatus(), re.getCause(), re.getLocalizedMessage());
        }

        return result;
    }

    /**
     * Returns a representation whose metadata will be returned to the client.<br>
     * <br>
     * This method is only invoked if content negotiation has been disabled as
     * indicated by the {@link #isNegotiated()}, otherwise the
     * {@link #get(Variant)} method is invoked.<br>
     * <br>
     * The default behavior is to set the response status to
     * {@link Status#CLIENT_ERROR_METHOD_NOT_ALLOWED}.
     * 
     * @return The resource's representation.
     * @throws ResourceException
     * @see <a
     *      href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec9.html#sec9.3">HTTP
     *      GET method</a>
     */
    public Representation head() throws ResourceException {
        return get();
    }

    /**
     * Returns a representation whose metadata will be returned to the client.
     * The default implementation directly returns the variant if it is already
     * an instance of {@link Representation}. In other cases, you need to
     * override this method in order to provide your own implementation.<br>
     * <br>
     * This method is only invoked if content negotiation has been enabled as
     * indicated by the {@link #isNegotiated()}, otherwise the {@link #get()}
     * method is invoked.<br>
     * <br>
     * The default behavior is to set the response status to
     * {@link Status#CLIENT_ERROR_METHOD_NOT_ALLOWED}.
     * 
     * @param variant
     *            The variant whose full representation must be returned.
     * @return The resource's representation.
     * @see #getVariants()
     * @throws ResourceException
     */
    public Representation head(Variant variant) throws ResourceException {
        return get(variant);
    }

    /**
     * Indicates if annotations are supported.
     * 
     * @return True if annotations are supported.
     */
    public boolean isAnnotated() {
        return annotated;
    }

    /**
     * Indicates if conditional handling is enabled.
     * 
     * @return True if conditional handling is enabled.
     */
    public boolean isConditional() {
        return conditional;
    }

    /**
     * Indicates if the identified resource exists.
     * 
     * @return True if the identified resource exists.
     */
    public boolean isExists() {
        return exists;
    }

    /**
     * Indicates if the authenticated subject associated to the current request
     * is in the given role name.
     * 
     * @param roleName
     *            The role name to test.
     * @return True if the authenticated subject is in the given role.
     */
    public boolean isInRole(String roleName) {
        return getClientInfo().isInRole(getApplication().findRole(roleName));
    }

    /**
     * Indicates if content negotiation of response entities is enabled. The
     * default value is 'false'.
     * 
     * @return True if content negotiation of response entities is enabled.
     */
    public boolean isNegotiated() {
        return this.negotiated;
    }

    /**
     * Indicates the communication options available for this resource. The
     * default behavior is to set the response status to
     * {@link Status#CLIENT_ERROR_METHOD_NOT_ALLOWED}.
     * 
     * @return The optional response entity.
     */
    public Representation options() throws ResourceException {
        setStatus(Status.CLIENT_ERROR_METHOD_NOT_ALLOWED);
        return null;
    }

    /**
     * Indicates the communication options available for this resource. The
     * default behavior is to set the response status to
     * {@link Status#CLIENT_ERROR_METHOD_NOT_ALLOWED}.
     * 
     * @param variant
     *            The variant of the response entity.
     * @return The optional response entity.
     */
    public Representation options(Variant variant) throws ResourceException {
        setStatus(Status.CLIENT_ERROR_METHOD_NOT_ALLOWED);
        return null;
    }

    /**
     * Posts a representation to the resource at the target URI reference. The
     * default behavior is to set the response status to
     * {@link Status#CLIENT_ERROR_METHOD_NOT_ALLOWED}.
     * 
     * @param entity
     *            The posted entity.
     * @return The optional response entity.
     * @throws ResourceException
     * @see <a
     *      href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec9.html#sec9.5">HTTP
     *      POST method</a>
     */
    public Representation post(Representation entity) throws ResourceException {
        setStatus(Status.CLIENT_ERROR_METHOD_NOT_ALLOWED);
        return null;
    }

    /**
     * Posts a representation to the resource at the target URI reference. The
     * default behavior is to set the response status to
     * {@link Status#CLIENT_ERROR_METHOD_NOT_ALLOWED}.
     * 
     * @param entity
     *            The posted entity.
     * @param variant
     *            The variant of the response entity.
     * @return The optional result entity.
     * @throws ResourceException
     * @see <a
     *      href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec9.html#sec9.5">HTTP
     *      POST method</a>
     */
    public Representation post(Representation entity, Variant variant)
            throws ResourceException {
        setStatus(Status.CLIENT_ERROR_METHOD_NOT_ALLOWED);
        return null;
    }

    /**
     * Creates or updates a resource with the given representation as new state
     * to be stored. The default behavior is to set the response status to
     * {@link Status#CLIENT_ERROR_METHOD_NOT_ALLOWED}.
     * 
     * @param representation
     *            The representation to store.
     * @return The optional result entity.
     * @throws ResourceException
     * @see <a
     *      href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec9.html#sec9.6">HTTP
     *      PUT method</a>
     */
    public Representation put(Representation representation)
            throws ResourceException {
        setStatus(Status.CLIENT_ERROR_METHOD_NOT_ALLOWED);
        return null;
    }

    /**
     * Creates or updates a resource with the given representation as new state
     * to be stored. The default behavior is to set the response status to
     * {@link Status#CLIENT_ERROR_METHOD_NOT_ALLOWED}.
     * 
     * @param representation
     *            The representation to store.
     * @param variant
     *            The variant of the response entity.
     * @return The optional result entity.
     * @throws ResourceException
     * @see <a
     *      href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec9.html#sec9.6">HTTP
     *      PUT method</a>
     */
    public Representation put(Representation representation, Variant variant)
            throws ResourceException {
        setStatus(Status.CLIENT_ERROR_METHOD_NOT_ALLOWED);
        return null;
    }

    /**
     * Sets the set of methods allowed on the requested resource. The set
     * instance set must be thread-safe (use {@link CopyOnWriteArraySet} for
     * example.
     * 
     * @param allowedMethods
     *            The set of methods allowed on the requested resource.
     * @see Response#setAllowedMethods(Set)
     */
    public void setAllowedMethods(Set<Method> allowedMethods) {
        getResponse().setAllowedMethods(allowedMethods);
    }

    /**
     * Indicates if annotations are supported.
     * 
     * @param annotated
     *            Indicates if annotations are supported.
     */
    public void setAnnotated(boolean annotated) {
        this.annotated = annotated;
    }

    /**
     * Sets the authentication request sent by an origin server to a client.
     * 
     * @param request
     *            The authentication request sent by an origin server to a
     *            client.
     * @see Response#setChallengeRequest(ChallengeRequest)
     */
    public void setChallengeRequest(ChallengeRequest request) {
        getResponse().setChallengeRequest(request);
    }

    /**
     * Sets the list of authentication requests sent by an origin server to a
     * client. The list instance set must be thread-safe (use
     * {@link CopyOnWriteArrayList} for example.
     * 
     * @param requests
     *            The list of authentication requests sent by an origin server
     *            to a client.
     * @see Response#setChallengeRequests(List)
     */
    public void setChallengeRequests(List<ChallengeRequest> requests) {
        getResponse().setChallengeRequests(requests);
    }

    /**
     * Indicates if conditional handling is enabled.
     * 
     * @param conditional
     *            True if conditional handling is enabled.
     */
    public void setConditional(boolean conditional) {
        this.conditional = conditional;
    }

    /**
     * Sets the cookie settings provided by the server.
     * 
     * @param cookieSettings
     *            The cookie settings provided by the server.
     * @see Response#setCookieSettings(Series)
     */
    public void setCookieSettings(Series<CookieSetting> cookieSettings) {
        getResponse().setCookieSettings(cookieSettings);
    }

    /**
     * Sets the set of dimensions on which the response entity may vary. The set
     * instance set must be thread-safe (use {@link CopyOnWriteArraySet} for
     * example.
     * 
     * @param dimensions
     *            The set of dimensions on which the response entity may vary.
     * @see Response#setDimensions(Set)
     */
    public void setDimensions(Set<Dimension> dimensions) {
        getResponse().setDimensions(dimensions);
    }

    /**
     * Indicates if the identified resource exists.
     * 
     * @param exists
     *            Indicates if the identified resource exists.
     */
    public void setExists(boolean exists) {
        this.exists = exists;
    }

    /**
     * Sets the reference that the client should follow for redirections or
     * resource creations.
     * 
     * @param locationRef
     *            The reference to set.
     * @see Response#setLocationRef(Reference)
     */
    public void setLocationRef(Reference locationRef) {
        getResponse().setLocationRef(locationRef);
    }

    /**
     * Sets the reference that the client should follow for redirections or
     * resource creations. If you pass a relative location URI, it will be
     * resolved with the current base reference of the request's resource
     * reference (see {@link Request#getResourceRef()} and
     * {@link Reference#getBaseRef()}.
     * 
     * @param locationUri
     *            The URI to set.
     * @see Response#setLocationRef(String)
     */
    public void setLocationRef(String locationUri) {
        getResponse().setLocationRef(locationUri);
    }

    /**
     * Indicates if content negotiation of response entities is enabled.
     * 
     * @param negotiateContent
     *            True if content negotiation of response entities is enabled.
     */
    public void setNegotiated(boolean negotiateContent) {
        this.negotiated = negotiateContent;
    }

    /**
     * Sets the server-specific information.
     * 
     * @param serverInfo
     *            The server-specific information.
     * @see Response#setServerInfo(ServerInfo)
     */
    public void setServerInfo(ServerInfo serverInfo) {
        getResponse().setServerInfo(serverInfo);
    }

    /**
     * Sets the status.
     * 
     * @param status
     *            The status to set.
     * @see Response#setStatus(Status)
     */
    public void setStatus(Status status) {
        getResponse().setStatus(status);
    }

    /**
     * Sets the status.
     * 
     * @param status
     *            The status to set.
     * @param message
     *            The status message.
     * @see Response#setStatus(Status, String)
     */
    public void setStatus(Status status, String message) {
        getResponse().setStatus(status, message);
    }

    /**
     * Sets the status.
     * 
     * @param status
     *            The status to set.
     * @param throwable
     *            The related error or exception.
     * @see Response#setStatus(Status, Throwable)
     */
    public void setStatus(Status status, Throwable throwable) {
        getResponse().setStatus(status, throwable);
    }

    /**
     * Sets the status.
     * 
     * @param status
     *            The status to set.
     * @param throwable
     *            The related error or exception.
     * @param message
     *            The status message.
     * @see Response#setStatus(Status, Throwable, String)
     */
    public void setStatus(Status status, Throwable throwable, String message) {
        getResponse().setStatus(status, throwable, message);
    }

    /**
     * Sets the modifiable map of variant declarations.
     * 
     * @param variants
     *            The modifiable map of variant declarations.
     */
    public void setVariants(Map<Method, Object> variants) {
        this.variants = variants;
    }

    /**
     * Invoked when the list of allowed methods needs to be updated. The
     * {@link #getAllowedMethods()} or the {@link #setAllowedMethods(Set)}
     * methods should be used. The default implementation does nothing.
     * 
     */
    protected void updateAllowedMethods() {
    }

    /**
     * Update the dimensions that were used for content negotiation. By default,
     * it adds the {@link Dimension#CHARACTER_SET}, {@link Dimension#ENCODING},
     * {@link Dimension#LANGUAGE}and {@link Dimension#MEDIA_TYPE} constants.
     */
    protected void updateDimensions() {
        getDimensions().add(Dimension.CHARACTER_SET);
        getDimensions().add(Dimension.ENCODING);
        getDimensions().add(Dimension.LANGUAGE);
        getDimensions().add(Dimension.MEDIA_TYPE);
    }

}
