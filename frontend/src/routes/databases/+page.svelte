<script lang="ts">
    import { onMount } from 'svelte';
    import { page } from '$app/stores';
    import { loggedIn, getUser, authenticatedFetch, checkAuth } from '$lib/auth';
    import { goto } from '$app/navigation';

    let user: any = null;
    let databases: any[] = [];
    let databaseSizes: Map<string, number> = new Map();
    let paymentMethods: any[] = [];
    let showCreateForm = false;
    let showPasswordWarning = false;
    let showCertWarning = false;
    let showDeleteWarning = false;
    let databaseToDelete = null;
    let newDatabaseName = '';
    let creating = false;
    let generatingPassword = false;
    let generatingCert = false;
    let deleting = false;
    let loading = true;
    let error = '';

    // Check if user has authentication credentials
    $: hasCredentials = hasCertificate || hasPassword;
    $: hasPassword = user?.role;
    $: hasCertificate = user?.serial && user?.fingerprint && user?.issuedAt && user?.expiresAt;

    // Calculate total database size in bytes
    $: totalSizeBytes = Array.from(databaseSizes.values()).reduce((sum, size) => sum + size, 0);
    $: totalSizeMB = totalSizeBytes / (1024 * 1024);

    // Check if user has payment method
    $: hasPaymentMethod = paymentMethods.length > 0;

    // Check if buttons should be disabled due to no payment method and size over 200MB
    $: shouldDisableButtons = !hasPaymentMethod && totalSizeMB > 200;

    // Environment variables for JDBC endpoints
    const jdbcPasswordEndpoint = import.meta.env.VITE_JDBC_PWD_ENDPOINT;
    const jdbcCertEndpoint = import.meta.env.VITE_JDBC_CRT_ENDPOINT;

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
            // Load user data from backend API to get role information
            const userResponse = await authenticatedFetch('/api/user/details');
            if (userResponse.ok) {
                user = await userResponse.json();
            } else {
                // Fallback to Auth0 user if backend fails
                user = await getUser();
            }

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

            // Load database sizes if we have databases
            if (databases.length > 0) {
                await loadDatabaseSizes();
            }

            // Load payment methods
            await loadPaymentMethods();
        } catch (err) {
            error = 'Failed to load data';
            console.error(err);
        } finally {
            loading = false;
        }
    }

    async function loadDatabaseSizes() {
        try {
            const sizesResponse = await authenticatedFetch('/api/database', {
                method: 'GET'
            });

            if (sizesResponse.ok) {
                const sizesData = await sizesResponse.json();
                databaseSizes = new Map();

                // Log the raw response to debug
                console.log('Database sizes response:', sizesData);

                // Handle the complex Map<Database, Int> structure
                // The key format is: "Database(name=dbname, createdAt=timestamp)"
                Object.entries(sizesData).forEach(([key, sizeBytes]) => {
                    // Extract database name from the Database object string representation
                    const nameMatch = key.match(/name=([^,\)]+)/);
                    if (nameMatch && nameMatch[1]) {
                        const dbName = nameMatch[1];
                        databaseSizes.set(dbName, sizeBytes as number);
                        console.log(`Found database: ${dbName} with size: ${sizeBytes}`);
                    } else {
                        console.warn('Could not extract database name from key:', key);
                    }
                });

                // Trigger reactivity
                databaseSizes = databaseSizes;
                console.log('Processed database sizes:', Object.fromEntries(databaseSizes));
            }
        } catch (err) {
            console.error('Failed to load database sizes:', err);
        }
    }

    function formatSizeToGB(sizeBytes: number): string {
        const sizeGB = sizeBytes / (1024 * 1024 * 1024);
        if (sizeGB >= 1) {
            return `${sizeGB.toFixed(2)} GB`;
        }

        const sizeMB = sizeBytes / (1024 * 1024);
        if (sizeMB < 0.01) {
            return '< 0.01 MB';
        }
        return `${sizeMB.toFixed(2)} MB`;
    }

    async function loadPaymentMethods() {
        try {
            const paymentResponse = await authenticatedFetch('/api/payments/paymentMethods');
            if (paymentResponse.ok) {
                const data = await paymentResponse.json();
                paymentMethods = data.paymentMethods || [];
            } else {
                paymentMethods = [];
            }
        } catch (err) {
            console.error('Failed to load payment methods:', err);
            paymentMethods = [];
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

    async function deleteDatabase() {
        if (!databaseToDelete) {
            return;
        }

        deleting = true;
        error = '';

        try {
            const response = await authenticatedFetch(`/api/provision/db/${encodeURIComponent(databaseToDelete.name)}`, {
                method: 'DELETE'
            });

            if (!response.ok) {
                throw new Error('Failed to delete database');
            }

            // Refresh data to get updated databases list
            await refreshData();

            // Reset form
            showDeleteWarning = false;
            databaseToDelete = null;
        } catch (err) {
            error = err.message || 'Failed to delete database';
            console.error(err);
        } finally {
            deleting = false;
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

    {#if shouldDisableButtons}
        <div class="bg-amber-50 border border-amber-200 rounded-md p-4 mb-6">
            <div class="flex">
                <div class="flex-shrink-0">
                    <svg class="h-5 w-5 text-amber-400" fill="currentColor" viewBox="0 0 20 20">
                        <path fill-rule="evenodd" d="M8.257 3.099c.765-1.36 2.722-1.36 3.486 0l5.58 9.92c.75 1.334-.213 2.98-1.742 2.98H4.42c-1.53 0-2.493-1.646-1.743-2.98l5.58-9.92zM11 13a1 1 0 11-2 0 1 1 0 012 0zm-1-8a1 1 0 00-1 1v3a1 1 0 002 0V6a1 1 0 00-1-1z" clip-rule="evenodd" />
                    </svg>
                </div>
                <div class="ml-3">
                    <h3 class="text-sm font-medium text-amber-800">Payment Method Required</h3>
                    <div class="mt-2 text-sm text-amber-700">
                        <p>Your total database storage ({formatSizeToGB(totalSizeBytes)}) exceeds the free tier limit of 100MB. Please add a payment method to continue managing your databases.</p>
                        <div class="mt-3">
                            <a href="/payments" class="bg-amber-600 hover:bg-amber-700 text-white px-3 py-2 rounded-md text-sm font-medium transition-colors">
                                Add Payment Method
                            </a>
                        </div>
                    </div>
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
                <div class="relative group">
                    <button
                        on:click={() => shouldDisableButtons ? null : (showPasswordWarning = true)}
                        disabled={generatingPassword || shouldDisableButtons}
                        class="bg-blue-600 hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed text-white px-4 py-2 rounded-md text-sm font-medium transition-colors"
                        title={shouldDisableButtons ? "Add a payment method first. Total storage exceeds 100MB." : "Generate password authentication"}
                    >
                        {generatingPassword ? 'Generating...' : 'Generate Password'}
                    </button>
                    {#if shouldDisableButtons}
                        <div class="absolute bottom-full left-1/2 transform -translate-x-1/2 mb-2 opacity-0 group-hover:opacity-100 transition-opacity duration-200 bg-gray-900 text-white text-xs rounded py-1 px-2 whitespace-nowrap z-10 pointer-events-none max-w-xs">
                            Add a payment method first. Total storage exceeds 100MB.
                            <div class="absolute top-full left-1/2 transform -translate-x-1/2 border-l-4 border-r-4 border-t-4 border-l-transparent border-r-transparent border-t-gray-900"></div>
                        </div>
                    {/if}
                </div>
                <div class="relative group">
                    <button
                        on:click={() => shouldDisableButtons ? null : (showCertWarning = true)}
                        disabled={generatingCert || shouldDisableButtons}
                        class="bg-green-600 hover:bg-green-700 disabled:opacity-50 disabled:cursor-not-allowed text-white px-4 py-2 rounded-md text-sm font-medium transition-colors"
                        title={shouldDisableButtons ? "Add a payment method first. Total storage exceeds 100MB." : "Generate certificate authentication"}
                    >
                        {generatingCert ? 'Generating...' : 'Generate Certificate'}
                    </button>
                    {#if shouldDisableButtons}
                        <div class="absolute bottom-full left-1/2 transform -translate-x-1/2 mb-2 opacity-0 group-hover:opacity-100 transition-opacity duration-200 bg-gray-900 text-white text-xs rounded py-1 px-2 whitespace-nowrap z-10 pointer-events-none max-w-xs">
                            Add a payment method first. Total storage exceeds 100MB.
                            <div class="absolute top-full left-1/2 transform -translate-x-1/2 border-l-4 border-r-4 border-t-4 border-l-transparent border-r-transparent border-t-gray-900"></div>
                        </div>
                    {/if}
                </div>
            </div>

            <!-- Current Password Info -->
            {#if hasPassword}
                <div class="border-t border-gray-200 pt-4 mb-4">
                    <h3 class="text-sm font-medium text-gray-900 mb-3">Current Password</h3>
                    <div class="flex items-center space-x-3">
                        <div class="flex items-center">
                            <svg class="h-5 w-5 text-green-500 mr-2" fill="currentColor" viewBox="0 0 20 20">
                                <path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clip-rule="evenodd" />
                            </svg>
                            <span class="text-sm text-gray-900">Password authentication is active</span>
                        </div>
                        <div class="text-sm text-gray-500">
                            Role: <span class="font-mono text-xs">{user.role}</span>
                        </div>
                    </div>
                    {#if jdbcPasswordEndpoint}
                        <div class="mt-3 p-3 bg-blue-50 border border-blue-200 rounded-md">
                            <h4 class="text-sm font-medium text-blue-800 mb-2">JDBC Connection URL</h4>
                            <div class="flex items-center space-x-2">
                                <code class="text-xs bg-blue-100 text-blue-800 px-2 py-1 rounded font-mono break-all">
                                    jdbc:postgresql://{jdbcPasswordEndpoint}/[database_name]?user={user.role}&password=[your_password]
                                </code>
                                <button
                                    on:click={() => navigator.clipboard.writeText(`jdbc:postgresql://${jdbcPasswordEndpoint}/[database_name]?user=${user.role}&password=[your_password]`)}
                                    class="flex-shrink-0 p-1 text-blue-600 hover:text-blue-800"
                                    title="Copy to clipboard"
                                >
                                    <svg class="w-4 h-4" fill="currentColor" viewBox="0 0 20 20">
                                        <path d="M8 3a1 1 0 011-1h2a1 1 0 110 2H9a1 1 0 01-1-1z"></path>
                                        <path d="M6 3a2 2 0 00-2 2v11a2 2 0 002 2h8a2 2 0 002-2V5a2 2 0 00-2-2 3 3 0 01-3 3H9a3 3 0 01-3-3z"></path>
                                    </svg>
                                </button>
                            </div>
                        </div>
                    {/if}
                </div>
            {:else}
                <div class="border-t border-gray-200 pt-4 mb-4">
                    <p class="text-sm text-gray-500">No password generated yet. Generate one to see details.</p>
                </div>
            {/if}

            <!-- Current Certificate Info -->
            {#if hasCertificate}
                <div class="border-t border-gray-200 pt-4">
                    <h3 class="text-sm font-medium text-gray-900 mb-3">Current Certificate</h3>
                    <div class="flex items-center space-x-3 mb-4">
                        <div class="flex items-center">
                            <svg class="h-5 w-5 text-green-500 mr-2" fill="currentColor" viewBox="0 0 20 20">
                                <path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clip-rule="evenodd" />
                            </svg>
                            <span class="text-sm text-gray-900">Certificate authentication is active</span>
                        </div>
                        {#if user.crtRole}
                            <div class="text-sm text-gray-500">
                                Role: <span class="font-mono text-xs">{user.crtRole}</span>
                            </div>
                        {/if}
                    </div>
                    <div class="grid grid-cols-1 md:grid-cols-2 gap-4 text-sm">
                        <div>
                            <dt class="font-medium text-gray-500">Serial Number</dt>
                            <dd class="mt-1 text-gray-900 font-mono text-xs">{user.serial}</dd>
                        </div>
                        <div>
                            <dt class="font-medium text-gray-500">Fingerprint</dt>
                            <dd class="mt-1 text-gray-900 font-mono text-xs break-all">{user.fingerprint}</dd>
                        </div>
                        <div>
                            <dt class="font-medium text-gray-500">Issued</dt>
                            <dd class="mt-1 text-gray-900">{new Date(user.issuedAt).toLocaleDateString()}</dd>
                        </div>
                        <div>
                            <dt class="font-medium text-gray-500">Expires</dt>
                            <dd class="mt-1 text-gray-900">{new Date(user.expiresAt).toLocaleDateString()}</dd>
                        </div>
                    </div>
                    {#if jdbcCertEndpoint}
                        <div class="mt-3 p-3 bg-green-50 border border-green-200 rounded-md">
                            <h4 class="text-sm font-medium text-green-800 mb-2">JDBC Connection URL</h4>
                            <div class="flex items-center space-x-2">
                                <code class="text-xs bg-green-100 text-green-800 px-2 py-1 rounded font-mono break-all">
                                    jdbc:postgresql://{jdbcCertEndpoint}/[database_name]?ssl=true&sslcert=[cert_file]&sslkey=[key_file]
                                </code>
                                <button
                                    on:click={() => navigator.clipboard.writeText(`jdbc:postgresql://${jdbcCertEndpoint}/[database_name]?ssl=true&sslcert=[cert_file]&sslkey=[key_file]`)}
                                    class="flex-shrink-0 p-1 text-green-600 hover:text-green-800"
                                    title="Copy to clipboard"
                                >
                                    <svg class="w-4 h-4" fill="currentColor" viewBox="0 0 20 20">
                                        <path d="M8 3a1 1 0 011-1h2a1 1 0 110 2H9a1 1 0 01-1-1z"></path>
                                        <path d="M6 3a2 2 0 00-2 2v11a2 2 0 002 2h8a2 2 0 002-2V5a2 2 0 00-2-2 3 3 0 01-3 3H9a3 3 0 01-3-3z"></path>
                                    </svg>
                                </button>
                            </div>
                        </div>
                    {/if}
                    {#if new Date(user.expiresAt) < new Date()}
                        <div class="mt-3 p-2 bg-red-50 border border-red-200 rounded-md">
                            <p class="text-sm text-red-600">⚠️ This certificate has expired</p>
                        </div>
                    {:else if new Date(user.expiresAt) < new Date(Date.now() + 30 * 24 * 60 * 60 * 1000)}
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
            <div class="relative group">
                <button
                    on:click={() => (hasCredentials && !shouldDisableButtons) && (showCreateForm = true)}
                    disabled={!hasCredentials || shouldDisableButtons}
                    class="bg-indigo-600 hover:bg-indigo-700 disabled:bg-gray-400 disabled:cursor-not-allowed text-white px-4 py-2 rounded-md text-sm font-medium transition-colors"
                    title={shouldDisableButtons ? "Add a payment method first. Total storage exceeds 100MB." : hasCredentials ? "Create a new database" : "Generate authentication credentials first"}
                >
                    Create Database
                </button>
                {#if shouldDisableButtons}
                    <div class="absolute bottom-full left-1/2 transform -translate-x-1/2 mb-2 opacity-0 group-hover:opacity-100 transition-opacity duration-200 bg-gray-900 text-white text-xs rounded py-1 px-2 whitespace-nowrap z-10 pointer-events-none max-w-xs">
                        Add a payment method first. Total storage exceeds 100MB.
                        <div class="absolute top-full left-1/2 transform -translate-x-1/2 border-l-4 border-r-4 border-t-4 border-l-transparent border-r-transparent border-t-gray-900"></div>
                    </div>
                {:else if !hasCredentials}
                    <div class="absolute bottom-full left-1/2 transform -translate-x-1/2 mb-2 opacity-0 group-hover:opacity-100 transition-opacity duration-200 bg-gray-900 text-white text-xs rounded py-1 px-2 whitespace-nowrap z-10 pointer-events-none">
                        Generate authentication credentials first
                        <div class="absolute top-full left-1/2 transform -translate-x-1/2 border-l-4 border-r-4 border-t-4 border-l-transparent border-r-transparent border-t-gray-900"></div>
                    </div>
                {/if}
            </div>
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
                                    <div class="flex items-center space-x-2">
                                        <h3 class="font-medium text-gray-900">{database.name}</h3>
                                        <button
                                            on:click={() => navigator.clipboard.writeText(database.name)}
                                            class="p-1 text-gray-400 hover:text-gray-600 rounded transition-colors"
                                            title="Copy database name"
                                        >
                                            <svg class="w-4 h-4" fill="currentColor" viewBox="0 0 20 20">
                                                <path d="M8 3a1 1 0 011-1h2a1 1 0 110 2H9a1 1 0 01-1-1z"></path>
                                                <path d="M6 3a2 2 0 00-2 2v11a2 2 0 002 2h8a2 2 0 002-2V5a2 2 0 00-2-2 3 3 0 01-3 3H9a3 3 0 01-3-3z"></path>
                                            </svg>
                                        </button>
                                    </div>
                                    <div class="flex items-center space-x-2">
                                        <p class="text-sm text-gray-500">Database instance</p>
                                        {#if databaseSizes.has(database.name)}
                                            <span class="text-sm text-gray-400">•</span>
                                            <span class="text-sm font-medium text-gray-600">
                                                {formatSizeToGB(databaseSizes.get(database.name))}
                                            </span>
                                        {/if}
                                    </div>
                                </div>
                                <div class="flex items-center space-x-2">
                                    <span class="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-green-100 text-green-800">
                                        Active
                                    </span>
                                    <div class="relative group">
                                        <button
                                            on:click={() => shouldDisableButtons ? null : (databaseToDelete = database, showDeleteWarning = true)}
                                            disabled={shouldDisableButtons}
                                            class="p-1 text-red-600 hover:text-red-800 hover:bg-red-50 disabled:opacity-50 disabled:cursor-not-allowed rounded transition-colors"
                                            title={shouldDisableButtons ? "Add a payment method first. Total storage exceeds 100MB." : "Delete database"}
                                        >
                                            <svg class="w-4 h-4" fill="currentColor" viewBox="0 0 20 20">
                                                <path fill-rule="evenodd" d="M9 2a1 1 0 00-.894.553L7.382 4H4a1 1 0 000 2v10a2 2 0 002 2h8a2 2 0 002-2V6a1 1 0 100-2h-3.382l-.724-1.447A1 1 0 0011 2H9zM7 8a1 1 0 012 0v6a1 1 0 11-2 0V8zm5-1a1 1 0 00-1 1v6a1 1 0 102 0V8a1 1 0 00-1-1z" clip-rule="evenodd" />
                                            </svg>
                                        </button>
                                        {#if shouldDisableButtons}
                                            <div class="absolute bottom-full left-1/2 transform -translate-x-1/2 mb-2 opacity-0 group-hover:opacity-100 transition-opacity duration-200 bg-gray-900 text-white text-xs rounded py-1 px-2 whitespace-nowrap z-10 pointer-events-none max-w-xs">
                                                Add a payment method first. Total storage exceeds 100MB.
                                                <div class="absolute top-full left-1/2 transform -translate-x-1/2 border-l-4 border-r-4 border-t-4 border-l-transparent border-r-transparent border-t-gray-900"></div>
                                            </div>
                                        {/if}
                                    </div>
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

<!-- Delete Database Warning Modal -->
{#if showDeleteWarning && databaseToDelete}
    <div class="fixed inset-0 bg-gray-500 bg-opacity-75 flex items-center justify-center p-4 z-50">
        <div class="bg-white rounded-lg max-w-md w-full p-6">
            <div class="flex items-start">
                <div class="flex-shrink-0">
                    <svg class="h-6 w-6 text-red-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-2.5L13.732 4c-.77-.833-1.964-.833-2.732 0L4.082 15.5c-.77.833.192 2.5 1.732 2.5z" />
                    </svg>
                </div>
                <div class="ml-3 w-0 flex-1">
                    <h3 class="text-lg font-medium text-gray-900">Delete Database?</h3>
                    <div class="mt-2">
                        <p class="text-sm text-gray-500">
                            Are you sure you want to delete the database "<strong>{databaseToDelete.name}</strong>"?
                            This action cannot be undone and all data in the database will be permanently lost.
                        </p>
                    </div>
                </div>
            </div>
            <div class="mt-5 flex justify-end space-x-3">
                <button
                    on:click={() => { showDeleteWarning = false; databaseToDelete = null; }}
                    disabled={deleting}
                    class="px-4 py-2 text-sm font-medium text-gray-700 bg-gray-100 hover:bg-gray-200 disabled:opacity-50 rounded-md transition-colors"
                >
                    Cancel
                </button>
                <button
                    on:click={deleteDatabase}
                    disabled={deleting}
                    class="px-4 py-2 text-sm font-medium text-white bg-red-600 hover:bg-red-700 disabled:opacity-50 rounded-md transition-colors"
                >
                    {deleting ? 'Deleting...' : 'Delete Database'}
                </button>
            </div>
        </div>
    </div>
{/if}