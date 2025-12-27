export interface Deal {
    dealId: string;
    address: {
        street: string;
        street2?: string;
        city: string;
        zip: string;
        state: string;
    } | null;
    parcelNumber?: string;
    sellingPrice: number;
    arv: number;
    title: string;
    description?: string;
    dealState: 'OPEN' | 'PENDING' | 'COMPLETED';
}

export interface FilterState {
    priceRange: { min: number; max: number };
    arvRange: { min: number; max: number };
    states: Set<string>;
    dealStates: Set<string>;
    searchTerm: string;
    showOnlyMappable: boolean;
}


export function filterDeals(deals:Deal[], filters: FilterState) {
    return deals.filter(deal => {

        // Map-only filter - only show deals with complete addresses
        if (filters.showOnlyMappable) {
            if (!deal.address || !deal.address.street || !deal.address.city || !deal.address.state) {
                return false;
            }
        }

        // Price range filter
        if (deal.sellingPrice < filters.priceRange.min || deal.sellingPrice > filters.priceRange.max) {
            return false;
        }

        // ARV range filter
        if (deal.arv < filters.arvRange.min || deal.arv > filters.arvRange.max) {
            return false;
        }

        // State filter
        if (deal.address?.state && !filters.states.has(deal.address.state)) {
            return false;
        }

        // Deal state filter
        if (!filters.dealStates.has(deal.dealState)) {

            return false;
        }

        // Search term filter
        if (filters.searchTerm) {
            const searchLower = filters.searchTerm.toLowerCase();
            const searchableText = [
                deal.address?.street,
                deal.address?.city,
                deal.address?.state,
                deal.address?.zip,
                deal.title,
                deal.description,
                deal.parcelNumber
            ].filter(Boolean).join(' ').toLowerCase();

            if (!searchableText.includes(searchLower)) {
                return false;
            }
        }
        return true;
    });
}