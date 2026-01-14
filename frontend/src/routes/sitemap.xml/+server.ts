import type { RequestHandler } from './$types';

const SITE = 'https://nofrillsdb.com';

const staticPaths = [
    '/',
    '/contact'
];

function xml(urls: string[]) {
    const body = urls
        .map((path) => `<url><loc>${SITE}${path}</loc></url>`)
        .join('');
    return `<?xml version="1.0" encoding="UTF-8"?>
<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
${body}
</urlset>`;
}

export const GET: RequestHandler = async () => {
    return new Response(xml(staticPaths), {
        headers: {
            'Content-Type': 'application/xml; charset=utf-8',
            'Cache-Control': 'public, max-age=3600'
        }
    });
};