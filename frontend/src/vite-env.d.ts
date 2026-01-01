/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_AUTH0_DOMAIN: string
  readonly VITE_AUTH0_CLIENT_ID: string
  readonly VITE_AUTH0_AUDIENCE: string
  readonly VITE_AUTH0_REDIRECT_URL: string
  readonly VITE_JDBC_PWD_ENDPOINT: string
  readonly VITE_JDBC_CRT_ENDPOINT: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}