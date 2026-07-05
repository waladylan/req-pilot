import axios, { AxiosError } from "axios";

import type { ApiErrorDTO } from "@/types/requirement";

export function resolveApiBaseUrl(value: string | undefined = import.meta.env.VITE_API_BASE_URL): string {
  return value ?? "";
}

export const axiosClient = axios.create({
  baseURL: resolveApiBaseUrl(),
  headers: {
    "Content-Type": "application/json",
  },
  timeout: 20000,
});

export function getApiErrorMessage(error: unknown, fallback: string): string {
  if (error instanceof AxiosError) {
    const data = error.response?.data as ApiErrorDTO | undefined;
    return data?.message ?? error.message ?? fallback;
  }

  if (error instanceof Error) {
    return error.message;
  }

  return fallback;
}
