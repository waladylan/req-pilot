# Coding Style Guide

Use this guide when building another project that should feel like this codebase. It documents the project conventions used across the React, TypeScript, API, routing, state, and UI layers.

## Project Stack
- Build with React, TypeScript, Vite, and ES modules.
- Use TanStack Router for file-based routing.
- Use TanStack Query for server state.
- Use Zustand for small client-side stores.
- Use Axios through one shared client.
- Use Radix UI/shadcn-style primitives for reusable UI components.
- Use `react-i18next` for user-facing text.
- Use `zod` for route search validation and lightweight runtime validation.
- Use `lucide-react` and existing icon constants for icons.
- All packages use LTS versions.

## Formatting

Follow the existing Prettier config:

- 2 spaces.
- Semicolons.
- Double quotes.
- Trailing commas.
- `printWidth: 100`.
- Always wrap arrow function params.
- Let `prettier-plugin-tailwindcss` sort Tailwind classes.

Run:

```sh
yarn format
yarn lint
yarn build
```

## TypeScript

- Keep `strict` TypeScript enabled.
- Prefer explicit DTO and payload types in `src/types`.
- Use `type` for object shapes and component props.
- Use `interface` mainly for Zustand state shapes or declaration merging.
- Import types with `import type` when the import is type-only.
- Avoid `any`. If unavoidable, keep it narrow and local.
- Use optional properties for optional API fields and `null` only when the API actually returns null.

Example:

```ts
export type CreateProjectPayload = {
  name: string;
  description?: string;
  departmentUuid: string;
};
```

## Imports

- Use the `@/` alias for files under `src`.
- Use relative imports for sibling files inside the same feature folder, especially `./api` and `./helpers`.
- Keep imports sorted by ESLint and `simple-import-sort`.
- Put package imports first, then app imports, then relative imports.
- Remove unused imports instead of leaving commented imports.

Example:

```ts
import { useQuery } from "@tanstack/react-query";

import { QUERY_KEYS } from "@/constants";
import type { AdminProjectListParams } from "@/types";

import { getProjectsAPI } from "./api";
```

## Folder Structure

Use feature-first folders with shared infrastructure separated clearly:

- `src/apis/<resource>/api.ts` for raw HTTP calls.
- `src/apis/<resource>/fetch.hook.ts` for query hooks.
- `src/apis/<resource>/mutate.hook.ts` for mutation hooks.
- `src/components/ui` for Radix/shadcn-style primitives.
- `src/components/<feature>` for reusable feature components.
- `src/pages/<page>` for route page containers.
- `src/routes` for TanStack Router route files only.
- `src/hooks` for reusable cross-page hooks.
- `src/helpers` for pure utilities and domain helpers.
- `src/stores` for Zustand stores.
- `src/constants` for shared constants, paths, keys, and assets.
- `src/types` for API DTOs and shared app types.

For non-trivial components, prefer a folder with `index.tsx`:

```txt
src/components/project-features/project-card/index.tsx
src/components/project-features/create-project-dialog/index.tsx
```

## API Layer

Raw API functions should be small, named with an `API` suffix, and return `res.data`.

```ts
const PREFIX = "/projects";

export async function getProjectByIdAPI(uuid: string): Promise<BaseResponse<ProjectResponseDTO>> {
  const res = await axiosClient.get(`${PREFIX}/${uuid}`);
  return res.data;
}
```

Rules:

- Use `axiosClient` from `@/lib/axios`.
- Keep endpoint prefixes at the top of `api.ts`.
- Type every request payload and response.
- Return `BaseResponse<T>` or `PaginationResponse<T>` from API functions.
- Do not toast or navigate from raw API functions.
- Keep auth, refresh-token, and global error behavior inside the shared Axios client.

## Query Hooks

Query hooks live in `fetch.hook.ts`.

- Prefix hooks with `useFetch`.
- Use `QUERY_KEYS` constants.
- Unwrap `response.data` inside the hook when useful to callers.
- Return a compact object with `data`, loading state, error, pagination, or `refetch`.
- Use `isFetching` for list/loading reads.

Example:

```ts
export function useFetchProjectById(uuid: string) {
  const { data, isFetching, error } = useQuery({
    queryKey: [QUERY_KEYS.PROJECT_BY_ID, uuid],
    queryFn: async () => {
      const response = await getProjectByIdAPI(uuid);
      return response.data;
    },
  });

  return { data, isFetching, error };
}
```

## Mutation Hooks

Mutation hooks live in `mutate.hook.ts`.

- Prefix hooks with `useCreate`, `useUpdate`, `useDelete`, or another action verb.
- Return renamed mutate functions, for example `{ createProject, isPending }`.
- Invalidate all affected `QUERY_KEYS` in `onSuccess`.
- Let pages/components provide success and error toasts when the message is workflow-specific.

Example:

```ts
export const useDeleteProject = () => {
  const queryClient = useQueryClient();

  const { mutate: deleteProject, isPending } = useMutation({
    mutationFn: async (uuid: string) => {
      const response = await deleteProjectAPI(uuid);
      return response;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [QUERY_KEYS.PROJECTS] });
    },
  });

  return { deleteProject, isPending };
};
```

## Components

- Use function components.
- Default-export page components and larger feature components.
- Named-export shared primitives and utilities.
- Define `Props` types near the component.
- Keep component state local unless it is shared across screens.
- Name handlers with `handle...`.
- Derive display data with `useMemo` when sorting, filtering, or mapping non-trivial lists.
- Use `useCallback` when passing route/state update handlers that are dependencies of effects.
- Prefer composition over large prop APIs.
- Keep destructive flows behind `ConfirmDialog` or feature-specific dialog wrappers.

Example:

```tsx
type ProjectCardProps = {
  project: ProjectResponseDTO;
  onClick: () => void;
  editTrigger?: ReactNode;
  deleteTrigger?: ReactNode;
};
```

## UI Style

- Compose class names with `cn()` from `@/lib/utils`.
- Prefer shared `Button`, `Input`, `Select`, `Dialog`, `DropdownMenu`, `Popover`, and `Command` primitives.
- Use Tailwind utilities for layout and spacing.
- Use theme tokens such as `bg-background`, `text-muted-foreground`, `border-border`, `bg-brand`, and `bg-comp-bg`.
- Keep custom colors in `src/styles/theme.css` instead of scattering raw colors through components.
- Use rounded, compact controls; many buttons use `rounded-full`.
- Use `lucide-react` icons in buttons and menus when possible.
- Keep loading states visible with `isPending`, `isFetching`, skeletons, or spinner icons.
- Use responsive Tailwind classes directly in JSX, for example `sm:flex-row`, `lg:grid-cols-3`, `xl:grid-cols-4`.
- Use `line-clamp`, `truncate`, and fixed heights for dense card/list layouts.

## Routing

- Keep route files thin.
- Route files should import page components and guards, then declare the route.
- Use `createFileRoute` and `createRootRouteWithContext`.
- Use `zod` for validating search params.
- Keep route path constants in `PATHS`.
- Do not edit `src/routeTree.gen.ts` manually.

Example:

```ts
const projectListSearchSchema = z.object({
  page: z.coerce.number().int().positive().optional(),
  keyword: z.string().optional(),
});

export const Route = createFileRoute("/_authenticated/")({
  component: ProjectsPage,
  validateSearch: projectListSearchSchema,
});
```

## Pages

- Pages orchestrate data hooks, URL search state, filtering, dialogs, and layout.
- Keep page-specific helper types/functions in `src/pages/<page>/helpers.ts`.
- Use `useSearch` and `navigate({ search })` for list filters that should persist in the URL.
- Use shared page layout components such as `PageHeader`, `Empty`, `LoadingSkeleton`, and `AppPagination`.
- Keep API details out of JSX by using hooks and helpers.

## State Stores

Use Zustand for small, focused stores.

- Name stores `useXStore`.
- Define a local state interface.
- Keep setters explicit.
- Include a `reset` action when state owns session-like or workflow-like data.
- Read store state outside React only when needed for interceptors or runtime helpers.

Example:

```ts
interface AuthState {
  accessToken: string | null;
  setAccessToken: (token: string | null) => void;
  reset: () => void;
}
```

## Constants

- Put query keys in `QUERY_KEYS`.
- Put route paths in `PATHS`.
- Put storage keys, default values, role values, node constants, and media constants under `src/constants`.
- Export constants from `src/constants/index.ts` when they are widely used.
- Prefer constants over repeated strings in query keys, paths, local storage keys, and API options.

## i18n

- Use `useTranslation(namespace)` in components.
- Do not hard-code user-facing text unless it is a temporary fallback.
- Prefer translation fallback strings when the key may not exist yet:

```tsx
{t("delete_dialog.success", "Project deleted")}
```

- Use the `common` namespace for shared actions such as cancel, delete, search, and reset.

## Helpers

- Keep helpers pure and framework-free when possible.
- Put page-specific helpers beside the page.
- Put reusable domain helpers in `src/helpers`.
- Validate and normalize URL/search/API inputs before passing them to hooks.
- Use small functions with clear names over inline repeated logic.

## Error Handling

- Handle auth refresh and global 401/403 behavior in `src/lib/axios.ts`.
- Use `toast` in pages or feature components for action-specific success and error messages.
- Reject failed Axios calls from interceptors after handling global side effects.
- Keep destructive operations guarded by confirmation dialogs.

## Comments

- Prefer self-explanatory names over comments.
- Use comments for sections, non-obvious business rules, and complex algorithms.
- Avoid comments that restate the code.
- Keep TODOs actionable and specific.

## Naming

- Components: `PascalCase`.
- Hooks: `useCamelCase`.
- API functions: `verbResourceAPI`.
- Query hooks: `useFetchResource`.
- Mutation hooks: `useCreateResource`, `useUpdateResource`, `useDeleteResource`.
- DTOs: `ResourceResponseDTO`, `ResourceUsageSummaryDTO`.
- Payloads: `CreateResourcePayload`, `UpdateResourcePayload`.
- Constants: `UPPER_SNAKE_CASE` for scalar constants, grouped objects for namespaced constants.

## Implementation Checklist

When adding a new feature:

1. Add DTO and payload types in `src/types`.
2. Add API functions in `src/apis/<resource>/api.ts`.
3. Add query hooks in `fetch.hook.ts`.
4. Add mutation hooks in `mutate.hook.ts`.
5. Add query keys to `QUERY_KEYS`.
6. Add page route in `src/routes`.
7. Keep page orchestration in `src/pages/<page>`.
8. Extract reusable UI into `src/components/<feature>`.
9. Put pure transforms in `helpers.ts`.
10. Add translations for user-facing strings.
11. Run `yarn format`, `yarn lint`, and `yarn build`.


## Registry Pattern

When a domain concept has metadata (label, color, icon, shape, category, etc.), use a **registry object** as the single source of truth.

### Rules

- Do not declare duplicated string literal unions manually.
- Derive TypeScript types from registry objects using `keyof typeof`.
- Avoid repeated string literals throughout the codebase.
- Avoid switch/case or mapping objects that duplicate registry metadata.
- Store UI metadata together with the domain definition.

Preferred:

```ts
export const NODE_REGISTRY = {
  START: {
    label: "Start",
    color: "#22C55E",
    shape: "pill",
  },
  ACTION: {
    label: "Action",
    color: "#3B82F6",
    shape: "rectangle",
  },
  DECISION: {
    label: "Decision",
    color: "#F59E0B",
    shape: "diamond",
  },
  END: {
    label: "End",
    color: "#EF4444",
    shape: "pill",
  },
} as const;

export type FlowNodeType = keyof typeof NODE_REGISTRY;
```

Use registry lookups instead of duplicated mappings:

```ts
const config = NODE_REGISTRY[node.type];

config.label;
config.color;
config.shape;
```

This pattern should also be applied to:
- Edge Registry
- Command Registry
- Tool Registry
- Sidebar Registry
- Menu Registry

Registry-first design is preferred over manually maintained enums or string union types whenever metadata exists.
