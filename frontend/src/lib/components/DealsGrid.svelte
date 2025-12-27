<script lang="ts">
    import type {Deal} from '$lib/deal';

    // Keep props intact (no destructuring) so runes reactivity works
    const props = $props<{ deals: Deal[] }>();

    function handleCardKeydown(event: KeyboardEvent, dealId: number) {
        if (event.key === 'Enter' || event.key === ' ') {
            event.preventDefault();
            goToDeal(dealId);
        }
    }

    function formatCurrency(amount: number): string {
        return new Intl.NumberFormat('en-US', {
            style: 'currency',
            currency: 'USD',
            minimumFractionDigits: 0,
            maximumFractionDigits: 0
        }).format(amount ?? 0);
    }

    function getStatusColor(status: string): string {
        switch (status) {
            case 'OPEN':
                return 'bg-green-100 text-green-800';
            case 'PENDING':
                return 'bg-yellow-100 text-yellow-800';
            case 'COMPLETED':
                return 'bg-purple-100 text-purple-800';
            default:
                return 'bg-gray-100 text-gray-800';
        }
    }

    function formatAddress(address: Deal['address']): string {
        if (!address) return 'No address';
        return `${address.street}, ${address.city}, ${address.state} ${address.zip}`;
    }

    function calculateProfit(deal: Deal): number {
        return (deal.arv ?? 0) - (deal.sellingPrice ?? 0);
    }

    function calculateMargin(deal: Deal): number {
        const arv = deal.arv ?? 0;
        if (arv <= 0) return 0;
        return ((arv - (deal.sellingPrice ?? 0)) / arv) * 100;
    }

    function goToDeal(dealId: number) {
        window.location.href = `/deals/${dealId}`;
    }
</script>

<div class="h-full overflow-auto bg-gray-50">
    <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-6">
        {#if (props.deals?.length ?? 0) === 0}
            <div class="text-center py-12">
                <svg class="mx-auto h-12 w-12 text-gray-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                          d="M20 13V6a2 2 0 00-2-2H6a2 2 0 00-2 2v7m16 0v5a2 2 0 01-2 2H6a2 2 0 01-2-2v-5m16 0h-2M4 13h2m13-8l-1 1m-6-2l-1 1"></path>
                </svg>
                <h3 class="mt-2 text-sm font-medium text-gray-900">No deals found</h3>
                <p class="mt-1 text-sm text-gray-500">Try adjusting your filters or check back later.</p>
            </div>
        {:else}
            <div class="p-4 pt-8 pb-12 grid gap-y-10 gap-x-6 md:gap-x-8 grid-cols-1  sm:grid-cols-2  lg:grid-cols-1  xl:grid-cols-2">
                {#each props.deals as deal (deal.dealId)}
                    <div
                            class="bg-white overflow-hidden shadow rounded-lg hover:shadow-md transition-shadow cursor-pointer
               min-w-[260px] sm:min-w-[300px] min-h-[300px]"
                            role="button"
                            tabindex="0"
                            onclick={() => goToDeal(deal.dealId)}
                            onkeydown={(event) => handleCardKeydown(event, deal.dealId)}
                    >
                        <div class="px-4 py-5 sm:p-6">
                            <!-- Header -->
                            <div class="flex items-center justify-between mb-4">
                <span class="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium {getStatusColor(deal.dealState)}">
                  {deal.dealState}
                </span>
                                {#if deal.parcelNumber}
                                    <span class="text-xs text-gray-500">#{deal.parcelNumber}</span>
                                {/if}
                            </div>

                            <!-- Title and Address -->
                            <div class="mb-4">
                                <h3 class="text-lg font-medium text-gray-900 truncate mb-1"
                                    title={deal.title}>
                                    {deal.title}
                                </h3>
                                <p class="text-sm text-gray-500 truncate" title={formatAddress(deal.address)}>
                                    {deal.address?.street || 'No address'}
                                </p>
                                <p class="text-xs text-gray-400 truncate" title={formatAddress(deal.address)}>
                                    {deal.address ? `${deal.address.city}, ${deal.address.state} ${deal.address.zip}` : ''}
                                </p>
                            </div>

                            <!-- Financial Info -->
                            <div class="space-y-3">
                                <div class="grid grid-cols-2 gap-4">
                                    <div>
                                        <p class="text-xs font-medium text-gray-500 uppercase tracking-wider">Selling
                                            Price</p>
                                        <p class="text-lg font-semibold text-gray-900">{formatCurrency(deal.sellingPrice)}</p>
                                    </div>
                                    <div>
                                        <p class="text-xs font-medium text-gray-500 uppercase tracking-wider">ARV</p>
                                        <p class="text-lg font-semibold text-gray-900">{formatCurrency(deal.arv)}</p>
                                    </div>
                                </div>

                                <div class="border-t pt-3">
                                    <div class="flex justify-between items-center">
                                        <div>
                                            <p class="text-xs font-medium text-gray-500 uppercase tracking-wider">
                                                Profit</p>
                                            <p class="text-lg font-semibold {calculateProfit(deal) >= 0 ? 'text-green-600' : 'text-red-600'}">
                                                {formatCurrency(calculateProfit(deal))}
                                            </p>
                                        </div>
                                        <div class="text-right">
                                            <p class="text-xs font-medium text-gray-500 uppercase tracking-wider">
                                                Margin</p>
                                            <p class="text-lg font-semibold {calculateMargin(deal) >= 20 ? 'text-green-600' : calculateMargin(deal) >= 10 ? 'text-yellow-600' : 'text-red-600'}">
                                                {calculateMargin(deal).toFixed(1)}%
                                            </p>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </div>

                        <!-- Action bar -->
                        <div class="bg-gray-50 px-4 py-3 sm:px-6">
                            <div class="flex justify-between items-center">
                                <div class="inline-flex items-center px-3 py-1 border border-transparent text-xs font-medium rounded text-indigo-700 bg-indigo-100 hover:bg-indigo-200 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500">
                                    View Details
                                    <svg class="ml-1 h-3 w-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                                              d="M9 5l7 7-7 7"></path>
                                    </svg>
                                </div>
                            </div>
                        </div>
                    </div>
                {/each}
            </div>
        {/if}
    </div>
</div>

<style>
    .line-clamp-2 {
        display: -webkit-box;
        -webkit-line-clamp: 2;
        -webkit-box-orient: vertical;
        overflow: hidden;
    }
</style>