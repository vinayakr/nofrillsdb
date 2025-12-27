<script lang="ts">
    import { goto } from '$app/navigation';
    import {getUser, login, signUp, loggedIn, authenticatedFetch, getTokenSilently} from '$lib/auth';
    import { onMount } from 'svelte';

    let isAuthenticated = false;
    let user: any = null;
    let loading = false;
    let error = '';
    let success = false;
    let isRegistered = false;

    // Form data
    let formData = {
        email: '',
        name: ''
    };

    loggedIn.subscribe(value => {
        isAuthenticated = value;
    });

    onMount(async () => {
        try {
            user = await getUser();
            if (user) {
                loggedIn.set(true);
                // Pre-fill form with Auth0 data
                formData.email = user.email || '';
                formData.name = user.name || '';

                // Check registration status
                await checkRegistrationStatus();
            }
        } catch (e) {
            user = null;
            loggedIn.set(false);
        }
    });

    async function checkRegistrationStatus() {

        const response = await authenticatedFetch('/api/user/registration-status');
        const data = await response.json();

        if (response.ok) {
            if (data.registered && data.hasUserIdInToken) {
                // User is fully set up, fetch their profile data and show as profile view
                goto('/profile')
                error = '';
            }
        }
    }

    async function handleSubmit() {
        if (!isAuthenticated) {
            error = 'Please log in first to complete registration';
            return;
        }

        loading = true;
        error = '';

        try {
            const response = await authenticatedFetch('/api/user/register', {
                method: 'POST',
                body: JSON.stringify(formData)
            });

            const data = await response.json();

            if (response.ok) {
                success = true;
                if (data.message) {
                    error = data.message;
                }

                try {
                    await getTokenSilently(true);
                } catch (e: any) {
                    console.error("getTokenSilently failed");

                    if (e?.error) {
                        console.error("Auth0 error:", e.error);
                    }
                    if (e?.error_description) {
                        console.error("Description:", e.error_description);
                    }
                    if (e?.message) {
                        console.error("Message:", e.message);
                    }

                    console.error("Full error object:", e);
                }
                setTimeout(() => {
                    goto('/');
                }, 3000);
            } else {
                error = data.error || (isRegistered ? 'Profile update failed' : 'Registration failed');
            }
        } catch (e) {
            error = 'Network error. Please try again.';
        } finally {
            loading = false;
        }
    }

    function handleSignUp() {
        signUp('/register');
    }

</script>

<div class="min-h-screen bg-gray-50 flex flex-col justify-center py-12 sm:px-6 lg:px-8">
    <div class="sm:mx-auto sm:w-full sm:max-w-md">
        <h2 class="mt-6 text-center text-3xl font-extrabold text-gray-900">
            {isRegistered ? 'Your Profile' : 'Complete Your Registration'}
        </h2>
        <p class="mt-2 text-center text-sm text-gray-600">
            {isRegistered ? 'View and update your account information' : 'Finish setting up your PrivateHomeDeals account'}
        </p>
    </div>

    <div class="mt-8 sm:mx-auto sm:w-full sm:max-w-md">
        <div class="bg-white py-8 px-4 shadow sm:rounded-lg sm:px-10">
            {#if !isAuthenticated}
                <div class="text-center">
                    <p class="text-sm text-gray-600 mb-4">
                        Please sign up with Auth0 first to create your account
                    </p>
                    <button
                        on:click={handleSignUp}
                        class="w-full flex justify-center py-2 px-4 border border-transparent rounded-md shadow-sm text-sm font-medium text-white bg-indigo-600 hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500"
                    >
                        Sign Up with Auth0
                    </button>
                </div>
            {:else if success}
                <div class="text-center">
                    <div class="text-green-600 text-lg font-semibold mb-2">✓ Registration Complete!</div>
                    <p class="text-sm text-gray-600">
                        Your account has been created successfully. Redirecting to home page...
                    </p>
                </div>
            {:else}
                <form on:submit|preventDefault={handleSubmit} class="space-y-6">
                    {#if error}
                        <div class="rounded-md bg-red-50 p-4">
                            <div class="text-sm text-red-700">{error}</div>
                        </div>
                    {/if}

                    <div>
                        <label class="block text-sm font-medium text-gray-700">
                            Email address
                        </label>
                        <div class="mt-1">
                            <div class="appearance-none block w-full px-3 py-2 border border-gray-300 rounded-md bg-gray-100 text-gray-600 sm:text-sm">
                                {user?.email || 'Loading...'}
                            </div>
                        </div>
                        <p class="mt-1 text-xs text-gray-500">Email from your Auth0 account (cannot be changed)</p>
                    </div>

                    <div>
                        <label for="name" class="block text-sm font-medium text-gray-700">
                            Full Name
                        </label>
                        <div class="mt-1">
                            <input
                                id="name"
                                name="name"
                                type="text"
                                maxlength="100"
                                bind:value={formData.name}
                                class="appearance-none block w-full px-3 py-2 border border-gray-300 rounded-md placeholder-gray-400 focus:outline-none focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm"
                            />
                        </div>
                        <p class="mt-1 text-xs text-gray-500">Maximum 100 characters</p>
                    </div>

                    <div>
                        <button
                            type="submit"
                            disabled={loading || !user?.email}
                            class="w-full flex justify-center py-2 px-4 border border-transparent rounded-md shadow-sm text-sm font-medium text-white bg-indigo-600 hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500 disabled:opacity-50 disabled:cursor-not-allowed"
                        >
                            {#if loading}
                                <svg class="animate-spin -ml-1 mr-3 h-5 w-5 text-white" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                                    <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                                    <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                                </svg>
                                {isRegistered ? 'Updating...' : 'Registering...'}
                            {:else}
                                {isRegistered ? 'Update Profile' : 'Complete Registration'}
                            {/if}
                        </button>
                    </div>
                </form>
            {/if}
        </div>
    </div>
</div>