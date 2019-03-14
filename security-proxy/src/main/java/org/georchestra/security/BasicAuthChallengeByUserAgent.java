/*
 * Copyright (C) 2009-2018 by the geOrchestra PSC
 *
 * This file is part of geOrchestra.
 *
 * geOrchestra is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * geOrchestra is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * geOrchestra.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.georchestra.security;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

/**
 * If the user-agent of the client is one of the supported user-agents then send a basic authentication request.
 * <p/>
 * Normally the security proxy is configured to redirect to cas login if a user is not authenticated and tries to access a secured page.
 * This behaviour is fine for users visiting the site via browsers, however user with other clients usch as QGis or ARcGIS that use
 * basic authentication expect a basic authentication challenge in order to authenticate.
 * <p/>
 * This filter will check the user-agent and, if a match is found, send a basic auth challenge.
 * <p/>
 * User: Jesse
 * Date: 11/7/13
 * Time: 9:44 AM
 */
public class BasicAuthChallengeByUserAgent extends BasicAuthenticationFilter {

    public BasicAuthChallengeByUserAgent(AuthenticationManager authenticationManager,
            AuthenticationEntryPoint authenticationEntryPoint) {
        super(authenticationManager, authenticationEntryPoint);
    }

    private final List<Pattern> userAgents = new ArrayList<Pattern>();
    private boolean ignoreHttps = false;
    private static final Log LOGGER = LogFactory.getLog(BasicAuthChallengeByUserAgent.class.getPackage().getName());
    private AuthenticationException _exception = new AuthenticationException("No basic authentication credentials provided") {};

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response, FilterChain chain)
                    throws IOException, ServletException {
        if(!request.getScheme().equalsIgnoreCase("https") && ! ignoreHttps) {
            LOGGER.debug("not in HTTPS, skipping filter.");
            chain.doFilter(request, response);
            return;
        }

        String auth = request.getHeader("Authorization");

        /* no valid Authorization header sent preemptively */
        if ((auth == null) || !auth.startsWith("Basic ")) {
            final String userAgent = request.getHeader("User-Agent");
            if (userAgentMatch(userAgent)) {
                /* UA matched, return a 401 directly to the client */
                LOGGER.debug("the user-agent matched and no Authorization header was sent, returning a 401.");
                getAuthenticationEntryPoint().commence(request, response, _exception);
            } else {
                LOGGER.debug("the user-agent does not match, skipping filter.");
                chain.doFilter(request, response);
            }
        } else {
            LOGGER.debug("Authorization header sent in the request, activating filter ...");
            super.doFilterInternal(request, response, chain);
        }
    }

    private boolean userAgentMatch(Object attribute) {
        if (attribute!=null) {
            for (Pattern userAgent : userAgents) {
                if (userAgent.matcher(attribute.toString()).matches()) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Set the user-agents the string is parsed as a Regex expression.
     */
    public void setUserAgents(List<String> userAgents) {
        this.userAgents.clear();
        for (String userAgent : userAgents) {
            this.userAgents.add(Pattern.compile(userAgent));
        }
    }

    /**
     * Sets the ignoreHttps flag.
     * if set to true the filter is active even on regular non-SSL HTTP requests.
     */
    public void setIgnoreHttps(boolean f) {
        ignoreHttps = f;
    }
}
