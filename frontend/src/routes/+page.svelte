<script lang="ts">
    import { loggedIn, signUp } from '$lib/auth';

    let isAuthenticated = false;

    loggedIn.subscribe(value => {
        isAuthenticated = value;
    });

</script>

