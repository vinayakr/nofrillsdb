# No-Frills DB Frontend

SvelteKit application for the No-Frills DB user interface.

## Development

```bash
# Install dependencies
npm install

# Start development server
npm run dev
```

## Production Deployment

### Environment Variables

Before building for production, configure the following environment variables in `.env.production`:

- `PUBLIC_API_BASE_URL` - Backend API URL (e.g., `https://api.example.com`)
- `PUBLIC_AUTH0_DOMAIN` - Auth0 domain
- `PUBLIC_AUTH0_CLIENT_ID` - Auth0 client ID
- `PUBLIC_AUTH0_AUDIENCE` - Auth0 API audience
- `PUBLIC_STRIPE_PUBLISHABLE_KEY` - Stripe publishable key

### Building for Production

```bash
# Install dependencies (including adapter-node)
npm install

# Build for production
npm run build

# Start production server
npm start
```

### Docker Deployment

1. **Build the Docker image:**
```bash
docker build -t nofrills-frontend .
```

2. **Run the container:**
```bash
docker run -d \
  --name nofrills-frontend \
  -p 3000:3000 \
  -e PUBLIC_API_BASE_URL=https://your-backend-url.com \
  -e PUBLIC_AUTH0_DOMAIN=your-domain.auth0.com \
  -e PUBLIC_AUTH0_CLIENT_ID=your-client-id \
  -e PUBLIC_STRIPE_PUBLISHABLE_KEY=pk_live_your_key \
  nofrills-frontend
```

3. **Using Docker Compose (recommended):**

Create `docker-compose.yml`:
```yaml
version: '3.8'
services:
  frontend:
    build: .
    ports:
      - "3000:3000"
    environment:
      - NODE_ENV=production
      - PUBLIC_API_BASE_URL=https://your-backend-url.com
      - PUBLIC_AUTH0_DOMAIN=your-domain.auth0.com
      - PUBLIC_AUTH0_CLIENT_ID=your-client-id
      - PUBLIC_STRIPE_PUBLISHABLE_KEY=pk_live_your_key
    restart: unless-stopped
```

Run with: `docker-compose up -d`

### Health Check

The application provides a health check endpoint at `/health` when running in production.

### Security Features

- Security headers (XSS protection, content type options, etc.)
- HTTPS enforcement in production
- Environment variable validation
- Graceful shutdown handling

## Project Structure

- `src/routes/` - SvelteKit routes and pages
- `src/lib/` - Shared components and utilities
- `static/` - Static assets
- `build/` - Production build output
- `Dockerfile` - Container configuration