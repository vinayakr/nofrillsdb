<script lang="ts">
    import { onMount } from 'svelte';
    import { goto } from '$app/navigation';
    import { signUp } from '$lib/auth';

    const STORAGE_KEY = 'nofrillsdb_demo_token';

    let loading = true;
    let creating = false;
    let error = '';

    let token = '';
    let role = '';
    let password = '';
    let databaseName = '';
    let storageExceeded = false;
    let sizeBytes = 0;
    let storageLimitBytes = 104_857_600; // 100MB default

    let copied = { role: false, password: false, db: false, jdbc: false };

    const jdbcEndpoint = import.meta.env.VITE_JDBC_PWD_ENDPOINT;

    $: jdbcUrl = jdbcEndpoint
        ? `jdbc:postgresql://${jdbcEndpoint}/${databaseName}?user=${role}&password=${password}`
        : '';

    $: psqlCmd = jdbcEndpoint
        ? `psql "host=${jdbcEndpoint.split(':')[0]} port=${jdbcEndpoint.split(':')[1] ?? 5432} dbname=${databaseName} user=${role} password=${password}"`
        : '';

    $: storagePct = Math.min((sizeBytes / storageLimitBytes) * 100, 100);
    $: storageBarColor = storagePct > 90 ? 'bg-red-500' : storagePct > 70 ? 'bg-amber-500' : 'bg-indigo-500';

    function formatBytes(bytes: number): string {
        if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
        if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(2)} MB`;
        return `${(bytes / (1024 * 1024 * 1024)).toFixed(2)} GB`;
    }

    async function startDemo() {
        creating = true;
        error = '';
        try {
            const res = await fetch('/api/demo', { method: 'POST' });
            if (!res.ok) {
                const body = await res.json().catch(() => ({}));
                throw new Error(body.message || 'Failed to create demo environment');
            }
            const data = await res.json();
            localStorage.setItem(STORAGE_KEY, data.token);
            applySession(data);
        } catch (e: any) {
            error = e.message || 'Failed to start demo';
        } finally {
            creating = false;
        }
    }

    async function loadExistingSession(savedToken: string) {
        const res = await fetch(`/api/demo/${savedToken}`);
        if (!res.ok) {
            localStorage.removeItem(STORAGE_KEY);
            return false;
        }
        const data = await res.json();
        applySession(data);
        return true;
    }

    function applySession(data: any) {
        token = data.token;
        role = data.role;
        password = data.password;
        databaseName = data.databaseName;
        storageExceeded = data.storageExceeded ?? false;
        sizeBytes = data.sizeBytes ?? 0;
        storageLimitBytes = data.storageLimitBytes ?? 524_288_000;
    }

    function resetDemo() {
        localStorage.removeItem(STORAGE_KEY);
        token = '';
        role = '';
        password = '';
        databaseName = '';
        expiresAt = null;
    }

    async function copyText(text: string, key: keyof typeof copied) {
        await navigator.clipboard.writeText(text);
        copied[key] = true;
        setTimeout(() => { copied[key] = false; }, 2000);
    }

    onMount(async () => {
        const savedToken = localStorage.getItem(STORAGE_KEY);
        if (savedToken) {
            const ok = await loadExistingSession(savedToken).catch(() => false);
            if (!ok) {
                // session gone, let user start fresh
            }
        }
        loading = false;
    });
</script>

<svelte:head>
    <title>Try Demo - No Frills DB</title>
</svelte:head>

<div class="max-w-3xl mx-auto p-6">
    <div class="mb-6">
        <h1 class="text-3xl font-bold text-gray-900 mb-1">Try Demo</h1>
        <p class="text-gray-600">Get a real PostgreSQL database instantly — no sign-up required. Limited to 100 MB.</p>
    </div>

    {#if loading}
        <div class="text-center py-16 text-gray-400">Loading...</div>

    {:else if !token}
        <!-- Start demo -->
        <div class="bg-white shadow rounded-lg p-8 text-center">
            <div class="w-16 h-16 bg-indigo-100 rounded-full flex items-center justify-center mx-auto mb-4">
                <svg class="w-8 h-8 text-indigo-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 7v10c0 2.21 3.582 4 8 4s8-1.79 8-4V7M4 7c0 2.21 3.582 4 8 4s8-1.79 8-4M4 7c0-2.21 3.582-4 8-4s8 1.79 8 4" />
                </svg>
            </div>
            <h2 class="text-xl font-semibold text-gray-900 mb-2">Start Your Demo Database</h2>
            <p class="text-gray-500 mb-6">One click to get a live PostgreSQL database you can connect to right now.</p>

            {#if error}
                <div class="mb-4 p-3 bg-red-50 border border-red-200 rounded-md text-sm text-red-700">{error}</div>
            {/if}

            <button
                on:click={startDemo}
                disabled={creating}
                class="bg-indigo-600 hover:bg-indigo-700 disabled:opacity-50 text-white px-8 py-3 rounded-lg text-base font-semibold transition-colors"
            >
                {creating ? 'Creating database...' : 'Launch Demo Database'}
            </button>

            <div class="mt-6 text-sm text-gray-400">
                No credit card • No account • 100 MB limit
            </div>
        </div>

    {:else}
        <!-- Active session -->
        {#if storageExceeded}
            <div class="mb-4 p-4 bg-amber-50 border border-amber-200 rounded-md">
                <p class="text-sm font-medium text-amber-800">Your demo database has exceeded the 100 MB limit. <button on:click={signUp} class="underline font-semibold">Sign up free</button> to get a permanent account with billing-based storage.</p>
            </div>
        {/if}

        <!-- Session banner -->
        <div class="mb-4 p-3 bg-indigo-50 border border-indigo-200 rounded-md flex items-center justify-between flex-wrap gap-2">
            <span class="text-sm text-indigo-700 font-medium">Demo session active</span>
            <div class="flex items-center gap-3">
                <button on:click={signUp} class="bg-indigo-600 hover:bg-indigo-700 text-white px-3 py-1.5 rounded-md text-sm font-medium transition-colors">
                    Sign Up Free
                </button>
                <button on:click={resetDemo} class="text-sm text-gray-400 hover:text-gray-600 underline">
                    New demo
                </button>
            </div>
        </div>

        <!-- Storage bar -->
        <div class="bg-white shadow rounded-lg p-4 mb-6">
            <div class="flex items-center justify-between mb-1">
                <span class="text-sm font-medium text-gray-700">Storage used</span>
                <span class="text-sm text-gray-500">{formatBytes(sizeBytes)} / {formatBytes(storageLimitBytes)}</span>
            </div>
            <div class="w-full bg-gray-200 rounded-full h-2">
                <div
                    class="h-2 rounded-full transition-all {storageBarColor}"
                    style="width: {storagePct}%"
                ></div>
            </div>
        </div>

        <!-- Credentials -->
        <div class="bg-white shadow rounded-lg mb-6">
            <div class="px-6 py-4 border-b border-gray-200">
                <h2 class="text-lg font-medium text-gray-900">Connection Details</h2>
                <p class="text-sm text-gray-500">Use these credentials with psql, JDBC, or any PostgreSQL client</p>
            </div>
            <div class="px-6 py-4 space-y-4">

                <!-- Credential row -->
                {#each [
                    { label: 'Database', value: databaseName, key: 'db' },
                    { label: 'Role (username)', value: role, key: 'role' },
                    { label: 'Password', value: password, key: 'password' },
                ] as item}
                    <div>
                        <label class="block text-xs font-medium text-gray-500 mb-1">{item.label}</label>
                        <div class="flex items-center gap-2">
                            <code class="flex-1 text-sm bg-gray-50 border border-gray-200 px-3 py-2 rounded-md font-mono break-all">{item.value}</code>
                            <button
                                on:click={() => copyText(item.value, item.key as any)}
                                class="flex-shrink-0 p-2 text-gray-400 hover:text-gray-700 rounded-md border border-gray-200 bg-gray-50 transition-colors"
                                title="Copy"
                            >
                                {#if copied[item.key as keyof typeof copied]}
                                    <svg class="w-4 h-4 text-green-500" fill="currentColor" viewBox="0 0 20 20">
                                        <path fill-rule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clip-rule="evenodd"/>
                                    </svg>
                                {:else}
                                    <svg class="w-4 h-4" fill="currentColor" viewBox="0 0 20 20">
                                        <path d="M8 3a1 1 0 011-1h2a1 1 0 110 2H9a1 1 0 01-1-1z"></path>
                                        <path d="M6 3a2 2 0 00-2 2v11a2 2 0 002 2h8a2 2 0 002-2V5a2 2 0 00-2-2 3 3 0 01-3 3H9a3 3 0 01-3-3z"></path>
                                    </svg>
                                {/if}
                            </button>
                        </div>
                    </div>
                {/each}

                {#if jdbcUrl}
                    <div>
                        <label class="block text-xs font-medium text-gray-500 mb-1">JDBC URL</label>
                        <div class="flex items-start gap-2">
                            <code class="flex-1 text-xs bg-blue-50 border border-blue-200 text-blue-800 px-3 py-2 rounded-md font-mono break-all">{jdbcUrl}</code>
                            <button
                                on:click={() => copyText(jdbcUrl, 'jdbc')}
                                class="flex-shrink-0 p-2 text-blue-400 hover:text-blue-700 rounded-md border border-blue-200 bg-blue-50 transition-colors"
                                title="Copy"
                            >
                                {#if copied.jdbc}
                                    <svg class="w-4 h-4 text-green-500" fill="currentColor" viewBox="0 0 20 20">
                                        <path fill-rule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clip-rule="evenodd"/>
                                    </svg>
                                {:else}
                                    <svg class="w-4 h-4" fill="currentColor" viewBox="0 0 20 20">
                                        <path d="M8 3a1 1 0 011-1h2a1 1 0 110 2H9a1 1 0 01-1-1z"></path>
                                        <path d="M6 3a2 2 0 00-2 2v11a2 2 0 002 2h8a2 2 0 002-2V5a2 2 0 00-2-2 3 3 0 01-3 3H9a3 3 0 01-3-3z"></path>
                                    </svg>
                                {/if}
                            </button>
                        </div>
                    </div>

                    <div>
                        <label class="block text-xs font-medium text-gray-500 mb-1">psql command</label>
                        <div class="flex items-start gap-2">
                            <code class="flex-1 text-xs bg-gray-50 border border-gray-200 px-3 py-2 rounded-md font-mono break-all">{psqlCmd}</code>
                        </div>
                    </div>
                {/if}
            </div>
        </div>

        <!-- Sign up CTA -->
        <div class="bg-indigo-50 border border-indigo-200 rounded-lg p-6 text-center">
            <h3 class="text-lg font-semibold text-indigo-900 mb-1">Want to keep your database?</h3>
            <p class="text-sm text-indigo-700 mb-4">Sign up free — first 100 MB is always free, no credit card required.</p>
            <button on:click={signUp} class="bg-indigo-600 hover:bg-indigo-700 text-white px-6 py-2.5 rounded-lg text-sm font-semibold transition-colors">
                Create Free Account
            </button>
        </div>
    {/if}
</div>
