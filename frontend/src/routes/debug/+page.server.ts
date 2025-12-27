import { dev } from '$app/environment';
import { error } from '@sveltejs/kit';

export async function load() {
    if (!dev) {
        // In production, throw a 404 error to hide the debug page
        throw error(404, 'Page not found');
    }

    return {};
}