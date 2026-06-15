import { describe, it, expect, beforeEach, vi, afterEach } from "vitest";
import { setActivePinia, createPinia } from "pinia";
import { useUserStore } from "@/store/user";

// Mock the auth API module
vi.mock("@/api/auth", () => ({
  login: vi.fn(),
  logout: vi.fn(),
}));

import { login as loginApi, logout as logoutApi } from "@/api/auth";

describe("User Store", () => {
  let store;

  beforeEach(() => {
    // Clear localStorage and sessionStorage before each test
    localStorage.clear();
    sessionStorage.clear();
    // Create a fresh pinia instance
    setActivePinia(createPinia());
    store = useUserStore();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe("initial state", () => {
    it("should have empty state when localStorage is empty", () => {
      expect(store.token).toBe("");
      expect(store.userInfo).toEqual({});
      expect(store.userRole).toBe("");
    });

    it("should restore token from localStorage on creation", () => {
      localStorage.setItem("token", "saved-token-123");
      localStorage.setItem("userRole", "ADMIN");
      localStorage.setItem(
        "userInfo",
        JSON.stringify({ id: 1, username: "admin" }),
      );

      // Need a new pinia instance to re-read localStorage
      setActivePinia(createPinia());
      const freshStore = useUserStore();

      expect(freshStore.token).toBe("saved-token-123");
      expect(freshStore.userRole).toBe("ADMIN");
      expect(freshStore.userInfo).toEqual({ id: 1, username: "admin" });
    });

    it("should handle corrupted JSON in localStorage gracefully", () => {
      localStorage.setItem("token", "some-token");
      localStorage.setItem("userInfo", "{invalid-json");
      localStorage.setItem("userRole", "ADMIN");

      // The store handles corrupted JSON gracefully via loadUserInfoFromStorage()
      // which catches JSON.parse errors and returns {}
      setActivePinia(createPinia());
      const freshStore = useUserStore();
      expect(freshStore.token).toBe("some-token");
      expect(freshStore.userInfo).toEqual({});
    });
  });

  describe("getters", () => {
    it("isLoggedIn should return true when token exists", () => {
      store.token = "some-token";
      expect(store.isLoggedIn).toBe(true);
    });

    it("isLoggedIn should return false when token is empty", () => {
      store.token = "";
      expect(store.isLoggedIn).toBe(false);
    });

    it("isAdmin should return true only for ADMIN role", () => {
      store.userRole = "ADMIN";
      expect(store.isAdmin).toBe(true);

      store.userRole = "TEACHER";
      expect(store.isAdmin).toBe(false);

      store.userRole = "STUDENT";
      expect(store.isAdmin).toBe(false);
    });

    it("isTeacher should return true only for TEACHER role", () => {
      store.userRole = "TEACHER";
      expect(store.isTeacher).toBe(true);

      store.userRole = "ADMIN";
      expect(store.isTeacher).toBe(false);
    });

    it("isStudent should return true only for STUDENT role", () => {
      store.userRole = "STUDENT";
      expect(store.isStudent).toBe(true);

      store.userRole = "USER";
      expect(store.isStudent).toBe(false);

      store.userRole = "ADMIN";
      expect(store.isStudent).toBe(false);
    });
  });

  describe("login action", () => {
    it("should set token, userInfo, and userRole on successful login", async () => {
      const mockResponse = {
        data: {
          token: "jwt-token-abc",
          user: { id: 1, username: "testuser", role: "STUDENT" },
        },
      };
      loginApi.mockResolvedValue(mockResponse);

      const result = await store.login({
        username: "testuser",
        password: "pass123",
      });

      expect(loginApi).toHaveBeenCalledWith({
        username: "testuser",
        password: "pass123",
      });
      expect(store.token).toBe("jwt-token-abc");
      expect(store.userInfo).toEqual({
        id: 1,
        username: "testuser",
        role: "STUDENT",
      });
      expect(store.userRole).toBe("STUDENT");
      expect(result).toEqual(mockResponse.data);
    });

    it("should persist login data to localStorage on success", async () => {
      const mockResponse = {
        data: {
          token: "jwt-token-xyz",
          user: { id: 2, username: "admin", role: "ADMIN" },
        },
      };
      loginApi.mockResolvedValue(mockResponse);

      await store.login({ username: "admin", password: "admin123" });

      expect(localStorage.getItem("token")).toBe("jwt-token-xyz");
      expect(localStorage.getItem("userRole")).toBe("ADMIN");
      expect(JSON.parse(localStorage.getItem("userInfo"))).toEqual({
        id: 2,
        username: "admin",
        role: "ADMIN",
      });
    });

    it("should not change state on login failure", async () => {
      loginApi.mockRejectedValue(new Error("Invalid credentials"));

      await expect(
        store.login({ username: "bad", password: "bad" }),
      ).rejects.toThrow("Invalid credentials");

      expect(store.token).toBe("");
      expect(store.userInfo).toEqual({});
      expect(store.userRole).toBe("");
      expect(localStorage.getItem("token")).toBeNull();
    });
  });

  describe("logout action", () => {
    it("should clear all state and localStorage on logout", async () => {
      // Set up logged-in state
      store.token = "some-token";
      store.userInfo = { id: 1, username: "user" };
      store.userRole = "STUDENT";
      localStorage.setItem("token", "some-token");
      localStorage.setItem("userInfo", JSON.stringify({ id: 1 }));
      localStorage.setItem("userRole", "STUDENT");

      logoutApi.mockResolvedValue({});

      await store.logout();

      expect(store.token).toBe("");
      expect(store.userInfo).toEqual({});
      expect(store.userRole).toBe("");
      expect(localStorage.getItem("token")).toBeNull();
      expect(localStorage.getItem("userInfo")).toBeNull();
      expect(localStorage.getItem("userRole")).toBeNull();
      // logout now accepts (token, refreshToken) - refreshToken may be empty string
      expect(logoutApi).toHaveBeenCalledWith("some-token", "");
    });

    it("should clear state even if API call fails", async () => {
      store.token = "some-token";
      store.userRole = "ADMIN";
      localStorage.setItem("token", "some-token");

      logoutApi.mockRejectedValue(new Error("Network error"));

      // The store catches the API error internally and does NOT re-throw
      await store.logout();

      // State should still be cleared
      expect(store.token).toBe("");
      expect(store.userRole).toBe("");
      expect(localStorage.getItem("token")).toBeNull();
      expect(logoutApi).toHaveBeenCalledWith("some-token", "");
    });
  });

  describe("updateUserInfo action", () => {
    it("should update userInfo in store and localStorage", () => {
      const newInfo = { id: 1, username: "updated", email: "new@test.com" };

      store.updateUserInfo(newInfo);

      expect(store.userInfo).toEqual(newInfo);
      expect(JSON.parse(localStorage.getItem("userInfo"))).toEqual(newInfo);
    });
  });

  describe("OPS role and elevation", () => {
    it("isOps getter should return true for OPS role", () => {
      store.userRole = "OPS";
      expect(store.isOps).toBe(true);

      store.userRole = "ADMIN";
      expect(store.isOps).toBe(false);
    });

    it("hasOpsAccess should return true for native OPS role", () => {
      store.userRole = "OPS";
      expect(store.hasOpsAccess).toBe(true);
    });

    it("hasOpsAccess should return true for elevated OPS with valid expiration", () => {
      store.userRole = "ADMIN";
      store.elevatedRole = "OPS";
      store.elevationExpiresAt = new Date(Date.now() + 3600000).toISOString();
      expect(store.hasOpsAccess).toBe(true);
    });

    it("hasOpsAccess should return false for elevated OPS with expired expiration", () => {
      store.userRole = "USER";
      store.elevatedRole = "OPS";
      store.elevationExpiresAt = new Date(Date.now() - 1000).toISOString();
      expect(store.hasOpsAccess).toBe(false);
    });

    it("hasOpsAccess should return false for non-OPS user without elevation", () => {
      store.userRole = "STUDENT";
      expect(store.hasOpsAccess).toBe(false);
    });

    it("hasAdminAccess should return true for native ADMIN role", () => {
      store.userRole = "ADMIN";
      expect(store.hasAdminAccess).toBe(true);
    });

    it("hasAdminAccess should return true for elevated ADMIN with valid expiration", () => {
      store.userRole = "TEACHER";
      store.elevatedRole = "ADMIN";
      store.elevationExpiresAt = new Date(Date.now() + 3600000).toISOString();
      expect(store.hasAdminAccess).toBe(true);
    });

    it("hasAdminAccess should return false for elevated ADMIN with expired expiration", () => {
      store.userRole = "TEACHER";
      store.elevatedRole = "ADMIN";
      store.elevationExpiresAt = new Date(Date.now() - 1000).toISOString();
      expect(store.hasAdminAccess).toBe(false);
    });

    it("setElevation should set elevation state and persist to sessionStorage", () => {
      const expiresAt = new Date(Date.now() + 3600000).toISOString();
      store.setElevation("OPS", expiresAt);

      expect(store.elevatedRole).toBe("OPS");
      expect(store.elevationExpiresAt).toBe(expiresAt);
      expect(sessionStorage.getItem("elevatedRole")).toBe("OPS");
      expect(sessionStorage.getItem("elevationExpiresAt")).toBe(expiresAt);
    });

    it("clearElevation should clear elevation state and sessionStorage", () => {
      const expiresAt = new Date(Date.now() + 3600000).toISOString();
      store.setElevation("OPS", expiresAt);

      store.clearElevation();

      expect(store.elevatedRole).toBeNull();
      expect(store.elevationExpiresAt).toBeNull();
      expect(sessionStorage.getItem("elevatedRole")).toBeNull();
      expect(sessionStorage.getItem("elevationExpiresAt")).toBeNull();
    });

    it("logout should clear elevation state", async () => {
      store.token = "some-token";
      store.userRole = "ADMIN";
      store.elevatedRole = "OPS";
      store.elevationExpiresAt = new Date(Date.now() + 3600000).toISOString();
      sessionStorage.setItem("elevatedRole", "OPS");

      logoutApi.mockResolvedValue({});
      await store.logout();

      expect(store.elevatedRole).toBeNull();
      expect(store.elevationExpiresAt).toBeNull();
      expect(sessionStorage.getItem("elevatedRole")).toBeNull();
      expect(sessionStorage.getItem("elevationExpiresAt")).toBeNull();
      expect(logoutApi).toHaveBeenCalledWith("some-token", "");
    });

    it("OPS should be a valid role in validateRole", () => {
      localStorage.setItem("userRole", "OPS");
      setActivePinia(createPinia());
      const freshStore = useUserStore();
      expect(freshStore.userRole).toBe("OPS");
    });
  });

  describe("token persistence across store recreation", () => {
    it("should preserve login state when store is recreated", async () => {
      const mockResponse = {
        data: {
          token: "persistent-token",
          user: { id: 5, username: "persist", role: "TEACHER" },
        },
      };
      loginApi.mockResolvedValue(mockResponse);

      await store.login({ username: "persist", password: "pass" });

      // Recreate pinia and store — simulates page reload
      setActivePinia(createPinia());
      const newStore = useUserStore();

      expect(newStore.token).toBe("persistent-token");
      expect(newStore.userRole).toBe("TEACHER");
      expect(newStore.userInfo).toEqual({
        id: 5,
        username: "persist",
        role: "TEACHER",
      });
      expect(newStore.isLoggedIn).toBe(true);
      expect(newStore.isTeacher).toBe(true);
    });
  });
});
