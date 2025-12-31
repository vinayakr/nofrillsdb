<script lang="ts">
    import 'mapbox-gl/dist/mapbox-gl.css';
    import '../app.css';
    import { onMount } from 'svelte';
    import { page } from '$app/stores';
    import {
        login, logout, getUser, loggedIn,
        handleRedirectIfPresent, signUp
    } from '$lib/auth';

    let user: any = null;
    let isAuthenticated = false;
    let mobileMenuOpen = false;

    loggedIn.subscribe(async value => {
        isAuthenticated = value;
        if (value && !user) {
            // If logged in but no user data, fetch it
            try {
                user = await getUser();
            } catch {
                user = null;
            }
        } else if (!value) {
            user = null;
        }
    });

    onMount(async () => {
        // Don't handle redirect if we're on the callback page
        if (!window.location.pathname.includes('/callback')) {
            try {
                user = await getUser();
                if (user) {
                    loggedIn.set(true);
                }
            } catch {
                user = null;
                loggedIn.set(false);
            }
        }
    });

    function handleAuth() {
        if (isAuthenticated) {
            logout();
            user = null;
        } else {
            login();
        }
    }


    function handleSignUp() {
        signUp('/register');
    }

    function toggleMobileMenu() {
        mobileMenuOpen = !mobileMenuOpen;
    }
</script>

<nav class="bg-white shadow-lg border-b">
    <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="flex justify-between h-16">
            <div class="flex items-center">
                <a href="/" class="flex-shrink-0 flex items-center">
                    <span class="text-xl font-bold text-gray-900">No Frills DB</span>
                </a>

                <!-- Desktop Navigation -->
                <div class="hidden md:ml-10 md:flex md:space-x-8">
                    <a href="/" class="{$page.url.pathname === '/' ? 'border-indigo-500 text-gray-900' : 'border-transparent text-gray-500 hover:border-gray-300 hover:text-gray-700'} inline-flex items-center px-1 pt-1 border-b-2 text-sm font-medium transition-colors">
                        Home
                    </a>
                    {#if isAuthenticated}
                        <a href="/databases" class="{$page.url.pathname === '/databases' ? 'border-indigo-500 text-gray-900' : 'border-transparent text-gray-500 hover:border-gray-300 hover:text-gray-700'} inline-flex items-center px-1 pt-1 border-b-2 text-sm font-medium transition-colors">
                            Databases
                        </a>
                        <a href="/profile" class="{$page.url.pathname === '/profile' ? 'border-indigo-500 text-gray-900' : 'border-transparent text-gray-500 hover:border-gray-300 hover:text-gray-700'} inline-flex items-center px-1 pt-1 border-b-2 text-sm font-medium transition-colors">
                            Profile
                        </a>
                    {/if}
                </div>
            </div>

            <!-- Desktop Auth Button -->
            <div class="hidden md:flex md:items-center">
                {#if user}
                    <div class="flex items-center space-x-4">
                        <span class="text-sm text-gray-700">Welcome, {user.name || user.email}</span>
                        <button
                            on:click={handleAuth}
                            class="bg-indigo-600 hover:bg-indigo-700 text-white px-4 py-2 rounded-md text-sm font-medium transition-colors"
                        >
                            Logout
                        </button>
                    </div>
                {:else}
                    <div class="flex items-center space-x-2">
                        <button
                                on:click={handleSignUp}
                                class="bg-indigo-600 hover:bg-indigo-700 text-white px-4 py-2 rounded-md text-sm font-medium transition-colors"
                        >
                            Sign Up
                        </button>
                        <button
                            on:click={handleAuth}
                            class="bg-indigo-600 hover:bg-indigo-700 text-white px-4 py-2 rounded-md text-sm font-medium transition-colors"
                        >
                            Login
                        </button>
                    </div>
                {/if}
            </div>

            <!-- Mobile menu button -->
            <div class="md:hidden flex items-center">
                <button
                    on:click={toggleMobileMenu}
                    class="inline-flex items-center justify-center p-2 rounded-md text-gray-400 hover:text-gray-500 hover:bg-gray-100 focus:outline-none focus:ring-2 focus:ring-inset focus:ring-indigo-500"
                    aria-label="Toggle mobile menu"
                >
                    <svg class="{mobileMenuOpen ? 'hidden' : 'block'} h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 6h16M4 12h16M4 18h16" />
                    </svg>
                    <svg class="{mobileMenuOpen ? 'block' : 'hidden'} h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
                    </svg>
                </button>
            </div>
        </div>
    </div>

    <!-- Mobile menu -->
    <div class="{mobileMenuOpen ? 'block' : 'hidden'} md:hidden">
        <div class="pt-2 pb-3 space-y-1">
            <a href="/" class="{$page.url.pathname === '/' ? 'bg-indigo-50 border-indigo-500 text-indigo-700' : 'border-transparent text-gray-500 hover:bg-gray-50 hover:border-gray-300 hover:text-gray-700'} block pl-3 pr-4 py-2 border-l-4 text-base font-medium">
                Home
            </a>
            <a href="/deals" class="{$page.url.pathname.startsWith('/deals') ? 'bg-indigo-50 border-indigo-500 text-indigo-700' : 'border-transparent text-gray-500 hover:bg-gray-50 hover:border-gray-300 hover:text-gray-700'} block pl-3 pr-4 py-2 border-l-4 text-base font-medium">
                Deals
            </a>
            {#if isAuthenticated}
                <a href="/databases" class="{$page.url.pathname === '/databases' ? 'bg-indigo-50 border-indigo-500 text-indigo-700' : 'border-transparent text-gray-500 hover:bg-gray-50 hover:border-gray-300 hover:text-gray-700'} block pl-3 pr-4 py-2 border-l-4 text-base font-medium">
                    Databases
                </a>
                <a href="/profile" class="{$page.url.pathname === '/profile' ? 'bg-indigo-50 border-indigo-500 text-indigo-700' : 'border-transparent text-gray-500 hover:bg-gray-50 hover:border-gray-300 hover:text-gray-700'} block pl-3 pr-4 py-2 border-l-4 text-base font-medium">
                    Profile
                </a>
            {/if}
        </div>
        <div class="pt-4 pb-3 border-t border-gray-200">
            {#if user}
                <div class="flex items-center px-4">
                    <div>
                        <div class="text-base font-medium text-gray-800">{user.name || 'User'}</div>
                        <div class="text-sm font-medium text-gray-500">{user.email}</div>
                    </div>
                </div>
                <div class="mt-3 space-y-1">
                    <button
                        on:click={handleAuth}
                        class="block w-full text-left px-4 py-2 text-base font-medium text-gray-500 hover:text-gray-800 hover:bg-gray-100"
                    >
                        Logout
                    </button>
                </div>
            {:else}
                <div class="px-4">
                    <button
                        on:click={handleAuth}
                        class="w-full bg-indigo-600 hover:bg-indigo-700 text-white px-4 py-2 rounded-md text-base font-medium transition-colors"
                    >
                        Login
                    </button>
                </div>
            {/if}
        </div>
    </div>
</nav>

<main class="min-h-screen bg-gray-50">

    <slot />
</main>

<style>
    :global(html,body,#app) {
        height: 100%;
        margin: 0;
        padding: 0;
    }
    :global(body) {
        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
    }
</style>