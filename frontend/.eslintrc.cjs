module.exports = {
  root: true,
  ignorePatterns: ["dist/", "coverage/", "android/", "*.timestamp-*.mjs"],
  env: {
    browser: true,
    es2021: true,
    node: true,
  },
  globals: {
    __IS_MOBILE_BUILD__: "readonly",
  },
  extends: [
    "eslint:recommended",
    "plugin:vue/vue3-essential",
    "@vue/eslint-config-prettier",
  ],
  parserOptions: {
    ecmaVersion: "latest",
    sourceType: "module",
  },
  overrides: [
    {
      files: ["*.vue"],
      parser: "vue-eslint-parser",
      parserOptions: {
        ecmaVersion: "latest",
        sourceType: "module",
      },
    },
  ],
  rules: {
    "no-unused-vars": "warn",
    "prettier/prettier": "off",
    "vue/script-setup-uses-vars": "error",
    "vue/multi-word-component-names": "off",
  },
};
