<script lang="ts">
    import { onMount } from 'svelte';
    import { page } from '$app/stores';
    import { checkAuth, authenticatedFetch } from '$lib/auth';
    import { goto } from '$app/navigation';
    import { loadStripe } from '@stripe/stripe-js';

    let loading = true;
    let error = '';
    let success = '';
    let stripe: any = null;
    let elements: any = null;
    let cardElement: any = null;
    let paymentMethods: any[] = [];
    let clientSecret = '';
    let customerId = '';
    let saving = false;

    const stripePublishableKey = import.meta.env.VITE_STRIPE_PUBLISHABLE_KEY;

    onMount(async () => {
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

        if (!stripePublishableKey) {
            error = 'Stripe is not configured. Please contact support.';
            loading = false;
            return;
        }

        await initializeStripe();
        await loadPaymentMethods();
        await createSetupIntent();
        loading = false;
    });

    async function initializeStripe() {
        try {
            stripe = await loadStripe(stripePublishableKey);
            if (!stripe) {
                throw new Error('Failed to load Stripe');
            }

            elements = stripe.elements({
                appearance: {
                    theme: 'stripe'
                }
            });

            cardElement = elements.create('card', {
                style: {
                    base: {
                        fontSize: '16px',
                        color: '#424770',
                        '::placeholder': {
                            color: '#aab7c4',
                        },
                    },
                },
            });
        } catch (err) {
            error = 'Failed to initialize Stripe';
            console.error(err);
        }
    }

    async function createSetupIntent() {
        try {
            const response = await authenticatedFetch('/api/payments/setupIntent', {
                method: 'POST'
            });

            if (!response.ok) {
                throw new Error('Failed to create setup intent');
            }

            const data = await response.json();
            clientSecret = data.clientSecret;
            customerId = data.customerId;
        } catch (err) {
            error = 'Failed to initialize payment setup';
            console.error(err);
        }
    }

    async function loadPaymentMethods() {
        try {
            const response = await authenticatedFetch('/api/payments/paymentMethods');

            if (response.ok) {
                const data = await response.json();
                paymentMethods = data.paymentMethods;
            }
        } catch (err) {
            console.error('Failed to load payment methods:', err);
        }
    }

    async function savePaymentMethod() {
        if (!stripe || !cardElement || !clientSecret) {
            error = 'Payment system not ready';
            return;
        }

        saving = true;
        error = '';
        success = '';

        try {
            const { error: stripeError, setupIntent } = await stripe.confirmCardSetup(
                clientSecret,
                {
                    payment_method: {
                        card: cardElement,
                    }
                }
            );

            if (stripeError) {
                error = stripeError.message;
                return;
            }

            if (setupIntent && setupIntent.payment_method) {
                const response = await authenticatedFetch('/api/payments/attachPaymentMethod', {
                    method: 'POST',
                    body: JSON.stringify({
                        paymentMethodId: setupIntent.payment_method
                    })
                });

                if (!response.ok) {
                    const errorData = await response.json();
                    throw new Error(errorData.message || 'Failed to save payment method');
                }

                success = 'Payment method saved successfully!';
                await loadPaymentMethods();

                // Create new setup intent for future use
                await createSetupIntent();

                // Clear the card element
                cardElement.clear();
            }
        } catch (err) {
            error = err.message || 'Failed to save payment method';
            console.error(err);
        } finally {
            saving = false;
        }
    }

    function mountCardElement() {
        if (cardElement && document.getElementById('card-element')) {
            cardElement.mount('#card-element');
        }
    }

    // Mount card element after DOM is ready
    $: if (cardElement && !loading) {
        setTimeout(mountCardElement, 100);
    }
</script>

<svelte:head>
    <title>Payments - No Frills DB</title>
</svelte:head>

<div class="max-w-4xl mx-auto p-6">
    <div class="mb-8">
        <h1 class="text-3xl font-bold text-gray-900 mb-2">Payment Methods</h1>
        <p class="text-gray-600">Manage your payment methods for monthly billing</p>
    </div>

    {#if loading}
        <div class="flex justify-center py-8">
            <div class="animate-spin rounded-full h-8 w-8 border-b-2 border-indigo-600"></div>
        </div>
    {:else}
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

        {#if success}
            <div class="bg-green-50 border border-green-200 rounded-md p-4 mb-6">
                <div class="flex">
                    <div class="ml-3">
                        <h3 class="text-sm font-medium text-green-800">Success</h3>
                        <div class="mt-2 text-sm text-green-700">{success}</div>
                    </div>
                </div>
            </div>
        {/if}

        <!-- Current Payment Method -->
        {#if paymentMethods.length > 0}
            <div class="bg-white shadow rounded-lg mb-8">
                <div class="px-6 py-4 border-b border-gray-200">
                    <h2 class="text-lg font-medium text-gray-900">Current Payment Method</h2>
                    <p class="text-sm text-gray-500">Your credit card for monthly billing</p>
                </div>
                <div class="px-6 py-4">
                    {#each paymentMethods as method}
                        <div class="border border-gray-200 rounded-lg p-4">
                            <div class="flex items-center justify-between">
                                <div class="flex items-center space-x-4">
                                    <div class="flex-shrink-0">
                                        <svg class="w-8 h-8 text-gray-400" fill="currentColor" viewBox="0 0 20 20">
                                            <path d="M4 4a2 2 0 00-2 2v8a2 2 0 002 2h12a2 2 0 002-2V6a2 2 0 00-2-2H4zm2 6a1 1 0 011-1h6a1 1 0 110 2H7a1 1 0 01-1-1zm1 3a1 1 0 100 2h3a1 1 0 100-2H7z"/>
                                        </svg>
                                    </div>
                                    <div>
                                        <p class="text-sm font-medium text-gray-900">
                                            {method.card?.brand?.toUpperCase() || 'CARD'} ••••{method.card?.last4 || '****'}
                                        </p>
                                        <p class="text-sm text-gray-500">
                                            Expires {method.card?.expMonth || '**'}/{method.card?.expYear || '****'}
                                        </p>
                                    </div>
                                </div>
                                <div>
                                    <span class="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-green-100 text-green-800">
                                        Active
                                    </span>
                                </div>
                            </div>
                        </div>
                    {/each}
                </div>
            </div>
        {/if}

        <!-- Add/Update Payment Method -->
        <div class="bg-white shadow rounded-lg">
            <div class="px-6 py-4 border-b border-gray-200">
                <h2 class="text-lg font-medium text-gray-900">{paymentMethods.length > 0 ? 'Update Payment Method' : 'Add Payment Method'}</h2>
                <p class="text-sm text-gray-500">{paymentMethods.length > 0 ? 'Replace your current credit card' : 'Add a credit card for monthly billing'}</p>
            </div>
            <div class="px-6 py-4">
                <form on:submit|preventDefault={savePaymentMethod}>
                    <div class="mb-4">
                        <label for="card-element" class="block text-sm font-medium text-gray-700 mb-2">
                            Credit Card Information
                        </label>
                        <div id="card-element" class="p-3 border border-gray-300 rounded-md">
                            <!-- Stripe card element will be mounted here -->
                        </div>
                    </div>

                    <div class="flex justify-end">
                        <button
                            type="submit"
                            disabled={saving || !stripe}
                            class="bg-indigo-600 hover:bg-indigo-700 disabled:opacity-50 disabled:cursor-not-allowed text-white px-6 py-2 rounded-md text-sm font-medium transition-colors"
                        >
                            {saving ? 'Saving...' : 'Save Payment Method'}
                        </button>
                    </div>
                </form>
            </div>
        </div>

        <!-- Billing Information -->
        <div class="mt-8 bg-blue-50 border border-blue-200 rounded-lg p-4">
            <div class="flex">
                <div class="flex-shrink-0">
                    <svg class="h-5 w-5 text-blue-400" fill="currentColor" viewBox="0 0 20 20">
                        <path fill-rule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7-4a1 1 0 11-2 0 1 1 0 012 0zM9 9a1 1 0 000 2v3a1 1 0 001 1h1a1 1 0 100-2v-3a1 1 0 00-1-1H9z" clip-rule="evenodd" />
                    </svg>
                </div>
                <div class="ml-3">
                    <h3 class="text-sm font-medium text-blue-800">About Billing</h3>
                    <div class="mt-2 text-sm text-blue-700">
                        <p>Your payment method will be used for monthly billing based on your database usage. You'll be charged automatically each month.</p>
                    </div>
                </div>
            </div>
        </div>
    {/if}
</div>