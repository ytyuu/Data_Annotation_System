# Data Annotation System Monorepo

This repository is organized as a Turbo monorepo.

## Structure

- `apps/api` - Java API server
- `apps/web` - static frontend app

## Quick start

```powershell
npm install
npm run dev
```

## Ports

- API: `http://localhost:7000`
- Web: `http://localhost:3000`

## Useful commands

```powershell
npm run build
npm run test
npm run lint
```

## Notes

The new monorepo entrypoints live under `apps/`. The root-level legacy files are left in place for reference, but the Turbo workflow uses the workspace packages.

