<script lang="ts">
    import { onMount } from 'svelte';
    import { page } from '$app/stores';
    import { loggedIn, getUser, authenticatedFetch, checkAuth } from '$lib/auth';
    import { goto } from '$app/navigation';

    let user: any = null;
    let databases: any[] = [];
    let certMetadata: any = null;
    let showCreateForm = false;
    let showPasswordWarning = false;
    let showCertWarning = false;
    let newDatabaseName = '';
    let creating = false;
    let generatingPassword = false;
    let generatingCert = false;
    let loading = true;
    let error = '';

    onMount(async () => {
        // Check authentication status before proceeding
        try {
            const isAuthenticated = await checkAuth();
            if (!isAuthenticated) {
                goto('/');
                return;
            }
        } catch (err) {
            console.error('Auth check failed:', err);
            goto('/');
            return;
        }

        await loadData();
    });

    async function loadData() {
        loading = true;
        error = '';

        try {
            user = await getUser();

            // Load databases from the new API endpoint
            const dbResponse = await authenticatedFetch('/api/provision/database', {
                method: 'GET'
            });

            if (dbResponse.ok) {
                databases = await dbResponse.json();
            } else {
                // Fall back to user object if API fails
                databases = user.databases || [];
            }

            // Try to load certificate metadata
            try {
                const certResponse = await authenticatedFetch('/api/provision/crt', {
                    method: 'GET'
                });

                if (certResponse.ok) {
                    certMetadata = await certResponse.json();
                } else {
                    certMetadata = null;
                }
            } catch {
                // No certificate exists yet, that's fine
                certMetadata = null;
            }
        } catch (err) {
            error = 'Failed to load data';
            console.error(err);
        } finally {
            loading = false;
        }
    }

    async function refreshData() {
        await loadData();
    }

    async function createDatabase() {
        if (!newDatabaseName.trim()) {
            error = 'Database name is required';
            return;
        }

        creating = true;
        error = '';

        try {
            const response = await authenticatedFetch('/api/provision', {
                method: 'POST',
                body: JSON.stringify({ name: newDatabaseName.trim() })
            });

            if (!response.ok) {
                const errorData = await response.json();
                throw new Error(errorData.message || 'Failed to create database');
            }

            const result = await response.json();

            // Refresh data to get updated databases list
            await refreshData();

            // Reset form
            newDatabaseName = '';
            showCreateForm = false;
        } catch (err) {
            error = err.message || 'Failed to create database';
            console.error(err);
        } finally {
            creating = false;
        }
    }

    async function generatePassword() {
        generatingPassword = true;
        error = '';

        try {
            const response = await authenticatedFetch('/api/provision/passwd', {
                method: 'GET'
            });

            if (!response.ok) {
                throw new Error('Failed to generate password');
            }

            // Download the file
            const blob = await response.blob();
            const url = window.URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = response.headers.get('Content-Disposition')?.split('filename=')[1]?.replace(/"/g, '') || 'credentials.txt';
            document.body.appendChild(a);
            a.click();
            window.URL.revokeObjectURL(url);
            document.body.removeChild(a);

            showPasswordWarning = false;

            // Refresh certificate metadata
            await refreshData();
        } catch (err) {
            error = err.message || 'Failed to generate password';
            console.error(err);
        } finally {
            generatingPassword = false;
        }
    }

    async function generateCertificate() {
        generatingCert = true;
        error = '';

        try {
            const response = await authenticatedFetch('/api/provision/crt', {
                method: 'POST'
            });

            if (!response.ok) {
                throw new Error('Failed to generate certificate');
            }

            // Download the file
            const blob = await response.blob();
            const url = window.URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = response.headers.get('Content-Disposition')?.split('filename=')[1]?.replace(/"/g, '') || 'credentials.zip';
            document.body.appendChild(a);
            a.click();
            window.URL.revokeObjectURL(url);
            document.body.removeChild(a);

            showCertWarning = false;

            // Refresh certificate metadata
            await refreshData();
        } catch (err) {
            error = err.message || 'Failed to generate certificate';
            console.error(err);
        } finally {
            generatingCert = false;
        }
    }
</script>

<svelte:head>
    <title>Databases - No Frills DB</title>
</svelte:head>

<div class="max-w-4xl mx-auto p-6">
    <div class="mb-8">
        <h1 class="text-3xl font-bold text-gray-900 mb-2">Database Management</h1>
        <p class="text-gray-600">Manage your databases and authentication credentials</p>
    </div>

    {#if error}
        <div class="bg-red-50 border border-red-200 rounded-md p-4 mb-6">
            <div class="flex">
                <div class="ml-3">
                    <h3 class="text-sm font-medium text-red-800">Error</h3>
                    <div class="mt-2 text-sm text-red-700">{error}</div>
                </div>
            </div>
        </div>
    {/if}

    <!-- Authentication Credentials Section -->
    <div class="bg-white shadow rounded-lg mb-8">
        <div class="px-6 py-4 border-b border-gray-200">
            <h2 class="text-lg font-medium text-gray-900">Authentication Credentials</h2>
            <p class="text-sm text-gray-500">Generate password or certificate-based authentication</p>
        </div>
        <div class="px-6 py-4">
            <div class="flex flex-wrap gap-4 mb-4">
                <button
                    on:click={() => showPasswordWarning = true}
                    disabled={generatingPassword}
                    class="bg-blue-600 hover:bg-blue-700 disabled:opacity-50 text-white px-4 py-2 rounded-md text-sm font-medium transition-colors"
                >
                    {generatingPassword ? 'Generating...' : 'Generate Password'}
                </button>
                <button
                    on:click={() => showCertWarning = true}
                    disabled={generatingCert}
                    class="bg-green-600 hover:bg-green-700 disabled:opacity-50 text-white px-4 py-2 rounded-md text-sm font-medium transition-colors"
                >
                    {generatingCert ? 'Generating...' : 'Generate Certificate'}
                </button>
            </div>

            <!-- Current Certificate Info -->
            {#if certMetadata}
                <div class="border-t border-gray-200 pt-4">
                    <h3 class="text-sm font-medium text-gray-900 mb-3">Current Certificate</h3>
                    <div class="grid grid-cols-1 md:grid-cols-2 gap-4 text-sm">
                        <div>
                            <dt class="font-medium text-gray-500">Serial Number</dt>
                            <dd class="mt-1 text-gray-900 font-mono text-xs">{certMetadata.serial}</dd>
                        </div>
                        <div>
                            <dt class="font-medium text-gray-500">Fingerprint</dt>
                            <dd class="mt-1 text-gray-900 font-mono text-xs break-all">{certMetadata.fingerprint}</dd>
                        </div>
                        <div>
                            <dt class="font-medium text-gray-500">Issued</dt>
                            <dd class="mt-1 text-gray-900">{new Date(certMetadata.issuedAt).toLocaleDateString()}</dd>
                        </div>
                        <div>
                            <dt class="font-medium text-gray-500">Expires</dt>
                            <dd class="mt-1 text-gray-900">{new Date(certMetadata.expiresAt).toLocaleDateString()}</dd>
                        </div>
                    </div>
                    {#if new Date(certMetadata.expiresAt) < new Date()}
                        <div class="mt-3 p-2 bg-red-50 border border-red-200 rounded-md">
                            <p class="text-sm text-red-600">⚠️ This certificate has expired</p>
                        </div>
                    {:else if new Date(certMetadata.expiresAt) < new Date(Date.now() + 30 * 24 * 60 * 60 * 1000)}
                        <div class="mt-3 p-2 bg-amber-50 border border-amber-200 rounded-md">
                            <p class="text-sm text-amber-600">⚠️ This certificate will expire soon</p>
                        </div>
                    {/if}
                </div>
            {:else}
                <div class="border-t border-gray-200 pt-4">
                    <p class="text-sm text-gray-500">No certificate generated yet. Generate one to see details.</p>
                </div>
            {/if}
        </div>
    </div>

    <!-- Databases Section -->
    <div class="bg-white shadow rounded-lg">
        <div class="px-6 py-4 border-b border-gray-200 flex justify-between items-center">
            <div>
                <h2 class="text-lg font-medium text-gray-900">Your Databases</h2>
                <p class="text-sm text-gray-500">Manage your database instances</p>
            </div>
            <button
                on:click={() => showCreateForm = true}
                class="bg-indigo-600 hover:bg-indigo-700 text-white px-4 py-2 rounded-md text-sm font-medium transition-colors"
            >
                Create Database
            </button>
        </div>

        <div class="px-6 py-4">
            {#if databases.length === 0}
                <div class="text-center py-8">
                    <div class="text-gray-400 text-lg mb-2">No databases yet</div>
                    <p class="text-gray-500">Create your first database to get started</p>
                </div>
            {:else}
                <div class="space-y-3">
                    {#each databases as database}
                        <div class="border border-gray-200 rounded-lg p-4">
                            <div class="flex items-center justify-between">
                                <div>
                                    <h3 class="font-medium text-gray-900">{database.name}</h3>
                                    <p class="text-sm text-gray-500">Database instance</p>
                                </div>
                                <div class="flex space-x-2">
                                    <span class="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-green-100 text-green-800">
                                        Active
                                    </span>
                                </div>
                            </div>
                        </div>
                    {/each}
                </div>
            {/if}
        </div>
    </div>
</div>

<!-- Create Database Modal -->
{#if showCreateForm}
    <div class="fixed inset-0 bg-gray-500 bg-opacity-75 flex items-center justify-center p-4 z-50">
        <div class="bg-white rounded-lg max-w-md w-full p-6">
            <h3 class="text-lg font-medium text-gray-900 mb-4">Create New Database</h3>

            <div class="mb-4">
                <label for="dbName" class="block text-sm font-medium text-gray-700 mb-2">
                    Database Name
                </label>
                <input
                    id="dbName"
                    type="text"
                    bind:value={newDatabaseName}
                    placeholder="Enter database name"
                    class="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
                />
                <p class="text-xs text-gray-500 mt-1">
                    Use letters, numbers, and underscores only. Must start with a letter or underscore
                </p>
            </div>

            {#if error && showCreateForm}
                <div class="mb-4 bg-red-50 border border-red-200 rounded-md p-3">
                    <div class="flex">
                        <div class="flex-shrink-0">
                            <svg class="h-5 w-5 text-red-400" fill="currentColor" viewBox="0 0 20 20">
                                <path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z" clip-rule="evenodd" />
                            </svg>
                        </div>
                        <div class="ml-3">
                            <p class="text-sm text-red-800">{error}</p>
                        </div>
                    </div>
                </div>
            {/if}

            <div class="flex justify-end space-x-3">
                <button
                    on:click={() => { showCreateForm = false; newDatabaseName = ''; error = ''; }}
                    disabled={creating}
                    class="px-4 py-2 text-sm font-medium text-gray-700 bg-gray-100 hover:bg-gray-200 rounded-md transition-colors"
                >
                    Cancel
                </button>
                <button
                    on:click={createDatabase}
                    disabled={creating || !newDatabaseName.trim()}
                    class="px-4 py-2 text-sm font-medium text-white bg-indigo-600 hover:bg-indigo-700 disabled:opacity-50 rounded-md transition-colors"
                >
                    {creating ? 'Creating...' : 'Create'}
                </button>
            </div>
        </div>
    </div>
{/if}

<!-- Password Warning Modal -->
{#if showPasswordWarning}
    <div class="fixed inset-0 bg-gray-500 bg-opacity-75 flex items-center justify-center p-4 z-50">
        <div class="bg-white rounded-lg max-w-md w-full p-6">
            <div class="flex items-start">
                <div class="flex-shrink-0">
                    <svg class="h-6 w-6 text-amber-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-2.5L13.732 4c-.77-.833-1.964-.833-2.732 0L4.082 15.5c-.77.833.192 2.5 1.732 2.5z" />
                    </svg>
                </div>
                <div class="ml-3 w-0 flex-1">
                    <h3 class="text-lg font-medium text-gray-900">Generate New Password?</h3>
                    <div class="mt-2">
                        <p class="text-sm text-gray-500">
                            Generating a new password will invalidate your current password.
                            You will need to update any applications using the old password.
                        </p>
                    </div>
                </div>
            </div>
            <div class="mt-5 flex justify-end space-x-3">
                <button
                    on:click={() => showPasswordWarning = false}
                    class="px-4 py-2 text-sm font-medium text-gray-700 bg-gray-100 hover:bg-gray-200 rounded-md transition-colors"
                >
                    Cancel
                </button>
                <button
                    on:click={generatePassword}
                    class="px-4 py-2 text-sm font-medium text-white bg-amber-600 hover:bg-amber-700 rounded-md transition-colors"
                >
                    Generate New Password
                </button>
            </div>
        </div>
    </div>
{/if}

<!-- Certificate Warning Modal -->
{#if showCertWarning}
    <div class="fixed inset-0 bg-gray-500 bg-opacity-75 flex items-center justify-center p-4 z-50">
        <div class="bg-white rounded-lg max-w-md w-full p-6">
            <div class="flex items-start">
                <div class="flex-shrink-0">
                    <svg class="h-6 w-6 text-amber-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-2.5L13.732 4c-.77-.833-1.964-.833-2.732 0L4.082 15.5c-.77.833.192 2.5 1.732 2.5z" />
                    </svg>
                </div>
                <div class="ml-3 w-0 flex-1">
                    <h3 class="text-lg font-medium text-gray-900">Generate New Certificate?</h3>
                    <div class="mt-2">
                        <p class="text-sm text-gray-500">
                            Generating a new certificate will invalidate your current certificate.
                            You will need to update any applications using the old certificate.
                        </p>
                    </div>
                </div>
            </div>
            <div class="mt-5 flex justify-end space-x-3">
                <button
                    on:click={() => showCertWarning = false}
                    class="px-4 py-2 text-sm font-medium text-gray-700 bg-gray-100 hover:bg-gray-200 rounded-md transition-colors"
                >
                    Cancel
                </button>
                <button
                    on:click={generateCertificate}
                    class="px-4 py-2 text-sm font-medium text-white bg-amber-600 hover:bg-amber-700 rounded-md transition-colors"
                >
                    Generate New Certificate
                </button>
            </div>
        </div>
    </div>
{/if}