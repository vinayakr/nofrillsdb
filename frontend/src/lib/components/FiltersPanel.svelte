<script lang="ts">
    import type { Deal, FilterState } from '$lib/deal';

    // Runes-style props with callback props (no createEventDispatcher)
    const { filters, deals, onFiltersChanged, onClose } =
        $props<{
            filters: FilterState;
            deals: Deal[];
            onFiltersChanged?: (f: FilterState) => void;
            onClose?: () => void;
        }>();

    // Local working copy - UI edits live here
    let localFilters: FilterState = {
        priceRange: { ...filters.priceRange },
        arvRange: { ...filters.arvRange },
        states: new Set(filters.states),
        dealStates: new Set(filters.dealStates),
        searchTerm: filters.searchTerm,
        showOnlyMappable: filters.showOnlyMappable
    };

    // Dropdown state
    let statesDropdownOpen = $state(false);

    // Derived: list of available states from deals
    const availableStates = $derived.by(() =>
        [...new Set(deals.map((d) => d.address?.state).filter(Boolean) as string[])].sort()
    );

    // Formatting helpers
    const formatCurrency = (n: number) =>
        n >= 1_000_000 ? `$${(n / 1_000_000).toFixed(1)}M`
            : n >= 1_000 ? `$${Math.round(n / 1_000)}K`
                : `$${n.toLocaleString()}`;

    // ---- Input handlers (update local only) ----
    function setPrice(type: 'min' | 'max', v: string) {
        const n = parseInt(v);
        if (!Number.isNaN(n)) localFilters.priceRange[type] = n;
    }
    function setArv(type: 'min' | 'max', v: string) {
        const n = parseInt(v);
        if (!Number.isNaN(n)) localFilters.arvRange[type] = n;
    }
    function toggleState(state: string) {
        const s = localFilters.states;
        s.has(state) ? s.delete(state) : s.add(state);
        // reassign to trigger reactivity in consumers if needed
        localFilters = { ...localFilters, states: new Set(s) };
    }
    function toggleDealState(state: 'OPEN'|'PENDING'|'COMPLETED') {
        const s = localFilters.dealStates;
        s.has(state) ? s.delete(state) : s.add(state);
        localFilters = { ...localFilters, dealStates: new Set(s) };
    }
    function selectAllStates() {
        localFilters = { ...localFilters, states: new Set(availableStates) };
    }
    function clearAllStates() {
        localFilters = { ...localFilters, states: new Set() };
    }

    function resetLocal() {
        if (!deals.length) return;
        const prices = deals.map((d) => d.sellingPrice);
        const arvs = deals.map((d) => d.arv);
        localFilters = {
            priceRange: { min: Math.min(...prices), max: Math.max(...prices) },
            arvRange:   { min: Math.min(...arvs),   max: Math.max(...arvs) },
            states: new Set(availableStates),
            dealStates: new Set(['OPEN','PENDING','COMPLETED']),
            searchTerm: '',
            showOnlyMappable: false
        };
        // Note: not emitting yet — user must click Apply
    }

    // ---- Commit / Close ----
    function apply() {
        // emit a fresh, clone-safe structure
        onFiltersChanged?.({
            priceRange: { ...localFilters.priceRange },
            arvRange:   { ...localFilters.arvRange },
            states:     new Set(localFilters.states),
            dealStates: new Set(localFilters.dealStates),
            searchTerm: localFilters.searchTerm,
            showOnlyMappable: localFilters.showOnlyMappable
        });
        onClose?.();
    }
    const close = () => {
        statesDropdownOpen = false;
        onClose?.();
    };

    // submit handler so Enter applies
    function onSubmit(e: Event) {
        e.preventDefault();
        apply();
    }
</script>

<!-- Overlay -->
<div class="fixed inset-0 z-[10000] overflow-hidden">
    <div class="absolute inset-0 overflow-hidden">
        <!-- Backdrop -->
        <div class="absolute inset-0 bg-gray-500/75" on:click={close}></div>

        <!-- Panel -->
        <div class="fixed inset-y-0 right-0 pl-4 sm:pl-10 max-w-full flex">
            <div class="w-screen max-w-md">
                <form class="h-full flex flex-col bg-white shadow-xl overflow-y-auto" on:submit={onSubmit}>
                    <!-- Header -->
                    <div class="px-4 py-6 bg-gray-50 sm:px-6 flex items-center justify-between">
                        <h2 class="text-lg font-medium text-gray-900">Filters</h2>
                        <button type="button" on:click={close}
                                class="rounded-md text-gray-400 hover:text-gray-500 focus:outline-none focus:ring-2 focus:ring-indigo-500">
                            <svg class="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"/>
                            </svg>
                        </button>
                    </div>

                    <!-- Body -->
                    <div class="flex-1 px-4 py-6 sm:px-6 space-y-6">
                        <!-- Search -->
                        <div>
                            <label class="block text-sm font-medium text-gray-700 mb-2">Search</label>
                            <input
                                    type="text"
                                    placeholder="Search address, city, parcel number..."
                                    bind:value={localFilters.searchTerm}
                                    class="w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm"
                            />
                        </div>

                        <!-- Selling Price -->
                        <div>
                            <label class="block text-sm font-medium text-gray-700 mb-2">Selling Price Range</label>
                            <div class="grid grid-cols-2 gap-3">
                                <div>
                                    <label class="block text-xs text-gray-500 mb-1">Min</label>
                                    <input type="number" value={localFilters.priceRange.min}
                                           on:input={(e) => setPrice('min', (e.target as HTMLInputElement).value)}
                                           class="w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm"/>
                                </div>
                                <div>
                                    <label class="block text-xs text-gray-500 mb-1">Max</label>
                                    <input type="number" value={localFilters.priceRange.max}
                                           on:input={(e) => setPrice('max', (e.target as HTMLInputElement).value)}
                                           class="w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm"/>
                                </div>
                            </div>
                            <div class="mt-1 text-xs text-gray-500">
                                {formatCurrency(localFilters.priceRange.min)} – {formatCurrency(localFilters.priceRange.max)}
                            </div>
                        </div>

                        <!-- ARV -->
                        <div>
                            <label class="block text-sm font-medium text-gray-700 mb-2">ARV Range</label>
                            <div class="grid grid-cols-2 gap-3">
                                <div>
                                    <label class="block text-xs text-gray-500 mb-1">Min</label>
                                    <input type="number" value={localFilters.arvRange.min}
                                           on:input={(e) => setArv('min', (e.target as HTMLInputElement).value)}
                                           class="w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm"/>
                                </div>
                                <div>
                                    <label class="block text-xs text-gray-500 mb-1">Max</label>
                                    <input type="number" value={localFilters.arvRange.max}
                                           on:input={(e) => setArv('max', (e.target as HTMLInputElement).value)}
                                           class="w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm"/>
                                </div>
                            </div>
                            <div class="mt-1 text-xs text-gray-500">
                                {formatCurrency(localFilters.arvRange.min)} – {formatCurrency(localFilters.arvRange.max)}
                            </div>
                        </div>

                        <!-- Deal Status -->
                        <div>
                            <label class="block text-sm font-medium text-gray-700 mb-2">Deal Status</label>
                            <div class="space-y-2">
                                {#each ['OPEN','PENDING','COMPLETED'] as dealState}
                                    <label class="flex items-center">
                                        <input type="checkbox"
                                               checked={localFilters.dealStates.has(dealState as any)}
                                               on:change={() => toggleDealState(dealState as any)}
                                               class="h-4 w-4 text-indigo-600 focus:ring-indigo-500 border-gray-300 rounded"/>
                                        <span class="ml-2 text-sm text-gray-700">{dealState}</span>
                                    </label>
                                {/each}
                            </div>
                        </div>

                        <!-- States -->
                        {#if availableStates.length > 0}
                            <div>
                                <label class="block text-sm font-medium text-gray-700 mb-2">States</label>
                                <div class="relative">
                                    <button type="button"
                                            class="w-full bg-white border border-gray-300 rounded-md px-3 py-2 text-left shadow-sm focus:outline-none focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm"
                                            on:click={() => statesDropdownOpen = !statesDropdownOpen}>
                                        <span class="block truncate">
                                            {#if localFilters.states.size === 0}
                                                Select states...
                                            {:else if localFilters.states.size === availableStates.length}
                                                All states selected
                                            {:else}
                                                {localFilters.states.size} state{localFilters.states.size === 1 ? '' : 's'} selected
                                            {/if}
                                        </span>
                                        <span class="absolute inset-y-0 right-0 flex items-center pr-2 pointer-events-none">
                                            <svg class="h-5 w-5 text-gray-400 transition-transform duration-200 {statesDropdownOpen ? 'rotate-180' : ''}" viewBox="0 0 20 20" fill="currentColor">
                                                <path fill-rule="evenodd" d="M5.293 7.293a1 1 0 011.414 0L10 10.586l3.293-3.293a1 1 0 111.414 1.414l-4 4a1 1 0 01-1.414 0l-4-4a1 1 0 010-1.414z" clip-rule="evenodd"/>
                                            </svg>
                                        </span>
                                    </button>

                                    {#if statesDropdownOpen}
                                        <div class="absolute z-10 mt-1 w-full bg-white shadow-lg max-h-60 rounded-md py-1 text-base ring-1 ring-black ring-opacity-5 overflow-auto focus:outline-none sm:text-sm">
                                            <div class="px-3 py-2 border-b border-gray-200">
                                                <div class="flex space-x-2">
                                                    <button type="button"
                                                            class="text-xs text-indigo-600 hover:text-indigo-800"
                                                            on:click={selectAllStates}>
                                                        Select All
                                                    </button>
                                                    <button type="button"
                                                            class="text-xs text-gray-600 hover:text-gray-800"
                                                            on:click={clearAllStates}>
                                                        Clear All
                                                    </button>
                                                </div>
                                            </div>
                                            {#each availableStates as state}
                                                <div class="relative cursor-pointer select-none py-2 pl-3 pr-9 hover:bg-gray-50"
                                                     on:click={() => toggleState(state)}
                                                     role="option"
                                                     tabindex="-1">
                                                    <span class="block truncate text-gray-900">{state}</span>
                                                    {#if localFilters.states.has(state)}
                                                        <span class="absolute inset-y-0 right-0 flex items-center pr-4 text-indigo-600">
                                                            <svg class="h-5 w-5" viewBox="0 0 20 20" fill="currentColor">
                                                                <path fill-rule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clip-rule="evenodd"/>
                                                            </svg>
                                                        </span>
                                                    {/if}
                                                </div>
                                            {/each}
                                        </div>
                                    {/if}
                                </div>
                            </div>
                        {/if}
                    </div>

                    <!-- Footer -->
                    <div class="px-4 py-4 bg-gray-50 sm:px-6">
                        <div class="flex space-x-3">
                            <button type="button" on:click={resetLocal}
                                    class="flex-1 bg-white py-2 px-4 border border-gray-300 rounded-md shadow-sm text-sm font-medium text-gray-700 hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500">
                                Reset
                            </button>
                            <button type="submit"
                                    class="flex-1 bg-indigo-600 py-2 px-4 border border-transparent rounded-md shadow-sm text-sm font-medium text-white hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500">
                                Apply
                            </button>
                        </div>
                    </div>
                </form>
            </div>
        </div>

    </div>
</div>