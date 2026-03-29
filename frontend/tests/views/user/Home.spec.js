import { describe, it, expect, vi, beforeEach } from "vitest";
import { shallowMount } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import Home from "@/views/user/Home.vue";
import { useUserStore } from "@/store/user";

vi.mock("vue-router", () => ({
  useRouter: () => ({ push: vi.fn() }),
}));

vi.mock("@/api/student", () => ({
  getStudentDashboard: vi.fn().mockResolvedValue({
    code: 200,
    data: {
      enrolledCourses: 1,
      vocabularyCount: 2,
      pendingAssignments: 3,
      todayStudyTime: 4,
      myClasses: [],
      pendingAssignmentsList: [],
      learningRecommendation: null,
    },
  }),
  getTodayLearningStats: vi.fn().mockResolvedValue({
    code: 200,
    data: {
      wordsLearned: 1,
      reviewCount: 2,
      studyTime: 3,
      accuracy: 88.8,
    },
  }),
}));

describe("Home.vue (User Dashboard)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    setActivePinia(createPinia());
    const userStore = useUserStore();
    userStore.userInfo = { username: "user", realName: "测试用户" };
  });

  const createWrapper = () =>
    shallowMount(Home, {
      global: {
        stubs: ["el-card", "el-row", "el-col", "el-button", "el-empty"],
      },
    });

  it("mounts without error", () => {
    const wrapper = createWrapper();
    expect(wrapper.exists()).toBe(true);
  });

  it("initializes dashboardData and learningStats with expected shape", () => {
    const wrapper = createWrapper();
    expect(wrapper.vm.dashboardData).toBeDefined();
    expect(wrapper.vm.learningStats).toBeDefined();
  });

  it("getCourseProgress converts decimal progress to percentage", () => {
    const wrapper = createWrapper();
    expect(wrapper.vm.getCourseProgress({ progress: 0.45 })).toBe(45);
  });

  it("formatDate returns empty string for invalid values", () => {
    const wrapper = createWrapper();
    expect(wrapper.vm.formatDate("")).toBe("");
    expect(wrapper.vm.formatDate(null)).toBe("");
  });

  it("loadDashboard populates dashboard data from student dashboard api", async () => {
    const wrapper = createWrapper();
    await wrapper.vm.loadDashboard();
    expect(wrapper.vm.dashboardData.enrolledCourses).toBe(1);
    expect(wrapper.vm.dashboardData.vocabularyCount).toBe(2);
  });

  it("loadLearningStats populates today stats from api", async () => {
    const wrapper = createWrapper();
    await wrapper.vm.loadLearningStats();
    expect(wrapper.vm.learningStats.wordsLearned).toBe(1);
    expect(wrapper.vm.learningStats.reviewCount).toBe(2);
  });
});
