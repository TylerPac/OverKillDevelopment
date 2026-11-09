import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    react({
      babel: {
        plugins: [['babel-plugin-react-compiler']],
      },
    }),
  ],
  server: {
    // Sign-in redirects must reach the backend on the same hostname as the page
    proxy: {
      '/oauth2/authorization': 'http://localhost:8081',
      '/login/oauth2': 'http://localhost:8081',
    },
  },
})
