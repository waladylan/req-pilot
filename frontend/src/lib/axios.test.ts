import { describe, expect, it } from "vitest";

import { resolveApiBaseUrl } from "./axios";

describe("axios API base URL", () => {
  it("uses relative API paths when no base URL is configured", () => {
    expect(resolveApiBaseUrl(undefined)).toBe("");
  });

  it("supports an explicit API base URL for non-Nginx deployments", () => {
    expect(resolveApiBaseUrl("https://api.example.com")).toBe("https://api.example.com");
  });
});
