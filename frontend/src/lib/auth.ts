// src/lib/auth.ts
import type { Auth0Client, Auth0ClientOptions } from '@auth0/auth0-spa-js';
import { writable } from 'svelte/store';
import { browser } from '$app/environment';

export const loggedIn = writable(false);
let auth0: Auth0Client | null = null;

export async function initAuth0() {
    if (!browser) throw new Error('Auth0 must run in the browser');
    if (auth0) return auth0;

    const { createAuth0Client } = await import('@auth0/auth0-spa-js');

    const config: Auth0ClientOptions = {
        domain: import.meta.env.VITE_AUTH0_DOMAIN!,
        clientId: import.meta.env.VITE_AUTH0_CLIENT_ID!,
        authorizationParams: {
            audience: import.meta.env.VITE_AUTH0_AUDIENCE,
            scope: 'openid profile email offline_access',
            redirect_uri: import.meta.env.VITE_AUTH0_REDIRECT_URL
        },
        cacheLocation: 'localstorage',
        useRefreshTokens: true
    };

    auth0 = await createAuth0Client(config);
    return auth0;
}

export function getAuth0(): Auth0Client {
    if (!auth0) throw new Error('Auth0 has not been initialized');
    return auth0;
}

// NEW: wrapper so your layout can import it directly
export async function getTokenSilently(refresh: boolean) {
    const auth0 = await initAuth0();
    var token = null;
    if (refresh) {
        token =  await auth0.getTokenSilently({
            cacheMode: 'off',
            authorizationParams: {
                audience: import.meta.env.VITE_AUTH0_AUDIENCE,   // your API audience
                scope: 'openid profile email offline_access'
            }
        });

    } else {
        token = await auth0.getTokenSilently({
            authorizationParams: {
                audience: import.meta.env.VITE_AUTH0_AUDIENCE
            }
        });
    }
    return token;

}

export async function getUser() {
    const c = await initAuth0();
    return c.getUser();
}

// NEW: handle the code/state after Auth0 redirects back
export async function handleRedirectIfPresent() {
    if (!browser) return false;
    if (window.location.search.includes('code=') && window.location.search.includes('state=')) {
        const c = await initAuth0();
        await c.handleRedirectCallback().catch(() => {});
        history.replaceState({}, '', '/'); // clean up callback URL
        return true;
    }
    return false;
}

export async function logout() {
    if (!browser) return;
    localStorage.removeItem('auth0.is.authenticated');
    loggedIn.set(false);
    const c = getAuth0();
    await c.logout({ logoutParams: { returnTo: window.location.origin } });
}

export async function checkAuth(): Promise<boolean> {
    const c = await initAuth0();
    try {
        await c.getTokenSilently();
        localStorage.setItem('auth0.is.authenticated', 'true');
        loggedIn.set(true);
        return true;
    } catch {
        localStorage.removeItem('auth0.is.authenticated');
        loggedIn.set(false);
        return false;
    }
}

export async function login(returnTo?: string) {
    const c = await initAuth0();
    await c.loginWithRedirect({
        authorizationParams: {
            redirect_uri: window.location.origin + '/callback' + (returnTo ? `?returnTo=${encodeURIComponent(returnTo)}` : '')
        }
    });
}

export async function signUp(returnTo?: string) {
    const c = await initAuth0();
    await c.loginWithRedirect({
        authorizationParams: {
            redirect_uri: window.location.origin + '/callback' + (returnTo ? `?returnTo=${encodeURIComponent(returnTo)}` : ''),
            screen_hint: 'signup'
        }
    });
}

// Helper function to make authenticated API requests
export async function authenticatedFetch(
    url: string,
    options: RequestInit = {},
    fetchImpl?: typeof fetch   // <-- allow DI of fetch from SvelteKit load()
): Promise<Response> {
    const token = await getTokenSilently(false); // or pass a flag as you need
    const f = fetchImpl ?? fetch;

    const headers: HeadersInit = {
        Authorization: `Bearer ${token}`,
        ...(options.headers ?? {})
    };

    // Set Content-Type for requests with body (POST, PUT, PATCH)
    if (options.body && !headers['Content-Type'] && !headers['content-type']) {
        headers['Content-Type'] = 'application/json';
    }

    return f(url, {
        ...options,
        headers
    });
}

// Helper function to get user email
export async function getUserEmail(): Promise<string> {
    const user = await getUser();
    return user?.email || '';
}

