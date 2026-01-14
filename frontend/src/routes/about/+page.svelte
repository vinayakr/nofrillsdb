<script lang="ts">
    // No logic needed — keep it boring on purpose
</script>

<svelte:head>
    <title>About NoFrillsDB</title>
    <meta
            name="description"
            content="NoFrillsDB is a simple, founder-run PostgreSQL hosting service focused on predictable pricing and reliability."
    />
</svelte:head>

<main class="about">
    <h1>About NoFrillsDB</h1>

    <p>
        NoFrillsDB is a small, founder-run PostgreSQL hosting service built for
        developers and small businesses that just want a database that works —
        without tiered pricing, surprise limits, or unnecessary complexity.
    </p>

    <p>
        I built NoFrillsDB after years of running PostgreSQL in production for
        side projects and small teams, and getting tired of paying for features
        I didn’t need. The goal is simple: reliable infrastructure, transparent
        pricing, and clear limits.
    </p>

    <p>
        I’ve spent over <strong>20 years</strong> building and operating production
        software systems. NoFrillsDB is intentionally boring — stable, predictable,
        and human-operated.
    </p>

    <h2>What NoFrillsDB is (and isn’t)</h2>

    <ul>
        <li>✔ Simple PostgreSQL hosting</li>
        <li>✔ Pay for the storage you actually use</li>
        <li>✔ Clear limits and transparent pricing</li>
        <li>✔ Direct, human support</li>
        <li>✖ Not a hyperscaler</li>
        <li>✖ Not an all-in-one platform</li>
        <li>✖ Not built for massive enterprise workloads</li>
    </ul>

    <p class="background">
        Background:
        <a
                href="https://www.linkedin.com/in/vinayak-rao-520a853/"
                target="_blank"
                rel="noopener noreferrer"
        >
            LinkedIn ↗
        </a>
    </p>
</main>

<style>
    main.about {
        max-width: 720px;
        margin: 0 auto;
        padding: 4rem 1.5rem;
    }

    h1 {
        font-size: 2.25rem;
        margin-bottom: 1.5rem;
    }

    h2 {
        margin-top: 3rem;
        margin-bottom: 1rem;
        font-size: 1.4rem;
    }

    p {
        line-height: 1.6;
        margin-bottom: 1.25rem;
        color: #222;
    }

    ul {
        margin-left: 1.25rem;
        line-height: 1.7;
    }

    .background {
        margin-top: 3rem;
        font-size: 0.95rem;
        color: #555;
    }

    a {
        color: inherit;
        text-decoration: underline;
    }
</style>