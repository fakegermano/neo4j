/*
 * Copyright (c) 2002-2020 "Neo4j,"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.kernel.api.security;

import java.util.Map;

import org.neo4j.internal.kernel.api.security.LoginContext;
import org.neo4j.internal.kernel.api.security.SecurityContext;
import org.neo4j.kernel.api.security.exception.InvalidAuthTokenException;
import org.neo4j.kernel.lifecycle.Lifecycle;

/**
 * An AuthManager is used to do basic authentication and user management.
 */
public interface AuthManager extends Lifecycle
{
    String INITIAL_USER_NAME = "neo4j";
    String INITIAL_PASSWORD = "neo4j";

    /**
     * Log in using the provided authentication token
     *
     * NOTE: The authToken will be cleared of any credentials
     *
     * @param authToken The authentication token to login with. Typically contains principals and credentials.
     * @return An AuthSubject representing the newly logged-in user
     * @throws InvalidAuthTokenException if the authentication token is malformed
     */
    LoginContext login( Map<String,Object> authToken ) throws InvalidAuthTokenException;

    void log( String message, SecurityContext securityContext );

    /**
     * Implementation that does no authentication.
     */
    AuthManager NO_AUTH = new AuthManager()
    {
        @Override
        public void init()
        {
        }

        @Override
        public void start()
        {
        }

        @Override
        public void stop()
        {
        }

        @Override
        public void shutdown()
        {
        }

        @Override
        public LoginContext login( Map<String,Object> authToken )
        {
            AuthToken.clearCredentials( authToken );
            return LoginContext.AUTH_DISABLED;
        }

        @Override
        public void log( String message, SecurityContext securityContext )
        {
        }
    };
}
