/**
 * Copyright (c) 2000-2016 Liferay, Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.liferay.faces.bridge.context.url.internal;

import java.net.MalformedURLException;

import javax.portlet.BaseURL;
import javax.portlet.PortletRequest;
import javax.portlet.PortletURL;
import javax.portlet.faces.Bridge;
import javax.portlet.faces.Bridge.PortletPhase;
import javax.portlet.faces.BridgeUtil;

import com.liferay.faces.bridge.context.BridgeContext;
import com.liferay.faces.bridge.context.url.BridgeURI;
import com.liferay.faces.bridge.context.url.BridgeURL;
import com.liferay.faces.util.helper.BooleanHelper;


/**
 * @author  Neil Griffin
 */
public class BridgeActionURLImpl extends BridgeURLInternalBase implements BridgeURL {

	// Private Data Members
	private BridgeURI bridgeURI;
	private PortletRequest portletRequest;

	public BridgeActionURLImpl(BridgeContext bridgeContext, BridgeURI bridgeURI, String viewId) {
		super(bridgeContext, bridgeURI, viewId);
		this.bridgeURI = bridgeURI;
		this.portletRequest = bridgeContext.getPortletRequest();
	}

	@Override
	public BaseURL toBaseURL() throws MalformedURLException {

		BaseURL baseURL;
		String uri = bridgeURI.toString();

		// If this is executing during the ACTION_PHASE of the portlet lifecycle, then
		PortletPhase portletRequestPhase = BridgeUtil.getPortletRequestPhase();

		if (portletRequestPhase == Bridge.PortletPhase.ACTION_PHASE) {

			// The Mojarra MultiViewHandler.getResourceURL(String) method is implemented in such a way that it calls
			// ExternalContext.encodeActionURL(ExternalContext.encodeResourceURL(url)). The return value of those calls
			// will ultimately be passed to the ExternalContext.redirect(String) method. For this reason, need to return
			// a simple string-based representation of the URL.
			baseURL = new BaseURLNonEncodedStringImpl(uri);
		}

		// Otherwise,
		else {

			// If the URL string starts with "http" then assume that it has already been encoded and simply return the
			// URL string.
			if (uri.startsWith("http")) {
				baseURL = new BaseURLNonEncodedStringImpl(uri, getParameterMap());
			}

			// Otherwise, if the URL string starts with a "#" character, or it's an absolute URL that is external to
			// this portlet, then simply return the URL string as required by the Bridge Spec.
			else if (uri.startsWith("#") || (bridgeURI.isAbsolute() && bridgeURI.isExternal())) {

				// TCK TestPage084: encodeActionURLPoundCharTest
				baseURL = new BaseURLNonEncodedStringImpl(uri, getParameterMap());
			}

			// Otherwise, if the URL string has a "javax.portlet.faces.DirectLink" parameter with a value of "true",
			// then return an absolute path (to the path in the URL string) as required by the Bridge Spec.
			else if (bridgeURI.isExternal() || BooleanHelper.isTrueToken(getParameter(Bridge.DIRECT_LINK))) {
				baseURL = new BaseURLDirectStringImpl(uri, getParameterMap(), bridgeURI.getPath(), portletRequest);
			}
			else {
				String portletMode = removeParameter(Bridge.PORTLET_MODE_PARAMETER);
				boolean modeChanged = ((portletMode != null) && (portletMode.length() > 0));
				String secure = removeParameter(Bridge.PORTLET_SECURE_PARAMETER);
				String windowState = removeParameter(Bridge.PORTLET_WINDOWSTATE_PARAMETER);

				// Note: If the URL string starts with "portlet:", then the type of URL the portlet container creates is
				// determined by what follows the scheme, such as "portlet:action" "portlet:render" and
				// "portlet:resource".
				if (bridgeURI.isPortletScheme()) {
					String urlWithModifiedParameters = _toString(modeChanged);
					Bridge.PortletPhase urlPortletPhase = bridgeURI.getPortletPhase();

					if (urlPortletPhase == Bridge.PortletPhase.ACTION_PHASE) {
						baseURL = createActionURL(urlWithModifiedParameters);
					}
					else if (urlPortletPhase == Bridge.PortletPhase.RENDER_PHASE) {
						baseURL = createRenderURL(urlWithModifiedParameters);
					}
					else {
						baseURL = createResourceURL(urlWithModifiedParameters);
					}
				}
				else {

					String bridgeAjaxRedirectValue = removeParameter("_bridgeAjaxRedirect");
					boolean bridgeAjaxRedirect = BooleanHelper.isTrueToken(bridgeAjaxRedirectValue);
					String urlWithModifiedParameters = _toString(modeChanged);

					if (portletRequestPhase == Bridge.PortletPhase.EVENT_PHASE) {
						baseURL = new BaseURLNonEncodedStringImpl(urlWithModifiedParameters);
					}
					else if ((portletRequestPhase == Bridge.PortletPhase.RESOURCE_PHASE) && bridgeAjaxRedirect) {
						baseURL = createRenderURL(urlWithModifiedParameters);
					}
					else {
						baseURL = createActionURL(urlWithModifiedParameters);
					}
				}

				// If the URL string is self-referencing, meaning, it targets the current Faces view, then copy the
				// render parameters from the current PortletRequest to the BaseURL.
				if (isSelfReferencing()) {
					setRenderParameters(baseURL);
				}

				// If the portlet container created a PortletURL, then apply the PortletMode and WindowState to the
				// PortletURL.
				if (baseURL instanceof PortletURL) {

					PortletURL portletURL = (PortletURL) baseURL;
					setPortletModeParameter(portletMode, portletURL);
					setWindowStateParameter(windowState, portletURL);
				}

				// Apply the security.
				setSecureParameter(secure, baseURL);
			}
		}

		return baseURL;
	}

}
