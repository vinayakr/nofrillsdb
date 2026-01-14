<script lang="ts">
    import { onMount } from 'svelte';
    import { getUser } from '$lib/auth';

    let email = '';
    let name = '';
    let message = '';
    let isSubmitting = false;
    let submitMessage = '';
    let submitError = '';

    onMount(async () => {
        try {
            const user = await getUser();
            if (user) {
                email = user.email || '';
                name = user.name || '';
            }
        } catch (error) {
            console.log('User not authenticated - form will work without pre-filled info');
        }
    });

    async function handleSubmit() {
        if (!email || !name || !message) {
            submitError = 'Please fill in all fields';
            return;
        }

        isSubmitting = true;
        submitError = '';
        submitMessage = '';

        try {
            const response = await fetch('/api/contact', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({
                    email,
                    name,
                    message
                })
            });

            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            const data = await response.json();
            submitMessage = data.message || 'Thank you for your message! We\'ll get back to you soon.';
            message = ''; // Clear only the message field, keep email and name
        } catch (error) {
            console.error('Error submitting contact form:', error);
            submitError = 'There was an error sending your message. Please try again.';
        } finally {
            isSubmitting = false;
        }
    }

</script>

<svelte:head>
    <title>Contact - No Frills DB</title>
</svelte:head>

<div class="max-w-4xl mx-auto py-8 px-4 sm:px-6 lg:px-8">
    <div class="text-center mb-8">
        <h1 class="text-3xl font-bold text-gray-900 mb-4">Contact Us</h1>
        <p class="text-lg text-gray-600">
            Have a question, suggestion, or need help? We'd love to hear from you!
        </p>
    </div>

    <div class="bg-white shadow-lg rounded-lg p-6 md:p-8">
        <form on:submit|preventDefault={handleSubmit} class="space-y-6">
            <div>
                <label for="email" class="block text-sm font-medium text-gray-700 mb-2">
                    Your Email Address *
                </label>
                <input
                    type="email"
                    id="email"
                    bind:value={email}
                    required
                    class="w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
                    placeholder="Enter your email address"
                />
            </div>

            <div>
                <label for="name" class="block text-sm font-medium text-gray-700 mb-2">
                    Your Name *
                </label>
                <input
                    type="text"
                    id="name"
                    bind:value={name}
                    required
                    class="w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
                    placeholder="Enter your full name"
                />
            </div>

            <div>
                <label for="message" class="block text-sm font-medium text-gray-700 mb-2">
                    Message *
                </label>
                <textarea
                    id="message"
                    bind:value={message}
                    required
                    rows="8"
                    class="w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
                    placeholder="Please describe your question, issue, or feedback in detail..."
                ></textarea>
                <p class="mt-1 text-sm text-gray-500">You can use basic formatting like line breaks and paragraphs.</p>
            </div>

            {#if submitMessage}
                <div class="bg-green-50 border border-green-200 rounded-md p-4">
                    <div class="flex">
                        <svg class="h-5 w-5 text-green-400" fill="currentColor" viewBox="0 0 20 20">
                            <path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clip-rule="evenodd" />
                        </svg>
                        <p class="ml-3 text-sm text-green-700">{submitMessage}</p>
                    </div>
                </div>
            {/if}

            {#if submitError}
                <div class="bg-red-50 border border-red-200 rounded-md p-4">
                    <div class="flex">
                        <svg class="h-5 w-5 text-red-400" fill="currentColor" viewBox="0 0 20 20">
                            <path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z" clip-rule="evenodd" />
                        </svg>
                        <p class="ml-3 text-sm text-red-700">{submitError}</p>
                    </div>
                </div>
            {/if}

            <div>
                <button
                    type="submit"
                    disabled={isSubmitting}
                    class="w-full flex justify-center py-3 px-4 border border-transparent rounded-md shadow-sm text-sm font-medium text-white bg-indigo-600 hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
                >
                    {#if isSubmitting}
                        <svg class="animate-spin -ml-1 mr-3 h-5 w-5 text-white" fill="none" viewBox="0 0 24 24">
                            <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                            <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                        </svg>
                        Sending...
                    {:else}
                        Send Message
                    {/if}
                </button>
            </div>
        </form>
    </div>

    <div class="mt-8 text-center">
        <p class="text-sm text-gray-500">
            You can also reach us directly at
            <a href="mailto:vinayakr@nofrillsdb.com" class="text-indigo-600 hover:text-indigo-500">
                vinayakr@nofrillsdb.com
            </a>
        </p>
    </div>
</div>