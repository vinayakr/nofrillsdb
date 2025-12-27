<script lang="ts">
    import { browser } from '$app/environment';
    import { PUBLIC_MAPBOX_TOKEN } from '$env/static/public';
    import { tick } from 'svelte';
    import type { Deal } from '$lib/deal';

    const {
        deals,
        token = PUBLIC_MAPBOX_TOKEN,
        onReady,
        onVisibleDealsChange
    } =
        $props<{
            deals: Deal[];
            token?: string;
            onReady?: (map: any) => void;
            onVisibleDealsChange?: (ids: Array<Deal['dealId']>) => void;
        }>();

    let mapContainer: HTMLDivElement | null = null;
    let mapboxgl: any = null;
    let map: any = null;

    // track marker + its dealId so we can compute which are visible
    let markers: Array<{ marker: any; dealId: Deal['dealId'] }> = [];

    let mapReady = false;
    let generation = 0;
    let searchQuery = $state('');
    let searchSuggestions = $state<Array<{ place_name: string; center: [number, number] }>>([]);
    let showSuggestions = $state(false);
    let searchLoading = $state(false);

    const geocodeCache = new Map<string, [number, number]>();

    // initialize map
    $effect.pre(() => {
        (async () => {
            if (!browser || map) return;

            // wait for bind:this
            let tries = 0;
            while (!mapContainer && tries < 120) {
                await tick();
                tries++;
            }
            if (!mapContainer) return;

            // wait for non-zero size
            tries = 0;
            while (tries < 120 && (mapContainer.offsetWidth === 0 || mapContainer.offsetHeight === 0)) {
                await new Promise((r) => setTimeout(r, 50));
                tries++;
            }
            if (mapContainer.offsetWidth === 0 || mapContainer.offsetHeight === 0) return;

            const mod = await import('mapbox-gl');
            mapboxgl = mod.default ?? mod;
            mapboxgl.accessToken = token || '';

            map = new mapboxgl.Map({
                container: mapContainer,
                style: 'mapbox://styles/mapbox/streets-v12',
                center: [-96, 37.8],
                zoom: 4,
                attributionControl: false
            });

            map.addControl(new mapboxgl.NavigationControl(), 'top-right');
            map.addControl(new mapboxgl.AttributionControl({ compact: true }), 'bottom-right');

            map.on('load', () => {
                mapReady = true;
                onReady?.(map);
                updateMarkers();
                map.resize();
                requestAnimationFrame(() => map && map.resize());

                // recompute visible deals whenever the map moves / zooms
                map.on('moveend', updateVisibleDeals);
                map.on('zoomend', updateVisibleDeals);
            });
        })();
    });

    // cleanup
    $effect(() => {
        return () => {
            if (!browser) return;
            markers.forEach((m) => m.marker.remove?.());
            markers = [];
            map?.remove?.();
            map = null;
            mapReady = false;
        };
    });

    // re-render markers when deals change
    $effect(() => {
        void deals;
        if (browser && mapReady && map) updateMarkers();
    });

    function addrToString(addr: NonNullable<Deal['address']>): string {
        return `${addr.street}, ${addr.city}, ${addr.state} ${addr.zip}`;
    }

    async function geocode(address: NonNullable<Deal['address']>): Promise<[number, number] | null> {
        const full = addrToString(address);
        if (geocodeCache.has(full)) return geocodeCache.get(full)!;

        const url = `https://api.mapbox.com/geocoding/v5/mapbox.places/${encodeURIComponent(
            full
        )}.json?access_token=${token}&limit=1&autocomplete=false`;
        const res = await fetch(url);
        if (!res.ok) return null;
        const data = await res.json();
        const coords = data?.features?.[0]?.center as [number, number] | undefined;
        if (!coords) return null;
        geocodeCache.set(full, coords);
        return coords;
    }

    // NEW: compute which markers/deals are visible in the current map bounds
    function updateVisibleDeals() {
        if (!map || !mapReady || !onVisibleDealsChange) return;
        const bounds = map.getBounds();
        const visibleIds = markers
            .filter((entry) => bounds.contains(entry.marker.getLngLat()))
            .map((entry) => entry.dealId);
        onVisibleDealsChange(visibleIds);
    }

    async function updateMarkers() {
        if (!map || !mapReady) return;

        const myGen = ++generation;

        // clear old markers
        markers.forEach((m) => m.marker.remove?.());
        markers = [];

        const list = deals ?? [];
        if (list.length === 0) {
            // no deals => nothing visible
            onVisibleDealsChange?.([]);
            return;
        }

        // build all markers; each respects generation to avoid stale pins
        const built = await Promise.all(
            list.map(async (deal) => {
                if (!deal?.address) return null;

                const coords = await geocode(deal.address);
                if (myGen !== generation || !coords) return null;

                const el = document.createElement('div');
                el.className = 'deal-marker';
                el.innerHTML = `
                    <div class="marker-pin ${markerClass(deal.dealState)}">
                        <span class="marker-price">$${formatPrice(deal.sellingPrice)}</span>
                    </div>
                `;

                const popupHtml = `
                    <div class="deal-popup">
                        <div class="deal-popup-header">
                            <span class="deal-status ${deal.dealState.toLowerCase()}">${deal.dealState}</span>
                        </div>
                        <div class="deal-popup-address">
                            ${deal.address.street ?? ''}<br>
                            ${deal.address.city ?? ''}, ${deal.address.state ?? ''} ${deal.address.zip ?? ''}
                        </div>
                        <div class="deal-popup-prices">
                            <div class="price-item"><span class="price-label">Selling Price:</span><span class="price-value">$${Number(
                    deal.sellingPrice ?? 0
                ).toLocaleString()}</span></div>
                            <div class="price-item"><span class="price-label">ARV:</span><span class="price-value">$${Number(
                    deal.arv ?? 0
                ).toLocaleString()}</span></div>
                        </div>
                        ${
                    deal.description
                        ? `<div class="deal-popup-comments">${deal.description.replace(/<[^>]*>/g, '')}</div>`
                        : ''
                }
                        <button class="deal-popup-view" onclick="window.location.href='/deals/${deal.dealId}'">View Details</button>
                    </div>
                `;

                const popup = new mapboxgl.Popup({ offset: 25 }).setHTML(popupHtml);

                const marker = new mapboxgl.Marker({ element: el })
                    .setLngLat(coords)
                    .setPopup(popup)
                    .addTo(map);

                return { marker, dealId: deal.dealId };
            })
        );

        // if a newer update started, discard these
        if (myGen !== generation) {
            built.forEach((m) => m?.marker?.remove?.());
            return;
        }

        markers = built.filter(Boolean) as Array<{ marker: any; dealId: Deal['dealId'] }>;

        // fit/center
        if (markers.length === 1) {
            map.easeTo({ center: markers[0].marker.getLngLat(), zoom: 12, duration: 300 });
        } else if (markers.length > 1) {
            const bounds = new mapboxgl.LngLatBounds();
            markers.forEach((entry) => bounds.extend(entry.marker.getLngLat()));
            map.fitBounds(bounds, { padding: 50, maxZoom: 15, duration: 300 });
        }

        // after markers update, recompute which deals are visible
        updateVisibleDeals();
    }

    function markerClass(state: Deal['dealState']): string {
        switch (state) {
            case 'OPEN':
                return 'marker-open';
            case 'PENDING':
                return 'marker-pending';
            case 'COMPLETED':
                return 'marker-completed';
            default:
                return 'marker-open';
        }
    }

    function formatPrice(price: number): string {
        if (price >= 1_000_000) return (price / 1_000_000).toFixed(1) + 'M';
        if (price >= 1_000) return (price / 1_000).toFixed(0) + 'K';
        return String(price ?? 0);
    }

    // Search functionality
    let searchTimeout: number;

    async function searchPlaces(query: string) {
        if (!query.trim() || !token) {
            searchSuggestions = [];
            showSuggestions = false;
            return;
        }

        searchLoading = true;

        try {
            const url = `https://api.mapbox.com/geocoding/v5/mapbox.places/${encodeURIComponent(
                query
            )}.json?access_token=${token}&limit=5&autocomplete=true&types=place,locality,neighborhood,address`;
            const response = await fetch(url);

            if (!response.ok) {
                throw new Error('Search failed');
            }

            const data = await response.json();
            searchSuggestions =
                data.features?.map((feature: any) => ({
                    place_name: feature.place_name,
                    center: feature.center
                })) || [];
            showSuggestions = searchSuggestions.length > 0;
        } catch (error) {
            searchSuggestions = [];
            showSuggestions = false;
        } finally {
            searchLoading = false;
        }
    }

    function handleSearchInput(event: Event) {
        const target = event.target as HTMLInputElement;
        searchQuery = target.value;

        clearTimeout(searchTimeout);
        searchTimeout = window.setTimeout(() => {
            searchPlaces(searchQuery);
        }, 300);
    }

    function selectPlace(place: { place_name: string; center: [number, number] }) {
        if (!map) return;

        searchQuery = place.place_name;
        showSuggestions = false;

        map.easeTo({
            center: place.center,
            zoom: 12,
            duration: 1000
        });
    }

    function clearSearch() {
        searchQuery = '';
        searchSuggestions = [];
        showSuggestions = false;
    }
</script>

<div class="w-full h-full relative">
    <!-- Map Container -->
    <div class="w-full h-full" bind:this={mapContainer}></div>

    <!-- Search Box -->
    <div class="absolute top-4 left-4 right-4 z-10">
        <div class="relative max-w-sm">
            <div class="relative">
                <input
                        type="text"
                        bind:value={searchQuery}
                        on:input={handleSearchInput}
                        on:focus={() => (showSuggestions = searchSuggestions.length > 0)}
                        placeholder="Search for a location..."
                        class="w-full pl-10 pr-10 py-3 bg-white rounded-lg shadow-lg border border-gray-200 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent text-sm"
                />

                <!-- Search Icon -->
                <div class="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                    {#if searchLoading}
                        <svg class="animate-spin h-5 w-5 text-gray-400" viewBox="0 0 24 24">
                            <circle
                                    class="opacity-25"
                                    cx="12"
                                    cy="12"
                                    r="10"
                                    stroke="currentColor"
                                    stroke-width="4"
                                    fill="none"
                            ></circle>
                            <path
                                    class="opacity-75"
                                    fill="currentColor"
                                    d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"
                            ></path>
                        </svg>
                    {:else}
                        <svg class="h-5 w-5 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path
                                    stroke-linecap="round"
                                    stroke-linejoin="round"
                                    stroke-width="2"
                                    d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"
                            ></path>
                        </svg>
                    {/if}
                </div>

                <!-- Clear Button -->
                {#if searchQuery}
                    <button
                            on:click={clearSearch}
                            class="absolute inset-y-0 right-0 pr-3 flex items-center text-gray-400 hover:text-gray-600 transition-colors"
                    >
                        <svg class="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path>
                        </svg>
                    </button>
                {/if}
            </div>

            <!-- Search Suggestions -->
            {#if showSuggestions && searchSuggestions.length > 0}
                <div
                        class="absolute top-full left-0 right-0 mt-1 bg-white rounded-lg shadow-lg border border-gray-200 overflow-hidden z-20"
                >
                    {#each searchSuggestions as suggestion}
                        <button
                                on:click={() => selectPlace(suggestion)}
                                class="w-full px-4 py-3 text-left hover:bg-gray-50 focus:bg-gray-50 focus:outline-none border-b border-gray-100 last:border-b-0 transition-colors"
                        >
                            <div class="flex items-center">
                                <svg
                                        class="h-4 w-4 text-gray-400 mr-3 flex-shrink-0"
                                        fill="none"
                                        stroke="currentColor"
                                        viewBox="0 0 24 24"
                                >
                                    <path
                                            stroke-linecap="round"
                                            stroke-linejoin="round"
                                            stroke-width="2"
                                            d="M17.657 16.657L13.414 20.9a1.998 1.998 0 01-2.827 0l-4.244-4.243a8 8 0 1111.314 0z"
                                    ></path>
                                    <path
                                            stroke-linecap="round"
                                            stroke-linejoin="round"
                                            stroke-width="2"
                                            d="M15 11a3 3 0 11-6 0 3 3 0 016 0z"
                                    ></path>
                                </svg>
                                <span class="text-sm text-gray-700 truncate">{suggestion.place_name}</span>
                            </div>
                        </button>
                    {/each}
                </div>
            {/if}
        </div>
    </div>
</div>

<style>
    :global(.mapboxgl-canvas) {
        width: 100%;
        height: 100%;
    }

    :global(.deal-marker .marker-pin) {
        background: white;
        border-radius: 50% 50% 50% 0;
        box-shadow: 0 2px 10px rgba(0, 0, 0, 0.2);
        cursor: pointer;
        height: 30px;
        width: 30px;
        position: relative;
        transform: rotate(-45deg);
        border: 2px solid;
        display: flex;
        align-items: center;
        justify-content: center;
    }
    :global(.deal-marker .marker-pin.marker-open) {
        border-color: #059669;
        background: #10b981;
    }
    :global(.deal-marker .marker-pin.marker-pending) {
        border-color: #d97706;
        background: #f59e0b;
    }
    :global(.deal-marker .marker-pin.marker-completed) {
        border-color: #7c3aed;
        background: #8b5cf6;
    }
    :global(.deal-marker .marker-price) {
        color: white;
        font-size: 10px;
        font-weight: 600;
        transform: rotate(45deg);
        white-space: nowrap;
    }

    :global(.mapboxgl-popup-content) {
        padding: 0;
        border-radius: 8px;
        box-shadow: 0 10px 25px rgba(0, 0, 0, 0.15);
    }
    :global(.deal-popup) {
        padding: 16px;
        min-width: 250px;
    }
    :global(.deal-popup-header) {
        margin-bottom: 8px;
    }
    :global(.deal-status) {
        padding: 4px 8px;
        border-radius: 12px;
        font-size: 12px;
        font-weight: 600;
        text-transform: uppercase;
    }
    :global(.deal-status.open) {
        background: #d1fae5;
        color: #059669;
    }
    :global(.deal-status.pending) {
        background: #fef3c7;
        color: #d97706;
    }
    :global(.deal-status.completed) {
        background: #e0e7ff;
        color: #7c3aed;
    }
    :global(.deal-popup-address) {
        margin-bottom: 12px;
        color: #4b5563;
        line-height: 1.4;
    }
    :global(.deal-popup-prices) {
        margin-bottom: 12px;
    }
    :global(.price-item) {
        display: flex;
        justify-content: space-between;
        margin-bottom: 4px;
    }
    :global(.price-label) {
        color: #6b7280;
        font-size: 14px;
    }
    :global(.price-value) {
        font-weight: 600;
        color: #111827;
    }
    :global(.deal-popup-comments) {
        margin-bottom: 12px;
        padding: 8px;
        background: #f9fafb;
        border-radius: 4px;
        font-size: 14px;
        color: #4b5563;
    }
    :global(.deal-popup-view) {
        width: 100%;
        background: #4f46e5;
        color: white;
        border: none;
        border-radius: 6px;
        padding: 8px 16px;
        font-weight: 500;
        cursor: pointer;
        transition: background 0.2s;
    }
    :global(.deal-popup-view:hover) {
        background: #3730a3;
    }
</style>