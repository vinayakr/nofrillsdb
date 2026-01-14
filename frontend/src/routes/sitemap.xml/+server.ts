import type { RequestHandler } from './$types';

const SITE = 'https://nofrillsdb.com';

const staticPaths = [
    '/',
    '/contact',
    '/about'
];

function buildSitemap(paths: string[]): string {
    const urls = paths
        .map(
            (path) => `
  <url>
    <loc>${SITE}${path}</loc>
  </url>`
        )
        .join('');

    return `<?xml version="1.0" encoding="UTF-8"?>
<urlset xmlns="https://www.sitemaps.org/schemas/sitemap/0.9">
${urls}
</urlset>`;
}

export const GET: RequestHandler = async () => {
    return new Response(buildSitemap(staticPaths), {
        status: 200,
        headers: {
            'Content-Type': 'application/xml; charset=utf-8',
            'Cache-Control': 'public, max-age=3600',
            'X-Content-Type-Options': 'nosniff'
        }
    });
};