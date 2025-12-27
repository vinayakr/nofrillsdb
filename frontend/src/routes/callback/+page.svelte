<script lang="ts">
    import { onMount } from 'svelte';
    import { goto } from '$app/navigation';
    import { initAuth0, loggedIn } from '$lib/auth';

    let error = '';
    let loading = true;

    onMount(async () => {
        try {
            const auth0 = await initAuth0();

            const result = await auth0.handleRedirectCallback();

            // Update logged in state
            loggedIn.set(true);

            // Check for returnTo parameter
            const urlParams = new URLSearchParams(window.location.search);
            const returnTo = urlParams.get('returnTo');

            goto(returnTo || '/', { replaceState: true });
        } catch (err) {
            error = err instanceof Error ? err.message : 'Unknown error';
            loading = false;
        }
    });
</script>

{#if loading}
    <div class="flex items-center justify-center min-h-screen">
        <div class="text-center">
            <div class="animate-spin rounded-full h-12 w-12 border-b-2 border-indigo-600 mx-auto mb-4"></div>
            <p class="text-lg text-gray-600">Completing login…</p>
        </div>
    </div>
{:else if error}
    <div>
        <h2>Login Error</h2>
        <p>{error}</p>
        <a href="/">Return to home</a>
    </div>
{/if}