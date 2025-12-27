<script lang="ts">
    import { onMount } from 'svelte';
    import {
        login, getUser, getTokenSilently, getAuth0,
        handleRedirectIfPresent, initAuth0, loggedIn
    } from '$lib/auth';

    let user: any = null;
    let cookieStatus = 'unknown';
    let authToken = '';

    onMount(async () => {
        // 1) Finish the Auth0 redirect if we're on the callback URL
        await handleRedirectIfPresent();

        // 2) optional: ping backend to see if cookie session already exists
        try {
            const r = await fetch('/api/me', { credentials: 'include' });
            cookieStatus = r.ok ? 'has-cookie' : 'no-cookie';
        } catch {
            cookieStatus = 'no-cookie';
        }

        // 3) fetch user (initializes Auth0 client if needed)
        try {
            user = await getUser();
        } catch {
            user = null;
        }
    });

    async function exchange() {
        try {
            const token = await getTokenSilently();
            const res = await fetch('/api/session', {
                method: 'POST',
                headers: { Authorization: `Bearer ${token}` },
                credentials: 'include'
            });
            cookieStatus = res.ok ? 'cookie-set' : `error(${res.status})`;
        } catch (e) {
            cookieStatus = `error(${e instanceof Error ? e.message : String(e)})`;
        }
    }

    async function debugLogout() {
        try {
            localStorage.removeItem('auth0.is.authenticated');
            loggedIn.set(false);
            const auth0 = getAuth0();
            await auth0.logout({
                logoutParams: {
                    returnTo: window.location.origin + '/debug'
                }
            });
        } catch (e) {
            console.error('Logout error:', e);
        }
    }

    async function debugLogin() {
        console.log('Login button clicked');
        await login('/debug');
    }

    async function getToken() {
        try {
            console.log('Attempting to get access token...');
            console.log('Current audience:', import.meta.env.VITE_AUTH0_AUDIENCE);

            authToken = await getTokenSilently();
            console.log('Access token retrieved successfully');
        } catch (error) {
            console.error('Error getting access token silently:', error);

            try {
                console.log('Attempting to get token with popup...');
                const auth0 = await initAuth0();
                authToken = await auth0.getTokenWithPopup({
                    authorizationParams: { audience: import.meta.env.VITE_AUTH0_AUDIENCE }
                });
                console.log('Access token retrieved via popup');
            } catch (popupError) {
                console.error('Error getting access token with popup:', popupError);

                try {
                    console.log('Attempting to get ID token instead...');
                    const auth0 = getAuth0();
                    const idToken = await auth0.getIdTokenClaims();
                    console.log('ID token claims:', idToken);
                    authToken = idToken?.__raw || 'No ID token available';
                    if (authToken !== 'No ID token available') {
                        console.log('ID token retrieved successfully');
                    }
                } catch (idError) {
                    console.error('Error getting ID token:', idError);
                    authToken = `Error: ${error instanceof Error ? error.message : String(error)}`;
                }
            }
        }
    }
</script>

<div class="p-6 max-w-xl mx-auto space-y-4">
    <h1 class="text-2xl font-bold">Auth0 → Session Cookie (Debug)</h1>

    <div class="space-x-2">
        <button class="px-3 py-1 rounded bg-black text-white" on:click={debugLogin}>Login</button>
        <button class="px-3 py-1 rounded bg-gray-200" on:click={debugLogout}>Logout</button>
        <button class="px-3 py-1 rounded bg-blue-600 text-white" on:click={() => {
            console.log('Exchange button clicked');
            exchange();
        }}>Exchange token → cookie</button>
        <button class="px-3 py-1 rounded bg-green-600 text-white" on:click={getToken}>Get Auth Token</button>
        <button class="px-3 py-1 rounded bg-purple-600 text-white" on:click={async () => {
            console.log('Testing Auth0 client creation...');
            try {
                console.log('Environment check:', {
                    domain: import.meta.env.VITE_AUTH0_DOMAIN,
                    clientId: import.meta.env.VITE_AUTH0_CLIENT_ID,
                    audience: import.meta.env.VITE_AUTH0_AUDIENCE
                });

                const auth0 = await Promise.race([
                    getAuth0(),
                    new Promise((_, reject) => setTimeout(() => reject(new Error('Auth0 client creation timeout')), 5000))
                ]);
                console.log('Auth0 client created successfully');

                const isAuthenticated = await auth0.isAuthenticated();
                console.log('Is authenticated:', isAuthenticated);

                if (isAuthenticated) {
                    const user = await auth0.getUser();
                    console.log('User:', user);
                    authToken = JSON.stringify(user, null, 2);
                } else {
                    authToken = 'Not authenticated';
                }
            } catch (error) {
                console.error('Auth0 test error:', error);
                authToken = `Error: ${error.message}`;
            }
        }}>Test Auth0</button>
    </div>

    <pre class="bg-gray-50 p-3 rounded">cookieStatus: {cookieStatus}</pre>

    {#if authToken}
        <div class="border p-3 rounded">
            <div class="font-semibold">Auth Token (for PAW testing)</div>
            <textarea class="w-full h-32 font-mono text-xs p-2 border" readonly>{authToken}</textarea>
            <button class="mt-2 px-2 py-1 bg-gray-200 text-xs rounded" on:click={() => navigator.clipboard.writeText(authToken)}>Copy Token</button>
        </div>
    {/if}

    {#if user}
        <div class="border p-3 rounded">
            <div class="font-semibold">User</div>
            <pre>{JSON.stringify(user, null, 2)}</pre>
        </div>
    {/if}
</div>