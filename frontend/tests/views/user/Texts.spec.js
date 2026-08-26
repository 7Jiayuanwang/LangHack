import { describe, it, expect, vi, beforeEach } from "vitest";
import { shallowMount } from "@vue/test-utils";

vi.mock("vue-router", () => ({
  useRouter: () => ({ push: vi.fn() }),
  useRoute: () => ({ query: {} }),
}));

vi.mock("element-plus", () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
}));

vi.mock("@/utils/request", () => ({
  default: vi.fn().mockResolvedValue({ data: { records: [], total: 0 } }),
}));

import Texts from "@/views/user/Texts.vue";

describe("User Texts.vue", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  const createWrapper = () => shallowMount(Texts);

  it("has loading ref defined", () => {
    const wrapper = createWrapper();
    expect(typeof wrapper.vm.loading).toBe("boolean");
  });

  it("has empty courses array", () => {
    const wrapper = createWrapper();
    expect(wrapper.vm.courses).toEqual([]);
  });

  it("has empty texts array", () => {
    const wrapper = createWrapper();
    expect(wrapper.vm.texts).toEqual([]);
  });

  it("uses an initial display count of 12", () => {
    const wrapper = createWrapper();
    expect(wrapper.vm.displayCount).toBe(12);
  });

  it("has an empty allTexts collection", () => {
    const wrapper = createWrapper();
    expect(wrapper.vm.allTexts).toEqual([]);
  });

  it("has loadingMore disabled initially", () => {
    const wrapper = createWrapper();
    expect(wrapper.vm.loadingMore).toBe(false);
  });

  it("has selectedCourse as empty string", () => {
    const wrapper = createWrapper();
    expect(wrapper.vm.selectedCourse).toBe("");
  });

  it("has selectedDifficulty as empty string", () => {
    const wrapper = createWrapper();
    expect(wrapper.vm.selectedDifficulty).toBe("");
  });

  it("has searchKeyword as empty string", () => {
    const wrapper = createWrapper();
    expect(wrapper.vm.searchKeyword).toBe("");
  });
});
