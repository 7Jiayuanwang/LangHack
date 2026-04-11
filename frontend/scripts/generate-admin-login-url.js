#!/usr/bin/env node

/**
 * 管理员登录链接生成器
 *
 * 用法：
 *   node scripts/generate-admin-login-url.js
 *   node scripts/generate-admin-login-url.js --base-url https://learn.imau.edu.cn
 *
 * 自动读取 docker/.env 中的 ADMIN_LOGIN_SECRET 和 ADMIN_PORTAL_PATH
 */

import { readFileSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));

// 从 docker/.env 加载配置
function loadDotEnv() {
  const envPath = resolve(__dirname, '../../docker/.env');
  try {
    const content = readFileSync(envPath, 'utf-8');
    const env = {};
    for (const line of content.split('\n')) {
      const trimmed = line.trim();
      if (!trimmed || trimmed.startsWith('#')) continue;
      const eqIdx = trimmed.indexOf('=');
      if (eqIdx === -1) continue;
      const key = trimmed.slice(0, eqIdx).trim();
      const val = trimmed.slice(eqIdx + 1).trim();
      env[key] = val;
    }
    return env;
  } catch {
    return {};
  }
}

const dotenv = loadDotEnv();

// 从命令行参数或环境变量或 .env 文件获取配置
const args = process.argv.slice(2);
const baseUrlIndex = args.indexOf('--base-url');
const baseUrl = baseUrlIndex !== -1 ? args[baseUrlIndex + 1] : (process.env.APP_BASE_URL || dotenv.APP_BASE_URL || 'http://localhost:3000');
const secret = process.env.ADMIN_LOGIN_SECRET || dotenv.ADMIN_LOGIN_SECRET || 'default-secret';
const portalPath = process.env.ADMIN_PORTAL_PATH || dotenv.ADMIN_PORTAL_PATH || 'admin-portal';

// 生成时间戳
const timestamp = Date.now();

// 生成令牌（与前端保持一致的算法）
function generateToken(timestamp, secret) {
  const str = timestamp + secret;
  let hash = 0;
  for (let i = 0; i < str.length; i++) {
    const char = str.charCodeAt(i);
    hash = ((hash << 5) - hash) + char;
    hash = hash & hash;
  }
  return Math.abs(hash).toString(36);
}

const token = generateToken(timestamp, secret);

// 生成完整的 URL
const adminLoginUrl = `${baseUrl}/${portalPath}?token=${token}&t=${timestamp}`;

console.log('\n==============================================');
console.log('管理员登录链接生成器');
console.log('==============================================\n');
console.log('配置信息:');
console.log(`  基础 URL: ${baseUrl}`);
console.log(`  密钥: ${secret === 'default-secret' ? '⚠️  使用默认密钥（不安全）' : '✓ 自定义密钥'}`);
console.log(`  时间戳: ${timestamp}`);
console.log(`  令牌: ${token}`);
console.log(`  有效期: 5 分钟\n`);
console.log('生成的管理员登录链接:');
console.log(`  ${adminLoginUrl}\n`);
console.log('==============================================\n');

if (secret === 'default-secret') {
  console.log('⚠️  警告: 您正在使用默认密钥！');
  console.log('   生产环境请设置环境变量 ADMIN_LOGIN_SECRET\n');
}

console.log('提示:');
console.log('  1. 此链接 5 分钟内有效');
console.log('  2. 请妥善保管此链接，不要泄露给非管理员');
console.log('  3. 建议通过安全渠道（如加密通讯工具）分享');
console.log('  4. 生产环境务必设置自定义密钥\n');
