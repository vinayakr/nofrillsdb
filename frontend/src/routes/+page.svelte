<script lang="ts">
    import { loggedIn, signUp } from '$lib/auth';
    import { goto } from '$app/navigation';

    let isAuthenticated = false;

    loggedIn.subscribe(value => {
        isAuthenticated = value;
    });

    function handleGetStarted() {
        if (isAuthenticated) {
            goto('/databases');
        } else {
            signUp();
        }
    }

    function handleSignUp() {
        signUp();
    }
</script>

<svelte:head>
    <title>No Frills DB - Affordable Shared PostgreSQL for Development</title>
    <meta name="description" content="Low-cost shared PostgreSQL database service. Perfect for development, testing, and small business workloads. First 100MB free, then $1/GB/month." />
</svelte:head>

<!-- Hero Section -->
<section class="bg-gradient-to-br from-indigo-50 to-blue-100 py-16 lg:py-24">
    <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="text-center">
            <h1 class="text-4xl md:text-6xl font-bold text-gray-900 mb-6">
                No-Frills<span class="text-indigo-600"> PostgreSQL</span>
            </h1>
            <p class="text-xl md:text-2xl text-gray-600 mb-4 max-w-3xl mx-auto">
                Dead-simple managed PostgreSQL for developers who don't want the hassle
            </p>
            <p class="text-lg text-gray-500 mb-4 max-w-2xl mx-auto">
                Skip the setup hassle. Get a managed PostgreSQL database without the enterprise price tag.
            </p>

            <!-- Trust Signal -->
            <div class="text-sm text-gray-400 mb-8">
                Standard PostgreSQL 18 • Standard psql connections
            </div>

            <!-- CTA Buttons -->
            <div class="flex flex-col sm:flex-row gap-4 justify-center items-center">
                <button
                    on:click={handleGetStarted}
                    class="bg-indigo-600 hover:bg-indigo-700 text-white px-8 py-4 rounded-lg text-lg font-semibold transition-colors shadow-lg"
                >
                    {isAuthenticated ? 'Go to Dashboard' : 'Start Free'}
                </button>
                <div class="text-sm text-gray-500">
                    Unlimited databases • First 100MB free • No credit card required
                </div>
            </div>
        </div>
    </div>
</section>

<!-- Pricing Section -->
<section class="py-16 bg-white">
    <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="text-center mb-12">
            <h2 class="text-3xl font-bold text-gray-900 mb-4">Simple, Transparent Pricing</h2>
            <p class="text-lg text-gray-600">Pay only for what you use. No hidden fees, no surprises.</p>
        </div>

        <div class="max-w-4xl mx-auto">
            <!-- Free Tier -->
            <div class="bg-green-50 border-2 border-green-200 rounded-lg p-6 mb-6">
                <div class="flex items-center justify-between flex-wrap">
                    <div>
                        <h3 class="text-xl font-bold text-green-800">Free Tier</h3>
                        <p class="text-green-600">Perfect for getting started</p>
                    </div>
                    <div class="text-right">
                        <div class="text-3xl font-bold text-green-800">$0</div>
                        <div class="text-sm text-green-600">First 100MB</div>
                    </div>
                </div>
            </div>

            <!-- Paid Tier -->
            <div class="bg-indigo-50 border-2 border-indigo-200 rounded-lg p-6">
                <div class="flex items-center justify-between flex-wrap mb-4">
                    <div>
                        <h3 class="text-xl font-bold text-indigo-800">Pay-as-you-grow</h3>
                        <p class="text-indigo-600">Scale with your needs</p>
                    </div>
                    <div class="text-right">
                        <div class="text-3xl font-bold text-indigo-800">$1<span class="text-lg">/GB</span></div>
                        <div class="text-sm text-indigo-600">per month</div>
                    </div>
                </div>
                <div class="bg-white rounded-md p-4">
                    <div class="text-sm text-gray-600 mb-2">
                        <strong>Minimum billing:</strong> $5/month for up to 5GB
                    </div>
                    <div class="text-xs text-gray-500">
                        Example: If you use 2GB, you pay $5. If you use 8GB, you pay $8.
                    </div>
                </div>
            </div>
        </div>
    </div>
</section>

<!-- Features Section -->
<section class="py-16 bg-gray-50">
    <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="text-center mb-12">
            <h2 class="text-3xl font-bold text-gray-900 mb-4">What You Get</h2>
            <p class="text-lg text-gray-600">Everything you need to get started, without the complexity</p>
        </div>

        <div class="grid md:grid-cols-2 lg:grid-cols-3 gap-8">
            <!-- Unlimited Databases -->
            <div class="bg-white p-6 rounded-lg shadow-sm">
                <div class="w-12 h-12 bg-indigo-100 rounded-lg flex items-center justify-center mb-4">
                    <svg class="w-6 h-6 text-indigo-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 7v10c0 2.21 3.582 4 8 4s8-1.79 8-4V7M4 7c0 2.21 3.582 4 8 4s8-1.79 8-4M4 7c0-2.21 3.582-4 8-4s8 1.79 8 4" />
                    </svg>
                </div>
                <h3 class="text-lg font-semibold text-gray-900 mb-2">Unlimited Databases</h3>
                <p class="text-gray-600">Create as many PostgreSQL databases as you need. Organize by project, environment, or however works for you.</p>
            </div>

            <!-- Easy Setup -->
            <div class="bg-white p-6 rounded-lg shadow-sm">
                <div class="w-12 h-12 bg-green-100 rounded-lg flex items-center justify-center mb-4">
                    <svg class="w-6 h-6 text-green-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 10V3L4 14h7v7l9-11h-7z" />
                    </svg>
                </div>
                <h3 class="text-lg font-semibold text-gray-900 mb-2">Instant Setup</h3>
                <p class="text-gray-600">Skip the server setup, configuration, and maintenance. Get a working PostgreSQL database in seconds.</p>
            </div>

            <!-- Standard PostgreSQL -->
            <div class="bg-white p-6 rounded-lg shadow-sm">
                <div class="w-12 h-12 bg-purple-100 rounded-lg flex items-center justify-center mb-4">
                    <svg class="w-6 h-6 text-purple-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
                    </svg>
                </div>
                <h3 class="text-lg font-semibold text-gray-900 mb-2">Standard PostgreSQL</h3>
                <p class="text-gray-600">Full PostgreSQL compatibility. Use your favorite tools, ORMs, and frameworks without changes.</p>
            </div>

            <!-- Password & Certificate Auth -->
            <div class="bg-white p-6 rounded-lg shadow-sm">
                <div class="w-12 h-12 bg-blue-100 rounded-lg flex items-center justify-center mb-4">
                    <svg class="w-6 h-6 text-blue-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z" />
                    </svg>
                </div>
                <h3 class="text-lg font-semibold text-gray-900 mb-2">Secure Authentication</h3>
                <p class="text-gray-600">Choose between password-based or certificate-based authentication to secure your databases.</p>
            </div>

            <!-- JDBC Ready -->
            <div class="bg-white p-6 rounded-lg shadow-sm">
                <div class="w-12 h-12 bg-yellow-100 rounded-lg flex items-center justify-center mb-4">
                    <svg class="w-6 h-6 text-yellow-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10 20l4-16m4 4l4 4-4 4M6 16l-4-4 4-4" />
                    </svg>
                </div>
                <h3 class="text-lg font-semibold text-gray-900 mb-2">JDBC Ready</h3>
                <p class="text-gray-600">Connect from any application with standard PostgreSQL connection strings. Works with all major frameworks.</p>
            </div>

            <!-- Always On -->
            <div class="bg-white p-6 rounded-lg shadow-sm">
                <div class="w-12 h-12 bg-orange-100 rounded-lg flex items-center justify-center mb-4">
                    <svg class="w-6 h-6 text-orange-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9.663 17h4.673M12 3v1m6.364 1.636l-.707.707M21 12h-1M4 12H3m3.343-5.657l-.707-.707m2.828 9.9a5 5 0 117.072 0l-.548.547A3.374 3.374 0 0014 18.469V19a2 2 0 11-4 0v-.531c0-.895-.356-1.754-.988-2.386l-.548-.547z" />
                    </svg>
                </div>
                <h3 class="text-lg font-semibold text-gray-900 mb-2">Always On</h3>
                <p class="text-gray-600">Your databases are always available 24/7. No cold starts, no sleep modes, no downtime for maintenance windows.</p>
            </div>

            <!-- Low Cost -->
            <div class="bg-white p-6 rounded-lg shadow-sm">
                <div class="w-12 h-12 bg-red-100 rounded-lg flex items-center justify-center mb-4">
                    <svg class="w-6 h-6 text-red-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8c-1.657 0-3 .895-3 2s1.343 2 3 2 3 .895 3 2-1.343 2-3 2m0-8c1.11 0 2.08.402 2.599 1M12 8V7m0 1v8m0 0v1m0-1c-1.11 0-2.08-.402-2.599-1" />
                    </svg>
                </div>
                <h3 class="text-lg font-semibold text-gray-900 mb-2">Cost Effective</h3>
                <p class="text-gray-600">Pay only for storage used. No expensive compute charges, connection limits, or hidden fees.</p>
            </div>
        </div>
    </div>
</section>

<!-- Who This Is For Section -->
<section class="py-16 bg-white">
    <div class="max-w-6xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="text-center mb-12">
            <h2 class="text-3xl font-bold text-gray-900 mb-4">Is This Right For You?</h2>
            <p class="text-lg text-gray-600">This dramatically reduces bad-fit users and builds trust</p>
        </div>

        <div class="grid md:grid-cols-2 gap-8">
            <!-- Good Fit -->
            <div class="bg-green-50 border border-green-200 rounded-lg p-6">
                <div class="flex items-center mb-4">
                    <div class="w-8 h-8 bg-green-500 rounded-full flex items-center justify-center mr-3">
                        <svg class="w-5 h-5 text-white" fill="currentColor" viewBox="0 0 20 20">
                            <path fill-rule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clip-rule="evenodd" />
                        </svg>
                    </div>
                    <h3 class="text-xl font-semibold text-green-800">Good Fit</h3>
                </div>

                <ul class="space-y-3 text-green-700">
                    <li class="flex items-start">
                        <span class="w-2 h-2 bg-green-500 rounded-full mt-2 mr-3 flex-shrink-0"></span>
                        <span>Side projects</span>
                    </li>
                    <li class="flex items-start">
                        <span class="w-2 h-2 bg-green-500 rounded-full mt-2 mr-3 flex-shrink-0"></span>
                        <span>Internal tools</span>
                    </li>
                    <li class="flex items-start">
                        <span class="w-2 h-2 bg-green-500 rounded-full mt-2 mr-3 flex-shrink-0"></span>
                        <span>Early-stage SaaS</span>
                    </li>
                    <li class="flex items-start">
                        <span class="w-2 h-2 bg-green-500 rounded-full mt-2 mr-3 flex-shrink-0"></span>
                        <span>Dev/test environments</span>
                    </li>
                </ul>
            </div>

            <!-- Not a Fit -->
            <div class="bg-red-50 border border-red-200 rounded-lg p-6">
                <div class="flex items-center mb-4">
                    <div class="w-8 h-8 bg-red-500 rounded-full flex items-center justify-center mr-3">
                        <svg class="w-5 h-5 text-white" fill="currentColor" viewBox="0 0 20 20">
                            <path fill-rule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clip-rule="evenodd" />
                        </svg>
                    </div>
                    <h3 class="text-xl font-semibold text-red-800">Not a Fit</h3>
                </div>

                <ul class="space-y-3 text-red-700">
                    <li class="flex items-start">
                        <span class="w-2 h-2 bg-red-500 rounded-full mt-2 mr-3 flex-shrink-0"></span>
                        <span>Mission-critical production</span>
                    </li>
                    <li class="flex items-start">
                        <span class="w-2 h-2 bg-red-500 rounded-full mt-2 mr-3 flex-shrink-0"></span>
                        <span>Compliance workloads</span>
                    </li>
                    <li class="flex items-start">
                        <span class="w-2 h-2 bg-red-500 rounded-full mt-2 mr-3 flex-shrink-0"></span>
                        <span>Zero-downtime requirements</span>
                    </li>
                    <li class="flex items-start">
                        <span class="w-2 h-2 bg-red-500 rounded-full mt-2 mr-3 flex-shrink-0"></span>
                        <span>High-performance needs</span>
                    </li>
                </ul>
            </div>
        </div>
    </div>
</section>

<!-- Important Notes Section -->
<section class="py-16 bg-amber-50">
    <div class="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="text-center mb-8">
            <div class="w-16 h-16 bg-amber-100 rounded-full flex items-center justify-center mx-auto mb-4">
                <svg class="w-8 h-8 text-amber-600" fill="currentColor" viewBox="0 0 20 20">
                    <path fill-rule="evenodd" d="M8.257 3.099c.765-1.36 2.722-1.36 3.486 0l5.58 9.92c.75 1.334-.213 2.98-1.742 2.98H4.42c-1.53 0-2.493-1.646-1.743-2.98l5.58-9.92zM11 13a1 1 0 11-2 0 1 1 0 012 0zm-1-8a1 1 0 00-1 1v3a1 1 0 002 0V6a1 1 0 00-1-1z" clip-rule="evenodd" />
                </svg>
            </div>
            <h2 class="text-2xl font-bold text-amber-800 mb-4">Important: Not for Critical Workloads</h2>
        </div>

        <div class="bg-white rounded-lg p-6 shadow-sm">
            <p class="text-amber-800 mb-4 font-medium">
                No backups. You are responsible for your data.<br>
                Designed for dev, test, and lightweight production workloads.
            </p>

            <ul class="space-y-2 text-amber-700">
                <li class="flex items-start">
                    <svg class="w-5 h-5 text-amber-600 mt-0.5 mr-2 flex-shrink-0" fill="currentColor" viewBox="0 0 20 20">
                        <path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z" clip-rule="evenodd" />
                    </svg>
                    <span><strong>No automated backups</strong> - You're responsible for backing up your data</span>
                </li>
                <li class="flex items-start">
                    <svg class="w-5 h-5 text-amber-600 mt-0.5 mr-2 flex-shrink-0" fill="currentColor" viewBox="0 0 20 20">
                        <path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z" clip-rule="evenodd" />
                    </svg>
                    <span><strong>Shared resources</strong> - Performance may vary based on other users</span>
                </li>
                <li class="flex items-start">
                    <svg class="w-5 h-5 text-amber-600 mt-0.5 mr-2 flex-shrink-0" fill="currentColor" viewBox="0 0 20 20">
                        <path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z" clip-rule="evenodd" />
                    </svg>
                    <span><strong>Basic SLA</strong> - Best effort uptime, no guaranteed availability</span>
                </li>
                <li class="flex items-start">
                    <svg class="w-5 h-5 text-amber-600 mt-0.5 mr-2 flex-shrink-0" fill="currentColor" viewBox="0 0 20 20">
                        <path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z" clip-rule="evenodd" />
                    </svg>
                    <span><strong>Limited features</strong> - Advanced PostgreSQL features may be added in future</span>
                </li>
            </ul>

            <div class="mt-4 p-4 bg-amber-100 rounded-md">
                <p class="text-amber-800 text-sm">
                    <strong>For production workloads,</strong> consider enterprise PostgreSQL solutions with dedicated resources, automated backups, and SLA guarantees.
                </p>
            </div>
        </div>
    </div>
</section>

<!-- CTA Section -->
<section class="py-16 bg-indigo-600">
    <div class="max-w-4xl mx-auto text-center px-4 sm:px-6 lg:px-8">
        <h2 class="text-3xl font-bold text-white mb-4">Ready to Get Started?</h2>
        <p class="text-xl text-indigo-100 mb-8">
            Create your first PostgreSQL database in under a minute
        </p>

        <div class="flex flex-col sm:flex-row gap-4 justify-center items-center">
            <button
                on:click={handleSignUp}
                class="bg-white hover:bg-gray-50 text-indigo-600 px-8 py-4 rounded-lg text-lg font-semibold transition-colors shadow-lg"
            >
                Sign Up Free
            </button>
            <div class="text-indigo-200 text-sm">
                No credit card required • Free 100MB to start
            </div>
        </div>
    </div>
</section>