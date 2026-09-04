# SkyFlow frontend

React 19 + TypeScript + Vite. See the [root README](../readme.md) for the platform as a whole.

```bash
npm install
npm run dev          # http://localhost:3001, proxies /api to http://localhost:8080
npm run build        # type-check then bundle to dist/
npm test             # vitest
npm run type-check
```

`VITE_PROXY_TARGET` overrides the dev proxy target; `VITE_API_BASE_URL` sets the API origin at
build time (leave it empty to use the same origin, which is what the nginx image does in
production).

## Layout

```
src/
  lib/api.ts        axios instance, token refresh, typed endpoint wrappers
  lib/types.ts      payload types mirroring the service DTOs
  lib/format.ts     UTC date, duration and money formatting
  stores/auth.ts    zustand auth store (tokens in localStorage, profile persisted)
  components/       Layout, ProtectedRoute, FlightCard, AssistantWidget
  pages/            Home, FlightSearch, FlightDetail, Checkout, PaymentResult,
                    MyBookings, Profile, AdminDashboard, Login, Register, NotFound
```

Two things worth knowing:

- **Tokens live in `localStorage`, not in the store.** The axios interceptor is not a React
  component and has to read them on every request. Only the user profile is persisted through
  zustand.
- **A search lives in the URL.** `FlightSearch` reads its filters from query parameters, so results
  are shareable and survive a reload.
